# Asset track — bring-your-own original install (DECIDED)

**Decision:** ship no original assets; load the original game's pixels from the user's own legal
install (Steam/GOG "Classic"). If none is configured, fall back to FreeCol's free art so the build
always runs — FreeCol art is the *fallback skin*, original art the *fidelity skin*.

**Pipeline:** an offline **converter** (pure Java, `net.sf.freecol.tools.classicassets`, driven by
`ant classic-assets`) decodes the install's art into a **git-ignored** `data/mods/classic_original/`
pack (PNGs + `resources.properties` + `mod.xml`), which the classic UI loads as the highest-priority
mod. Key→frame aliases are curated in committed `tools/classic_assets/aliases.properties` (appended
into the pack on regen, so regenerating never clobbers them). No Python/external tool.

**Formats (verified — GOG install `…\Colonization\MPS\COLONIZE\`):** all graphics are `MADSPACK 2.0`
containers — **`.PIK`** (35 full-screen 320×200 screens/chrome) and **`.SS`** (sprite sets, many
frames each). Master palette `VICEROY.PAL` (768-byte 6-bit VGA + trailer) decodes palette-less PIKs.
The decoder path (`MadsPack` container → `Fab` LZ bit-stream decompressor → `Palette`/`SsDecoder`/
`PikDecoder` → `ClassicAssetConverter`) is documented in those classes' comments; the tricky bits are
FAB decompression and the per-set transparency encodings (`0xFD` alpha vs the coast frames' opaque
colour-key black). Written clean-room from the format spec → GPLv2+, upstream-compatible.

**Screen manifest (the 35 `.PIK` files) — scopes remaining UI screens:** `COLONY`, `EUROPE`,
`REPORT1`–`REPORT9`, `NATIONS`, `DIFFICUL`, `CUSTOMIZ`, `DECLARAT`/`DECOIND`, `OPENING`/`OPENMENU`/
`OPENBORD`, `KINGLSS1/2`, `WOODPANL`/`WOODPAN2`, `CLOS-BKG`/`CCBKGD` + `LEVN0001`–`LEVN0010`.

## Backlog

- **A0/A1/A3 ✅** — assets located + format confirmed; native-Java decoder/converter; pack loader
  (`FreeColClient.withClassicOriginalPack`, graceful fallback when absent).
- **A2 🔨 growing** — alias curation is the bulk of the asset work. Live so far: title screen, all map
  terrain, `PHYS0.SS` overlays + coast feathering, `ICONS.SS` unit/goods/settlement sprites; the
  colony/Europe/report screens load their `.PIK`/`.SS` art by key. Remaining: per-nation unit/colony
  tints, a fortress-distinct colony frame, and each new Phase-2/4 screen's art as it lands.
- **A5 ⬜** — runtime (in-game) extraction: install picker → decode on demand in-process (the Java
  decoder unblocks this), restart-to-apply UI switch (the GUI is chosen once at `FreeColClient`
  construction, so switching UI is preference+restart, not an in-place swap).
- **A6 ⬜** — original audio: SFX (`COLDIG.BIN`, raw unsigned 8-bit PCM) feasible; music
  (`AMER2.MP` MPS sequence + `*SOUND.COL` DOS drivers) hard/poor-ROI. Do SFX after the UI phases that
  trigger them; music likely out-of-scope. Gates nothing.
