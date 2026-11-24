# Golem Logix

**Cold, occult, analog logistics.** A storage and autocrafting network in the spirit of Refined Storage and AE2, built not from circuits but from brass, copper, glass and ice, and powered by golems and Source.

Available for **Minecraft 26.1.2** (`NeoForge 26.1.2.x`, Java 25) and backported to **Minecraft 1.21.1** (`NeoForge 21.1.x`, Java 21).

---

## What is it?

Golem Logix gives you a full logistics network:
- **Storage:** cassette cabinets hold item and cryo-fluid disks, backed up by server-side storage.
- **Network:** frost pipes carry AE2-style channels; a tiny snowman runs inside them.
- **Buses & automation:** import, export, periscope storage, Mason's Formation, Hewer's Annihilation, and Golem Interfaces.
- **Terminals:** analog CRT terminals, crafting terminals, and wireless Portable Chronometers.
- **Autocrafting:** punch-card patterns, Cryo-Calculators, Cryo-Mainframes, Order Ledgers, and Recipe Monitors.
- **Source & Cryo-Distillation:** powered by Ars Nouveau Source jars or distilled from Neo Vitae blood into Cryo-Source Jars, Giga Jars, and Antigravity Reactors (runs free if neither Source mod is installed).

Everything is steampunk-and-frost: polished copper, CRT screens, vacuum tubes, violet runes, nixie counters, and animated golems everywhere.

## Features

### The Atlas Array
- **Three core stages:** the **Atlas Core** alone runs 8 channels through its bottom face; a starter tower (conduits + bushing) gives 16; the full **3×3×4 Atlas Array** features animated iron Atlants carrying the core on their shoulders.
- **Channels:** five Cable Bushings at the base give 5 lines × 64 = **320 channels**.
- **Expanders:** Channel Expanders and Quantum Channel Expanders in the walls raise capacity up to **1,600 channels**.
- **Ghost projection:** the in-game grimoire projects ghost blocks showing what to place where (`Y` confirms, `N` cancels).

### Storage & Disks
- **Copper Drive Bay:** a 2×2 cabinet with **10 cassette slots** and priorities.
  - Every disk is a cassette with a color-coded capacity label and fill LEDs.
  - Reels wind tape as it fills, and copper archivist golems carry crates along the shelves.
- **Disks:** item disks and cryo-fluid disks from 1k to 256k. Contents are stored server-side by UUID so stacks never bloat.
  - Equipment (worn, enchanted, or customized items) counts as 4,000 pieces per disk (`1k` and `4k` disks still hold one item); tools, armor, and items in `#golem_logix:not_gear` rules are explained right in the grimoire.
- **Periscope Storage Bus:** turns any chest, barrel, or fluid tank into prioritized network storage with deny lists and fuzzy matching.
- **Copper Tank:** a glass-enclosed copper tank holding 16,000 mB (craft multiple together up to 1,024,000 mB), retaining contents when broken, plus sneak-right-click bucket mode.

### Frost Pipes & Channel Management
- **Basic pipe:** 16 channels. **Dense pipe:** 64 channels. **Pipe Plugs** isolate adjacent runs.
- **AE2-style channel routing:** shortest path from the core, dense routes first, with Jade tooltips (`Device Online`, `Device Missing Channel`, `X of Y Channels`).
- **Channel Counter:** three nixie tubes on a walnut base fixed directly to a pipe, bushing, or device; shows live channel load (or capacity on right-click) without using a channel.
- **Cryo-Diode:** directional subnet isolator passing Liquid Cryo-Source across networks without merging their channel topologies.
- **Glow:** pipes emit soft non-light glow layers that work cleanly alongside shaders.

### Cryo-Source Telegraph & Security
- **Cryo-Source Telegraph:** a 2×2 relay with a three-block mast linking distant outposts across the base network (200 blocks, whole dimension with an **Aether Resonance Core**, or cross-dimension with the **Void-Glass Prism**).
- **Security Terminal & Identity Punch Card:** per-player network permissions (`Insert`, `Extract`, `Craft`, `Build`) guarding all connected blocks and terminals in multiplayer, with a CRT sweep display.

