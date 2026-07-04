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
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
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
        final Dimension size = (desiredWindowSize != null)
            ? desiredWindowSize : new Dimension(1024, 768);
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame("FreeCol — Classic UI (experimental)");
            this.frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            final JLabel placeholder = new JLabel(
                "<html><center>Classic UI scaffold (Phase 0)<br>"
                + "map &amp; panels coming next.</center></html>",
                SwingConstants.CENTER);
            this.frame.add(placeholder, BorderLayout.CENTER);
            this.frame.setSize(size);
            this.frame.setLocationByPlatform(true);
            this.frame.setVisible(true);
            logger.info("ClassicGUI window shown.");
        });
        logger.info("ClassicGUI started.");
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
