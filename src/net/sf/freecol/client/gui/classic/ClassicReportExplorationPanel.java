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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Region;


/**
 * The classic-UI <b>Exploration Report</b> (accelerator {@code shift F2}) over the
 * original's map-and-wax-seal illustration ({@code REPORT8.PIK}).  Lists the
 * named regions the player has discovered — the same data as FreeCol's own
 * {@code ReportExplorationPanel}: region name, type, the turn it was discovered
 * and its exploration score, newest first.  Uses the shared
 * {@link ClassicReportPanel} frame.
 */
final class ClassicReportExplorationPanel extends ClassicReportPanel {

    private static final int COL_NAME = 6;
    private static final int COL_TYPE = 120;
    private static final int COL_TURN = 210;
    private static final int COL_SCORE = 270;

    /** Newest discoveries first, then higher score. */
    private static final Comparator<Region> BY_RECENT =
        Comparator.comparingInt((Region r) -> r.getDiscoveredIn().getNumber())
            .thenComparingInt(Region::getScoreValue).reversed();


    ClassicReportExplorationPanel(FreeColClient freeColClient, ImageLibrary lib,
                                  Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT8.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportExplorationAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Map map = this.freeColClient.getGame().getMap();
        if (map == null) return;

        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString(cap("head.region"), COL_NAME, HEAD_Y);
        g.drawString(cap("head.type"), COL_TYPE, HEAD_Y);
        g.drawString(cap("head.turn"), COL_TURN, HEAD_Y);
        g.drawString(cap("head.score"), COL_SCORE, HEAD_Y);

        final List<Region> regions = new ArrayList<>();
        for (Region r : map.getRegions()) {
            if (r.getDiscoveredIn() != null) regions.add(r);
        }
        if (regions.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString(cap("empty.regions"), COL_NAME, ROW_Y0);
            return;
        }
        regions.sort(BY_RECENT);

        int y = ROW_Y0;
        int i = 0;
        for (Region r : regions) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            if ((i & 1) == 1) {
                g.setColor(ROW_ALT);
                g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
            }
            g.setFont(font(6f, Font.BOLD));
            g.setColor(FG);
            String name = r.getName();
            if (name == null || name.isEmpty()) {
                name = Messages.message(r.getType().getNameKey());
            }
            g.drawString(clip(name, 22), COL_NAME, y);
            g.drawString(clip(Messages.message(r.getType().getNameKey()), 14),
                         COL_TYPE, y);
            g.drawString(String.valueOf(r.getDiscoveredIn().getNumber()),
                         COL_TURN, y);
            g.drawString(String.valueOf(r.getScoreValue()), COL_SCORE, y);
            y += ROW_H;
            i++;
        }
    }
}
