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


/**
 * A 256-entry colour table for the original Colonization art.  Palette
 * channels are stored on disk as 6-bit VGA DAC values (0-63) and scaled to
 * 8 bits here.  Each entry is kept as an opaque {@code 0xFFRRGGBB} int so
 * decoders can index straight into an ARGB image; transparency is handled by
 * the decoders via the reserved index, never by the palette.
 */
public final class Palette {

    /** Opaque {@code 0xFFRRGGBB} entries, always at least 256 long. */
    public final int[] argb;

    private Palette(int[] argb) {
        this.argb = argb;
    }

    /** Scale a 6-bit VGA DAC channel (0-63) to 8 bits (0-255). */
    private static int vga(int v) {
        return (v & 0xFF) * 255 / 63;
    }

    private static int[] newTable() {
        int[] a = new int[256];
        for (int i = 0; i < a.length; i++) a[i] = 0xFF000000;
        return a;
    }

    /**
     * Read a Colonization "col" palette: 256 entries of three 6-bit bytes
     * (R, G, B).  Also the layout of the leading 768 bytes of VICEROY.PAL.
     */
    public static Palette readCol(byte[] part) {
        int[] a = newTable();
        for (int i = 0; i < 256; i++) {
            int r = vga(part[i * 3] & 0xFF);
            int g = vga(part[i * 3 + 1] & 0xFF);
            int b = vga(part[i * 3 + 2] & 0xFF);
            a[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return new Palette(a);
    }

    /**
     * Read a "Rex" palette: a 16-bit colour count then that many 6-byte
     * entries (R, G, B, index, unused, flags).  Used by {@code .SS} files
     * whose header palette flag is clear.
     */
    public static Palette readRex(byte[] part) {
        int ncolors = Bytes.u16(part, 0);
        int[] a = newTable();
        int o = 2;
        for (int i = 0; i < ncolors && i < a.length; i++) {
            int r = vga(part[o] & 0xFF);
            int g = vga(part[o + 1] & 0xFF);
            int b = vga(part[o + 2] & 0xFF);
            o += 6;
            a[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return new Palette(a);
    }

    /**
     * Read the master gameplay palette VICEROY.PAL: 1024 bytes = a 768-byte
     * 256x3 six-bit-VGA table followed by a 256-byte trailer we ignore.  This
     * is the fallback for palette-less PIK screens (e.g. COLONY.PIK).
     */
    public static Palette readViceroy(byte[] raw) {
        if (raw.length < 256 * 3) {
            throw new IllegalArgumentException("VICEROY.PAL too small: "
                + raw.length + " bytes");
        }
        return readCol(raw);
    }
}
