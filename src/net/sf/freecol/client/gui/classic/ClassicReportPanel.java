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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;


/**
 * Shared frame for the classic-UI <b>advisor report</b> screens — the original
 * 1994 <em>Colonization</em>'s full-screen report windows (design ref: the
 * expert's {@code opening_014}/{@code opening_015} shots).
 *
 * <p>Factors out the framing every report shares so each concrete report only
 * supplies its backdrop, title and body: everything is painted into a virtual
 * <b>320&times;200</b> canvas up-scaled by the largest integer factor that fits
 * the window, nearest-neighbour (so layout constants read straight off the
 * screenshots and the classic pixels stay crisp), over the dimmed sepia
 * {@code REPORTn.PIK} illustration, under a gold-on-black title bar, with a red
 * <b>Okay</b> plate at the bottom-right and <b>Escape</b> to close.  Hosted, like
 * the colony/Europe screens, in its own {@code JFrame} (the classic UI has no
 * {@code Canvas}).
 *
 * <p>Subclasses implement {@link #backgroundKey()}, {@link #titleKey()} and
 * {@link #paintBody(Graphics2D)} (which draws the header + rows in the region
 * between {@link #ROW_Y0} and {@link #BODY_BOTTOM}).  The concrete reports are
 * {@link ClassicReportColonyPanel} (the template), {@link ClassicReportMilitaryPanel},
 * {@link ClassicReportTradePanel} and {@link ClassicReportReligiousPanel}.
 */
abstract class ClassicReportPanel extends JPanel {

    /** The original VGA canvas every report is laid out in. */
    protected static final int VW = 320;
    protected static final int VH = 200;

    protected static final int TITLE_H = 9;

    /** Baseline of the column heads. */
    protected static final int HEAD_Y = TITLE_H + 8;

    /** Row pitch; a row's cell spans {@code [y-ROW_H+3, y+3)} about its baseline. */
    protected static final int ROW_H = 15;

    /**
     * Baseline of the first body row.  A full row-pitch below {@link #HEAD_Y} so
     * the first row's cell (sprites included, which start at
     * {@code y-ROW_H+4}) clears the column heads rather than colliding with them.
     */
    protected static final int ROW_Y0 = HEAD_Y + ROW_H;

    private static final int OK_W = 24;
    private static final int OK_H = 11;
    private static final int OK_X = VW - OK_W - 3;
    private static final int OK_Y = VH - OK_H - 3;

    /** The lowest y a report body may paint into (above the Okay plate). */
    protected static final int BODY_BOTTOM = OK_Y;

    protected static final Color TITLE_BG = new Color(0x00, 0x00, 0x00);
    protected static final Color GOLD = new Color(0xC8, 0xB0, 0x40);
    protected static final Color DIM = new Color(0x20, 0x10, 0x08, 0xA8);
    protected static final Color ROW_ALT = new Color(0x00, 0x00, 0x00, 0x40);
    protected static final Color FG = new Color(0xFF, 0xF0, 0xD0);
    protected static final Color HEAD_FG = new Color(0xE0, 0xC0, 0x60);
    private static final Color OK_BG = new Color(0x80, 0x18, 0x10);
    private static final Color OK_FG = new Color(0xFF, 0xE0, 0x40);

    protected final FreeColClient freeColClient;
    protected final ImageLibrary lib;

    /** Run when the screen is dismissed (Escape / the OK button). */
    private final Runnable onClose;

    /** Device-space scale + origin of the virtual canvas, set on each paint. */
    private int scale = 1;
    private int originX;
    private int originY;

    /**
     * Clickable colony rows (a virtual-space row band &rarr; its colony),
     * rebuilt every paint by the reports that list colonies.  A click in a band
     * jumps to that colony's screen, as the original advisor does; the cursor
     * turns to a hand over one.
     */
    private final List<ColonyHit> colonyHits = new ArrayList<>();


