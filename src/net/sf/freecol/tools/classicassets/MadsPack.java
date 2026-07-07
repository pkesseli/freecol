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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Reader for "MADSPACK 2.0" container files, the archive format wrapping
 * every graphic in Sid Meier's Colonization (1994).  Clean-room
 * implementation from the published format documentation.
 *
 * Layout: a 12-byte ASCII magic ({@code "MADSPACK 2.0"}), a marker word, a
 * 16-bit part count at offset 14, then a fixed 0xA0-byte header block of up
 * to 16 entries -- each {@code (uint16 flag, uint32 size, uint32 csize)} --
 * followed by the raw parts.  A part whose flag bit 0 is set is
 * {@link Fab}-compressed; otherwise it is stored verbatim.
 */
public final class MadsPack {

    private static final String MAGIC = "MADSPACK 2.0";
    private static final int COUNT_OFFSET = 14;
    private static final int HEADER_OFFSET = 16;
    private static final int HEADER_BLOCK_SIZE = 0xA0;   // 16 entries * 10 bytes

    private MadsPack() {}

    /**
     * Split a MADSPACK file into its (decompressed) parts.
     *
     * @param data The whole file contents.
     * @return The parts, in order, each already FAB-decompressed if needed.
     */
    public static List<byte[]> read(byte[] data) {
        String magic = new String(data, 0, MAGIC.length(), StandardCharsets.US_ASCII);
        if (!MAGIC.equals(magic)) {
            throw new IllegalArgumentException("not a MADSPACK 2.0 file (magic="
                + magic + ")");
        }

        int count = Bytes.u16(data, COUNT_OFFSET);
        int headerPos = HEADER_OFFSET;
        int dataPtr = HEADER_OFFSET + HEADER_BLOCK_SIZE;

        List<byte[]> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int flag = Bytes.u16(data, headerPos);
            int size = (int) Bytes.u32(data, headerPos + 2);
            headerPos += 10;

            if ((flag & 1) == 0) {
                parts.add(Arrays.copyOfRange(data, dataPtr, dataPtr + size));
                dataPtr += size;
            } else {
                // FAB-terminated by a HALT command; advance by bytes consumed.
                Fab.Result r = Fab.decode(data, dataPtr);
                parts.add(r.data);
                dataPtr += r.consumed;
            }
        }
        return parts;
    }
}
