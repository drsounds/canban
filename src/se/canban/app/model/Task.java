package se.canban.app.model;

/**
 * Identifies a single task card by its location in the kanban tree.
 *
 * <p>A task lives at {@code kanban/<board>/<lane>/<status>/<name>.md}. The
 * {@code name} never includes the {@code .md} extension.
 */
public record Task(String board, String lane, String status, String name) {
}
