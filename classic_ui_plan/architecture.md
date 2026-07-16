# Architecture — why this is feasible, and the standing decisions

Background for newcomers to the project; settled, and rarely needs re-reading once you are working.
The *how it works* detail lives next to the code in
[`src/net/sf/freecol/client/gui/classic/README.md`](../src/net/sf/freecol/client/gui/classic/README.md).

## Why a second UI is cheap

FreeCol is cleanly layered: **model** (`common/model/`, UI-agnostic, server-authoritative),
**controllers** (`client/control/`, reach the view only via `getGUI().<method>`), and the **view
facade** `client/gui/GUI.java` — a concrete base class with **0 abstract methods, no-op stubs**, used
as-is in headless mode. So a new UI = another `GUI` subclass: unoverridden methods no-op, **the app
runs from day one**, and screens light up incrementally. Selection point (`FreeColClient.java`):
`gui = headless ? new GUI(this) : classic ? new ClassicGUI(this) : new SwingGUI(this)`.

## Decisions

**Swing**, as `ClassicGUI extends GUI` in a new `client/gui/classic/` package (the whole client is
Swing/AWT-bound; a rectangular-tile map is trivial in `Graphics2D`). **Additive changes only** — new
package + one-line selector change + a `--classic` flag — keeps us mergeable with upstream `master`.

## Reuse map

| Layer | Reuse |
|---|---|
| `common/model` + `client/control/*` + networking/options/resources | 100% — read state / drive controllers directly |
| `client/gui/action/` | high — the `FreeColAction`s, reused for the menu bar + order buttons |
| `client/gui/mapviewer/` | partial — image-selection logic; isometric projection replaced by our rectangular one |
| `client/gui/panel/` + `dialog/` | optional — we reskin our own or delegate as a stopgap |

## ⚠️ `data/mods/classic_ui/` is a false friend

It is "classic *FreeCol*" (an old parchment theme), **not** classic *Colonization*. Not the design
target; the expert's original-game screenshots + our own panels are. (Base order-button icons come
from `data/base`, not this mod.)
