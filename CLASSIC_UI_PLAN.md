# Classic UI for FreeCol — architecture findings & implementation plan

Goal: a faithful original-Colonization experience — a primitive, original-style **UI**
*on top of the classic **rules*** — built as an alternative view on the existing engine.
An expert Colonization player iterates with us per slice.

Two independent axes (keep them separate):
- **UI** — our `ClassicGUI` work (this document's phases).
- **Rules** — supplied by FreeCol's data-driven **`classic` ruleset** (`data/rules/classic/`),
  loaded at game start. The default FreeCol game is `classic` *plus a diff*
  (`freecol`'s spec literally declares `extends="classic"`). Faithfulness to the original
  game is therefore mostly a *ruleset* question, audited in one place — see
  "Gameplay fidelity" below.

## How to run & test (classic UI)

Build once after a code change, then launch with `--classic`. Run from the repo root so `data/`
is found. `ant compile` refreshes `build/`, so we can launch straight from there (no repackage):

```powershell
ant compile
java -Xmx2G -cp "build;jars/*" net.sf.freecol.FreeCol --classic
```

One-liner for the tight test loop (compile, launch, capture console to `classic_run.txt`):

```powershell
ant compile; java -Xmx2G -cp "build;jars/*" net.sf.freecol.FreeCol --classic 2>&1 | Tee-Object classic_run.txt
```

Notes:
- `;` is the Windows classpath separator; `jars/*` pulls in all dependency jars.
- `--classic` selects `ClassicGUI` (see `FreeColClient` selector). Start a new game to reach the
  in-game view where `ClassicGUI.startGUI` fires.
- The `options.xml NoSuchFileException` on first launch is a benign pre-existing warning.
- Packaged-jar alternative: `ant package` then `java -Xmx2G -jar FreeCol.jar --classic` (the Ant
  `run` target does not pass `--classic`, so invoke the jar directly).

## Design inputs: reference screenshots & where the art comes from

Two distinct things, do not conflate them:

- **Layout/interaction reference = screenshots of the *original* 1994 Colonization.** Yes — asking
  the expert for a screenshot of *every screen and key sub-state* is exactly right and is the
  primary design spec per screen. Guidance for the expert:
  - **Naming:** name each file after the screen so it maps to the `GUI` method / phase slice it
    drives, e.g. `colony.png`, `colony-drag-colonist.png`, `europe.png`, `europe-recruit.png`,
    `mapview.png`, `unit-orders-bar.png`, `combat-result-dialog.png`, `report-trade.png`,
    `negotiation.png`. Sub-states get a suffix (`-<state>`).
  - **Capture states, not just the idle screen:** open menus, an in-progress drag, a selected unit,
    a dialog mid-interaction — the *interactions* are as important as the static layout.
  - A one-line note per shot on what clicks/keys do is gold (we reuse the existing keyboard
    accelerators, so noting Col1's hotkeys helps us match them).
  These reference shots are design input only; we render with FreeCol's own art (below), not the
  original game's copyrighted assets.

- **Render assets = FreeCol's own resources, not the original game's files.** Image keys are mapped
  to PNGs in `resources.properties` files and resolved via `ImageCache`/`ImageLibrary`. Locations:
  - `data/base/resources/images/`, `data/default/resources/images/` — base + default art. The
    base set **already defines the order-button icons** (`build`, `fortify`, `sentry`, `done`,
    `plow`, `road`, `disband`, …) that the `FreeColAction`s look up via
    `ImageLibrary.getButtonImages` — i.e. the icons our Phase-0 fix resolves come from `base`,
    not from any "classic" mod.
  - ⚠️ **`data/mods/classic_ui/` is NOT the original-game look — name is a false friend.** Its own
    description: *"UI graphics used by FreeCol up to v0.9.x."* "Classic" here means **classic
    *FreeCol*** (an old FreeCol parchment theme), **not classic *Colonization* (the 1994 game)**. It
    only reskins chrome (paper backgrounds, borders, an old minimap/infopanel skin). Useful as
    *asset plumbing* reference and maybe for a few textures, but it will **not** make the UI resemble
    the original game. Do not treat it as the design target — the expert's original-game screenshots
    are the design target; our own panels + rectangular `ClassicMapViewer` are the real work.
  - To find the asset behind any key, grep the `resources.properties` files for the key; the value
    is a path under that mod's `resources/`.

## Asset strategy: bring-your-own original install (DECIDED)

**Context:** personal-use build; goal is pixel-faithful original art *without* committing copyrighted
files. *Sid Meier's Colonization* is still sold (Steam/GOG "Classic" = the original DOS game in
DOSBox), so the user can legally own it and we load its art at runtime — the standard open-remake
pattern (OpenRCT2, ScummVM, OpenMW, Devilution).

**Decision:** **Ship no original assets. Load the original game's pixels from the user's own install.**
If no install is configured, fall back to FreeCol's free art so the build always runs. FreeCol art is
the *fallback skin*; original art is the *fidelity skin*.

### Pipeline — offline converter → local resource pack (reuse FreeCol's mod system)
Prefer one-time *conversion* over runtime format parsing:
1. A **converter** reads the original art from the install dir and writes PNGs into a **git-ignored**
   `data/mods/classic_original/` pack, plus a `resources.properties` mapping FreeCol resource keys →
   the extracted PNGs.
2. The classic UI loads that pack if present (like any other mod); otherwise base/default art is used.
3. Nothing copyrighted is committed — the generated pack is local-only (add to `.gitignore`).

**The converter is pure Java** (`net.sf.freecol.tools.classicassets`), part of the FreeCol source
tree and driven by `ant classic-assets`. There is **no Python / venv / Pillow / external tool** in
the pipeline any more — see "Decoder" below for why and how we got here.

### Original formats & tooling — VERIFIED against this user's GOG install (A0 done)
Install path (GOG): `C:\Program Files (x86)\GOG Galaxy\Games\Colonization`; assets live in
`MPS\COLONIZE\` (290 files). Confirmed by magic bytes (`MADSPACK 2.0` header on every graphic):
- **All graphics are `MADSPACK 2.0` containers** (not `.PIC` as earlier web research suggested —
  that was for *other* MicroProse titles). Two graphic kinds:
  - **`.PIK`** (35 files) — full-screen 320×200 pictures = the game's *screens* & chrome (see manifest below).
  - **`.SS`** (206 files) — sprite/"shape" sets, multiple frames per file (units, terrain, buildings, icons).
- **Palette:** `VICEROY.PAL` (1024 bytes = a 768-byte 256×3 six-bit-VGA RGB palette + a 256-byte
  trailer we ignore) is the master 256-colour gameplay palette, used to decode the palette-less PIK
  screens (`COLONY.PIK`). *(`ASOUND/GSOUND/PSOUND/RSOUND.COL` are sound-driver configs, NOT palettes.)*
- **Format reference:** [`eb4x/viceroy`](https://github.com/eb4x/viceroy) / re:Colonization and the
  format notes in [`institution/mpskit`](https://github.com/institution/mpskit).

### Decoder — native Java, clean-room from the format spec (A1 done)
The whole read path is a small, self-contained slice of the MADSPACK format, so we **reimplemented
it directly in Java** rather than depend on an external tool:
- **`MadsPack`** — MADSPACK 2.0 container: 12-byte magic, `uint16` part count at offset 14, a 0xA0
  header block of `(uint16 flag, uint32 size, uint32 csize)` entries, then the parts. Per-part
  bit-0 flag selects FAB compression.
- **`Fab`** — the one non-trivial algorithm: an LZ-style bit-stream decompressor (literal / copy
  commands, LSB-first bit reader with the 16-bit refill quirk).
- **`Palette`** — 6-bit VGA → 8-bit (`v*255/63`); reads both the embedded `.SS`/`.PIK` palettes and
  the master `VICEROY.PAL`.
- **`SsDecoder`** — `.SS` sprite sets: per-sprite header table + the linemode/`FE`/`FD`/`FF`/`FC`
  RLE, decoded straight into `TYPE_INT_ARGB` `BufferedImage`s (palette index `0xFD` → transparent).
- **`PikDecoder`** — `.PIK` full screens: raw 8-bit indexed blit, embedded palette or `VICEROY.PAL`.
- **`ClassicAssetConverter`** — the `main`: walks the install dir, decodes every `.SS`/`.PIK`, writes
  the PNGs + `resources.properties` + `mod.xml` + messages, appending `aliases.properties` (A2).

**Why Java, not the earlier Python/mpskit route.** We first wrapped `mpskit` (AGPLv3, Python) and
hit its Pillow-9 `ImagePalette` palette-scrambling bug plus a palette-less-`COLONY.PIK` gap, patched
around both with a monkeypatch wrapper — brittle, and it dragged in a venv + pinned `Pillow<10`
(⇒ Python 3.9–3.11 only) + a network fetch. Decoding into a `BufferedImage` ourselves makes those
bugs structurally impossible (we never touch an indexed-PNG palette), removes every non-Java
dependency, and — decisively — is a **prerequisite for runtime extraction** (below). Written
clean-room from the format documentation, so it is our own code under FreeCol's **GPLv2+** (an
AGPLv3 line-by-line port would have been license-incompatible with upstream).

### Runtime extraction — future direction (A5, not yet built)
Today extraction is build-time (`ant classic-assets`). The intended end state is **in-game**: the
user points the game at their Colonization install in settings and the classic art is decoded on
demand. The Java decoder above is exactly what unblocks this — the same classes run in-process, so
A5 is mostly UI plumbing on top:
- a settings field + directory picker (validate by MADSPACK magic bytes, as A0 did);
- on confirm, run the decoder → write the **same** git-ignored `classic_original` pack, then load it
  as a mod (reuses all of A2/A3's plumbing unchanged); later, decode straight into `ResourceManager`
  to skip the disk pack.
- ⚠️ **Switching the *UI* at runtime ≠ switching *assets*.** The GUI is chosen once at
  `FreeColClient` construction, so "switch to classic UI in settings" is realistically a
  **preference + restart** (an in-place `SwingGUI`↔`ClassicGUI` swap is a much larger job). Scope A5
  as restart-to-apply. FreeCol art remains the fallback skin when no install is configured.

### Original-game screen manifest (from the 35 `.PIK` files) — drives UI phasing & the expert's shot list
`COLONY` (colony screen), `EUROPE` (Europe), `REPORT1`–`REPORT9` (the nine reports), `NATIONS`
(nation select), `DIFFICUL` (difficulty), `CUSTOMIZ` (customise), `DECLARAT`/`DECOIND` (declare
independence), `OPENING`/`OPENMENU`/`OPENBORD` (title/menu), `KINGLSS1/2` (king screens),
`WOODPANL`/`WOODPAN2` (wood-panel UI chrome), `CLOS-BKG`/`CCBKGD` + `LEVN0001`–`LEVN0010` (intro/closing
art). This enumerates exactly which screens exist — use it to scope the UI phases and to give the
expert a complete screenshot checklist.

### Asset backlog (own track; UI phases 0–2 proceed on fallback art meanwhile)
- **A0** — locate the PIC/MADSPACK assets in a Steam/GOG "Classic" install; confirm layout & format.
- **A1** — stand up the converter → PNGs. **Native-Java** MADSPACK/FAB/SS/PIK decoder (no external tool).
- **A2** — author the **key-mapping table**: FreeCol keys (terrain, units, goods, UI chrome, order
  buttons, fonts) → original frames, in the committed `tools/classic_assets/aliases.properties`.
  This curation is the bulk of the work.
- **A3** — wire a `--classic-assets <dir>` option (or client option) + `classic_original` pack
  loader, with graceful fallback to FreeCol art.
- **A5** — runtime extraction: in-game install picker → decode on demand (see "Runtime extraction"
  above). Reuses the A1 decoder in-process; scope the UI switch as restart-to-apply.

## Architecture findings (why this is feasible)

FreeCol is cleanly layered (client/server):

- **Model** — `common/model/` (138 classes; `Unit`, `Colony`, `Player`, `Tile`, `Map`…). UI-agnostic; the client holds a synced copy, the **server** (`server/`) is rules-authoritative.
- **Controllers** — `client/control/` (`InGameController`, `PreGameController`, `ConnectController`). UI-agnostic; reach the view **only** via `getGUI().<method>`.
- **View facade** — `client/gui/GUI.java`: a **concrete base class, 0 abstract methods, no-op stubs** (e.g. `public void changeView(Tile tile) {}`), **217 public methods**. `SwingGUI extends GUI` overrides **174**.

Selection point — `client/FreeColClient.java:243`:
```java
gui = (FreeCol.getHeadless()) ? new GUI(this) : new SwingGUI(this);
```
FreeCol already runs with a non-Swing view (base `GUI`, headless). So a new UI = another `GUI`
subclass; unoverridden methods no-op, so **the app runs from day one** and we light up screens
incrementally. Each base method's Javadoc names its callers.

## Decisions

- **Technology: Swing**, as `ClassicGUI extends GUI` in a new `client/gui/classic/` package. The
  whole client is Swing/AWT-bound (ImageLibrary, FontLibrary, FreeColFrame, plaf, 86 Action
  classes); a rectangular-tile map is trivial in `Graphics2D`. Non-Swing would mean reimplementing
  all that plumbing for no gain.
- **Additive changes only**: new `client/gui/classic/` package + a one-line selector change +
  a `--classic` flag. Keeps us mergeable with upstream `master`.

## Reuse map

| Component | Reuse |
|---|---|
| `common/model` | 100% (read state directly) |
| `client/control/*` controllers | 100% |
| Networking, `ClientOptions`, Image/Font/Sound resources | 100% |
| `client/gui/action/` (86) | High |
| `client/gui/mapviewer/` (5210 lines) | Partial — reuse image-selection logic, replace isometric→rectangular projection (`TileBounds`/`MapViewerBounds`) |
| `client/gui/panel/` (101) + `dialog/` (33) | Optional — reskin our own, or delegate temporarily |

## Phased plan (each slice independently demoable)

- **Phase 0 — Scaffold & launch. ✅ DONE (verified live 2026-07-07).** New
  `client/gui/classic/ClassicGUI extends GUI`; `--classic` flag; selector
  `headless ? GUI : classic ? ClassicGUI : SwingGUI`. Bare window boots and a **new
  single-player game runs** with `ClassicGUI` — 0 SEVERE, only the benign `options.xml`
  warning. *(Proves the seam.)* One override was needed to reach the in-game view:
  `showStartGamePanel` auto-launches single-player (base `GUI` no-ops the lobby, which
  otherwise stalls a new game at login — see below).
- **Phase 1 — The map.** `ClassicMapViewer` with rectangular projection (reuse `ImageLibrary`
  lookups). Terrain/units/colonies/cursor/minimap. Wire mouse/keyboard → controllers. Override
  `changeView`, active-unit state, `refresh`/`refreshTile`, scrolling. *(First real demo.)*
- **Phase 2 — HUD & core screens.** Info/orders bar, menu bar (reuse `action/`), **Colony screen**
  (signature original screen), Europe, unit/cargo, reports. Each = a `showXPanel` override; may
  delegate to existing Swing panel as a stopgap, then reskin.
- **Phase 3 — Dialogs & polish.** `modalConfirmDialog`/`modalChoiceDialog`/`modalInputDialog`,
  negotiation, end-turn. Reuse existing Swing dialogs first, reskin to taste.

**Expert player drives:** screen priority, validating each rebuilt screen vs. the real
Colonization layout/interaction, sign-off per slice.

## Gameplay fidelity: classic ruleset vs. original Colonization (Col1)

FreeCol's `classic` ruleset is the team's **best-effort emulation of the original 1994 game**
("attempts to emulate the rules of the original game as far as possible"). It is the base that
the `freecol` ruleset extends. Choosing it gets us most of the way to Col1 fidelity *for free*,
but it is **not a bit-perfect clone** — some divergences are deliberate options (already set to
the Col1 value in classic) and some are residual engine-level differences the ruleset cannot fix.

Because rules are data-driven, **our UI project does not implement any of this** — we just load
the `classic` ruleset. This section exists so the expert player and we share one audited list of
where "classic" still departs from the real game, separate from UI work.

### A. Options where `classic` already matches Col1 (we get these for free)

Verified against this repo's [`data/rules/classic/specification.xml`](data/rules/classic/specification.xml)
(defaults shown are the classic values; the `freecol` ruleset flips several of these):

| Behaviour | Option | classic default | Col1-faithful? |
|---|---|---|---|
| Amphibious assault (attack from ship) | `amphibiousMoves` | `false` | ✅ Col1 had none |
| Manual choice of student to train | `allowStudentSelection` | `false` (least-skilled first) | ✅ matches Col1 |
| REF arrival | `teleportREF` | `true` (teleports) | ✅ matches Col1 |
| Custom House sells boycotted goods | `customIgnoreBoycott` | `true` (ignores boycott) | ✅ matches Col1 |
| Enhanced missionaries (vision/trade/training) | `enhancedMissionaries` | `false` | ✅ Col1 had none |
| Exploration (lost-city) points scoring | `explorationPoints` | `false` | ✅ matches Col1 |
| Scouting any settlement action consumes bonus | `settlementActionsContactChief` | `false` | ✅ matches Col1 |
| Found colonies during War of Independence | `foundColonyDuringRebellion` | `false` | ✅ matches Col1 |
| Bell accumulation capped at 100% rebels | `bellAccumulationCapped` | `true` | ✅ matches Col1 |
| Classic fixed starting positions | `startingPositions` | `0` (classic) | ✅ matches Col1 |
| Equip new European recruits | `equipEuropeanRecruits` | `true` | ✅ matches Col1 |

> ⚠️ Doc-vs-repo discrepancy: the official user guide (v0.11.6) describes classic as enabling
> `expertsHaveConnections` ("experts produce without raw materials"). **This repo's classic spec
> sets it `false`.** The repo is what we ship — treat the repo defaults above as authoritative and
> re-audit if we bump the ruleset version.

### B. Residual divergences the classic ruleset does *not* fix (true fidelity gaps)

These are engine-level — not toggleable from the ruleset — so a faithful-classic goal means
either accepting them or patching the engine. Confidence varies; sources noted.

- **Combat math.** Even with the classic ruleset, FreeCol does **not** reproduce Col1's combat
  resolution (FreeCol uses a power × modifiers / random model; Col1 used its own formula with
  different terrain/ambush/odds handling). Acknowledged open item upstream (SF pending-feature
  #65). *Highest-impact divergence; a power player will feel it.* **[Confirmed — FreeCol dev tracker]**
- **Founding Father recruitment basis.** Col1 recruits from *gross* bell production; FreeCol uses
  *net*. The classic ruleset is supposed to switch to gross — verify in our build. **[FreeCol "What Would Col1 Do?" wiki]**
- **Movement-point carryover.** Col1 allegedly lets unused movement carry to the next turn;
  FreeCol resets each turn. **[Community-reported]**
- **River corner-cutting.** Diagonal moves cutting a corner go overland in Col1 (can end the turn
  early); FreeCol treats them as river movement. **[Community-reported]**
- **Bell→Sons-of-Liberty formula.** Col1 ≈ `bells/(pop+1)`; FreeCol ≈ `bells/(pop·2)`, giving a
  different SoL ramp. **[Community-reported — verify before relying on it]**
- **Food/horse growth ordering.** FreeCol counts fish before grain and fish don't feed horse
  growth; Col1 ordered these differently. **[Community-reported]**
- **REF growth.** Col1's REF scales with tax income; FreeCol grows it steadily regardless. **[FreeCol wiki]**
- **Unimplemented Col1 features.** Escalating native tribute demands before war; sailing to other
  Europeans' ports after independence; the Custom House rival-trade report / post-war trade perks;
  the 9% tax bump accompanying the post-privateer frigate offer. **[FreeCol wiki]**
- **Ranged attack is dormant, not a divergence.** The `AttackRanged` order requires a unit type
  with `attackRange > 0`; **no classic (or freecol) unit type sets it**, so the order is always
  disabled. It is a mod-only engine hook, not a gameplay difference to worry about.
  (`model.ability.bombard` / fort-and-ship bombardment *is* genuine Col1 behaviour and is present.)

### C. Other FreeCol additions to keep out of the classic experience

Not rule divergences per se, but features absent from Col1 that our UI should not surface (or
should gate behind the ruleset): **trade routes**, the four extra nations (Portugal/Sweden/
Denmark/Russia) and their advantages, abandon-colony-anytime, and the "destroy all Europeans"
victory condition. The classic ruleset already restricts nations to the original four.

### Implications for this project

- **Scope:** UI only. We do **not** re-implement rules; we load `classic`. Items in **B** are an
  engine-fidelity backlog, explicitly *out of scope* for the UI phases unless we later choose to
  patch the engine.
- **Validation:** when the expert player flags a "that's not how Col1 behaves" issue, first
  classify it — UI rendering (our bug), a class-A option (check the default), or a class-B engine
  gap (known, backlog). This keeps UI iteration from getting derailed by rules questions.
- **Open task:** confirm in *our* build that (a) classic loads with the defaults in table A, and
  (b) the gross-vs-net Founding Father basis actually switches under classic.

### D. Roadmap to "as-classic-as-possible" (rules, including engine changes)

This is a **separate track** from the UI phases, runnable in parallel. Guiding principle, consistent
with this project's "additive changes only" decision: **prefer data/ruleset fixes; when engine code
must change, gate the new behaviour behind a ruleset option** (default = current behaviour for the
`freecol` ruleset, Col1-correct for `classic`). That keeps us mergeable with upstream `master` and
avoids regressing the default game. Each item below is tagged **[data]** (ruleset only) or
**[engine]** (code + a gating option), with a rough confidence/effort note.

**Phase R0 — Baseline & instrumentation** *(prereq for everything; low risk)*
- Launch with `--classic` *and* the classic ruleset; confirm table-A defaults actually load.
- Build a one-page **Col1 conformance checklist** the expert signs off against (combat odds,
  SoL ramp, immigration, REF, prices…). This is the acceptance instrument for R1–R3.
- Verify the gross-vs-net Founding-Father bell basis in *our* build; decide data vs engine fix.
- Confirm the class-B "community-reported" formulas against the engine source before acting on
  them (don't patch on forum lore). Promote each to confirmed/rejected.

**Phase R1 — Ruleset-only corrections** *(no engine risk; do first)* **[data]**
- Audit every `booleanOption`/modifier in `classic/specification.xml` against the checklist; flip
  any whose default isn't Col1-correct.
- Resolve the `expertsHaveConnections` doc-vs-repo discrepancy by decision, not assumption.
- Tune Col1-divergent modifier *values* (e.g. combat terrain/ambush/fortify/artillery-in-open) where
  the structure already exists and only the number is off.

**Phase R2 — Engine corrections, gated by option** *(the substantive work)* **[engine]**
- **Combat resolution** *(highest impact, hardest)* — implement a Col1-faithful combat path
  selectable via a new `model.option.combatModel` (values: `freecol` default, `classic`). Reproduce
  Col1's odds/terrain/ambush/fortification handling. Confidence: **confirmed gap**; effort: **high**.
- **Bell→SoL formula** — if confirmed as `bells/(pop+1)` vs FreeCol's `bells/(pop·2)`, add a
  ruleset-selectable formula. Effort: low–med once confirmed.
- **Movement-point carryover** and **river corner-cutting** — option-gated tweaks in the move logic.
  Effort: med; confirm first (R0).
- **Founding-Father gross-bell basis** — switch under classic if not achievable in data.
- **Food/horse growth ordering** — align ordering under a classic option. Effort: low.
- **REF-growth-vs-tax** — make REF growth scale with tax income under classic. Effort: med.

**Phase R3 — Missing Col1 features** *(net-new; largest)* **[engine]**
- Escalating native **tribute demands** before war; post-independence **trade with other Europeans'
  ports**; Custom House **rival-trade report** & post-war trade perks; the **9% tax bump** with the
  post-privateer frigate offer. Each is a discrete feature; schedule by the expert's priority.

**Explicitly out of scope (anti-fidelity if added):** keep trade routes, the four extra nations,
abandon-colony-anytime, and "destroy all Europeans" victory *off* in the classic experience. The
classic ruleset already restricts nations; ensure our UI doesn't surface the rest.

**Sequencing:** R0 → R1 (cheap wins, no risk) → R2 (combat first, it dominates feel) → R3 (by
priority). Track these as their own backlog; they do not block UI phases 0–3 and vice-versa.

*Sources:* [FreeCol user guide — ruleset comparison](https://www.freecol.org/docs/FreeCol.html),
[FreeCol "What Would Col1 Do?" wiki](https://sourceforge.net/p/freecol/wiki/What%20Would%20Col1%20Do%3F/),
[SF pending-feature #65 — combat differs under classic](https://sourceforge.net/p/freecol/pending-features-for-freecol/65/),
[Civ wiki — FreeCol divergences from Colonization](https://civilization.fandom.com/wiki/FreeCol_1.0.0/Divergences_from_Colonization),
and this repo's `data/rules/classic/specification.xml`.

## Immediate next steps (as of the native-Java asset decoder landing)

Assets now extract cleanly, but **nothing renders them yet**: the `classic_original` pack is
produced (A1) but not loaded (A3), unmapped (A2), and the classic UI has only its Phase-0 placeholder
window. The classic UI runs on **fallback FreeCol art** until A3+A2 land — asset availability does
*not* gate the UI phases. Recommended order:

1. **Phase 0 live start-up test** *(gate; independent of the new assets).* ✅ **DONE 2026-07-07.**
   `ant compile` then `java -cp "build;jars/*" net.sf.freecol.FreeCol --classic --fast --no-intro`
   boots a new single-player game (map generated, in-game view reached) with 0 SEVERE and only the
   benign `options.xml` warning. Fix applied: `ClassicGUI.showStartGamePanel` auto-launches
   single-player (base lobby is a no-op, otherwise the new game stalls at login).
2. **A3 — pack loader** *(small; makes A1's output usable).* Load `data/mods/classic_original/` into
   the `ResourceManager` when it exists (and when `--classic`), with graceful fallback to base/default
   art. Open question to settle here: does our minimal `mod.xml` satisfy `FreeColModFile`, or do we
   overlay resources more directly? Verify one `image.classic_original.*` key resolves in the running
   client.
3. **A2 (seed) — a few aliases** to prove end-to-end that original art appears (e.g. one screen/tile),
   then grow the mapping alongside each UI phase.
4. **Phase 1 — the map** — the first real screen and the first place the extracted terrain/unit
   sprites visibly pay off. Proceeds on fallback art if A2 lags.

## Status

UI track:
- [x] Phase 0 — scaffold & launch: **DONE — verified live 2026-07-07.** `--classic` flag,
      selector, `ClassicGUI` placeholder window, image libraries wired; icon-loading NPE fixed.
      Live start-up test passed: `java --classic --fast --no-intro` boots a **new single-player
      game** (map generated, in-game view reached) with **0 SEVERE** and only the benign
      `options.xml` warning. Needed one override — `showStartGamePanel` auto-launches single-player
      (the base `GUI` no-ops the lobby panel, so `ConnectController.login` otherwise stalls a new
      game at login; auto-launch mirrors `StartGamePanel`'s Start button). Test recipe:
      `--fast --no-intro` avoids needing GUI clicks to start a game.
- [ ] Phase 1 — map
- [ ] Phase 2 — HUD & core screens
- [ ] Phase 3 — dialogs & polish

Rules-fidelity track (see "Gameplay fidelity" §D):
- [ ] R0 — baseline & instrumentation (load classic, conformance checklist, confirm class-B formulas)
- [ ] R1 — ruleset-only corrections
- [ ] R2 — engine corrections gated by option (combat first)
- [ ] R3 — missing Col1 features

Asset track — bring-your-own original install (see "Asset strategy"):
- [x] A0 — assets located & format confirmed: GOG `…\Colonization\MPS\COLONIZE\`, all MADSPACK 2.0
      (`.PIK` screens, `.SS` sprite sets, `VICEROY.PAL` palette).
- [x] A1 — converter IMPLEMENTED & RUN end-to-end as a **native-Java** tool
      (`net.sf.freecol.tools.classicassets`, driven by `ant classic-assets`; unpack→PNG,
      git-ignored `data/mods/classic_original/`). BUILD SUCCESSFUL: **1723 SS frames + 35 PIK
      screens** extracted, all usable. No Python / venv / Pillow / mpskit in the pipeline.
      - History: originally wrapped `mpskit` (Python/AGPLv3) with a monkeypatch for its Pillow-9
        palette-scrambling bug + palette-less-`COLONY.PIK` gap. That whole route was **removed** and
        replaced by the clean-room Java decoder — structurally free of the palette bug (decodes into
        `BufferedImage`), dependency-free, GPLv2+-compatible, and reusable in-process for runtime
        extraction (A5). VICEROY.PAL layout cracked earlier still holds: 1024 B = 768-B 256×3
        six-bit-VGA palette + 256-B trailer (ignored).
- [ ] A2 — key-mapping table: FreeCol keys → `image.classic_original.*` via committed
      `tools/classic_assets/aliases.properties` (appended into the pack automatically)
- [ ] A3 — `--classic-assets` option / `classic_original` pack loader in `ClassicGUI`, fallback to FreeCol art
- [ ] A5 — runtime (in-game) extraction: install picker + on-demand decode, restart-to-apply UI switch
