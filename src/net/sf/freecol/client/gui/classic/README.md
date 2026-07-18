# Classic UI (`net.sf.freecol.client.gui.classic`)

A primitive, original-1994-*Colonization*-style view for FreeCol, built as an
alternative `GUI` implementation on top of the unchanged engine. This document
is the **implementation reference** for the code in this package — the durable
"how it works / why" that outlives the phase-by-phase tracker in the repo-root
[`CLASSIC_UI_PLAN.md`](../../../../../../../CLASSIC_UI_PLAN.md) (which tracks
*remaining* work and the broader asset/rules tracks).

## What this is & how it is selected

FreeCol is cleanly layered: `common/model` (UI-agnostic game state), the
`client/control/*` controllers (reach the view only via `getGUI().<method>`),
and the view facade `client/gui/GUI.java` — a **concrete base class with no
abstract methods and no-op stubs** (used as-is in headless mode). A new UI is
therefore just another `GUI` subclass: unoverridden methods no-op, so the app
runs from day one and screens light up incrementally.

Selection point, `client/FreeColClient.java`:
```java
gui = headless ? new GUI(this) : classic ? new ClassicGUI(this) : new SwingGUI(this);
```
The `--classic` command-line flag picks `ClassicGUI`. The change to upstream is
purely additive: this package + a one-line selector change + the flag, so the
fork stays mergeable with `master`.

Run it (from the repo root, so `data/` is found):
```powershell
ant compile
java -Xmx2G -cp "build;jars/*" net.sf.freecol.FreeCol --classic --fast --no-intro
```
`--fast --no-intro` auto-starts a single-player game with no GUI clicks — the
fastest way to the in-game view. It starts at sea: the ship on an ocean patch.

## `ClassicGUI` — the view facade

`ClassicGUI extends GUI` and overrides only the methods it implements:

- **Lifecycle.** `startGUI` shows the main `JFrame` (title-screen background is
  the original `OPENING.PIK`, painted via the FreeCol key
  `image.background.MainPanel` when the `classic_original` asset pack is loaded;
  otherwise base art). `reconnectGUI(active, tile)` — the game-start hook fired
  by `FreeColClient.restoreGUI` — builds the Phase-2 HUD: a `BorderLayout`
  content pane with the `ClassicMapViewer` in the centre, the `ClassicInfoPanel`
  on the right (`EAST`), and the reused `InGameMenuBar` as the frame's menu bar;
  then seeds the initial view state/focus. `quitGUI` disposes the frame. See
  "Phase 2 HUD" below.
