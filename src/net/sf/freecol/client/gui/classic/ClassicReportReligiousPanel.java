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
 * The classic-UI <b>Religious Advisor report</b> (accelerator {@code F1}; the
 * original's preacher-and-congregation illustration {@code REPORT2.PIK}).  Crosses
 * drive immigration, so this report shows the immigration standing at the top —
 * accumulated crosses toward the next immigrant and the empire-wide cross output
 * per turn — then one row per colony with the crosses it produces.  Uses the
 * shared {@link ClassicReportPanel} frame.
 */
final class ClassicReportReligiousPanel extends ClassicReportPanel {

    private static final int COL_ICON = 6;
    private static final int COL_NAME = 24;
    private static final int COL_CROSSES = 150;

    /** y of the immigration summary lines, above the per-colony table. */
    private static final int SUMMARY_Y0 = TITLE_H + 12;

    /** Baseline of this report's own column heads, below the summary block. */
    private static final int TABLE_HEAD_Y = TITLE_H + 40;

    /** Baseline of the first colony row; a row-pitch below the heads (see ROW_Y0). */
    private static final int TABLE_Y0 = TABLE_HEAD_Y + ROW_H;


    ClassicReportReligiousPanel(FreeColClient freeColClient, ImageLibrary lib,
                                Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT2.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportReligionAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;

        // The immigration goods (crosses); usually exactly one type.
        final List<GoodsType> crossTypes
            = player.getSpecification().getImmigrationGoodsTypeList();
        final GoodsType crosses = crossTypes.isEmpty() ? null : crossTypes.get(0);

        paintSummary(g, player, crosses);
        paintColonies(g, player, crossTypes);
    }

    /** Immigration accumulation and total cross production. */
    private void paintSummary(Graphics2D g, Player player, GoodsType crosses) {
        g.setFont(font(7f, Font.BOLD));
        g.setColor(FG);
        if (crosses != null) {
            final BufferedImage icon = this.lib.getScaledGoodsTypeImage(crosses);
            if (icon != null) drawFitted(g, icon, COL_ICON, SUMMARY_Y0 - 10, 12);
        }
        g.drawString(cap("religion.immigration") + ": " + player.getImmigration()
                     + " / " + player.getImmigrationRequired(),
                     COL_NAME, SUMMARY_Y0);
        g.drawString(cap("religion.crossesPerTurn") + ": +"
                     + player.getTotalImmigrationProduction(),
                     COL_NAME, SUMMARY_Y0 + 12);
    }

    /** One row per colony with the crosses it produces. */
    private void paintColonies(Graphics2D g, Player player,
                               List<GoodsType> crossTypes) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString(cap("head.colony"), COL_NAME, TABLE_HEAD_Y);
        g.drawString(cap("head.crosses"), COL_CROSSES, TABLE_HEAD_Y);

        final List<Colony> colonies = player.getColonyList();
        if (colonies.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString(cap("empty.colonies"), COL_NAME, TABLE_Y0);
            return;
        }

        int y = TABLE_Y0;
        int i = 0;
        for (Colony colony : colonies) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            if ((i & 1) == 1) {
                g.setColor(ROW_ALT);
                g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
            }
            addColonyRow(y, colony);   // click a row to jump to the colony's screen
            final BufferedImage flag = this.lib.getScaledSettlementImage(colony);
            if (flag != null) drawFitted(g, flag, COL_ICON, y - ROW_H + 4, 14);

            g.setFont(font(6f, Font.BOLD));
            g.setColor(FG);
            g.drawString(clip(colony.getName(), 20), COL_NAME, y);

            int net = 0;
            for (GoodsType gt : crossTypes) {
                net += colony.getNetProductionOf(gt);
            }
            g.drawString("+" + net, COL_CROSSES, y);
            y += ROW_H;
            i++;
        }
    }
}
