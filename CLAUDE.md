# Project-specific notes for Claude Code

## Toolchain: scoop, not conda

This project's build (`ant`, `java`) does **not** use conda, despite the global
instruction to prefer conda-run tools. On this machine `ant`/`java` resolve via
PATH to **scoop** installs:

- `ant` → `scoop install main/ant` (bucket `main`, added by default)
- `java`/JDK → `scoop install java/openjdk` (bucket `java`, needs
  `scoop bucket add java` first)

If scoop itself isn't installed yet (fresh machine), bootstrap it first —
official installer, PowerShell:

```powershell
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex
```

Then:

```powershell
scoop bucket add java
scoop install java/openjdk main/ant
```

Verify with `Get-Command ant` / `Get-Command java` — they should point at
`~/scoop/apps/ant/current/bin/ant.bat` and `~/scoop/apps/openjdk/current/bin/java.exe`.
Building with `ant package` (or plain `ant`) and running with
`java -jar FreeCol.jar` works directly with these, no conda env involved.

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