- **`installLookAndFeel(fontName)`.** The base `GUI` no-ops this, leaving
  `FontLibrary`'s main font null — which NPEs once the reused `InGameMenuBar`
  paints its golden gold/tax/year status line via `FontLibrary.getMainFont()`.
  So the classic GUI overrides it to create the main font (and set the
  image-border scale factor so the menu bar's wood border renders). It
  deliberately does **not** install `FreeColLookAndFeel`: that L&F swaps in a
  `PanelUI` that paints the parchment texture behind every `JPanel`, which would
  override the classic map's black fog and the dark info panel. The menu bar
  paints its own parchment background + wood border regardless of the L&F, so the
  top bar still reads classic; only the dropdown popups fall back to default
  Swing styling.
- **Pre-game lobby stopgap.** There is no classic lobby yet, so
  `showStartGamePanel` auto-launches single-player games (`player.setReady(true)`
  + `requestLaunch`); otherwise a new game stalls at login because the base
  `GUI` no-ops the lobby. Multiplayer no-ops.
- **View-state delegation.** `changeView(Tile|Unit|)`, `getViewMode`,
  `getActiveUnit`, `getSelectedTile`, `getFocus`/`setFocus`,
  `refresh`/`refreshTile` all delegate to the `ClassicMapViewer`, which *owns*
  the classic view state. `refresh`/`refreshTile` also call
  `invalidateMinimap()` (they are the model-change hooks).
- **Image libraries.** `getFixedImageLibrary`/`getScaledImageLibrary` must be
  non-null even at scaffold stage: `ActionManager` builds every `FreeColAction`
  regardless of the active view and several look up order-button icons from
  their constructors. One unscaled `ImageLibrary` serves both today.
- **`showColonyPanel` — the classic colony screen.** A click on an owned colony
  (and the automatic open when one is founded) shows the `ClassicColonyPanel` in a
  window of its own (the classic UI has no `Canvas` to host panels in). Only one
  colony screen is open at a time — opening another disposes the previous — and
  the whole thing is guarded so a failure degrades to a log line. See "Colony
  screen" below.
- **Dialog seams (`modalConfirmDialog` / `modalChoiceDialog` / `getNewColonyName`).**
  The classic dialogs are Phase 3, but three controller flows can't proceed
  without an answer, so these are wired now with plain (unstyled) Swing dialogs:
  `modalConfirmDialog` (e.g. the build-colony site warnings), `modalChoiceDialog`
  (e.g. which unit(s) to disembark from a laden ship) and `getNewColonyName`
  (which returns FreeCol's suggested name, made unique, rather than prompting —
  the base `modalInputDialog` still no-ops). Without these, founding a colony —
  and hence the colony screen — would be unreachable. All run their dialog on the
  event thread via `onEventThread` (controllers call from arbitrary threads).
  Phase 3 reskins them.

## `ClassicMapViewer` — the map

A `JPanel` that renders the map on a **plain rectangular grid** (where
`SwingGUI`'s `MapViewer` paints isometric diamonds) centred on a focus tile,
mirroring the original game. It owns the view state
(`viewMode`/`focus`/`selectedTile`/`activeUnit`) and holds a back-reference to
`ClassicGUI` so clicks/keys route through the `GUI`/controller path exactly as
`SwingGUI.clickAt`/`MoveAction` do.

**Projection** (square 48px cells = native 16px × `CLASSIC_SCALE` 3):
```
screenX = width/2  + (tileX - focusX) * TILE_W - TILE_W/2
screenY = height/2 + (tileY - focusY) * TILE_H - TILE_H/2
```
`tileAt(px,py)` is the inverse (via `Math.floorDiv`).

**Terrain rendering.** The original rectangular `TERRAIN.SS` tiles fill the grid
cleanly (no diamond gaps). Tiles are fetched at native 16×16 via
`ImageLibrary.getTerrainImage(type, x, y, SRC_SIZE)` and up-scaled
nearest-neighbour (`VALUE_INTERPOLATION_NEAREST_NEIGHBOR`) so the chunky classic
pixels stay crisp. Unexplored tiles are left black (classic fog). The
key→frame mapping lives in `tools/classic_assets/aliases.properties`. When the
asset pack is absent the same keys fall back to FreeCol's own (isometric) art.

**Unit & goods sprites.** The original *Colonization* unit map-sprites and goods
icons come from `ICONS.SS`, aliased onto FreeCol's own resource keys in the same
`aliases.properties` (all 194 `image.unit.model.unit.*` base+role keys and 22
`image.icon.model.goods.*` keys). Because Col1 draws every civilian colonist with
one generic map sprite and one sprite per *role*, the aliases collapse to a small
set of frames: a base colonist for all civilian types, shared soldier / dragoon /
scout / pioneer / missionary role sprites, one frame per ship, and wagon /
artillery / treasure / brave / regular. The full frame table is documented in the
"Units & goods" block of `aliases.properties`. Goods icons only surface on the
Phase-2 colony/Europe screens; the unit sprites render on the map immediately.

**`drawCentered` — cell-fit for both art styles.** `paintTile` draws the
settlement/unit sprite through `drawCentered`, which sizes it to the cell
(preserving aspect) by branching on source size — which doubles as pack
detection. The original `ICONS.SS` sprites are ~16px, so they fit *inside* the
48px cell and are **up-scaled** to `UNIT_CELL_FRACTION` (0.9) of it,
nearest-neighbour like the terrain (else a 16px unit renders tiny). FreeCol's own
pack-absent art is sized for its 128×64 tiles, larger than the cell, and is
**shrunk** to fit as before. `paintTile` draws the settlement sprite the same way,
through `getScaledSettlementImage` (native ~16px, so the up-scale branch fires),
mirroring `getScaledUnitImage` — *not* `getSettlementImage(…, TILE_SIZE)`, which
would pre-size to 128×64 and bypass the crisp branch. Per-nation unit/colony
tinting and a fortress-distinct colony frame are documented follow-ups in
`CLASSIC_UI_PLAN.md`.

**Settlement sprites.** Colony and native-settlement map-sprites are aliased onto
FreeCol's `image.tileitem.model.settlement.*` keys in the same `aliases.properties`
(the "Settlements" block). Native settlements key off the settlement-type id
(`camp`/`village`/`inca`/`aztec` = `ICONS.SS` `010`/`011`/`012`/`013`). Colonies are
subtler: `ImageLibrary.getSettlementKey` builds `…colony.<size>[.stockade|.fort|
.fortress]` and then *prefers a per-nation key when one exists* — and the base pack
defines one for every European nation — so a neutral `…colony.small` alias would
never be reached. The block therefore overrides all 8 nation suffixes directly,
folding Col1's real signal (fortification) onto FreeCol's size×stockade grid:
unfortified `003`(small)→`000`(medium/large), stockade `001`, fort/fortress the
stone `002`. See the "Settlements" block of `aliases.properties` for the full table.

**Edge scrolling.** A mouse-motion listener sets an edge direction when the
cursor enters a ~1-tile hot zone at any window edge/corner; a repeating `Timer`
pans the focus by a **raw** rectangular-grid step while the mouse stays there
(stopped on `mouseExited`). Raw `(x,y)` steps, *not* `Direction.step` — the
isometric N/S step jumps two raw rows. Suppressed while the mouse is over the
minimap box.

**Controller wiring (clicks & keys drive the real `InGameController`).**

- **Click** ports `SwingGUI.clickAt`: unexplored → `setFocus`; owned colony →
  `showColonyPanel`; owned unit → `changeView(unit,false)` (make active);
  foreign unit/settlement → `setFocus`; empty explored tile → `changeView(tile)`
  (TERRAIN-select). A single click already terrain-selects (the rectangular grid
  has no drag-vs-click ambiguity), which also arms the TERRAIN cursor keys.
  Minimap-region clicks are intercepted *before* this (see below).
- **Keys** mirror `MoveAction`: arrow / numpad 8-2-4-6 orthogonal, numpad
  7-9-1-3 & Home/PageUp/End/PageDown diagonal, bound `WHEN_IN_FOCUSED_WINDOW`.
  MOVE_UNITS → `InGameController.moveUnit(activeUnit, dir)`; TERRAIN → step the
  selected-tile cursor to `getNeighbourOrNull(dir)`; nothing selected (END_TURN)
  → raw-grid free pan so the map stays navigable. After a unit move the focus
  follows the unit.
- **⚠️ Isometric-vs-rectangular caveat (resolved).** Model `Direction` is
  isometric (`Direction.N` steps two raw rows), but this viewer draws a raw grid.
  The four orthogonal keys therefore resolve — parity-aware, via
  `Map.getDirection` — to the `Direction` whose *raw* step lands on the visually
  adjacent cell (e.g. straight-up is `NE` on even rows, `NW` on odd), so
  on-screen movement matches the key. The four diagonal keys map to the
  isometric corner directions, whose raw offset shifts with row parity — an
  inherent flattening artefact, documented on `intentToDirection`.
- **Turn controls (classic-*Colonization* key scheme).** Because there is no menu
  bar / `Canvas` to install FreeCol's own accelerators, the viewer binds the
  turn-control keys directly (`WHEN_IN_FOCUSED_WINDOW`, like the movement keys),
  driving the real `InGameController`. Keys follow the original 1994 game (from its
  manual — see `bindTurnControls`):
  - **Enter** → `endTurn(false)` — end the turn. `false` because the classic `GUI`
    no-ops modal dialogs, so `endTurn(true)`'s "units still active" confirm would
    misbehave. Verified live: the turn advances (AI players process, a new turn
    begins) and the map/minimap refresh.
  - **Space** → "no orders": skip the active unit for this turn, mirroring
    `SkipUnitAction` (`changeState(unit, SKIPPED)` then `nextActiveUnit()`). With no
    active unit, Space ends the turn instead (as in the original, where Space
    advances the turn once every unit is done).
  - **W** → wait: `InGameController.waitUnit()` — cycle to the other units needing
    orders and return to this one.
  - **B** → build colony: `InGameController.buildColony(activeUnit)`, mirroring
    `BuildColonyAction` (the original manual: "To build a colony, press the build
    key (B)"). Guarded by the same precondition as the action's `shouldBeEnabled`
    (`hasTile() && canBuildColony()`), so pressing B with a ship or a spent unit
    selected quietly does nothing. The controller does the rest — it confirms any
    site warnings (`modalConfirmDialog`), names the colony (`getNewColonyName`) and
    on success opens the colony screen. This is the only way to reach the colony
    screen live, since `--fast` starts at sea; the flow is **sail to land →
    disembark a colonist → press B**.
- **Next-active-unit at turn start (caveat).** After `endTurn`, whether a fresh
  active unit is auto-selected is up to the controller's `setCurrentPlayer` →
  `updateActiveUnit` → `changeView(unit)` path, which the classic `GUI` already
  delegates to `changeToMoveUnits` (so it *does* re-centre on the next active unit
  when `player.hasNextActiveUnit()`). Movement was verified live post-end-turn
  (the active ship moves and the focus follows it); note that panning the focus
  away (e.g. a minimap click) leaves the active unit's cursor off-screen until a
  movement key re-centres on it.

**Minimap raster.** A whole-map overview: a plain rectangular
`map.getWidth() × map.getHeight()` raster — **no** isometric projection (unlike
`client/gui/panel/MiniMap`, which is the colour-source reference only). The map
viewer *builds and caches* it (it is map data); it is **drawn by
`ClassicInfoPanel`**, which hosts the minimap at the top of the right column as
the original does. (It began as a bottom-left overlay on the map itself; the
"HUD minimap" slice moved it into the info panel — see "Phase 2 HUD" below.)

- Colours: unexplored → `ImageLibrary.getMinimapBackgroundColor()`; explored →
  `getMinimapPoliticsColor(tile.getType())`; a tile with a settlement/unit →
  the owner's `getNationColor()`. All guarded with fallbacks (`orElse`).
- Sizing: integer pixels-per-tile `max(1, MINIMAP_MAX/max(w,h))` fits the raster
  into a ~200px box, so a tall/narrow map renders as a vertical strip.
- **Caching / performance:** the raster is cached in a `BufferedImage` and
  rebuilt only when `invalidateMinimap()` marks it dirty — wired to
  `ClassicGUI.refresh`/`refreshTile`, the model-change hooks (exploration, new
  settlements, unit moves). `getMinimapImage()` returns the cache, rebuilding if
  stale; the panel blits it every repaint, so no per-repaint tile iteration.
- **Accessors for the panel:** `getMinimapImage()`, `getMinimapPixelsPerTile()`,
  `getViewHalfCols()`/`getViewHalfRows()` (the visible tile span, for the panel's
  viewport box) and `recenterOnTile(x,y)` (a minimap click → `setFocus`). The old
  in-viewer `paintMinimap`/`minimapClick`/`minimapBounds` and the edge-scroll
  suppression over the overlay box were removed with the move.

**Feature overlays (item (e)).** On top of the base terrain, `paintTile`
composites the per-tile *physical features* — forest trees, hills, mountains,
rivers, roads, plowed fields, resource markers and the lost-city rumour — via
`ClassicTileArt`, before the settlement/unit sprite. This is the last
map-fidelity slice, and its crux was **asset shape, not code**:

- **The overlays live in `PHYS0.SS`, not `TERRAIN.SS`.** `TERRAIN.SS` holds
  only the 12 base terrains; the original game drew a cell as a base tile plus
  square 16×16 *overlay* sprites, and those are a separate 154-frame set,
  `PHYS0.SS`. Being square (like the base tiles), they composite onto the
  classic rectangular cells with **no skew** — which is exactly why FreeCol's
  own isometric-diamond overlay art could not be reused here.
- **Frame map** (verified by pixel edge-analysis of the extracted frames, see
  the `ClassicTileArt` class comment for the table): all the *directional*
  feature sets share one 4-bit connectivity encoding — the frame within a set is
  `(E?1) | (W?2) | (S?4) | (N?8)` over the neighbours that also carry the
  feature — with bases minor-river `0`, major-river `16`, mountains `32`,
  hills `48`, forest `64`. Roads are composited instead: frame `80` is the
  centre hub and `81..88` the eight directional spokes (N…NW clockwise), one
  drawn per neighbour with a road. `103` is the lost-city rumour, `149` the
  plowed field, `89..102` the resource markers (a provisional read pending the
  expert's validation).
- **Connectivity is over *raw-grid* neighbours** (the cells drawn directly
  up/down/left/right and at the corners), not FreeCol's isometric
  `Direction`s, so a feature blends with whatever is *visually* adjacent on the
  square grid. Area features (forest / hills / mountains) use the four cardinal
  neighbours. Rivers are linear and FreeCol lays them along the isometric
  long-sides — which flatten to raw *diagonals* — so each diagonal neighbour
  with a river folds into its two adjacent cardinal bits, keeping a
  diagonally-running river visually connected. Roads draw a spoke toward each of
  the eight raw neighbours that has a road.
- **Loading & fallback.** The frames are exposed by the `classic_original` pack
  under the keys `image.classic_original.ss.PHYS0.SS.NNN` and loaded straight by
  key (cached), so no new alias entries are needed. When the pack is absent
  (`packPresent` false) `ClassicTileArt` falls back to FreeCol's own
  `getForestImage` / `getSizedOverlayImage` / `getRiverImage` — imperfect
  (isometric-shaped) on the square grid but enough to keep the build running.
- **Live-verification note.** Forest compositing was confirmed live by sailing
  the start ship to a coast (`--fast` starts at sea, so land must be reached to
  see overlays). Rivers/hills/mountains/roads share the *identical* draw path
  and 16×16 square sprites, so they render the same way; the classic UI's
  reconnect stopgap makes FreeCol's debug "reveal map" ineffective (it resyncs
  via a reconnect the classic UI does not fully reload), so inland features are
  reached by sailing rather than revealed.

**Coastline (beach feathering).** On top of the base ocean, `paintOverlays`
also feathers the land/water border for every **water** cell (`!tile.isLand()`)
with the original `PHYS0.SS` coast quarter-tiles, so borders blend like Col1
instead of showing a hard edge. The crux was again asset RE:

- **32 8×8 quarter-tiles, frames `108..139`** — `4 corners × 8 configs` laid out
  `frame = 108 + config*4 + corner`, corner clockwise `NW=0, NE=1, SE=2, SW=3`.
  One sub-tile is drawn per cell quadrant; for a given corner the two orthogonal
  neighbours bounding it and the diagonal neighbour select the config:
  `config = (ccwEdgeLand?1) | (diagLand?2) | (cwEdgeLand?4)` (verified
  rotationally consistent across all four corners — config 1 = the
  counter-clockwise edge neighbour is land, 4 = the clockwise edge, 2 = a
  diagonal-only neighbour draws a light coastal-water wedge, 0 = open ocean draws
  nothing). The per-corner neighbour offsets live in `COAST_CORNERS`.
- **Two decode quirks masked the scheme in earlier recon:** (i) these frames
  encode transparency as **opaque black** (a colour-key, index 0) rather than the
  `0xFD` alpha the other sets use — so `108..111` ("empty") are config 0; (ii)
  `116..119`'s "water but no land" are config 2, the diagonal-only coastal-water
  wedges. The decoder leaves that colour-key black **opaque**, so
  `ClassicTileArt.frame` keys pure black out to alpha 0 when it loads a coast
  frame (`keyOutBlack`, cached) — otherwise plain `drawImage` would paint the
  black regions as opaque wedges over the sea along every coastline. With the
  black keyed out the base ocean shows through and `paintCoast` just draws the
  8×8 sub-tile into its quadrant.
- **Not yet wired:** the estuary/river-mouth pieces — `140..147` (ocean
  corner-hints) and `150..153` (diagonal sand strips). Deferred (river mouths).

## Phase 2 HUD (menu bar + info/orders panel)

The map view no longer fills the whole frame. `ClassicGUI.reconnectGUI` composes
the classic map screen: the `ClassicMapViewer` in the centre, a `ClassicInfoPanel`
strip on the right, and a menu bar on top. This is the **first Phase-2 slice**,
driven by the expert's original-game screenshots (local `screenshots/`, git-
excluded); it is functional, not yet pixel-faithful chrome.

- **Menu bar — reused `InGameMenuBar`.** Rather than build a classic menu bar, the
  frame uses FreeCol's own `InGameMenuBar` (the sanctioned "reuse `action/`"
  path): five menus (Game / View / Orders / Report / Colopedia) already wired to
  the real `FreeColAction`s, plus a golden gold/tax/score/year status line it
  paints itself. Building the bar only looks up pre-built actions, so it is safe.
  In the user's locale the menus render localized (German in the expert's shots).
