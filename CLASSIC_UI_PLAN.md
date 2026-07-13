# Classic UI for FreeCol — architecture findings & implementation plan

Goal: a faithful original-Colonization experience — a primitive, original-style **UI**
*on top of the classic **rules*** — built as an alternative view on the existing engine.
An expert Colonization player iterates with us per slice.

Two independent axes (keep them separate):
- **UI** — our `ClassicGUI` work (this document's phases).
- **Rules** — supplied by FreeCol's data-driven **`classic` ruleset** (`data/rules/classic/`),
  loaded at game start. The default FreeCol game is `classic` *plus a diff*
  (`freecol`'s spec literally declares `extends="classic"`). Faithfulness to the original
  game is therefore mostly a *ruleset* question, audited in one place — see
  "Gameplay fidelity" below.

## Status at a glance (2026-07-14)

- **UI:** Phase 0 ✅ → **Phase 1 (the map) ✅ DONE** (rectangular grid renders terrain +
  units + colonies + cursor; **original `TERRAIN.SS` sprites fill the grid cleanly**; **edge
  panning of the focus tile**; **clicks + keys drive the real `InGameController` — click
  selects units/tiles & opens owned colonies, arrow/numpad keys move the active unit or the
  terrain cursor with the focus following**; **a bottom-left minimap overlay gives a whole-map
  overview with a viewport box + click-to-recentre**; **per-tile feature overlays — forest trees,
  hills, mountains, rivers, roads, plowed fields, resource markers and the lost-city rumour —
  composite onto each square cell from the original `PHYS0.SS` overlay set**; **land/water borders
  feather with the original `PHYS0.SS` coast quarter-tiles (beach ring + coastal water) instead of a
  hard edge**; **the original Colonization unit map-sprites and goods icons (`ICONS.SS`) are aliased
  over FreeCol's own art, up-scaled into the cell so the classic ship/colonists/etc. render at a
  sensible size**; **the original colony & native-settlement sprites (`ICONS.SS`) are aliased too —
  colonies by size×stockade (per-nation keys) and camps/villages/inca/aztec by type — rendered through
  the same up-scale path** — all verified live (settlements by shared-pipeline + frame RE, as an
  existing native settlement is beyond turn-1 reach and colonies cannot be founded yet)).
  → **Turn control ✅ DONE (verified live 2026-07-11).** End-turn + skip/wait are wired into
  `ClassicMapViewer`'s key bindings (bound `WHEN_IN_FOCUSED_WINDOW`, driving the real
  `InGameController`), using the **original *Colonization* key scheme** confirmed from the 1994
  manual: **Enter** = end turn (`endTurn(false)` — `false` because the classic GUI no-ops the
  "units active" dialog); **Space** = "no orders" (skip the active unit, `changeState(unit,
  SKIPPED)` + `nextActiveUnit()`, mirroring `SkipUnitAction`; with no active unit Space ends the
  turn); **W** = wait (`waitUnit()`). Movement keys (arrows + numpad) unchanged. **Verified live:**
  Enter advances the turn (AI players process, a new turn begins) three round-trips; the active
  ship then moves and the focus follows it. This removes the turn-1 movement cap. See the package
  README "Controller wiring → Turn controls" for the full contract and the next-active-unit caveat.
  → **Phase 2 (HUD & core screens) — STARTED. Map-view HUD ✅ DONE (verified live 2026-07-13).**
  The expert delivered the original-game screenshots (local `screenshots/`, git-excluded, never
  commit): map + **top menu bar** (SPIEL/ANSICHT/BEFEHLE/BERICHTE/HANDEL) + **right info/orders
  panel** (minimap, turn/gold/tax, unit & terrain info, order hints); a **menu open** state; the
  **Europe** dock screen and its **recruit** dialog; the signature **colony** screen ("Northern
  Sugar"); and a **report** screen. **First slice shipped:** `ClassicGUI.reconnectGUI` now composes
  a map screen — `ClassicMapViewer` (centre) + new **`ClassicInfoPanel`** (right strip: turn, gold,
  tax, live active-unit type/moves/terrain, order-key hints) + the **reused `InGameMenuBar`** (all
  five classic menus wired to the real `FreeColAction`s + a golden gold/tax/year status line). A new
  `installLookAndFeel` override creates the main font (needed by the menu bar) *without* installing
  `FreeColLookAndFeel`, so the map keeps its black fog and the info panel its dark ground. Verified
  live: menu bar + status line render, the info panel tracks the active ship (Moves 5/5→3/5, terrain
  updates), 0 SEVERE.
  → **Colony screen ✅ DONE (verified live 2026-07-14).** `showColonyPanel` now shows a real classic
  colony screen — **`ClassicColonyPanel`**, a 320×200 (native-VGA) integer-up-scaled repaint of the
  signature original (refs `opening_016/017`): a gold-on-black title bar; the **buildings pane** on the
  sandy ground drawn from the original **`BUILDING.SS`** sprite set (workers + production tags, names
  on hover); the **3×3 work-tile grid** on the `WOODTILE.SS` wood panel (cells placed by compass
  `Direction`, colony centred); and the **`COLONY.PIK`** bottom band with the live SoL/tory split,
  ships-in-port, net production and the 16-slot warehouse row. To reach it live the **`B` = build
  colony** key was wired (`ClassicMapViewer`, mirroring `BuildColonyAction`), plus the
  `modalConfirmDialog`/`modalChoiceDialog`/`getNewColonyName` seams the founding flow needs (plain
  Swing for now — Phase 3 reskins). Verified: sail→disembark→**B**→click colony renders the screen,
  hover names work, Escape closes, 0 SEVERE. See the package README "Colony screen" + "Controller
  wiring → Turn controls". **Remaining Phase 2 (each its own slice, priority order): (1)** the
  **Europe** dock screen + recruit/train (`opening_009-013`); **(2)** the **report** screens
  (`opening_014/015`); **(3)** HUD polish — port the minimap and order **buttons** into the info
  panel, the `WOODPANL.PIK` chrome, menu-label contrast, and caption localization; **(4)** colony-screen
  polish — validate the `BUILDING.SS` frame map, the original fixed building slots (vs. our flow
  layout), and interaction (drag colonists, build queue, cargo). Phase 3 ⬜.
- **Assets (bring-your-own original install):** A0 ✅ · A1 ✅ (decoder + converter) ·
  A3 ✅ (pack loader) · A2 🔨 growing (title screen + all base/forest/water **terrain** tiles +
  the `PHYS0.SS` physical-feature overlays + the `PHYS0.SS` **coast/beach feathering** + the
  `ICONS.SS` **unit map-sprites & goods icons** + the `ICONS.SS` **colony & native-settlement
  sprites** render live) · A5 ⬜ (runtime picker) · A6 ⬜ (audio).
- **Rules fidelity:** R0–R3 ⬜ — a separate track that does **not** block the UI.

Per-item detail (with verification notes) lives inline in the **Phased plan**, **Asset backlog &
status**, and **Gameplay fidelity §D** sections below. This snapshot is the single at-a-glance
tracker — update it as items land.

## How to run & test (classic UI)

Build once after a code change, then launch with `--classic`. Run from the repo root so `data/`
is found. `ant compile` refreshes `build/`, so we can launch straight from there (no repackage):

```powershell
ant compile
java -Xmx2G -cp "build;jars/*" net.sf.freecol.FreeCol --classic
```

One-liner for the tight test loop (compile, launch, capture console to `classic_run.txt`):

```powershell
ant compile; java -Xmx2G -cp "build;jars/*" net.sf.freecol.FreeCol --classic 2>&1 | Tee-Object classic_run.txt
```

Notes:
- `;` is the Windows classpath separator; `jars/*` pulls in all dependency jars.
- `--classic` selects `ClassicGUI` (see `FreeColClient` selector). Start a new game to reach the
  in-game view where `ClassicGUI.startGUI` fires.
- Add `--fast --no-intro` to auto-start a new single-player game with **no GUI clicks** (skips the
  intro video and the lobby) — the fastest way to reach the running in-game view for testing.
- The `options.xml NoSuchFileException` on first launch is a benign pre-existing warning.
- Packaged-jar alternative: `ant package` then `java -Xmx2G -jar FreeCol.jar --classic` (the Ant
  `run` target does not pass `--classic`, so invoke the jar directly).

## Phased plan (each slice independently demoable)

- **Phase 0 — Scaffold & launch. ✅ DONE (verified live 2026-07-07).** `ClassicGUI extends GUI`,
  `--classic` flag, selector `headless ? GUI : classic ? ClassicGUI : SwingGUI`; a single-player
  game boots and runs on `ClassicGUI` with 0 SEVERE. Implementation details (auto-launch lobby
  stopgap, window-size sentinel) live in the package README.
- **Phase 1 — The map. ✅ DONE (verified live 2026-07-10).** `ClassicMapViewer` (a `JPanel` in
  `client/gui/classic/`, installed as the frame's whole content pane) renders the map on a
  rectangular 48px grid centred on a focus tile and owns the classic view state; `ClassicGUI`
  delegates the view-mode / focus / refresh `GUI` methods to it. **Items (a)–(e) done & verified
  live** — original `TERRAIN.SS` sprites fill the grid; (a) rectangular projection +
  terrain/unit/colony/cursor rendering; (b) edge-of-window mouse panning; (c) clicks + keys drive
  the real `InGameController` (select units/tiles, open owned colonies, move the active unit /
  terrain cursor with the focus following, parity-aware key→`Direction` resolution); (d) a
  bottom-left minimap overlay (cached whole-map raster, viewport box, click-to-recentre); (e)
  per-tile feature overlays (forest trees, hills, mountains, rivers, roads, plowed fields, resource
  markers, lost-city rumour) composited from the original `PHYS0.SS` overlay set on top of the base
  terrain. **How each piece works — projection, view-state ownership, terrain scaling, the
  isometric-vs-rectangular key caveat, the minimap caching model, and the `PHYS0.SS` overlay
  compositing / frame map / connectivity — is documented in the package
  [README](src/net/sf/freecol/client/gui/classic/README.md); consult it before extending the map.**
  - **(e) resolved — the overlays came from `PHYS0.SS`, not `TERRAIN.SS`.** The base terrains are
    in `TERRAIN.SS` (12 frames); the *physical-feature* overlays are a separate 154-frame square
    16×16 set, `PHYS0.SS`, cut exactly for a rectangular grid — so they composite onto the classic
    cells with no skew (the reason to prefer them over FreeCol's isometric overlay art). The
    directional feature sets (minor/major river, mountains, hills, forest) share one 4-bit
    connectivity encoding and roads are spoke-composited; the full frame map lives in
    `ClassicTileArt` and the package README. FreeCol's own overlay/forest/river art remains the
    pack-absent fallback.
  - **Map-fidelity polish (unblocked — no screenshots needed).** With the decoded pack in
    hand, both slices below shipped and sharpen the map without waiting on the expert:
    - **Coastline / beach tiles** ✅ **DONE (2026-07-11).** The 1994 game's land/water feathering
      now renders: the classic viewer composites the original `PHYS0.SS` coast quarter-tiles onto
      each water cell adjacent to land, so borders feather like Col1 instead of the old hard edge.
      **Verified live** (`--fast` start-at-sea, coast around the nearby landmasses) — the beach ring,
      lighter coastal water, green coast outline and diagonal corner-bridges all render crisp
      (nearest-neighbour), 0 SEVERE, no missing-resource warnings. **The RE resolved** (it was the
      hypothesised 4-corner × 8-config scheme all along — the earlier recon was defeated by two
      decode quirks): `PHYS0.SS` frames `108–139` are 32 **8×8** beach sub-tiles laid out
      `frame = 108 + config·4 + corner` with `corner` clockwise `NW=0, NE=1, SE=2, SW=3`, and
      per-corner `config = (ccwEdgeLand?1) | (diagLand?2) | (cwEdgeLand?4)` over the two orthogonal
      neighbours bounding the corner plus the diagonal — **verified rotationally consistent across
      all four corners** (config 1 = the counter-clockwise edge neighbour is land, config 4 = the
      clockwise edge, config 2 = a diagonal-only neighbour draws a light coastal-water wedge,
      config 0 = open ocean draws nothing). The two decode quirks that had masked this: (i) these
      frames encode transparency as **opaque black** (a colour-key, index 0), *not* the `0xFD` alpha
      used elsewhere — so "empty" `108–111` are config-0 (no adjacent land); and (ii) `116–119`'s
      "water but no land" are config 2, the diagonal-only coastal-water wedges. Drawn on **water**
      cells (`!tile.isLand()`), one 8×8 sub-tile per quadrant. **Colour-key fix (2026-07-11):** the
      decoder leaves that colour-key black *opaque*, so the first cut painted black wedges over the
      sea along every coast; `ClassicTileArt.frame` now keys pure black out to alpha 0 when it loads a
      coast frame (`keyOutBlack`, cached), so the base ocean shows through and only true unexplored
      tiles stay black. Re-verified live. Frame layout + the RE writeup live in `ClassicTileArt`'s
      class comment and the
      package README. **Not yet wired (deferred, low priority):** the estuary/river-mouth pieces —
      `140–147` (ocean corner-hints) and `150–153` (diagonal sand strips) — for river mouths.
    - **Original unit & goods sprites** ✅ **DONE (2026-07-11).** The original *Colonization*
      `ICONS.SS` unit map-sprites and goods icons are aliased over FreeCol's own art in the committed
      `tools/classic_assets/aliases.properties` (grows **A2**), and the classic viewer now up-scales
      the small (~16px) sprites into the 48px cell. Verified live: the start **merchantman** renders
      as the original ship sprite, up-scaled crisp (nearest-neighbour) with no skew, cursor around it;
      0 SEVERE, no missing-resource warnings. What shipped:
      - **Frame map (curated by labelled montage + eyeball, as for `TERRAIN.SS`/`PHYS0.SS`).**
        `ICONS.SS` (131 frames): **goods** are frames `022–037` in colour (food/corn `022`, sugar,
        tobacco, cotton, furs, lumber, ore, silver, horses, rum, cigars, cloth, coats, tradeGoods,
        tools, muskets `037`) with `038–053` their unused grayscale twins, plus `054` hammers,
        `056` crosses, `057` bells (`055` is a red-X "none" marker). **Units:** `005` caravel,
        `006` merchantman, `007` galleon, `014` privateer, `015` frigate, `127` man-o-war; `008`
        wagon train, `016` treasure train, `065` artillery (cannon); `058` base colonist (all
        civilian types share one map sprite in Col1), `061` elder statesman, `059` colonial regular,
        `098` native brave, `115`/`116` king's regular infantry/cavalry. Role sprites (shared across
        colonist types): `089` soldier, `076` dragoon, `075` scout, `081` pioneer, `085` missionary,
        `099` armed brave, `101` mounted brave. (Note `117` is a *face portrait*, not a ship.)
      - **Keys.** goods → `image.icon.model.goods.<id>` (all 22: incl. the FreeCol-only food-derived
        grain/fish/meat, which reuse the food/corn frame `022`); units →
        `image.unit.model.unit.<id>[.<roleSuffix>]` — all 194 base+role keys defined in
        `data/base|default/resources.properties` are overridden so armed/mounted units also render
        classic (per `ImageLibrary.getUnitTypeImageKey` / `Role.getRoleIdSuffix`; no nation-suffixed
        unit keys exist, so the non-nation aliases take effect).
      - **Code caveat #1 (scaling) — fixed.** `ClassicMapViewer.drawCentered` now branches on source
        size (which doubles as pack detection): small classic art is *up-scaled* to
        `UNIT_CELL_FRACTION` (0.9) of the cell, nearest-neighbour like terrain; large FreeCol
        pack-absent art is still *shrunk* to fit as before.
      - **Follow-ups (out of scope for this pass, documented):** **(a) Nation tint** — `ICONS.SS`
        carries per-nation colour variants of the colonist, but FreeCol tints units itself; the first
        pass aliases one neutral sprite per type/role, so all nations share a colour. Per-nation
        fidelity via the `image.unit.<id>.<nationResourceKey>` suffix is a later slice.
        **(c) Goods icons** only surface on the Phase-2 colony/Europe screens; the aliases resolve
        cleanly now but are not yet visible.
    - **Original settlement/colony sprites** ✅ **DONE (2026-07-11)** (was follow-up (b)). The original
      *Colonization* colony & native-settlement map-sprites (also `ICONS.SS`) are aliased over FreeCol's
      own settlement art in the same committed `aliases.properties`, rendered through the **same**
      `drawCentered` up-scale branch as the unit sprites. What shipped:
      - **Frame map (labelled montage + eyeball).** Colonies (blue flag): `000` open colony (ring of
        buildings, no wall), `001` wooden **stockade**, `002` grey stone **fort/fortress**, `003`
        sparse huts (a just-founded / smallest colony). Natives: `010` teepee cluster (**camp**),
        `011` tan longhouse (**village**), `012` terraced pyramid (**aztec**), `013` grey stone city
        (**inca**).
      - **Keys.** Native settlements key off the settlement-type id
        (`image.tileitem.model.settlement.{camp,village,inca,aztec}` + the `.mission`/`.capital*`
        variants). Colonies key off apparent **size** (`.small`/`.medium`/`.large`) × **stockade**
        level (none/`.stockade`/`.fort`/`.fortress`) **and FreeCol prefers a per-nation frame when one
        exists** — and the base pack defines one for every European nation, so `getSettlementKey`
        resolves e.g. `…colony.small.dutch` and a neutral `…colony.small` alias would never be reached.
        So all 8 nation suffixes (returned by `Player.getNationResourceKey`) are aliased directly
        (`96` colony keys), collapsing Col1's real signal — **fortification** — onto FreeCol's
        size×stockade grid: unfortified colonies grow `003`(small)→`000`(medium/large), a stockade is
        `001`, a fort/fortress the stone `002`. Base non-nation colony keys catch REF-captured colonies
        (no `…<nationREF>` art exists).
      - **Code caveat (scaling).** `ClassicMapViewer.paintTile` now draws the settlement via
        `getScaledSettlementImage` (native ~16px) instead of `getSettlementImage(…, TILE_SIZE)` (which
        pre-sized to 128×64 and bypassed the crisp branch), so settlements hit the same
        nearest-neighbour up-scale path as units — exactly `getScaledUnitImage`'s treatment.
      - **Verification.** Aliases regenerate into the pack (125 settlement keys), 0 SEVERE, no
        missing-resource warnings; the caravel (`ICONS.SS.005`) renders **live** crisp in-cell via the
        identical `getScaled*Image → drawCentered` up-scale path settlements now share. Reaching an
        *existing* native settlement in-game to screenshot it is beyond turn-1 movement radius and the
        classic UI cannot yet end turns (same limitation the coastline/overlay slices hit for inland
        features), and a player colony cannot be founded yet (no build-colony action) — so the
        settlement pixels are verified by shared-pipeline + frame RE, not an in-game shot.
      - **Follow-up:** a fortress-distinct frame (Col1's fort and fortress both map to stone `002`
        here), and per-nation colony flag tints, are later slices — same axis as unit follow-up (a).
- **Phase 2 — HUD & core screens. 🔨 STARTED.** Info/orders bar, menu bar (reuse `action/`),
  **Colony screen** (signature original screen), Europe, unit/cargo, reports. Each = a `showXPanel`
  override; may delegate to existing Swing panel as a stopgap, then reskin.
  - **Map-view HUD ✅ DONE (verified live 2026-07-13).** `ClassicGUI.reconnectGUI` composes the map
    screen: `ClassicMapViewer` (centre) + **`ClassicInfoPanel`** (right info/orders strip — turn,
    gold, tax, live active-unit type/moves/terrain, order-key hints) + the reused **`InGameMenuBar`**
    (five classic menus wired to the real actions + a golden gold/tax/year status line). New
    `installLookAndFeel` override initialises the main font the menu bar needs, deliberately *without*
    `FreeColLookAndFeel` (which would parchment-wash the black map fog and the dark info panel — the
    menu bar paints its own parchment+wood chrome regardless). Info panel repaints on every
    `changeView`/`refresh`. See the package README "Phase 2 HUD" for the full design + follow-ups.
  - **Colony screen ✅ DONE (verified live 2026-07-14).** `ClassicColonyPanel` — a 320×200 integer
    up-scaled repaint of the signature original (refs `opening_016/017`): title bar; buildings pane
    (`BUILDING.SS` sprites, workers, production tags, hover names) on the sandy ground; the 3×3
    work-tile grid on the `WOODTILE.SS` panel (cells by compass `Direction`, colony centred); and the
    `COLONY.PIK` band with the SoL split, ships-in-port, net production and the 16-slot warehouse row.
    Reached by the new **`B` = build colony** key (mirrors `BuildColonyAction`) or a click on an owned
    colony; the founding flow's `modalConfirmDialog`/`modalChoiceDialog`/`getNewColonyName` seams were
    wired (plain Swing, Phase-3 reskin). See the package README "Colony screen". **Follow-ups:** validate
    the `BUILDING.SS` frame map (provisional), the original fixed building slots (vs. our flow layout),
    and interaction (drag colonists between tiles/buildings, build queue, cargo).
  - **Remaining (own slices, priority order):** **(1) Europe** dock + recruit/train
    (`opening_009-013`); **(2) reports** (`opening_014/015`); **(3) HUD polish** — port the minimap +
    order **buttons** into the info panel, `WOODPANL.PIK` chrome, menu-label contrast, caption
    localization.
- **Phase 3 — Dialogs & polish.** `modalConfirmDialog`/`modalChoiceDialog`/`modalInputDialog`,
  negotiation, end-turn. Reuse existing Swing dialogs first, reskin to taste.

**Expert player drives:** screen priority, validating each rebuilt screen vs. the real
Colonization layout/interaction, sign-off per slice.

> **Implementation reference.** The seam facts and per-piece "how it works" for the classic UI
> (the `GUI` method contracts, `ClassicGUI`/`ClassicMapViewer` responsibilities, the rectangular
> projection, controller wiring and key/click semantics, the minimap caching model, and the
> live-testing harness recipe) now live next to the code in
> [`src/net/sf/freecol/client/gui/classic/README.md`](src/net/sf/freecol/client/gui/classic/README.md).
> This plan tracks *remaining* work; the README documents what shipped.

## Gameplay fidelity: classic ruleset vs. original Colonization (Col1)

FreeCol's `classic` ruleset is the team's **best-effort emulation of the original 1994 game**
("attempts to emulate the rules of the original game as far as possible"). It is the base that
the `freecol` ruleset extends. Choosing it gets us most of the way to Col1 fidelity *for free*,
but it is **not a bit-perfect clone** — some divergences are deliberate options (already set to
the Col1 value in classic) and some are residual engine-level differences the ruleset cannot fix.

Because rules are data-driven, **our UI project does not implement any of this** — we just load
the `classic` ruleset. This section exists so the expert player and we share one audited list of
where "classic" still departs from the real game, separate from UI work.

### A. Options where `classic` already matches Col1 (we get these for free)

Verified against this repo's [`data/rules/classic/specification.xml`](data/rules/classic/specification.xml)
(defaults shown are the classic values; the `freecol` ruleset flips several of these):

| Behaviour | Option | classic default | Col1-faithful? |
|---|---|---|---|
| Amphibious assault (attack from ship) | `amphibiousMoves` | `false` | ✅ Col1 had none |
| Manual choice of student to train | `allowStudentSelection` | `false` (least-skilled first) | ✅ matches Col1 |
| REF arrival | `teleportREF` | `true` (teleports) | ✅ matches Col1 |
| Custom House sells boycotted goods | `customIgnoreBoycott` | `true` (ignores boycott) | ✅ matches Col1 |
| Enhanced missionaries (vision/trade/training) | `enhancedMissionaries` | `false` | ✅ Col1 had none |
| Exploration (lost-city) points scoring | `explorationPoints` | `false` | ✅ matches Col1 |
| Scouting any settlement action consumes bonus | `settlementActionsContactChief` | `false` | ✅ matches Col1 |
| Found colonies during War of Independence | `foundColonyDuringRebellion` | `false` | ✅ matches Col1 |
| Bell accumulation capped at 100% rebels | `bellAccumulationCapped` | `true` | ✅ matches Col1 |
| Classic fixed starting positions | `startingPositions` | `0` (classic) | ✅ matches Col1 |
| Equip new European recruits | `equipEuropeanRecruits` | `true` | ✅ matches Col1 |

> ⚠️ Doc-vs-repo discrepancy: the official user guide (v0.11.6) describes classic as enabling
> `expertsHaveConnections` ("experts produce without raw materials"). **This repo's classic spec
> sets it `false`.** The repo is what we ship — treat the repo defaults above as authoritative and
> re-audit if we bump the ruleset version.

### B. Residual divergences the classic ruleset does *not* fix (true fidelity gaps)

These are engine-level — not toggleable from the ruleset — so a faithful-classic goal means
either accepting them or patching the engine. Confidence varies; sources noted.

- **Combat math.** Even with the classic ruleset, FreeCol does **not** reproduce Col1's combat
  resolution (FreeCol uses a power × modifiers / random model; Col1 used its own formula with
  different terrain/ambush/odds handling). Acknowledged open item upstream (SF pending-feature
  #65). *Highest-impact divergence; a power player will feel it.* **[Confirmed — FreeCol dev tracker]**
- **Founding Father recruitment basis.** Col1 recruits from *gross* bell production; FreeCol uses
  *net*. The classic ruleset is supposed to switch to gross — verify in our build. **[FreeCol "What Would Col1 Do?" wiki]**
- **Movement-point carryover.** Col1 allegedly lets unused movement carry to the next turn;
  FreeCol resets each turn. **[Community-reported]**
- **River corner-cutting.** Diagonal moves cutting a corner go overland in Col1 (can end the turn
  early); FreeCol treats them as river movement. **[Community-reported]**
- **Bell→Sons-of-Liberty formula.** Col1 ≈ `bells/(pop+1)`; FreeCol ≈ `bells/(pop·2)`, giving a
  different SoL ramp. **[Community-reported — verify before relying on it]**
- **Food/horse growth ordering.** FreeCol counts fish before grain and fish don't feed horse
  growth; Col1 ordered these differently. **[Community-reported]**
- **REF growth.** Col1's REF scales with tax income; FreeCol grows it steadily regardless. **[FreeCol wiki]**
- **Unimplemented Col1 features.** Escalating native tribute demands before war; sailing to other
  Europeans' ports after independence; the Custom House rival-trade report / post-war trade perks;
  the 9% tax bump accompanying the post-privateer frigate offer. **[FreeCol wiki]**
- **Ranged attack is dormant, not a divergence.** The `AttackRanged` order requires a unit type
  with `attackRange > 0`; **no classic (or freecol) unit type sets it**, so the order is always
  disabled. It is a mod-only engine hook, not a gameplay difference to worry about.
  (`model.ability.bombard` / fort-and-ship bombardment *is* genuine Col1 behaviour and is present.)

### C. Other FreeCol additions to keep out of the classic experience

Not rule divergences per se, but features absent from Col1 that our UI should not surface (or
should gate behind the ruleset): **trade routes**, the four extra nations (Portugal/Sweden/
Denmark/Russia) and their advantages, abandon-colony-anytime, and the "destroy all Europeans"
victory condition. The classic ruleset already restricts nations to the original four.

### Implications for this project

- **Scope:** UI only. We do **not** re-implement rules; we load `classic`. Items in **B** are an
  engine-fidelity backlog, explicitly *out of scope* for the UI phases unless we later choose to
  patch the engine.
- **Validation:** when the expert player flags a "that's not how Col1 behaves" issue, first
  classify it — UI rendering (our bug), a class-A option (check the default), or a class-B engine
  gap (known, backlog). This keeps UI iteration from getting derailed by rules questions.
- **Open task:** confirm in *our* build that (a) classic loads with the defaults in table A, and
  (b) the gross-vs-net Founding Father basis actually switches under classic.

### D. Roadmap to "as-classic-as-possible" (rules, including engine changes)

This is a **separate track** from the UI phases, runnable in parallel. Guiding principle, consistent
with this project's "additive changes only" decision: **prefer data/ruleset fixes; when engine code
must change, gate the new behaviour behind a ruleset option** (default = current behaviour for the
`freecol` ruleset, Col1-correct for `classic`). That keeps us mergeable with upstream `master` and
avoids regressing the default game. Each item below is tagged **[data]** (ruleset only) or
**[engine]** (code + a gating option), with a rough confidence/effort note.

**Phase R0 — Baseline & instrumentation** *(prereq for everything; low risk)*
- Launch with `--classic` *and* the classic ruleset; confirm table-A defaults actually load.
- Build a one-page **Col1 conformance checklist** the expert signs off against (combat odds,
  SoL ramp, immigration, REF, prices…). This is the acceptance instrument for R1–R3.
- Verify the gross-vs-net Founding-Father bell basis in *our* build; decide data vs engine fix.
- Confirm the class-B "community-reported" formulas against the engine source before acting on
  them (don't patch on forum lore). Promote each to confirmed/rejected.

**Phase R1 — Ruleset-only corrections** *(no engine risk; do first)* **[data]**
- Audit every `booleanOption`/modifier in `classic/specification.xml` against the checklist; flip
  any whose default isn't Col1-correct.
- Resolve the `expertsHaveConnections` doc-vs-repo discrepancy by decision, not assumption.
- Tune Col1-divergent modifier *values* (e.g. combat terrain/ambush/fortify/artillery-in-open) where
  the structure already exists and only the number is off.

**Phase R2 — Engine corrections, gated by option** *(the substantive work)* **[engine]**
- **Combat resolution** *(highest impact, hardest)* — implement a Col1-faithful combat path
  selectable via a new `model.option.combatModel` (values: `freecol` default, `classic`). Reproduce
  Col1's odds/terrain/ambush/fortification handling. Confidence: **confirmed gap**; effort: **high**.
- **Bell→SoL formula** — if confirmed as `bells/(pop+1)` vs FreeCol's `bells/(pop·2)`, add a
  ruleset-selectable formula. Effort: low–med once confirmed.
- **Movement-point carryover** and **river corner-cutting** — option-gated tweaks in the move logic.
  Effort: med; confirm first (R0).
- **Founding-Father gross-bell basis** — switch under classic if not achievable in data.
- **Food/horse growth ordering** — align ordering under a classic option. Effort: low.
- **REF-growth-vs-tax** — make REF growth scale with tax income under classic. Effort: med.

**Phase R3 — Missing Col1 features** *(net-new; largest)* **[engine]**
- Escalating native **tribute demands** before war; post-independence **trade with other Europeans'
  ports**; Custom House **rival-trade report** & post-war trade perks; the **9% tax bump** with the
  post-privateer frigate offer. Each is a discrete feature; schedule by the expert's priority.

**Explicitly out of scope (anti-fidelity if added):** keep trade routes, the four extra nations,
abandon-colony-anytime, and "destroy all Europeans" victory *off* in the classic experience. The
classic ruleset already restricts nations; ensure our UI doesn't surface the rest.

**Sequencing:** R0 → R1 (cheap wins, no risk) → R2 (combat first, it dominates feel) → R3 (by
priority). Track these as their own backlog; they do not block UI phases 0–3 and vice-versa.

*Sources:* [FreeCol user guide — ruleset comparison](https://www.freecol.org/docs/FreeCol.html),
[FreeCol "What Would Col1 Do?" wiki](https://sourceforge.net/p/freecol/wiki/What%20Would%20Col1%20Do%3F/),
[SF pending-feature #65 — combat differs under classic](https://sourceforge.net/p/freecol/pending-features-for-freecol/65/),
[Civ wiki — FreeCol divergences from Colonization](https://civilization.fandom.com/wiki/FreeCol_1.0.0/Divergences_from_Colonization),
and this repo's `data/rules/classic/specification.xml`.

## Design inputs: reference screenshots & where the art comes from

Two distinct things, do not conflate them:

- **Layout/interaction reference = screenshots of the *original* 1994 Colonization.** Yes — asking
  the expert for a screenshot of *every screen and key sub-state* is exactly right and is the
  primary design spec per screen. Guidance for the expert:
  - **Naming:** name each file after the screen so it maps to the `GUI` method / phase slice it
    drives, e.g. `colony.png`, `colony-drag-colonist.png`, `europe.png`, `europe-recruit.png`,
    `mapview.png`, `unit-orders-bar.png`, `combat-result-dialog.png`, `report-trade.png`,
    `negotiation.png`. Sub-states get a suffix (`-<state>`).
  - **Capture states, not just the idle screen:** open menus, an in-progress drag, a selected unit,
    a dialog mid-interaction — the *interactions* are as important as the static layout.
  - A one-line note per shot on what clicks/keys do is gold (we reuse the existing keyboard
    accelerators, so noting Col1's hotkeys helps us match them).
  - **Priority order** (so the contributor isn't overwhelmed — matches the phase sequence):
    1. **`mapview.png`** — the main map with the unit-orders/info bar, plus `mapview-unit-selected.png`
       and `mapview-menus.png` (top menu bar open). *Drives Phase 1; capture first.*
    2. **`colony.png`** — the signature colony screen, plus sub-states `colony-drag-colonist.png`,
       `colony-build-queue.png`. *Drives Phase 2, the biggest screen.*
    3. **`europe.png`** (+ `europe-recruit.png`, `europe-train.png`) and the **unit/cargo** view.
    4. One clean **terrain + unit sprite reference** (a varied map area) to speed A2 frame ID.
    5. **`report-*.png`** (the nine reports) and the **dialogs** (`combat-result-dialog.png`,
       `negotiation.png`, end-turn) — Phase 2/3, lower urgency.
  These reference shots are design input only; we render with FreeCol's own art (below), not the
  original game's copyrighted assets.

- **Render assets = FreeCol's own resources, not the original game's files.** Image keys are mapped
  to PNGs in `resources.properties` files and resolved via `ImageCache`/`ImageLibrary`. Locations:
  - `data/base/resources/images/`, `data/default/resources/images/` — base + default art. The
    base set **already defines the order-button icons** (`build`, `fortify`, `sentry`, `done`,
    `plow`, `road`, `disband`, …) that the `FreeColAction`s look up via
    `ImageLibrary.getButtonImages` — i.e. the icons our Phase-0 fix resolves come from `base`,
    not from any "classic" mod.
  - ⚠️ **`data/mods/classic_ui/` is NOT the original-game look — name is a false friend.** Its own
    description: *"UI graphics used by FreeCol up to v0.9.x."* "Classic" here means **classic
    *FreeCol*** (an old FreeCol parchment theme), **not classic *Colonization* (the 1994 game)**. It
    only reskins chrome (paper backgrounds, borders, an old minimap/infopanel skin). Useful as
    *asset plumbing* reference and maybe for a few textures, but it will **not** make the UI resemble
    the original game. Do not treat it as the design target — the expert's original-game screenshots
    are the design target; our own panels + rectangular `ClassicMapViewer` are the real work.
  - To find the asset behind any key, grep the `resources.properties` files for the key; the value
    is a path under that mod's `resources/`.

## Asset strategy: bring-your-own original install (DECIDED)

**Context:** personal-use build; goal is pixel-faithful original art *without* committing copyrighted
files. *Sid Meier's Colonization* is still sold (Steam/GOG "Classic" = the original DOS game in
DOSBox), so the user can legally own it and we load its art at runtime — the standard open-remake
pattern (OpenRCT2, ScummVM, OpenMW, Devilution).

**Decision:** **Ship no original assets. Load the original game's pixels from the user's own install.**
If no install is configured, fall back to FreeCol's free art so the build always runs. FreeCol art is
the *fallback skin*; original art is the *fidelity skin*.

### Pipeline — offline converter → local resource pack (reuse FreeCol's mod system)
Prefer one-time *conversion* over runtime format parsing:
1. A **converter** reads the original art from the install dir and writes PNGs into a **git-ignored**
   `data/mods/classic_original/` pack, plus a `resources.properties` mapping FreeCol resource keys →
   the extracted PNGs.
2. The classic UI loads that pack if present (like any other mod); otherwise base/default art is used.
3. Nothing copyrighted is committed — the generated pack is local-only (add to `.gitignore`).

**The converter is pure Java** (`net.sf.freecol.tools.classicassets`), part of the FreeCol source
tree and driven by `ant classic-assets`. There is **no Python / venv / Pillow / external tool** in
the pipeline any more — see "Decoder" below for why and how we got here.

### Original formats & tooling — VERIFIED against this user's GOG install (A0 done)
Install path (GOG): `C:\Program Files (x86)\GOG Galaxy\Games\Colonization`; assets live in
`MPS\COLONIZE\` (290 files). Confirmed by magic bytes (`MADSPACK 2.0` header on every graphic):
- **All graphics are `MADSPACK 2.0` containers** (not `.PIC` as earlier web research suggested —
  that was for *other* MicroProse titles). Two graphic kinds:
  - **`.PIK`** (35 files) — full-screen 320×200 pictures = the game's *screens* & chrome (see manifest below).
  - **`.SS`** (206 files) — sprite/"shape" sets, multiple frames per file (units, terrain, buildings, icons).
- **Palette:** `VICEROY.PAL` (1024 bytes = a 768-byte 256×3 six-bit-VGA RGB palette + a 256-byte
  trailer we ignore) is the master 256-colour gameplay palette, used to decode the palette-less PIK
  screens (`COLONY.PIK`). *(`ASOUND/GSOUND/PSOUND/RSOUND.COL` are sound-driver configs, NOT palettes.)*
- **Format reference:** [`eb4x/viceroy`](https://github.com/eb4x/viceroy) / re:Colonization and the
  format notes in [`institution/mpskit`](https://github.com/institution/mpskit).

### Decoder — native Java, clean-room from the format spec (A1 done)
The whole read path is a small, self-contained slice of the MADSPACK format, so we **reimplemented
it directly in Java** rather than depend on an external tool:
- **`MadsPack`** — MADSPACK 2.0 container: 12-byte magic, `uint16` part count at offset 14, a 0xA0
  header block of `(uint16 flag, uint32 size, uint32 csize)` entries, then the parts. Per-part
  bit-0 flag selects FAB compression.
- **`Fab`** — the one non-trivial algorithm: an LZ-style bit-stream decompressor (literal / copy
  commands, LSB-first bit reader with the 16-bit refill quirk).
- **`Palette`** — 6-bit VGA → 8-bit (`v*255/63`); reads both the embedded `.SS`/`.PIK` palettes and
  the master `VICEROY.PAL`.
- **`SsDecoder`** — `.SS` sprite sets: per-sprite header table + the linemode/`FE`/`FD`/`FF`/`FC`
  RLE, decoded straight into `TYPE_INT_ARGB` `BufferedImage`s (palette index `0xFD` → transparent).
- **`PikDecoder`** — `.PIK` full screens: raw 8-bit indexed blit, embedded palette or `VICEROY.PAL`.
- **`ClassicAssetConverter`** — the `main`: walks the install dir, decodes every `.SS`/`.PIK`, writes
  the PNGs + `resources.properties` + `mod.xml` + messages, appending `aliases.properties` (A2).

**Why Java, not the earlier Python/mpskit route.** We first wrapped `mpskit` (AGPLv3, Python) and
hit its Pillow-9 `ImagePalette` palette-scrambling bug plus a palette-less-`COLONY.PIK` gap, patched
around both with a monkeypatch wrapper — brittle, and it dragged in a venv + pinned `Pillow<10`
(⇒ Python 3.9–3.11 only) + a network fetch. Decoding into a `BufferedImage` ourselves makes those
bugs structurally impossible (we never touch an indexed-PNG palette), removes every non-Java
dependency, and — decisively — is a **prerequisite for runtime extraction** (below). Written
clean-room from the format documentation, so it is our own code under FreeCol's **GPLv2+** (an
AGPLv3 line-by-line port would have been license-incompatible with upstream).

### Runtime extraction — future direction (A5, not yet built)
Today extraction is build-time (`ant classic-assets`). The intended end state is **in-game**: the
user points the game at their Colonization install in settings and the classic art is decoded on
demand. The Java decoder above is exactly what unblocks this — the same classes run in-process, so
A5 is mostly UI plumbing on top:
- a settings field + directory picker (validate by MADSPACK magic bytes, as A0 did);
- on confirm, run the decoder → write the **same** git-ignored `classic_original` pack, then load it
  as a mod (reuses all of A2/A3's plumbing unchanged); later, decode straight into `ResourceManager`
  to skip the disk pack.
- ⚠️ **Switching the *UI* at runtime ≠ switching *assets*.** The GUI is chosen once at
  `FreeColClient` construction, so "switch to classic UI in settings" is realistically a
  **preference + restart** (an in-place `SwingGUI`↔`ClassicGUI` swap is a much larger job). Scope A5
  as restart-to-apply. FreeCol art remains the fallback skin when no install is configured.

### Original audio — SFX & music (A6, not started)
The install also holds the original sounds, in two tiers of very different difficulty (file headers
inspected 2026-07-07):
- **Digital SFX — `COLDIG.BIN` (~970 KB): feasible.** Raw **unsigned 8-bit PCM** (the bytes sit
  around the `0x7F/0x80` silence midpoint) — the digitized sound-effect bank. Extractable to WAV
  once its internal layout is cracked (an index/offset table of individual effects + the sample
  rate). Mirrors the graphics pipeline: a small clean-room reader in `net.sf.freecol.tools`,
  output WAVs + a `sound.classic_original.*` mapping aliased onto FreeCol's `sound.*` keys,
  bring-your-own-install, ship nothing.
- **Music — `AMER2.MP` + `*SOUND.COL`: hard, poor ROI.** The `A/G/P/RSOUND.COL` files start with
  `MZ` — they are DOS sound-*driver* executables (AdLib/Gravis/ProAudio/Roland), not audio.
  `AMER2.MP` is MPS synth **sequence** data (XMIDI-like note events) meant to *drive* an AdLib /
  Roland MT-32 chip, not sampled audio — faithful playback needs both a sequence parser and chip
  synthesis (or a bundled soundfont). Punt: FreeCol's own music is an acceptable fallback, and
  recording DOSBox output beats writing an MPS synth.

**Sequencing:** audio gates nothing (FreeCol's sound set is the fallback, exactly as its art is).
Do the SFX only *after* the UI phases that trigger them; treat music as out-of-scope unless the
expert deems the original score essential.

### Original-game screen manifest (from the 35 `.PIK` files) — drives UI phasing & the expert's shot list
`COLONY` (colony screen), `EUROPE` (Europe), `REPORT1`–`REPORT9` (the nine reports), `NATIONS`
(nation select), `DIFFICUL` (difficulty), `CUSTOMIZ` (customise), `DECLARAT`/`DECOIND` (declare
independence), `OPENING`/`OPENMENU`/`OPENBORD` (title/menu), `KINGLSS1/2` (king screens),
`WOODPANL`/`WOODPAN2` (wood-panel UI chrome), `CLOS-BKG`/`CCBKGD` + `LEVN0001`–`LEVN0010` (intro/closing
art). This enumerates exactly which screens exist — use it to scope the UI phases and to give the
expert a complete screenshot checklist.

### Asset backlog & status (own track; UI phases 0–2 proceed on fallback art meanwhile)
- **A0 ✅** — assets located & format confirmed: GOG `…\Colonization\MPS\COLONIZE\`, all MADSPACK
  2.0 (`.PIK` screens, `.SS` sprite sets, `VICEROY.PAL` palette).
- **A1 ✅** — native-Java MADSPACK/FAB/SS/PIK decoder + converter
  (`net.sf.freecol.tools.classicassets`, driven by `ant classic-assets`, no external tool);
  produces the git-ignored `data/mods/classic_original/` pack (35 PIK screens + 1517 SS frames).
- **A2 🔨 (growing)** — key-mapping table in committed `tools/classic_assets/aliases.properties`
  (appended into the pack by the converter, so regenerating never clobbers it). Mapped so far:
  `image.background.MainPanel`→`…pik.OPENING.PIK` and `image.background.ColonyPanel`→`…pik.COLONY.PIK`
  (title screen shows live); **all map terrain** — the eight base land types, eight forest types,
  and ocean/lake/greatRiver/highSeas/arctic/hills/mountains — aliased to `TERRAIN.SS` frames and
  **rendering live** on the map (Phase 1a); the `PHYS0.SS` physical-feature overlays are loaded by
  key (not alias) by `ClassicTileArt` for Phase 1e — including the `PHYS0.SS` **coast/beach
  quarter-tiles** that feather water/land borders; and the `ICONS.SS` **unit map-sprites (all 194
  base+role `image.unit.model.unit.*` keys) and goods icons (22 `image.icon.model.goods.*` keys)**
  are aliased and **rendering live**; and the `ICONS.SS` **colony & native-settlement sprites**
  (colonies by size×stockade over per-nation keys, camps/villages/inca/aztec by type) are aliased and
  render through the same `drawCentered` up-scale path (Phase-1 map-fidelity polish — see those
  bullets for the frame maps). **This curation is the bulk of the asset work** — remaining: per-nation
  unit/colony tints and a fortress-distinct colony frame, then the colony/europe/report screens with
  Phase 2, driven by the expert's screenshots.
- **A3 ✅** — pack loader: when `--classic`, `FreeColClient.withClassicOriginalPack` overlays the
  pack as the highest-priority mod (at the `ResourceManager.setMods` call), with graceful fallback
  when it is absent. The one-line `mod.xml` is a valid descriptor (identical to every
  `data/mods/*/mod.xml`), so no `--classic-assets <dir>` option was needed.
- **A5 ⬜** — runtime (in-game) extraction: install picker → decode on demand, reusing the A1
  decoder in-process; restart-to-apply UI switch. See "Runtime extraction" above.
- **A6 ⬜** — original audio: SFX (`COLDIG.BIN`) feasible, music hard. See "Original audio" above.
  Do the SFX after the UI phases that trigger them; music likely out-of-scope.

## Architecture findings (why this is feasible)

FreeCol is cleanly layered (client/server):

- **Model** — `common/model/` (138 classes; `Unit`, `Colony`, `Player`, `Tile`, `Map`…). UI-agnostic; the client holds a synced copy, the **server** (`server/`) is rules-authoritative.
- **Controllers** — `client/control/` (`InGameController`, `PreGameController`, `ConnectController`). UI-agnostic; reach the view **only** via `getGUI().<method>`.
- **View facade** — `client/gui/GUI.java`: a **concrete base class, 0 abstract methods, no-op stubs** (e.g. `public void changeView(Tile tile) {}`), **217 public methods**. `SwingGUI extends GUI` overrides **174**.

Selection point — `client/FreeColClient.java:243`:
```java
gui = (FreeCol.getHeadless()) ? new GUI(this) : new SwingGUI(this);
```
FreeCol already runs with a non-Swing view (base `GUI`, headless). So a new UI = another `GUI`
subclass; unoverridden methods no-op, so **the app runs from day one** and we light up screens
incrementally. Each base method's Javadoc names its callers.

## Decisions

- **Technology: Swing**, as `ClassicGUI extends GUI` in a new `client/gui/classic/` package. The
  whole client is Swing/AWT-bound (ImageLibrary, FontLibrary, FreeColFrame, plaf, 86 Action
  classes); a rectangular-tile map is trivial in `Graphics2D`. Non-Swing would mean reimplementing
  all that plumbing for no gain.
- **Additive changes only**: new `client/gui/classic/` package + a one-line selector change +
  a `--classic` flag. Keeps us mergeable with upstream `master`.

## Reuse map

| Component | Reuse |
|---|---|
| `common/model` | 100% (read state directly) |
| `client/control/*` controllers | 100% |
| Networking, `ClientOptions`, Image/Font/Sound resources | 100% |
| `client/gui/action/` (86) | High |
| `client/gui/mapviewer/` (5210 lines) | Partial — reuse image-selection logic, replace isometric→rectangular projection (`TileBounds`/`MapViewerBounds`) |
| `client/gui/panel/` (101) + `dialog/` (33) | Optional — reskin our own, or delegate temporarily |

## History note

Earlier iterations of this plan carried a separate "Immediate next steps" and "Status" section;
both have been folded into the top-of-doc **Status at a glance** snapshot plus the inline status
markers in the Phased-plan and Asset-backlog sections, to keep a single source of truth. The
asset decoder's own history (the abandoned Python/`mpskit` route and its Pillow-9 palette bug,
replaced by the clean-room Java decoder) is preserved in the git log and the "Decoder" section
above.
