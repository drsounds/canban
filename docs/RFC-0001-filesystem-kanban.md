# RFC 0001 — The Filesystem Kanban Specification (FSK)

- **Status:** Draft
- **Version:** 0.1
- **Author:** Alexander Forselius
- **Created:** 2026-06-30
- **License:** MIT (same as the reference implementation, *Canban*)

## Abstract

This document specifies **FSK**, an open, plain-filesystem representation of a
Kanban board. A board, its swimlanes, its columns, its cards, and its tags are
expressed entirely as directories, Markdown files, and symbolic links. No
database, binary format, network service, or vendor account is required. Any
program — a GUI, a shell script, a CI job, or an autonomous AI agent — can read
and write a conformant board using nothing but ordinary file operations.

The goal is to make project state a **first-class, version-controllable artifact**
that lives beside the work it describes, so that the full history of a board is
captured by the same version-control system that tracks the code.

## 1. Terminology

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are
to be interpreted as described in RFC 2119.

- **Root** — the `kanban/` directory that contains an FSK dataset.
- **Board** — a directory directly under the root representing one Kanban board.
- **Lane** — a directory under a board representing a horizontal swimlane (row).
- **Status** — a directory under a lane representing a workflow column.
- **Task / Card** — a Markdown (`.md`) file inside a status directory.
- **Tag** — a label applied to a task, materialized as a symbolic link.
- **Cell** — the intersection of one lane and one status (a status directory).

## 2. Motivation

### 2.1 An open standard, not a product

Conventional Kanban tools store boards inside a proprietary service or database.
The data model is opaque, export is lossy, and access requires the vendor's API.
FSK inverts this: **the board is the directory tree**. The specification is the
product; any conforming tool is interchangeable. Migrating between tools is a
`cp -r`, not an import/export migration project.

### 2.2 No reliance on third-party services

An FSK board is local files. It works offline, has no rate limits, no
subscription, no account, and no privacy surface beyond the filesystem it sits
on. Teams retain complete custody of their planning data.

### 2.3 Boards that live in repositories

Because a board is just files, it can be committed to the same repository as the
code it plans. Open-source projects can ship their roadmap **in the repo**, where
it is forkable, reviewable through pull requests, and branchable alongside
feature work. A feature branch can carry both the implementation *and* the board
changes that describe it.

### 2.4 Audit trails for free

The single most valuable property of FSK is that **state changes are file
changes**, and file changes are exactly what version control records. Moving a
card from `Doing` to `Done` is a file move; the commit that contains it carries
an author, a timestamp, a message, and (optionally) a cryptographic signature.
The result is a tamper-evident, exportable, independently verifiable audit log —
something SaaS activity feeds rarely provide.

## 3. Specification

### 3.1 Directory layout

A conformant dataset is rooted at a directory named `kanban/`:

```
kanban/
├── <board>/
│   ├── .lanes
│   ├── .statuses
│   └── <lane>/
│       └── <status>/
│           └── <task>.md
└── tag/
    └── <tag-slug>/
        └── <link>.md -> ../../<board>/<lane>/<status>/<task>.md
```

- Each directory directly under the root is a **board**, **except** the reserved
  name `tag` (Section 3.5). Implementations **MUST NOT** treat `tag` as a board.
- Each directory directly under a board (other than dot-files) is a **lane**.
- Each directory directly under a lane is a **status**.
- Each regular file ending in `.md` inside a status directory is a **task**.

Names of boards, lanes, and statuses are taken verbatim from their directory
names and **MAY** contain spaces. Implementations **MUST** treat a name
beginning with `.` as metadata, not as a board/lane/status.

### 3.2 Task files

A task's identity is its file name without the `.md` extension. A task's content
is the **entire file body**, which **SHOULD** be Markdown. Implementations
**MUST NOT** require any particular structure within the body; a task **MAY** be
empty.

Task files **MAY** begin with a YAML front-matter block (delimited by `---`) to
carry structured metadata (assignee, due date, checklist, priority). Front-matter
is **OPTIONAL**; tools that do not understand it **MUST** preserve it unchanged.

A task is considered **done** when it resides in a status directory designated as
a completion column (commonly `Done`). FSK does not hard-code which columns mean
"done"; that is a project convention. Front-matter **MAY** additionally carry a
boolean such as `done: true` or a `completed:` timestamp (Section 4.2).

### 3.3 Lane and status ordering

