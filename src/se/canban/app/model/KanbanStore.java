package se.canban.app.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Filesystem-backed Kanban data store.
 *
 * <p>Everything lives under a single {@code .kanban/} directory and maps directly
 * onto folders and files:
 * <pre>
 *   .kanban/&lt;board&gt;/&lt;lane&gt;/&lt;status&gt;/&lt;task&gt;.md
 * </pre>
 *
 * <p>Boards, lanes and statuses are directories; tasks are Markdown files whose
 * content is the card body. Column (status) and row (lane) ordering is kept in
 * two small dot-files per board ({@code .statuses} and {@code .lanes}) so the
 * board reads in a meaningful order rather than alphabetically. Those files are
 * plain text, one name per line, and are optional &mdash; if absent, ordering
 * falls back to case-insensitive alphabetical.
 *
 * <p>Tags live in a reserved sibling directory {@code .kanban/tag/}. Each tag is
 * a folder named after a slug and holds <em>relative</em> symbolic links back to
 * the tagged task files:
 * <pre>
 *   .kanban/tag/&lt;tag-slug&gt;/&lt;task&gt;.md -&gt; ../../&lt;board&gt;/&lt;lane&gt;/&lt;status&gt;/&lt;task&gt;.md
 * </pre>
 * The links are kept in sync as tasks are moved, renamed and deleted.
 */
public final class KanbanStore {

	/** Name of the dataset root directory; hidden ("dot") for safety. */
	public static final String DIRECTORY_NAME = ".kanban";

	private static final String STATUS_ORDER = ".statuses";
	private static final String LANE_ORDER = ".lanes";

	/** Reserved top-level directory holding tag folders of symbolic links. */
	private static final String TAG_DIR = "tag";

	private final Path kanbanDir;

	public KanbanStore(Path kanbanDir) {
		this.kanbanDir = kanbanDir;
	}

	public Path kanbanDir() {
		return kanbanDir;
	}

	/**
	 * Resolves (and creates if missing) the {@code .kanban/} directory inside the
	 * given parent folder.
	 */
	public static Path ensureKanbanDir(Path parent) throws IOException {
		Path k = parent.resolve(DIRECTORY_NAME);
		Files.createDirectories(k);
		return k;
	}

	// --- listing -----------------------------------------------------------

	public List<String> boards() {
		return childDirs(kanbanDir).stream().filter(n -> !n.equals(TAG_DIR)).toList();
	}

	public List<String> lanes(String board) {
		Path b = kanbanDir.resolve(board);
		return ordered(b.resolve(LANE_ORDER), childDirs(b));
	}

	/** Statuses for a board: the union of column folders found across its lanes. */
	public List<String> statuses(String board) {
		TreeSet<String> union = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (String lane : childDirs(kanbanDir.resolve(board))) {
			union.addAll(childDirs(kanbanDir.resolve(board).resolve(lane)));
		}
		return ordered(kanbanDir.resolve(board).resolve(STATUS_ORDER), new ArrayList<>(union));
	}

	public List<String> tasks(String board, String lane, String status) {
		Path cell = cellDir(board, lane, status);
		if (!Files.isDirectory(cell)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(cell)) {
			return s.filter(Files::isRegularFile)
					.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".md"))
					.map(n -> n.substring(0, n.length() - 3))
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// --- creation ----------------------------------------------------------

	/**
	 * Creates a board pre-populated with a usable default structure (one
	 * {@code Backlog} lane and {@code To Do} / {@code Doing} / {@code Done}
	 * columns) so it is immediately usable.
	 */
	public void createBoard(String name) throws IOException {
		if (name.equals(TAG_DIR)) {
			throw new IOException("'" + TAG_DIR + "' is reserved for tags; pick another board name.");
		}
		Path board = kanbanDir.resolve(name);
		if (Files.exists(board)) {
			throw new IOException("A board named '" + name + "' already exists.");
		}
		Files.createDirectories(board);
		List<String> statuses = List.of("To Do", "Doing", "Done");
		writeOrder(board.resolve(STATUS_ORDER), statuses);
		writeOrder(board.resolve(LANE_ORDER), List.of("Backlog"));
		for (String st : statuses) {
			Files.createDirectories(board.resolve("Backlog").resolve(st));
		}
	}

