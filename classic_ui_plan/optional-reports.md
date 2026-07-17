# Optional reports — our own additions, in the original's style

**Status: ⬜ deferred by decision (2026-07-17).** Not Col1 screens, not blocking any UI phase, and not
to be built until the faithful surface is done. This chapter exists so they stop being counted as
*missing* work in Phase 2.

## Why these are here rather than in Phase 2

Four `showReport*Panel` seams have **no original screen behind them**. The original ships nine report
backdrops (`REPORT1`–`REPORT9`); the ten reports we already show cover eight, and `REPORT9` merely
duplicates `REPORT1`'s native-scout art. Only **foreign affairs** — which Col1 does have — has
original art unaccounted for, and it stays in [Phase 2](ui-phases.md#remaining) as real work.

But these four are **not FreeCol inventions**, which is why they are deferred rather than refused.
Their concepts are all Col1: education is a core original mechanic (the classic ruleset ships
schoolhouse/college/university and `allowStudentSelection`, listed Col1-faithful in
[rules fidelity](rules-fidelity.md) table A); labour is a census of unit types you own; history logs
events that are almost all original. The divergence is **informational, not conceptual** — aggregate
views Col1 made you track by hand — so §C ("FreeCol additions to keep out") does not apply, and the
rules axis is untouched.

**The decision:** build them later as **genuine contributions of our own** — the reports Col1 might
have had — in the same visual style as the rest of the game: the shared `ClassicReportPanel` frame,
gold-on-black title bar, red Okay plate, the 320×200 virtual canvas up-scaled nearest-neighbour.
An addition presented in the original's idiom, not a FreeCol panel bolted on.

## The four

### Labour — `showReportLabourPanel`, `showReportLabourDetailPanel(UnitType, …)`

A census of every unit type the player owns and how many of each, with a drill-down per type
(`ReportLabourPanel` / `ReportLabourDetailPanel`, and FreeCol's own `CompactLabourReport` variant).
Two seams, so the detail view is a second screen. Every concept is Col1 — it is a count of what you
already have.

### Education — `showReportEducationPanel`

Per colony, each teaching building (`building.canTeach()` — schoolhouse/college/university) with its
teachers and which student each is training. Pure Col1 mechanic; the report only aggregates it.
Closest in shape to our existing per-colony reports, so likely the cheapest of the four.

### History — `showReportHistoryPanel`

The player's `HistoryEvent` log, turn-labelled: discovering the New World, discovering regions,
meeting nations, cities of gold, founding/losing colonies, founding fathers, declaring independence,
war and peace. Almost all Col1 concepts.

> ⚠️ **§C leak.** Two `HistoryEventType`s — `ABANDON_COLONY` and `DESTROY_NATION` — map onto the two
> additions [rules fidelity](rules-fidelity.md) §C says to keep out of the classic experience
> (abandon-colony-anytime, the "destroy all Europeans" victory). If we build this, filter those two
> event types rather than surfacing them.

### Requirements — `showReportRequirementsPanel` ⚠️ decide deliberately

**Not a view — an optimisation coach**, and the one item here that adds a *capability* rather than an
information view. Its own strings give it away: *"%colony% has a %expert% currently working as
%expertWork%, while a %nonExpert% is working as %nonExpertWork%. Production would be greater if the
colonists swapped jobs"*, *"%location% would benefit from exploration"*, *"All requirements are
met"*. (FreeCol's Javadoc calls it "the Advanced Colony Report"; its menu label is "Requirements".)

Col1 never advised you — noticing that your Master Carpenter was stuck farming *was* the game. So
even as an optional extra this one deserves its own decision, separate from the other three: it does
not aggregate the original's information, it plays part of the original's game for you.

## What building any of these needs

1. **Backdrop art — the real constraint.** These have no `REPORTn.PIK` of their own. Options: the
   spare `REPORT9` (a `REPORT1` duplicate, so it reads as native-scout art — wrong for all four);
   reuse a thematically close backdrop as `REPORT6`/`REPORT7` are already double-booked; or paint
   nothing and let the dimmed frame carry it. Needs a look decision before code.
2. **Gate them behind a client option.** A player who chose `--classic` for fidelity should be able
   to not have these. FreeCol already has per-report client options (e.g.
   `clientOptions.messages.labourReport.compact`), so there is precedent for the pattern.
3. **Localized captions** in the isolated `classic.report.*` block of
   `FreeColMessages[_de].properties`, as the shipped reports do.
4. **Menu accelerators that do not collide** with the original's key scheme — these are *additions*,
   so they must not take a key Col1 used for something else.
