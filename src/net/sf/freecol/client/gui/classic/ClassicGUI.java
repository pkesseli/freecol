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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ChoiceItem;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.menu.InGameMenuBar;
import net.sf.freecol.client.gui.panel.FreeColImageBorder;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.resources.ImageCache;


/**
 * A primitive, original-Colonization-style view for FreeCol.
 *
 * This is an alternative implementation of the {@link GUI} view facade, selected
 * with the {@code --classic} command line option (see
 * {@link net.sf.freecol.client.FreeColClient}).  Because the base {@code GUI}
 * class is a fully functional no-op implementation (used in headless mode), this
 * subclass only needs to override the methods it actually implements; everything
 * else degrades gracefully to a no-op.  Screens are therefore added incrementally,
 * one {@code GUI} method at a time.
 *
 * Phase 0: scaffold only.  Brings up a placeholder window to prove the seam.
 * Map rendering, HUD, colony/europe panels and dialogs follow in later phases
 * (see CLASSIC_UI_PLAN.md).
 */
public class ClassicGUI extends GUI {

    private static final Logger logger = Logger.getLogger(ClassicGUI.class.getName());

    /** The main application window. */
    private JFrame frame;

    /** The colony screen's window, while one is open (see {@link #showColonyPanel}). */
    private JFrame colonyFrame;

    /** The Europe screen's window, while one is open (see {@link #showEuropePanel}). */
    private JFrame europeFrame;

    /** The Europe panel inside {@link #europeFrame}, kept so it can be repainted. */
    private ClassicEuropePanel europePanel;

    /** The report screen's window, while one is open (see {@link #showReportColonyPanel}). */
    private JFrame reportFrame;

    /**
     * The in-game map view, created lazily when a game starts (see
     * {@link #reconnectGUI}).  Null before then (title-screen placeholder).
     * Owns the classic view state (view mode, focus, selected tile, active
     * unit); this class delegates the corresponding {@code GUI} methods to it.
     */
    private ClassicMapViewer mapViewer;

    /**
     * The right-hand info / orders panel (Phase 2 HUD), created alongside the
     * map viewer in {@link #reconnectGUI}.  Repainted whenever the view state or
     * model changes so it tracks the active unit / selected tile / treasury.
     */
    private ClassicInfoPanel infoPanel;

    /** Persistent image cache, shared by the image libraries. */
    private final ImageCache imageCache;

    /**
     * The image library used to resolve images (e.g. the order-button icons
     * loaded by the {@code FreeColAction}s during construction).  Even at the
     * Phase 0 scaffold stage this must be non-null: {@code ActionManager} builds
     * every action regardless of the active view, and several actions call
     * {@code getGUI().getFixedImageLibrary()} from their constructors.  Phase 0
     * uses a single unscaled ({@code NORMAL_SCALE}) library; the map will likely
     * want a separately scaled one later.
     */
    private final ImageLibrary imageLibrary;


