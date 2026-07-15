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
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI <b>Cargo Report</b> (accelerator {@code shift F1}) over the
 * original's ship illustration ({@code REPORT7.PIK}, shared with the Naval
 * Advisor).  Where the Naval Advisor tallies the fleet by type, this shows each
 * carrier's <em>load</em>: one row per carrier (the reportable set from FreeCol's
 * own {@code ReportCargoPanel} — {@code isCarrier() || canCarryTreasure()}), its
 * sprite and name, then the goods it holds (icon+amount) and the units aboard
 * (sprites).  Uses the shared {@link ClassicReportPanel} frame.
 */
final class ClassicReportCargoPanel extends ClassicReportPanel {

    private static final int COL_SPRITE = 4;
    private static final int COL_NAME = 20;
    private static final int COL_CARGO = 92;

    /** Horizontal step between cargo items along a row. */
    private static final int GOODS_STEP = 26;
    private static final int UNIT_STEP = 14;


    ClassicReportCargoPanel(FreeColClient freeColClient, ImageLibrary lib,
                            Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT7.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportCargoAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;

        final List<Unit> carriers = new ArrayList<>();
        for (Unit u : (Iterable<Unit>) player.getUnits()::iterator) {
            if (u.isCarrier() || u.canCarryTreasure()) carriers.add(u);
        }
        if (carriers.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString("No carriers.", COL_NAME, ROW_Y0);
            return;
        }
        carriers.sort(Comparator.comparing(
            (Unit u) -> Messages.message(u.getLabel())));

        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString("Carrier", COL_NAME, HEAD_Y);
        g.drawString("Cargo", COL_CARGO, HEAD_Y);

        int y = ROW_Y0;
        int i = 0;
        for (Unit carrier : carriers) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            paintRow(g, carrier, y, (i & 1) == 1);
            y += ROW_H;
            i++;
        }
    }

    private void paintRow(Graphics2D g, Unit carrier, int y, boolean alt) {
        if (alt) {
            g.setColor(ROW_ALT);
            g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
        }
        final BufferedImage sprite = this.lib.getScaledUnitImage(carrier);
        if (sprite != null) drawFitted(g, sprite, COL_SPRITE, y - ROW_H + 4, 14);

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(clip(Messages.message(carrier.getLabel()), 13),
                     COL_NAME, y);

        int x = COL_CARGO;
        // Goods held: icon + amount.
        for (Goods goods : carrier.getCompactGoodsList()) {
            if (x > VW - GOODS_STEP) return;   // row full; rest clipped
            final BufferedImage icon
                = this.lib.getScaledGoodsTypeImage(goods.getType());
            if (icon != null) drawFitted(g, icon, x, y - ROW_H + 5, 11);
            g.drawString(String.valueOf(goods.getAmount()), x + 11, y);
            x += GOODS_STEP;
        }
        // Units aboard: sprite each.
        for (Unit aboard : carrier.getUnitList()) {
            if (x > VW - UNIT_STEP) return;    // row full; rest clipped
            final BufferedImage img = this.lib.getScaledUnitImage(aboard);
            if (img != null) drawFitted(g, img, x, y - ROW_H + 4, 13);
            x += UNIT_STEP;
        }
        // Nothing aboard.
        if (carrier.getCompactGoodsList().isEmpty()
            && carrier.getUnitList().isEmpty()) {
            g.setColor(HEAD_FG);
            g.drawString("(empty)", COL_CARGO, y);
        }
    }
}
