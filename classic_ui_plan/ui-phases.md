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
| **Q2** | **The per-view Colony Advisor paging key for >9 colonies** — the *between-views* cycle is now confirmed (F6 again; see Resolved below), but the shots' two-page military view (9 colonies, then a 10th on its own page) doesn't show what pages *within* one view. Left un-implemented (rows past the ninth are just clipped) pending this. | [Colony Advisor](../src/net/sf/freecol/client/gui/classic/README.md#colony-advisor-classicreportcolonypanel-f6--the-paged-report) | Shipped and wrong: a key nobody would press, for a case only a long game hits. |
| **Q3** | **The popup look** — `ClassicDialog`'s metrics and green-on-wood palette are read off screenshots by eye, not measured from the art. `WOODPAN2.PIK` is unused; should it be? | [Phase 3 §2](#remaining-1) | Every popup in the game inherits the error. |
| **Q4** | **Reference shots for the choice-list and text-input popups**, whose original look we have never seen. | [Phase 3 §1](#remaining-1) | Cannot start; they stay Swing stopgaps. |
| **Q5** | **The colony screen's fixed building ground-slots** — the original places buildings at set positions; we flow-layout them. | [Phase 2 §3](#remaining) | Rework of the biggest remaining slice. |
| **Q6** | **Four shipped reports — Military, Production, Exploration, Cargo — have no confirmed Col1 counterpart** at all (see Resolved below); "Military Garrison" may just be the Colony Advisor's own paging, not a standalone report. **Concretely blocking now:** remapping Naval to its confirmed key F7 collides with Military, which already sits there and which this plan does not touch unilaterally — both menu items show "F7" today but only one responds (verified live). Should any of the four be reworked, re-keyed, or removed? | Key-scheme remap (`ClassicGUI.remapClassicReportAccelerators`); README "Report screens" | Guessing wrong either reworks a report the original never had, or leaves a live, user-visible key collision unresolved. |

**Q4 is the one to ask first** — pure reference material (shots), cheap for him to answer, and
unblocks a seam that cannot be started without it. Q2/Q3/Q5/Q6 are sign-off on work that already runs
(or a call on work already flagged), so they can wait for a review pass.

*Resolved* (detail in the README, not repeated here):
- Whether Education/History/Requirements/Labour belong in a faithful classic UI — no for the first
  three (deferred to [optional reports](optional-reports.md), 2026-07-17); **Labour reversed** —
  the original does have it, at F4 (2026-07-24).
- **Q1, Foreign Affairs** (2026-07-24) — key F8, backdrop `REPORT8.PIK` (also double-booked with
  Exploration → feeds Q6), full field/layout spec, all powers listed regardless of contact, own
  nation last, no sub-states. Built as `ClassicReportForeignAffairPanel`.
- **Q2, the *between-views* Colony Advisor paging key** (2026-07-24) — confirmed **F6 again**, not
  Left/Right/Space. The *within-a-view* key for >9 colonies remains open (table above).
- **The observed F-key scheme** (2026-07-24) — remapped in `ClassicGUI` at runtime, in-memory only;
  see README "Report screens".

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
(`ClassicEuropePanel`, `EUROPE.PIK`, recruit/purchase/train via the real controllers); **all twelve
advisor reports** — the original's ten plus Labour, a reversed decision (see "Remaining" below) — over
a shared `ClassicReportPanel` frame (unit rosters share a further `ClassicReportRosterPanel`): Colony
(`REPORT6.PIK`), Military (`REPORT6`), Naval (`REPORT7`), Trade (`REPORT5`), Religious (`REPORT2`),
Production (`REPORT4`), Congress (`REPORT3`), Exploration (`REPORT8`), Cargo (`REPORT7`), Indian
(`REPORT1`), **Labour** (`REPORT4`, new), **Foreign Affairs** (`REPORT8`, new — the last report the
original actually has, finishing the set). Keys now follow the *observed* original F-key scheme
(F2 Religious, F3 Congress, F4 Labour, F5 Trade, F6 Colony, F7 Naval, F8 Foreign Affairs, F9 Indian),
remapped at runtime in `ClassicGUI` rather than FreeCol's own arbitrary layout — see
["Report screens"](../src/net/sf/freecol/client/gui/classic/README.md#report-screens-classicreportpanel--concrete-reports)
in the README for the mechanism and the one confirmed, still-open key collision (F7: Naval vs.
Military — [Q6](#open-questions-for-the-expert)). Also: **order buttons** (reuse `FreeColAction`
`BUTTON_IMAGE` art), **in-panel minimap**, **unit portrait**, **seam-free `WOODPANL.PIK` wood chrome**,
Col1's light-on-dark menu bar, and localized report captions. (README "Phase 2 HUD" / "Colony screen" /
"Europe screen" / "Report screens".)

> **Key enabler:** `ClassicGUI.updateActions()` (called on `reconnectGUI` + every `changeView`)
> refreshes the reused actions' enabled state — the classic HUD has no `Canvas`, so without it the
> Europe/report menu items and the order buttons stay stuck disabled.

The report set (Foreign Affairs, Labour, and the key-scheme remap) is now **done** — full detail lives
in the README's "Report screens" section, not here. What's left:

### Remaining

1. **Colony Advisor: the within-a-view paging key for >9 colonies** — open, see
   [Q2](#open-questions-for-the-expert). (The between-views cycle, F6-again, is done.) Colony rows
   stay clickable (Colony/Production/Religious → jump to that colony's screen); the Congress and
   Indian row loops still haven't been exercised with real data — see the README's verification
   caveat.
2. **F10 "Kolonisationspunkte" (score breakdown) — newly discovered scope, not yet built.** ⬜ FreeCol's
   matching seam, `showHighScoresPanel(String, List<HighScore>)`, differs in shape from every other
   report (takes its data as arguments) and has **no accelerator at all** in FreeCol's own scheme, so
   F10 is uncontested. Lower priority; build when there's a natural opening.
3. **HUD polish — otherwise done.** Remaining: only the colony/Europe screens' own few hard-coded
   captions. (The menu **dropdown popups** are still default Swing — folded into the Phase-3 reskin.)
4. **Screen-interaction polish** — the bulk of what's left, and now the only large item in this phase.
   *Colony:* validate the provisional `BUILDING.SS` frame map, place buildings at the original fixed
   ground-slots (vs. our flow layout), and add interaction (drag colonists between tiles/buildings,
   build queue, cargo). *Europe:* drag-to-board / load-cargo / set-sail. *Both:* per-nation
   building/flag tints.

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
- **`showErrorPanel`** — the no-op seam audit's find (item 4 below), same silent-loss class as the
  messages: all five overloads funnel into one non-final seam that no-op'd, so **every error
  vanished**, and errors carrying a `System.exit` callback left the app hung. Now routes through the
  shared popup and runs the callback on dismiss. Live-verified 2026-07-18 (README "Wired seams").
- **The three event confirm dialogs** — `showMonarchDialog` (king's demands), `showFirstContactDialog`
  (meeting natives), `showNativeDemandDialog` (native tribute). Also silent-loss bugs the audit
  surfaced, not just missing screens: each hands the player's yes/no back over the wire, so a no-op
  dropped the exchange (a tax hike accepted by omission). These seams are *async*
  (`DialogHandler<Boolean>`); a shared `askEvent` helper mirrors the matching FreeCol dialog's
  message/labels/icon over the popup and fires the handler. Monarch tax dialog live-verified
  2026-07-18 (README "Event confirm dialogs").

### Remaining

1. **The choice / input dialogs — blocked on [Q4](#open-questions-for-the-expert).** What is left of
   the dialog seams needs a **list widget** (`modalChoiceDialog`, and `showEmigrationDialog` — choose
   1 of 3 recruits) or a **text field** (`modalInputDialog`, and `showNamingDialog` — name a
   colony/region), both still plain Swing stopgaps whose original look we have never seen. The
   confirm-shaped popups these used to be grouped with (meeting natives, king's demands, native
   tribute) are **done** — see Shipped. Learn-skill and lost-city-rumour choosers ride on the list
   widget too. Negotiation (`DiplomacyPanel`) and the end-turn "units still active" prompt are the
   larger bespoke ones.
2. **The popup look is a guess — see [Q3](#open-questions-for-the-expert).** `ClassicDialog`'s
   metrics and green-on-wood palette are read off the original's screenshots by eye, not measured
   from the art. Every popup inherits whatever is wrong, so this wants sign-off before the seams
   above multiply it. `WOODPAN2.PIK` is not used yet.
3. **The menu dropdowns** are still default Swing (deferred here from Phase 2's HUD polish).
4. **Audit the other no-op seams — done 2026-07-18, four fixes total.** Cross-referenced every
   `getGUI().X` call in `client/control/*` against `ClassicGUI`'s overrides. Fixed **`showErrorPanel`**
   and, chasing the event-dialog sub-audit, the **three confirm-shaped event dialogs** (monarch /
   first-contact / native-demand) — all silent-loss bugs (see Shipped above), all now confirm-popup
   fixes needing no expert input. Reassuring otherwise: the combat/leave/stop confirms already
   delegate to the overridden `modalConfirmDialog`. **What's left** are the two *non*-confirm event
   dialogs — `showEmigrationDialog` (choice of 3 recruits) and `showNamingDialog` (text) — which need
   the Q4 widgets, folded into item 1 below. Details in README "The no-op seam audit".

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