- **Menu-bar contrast — `ClassicGUI.styleClassicMenuBar`.** The original's bar is a
  **dark strip with light labels** (design ref: `opening_008`); FreeCol's reused bar
  is dark-on-parchment, which read as nearly illegible in our HUD. The hook is
  `FreeColMenuBar.paintComponent`, which tiles its parchment background **only while
  the bar is non-opaque** and otherwise defers to `super.paintComponent` — so making
  the bar **opaque with a dark background** swaps the parchment for Col1's dark
  strip, and the menu labels are then re-coloured light. The bar's own golden status
  line already reads on dark (`InGameMenuBar.paintComponent` draws it *after* the
  background), and the wood border is kept — it still reads classic.
  - Safe **after construction**: `InGameMenuBar.reset()` rebuilds (and would
    re-create) the menus, but it is only called from its own constructor and from
    `FreeColFrame`, which the classic UI does not use.
  - **Still deferred:** the dropdown popups keep default Swing styling (light
    background, dark text) — the Phase-3 reskin covers them. *Triggering* a few
    items still reaches `GUI` methods the classic UI no-ops.
- **`ClassicInfoPanel` — the right strip.** A fixed-width (240px) `Graphics2D`-
  painted panel echoing the original's right column, backed by the original's
  **`WOODPANL.PIK`** wood-panel texture (`paintWoodChrome`, washed slightly darker
  so the gold/parchment text keeps contrast; flat dark ground when the pack is
  absent). `WOODPANL.PIK` has a dark ornamental border on all four edges, so
  tiling the whole image repeats its top/bottom border mid-strip as hard
  horizontal seams; instead `paintWoodChrome` tiles only the **central grain band**
  (the borderless middle 40% of the source) — the left/right border *columns* are
  full-height so they stay continuous and keep the framed look, while the seams
  fall in uninterrupted wood — and **flips every other copy vertically** so
  adjacent copies meet at a matching grain edge (a mirror fold) rather than a hard
  discontinuity. It
  reads live state directly from the model (`game.getTurn()`, `player.getGold()`
  / `getTax()`) and from the `ClassicMapViewer`'s view state (`getActiveUnit` /
  `getSelectedTile`), showing top-to-bottom: the **minimap** (at the very top, as
  in the original — `paintMinimap` draws the map viewer's cached raster scaled to
  the strip width, framed, with the viewport box, and recentres the map on a
  click; see "Minimap raster" above); turn (season+year), gold, tax; then
  the active unit — its localized `getLabel` on its own line, then a **portrait**
  (`paintUnitPortrait`: the unit's map sprite on a dark plate, left of the
  `getMovesAsString` / terrain lines, as the original puts a sprite beside each
  unit — design ref `opening_006`/`opening_008`; the name keeps a full-width line
  because our localized labels, "Pionier (Freier Kolonist)", are far longer than
  Col1's and would not fit beside a 36px portrait in a 240px strip). The `ICONS.SS`
  sprites are ~16px, so the portrait up-scales them **nearest-neighbour**, the same
  crisp-pixels treatment the map gives them. In TERRAIN mode the selected tile's
  terrain shows instead; then the **order buttons** (below); and a bottom reminder
  of the classic order keys (Enter / Space / W, each followed by its **localized**
  action name via the reused `endTurnAction`/`skipUnitAction`/`waitAction` `.name`
  keys). It
  is repainted by `ClassicGUI`'s `repaintInfo()` on every
  `changeView`/`refresh`/`refreshTile`, so it tracks the active unit and treasury
  live (verified: moving the ship updated Moves 5/5 → 3/5 and the terrain line). The
  Gold/Tax/Moves captions are localized (`gold`/`tax`/`infoPanel.moves`).
- **Order buttons (the "orders" half).** The panel's lower half hosts the original's
  unit-order buttons by **reusing the real `FreeColAction`s** (the sanctioned "reuse
  `action/`" path). Each order action carries its own four-state order-button art
  (`FreeColAction.BUTTON_IMAGE`, populated from `ImageLibrary.getButtonImages`),
  enables itself via `shouldBeEnabled`, and its `actionPerformed` drives the real
  `InGameController`. `paintOrderButtons` walks a curated id list
  (`fortifyAction`, `sentryAction`, `buildColonyAction`, `roadAction`, `plowAction`,
  `clearForestAction`, `waitAction`, `skipUnitAction`, `disbandUnitAction`), looks
  each up in the `ActionManager`, and paints the `BUTTON_IMAGE` of every one that is
  **currently enabled** into a wrapped icon grid — recording each rectangle so
  `onClick` can fire the matching action (`onHover` highlights). Because the buttons
  read the same enabled state the menu items do (kept fresh by the `updateActions()`
  wiring), they track the active unit automatically: a pioneer shows fortify / sentry
  / build-colony / road / plow / clear / wait / skip / disband, a ship a different
  set. Verified live: the pioneer's two button rows render with the real art and
  hover-highlight, and clicking **build colony** opens the founding confirm dialog
  (proving click→action). **Still not ported** (later slices): the in-panel minimap
  (the map viewer still draws its own bottom-left overlay), a unit portrait, the
  wood-panel (`WOODPANL.PIK`) chrome, and the remaining key-hint captions.

## Colony screen (`ClassicColonyPanel`)

The signature original screen (design ref: the expert's `opening_016/017`,
"Northern Sugar"). Everything is painted into a virtual **320×200** canvas (the
original's VGA size) then up-scaled by the largest integer factor that fits the
window, nearest-neighbour — so the layout constants read straight off the
screenshots and the classic pixels stay crisp. Reached by clicking an owned
colony on the map (`ClassicMapViewer.onClick → gui.showColonyPanel`) or founding
one with **B**; hosted in its own `JFrame` (no `Canvas`). Layout, top to bottom:

- **Title bar** — colony name, turn and gold, gold-on-black.
- **Buildings pane** (left) — the colony's buildings on the sandy ground
  (`TERRAIN.SS.001`, tiled), each drawn from the original **`BUILDING.SS`** sprite
  set with the colonists working inside it and a black production tag
  (`amount` + goods icon). Names show **on hover** only (as in the original),
  which also keeps them from overlapping; the hover targets are the building
  bounds recorded during paint. *This slice flows the buildings left-to-right in
  rows rather than at the original's fixed ground positions — a documented
  deviation pending the expert's slot map.*
- **Tile pane** (right) — the **3×3 work-tile grid** on the wood panel
  (`WOODTILE.SS`), the colony in the centre cell and its eight neighbours around
  it. Each cell is placed by the work tile's compass `Direction` from the colony
  (`cellForDirection`), **not** its raw `(x,y)` offset — FreeCol's map is
  isometric, so the eight neighbours' raw offsets do not fill a −1..+1 square
  (the same isometric-vs-rectangular gotcha as the map viewer; placing by raw
  offset left cells empty). Each cell draws the same terrain art as the map, plus
  the colonist working it (green-boxed) and its production tag.
- **Bottom band** — the original **`COLONY.PIK`** chrome (320×72) blitted as-is,
  with the live figures over it: the SoL/tory split and the units standing in the
  colony (left), the ships in port or the empty-dock caption (middle), the net
  production (right), and the 16-slot **warehouse** row of goods icons + amounts
  along the very bottom. The red **"E"** at the bottom-right (part of the
  `COLONY.PIK` art) and **Escape** both close the screen.

**Provisional `BUILDING.SS` frame map (`BUILDING_FRAMES`).** Which of the 48
frames is which building was read off a labelled montage by eye. The clearly
distinct sets are certain (fortification walls; dock/drydock/shipyard; the sooty
blacksmith chain; the churches; the banner town hall), but several
interchangeable house/shop/factory chains are best-effort — hence the hover name,
which lets the expert spot a mis-mapped sprite and correct the table from a shot.

**Verified live (2026-07-14):** sailed the start ship to land, disembarked a
pioneer (the multi-unit disembark choice dialog now works), founded a colony with
**B** (the site-warnings confirm dialog now works), clicked it — the colony
screen renders (buildings, the filled 3×3 grid with the colony centred, the
`COLONY.PIK` band with SoL/port/production/warehouse), hover shows building names,
Escape closes it. 0 SEVERE.

**Follow-ups (later slices, need the expert's sign-off):** validate/correct the
`BUILDING.SS` frame map; the original's fixed building ground-slots (vs. our
flow layout); interaction — dragging colonists between tiles/buildings, the build
queue, loading cargo; per-nation building/flag tints; localizing the few
hard-coded captions.

## Europe screen (`ClassicEuropePanel`)

The original's home-port dock (design ref: the expert's `opening_009`–`013`).
Built exactly like the colony screen — painted into a virtual **320×200** canvas
and up-scaled by the largest integer factor that fits, nearest-neighbour, hosted
in its own `JFrame` (no `Canvas`). The original **`EUROPE.PIK`** harbour picture
(sky, sea, the wooden piers, the row of European town houses) is blitted as the
backdrop (loaded straight by its pack key `image.classic_original.pik.EUROPE.PIK`,
with a plain sea/sky fallback when the pack is absent); the live figures are drawn
over it. Layout:

- **Title bar** — port name, turn, tax and treasury, gold on black.
- **Action buttons** (top right) — the three golden buttons **Anwerben / Kaufen /
  Ausbilden** (recruit / purchase / train). Their virtual-space bounds + actions
  are recorded during paint so `onClick`/`onHover` can drive them (hover
  highlights). Each opens a **plain Swing choice dialog** listing the priced
  options and calls the **real controller** — recruit via
  `InGameController.recruitUnitInEurope(index)` over `europe.getExpandedRecruitables`,
  train/purchase via `trainUnitInEurope(unitType)` over the spec's
  `getUnitTypes{Trained,Purchased}InEurope` (cheapest first). This mirrors the
  standard `RecruitPanel` / `NewUnitPanel` exactly (both of those also route
  purchase through `trainUnitInEurope`). The dialogs are the same stopgap as the
  colony-founding seams; Phase 3 reskins them to the wood-framed look with the
  colonist portrait (`opening_011`–`013`).
- **Ships in port** — the naval units in Europe, floating on the water by the piers.
- **Units on the docks** — the land units in Europe, standing on the quay (wrapping
  onto a second rank).
- **Sailing rows** — the high-seas units split by heading (to-America vs to-Europe,
  keyed on `unit.getDestination() instanceof Europe`), each a caption + sprites.
- **Market row** — every storable good with its current sale price
  (`market.getPaidForSale`) along the bottom, on a dark plate.
- **Exit** — the red "E" at the bottom-right (part of the `EUROPE.PIK` art) and
  **Escape** both close the screen.

**Reaching it — the `updateActions()` fix.** The Europe screen is opened by the
reused `EuropeAction` (the **Europe** menu item, accelerator **E**) or
automatically when a ship arrives in Europe (the controller calls
`showEuropePanel`). The menu item is the intended trigger, but the reused
`FreeColAction`s were **stuck disabled** in the classic HUD: `SwingGUI` refreshes
their enabled state through the `Canvas` on every view change / panel open, and
the classic UI has no `Canvas`, so `EuropeAction` (and the map/turn menu items)
never re-evaluated `shouldBeEnabled` after construction. `ClassicGUI.updateActions`
(→ `FreeColClient.updateActions` → `ActionManager.update`) is now called on
`reconnectGUI` and every `changeView`, so the menu items enable correctly. Only
one Europe screen is open at a time; `updateEuropeanSubpanels` (called by the
controllers after a recruit/train) and the `refresh` hooks repaint it.

**Verified live (2026-07-14):** **E** opens the Amsterdam port (backdrop, title,
the three localized buttons, a colonist on the dock, the market row); Anwerben
lists the recruitable (`Schuldknecht (200)`), Ausbilden the cheapest trainable
(`Erfahrener Erzschürfer (600)`), Kaufen the cheapest purchasable
(`Artillerie (500)`) — each calling the real controller; Escape closes. 0 SEVERE.

**Follow-ups (later slices):** drag-to-board / load-cargo / set-sail interaction
(the equivalent of the colony screen's drag/queue/cargo work); the wood-framed
dialog reskin (shared Phase-3 component); refining the dock/pier sprite positions
against the original; localizing the few captions.

## Report screens (`ClassicReportPanel` + concrete reports)

The original 1994 game's full-screen **advisor reports** (design ref: the
expert's `opening_014`/`opening_015` shots). Four are built so far — Colony,
Military, Trade and Religious — all sharing one frame.

### The shared frame (`ClassicReportPanel`)

`ClassicReportPanel` is the abstract base every report extends; it owns the
framing so a concrete report only supplies its backdrop, title and body. Like the
colony/Europe screens, everything is painted into a virtual **320×200** canvas
up-scaled by the largest integer factor that fits, nearest-neighbour, hosted in
its own `JFrame`. The base paints: the dimmed sepia `REPORTn.PIK` backdrop (loaded
by pack key, with a flat-sepia fallback when the pack is absent), the gold-on-black
**title bar** (localized report name), and the red **Okay** plate at the
bottom-right; **Escape** and clicking Okay both close it. Subclasses implement
`backgroundKey()`, `titleKey()` and `paintBody(Graphics2D)` (drawing the header +
rows between `ROW_Y0` and `BODY_BOTTOM`), and may override `onBodyClick(vx,vy)` for
extra click behaviour (default no-op). Shared drawing helpers (`drawFitted`,
`clip`, `font`, and `cap(key)` for captions) and the palette/layout constants live
on the base.

**Clickable colony rows.** A report that lists colonies calls
`addColonyRow(yBaseline, colony)` per row during paint (the base clears the hit
list each paint, before `paintBody`); the base then handles the rest — a click in a
row band closes the report and opens that colony's screen via
`getGUI().showColonyPanel(colony, null)` (a jump-to, as the original advisor does),
and the cursor turns to a hand over a clickable row. Wired in the Colony Advisor,
Production and Religious reports (the three that list colonies). The Okay-plate and
Escape close paths take priority over a row hit.

**Caption localization.** The reports' short captions — column heads, the Colony
Advisor's page subtitles, the Religious/Congress summary labels, the empty-state
lines — have no equivalent in the standard FreeCol UI, so they get their own keys
in an isolated `classic.report.*` block appended to
`FreeColMessages[_de].properties`, fetched through the base's `cap(tail)` helper
(`Messages.message("classic.report." + tail)`). The German block deliberately
gives the Colony Advisor its **original captions** — `classic.report.colony.sol`
= "Söhne der Freiheit", `classic.report.colony.military` = "Militärgarnision" —
matching `opening_015` / `opening_014`. The info panel's key-hint captions reuse
the existing `endTurnAction`/`skipUnitAction`/`waitAction` `.name` keys (the key
tokens Enter/Space/W stay literal, being our actual bindings).

**Verified live (2026-07-16, German locale, 0 SEVERE):** Trade heads render
"Waren / Netto / $"; Exploration "Region / Typ / Runde / Punkte"; Congress
"Rekrutierung / Glocken", "Gründervater / Kategorie", "Noch keine Gründerväter.";
the info-panel hints "Enter: Zug beenden / Space: Überspringen / W: Warten/Nächste
Einheit"; and the Colony Advisor pages between the two original captions —
**"Söhne der Freiheit"** and **"Militärgarnision"** — over "Noch keine Kolonien.".
Umlauts render correctly throughout.

**Row geometry (the invariant to keep).** A row's cell spans `[y-ROW_H+3, y+3)`
about its baseline `y`, and sprites are drawn from `y-ROW_H+4` — i.e. a row
occupies space *above* its own baseline. So the first row's baseline must sit a
full row-pitch below the column heads: `ROW_Y0 = HEAD_Y + ROW_H`. (An earlier
`ROW_Y0 = TITLE_H + 12` put the first row's sprites *above* the head baseline, so
every report's heads collided with its first row — most visibly the Cargo report's
sprites.) The two reports with a summary block above their table
(Religious, Congress) repeat the same relation locally with their own
`TABLE_HEAD_Y` / `TABLE_Y0 = TABLE_HEAD_Y + ROW_H`.

> ⚠️ **These layout constants are `static final int`, so javac *inlines* them into
> every report class.** `ant compile` only recompiles changed sources, so changing a
> constant on the base silently leaves untouched subclasses running the **old**
> value (this bit us: after fixing `ROW_Y0`, `ClassicReportTradePanel` — the one
> file not otherwise edited — still painted with the stale `21`). **Run
> `ant clean compile` after touching any shared constant**, not just `ant compile`.

**Wiring.** Every `showReport*Panel` override routes through one private
`ClassicGUI.showReport(titleKey, factory)` helper: it disposes any open report
(`closeReportPanel` — **one report window at a time**), builds the panel via the
factory (passing the close callback), frames it, and is guarded so a failure
degrades to a log line. Reached by the reused report menu items, enabled by the
same `updateActions()` wiring the Europe menu item needed. The remaining
`showReport*Panel` seams still no-op.

### Colony Advisor (`ClassicReportColonyPanel`, F3) — the paged report

The "KOLONIEBERATER-BERICHT" over the sepia fort illustration (**`REPORT6.PIK`**).
This is the one report that, as in the original, **pages through several column
sets** — the behaviour the expert's two shots document (`opening_014`,
`opening_015` are the *same* report on different pages, not different reports).

Every page keeps the same **left column** — the colony's flag sprite
(`getScaledSettlementImage`), a **population badge** (the boxed `getUnitCount()`
number, as in the original) and its name — and swaps what is drawn to the right,
with a **subtitle** naming the current page. The pages (the `Page` enum):

- **Sons of Liberty** (`opening_015`, the original's "Söhne der Freiheit") —
  `getSonsOfLiberty()` %, the building producing the colony's bells
  (`getWorkLocationForProducing(liberty)`, the original's "Druckerei" column; a new
  colony shows its *Rathaus*/Town Hall), the bells per turn
  (`getNetProductionOf(liberty)`) as icon+amount, and one colonist figure per SoL
  member.
- **Military Garrison** (`opening_014`, "Militärgarnision") — the offensive land
  units standing in the colony (`tile.getUnitList()` filtered by
  `isOffensiveUnit() && !isNaval()`) as sprites; none → "—".

> **The original shows no column heads** — the subtitle names the page and the
> columns are self-evident — so this panel deliberately paints none (and starts its
> rows a row-pitch below the *subtitle* via its own `PAGE_ROW_Y0`, keeping the same
> "a row's cell is drawn above its baseline" invariant).

**Paging interaction is provisional:** the shots show the layouts, not the keys, so
**Left**/**Right**/**Space** cycle the pages (wrapping) pending the expert's
sign-off on how the original actually pages. Okay/Escape close as everywhere else.
Rows that overflow are clipped (no scroll yet); empty → "No colonies yet."

### Unit rosters — Military & Naval (`ClassicReportRosterPanel`)

`ClassicReportRosterPanel` is a second small base (over `ClassicReportPanel`) for
the reports that tally units by type×role, mirroring FreeCol's own
`ReportUnitPanel`. A subclass supplies backdrop, title, an `isReportable(Unit)`
predicate and an empty caption; the base groups the player's units
(`player.getUnits()`), keeps a sample for the sprite, **sorts by descending count
then label** (the unit set is unordered, so this keeps the roster stable), and
paints one row per group: sprite, the localized `Messages.getUnitLabel(...)`
type/role label, and the count.

- **Military Advisor** (`ClassicReportMilitaryPanel`, **F7**) — the standing army
  over the fort illustration (**`REPORT6.PIK`** — the fortification is the garrison
  image; shared with the Colony Advisor, a framing the expert may re-assign once
  REPORT9 has a home). Reportable = FreeCol's `ReportMilitaryPanel` set:
  `!isNaval() && (hasAbility(EXPERT_SOLDIER) || isOffensiveUnit())`. Empty → "No
  military units."
- **Naval Advisor** (`ClassicReportNavalPanel`, **F8**) — the fleet over the ship
  illustration (**`REPORT7.PIK`**). Reportable = `unit.isNaval()`. Empty → "No
  naval units."

### Trade Advisor (`ClassicReportTradePanel`, F9)

The goods ledger over the scales/candle/hourglass illustration (**`REPORT5.PIK`**).
Every storable good (`spec.getStorableGoodsTypeList()`) as **icon | name | Net |
$**: `Net` is the empire-wide net production summed over all colonies
(`Σ colony.getNetProductionOf(gt)`), `$` the market sale price
(`market.getPaidForSale(gt)`). Two goods per row (each occupies one 160px half —
the column origins are *within* a half, added to `col*HALF`) so the 21-good ledger
fits the canvas.

### Religious Advisor (`ClassicReportReligiousPanel`, F1)

Crosses/immigration over the preacher-and-congregation illustration
(**`REPORT2.PIK`**). A summary block at the top — accumulated immigration
(`player.getImmigration()` / `getImmigrationRequired()`) and empire-wide cross
output (`getTotalImmigrationProduction()`) with the cross goods icon — then one row
per colony with the crosses it produces (`Σ colony.getNetProductionOf(gt)` over the
`spec.getImmigrationGoodsTypeList()`). Empty → "No colonies yet."

### Production Report (`ClassicReportProductionPanel`, shift F4)

The per-colony production breakdown over the colony-under-construction
illustration (**`REPORT4.PIK`**). Where the Colony Advisor shows each colony's
*two* largest outputs and the Trade Advisor sums production empire-wide, this
lists **every** good a colony nets positively, as icon+amount along the row
(`colony.getNetProductionOf(gt)` over the storable goods, clipped when the row
fills). Empty → "No colonies yet."; a producing-nothing colony → "—". Its
per-colony row logic is shared with the (data-verified) Colony Advisor.

### Continental Congress (`ClassicReportCongressPanel`, F6)

The founding-father standing over the two-men-at-a-desk illustration
(**`REPORT3.PIK`**). A summary block — who is currently being recruited
(`player.getCurrentFather()`) and the liberty-bell progress toward them
(`getLiberty()` / `getTotalFoundingFatherCost()`, `+getLibertyProductionNextTurn()`
/turn) — then the roster of fathers already in Congress
(`player.getFoundingFathers()`): portrait (`lib.getFoundingFatherImage`), name and
category (`father.getTypeKey()`), grouped by type. Empty → "No founding fathers
yet."

### Exploration Report (`ClassicReportExplorationPanel`, shift F2)

The discovered regions over the map-and-wax-seal illustration (**`REPORT8.PIK`**) —
the same data as FreeCol's own `ReportExplorationPanel`: every `map.getRegions()`
with a non-null `getDiscoveredIn()`, as name | type | turn | score, newest first
(by discovered turn then score). Unnamed regions fall back to their localized type
name. Empty → "Nothing discovered yet."

### Cargo Report (`ClassicReportCargoPanel`, shift F1)

Each carrier's load, over the ship illustration (**`REPORT7.PIK`**, shared with the
Naval Advisor). Where the Naval Advisor tallies the fleet *by type*, this is one row
per carrier — the reportable set from FreeCol's own `ReportCargoPanel`
(`isCarrier() || canCarryTreasure()`) — showing its sprite and name, then the goods
it holds (`getCompactGoodsList()`, icon+amount) and the units aboard
(`getUnitList()`, sprites), clipped when the row fills. Nothing aboard → "(empty)";
no carriers → "No carriers."

### Indian Advisor (`ClassicReportIndianPanel`, F5)

The contacted native nations, over the native-scout illustration
(**`REPORT1.PIK`**). One row per native nation the player has **contacted**
(`player.hasContacted`, the same filter FreeCol's own `ReportIndianPanel` applies):
the tribe's capital settlement sprite
(`lib.getScaledSettlementTypeImage(nationType.getCapitalType())`), its name, the
number of its settlements the player knows of, and its tension toward the player
(`tribe.getTension(player)`, via `model.tension.*.name`). Empty → "No tribes
contacted yet."

> Unlike FreeCol's panel this deliberately omits the tribe's *true* settlement
> total: that comes from `igc().nationSummary()`, which a static paint must not
> drive — see "The `nationSummary` trap" below (the same reason the Foreign
> Affairs report is still unbuilt). The locally-known count is shown instead.

**Verified live (2026-07-15):** at the `--fast` start (at sea, no colonies) all ten
render framed over their correct backdrops with 0 SEVERE — **F3** "No colonies
yet."; **F7** the starting `Soldat (Freier Kolonist) ×1`; **F8** the starting
`Handelsschiff ×1`; **F9** the full 21-good two-column ledger with live sale prices;
**F1** "Immigration: 0 / 19", "Crosses per turn: +0"; **shift F4** "No colonies
yet."; **F6** "Recruiting: (none)", "Bells: 0 / 40 (+0/turn)", "No founding fathers
yet."; **shift F2** the three regions already discovered at the start (*Acadie* T2
sc66, *Newfoundland* T2 sc38, *Chile* T1 sc74), newest first; **shift F1** the
starting ship *Salm (Handelsschiff)* with the colonist aboard (the other starting
unit is the pioneer already ashore, so it is correctly absent from the hold);
**F5** "No tribes contacted yet." All localize (German) via the reused message keys
and `Messages.getName`/`getUnitLabel`. Escape/Okay close each; opening another
report replaces the previous window.

**Verified with a live colony (2026-07-15).** The `--fast` start put the active
unit ashore as a **Pionier** (the documented variant), so **B** → confirm the site
warnings founded *Nieuw Amsterdam* directly, no sailing needed. With it standing:

- **F3** both pages, with the row data matching `opening_015`'s shape — *Sons of
  Liberty*: flag, pop badge `1`, `Nieuw Amst…`, `0%`, **`Rathaus`**, bells `4`; then
  **Right** → *Military Garrison*: same left column, `—` (the colonist works inside,
  so it is correctly not a garrison unit); **Right** again wraps back.
- **shift F4** *Nieuw Amster…* with its furs `+2`; **F1** *Nieuw Amsterdam* `+1`
  crosses — closing those two row loops' verification gap. 0 SEVERE throughout.

> **Verification caveat — the row loops still unproven with data.** **Congress**
> (needs a founding father) and **Indian** (needs native contact) have so far only
> run through their empty-state branch. Populate them when next testing near that
> code. Note founding a colony also puts a road on its tile and turns the founding
> pioneer into a plain colonist — that is the engine's own behaviour, not a bug.

### The `nationSummary` trap

`InGameController.nationSummary(player)` is **a blocking server round-trip, not an
async callback** — an earlier note here and in the plan said otherwise. It reads
`myPlayer.getNationSummary(other)` and, **on a cache miss, calls
`askServer().nationSummary(…)` and waits** for the reply before returning.

So it must never be called from `paintComponent`: that is network I/O on the EDT,
on every repaint. Any report needing it has to fetch **once, off the EDT, when the
screen opens**, stash the results, and paint from the stash — a different shape
from every report shipped so far, all of which paint straight off the model. This
is why the Indian Advisor shows the locally-known settlement count, and it is the
main structural work in the unbuilt Foreign Affairs report.

### Follow-ups (later slices)

**Foreign affairs** — the last report the original actually has, and the only
unbuilt one with original art unaccounted for. Blocked on the expert's reference
shots (which backdrop, layout, which per-nation fields, key, sub-states), and
subject to the `nationSummary` trap above, which is its main structural work.

**Labour / education / history / requirements are decided out** — Col1 has no
screen for them, so they are deferred to
[`classic_ui_plan/optional-reports.md`](../../../../../../../classic_ui_plan/optional-reports.md)
as possible later additions of our own, not missing work.

Also: the expert's sign-off on the Colony Advisor's **paging keys** and whether it
wants more pages (population / production were mentioned but are not in the two
shots); row scrolling for many entities. (Captions are localized and colony rows
are clickable now — see "Caption localization" and "Clickable colony rows" above.)

## Popups (`ClassicDialog`) — and the dispatch seams that stranded them

The shared wood-framed popup every classic dialog routes through — the plan's
"build it once" for Phase 3. Like the reports it paints a virtual pixel canvas
(240 wide, height computed from the content) up-scaled ×3 nearest-neighbour, over
`ClassicWood` grain: an optional illustration at the left, wrapped green-on-wood
text, a centred row of raised option plates. Hosted in a modal `JDialog` (the
classic UI has no `Canvas`). Two entry points share the painting:

- `showMessages(owner, title, pages)` — pages through *n* notices one at a time
  with a single **Okay** plate and an `i/n` counter. The original has no batched
  turn-report screen, so both message seams page rather than list.
- `ask(owner, title, page, options, defaultIndex)` — a question with *n* plates,
  returning the index chosen (`-1` if dismissed). `Escape` dismisses, `Enter`
  takes the default.

Multi-page + multi-option is not meaningful (a page step consumes the plate), and
neither entry point builds one.

**`ClassicWood`** holds the seam-free grain tiling — the mirror-fold described
under `ClassicInfoPanel` — shared by the info strip and these popups.

### The dead dispatch seams (the actual bug)

`invokeNowOrLater` / `invokeNowOrWait` are **no-ops in the base `GUI`** (headless
has no EDT to reach), and `ClassicGUI` never overrode them. Every task routed
through them was therefore dropped on the floor, so the classic UI **silently
discarded every in-game notice**: `InGameController.displayModelMessages` posts
its display task through `invokeNowOrWait`, and `Message.clientGeneric` posts the
server-driven message flush (and sound) through `invokeNowOrLater`. Overriding
`showModelMessages`/`showReportTurnPanel` alone would have changed nothing —
they were never called. Both seams now mirror `SwingGUI`: run inline on the EDT,
else hand off.

This is the general hazard of building on a no-op base class: a seam you never
override fails *silently and invisibly*, and the two dispatch seams are
especially costly because they strand other seams rather than losing one screen.

### The no-op seam audit (2026-07-18)

Following that hazard to its conclusion: cross-referencing every `getGUI().X`
call in `client/control/*` against what `ClassicGUI` overrides turned up one
more silent-loss bug of the same class — **`showErrorPanel`** (below) — and
otherwise a reassuring picture:

- **Most confirms already work.** `confirmHostileAction` / `confirmLeaveColony`
  / `confirmStopGame` / `confirmClearTradeRoute` all delegate to
  `modalConfirmDialog`, which we override — so e.g. attacking an ally now
  prompts. `confirmPreCombat` hits a Phase-3 dialog no-op but is gated behind a
  client option that is off by default, so it degrades to "proceed".
- **Event dialogs are a genuine open sub-audit, deferred to Phase 3.**
  `showMonarchDialog`, `showEmigrationDialog`, `showNamingDialog`,
  `showFirstContactDialog`, `showNativeDemandDialog` no-op today, and some
  *return a value that gates flow* (which emigrant boards, what a colony is
  named). Whether their base no-op returns strand anything wants checking when
  those dialogs are built — they overlap the Q4 choice/input work.

### Wired seams

- `showModelMessages(List<ModelMessage>)` — in-turn notices.
- `showReportTurnPanel(List<ModelMessage>)` — the end-of-turn batch.
  Each notice keeps FreeCol's own illustration for it
  (`ImageLibrary.getObjectImageIcon(game.getMessageDisplay(m))`).
- `modalConfirmDialog(Tile, StringTemplate, ImageIcon, …)` — replaces the plain
  `JOptionPane` stopgap. The `(…, Unit, …)` and `(…, FreeColObject, …)`
  overloads in `GUI` are `final` and delegate here, so every confirm in the game
  lands on this one override. A dismissed popup falls back to `defaultOk`.
  Window title is `colony(tile)` — the tile's colony, else **"FreeCol"**.
- `showErrorPanel(String, Runnable)` — the audit's find. All five `showErrorPanel`
  overloads are `final` and funnel into this one non-final seam, so a no-op meant
  **every error in the classic UI vanished**. Worse, some errors carry a
  `callback` due to run on close — the uncaught-exception handler in
  `FreeColClient` shows a *serious* error with a `System.exit` callback, so the
  no-op left the app hung, neither warning nor exiting. Now routes the message
  through the shared popup and runs the callback in a `finally` (so the exit path
  fires even if the popup throws). Error text currently uses the same green as a
  message — whether the original styled errors distinctly is an open expert
  question.

`modalChoiceDialog` / `modalInputDialog` remain plain Swing stopgaps: they need a
list widget and a text field, whose original look wants the expert's reference
shots first.

**Verified live** (2026-07-17 / -18): an end-of-turn notice (*Sons of Liberty at
10%*, title "Rundenende") and the **high-seas confirm** (title "FreeCol", ship
portrait, "Jawohl, setzt alle Segel!" / "Nein, verweilt in diesen Gewässern.")
both render in the wood frame; the **error popup** (title "Fehler") renders and
its callback fires on dismiss (driven via a temporary key hook, reverted). 0
SEVERE throughout. Reaching the notices needs a *populated* save — an idle unit
generates none, which is why the first attempt saw nothing.

> **Awaiting expert sign-off.** The popup metrics and the green-on-wood palette
> are read off the original's screenshots by eye, not measured from the art —
> a considered guess, like the Colony Advisor's paging keys.

## Seam facts (for the remaining/next work)

**`GUI` methods** (all no-ops in the base class; each Javadoc names its callers):
- View state: `changeView(Tile)` (TERRAIN), `changeView(Unit,boolean)`
  (MOVE_UNITS), `changeView()` (END_TURN), `changeView(MapTransform)`
  (map-editor only — ignore). `refresh()`/`refreshTile(Tile)`.
  `setFocus(Tile)`/`getFocus()`/`getFocusMapPoint()`/`setFocusMapPoint(Point)`.
- Model access: `getFreeColClient().getGame().getMap()`, `getMyPlayer()`; route
  clicks/keys through `getFreeColClient().getInGameController()`.

**Image lookups** (`ImageLibrary`): `getTerrainImage(TileType,x,y,size)` is the
base terrain the classic viewer draws; the pack-absent overlay fallbacks are
`getForestImage` / `getSizedOverlayImage` / `getRiverImage` (see
`ClassicTileArt`). With the pack present the feature overlays are the square
`PHYS0.SS` frames loaded by key (item (e), done — see "Feature overlays" above),
not FreeCol's isometric overlay art.

## Testing live (non-interactive harness)

The `--fast --no-intro` start-at-sea view (ship on an ocean patch, 0 SEVERE) is
the smoke test. Because this harness can't see the screen, drive/verify the
window from PowerShell: launch detached (`Start-Process -PassThru` with
redirected stdout/err — a `Start-Job` dies when the tool call ends); poll
`Get-Process -Id <pid>` for a non-zero `MainWindowHandle`; bring the window
frontmost with a minimize(6)→restore(9)→`SetForegroundWindow` bounce (a plain
`SetForegroundWindow` is refused — check `GetForegroundWindow`); drive input with
`SendKeys`/`SetCursorPos`/`mouse_event` only while frontmost; screenshot via
`CopyFromScreen` over `GetWindowRect`. Note `$pid` is a read-only automatic
variable — use another name. **Kill the game process as soon as verification is
done** (the window stealing foreground interrupts parallel work).

Hard-won details, each of which silently wastes a run:

- **`--fast` resumes the last save**, so the start state is whatever you left —
  and your test turns get autosaved back. The "starts at sea" start only happens
  on a profile with no saves.
- **The window takes ~30–55 s.** Poll `MainWindowHandle` for a couple of minutes;
  a 30 s timeout reports "no window" on a perfectly healthy launch.
- **Drive states that actually produce output.** An idle unit generates no
  notices at all. Populated saves, ending turns, `B` (found colony) and sailing
  a ship east into the high seas (fires the `highseas.text` confirm) do.
- **Screenshot a popup by the handle you enumerated**, not by re-finding it by
  title: `GetWindowText` raced against dialog creation returns a truncated title
  (a "FreeCol" dialog read as "F"), and the re-find then misses.
- **Log lives in `%USERPROFILE%\OneDrive\Dokumente\freecol\FreeCol.log`**
  (`getUserCacheDirectory()`, OneDrive-redirected Documents) — *not* the repo root.
- In PowerShell, `Write-Output` inside a function becomes part of its **return
  value**; a logging line will silently corrupt an `if (Check ...)` boolean. Use
  `Write-Host`.
