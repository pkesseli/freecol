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

import java.util.Arrays;


/**
 * Decompressor for the "FAB" LZ-style bit stream used by MicroProse's
 * MADSPACK archives (Sid Meier's Colonization, 1994).  Clean-room
 * implementation from the published format documentation.
 *
 * The stream starts with the ASCII magic {@code "FAB"} and a shift-value
 * byte, then a 16-bit control word followed by data.  Control bits are read
 * least-significant-bit first; commands are either a literal byte, or a copy
 * of a run of already-decoded bytes at a negative back-reference.
 */
public final class Fab {

    /** The outcome of a decode: the decompressed bytes and the number of
     *  compressed input bytes consumed (a MADSPACK part is FAB-terminated by
     *  a HALT command, not by a stored length). */
    public static final class Result {
        public final byte[] data;
        public final int consumed;

        Result(byte[] data, int consumed) {
            this.data = data;
            this.consumed = consumed;
        }
    }

    private Fab() {}

    /**
     * Decode a FAB stream beginning at {@code off} within {@code src}.
     *
     * @param src The buffer holding the FAB stream.
     * @param off The offset of the {@code "FAB"} magic.
     * @return The decompressed bytes plus the input byte count consumed.
     */
    public static Result decode(byte[] src, int off) {
        int p = off;
        if (src[p] != 'F' || src[p + 1] != 'A' || src[p + 2] != 'B') {
            throw new IllegalArgumentException("FAB magic not found at " + off);
        }
        p += 3;
        int shiftVal = src[p++] & 0xFF;
        if (shiftVal < 10 || shiftVal >= 14) {
            throw new IllegalArgumentException("invalid FAB shift_val: " + shiftVal);
        }
        final int copyAdrShift = 16 - shiftVal;
        final int copyAdrFill = 0xFF << (shiftVal - 8);
        final int copyLenMask = (1 << copyAdrShift) - 1;

        // 16-bit little-endian control word, consumed LSB-first.
        int bitBuffer = (src[p] & 0xFF) | ((src[p + 1] & 0xFF) << 8);
        p += 2;
        int bitsLeft = 16;

        byte[] out = new byte[256];
        int j = 0;

        while (true) {
            // get_bit(): decrement first; on underflow refill a fresh 16-bit
            // word, preserving the last leftover bit at position 0.
            bitsLeft--;
            if (bitsLeft == 0) {
                int nx = (src[p] & 0xFF) | ((src[p + 1] & 0xFF) << 8);
                p += 2;
                bitBuffer = (nx << 1) | (bitBuffer & 1);
                bitsLeft = 16;
            }
            int bit = bitBuffer & 1;
            bitBuffer >>>= 1;

            if (bit == 1) {
                // literal byte
                if (j >= out.length) out = Arrays.copyOf(out, out.length * 2);
                out[j++] = src[p++];
                continue;
            }

            // second control bit selects the copy encoding
            bitsLeft--;
            if (bitsLeft == 0) {
                int nx = (src[p] & 0xFF) | ((src[p + 1] & 0xFF) << 8);
                p += 2;
                bitBuffer = (nx << 1) | (bitBuffer & 1);
                bitsLeft = 16;
            }
            int bit2 = bitBuffer & 1;
            bitBuffer >>>= 1;

            int copyLen;
            int copyAdr;
            if (bit2 == 0) {
                // 00 b1 b2 A -- short copy, len in [2,5], adr in [-255,-1]
                int b1;
                int b2;
                bitsLeft--;
                if (bitsLeft == 0) {
                    int nx = (src[p] & 0xFF) | ((src[p + 1] & 0xFF) << 8);
                    p += 2;
                    bitBuffer = (nx << 1) | (bitBuffer & 1);
                    bitsLeft = 16;
                }
                b1 = bitBuffer & 1;
                bitBuffer >>>= 1;
                bitsLeft--;
                if (bitsLeft == 0) {
                    int nx = (src[p] & 0xFF) | ((src[p + 1] & 0xFF) << 8);
                    p += 2;
                    bitBuffer = (nx << 1) | (bitBuffer & 1);
                    bitsLeft = 16;
                }
                b2 = bitBuffer & 1;
                bitBuffer >>>= 1;

                copyLen = ((b1 << 1) | b2) + 2;
                int rawCopyAdr = src[p++] & 0xFF;
                copyAdr = rawCopyAdr | 0xFFFFFF00;  // sign-extend: 0xFF -> -1, 0x00 -> -256
            } else {
                // 01 A B [C] -- long copy / HALT / NOP
                int a = src[p++] & 0xFF;
                int b = src[p++] & 0xFF;
                copyAdr = (((b >>> copyAdrShift) | copyAdrFill) << 8) | a;
                copyAdr |= 0xFFFF0000;
                copyLen = b & copyLenMask;
                if (copyLen == 0) {
                    copyLen = src[p++] & 0xFF;
                    if (copyLen == 0) {
                        break;              // HALT
                    } else if (copyLen == 1) {
                        continue;           // NOP
                    } else {
                        copyLen += 1;
                    }
                } else {
                    copyLen += 2;
                }
            }

            while (copyLen-- > 0) {
                if (j >= out.length) out = Arrays.copyOf(out, out.length * 2);
                out[j] = out[j + copyAdr];  // copyAdr is negative
                j++;
            }
        }

        return new Result(Arrays.copyOf(out, j), p - off);
    }
}
