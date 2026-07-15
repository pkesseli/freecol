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

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
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
 * <p>Uses the shared {@link ClassicReportPanel} frame (virtual 320&times;200
 * canvas, dimmed backdrop, gold title, Okay/Escape) over the sepia fort
 * illustration ({@code REPORT6.PIK}).  One row per colony:
 *
 * <ul>
 *   <li>the colony's flag/settlement sprite and its name;</li>
 *   <li>the Sons-of-Liberty percentage ({@code opening_015});</li>
 *   <li>the population (colonists working the colony);</li>
 *   <li>the military units garrisoned in it, as sprites ({@code opening_014});</li>
 *   <li>a couple of the colony's largest net productions.</li>
 * </ul>
 */
final class ClassicReportColonyPanel extends ClassicReportPanel {

    /** Column x-origins (virtual pixels). */
    private static final int COL_FLAG = 3;
    private static final int COL_NAME = 20;
    private static final int COL_SOL = 96;
    private static final int COL_POP = 130;
    private static final int COL_MIL = 158;
    private static final int COL_PROD = 232;


    ClassicReportColonyPanel(FreeColClient freeColClient, ImageLibrary lib,
                             Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT6.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportColonyAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        paintHeader(g);
        paintRows(g);
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
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            paintRow(g, colony, y, (i & 1) == 1);
            y += ROW_H;
            i++;
        }
        if (colonies.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString("No colonies yet.", COL_NAME, ROW_Y0);
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
}
