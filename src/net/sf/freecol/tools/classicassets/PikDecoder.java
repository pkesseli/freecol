/**
 *  Copyright (C) 2002-2024   The FreeCol Team
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

package net.sf.freecol.tools.classicassets;

import java.awt.image.BufferedImage;
import java.util.List;


/**
 * Decoder for {@code .PIK} full-screen pictures -- the game's screens and UI
 * chrome from Sid Meier's Colonization (1994).  Clean-room implementation
 * from the published format documentation.
 *
 * A {@code .PIK} is a MADSPACK with a header (part 0: height, width), an
 * 8-bit indexed image (part 1) and, usually, an embedded palette (part 2).
 * A few screens (e.g. COLONY.PIK) ship without a palette and are drawn under
 * the master gameplay palette, so VICEROY.PAL is supplied as a fallback.
 */
public final class PikDecoder {

    private PikDecoder() {}

    /**
     * Decode a {@code .PIK} screen.
     *
     * @param file The whole {@code .PIK} file contents.
     * @param viceroy The master palette, used for palette-less screens; may
     *     be {@code null} if every screen is known to embed its own.
     * @return The screen as an opaque RGB image.
     */
    public static BufferedImage decode(byte[] file, Palette viceroy) {
        List<byte[]> parts = MadsPack.read(file);
        byte[] header = parts.get(0);
        int height = Bytes.u16(header, 0);
        int width = Bytes.u16(header, 2);

        Palette pal;
        if (parts.size() >= 3) {
            pal = Palette.readCol(parts.get(2));
        } else if (viceroy != null) {
            pal = viceroy;
        } else {
            throw new IllegalStateException("palette-less PIK needs VICEROY.PAL");
        }

        byte[] indices = parts.get(1);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int o = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, pal.argb[indices[o++] & 0xFF]);
            }
        }
        return img;
    }
}
