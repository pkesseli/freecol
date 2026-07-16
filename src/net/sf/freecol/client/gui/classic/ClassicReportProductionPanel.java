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


/**
 * The classic-UI <b>Production Report</b> (accelerator {@code shift F4}) over the
 * original's colony-under-construction illustration ({@code REPORT4.PIK}).  Where
 * the Colony Advisor shows each colony's <em>two</em> largest outputs and the
 * Trade Advisor sums production empire-wide, this report is the per-colony
 * production breakdown: one row per colony listing <em>every</em> good it nets
 * positively, as icon+amount, so the player sees at a glance what each settlement
 * makes.  Uses the shared {@link ClassicReportPanel} frame.
 */
final class ClassicReportProductionPanel extends ClassicReportPanel {

    private static final int COL_FLAG = 3;
    private static final int COL_NAME = 20;
    private static final int COL_PROD = 96;

    /** Horizontal step between the per-good icon+amount cells. */
    private static final int PROD_STEP = 30;


    ClassicReportProductionPanel(FreeColClient freeColClient, ImageLibrary lib,
                                 Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT4.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportProductionAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;

        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString(cap("head.colony"), COL_NAME, HEAD_Y);
        g.drawString(cap("head.production"), COL_PROD, HEAD_Y);

        final List<Colony> colonies = player.getColonyList();
        if (colonies.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString(cap("empty.colonies"), COL_NAME, ROW_Y0);
            return;
        }

        int y = ROW_Y0;
        int i = 0;
        for (Colony colony : colonies) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            paintRow(g, colony, y, (i & 1) == 1);
            y += ROW_H;
            i++;
        }
    }

    private void paintRow(Graphics2D g, Colony colony, int y, boolean alt) {
        if (alt) {
            g.setColor(ROW_ALT);
            g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
        }
        addColonyRow(y, colony);   // click a row to jump to the colony's screen
        final BufferedImage flag = this.lib.getScaledSettlementImage(colony);
        if (flag != null) drawFitted(g, flag, COL_FLAG, y - ROW_H + 4, 14);

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(clip(colony.getName(), 13), COL_NAME, y);

        // Every good the colony nets positively, as icon + amount.
        int x = COL_PROD;
        boolean any = false;
        for (GoodsType gt : colony.getSpecification().getStorableGoodsTypeList()) {
            final int net = colony.getNetProductionOf(gt);
            if (net <= 0) continue;
            if (x > VW - PROD_STEP) break;   // row full; remaining goods clipped
            final BufferedImage icon = this.lib.getScaledGoodsTypeImage(gt);
            if (icon != null) drawFitted(g, icon, x, y - ROW_H + 5, 11);
            g.drawString("+" + net, x + 11, y);
            x += PROD_STEP;
            any = true;
        }
        if (!any) {
            g.setColor(HEAD_FG);
            g.drawString("—", COL_PROD, y);
        }
    }
}
