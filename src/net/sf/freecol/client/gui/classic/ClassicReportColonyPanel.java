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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI <b>Colony Advisor report</b> — the original 1994
 * <em>Colonization</em>'s "KOLONIEBERATER-BERICHT" (design ref: the expert's
 * {@code opening_014} military-garrison and {@code opening_015} Sons-of-Liberty
 * sub-views).  The original pages one report through several column sets; this
 * classic-framed first slice shows the per-colony overview that both shots share.
 *
 * <p>Painted, like the colony and Europe screens, into a virtual <b>320&times;200</b>
 * canvas up-scaled by the largest integer factor that fits, nearest-neighbour, and
 * hosted in its own {@code JFrame}.  The original sepia fort illustration
 * ({@code REPORT6.PIK}) is the backdrop, dimmed so the table reads over it.  One
 * row per colony:
 *
 * <ul>
 *   <li>the colony's flag/settlement sprite and its name;</li>
 *   <li>the Sons-of-Liberty percentage ({@code opening_015});</li>
 *   <li>the population (colonists working the colony);</li>
 *   <li>the military units garrisoned in it, as sprites ({@code opening_014});</li>
 *   <li>a couple of the colony's largest net productions.</li>
 * </ul>
 *
 * <p>The red <b>OK</b> plate at the bottom right and <b>Escape</b> both close it.
 * Interaction (clicking a colony to open its screen, the original's page-through
 * to the other column sets) is a later slice.
 */
