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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI map view: a rectangular-grid renderer for the FreeCol map.
 *
 * Where {@code SwingGUI}'s {@link net.sf.freecol.client.gui.mapviewer.MapViewer}
 * paints isometric diamonds, this paints a plain rectangular grid of tiles
 * centred on a focus tile, mirroring the look of the original 1994 Colonization.
 * The per-tile <em>image selection</em> logic is reused from {@link ImageLibrary}
 * (terrain / settlement / unit lookups); only the projection is different:
 *
 * <pre>screenX = centreX + (tileX - focusX) * tileW - tileW/2
 * screenY = centreY + (tileY - focusY) * tileH - tileH/2</pre>
 *
 * <p><b>Phase 1.</b> Terrain is drawn from the original Colonization
 * {@code TERRAIN.SS} sprites — square 16&times;16 tiles, aliased onto FreeCol's
 * {@code image.tile.<type>.center} keys by the {@code classic_original} pack
 * (see {@code tools/classic_assets/aliases.properties}) — so the rectangular
 * grid fills cleanly with no diamond-shaped gaps. The native 16&times;16 tiles
 * are fetched at source size and up-scaled by {@link #CLASSIC_SCALE} with
 * nearest-neighbour interpolation to keep the chunky classic pixels crisp. When
 * the pack is absent the same keys fall back to FreeCol's own (isometric) art,
 * shrunk into the square cells. See CLASSIC_UI_PLAN.md ("Phase 1").
 *
 * <p>This panel owns the classic view state (view mode, focus, selected tile,
 * active unit); {@link ClassicGUI} delegates the corresponding {@code GUI}
 * facade methods to it.
 */
final class ClassicMapViewer extends JPanel {

    /** Native size (px) of an original {@code TERRAIN.SS} tile sprite. */
    private static final int TILE_SRC = 16;

    /** Integer up-scale from the native 16&times;16 tile to on-screen pixels. */
    private static final int CLASSIC_SCALE = 3;

    /** On-screen tile cell size (square, like the original game). */
    private static final int TILE_W = TILE_SRC * CLASSIC_SCALE;  // 48
    private static final int TILE_H = TILE_SRC * CLASSIC_SCALE;  // 48

    /** Native tile-sprite size requested from {@link ImageLibrary}. */
    private static final Dimension SRC_SIZE = new Dimension(TILE_SRC, TILE_SRC);

    /**
     * Distance (px) from a window edge within which the mouse triggers edge
     * scrolling.  Roughly a tile wide, so the hot zone is easy to hit without
     * being triggered by ordinary map clicks.
     */
    private static final int EDGE_SCROLL_MARGIN = TILE_W;

    /** Interval (ms) between successive edge-scroll steps while at an edge. */
    private static final int EDGE_SCROLL_INTERVAL_MS = 110;

    private final FreeColClient freeColClient;

    /**
     * The owning {@link ClassicGUI}, used to route clicks/keys through the
     * {@code GUI} facade (select, focus, show-colony) exactly as
     * {@code SwingGUI.clickAt}/{@code MoveAction} do, so the classic UI drives
     * the real controllers rather than a self-contained local state.
     */
    private final ClassicGUI gui;

    /** Image library used for terrain/unit/settlement lookups. */
    private final ImageLibrary lib;

    // Classic view state (owned here; ClassicGUI delegates to these).
    private GUI.ViewMode viewMode = GUI.ViewMode.END_TURN;
    private Tile focus;
    private Tile selectedTile;
    private Unit activeUnit;

    /**
     * Repeating timer that drives edge scrolling; it pans the focus by
     * {@link #edgeDX}/{@link #edgeDY} each tick while the mouse sits in an edge
     * hot zone, and is stopped whenever that direction is zero.
     */
    private final Timer edgeScrollTimer;
    private int edgeDX;
    private int edgeDY;

    /** Longest on-screen edge (px) of the whole-map minimap overlay box. */
    private static final int MINIMAP_MAX = 200;

    /** Gap (px) between the minimap box and the window edge (bottom-left). */
    private static final int MINIMAP_MARGIN = 12;

    /**
     * Cached raster of the whole map (one {@link #minimapPPT}-px square per
     * tile), rebuilt only when {@link #minimapDirty} — i.e. when the model
     * changes (exploration / settlements / unit moves come through
     * {@code refresh}) — not on every repaint.  Each paint just blits this
     * image and overlays the viewport box, so panning/cursor repaints stay
     * cheap even on a large map.  Null until first built.
     */
    private BufferedImage minimapCache;

    /** Pixels per tile in {@link #minimapCache} (integer, at least 1). */
    private int minimapPPT = 1;

    /** Whether {@link #minimapCache} needs rebuilding before the next blit. */
    private boolean minimapDirty = true;


    ClassicMapViewer(FreeColClient freeColClient, ClassicGUI gui,
                     ImageLibrary lib) {
        this.freeColClient = freeColClient;
        this.gui = gui;
        this.lib = lib;
        setBackground(Color.BLACK);
        setOpaque(true);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    stopEdgeScroll();
                }
            });
        addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateEdgeScroll(e.getPoint());
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    updateEdgeScroll(e.getPoint());
                }
            });
        this.edgeScrollTimer = new Timer(EDGE_SCROLL_INTERVAL_MS, e -> {
                if (this.edgeDX != 0 || this.edgeDY != 0) {
                    panFocus(this.edgeDX, this.edgeDY);
                }
            });
        installKeyBindings();
    }


    /**
     * Bind the keyboard movement controls.  The arrow keys and numpad 8/2/4/6
     * move orthogonally, numpad 7/9/1/3 and Home/PageUp/End/PageDown move
     * diagonally — matching FreeCol's own {@code moveAction.*.accelerator}
     * key layout.  Bound {@code WHEN_IN_FOCUSED_WINDOW} so the keys work
     * whenever the map window is focused, independent of which child component
     * currently holds focus.
     *
     * <p>Unlike item (b)'s provisional raw-grid pan, these keys now drive the
     * real game, mirroring {@link net.sf.freecol.client.gui.action.MoveAction}:
     * in MOVE_UNITS mode they move the active unit via
     * {@link net.sf.freecol.client.control.InGameController#moveUnit}; in
     * TERRAIN mode they step the selected-tile cursor to a neighbour.  When
     * nothing is selected (END_TURN mode) they fall back to the raw-grid free
     * pan so the map stays navigable.
     *
     * <p><b>Isometric vs. rectangular.</b> The model is isometric — a model
     * {@link Direction} steps in the diamond lattice, so {@code Direction.N}
     * jumps two raw rows — but this viewer draws a plain rectangular grid.  To
     * keep on-screen movement matching the pressed key, the four orthogonal
     * keys resolve to the {@code Direction} whose <em>raw</em> step lands on the
     * visually adjacent cell (computed parity-aware via
     * {@link Map#getDirection}); the four diagonal keys map to the isometric
     * corner directions, whose raw offset shifts with row parity (documented in
     * {@link #intentToDirection}).
     */
    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        bindMove(im, am, Intent.UP,    0, -1, "UP", "NUMPAD8");
        bindMove(im, am, Intent.DOWN,  0,  1, "DOWN", "NUMPAD2");
        bindMove(im, am, Intent.LEFT, -1,  0, "LEFT", "NUMPAD4");
        bindMove(im, am, Intent.RIGHT, 1,  0, "RIGHT", "NUMPAD6");
        bindMove(im, am, Intent.NW,   -1, -1, "HOME", "NUMPAD7");
        bindMove(im, am, Intent.NE,    1, -1, "PAGE_UP", "NUMPAD9");
        bindMove(im, am, Intent.SW,   -1,  1, "END", "NUMPAD1");
        bindMove(im, am, Intent.SE,    1,  1, "PAGE_DOWN", "NUMPAD3");
    }

    /**
     * An on-screen movement intent from a key press.  Distinct from a model
     * {@link Direction} because the four orthogonal intents resolve to a
     * parity-dependent {@code Direction} (see {@link #intentToDirection}).
     */
    private enum Intent { UP, DOWN, LEFT, RIGHT, NW, NE, SW, SE }

    /**
     * Bind the given keystrokes to the given {@link Intent}; {@code (panDx,
     * panDy)} is the raw-grid pan used as a fallback when nothing is selected.
     */
    private void bindMove(InputMap im, ActionMap am, Intent intent,
                          int panDx, int panDy, String... keys) {
        final String name = "move_" + intent;
        for (String key : keys) {
            im.put(KeyStroke.getKeyStroke(key), name);
        }
        am.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    handleMoveKey(intent, panDx, panDy);
                }
            });
    }

    /**
     * Resolve a movement {@link Intent} to the model {@link Direction} to apply
     * from a reference tile.
     *
     * <p>The four orthogonal intents pick the {@code Direction} whose step from
     * {@code ref} lands on the visually adjacent raw cell — always a real
     * isometric neighbour, but which one depends on {@code ref}'s row parity
     * (e.g. the cell straight above is {@code NE} on even rows, {@code NW} on
     * odd rows), so we look it up via {@link Map#getDirection}.  The four
     * diagonal intents map straight to the isometric corner directions; their
     * raw-grid offset likewise shifts with parity, so a diagonal key may read as
     * a straight or diagonal step depending on the row — an inherent artefact of
     * flattening the isometric map onto a rectangular grid.
     *
     * @return The {@code Direction}, or null if the intended neighbour is off
     *     the map.
     */
    private Direction intentToDirection(Intent intent, Tile ref) {
        final Map map = getMap();
        if (map == null || ref == null) return null;
        switch (intent) {
        case UP:    return dirToRaw(ref, ref.getX(), ref.getY() - 1);
        case DOWN:  return dirToRaw(ref, ref.getX(), ref.getY() + 1);
        case LEFT:  return Direction.W;
        case RIGHT: return Direction.E;
        case NW:    return Direction.NW;
        case NE:    return Direction.NE;
        case SW:    return Direction.SW;
        case SE:    return Direction.SE;
        default:    return null;
        }
    }

    /** The {@code Direction} from {@code ref} to the raw cell {@code (tx, ty)}. */
    private Direction dirToRaw(Tile ref, int tx, int ty) {
        final Map map = getMap();
        final Tile t = (map == null) ? null : map.getTile(tx, ty);
        return (t == null) ? null : map.getDirection(ref, t);
    }

    /**
     * Handle a movement key: move the active unit (MOVE_UNITS), step the
     * selected-tile cursor (TERRAIN), or raw-grid pan the focus when nothing is
     * selected.  Mirrors {@code MoveAction.actionPerformed}.
     */
    private void handleMoveKey(Intent intent, int panDx, int panDy) {
        if (this.viewMode == GUI.ViewMode.MOVE_UNITS
            && this.activeUnit != null && this.activeUnit.getTile() != null) {
            final Direction d = intentToDirection(intent, this.activeUnit.getTile());
            if (d != null) {
                final Unit u = this.activeUnit;
                this.freeColClient.getInGameController().moveUnit(u, d);
                // Focus follows the (possibly moved) unit.
                if (u.getTile() != null) setFocus(u.getTile());
            }
            return;
        }
        if (this.viewMode == GUI.ViewMode.TERRAIN && this.selectedTile != null) {
            final Direction d = intentToDirection(intent, this.selectedTile);
            if (d != null) {
                final Tile n = this.selectedTile.getNeighbourOrNull(d);
                if (n != null) this.gui.changeView(n);
            }
            return;
        }
        // Nothing selected: keep the map navigable with a raw-grid free pan.
        panFocus(panDx, panDy);
    }


    // View-state accessors, delegated to from ClassicGUI.

    GUI.ViewMode getViewMode() {
        return this.viewMode;
    }

    Unit getActiveUnit() {
        return this.activeUnit;
    }

    Tile getSelectedTile() {
        return this.selectedTile;
    }

    /**
     * Get the focus tile, lazily defaulting to a sensible starting tile the
     * first time it is needed (a settlement, a unit, the player's entry tile,
     * or the map centre).
     *
     * @return The current focus {@code Tile}, or null if there is no map yet.
     */
    Tile getFocus() {
        if (this.focus == null) {
            this.focus = defaultFocus();
        }
        return this.focus;
    }

    void setFocus(Tile tile) {
        this.focus = tile;
        repaint();
    }

    /**
     * Mark the cached minimap raster stale so it is rebuilt before the next
     * paint.  Called from {@code ClassicGUI.refresh}/{@code refreshTile} — the
     * hooks the controllers fire when the model changes (exploration, new
     * settlements, unit movement) — so the minimap picks up map changes without
     * rebuilding on ordinary (pan/cursor) repaints.
     */
    void invalidateMinimap() {
        this.minimapDirty = true;
    }

    /** TERRAIN view mode: a tile is selected (see {@code GUI.changeView(Tile)}). */
    void changeToTerrain(Tile tile) {
        this.viewMode = GUI.ViewMode.TERRAIN;
        this.selectedTile = tile;
        this.activeUnit = null;
        if (tile != null) this.focus = tile;
        repaint();
    }

    /** MOVE_UNITS mode: an active unit is selected (centre on it). */
    void changeToMoveUnits(Unit unit) {
        this.viewMode = GUI.ViewMode.MOVE_UNITS;
        this.activeUnit = unit;
        if (unit != null && unit.getTile() != null) {
            this.selectedTile = unit.getTile();
            this.focus = unit.getTile();
        }
        repaint();
    }

    /** END_TURN mode: clear active unit and selected tile. */
    void changeToEndTurn() {
        this.viewMode = GUI.ViewMode.END_TURN;
        this.activeUnit = null;
        this.selectedTile = null;
        repaint();
    }


    // Internals

    private Map getMap() {
        return (this.freeColClient.getGame() == null) ? null
            : this.freeColClient.getGame().getMap();
    }

    /**
     * Find a reasonable initial focus tile: the player's first settlement, else
     * their first unit, else their entry tile, else the map centre.
     */
    private Tile defaultFocus() {
        final Player player = this.freeColClient.getMyPlayer();
        if (player != null) {
            final List<Settlement> settlements = player.getSettlementList();
            if (!settlements.isEmpty() && settlements.get(0).getTile() != null) {
                return settlements.get(0).getTile();
            }
            final Unit unit = player.getUnits().findFirst().orElse(null);
            if (unit != null && unit.getTile() != null) {
                return unit.getTile();
            }
            if (player.getEntryTile() != null) {
                return player.getEntryTile();
            }
        }
        final Map map = getMap();
        return (map == null) ? null
            : map.getTile(map.getWidth() / 2, map.getHeight() / 2);
    }

    /** Screen x of the left edge of the cell for map column {@code x}. */
    private int screenX(int x, int focusX) {
        return getWidth() / 2 + (x - focusX) * TILE_W - TILE_W / 2;
    }

    /** Screen y of the top edge of the cell for map row {@code y}. */
    private int screenY(int y, int focusY) {
        return getHeight() / 2 + (y - focusY) * TILE_H - TILE_H / 2;
    }

    /**
     * Pan the focus by {@code (dx, dy)} raw grid cells, clamped to the map.
     *
     * <p>The classic viewer draws on a plain rectangular grid keyed on raw map
     * coordinates, so panning steps by raw {@code x}/{@code y} — not via
     * {@link net.sf.freecol.common.model.Direction} (whose isometric N/S steps
     * two rows), so the grid recentres exactly one cell in the pressed
     * direction.
     */
    private void panFocus(int dx, int dy) {
        final Map map = getMap();
        final Tile f = getFocus();
        if (map == null || f == null) return;
        final int nx = Math.max(0, Math.min(map.getWidth() - 1, f.getX() + dx));
        final int ny = Math.max(0, Math.min(map.getHeight() - 1, f.getY() + dy));
        final Tile tile = map.getTile(nx, ny);
        if (tile != null && tile != this.focus) {
            this.focus = tile;
            repaint();
        }
    }

    /**
     * Update the edge-scroll direction from the current mouse position, starting
     * or stopping the repeating scroll timer as the mouse enters or leaves an
     * edge hot zone.
     */
    private void updateEdgeScroll(Point p) {
        // The minimap sits in the bottom-left corner, inside the edge hot zone;
        // don't edge-scroll while the mouse is over it.
        final Rectangle mb = minimapBounds();
        if (mb != null && mb.contains(p)) {
            stopEdgeScroll();
            return;
        }
        int dx = 0;
        int dy = 0;
        if (p.x < EDGE_SCROLL_MARGIN) dx = -1;
        else if (p.x >= getWidth() - EDGE_SCROLL_MARGIN) dx = 1;
        if (p.y < EDGE_SCROLL_MARGIN) dy = -1;
        else if (p.y >= getHeight() - EDGE_SCROLL_MARGIN) dy = 1;
        this.edgeDX = dx;
        this.edgeDY = dy;
        if (dx == 0 && dy == 0) {
            this.edgeScrollTimer.stop();
        } else if (!this.edgeScrollTimer.isRunning()) {
            this.edgeScrollTimer.start();
        }
    }

    /** Stop edge scrolling (mouse left the panel). */
    private void stopEdgeScroll() {
        this.edgeDX = 0;
        this.edgeDY = 0;
        this.edgeScrollTimer.stop();
    }

    /** Resolve the map {@link Tile} under a screen point, or null if off-map. */
    private Tile tileAt(int px, int py) {
        final Map map = getMap();
        final Tile f = getFocus();
        if (map == null || f == null) return null;
        final int x = f.getX()
            + Math.floorDiv(px - (getWidth() / 2 - TILE_W / 2), TILE_W);
        final int y = f.getY()
            + Math.floorDiv(py - (getHeight() / 2 - TILE_H / 2), TILE_H);
        return map.getTile(x, y);
    }

    /**
     * Map click → select the tile through the {@code GUI}/controller path,
     * porting {@code SwingGUI.clickAt}: an unexplored tile just takes the focus;
     * an owned colony opens the colony panel; an owned unit becomes the active
     * unit (MOVE_UNITS); anything else selects the tile in TERRAIN mode.  Unlike
     * {@code SwingGUI}, a single click already terrain-selects (the rectangular
     * classic grid has no drag-vs-click ambiguity to disambiguate with a
     * double-click), which also arms the TERRAIN-mode cursor keys.
     */
    private void onClick(MouseEvent e) {
        // A click inside the minimap overlay recentres the main view; intercept
        // it before the normal tile-selection logic so it does not also select a
        // terrain tile underneath the box.
        if (minimapClick(e)) return;

        final Tile tile = tileAt(e.getX(), e.getY());
        if (tile == null) return;
        requestFocusInWindow();
        final Player player = this.freeColClient.getMyPlayer();

        if (!tile.isExplored()) { // Select (focus) unexplored tiles
            this.gui.setFocus(tile);
            return;
        }
        final Settlement settlement = tile.getSettlement();
        if (settlement != null) {
            if (settlement instanceof Colony && player != null
                && player.owns(settlement)) {
                this.gui.showColonyPanel((Colony) settlement, null);
            } else { // Foreign/indian settlement: just centre for now
                this.gui.setFocus(tile);
            }
            return;
        }
        final Unit unit = tile.getFirstUnit();
        if (unit != null && player != null && player.owns(unit)) {
            this.gui.changeView(unit, false); // Make our unit active
        } else if (unit != null) { // Someone else's unit: select the tile
            this.gui.setFocus(tile);
        } else { // Empty explored tile: terrain-select
            this.gui.changeView(tile);
        }
    }


    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        final Map map = getMap();
        final Tile f = getFocus();
        if (map == null || f == null) {
            paintWaiting(g);
            return;
        }

        final int focusX = f.getX();
        final int focusY = f.getY();
        final int cols = getWidth() / TILE_W + 2;
        final int rows = getHeight() / TILE_H + 2;

        for (int dy = -rows; dy <= rows; dy++) {
            for (int dx = -cols; dx <= cols; dx++) {
                final Tile tile = map.getTile(focusX + dx, focusY + dy);
                if (tile == null) continue;
                paintTile(g, tile, screenX(tile.getX(), focusX),
                          screenY(tile.getY(), focusY));
            }
        }

        paintCursor(g, focusX, focusY);
        paintMinimap(g);
    }

    /** Paint one tile: terrain, then any settlement or unit on top. */
    private void paintTile(Graphics2D g, Tile tile, int sx, int sy) {
        if (!tile.isExplored()) {
            // Unexplored: leave the black background (classic "fog").
            return;
        }
        // Fetch the tile at its native 16x16 size and let the (nearest-neighbour)
        // scaling in paintComponent up-scale it, so classic pixels stay crisp.
        final BufferedImage terrain = this.lib.getTerrainImage(
            tile.getType(), tile.getX(), tile.getY(), SRC_SIZE);
        if (terrain != null) {
            g.drawImage(terrain, sx, sy, TILE_W, TILE_H, null);
        }

        final Settlement settlement = tile.getSettlement();
        if (settlement != null) {
            drawCentered(g, this.lib.getSettlementImage(settlement,
                    ImageLibrary.TILE_SIZE), sx, sy);
        } else {
            final Unit unit = tile.getFirstUnit();
            if (unit != null) {
                drawCentered(g, this.lib.getScaledUnitImage(unit), sx, sy);
            }
        }
    }

    /**
     * Draw an image centred within a tile cell at {@code (sx, sy)}, shrunk to
     * fit the cell (preserving aspect) when it is larger — FreeCol's own
     * unit/settlement art is sized for its 128&times;64 tiles, much bigger than
     * our square classic cell.
     */
    private void drawCentered(Graphics2D g, BufferedImage img, int sx, int sy) {
        if (img == null) return;
        int w = img.getWidth();
        int h = img.getHeight();
        if (w > TILE_W || h > TILE_H) {
            final double s = Math.min((double) TILE_W / w, (double) TILE_H / h);
            w = Math.max(1, (int) Math.round(w * s));
            h = Math.max(1, (int) Math.round(h * s));
        }
        final int x = sx + (TILE_W - w) / 2;
        final int y = sy + (TILE_H - h) / 2;
        g.drawImage(img, x, y, w, h, null);
    }

    /** Highlight the active-unit tile (or the selected tile) with a cursor. */
    private void paintCursor(Graphics2D g, int focusX, int focusY) {
        final Tile cursor =
            (this.activeUnit != null && this.activeUnit.getTile() != null)
            ? this.activeUnit.getTile() : this.selectedTile;
        if (cursor == null) return;
        final int sx = screenX(cursor.getX(), focusX);
        final int sy = screenY(cursor.getY(), focusY);
        final Stroke old = g.getStroke();
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(sx + 1, sy + 1, TILE_W - 3, TILE_H - 3);
        g.setStroke(old);
    }

    // Minimap overlay (item (d)): a scaled whole-map overview in the bottom-left
    // corner, giving a navigation aid the 48px main view cannot (it shows only a
    // handful of tiles).  Not isometric — a plain rectangular map.getWidth() x
    // map.getHeight() raster, unlike FreeCol's own iso MiniMap.

    /** {@code c} if non-null, else {@code fallback} (guards missing resources). */
    private static Color orElse(Color c, Color fallback) {
        return (c != null) ? c : fallback;
    }

    /**
     * Rebuild {@link #minimapCache} if it is stale, then return the on-screen
     * bounds of the minimap box (bottom-left), or null if there is no map to
     * draw.  Both the paint and the click/edge-scroll hit-tests go through here,
     * so they agree on the box geometry.
     */
    private Rectangle minimapBounds() {
        if (this.minimapDirty || this.minimapCache == null) {
            buildMinimap();
        }
        if (this.minimapCache == null) return null;
        final int mmW = this.minimapCache.getWidth();
        final int mmH = this.minimapCache.getHeight();
        return new Rectangle(MINIMAP_MARGIN, getHeight() - mmH - MINIMAP_MARGIN,
                             mmW, mmH);
    }

    /**
     * Render the whole map into {@link #minimapCache}: one
     * {@link #minimapPPT}-px square per tile, background colour for unexplored
     * tiles, {@link ImageLibrary#getMinimapPoliticsColor} for explored terrain,
     * and the owner's nation colour for a tile carrying a settlement or unit.
     * Iterates every tile, so it runs only on a rebuild (see
     * {@link #invalidateMinimap}).
     */
    private void buildMinimap() {
        this.minimapDirty = false;
        final Map map = getMap();
        if (map == null) { this.minimapCache = null; return; }
        final int w = map.getWidth();
        final int h = map.getHeight();
        if (w <= 0 || h <= 0) { this.minimapCache = null; return; }

        final int ppt = Math.max(1, Math.min(MINIMAP_MAX / w, MINIMAP_MAX / h));
        this.minimapPPT = ppt;
        final Color bg = orElse(ImageLibrary.getMinimapBackgroundColor(),
                                new Color(0x0a2a3a));
        final BufferedImage img = new BufferedImage(w * ppt, h * ppt,
                                                    BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, w * ppt, h * ppt);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final Tile tile = map.getTile(x, y);
                if (tile == null || !tile.isExplored()) continue;
                Color c = orElse(
                    ImageLibrary.getMinimapPoliticsColor(tile.getType()), bg);
                final Settlement s = tile.getSettlement();
                if (s != null && s.getOwner() != null) {
                    c = orElse(s.getOwner().getNationColor(), c);
                } else {
                    final Unit u = tile.getFirstUnit();
                    if (u != null && u.getOwner() != null) {
                        c = orElse(u.getOwner().getNationColor(), c);
                    }
                }
                g.setColor(c);
                g.fillRect(x * ppt, y * ppt, ppt, ppt);
            }
        }
        g.dispose();
        this.minimapCache = img;
    }

    /**
     * Blit the cached minimap in the bottom-left corner with a framed border,
     * then overlay a rectangle marking the tile region currently visible in the
     * main view (derived from the focus tile and the main view's tile span).
     */
    private void paintMinimap(Graphics2D g) {
        final Rectangle b = minimapBounds();
        if (b == null) return;
        final Color border = orElse(ImageLibrary.getMinimapBorderColor(),
                                    Color.WHITE);
        // A dark frame so the box reads over any terrain, then the raster.
        g.setColor(Color.BLACK);
        g.fillRect(b.x - 2, b.y - 2, b.width + 4, b.height + 4);
        g.drawImage(this.minimapCache, b.x, b.y, null);
        g.setColor(border);
        g.drawRect(b.x - 1, b.y - 1, b.width + 1, b.height + 1);

        // Viewport box: the main view spans getWidth()/TILE_W by
        // getHeight()/TILE_H tiles centred on the focus.  Clip to the minimap so
        // a focus near the map edge doesn't draw lines out over the terrain.
        final Tile f = getFocus();
        if (f == null) return;
        final int ppt = this.minimapPPT;
        final int halfCols = Math.max(0, getWidth() / TILE_W / 2);
        final int halfRows = Math.max(0, getHeight() / TILE_H / 2);
        final int vx = b.x + (f.getX() - halfCols) * ppt;
        final int vy = b.y + (f.getY() - halfRows) * ppt;
        final int vw = (halfCols * 2 + 1) * ppt;
        final int vh = (halfRows * 2 + 1) * ppt;
        final Shape oldClip = g.getClip();
        g.setClip(b);
        g.setColor(border);
        g.drawRect(vx, vy, vw, vh);
        g.setClip(oldClip);
    }

    /**
     * If {@code e} falls inside the minimap box, translate it to a map tile and
     * recentre the main view on it, returning true so the caller skips the normal
     * terrain-selection path.
     */
    private boolean minimapClick(MouseEvent e) {
        final Rectangle b = minimapBounds();
        if (b == null || !b.contains(e.getPoint())) return false;
        requestFocusInWindow();
        final Map map = getMap();
        if (map != null && this.minimapPPT > 0) {
            final int tx = (e.getX() - b.x) / this.minimapPPT;
            final int ty = (e.getY() - b.y) / this.minimapPPT;
            final Tile t = map.getTile(
                Math.max(0, Math.min(map.getWidth() - 1, tx)),
                Math.max(0, Math.min(map.getHeight() - 1, ty)));
            if (t != null) this.gui.setFocus(t);
        }
        return true;
    }

    private void paintWaiting(Graphics2D g) {
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 18f));
        g.drawString("Classic UI — waiting for map…", 24, 32);
    }
}
