# Golem Logix

*Cold, occult, analog logistics for Minecraft 26.1.2 (NeoForge 26.1).*

The 1.21.1 version lives in the branch [`mc-1.21.1`](https://github.com/NiceClown/golem-logix/tree/mc-1.21.1) (critical fixes only).

A Refined Storage / AE2 style storage network built from brass, copper, glass and ice instead of circuits: a multiblock
**Atlas Array** powers the network, **cassette cabinets** store items and fluids, **frost pipes** carry the channels (watch the
snowmen run inside them), **buses** move items between chests and the network, **terminals** give access, and a **cryo calculator**
crafts things automatically from punch-card patterns. The whole network runs on **Source**: from Ars Nouveau jars, or distilled
from Neo Vitae blood by the **Cryo-Distillation Tower** into **Cryo-Source Jars** (without either mod
it simply runs for free); the late-game **Eternal Frost Resonator** keeps your Portable Chronometers charged through the aether.

Everything comes with an illustrated, searchable **guide book** (Russian and English) that remembers the page you left it on.

## Features

- **Atlas Core and Array** — the network grows with what stands under its core: the core alone gives 8 channels (through its bottom
  face), a starter tower (two conduits and a Cable Bushing under it) 16, and the whole Atlas Array, a 3x3x4 multiblock with five
  bushings at the base, a 64-channel line per bushing (320 total); Channel and Quantum Channel Expanders in the walls raise every
  line (up to 1600).
  The guide book can project ghost blocks showing what to place where (`Y` confirms, `N` cancels).
- **Drive Cabinet (2x2)** — one item, four blocks, **10 cassette slots** with a priority setting. Behind the glass every disk is a
  cassette with a colored label (color = capacity) and fill LEDs, reels spin and tape winds while the network is powered, copper
  archivist golems carry crates along the shelves.
- **Disks** — item disks and cryo fluid disks, 1k to 256k; contents are stored on the server, the disk itself only carries an id.
- **Frost pipes** — basic (16 channels) and dense (64 channels). Straight runs are smooth translucent tubes with brass bands, brass
  sleeves appear only where a pipe meets a device; a snowman with a pumpkin head and a crate runs through every connected pipe network.
- **Channels like AE2** — channels go from the core along the shortest routes, dense runs first; a device gets one only if every
  block on its route has room, and a device itself passes on as many as a basic pipe (16). Jade shows "Device Online", "Device
  Missing Channel" (and the full block that stopped it) and "X of Y Channels", with the numbers from the server. Pipe Plugs close
  a pipe's side to keep neighbouring lines apart.
- **Cryo-Source Telegraph** — a 2x2 relay with a three-block mast that links a distant outpost to the base network. Aetherial
  Linkage cards are written in one telegraph's Write slot (a whole stack at once) and read in the other's Memory slot; range cards reach the whole dimension
  or, with the secret Void-Glass Prism, any dimension. The golem operator keys Morse, the desk lamp blinks, pulses run up the mast.
- **Buses** — Vacuum Import Bus, Injector Export Bus and Periscope Storage Bus (turns any chest or tank into network storage, items and
  fluids). Compact bodies with a pipe socket on every side that grows into a mini tube next to a neighbour; filters, per-bus priorities.
  Upgrade slots: up to four speed upgrades, an Autocraft Module (the export bus orders what is missing), a Redstone Module and a
  Fuzzy Card (filters take the same item with any components, or any item of a tag chosen on the filter with a middle-click).
- **Redstone control** for buses, periscopes, pattern providers, calculators and the Mana Saturator: ignore / with signal / without
  signal / one action per pulse, as in AE2 and Refined Storage.
- **Terminals** — analog CRT terminal (search, sorting, items and fluids in one list with fluids counted in buckets, bucket support),
  crafting terminal, pattern encoder; animated screens; windows stretch to the full screen height; JEI R/U and "+" transfer.
- **Autocrafting** — punch-card patterns (with a **fuzzy mode** like RS and AE2: a crafting recipe takes what each cell accepts, a
  processing input takes any components or a chosen tag), a Cryo Calculator with Cooling Coprocessors around it and Compressed Co-Processors in its
  upgrade slots (it shows what it is making and its coprocessors pump like flywheels), pattern providers that feed machines (with a waiting mode), crafting tree planning with intermediates and a resource planner in the order window, precraft from
  JEI (Ctrl + "+"), "order ready" notifications and the **Recipe Monitor** to watch and abort every job of the network.
- **Rituals** — flattened plates by dropping an anvil on damaged iron / copper / snow golems, by hand on the **Mana Saturator** anvil,
  or with the assembled Saturator, a 3x3x2 multiblock where a small iron golem forges blocks into plates (roof coolers speed it up). A secret recipe opens after two trials.
  With Ars Nouveau, the first anvil plate opens the **Dispel method** (a battered golem's raw mind, pressed into two plates on an
  Enchanting Apparatus), and the assembled Saturator makes **Source Gems** (the Imbuement Chamber's recipes, twice as fast for a
  third more Source) and the Imbuement Chamber charges Blue Ice into cryo-substrates; with Create, a Mixer tears a block's mind in
  Liquid Cryo-Source and a Press makes a plate of it, and an assembly line (Spout, Deployers with an iron sheet and a popped chorus fruit,
  Press, three passes) finishes Compressed Co-Processors. The Saturator (purpur and End rods: after the End) stays the cheapest way to
  make plates; before it they come from golems, the Dispel or Create's mixer.
- **Liquid Cryo-Source and fluid autocrafting** — the network's own fluid, made from Source when an Export Bus, a Pattern Provider
  or an Interface pumps it out (no bucket, never stored). Patterns can have fluid ingredients in mB (JEI "+" carries them over), and
  the Pattern Provider pours them into the machine together with the items.
- **Cryo-Mixer** — a two-block machine with two flasks that freezes water with Liquid Cryo-Source: **snow** fast (for snow golems
  and the plates for pipes) or **ice** slow (for Packed Ice and Cryo-Substrate). Up to four Compressed Co-Processors and a Redstone
  Module; fluids go in through either block, the tray gives the blocks to an Import Bus.
- **Extras** — Portable Chronometer (a brass handheld with a green screen where a Wither keeps firing at you; a wireless crafting
  terminal with its own Source charge; **Ctrl+E** opens it from anywhere on you, and with Curios it has its own Chronometer slot),
  Jade tooltips, JEI categories, server settings in `golem_logix-common.toml`.

## Requirements

- Minecraft 26.1.2, NeoForge 26.1.2.x (tested with 26.1.2.122), Java 25
- Copper golems and copper golem statues are the game's own: nothing else is needed for the rituals and the Punch Card Catalog
- Optional: Neo Vitae (Essentia Vitae for the Cryo-Distillation Tower and the Cryo-Source Jar), Curios (a Chronometer slot), JEI,
  Jade. Ars Nouveau (Source, the Dispel method, Source Gems in the Saturator, Blue Ice imbuement) and Create (mixer and press
  recipes, the Co-Processor assembly line) have no 26.1.2 builds yet: their integrations and recipes are in the mod and wake up
  once they are ported

## Installing

Put `golem_logix-<version>.jar` into the `mods` folder (client and server). On a dedicated server nothing else is needed.

## Quick start

1. Craft the **Guide Book**: a book and a copper ingot, shapeless.
2. Place the Atlas Core with a pipe or a device right under it (8 channels), or build the starting tower under it (bushing, two
   conduits, core: 16 channels), and put a Source jar against any block of the network.
3. Attach a Copper Drive Cabinet and a Terminal to the bushing, put a disk in the cabinet.

## Building from source

```
gradlew build              # build/libs/golem_logix-26.1.2-2.0.2.jar (JAVA_HOME must point at JDK 25)
gradlew runGameTestServer  # 109 game tests on a real server (add -PtestMods for JEI, Jade, Curios; -Pvitae for Neo Vitae)
```

Art (textures and block models) is generated by code: compile `tools/*.java` (except `Audit.java`) and run `ArtForge` from the
project root, or `powershell -File tools\devshots.ps1 art`. See `tools/README.md` for details. `gradle runDevShots` starts a development client that
takes screenshots from the game itself (`run/devshots`); that harness is not part of the release jar.

## For modpacks

The rituals are data, so a modpack can change or add them with a data pack or KubeJS:

- `golem_logix:saturator` — the Mana Saturator: `ingredient`, `result`, optional `hand_result` (forging by hand on the anvil),
  `source` (default 1000), `ticks` without coolers (default 100), `secret` (hidden in JEI until the secrets are known).
- `golem_logix:anvil_crushing` — a falling anvil on a battered golem: `entity` (an id, a `#tag` or a list), optional
  `entity_id_contains` (any mob whose id contains a word), `min_health` (default 0.5), `max_health`, `result`, optional `icon` for JEI.
- Tags: `golem_logix:copper_golems` (entity types that count as copper golems, `minecraft:copper_golem` by default),
  `golem_logix:copper_golem_statues` (blocks for the Punch Card Catalog, the game's statues by default), `golem_logix:iron_plates` (the Forge Hammer's centre: the Flattened Iron Core and any `c:plates/iron`).
- Fluid tag `golem_logix:cryo_distillable`: the blood the Cryo-Distillation Tower turns into Source (Essentia Vitae by default; add
  another mod's blood to use it). The rate and the jar size are in the config (`distillation`).

The defaults live in `data/golem_logix/recipe/saturator` and `data/golem_logix/recipe/anvil_crushing`. Ingredients use the
26.1 format: an item id, a `#tag` or a list of ids.

## Translations

English and Russian: every block, item and message, and the whole guide book. When you change player-facing text, update both
`en_us` and `ru_ru` (the lang files and `assets/golem_logix/book/*.json`); the `bookDataIsValid` game test checks that both books
have the same structure.

## Credits and license

MIT, see `LICENSE`. The copper golem puppets use the game's own copper golem model and textures.

The channel system follows the design of [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)
by AlgorithmX2 and TeamAppliedEnergistics (its code is LGPL-3.0): channels are handed out from the controller along the shortest
routes with dense cables first, a device carries as much as a normal cable, and the Jade tooltips use the same wording
("Device Online", "Device Missing Channel", "X of Y Channels"). Golem Logix contains no AE2 code: `system/ChannelCalculation`
is an independent implementation written for this mod's blocks, so the mod stays under MIT. Thanks to the AE2 team for the
reference design.

