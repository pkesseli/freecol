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

## Follow-up (2026-08-06): land/water blend reads as "flooded" — RESOLVED, verified

Q7 shipped and was marked resolved (below), but the expert's live review found three more rounds of
issues in the same blend, worked through in one session on `house-rules`. Round 4 (this section) was cut
off mid-verification when that session was interrupted to switch machines, then resumed and finished on
2026-08-06 on the `C:\Users\Pascal` machine.

The open design risk flagged in round 4 below turned out to be real; see "Design risk found and fixed"
under Round 4 and "Resolution" at the end of this section for what was checked and changed.

### The four rounds, in order

1. **Commit `bab8236`** — the original Q7 fix: `ClassicMapViewer.blendLandBorders` + `ditherEdge`, a
   2×2 Bayer ordered dither blending land-land edges only (water neighbours explicitly excluded).
2. **Commit `e75c6b9`** — expert feedback: the Bayer matrix read as a visibly regular, dense checkerboard
   band, denser/more uniform than the reference art's sparse, uneven speckling. Swapped the Bayer matrix
   for `hashNoise` (a stable per-world-pixel integer hash, so no flicker but no repeating tile either) at
   a lower `BORDER_DENSITY` (0.45, tapering over `BORDER_BAND` = 3 rows).
3. **Commit `ee1d95f`** — expert feedback: the *land* tile's own edge was still a hard square against
   water (only the water tile's fixed, unrelated-to-land-colour `paintCoast` sprite got any treatment).
   Dropped `blendLandBorders`'s land-only restriction so it also blends toward water neighbours through
   the same `ditherEdge`. That surfaced a real, previously-latent crash:
   `ArrayIndexOutOfBoundsException` in `ditherEdge`, because it indexed the neighbour image using the
   *tile's own* width/height, assuming every neighbour sprite comes back at the requested 16×16 —
   true for every square `TERRAIN.SS` land frame, but not for water's source art, which
   `ImageUtils.wildcardDimension` scales preserving its own (non-square) aspect ratio instead of
   distorting it to fit. Caught live via `FreeCol.log` (uncaught-exception handler logged it every
   repaint of any coastal tile; the map view just silently never advanced past its loading placeholder).
   Fixed by having `ditherEdge` read the neighbour's own dimensions instead of assuming they match.

### Round 4 (this section): "flooded" land tiles — the actual complaint

With round 3 shipped, the expert sent a side-by-side screenshot (ours vs. the original 1994 game, not
saved into `screenshots/` — ask for a fresh capture if it's needed again) showing land tiles near a
coastline looking **flooded**: isolated water-coloured pixels scattered *inside* solid green/yellow land,
disconnected from the actual water body, reading as unnatural potholes rather than a coastline. Their
framing: *"the separation here should be probably more strict — these rugged edges are great, but as far
as the water reaches, it should be all water, and vice-versa."* — i.e. the jagged, irregular *shape* of
the boundary is right (and they explicitly liked the land-land case), but individual pixels flipping
independently is wrong specifically for land/water.

**Diagnosis.** `ditherEdge`'s per-pixel independent Bernoulli scatter (`hashNoise(x,y) < density`) reads
as organic texture noise for **land-land** because neighbouring dithered land textures are close enough
in colour value that an isolated swapped pixel still looks like part of the texture. Water is a much
higher-contrast colour swap from any land tone, so the *same* scatter mechanism applied to a land/water
edge reads as isolated "holes" instead.

**Fix.** Split the two cases:

- `ditherEdge` — **unchanged** in behaviour, now only called for land-land neighbours (independent
  per-pixel scatter, as rounds 1–3 left it).
- `blendCoastEdge` — **new**, called only for water neighbours. Instead of gating each pixel
  independently, it gates *per lateral position* along the edge: one `hashNoise` roll per position
  decides how far the incursion reaches, in `[1, BORDER_BAND]`, filled solid from the edge inward. The
  result: the boundary is a wavy but solid line — strictly water past it, strictly land before it — never
  an isolated pixel floating alone.
- Both share a new `edgeCoords` helper (the row/lateral-position → own-pixel/neighbour-pixel coordinate
  math factored out of the old `ditherEdge`, unchanged in behaviour, just deduplicated).
- `blendLandBorders` now branches on `neighbour.isLand()` to pick which of the two to call.

**Design risk found and fixed (2026-08-06).** The version committed at the point of the machine-switch
hand-off (`1d37644`) gated *every* row on `COAST_GAP_PROBABILITY`, including row 0 (the pixel immediately
at the shared edge) — not just the deeper rows. Reproduced with a standalone harness that runs the actual
`blendCoastEdge`/`edgeCoords`/`hashNoise` logic against synthetic solid-colour tiles (no map geometry or
tree-canopy occlusion to obscure the result): 50.3% of lateral positions got *zero* incursion at row 0,
i.e. a hard 1px land/water step at roughly half the coastline, even though the same harness confirmed the
underlying "flooded" complaint was otherwise fully fixed (0 non-contiguous/isolated incursions across 320
sampled positions — every touched pixel connects back to the edge). Fixed by always touching row 0 and
letting `COAST_GAP_PROBABILITY` gate only how much *further* a position reaches inland (1 row the rest of
the time, up to `BORDER_BAND` rows otherwise) — re-running the same harness post-fix showed 0% row-0 gaps
with the isolated-incursion count still at 0. Confirmed live too: a before/after pixel diff of the same
coastline screenshot shows the changed pixels landing exactly on the shared edge row, nowhere else.

### Resolution (2026-08-06)

Resumed on the `C:\Users\Pascal` machine (the stale-Documents-registry workaround from the top-level
`CLAUDE.md` was confirmed to apply here too — same symptom, `HKCU\...\Personal` pointing at a dead
`D:\Users\pkesseli\...` OneDrive tree). Built, launched via the classic README's "Testing live" harness,
confirmed 0 uncaught exceptions in `FreeCol.log`, diagnosed and fixed the row-0 gap risk above, rebuilt,
and re-verified live: land-water boundary reads as a continuous jagged line with no isolated pixels,
land-land dithering (`ditherEdge`, untouched by this fix) still matches the round-2 approved look at a
prairie/forest boundary, and 0 uncaught exceptions after the fix either.
`screenshots/ui-square-tiles-fixed*.png` were refreshed from this session's captures, and the classic
README's "Land/land tile borders" section was rewritten to describe the full split `ditherEdge`/
`blendCoastEdge` design (rounds 1–4), not just the pre-round-4 single-mechanism version.

## Round 5 (2026-08-06): the water side had its own bug — the coast quarter-tile frames themselves

After round 4 shipped, the expert looked at a live screenshot of the same coastline and reported it
still read as "swampy": *"there is still a green line past the blue zone, making it look like swamp."*
Their hypothesis was that the land-land dithering algorithm had somehow been applied to sea tiles too.

**Diagnosis.** It hadn't — but proving that took more than argument. Clicking the tile in question
confirmed it was plain `Ocean` (not a hidden skerry), and `blendCoastEdge`/`ditherEdge` only ever touch
a *land* tile's own image, sampling a water neighbour's plain base texture (never its coast decoration)
and never reaching more than `BORDER_BAND` (3) native pixels from the edge — far short of the roughly
one-third-of-a-tile offset where the green line actually sat. The clinching test: temporarily
short-circuiting `blendLandBorders` to a no-op and rebuilding left the green line pixel-for-pixel
unchanged in a live screenshot — proof the artifact had nothing to do with this package's land-side
code. Pulling the actual extracted `PHYS0.SS` coast quarter-tile frames (`108..139`, the water-side
"beach feathering" mechanism from an earlier phase, well before Q7) into a contact sheet showed the
green fleck baked directly into the sprite art itself, on the outward edge of every non-empty
config's wave-crest shape. Sampling the coastline in the actual reference screenshot
(`screenshots/initial/opening_007.png`) confirmed the real game shows a clean, fairly desaturated
grey/white foam fringe there instead (`RGB` roughly `120–150` across all three channels) — no green at
all. Conclusion: the frame *extraction* (a palette/decode issue predating this session, not a rendering
bug) doesn't match the source material.

**Fix.** Rather than patch fidelity into art that's already wrong, `paintCoast` and its supporting
`COAST_CORNERS`/`COAST_BASE`/`COAST_LAST`/`keyOutBlack` were removed from `ClassicTileArt` entirely, and
the water side of the coastline is now painted procedurally — the same call Q7 already made for the
land side once it turned out there was no faithful land-land border sprite to source either. New
`ClassicMapViewer.blendWaterBorders`/`foamEdge`: for each cardinal neighbour that is land, alpha-blend
the sampled foam colour (`FOAM_R/G/B = 150,155,160`) into the water tile's own edge, peaking at
`FOAM_MAX_ALPHA` (0.6) right at the shared edge and tapering to 0 over `FOAM_BAND` (2) native pixels —
narrower than the land side's `BORDER_BAND`, matching how thin the original's fringe reads at native
resolution. This blends *over* the existing water pixel (alpha compositing) rather than replacing it
outright, unlike the land-side mechanisms — there's no neighbour art to stay faithful to on the water
side, so lightening the existing water colour reads as a highlight, matching how the original's own
foam looks like whitecaps over water rather than a separate layer. A per-pixel `hashNoise` factor varies
the alpha for an uneven, natural-looking line; there's no "flooded" failure mode to guard against here
(a lighter-than-usual water pixel never reads as an isolated hole the way a land-coloured pixel does in
water), so `foamEdge` needed none of `blendCoastEdge`'s gap/depth machinery.

**Verification.** Rebuilt, relaunched, confirmed 0 uncaught exceptions in `FreeCol.log`. Re-screenshotted
the exact same coastline crop used to diagnose the bug: the green fleck is gone, replaced by a clean
thin grey-blue line hugging the shore. Re-checked a wider crop and the land-land dithering crop from
round 4 — both unaffected. The "tan blob" visible near the coast in earlier screenshots turned out to be
a fish resource marker (matches the original's own jumping-fish sprite in `opening_007.png` almost
exactly), not a related bug.

### State at hand-off

Resolved and verified live. `screenshots/ui-square-tiles-fixed*.png` need a fresh capture with the foam
fix (round 4's captures predate this change); the classic README's "Coastline (beach feathering)" and
"Land/land tile borders" sections were rewritten to describe the removal and the new procedural water
side. Open follow-up, not blocking: diagonal-only neighbours (a land tile touching water only at a
corner) get no foam treatment from `blendWaterBorders`, which only checks the four cardinal offsets —
the original's coast frames handled diagonal corners via `COAST_CORNERS`' config bit 2, so a concave
coastline corner may still read as slightly under-treated on the water side. Not checked live this
round; worth a screenshot of a concave corner before calling the water side fully done.
