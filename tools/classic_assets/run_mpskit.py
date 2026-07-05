#!/usr/bin/env python3
"""Driver that runs mpskit with two committed decode-fidelity fixes applied.

Part of the classic-UI "bring-your-own-install" asset pipeline (see
CLASSIC_UI_PLAN.md, item A1.1).  mpskit is fetched fresh (pinned commit) into the
git-ignored ``build/classic-assets/`` on every ``ant classic-assets`` run, so we
cannot edit it in place.  Instead this wrapper imports mpskit and monkeypatches
the two functions that need correcting, then delegates to mpskit's own ``main``.
That keeps the fixes in committed source while leaving the vendored decoder pristine.

Invoked exactly like ``mpskit/main.py`` -- ``run_mpskit.py <fmt> <cmd> [file ...]``
-- because it hands ``sys.argv`` straight to ``main.main()``.  Two env vars locate
the vendored code and the master palette:

  MPSKIT_DIR   directory of the fetched mpskit checkout (has main.py, palette.py, ...)
  VICEROY_PAL  path to the original game's VICEROY.PAL (only needed for `pik unpack`)

--- Fix 1: palette corruption (affects every SS frame and PIK screen) ----------
mpskit's ``palette.attach_palette`` builds the palette planar (all R, then all G,
then all B) and hands it to ``ImagePalette(mode='RGB', palette=..., size=...)``.
On Pillow 9.x that produces a scrambled palette: the *pixel indices* decode
correctly but the stored RGB values are wrong, so terrain/textures/screens come
out as colour noise and transparency maps to the wrong key colour.  The canonical
fix is ``Image.putpalette(interleaved_rgb_bytes)``, which we substitute here.

--- Fix 2: palette-less PIK screens (COLONY.PIK) -------------------------------
Most PIK screens embed their own palette as MADSPACK part 2, but a few (COLONY.PIK)
ship only header+image (2 parts) and are meant to be shown under the in-game global
palette.  Stock ``pik.read_pik`` asserts >= 3 parts and skips them.  We fall back to
VICEROY.PAL (the master 256-colour gameplay palette) for those.

VICEROY.PAL layout (verified against this install): 1024 bytes = a 768-byte
256x3 six-bit-VGA RGB palette followed by a 256-byte trailer we ignore.
"""

import os
import sys


def _fixed_attach_palette(img, pal):
    """Attach an already-8-bit ``[(r,g,b), ...]`` palette the canonical way.

    Replaces mpskit's ImagePalette-based version (see Fix 1 above).  ``pal`` comes
    from mpskit's own ``read_palette_col`` / ``read_palette_rex`` already scaled to
    0-255, so we just flatten to interleaved RGB and call ``putpalette``.
    """
    flat = bytearray()
    for c in pal:
        flat += bytes((c[0] & 0xFF, c[1] & 0xFF, c[2] & 0xFF))
    img.putpalette(bytes(flat))


def _read_viceroy_palette(path):
    """Return VICEROY.PAL as a 256-entry ``[(r,g,b), ...]`` list (8-bit)."""
    raw = open(path, "rb").read()
    if len(raw) < 256 * 3:
        raise SystemExit("VICEROY_PAL too small ({} bytes): {}".format(len(raw), path))
    return [
        (int(raw[i * 3] * 255 / 63),
         int(raw[i * 3 + 1] * 255 / 63),
         int(raw[i * 3 + 2] * 255 / 63))
        for i in range(256)
    ]


def _install_fixes(viceroy_pal):
    """Import mpskit modules and monkeypatch the two fidelity fixes into them."""
    import palette
    import ss
    import pik
    import main as mpskit_main

    # Fix 1: patch every module that imported attach_palette by value.
    # (ss/pik did ``from palette import attach_palette`` so they hold their own
    # reference; palette.export_palette calls it within its own module.)  It is
    # looked up as a module global by read_ss/read_pik_image at call time, so
    # patching the modules is enough -- no need to touch main.
    palette.attach_palette = _fixed_attach_palette
    ss.attach_palette = _fixed_attach_palette
    pik.attach_palette = _fixed_attach_palette

    # Fix 2: tolerant read_pik that supplies VICEROY.PAL for palette-less screens.
    def _patched_read_pik(pik_name):
        parts = pik.read_madspack(pik_name)
        pik.save_madspack(pik_name, parts)
        h = pik.read_pik_header(parts[0])
        if len(parts) >= 3:
            pal = pik.read_palette_col(parts[2])
        else:
            if not viceroy_pal:
                raise SystemExit(
                    "palette-less PIK {} needs VICEROY_PAL env var".format(pik_name))
            pal = _read_viceroy_palette(viceroy_pal)
        img = pik.read_pik_image(parts[1], h, pal)
        pik.save_image(pik_name, img)

    # read_pik is replaced wholesale, so update BOTH the pik module and main's
    # own reference: main.py did ``from pik import read_pik`` and get_handler
    # resolves the bare name ``read_pik`` from main's globals at call time.
    pik.read_pik = _patched_read_pik
    mpskit_main.read_pik = _patched_read_pik


def main():
    mpskit_dir = os.environ.get("MPSKIT_DIR")
    if not mpskit_dir:
        raise SystemExit("set MPSKIT_DIR to the fetched mpskit checkout directory")
    # mpskit uses flat imports (``from common import *``); its own dir must be on
    # sys.path.  main.py normally gets this for free by being run directly.
    sys.path.insert(0, mpskit_dir)

    _install_fixes(os.environ.get("VICEROY_PAL"))

    import main as mpskit_main
    mpskit_main.main()  # reads sys.argv[1:] exactly as `mpskit <fmt> <cmd> [file ...]`


if __name__ == "__main__":
    main()
