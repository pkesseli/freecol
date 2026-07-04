# Classic UI — original-Colonization asset conversion

One-time, run-once tooling that converts **your own** legally-owned original
*Sid Meier's Colonization* (1994) art into a local FreeCol mod pack for the
classic UI. See `CLASSIC_UI_PLAN.md` → "Asset strategy" for the rationale.

**Nothing copyrighted is committed.** The tooling reads your install and writes
a **git-ignored** `data/mods/classic_original/` pack; FreeCol loads it like any
other mod. FreeCol stays pure Java/Ant at build and run time — this is a
separate asset-prep step that shells out to Python once.

## Prerequisite

- **Python 3 on PATH, with `venv` + `pip`.** That is the only thing you provide.
  - Windows (python.org / GOG setups): `venv` + `pip` are bundled — nothing else.
  - Some Linux distros split these out: `sudo apt install python3-venv python3-pip`.
- Network access during the run (to fetch `mpskit` and `pip install Pillow`).

Everything else — a virtualenv, [Pillow](https://python-pillow.org/), and
[`mpskit`](https://github.com/institution/mpskit) (the MADSPACK/`.SS`/`.PIK`
decoder, AGPLv3) — is provisioned automatically into `build/classic-assets/`.

## Usage

```
ant classic-assets -Dcol.install="C:\Program Files (x86)\GOG Galaxy\Games\Colonization\MPS\COLONIZE"
```

`col.install` must point at the directory holding the original `*.SS` and
`*.PIK` files (the GOG/Steam "Classic" release keeps them under `MPS\COLONIZE\`).

Output: `data/mods/classic_original/` — `mod.xml`, `resources.properties`, and
`resources/images/{pik,ss}/*.png`.

## How it works

1. Copies the read-only `*.SS`/`*.PIK` into a writable staging dir (mpskit writes
   each PNG next to its input).
2. Creates a venv and installs Pillow; downloads `mpskit` at a pinned commit.
3. Runs `mpskit ss unpack` / `pik unpack` → PNGs (`.PIK` palettes are embedded;
   `.SS` frames export as `NAME.000.png`, …).
4. `build_pack.py` assembles the pack and writes `resources.properties` exposing
   every frame under `image.classic_original.*`.

### Windows/Linux note

The venv interpreter lives at `Scripts\python.exe` (Windows) vs `bin/python`
(POSIX) — Python's own convention. Ant's `<exec>` has no shell and does not run
the venv `activate` script, so the target invokes the interpreter by its
absolute, OS-specific path, chosen by a `<condition>` in `build.xml`. Same
reason there is no shell glob expansion: `<apply>` feeds the file list instead.

## A2 — mapping to FreeCol keys

The generated keys are a stable scaffold namespace. To actually skin the UI,
map FreeCol resource keys onto them in a committed `aliases.properties` beside
this file, e.g.:

```
image.background.FreeColPanel=resource:image.classic_original.pik.WOODPANL
```

`build_pack.py` appends `aliases.properties` (if present) to the generated
`resources.properties`, so re-running the conversion never clobbers that work.

## Refresh / troubleshooting

- To force a clean re-fetch of `mpskit`, delete `build/classic-assets/`.
- If Ant's `<get>` cannot follow the GitHub redirect, download
  `https://github.com/institution/mpskit/archive/<ref>.tar.gz` manually into
  `build/classic-assets/mpskit.tar.gz` and re-run.
