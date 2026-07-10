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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.panel.ColonyPanel;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
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

    /**
     * The in-game map view, created lazily when a game starts (see
     * {@link #reconnectGUI}).  Null before then (title-screen placeholder).
     * Owns the classic view state (view mode, focus, selected tile, active
     * unit); this class delegates the corresponding {@code GUI} methods to it.
     */
    private ClassicMapViewer mapViewer;

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
            }
            if (this.frame.getContentPane() != this.mapViewer) {
                this.frame.setContentPane(this.mapViewer);
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
            logger.info("ClassicGUI: in-game map installed.");
        });
    }

    // View mode / focus — delegated to the map viewer.

    /** {@inheritDoc} */
    @Override
    public void changeView(Tile tile) {
        if (this.mapViewer != null) this.mapViewer.changeToTerrain(tile);
    }

    /** {@inheritDoc} */
    @Override
    public void changeView(Unit unit, boolean force) {
        if (this.mapViewer != null) this.mapViewer.changeToMoveUnits(unit);
    }

    /** {@inheritDoc} */
    @Override
    public void changeView() {
        if (this.mapViewer != null) this.mapViewer.changeToEndTurn();
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
    }

    /** {@inheritDoc} */
    @Override
    public void refreshTile(Tile tile) {
        if (this.mapViewer != null) {
            this.mapViewer.invalidateMinimap();
            this.mapViewer.repaint();
        }
    }

    // Core screens (Phase 2 stopgap)

    /**
     * {@inheritDoc}
     *
     * Phase 1 stopgap: the classic colony screen is Phase 2 (it needs the
     * expert's original-game screenshots), so a click on an owned colony
     * delegates to FreeCol's own {@link ColonyPanel} — hosted in a standalone
     * window since the classic UI has no {@code Canvas}.  This is only ever
     * reached once the player founds a colony (never at the start-at-sea view);
     * it is guarded so any failure degrades to a log line rather than breaking
     * the map.  Phase 2 replaces this with a real classic colony screen.
     */
    @Override
    public FreeColPanel showColonyPanel(Colony colony, Unit unit) {
        if (colony == null) return null;
        try {
            final ColonyPanel panel = new ColonyPanel(getFreeColClient(), colony);
            if (unit != null) panel.setSelectedUnit(unit);
            final JFrame f = new JFrame(colony.getName());
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.add(panel);
            f.pack();
            f.setLocationRelativeTo(this.frame);
            f.setVisible(true);
            return panel;
        } catch (Exception e) {
            logger.log(Level.WARNING, "ClassicGUI: could not show colony panel "
                + "for " + colony.getId(), e);
            return null;
        }
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
