# UI phases — the `ClassicGUI` build-out

Our own work: an original-style view on the unchanged engine. Tracks *remaining* work — how the
shipped code works lives in
[`src/net/sf/freecol/client/gui/classic/README.md`](../src/net/sf/freecol/client/gui/classic/README.md).

**Expert player drives:** screen priority, validating each rebuilt screen vs. the real game, sign-off
per slice. Reference screenshots live in git-excluded `screenshots/` (never commit; `git add` explicit
paths). Sub-states matter as much as idle layouts (open menus, in-progress drag, mid-dialog).

## Open questions for the expert

What is blocked on him rather than on more code: **reference material only he has**, a **guess we
shipped** that needs sign-off, or a **call we cannot make from the repo**. The detail lives in the
phase item linked; this list is just the queue to take to him.

| # | Question | Blocks | Cost of guessing wrong |
|---|---|---|---|
| **Q1** | **Reference shots of Col1's Foreign Affairs report** — the last report the original has that we have not built. Needed: which **backdrop**, the **layout**, **which fields** per nation, the **key**, **sub-states**, and shots with **several rivals met** (see the phase item for why each). | [Phase 2 §1](#remaining) | Cannot start — every one of those is unguessable from the repo. |
| **Q2** | **The Colony Advisor's paging keys**, and whether it wants pages beyond Sons of Liberty / Military Garrison (population / production were mentioned but are in neither shot). | [Phase 2 §1](#remaining) | Shipped and wrong: keys nobody would press. |
| **Q3** | **The popup look** — `ClassicDialog`'s metrics and green-on-wood palette are read off screenshots by eye, not measured from the art. `WOODPAN2.PIK` is unused; should it be? | [Phase 3 §2](#remaining-1) | Every popup in the game inherits the error. |
| **Q4** | **Reference shots for the choice-list and text-input popups**, whose original look we have never seen. | [Phase 3 §1](#remaining-1) | Cannot start; they stay Swing stopgaps. |
| **Q5** | **The colony screen's fixed building ground-slots** — the original places buildings at set positions; we flow-layout them. | [Phase 2 §3](#remaining) | Rework of the biggest remaining slice. |

**Q1 and Q4 are the ones to ask first** — each is pure reference material (shots), cheap for him to
answer, and each unblocks a seam that cannot be started without it. Q2/Q3/Q5 are sign-off on work
that already runs, so they can wait for a review pass.

*Resolved:* whether the four Col1-less reports belong in a faithful classic UI — **no**, deferred to
[optional reports](optional-reports.md) as later additions of our own (2026-07-17).

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

1. **Foreign affairs — the last report the original actually has.** ⬜ The other four unbuilt
   `showReport*Panel` seams (labour / education / history / requirements) are **decided out of this
   phase**: Col1 has no screen for them, so they are deferred to
   [optional reports](optional-reports.md) as later additions of our own. Foreign affairs is the one
   Col1 *does* have, and the only unbuilt report with original art unaccounted for — so it finishes
   the report set. Blocked on reference shots ([Q1](#open-questions-for-the-expert)).
   - **What it shows** (FreeCol's `ReportForeignAffairPanel`, per live European player): coat of
     arms, country, **stance**, number of colonies, number of units, military strength, naval
     strength, gold, continental congress, tax, Sons of Liberty. Which of those Col1 surfaces is a
     question for the shots — showing *more* than the original is the same information-availability
     call we just settled for the other four, so do not carry fields over unexamined.
   - ⚠️ **`nationSummary` is a blocking server round-trip, not an async callback** — despite what
     this plan said before. `InGameController.nationSummary(player)` reads a cache and, on a miss,
     does `askServer().nationSummary(…)` and waits. So it must **never** be called from
     `paintComponent` (network I/O on every repaint) and must not run on the EDT. Fetch every
     player's summary **once, off the EDT, when the report opens**, stash the results, and paint from
     the stash — a different shape from every report so far, which paints straight off the model.
   - **What we need from the expert before writing it** ([Q1](#open-questions-for-the-expert)) — all
     of it unguessable from the repo:
     1. **Which backdrop.** The spare `REPORT9` duplicates `REPORT1`'s native-scout art, which reads
        wrong for a European-powers screen. So either foreign affairs owns one of the backdrops we
        have **double-booked** — `REPORT6` (Colony + Military) or `REPORT7` (Naval + Cargo), meaning
        one of those pairings is our mistake — or `REPORT9` really is it. One shot settles it, and it
        may correct an existing assignment.
     2. **The layout**, to read constants straight off into the 320×200 canvas: are nations a table
        of rows, a panel each, or one nation per page? Where does the coat of arms sit?
     3. **Which fields the original shows**, of FreeCol's stance / colonies / units / military /
        naval / gold / congress / tax / SoL — plus their captions, for the isolated
        `classic.report.*` block (EN + DE). Carrying all nine over unexamined would re-introduce the
        very information-availability question the other four reports were just deferred over.
     4. **The accelerator key.** Our mapping leaves **F2 and F4 unclaimed** (F1 Religious, F3 Colony,
        F5 Indian, F6 Congress, F7 Military, F8 Naval, F9 Trade, shift-F1 Cargo, shift-F2
        Exploration, shift-F4 Production), so foreign affairs plausibly owns one of them — worth
        confirming rather than assuming.
     5. **Sub-states, not just the idle screen** — does it page per nation, as the Colony Advisor
        pages its column sets?
     6. **Shots with several rivals met, at war and at peace.** The Congress and Indian row loops
        have only ever run their empty-state branch (README verification caveat); populated shots
        stop foreign affairs joining them.
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