	public void createLane(String board, String lane) throws IOException {
		Path lanePath = kanbanDir.resolve(board).resolve(lane);
		if (Files.exists(lanePath)) {
			throw new IOException("A lane named '" + lane + "' already exists.");
		}
		Files.createDirectories(lanePath);
		for (String st : statuses(board)) {
			Files.createDirectories(lanePath.resolve(st));
		}
		appendOrder(kanbanDir.resolve(board).resolve(LANE_ORDER), lane);
	}

	public void createStatus(String board, String status) throws IOException {
		List<String> lanes = lanes(board);
		if (lanes.isEmpty()) {
			throw new IOException("Create a lane before adding a status column.");
		}
		for (String lane : lanes) {
			Files.createDirectories(kanbanDir.resolve(board).resolve(lane).resolve(status));
		}
		appendOrder(kanbanDir.resolve(board).resolve(STATUS_ORDER), status);
	}

	// --- task CRUD ---------------------------------------------------------

	public String readTask(Task task) throws IOException {
		Path f = taskFile(task);
		return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
	}

	public void writeTask(Task task, String content) throws IOException {
		Path f = taskFile(task);
		Files.createDirectories(f.getParent());
		Files.writeString(f, content, StandardCharsets.UTF_8);
	}

	public Task createTask(String board, String lane, String status, String name, String content)
			throws IOException {
		Task task = new Task(board, lane, status, name);
		Path f = taskFile(task);
		if (Files.exists(f)) {
			throw new IOException("A task named '" + name + "' already exists here.");
		}
		Files.createDirectories(f.getParent());
		Files.writeString(f, content, StandardCharsets.UTF_8);
		return task;
	}

	public void deleteTask(Task task) throws IOException {
		List<Path> links = linksPointingTo(taskFile(task));
		Files.deleteIfExists(taskFile(task));
		for (Path link : links) {
			Files.deleteIfExists(link);
			deleteIfEmpty(link.getParent());
		}
	}

	public Task renameTask(Task task, String newName) throws IOException {
		if (newName.equals(task.name())) {
			return task;
		}
		Task target = new Task(task.board(), task.lane(), task.status(), newName);
		Path dst = taskFile(target);
		if (Files.exists(dst)) {
			throw new IOException("A task named '" + newName + "' already exists here.");
		}
		List<Path> links = linksPointingTo(taskFile(task));
		Files.move(taskFile(task), dst);
		rebindLinks(links, dst);
		return target;
	}

	/** Moves a task to a different lane and/or status within the same board. */
	public Task moveTask(Task task, String newLane, String newStatus) throws IOException {
		if (task.lane().equals(newLane) && task.status().equals(newStatus)) {
			return task;
		}
		Task target = new Task(task.board(), newLane, newStatus, task.name());
		Path src = taskFile(task);
		Path dst = taskFile(target);
		if (Files.exists(dst)) {
			throw new IOException("A task named '" + task.name() + "' already exists in the target column.");
		}
		List<Path> links = linksPointingTo(src);
		Files.createDirectories(dst.getParent());
		try {
			Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException atomicUnsupported) {
			Files.move(src, dst);
		}
		rebindLinks(links, dst);
		return target;
	}

	// --- tags --------------------------------------------------------------

	/** All known tag slugs (the folder names under {@code .kanban/tag/}). */
	public List<String> tags() {
		return childDirs(kanbanDir.resolve(TAG_DIR));
	}

	/** Tag slugs currently applied to the given task. */
	public List<String> tagsForTask(Task task) {
		Path canonical = canonical(taskFile(task));
		List<String> result = new ArrayList<>();
		for (String slug : tags()) {
			if (!linksInDirTo(kanbanDir.resolve(TAG_DIR).resolve(slug), canonical).isEmpty()) {
				result.add(slug);
			}
		}
		return result;
	}

	/**
	 * Tags a task by creating a relative symbolic link under
	 * {@code .kanban/tag/<slug>/}. No-op if the task already carries the tag.
	 *
	 * @return the slug that was applied
	 */
	public String addTag(Task task, String rawTag) throws IOException {
		String slug = slugify(rawTag);
		if (slug.isEmpty()) {
			throw new IOException("A tag must contain at least one letter or digit.");
		}
		Path target = taskFile(task);
		if (!Files.exists(target)) {
			throw new IOException("Save the task before tagging it.");
		}
		if (tagsForTask(task).contains(slug)) {
			return slug;
		}
		Path dir = kanbanDir.resolve(TAG_DIR).resolve(slug);
		Files.createDirectories(dir);
		Path link = uniqueLink(dir, task.name());
		Files.createSymbolicLink(link, dir.relativize(target));
		return slug;
	}

