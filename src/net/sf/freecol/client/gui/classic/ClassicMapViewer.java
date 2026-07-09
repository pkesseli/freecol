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
import java.awt.RenderingHints;
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


    ClassicMapViewer(FreeColClient freeColClient, ImageLibrary lib) {
        this.freeColClient = freeColClient;
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
     * Bind the keyboard pan controls, matching FreeCol's own map accelerators
     * (see {@code moveAction.*.accelerator} in the message bundle): the arrow
     * keys and numpad 8/2/4/6 pan orthogonally, numpad 7/9/1/3 and
     * Home/PageUp/End/PageDown pan diagonally.  Bound {@code WHEN_IN_FOCUSED_WINDOW}
     * so panning works whenever the map window is focused, independent of which
     * child component currently holds focus.
     */
    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        bindPan(im, am,  0, -1, "UP", "NUMPAD8");
        bindPan(im, am,  0,  1, "DOWN", "NUMPAD2");
        bindPan(im, am, -1,  0, "LEFT", "NUMPAD4");
        bindPan(im, am,  1,  0, "RIGHT", "NUMPAD6");
        bindPan(im, am, -1, -1, "HOME", "NUMPAD7");
        bindPan(im, am,  1, -1, "PAGE_UP", "NUMPAD9");
        bindPan(im, am, -1,  1, "END", "NUMPAD1");
        bindPan(im, am,  1,  1, "PAGE_DOWN", "NUMPAD3");
    }

    /** Bind the given keystrokes to a focus pan of {@code (dx, dy)} raw cells. */
    private void bindPan(InputMap im, ActionMap am, int dx, int dy,
                         String... keys) {
        final String name = "pan_" + dx + "_" + dy;
        for (String key : keys) {
            im.put(KeyStroke.getKeyStroke(key), name);
        }
        am.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    panFocus(dx, dy);
                }
            });
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

    /** Map click → select the clicked tile and recentre on it. */
    private void onClick(MouseEvent e) {
        final Map map = getMap();
        final Tile f = getFocus();
        if (map == null || f == null) return;
        final int x = f.getX()
            + Math.floorDiv(e.getX() - (getWidth() / 2 - TILE_W / 2), TILE_W);
        final int y = f.getY()
            + Math.floorDiv(e.getY() - (getHeight() / 2 - TILE_H / 2), TILE_H);
        final Tile tile = map.getTile(x, y);
        if (tile != null && tile.isExplored()) {
            this.selectedTile = tile;
            this.focus = tile;
            requestFocusInWindow();
            repaint();
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

    private void paintWaiting(Graphics2D g) {
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 18f));
        g.drawString("Classic UI — waiting for map…", 24, 32);
    }
}
