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
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.action.FreeColAction;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI info / orders panel: the vertical strip on the right of the
 * original 1994 <em>Colonization</em> map screen.
 *
 * <p>It shows, top to bottom: the turn (season + year), the player's gold and
 * tax rate, then information about whatever is in focus — the active unit (type,
 * moves, state, the terrain it stands on) or, in TERRAIN mode, the selected
 * tile's terrain — then a row of <b>clickable order buttons</b> for the active
 * unit, and a short reminder of the classic order keys.
 *
 * <p><b>Phase 2 HUD.</b> A plain {@code Graphics2D}-painted panel (dark ground,
 * light text) that reads live state directly from the model and from the
 * {@link ClassicMapViewer}'s view state; not yet the pixel-faithful wood-panel
 * chrome of the original (that needs the {@code WOODPANL.PIK} art — see
 * CLASSIC_UI_PLAN.md Phase 2). {@link ClassicGUI} repaints it whenever the view
 * state or model changes.
 *
 * <p><b>Order buttons.</b> The lower half hosts the original's unit-order buttons
 * — fortify, sentry, build colony, road/plow/clear, wait, skip and disband — by
 * reusing the real {@link FreeColAction}s (the "reuse {@code action/}" path): each
 * carries its own order-button art ({@link FreeColAction#BUTTON_IMAGE}), enables
 * itself via {@code shouldBeEnabled}, and its {@code actionPerformed} drives the
 * real {@code InGameController}. We paint the icon of every currently-enabled
 * order action and hit-test clicks against the painted rectangles, so the buttons
 * track the active unit exactly as the menu items do.
 */
final class ClassicInfoPanel extends JPanel {

    /** Fixed on-screen width (px) of the right-hand strip. */
    private static final int PANEL_WIDTH = 240;

    /** Left inset (px) for the text column. */
    private static final int PAD = 14;

    /** Order-button metrics. */
    private static final int BTN = 32;
    private static final int BTN_GAP = 6;

    private static final Color GROUND = new Color(0x20, 0x1a, 0x12); // dark wood
    private static final Color HEAD = new Color(0xF0, 0xD8, 0x8C);   // gold-ish
    private static final Color TEXT = new Color(0xE8, 0xE0, 0xD0);   // parchment
    private static final Color DIM = new Color(0x9a, 0x8f, 0x7c);    // muted
    private static final Color RULE = new Color(0x4a, 0x3c, 0x2a);   // separator
    private static final Color BTN_HOT = new Color(0x4a, 0x3c, 0x2a); // hover plate

    /**
     * The unit-order actions to offer, in the original's rough order.  Looked up
     * by id in the {@code ActionManager}; any that is absent, disabled or has no
     * order-button art is simply skipped, so this list is a superset — the
     * improvement actions ({@code road}/{@code plow}/{@code clearForest}) in
     * particular exist only for the ruleset's improvement types.
     */
    private static final String[] ORDER_ACTION_IDS = {
        "fortifyAction", "sentryAction", "buildColonyAction",
        "roadAction", "plowAction", "clearForestAction",
        "waitAction", "skipUnitAction", "disbandUnitAction",
    };

    private final FreeColClient freeColClient;

    /** Source of the live view state (active unit / selected tile / mode). */
    private final ClassicMapViewer mapViewer;

    /** Order-button hit targets, rebuilt each paint. */
    private final List<Rectangle> buttonBounds = new ArrayList<>();
    private final List<FreeColAction> buttonActions = new ArrayList<>();

    /** Index of the hovered order button, or -1. */
    private int hovered = -1;


    ClassicInfoPanel(FreeColClient freeColClient, ClassicMapViewer mapViewer) {
        this.freeColClient = freeColClient;
        this.mapViewer = mapViewer;
        setOpaque(true);
        setBackground(GROUND);
        setPreferredSize(new Dimension(PANEL_WIDTH, 100));
        setMinimumSize(new Dimension(PANEL_WIDTH, 100));
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    if (hovered != -1) { hovered = -1; repaint(); }
                }
            });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    onHover(e);
                }
            });
    }


    /** Render a StringTemplate, guarding against nulls / missing keys. */
    private static String msg(net.sf.freecol.common.model.StringTemplate t) {
        try {
            return (t == null) ? "" : Messages.message(t);
        } catch (RuntimeException e) {
            return "";
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        final Font base = getFont().deriveFont(Font.PLAIN, 14f);
        final Font head = getFont().deriveFont(Font.BOLD, 15f);
        int y = 28;

        final Game game = this.freeColClient.getGame();
        final Player player = this.freeColClient.getMyPlayer();

        // --- Turn / treasury header -------------------------------------
        if (game != null && game.getTurn() != null) {
            g.setColor(HEAD);
            g.setFont(head);
            y = line(g, msg(game.getTurn().getLabel()), y);
        }
        if (player != null) {
            g.setColor(TEXT);
            g.setFont(base);
            y = line(g, Messages.message("gold") + ": " + player.getGold(), y);
            y = line(g, Messages.message("tax") + ": " + player.getTax() + "%", y);
        }
        y = rule(g, y);

        // --- Focus: active unit, else selected tile ---------------------
        final Unit unit = this.mapViewer.getActiveUnit();
        final Tile selected = this.mapViewer.getSelectedTile();
        if (unit != null) {
            g.setColor(HEAD);
            g.setFont(head);
            y = line(g, msg(unit.getLabel()), y);
            g.setColor(TEXT);
            g.setFont(base);
            y = line(g, Messages.message("infoPanel.moves") + " " + safeMoves(unit), y);
            final Tile ut = unit.getTile();
            if (ut != null && ut.getType() != null) {
                g.setColor(DIM);
                y = line(g, msg(ut.getLabel()), y);
            }
        } else if (selected != null && selected.getType() != null) {
            g.setColor(HEAD);
            g.setFont(head);
            y = line(g, msg(selected.getLabel()), y);
        } else {
            g.setColor(DIM);
            g.setFont(base);
            y = line(g, Messages.message("endTurnAction.name"), y);
        }

        // --- Order buttons (for the active unit) ------------------------
        paintOrderButtons(g, y + 4);

        // --- Order-key reminder (bottom) --------------------------------
        g.setFont(base.deriveFont(12f));
        g.setColor(DIM);
        int yb = getHeight() - 74;
        yb = rule(g, yb);
        yb = line(g, "Enter: end turn", yb);
        yb = line(g, "Space: skip unit", yb);
        line(g, "W: wait", yb);
    }

    /**
     * Paint the enabled unit-order buttons as a wrapped grid of icons starting at
     * {@code y0}, recording each one's bounds + action for {@link #onClick}.
     */
    private void paintOrderButtons(Graphics2D g, int y0) {
        this.buttonBounds.clear();
        this.buttonActions.clear();
        if (this.freeColClient.getActionManager() == null) return;

        int x = PAD;
        int y = y0;
        int i = 0;
        for (String id : ORDER_ACTION_IDS) {
            final FreeColAction action
                = this.freeColClient.getActionManager().getFreeColAction(id);
            if (action == null || !action.isEnabled()) continue;
            final Icon icon = (Icon) action.getValue(FreeColAction.BUTTON_IMAGE);
            if (!(icon instanceof ImageIcon)) continue;

            if (x + BTN > getWidth() - PAD) {      // wrap to next row
                x = PAD;
                y += BTN + BTN_GAP;
            }
            final Rectangle r = new Rectangle(x, y, BTN, BTN);
            if (i == this.hovered) {
                g.setColor(BTN_HOT);
                g.fillRect(r.x - 2, r.y - 2, BTN + 4, BTN + 4);
            }
            g.drawImage(((ImageIcon) icon).getImage(), x, y, BTN, BTN, this);
            this.buttonBounds.add(r);
            this.buttonActions.add(action);
            x += BTN + BTN_GAP;
            i++;
        }
    }

    private void onClick(MouseEvent e) {
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            if (this.buttonBounds.get(i).contains(e.getPoint())) {
                final FreeColAction action = this.buttonActions.get(i);
                action.actionPerformed(new ActionEvent(this,
                    ActionEvent.ACTION_PERFORMED, action.getId()));
                repaint();
                return;
            }
        }
    }

    private void onHover(MouseEvent e) {
        int found = -1;
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            if (this.buttonBounds.get(i).contains(e.getPoint())) { found = i; break; }
        }
        if (found != this.hovered) {
            this.hovered = found;
            repaint();
        }
    }

    /** Draw one text line at {@code (PAD, y)} and return the next baseline. */
    private int line(Graphics2D g, String s, int y) {
        if (s != null && !s.isEmpty()) g.drawString(s, PAD, y);
        return y + g.getFontMetrics().getHeight() + 2;
    }

    /** Draw a horizontal separator and return the y below it. */
    private int rule(Graphics2D g, int y) {
        final Color old = g.getColor();
        g.setColor(RULE);
        g.drawLine(PAD, y - 4, getWidth() - PAD, y - 4);
        g.setColor(old);
        return y + 8;
    }

    /** Unit moves as a short string, guarded. */
    private static String safeMoves(Unit unit) {
        try {
            return unit.getMovesAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
