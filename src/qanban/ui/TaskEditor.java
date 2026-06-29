package qanban.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import qanban.model.KanbanStore;
import qanban.model.Task;

/**
 * Modal editor for a single task: rename the card and edit its Markdown body.
 * Saving writes straight back to the {@code .md} file; deleting removes it.
 */
final class TaskEditor {

	enum Result {
		SAVED, DELETED, CANCELLED
	}

	private final Shell shell;
	private final KanbanStore store;
	private Task task;
	private Result result = Result.CANCELLED;

	TaskEditor(Shell parent, KanbanStore store, Task task) {
		this.store = store;
		this.task = task;
		this.shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		this.shell.setText("Edit task – " + task.name());
		this.shell.setLayout(new GridLayout(2, false));
		this.shell.setSize(560, 460);
		build();
	}

	/** Opens the dialog and blocks until it is closed. */
	Result open() {
		shell.open();
		var display = shell.getDisplay();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result;
	}

	Task task() {
		return task;
	}

	private void build() {
		new Label(shell, SWT.NONE).setText("Name:");
		Text nameText = new Text(shell, SWT.BORDER | SWT.SINGLE);
		nameText.setText(task.name());
		nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Label bodyLabel = new Label(shell, SWT.NONE);
		bodyLabel.setText("Content (Markdown):");
		bodyLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 2, 1));

		Text body = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		try {
			body.setText(store.readTask(task));
		} catch (Exception e) {
			body.setText("");
		}

		Composite buttons = new Composite(shell, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		buttons.setLayout(new GridLayout(3, false));

		Button delete = new Button(buttons, SWT.PUSH);
		delete.setText("Delete");
		delete.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

		Button cancel = new Button(buttons, SWT.PUSH);
		cancel.setText("Cancel");

		Button save = new Button(buttons, SWT.PUSH);
		save.setText("Save");
		shell.setDefaultButton(save);

		save.addListener(SWT.Selection, e -> {
			String newName = nameText.getText().trim();
			if (newName.isEmpty()) {
				error("Name cannot be empty.");
				return;
			}
			try {
				task = store.renameTask(task, newName);
				store.writeTask(task, body.getText());
				result = Result.SAVED;
				shell.close();
			} catch (Exception ex) {
				error(ex.getMessage());
			}
		});

		cancel.addListener(SWT.Selection, e -> {
			result = Result.CANCELLED;
			shell.close();
		});

		delete.addListener(SWT.Selection, e -> {
			MessageBox confirm = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO);
			confirm.setText("Delete task");
			confirm.setMessage("Delete task \"" + task.name() + "\"? This removes the file.");
			if (confirm.open() == SWT.YES) {
				try {
					store.deleteTask(task);
					result = Result.DELETED;
					shell.close();
				} catch (Exception ex) {
					error(ex.getMessage());
				}
			}
		});
	}

	private void error(String message) {
		MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
		box.setText("Error");
		box.setMessage(message == null ? "Unknown error" : message);
		box.open();
	}
}
