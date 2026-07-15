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

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI <b>Military Advisor report</b> (accelerator {@code F7}; design
 * ref: the expert's {@code opening_014} garrison shot).  Where the Colony
 * Advisor shows the garrison <em>per colony</em>, the Military Advisor is the
 * army roster: it groups every land military unit the player owns by type and
 * role (via {@link ClassicReportRosterPanel}), so a glance shows the whole
 * standing army.  Military units are the same set FreeCol's own
 * {@code ReportMilitaryPanel} reports — non-naval and either an expert soldier or
 * otherwise offensive.
 *
 * <p>Backdrop is the sepia fort illustration ({@code REPORT6.PIK} — the
 * fortification being the garrison image; it doubles as the Colony Advisor
 * backdrop, a shared framing the expert may re-assign once REPORT9 has a home).
 */
final class ClassicReportMilitaryPanel extends ClassicReportRosterPanel {

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
    protected boolean isReportable(Unit u) {
        return !u.isNaval()
            && (u.hasAbility(Ability.EXPERT_SOLDIER) || u.isOffensiveUnit());
    }

    @Override
    protected String emptyText() {
        return "No military units.";
    }
}