    ClassicReportPanel(FreeColClient freeColClient, ImageLibrary lib,
                       Runnable onClose) {
        this.freeColClient = freeColClient;
        this.lib = lib;
        this.onClose = onClose;
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * 3, VH * 3));
        setFocusable(true);
        installKeyBindings();
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleClick(e);
                }
            });
        addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateCursor(e);
                }
            });
    }


    // Contract for concrete reports

    /** The {@code REPORTn.PIK} backdrop key for this report. */
    protected abstract String backgroundKey();

    /** The message key of this report's localized title. */
    protected abstract String titleKey();

    /**
     * Paint the report body (header + rows) into the region between
     * {@link #ROW_Y0} and {@link #BODY_BOTTOM}, in virtual (320&times;200) space.
     */
    protected abstract void paintBody(Graphics2D g);

    /**
     * Handle a click in the report body, in virtual coordinates.  The default
     * is a no-op (only the Okay plate / Escape / colony-row jumps are handled by
     * the base); reports that want other click behaviour override this.
     */
    protected void onBodyClick(int vx, int vy) {}

    /**
     * Record a full-width clickable row for {@code colony} at text baseline
     * {@code y} — a report that lists colonies calls this per row during paint so
     * a click there jumps to the colony's screen.  Cleared each paint.
     */
    protected void addColonyRow(int y, Colony colony) {
        this.colonyHits.add(new ColonyHit(y - ROW_H + 3, ROW_H, colony));
    }

    /** The colony whose row band contains virtual {@code vy}, or null. */
    private Colony colonyAt(int vy) {
        for (ColonyHit h : this.colonyHits) {
            if (vy >= h.y && vy < h.y + h.h) return h.colony;
        }
        return null;
    }

    /** Close this report and open {@code colony}'s screen (jump-to). */
    private void openColony(Colony colony) {
        close();
        this.freeColClient.getGUI().showColonyPanel(colony, null);
    }


    // Input

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_closeReport");
        am.put("classic_closeReport", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });
    }

    private void handleClick(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        final Rectangle ok = new Rectangle(OK_X, OK_Y, OK_W, OK_H);
        if (ok.contains(vx, vy)) {
            close();
            return;
        }
        final Colony colony = colonyAt(vy);
        if (colony != null) {
            openColony(colony);
            return;
        }
        onBodyClick(vx, vy);
    }

    /** Hand cursor over a clickable colony row, default cursor elsewhere. */
    private void updateCursor(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vy = (e.getY() - this.originY) / this.scale;
        final boolean hand = colonyAt(vy) != null;
        setCursor(Cursor.getPredefinedCursor(
            hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    protected void close() {
        if (this.onClose != null) this.onClose.run();
    }


    // Painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
        this.originX = (getWidth() - VW * this.scale) / 2;
        this.originY = (getHeight() - VH * this.scale) / 2;
        g.translate(this.originX, this.originY);
        g.scale(this.scale, this.scale);
        g.clipRect(0, 0, VW, VH);

        paintBackground(g);
        paintTitle(g);
        this.colonyHits.clear();   // rebuilt by paintBody via addColonyRow
        paintBody(g);
        paintOk(g);
        g.dispose();
    }

    /** The sepia illustration, dimmed so the table reads over it. */
    private void paintBackground(Graphics2D g) {
        final BufferedImage bg = ImageLibrary.getUnscaledImage(backgroundKey());
        if (bg != null) {
            g.drawImage(bg, 0, 0, VW, VH, null);
        } else {
            g.setColor(new Color(0x8A, 0x4A, 0x24));
            g.fillRect(0, 0, VW, VH);
        }
        g.setColor(DIM);
        g.fillRect(0, TITLE_H, VW, VH - TITLE_H);
    }

    /** The gold-on-black header with the report's name. */
    private void paintTitle(Graphics2D g) {
        g.setColor(TITLE_BG);
        g.fillRect(0, 0, VW, TITLE_H);
        g.setColor(GOLD);
        g.setFont(font(7f, Font.BOLD));
        final String s = Messages.message(titleKey());
        g.drawString(s, (VW - g.getFontMetrics().stringWidth(s)) / 2, 7);
    }

    /** The red Okay plate at the bottom right (mirrors the original art). */
    private void paintOk(Graphics2D g) {
        g.setColor(OK_BG);
        g.fillRect(OK_X, OK_Y, OK_W, OK_H);
        g.setColor(OK_FG);
        g.drawRect(OK_X, OK_Y, OK_W - 1, OK_H - 1);
        g.setFont(font(7f, Font.BOLD));
        final String s = Messages.message("ok");
        g.drawString(s, OK_X + (OK_W - g.getFontMetrics().stringWidth(s)) / 2,
                     OK_Y + OK_H - 3);
    }


    // Shared drawing helpers for concrete reports

    /** Draw {@code img} scaled to fit a {@code size}&times;{@code size} box, centred. */
    protected void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int size) {
        final double s = Math.min((double) size / img.getWidth(),
                                  (double) size / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        g.drawImage(img, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
    }

    protected static String clip(String s, int n) {
        if (s == null) return "";
        return (s.length() <= n) ? s : s.substring(0, n - 1) + "…";
    }

    /**
     * A localized classic-report caption — {@code key} is the tail under the
     * {@code classic.report.} namespace (e.g. {@code "head.colony"}), added in the
     * isolated block of {@code FreeColMessages[_de].properties}.
     */
    protected static String cap(String key) {
        return Messages.message("classic.report." + key);
    }

    protected Font font(float size, int style) {
        return getFont().deriveFont(style, size);
    }

    /** A clickable colony row: a virtual-space y-band and its colony. */
    private static final class ColonyHit {
        final int y;
        final int h;
        final Colony colony;

        ColonyHit(int y, int h, Colony colony) {
            this.y = y;
            this.h = h;
            this.colony = colony;
        }
    }
}
