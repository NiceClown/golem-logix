# Golem Logix

![Golem Logix Overview](https://media.forgecdn.net/attachments/2270/619/00-golem-logix-preview-v215-gif.gif)

**Cold, occult, analog logistics.** An autonomous storage and autocrafting network built not from modern electronic circuits, but from brass, copper, glass, and frosted ice — powered by golems, cryogenic logic, and Source.

Available for **Minecraft 26.1.2** (`NeoForge 26.1.2.x`, Java 25) and backported to **Minecraft 1.21.1** (`NeoForge 21.1.x`, Java 21).

[![Join the Golem Logix Discord](https://media.forgecdn.net/attachments/description/1735911/description_7e3c4489-7ccc-497d-81f6-a737df9ea2bb.png)](https://discord.gg/qJ8fvNzFkA)

---

## What is it?

Golem Logix gives you a full logistics network:

- **Storage:** cassette cabinets hold item and cryo-fluid disks, backed up by server-side storage.
- **Network:** frosted glass pipes carry resonant logistical channels; a tiny snowman runs inside them.
- **Buses & automation:** import, export, periscope storage, Mason's Formation, Hewer's Annihilation, and Golem Interfaces.
- **Terminals:** analog CRT terminals, crafting terminals, and wireless Portable Chronometers.
- **Autocrafting:** punch-card patterns, Cryo-Calculators, Cryo-Mainframes, Order Ledgers, and Recipe Monitors.
- **Magic-Powered Source, Cryo-Distillation & Thaumaturge:** powered by magical Source - **Neo Vitae** blood distilled into Cryo-Source Jars, Giga Jars, and Antigravity Reactors, **Thaumaturge** chunk Vis & Essentia condensed via the **Vis Condenser** and **Essentia Cryo-Cauldron** (with full Thaumaturge item aspects and Crucible crafting), or **Ars Nouveau** Source jars (required on 1.21.1; built-in integration activates automatically once ported to 26.1.2).

Everything is steampunk-and-frost: polished copper, CRT screens, vacuum tubes, violet runes, nixie counters, and animated golems everywhere.

## Features

### The Atlas Array

- **Three core stages:** the **Atlas Core** alone runs 8 channels through its bottom face; a starter tower (conduits + bushing) gives 16; the full **3×3×4 Atlas Array** features animated iron Atlants carrying the core on their shoulders.
- **Channels:** five Cable Bushings at the base give 5 lines × 64 = **320 channels**.
- **Expanders:** Channel Expanders and Quantum Channel Expanders in the walls raise capacity up to **1,600 channels** (or **2,000** with an Antigravity Reactor).
- **Ghost projection:** the in-game grimoire projects ghost blocks showing what to place where (`Y` confirms, `N` cancels).

![Atlas Array](https://media.forgecdn.net/attachments/description/1735911/description_c37a6066-36b0-4c33-920c-2a4a281ee979.gif)

### Storage & Disks

- **Copper Drive Bay:** a 2×2 cabinet with **10 cassette slots** and priorities.
    - Every disk is a cassette with a color-coded capacity label and fill LEDs.
    - Reels wind tape as it fills, and copper archivist golems carry crates along the shelves.
- **Disks:** item disks and cryo-fluid disks from 1k to 256k. Contents are stored server-side by UUID so stacks never bloat.
    - Equipment (worn, enchanted, or customized items) counts as 4,000 pieces per disk (`1k` and `4k` disks still hold one item); tools, armor, and items in `#golem_logix:not_gear` rules are explained right in the grimoire.

![Copper Drive Bay and Disks](https://media.forgecdn.net/attachments/description/1735911/description_98ed4d96-3595-482d-8198-e2000dfe1f89.gif)

- **Periscope Storage Bus:** turns any chest, barrel, or fluid tank into prioritized network storage with deny lists and fuzzy matching.

![Periscope Storage Bus](https://media.forgecdn.net/attachments/description/1735911/description_67155ac0-ef11-43c1-97d0-beca5e84003d.gif)

- **Copper Tank:** a glass-enclosed copper tank holding 16,000 mB (craft multiple together up to 1,024,000 mB), retaining contents when broken, plus sneak-right-click bucket mode.

![Copper Tank](https://media.forgecdn.net/attachments/description/1735911/description_806f3473-fdb2-4190-8e8f-b5226149357a.png)

### Frost Pipes & Channel Management

- **Basic pipe:** 16 channels. **Dense pipe:** 64 channels. **Pipe Plugs** isolate adjacent runs.
- **Channel routing:** channels route from the core along the shortest path (dense pipes first), with Jade tooltips (`Device Online`, `Device Missing Channel`, `X of Y Channels`).
- **Glow:** pipes emit soft non-light glow layers that work cleanly alongside shaders.

![Frost Pipes and Channel Routing](https://media.forgecdn.net/attachments/description/1735911/description_ce6d9e0a-40bf-42e4-88db-86196dd64f57.gif)

- **Channel Counter:** three nixie tubes on a walnut base fixed directly to a pipe, bushing, or device; shows live channel load (or capacity on right-click) without using a channel.

![Channel Counter](https://media.forgecdn.net/attachments/description/1735911/description_526d4e3b-ea3c-4a68-b02a-331c2fb81137.png)

- **Cryo-Diode:** directional subnet isolator passing Liquid Cryo-Source across networks without merging their channel topologies.

![Cryo-Diode](https://media.forgecdn.net/attachments/description/1735911/description_8450bfe4-6b1a-4561-82cf-ceb41944acaa.gif)

### Cryo-Source Telegraph & Security

- **Cryo-Source Telegraph:** a 2×2 relay with a three-block mast linking distant outposts across the base network (200 blocks, whole dimension with an **Aether Resonance Core**, or cross-dimension with the **Void-Glass Prism**).

![Cryo-Source Telegraph](https://media.forgecdn.net/attachments/description/1735911/description_126a935e-e450-403b-a8b8-df78ae3b8194.gif)

- **Security Terminal & Identity Punch Card:** per-player network permissions (`Insert`, `Extract`, `Craft`, `Build`) guarding all connected blocks and terminals in multiplayer, with a CRT sweep display.

![Security Terminal and Identity Punch Card](https://media.forgecdn.net/attachments/description/1735911/description_49a49223-375c-418f-8d17-3d9d4b657a22.gif)

### Buses & Interfaces

- **Vacuum Import Bus**, **Injector Export Bus**, **Periscope Storage Bus**, **Mason's Formation Bus** (places blocks, with a **Replace / Respect fluids** toggle for re-freezing melted Ice under Essentia Cryo-Cauldrons), and **Hewer's Annihilation Bus** (breaks/interacts with blocks using a held tool or iron-tier strength; upgradeable with **Plasma Temper** for Netherite-tier harvesting plus +1 Fortune and +1 Looting).
- **Upgrades & Pacing:** buses hold up to **28 Compressed Co-Processors** in a single stack slot (up to 128 items/tick), plus Autocraft Modules, Redstone Modules (`Ignore`, `High`, `Low`, `Pulse`), and Fuzzy Cards (match any NBT/components or a chosen tag). Self-pacing buses sleep when idle and admins can monitor or cap server impact with `busMaxMillisPerTick` and `/golemlogix_buses`.

![Vacuum Import and Injector Export Buses](https://media.forgecdn.net/attachments/description/1735911/description_cbc7e9a5-cfca-4aab-9029-5b6e493a5db7.gif)

![Mason's Formation and Hewer's Annihilation Buses](https://media.forgecdn.net/attachments/description/1735911/description_4fc7ab21-25e9-4d7a-8973-70e3a79c9d8c.gif)

- **Golem Interface:** keeps stocked items and fluids, pushes incoming items/fluids directly to storage without throttling, and supports fuzzy filters and redstone pulse mode.

![Golem Interface](https://media.forgecdn.net/attachments/description/1735911/description_5292753b-7f93-4e06-8bd5-2ab7ef622e69.gif)

### Terminals & Wireless

- **Analog CRT Terminal & Crafting Terminal:** unified searchable item + fluid list (`@mod`, `#tooltip`), sorting, fluid bucket filling/pouring, resizable window heights, JEI `R`/`U`/`+` recipe transfer, and **Ctrl + "+"** precrafting.

![Analog CRT and Crafting Terminals](https://media.forgecdn.net/attachments/description/1735911/description_6aafbfb5-9171-4413-aa65-f719b2f1147d.gif)

- **Portable Chronometer:** wireless crafting terminal in three range tiers (up to cross-dimension), **Ctrl+E** hotkey, Curios slot support, and a tiny animated Wither on its CRT screen. Recharged automatically through the **Eternal Frost Resonator**.

![Portable Chronometer and Eternal Frost Resonator](https://media.forgecdn.net/attachments/description/1735911/description_85dcdabe-13d0-4156-b73d-9d90328d193c.gif)

![Portable Chronometer GUI](https://media.forgecdn.net/attachments/description/1735911/description_19f942ff-a46a-4413-afa0-a88642f626a8.png)

### Autocrafting

- **Punch Card Catalog:** a 3×2×2 archival filing cabinet housing punch-card patterns with sliding drawers and a live CRT search screen.

![Punch Card Catalog](https://media.forgecdn.net/attachments/description/1735911/description_fb72d807-6b92-45c6-8b34-cf3525a71364.gif)

- **Cryo-Calculator:** plans arbitrarily large dependency trees cleanly (including cyclic patterns and catalysts), with idle calculators lending steps to busy jobs. **Cooling Co-Processors** beside calculators pump like flywheels for parallel speedup.

![Cryo-Calculator and Cooling Co-Processors](https://media.forgecdn.net/attachments/description/1735911/description_8345b536-64a2-460b-a101-016fbc856dd0.gif)

- **Cryo-Mainframe:** multi-tier server rack multiblock framed by iron Atlants for high-throughput parallel autocrafting.

![Cryo-Mainframe](https://media.forgecdn.net/attachments/description/1735911/description_a6ab0cbc-f36b-40bd-9dc5-baf019222f1b.gif)

- **Pattern Encoder, Pattern Provider & Recipe Monitor:** encodes crafting and processing recipes (with fluids in mB and fuzzy tag matching), feeds machines, retrieves results through return slots, and monitors active jobs with optional typewriter bells on completion and warning ticks for blocked jobs.

![Pattern Bench and Pattern Encoder](https://media.forgecdn.net/attachments/description/1735911/description_80fbc3b0-eac3-47a1-b738-a568f6958820.gif)

![Pattern Provider and Recipe Monitor](https://media.forgecdn.net/attachments/description/1735911/description_f8170c70-0847-444d-93af-57c85ebc3eca.gif)

- **Order Ledger (Stock Requester):** a brass-and-copper ledger with 9 target stock slots and a copper golem clerk that automatically maintains target item/fluid stock levels via network autocrafting.

![Order Ledger](https://media.forgecdn.net/attachments/description/1735911/description_9d5f5f8d-72a2-4586-87ff-49c0e5ea1976.gif)

- **Stock Manometer (Warden):** a clockwork redstone level emitter (Above, Below, Analog `0..15`, hysteresis, Autocraft Module trigger) with miniature redstone-carrying golems on copper bridges to neighbouring receivers.

![Stock Manometer](https://media.forgecdn.net/attachments/description/1735911/description_641dbe97-3607-49bf-b29e-163f1a807ed0.gif)

### Golem Rituals, Cryo-Distillation & Cryo-Mixer

- **Anvil Stamping & Plates:** drop anvils on damaged iron, copper, or snow golems, or forge by hand on an unassembled anvil. Supports datapack recipes (`golem_logix:saturator`, `golem_logix:anvil_crushing`).

![Anvil Stamping Ritual](https://media.forgecdn.net/attachments/description/1735911/description_fb4a8e40-f0aa-4315-9fa1-d8eda9b044bc.gif)

- **Mana Saturator:** 3×3×2 industrial forge where an iron golem smith hammers ingots into infused plates with up to five roof coolers.

![Mana Saturator](https://media.forgecdn.net/attachments/description/1735911/description_1fa44607-e18a-45aa-8cb0-68c17fb09d4c.gif)

- **Cryo-Distillation Tower & Cryo-Source Jars:** distills Essentia Vitae (or any `#golem_logix:cryo_distillable` fluid) into Source stored in Cryo-Source Jars, which automatically merge into **2×2 Giga Jars** (non-stacking -7% network Source consumption discount) and **3×3 Antigravity Reactors** (non-stacking +25% network channel capacity boost, e.g. 64 -> 80, 128 -> 160). Extra Giga Jars and Reactors still stack Source storage, and both bonuses work independently.

![Cryo-Distillation Tower and Cryo-Source Jars](https://media.forgecdn.net/attachments/description/1735911/description_2e8fd469-236d-4aad-946f-c3bb97306cb8.gif)

![Antigravity Reactor](https://media.forgecdn.net/attachments/description/1735911/description_5986760c-2b52-43c5-8521-92c3d0ead43e.gif)

- **Cryo-Mixer:** freezes Water and Liquid Cryo-Source into Snow or Ice blocks with automatic neighbour output.

![Cryo-Mixer](https://media.forgecdn.net/attachments/description/1735911/description_dd70b3e9-c4ed-41ce-bb7c-82ccf8903ff8.gif)

- **Thaumaturge Integration (Vis Condenser & Essentia Cryo-Cauldron):**
  - **Vis Condenser:** early two-block aura condenser where a goggled copper golem pulls ambient chunk Vis (`6 Vis/s -> 15 Source/s`) into adjacent Cryo-Source Jars and networked Giga Jars / Reactors, complete with a live brass manometer, configurable `%` / exact `Vis` stop floor + hysteresis, redstone pause, and `<10%` low-Vis Flux risk.
  - **Essentia Cryo-Cauldron:** forge a **Hollow Golem Head** in a Thaumaturge Crucible (`Flattened Iron Core` + `20 Vacuos` + `20 Gelum` + `15 Auram`, no research gate) to assemble a two-block iron-skull cauldron tended by an Archmage copper golem. Pipes `Auram` on the left and `Gelum` on the right (`1 Auram + 2 Gelum -> 40 Source`, 0 Flux, up to 28 Compressed Co-Processors) over **Ice** (`Blue Ice -> Packed Ice -> Ice -> Water`, easily automated with a Mason's Formation Bus); missing Ice or Gelum triggers Flux boil-off after a 2 s grace window unless paused by redstone.
  - Full Thaumaturge aspect coverage (`base_aspects.json`) for all Golem Logix items and blocks.

### Mod Integrations & Guide Book

- **Magic Power Integrations:** **Neo Vitae** (blood distillation via the Cryo-Distillation Tower), **Thaumaturge** (Vis Condenser, Hollow Golem Head Crucible recipe, Essentia Cryo-Cauldron, Cryo-Source Jars, and full item aspects - optional on 26.1.2), and **Ars Nouveau** (Source jars, Dispel rituals, Saturator Source Gems, and Blue Ice imbuement - **required on 1.21.1**, built-in and activates automatically once ported to 26.1.2).
- **Optional Integrations:** **Copper Age Backport** `0.1.5+` (recommended on 1.21.1 for early copper golems & statues; vanilla on 26.1.2), **Create** (Mixer, Press, and Sequential Assembly recipes on 1.21.1; built-in for 26.1.2 once ported), **Curios** (dedicated Portable Chronometer slot), **JEI**, and **Jade**.
- **Occult Grimoire of Logistics:** built-in illustrated, searchable research map with multiblock checklists and ghost projections in **English and Russian**.

## Requirements

Golem Logix is powered exclusively by magic mods — each Minecraft version requires its primary magic power mod on CurseForge:

- **Minecraft 26.1.2:**
    - **Recommended Magic Power (at least one):** **Neo Vitae** (provides *Essentia Vitae* for the Cryo-Distillation Tower & Cryo-Source Jars) or **Thaumaturge** (unlocks the Vis Condenser, Essentia Cryo-Cauldron, Crucible recipe, Cryo-Source Jars, and item aspects). *(Golem Logix also runs standalone in Free Mode without either.)*
    - **Required:** NeoForge `26.1.2.x`, Java `25`.
    - **Optional:** Curios, JEI, Jade. *(Copper golems & statues are vanilla on 26.1.2; Ars Nouveau and Create integrations are already built in and activate automatically once released for 26.1.2.)*
- **Minecraft 1.21.1:**
    - **Required:** NeoForge `21.1.x`, Java `21`, and **Ars Nouveau** (provides Source jars, Dispel rituals, Saturator Source Gems, and Blue Ice imbuement).
    - **Recommended:** **Copper Age Backport** `0.1.5+` (adds copper golems & statues for early anvil rituals and the Punch Card Catalog; optional if a modpack supplies `#golem_logix:copper_golems` or custom datapack recipes).
    - **Optional:** **Neo Vitae** (blood-to-Source cryo-distillation alongside Ars Nouveau), **Create** (Mixer/Press/Sequential Assembly recipes), Curios, JEI, Jade.

Install on both client and server.

## Getting Started

1. Craft the **Guide Book** (`Book` + `Copper Ingot`, shapeless).
2. Follow **Quick start** to forge your first plates, set up an Atlas Core + Cable Bushing + Copper Drive Bay + Terminal, and power the network with Source.

---

*Licensed under MIT. Source code and issue tracker on [GitHub](https://github.com/NiceClown/golem-logix) · Join our [Discord](https://discord.gg/qJ8fvNzFkA).*
