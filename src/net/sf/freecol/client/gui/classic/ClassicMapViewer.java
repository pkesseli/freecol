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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JPanel;

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
 * <p><b>Phase 1, first slice.</b> This deliberately renders FreeCol's own
 * (isometric, 128&times;64) terrain art laid out on a rectangular grid, which
 * leaves diamond-shaped gaps between tiles — it proves the projection/paint
 * pipeline end-to-end. The next asset step (A2 terrain aliases) swaps in the
 * original {@code TERRAIN.SS} rectangular sprites so the grid fills cleanly;
 * see CLASSIC_UI_PLAN.md ("Phase 1 — implementation notes").
 *
 * <p>This panel owns the classic view state (view mode, focus, selected tile,
 * active unit); {@link ClassicGUI} delegates the corresponding {@code GUI}
 * facade methods to it.
 */
final class ClassicMapViewer extends JPanel {

    /** Tile cell size in pixels — FreeCol's native terrain-image size. */
    private static final int TILE_W = ImageLibrary.TILE_SIZE.width;   // 128
    private static final int TILE_H = ImageLibrary.TILE_SIZE.height;  //  64

    private final FreeColClient freeColClient;

    /** Image library used for terrain/unit/settlement lookups. */
    private final ImageLibrary lib;

    // Classic view state (owned here; ClassicGUI delegates to these).
    private GUI.ViewMode viewMode = GUI.ViewMode.END_TURN;
    private Tile focus;
    private Tile selectedTile;
    private Unit activeUnit;


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
        final BufferedImage terrain =
            this.lib.getScaledTerrainImage(tile.getType(), tile.getX(), tile.getY());
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

    /** Draw an image centred within a tile cell at {@code (sx, sy)}. */
    private void drawCentered(Graphics2D g, BufferedImage img, int sx, int sy) {
        if (img == null) return;
        final int x = sx + (TILE_W - img.getWidth()) / 2;
        final int y = sy + (TILE_H - img.getHeight()) / 2;
        g.drawImage(img, x, y, null);
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