Directory listings are unordered, but columns and rows have meaning. A board
**MAY** contain two optional plain-text files:

- `.lanes` — lane names, one per line, in display order.
- `.statuses` — status names, one per line, in display order.

When present, implementations **MUST** render rows/columns in that order, append
any on-disk names not listed (alphabetically), and ignore listed names that no
longer exist on disk. When absent, ordering **MUST** fall back to
case-insensitive alphabetical. These files are advisory: deleting them never
loses tasks.

### 3.4 Operations

| Operation        | Filesystem effect                                              |
|------------------|---------------------------------------------------------------|
| Create task      | Write `kanban/<b>/<l>/<s>/<name>.md`                           |
| Edit task        | Overwrite the file body                                        |
| Rename task      | Rename the file within its cell                                |
| Move task        | Move the file to another lane/status directory                 |
| Delete task      | Remove the file                                                |
| Create lane      | `mkdir` the lane and mirror existing status subdirectories     |
| Create status    | `mkdir` that status under every lane                           |
| Tag / untag task | Create / remove a symbolic link under `kanban/tag/<slug>/`     |

A **move MUST be a rename of the file**, not a copy-and-delete, so that version
control records it as a single coherent change and history is preserved.

### 3.5 Tags

Tags live under the reserved root directory `kanban/tag/`. Each tag is a
directory whose name is a **slug**, and it contains one symbolic link per tagged
task:

```
kanban/tag/urgent/Build login.md -> ../../Web/Backlog/Doing/Build login.md
```

- A tag link **MUST** be a **relative** symbolic link whose target resolves to
  the task file. Relative targets keep the entire `kanban/` tree relocatable and
  clonable.
- A slug **SHOULD** match `^[a-z0-9]+(-[a-z0-9]+)*$`. Implementations producing
  tags from free text **SHOULD** lower-case, replace runs of non-alphanumerics
  with `-`, and trim leading/trailing `-`.
- The link's own file name is informational; membership is determined by the
  link **target**, not the link name. Implementations **MUST** resolve targets to
  decide which tasks a tag contains.
- When a task is moved, renamed, or deleted, implementations **MUST** update or
  remove the corresponding tag links so that no dangling links remain. A tag
  directory that becomes empty **SHOULD** be removed.

Tags are an index, never the source of truth: deleting the entire `kanban/tag/`
tree loses only labels, never tasks.

### 3.6 Conformance

A **reader** is conformant if it interprets the layout in this section. A
**writer** is conformant if every mutation it performs leaves a dataset that a
conformant reader interprets identically, and if it preserves data (front-matter,
unknown files) it does not understand. Implementations **MUST** tolerate
concurrent external edits to the tree between operations.

## 4. Version control integration and audit trails

FSK is designed to be stored in git (or any VCS). This section is normative for
tools that integrate with git and informative otherwise.

### 4.1 The board's history is the project's history

Each commit that touches `kanban/` is an entry in the board's audit log:

```
$ git log --oneline -- kanban/
a1b9f2c Move "Build login" Doing -> Done
4e7c0d1 Add task "Build login" to Web/Backlog/To Do
9bd33a8 Tag "Build login" #urgent
```

Because moves are renames, `git log --follow -- <path>` reconstructs the entire
journey of a single card across lanes and statuses, with author and timestamp at
every step. This is a **complete, verifiable, exportable audit trail** with no
extra infrastructure. Signed commits (`git commit -S`) make it tamper-evident.

### 4.2 Syncing "done" with commits

Marking a task done **SHOULD** be expressed as a commit, so completion is
attributable and reviewable:

- Move the card into the completion column (`git mv .../Doing/X.md .../Done/X.md`),
  and/or set `done: true` / `completed: <ISO-8601>` in front-matter.
- Commit it with a message that references the work, ideally the same commit (or
  PR) that delivers the change:

  ```
  git commit -m "Implement login form

  Closes board task Web/Build login (-> Done)."
  ```

This couples *task completion* to *delivered work*: a reviewer reading history
sees the card move to `Done` in the same change set that implemented it. A simple
`commit-msg` hook or CI check **MAY** enforce that any commit moving a card to a
completion column references an issue, PR, or test result — turning the audit
trail into an enforceable policy rather than a convention.

### 4.3 Branching and review

Board changes ride along feature branches. A pull request can show, in one diff,
the code change and the corresponding card moving to `Done`. Merging the branch
merges the board update; reverting the branch reverts the plan. Planning becomes
subject to the same review discipline as code.

