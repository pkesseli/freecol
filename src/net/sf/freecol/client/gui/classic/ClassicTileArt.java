/**
 *  Copyright (C) 2002-2024  The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.gui.classic;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Resource;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.resources.ResourceManager;


/**
 * Composites the per-tile terrain-feature overlays (Phase 1 item (e)) —
 * forest trees, hills, mountains, rivers, roads, plowed fields, the
 * lost-city rumour and resource markers — onto a square classic map cell,
 * on top of the base terrain drawn by {@link ClassicMapViewer#paintTile}.
 *
 * <h2>Why {@code PHYS0.SS}, not {@code TERRAIN.SS}</h2>
 * The 1994 game drew a map cell as a base terrain tile plus <em>overlay</em>
 * sprites for its physical features, all cut as square 16&times;16 tiles.
 * {@code TERRAIN.SS} holds only the 12 base terrains; the feature overlays
 * live in {@code PHYS0.SS} (154 frames).  Those square sprites composite
 * cleanly onto the classic rectangular grid, where FreeCol's own
 * isometric-diamond overlays would skew — which is why item (e) waited on
 * sourcing them.  The frames are exposed by the {@code classic_original}
 * pack under the keys {@code image.classic_original.ss.PHYS0.SS.NNN}; when
 * the pack is absent we fall back to FreeCol's own (imperfect) overlay art
 * so the build always runs.
 *
 * <h2>Frame layout (verified by pixel edge-analysis of the extracted frames)</h2>
 * All the <em>directional</em> feature sets share one 4-bit connectivity
 * encoding — the frame within a set is
 * {@code (E?1:0) | (W?2:0) | (S?4:0) | (N?8:0)} over the raw-grid cardinal
 * neighbours that also carry the feature:
 * <ul>
 *   <li>minor river {@code 0..15}, major river {@code 16..31},
 *       mountains {@code 32..47}, hills {@code 48..63}, forest {@code 64..79};</li>
 *   <li>roads are composited instead: {@code 80} is the centre hub and
 *       {@code 81..88} are the eight directional spokes
 *       (N, NE, E, SE, S, SW, W, NW clockwise), one drawn per raw neighbour
 *       that has a road;</li>
 *   <li>{@code 103} lost-city rumour, {@code 149} plowed field, and the
 *       resource markers {@code 89..102}.</li>
 * </ul>
 *
 * <h2>Coastline (frames {@code 108..139})</h2>
 * The 1994 game feathered the ocean&harr;land border with 32 small <b>8&times;8
 * quarter-tiles</b> composited onto the four quadrants of each <em>water</em>
 * cell — {@code 4 corners &times; 8 configs} laid out as
 * {@code frame = 108 + config*4 + corner}, with {@code corner} clockwise
 * {@code NW=0, NE=1, SE=2, SW=3}.  For a given corner the two orthogonal
 * neighbours bounding it and the diagonal neighbour select the sub-tile:
 * {@code config = (ccwEdgeLand?1) | (diagLand?2) | (cwEdgeLand?4)} — verified
 * rotationally consistent across all four corners (config&nbsp;1 = the
 * counter-clockwise edge neighbour is land, config&nbsp;4 = the clockwise edge,
 * config&nbsp;2 = a diagonal-only neighbour draws a light coastal-water wedge,
 * config&nbsp;5 = both edges land form an inlet, config&nbsp;0 = open ocean,
 * nothing drawn).  Unlike the directional sets (which mark transparency with the
 * {@code 0xFD} alpha index the decoder honours), this set encodes its transparent
 * regions as an <b>opaque-black colour-key</b> that the decoder leaves opaque, so
 * {@link #frame} keys that black out to alpha&nbsp;0 on load (see
 * {@link #keyOutBlack}); the base ocean tile then shows through and the
 * quarter-tiles composite cleanly.  The estuary/river-mouth pieces
 * ({@code 140..147} ocean corner-hints, {@code 150..153} diagonal sand strips)
 * are not yet wired.
 *
 * <h2>Connectivity on the classic grid</h2>
 * Connectivity is computed from <em>raw-grid</em> neighbours (the tiles drawn
 * directly up/down/left/right and at the corners) rather than FreeCol's
 * isometric {@link net.sf.freecol.common.model.Direction}s, so features blend
 * with whatever is <em>visually</em> adjacent on the square grid.  Area
 * features (forest / hills / mountains) use the four cardinal neighbours.
 * Rivers are linear and — because FreeCol lays them out along the isometric
 * long-sides, which flatten to raw diagonals — fold each diagonal neighbour
 * into its two adjacent cardinal bits so a diagonal river still reads as
 * connected.  Roads simply draw a spoke toward every one of the eight raw
 * neighbours that has a road.
 */
final class ClassicTileArt {

