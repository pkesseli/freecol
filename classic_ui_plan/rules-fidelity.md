# Rules fidelity — classic ruleset vs. original Colonization (Col1)

FreeCol's `classic` ruleset is the team's **best-effort emulation of the original 1994 game**, and
the base the `freecol` ruleset extends (`freecol`'s spec declares `extends="classic"`). Choosing it
gets us most of the way to Col1 fidelity *for free*, but it is **not a bit-perfect clone**.

Because rules are data-driven, **our UI project does not implement any of this** — we just load
`classic`. This is the shared audit of where "classic" still departs from the real game: a separate
track that **does not block the UI**, and vice-versa.

## A. Options where `classic` already matches Col1 (free)

Verified against [`data/rules/classic/specification.xml`](../data/rules/classic/specification.xml)
(defaults are the classic values):

| Behaviour | Option | classic default | Col1-faithful? |
|---|---|---|---|
| Amphibious assault (attack from ship) | `amphibiousMoves` | `false` | ✅ Col1 had none |
| Manual choice of student to train | `allowStudentSelection` | `false` (least-skilled first) | ✅ |
| REF arrival | `teleportREF` | `true` (teleports) | ✅ |
| Custom House sells boycotted goods | `customIgnoreBoycott` | `true` | ✅ |
| Enhanced missionaries | `enhancedMissionaries` | `false` | ✅ Col1 had none |
| Exploration (lost-city) points scoring | `explorationPoints` | `false` | ✅ |
| Scouting settlement consumes bonus | `settlementActionsContactChief` | `false` | ✅ |
| Found colonies during War of Independence | `foundColonyDuringRebellion` | `false` | ✅ |
| Bell accumulation capped at 100% rebels | `bellAccumulationCapped` | `true` | ✅ |
| Classic fixed starting positions | `startingPositions` | `0` (classic) | ✅ |
| Equip new European recruits | `equipEuropeanRecruits` | `true` | ✅ |

> ⚠️ Doc-vs-repo: the official user guide (v0.11.6) says classic enables `expertsHaveConnections`;
> **this repo's classic spec sets it `false`.** The repo is what we ship — treat repo defaults as
> authoritative and re-audit if we bump the ruleset version.

## B. Residual divergences the classic ruleset does *not* fix (engine-level)

Not toggleable from the ruleset — a faithful-classic goal means accepting or patching the engine.

- **Combat math.** FreeCol does **not** reproduce Col1's combat resolution (power × modifiers /
  random vs Col1's own odds/terrain/ambush handling). SF pending-feature #65. *Highest-impact.*
  **[Confirmed — FreeCol dev tracker]**
- **Founding Father basis.** Col1 recruits from *gross* bells; FreeCol uses *net*. classic is
  supposed to switch to gross — verify. **[FreeCol wiki]**
- **Movement-point carryover.** Col1 allegedly carries unused movement; FreeCol resets. **[Community]**
- **River corner-cutting.** Diagonal corner-cuts go overland in Col1; FreeCol treats as river.
  **[Community]**
- **Bell→SoL formula.** Col1 ≈ `bells/(pop+1)`; FreeCol ≈ `bells/(pop·2)`. **[Community — verify]**
- **Food/horse growth ordering.** FreeCol counts fish before grain, fish don't feed horses; Col1
  differed. **[Community]**
- **REF growth.** Col1 scales REF with tax income; FreeCol grows it steadily. **[FreeCol wiki]**
- **Unimplemented Col1 features.** Escalating native tribute before war; sailing to other Europeans'
  ports after independence; Custom House rival-trade report / post-war perks; the 9% tax bump with the
  post-privateer frigate offer. **[FreeCol wiki]**
- **Ranged attack is dormant, not a divergence.** `AttackRanged` needs `attackRange > 0`; no classic
  or freecol unit type sets it. (`model.ability.bombard` fort/ship bombardment *is* genuine Col1 and
  present.)

## C. Other FreeCol additions to keep out of the classic experience

Absent from Col1, our UI should not surface: **trade routes**, the four extra nations
(Portugal/Sweden/Denmark/Russia) + advantages, abandon-colony-anytime, "destroy all Europeans"
victory. The classic ruleset already restricts nations to the original four.

## D. Roadmap to "as-classic-as-possible" (rules track, parallel to UI)

Guiding principle (consistent with "additive changes only"): **prefer data/ruleset fixes; when engine
code must change, gate it behind a ruleset option** (default = current `freecol` behaviour,
Col1-correct for `classic`) so we stay mergeable with upstream `master`.

- **R0 — Baseline & instrumentation** ⬜ *(prereq; low risk)* — confirm table-A defaults load under
  `--classic`; build a one-page Col1 conformance checklist (the acceptance instrument); verify the
  gross-vs-net Founding-Father basis in our build; confirm the class-B "community-reported" formulas
  against engine source before acting (don't patch on forum lore).
- **R1 — Ruleset-only corrections** ⬜ **[data]** — audit every option/modifier vs the checklist and
  flip non-Col1 defaults; resolve `expertsHaveConnections` by decision; tune divergent modifier
  *values* (combat terrain/ambush/fortify/artillery-in-open).
- **R2 — Engine corrections, gated by option** ⬜ **[engine]** — combat resolution (new
  `model.option.combatModel`, *highest impact / hardest*); Bell→SoL formula; movement carryover +
  river corner-cutting; Founding-Father gross basis; food/horse ordering; REF-growth-vs-tax.
- **R3 — Missing Col1 features** ⬜ **[engine]** — native tribute escalation; post-independence trade
  with other Europeans; Custom House rival-trade report + post-war perks; the 9% tax bump.

**Sequencing:** R0 → R1 (cheap, no risk) → R2 (combat first) → R3.

*Sources:* [FreeCol user guide](https://www.freecol.org/docs/FreeCol.html),
["What Would Col1 Do?" wiki](https://sourceforge.net/p/freecol/wiki/What%20Would%20Col1%20Do%3F/),
[SF pending-feature #65](https://sourceforge.net/p/freecol/pending-features-for-freecol/65/),
[Civ wiki — divergences](https://civilization.fandom.com/wiki/FreeCol_1.0.0/Divergences_from_Colonization),
and this repo's `data/rules/classic/specification.xml`.