### Buses & Interfaces
- **Vacuum Import Bus**, **Injector Export Bus**, **Periscope Storage Bus**, **Mason's Formation Bus** (places blocks), and **Hewer's Annihilation Bus** (breaks/interacts with blocks using a held tool or iron-tier strength; upgradeable with **Plasma Temper** for Netherite-tier harvesting plus +1 Fortune and +1 Looting).
- **Upgrades & Pacing:** buses hold up to **28 Compressed Co-Processors** in a single stack slot (up to 128 items/tick), plus Autocraft Modules, Redstone Modules (`Ignore`, `High`, `Low`, `Pulse`), and Fuzzy Cards (match any NBT/components or a chosen tag). Self-pacing buses sleep when idle and admins can monitor or cap server impact with `busMaxMillisPerTick` and `/golemlogix_buses`.
- **Golem Interface:** keeps stocked items and fluids, pushes incoming items/fluids directly to storage without throttling, and supports fuzzy filters and redstone pulse mode.

### Terminals & Wireless
- **Analog CRT Terminal & Crafting Terminal:** unified searchable item + fluid list (`@mod`, `#tooltip`), sorting, fluid bucket filling/pouring, resizable window heights, JEI `R`/`U`/`+` recipe transfer, and **Ctrl + "+"** precrafting.
- **Portable Chronometer:** wireless crafting terminal in three range tiers (up to cross-dimension), **Ctrl+E** hotkey, Curios slot support, and a tiny animated Wither on its CRT screen. Recharged automatically through the **Eternal Frost Resonator**.

### Autocrafting
- **Pattern Encoder, Pattern Provider & Punch Card Catalog:** encodes crafting and processing recipes (with fluids in mB and fuzzy tag matching), feeds machines, and retrieves results through return slots.
- **Cryo-Calculator & Cryo-Mainframe:** plans arbitrarily large dependency trees cleanly (including cyclic patterns and catalysts), with idle calculators lending steps to busy jobs. **Cooling Co-Processors** beside calculators pump like flywheels for parallel speedup.
- **Order Ledger (Stock Requester):** a 3×3 brass-and-copper keeper with a copper golem clerk that automatically maintains target item/fluid stock levels via network autocrafting.
- **Stock Manometer (Warden):** a clockwork redstone level emitter (Above, Below, Analog `0..15`, hysteresis, Autocraft Module trigger) with miniature redstone-carrying golems on copper bridges to neighbouring receivers.
- **Recipe Monitor & Mechanical ASMR:** inspect or cancel active jobs across the network, with optional typewriter bells on job completion and warning ticks for blocked jobs.

### Golem Rituals, Cryo-Distillation & Cryo-Mixer
- **Plates & Rituals:** drop anvils on damaged iron, copper, or snow golems, forge by hand on an unassembled anvil, or build the **3×3×2 Mana Saturator** multiblock with roof coolers. Supports datapack recipes (`golem_logix:saturator`, `golem_logix:anvil_crushing`).
- **Cryo-Distillation Tower & Cryo-Source Jars:** distills Essentia Vitae (or any `#golem_logix:cryo_distillable` fluid) into Source stored in Cryo-Source Jars, which automatically merge into **2×2 Giga Jars** (+7% tower bonus) and **3×3 Antigravity Reactors**.
- **Cryo-Mixer:** freezes Water and Liquid Cryo-Source into Snow or Ice blocks with automatic neighbour output.

### Mod Integrations & Guide Book
- **Optional integrations:** Ars Nouveau (Source & Dispel rituals), Neo Vitae (blood distillation), Create (Mixer/Press/Sequential Assembly), Curios (Chronometer slot), JEI, Jade. *(Ars Nouveau and Create integrations activate automatically when those mods are present on supported MC versions.)*
- **Occult Grimoire of Logistics:** built-in illustrated, searchable research map with multiblock checklists and ghost projections in **English and Russian**.

## Requirements

- **Minecraft 26.1.2:** NeoForge `26.1.2.x`, Java `25` (copper golems & statues are vanilla).
- **Minecraft 1.21.1:** NeoForge `21.1.x`, Java `21` (optional **Copper Age Backport** `0.1.5+` recommended for early copper golems, or configure `#golem_logix:copper_golems` / datapack recipes).

Install on both client and server.

## Getting Started

1. Craft the **Guide Book** (`Book` + `Copper Ingot`, shapeless).
2. Follow **Quick start** to forge your first plates, set up an Atlas Core + Cable Bushing + Copper Drive Bay + Terminal, and power the network with Source.

---

*Licensed under MIT. The channel system follows the design of Applied Energistics 2 (no AE2 code is included). Source code and issue tracker on [GitHub](https://github.com/NiceClown/golem-logix).*
