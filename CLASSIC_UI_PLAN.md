# Classic UI for FreeCol — implementation plan (index)

Goal: a faithful original-Colonization experience — a primitive, original-style **UI** *on top of the
classic **rules*** — built as an alternative view on the existing engine. An expert Colonization
player iterates with us per slice.

Two independent axes (keep them separate):
- **UI** — our `ClassicGUI` work → [UI phases](classic_ui_plan/ui-phases.md).
- **Rules** — supplied by FreeCol's data-driven **`classic` ruleset** (`data/rules/classic/`), loaded
  at game start. Faithfulness is therefore mostly a *ruleset* question, and **we implement none of
  it** → [rules fidelity](classic_ui_plan/rules-fidelity.md).

> **This plan tracks *remaining* work.** How the *shipped* classic UI works — the `GUI` seam
> contracts, `ClassicGUI`/`ClassicMapViewer`, the rectangular projection, controller/key wiring, the
> minimap model, the asset RE frame maps, and the live-test harness — lives next to the code in
> [`src/net/sf/freecol/client/gui/classic/README.md`](src/net/sf/freecol/client/gui/classic/README.md).
> Completed-slice detail and the asset-decoder history are in that README + the git log.

## Chapters & status (2026-07-16)

### [UI phases](classic_ui_plan/ui-phases.md) — our `ClassicGUI` build-out, phase by phase

Phase 0 scaffold ✅ · Phase 1 map ✅ · **Phase 2 HUD & core screens 🔨** — the map HUD, colony and
Europe screens, and ten advisor reports ship; HUD polish is done. Left: the remaining reports
(foreign affairs, labour, education, history, requirements) and **screen interaction** (drag
colonists, build queue, cargo, set-sail) — the bulk of the phase. Then Phase 3 dialogs ⬜ (plain-Java
stopgaps are wired; they need one shared wood-framed component) · Phase 4 pre-game/setup ⬜
(auto-launch stopgap keeps the game reachable, so this is lower urgency). Cinematics are deliberately
out of scope.

### [Assets](classic_ui_plan/assets.md) — bring-your-own original install (DECIDED)

Ship no original art; decode it from the user's own legal install, falling back to FreeCol's free art.
A0/A1/A3 (locate, decode, load) ✅ · **A2 alias curation 🔨** — grows with each new screen; the bulk of
the asset work · A5 runtime extraction ⬜ · A6 audio ⬜ (gates nothing).

### [Rules fidelity](classic_ui_plan/rules-fidelity.md) — where `classic` still departs from Col1

The audit of ruleset-vs-original, plus the R0–R3 roadmap. **R0–R3 all ⬜, and this track does not
block the UI** — skip this chapter unless you are working on rules.

### [Architecture](classic_ui_plan/architecture.md) — why a second UI is cheap, and the decisions

The `GUI` seam, the Swing/additive-changes decisions, the reuse map, and the
`data/mods/classic_ui/` false-friend warning. Settled background — read once.

## How to run & test (classic UI)

Build once after a code change, then launch with `--classic`. Run from the repo root so `data/` is
found. `ant compile` refreshes `build/`, so launch straight from there (no repackage):

```powershell
ant compile
java -Xmx2G -cp "build;jars/*" net.sf.freecol.FreeCol --classic --fast --no-intro
```

- `;` is the Windows classpath separator; `jars/*` pulls in all dependency jars.
- `--fast --no-intro` auto-starts a single-player game with **no GUI clicks** — the fastest way to
  the in-game view. It starts **at sea** (ship on an ocean patch).
- The `options.xml NoSuchFileException` / "Special options unavailable" on first launch is a benign
  pre-existing warning. Confirm **0 SEVERE** in `FreeCol.log`.
- The non-interactive live-test harness recipe (drive/screenshot the window from PowerShell) is in
  the package README, "Testing live".
