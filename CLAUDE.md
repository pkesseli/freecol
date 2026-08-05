# Project-specific notes for Claude Code

## Toolchain: scoop, not conda

This project's build (`ant`, `java`) does **not** use conda, despite the global
instruction to prefer conda-run tools. On this machine `ant`/`java` resolve via
PATH to **scoop** installs:

- `ant` → `scoop install main/ant` (bucket `main`, added by default)
- `java`/JDK → `scoop install java/openjdk21` (bucket `java`, needs
  `scoop bucket add java` first)

**Pin `openjdk21`, not the bare `openjdk` alias.** The unpinned `openjdk`
manifest tracks the latest release (26 as of 2026-08) and `build.xml` still
compiles `java.applet.Applet`-based code
(`src/net/sf/freecol/client/gui/video/VideoComponent.java`) — that class was
removed by JDK 26 (deprecated for removal since JDK 17's JEP 398). `build.xml`
targets `source`/`target=11`, but that only limits accepted *syntax*; it does
not restore APIs the compiling JDK itself dropped, so the build fails on any
JDK where `java.applet` is gone. 21 (LTS) still has it and is confirmed
working; treat this as the floor for the compiling JDK.

If scoop itself isn't installed yet (fresh machine), bootstrap it first —
official installer, PowerShell:

```powershell
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex
```

Then:

```powershell
scoop bucket add java
scoop install java/openjdk21 main/ant
```

Verify with `Get-Command ant` / `Get-Command java` — they should point at
`~/scoop/apps/ant/current/bin/ant.bat` and `~/scoop/apps/openjdk21/current/bin/java.exe`.
Building with `ant package` (or plain `ant`) and running with
`java -jar FreeCol.jar` works directly with these, no conda env involved.

**PATH/`JAVA_HOME` precedence gotchas (Windows):** Machine-level `PATH`
entries always win over User-level ones (e.g. a separately-installed
system-wide JDK will shadow scoop's on bare `java`/`javac`), but plain env
vars like `JAVA_HOME` resolve the other way — User overrides Machine on
conflict. `ant.bat` reads `JAVA_HOME` itself (falling back to `PATH` only if
unset), so as long as User `JAVA_HOME` points at scoop's `openjdk21`, `ant`
uses the right JVM regardless of what bare `java` resolves to. Also: a
PowerShell/cmd session only sees `PATH`/env changes made *after* it started
(inherited at process launch, not live) — after running the scoop bootstrap,
open a **new** terminal (and if VS Code's process tree predates the install,
a new *window* still inherits the stale environment from its parent; you need
the root `Code.exe` to fully exit and relaunch).

Two conda envs named `colonization` and `colonization-patch` exist on this
machine but are **not** wired into this project's build: `colonization` is a
bare Python 3.11 env with no Java at all; `colonization-patch` has a
conda-forge `openjdk` 25.0.2 but no `ant`, and nothing in `build.xml` or any
doc references either env. They appear to be a stale/incomplete attempt to
follow the global conda convention here — don't assume they're load-bearing,
and don't be surprised if a fresh machine has neither.

## Reference screenshots are local-only, never committed

`screenshots/` is **not** covered by the tracked `.gitignore` — it's excluded
only via the local, per-clone `.git/info/exclude` (see that file's comment:
"Expert reference screenshots (local only, never commit)"). Since
`.git/info/exclude` doesn't travel with the repo, a fresh clone on another
machine has neither the exclusion rule nor the screenshots themselves (they
were never part of git history). When setting up a new machine: copy
`screenshots/` over manually, and re-add the exclusion locally (deliberately
*not* in the tracked `.gitignore` — this is a fork of upstream FreeCol, and a
personal local-only exclusion doesn't belong in a file that could flow back
upstream):

```powershell
Add-Content .git/info/exclude "screenshots/"
```

## Classic UI live-testing

See `src/net/sf/freecol/client/gui/classic/README.md` ("Testing live") for the
full non-interactive Windows harness (launch detached, poll `MainWindowHandle`,
foreground via minimize/restore bounce, screenshot via `CopyFromScreen`).
Quick version:

```powershell
ant package
java -Xmx2G -jar FreeCol.jar --fast --no-intro --classic
```

`--classic` is required — without it you get FreeCol's normal isometric UI, not
the reskinned Classic UI. `--fast` resumes the last save (only starts a fresh
game on a profile with no saves). Kill the process as soon as verification is
done; it steals foreground focus while running.

## Stale "Documents" registry means FreeCol resolves to the wrong, dead OneDrive tree

On this machine, `HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders`
(and the legacy `...\Shell Folders`) `Personal` value still points at
`D:\Users\pkesseli\OneDrive\Dokumente` — a leftover from before the Windows
profile was migrated/renamed to `C:\Users\Pascal`. `%USERPROFILE%` is correct
(`C:\Users\Pascal`), but that env var is **not** what FreeCol uses:
`FreeColDirectories.getUserDefaultDirectory()`
(`src/net/sf/freecol/common/io/FreeColDirectories.java`) calls
`FileSystemView.getFileSystemView().getDefaultDirectory()`, which on Windows
resolves via that registry key, not the env var. So FreeCol's home lands in
`D:\Users\pkesseli\OneDrive\Dokumente\freecol` — a second, orphaned
OneDrive-synced copy of the save data (same filenames/sizes as the real
`C:\Users\Pascal\OneDrive\Dokumente\freecol` tree, since both were cloud
placeholders from the same account) that no longer has a live OneDrive client
servicing it, so its files can't be hydrated/recalled on read.

**Symptom:** `--fast` doesn't start a fresh game even on an apparently-new
profile — it finds the (wrong-tree) last autosave and fails to load it with
`error.couldNotLoad` ("An error occurred while trying to load the game from
file D:\Users\pkesseli\...").

**Workaround (no system changes):** point FreeCol at the real tree explicitly,
bypassing the shell lookup:

```powershell
java -Xmx2G -cp "build;jars/*" net.sf.freecol.FreeCol --classic --fast --no-intro `
  --user-data-directory "C:\Users\Pascal\OneDrive\Dokumente\freecol" `
  --user-config-directory "C:\Users\Pascal\OneDrive\Dokumente\freecol" `
  --user-cache-directory "C:\Users\Pascal\OneDrive\Dokumente\freecol"
```

The real fix (not done — touches the registry, affects every app that reads
the Documents folder, not just FreeCol) is correcting the `Personal` value
under both registry keys above to `C:\Users\Pascal\OneDrive\Dokumente`.
