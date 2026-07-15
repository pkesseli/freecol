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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI <b>Military Advisor report</b> (accelerator {@code F7}; design
 * ref: the expert's {@code opening_014} garrison shot).  Where the Colony
 * Advisor shows the garrison <em>per colony</em>, the Military Advisor is the
 * army roster: it groups every land military unit the player owns by its type
 * and role, so a glance shows the whole standing army.
 *
 * <p>Uses the shared {@link ClassicReportPanel} frame over the sepia fort
 * illustration ({@code REPORT6.PIK} — the fortification being the garrison
 * image; it doubles as the Colony Advisor backdrop, a shared framing the expert
 * may re-assign once REPORT9 has a home).  One row per type&times;role group:
 * its sprite, the localized type/role label, and the count.  Military units are
 * the same set FreeCol's own {@code ReportMilitaryPanel} reports — non-naval and
 * either an expert soldier or otherwise offensive.
 */
final class ClassicReportMilitaryPanel extends ClassicReportPanel {

    private static final int COL_SPRITE = 6;
    private static final int COL_NAME = 24;
    private static final int COL_COUNT = 150;


    ClassicReportMilitaryPanel(FreeColClient freeColClient, ImageLibrary lib,
                               Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT6.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportMilitaryAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;

        final List<Group> groups = collectGroups(player);
        if (groups.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString("No military units.", COL_NAME, ROW_Y0 + 10);
            return;
        }

        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString("Unit", COL_NAME, HEAD_Y);
        g.drawString("Qty", COL_COUNT, HEAD_Y);

        int y = ROW_Y0;
        int i = 0;
        for (Group grp : groups) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            if ((i & 1) == 1) {
                g.setColor(ROW_ALT);
                g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
            }
            final BufferedImage img = this.lib.getScaledUnitImage(grp.sample);
            if (img != null) drawFitted(g, img, COL_SPRITE, y - ROW_H + 4, 14);
            g.setFont(font(6f, Font.BOLD));
            g.setColor(FG);
            g.drawString(clip(grp.label, 26), COL_NAME, y);
            g.drawString("x" + grp.count, COL_COUNT, y);
            y += ROW_H;
            i++;
        }
    }

    /**
     * Group every land military unit the player owns by type&times;role,
     * keeping a sample unit for the sprite.  Sorted by descending count then
     * label so the roster is stable (the player's unit set is unordered).
     */
    private List<Group> collectGroups(Player player) {
        final Map<String, Group> byKind = new HashMap<>();
        for (Unit u : (Iterable<Unit>) player.getUnits()::iterator) {
            if (!isMilitary(u)) continue;
            final String key = u.getType().getId() + "|" + u.getRole().getId();
            Group grp = byKind.get(key);
            if (grp == null) {
                final String label = Messages.message(Messages.getUnitLabel(
                        null, u.getType().getId(), 1, null, u.getRole().getId(),
                        null));
                grp = new Group(u, label);
                byKind.put(key, grp);
            }
            grp.count++;
        }
        final List<Group> groups = new ArrayList<>(byKind.values());
        groups.sort(Comparator.comparingInt((Group grp) -> -grp.count)
                    .thenComparing(grp -> grp.label));
        return groups;
    }

    /** The reportable set from FreeCol's {@code ReportMilitaryPanel}. */
    private static boolean isMilitary(Unit u) {
        return !u.isNaval()
            && (u.hasAbility(Ability.EXPERT_SOLDIER) || u.isOffensiveUnit());
    }

    /** A type&times;role tally with a sample unit for the sprite. */
    private static final class Group {
        final Unit sample;
        final String label;
        int count;

        Group(Unit sample, String label) {
            this.sample = sample;
            this.label = label;
        }
    }
}
