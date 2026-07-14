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
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HighSeas;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The classic-UI <b>Europe</b> screen — the docks of the player's home port in
 * the original 1994 <em>Colonization</em> (design ref: the expert's
 * {@code opening_009}&ndash;{@code 013}).
 *
 * <p>As with {@link ClassicColonyPanel}, everything is painted into a virtual
 * <b>320&times;200</b> canvas (the original's VGA resolution) and then up-scaled
 * by the largest integer factor that fits the window, nearest-neighbour, so the
 * layout constants read straight off the screenshots and the classic pixels stay
 * crisp.  The original {@code EUROPE.PIK} harbour picture (sky, sea, the wooden
 * piers and the row of European town houses) is blitted as the backdrop; the
 * live figures are drawn over it:
 *
 * <ul>
 *   <li><b>Title bar</b> — the port name, turn, tax and treasury, gold on black.</li>
 *   <li><b>Recruit / Purchase / Train buttons</b> (top right) — the three golden
 *   buttons of the original (REKRUT / KAUFEN / AUSBILDEN).  Each opens a choice
 *   dialog and drives the real {@link InGameController} recruit/train path; the
 *   dialogs are plain Swing for now (Phase 3 reskins them to the wood-framed
 *   look with the colonist portrait, exactly like the colony-founding seams).</li>
 *   <li><b>Ships in port</b> — the naval units waiting in Europe, floating on the
 *   water by the piers.</li>
 *   <li><b>Units on the docks</b> — the land units standing on the quay, ready to
 *   board a ship.</li>
 *   <li><b>Sailing units</b> — the units on the high seas, split into those bound
 *   for the New World and those returning to Europe.</li>
 *   <li><b>Market row</b> — every storable good with its current sale price along
 *   the bottom.</li>
 * </ul>
 *
 * <p>This first Europe slice <em>renders</em> the port and wires the
 * recruit/purchase/train actions.  Interaction by drag (boarding a ship, loading
 * cargo, setting sail) is a later slice — the equivalent follow-up to the colony
 * screen's drag/queue/cargo work; for now Escape or a click on the red exit
 * button closes the screen.
 */
final class ClassicEuropePanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    private static final int VW = 320;
    private static final int VH = 200;

    /** Title bar: full width, gold on black. */
    private static final int TITLE_H = 9;

    /** The three action buttons, stacked at the top right. */
    private static final int BTN_W = 40;
    private static final int BTN_H = 11;
    private static final int BTN_X = VW - BTN_W - 1;
    private static final int BTN_Y = TITLE_H + 3;

    /** The market row along the very bottom. */
    private static final int MARKET_Y = 182;
    private static final int MARKET_H = VH - MARKET_Y;

    /** The exit "E" hot zone at the bottom right (part of the harbour art). */
    private static final int EXIT_X = VW - 14;

    private static final Color TITLE_BG = new Color(0x00, 0x00, 0x00);
    private static final Color GOLD = new Color(0xC8, 0xB0, 0x40);
    private static final Color BTN_BG = new Color(0x30, 0x28, 0x10);
    private static final Color BTN_HOT = new Color(0x5A, 0x4C, 0x1C);
    private static final Color TAG_BG = new Color(0x00, 0x00, 0x00, 0xC0);
    private static final Color TAG_FG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color SKY = new Color(0x88, 0xA8, 0xD0);
    private static final Color SEA = new Color(0x28, 0x50, 0x98);

    private final FreeColClient freeColClient;
    private final ImageLibrary lib;
    private final Europe europe;

    /** Run when the screen is dismissed (Escape / the exit button). */
    private final Runnable onClose;

    /** The three action buttons, rebuilt each paint (virtual-space bounds + label). */
    private final List<Rectangle> buttonBounds = new ArrayList<>();
    private final List<String> buttonLabels = new ArrayList<>();
    private final List<Runnable> buttonActions = new ArrayList<>();

    /** Index into {@link #buttonBounds} of the hovered button, or -1. */
    private int hovered = -1;

    /** Device-space scale + origin of the virtual canvas, set on each paint. */
    private int scale = 1;
    private int originX;
    private int originY;


    ClassicEuropePanel(FreeColClient freeColClient, ImageLibrary lib,
                       Europe europe, Runnable onClose) {
        this.freeColClient = freeColClient;
        this.lib = lib;
        this.europe = europe;
        this.onClose = onClose;
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * 3, VH * 3));
        setFocusable(true);
        installKeyBindings();
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e);
                }
            });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    onHover(e);
                }
            });
    }


    // Assets

    /** The original harbour backdrop, or null when the asset pack is absent. */
    private BufferedImage backdrop() {
        return ImageLibrary.getUnscaledImage("image.classic_original.pik.EUROPE.PIK");
    }


    // Input

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_closeEurope");
        am.put("classic_closeEurope", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });
    }

    private void onClick(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        // The exit button ("E") at the bottom right, as in the original art.
        if (vx >= EXIT_X && vy >= MARKET_Y) {
            close();
            return;
        }
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            if (this.buttonBounds.get(i).contains(vx, vy)) {
                this.buttonActions.get(i).run();
                return;
            }
        }
    }

    /** Track which button the pointer is over, and repaint if it changed. */
    private void onHover(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        int found = -1;
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            if (this.buttonBounds.get(i).contains(vx, vy)) {
                found = i;
                break;
            }
        }
        if (found != this.hovered) {
            this.hovered = found;
            repaint();
        }
    }

    private void close() {
        if (this.onClose != null) this.onClose.run();
    }

    private InGameController igc() {
        return this.freeColClient.getInGameController();
    }


    // Recruit / purchase / train — the real controller paths.

    /**
     * The recruit dialog: offer the three migrants waiting on the docks in
     * Europe, each for the current passage price, and recruit the chosen one via
     * {@link InGameController#recruitUnitInEurope(int)} (the same call the
     * standard {@code RecruitPanel} makes).
     */
    private void recruit() {
        final Player player = this.freeColClient.getMyPlayer();
        final List<AbstractUnit> recruitables = this.europe.getExpandedRecruitables(false);
        if (recruitables.isEmpty()) return;
        final int price = player.getEuropeanRecruitPrice();
        final String[] options = new String[recruitables.size()];
        for (int i = 0; i < options.length; i++) {
            options[i] = Messages.message(recruitables.get(i).getSingleLabel())
                + "  (" + price + ")";
        }
        final String prompt = Messages.message(net.sf.freecol.common.model.StringTemplate
            .template("recruitPanel.clickOn")
            .addAmount("%money%", price)
            .addAmount("%number%", 0));
        final int idx = choose(Messages.message("recruit"), prompt, options);
        if (idx >= 0 && Europe.MigrationType.validMigrantIndex(idx)) {
            igc().recruitUnitInEurope(idx);
            refresh();
        }
    }

    /**
     * The train dialog: the unit types Europe can school, cheapest first, paid
     * for through {@link InGameController#trainUnitInEurope(UnitType)}.
     */
    private void train() {
        final Specification spec = this.freeColClient.getGame().getSpecification();
        offerUnits(Messages.message("train"),
                   Messages.message("trainPanel.clickOn"),
                   spec.getUnitTypesTrainedInEurope(this.freeColClient.getMyPlayer()));
    }

    /**
     * The purchase dialog: the artillery and ships Europe sells.  These are paid
     * for through the same {@code trainUnitInEurope} controller call as trained
     * units (as the standard {@code PurchasePanel} does).
     */
    private void purchase() {
        final Specification spec = this.freeColClient.getGame().getSpecification();
        offerUnits(Messages.message("purchase"),
                   Messages.message("purchasePanel.clickOn"),
                   spec.getUnitTypesPurchasedInEurope(this.freeColClient.getMyPlayer()));
    }

    /** Shared body of {@link #train} / {@link #purchase}: a priced unit-type list. */
    private void offerUnits(String title, String prompt, List<UnitType> types) {
        if (types == null || types.isEmpty()) return;
        final List<UnitType> sorted = new ArrayList<>(types);
        sorted.sort(Comparator.comparingInt(this.europe::getUnitPrice));
        final String[] options = new String[sorted.size()];
        for (int i = 0; i < options.length; i++) {
            final UnitType ut = sorted.get(i);
            options[i] = Messages.getName(ut) + "  (" + this.europe.getUnitPrice(ut) + ")";
        }
        final int idx = choose(title, prompt, options);
        if (idx >= 0) {
            igc().trainUnitInEurope(sorted.get(idx));
            refresh();
        }
    }

    /**
     * Show a plain Swing selection list and return the chosen index, or -1.  The
     * classic wood-framed dialog is Phase 3 (shared with the colony-founding
     * seams); this is the stopgap that puts the real choice in front of the
     * player meanwhile.
     */
    private int choose(String title, String prompt, String[] options) {
        final Object sel = JOptionPane.showInputDialog(this, prompt, title,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (sel == null) return -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(sel)) return i;
        }
        return -1;
    }

    /** Repaint after a model change (a recruit, a purchase, an arrival). */
    void refresh() {
        repaint();
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
        paintSailing(g);
        paintPort(g);
        paintDocks(g);
        paintMarket(g);
        paintButtons(g);
        g.dispose();
    }

    /** The harbour backdrop ({@code EUROPE.PIK}), or a plain sea/sky fallback. */
    private void paintBackground(Graphics2D g) {
        final BufferedImage bg = backdrop();
        if (bg != null) {
            g.drawImage(bg, 0, 0, VW, VH, null);
        } else {
            g.setColor(SKY);
            g.fillRect(0, 0, VW, 120);
            g.setColor(SEA);
            g.fillRect(0, 120, VW, VH - 120);
        }
    }

    /** The gold-on-black header: port name, turn, tax, treasury. */
    private void paintTitle(Graphics2D g) {
        g.setColor(TITLE_BG);
        g.fillRect(0, 0, VW, TITLE_H);
        final Game game = this.freeColClient.getGame();
        final Player player = this.freeColClient.getMyPlayer();
        final StringBuilder sb = new StringBuilder(msg(this.europe.getNameKey()));
        if (game != null && game.getTurn() != null) {
            sb.append(", ").append(msg(game.getTurn().getLabel()));
        }
        if (player != null) {
            sb.append(", ").append(Messages.message("tax")).append(": ")
                .append(player.getTax()).append("%   ")
                .append(Messages.message("gold")).append(": ")
                .append(player.getGold());
        }
        g.setColor(GOLD);
        g.setFont(font(7f, Font.BOLD));
        g.drawString(sb.toString(), 3, 7);
    }

    /**
     * The units on the high seas, split by heading: those sailing out to the New
     * World and those returning to Europe.  Each row is a caption plus the unit
     * sprites, at the left of the sky as in the original.
     */
    private void paintSailing(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        final HighSeas highSeas = (player == null) ? null : player.getHighSeas();
        final List<Unit> toAmerica = new ArrayList<>();
        final List<Unit> toEurope = new ArrayList<>();
        if (highSeas != null) {
            for (Unit u : highSeas.getUnitList()) {
                final Location dest = u.getDestination();
                if (dest instanceof Europe) {
                    toEurope.add(u);
                } else {
                    toAmerica.add(u);
                }
            }
        }
        paintSailingRow(g, Messages.message("sailingToAmerica"), toAmerica, TITLE_H + 4);
        paintSailingRow(g, Messages.message("sailingToEurope"), toEurope, TITLE_H + 22);
    }

    private void paintSailingRow(Graphics2D g, String caption, List<Unit> units, int y) {
        if (units.isEmpty()) return;
        plate(g, caption, 3, y);
        int x = 3;
        for (Unit u : units) {
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y + 2, 18);
            x += 18;
            if (x > 120) break;
        }
    }

    /** The ships waiting in port, floating on the water by the piers. */
    private void paintPort(Graphics2D g) {
        int x = 6;
        final int y = 122;
        for (Unit u : this.europe.getUnitList()) {
            if (!u.isNaval()) continue;
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y, 22);
            x += 24;
            if (x > 150) break;
        }
    }

    /** The land units standing on the quay, ready to embark. */
    private void paintDocks(Graphics2D g) {
        int x = 112;
        final int y = 150;
        int row = 0;
        for (Unit u : this.europe.getUnitList()) {
            if (u.isNaval()) continue;
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y + row * 16, 16);
            x += 13;
            if (x > VW - 16) {           // wrap onto a second rank
                x = 112;
                row++;
                if (row > 1) break;
            }
        }
    }

    /** The market row: every storable good with its current sale price. */
    private void paintMarket(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        final Market market = (player == null) ? null : player.getMarket();
        final List<GoodsType> goods
            = this.europe.getSpecification().getStorableGoodsTypeList();
        if (goods.isEmpty()) return;

        g.setColor(TAG_BG);
        g.fillRect(0, MARKET_Y, VW, MARKET_H);

        final int cw = (VW - 14) / goods.size();      // leave room for the exit "E"
        g.setFont(font(6f, Font.PLAIN));
        for (int i = 0; i < goods.size(); i++) {
            final GoodsType gt = goods.get(i);
            final int x = i * cw;
            final BufferedImage img = this.lib.getScaledGoodsTypeImage(gt);
            if (img != null) drawFitted(g, img, x + (cw - 12) / 2, MARKET_Y + 1, 12);
            if (market != null) {
                final String s = String.valueOf(market.getPaidForSale(gt));
                g.setColor(TAG_FG);
                g.drawString(s, x + (cw - g.getFontMetrics().stringWidth(s)) / 2,
                             MARKET_Y + MARKET_H - 2);
            }
        }
    }

    /**
     * The three golden action buttons at the top right — recruit, purchase and
     * train.  Their bounds and actions are recorded here so {@link #onClick}
     * and {@link #onHover} can drive them.
     */
    private void paintButtons(Graphics2D g) {
        this.buttonBounds.clear();
        this.buttonLabels.clear();
        this.buttonActions.clear();
        addButton(Messages.message("recruit"), this::recruit);
        addButton(Messages.message("purchase"), this::purchase);
        addButton(Messages.message("train"), this::train);

        g.setFont(font(7f, Font.BOLD));
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            final Rectangle r = this.buttonBounds.get(i);
            g.setColor((i == this.hovered) ? BTN_HOT : BTN_BG);
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(GOLD);
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            final String s = this.buttonLabels.get(i);
            final int tw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, r.x + (r.width - tw) / 2, r.y + r.height - 3);
        }
    }

    private void addButton(String label, Runnable action) {
        final int i = this.buttonBounds.size();
        this.buttonBounds.add(new Rectangle(BTN_X, BTN_Y + i * (BTN_H + 2), BTN_W, BTN_H));
        this.buttonLabels.add(label);
        this.buttonActions.add(action);
    }


    // Shared drawing helpers

    /** A small black-plated caption, as used above the sailing rows. */
    private void plate(Graphics2D g, String s, int x, int y) {
        if (s == null || s.isEmpty()) return;
        g.setFont(font(6f, Font.BOLD));
        final int tw = g.getFontMetrics().stringWidth(s);
        g.setColor(TAG_BG);
        g.fillRect(x - 1, y - 6, tw + 2, 7);
        g.setColor(TAG_FG);
        g.drawString(s, x, y);
    }

    /**
     * Draw {@code img} scaled to fit a {@code size}-px box at {@code (x, y)},
     * preserving aspect — the original sprites are small so this up-scales, the
     * pack-absent FreeCol art is large and shrinks (see {@code ClassicColonyPanel}).
     */
    private void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int size) {
        final double s = Math.min((double) size / img.getWidth(),
                                  (double) size / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        g.drawImage(img, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
    }

    /** Render a StringTemplate/key, guarding against nulls / missing keys. */
    private static String msg(String key) {
        try {
            return (key == null) ? "" : Messages.message(key);
        } catch (RuntimeException e) {
            return (key == null) ? "" : key;
        }
    }

    private static String msg(net.sf.freecol.common.model.StringTemplate t) {
        try {
            return (t == null) ? "" : Messages.message(t);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** A font in <em>virtual</em> pixels — the paint transform scales it up. */
    private Font font(float size, int style) {
        return getFont().deriveFont(style, size);
    }
}