    // PHYS0.SS frame bases for each directional feature set.
    private static final int RIVER_MINOR = 0;
    private static final int RIVER_MAJOR = 16;
    private static final int MOUNTAINS = 32;
    private static final int HILLS = 48;
    private static final int FOREST = 64;

    /** Road centre-hub frame; the eight spokes follow at {@code ROAD_HUB+1..+8}. */
    private static final int ROAD_HUB = 80;

    private static final int LOST_CITY = 103;
    private static final int PLOWED = 149;

    /** Base and last frame of the 32 coast/beach quarter-tiles ({@code 108..139}). */
    private static final int COAST_BASE = 108;
    private static final int COAST_LAST = 139;

    /**
     * Per-cell-quadrant coast data, one row per corner (clockwise):
     * {@code {cornerIndex, qx, qy, e1dx,e1dy, ddx,ddy, e4dx,e4dy}} — the
     * quadrant position ({@code qx,qy} in {@code {0,1}}), then the raw-grid
     * offsets of the counter-clockwise edge (config bit&nbsp;1), the diagonal
     * (bit&nbsp;2) and the clockwise edge (bit&nbsp;4) neighbours.
     */
    private static final int[][] COAST_CORNERS = {
        // NW quad(0,0): ccwEdge=W, diag=NW, cwEdge=N
        { 0, 0, 0, -1,  0, -1, -1,  0, -1 },
        // NE quad(1,0): ccwEdge=N, diag=NE, cwEdge=E
        { 1, 1, 0,  0, -1,  1, -1,  1,  0 },
        // SE quad(1,1): ccwEdge=E, diag=SE, cwEdge=S
        { 2, 1, 1,  1,  0,  1,  1,  0,  1 },
        // SW quad(0,1): ccwEdge=S, diag=SW, cwEdge=W
        { 3, 0, 1,  0,  1, -1,  1, -1,  0 },
    };

    /** Connectivity bits (see the class comment): the frame is their sum. */
    private static final int E = 1, W = 2, S = 4, N = 8;

    /** Number of frames in {@code PHYS0.SS} (used to bound the cache/lookups). */
    private static final int PHYS0_FRAMES = 154;

    /**
     * Raw-grid offsets and spoke frame for each of the eight road directions,
     * clockwise from N: {@code {dx, dy, frame}}.  N=up, NE=up-right, E=right,
     * SE=down-right, S=down, SW=down-left, W=left, NW=up-left.
     */
    private static final int[][] ROAD_SPOKES = {
        { 0, -1, 81 }, { 1, -1, 82 }, { 1, 0, 83 }, { 1, 1, 84 },
        { 0,  1, 85 }, { -1, 1, 86 }, { -1, 0, 87 }, { -1, -1, 88 },
    };

    private final ImageLibrary lib;

    /** Whether the {@code classic_original} pack (hence {@code PHYS0.SS}) is loaded. */
    private final boolean packPresent;

    /** Lazily-loaded cache of the native 16&times;16 {@code PHYS0.SS} frames. */
    private final BufferedImage[] frames = new BufferedImage[PHYS0_FRAMES];


    ClassicTileArt(ImageLibrary lib) {
        this.lib = lib;
        this.packPresent = ResourceManager.getImageResource(phys0Key(0), false) != null;
    }


    private static String phys0Key(int n) {
        return String.format("image.classic_original.ss.PHYS0.SS.%03d", n);
    }

    /** Load (and cache) a {@code PHYS0.SS} frame, or null if out of range. */
    private BufferedImage frame(int n) {
        if (n < 0 || n >= PHYS0_FRAMES) return null;
        if (this.frames[n] == null) {
            BufferedImage img = ImageLibrary.getUnscaledImage(phys0Key(n));
            // The coast quarter-tiles (108..139) encode their transparent regions
            // as an opaque-black colour-key (index 0), not the 0xFD alpha the other
            // PHYS0.SS sets use, so the decoder leaves them opaque.  Key that black
            // out to alpha 0 here (once, cached) so the base ocean shows through
            // when the quarter-tile is composited -- otherwise plain drawImage
            // paints black wedges over the sea along every coastline.
            if (img != null && n >= COAST_BASE && n <= COAST_LAST) {
                img = keyOutBlack(img);
            }
            this.frames[n] = img;
        }
        return this.frames[n];
    }

