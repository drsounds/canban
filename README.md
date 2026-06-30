# Canban

A filesystem-backed Kanban board built with Java + SWT. There is no database,
no proprietary file format and no cloud account — you just **Open a folder** and
Canban creates a `.kanban/` directory inside it. Everything you see in the UI is
plain folders and Markdown files you can edit, `grep`, diff, and commit to git.

The on-disk layout is a small open specification — see
[`docs/RFC-0001-filesystem-kanban.md`](docs/RFC-0001-filesystem-kanban.md).
Canban is just one client of it.

## Why a board made of files?

- **Open standard, no lock-in.** The board *is* the directory tree. Any tool,
  script, or editor can read and write it; Canban is replaceable.
- **No third-party service.** Your roadmap doesn't live in someone else's SaaS.
  Nothing to host, no account, no API limits, works offline.
- **Lives in the repository.** Drop `.kanban/` next to your code. The board is
  versioned, branched, and reviewed exactly like the project it describes.
- **Audit trail for free.** Because moving a card is moving a file, `git log`
  becomes a complete, signed, timestamped history of who changed what, when, and
  why — far stronger than a SaaS activity feed you can't export or verify.
- **AI-agent friendly.** A board of files is a shared task queue an agent can
  read and act on directly: pick up a card, do the work, move the card, commit.
  Instead of *prompting* an assistant, you *file a task* and the agent runs it.

## Layout on disk

```
.kanban/
├── <board>/
│   ├── .lanes        # optional: lane (row) order, one name per line
│   ├── .statuses     # optional: status (column) order, one name per line
│   └── <lane>/
│       └── <status>/
│           └── <task name>.md       # the card body (Markdown)
└── tag/
    └── <tag-slug>/
        └── tasks/
            └── <task>.md -> ../../../<board>/<lane>/<status>/<task>.md   # relative symlink
```

- **Boards** are top-level directories under `.kanban/`.
- **Lanes** are the rows of a board (swimlanes).
- **Statuses** are the columns (e.g. To Do / Doing / Done).
- **Tasks** are `.md` files; the file contents are the card body.
- **Tags** live in the reserved `.kanban/tag/` directory: each tag is a folder
  whose `tasks/` sub-folder holds *relative* symbolic links pointing back at the
  tagged task files. The links are kept in sync automatically as tasks move, get
  renamed, or are deleted. Keeping links in `tasks/` leaves the tag folder free
  to be extended with per-tag metadata later.

The two dot-files keep a meaningful row/column order. If they are missing, order
falls back to alphabetical.

## Using it

1. **File ▸ Open Folder…** — pick any folder. If it has no `.kanban/` directory,
   Canban offers to create one.
2. **New Board…** creates a board pre-filled with a `Backlog` lane and
   `To Do` / `Doing` / `Done` columns so it is usable immediately.
3. **New Lane… / New Status…** add rows and columns.
4. **+ Add** in any cell (or **New Task…**) creates a card and opens the editor.
5. **Double-click** a card to edit its name, Markdown body, and tags.
6. **Drag** a card into another cell to move it — or right-click ▸ *Move to*.
7. Right-click a card for **Edit / Move to / Add tag / Remove tag / Delete**.
8. **Tag** filter in the toolbar shows only cards carrying the selected tag.

Every action is written to the filesystem immediately.

## Build & run

```bash
./run.sh
```

The script compiles `src/` against the SWT GTK jar in your local Maven
repository and launches `se.canban.app.Main`. SWT is platform-specific; this
project is wired for **Linux GTK x86_64**. To run elsewhere, swap the SWT jar in
`run.sh` and `.classpath` for the matching platform fragment.

Inside Eclipse you can just run `se.canban.app.Main` as a Java Application — the
SWT jar is already on the project classpath (`.classpath`).

## Releases

Pushing a tag like `v1.2.3` triggers the GitHub Actions workflow in
[`.github/workflows/release.yml`](.github/workflows/release.yml), which uses
`jpackage` to build native installers (`.deb`, `.dmg` for Intel and Apple
Silicon, `.msi`) and attaches them to a GitHub Release.

## License

MIT — see [`LICENSE`](LICENSE).
