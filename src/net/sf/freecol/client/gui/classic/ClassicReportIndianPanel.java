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
import net.sf.freecol.common.model.NationType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tension;


/**
 * The classic-UI <b>Indian Advisor report</b> (accelerator {@code F5}) over the
 * original's native-scout illustration ({@code REPORT1.PIK}).  One row per native
 * nation the player has <em>contacted</em> ({@code player.hasContacted}, the same
 * filter FreeCol's own {@code ReportIndianPanel} applies): the nation's capital
 * settlement sprite, its name, how many of its settlements the player knows of, and
 * its tension toward the player.
 *
 * <p>Unlike FreeCol's panel this deliberately does <em>not</em> show the tribe's
 * true settlement total: that comes from {@code igc().nationSummary()}, an async
 * network fetch which a static paint cannot drive.  The locally-known count
 * ({@code opponent.getIndianSettlementList()}) is shown instead.  Uses the shared
 * {@link ClassicReportPanel} frame.
 */
final class ClassicReportIndianPanel extends ClassicReportPanel {

    private static final int COL_SPRITE = 4;
    private static final int COL_NAME = 22;
    private static final int COL_SETTLEMENTS = 150;
    private static final int COL_TENSION = 210;


    ClassicReportIndianPanel(FreeColClient freeColClient, ImageLibrary lib,
                             Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT1.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportIndianAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;

        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString(cap("head.tribe"), COL_NAME, HEAD_Y);
        g.drawString(cap("head.villages"), COL_SETTLEMENTS, HEAD_Y);
        g.drawString(cap("head.attitude"), COL_TENSION, HEAD_Y);

        final List<Player> tribes = new ArrayList<>();
        for (Player p : (Iterable<Player>)
                 this.freeColClient.getGame().getLiveNativePlayers()::iterator) {
            if (player.hasContacted(p)) tribes.add(p);
        }
        if (tribes.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString(cap("empty.tribes"), COL_NAME, ROW_Y0);
            return;
        }
        tribes.sort(Comparator.comparing(
            (Player p) -> Messages.message(p.getNationLabel())));

        int y = ROW_Y0;
        int i = 0;
        for (Player tribe : tribes) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            paintRow(g, player, tribe, y, (i & 1) == 1);
            y += ROW_H;
            i++;
        }
    }

    private void paintRow(Graphics2D g, Player player, Player tribe, int y,
                          boolean alt) {
        if (alt) {
            g.setColor(ROW_ALT);
            g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
        }
        // The tribe's capital settlement sprite.
        final NationType nt = tribe.getNationType();
        if (nt != null && nt.getCapitalType() != null) {
            final BufferedImage img
                = this.lib.getScaledSettlementTypeImage(nt.getCapitalType());
            if (img != null) drawFitted(g, img, COL_SPRITE, y - ROW_H + 4, 14);
        }

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(clip(Messages.message(tribe.getNationLabel()), 20),
                     COL_NAME, y);

        // Settlements the player knows of.
        g.drawString(String.valueOf(tribe.getIndianSettlementList().size()),
                     COL_SETTLEMENTS, y);

        // The tribe's tension toward us.
        final Tension tension = tribe.getTension(player);
        g.drawString((tension == null) ? "?"
                     : clip(Messages.message(tension.getNameKey()), 12),
                     COL_TENSION, y);
    }
}
