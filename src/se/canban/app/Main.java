package se.canban.app;

import org.eclipse.swt.widgets.Display;

import se.canban.app.ui.KanbanWindow;

/**
 * Entry point for Qanban, a filesystem-backed Kanban board built on SWT.
 */
public final class Main {

	private Main() {
	}

	public static void main(String[] args) {
		Display display = new Display();
		try {
			new KanbanWindow(display).open();
		} finally {
			display.dispose();
		}
	}
}
