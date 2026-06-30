package se.canban.app.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.widgets.DirectoryDialog;

import se.canban.app.model.KanbanStore;
import se.canban.app.model.Task;

/**
 * Main application window. Pick a folder, get a {@code .kanban/} directory, then
 * see each board as a grid of lanes (rows) × statuses (columns) with draggable
 * task cards. Every change is written straight to the filesystem.
 */
public final class KanbanWindow {

	/** Field separator used when a card is encoded for drag-and-drop transfer. */
	private static final char SEP = '';
	private static final int CELL_WIDTH = 210;
	private static final String ALL_TAGS = "(all tags)";

	private final Display display;
	private final Shell shell;

	private KanbanStore store;
	private String currentBoard;
	private String currentTag; // null = show all tasks

	private Combo boardCombo;
	private Combo tagCombo;
	private ScrolledComposite scroll;

	public KanbanWindow(Display display) {
		this.display = display;
		this.shell = new Shell(display);
		this.shell.setText("Canban");
		this.shell.setSize(1000, 680);
		this.shell.setLayout(new GridLayout(1, false));
		buildMenu();
		buildToolbar();
		buildBoardArea();
		refresh();
	}

	public void open() {
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	// --- chrome ------------------------------------------------------------

	private void buildMenu() {
		Menu bar = new Menu(shell, SWT.BAR);
		shell.setMenuBar(bar);

		MenuItem fileItem = new MenuItem(bar, SWT.CASCADE);
		fileItem.setText("&File");
		Menu fileMenu = new Menu(fileItem);
		fileItem.setMenu(fileMenu);

		MenuItem openItem = new MenuItem(fileMenu, SWT.PUSH);
		openItem.setText("&Open Folder…\tCtrl+O");
		openItem.setAccelerator(SWT.MOD1 | 'O');
		openItem.addListener(SWT.Selection, e -> openFolder());

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem exitItem = new MenuItem(fileMenu, SWT.PUSH);
		exitItem.setText("E&xit");
		exitItem.addListener(SWT.Selection, e -> shell.close());
	}

	private void buildToolbar() {
		Composite bar = new Composite(shell, SWT.NONE);
		bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		bar.setLayout(new GridLayout(9, false));

		new Label(bar, SWT.NONE).setText("Board:");

		boardCombo = new Combo(bar, SWT.DROP_DOWN | SWT.READ_ONLY);
		boardCombo.setLayoutData(new GridData(200, SWT.DEFAULT));
		boardCombo.addListener(SWT.Selection, e -> {
			currentBoard = boardCombo.getText();
			refreshBoard();
		});

		new Label(bar, SWT.NONE).setText("Tag:");

		tagCombo = new Combo(bar, SWT.DROP_DOWN | SWT.READ_ONLY);
		tagCombo.setLayoutData(new GridData(150, SWT.DEFAULT));
		tagCombo.addListener(SWT.Selection, e -> {
			String selected = tagCombo.getText();
			currentTag = (selected.isEmpty() || selected.equals(ALL_TAGS)) ? null : selected;
			refreshBoard();
		});

		button(bar, "New Board…", e -> newBoard());
		button(bar, "New Lane…", e -> newLane());
		button(bar, "New Status…", e -> newStatus());
		button(bar, "New Task…", e -> newTaskViaToolbar());
		button(bar, "Refresh", e -> refresh());
	}

	private void buildBoardArea() {
		scroll = new ScrolledComposite(shell, SWT.H_SCROLL | SWT.V_SCROLL);
		scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scroll.setExpandHorizontal(true);
		scroll.setExpandVertical(true);
	}

	// --- open folder -------------------------------------------------------

	private void openFolder() {
		DirectoryDialog dialog = new DirectoryDialog(shell, SWT.OPEN);
		dialog.setText("Select a folder for your Kanban data");
		String chosen = dialog.open();
		if (chosen == null) {
			return;
		}
		Path parent = Path.of(chosen);
		Path kanban = parent.resolve(KanbanStore.DIRECTORY_NAME);
		if (!Files.isDirectory(kanban)) {
			MessageBox box = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
			box.setText("Create " + KanbanStore.DIRECTORY_NAME + " directory");
			box.setMessage("No '" + KanbanStore.DIRECTORY_NAME + "' directory found in:\n" + parent
					+ "\n\nCreate one here?");
			if (box.open() != SWT.YES) {
				return;
			}
		}
		try {
			Path k = KanbanStore.ensureKanbanDir(parent);
			store = new KanbanStore(k);
			currentBoard = null;
			shell.setText("Canban – " + k);
			refresh();
		} catch (IOException e) {
			error(e.getMessage());
		}
	}

	// --- structure creation ------------------------------------------------

	private void newBoard() {
		if (!requireStore()) {
			return;
		}
		String name = inputDialog("New Board", "Board name:", "");
		if (name == null) {
			return;
		}
		try {
			store.createBoard(name);
			currentBoard = name;
			refresh();
		} catch (IOException e) {
			error(e.getMessage());
		}
	}

	private void newLane() {
		if (!requireBoard()) {
			return;
		}
		String name = inputDialog("New Lane", "Lane name:", "");
		if (name == null) {
			return;
		}
		try {
			store.createLane(currentBoard, name);
			refreshBoard();
		} catch (IOException e) {
			error(e.getMessage());
		}
	}

	private void newStatus() {
		if (!requireBoard()) {
			return;
		}
		String name = inputDialog("New Status", "Status (column) name:", "");
		if (name == null) {
			return;
		}
		try {
			store.createStatus(currentBoard, name);
			refreshBoard();
		} catch (IOException e) {
			error(e.getMessage());
		}
	}

	private void newTaskViaToolbar() {
		if (!requireBoard()) {
			return;
		}
		List<String> lanes = store.lanes(currentBoard);
		List<String> statuses = store.statuses(currentBoard);
		if (lanes.isEmpty() || statuses.isEmpty()) {
			error("Add at least one lane and one status column first.");
			return;
		}
		createTask(lanes.get(0), statuses.get(0));
	}

	private void createTask(String lane, String status) {
		String name = inputDialog("New Task", "Task name (in " + lane + " / " + status + "):", "");
		if (name == null) {
			return;
		}
		try {
			Task task = store.createTask(currentBoard, lane, status, name, "# " + name + "\n\n");
			refreshBoard();
			openEditor(task);
		} catch (IOException e) {
			error(e.getMessage());
		}
	}

	// --- rendering ---------------------------------------------------------

	private void refresh() {
		List<String> boards = store == null ? List.of() : store.boards();
		boardCombo.setItems(boards.toArray(String[]::new));
		if (currentBoard != null && boards.contains(currentBoard)) {
			boardCombo.setText(currentBoard);
		} else {
			currentBoard = boards.isEmpty() ? null : boards.get(0);
			if (currentBoard != null) {
				boardCombo.setText(currentBoard);
			}
		}
		refreshTagFilter();
		refreshBoard();
	}

	private void refreshTagFilter() {
		List<String> tags = store == null ? List.of() : store.tags();
		List<String> items = new ArrayList<>();
		items.add(ALL_TAGS);
		items.addAll(tags);
		tagCombo.setItems(items.toArray(String[]::new));
		if (currentTag != null && tags.contains(currentTag)) {
			tagCombo.setText(currentTag);
		} else {
			currentTag = null;
			tagCombo.setText(ALL_TAGS);
		}
	}

	private void refreshBoard() {
		Control old = scroll.getContent();
		Composite content = buildGrid();
		scroll.setContent(content);
		scroll.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		if (old != null) {
			old.dispose();
		}
		scroll.layout();
	}

	private Composite buildGrid() {
		if (store == null) {
			Composite empty = new Composite(scroll, SWT.NONE);
			empty.setLayout(new GridLayout(1, false));
			Label l = new Label(empty, SWT.NONE);
			l.setText("Use File ▸ Open Folder… to choose where your .kanban/ directory lives.");
			return empty;
		}
		if (currentBoard == null) {
			Composite empty = new Composite(scroll, SWT.NONE);
			empty.setLayout(new GridLayout(1, false));
			Label l = new Label(empty, SWT.NONE);
			l.setText("No boards yet. Click \"New Board…\" to create one.");
			return empty;
		}

		List<String> lanes = store.lanes(currentBoard);
		List<String> statuses = store.statuses(currentBoard);

		Composite grid = new Composite(scroll, SWT.NONE);
		GridLayout layout = new GridLayout(statuses.size() + 1, false);
		layout.makeColumnsEqualWidth = false;
		grid.setLayout(layout);

		// header row: empty corner + status names
		Label corner = new Label(grid, SWT.NONE);
		corner.setLayoutData(new GridData(120, SWT.DEFAULT));
		for (String status : statuses) {
			Label h = new Label(grid, SWT.CENTER | SWT.BORDER);
			h.setText(status);
			h.setBackground(display.getSystemColor(SWT.COLOR_TITLE_BACKGROUND_GRADIENT));
			GridData gd = new GridData(SWT.FILL, SWT.CENTER, false, false);
			gd.widthHint = CELL_WIDTH;
			h.setLayoutData(gd);
		}

		if (statuses.isEmpty()) {
			Label hint = new Label(grid, SWT.NONE);
			hint.setText("No status columns yet — click \"New Status…\".");
		}

		for (String lane : lanes) {
			Label laneLabel = new Label(grid, SWT.WRAP | SWT.BORDER);
			laneLabel.setText(lane);
			laneLabel.setBackground(display.getSystemColor(SWT.COLOR_TITLE_BACKGROUND_GRADIENT));
			GridData laneGd = new GridData(SWT.FILL, SWT.FILL, false, true);
			laneGd.widthHint = 120;
			laneLabel.setLayoutData(laneGd);

			for (String status : statuses) {
				buildCell(grid, lane, status);
			}
		}

		if (lanes.isEmpty()) {
			Label hint = new Label(grid, SWT.NONE);
			hint.setText("No lanes yet — click \"New Lane…\".");
			hint.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, statuses.size() + 1, 1));
		}

