# Land-land tile borders render as hard, unblended rectangles (Q7)

**Status: ready to implement.** This is a self-contained brief — read this file only,
no need to read the rest of the plan first. Background/history lives in
[ui-phases.md Q7](ui-phases.md#open-questions-for-the-expert) and the
["Known gap" note](../src/net/sf/freecol/client/gui/classic/README.md) in the classic
package README, if you want the full trail later.

## The bug

In the classic UI (`--classic`), two adjacent **land** tiles of different `TileType`
meet at a perfectly straight, one-pixel-wide edge — a flat geometric rectangle, not
organic terrain. `screenshots\ui-square-tiles-bug.png` shows it starkly (adjacent
tiles read as flat solid-color squares). No original reference screenshot shows this
— see `screenshots\initial\opening_007.png`, `opening_008.png`, and others: terrain
boundaries in the original always look soft/irregular, never a straight ruled line.

## Root cause (confirmed live, not guessed)

`ClassicTileArt.paintFromPack` only feathers the **ocean↔land coast**
(`paintCoast`, gated on `if (!tile.isLand())`). There is **no equivalent for
land↔land**: a bare tile (no forest/hill/mountain overlay) is drawn as its flat
`TERRAIN.SS` base texture and nothing else, so its rectangular boundary against any
differently-colored neighbor is 100% hard-edged, always.

Confirmed by launching `--classic` live and clicking the tiles in the bug
screenshot: a "Prärie" (Prairie) tile with **zero overlay** sits directly next to a
"Mischwald" (Mixed Forest) tile whose substituted base texture
(`image.tile.model.tile.mixedForest.center` → `TERRAIN.SS.002`, Plains) happens to
be visually near-identical to Prairie's own (`TERRAIN.SS.003`) — so what reads as
"one big yellow square" is actually two different `TileType`s with a completely
uncamouflaged edge between them. (A forest-canopy/connectivity explanation was
chased first and ruled out — sparse tree overlays are a real, separate, correctly-
working mechanism, not the cause here.)

**What's still unconfirmed:** whether Col1 itself does genuine land-land sub-tile
blending, and if so, how. Every extracted `.SS` archive under `tools/classic_assets`
output was checked; there is no dedicated land-land border/transition sheet
analogous to the 32 coast quarter-tiles (`PHYS0.SS` frames `108..139`). If the
original blends land edges, it is not via a sprite-lookup table we have access to.

## Recommended fix

Since no border sprite sheet exists to source, **blend procedurally at render
time** rather than waiting on more asset RE:

- In `ClassicTileArt` (or `ClassicMapViewer.paintTile`), after drawing a land
  tile's base terrain and before its feature overlays, check each raw-grid
  neighbor (reuse the existing cardinal/corner neighbor-lookup pattern already in
  `ClassicTileArt`, e.g. `isLand`/`hasFeature`) for land of a **different**
  `TileType`.
- Where that's true, blend a narrow edge band (a few native pixels, i.e. before
  the ×3 `CLASSIC_SCALE` upscale) toward the neighbor's base color using an
  **ordered dither** (checkerboard or small Bayer-style pattern), not a smooth
  alpha gradient — the existing terrain textures are themselves 2-color dithers
  (see `TERRAIN.SS` frames), so a dithered blend matches the established pixel-art
  aesthetic; a soft/anti-aliased gradient would look out of place.
- Scope this to the **base-terrain pass only**. Don't touch `paintCoast` (already
  correct) or the overlay compositing (forest/hills/mountains/rivers/roads/
  resources, all already correct).
- There's no numeric spec for band width/pattern — this is a visual-fidelity call.
  Iterate empirically: render, screenshot, compare against
  `screenshots\ui-square-tiles-bug.png` (before) and
  `screenshots\initial\opening_007.png` / `opening_008.png` (target look) until it
  reads as similarly organic, rather than guessing a fixed formula up front.
- Use the live-test harness in the classic README ("Testing live") to
  build+launch+screenshot — `ant package` then
  `java -Xmx2G -jar FreeCol.jar --fast --no-intro --classic`, or `ant compile` +
  launch straight from `build/` for faster iteration.

## Acceptance criteria

1. Live in `--classic`, at the map location shown in `ui-square-tiles-bug.png` (or
   an equivalent spot with two different bare land `TileType`s adjacent): the
   boundary is no longer a single hard straight edge. Capture a new screenshot
   showing the blended transition.
2. No regressions: ocean/land coastlines, forest/hill/mountain overlays, rivers,
   roads, and resource markers are visually unchanged from before the fix.
3. No new asset extraction or `resources.properties`/`aliases.properties` changes
   required — implementation confined to the `ClassicTileArt`/`ClassicMapViewer`
   rendering code in `src/net/sf/freecol/client/gui/classic/`.
4. Once shipped: mark Q7 resolved in
   [`ui-phases.md`](ui-phases.md#open-questions-for-the-expert) (move it to the
   "Resolved" list, matching how Q1/Q2 are recorded there) and update the
   classic-package README's "Known gap" note to describe the fix instead of the
   gap. Leave this file in place as the implementation record — don't delete it.