## 5. FSK and AI agents

A board made of files is, structurally, a **shared task queue with rich
context** — which is exactly what an autonomous agent needs. FSK reframes the
human↔AI interaction from *prompting* to *delegating through the board*.

### 5.1 From prompting to filing tasks

Instead of typing an instruction into a chat window, a human (or another agent)
**creates a task file**. The card body is the brief; tags and front-matter are
structured parameters; the column is the lifecycle state. The agent's job is to
move cards from a "ready" column to "done" by actually doing the work.

### 5.2 A claim/execute/commit loop

A conformant agent **SHOULD** operate as follows:

1. **Discover** ready work: list tasks in an agreed column (e.g. `To Do`),
   optionally filtered by a tag such as `#agent`.
2. **Claim** a task: move it to `Doing` (and/or set `assignee: <agent-id>`),
   then commit. The commit is an atomic, race-safe claim — two agents cannot both
   win the same rename, and the claim is visible to everyone.
3. **Execute** the task: read the card body for intent, perform the work (write
   code, run tools, produce artifacts).
4. **Record**: append results, links, or logs to the task body; reference
   produced commits or PRs.
5. **Complete**: move the card to `Done` and commit, per Section 4.2. If blocked,
   move it to a `Blocked` column with an explanation instead.

Because every step is a commit, the agent's reasoning trail and the human's
review live in the **same history**, side by side. There is no separate
agent-action log to trust: the git log *is* the log.

### 5.3 Why FSK suits agents specifically

- **No API to integrate.** The agent already has file and git tools; the board
  needs no SDK, auth, or rate-limit handling.
- **Atomic, auditable claims.** Rename + commit gives safe coordination and an
  attributable record for every autonomous action.
- **Context is co-located.** The repository, the code, and the tasks are one
  checkout, so the agent plans and acts in the same workspace.
- **Human-in-the-loop by default.** Humans review board commits exactly like code
  commits; a card can be moved back out of `Done` to reject an agent's work.
- **Bounded autonomy.** Conventions (which columns/tags an agent may touch,
  enforced by hooks or CI) constrain what agents may do without bespoke
  permission systems.

## 6. Security and integrity considerations

- **Symbolic links.** Tag links are relative and **MUST** resolve within the
  `kanban/` tree. Readers **SHOULD** reject or ignore links whose resolved target
  escapes the root, to avoid path-traversal when a dataset is untrusted.
- **Path safety.** Task/board/lane/status names become path components.
  Implementations **MUST** reject names containing path separators or the
  components `.` and `..`, and **SHOULD** reject control characters.
- **Untrusted datasets.** Treat a cloned `kanban/` like any untrusted repo
  content: do not execute task bodies, and sandbox agents that act on them.
- **Tamper evidence.** Use signed commits where the audit trail is relied upon
  for compliance; the integrity guarantee is the VCS's, not FSK's.

## 7. Filesystem portability

Symbolic links require a capable filesystem. On platforms where links are
unavailable or unprivileged (e.g. some Windows configurations), an
implementation **MAY** degrade gracefully (omit the tag index) but **MUST NOT**
lose tasks. Names are case-sensitive on most Unix filesystems; datasets
**SHOULD** avoid relying on case to distinguish two boards/lanes/statuses so they
remain portable to case-insensitive filesystems.

## 8. Reference implementation

*Canban* (this repository) is the reference implementation: a Java + SWT desktop
client. It demonstrates boards/lanes/statuses as directories, tasks as Markdown
files, `.lanes`/`.statuses` ordering, and the `kanban/tag/` symlink index with
automatic relinking on move/rename/delete.

## Appendix A. Example tree

```
kanban/
├── Web/
│   ├── .lanes            # Backlog\nIn sprint
│   ├── .statuses         # To Do\nDoing\nDone
│   ├── Backlog/
│   │   ├── To Do/
│   │   │   └── Design login.md
│   │   ├── Doing/
│   │   └── Done/
│   └── In sprint/
│       ├── To Do/
│       ├── Doing/
│       │   └── Build login.md
│       └── Done/
│           └── Set up CI.md
└── tag/
    ├── urgent/
    │   └── Build login.md -> ../../Web/In sprint/Doing/Build login.md
    └── agent/
        └── Set up CI.md  -> ../../Web/In sprint/Done/Set up CI.md
```
