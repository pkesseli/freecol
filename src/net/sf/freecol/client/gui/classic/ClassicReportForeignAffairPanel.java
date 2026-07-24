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
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Map;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;


/**
 * The classic-UI <b>Foreign Affairs report</b> — the original's
 * "AUSSENPOLITIK-BERICHT", over the map-and-wax-seal illustration
 * ({@code REPORT8.PIK}; confirmed pixel-for-pixel against the expert's capture —
 * see the package README's Foreign Affairs section for why this is shared with
 * the Exploration report rather than a spare backdrop).
 *
 * <p>One fixed-height block per European power the game has ever had — met or
 * not, alive or withdrawn (the expert's shots list a withdrawn England) — in
 * game order, with the <b>viewing player's own block last</b> (verified). A
 * withdrawn power's block shows only its name and a centred notice. A live
 * rival's block shows: colonies, average colony size, population,
 * military/naval/merchant strength, its stance toward the viewer (peace in
 * yellow, war in red), and rebel/loyalist head counts. The viewer's own block
 * instead lists its stance toward every <em>met</em> rival, one pair per slot.
 *
 * <p>Matches the confirmed field set exactly — no gold, tax, Continental
 * Congress membership or Sons-of-Liberty percentage (all of which FreeCol's own
 * {@code ReportForeignAffairPanel} shows and this deliberately omits), plus
 * three fields that panel does not have (average colony size, merchant marine,
 * rebel/loyalist counts).
 *
 * <p><b>The {@code nationSummary} trap.</b> A rival's data comes from a
 * blocking server round trip, so it is never fetched from here —
 * {@link ClassicGUI#showReportForeignAffairPanel} fetches every rival's
 * {@link NationSummary} off the EDT before this panel is even constructed, and
 * {@link #paintBody} only ever paints from that stash. The viewing player's own
 * figures are read live off its own (always-authoritative, already-local)
 * {@link Player} instead, needing no round trip at all.
 */
final class ClassicReportForeignAffairPanel extends ClassicReportPanel {

    /** Column x-origins (virtual pixels), read off the expert's raw captures. */
    private static final int COL0 = 3;
    private static final int COL_MID = 100;
    private static final int COL_RIGHT = 190;
    private static final int COL_SECOND = 80;

    /** Pitch between nation blocks, and the baseline pitch within one. */
    private static final int BLOCK_H = 45;
    private static final int LINE_H = 7;

    /** Top of the first block, directly under the title bar's own rule. */
    private static final int BLOCK_Y0 = TITLE_H + 1;

    private static final Color RULE = new Color(0x86, 0x00, 0x00);
    private static final Color PEACE_FG = new Color(0xE8, 0xD8, 0x40);
    private static final Color WAR_FG = new Color(0xE0, 0x30, 0x30);

    private final Player me;
    private final List<Player> others;
    private final Map<Player, NationSummary> summaries;


    ClassicReportForeignAffairPanel(FreeColClient freeColClient, ImageLibrary lib,
            Runnable onClose, Player me, List<Player> others,
            Map<Player, NationSummary> summaries) {
        super(freeColClient, lib, onClose);
        this.me = me;
        this.others = others;
        this.summaries = summaries;
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT8.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportForeignAction.name";
    }

    /**
     * A block's own content reaches {@code LINE_H*5} below its top (the
     * Rebellen/Loyalisten line) — {@link #BLOCK_H} (45) is the full pitch to
     * the <em>next</em> block, which leaves an unused ~10px pad after the last
     * line. Clipping against the fuller {@link #BLOCK_H} instead of this would
     * falsely drop a block that would actually have fit (as it did for a
     * fully-populated 4-nation game before this was fixed).
     */
    private static final int BLOCK_CONTENT_H = LINE_H * 5 + 4;

    @Override
    protected void paintBody(Graphics2D g) {
        int block = 0;
        for (Player other : this.others) {
            final int top = BLOCK_Y0 + block * BLOCK_H;
            if (top + BLOCK_CONTENT_H > BODY_BOTTOM) return;   // out of room; rest clipped
            paintRule(g, top);
            if (other.isDead()) {
                paintWithdrawn(g, other, top);
            } else {
                paintRival(g, other, this.summaries.get(other), top);
            }
            block++;
        }
        // The viewer's own nation is always the last block (verified).
        final int top = BLOCK_Y0 + block * BLOCK_H;
        if (top + BLOCK_CONTENT_H <= BODY_BOTTOM) {
            paintRule(g, top);
            paintOwn(g, top);
        }
    }

    private void paintRule(Graphics2D g, int top) {
        g.setColor(RULE);
        g.drawLine(0, top, VW - 1, top);
    }

    /** A power no longer in the New World: name, then a centred notice. */
    private void paintWithdrawn(Graphics2D g, Player other, int top) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString(nameLine(other), COL0, top + LINE_H);

