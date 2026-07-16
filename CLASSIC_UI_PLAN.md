# Classic UI for FreeCol — implementation plan (remaining work)

Goal: a faithful original-Colonization experience — a primitive, original-style **UI**
*on top of the classic **rules*** — built as an alternative view on the existing engine.
An expert Colonization player iterates with us per slice.

Two independent axes (keep them separate):
- **UI** — our `ClassicGUI` work (this document's phases).
- **Rules** — supplied by FreeCol's data-driven **`classic` ruleset** (`data/rules/classic/`),
  loaded at game start. The default FreeCol game is `classic` *plus a diff* (`freecol`'s spec
  declares `extends="classic"`). Faithfulness is therefore mostly a *ruleset* question — see
  "Gameplay fidelity" below.

> **This plan tracks *remaining* work.** How the *shipped* classic UI works — the `GUI` seam
> contracts, `ClassicGUI`/`ClassicMapViewer`, the rectangular projection, controller/key wiring,
> the minimap model, the asset RE frame maps, and the live-test harness — lives next to the code in
> [`src/net/sf/freecol/client/gui/classic/README.md`](src/net/sf/freecol/client/gui/classic/README.md).
> Completed-slice detail and the asset-decoder history are in that README + the git log; this doc
> keeps only a one-line pointer per shipped item.

## Status at a glance (2026-07-15)

- **UI:** Phase 0 ✅ · **Phase 1 (map) ✅** · **Phase 2 (HUD & core screens) 🔨** — shipped: map
  HUD (menu bar + info panel), colony screen, Europe screen, ten advisor reports (Colony / Military /
  Naval / Trade / Religious / Production / Congress / Exploration / Cargo / Indian over a shared
  `ClassicReportPanel` frame, the Colony Advisor **paging** through its column sets as the original
  does), order buttons, in-panel minimap + unit portrait, wood chrome, Col1's light-on-dark menu bar,
  localized report captions; **Phase 3 (dialogs) ⬜** · **Phase 4 (pre-game/setup) ⬜**.
- **Assets (bring-your-own install):** A0/A1/A3 ✅ · **A2 🔨 growing** (alias curation) · A5/A6 ⬜.
- **Rules fidelity:** R0–R3 ⬜ — a separate track that does **not** block the UI.

### Shipped (one line each; see the package README for how-it-works)

- **Phase 0 — scaffold & launch** ✅ — `ClassicGUI extends GUI`, `--classic` flag, selector,
  auto-launch lobby stopgap. (README "What this is".)
- **Phase 1 — the map** ✅ — rectangular 48px grid: `TERRAIN.SS` terrain, `PHYS0.SS` feature
  overlays + coast/beach feathering, `ICONS.SS` unit/goods/settlement sprites, edge-pan, minimap,
  clicks/keys driving the real `InGameController`, and **turn controls** (Enter/Space/W/B, the 1994
  key scheme). (README "`ClassicMapViewer`".) Frame maps + RE writeups live in `ClassicTileArt`,
  `aliases.properties`, and the README.
- **Phase 2 so far** ✅ — map HUD (reused `InGameMenuBar` + `ClassicInfoPanel`); **colony screen**
  (`ClassicColonyPanel`, `COLONY.PIK`/`BUILDING.SS`/`WOODTILE.SS`); **Europe screen**
  (`ClassicEuropePanel`, `EUROPE.PIK`, recruit/purchase/train via the real controllers); **ten advisor
  reports** over a shared `ClassicReportPanel` frame (unit rosters share a further
  `ClassicReportRosterPanel`) — Colony (`REPORT6.PIK`, F3), Military (`REPORT6`, F7), Naval
  (`REPORT7`, F8), Trade (`REPORT5`, F9), Religious (`REPORT2`, F1), Production (`REPORT4`, shift F4),
  Congress (`REPORT3`, F6), Exploration (`REPORT8`, shift F2), Cargo (`REPORT7`, shift F1), Indian
  (`REPORT1`, F5); **order buttons** (reuse `FreeColAction`
  `BUTTON_IMAGE` art), **in-panel minimap**, **`WOODPANL.PIK` wood chrome**, and caption localization.
  (README "Phase 2 HUD" / "Colony screen" / "Europe screen" / "Report screens".)
  - **Key enabler:** `ClassicGUI.updateActions()` (called on `reconnectGUI` + every `changeView`)
    refreshes the reused actions' enabled state — the classic HUD has no `Canvas`, so without it the
    Europe/report menu items and the order buttons stay stuck disabled.

## Remaining work (priority order)

### Phase 2 — finish HUD & core screens 🔨

1. **Remaining reports.** Ten advisor reports ship
   (Colony/Military/Naval/Trade/Religious/Production/Congress/Exploration/Cargo/Indian) over the shared
   `ClassicReportPanel` frame, and the **Colony Advisor now pages** through its column sets as the
   original does (Sons of Liberty / Military Garrison — the two the expert's shots document; the paging
   *keys* are a guess awaiting sign-off). The rest of the `showReport*Panel` seams still no-op — add
   them over their `REPORTn.PIK` backdrops (**foreign affairs** — needs the async `nationSummary`
   fetch, so more than a static paint — plus labour/education/history/requirements; `REPORT9`, a
   duplicate of the `REPORT1` native-scout art, is still unassigned). Extend `ClassicReportPanel` (unit
   rosters: `ClassicReportRosterPanel`). The Congress and Indian row loops have only run their
   empty-state branch — see the README's verification caveat.
2. **HUD polish (remaining).** ~~Menu-label contrast~~ ✅ (Col1's light-on-dark bar — README "Menu-bar
   contrast"; the **dropdown popups** are still default Swing, folded into the Phase-3 reskin).
   ~~Unit portrait in the info panel~~ ✅ (`ClassicInfoPanel.paintUnitPortrait`). ~~Localize the
   key-hint captions, report column heads and Colony Advisor subtitles~~ ✅ (new isolated
   `classic.report.*` keys in `FreeColMessages[_de].properties`, German subtitles matching the
   original; info-panel hints reuse the action `.name` keys — README "Caption localization"). Remaining:
   the colony/Europe screens' own few hard-coded captions; refine the wood-chrome tiling seams.
3. **Screen-interaction polish.** *Colony:* validate the provisional `BUILDING.SS` frame map, place
   buildings at the original fixed ground-slots (vs. our flow layout), and add interaction (drag
   colonists between tiles/buildings, build queue, cargo). *Europe:* drag-to-board / load-cargo /
   set-sail. *Both:* per-nation building/flag tints.

### Phase 3 — Dialogs & polish ⬜

`modalConfirmDialog`/`modalChoiceDialog`/`modalInputDialog`, negotiation, end-turn. The stopgaps are
already wired as **plain Java dialogs** (the colony-founding confirm/choice/name seams, the
Europe recruit/train/purchase choosers). **Reskin them to the original wood-framed look** —
`WOODPANL.PIK`/`WOODPAN2.PIK` frame + colonist portrait + green-on-wood text — **once, as a shared
classic-dialog component** all popups route through (rather than per-popup). The same component then
reskins the event popups sharing these seams (meeting natives, king's demands, learn-skill, lost-city
rumours, disembark/high-seas confirms). Negotiation (`DiplomacyPanel`) and the end-turn "units still
active" prompt are the larger bespoke ones.

### Phase 4 — Pre-game & setup screens ⬜

No classic lobby yet — `showStartGamePanel` auto-launches single-player as a stopgap (why `--fast`
drops straight into the map, no nation/difficulty choice). Each setup screen is a `showXPanel`-style
override + its `.PIK` background: **nation select** (`NATIONS.PIK`), **difficulty** (`DIFFICUL.PIK`),
**customise** (`CUSTOMIZ.PIK`); the **declare-independence** (`DECLARAT`/`DECOIND.PIK`) and **king**
(`KINGLSS1/2.PIK`) art sits adjacent (some overlaps Phase 3 event dialogs). Lower urgency than the
in-game screens — the auto-launch keeps the game reachable.

**Explicitly out of scope (deliberate):** the intro/new-game **cinematics** and closing sequence.
`--no-intro` skips them; only the static `OPENING.PIK` title renders. The animated frames
(`LEVN0001–0010`, `CLOS-BKG`/`CCBKGD`) are extracted but nothing plays them — revisit only if the
expert deems the original intro essential (a fresh scope decision, akin to A6 audio).

**Expert player drives:** screen priority, validating each rebuilt screen vs. the real game, sign-off
per slice. Reference screenshots live in git-excluded `screenshots/` (never commit; `git add` explicit
paths). Sub-states matter as much as idle layouts (open menus, in-progress drag, mid-dialog).

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

## Gameplay fidelity: classic ruleset vs. original Colonization (Col1)

FreeCol's `classic` ruleset is the team's **best-effort emulation of the original 1994 game**, and
the base the `freecol` ruleset extends. Choosing it gets us most of the way to Col1 fidelity *for
free*, but it is **not a bit-perfect clone**. Because rules are data-driven, **our UI project does
not implement any of this** — we just load `classic`. This section is the shared audit of where
"classic" still departs from the real game, separate from UI work.

### A. Options where `classic` already matches Col1 (free)

Verified against [`data/rules/classic/specification.xml`](data/rules/classic/specification.xml)
(defaults are the classic values):

| Behaviour | Option | classic default | Col1-faithful? |
|---|---|---|---|
| Amphibious assault (attack from ship) | `amphibiousMoves` | `false` | ✅ Col1 had none |
| Manual choice of student to train | `allowStudentSelection` | `false` (least-skilled first) | ✅ |
| REF arrival | `teleportREF` | `true` (teleports) | ✅ |
| Custom House sells boycotted goods | `customIgnoreBoycott` | `true` | ✅ |
| Enhanced missionaries | `enhancedMissionaries` | `false` | ✅ Col1 had none |
| Exploration (lost-city) points scoring | `explorationPoints` | `false` | ✅ |
| Scouting settlement consumes bonus | `settlementActionsContactChief` | `false` | ✅ |
| Found colonies during War of Independence | `foundColonyDuringRebellion` | `false` | ✅ |
| Bell accumulation capped at 100% rebels | `bellAccumulationCapped` | `true` | ✅ |
| Classic fixed starting positions | `startingPositions` | `0` (classic) | ✅ |
| Equip new European recruits | `equipEuropeanRecruits` | `true` | ✅ |

> ⚠️ Doc-vs-repo: the official user guide (v0.11.6) says classic enables `expertsHaveConnections`;
> **this repo's classic spec sets it `false`.** The repo is what we ship — treat repo defaults as
> authoritative and re-audit if we bump the ruleset version.

### B. Residual divergences the classic ruleset does *not* fix (engine-level)

Not toggleable from the ruleset — a faithful-classic goal means accepting or patching the engine.

- **Combat math.** FreeCol does **not** reproduce Col1's combat resolution (power × modifiers /
  random vs Col1's own odds/terrain/ambush handling). SF pending-feature #65. *Highest-impact.*
  **[Confirmed — FreeCol dev tracker]**
- **Founding Father basis.** Col1 recruits from *gross* bells; FreeCol uses *net*. classic is
  supposed to switch to gross — verify. **[FreeCol wiki]**
- **Movement-point carryover.** Col1 allegedly carries unused movement; FreeCol resets. **[Community]**
- **River corner-cutting.** Diagonal corner-cuts go overland in Col1; FreeCol treats as river.
  **[Community]**
- **Bell→SoL formula.** Col1 ≈ `bells/(pop+1)`; FreeCol ≈ `bells/(pop·2)`. **[Community — verify]**
- **Food/horse growth ordering.** FreeCol counts fish before grain, fish don't feed horses; Col1
  differed. **[Community]**
- **REF growth.** Col1 scales REF with tax income; FreeCol grows it steadily. **[FreeCol wiki]**
- **Unimplemented Col1 features.** Escalating native tribute before war; sailing to other Europeans'
  ports after independence; Custom House rival-trade report / post-war perks; the 9% tax bump with the
  post-privateer frigate offer. **[FreeCol wiki]**
- **Ranged attack is dormant, not a divergence.** `AttackRanged` needs `attackRange > 0`; no classic
  or freecol unit type sets it. (`model.ability.bombard` fort/ship bombardment *is* genuine Col1 and
  present.)

### C. Other FreeCol additions to keep out of the classic experience

Absent from Col1, our UI should not surface: **trade routes**, the four extra nations
(Portugal/Sweden/Denmark/Russia) + advantages, abandon-colony-anytime, "destroy all Europeans"
victory. The classic ruleset already restricts nations to the original four.

### D. Roadmap to "as-classic-as-possible" (rules track, parallel to UI)

Guiding principle (consistent with "additive changes only"): **prefer data/ruleset fixes; when engine
code must change, gate it behind a ruleset option** (default = current `freecol` behaviour,
Col1-correct for `classic`) so we stay mergeable with upstream `master`.

- **R0 — Baseline & instrumentation** *(prereq; low risk)* — confirm table-A defaults load under
  `--classic`; build a one-page Col1 conformance checklist (the acceptance instrument); verify the
  gross-vs-net Founding-Father basis in our build; confirm the class-B "community-reported" formulas
  against engine source before acting (don't patch on forum lore).
- **R1 — Ruleset-only corrections** **[data]** — audit every option/modifier vs the checklist and
  flip non-Col1 defaults; resolve `expertsHaveConnections` by decision; tune divergent modifier
  *values* (combat terrain/ambush/fortify/artillery-in-open).
- **R2 — Engine corrections, gated by option** **[engine]** — combat resolution (new
  `model.option.combatModel`, *highest impact / hardest*); Bell→SoL formula; movement carryover +
  river corner-cutting; Founding-Father gross basis; food/horse ordering; REF-growth-vs-tax.
- **R3 — Missing Col1 features** **[engine]** — native tribute escalation; post-independence trade
  with other Europeans; Custom House rival-trade report + post-war perks; the 9% tax bump.

**Sequencing:** R0 → R1 (cheap, no risk) → R2 (combat first) → R3. Does not block UI phases and
vice-versa.

*Sources:* [FreeCol user guide](https://www.freecol.org/docs/FreeCol.html),
["What Would Col1 Do?" wiki](https://sourceforge.net/p/freecol/wiki/What%20Would%20Col1%20Do%3F/),
[SF pending-feature #65](https://sourceforge.net/p/freecol/pending-features-for-freecol/65/),
[Civ wiki — divergences](https://civilization.fandom.com/wiki/FreeCol_1.0.0/Divergences_from_Colonization),
and this repo's `data/rules/classic/specification.xml`.

## Asset track: bring-your-own original install (DECIDED)

**Decision:** ship no original assets; load the original game's pixels from the user's own legal
install (Steam/GOG "Classic"). If none is configured, fall back to FreeCol's free art so the build
always runs — FreeCol art is the *fallback skin*, original art the *fidelity skin*.

**Pipeline:** an offline **converter** (pure Java, `net.sf.freecol.tools.classicassets`, driven by
`ant classic-assets`) decodes the install's art into a **git-ignored** `data/mods/classic_original/`
pack (PNGs + `resources.properties` + `mod.xml`), which the classic UI loads as the highest-priority
mod. Key→frame aliases are curated in committed `tools/classic_assets/aliases.properties` (appended
into the pack on regen, so regenerating never clobbers them). No Python/external tool.

**Formats (verified — GOG install `…\Colonization\MPS\COLONIZE\`):** all graphics are `MADSPACK 2.0`
containers — **`.PIK`** (35 full-screen 320×200 screens/chrome) and **`.SS`** (sprite sets, many
frames each). Master palette `VICEROY.PAL` (768-byte 6-bit VGA + trailer) decodes palette-less PIKs.
The decoder path (`MadsPack` container → `Fab` LZ bit-stream decompressor → `Palette`/`SsDecoder`/
`PikDecoder` → `ClassicAssetConverter`) is documented in those classes' comments; the tricky bits are
FAB decompression and the per-set transparency encodings (`0xFD` alpha vs the coast frames' opaque
colour-key black). Written clean-room from the format spec → GPLv2+, upstream-compatible.

**Screen manifest (the 35 `.PIK` files) — scopes remaining UI screens:** `COLONY`, `EUROPE`,
`REPORT1`–`REPORT9`, `NATIONS`, `DIFFICUL`, `CUSTOMIZ`, `DECLARAT`/`DECOIND`, `OPENING`/`OPENMENU`/
`OPENBORD`, `KINGLSS1/2`, `WOODPANL`/`WOODPAN2`, `CLOS-BKG`/`CCBKGD` + `LEVN0001`–`LEVN0010`.

**Backlog:**
- **A0/A1/A3 ✅** — assets located + format confirmed; native-Java decoder/converter; pack loader
  (`FreeColClient.withClassicOriginalPack`, graceful fallback when absent).
- **A2 🔨 growing** — alias curation is the bulk of the asset work. Live so far: title screen, all map
  terrain, `PHYS0.SS` overlays + coast feathering, `ICONS.SS` unit/goods/settlement sprites; the
  colony/Europe/report screens load their `.PIK`/`.SS` art by key. Remaining: per-nation unit/colony
  tints, a fortress-distinct colony frame, and each new Phase-2/4 screen's art as it lands.
- **A5 ⬜** — runtime (in-game) extraction: install picker → decode on demand in-process (the Java
  decoder unblocks this), restart-to-apply UI switch (the GUI is chosen once at `FreeColClient`
  construction, so switching UI is preference+restart, not an in-place swap).
- **A6 ⬜** — original audio: SFX (`COLDIG.BIN`, raw unsigned 8-bit PCM) feasible; music
  (`AMER2.MP` MPS sequence + `*SOUND.COL` DOS drivers) hard/poor-ROI. Do SFX after the UI phases that
  trigger them; music likely out-of-scope. Gates nothing.

## Architecture (why this is feasible)

FreeCol is cleanly layered: **model** (`common/model/`, UI-agnostic, server-authoritative),
**controllers** (`client/control/`, reach the view only via `getGUI().<method>`), and the **view
facade** `client/gui/GUI.java` — a concrete base class with **0 abstract methods, no-op stubs**, used
as-is in headless mode. So a new UI = another `GUI` subclass: unoverridden methods no-op, **the app
runs from day one**, and screens light up incrementally. Selection point (`FreeColClient.java`):
`gui = headless ? new GUI(this) : classic ? new ClassicGUI(this) : new SwingGUI(this)`.

**Decisions:** **Swing**, as `ClassicGUI extends GUI` in a new `client/gui/classic/` package (the
whole client is Swing/AWT-bound; a rectangular-tile map is trivial in `Graphics2D`). **Additive
changes only** — new package + one-line selector change + a `--classic` flag — keeps us mergeable
with upstream `master`.

**Reuse:** `common/model` + `client/control/*` + networking/options/resources = 100% (read state /
drive controllers directly); `client/gui/action/` (the `FreeColAction`s, reused for the menu bar +
order buttons) = high; `client/gui/mapviewer/` = partial (image-selection logic; isometric projection
replaced by our rectangular one); `client/gui/panel/` + `dialog/` = optional (we reskin our own or
delegate as a stopgap).

**⚠️ `data/mods/classic_ui/` is a false friend** — it is "classic *FreeCol*" (an old parchment
theme), **not** classic *Colonization*. Not the design target; the expert's original-game screenshots
+ our own panels are. (Base order-button icons come from `data/base`, not this mod.)
