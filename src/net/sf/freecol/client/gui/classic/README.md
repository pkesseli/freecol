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
  by `FreeColClient.restoreGUI` — builds the `ClassicMapViewer`, installs it as
  the frame's whole content pane, and seeds the initial view state/focus.
  `quitGUI` disposes the frame.
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
- **`showColonyPanel` stopgap (Phase 2 seam).** A click on an owned colony
  hosts FreeCol's own `ColonyPanel` in a standalone `JFrame` (the classic UI has
  no `Canvas`), guarded so any failure just logs. Never reached at start-at-sea.
  Phase 2 replaces this with a real classic colony screen.

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
- **Next-active-unit at turn start (caveat).** After `endTurn`, whether a fresh
  active unit is auto-selected is up to the controller's `setCurrentPlayer` →
  `updateActiveUnit` → `changeView(unit)` path, which the classic `GUI` already
  delegates to `changeToMoveUnits` (so it *does* re-centre on the next active unit
  when `player.hasNextActiveUnit()`). Movement was verified live post-end-turn
  (the active ship moves and the focus follows it); note that panning the focus
  away (e.g. a minimap click) leaves the active unit's cursor off-screen until a
  movement key re-centres on it.

**Minimap overlay.** A whole-map overview in the bottom-left, painted at the end
of `paintComponent` (after map + cursor). It is a plain rectangular
`map.getWidth() × map.getHeight()` raster — **no** isometric projection (unlike
`client/gui/panel/MiniMap`, which is the colour-source reference only):

- Colours: unexplored → `ImageLibrary.getMinimapBackgroundColor()`; explored →
  `getMinimapPoliticsColor(tile.getType())`; a tile with a settlement/unit →
  the owner's `getNationColor()`; frame + viewport box →
  `getMinimapBorderColor()`. All guarded with fallbacks (`orElse`).
- Sizing: integer pixels-per-tile `max(1, MINIMAP_MAX/max(w,h))` fits the raster
  into a ~200px box, so a tall/narrow map renders as a vertical strip.
- **Caching / performance:** the raster is cached in a `BufferedImage` and
  rebuilt only when `invalidateMinimap()` marks it dirty — wired to
  `ClassicGUI.refresh`/`refreshTile`, the model-change hooks (exploration, new
  settlements, unit moves). Ordinary pan/cursor repaints just blit the cache +
  overlay the viewport box, so no per-repaint tile iteration on large maps.
- **Viewport box:** a rectangle marking the tile region visible in the main view
  (focus ± half the `TILE_W`/`TILE_H` span), clipped to the box.
- **Click-to-recentre:** minimap-region clicks are intercepted in `onClick`
  *before* `tileAt`, translated to a tile via the pixels-per-tile scale, and
  `setFocus`ed, so a minimap click never also selects terrain underneath.

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
