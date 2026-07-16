# UI phases — the `ClassicGUI` build-out

Our own work: an original-style view on the unchanged engine. Tracks *remaining* work — how the
shipped code works lives in
[`src/net/sf/freecol/client/gui/classic/README.md`](../src/net/sf/freecol/client/gui/classic/README.md).

**Expert player drives:** screen priority, validating each rebuilt screen vs. the real game, sign-off
per slice. Reference screenshots live in git-excluded `screenshots/` (never commit; `git add` explicit
paths). Sub-states matter as much as idle layouts (open menus, in-progress drag, mid-dialog).

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

1. **Remaining reports.** The rest of the `showReport*Panel` seams still no-op — add them over their
   `REPORTn.PIK` backdrops: **foreign affairs** (needs the async `nationSummary` fetch, so more than
   a static paint), plus labour/education/history/requirements. `REPORT9`, a duplicate of the
   `REPORT1` native-scout art, is still unassigned. Extend `ClassicReportPanel` (unit rosters:
   `ClassicReportRosterPanel`).
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

## Phase 3 — dialogs & polish ⬜

`modalConfirmDialog`/`modalChoiceDialog`/`modalInputDialog`, negotiation, end-turn. The stopgaps are
already wired as **plain Java dialogs** (the colony-founding confirm/choice/name seams, the Europe
recruit/train/purchase choosers). **Reskin them to the original wood-framed look** —
`WOODPANL.PIK`/`WOODPAN2.PIK` frame + colonist portrait + green-on-wood text — **once, as a shared
classic-dialog component** all popups route through (rather than per-popup). The same component then
reskins the event popups sharing these seams (meeting natives, king's demands, learn-skill, lost-city
rumours, disembark/high-seas confirms). Negotiation (`DiplomacyPanel`) and the end-turn "units still
active" prompt are the larger bespoke ones.

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
