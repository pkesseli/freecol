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
import java.util.List;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.HighScore;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Turn;


/**
 * The classic-UI <b>score breakdown</b> ("Kolonisationspunkte" / High Scores) —
 * FreeCol's own {@code GUI.showHighScoresPanel(String, List<HighScore>)} seam,
 * reached by the Game menu's High Scores item (no accelerator in either
 * FreeCol's own key scheme or the observed original one, so F10 is
 * uncontested; see the package README's "Report screens" section).
 *
 * <p>Unlike every other classic report, this one <b>takes its data as
 * constructor arguments</b> rather than reading the live model from
 * {@link #paintBody}: {@code InGameController.highScoresHandler} has already
 * done the server round trip before {@code ClassicGUI.showHighScoresPanel} is
 * ever called, so there is no {@code nationSummary}-style trap here — the
 * panel is built synchronously, on the EDT, straight from the given list.
 *
 * <p>One row per {@link HighScore}, in the order given (already best-first,
 * per {@code HighScore.tidyScores}): rank, score, the same localized
 * governor/president-of-nation headline the standard {@code
 * ReportHighScoresPanel} shows, the retirement turn, and colony/unit counts.
 * That panel's other fields (difficulty, independence turn, the
 * original/final nation name and type, the retirement date) are omitted for
 * space — a single 320&times;200 screen has no room for its full
 * nine-field-per-entry layout, the same information-availability call already
 * made for the other reports (e.g. Foreign Affairs' own deliberate omissions).
 *
 * <p><b>No confirmed original backdrop or layout for this screen</b> (see the
 * README): {@code REPORT3.PIK} (otherwise single-booked by the Continental
 * Congress) is reused as a records-at-a-desk scene, the closest thematic fit
 * among the free single-use backdrops — a guess, not a faithfulness claim, in
 * the same vein as the build-queue picker's placeholder look.
 */
final class ClassicReportHighScoresPanel extends ClassicReportPanel {

    private static final int COL_RANK = 3;
    private static final int COL_SCORE = 18;
    private static final int COL_PLAYER = 52;
    private static final int COL_TURN = 200;
    private static final int COL_COLONIES = 232;
    private static final int COL_UNITS = 270;

    /** y of the optional {@code messageId} prefix line, above the table. */
    private static final int SUMMARY_Y0 = TITLE_H + 12;

    /**
     * This report's own column heads.  Space for the summary line above is
     * always reserved (whether or not {@code messageId} is actually present)
     * so the table position does not shift between the two cases.
     */
    private static final int TABLE_HEAD_Y = TITLE_H + 24;

    /** Baseline of the first score row; a row-pitch below the heads (see ROW_Y0). */
    private static final int TABLE_Y0 = TABLE_HEAD_Y + ROW_H;

    private final String messageId;
    private final List<HighScore> scores;


    ClassicReportHighScoresPanel(FreeColClient freeColClient, ImageLibrary lib,
            Runnable onClose, String messageId, List<HighScore> scores) {
        super(freeColClient, lib, onClose);
        this.messageId = messageId;
        this.scores = scores;
    }


    @Override
    protected String backgroundKey() {
        return "image.classic_original.pik.REPORT3.PIK";
    }

    @Override
    protected String titleKey() {
        return "reportHighScoresAction.name";
    }

    @Override
    protected void paintBody(Graphics2D g) {
        if (this.messageId != null) {
            g.setFont(font(7f, Font.BOLD));
            g.setColor(FG);
            g.drawString(Messages.message(this.messageId), COL_RANK, SUMMARY_Y0);
        }

        if (this.scores == null || this.scores.isEmpty()) {
            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString(cap("empty.highScores"), COL_RANK, TABLE_Y0);
            return;
        }

        g.setFont(font(6f, Font.BOLD));
        g.setColor(HEAD_FG);
        g.drawString("#", COL_RANK, TABLE_HEAD_Y);
        g.drawString(cap("head.score"), COL_SCORE, TABLE_HEAD_Y);
        g.drawString(cap("head.player"), COL_PLAYER, TABLE_HEAD_Y);
        g.drawString(cap("head.turn"), COL_TURN, TABLE_HEAD_Y);
        g.drawString(cap("head.colonies"), COL_COLONIES, TABLE_HEAD_Y);
        g.drawString(cap("head.units"), COL_UNITS, TABLE_HEAD_Y);

        int y = TABLE_Y0;
        int i = 0;
        for (HighScore score : this.scores) {
            if (y + ROW_H > BODY_BOTTOM) break;   // out of room; rest are clipped
            paintRow(g, score, i, y);
            y += ROW_H;
            i++;
        }
    }

    private void paintRow(Graphics2D g, HighScore score, int i, int y) {
        if ((i & 1) == 1) {
            g.setColor(ROW_ALT);
            g.fillRect(0, y - ROW_H + 3, VW, ROW_H);
        }
        g.setFont(font(6f, Font.BOLD));
        g.setColor(FG);
        g.drawString((i + 1) + ".", COL_RANK, y);
        g.drawString(String.valueOf(score.getScore()), COL_SCORE, y);

        final boolean independent = score.getIndependenceTurn() > 0;
        final String headlineKey = independent
            ? "report.highScores.president" : "report.highScores.governor";
        final StringTemplate headline = StringTemplate.template(headlineKey)
            .addName("%name%", score.getPlayerName())
            .addName("%nation%", score.getNewLandName());
        g.drawString(clip(Messages.message(headline), 26), COL_PLAYER, y);

        final int turn = score.getRetirementTurn();
        g.drawString((turn <= 0) ? "—"
            : Messages.message(Turn.getTurnLabel(turn)), COL_TURN, y);

        g.drawString(String.valueOf(score.getColonyCount()), COL_COLONIES, y);
        g.drawString(String.valueOf(score.getUnitCount()), COL_UNITS, y);
    }
}
