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
import java.util.HashMap;
import java.util.Map;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The classic-UI <b>Labour Advisor report</b> — the original's
 * "ARBEITSBERATER-BERICHT" (accelerator {@code F4} in the original; see the
 * README's key-scheme note for why the classic UI does not yet claim that
 * key), over the colonists-at-the-dock illustration
 * ({@code image.classic_original.pik.REPORT4.PIK}, shared with the Production
 * Report).
 *
 * <p>This is a <b>reversal of an earlier decision</b>: the plan used to file
 * Labour among four reports judged FreeCol-only and deferred (see
 * {@code classic_ui_plan/optional-reports.md}'s git history). The expert's
 * capture ({@code F4_Arbeitsberater_Labor}) shows the original genuinely has
 * this screen, so it moved back into the faithful build. Education, History
 * and Requirements are unaffected — the capture says nothing about them.
 *
 * <p>A fixed <b>three-column census</b> of every "person" unit type the player
 * can have, portrait + localized name + count, grouped exactly as the capture
 * groups them (not spec order, and not a simple three-way split of it):
 * primary-good producers (farm/plantation/mine/trap/lumber) on the left,
 * building/processing experts plus the fisherman and preacher in the middle,
 * and the "special" civil/military roles on the right. A type with zero units
 * still gets a row (the capture shows {@code Jesuitenmissionare 0}) — only
 * types unavailable to the player's nation/ruleset are skipped.
 *
 * <p><b>Deliberately not built yet:</b> the capture's own subtitle promises
 * "(click an item to zoom)" — a drill-down akin to FreeCol's
 * {@code ReportLabourDetailPanel} — but clicking here does nothing, so this
 * panel does not paint that caption (it would advertise an interaction that
 * does not exist). The row geometry below is still laid out exactly as the
 * capture shows it, reserving the same space, so the drill-down is a
 * self-contained follow-up rather than a relayout.
 */
final class ClassicReportLabourPanel extends ClassicReportPanel {

    /** The three grouped columns, in the capture's own order (not spec order). */
    private static final String[][] COLUMN_UNIT_IDS = {
        {   // Primary-good producers (raw materials off the land).
            "model.unit.expertFarmer", "model.unit.masterSugarPlanter",
            "model.unit.masterTobaccoPlanter", "model.unit.masterCottonPlanter",
            "model.unit.expertFurTrapper", "model.unit.expertLumberJack",
            "model.unit.expertOreMiner", "model.unit.expertSilverMiner",
        },
        {   // Building/processing experts, plus the fisherman and preacher.
            "model.unit.expertFisherman", "model.unit.masterDistiller",
            "model.unit.masterTobacconist", "model.unit.masterWeaver",
            "model.unit.masterFurTrader", "model.unit.masterCarpenter",
            "model.unit.masterBlacksmith", "model.unit.masterGunsmith",
            "model.unit.firebrandPreacher",
        },
        {   // The "special" civil/military/other roles.
            "model.unit.elderStatesman", "model.unit.hardyPioneer",
            "model.unit.veteranSoldier", "model.unit.seasonedScout",
            "model.unit.jesuitMissionary", "model.unit.indenturedServant",
            "model.unit.pettyCriminal", "model.unit.indianConvert",
            "model.unit.freeColonist",
        },
    };

    /** Column pitch (virtual pixels); the three columns split the canvas evenly. */
    private static final int COL_W = VW / 3;

    /** Column-relative x-origins, read off the expert's raw capture. */
    private static final int COL_ICON = 3;
    private static final int COL_NAME = 14;
    private static final int COL_COUNT = 41;

    /** Baseline pitch of one entry (portrait + stacked name/count lines). */
    private static final int ENTRY_H = 18;

    /** Baseline of the first row's name line, read off the raw capture. */
    private static final int FIRST_Y = TITLE_H + 21;

    /** The count line's baseline, relative to its entry's name-line baseline. */
    private static final int COUNT_DY = 6;


    ClassicReportLabourPanel(FreeColClient freeColClient, ImageLibrary lib,
                             Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT4.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportLabourAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;
        final Specification spec = player.getSpecification();

        final Map<UnitType, Integer> counts = new HashMap<>();
        for (Unit unit : player.getUnitSet()) {
            counts.merge(unit.getType(), 1, Integer::sum);
        }

        for (int col = 0; col < COLUMN_UNIT_IDS.length; col++) {
            paintColumn(g, spec, player, counts, col);
        }
    }

    private void paintColumn(Graphics2D g, Specification spec, Player player,
                             Map<UnitType, Integer> counts, int col) {
        final int dx = col * COL_W;
        int y = FIRST_Y;
        for (String id : COLUMN_UNIT_IDS[col]) {
            final UnitType unitType = spec.getUnitType(id);
            if (unitType == null || !unitType.isAvailableTo(player)) continue;
            // A row's own content (name + count line) reaches y+COUNT_DY, not
            // y+ENTRY_H (that's the *next* row's slot) — so check against that,
            // or the ninth row of a 9-entry column falsely clips.
            if (y + COUNT_DY + 4 > BODY_BOTTOM) break;   // out of room; rest clipped
            paintEntry(g, unitType, counts.getOrDefault(unitType, 0), dx, y);
            y += ENTRY_H;
        }
    }

    private void paintEntry(Graphics2D g, UnitType unitType, int count,
                            int dx, int y) {
        // getScaledUnitTypeImage (not getSmallUnitTypeImage) matches every other
        // classic report's sprite lookups: it resolves through the same native
        // ~16px classic-pack aliasing that drawFitted's upscale branch expects.
        final BufferedImage img = this.lib.getScaledUnitTypeImage(unitType);
        if (img != null) drawFitted(g, img, COL_ICON + dx, y - 13, 16);

        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString(clip(Messages.getName(unitType), 20), COL_NAME + dx, y);
        g.drawString(String.valueOf(count), COL_COUNT + dx, y + COUNT_DY);
    }
}