final class ClassicReportColonyPanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    private static final int VW = 320;
    private static final int VH = 200;

    private static final int TITLE_H = 9;
    private static final int HEAD_Y = TITLE_H + 8;
    private static final int ROW_Y0 = TITLE_H + 12;
    private static final int ROW_H = 15;

    /** Column x-origins (virtual pixels). */
    private static final int COL_FLAG = 3;
    private static final int COL_NAME = 20;
    private static final int COL_SOL = 96;
    private static final int COL_POP = 130;
    private static final int COL_MIL = 158;
    private static final int COL_PROD = 232;

    private static final int OK_W = 24;
    private static final int OK_H = 11;
    private static final int OK_X = VW - OK_W - 3;
    private static final int OK_Y = VH - OK_H - 3;

    private static final Color TITLE_BG = new Color(0x00, 0x00, 0x00);
    private static final Color GOLD = new Color(0xC8, 0xB0, 0x40);
    private static final Color DIM = new Color(0x20, 0x10, 0x08, 0xA8);
    private static final Color ROW_ALT = new Color(0x00, 0x00, 0x00, 0x40);
    private static final Color FG = new Color(0xFF, 0xF0, 0xD0);
    private static final Color HEAD_FG = new Color(0xE0, 0xC0, 0x60);
    private static final Color OK_BG = new Color(0x80, 0x18, 0x10);
    private static final Color OK_FG = new Color(0xFF, 0xE0, 0x40);

    private final FreeColClient freeColClient;
    private final ImageLibrary lib;

    /** Run when the screen is dismissed (Escape / the OK button). */
    private final Runnable onClose;

    /** Device-space scale + origin of the virtual canvas, set on each paint. */
    private int scale = 1;
    private int originX;
    private int originY;


    ClassicReportColonyPanel(FreeColClient freeColClient, ImageLibrary lib,
                             Runnable onClose) {
        this.freeColClient = freeColClient;
        this.lib = lib;
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
    }


    // Input

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_closeReport");
        am.put("classic_closeReport", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });
    }

    private void onClick(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        final Rectangle ok = new Rectangle(OK_X, OK_Y, OK_W, OK_H);
        if (ok.contains(vx, vy)) close();
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

        this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
        this.originX = (getWidth() - VW * this.scale) / 2;
        this.originY = (getHeight() - VH * this.scale) / 2;
        g.translate(this.originX, this.originY);
        g.scale(this.scale, this.scale);
        g.clipRect(0, 0, VW, VH);

        paintBackground(g);
        paintTitle(g);
        paintHeader(g);
        paintRows(g);
        paintOk(g);
        g.dispose();
    }

    /** The sepia fort illustration, dimmed so the table reads over it. */
    private void paintBackground(Graphics2D g) {
        final BufferedImage bg
            = ImageLibrary.getUnscaledImage("image.classic_original.pik.REPORT6.PIK");
        if (bg != null) {
            g.drawImage(bg, 0, 0, VW, VH, null);
        } else {
            g.setColor(new Color(0x8A, 0x4A, 0x24));
            g.fillRect(0, 0, VW, VH);
        }
        g.setColor(DIM);
        g.fillRect(0, TITLE_H, VW, VH - TITLE_H);
    }

    /** The gold-on-black header with the report's name. */
    private void paintTitle(Graphics2D g) {
        g.setColor(TITLE_BG);
        g.fillRect(0, 0, VW, TITLE_H);
        g.setColor(GOLD);
        g.setFont(font(7f, Font.BOLD));
        final String s = Messages.message("reportColonyAction.name");
        g.drawString(s, (VW - g.getFontMetrics().stringWidth(s)) / 2, 7);
    }

    /**
     * The column captions.  Hard-coded English for now — the same localization
     * follow-up the info/colony panels carry (there are no FreeCol message keys
     * for these short column heads).
     */
    private void paintHeader(Graphics2D g) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString("Colony", COL_NAME, HEAD_Y);
        g.drawString("SoL", COL_SOL, HEAD_Y);
        g.drawString("Pop", COL_POP - 4, HEAD_Y);
        g.drawString("Troops", COL_MIL, HEAD_Y);
    }

    /** One row per colony. */
    private void paintRows(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;
        final List<Colony> colonies = player.getColonyList();
        int y = ROW_Y0;
        int i = 0;
        for (Colony colony : colonies) {
            if (y + ROW_H > OK_Y) break;      // out of room; rest are clipped
            paintRow(g, colony, y, (i & 1) == 1);
            y += ROW_H;
            i++;
        }
        if (colonies.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString("No colonies yet.", COL_NAME, ROW_Y0 + 10);
        }
    }

    private void paintRow(Graphics2D g, Colony colony, int y, boolean alt) {
        if (alt) {
            g.setColor(ROW_ALT);
            g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
        }
        // Flag / settlement sprite.
        final BufferedImage flag = this.lib.getScaledSettlementImage(colony);
        if (flag != null) drawFitted(g, flag, COL_FLAG, y - ROW_H + 4, 14);

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(clip(colony.getName(), 13), COL_NAME, y);

        // Sons of Liberty.
        final int sol = colony.getSonsOfLiberty();
        g.drawString(sol + "%", COL_SOL, y);

        // Population.
        g.drawString(String.valueOf(colony.getUnitCount()), COL_POP, y);

        // Garrisoned military units.
        int mx = COL_MIL;
        final Tile tile = colony.getTile();
        if (tile != null) {
            for (Unit u : tile.getUnitList()) {
                if (!u.isOffensiveUnit() || u.isNaval()) continue;
                if (mx > COL_PROD - 12) break;
                final BufferedImage img = this.lib.getScaledUnitImage(u);
                if (img != null) drawFitted(g, img, mx, y - ROW_H + 4, 13);
                mx += 11;
            }
        }

        // The two largest net productions.
        paintTopProduction(g, colony, y);
    }

    /** The colony's two largest positive net productions, as icon + amount. */
    private void paintTopProduction(Graphics2D g, Colony colony, int y) {
        GoodsType g1 = null, g2 = null;
        int a1 = 0, a2 = 0;
        for (GoodsType gt : colony.getSpecification().getStorableGoodsTypeList()) {
            final int net = colony.getNetProductionOf(gt);
            if (net <= 0) continue;
            if (net > a1) { g2 = g1; a2 = a1; g1 = gt; a1 = net; }
            else if (net > a2) { g2 = gt; a2 = net; }
        }
        int x = COL_PROD;
        x = paintProd(g, g1, a1, x, y);
        paintProd(g, g2, a2, x, y);
    }

    private int paintProd(Graphics2D g, GoodsType gt, int amount, int x, int y) {
        if (gt == null || amount <= 0 || x > VW - 24) return x;
        final BufferedImage icon = this.lib.getScaledGoodsTypeImage(gt);
        if (icon != null) drawFitted(g, icon, x, y - ROW_H + 5, 11);
        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString("+" + amount, x + 11, y);
        return x + 11 + g.getFontMetrics().stringWidth("+" + amount) + 4;
    }

    /** The red OK plate at the bottom right (mirrors the original art). */
    private void paintOk(Graphics2D g) {
        g.setColor(OK_BG);
        g.fillRect(OK_X, OK_Y, OK_W, OK_H);
        g.setColor(OK_FG);
        g.drawRect(OK_X, OK_Y, OK_W - 1, OK_H - 1);
        g.setFont(font(7f, Font.BOLD));
        final String s = Messages.message("ok");
        g.drawString(s, OK_X + (OK_W - g.getFontMetrics().stringWidth(s)) / 2,
                     OK_Y + OK_H - 3);
    }


    // Shared drawing helpers

    private void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int size) {
        final double s = Math.min((double) size / img.getWidth(),
                                  (double) size / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        g.drawImage(img, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        return (s.length() <= n) ? s : s.substring(0, n - 1) + "…";
    }

    private Font font(float size, int style) {
        return getFont().deriveFont(style, size);
    }
}
