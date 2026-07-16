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
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.WorkLocation;


/**
 * The classic-UI <b>Colony Advisor report</b> — the original 1994
 * <em>Colonization</em>'s "KOLONIEBERATER-BERICHT" (design refs: the expert's
 * {@code opening_014} and {@code opening_015}).
 *
 * <p>As in the original this is <b>one report paged through several column
 * sets</b>: every page keeps the same left column — the colony's flag sprite, a
 * population badge and its name — and swaps what is shown to its right, with a
 * <b>subtitle</b> naming the current page.  The two pages the expert's shots
 * document are:
 *
 * <ul>
 *   <li>{@link Page#SONS_OF_LIBERTY} — "Söhne der Freiheit" ({@code opening_015}):
 *       the Sons-of-Liberty percentage, the building producing the colony's bells,
 *       the bells produced per turn, and the member/tory figures.</li>
 *   <li>{@link Page#MILITARY} — "Militärgarnision" ({@code opening_014}): the
 *       military units garrisoned in the colony, as sprites.</li>
 * </ul>
 *
 * <p>Note the original shows <b>no column heads</b> — the subtitle names the page
 * and the columns are self-evident — so this panel deliberately paints none.
 *
 * <p><b>Paging interaction is provisional</b> (the shots show only the layouts, not
 * the keys): <b>Left</b>/<b>Right</b>/<b>Space</b> cycle the pages, and — as on
 * every report — the Okay plate and <b>Escape</b> close.  Pending the expert's
 * sign-off on how the original actually pages.
 */
final class ClassicReportColonyPanel extends ClassicReportPanel {

    /** The column sets this report pages through (design refs in the class doc). */
    private enum Page {
        SONS_OF_LIBERTY("colony.sol"),
        MILITARY("colony.military");

        /**
         * The message-key tail for the page's subtitle, under the classic.report.
         * namespace.  In German these resolve to the original's own captions —
         * "Söhne der Freiheit" / "Militärgarnision" — matching {@code opening_015}
         * / {@code opening_014}.
         */
        final String subtitleKey;

        Page(String subtitleKey) {
            this.subtitleKey = subtitleKey;
        }
    }

    /** Column x-origins (virtual pixels), read off the expert's shots. */
    private static final int COL_FLAG = 3;
    private static final int COL_POP = 19;
    private static final int COL_NAME = 31;
    private static final int COL_DATA = 96;

    /** Sons-of-Liberty page columns, to the right of the shared left column. */
    private static final int COL_SOL = COL_DATA;
    private static final int COL_BUILDING = COL_DATA + 26;
    private static final int COL_BELLS = COL_DATA + 94;
    private static final int COL_FIGURES = COL_DATA + 122;

    /** Baseline of the page subtitle, just under the title bar. */
    private static final int SUBTITLE_Y = TITLE_H + 7;

    /**
     * Baseline of the first colony row.  A full row-pitch below the subtitle, for
     * the same reason {@link #ROW_Y0} sits below the heads (a row's cell is drawn
     * above its baseline) — this report has no column heads, so it starts higher.
     */
    private static final int PAGE_ROW_Y0 = SUBTITLE_Y + ROW_H;

    private static final Color SUBTITLE_FG = new Color(0xE8, 0xD0, 0x70);
    private static final Color POP_BG = new Color(0x00, 0x00, 0x00, 0xB0);

    /** The column set currently shown. */
    private Page page = Page.SONS_OF_LIBERTY;


    ClassicReportColonyPanel(FreeColClient freeColClient, ImageLibrary lib,
                             Runnable onClose) {
        super(freeColClient, lib, onClose);
        installPagingKeys();
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT6.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportColonyAction.name";
    }


    // Paging