        g.setFont(font(6f, Font.PLAIN));
        g.setColor(FG);
        final String s = cap("foreignAffairs.withdrawn");
        g.drawString(s, (VW - g.getFontMetrics().stringWidth(s)) / 2,
                     top + LINE_H * 3);
    }

    /** A live rival, painted from its fetched {@link NationSummary}. */
    private void paintRival(Graphics2D g, Player other, NationSummary ns, int top) {
        if (ns == null) return;   // fetch failed; leave the block blank, not a guess

        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString(nameLine(other), COL0, top + LINE_H);

        final int settlements = ns.getNumberOfSettlements();
        final int population = ns.getNumberOfUnits();
        final int avgSize = avgColonySize(settlements, population);

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(cap("foreignAffairs.colonies") + ": " + settlements,
                     COL0, top + LINE_H * 2);
        g.drawString(cap("foreignAffairs.avgColonySize") + ": " + avgSize,
                     COL_MID, top + LINE_H * 2);
        g.drawString(cap("foreignAffairs.population") + ": " + population,
                     COL_RIGHT, top + LINE_H * 2);

        g.drawString(cap("foreignAffairs.militaryStrength") + ": "
                     + ns.getMilitaryStrength(), COL0, top + LINE_H * 3);
        g.drawString(cap("foreignAffairs.navalStrength") + ": "
                     + ns.getNavalStrength(), COL_MID, top + LINE_H * 3);
        g.drawString(cap("foreignAffairs.mercantileMarine") + ": "
                     + ns.getMercantileMarine(), COL_RIGHT, top + LINE_H * 3);

        paintStance(g, abbr(other), ns.getStance(), COL0, top + LINE_H * 4);

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(cap("foreignAffairs.rebels") + ": " + ns.getRebels(),
                     COL0, top + LINE_H * 5);
        g.drawString(cap("foreignAffairs.loyalists") + ": " + ns.getLoyalists(),
                     COL_SECOND, top + LINE_H * 5);
    }

    /** The viewer's own block: figures read live, no {@code NationSummary}. */
    private void paintOwn(Graphics2D g, int top) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString(nameLine(this.me), COL0, top + LINE_H);

        final int settlements = this.me.getSettlementCount();
        final int population = this.me.getUnitCount();
        final int avgSize = avgColonySize(settlements, population);

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(cap("foreignAffairs.colonies") + ": " + settlements,
                     COL0, top + LINE_H * 2);
        g.drawString(cap("foreignAffairs.avgColonySize") + ": " + avgSize,
                     COL_MID, top + LINE_H * 2);
        g.drawString(cap("foreignAffairs.population") + ": " + population,
                     COL_RIGHT, top + LINE_H * 2);

        g.drawString(cap("foreignAffairs.militaryStrength") + ": "
                     + this.me.calculateStrength(false), COL0, top + LINE_H * 3);
        g.drawString(cap("foreignAffairs.navalStrength") + ": "
                     + this.me.calculateStrength(true), COL_MID, top + LINE_H * 3);
        g.drawString(cap("foreignAffairs.mercantileMarine") + ": "
                     + NationSummary.computeMercantileMarine(this.me),
                     COL_RIGHT, top + LINE_H * 3);

        paintOwnStances(g, top + LINE_H * 4);

        final int sol = this.me.getSoL();
        final int rebels = (population * sol) / 100;
        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(cap("foreignAffairs.rebels") + ": " + rebels,
                     COL0, top + LINE_H * 5);
        g.drawString(cap("foreignAffairs.loyalists") + ": " + (population - rebels),
                     COL_SECOND, top + LINE_H * 5);
    }

    /**
     * The viewer's stance toward every <em>met</em> rival (the original shows
     * no entry for an unmet or withdrawn power here), one pair per slot,
     * clipped when the row fills.
     */
    private void paintOwnStances(Graphics2D g, int y) {
        int x = COL0;
        for (Player other : this.others) {
            if (other.isDead() || !this.me.hasContacted(other)) continue;
            if (x > VW - 20) break;   // row full; rest are clipped
            paintStance(g, abbr(other), this.me.getStance(other), x, y);
            x += COL_SECOND - COL0;
        }
    }

    /** One "<Nation>: <Stance>" pair; the stance word colour-codes hostility. */
    private void paintStance(Graphics2D g, String nationAbbr, Stance stance,
                             int x, int y) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        final String prefix = nationAbbr + ": ";
        g.drawString(prefix, x, y);
        final int dx = g.getFontMetrics().stringWidth(prefix);
        final boolean war = stance == Stance.WAR;
        g.setColor(war ? WAR_FG : PEACE_FG);
        g.drawString(cap(war ? "foreignAffairs.war" : "foreignAffairs.peace"),
                     x + dx, y);
    }

    /** "<leader name> <nation abbreviation>:" — the name line of a block. */
    private String nameLine(Player player) {
        return clip(player.getName(), 20) + " " + abbr(player) + ":";
    }

    /** The localized short nation abbreviation ("Frz.", "Span.", ...). */
    private String abbr(Player player) {
        return cap("foreignAffairs.abbr." + player.getNationResourceKey());
    }

    private static int avgColonySize(int settlements, int population) {
        return (settlements <= 0) ? 0
            : Math.round((float) population / settlements);
    }
}
