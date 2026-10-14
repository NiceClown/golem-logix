# Golem Logix

**Cold, occult, analog logistics.** A storage and autocrafting network in the spirit of Refined Storage and AE2, built not from circuits but from brass, copper, glass and ice, and powered by golems and Ars Nouveau Source.

---

## What is it?

Golem Logix gives you a full logistics network:
- **Storage:** cassette cabinets hold item and fluid disks.
- **Network:** frost pipes carry the channels; a tiny snowman runs inside them.
- **Buses:** move items and fluids between chests and the network.
- **Terminals:** give access to everything.
- **Autocrafting:** cryo-calculators craft on their own from punch-card patterns.

Everything is steampunk-and-frost: polished copper, CRT screens, vacuum tubes, violet runes and animated golems everywhere.

The whole network runs on **Source** from Ars Nouveau. Without Ars Nouveau it simply runs for free.

## Features

### The Atlas Array
- **The heart of your network:** a 3×3×4 multiblock monument. Four iron Atlants carry a crown with the Atlas Core on their shoulders.
- **Channels:** five Cable Bushings at the base give 5 lines × 64 = **320 channels**.
- **Expanders:** Channel Expanders and Quantum Channel Expanders in the walls raise that up to **1600**.
- **Starter tower:** a small tower of core, conduits and bushing gives 8 channels to get going.
- **Ghost projection:** the guide book can project the build, showing what to place where.

### Storage
- **Copper Drive Bay:** a 2×2 cabinet with 10 cassette slots.
  - Every disk is a cassette with a colored label and fill LEDs.
  - Reels wind tape as it fills, and copper archivist golems run along the shelves.
- **Disks:** item disks and cryo fluid disks from 1k to 256k, with no type limit. Contents are stored server-side.
- **Periscope Storage Bus:** turns any chest, barrel or tank into network storage.
- **Priorities:** set them on cabinets, periscopes and buses.

### Frost pipes
- **Basic pipe:** 16 channels. **Dense pipe:** 64 channels.
- **Junctions:** brass cage hubs, device sleeves, and a reducer washer where the two sizes meet.
- **Glow:** pipes glow softly while powered. This is visual only: they give no light, so mobs still spawn.
- **Channels like in AE2:** they go from the core along the shortest routes, dense pipes first. A device passes on as many as a basic pipe (16).
- **Jade:** "Device Online", "Device Missing Channel" (with the full block that stopped it), "X of Y Channels" on pipes and bushings.
- **Pipe Plugs:** close one side of a pipe to keep neighbouring lines apart.

### Cryo-Source Telegraph
- **Outposts on your base network:** a 2×2 relay with a three-block mast. Link two telegraphs and the far one joins your base network, Atlas Core and all.
- **Linking cards:** write **Aetherial Linkages** in one telegraph's **Write** slot (a whole stack at once), put one into each outpost's **Memory** slot. The two slots lock each other, and a big **RESET** button voids every card written for that telegraph.
- **Range like the Chronometer's:** 200 blocks, the whole dimension with the **Aether Resonance Core**, any dimension with the secret **Void-Glass Prism**.
- **A living machine:** Source strikes the crystal on the mast, a golem operator keys Morse, the desk lamp blinks with every press and blue pulses run up the mast.

### Buses
- **Vacuum Import Bus** and **Injector Export Bus**, with item and fluid filters and priorities.
- **Six upgrade slots:**
  - up to four **Compressed Co-Processors** (speed, up to 8× per move, every tick);
  - an **Autocraft Module** (the export bus orders what the network lacks);
  - a **Redstone Module**.
- **Redstone modes for every machine**, like AE2 and RS: ignore, with signal, without signal, one action per pulse.

### Terminals
- **Analog CRT Terminal:**
  - search by name, `@mod` or `#tooltip`;
  - sorting;
  - items and fluids in one list, with bucket filling and pouring.
- **Crafting Terminal:** a 3×3 grid that refills itself from the network.
- **Window size:** normal, tall or full screen.
- **JEI:** R/U on list entries and "+" recipe transfer. **Ctrl + "+"** orders all missing ingredients (precraft).

