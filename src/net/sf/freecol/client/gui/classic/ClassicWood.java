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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import net.sf.freecol.client.gui.ImageLibrary;


/**
 * The original's wood-panel chrome ({@code WOODPANL.PIK}), tiled seam-free over
 * an arbitrary rectangle.  Shared by the classic UI's wood surfaces — the
 * {@link ClassicInfoPanel} strip and the {@link ClassicDialog} popups.
 *
 * <p>{@code WOODPANL.PIK} carries a dark ornamental <b>border</b> on all four
 * edges.  Naively tiling the whole image repeats the dark <em>top/bottom</em>
 * border mid-surface, which reads as hard horizontal seams.  So tile only a
 * central <b>grain band</b> (the borderless middle of the source): its left/right
 * border columns are full-height, so they stay continuous and keep the framed
 * look, while the seams now fall in uninterrupted wood.  Each copy is
 * <b>flipped vertically</b> from the last, so adjacent copies meet at a
 * <em>matching</em> grain edge — a mirror fold — rather than a hard grain
 * discontinuity.
 */
final class ClassicWood {

    /** The pack key of the original's wood panel art. */
    private static final String WOOD_KEY
        = "image.classic_original.pik.WOODPANL.PIK";

    /** Crop bounds of the borderless grain band, as a fraction of source height. */
    private static final int BAND_TOP_TENTHS = 3;
    private static final int BAND_BOTTOM_TENTHS = 7;

    private ClassicWood() {}   // static helpers only

    /**
     * Tile the wood grain over the {@code w}&times;{@code h} rectangle at
     * ({@code x}, {@code y}).
     *
     * @return {@code false} when the original pack is absent (nothing painted, so
     *     the caller keeps whatever fallback ground it painted first).
     */
    static boolean paint(Graphics2D g, int x, int y, int w, int h) {
        final BufferedImage wood = ImageLibrary.getUnscaledImage(WOOD_KEY);
        if (wood == null || wood.getWidth() <= 0 || w <= 0 || h <= 0) return false;
        final int sw = wood.getWidth();
        final int sh = wood.getHeight();
        final int sy1 = sh * BAND_TOP_TENTHS / 10;
        final int sy2 = sh * BAND_BOTTOM_TENTHS / 10;
        final int th = Math.max(1, (sy2 - sy1) * w / sw);

        final Graphics2D gg = (Graphics2D) g.create();
        gg.clipRect(x, y, w, h);
        boolean flip = false;
        for (int yy = y; yy < y + h; yy += th, flip = !flip) {
            // Flip vertically by swapping the source top/bottom edges.
            final int a = flip ? sy2 : sy1;
            final int b = flip ? sy1 : sy2;
            gg.drawImage(wood, x, yy, x + w, yy + th, 0, a, sw, b, null);
        }
        gg.dispose();
        return true;
    }
}
