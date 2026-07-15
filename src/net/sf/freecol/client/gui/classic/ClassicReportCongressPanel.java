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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.Player;


/**
 * The classic-UI <b>Continental Congress</b> report (accelerator {@code F6}) over
 * the original's two-men-at-a-desk illustration ({@code REPORT3.PIK}).  A summary
 * of the founding-father standing: who is currently being recruited and the
 * liberty-bell progress toward them at the top, then the roster of fathers already
 * in the Congress — portrait, name and category — grouped by type.  Uses the
 * shared {@link ClassicReportPanel} frame.
 */
final class ClassicReportCongressPanel extends ClassicReportPanel {

    private static final int COL_PORTRAIT = 4;
    private static final int COL_NAME = 22;
    private static final int COL_TYPE = 150;

    /** y of the summary lines, above the father roster. */
    private static final int SUMMARY_Y0 = TITLE_H + 12;

    /** Baseline of this report's own column heads, below the summary block. */
    private static final int TABLE_HEAD_Y = TITLE_H + 36;

    /** Baseline of the first father row; a row-pitch below the heads (see ROW_Y0). */
    private static final int TABLE_Y0 = TABLE_HEAD_Y + ROW_H;


    ClassicReportCongressPanel(FreeColClient freeColClient, ImageLibrary lib,
                               Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT3.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportCongressAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;

        paintSummary(g, player);
        paintFathers(g, player);
    }

    /** Who is being recruited and the bell progress toward them. */
    private void paintSummary(Graphics2D g, Player player) {
        g.setFont(font(7f, Font.BOLD));
        g.setColor(FG);

        final FoundingFather current = player.getCurrentFather();
        final String recruiting = (current == null)
            ? "(none)" : Messages.getName(current);
        g.drawString("Recruiting: " + clip(recruiting, 24), COL_NAME - 2,
                     SUMMARY_Y0);

        final int liberty = Math.max(0, player.getLiberty());
        final int total = player.getTotalFoundingFatherCost();
        final String bells = "Bells: " + liberty
            + ((total > 0) ? " / " + total : "")
            + "  (+" + player.getLibertyProductionNextTurn() + "/turn)";
        g.drawString(bells, COL_NAME - 2, SUMMARY_Y0 + 12);
    }

    /** The fathers already in the Congress, grouped by type. */
    private void paintFathers(Graphics2D g, Player player) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString("Founding Father", COL_NAME, TABLE_HEAD_Y);
        g.drawString("Category", COL_TYPE, TABLE_HEAD_Y);

        final List<FoundingFather> fathers
            = new ArrayList<>(player.getFoundingFathers());
        if (fathers.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString("No founding fathers yet.", COL_NAME, TABLE_Y0);
            return;
        }
        fathers.sort(Comparator.comparing((FoundingFather f) -> f.getType())
                     .thenComparing(Messages::getName));

        int y = TABLE_Y0;
        int i = 0;
        for (FoundingFather father : fathers) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            if ((i & 1) == 1) {
                g.setColor(ROW_ALT);
                g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
            }
            final BufferedImage portrait
                = this.lib.getFoundingFatherImage(father, false);
            if (portrait != null) {
                drawFitted(g, portrait, COL_PORTRAIT, y - ROW_H + 4, 14);
            }
            g.setFont(font(6f, Font.BOLD));
            g.setColor(FG);
            g.drawString(clip(Messages.getName(father), 26), COL_NAME, y);
            g.drawString(Messages.message(father.getTypeKey()), COL_TYPE, y);
            y += ROW_H;
            i++;
        }
    }
}