		return grid;
	}

	private void buildCell(Composite grid, String lane, String status) {
		Composite cell = new Composite(grid, SWT.BORDER);
		cell.setLayout(new GridLayout(1, false));
		GridData gd = new GridData(SWT.FILL, SWT.FILL, false, true);
		gd.widthHint = CELL_WIDTH;
		gd.verticalAlignment = SWT.FILL;
		cell.setLayoutData(gd);
		cell.setData(new String[] { lane, status });

		for (String name : store.tasks(currentBoard, lane, status)) {
			Task task = new Task(currentBoard, lane, status, name);
			if (currentTag != null && !store.tagsForTask(task).contains(currentTag)) {
				continue;
			}
			buildCard(cell, task);
		}

		Button add = new Button(cell, SWT.PUSH | SWT.FLAT);
		add.setText("+ Add");
		add.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		add.addListener(SWT.Selection, e -> createTask(lane, status));

		enableDrop(cell);
	}

	private void buildCard(Composite cell, Task task) {
		List<String> tags = store.tagsForTask(task);
		Label card = new Label(cell, SWT.WRAP | SWT.BORDER);
		card.setText(tagsLabel(task.name(), tags));
		card.setData(task);
		card.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		card.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
		card.setToolTipText("Double-click to edit · drag to move"
				+ (tags.isEmpty() ? "" : "\nTags: " + String.join(", ", tags)));
		card.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		card.addListener(SWT.MouseDoubleClick, e -> openEditor((Task) card.getData()));
		enableDrag(card);
		attachCardMenu(card);
	}

	// --- drag and drop -----------------------------------------------------

	private void enableDrag(Label card) {
		DragSource source = new DragSource(card, DND.DROP_MOVE);
		source.setTransfer(TextTransfer.getInstance());
		source.addDragListener(new DragSourceAdapter() {
			@Override
			public void dragSetData(DragSourceEvent event) {
				Task t = (Task) card.getData();
				event.data = t.lane() + SEP + t.status() + SEP + t.name();
			}
		});
	}

	private void enableDrop(Composite cell) {
		DropTarget target = new DropTarget(cell, DND.DROP_MOVE);
		target.setTransfer(new Transfer[] { TextTransfer.getInstance() });
		target.addDropListener(new DropTargetAdapter() {
			@Override
			public void drop(DropTargetEvent event) {
				if (!(event.data instanceof String encoded)) {
					return;
				}
				String[] parts = encoded.split(String.valueOf(SEP), -1);
				if (parts.length != 3) {
					return;
				}
				String[] dest = (String[]) cell.getData();
				Task source = new Task(currentBoard, parts[0], parts[1], parts[2]);
				try {
					store.moveTask(source, dest[0], dest[1]);
					refreshBoard();
				} catch (IOException e) {
					error(e.getMessage());
				}
			}
		});
	}

	private void attachCardMenu(Label card) {
		Menu menu = new Menu(card);
		card.setMenu(menu);

		MenuItem edit = new MenuItem(menu, SWT.PUSH);
		edit.setText("Edit…");
		edit.addListener(SWT.Selection, e -> openEditor((Task) card.getData()));

		MenuItem move = new MenuItem(menu, SWT.CASCADE);
		move.setText("Move to");
		Menu moveMenu = new Menu(menu);
		move.setMenu(moveMenu);
		for (String lane : store.lanes(currentBoard)) {
			for (String status : store.statuses(currentBoard)) {
				MenuItem dest = new MenuItem(moveMenu, SWT.PUSH);
				dest.setText(lane + " / " + status);
				dest.addListener(SWT.Selection, e -> {
					try {
						store.moveTask((Task) card.getData(), lane, status);
						refreshBoard();
					} catch (IOException ex) {
						error(ex.getMessage());
					}
				});
			}
		}

		new MenuItem(menu, SWT.SEPARATOR);

		MenuItem addTag = new MenuItem(menu, SWT.PUSH);
		addTag.setText("Add tag…");
		addTag.addListener(SWT.Selection, e -> {
			String input = inputDialog("Add tag", "Tag name:", "");
			if (input != null) {
				try {
					store.addTag((Task) card.getData(), input);
					refresh();
				} catch (IOException ex) {
					error(ex.getMessage());
				}
			}
		});

		List<String> cardTags = store.tagsForTask((Task) card.getData());
		if (!cardTags.isEmpty()) {
			MenuItem removeTag = new MenuItem(menu, SWT.CASCADE);
			removeTag.setText("Remove tag");
			Menu removeMenu = new Menu(menu);
			removeTag.setMenu(removeMenu);
			for (String slug : cardTags) {
				MenuItem item = new MenuItem(removeMenu, SWT.PUSH);
				item.setText("#" + slug);
				item.addListener(SWT.Selection, e -> {
					try {
						store.removeTag((Task) card.getData(), slug);
						refresh();
					} catch (IOException ex) {
						error(ex.getMessage());
					}
				});
			}
		}

		new MenuItem(menu, SWT.SEPARATOR);

		MenuItem delete = new MenuItem(menu, SWT.PUSH);
		delete.setText("Delete");
		delete.addListener(SWT.Selection, e -> {
			Task t = (Task) card.getData();
			MessageBox confirm = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO);
			confirm.setText("Delete task");
			confirm.setMessage("Delete task \"" + t.name() + "\"?");
			if (confirm.open() == SWT.YES) {
				try {
					store.deleteTask(t);
					refreshBoard();
				} catch (IOException ex) {
					error(ex.getMessage());
				}
			}
		});
	}

	private void openEditor(Task task) {
		TaskEditor editor = new TaskEditor(shell, store, task);
		if (editor.open() != TaskEditor.Result.CANCELLED) {
			refresh();
		}
	}

	// --- small helpers -----------------------------------------------------

	private static String tagsLabel(String name, List<String> tags) {
		if (tags.isEmpty()) {
			return name;
		}
		StringBuilder sb = new StringBuilder(name).append('\n');
		for (String tag : tags) {
			sb.append('#').append(tag).append(' ');
		}
		return sb.toString().strip();
	}

	private boolean requireStore() {
		if (store == null) {
			error("Open a folder first (File ▸ Open Folder…).");
			return false;
		}
		return true;
	}

	private boolean requireBoard() {
		if (!requireStore()) {
			return false;
		}
		if (currentBoard == null) {
			error("Create or select a board first.");
			return false;
		}
		return true;
	}

	private void button(Composite parent, String text, org.eclipse.swt.widgets.Listener onClick) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(text);
		b.addListener(SWT.Selection, onClick);
	}

	private void error(String message) {
		MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
		box.setText("Error");
		box.setMessage(message == null ? "Unknown error" : message);
		box.open();
	}

	/** Minimal single-line input dialog; returns trimmed text, or null if cancelled. */
	private String inputDialog(String title, String prompt, String initial) {
		Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dialog.setText(title);
		dialog.setLayout(new GridLayout(2, false));

		new Label(dialog, SWT.NONE).setText(prompt);
		Text text = new Text(dialog, SWT.BORDER | SWT.SINGLE);
		text.setText(initial);
		GridData textGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		textGd.widthHint = 260;
		text.setLayoutData(textGd);

		Composite buttons = new Composite(dialog, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
		buttons.setLayout(new GridLayout(2, true));

		Button ok = new Button(buttons, SWT.PUSH);
		ok.setText("OK");
		Button cancel = new Button(buttons, SWT.PUSH);
		cancel.setText("Cancel");
		dialog.setDefaultButton(ok);

		String[] result = { null };
		ok.addListener(SWT.Selection, e -> {
			String v = text.getText().trim();
			if (!v.isEmpty()) {
				result[0] = v;
			}
			dialog.close();
		});
		cancel.addListener(SWT.Selection, e -> dialog.close());

		dialog.pack();
		dialog.open();
		while (!dialog.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result[0];
	}
}
