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

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;


/**
 * The classic-UI <b>Trade Advisor report</b> (accelerator {@code F9}; the
 * original's economic advisor — the scales/candle/hourglass illustration
 * {@code REPORT5.PIK}).  A ledger of every storable good: the empire-wide net
 * production across all colonies (what the economy makes each turn) and the
 * good's current market sale price, so the player can see at a glance what to
 * export.  Uses the shared {@link ClassicReportPanel} frame.
 */
final class ClassicReportTradePanel extends ClassicReportPanel {

    /**
     * Two goods per row so the 21-good ledger fits the 320x200 canvas; each
     * good occupies one {@link #HALF} (160px), laid out icon | name | net | sell.
     */
    private static final int COLUMNS = 2;
    private static final int HALF = VW / 2;

    /** Column x-origins <em>within</em> one half (added to {@code col*HALF}). */
    private static final int COL_ICON = 4;
    private static final int COL_NAME = 20;
    private static final int COL_PROD = 104;
    private static final int COL_PRICE = 132;


    ClassicReportTradePanel(FreeColClient freeColClient, ImageLibrary lib,
                            Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT5.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportTradeAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;
        final Market market = player.getMarket();

        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        for (int c = 0; c < COLUMNS; c++) {
            final int dx = c * HALF;
            g.drawString("Goods", COL_NAME + dx, HEAD_Y);
            g.drawString("Net", COL_PROD + dx, HEAD_Y);
            g.drawString("$", COL_PRICE + dx, HEAD_Y);
        }

        int i = 0;
        for (GoodsType gt : player.getSpecification().getStorableGoodsTypeList()) {
            final int col = i % COLUMNS;
            final int rowY = ROW_Y0 + (i / COLUMNS) * ROW_H;
            if (rowY + ROW_H > BODY_BOTTOM) break;   // out of room; rest clipped
            paintGoods(g, gt, market, player, col * HALF, rowY, i);
            i++;
        }
    }

    private void paintGoods(Graphics2D g, GoodsType gt, Market market,
                            Player player, int dx, int y, int i) {
        if ((i & 2) == 2) {          // shade alternate visual rows
            g.setColor(ROW_ALT);
            g.fillRect(dx, y - ROW_H + 3, HALF, ROW_H);
        }
        final BufferedImage icon = this.lib.getScaledGoodsTypeImage(gt);
        if (icon != null) drawFitted(g, icon, COL_ICON + dx, y - ROW_H + 4, 13);

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(clip(Messages.getName(gt), 11), COL_NAME + dx, y);

        int net = 0;
        for (Colony colony : player.getColonyList()) {
            net += colony.getNetProductionOf(gt);
        }
        g.drawString((net > 0 ? "+" : "") + net, COL_PROD + dx, y);

        final int sell = (market == null) ? 0 : market.getPaidForSale(gt);
        g.drawString(String.valueOf(sell), COL_PRICE + dx, y);
    }
}
