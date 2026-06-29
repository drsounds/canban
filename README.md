# Canban

A filesystem-backed Kanban board built with Java + SWT. There is no database,
no file format and no cloud account — you just **Open a folder** and Canban
creates a `kanban/` directory inside it. Everything you see in the UI is plain
folders and Markdown files you can edit, grep, or commit to git.

## Layout on disk

```
kanban/
└── <board>/
    ├── .lanes        # optional: lane (row) order, one name per line
    ├── .statuses     # optional: status (column) order, one name per line
    └── <lane>/
        └── <status>/
            └── <task name>.md   # the card body (Markdown)
```

- **Boards** are top-level directories under `kanban/`.
- **Lanes** are the rows of a board (swimlanes).
- **Statuses** are the columns (e.g. To Do / Doing / Done).
- **Tasks** are `.md` files; the file contents are the card body.

The two dot-files keep a meaningful row/column order. If they are missing,
order falls back to alphabetical.

## Using it

1. **File ▸ Open Folder…** — pick any folder. If it has no `kanban/`
   directory, Canban offers to create one.
2. **New Board…** creates a board pre-filled with a `Backlog` lane and
   `To Do` / `Doing` / `Done` columns so it is usable immediately.
3. **New Lane… / New Status…** add rows and columns.
4. **+ Add** in any cell (or **New Task…**) creates a card and opens the editor.
5. **Double-click** a card to edit its name and Markdown body.
6. **Drag** a card into another cell to move it — or right-click ▸ *Move to*.
7. Right-click a card for **Edit / Move to / Delete**.

Every action is written to the filesystem immediately.

## Build & run

```bash
./run.sh
```

The script compiles `src/` against the SWT GTK jar in your local Maven
repository and launches `se.canban.app.Main`. SWT is platform-specific; this project is
wired for **Linux GTK x86_64**. To run elsewhere, swap the SWT jar in `run.sh`
and `.classpath` for the matching platform fragment.

Inside Eclipse you can just run `se.canban.app.Main` as a Java Application — the SWT jar
is already on the project classpath (`.classpath`).
