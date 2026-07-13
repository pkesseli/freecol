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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI <b>colony screen</b> — the signature screen of the original
 * 1994 <em>Colonization</em>.
 *
 * <p>Everything is painted into a virtual <b>320&times;200</b> canvas (the
 * original's VGA resolution) which is then up-scaled by the largest integer
 * factor that fits the window, nearest-neighbour, so the classic pixels stay
 * crisp and the layout constants below can be read straight off the original
 * screenshots.  Top to bottom:
 *
 * <ul>
 *   <li><b>Title bar</b> — colony name, turn and treasury in gold on black.</li>
 *   <li><b>Buildings pane</b> (left) — the colony's buildings on the sandy
 *   ground, each with the colonists working in it and a black production tag
 *   ({@code amount} + goods icon), drawn from the original {@code BUILDING.SS}
 *   sprite set.</li>
 *   <li><b>Tile pane</b> (right) — the 3&times;3 grid of the colony's work
 *   tiles on the wood panel ({@code WOODTILE.SS}), each worked tile carrying its
 *   colonist and a production tag.  The centre cell is the colony itself.</li>
 *   <li><b>Bottom band</b> — the original {@code COLONY.PIK} chrome (320&times;72)
 *   blitted as-is, with the Sons-of-Liberty / population figures and the units
 *   outside the colony on the left, the ships in port in the middle, the net
 *   production on the right, and the 16-slot <b>warehouse</b> row of goods icons
 *   and amounts along the bottom.</li>
 * </ul>
 *
 * <p><b>Provisional building frame map.</b> {@code BUILDING.SS} holds 48 frames;
 * which frame is which building was read off a labelled montage by eye (as for
 * {@code TERRAIN.SS} / {@code PHYS0.SS} / {@code ICONS.SS} before it).  The
 * clearly-identifiable sets are certain — the fortification walls, the dock /
 * drydock / shipyard water scenes, the sooty blacksmith chain, the churches, the
 * town hall with its banner — but several of the interchangeable house/shop/
 * factory chains are a best-effort assignment pending the expert player's
 * sign-off.  Each building therefore also carries its localized <b>name tag</b>,
 * which the original only shows on hover: that makes a mis-mapped sprite legible
 * and lets the expert correct {@link #BUILDING_FRAMES} from a screenshot.  Both
 * are noted as follow-ups in CLASSIC_UI_PLAN.md.
 *
 * <p>This is the first colony-screen slice: it <em>renders</em> the colony.
 * Interaction (dragging colonists between tiles and buildings, the build queue,
 * loading cargo) is a later slice; for now Escape or a click on the red exit
 * button closes the screen.
 */
final class ClassicColonyPanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    private static final int VW = 320;
    private static final int VH = 200;

    /** Title bar: full width, gold on black. */
    private static final int TITLE_H = 9;

    /** The colony area (buildings + work tiles) sits between title and band. */
    private static final int AREA_Y = TITLE_H;
    private static final int BUILD_W = 204;

    /** The {@code COLONY.PIK} chrome occupies the bottom 72 rows. */
    private static final int BAND_Y = 128;

    /** Work-tile cell size: the native 16px tiles at a crisp integer 2x. */
    private static final int GRID_CELL = 32;
    private static final int GRID_X = 213;
    private static final int GRID_Y = 19;

    /** Warehouse row (inside the COLONY.PIK chrome). */
    private static final int WARE_Y = 178;
    private static final int WARE_H = 22;
    private static final int WARE_W = 306;

    /** The three sub-panels of the band, and their shared top. */
    private static final int PANEL_Y = 130;
    private static final int PANEL1_X = 3;
    private static final int PANEL2_X = 122;
    private static final int PANEL3_X = 207;

    private static final Color TITLE_BG = new Color(0x00, 0x00, 0x00);
    private static final Color GOLD = new Color(0xC8, 0xB0, 0x40);
    private static final Color TAG_BG = new Color(0x00, 0x00, 0x00, 0xC0);
    private static final Color TAG_FG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color SAND = new Color(0xEB, 0xDB, 0xA2);
    private static final Color WOOD = new Color(0x49, 0x28, 0x1C);
    private static final Color FRAME = new Color(0x00, 0x00, 0x00);
    private static final Color SELECT = new Color(0x40, 0xE0, 0x40);

    /**
     * FreeCol building-type id (minus the {@code model.building.} prefix) to
     * {@code BUILDING.SS} frame.  Provisional — see the class comment.  Types
     * absent from the map (notably {@code depot} and {@code country}, which have
     * no structure of their own in the original) simply draw no sprite.
     */
    private static final Map<String, Integer> BUILDING_FRAMES = new HashMap<>();
    static {
        // Fortification walls (certain).
        BUILDING_FRAMES.put("stockade", 0);
        BUILDING_FRAMES.put("fort", 1);
        BUILDING_FRAMES.put("fortress", 2);
        // Harbour scenes (certain).
        BUILDING_FRAMES.put("docks", 6);
        BUILDING_FRAMES.put("drydock", 7);
        BUILDING_FRAMES.put("shipyard", 8);
        // Civic (the banner buildings + the colonnaded trade house).
        BUILDING_FRAMES.put("townHall", 19);
        BUILDING_FRAMES.put("printingPress", 13);
        BUILDING_FRAMES.put("newspaper", 20);
        BUILDING_FRAMES.put("customHouse", 9);
        // Churches (certain).
        BUILDING_FRAMES.put("chapel", 12);
        BUILDING_FRAMES.put("church", 37);
        BUILDING_FRAMES.put("cathedral", 38);
        // Schooling.
        BUILDING_FRAMES.put("schoolhouse", 3);
        BUILDING_FRAMES.put("college", 4);
        BUILDING_FRAMES.put("university", 5);
        // Lumber -> hammers (the open shed and the water mill).
        BUILDING_FRAMES.put("carpenterHouse", 35);
        BUILDING_FRAMES.put("lumberMill", 36);
        // Ore -> tools (the sooty, chimneyed chain; certain).
        BUILDING_FRAMES.put("blacksmithHouse", 32);
        BUILDING_FRAMES.put("blacksmithShop", 33);
        BUILDING_FRAMES.put("ironWorks", 34);
        // The interchangeable goods chains (provisional).
        BUILDING_FRAMES.put("tobacconistHouse", 21);
        BUILDING_FRAMES.put("tobacconistShop", 22);
        BUILDING_FRAMES.put("cigarFactory", 23);
        BUILDING_FRAMES.put("weaverHouse", 24);
        BUILDING_FRAMES.put("weaverShop", 25);
        BUILDING_FRAMES.put("textileMill", 26);
        BUILDING_FRAMES.put("distillerHouse", 27);
        BUILDING_FRAMES.put("rumDistillery", 28);
        BUILDING_FRAMES.put("rumFactory", 29);
        BUILDING_FRAMES.put("furTraderHouse", 18);
        BUILDING_FRAMES.put("furTradingPost", 39);
        BUILDING_FRAMES.put("furFactory", 40);
        BUILDING_FRAMES.put("armory", 41);
        BUILDING_FRAMES.put("magazine", 14);
        BUILDING_FRAMES.put("arsenal", 15);
        // Storage and livestock.
        BUILDING_FRAMES.put("warehouse", 47);
        BUILDING_FRAMES.put("warehouseExpansion", 15);
        BUILDING_FRAMES.put("stables", 46);
    }

    private final FreeColClient freeColClient;
    private final ImageLibrary lib;
    private final Colony colony;

    /** Run when the screen is dismissed (Escape / the exit button). */
    private final Runnable onClose;

    /** Cache of {@code BUILDING.SS} frames, loaded on demand. */
    private final Map<Integer, BufferedImage> buildingCache = new HashMap<>();

    /**
     * Per-building hover targets, rebuilt each paint: virtual-space bounds and
     * the localized name.  The original shows a building's name only when the
     * pointer is over it, which also keeps the names from overlapping.
     */
    private final java.util.List<java.awt.Rectangle> buildingBounds
        = new java.util.ArrayList<>();
    private final java.util.List<String> buildingNames = new java.util.ArrayList<>();

    /** Index into {@link #buildingBounds} of the hovered building, or -1. */
    private int hovered = -1;

    /** Device-space scale + origin of the virtual canvas, set on each paint. */
    private int scale = 1;
    private int originX;
    private int originY;


    ClassicColonyPanel(FreeColClient freeColClient, ImageLibrary lib,
                       Colony colony, Runnable onClose) {
        this.freeColClient = freeColClient;
        this.lib = lib;
        this.colony = colony;
        this.onClose = onClose;
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * 3, VH * 3));
        setFocusable(true);
        installKeyBindings();
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e);
                }
            });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    onHover(e);
                }
            });
    }


    // Assets

    private static String ssKey(String set, int frame) {
        return String.format("image.classic_original.ss.%s.%03d", set, frame);
    }

    /** Load (and cache) a {@code BUILDING.SS} frame; null when the pack is absent. */
    private BufferedImage buildingImage(int frame) {
        return this.buildingCache.computeIfAbsent(frame,
            n -> ImageLibrary.getUnscaledImage(ssKey("BUILDING.SS", n)));
    }


    // Dismissal

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_closeColony");
        am.put("classic_closeColony", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });
    }

    /**
     * The original's exit button is the red "E" at the bottom right of the
     * {@code COLONY.PIK} chrome; a click anywhere on it closes the screen.
     */
    private void onClick(MouseEvent e) {
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        if (vx >= WARE_W && vy >= WARE_Y) close();
    }

    /** Track which building the pointer is over, and repaint if it changed. */
    private void onHover(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        int found = -1;
        for (int i = 0; i < this.buildingBounds.size(); i++) {
            if (this.buildingBounds.get(i).contains(vx, vy)) {
                found = i;
                break;
            }
        }
        if (found != this.hovered) {
            this.hovered = found;
            repaint();
        }
    }

    private void close() {
        if (this.onClose != null) this.onClose.run();
    }


    // Painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fit the 320x200 canvas into the window at the largest integer scale.
        this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
        this.originX = (getWidth() - VW * this.scale) / 2;
        this.originY = (getHeight() - VH * this.scale) / 2;
        g.translate(this.originX, this.originY);
        g.scale(this.scale, this.scale);
        g.clipRect(0, 0, VW, VH);

        paintTitle(g);
        paintBuildings(g);
        paintWorkTiles(g);
        paintBand(g);
        paintHover(g);
        g.dispose();
    }

    /** The gold-on-black header: colony name, turn, treasury. */
    private void paintTitle(Graphics2D g) {
        g.setColor(TITLE_BG);
        g.fillRect(0, 0, VW, TITLE_H);
        final Game game = this.freeColClient.getGame();
        final Player player = this.freeColClient.getMyPlayer();
        final StringBuilder sb = new StringBuilder(this.colony.getName());
        if (game != null && game.getTurn() != null) {
            sb.append(", ").append(msg(game.getTurn().getLabel()));
        }
        if (player != null) {
            sb.append(", ").append(Messages.message("gold")).append(": ")
                .append(player.getGold());
        }
        g.setColor(GOLD);
        g.setFont(font(7f, Font.BOLD));
        final String s = sb.toString();
        g.drawString(s, (VW - g.getFontMetrics().stringWidth(s)) / 2, 7);
    }

    /**
     * The buildings pane: the sandy ground, then every building in the colony —
     * its sprite, the colonists working inside it, its production tag and its
     * name.  The original places each building at a fixed spot on the ground;
     * this first slice flows them left-to-right in rows instead (a documented
     * deviation — the fixed original layout wants the expert's slot map).
     */
    private void paintBuildings(Graphics2D g) {
        // Ground: the original tan colony ground (TERRAIN.SS.001), tiled.
        final BufferedImage ground = ImageLibrary.getUnscaledImage(ssKey("TERRAIN.SS", 1));
        fillTiled(g, ground, SAND, 0, AREA_Y, BUILD_W, BAND_Y - AREA_Y);

        this.buildingBounds.clear();
        this.buildingNames.clear();
        int x = 4;
        int y = AREA_Y + 6;
        int rowH = 0;
        for (Building b : this.colony.getBuildings()) {
            final Integer frame = BUILDING_FRAMES.get(shortId(b.getType().getId()));
            final BufferedImage img = (frame == null) ? null : buildingImage(frame);
            final int w = (img != null) ? img.getWidth() : 24;
            final int h = (img != null) ? img.getHeight() : 20;
            if (x + w > BUILD_W - 4) {           // wrap
                x = 4;
                y += rowH + 12;
                rowH = 0;
            }
            if (y + h > BAND_Y - 6) break;       // out of ground; rest are hidden
            if (img != null) {
                g.drawImage(img, x, y, null);
            } else {
                g.setColor(WOOD);
                g.fillRect(x, y + h - 6, w, 6);
            }
            paintWorkers(g, b.getUnitList(), x + 2, y + h - 2);
            paintProductionTag(g, b.getProductionInfo(), x, y - 1);
            // The name is drawn only for the hovered building (see paintHover),
            // as in the original — so record the target instead of drawing now.
            this.buildingBounds.add(new java.awt.Rectangle(x, y, w, h));
            this.buildingNames.add(Messages.getName(b.getType()));
            rowH = Math.max(rowH, h);
            x += w + 6;
        }
    }

    /** Draw the hovered building's name plate over everything, as a tooltip. */
    private void paintHover(Graphics2D g) {
        if (this.hovered < 0 || this.hovered >= this.buildingBounds.size()) return;
        final java.awt.Rectangle r = this.buildingBounds.get(this.hovered);
        paintName(g, this.buildingNames.get(this.hovered),
                  r.x - 8, r.y - 2, r.width + 16);
    }

    /**
     * The work-tile pane: the wood panel and, inset in it, the 3&times;3 grid of
     * the colony's {@link ColonyTile}s — the colony itself in the centre cell and
     * its eight neighbours around it, each drawn with the same terrain art as the
     * map, plus the colonist working it and what that tile yields.
     */
    private void paintWorkTiles(Graphics2D g) {
        final BufferedImage wood = ImageLibrary.getUnscaledImage(ssKey("WOODTILE.SS", 0));
        fillTiled(g, wood, WOOD, BUILD_W, AREA_Y, VW - BUILD_W, BAND_Y - AREA_Y);

        g.setColor(FRAME);
        g.drawRect(GRID_X - 1, GRID_Y - 1, GRID_CELL * 3 + 1, GRID_CELL * 3 + 1);

        final Tile centre = this.colony.getTile();
        for (ColonyTile ct : this.colony.getColonyTiles()) {
            final Tile t = ct.getWorkTile();
            if (t == null || centre == null) continue;
            // Place each cell by the work tile's compass Direction from the
            // colony, not its raw (x,y) offset: FreeCol's map is isometric, so
            // the eight neighbours' raw offsets do not fill a -1..+1 square.
            // The centre cell is the colony tile itself.
            final int[] cell = (t == centre)
                ? new int[] { 1, 1 } : cellForDirection(centre.getDirection(t));
            if (cell == null) continue;
            final int sx = GRID_X + cell[0] * GRID_CELL;
            final int sy = GRID_Y + cell[1] * GRID_CELL;

            final BufferedImage terrain = this.lib.getTerrainImage(
                t.getType(), t.getX(), t.getY(), new Dimension(16, 16));
            if (terrain != null) {
                g.drawImage(terrain, sx, sy, GRID_CELL, GRID_CELL, null);
            }
            if (t == centre) {
                final BufferedImage settlement
                    = this.lib.getScaledSettlementImage(this.colony);
                if (settlement != null) {
                    drawFitted(g, settlement, sx, sy, GRID_CELL);
                }
                continue;
            }
            final List<Unit> workers = ct.getUnitList();
            if (!workers.isEmpty()) {
                g.setColor(SELECT);
                g.drawRect(sx, sy, GRID_CELL - 1, GRID_CELL - 1);
                paintWorkers(g, workers, sx + GRID_CELL / 2 - 6,
                             sy + GRID_CELL - 4);
                paintProductionTag(g, ct.getProductionInfo(), sx + 1, sy + 1);
            }
        }
    }

    /** The 3&times;3 grid cell {@code {col,row}} for a compass direction, or null. */
    private static int[] cellForDirection(net.sf.freecol.common.model.Direction d) {
        if (d == null) return null;
        switch (d) {
        case NW: return new int[] { 0, 0 };
        case N:  return new int[] { 1, 0 };
        case NE: return new int[] { 2, 0 };
        case W:  return new int[] { 0, 1 };
        case E:  return new int[] { 2, 1 };
        case SW: return new int[] { 0, 2 };
        case S:  return new int[] { 1, 2 };
        case SE: return new int[] { 2, 2 };
        default: return null;
        }
    }

    /**
     * The bottom band: the original {@code COLONY.PIK} chrome, then the live
     * numbers over it — Sons of Liberty and the units outside the colony (left),
     * the ships in port (middle), the colony's net production (right), and the
     * warehouse row of goods along the bottom.
     */
    private void paintBand(Graphics2D g) {
        final BufferedImage band
            = ImageLibrary.getUnscaledImage("image.background.ColonyPanel");
        if (band != null) {
            g.drawImage(band, 0, BAND_Y, VW, VH - BAND_Y, null);
        }
        paintPopulation(g);
        paintPort(g);
        paintNetProduction(g);
        paintWarehouse(g);
    }

    /** Left panel: the SoL / tory split and the units standing in the colony. */
    private void paintPopulation(Graphics2D g) {
        final int sol = this.colony.getSonsOfLiberty();
        final int count = this.colony.getUnitCount();
        final int rebels = count * sol / 100;
        g.setFont(font(7f, Font.BOLD));
        g.setColor(TAG_FG);
        g.drawString(sol + "% (" + rebels + ")", PANEL1_X + 12, PANEL_Y + 7);
        g.drawString((100 - sol) + "% (" + (count - rebels) + ")",
                     PANEL1_X + 62, PANEL_Y + 7);

        final Tile tile = this.colony.getTile();
        if (tile == null) return;
        int x = PANEL1_X + 3;
        for (Unit u : tile.getUnitList()) {
            if (u.isNaval()) continue;                 // ships go in the port
            if (x > PANEL2_X - 16) break;
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, PANEL_Y + 12, 16);
            x += 12;
        }
    }

    /** Middle panel: the ships tied up at the dock, or the empty-harbour notice. */
    private void paintPort(Graphics2D g) {
        final Tile tile = this.colony.getTile();
        int x = PANEL2_X + 3;
        boolean any = false;
        if (tile != null) {
            for (Unit u : tile.getUnitList()) {
                if (!u.isNaval()) continue;
                if (x > PANEL3_X - 20) break;
                final BufferedImage img = this.lib.getScaledUnitImage(u);
                if (img != null) drawFitted(g, img, x, PANEL_Y + 10, 20);
                x += 20;
                any = true;
            }
        }
        if (!any) {
            // The original captions the empty dock ("Keine Schiffe im Hafen").
            // FreeCol has no such message, so reuse its own "In Port" caption.
            g.setFont(font(7f, Font.BOLD));
            g.setColor(new Color(0x60, 0x70, 0xA0));
            g.drawString(Messages.message("colonyPanel.inPort"),
                         PANEL2_X + 4, PANEL_Y + 7);
        }
    }

    /** Right panel: what the colony nets each turn, good by good. */
    private void paintNetProduction(Graphics2D g) {
        int x = PANEL3_X + 2;
        int y = PANEL_Y + 2;
        int n = 0;
        for (GoodsType gt : this.colony.getSpecification().getStorableGoodsTypeList()) {
            final int net = this.colony.getNetProductionOf(gt);
            if (net == 0) continue;
            if (n == 4) {                    // second row
                x = PANEL3_X + 2;
                y += 16;
            }
            if (n >= 8) break;
            final BufferedImage img = this.lib.getScaledGoodsTypeImage(gt);
            if (img != null) drawFitted(g, img, x, y, 12);
            tag(g, (net > 0 ? "+" : "") + net, x, y + 12);
            x += 22;
            n++;
        }
    }

    /** The warehouse row: every storable good, with what is in store. */
    private void paintWarehouse(Graphics2D g) {
        final List<GoodsType> goods
            = this.colony.getSpecification().getStorableGoodsTypeList();
        if (goods.isEmpty()) return;
        final int cw = WARE_W / goods.size();
        g.setFont(font(7f, Font.PLAIN));
        for (int i = 0; i < goods.size(); i++) {
            final GoodsType gt = goods.get(i);
            final int x = i * cw;
            final BufferedImage img = this.lib.getScaledGoodsTypeImage(gt);
            if (img != null) {
                drawFitted(g, img, x + (cw - 12) / 2, WARE_Y + 2, 12);
            }
            final String s = String.valueOf(this.colony.getGoodsCount(gt));
            g.setColor(TAG_FG);
            g.drawString(s, x + (cw - g.getFontMetrics().stringWidth(s)) / 2,
                         WARE_Y + WARE_H - 3);
        }
    }


    // Shared drawing helpers

    /** Draw the colonists of a work location in a row, ending at {@code (x, y)}. */
    private void paintWorkers(Graphics2D g, List<Unit> units, int x, int y) {
        for (Unit u : units) {
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y - 14, 14);
            x += 10;
        }
    }

    /**
     * The original's production tag: a black plate carrying the amount produced
     * and the goods icon, floating above the building or work tile.
     */
    private void paintProductionTag(Graphics2D g,
                                    net.sf.freecol.common.model.ProductionInfo pi,
                                    int x, int y) {
        if (pi == null) return;
        for (AbstractGoods ag : pi.getProduction()) {
            if (ag.getAmount() <= 0) continue;
            g.setFont(font(7f, Font.BOLD));
            final String s = String.valueOf(ag.getAmount());
            final int tw = g.getFontMetrics().stringWidth(s) + 13;
            g.setColor(TAG_BG);
            g.fillRect(x, y - 8, tw, 9);
            g.setColor(TAG_FG);
            g.drawString(s, x + 1, y - 1);
            final BufferedImage icon = this.lib.getScaledGoodsTypeImage(ag.getType());
            if (icon != null) drawFitted(g, icon, x + tw - 10, y - 8, 9);
            x += tw + 1;
        }
    }

    /** A small caption plate, used for the building names. */
    private void paintName(Graphics2D g, String name, int x, int y, int w) {
        if (name == null || name.isEmpty()) return;
        g.setFont(font(6f, Font.PLAIN));
        final int tw = g.getFontMetrics().stringWidth(name);
        final int tx = x + (w - tw) / 2;
        g.setColor(TAG_BG);
        g.fillRect(tx - 1, y - 6, tw + 2, 7);
        g.setColor(TAG_FG);
        g.drawString(name, tx, y);
    }

    /** A tiny black-plated number, as used under the net-production icons. */
    private void tag(Graphics2D g, String s, int x, int y) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(TAG_BG);
        g.fillRect(x, y - 5, g.getFontMetrics().stringWidth(s) + 2, 6);
        g.setColor(TAG_FG);
        g.drawString(s, x + 1, y);
    }

    /**
     * Draw {@code img} scaled to fit a {@code size}-px box at {@code (x, y)},
     * preserving aspect.  The original sprites are small (~16px), so this is
     * usually an up-scale; the pack-absent FreeCol art is large and shrinks.
     */
    private void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int size) {
        final double s = Math.min((double) size / img.getWidth(),
                                  (double) size / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        g.drawImage(img, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
    }

    /** Tile {@code texture} over a rectangle, falling back to a flat colour. */
    private void fillTiled(Graphics2D g, BufferedImage texture, Color fallback,
                           int x, int y, int w, int h) {
        if (texture == null) {
            g.setColor(fallback);
            g.fillRect(x, y, w, h);
            return;
        }
        final Graphics2D gg = (Graphics2D) g.create();
        gg.clipRect(x, y, w, h);
        for (int ty = y; ty < y + h; ty += texture.getHeight()) {
            for (int tx = x; tx < x + w; tx += texture.getWidth()) {
                gg.drawImage(texture, tx, ty, null);
            }
        }
        gg.dispose();
    }

    /** The last segment of a FreeCol id ({@code model.building.townHall} → {@code townHall}). */
    private static String shortId(String id) {
        final int i = id.lastIndexOf('.');
        return (i < 0) ? id : id.substring(i + 1);
    }

    /** Render a StringTemplate, guarding against nulls / missing keys. */
    private static String msg(net.sf.freecol.common.model.StringTemplate t) {
        try {
            return (t == null) ? "" : Messages.message(t);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** A font in <em>virtual</em> pixels — the paint transform scales it up. */
    private Font font(float size, int style) {
        return getFont().deriveFont(style, size);
    }
}