    /**
     * Return a copy of {@code src} with every fully-opaque pure-black pixel made
     * transparent.  Used to honour the coast set's black colour-key without
     * mutating the shared image cached by {@link ImageLibrary}.
     */
    private static BufferedImage keyOutBlack(BufferedImage src) {
        final int w = src.getWidth(), h = src.getHeight();
        final BufferedImage out =
            new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int argb = src.getRGB(x, y);
                // Opaque (alpha 0xFF) and pure black (RGB 0) -> fully transparent.
                out.setRGB(x, y,
                    ((argb >>> 24) == 0xFF && (argb & 0xFFFFFF) == 0) ? 0 : argb);
            }
        }
        return out;
    }

    /** Draw a {@code PHYS0.SS} frame scaled to fill the cell at {@code (sx, sy)}. */
    private void drawFrame(Graphics2D g, int n, int sx, int sy, int w, int h) {
        final BufferedImage img = frame(n);
        if (img != null) g.drawImage(img, sx, sy, w, h, null);
    }


    /**
     * Composite every terrain-feature overlay for {@code tile} into the cell at
     * {@code (sx, sy)} sized {@code w}&times;{@code h}, on top of the already-drawn
     * base terrain and below the settlement/unit sprite.
     */
    void paintOverlays(Graphics2D g, Map map, Tile tile, int sx, int sy,
                       int w, int h) {
        if (this.packPresent) {
            paintFromPack(g, map, tile, sx, sy, w, h);
        } else {
            paintFallback(g, tile, sx, sy, w, h);
        }
    }

    /** The classic path: composite the square {@code PHYS0.SS} overlays. */
    private void paintFromPack(Graphics2D g, Map map, Tile tile, int sx, int sy,
                               int w, int h) {
        final int x = tile.getX();
        final int y = tile.getY();

        // Coastline: feather the border of a water cell with the 8x8 beach
        // quarter-tiles wherever a neighbour is land (open ocean draws nothing).
        if (!tile.isLand()) {
            paintCoast(g, map, tile, sx, sy, w, h);
        }

        // Terrain relief: forest trees, or the hill/mountain massif.  These are
        // area features, so connectivity is over the four cardinal neighbours.
        if (tile.isForested()) {
            drawFrame(g, FOREST + areaMask(map, x, y, Feature.FOREST), sx, sy, w, h);
        } else if (isType(tile, "model.tile.mountains")) {
            drawFrame(g, MOUNTAINS + areaMask(map, x, y, Feature.MOUNTAINS), sx, sy, w, h);
        } else if (isType(tile, "model.tile.hills")) {
            drawFrame(g, HILLS + areaMask(map, x, y, Feature.HILLS), sx, sy, w, h);
        }

        // A plowed field sits on open, cleared ground.
        if (isPlowed(tile)) {
            drawFrame(g, PLOWED, sx, sy, w, h);
        }

        // River: minor (magnitude 1) or major (>= 2), connectivity folded from
        // the raw-grid neighbours (see class comment).
        final TileImprovement river = tile.getRiver();
        if (river != null) {
            final int base = (river.getMagnitude() >= 2) ? RIVER_MAJOR : RIVER_MINOR;
            drawFrame(g, base + riverMask(map, x, y), sx, sy, w, h);
        }

        // Road: centre hub plus a spoke toward each raw neighbour with a road.
        if (tile.hasRoad()) {
            drawFrame(g, ROAD_HUB, sx, sy, w, h);
            for (int[] spoke : ROAD_SPOKES) {
                if (hasRoad(map, x + spoke[0], y + spoke[1])) {
                    drawFrame(g, spoke[2], sx, sy, w, h);
                }
            }
        }

        // Resource marker, then a lost-city rumour on top of everything.
        if (tile.hasResource()) {
            final int r = resourceFrame(tile.getResource());
            if (r >= 0) drawFrame(g, r, sx, sy, w, h);
        }
        if (tile.hasLostCityRumour()) {
            drawFrame(g, LOST_CITY, sx, sy, w, h);
        }
    }

    /**
     * Fallback path when the pack is absent: draw FreeCol's own overlay/forest/
     * river art shrunk into the cell.  Isometric-shaped, so imperfect on the
     * square grid, but it keeps the build running without the original assets.
     */
    private void paintFallback(Graphics2D g, Tile tile, int sx, int sy,
                               int w, int h) {
        final Dimension size = new Dimension(w, h);
        if (tile.isForested()) {
            g.drawImage(this.lib.getForestImage(tile.getType(), size), sx, sy, w, h, null);
        } else if (tile.getType() != null && tile.getType().isElevation()) {
            g.drawImage(this.lib.getSizedOverlayImage(tile.getType(), size),
                        sx, sy, w, h, null);
        }
        final TileImprovement river = tile.getRiver();
        if (river != null && river.getStyle() != null) {
            g.drawImage(this.lib.getRiverImage(river.getStyle().getString(), size),
                        sx, sy, w, h, null);
        }
    }


    /**
     * Composite the coastline for a water cell: for each of the four quadrants,
     * pick the beach quarter-tile from its three raw-grid neighbours and draw it
     * into that quadrant (open-ocean quadrants — config 0 — draw nothing).
     */
    private void paintCoast(Graphics2D g, Map map, Tile tile, int sx, int sy,
                            int w, int h) {
        final int x = tile.getX(), y = tile.getY();
        final int halfW = w / 2, halfH = h / 2;
        for (int[] c : COAST_CORNERS) {
            int config = 0;
            if (isLand(map, x + c[3], y + c[4])) config |= 1;   // ccw edge
            if (isLand(map, x + c[5], y + c[6])) config |= 2;   // diagonal
            if (isLand(map, x + c[7], y + c[8])) config |= 4;   // cw edge
            if (config == 0) continue;
            final int qx = sx + (c[1] == 0 ? 0 : halfW);
            final int qy = sy + (c[2] == 0 ? 0 : halfH);
            final int qw = (c[1] == 0 ? halfW : w - halfW);
            final int qh = (c[2] == 0 ? halfH : h - halfH);
            drawFrame(g, COAST_BASE + config * 4 + c[0], qx, qy, qw, qh);
        }
    }

    /** True if the raw cell {@code (x, y)} exists and is land (not water). */
    private boolean isLand(Map map, int x, int y) {
        final Tile t = map.getTile(x, y);
        return t != null && t.isLand();
    }


    // Connectivity helpers (raw-grid neighbours).

    private enum Feature { FOREST, HILLS, MOUNTAINS }

    /** True if the raw cell {@code (x, y)} carries {@code feature}. */
    private boolean hasFeature(Map map, int x, int y, Feature feature) {
        final Tile t = map.getTile(x, y);
        if (t == null) return false;
        switch (feature) {
        case FOREST:    return t.isForested();
        case HILLS:     return isType(t, "model.tile.hills");
        case MOUNTAINS: return isType(t, "model.tile.mountains");
        default:        return false;
        }
    }

    /** 4-bit cardinal-neighbour connectivity mask for an area feature. */
    private int areaMask(Map map, int x, int y, Feature feature) {
        int m = 0;
        if (hasFeature(map, x, y - 1, feature)) m |= N;
        if (hasFeature(map, x + 1, y, feature)) m |= E;
        if (hasFeature(map, x, y + 1, feature)) m |= S;
        if (hasFeature(map, x - 1, y, feature)) m |= W;
        return m;
    }

    private boolean hasRiver(Map map, int x, int y) {
        final Tile t = map.getTile(x, y);
        return t != null && t.hasRiver();
    }

    /**
     * River connectivity mask.  Rivers run along FreeCol's isometric long-sides,
     * which flatten to raw diagonals, so each diagonal neighbour with a river
     * lights both its adjacent cardinal bits — keeping a diagonally-running
     * river visually connected on the square grid.
     */
    private int riverMask(Map map, int x, int y) {
        final boolean up = hasRiver(map, x, y - 1), dn = hasRiver(map, x, y + 1);
        final boolean lf = hasRiver(map, x - 1, y), rt = hasRiver(map, x + 1, y);
        final boolean ul = hasRiver(map, x - 1, y - 1), ur = hasRiver(map, x + 1, y - 1);
        final boolean dl = hasRiver(map, x - 1, y + 1), dr = hasRiver(map, x + 1, y + 1);
        int m = 0;
        if (up || ul || ur) m |= N;
        if (dn || dl || dr) m |= S;
        if (rt || ur || dr) m |= E;
        if (lf || ul || dl) m |= W;
        return m;
    }

    private boolean hasRoad(Map map, int x, int y) {
        final Tile t = map.getTile(x, y);
        return t != null && t.hasRoad();
    }


    // Tile-feature predicates.

    private static boolean isType(Tile tile, String typeId) {
        return tile.getType() != null && typeId.equals(tile.getType().getId());
    }

    private static boolean isPlowed(Tile tile) {
        for (TileImprovement imp : tile.getCompleteTileImprovements()) {
            if (imp.getType() != null
                && "model.improvement.plow".equals(imp.getType().getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Map a {@link Resource} to its {@code PHYS0.SS} marker frame, or -1 when
     * unmapped.  <b>Provisional</b> — the sprite-to-resource identification is a
     * best-effort visual read of the extracted frames, pending the expert's
     * validation against the original game.
     */
    private static int resourceFrame(Resource resource) {
        if (resource == null || resource.getType() == null) return -1;
        switch (resource.getType().getId()) {
        case "model.resource.fish":     return 96;
        case "model.resource.game":     return 98;
        case "model.resource.furs":     return 97;
        case "model.resource.minerals": return 95;
        case "model.resource.ore":      return 102;
        case "model.resource.silver":   return 101;
        case "model.resource.lumber":   return 99;
        case "model.resource.tobacco":  return 91;
        case "model.resource.cotton":   return 92;
        case "model.resource.sugar":    return 93;
        default:                        return -1;
        }
    }
}
