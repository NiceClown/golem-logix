# tools: Golem Logix development tools

None of this goes into the release jar.

## Graphics (everything is drawn by code)

| file | what it does |
|---|---|
| `ArtForge.java` | entry point: draws the first-generation textures (core, array, terminals, caps, book GUI, screen animations), calls `Models` and `Forge2`, then `Logo` |
| `Paint.java` | pixel graphics library: a canvas in "block units" (1/16), palettes, bevel, grain, gears, gauges, runes, frost |
| `Forge2.java` | second generation: the 2×2 cabinet (front, side and display textures, models from `BigModel`), the cabinet's dynamic textures |
| `BigModel.java` | a big model in "cabinet" coordinates (2x2) that is cut into block cells; `Big3` does the same for 3x3x2 |
| `Buses.java` | the three buses: a compact body, the working node, a star on top, sockets on every side, mini tubes (multipart by the `north..down` flags) |
| `Terminals.java` | analog terminal, crafting terminal, encoder: fronts, sides, backs, roofs and models with protrusions |
| `CabBack.java` | the cabinet back: one 32x32 picture and a glowing relief |
| `Saturator.java`, `Big3.java` | Mana Saturator 3x3x2: front/back/sides, details, cutting the model into 18 cells, block states; the unassembled anvil |
| `Pipes.java` | pipes: glass, bands, hub, sleeves at devices, plugs, warehouse docks, block states |
| `Cassette.java` | one cassette drawing for the cabinet and for the item disk icons |
| `Items.java` | 32x32 item icons |
| `Logo.java` | `logo.png` for the mod list |
| `Models.java` | other models and block states |
| `ModelPreview.java` | offline preview of JSON models without starting the game (`java -cp <out> ModelPreview out.png 0 name…`) |

Run: `powershell -File tools\devshots.ps1 art` (compiles all `tools/*.java` except `GeneratePdfDocument` and `Audit`, and runs `ArtForge` from the project root).

## Audit

`java tools/Audit.java` checks references from models and block states to textures, "orphaned" textures and models, texture sizes that are not
a multiple of 16 (they limit mip levels), matching ru/en translation keys and the keys Java refers to.

## Screenshot rig (`gradle runDevShots`)

The client (task `runDevShots`) creates a flat world itself and runs text scenarios from `run/devshots/queue/*.txt`. Shots are taken
from the game's own framebuffer (`Screenshot.grab`); the screen and the operating system input are not touched; sound is off.
The rig code is in `src/main/java/com/golemlogix/devtest` (not part of the release jar).

Scenario commands (one per line, `#` starts a comment):

```
cmd <server command>         run a command as the player (fill, setblock, kill …)
hook <name> <arguments>      scene builders and checks on the server (array, rig, bay, stock, fillnet, netput, netcheck, netfluid,
                             baycheck, buscheck, give, carry, giveall, mode, refresh, time, saturator, saturatorput, saturatorheld,
                             saturatorcheck, mainframe, catalog, provfill, anvilput, anvilstrike …; see DevScenes)
look x y z distance yaw pitch   put the camera at a distance from a point
cam x y z yaw pitch          put the camera exactly
wait N                       wait N ticks
shot name                    a shot into run/devshots/screenshots/name.png
reload                       reload resources (after `gradle processResources`)
use x y z                    right-click on a block (opens its window)
useheld x y z                right-click on a block with the held item, like a player
attack x y z                 left-click on a block, like a player
uiclick list index|name:part button   a click on the terminal list inside the screen
uiat x y button              a click at a point of the open container window
uiscroll x y steps           the mouse wheel at a point of the open window
slotclick slot button        a click on a slot of the open window
craftreq item amount         an autocraft order (like the "Order" button)
planner item amount          open the order window with the planner over the open terminal
book id [spread] [section]   open the grimoire at an entry
bookaudit                    check the layout of all book entries in the current language
inv / closeScreen            the inventory window / close the window
ghost start full|tower|saturator | clear, key Y|N     ghost build and an in-game key press
check name expected          a check on the client (carried, inventory, ghostActive, ghostLocked, conflictClicks)
gui on|off, fov N, lang code, log text, quit
```

The scenario result is `name.log` next to it (`OK …` / `ERR …` lines). Ready scenarios live in `tools/shots/`
(`e2e1.txt`, `e2e2.txt` are end-to-end checks of the terminal, autocrafting, cabinet, bus and bucket; `ghost1.txt` checks the Y/N key priority).
A Java change needs a client restart; resources (textures, models, translations, the book) only need `processResources` and `reload`.