    private void installPagingKeys() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("RIGHT"), "classic_reportNextPage");
        im.put(KeyStroke.getKeyStroke("SPACE"), "classic_reportNextPage");
        am.put("classic_reportNextPage", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    turnPage(1);
                }
            });
        im.put(KeyStroke.getKeyStroke("LEFT"), "classic_reportPrevPage");
        am.put("classic_reportPrevPage", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    turnPage(-1);
                }
            });
    }

    private void turnPage(int delta) {
        final Page[] pages = Page.values();
        final int n = pages.length;
        this.page = pages[((this.page.ordinal() + delta) % n + n) % n];
        repaint();
    }


    // Painting

    @Override
    protected void paintBody(Graphics2D g) {
        paintSubtitle(g);

        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;
        final List<Colony> colonies = player.getColonyList();
        if (colonies.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString(cap("empty.colonies"), COL_NAME, PAGE_ROW_Y0);
            return;
        }

        int y = PAGE_ROW_Y0;
        int i = 0;
        for (Colony colony : colonies) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            paintRow(g, colony, y, (i & 1) == 1);
            y += ROW_H;
            i++;
        }
    }

    /** The page name, centred under the title (the original's own sub-caption). */
    private void paintSubtitle(Graphics2D g) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(SUBTITLE_FG);
        final String s = cap(this.page.subtitleKey);
        g.drawString(s, (VW - g.getFontMetrics().stringWidth(s)) / 2, SUBTITLE_Y);
    }

    private void paintRow(Graphics2D g, Colony colony, int y, boolean alt) {
        if (alt) {
            g.setColor(ROW_ALT);
            g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
        }
        paintColonyCell(g, colony, y);
        switch (this.page) {
        case SONS_OF_LIBERTY:
            paintSonsOfLibertyCells(g, colony, y);
            break;
        case MILITARY:
            paintGarrisonCells(g, colony, y);
            break;
        }
    }

    /** The shared left column: flag sprite, population badge, colony name. */
    private void paintColonyCell(Graphics2D g, Colony colony, int y) {
        final BufferedImage flag = this.lib.getScaledSettlementImage(colony);
        if (flag != null) drawFitted(g, flag, COL_FLAG, y - ROW_H + 4, 14);

        // Population badge (the boxed number left of the name in the original).
        final String pop = String.valueOf(colony.getUnitCount());
        g.setFont(font(6f, Font.BOLD));
        final int pw = g.getFontMetrics().stringWidth(pop);
        g.setColor(POP_BG);
        g.fillRect(COL_POP - 1, y - 7, pw + 3, 9);
        g.setColor(HEAD_FG);
        g.drawString(pop, COL_POP, y);

        g.setColor(FG);
        g.drawString(clip(colony.getName(), 11), COL_NAME, y);
    }

    /**
     * The Sons-of-Liberty page ({@code opening_015}): SoL %, the bell-producing
     * building, bells per turn, and a figure per member.
     */
    private void paintSonsOfLibertyCells(Graphics2D g, Colony colony, int y) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(colony.getSonsOfLiberty() + "%", COL_SOL, y);

        final GoodsType liberty = firstLibertyGoodsType(colony);
        if (liberty == null) return;

        // The building producing the colony's bells (the original's "Druckerei").
        final WorkLocation wl = colony.getWorkLocationForProducing(liberty);
        if (wl instanceof Building) {
            final String name = Messages.getName(((Building) wl).getType());
            g.drawString(clip(name, 13), COL_BUILDING, y);
        }

        // Bells per turn, as icon + amount.
        final int bells = colony.getNetProductionOf(liberty);
        final BufferedImage icon = this.lib.getScaledGoodsTypeImage(liberty);
        if (icon != null) drawFitted(g, icon, COL_BELLS, y - ROW_H + 5, 11);
        g.setColor(FG);
        g.drawString(String.valueOf(bells), COL_BELLS + 12, y);

        paintMemberFigures(g, colony, y);
    }

    /**
     * One colonist figure per Sons-of-Liberty member (the small figures at the
     * right of {@code opening_015}), clipped to the room available.
     */
    private void paintMemberFigures(Graphics2D g, Colony colony, int y) {
        final int members
            = colony.getUnitCount() * colony.getSonsOfLiberty() / 100;
        final Unit sample = colony.getUnitList().isEmpty()
            ? null : colony.getUnitList().get(0);
        if (sample == null) return;
        final BufferedImage img = this.lib.getScaledUnitImage(sample);
        if (img == null) return;
        int x = COL_FIGURES;
        for (int n = 0; n < members; n++) {
            if (x > VW - 10) break;    // out of room; rest are clipped
            drawFitted(g, img, x, y - ROW_H + 4, 9);
            x += 8;
        }
    }

    /**
     * The military-garrison page ({@code opening_014}): the offensive land units
     * standing in the colony, as sprites.
     */
    private void paintGarrisonCells(Graphics2D g, Colony colony, int y) {
        final Tile tile = colony.getTile();
        if (tile == null) return;
        int x = COL_DATA;
        boolean any = false;
        for (Unit u : tile.getUnitList()) {
            if (!u.isOffensiveUnit() || u.isNaval()) continue;
            if (x > VW - 14) break;    // row full; rest are clipped
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y - ROW_H + 4, 13);
            x += 12;
            any = true;
        }
        if (!any) {
            g.setFont(font(6f, Font.BOLD));
            g.setColor(HEAD_FG);
            g.drawString("—", COL_DATA, y);
        }
    }

    /** The spec's bells/liberty goods type, if any. */
    private GoodsType firstLibertyGoodsType(Colony colony) {
        final List<GoodsType> types
            = colony.getSpecification().getLibertyGoodsTypeList();
        return types.isEmpty() ? null : types.get(0);
    }
}