    /**
     * Create the classic GUI.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ClassicGUI(FreeColClient freeColClient) {
        super(freeColClient);
        this.imageCache = new ImageCache();
        this.imageLibrary = new ImageLibrary(this.imageCache);
        logger.info("ClassicGUI selected (experimental classic UI).");
    }


    // Lifecycle

    /**
     * {@inheritDoc}
     *
     * Phase 0: create and show a placeholder main window.
     */
    @Override
    public void startGUI(final Dimension desiredWindowSize) {
        logger.info("Starting ClassicGUI (Phase 0 scaffold).");
        // FreeCol passes Dimension(-1,-1) (WINDOWSIZE_FALLBACK) when no explicit
        // --windowsize is given, meaning "use the full screen".  A plain
        // null-check treats that sentinel as a real size and yields a 1x1 window,
        // so only honour a size with positive dimensions; otherwise maximize
        // (mirrors FreeColFrame's handling of invalid/absent bounds).
        final boolean explicitSize = desiredWindowSize != null
            && desiredWindowSize.width > 0 && desiredWindowSize.height > 0;
        final Dimension size = explicitSize
            ? desiredWindowSize : new Dimension(1024, 768);
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame("FreeCol — Classic UI (experimental)");
            this.frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            final JLabel placeholder = new JLabel(
                "<html><center>Classic UI scaffold (Phase 0)<br>"
                + "map &amp; panels coming next.</center></html>",
                SwingConstants.CENTER);
            // A2 end-to-end proof: paint the original-Colonization title screen
            // as the background, fetched through the normal ImageLibrary path via
            // the FreeCol key image.background.MainPanel.  That key is aliased to
            // image.classic_original.pik.OPENING.PIK in the classic_original pack
            // (tools/classic_assets/aliases.properties), so this shows original
            // art only when the pack is loaded (A3) — otherwise the base FreeCol
            // background shows and the scaffold still works.
            final BufferedImage background =
                ImageLibrary.getUnscaledImage("image.background.MainPanel");
            final JPanel content = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (background != null) {
                        g.drawImage(background, 0, 0, getWidth(), getHeight(), this);
                    }
                }
            };
            placeholder.setForeground(java.awt.Color.WHITE);
            content.add(placeholder, BorderLayout.CENTER);
            this.frame.setContentPane(content);
            this.frame.setSize(size);
            this.frame.setLocationByPlatform(true);
            if (!explicitSize) {
                this.frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            }
            this.frame.setVisible(true);
            logger.info("ClassicGUI window shown.");
        });
        logger.info("ClassicGUI started.");
    }

    // Pre-game lobby

    /**
     * {@inheritDoc}
     *
     * Phase 0 has no pre-game lobby panel.  The base {@code GUI} no-ops this,
     * which leaves a new single-player game stalled at login (see
     * {@code ConnectController.login}: with no map yet, control passes here
     * instead of {@code requestLaunch}).  Until a real lobby exists, auto-launch
     * single-player games so the classic UI can actually reach the in-game view;
     * this mirrors the "Start Game" button of {@code StartGamePanel}.  For
     * multiplayer there is nothing sensible to do headlessly, so we no-op.
     */
    @Override
    public FreeColPanel showStartGamePanel(Game game, Player player,
                                           boolean singlePlayerMode) {
        if (singlePlayerMode && player != null) {
            logger.info("ClassicGUI: auto-launching single-player game "
                + "(no lobby panel in Phase 0).");
            player.setReady(true);
            getFreeColClient().getPreGameController().requestLaunch();
        }
        return null;
    }

    // In-game map

    /**
     * {@inheritDoc}
     *
     * Called from {@code FreeColClient.restoreGUI} once a game is ready (the
     * initial active unit / focus tile are supplied here).  Phase 1: build the
     * {@link ClassicMapViewer} and swap it in for the title-screen placeholder,
     * then set the initial view state so the map renders centred on the action.
     */
    @Override
    public void reconnectGUI(Unit active, Tile tile) {
        SwingUtilities.invokeLater(() -> {
            if (this.frame == null) return;
            if (this.mapViewer == null) {
                this.mapViewer = new ClassicMapViewer(getFreeColClient(),
                                                      this, this.imageLibrary);
                this.infoPanel = new ClassicInfoPanel(getFreeColClient(),
                                                      this.mapViewer);
                // Phase 2 HUD: the map fills the centre, the classic info/orders
                // strip sits on the right, and the reused InGameMenuBar (wired to
                // the real FreeColActions) is the top menu bar.
                final JPanel content = new JPanel(new BorderLayout());
                content.add(this.mapViewer, BorderLayout.CENTER);
                content.add(this.infoPanel, BorderLayout.EAST);
                this.frame.setContentPane(content);
                try {
                    this.frame.setJMenuBar(new InGameMenuBar(getFreeColClient(),
                                                             null));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "ClassicGUI: menu bar unavailable",
                               e);
                }
                this.frame.revalidate();
            }
            if (active != null) {
                this.mapViewer.changeToMoveUnits(active);
            } else if (tile != null) {
                this.mapViewer.changeToTerrain(tile);
            }
            final Tile focusTile = (tile != null) ? tile
                : (active != null) ? active.getTile() : null;
            if (focusTile != null) {
                this.mapViewer.setFocus(focusTile);
            }
            this.mapViewer.requestFocusInWindow();
            this.mapViewer.repaint();
            repaintInfo();
            updateActions();
            logger.info("ClassicGUI: in-game map installed.");
        });
    }

    // View mode / focus — delegated to the map viewer.

    /** Repaint the HUD info panel if it exists (view/model state changed). */
    private void repaintInfo() {
        if (this.infoPanel != null) this.infoPanel.repaint();
        if (this.europePanel != null) this.europePanel.refresh();
    }

    /**
     * Refresh the enabled state of the reused {@code FreeColAction}s (and hence
     * the menu items wired to them).  {@code SwingGUI} does this through the
     * {@code Canvas} on every view change / panel open; the classic UI has no
     * {@code Canvas}, so without this call the menu items keep the (disabled)
     * state they were built with — e.g. the {@code Europe} item never enables and
     * the map/turn menus stay greyed.  Cheap and idempotent (it just re-evaluates
     * {@code shouldBeEnabled} on each action).
     */
    private void updateActions() {
        try {
            getFreeColClient().updateActions();
        } catch (Exception e) {
            logger.log(Level.WARNING, "ClassicGUI: updateActions failed.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void changeView(Tile tile) {
        if (this.mapViewer != null) this.mapViewer.changeToTerrain(tile);
        repaintInfo();
        updateActions();
    }

    /** {@inheritDoc} */
    @Override
    public void changeView(Unit unit, boolean force) {
        if (this.mapViewer != null) this.mapViewer.changeToMoveUnits(unit);
        repaintInfo();
        updateActions();
    }

    /** {@inheritDoc} */
    @Override
    public void changeView() {
        if (this.mapViewer != null) this.mapViewer.changeToEndTurn();
        repaintInfo();
        updateActions();
    }

    /** {@inheritDoc} */
    @Override
    public ViewMode getViewMode() {
        return (this.mapViewer != null) ? this.mapViewer.getViewMode()
            : super.getViewMode();
    }

    /** {@inheritDoc} */
    @Override
    public Unit getActiveUnit() {
        return (this.mapViewer != null) ? this.mapViewer.getActiveUnit() : null;
    }

    /** {@inheritDoc} */
    @Override
    public Tile getSelectedTile() {
        return (this.mapViewer != null) ? this.mapViewer.getSelectedTile() : null;
    }

    /** {@inheritDoc} */
    @Override
    public Tile getFocus() {
        return (this.mapViewer != null) ? this.mapViewer.getFocus() : null;
    }

    /** {@inheritDoc} */
    @Override
    public void setFocus(Tile tile) {
        if (this.mapViewer != null) this.mapViewer.setFocus(tile);
    }

    /** {@inheritDoc} */
    @Override
    public void refresh() {
        if (this.mapViewer != null) {
            // A refresh signals a model change (exploration, settlements, unit
            // moves), so rebuild the minimap raster before the next paint.
            this.mapViewer.invalidateMinimap();
            this.mapViewer.repaint();
        }
        repaintInfo();
    }

    /** {@inheritDoc} */
    @Override
    public void refreshTile(Tile tile) {
        if (this.mapViewer != null) {
            this.mapViewer.invalidateMinimap();
            this.mapViewer.repaint();
        }
        repaintInfo();
    }

    // Core screens

    /**
     * {@inheritDoc}
     *
     * Phase 2: show the classic colony screen — {@link ClassicColonyPanel}, a
     * 320&times;200 repaint of the original's signature screen — in a window of
     * its own (the classic UI has no {@code Canvas} to host panels in).  Reached
     * by clicking an owned colony on the map, and automatically by
     * {@code InGameController.buildColony} the moment a colony is founded.
     *
     * <p>Only one colony screen is open at a time; opening another replaces it.
     * The whole thing is guarded, so a failure degrades to a log line rather
     * than breaking the map.  Returns null (as the base {@code GUI} does): no
     * caller uses the returned panel, and the classic screen is not a
     * {@code FreeColPanel}.
     */
    @Override
    public FreeColPanel showColonyPanel(Colony colony, Unit unit) {
        if (colony == null) return null;
        SwingUtilities.invokeLater(() -> {
            try {
                closeColonyPanel();
                final JFrame f = new JFrame(colony.getName());
                this.colonyFrame = f;
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.setContentPane(new ClassicColonyPanel(getFreeColClient(),
                        this.imageLibrary, colony, this::closeColonyPanel));
                f.pack();
                f.setLocationRelativeTo(this.frame);
                f.setVisible(true);
                f.getContentPane().requestFocusInWindow();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: could not show colony "
                    + "screen for " + colony.getId(), e);
            }
        });
        return null;
    }

    /** Dismiss the colony screen if one is open. */
    private void closeColonyPanel() {
        final JFrame f = this.colonyFrame;
        this.colonyFrame = null;
        if (f != null) f.dispose();
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: show the classic Europe screen — {@link ClassicEuropePanel}, a
     * 320&times;200 repaint of the original's harbour — in a window of its own
     * (the classic UI has no {@code Canvas} to host panels in).  Reached by the
     * {@code Europe} menu action (accelerator {@code E}) and automatically when a
     * ship arrives in Europe (the controller calls this).
     *
     * <p>Only one Europe screen is open at a time; opening another replaces it.
     * Guarded so a failure degrades to a log line rather than breaking the map.
     */
    @Override
    public FreeColPanel showEuropePanel() {
        final Player player = getMyPlayer();
        if (player == null || player.getEurope() == null) return null;
        SwingUtilities.invokeLater(() -> {
            try {
                closeEuropePanel();
                final ClassicEuropePanel panel = new ClassicEuropePanel(
                    getFreeColClient(), this.imageLibrary, player.getEurope(),
                    this::closeEuropePanel);
                final JFrame f = new JFrame(Messages.message(player.getEurope()
                        .getNameKey()));
                this.europeFrame = f;
                this.europePanel = panel;
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.setContentPane(panel);
                f.pack();
                f.setLocationRelativeTo(this.frame);
                f.setVisible(true);
                panel.requestFocusInWindow();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: could not show Europe "
                    + "screen", e);
            }
        });
        return null;
    }

    /** Dismiss the Europe screen if one is open. */
    private void closeEuropePanel() {
        final JFrame f = this.europeFrame;
        this.europeFrame = null;
        this.europePanel = null;
        if (f != null) f.dispose();
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: show the classic <b>Colony Advisor report</b> — the original's
     * "KOLONIEBERATER-BERICHT" — as {@link ClassicReportColonyPanel} in a window
     * of its own.  Reached by the reused {@code Colony Advisor} report menu item
     * (accelerator {@code F3}).
     */
    @Override
    public FreeColPanel showReportColonyPanel() {
        return showReport("reportColonyAction.name",
            onClose -> new ClassicReportColonyPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Military Advisor report</b> — the standing-army
     * roster (accelerator {@code F7}).  See {@link ClassicReportMilitaryPanel}.
     */
    @Override
    public FreeColPanel showReportMilitaryPanel() {
        return showReport("reportMilitaryAction.name",
            onClose -> new ClassicReportMilitaryPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Trade Advisor report</b> — the goods ledger
     * (accelerator {@code F9}).  See {@link ClassicReportTradePanel}.
     */
    @Override
    public FreeColPanel showReportTradePanel() {
        return showReport("reportTradeAction.name",
            onClose -> new ClassicReportTradePanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Religious Advisor report</b> — the immigration /
     * crosses standing (accelerator {@code F1}).  See
     * {@link ClassicReportReligiousPanel}.
     */
    @Override
    public FreeColPanel showReportReligiousPanel() {
        return showReport("reportReligionAction.name",
            onClose -> new ClassicReportReligiousPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Naval Advisor report</b> — the fleet roster
     * (accelerator {@code F8}).  See {@link ClassicReportNavalPanel}.
     */
    @Override
    public FreeColPanel showReportNavalPanel() {
        return showReport("reportNavalAction.name",
            onClose -> new ClassicReportNavalPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Production Report</b> — per-colony production
     * breakdown (accelerator {@code shift F4}).  See
     * {@link ClassicReportProductionPanel}.
     */
    @Override
    public FreeColPanel showReportProductionPanel() {
        return showReport("reportProductionAction.name",
            onClose -> new ClassicReportProductionPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * Show a classic advisor report in a window of its own, one report at a
     * time.  Every {@code showReport*Panel} override routes through here: it
     * disposes any open report, builds the panel via {@code factory} (passing the
     * close callback), and frames it.  Guarded so a failure degrades to a log
     * line.  The report panels themselves share {@link ClassicReportPanel}.
     *
     * @param titleKey The message key of the window title.
     * @param factory Builds the report panel given its close callback.
     * @return {@code null} (the classic UI hosts its own windows).
     */
    private FreeColPanel showReport(String titleKey,
            java.util.function.Function<Runnable, JPanel> factory) {
        SwingUtilities.invokeLater(() -> {
            try {
                closeReportPanel();
                final JFrame f = new JFrame(Messages.message(titleKey));
                this.reportFrame = f;
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.setContentPane(factory.apply(this::closeReportPanel));
                f.pack();
                f.setLocationRelativeTo(this.frame);
                f.setVisible(true);
                f.getContentPane().requestFocusInWindow();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: could not show report "
                    + titleKey, e);
            }
        });
        return null;
    }

    /** Dismiss the report screen if one is open. */
    private void closeReportPanel() {
        final JFrame f = this.reportFrame;
        this.reportFrame = null;
        if (f != null) f.dispose();
    }

    /**
     * {@inheritDoc}
     *
     * The controllers call this after a recruit / train / purchase so any open
     * Europe view refreshes; repaint the classic Europe screen if it is showing.
     */
    @Override
    public void updateEuropeanSubpanels() {
        final ClassicEuropePanel panel = this.europePanel;
        if (panel != null) SwingUtilities.invokeLater(panel::refresh);
    }

    /**
     * {@inheritDoc}
     *
     * The base implementation asks for the name through {@code modalInputDialog},
     * which the classic {@code GUI} still no-ops (dialogs are Phase 3) — so it
     * would return null and silently abort every attempt to found a colony.
     * Until the classic name prompt exists, take the name FreeCol would have
     * suggested, made unique if the player somehow already used it.
     */
    @Override
    public String getNewColonyName(Player player, Tile tile) {
        final String suggested = player.getSettlementName(null);
        if (player.getSettlementByName(suggested) == null) return suggested;
        for (int i = 2; i < 100; i++) {
            final String name = suggested + " " + i;
            if (player.getSettlementByName(name) == null) return name;
        }
        return suggested;
    }

    /**
     * {@inheritDoc}
     *
     * The classic dialogs are Phase 3, but the base {@code GUI} <em>declines</em>
     * every confirmation, which silently aborts the controller flows that gate on
     * one — notably {@code buildColony}, which confirms the site warnings before
     * founding a colony.  So show a plain Swing confirmation for now: unstyled,
     * but it puts the real question (and the real choice) in front of the player.
     * Phase 3 replaces it with classic chrome.
     */
    @Override
    public boolean modalConfirmDialog(Tile tile, StringTemplate template,
                                      ImageIcon icon, String okKey,
                                      String cancelKey, boolean defaultOk) {
        final String text = Messages.message(template);
        final String ok = Messages.message(okKey);
        final String cancel = Messages.message(cancelKey);
        final Object[] options = { ok, cancel };
        return onEventThread(() -> JOptionPane.showOptionDialog(this.frame, text,
                colony(tile), JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, icon, options,
                (defaultOk ? ok : cancel)) == JOptionPane.YES_OPTION,
            defaultOk);
    }

    /**
     * {@inheritDoc}
     *
     * Wired for the same reason as {@link #modalConfirmDialog}: some controller
     * flows can only proceed through a choice.  Notably, disembarking a carrier
     * that holds more than one unit asks <em>which</em> unit(s) to land — so
     * without this a laden ship could never put colonists ashore, and the colony
     * screen (which needs a founded colony) would be unreachable.  Presented as a
     * plain Swing selection list for now; Phase 3 reskins it.
     */
    @Override
    protected <T> T modalChoiceDialog(Tile tile, StringTemplate template,
                                      ImageIcon icon, String cancelKey,
                                      List<ChoiceItem<T>> choices) {
        if (choices == null || choices.isEmpty()) return null;
        final String text = Messages.message(template);
        final ChoiceItem<T>[] options = choices.toArray(new ChoiceItem[0]);
        final ChoiceItem<T> chosen = onEventThread(() -> {
            final Object sel = JOptionPane.showInputDialog(this.frame, text,
                colony(tile), JOptionPane.QUESTION_MESSAGE, icon,
                options, options[0]);
            return (ChoiceItem<T>) sel;
        }, null);
        return (chosen == null) ? null : chosen.getObject();
    }

    /** Title for a tile-anchored dialog: the settlement there, else the game name. */
    private static String colony(Tile tile) {
        final Colony c = (tile == null) ? null : tile.getColony();
        return (c == null) ? "FreeCol" : c.getName();
    }

    /**
     * Run {@code task} on the event dispatch thread and return its result.  The
     * controllers call the dialog methods from whichever thread they happen to be
     * on (a key binding runs on the EDT; a server message does not), and Swing
     * dialogs must not be shown off it.  Any failure yields {@code fallback}.
     */
    private <T> T onEventThread(Callable<T> task, T fallback) {
        try {
            if (SwingUtilities.isEventDispatchThread()) return task.call();
            final FutureTask<T> ft = new FutureTask<>(task);
            SwingUtilities.invokeAndWait(ft);
            return ft.get();
        } catch (Exception e) {
            logger.log(Level.WARNING, "ClassicGUI: dialog failed.", e);
            return fallback;
        }
    }

    // Look and feel

    /**
     * {@inheritDoc}
     *
     * Called once during client startup ({@code FreeColClient} constructor).
     * The base {@code GUI} no-ops this, which leaves {@link FontLibrary}'s main
     * font null — fine while nothing painted text, but the Phase 2 HUD (the
     * reused {@link InGameMenuBar} draws a golden gold/tax/year status line via
     * {@code FontLibrary.getMainFont()}) then NPEs.  So initialise the main font
     * here, and the image-border scale factor so the menu bar's wood border
     * renders.
     *
     * <p>We deliberately do <em>not</em> install {@code FreeColLookAndFeel}: it
     * swaps in a {@code PanelUI} that paints the FreeCol parchment texture behind
     * every {@code JPanel}, which would override the classic map's black fog and
     * the dark info panel.  The reused {@link InGameMenuBar} paints its own
     * parchment background + wood border regardless of the active L&F, so the top
     * bar still reads classic; only the dropdown popups fall back to the default
     * Swing styling (acceptable for this stopgap).  All guarded so a failure just
     * leaves the default look rather than aborting startup.
     */
    @Override
    public void installLookAndFeel(String fontName) throws FreeColException {
        try {
            FreeColImageBorder.setScaleFactor(this.imageLibrary.getScaleFactor());
        } catch (Exception e) {
            logger.log(Level.WARNING, "ClassicGUI: image-border scale setup "
                + "failed.", e);
        }
        FontLibrary.createMainFont(fontName);
    }

    // Image libraries

    /**
     * {@inheritDoc}
     *
     * Overridden so that actions and (later) panels can resolve images; the base
     * {@code GUI} returns {@code null}, which causes a NullPointerException storm
     * as the actions try to load their order-button icons.
     */
    @Override
    public ImageLibrary getFixedImageLibrary() {
        return this.imageLibrary;
    }

    /**
     * {@inheritDoc}
     *
     * Phase 0 has no separate map scaling, so the scaled library is the same as
     * the fixed one.  Phase 1 (the map) may introduce a distinct scaled library.
     */
    @Override
    public ImageLibrary getScaledImageLibrary() {
        return this.imageLibrary;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void quitGUI() {
        final JFrame f = this.frame;
        if (f != null) {
            SwingUtilities.invokeLater(f::dispose);
        }
    }
}
