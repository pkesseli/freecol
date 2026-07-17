# UI phases — the `ClassicGUI` build-out

Our own work: an original-style view on the unchanged engine. Tracks *remaining* work — how the
shipped code works lives in
[`src/net/sf/freecol/client/gui/classic/README.md`](../src/net/sf/freecol/client/gui/classic/README.md).

**Expert player drives:** screen priority, validating each rebuilt screen vs. the real game, sign-off
per slice. Reference screenshots live in git-excluded `screenshots/` (never commit; `git add` explicit
paths). Sub-states matter as much as idle layouts (open menus, in-progress drag, mid-dialog).

## Open questions for the expert

What is blocked on his ruling rather than on more code. Each is a **guess we shipped** or a **scope
call we cannot make from the repo** — the detail lives in the phase item linked; this list is just the
queue to take to him.

| # | Question | Blocks | Cost of guessing wrong |
|---|---|---|---|
| **Q1** | **May the classic UI surface information the original never aggregated?** The unbuilt reports add no Col1-absent *concepts* — it is an information-availability call, not a rules one. Two sub-calls: **Requirements** is an advisor Col1 never gave, and **History** would log two §C concepts. | [Phase 2 §1](#remaining) | Mild — conveniences, not rules breaks. The cost is handing the player aggregate views Col1 made you track by hand. |
| **Q2** | **The Colony Advisor's paging keys**, and whether it wants pages beyond Sons of Liberty / Military Garrison (population / production were mentioned but are in neither shot). | [Phase 2 §1](#remaining) | Shipped and wrong: keys nobody would press. |
| **Q3** | **The popup look** — `ClassicDialog`'s metrics and green-on-wood palette are read off screenshots by eye, not measured from the art. `WOODPAN2.PIK` is unused; should it be? | [Phase 3 §2](#remaining-1) | Every popup in the game inherits the error. |
| **Q4** | **Reference shots for the choice-list and text-input popups**, whose original look we have never seen. | [Phase 3 §1](#remaining-1) | Cannot start; they stay Swing stopgaps. |
| **Q5** | **The colony screen's fixed building ground-slots** — the original places buildings at set positions; we flow-layout them. | [Phase 2 §3](#remaining) | Rework of the biggest remaining slice. |

Q1 and Q4 are the cheap ones to ask first: Q1 can *delete* work, and Q4 unblocks a whole seam.

## Phase 0 — scaffold & launch ✅

`ClassicGUI extends GUI`, `--classic` flag, selector, auto-launch lobby stopgap.
(README "What this is".)

## Phase 1 — the map ✅

Rectangular 48px grid: `TERRAIN.SS` terrain, `PHYS0.SS` feature overlays + coast/beach feathering,
`ICONS.SS` unit/goods/settlement sprites, edge-pan, minimap, clicks/keys driving the real
`InGameController`, and **turn controls** (Enter/Space/W/B, the 1994 key scheme).
(README "`ClassicMapViewer`".) Frame maps + RE writeups live in `ClassicTileArt`,
`aliases.properties`, and the README.

## Phase 2 — HUD & core screens 🔨

**Shipped:** map HUD (reused `InGameMenuBar` + `ClassicInfoPanel`); **colony screen**
(`ClassicColonyPanel`, `COLONY.PIK`/`BUILDING.SS`/`WOODTILE.SS`); **Europe screen**
(`ClassicEuropePanel`, `EUROPE.PIK`, recruit/purchase/train via the real controllers); **ten advisor
reports** over a shared `ClassicReportPanel` frame (unit rosters share a further
`ClassicReportRosterPanel`) — Colony (`REPORT6.PIK`, F3), Military (`REPORT6`, F7), Naval
(`REPORT7`, F8), Trade (`REPORT5`, F9), Religious (`REPORT2`, F1), Production (`REPORT4`, shift F4),
Congress (`REPORT3`, F6), Exploration (`REPORT8`, shift F2), Cargo (`REPORT7`, shift F1), Indian
(`REPORT1`, F5); **order buttons** (reuse `FreeColAction` `BUTTON_IMAGE` art), **in-panel minimap**,
**unit portrait**, **seam-free `WOODPANL.PIK` wood chrome**, Col1's light-on-dark menu bar, and
localized report captions. (README "Phase 2 HUD" / "Colony screen" / "Europe screen" / "Report
screens".)

> **Key enabler:** `ClassicGUI.updateActions()` (called on `reconnectGUI` + every `changeView`)
> refreshes the reused actions' enabled state — the classic HUD has no `Canvas`, so without it the
> Europe/report menu items and the order buttons stay stuck disabled.

### Remaining

1. **Remaining reports — a scope question first, code second. See
   [Q1](#open-questions-for-the-expert).** Five `showReport*Panel` seams still no-op: foreign
   affairs, labour, education, history, requirements. Building one means extending
   `ClassicReportPanel` over its `REPORTn.PIK` backdrop (unit rosters: `ClassicReportRosterPanel`) —
   but *whether four of them belong in a faithful classic UI at all* is a design call, not a coding
   one.
   - **The original has no screen for four of them.** Nine report backdrops ship (`REPORT1`–
     `REPORT9`); the ten reports we already show cover eight, and `REPORT9` duplicates `REPORT1`'s
     native-scout art. So at most **one** unbuilt report has original art behind it — plausibly
     foreign affairs, which Col1 does have.
   - **But they are not FreeCol *inventions*** — the distinction that matters, and the one that makes
     this the expert's call ([Q1](#open-questions-for-the-expert)) rather than ours. The concepts are
     all Col1: **education** is a core original mechanic (the classic ruleset ships
     schoolhouse/college/university and `allowStudentSelection`, itself listed Col1-faithful in
     [rules fidelity](rules-fidelity.md) table A); **labour** is a census of unit types you own;
     **history** logs `HistoryEvent`s that are almost all Col1 (discover New World, meet nation, city
     of gold, found colony, founding father, declare independence, war/peace). So §C ("FreeCol
     additions to keep out") does **not** settle this: the divergence is *informational* — aggregate
     views the original made you track by hand — not conceptual, and it does not touch the rules axis.
   - **Two genuine §C snags if we do build them.** **Requirements** (menu label "Requirements"; its
     Javadoc calls it "the Advanced Colony Report") is not a view at all — it is an **optimisation
     coach**. Its own strings: *"%colony% has a %expert% currently working as %expertWork%, while a
     %nonExpert% is working as %nonExpertWork%. Production would be greater if the colonists swapped
     jobs"*, *"%location% would benefit from exploration"*, *"All requirements are met"*. Col1 never
     advised you — noticing your Master Carpenter was stuck farming *was* the game. This is the one
     item that adds a **capability** rather than an information view, so it is the strongest keep-out
     candidate of the five even if the other three are waved through. And **history** would log
     `ABANDON_COLONY` and `DESTROY_NATION`, which map onto the two additions §C says to keep out.
   - **Foreign affairs** is the one to build regardless of the ruling — Col1 has it, and it needs the
     async `nationSummary` fetch, so it is more than a static paint.
   - The Colony Advisor **pages** through its column sets as the original does (Sons of Liberty /
     Military Garrison — the two the expert's shots document); the paging *keys* are a guess awaiting
     sign-off.
   - **Colony rows are clickable** — a click in the Colony/Production/Religious reports jumps to that
     colony's screen (README "Clickable colony rows").
   - The Congress and Indian row loops have only ever run their empty-state branch — see the README's
     verification caveat.
2. **HUD polish — otherwise done.** Remaining: only the colony/Europe screens' own few hard-coded
   captions. (The menu **dropdown popups** are still default Swing — folded into the Phase-3 reskin.)
3. **Screen-interaction polish** — the bulk of what's left. *Colony:* validate the provisional
   `BUILDING.SS` frame map, place buildings at the original fixed ground-slots (vs. our flow layout),
   and add interaction (drag colonists between tiles/buildings, build queue, cargo). *Europe:*
   drag-to-board / load-cargo / set-sail. *Both:* per-nation building/flag tints.

## Phase 3 — dialogs & polish 🔨

**Shipped:** the shared **`ClassicDialog`** component — the wood-framed popup (`ClassicWood` grain +
illustration + green-on-wood text + option plates) that the plan called for building *once*, with
every popup routing through it. Live-verified 2026-07-17 (README "Popups"). Over it:

- **The message channel** — `showModelMessages` + `showReportTurnPanel`. Until now the classic UI
  **silently discarded every in-game notice**, and the root cause was worse than the missing
  overrides: `invokeNowOrLater`/`invokeNowOrWait` are no-ops in the base `GUI` and were never
  overridden, so the controllers' display tasks never ran at all. Both seams now mirror `SwingGUI`.
  The original has no batched turn report, so notices page one at a time rather than list.
- **`modalConfirmDialog`** — off `JOptionPane` onto the shared frame. The `Unit`/`FreeColObject`
  overloads in `GUI` are `final` and delegate here, so every confirm in the game lands on it.

### Remaining

1. **The other dialog seams — blocked on [Q4](#open-questions-for-the-expert).** `modalChoiceDialog`
   and `modalInputDialog` are still plain Swing stopgaps: they need a list widget and a text field,
   and we have never seen the original's look for either. Then the event popups sharing these seams
   (meeting natives, king's demands, learn-skill, lost-city rumours, disembark). Negotiation
   (`DiplomacyPanel`) and the end-turn "units still active" prompt are the larger bespoke ones.
2. **The popup look is a guess — see [Q3](#open-questions-for-the-expert).** `ClassicDialog`'s
   metrics and green-on-wood palette are read off the original's screenshots by eye, not measured
   from the art. Every popup inherits whatever is wrong, so this wants sign-off before the seams
   above multiply it. `WOODPAN2.PIK` is not used yet.
3. **The menu dropdowns** are still default Swing (deferred here from Phase 2's HUD polish).
4. **Audit the other no-op seams.** The dispatch bug is the general hazard of a no-op base class: an
   un-overridden seam fails silently and invisibly, and dispatch seams strand *other* seams. Worth a
   sweep for `GUI` methods the classic UI depends on transitively.

## Phase 4 — pre-game & setup screens ⬜

No classic lobby yet — `showStartGamePanel` auto-launches single-player as a stopgap (why `--fast`
drops straight into the map, no nation/difficulty choice). Each setup screen is a `showXPanel`-style
override + its `.PIK` background: **nation select** (`NATIONS.PIK`), **difficulty** (`DIFFICUL.PIK`),
**customise** (`CUSTOMIZ.PIK`); the **declare-independence** (`DECLARAT`/`DECOIND.PIK`) and **king**
(`KINGLSS1/2.PIK`) art sits adjacent (some overlaps Phase 3 event dialogs). Lower urgency than the
in-game screens — the auto-launch keeps the game reachable.

## Explicitly out of scope (deliberate)

The intro/new-game **cinematics** and closing sequence. `--no-intro` skips them; only the static
`OPENING.PIK` title renders. The animated frames (`LEVN0001–0010`, `CLOS-BKG`/`CCBKGD`) are extracted
but nothing plays them — revisit only if the expert deems the original intro essential (a fresh scope
decision, akin to A6 audio).
