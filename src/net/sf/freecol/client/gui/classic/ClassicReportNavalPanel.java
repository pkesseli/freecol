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
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI <b>Naval Advisor report</b> (accelerator {@code F8}) — the fleet
 * roster over the original's ship illustration ({@code REPORT7.PIK}).  The naval
 * counterpart of the Military Advisor: it groups every naval unit the player owns
 * by type (via {@link ClassicReportRosterPanel}), the same set FreeCol's own
 * {@code ReportNavalPanel} reports ({@code unit.isNaval()}).
 */
final class ClassicReportNavalPanel extends ClassicReportRosterPanel {

    ClassicReportNavalPanel(FreeColClient freeColClient, ImageLibrary lib,
                            Runnable onClose) {
        super(freeColClient, lib, onClose);
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT7.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportNavalAction.name";
    }

    @Override
    protected boolean isReportable(Unit u) {
        return u.isNaval();
    }

    @Override
    protected String emptyText() {
        return cap("empty.naval");
    }
}