### Autocrafting
- **Pattern Encoder:** punches crafting and machine recipes onto copper punch cards.
- **Pattern Provider:** feeds machines, with an optional "wait for result" mode.
- **Cryo-Calculator:**
  - plans the whole recipe tree, reserves materials and runs the steps;
  - speeds up with Cooling Co-Processors next to it, which pump like flywheels, and with Compressed Co-Processors in its slots;
  - while working, a miniature of the current item spins above it, ice hills rise and sink, and "matrix rain" runs down its sides.
- **Recipe Monitor:** a brass cabinet with a swirling portal where punch cards orbit for every running job. Its window shows and aborts every job in the network.
- **"Order ready" notification** when your craft is done.
- **Fluids in patterns:** JEI "+" carries fluid ingredients over, and the Pattern Provider pours them into the machine together with the items.
- **Liquid Cryo-Source:** the network's own fluid, made from Source on the spot. Only automation pumps it out (Export Bus, Pattern Provider, Interface); no bucket can hold it.

### Golem rituals
- **Anvil plates:** drop an anvil on a wounded iron, copper or snow golem to get flattened plates, the crafting base of the mod.
- **Mana Saturator:** a 3×3×2 multiblock forge where a small iron golem hammers blocks into plates.
  - Up to five roof coolers speed it up.
  - Network connectors on every side.
  - It offers you a poppy when idle.
- **Forge Hammer:** forge plates by hand on the unassembled Saturator, the anvil.
- **With Ars Nouveau:** after your first anvil plate, a Dispel unbinds a battered golem's mind; an Enchanting Apparatus presses it into two plates.
- **Source Gems:** with Ars Nouveau the assembled Saturator makes Source Gems and their blocks, twice as fast as the Imbuement Chamber for a third more Source.
- **With Create:** a Mixer tears a block's mind in Liquid Cryo-Source, a Press turns it into a plate. An assembly line (Spout, Deployer, Press, three passes) finishes Compressed Co-Processors without keeping the Saturator busy.
- **Blue Ice imbuement:** with Ars Nouveau the Imbuement Chamber charges a Blue Ice block into two Cryo-Substrates: dear, but no golem or machine needed.
- **The Saturator stays king:** no other path makes plates cheaper.
- **Secret recipe:** it reveals itself only after two trials… find them yourself.

### Late game
- **Portable Chronometer:** a brass handheld device and wireless crafting terminal with three range tiers, up to cross-dimension.
  - It runs on its own Source charge.
  - **Ctrl+E** opens it from anywhere on you; with **Curios** it gets its own Chronometer slot.
  - On its green screen, a flat Wither keeps trying to shoot a skull right at you.
- **Eternal Frost Resonator:** sends Source through the aether to your Chronometers, so they never need charging.

### For modpacks
- The Saturator and anvil rituals are data-driven recipes (`golem_logix:saturator`, `golem_logix:anvil_crushing`): change or add them with a data pack or KubeJS.
- Any mod's copper golem works (tag `golem_logix:copper_golems`).

### Guide book
The **Occult Logistics Grimoire** is an illustrated, searchable research map:
- material checklists and ghost-block projections for every multiblock;
- recipes, tables and tips.

It's available in **English and Russian**.

## Requirements

- Minecraft **1.21.1**, NeoForge **21.1.x**
- Recommended: **Copper Age Backport** (copper golems for the first plates). A modpack may use another copper golem or its own recipes instead.
- Optional: **Ars Nouveau** (Source, the Dispel method, Source Gems, Blue Ice imbuement), **Create** (mixer and press recipes, the Co-Processor assembly line), **Curios** (a Chronometer slot), **JEI**, **Jade**

Works in singleplayer and on dedicated servers. Install on both client and server.

## Getting started

1. Craft the **Guide Book**: a book and a copper ingot, shapeless.
2. Open **Quick start**. It walks you from the first plates to your first terminal.

---

*The copper golem model and textures come from Copper Age Backport (CC0). The channel system follows the design of Applied Energistics 2 (no AE2 code is included). Licensed under MIT.*
