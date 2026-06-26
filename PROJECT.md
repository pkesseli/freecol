# FreeCol — "House Rules" personal edition

A personal, single-user fork of [FreeCol](https://www.freecol.org/) (the open-source
reimplementation of Sid Meier's Colonization) that lifts several of the original game's
arbitrary limits and adds quality-of-life features.

## Why FreeCol (and not the original binary)

We first tried to patch the original 1994 MicroProse `VICEROY.EXE` directly. That toolchain
and its findings are preserved in the sibling repo **`../colonization-patch-backup`**. The
binary turned out to be a 16-bit real-mode MZ executable with a custom overlay loader (~73% of
the code is overlaid and not cleanly disassemblable), and real-mode segment ambiguity made even
*locating* the unit-cap check impractical — let alone adding new UI, data structures, or
networking. FreeCol is open-source Java with a documented ruleset/mod system, so every one of
our requirements is dramatically higher-odds and lower-effort there.

## Base

- Upstream: <https://github.com/FreeCol/freecol> — branch `master` (shallow clone)
- Work branch: **`house-rules`**

## Goals / requirements

Carried over from the original `requirements.md` (in the patch backup), re-framed for FreeCol:

| # | Requirement | FreeCol notes |
|---|-------------|---------------|
| 1 | Lift the arbitrary per-player unit cap (original game capped at 255) | Likely a config/ruleset value or absent — verify and relax. |
| 2 | Fortified colonies (stockade/fort/fortress) may drop below population 3 and eventually be abandoned | Colony population / abandonment logic change. |
| 3 | Allow players to label (name) ships | Unit naming — model + UI dialog; may partly exist. |
| 4 | Allow players to label (name) wagon trains | Same mechanism as #3. |
| 5 | Extend the game beyond the year 1799 | Turn/date end-condition is a ruleset/config value. |
| 6 | Online multiplayer | **Already provided by FreeCol** (client/server) — verify/use, don't build. |

## Status

- [x] FreeCol checked out; `house-rules` branch created off upstream `master`
- [ ] Build FreeCol successfully (toolchain: Apache Ant; JDK present is 25)
- [ ] Triage each requirement against FreeCol's model / ruleset (`data/rules/...`, `src/net/sf/freecol/...`)
- [ ] Implement requirements 1–5; verify 6

## Build

See `build.xml` (Apache Ant). Build/run notes will be filled into `CLAUDE.md` once verified.