	/** Removes a tag from a task (and the tag folder if it becomes empty). */
	public void removeTag(Task task, String slug) throws IOException {
		Path dir = kanbanDir.resolve(TAG_DIR).resolve(slug);
		for (Path link : linksInDirTo(dir, canonical(taskFile(task)))) {
			Files.delete(link);
		}
		deleteIfEmpty(dir);
	}

	/** Normalises arbitrary text into a filesystem-safe tag slug. */
	public static String slugify(String raw) {
		String slug = raw.trim().toLowerCase(Locale.ROOT);
		slug = slug.replaceAll("[^a-z0-9]+", "-");
		slug = slug.replaceAll("^-+|-+$", "");
		return slug;
	}

	private List<Path> linksPointingTo(Path taskFile) {
		Path canonical = canonical(taskFile);
		List<Path> out = new ArrayList<>();
		for (String slug : tags()) {
			out.addAll(linksInDirTo(kanbanDir.resolve(TAG_DIR).resolve(slug), canonical));
		}
		return out;
	}

	private void rebindLinks(List<Path> links, Path newTarget) throws IOException {
		for (Path link : links) {
			Path dir = link.getParent();
			Files.deleteIfExists(link);
			Files.createSymbolicLink(link, dir.relativize(newTarget));
		}
	}

	private static List<Path> linksInDirTo(Path dir, Path canonicalTarget) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(dir)) {
			return s.filter(Files::isSymbolicLink)
					.filter(link -> canonical(link).equals(canonicalTarget))
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Real path if it resolves (following links), else a normalised absolute path. */
	private static Path canonical(Path p) {
		try {
			return p.toRealPath();
		} catch (IOException e) {
			return p.toAbsolutePath().normalize();
		}
	}

	private static Path uniqueLink(Path dir, String baseName) {
		Path candidate = dir.resolve(baseName + ".md");
		for (int i = 2; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); i++) {
			candidate = dir.resolve(baseName + "-" + i + ".md");
		}
		return candidate;
	}

	private static void deleteIfEmpty(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> s = Files.list(dir)) {
			if (s.findAny().isEmpty()) {
				Files.delete(dir);
			}
		}
	}

	// --- paths -------------------------------------------------------------

	private Path cellDir(String board, String lane, String status) {
		return kanbanDir.resolve(board).resolve(lane).resolve(status);
	}

	public Path taskFile(Task task) {
		return cellDir(task.board(), task.lane(), task.status()).resolve(task.name() + ".md");
	}

	// --- helpers -----------------------------------------------------------

	private static List<String> childDirs(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(dir)) {
			return s.filter(Files::isDirectory)
					.map(p -> p.getFileName().toString())
					.filter(n -> !n.startsWith("."))
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Returns {@code actual} reordered to match the names listed in the order
	 * file, with any unlisted names appended alphabetically. Names in the order
	 * file that no longer exist on disk are dropped.
	 */
	private static List<String> ordered(Path orderFile, List<String> actual) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		if (Files.exists(orderFile)) {
			try {
				for (String line : Files.readAllLines(orderFile, StandardCharsets.UTF_8)) {
					String name = line.trim();
					if (!name.isEmpty() && actual.contains(name)) {
						result.add(name);
					}
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		List<String> rest = new ArrayList<>(actual);
		rest.removeAll(result);
		result.addAll(rest);
		return new ArrayList<>(result);
	}

	private static void writeOrder(Path orderFile, List<String> names) throws IOException {
		Files.createDirectories(orderFile.getParent());
		Files.write(orderFile, names, StandardCharsets.UTF_8);
	}

	private static void appendOrder(Path orderFile, String name) throws IOException {
		List<String> names = new ArrayList<>();
		if (Files.exists(orderFile)) {
			names.addAll(Files.readAllLines(orderFile, StandardCharsets.UTF_8));
		}
		if (!names.contains(name)) {
			names.add(name);
		}
		writeOrder(orderFile, names);
	}
}
