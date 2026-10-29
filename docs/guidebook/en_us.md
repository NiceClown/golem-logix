<!--
  Golem Logix guide book (English). This file is the book's source: the build turns it into
  assets/golem_logix/book/en_us.json. See docs/guidebook/README.md for the format.
-->

# Occult Logistics Grimoire

## Basics
<!-- {"id": "basics", "icon": "golem_logix:guide_book", "color": "38d9d6"} -->
> where it all begins

### Introduction to Golem Logix
<!-- {"id": "intro", "icon": "golem_logix:guide_book", "x": 0, "y": 0, "gold": true} -->

#### Cold, copper and golems

Golem Logix is the science of making the world store, remember and craft things on its own. Nothing electric here: the turn of the twentieth century, polished copper, brass, vacuum tubes, punch cards - and the icy breath of snow golems.

The mod gives you **network storage** (disks and cabinets), **terminals** to reach it, **buses** that haul items between chests and the network by themselves, and **autocrafting** on punch cards.

##### The triad of golems

- **Iron** - the heart: the network core, the colossal Atlants and the Saturator's smith.

- **Copper** - the hand: buses, disks, punch cards.

- **Snow** - the cold: pipes, channels, cryo calculators.

#### How this book works

Tabs on the left are sections. On the map every article is a node; the lines show what connects to what and in which order to read. New articles pulse white.

Drag the map with the mouse and zoom it with the wheel. The search box at the top looks through every article (press **Tab** or **/**).

Inside an article flip the spread with the arrows, the mouse wheel or **← →**. **Backspace** or **Esc** returns to the map.

If you want the road from zero to your first terminal, open **Quick start**.

### Source - the only energy
<!-- {"id": "source", "icon": "golem_logix:creative_source_jar", "x": 1, "y": 0, "parents": ["intro"]} -->

#### Powering the network

There is no RF or FE here: the network runs on magical **Source** - the same power that flows in runes, blood and gems. A source can be:

- a **Cryo-Source Jar**: a Cryo-Distillation Tower fills it with Source frozen out of blood (Neo Vitae, see “Cryo-Distillation”),

- a Source Jar from Ars Nouveau (or any of its machines holding Source),

- the **Creative Source Jar** - infinite, for testing.

The source must touch **any block of the network** (a bushing, bus, pipe, cabinet...).

*The Eternal Frost Source Resonator does not feed the network: it sends Source through the aether to Portable Chronometers (see “Chronometer and late game”).*

#### What it costs

Once a second the **Atlas Core** takes from its sources:

**1 Source** for the core itself and **1/4 Source** for every used channel (rounded up).

Example: 40 used channels cost 1 + 10 = 11 Source per second.

An infinite source never runs dry. When Source runs out the core goes dark and the whole network falls asleep: terminals do not open, buses stop, calculators halt. When the flow returns, everything starts again by itself.

Every autocrafting step costs **2 Source** too.

*If the world has neither Neo Vitae nor Ars Nouveau, there is nowhere to get Source: then the network, the Saturator, the hammer and the Chronometer work for free.*

### Quick start
<!-- {"id": "quickstart", "icon": "golem_logix:analog_terminal", "x": 2, "y": 0, "parents": ["source"], "gold": true} -->

#### The first plates

Nearly everything here is made of three **plates**. The first come from the **anvil ritual**: wound a golem to 4 HP and drop an anvil on it.

- an iron golem gives a **Flattened Iron Core**,

- a copper golem a **Flattened Copper Matrix**,

- a snow golem a **Flattened Cryo-Substrate** (it has only 4 HP: one punch is enough).

The first network takes **4 cores and 6 matrices**.

*With Nether quartz comes the Mana Saturator: on its anvil the Forge Hammer forges plates by hand.*

#### Power

The network runs on **Source** alone. Put a supply against any block of the network:

- a **Cryo-Source Jar** - the Cryo-Distillation Tower fills it from Neo Vitae blood,

- an Ars Nouveau Source Jar,

- a **Creative Source Jar** for testing.

The core takes 1 Source a second, plus 1/4 per channel in use.

*No Neo Vitae or Ars Nouveau in the pack: it runs free.*

#### The first network

1. Place the **Atlas Core**. On its own it already runs **8 channels**, but it connects **only through its bottom face**.

2. Put a **Cable Bushing** under the core. Two **Atlas Conduits** between them make the starter tower: 16 channels.

3. Attach a **Copper Drive Bay** to the bushing and put a **1k Magnetic Disk** in.

4. Attach an **Analog CRT Terminal** to the bay or the bushing and right-click it.

Done: what you put into the terminal lies on the disk. Devices pass channels on to each other, so no pipes are needed at first.

*Right-click the core to see the channels in use and what to build next.*

#### What next

- The **Crafting Terminal** - a terminal with a crafting grid: craft straight from the network, JEI recipes with its "+".

- **Pipes** of cryo-substrate spread the network round the base: a basic one carries 16 channels, a dense one 64.

- **Buses**: import and export, the **Periscope** (a chest or a tank as storage), the golem buses that lay and mine blocks; fluids go into a **Copper Tank**.

- The **Atlas Array** - 320 channels instead of 16.

- **Autocrafting**: the Encoder, the Stamping Gateway, the Cryo-Calculator.

- Late game: the **Portable Chronometer** - the network in your pocket.

*The lines on the map lead from the simple to the complex.*

### Frozen Runes & Golem Will
<!-- {"id": "lore", "icon": "golem_logix:flattened_cryo_substrate", "x": 0, "y": 1, "parents": ["intro"]} -->

#### Cryogenic Aether Fixation

In classical sorcery **Source** is a warm, restless power. Runes in warm aether tremble like a candle flame: weave a hundred of them into one thought and the aether heats up, warps and scatters it.

Our breakthrough began with **snow golems**. Around them lies a field of perfect stillness: the aether near them cools and falls still.

In that cold, runes that carry the memory of how things are made **freeze into dense glyphs of ice**. A frozen rune no longer trembles: a grain of ice holds a whole chest. Along the frost pipes the glyphs glide faster than sound - and so the network reaches thousands of items at once without falling apart.

#### Mechanical Will

No human hand can hold thousands of frozen runes. That is what **golems** are for: flattened plates bind them together, and the master's will guides them.

A golem never tires and never doubts: it is the hand of whoever made it, reaching further.

With copper fingers the golems sort the frozen runes and stack them into lattices. A punch card sets the pattern, and in a split second golems and snowmen **build the finished item out of runes**.

*Source gives the strength, cold keeps the memory, and golems keep the order.*

#### Warm blood, cold Source

Rune jars are not the only keepers of Source. It flows in the blood of the living too - hot, murky, full of fear and heartbeat. The **Ara Vitae** altar (Neo Vitae) gathers it as **Essentia Vitae**.

The network cannot drink such power: living chaos would melt the frozen runes. So the blood is **frozen out**. In the Cryo-Distillation Tower the snow golems' cold takes its warmth and turmoil away, and pure, still **Cryo-Source** settles at the bottom of the column.

*Ice does not ask whose blood it was. It only makes it quiet.*

#### About this grimoire

This book is put together from the workshop's notebooks: drawings, channel sums, notes in the margins. Some pages are touched with frost - they were written beside a running array.

The workshop left no names. Only its rules remain: do not cripple a golem without need, do not let the cold out of the pipes, and never leave the core without Source.

*New articles pulse white on the map - they were added recently.*

### Glossary
<!-- {"id": "glossary", "icon": "minecraft:writable_book", "x": 1, "y": 1, "parents": ["intro"]} -->

#### Terms
<!-- {"type": "table", "widths": [50, 98]} -->

| Word | Meaning |
|---|---|
| Network | touching blocks |
| Node | a network block |
| Channel | a device's seat |
| Line | bushing channels |
| Array | 3x3x4 multiblock |
| Core | network brain |
| Disk | item storage |
| Pattern | punched recipe |

#### About the text

**Bold** marks block names and important numbers.

*Italics* are tips and remarks.

### Cryo-Distillation
<!-- {"id": "distillation", "icon": "golem_logix:cryo_distillation_tower", "x": 1, "y": -1, "parents": ["source"], "requires": "neovitae"} -->

#### Source from blood

Blood gathered by the **Ara Vitae** altar (Neo Vitae) - **Essentia Vitae** - carries Source, only hot and murky.

The **Cryo-Distillation Tower** freezes the living warmth and chaos out of it. Pure magic settles and sets as icy **Cryo-Source** - and that is what the network feeds on.

*If Ars Nouveau is there too, its Source Jars and Cryo-Source Jars feed the same network.*

#### How it works

Pour **Essentia Vitae** into any of the tower's three blocks: by pipes from an Ara Vitae, by network buses or by buckets. The tower holds **8000 mB**.

Every second it distils **20 mB** of blood into **30 Source** and pours it into the **Cryo-Source Jars** (or Source Jars) standing **right against it**: a jar even one block away gets nothing. When the jars are full it waits.

Put the jars against the plinth, on the same level as it: then a pipe runs from the tower's head over to each jar and drops into its vent. Any side will do - the copper golem on the tower moves to a free one.

A jar holds **10 000 Source**. Put it against any block of the network: a pipe docks onto it with a brass socket.

<!-- {"type": "crafting", "recipe": "golem_logix:cryo_distillation_tower"} -->

Three blocks high: a rune plinth, a glass column with the coil, a brass head under snow. A Tabula Rasa from the Ara Vitae seals the plinth.

<!-- {"type": "crafting", "recipe": "golem_logix:cryo_source_jar"} -->

Two blocks high. Right-click it to see how much Source it holds; a comparator reads its level.

## Golem Rituals
<!-- {"id": "rituals", "icon": "golem_logix:flattened_iron_core", "color": "e0b04a"} -->
> plates from iron, copper and ice

### Anvil ritual
<!-- {"id": "anvil", "icon": "minecraft:anvil", "x": 0, "y": 0, "gold": true} -->

#### A blow to logic

A golem lives as long as its body holds the Source put into it. In a healthy golem this inner matrix is firm; in a badly wounded one (4 HP or less) it cracks.

At that moment a falling anvil collapses the cracked matrix and presses the golem's strength into a dense plate.

The anvil must fall onto the golem from at least one block above its head.

*An anvil only hurts a healthy golem: while the matrix holds, the ritual does not work.*

#### Iron Golem
<!-- {"type": "ritual", "station": "minecraft:anvil", "inputs": ["minecraft:iron_block"], "outputs": ["golem_logix:flattened_iron_core"]} -->

> A golem with 1-4 HP (out of 100).

Cores go into the Atlas Core, casings, Atlants and calculators.

To bring 100 HP down to four, a harming potion, lava or a long fall helps.

#### Copper Golem
<!-- {"type": "ritual", "station": "minecraft:anvil", "inputs": ["minecraft:copper_block"], "outputs": ["golem_logix:flattened_copper_matrix"]} -->

> A golem with 1-4 HP (out of 12).

Matrices are the basis of disks, buses, punch cards and cabinets.

*A copper golem: a carved pumpkin on top of a copper block.*

#### Snow Golem
<!-- {"type": "ritual", "station": "minecraft:anvil", "inputs": ["minecraft:snow_block"], "outputs": ["golem_logix:flattened_cryo_substrate"]} -->

> A golem with 1-3 HP (out of 4).

A snow golem has only 4 HP. Hit it once with your hand - 3 are left and the anvil will now work (snowballs do not hurt snow golems).

Substrates go into pipes, cryo blocks and fluid disks.

### Mana Saturator
<!-- {"id": "saturator", "icon": "golem_logix:mana_saturator", "x": 1, "y": 0, "parents": ["anvil"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:mana_saturator"} -->

The head of a 3x3x2 multiblock, an alchemical forge. No living golem has to be crippled here: Source is poured straight into a plain block. No golem gets hurt, and you get twice the plates.

On its own the Saturator is a hand anvil. To make it forge by itself, build casings, an End Rod and a dense pipe around it.

#### Forging by hand

Until the machine is assembled, the Saturator is a **magic anvil**. Right-click puts an iron block, a copper block or packed ice on its face — **only these three** plates can be forged by hand.

Strike with the **Forge Hammer**: left-click the anvil. A strike takes **100 Source** from the hammer's charge; after **4 strikes** the blank becomes a plate.

By hand you get **one** plate. The multiblock around the anvil is its upgrade: the forging golem makes two plates and works on its own.

Right-click with an empty hand takes the result (or the blank back).

#### Materials list
<!-- {"type": "checklist", "variant": "saturator"} -->

Checked against your inventory. The button shows the ghost blocks: the centre from bottom to top is the dense pipe, the End Rod and the Saturator; the other 15 cells are Atlas Casings.

The ghosts follow the block you look at; **Y** locks them, **N** cancels them. The front of the machine faces you.

#### Assembly

The machine assembles itself as soon as every block is in place (checked once a second; right-clicking the Saturator tells you what is missing).

The **End Rod** is the base of the anvil and the **dense pipe** is the central crate of the front.

On assembly the blocks turn into one machine: Source columns, the forge chamber with its anvil, the crystal. Break any part and everything goes back to ordinary blocks.

##### Network connectors

A network pipe can come from **any side except the roof**: one connector on the front, one on the back, one on each side and one underneath. They are easy to spot - brass rings with a turquoise rim.

#### How it works

1. Right-click any part of the machine with a block in hand - it joins the queue on the **left rear deck** (up to four blanks of one kind).

2. The forge saturates the blank with pure Source (**1000** a ritual): the crystal weaves the magic into the metal, the smith hammers out the plates. Right-click with an empty hand to see the Source stock.

3. Over **five seconds** the smith strikes five blows, condensing magic into plates. The two copper carrier golems work **independently**: one takes the finished plates to the **right deck** while the other is already bringing the next blank from the queue — the smith hardly ever waits. The result is taken from the right deck (by hand, a bus or a Stamping Gateway).

##### The result is TWO plates

- Iron Block → 2 Flattened Iron Cores,

- Copper Block → 2 Flattened Copper Matrices,

- Packed Ice → 2 Flattened Cryo-Substrates.

And a Cooling Co-Processor gets squeezed into a **Compressed Co-Processor** - a speed upgrade for buses and calculators.

Source comes from the network through any connector (if the network has a core) or from jars next to the machine. Right-clicking with an empty hand takes the finished plates from the right deck (or, if it is empty, the queue from the left one). Buses and the Stamping Gateway work with the machine through any of its parts: they put blanks into the left deck's queue and take the finished plates from the right one.

*With nothing to forge, the golem just stands there offering you a poppy.*

#### Roof cooling

The roof of the machine has five **ventilation grilles**. Each can take a **Cooling Co-Processor**: hold one and right-click a grille **from above**. A radiator with a fan covers the grille.

Every co-processor speeds the smith up by **40%**. With all five the ritual runs three times faster — under two seconds, and the carriers still keep up.

Sneak and right-click the radiator with an empty hand to take it off. When the machine is dismantled the co-processors drop as items.

*The fans spin faster while the golem is at work.*

#### Redstone Module

The Saturator has no window, so the **Redstone Module** goes straight into the machine: right-click any part with the module.

Right-clicking again with a module switches the mode (with signal, without signal, per pulse, ignore). Any wall of the machine takes the signal.

Sneak and right-click the machine with an empty hand (not on a radiator) to take the module out.

#### Auto-output

Finished plates do not wait on the right deck: **auto-output** is **on by default**. Twice a second the machine hands them to a neighbouring **Stamping Gateway** first - its return slots send them into the network, so no Import Bus and no extra channel are needed.

Only when no Gateway stands next to the machine (or it is full) the plates go into any neighbouring inventory: a chest, a hopper, an Interface.

Right-click any part with the **Forge Hammer** to switch auto-output off or on again; a right-click with an empty hand shows the state.

*Switched off, the plates stay on the deck for your hand or an Import Bus.*

### Forge Hammer
<!-- {"id": "hammer", "icon": "golem_logix:forge_hammer", "x": 1, "y": 1, "parents": ["anvil"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:forge_hammer"} -->

A heavy frost-iron hammer in copper bands: it forges plates on the anvil — an unassembled Mana Saturator.

The head sits on an iron core or an iron sheet.

#### Charge and strikes

The hammer runs on **Source** from its own charge (up to **4000**), like the Portable Chronometer; it draws nothing from nearby jars. **Right-click a Cryo-Source Jar** (or any Source jar) to charge it (visible in its bar and tooltip).

**Left-click the anvil** to strike (100 Source; does not break the anvil).

*Without Neo Vitae or Ars Nouveau in the world, the hammer strikes for free.*

### Source Gems in the Saturator
<!-- {"id": "saturator_gems", "icon": "ars_nouveau:source_gem", "x": 2, "y": 1, "parents": ["saturator"], "requires": "ars_nouveau"} -->

#### Faster than the chamber

With Ars Nouveau the assembled Saturator also runs the **Imbuement Chamber** recipes that make Source Gems: from amethyst, an amethyst block or lapis.

It works **twice as fast** as the chamber but takes **a third more Source**. Roof coolers speed it up as usual, and buses and Stamping Gateways feed it like the plates.

#### Source Gem
<!-- {"type": "ritual", "station": "golem_logix:mana_saturator", "inputs": ["minecraft:amethyst_shard"], "outputs": ["ars_nouveau:source_gem"]} -->

> 665 Source, 2.5 s (the chamber: 500, 5 s).

Lapis Lazuli works the same way.

#### Source Gem Block
<!-- {"type": "ritual", "station": "golem_logix:mana_saturator", "inputs": ["minecraft:amethyst_block"], "outputs": ["ars_nouveau:source_gem_block"]} -->

> 2660 Source, 5 s (the chamber: 2000, 10 s).

The Saturator's bowl holds 5000 Source and refills on its own.

### Dispel: the Mind of Metal
<!-- {"id": "dispel", "icon": "golem_logix:raw_copper_mind", "x": 1, "y": -1, "parents": ["anvil"], "secret": "mind", "requires": "ars_nouveau"} -->

#### Unbinding a mind

Your first plate made on an anvil showed what holds a golem together. Now an Ars Nouveau **Dispel** cast at a **battered** golem unbinds its mind: the golem is gone, and a **raw mind** of its kind drops.

Battered means the same as for the anvil: an iron or copper golem with 4 HP or less, a snow golem below its full 4 HP. A healthy golem's mind holds fast.

#### Iron Golem
<!-- {"type": "ritual", "station": "ars_nouveau:glyph_dispel", "inputs": ["minecraft:iron_block"], "outputs": ["golem_logix:raw_iron_mind"]} -->

> A golem with 1-4 HP (out of 100).

Bring it down with a harming potion or a long fall first.

#### Copper Golem
<!-- {"type": "ritual", "station": "ars_nouveau:glyph_dispel", "inputs": ["minecraft:copper_block"], "outputs": ["golem_logix:raw_copper_mind"]} -->

> A golem with 1-4 HP (out of 12).

A copper golem: a carved pumpkin on top of a copper block.

#### Snow Golem
<!-- {"type": "ritual", "station": "ars_nouveau:glyph_dispel", "inputs": ["minecraft:snow_block"], "outputs": ["golem_logix:raw_snow_mind"]} -->

> A golem with 1-3 HP (out of 4).

One hit with your hand is enough.

#### Pressing an iron mind
<!-- {"type": "ritual", "station": "ars_nouveau:enchanting_apparatus", "inputs": ["golem_logix:raw_iron_mind", "minecraft:iron_ingot*6"], "outputs": ["golem_logix:flattened_iron_core*2"]} -->

> Six ingots on the pedestals, 1000 Source.

The raw mind goes into the Enchanting Apparatus, six ingots on the Arcane Pedestals around it.

#### Pressing a copper mind
<!-- {"type": "ritual", "station": "ars_nouveau:enchanting_apparatus", "inputs": ["golem_logix:raw_copper_mind", "minecraft:copper_ingot*6"], "outputs": ["golem_logix:flattened_copper_matrix*2"]} -->

> Six ingots on the pedestals, 1000 Source.

Six copper ingots on the pedestals around the Apparatus.

#### Pressing a snow mind
<!-- {"type": "ritual", "station": "ars_nouveau:enchanting_apparatus", "inputs": ["golem_logix:raw_snow_mind", "minecraft:snow_block*6"], "outputs": ["golem_logix:flattened_cryo_substrate*2"]} -->

> Six snow blocks on the pedestals, 1000 Source.

Snow blocks take the place of ingots here.

#### Anvil, Dispel, Saturator

Better than the anvil: one battered golem now gives **two** plates instead of one.

But every two plates still cost a golem, a spell cast by hand and six ingots, while the assembled Saturator forges two plates out of one block on its own, day and night.

### Create: Torn Minds
<!-- {"id": "create_minds", "icon": "golem_logix:torn_copper_mind", "x": 2, "y": -1, "parents": ["saturator"], "requires": "create"} -->

#### Mixer and press

With Create, a **Mechanical Mixer** tears the mind out of a block: the block and **250 mB of Liquid Cryo-Source** in a Basin give a **torn mind**.

A **Mechanical Press** then turns the torn mind into a plate. Both steps run well as autocrafting.

#### Torn Iron Mind
<!-- {"type": "ritual", "station": "create:mechanical_mixer", "inputs": ["minecraft:iron_block", "fluid:golem_logix:cryo_source*250"], "outputs": ["golem_logix:torn_iron_mind"]} -->

> Then the press: one Flattened Iron Core.

Mixing happens in a Basin under the Mixer.

#### Torn Copper Mind
<!-- {"type": "ritual", "station": "create:mechanical_mixer", "inputs": ["minecraft:copper_block", "fluid:golem_logix:cryo_source*250"], "outputs": ["golem_logix:torn_copper_mind"]} -->

> Then the press: one Flattened Copper Matrix.

Mixing happens in a Basin under the Mixer.

#### Torn Snow Mind
<!-- {"type": "ritual", "station": "create:mechanical_mixer", "inputs": ["minecraft:packed_ice", "fluid:golem_logix:cryo_source*250"], "outputs": ["golem_logix:torn_snow_mind"]} -->

> Then the press: one Flattened Cryo-Substrate.

Mixing happens in a Basin under the Mixer.

#### As an autocraft chain

Two processing patterns: a block + 250 mB of cryo-source -> torn mind, and torn mind -> plate.

An Import Bus brings the results back. The network pumps the cryo-source itself, see **Liquid Cryo-Source**.

### Plates and their uses
<!-- {"id": "plates", "icon": "golem_logix:flattened_copper_matrix", "x": 2, "y": 0, "parents": ["saturator"]} -->

#### Where plates go
<!-- {"type": "table", "widths": [58, 90]} -->

| Plate | Used for |
|---|---|
| Iron core | Atlas Core, casing, Atlant, calculator |
| Copper matrix | disks, cabinets, buses, terminals, encoder |
| Cryo substrate | pipes, fluid disks, cryo blocks |
| Occult Matrix | chronometer, quantum expander, resonator |

#### Advice

There are no crafting-table recipes for plates. At first they come from the anvil ritual. Nether quartz brings the Saturator: its anvil forges plates by hand. Once the machine around it is built on an End Rod, one iron block gives two cores.

*Packed ice for cryo-substrates can be mined with a Silk Touch pickaxe.*

### Create: Co-Processor Line
<!-- {"id": "create_assembly", "icon": "golem_logix:incomplete_compressed_coprocessor", "x": 3, "y": -1, "parents": ["create_minds"], "requires": "create"} -->

#### An assembly line

With Create a **Cooling Co-Processor** can be finished on a belt instead of in the Saturator: a **Spout** pours in Liquid Cryo-Source, two **Deployers** fit an iron sheet and a popped chorus fruit, a **Press** flattens it.

Three times round, nine steps in all. Nine of ten come out as a **Compressed Co-Processor**, the rest as scraps.

#### One pass
<!-- {"type": "ritual", "station": "create:spout", "inputs": ["golem_logix:incomplete_compressed_coprocessor", "fluid:golem_logix:cryo_source*250"], "outputs": ["golem_logix:incomplete_compressed_coprocessor"]} -->

> Then Deployers with an iron sheet and a popped chorus fruit, then a Press.

An Export Bus next to the Spout pumps the cryo-source out of the network.

#### Line or Saturator?

The Saturator: **1000 Source** a co-processor and nothing else.

The line: 750 mB of cryo-source (**750 Source**), three iron sheets and three chorus fruits a try, and one try in ten fails. Like the Saturator machine, it needs the End; but it keeps the Saturator free, and lines run side by side.

*Plates stay the Saturator's job: nothing makes them cheaper.*

### Imbuement: Blue Ice
<!-- {"id": "ars_substrate", "icon": "minecraft:blue_ice", "x": 3, "y": 0, "parents": ["plates"], "requires": "ars_nouveau"} -->

#### Cryo-Substrate
<!-- {"type": "ritual", "station": "ars_nouveau:imbuement_chamber", "inputs": ["minecraft:blue_ice", "minecraft:amethyst_shard*3"], "outputs": ["golem_logix:flattened_cryo_substrate*2"]} -->

> 1000 Source; three amethyst shards or quartz on the pedestals.

The Imbuement Chamber charges a block of Blue Ice into two substrates.

#### Before the Saturator

No golem and no machine: only the Imbuement Chamber, early in Ars Nouveau.

But it is dear: Blue Ice is nine packed ice, and the Saturator makes two substrates out of one packed ice for the same Source.

### Cryo-Mixer
<!-- {"id": "cryo_mixer", "icon": "golem_logix:cryo_mixer", "x": 3, "y": 1, "parents": ["plates"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:cryo_mixer"} -->

Two blocks high: a rune plinth, louvred sides, a copper module and two flasks on top - water on the left, Liquid Cryo-Source on the right.

#### Snow and ice from the network

The mixer freezes water with **Liquid Cryo-Source**. Two modes, switched in its window:

- **snow** - fast: a snow block every second. Two snow blocks and a carved pumpkin make a snow golem, and snow golems make the plates for pipes;

- **ice** - slow: a block of ice every 5 seconds. Nine blocks of ice make Packed Ice, by hand or by autocraft - the blank for Cryo-Substrate in the Saturator.

*The snow or ice lands in the tray in front and leaves it by itself (see “Auto-output”).*

#### Filling the flasks

- **Water**: a bucket by hand, or any pipe or bus that carries water (for example water on a fluid disk and an Export Bus).

- **Liquid Cryo-Source**: an **Export Bus** facing the mixer with a Cryo-Source ghost in its filter pumps it straight out of the network's Source. It cannot be poured by hand.

Both go in through either block of the mixer, each into its own flask; nothing flows back out.

#### Upgrades

Slots on the right of the window:

- up to **4 Compressed Co-Processors**: each adds the base speed once more (four make it five times faster);

- a **Redstone Module**: the mixer works by signal; in pulse mode every pulse makes one block.

The golem at the side turns the valve while it works; if something stands there, it moves to a free side.

#### Auto-output

**Auto-output** is **on by default**: the tray hands its snow or ice to a neighbouring **Stamping Gateway** first (into the network through its return slots), and only without one into any neighbouring inventory.

The **hopper** button to the left of the window switches it off: a **chest** on the button means the blocks wait in the tray for the window or an Import Bus.

## Atlas Array
<!-- {"id": "array", "icon": "golem_logix:atlas_core", "color": "7fd0e8"} -->
> from one core to a 3x3x4 monument

### The Atlas Core: step by step
<!-- {"id": "array_overview", "icon": "golem_logix:atlas_core", "x": 0, "y": 0, "gold": true} -->

#### A network grows with its core

The **Atlas Core** is the network's mind. How many devices it serves at once - its **channels** - depends on what you build under it, step by step:

1. **the core alone**: **8 channels**, through its bottom face;

2. **a starter tower** - two conduits and a Cable Bushing under the core: **16 channels**;

3. **the whole Atlas Array**, an iron monument 3x3x4: five bushings of **64 channels**, **320** in all;

4. **Channel Expanders** in the array's walls raise every line.

*Every step is built on top of the previous one: nothing is taken apart.*

#### The last step
<!-- {"type": "image", "texture": "array_full"} -->

The finished array: the core with its crystal on top, copper electron-golems orbit it, mini-golems carry crates around the ring of casings.

#### Why Atlants

Thought is heavy. A mind that keeps thousands of things in memory presses down on whatever lies beneath it - the old masters felt it in their hands.

So the core is set on a column, and the column is given to four iron colossi to carry. The **Atlants** count nothing and connect nothing: they just hold. While they stand, the core's thought does not sink through the stone.

*They say that at night, beside a finished array, you can hear the Atlants shift their weight from foot to foot.*

### Step 1: the Atlas Core
<!-- {"id": "array_core", "icon": "golem_logix:atlas_core", "x": 1, "y": 0, "parents": ["array_overview"], "gold": true} -->

<!-- {"type": "crafting", "recipe": "golem_logix:atlas_core"} -->

The core is the network's mind and its main Source consumer.

The crystal glows and electrons orbit it: three for a complete array, one for a tower.

#### The core alone

Placed on its own, the core already runs a small network of **8 channels**.

It connects **only through its bottom face**: put a pipe, a bushing or a device right under it; pipes and devices beside it or on top do not connect.

Source: a jar touching any block of the network.

*When you have two conduits and a bushing, set them under the core: that is the starter tower.*

#### Core rules

- A network must have **exactly one** core. A second core in the same network stays idle (right-clicking it tells you so).

- In a tower or an array the core links only to their parts: channels leave through the Cable Bushings.

- Once a second the core recounts the network, drains Source, lights up the pipes and wakes the cabinets.

##### Right-click on the core

Shows the channels (used/capacity) and what to build next: the block and its coordinates, with sparks at the spot.

### Step 2: the starter tower
<!-- {"id": "array_tower", "icon": "golem_logix:atlas_conduit", "x": 2, "y": 0, "parents": ["array_core"], "gold": true} -->

#### Materials list
<!-- {"type": "checklist", "variant": "tower"} -->

Checked against your inventory. The button shows the ghost blocks of the tower: the bushing, two conduits above it and the core on top.

The ghosts follow the block you look at; **Y** locks them, **N** cancels them.

#### 16 channels

Until you have iron for the whole array, put a **small tower** under the core - a column of four blocks:

- the core on top,

- two Atlas Conduits under it,

- a Cable Bushing at the bottom,

- a Source supply touching any block of the network.

Such a network gives **16 channels** - for terminals, a couple of cabinets and buses. Its one line goes through the bushing.

*The tower is the central column of the future array: build the monument around it, nothing is taken apart.*

### Step 3: the whole Atlas Array
<!-- {"id": "array_assembly", "icon": "golem_logix:atlas_casing", "x": 3, "y": 0, "parents": ["array_tower"], "gold": true} -->

#### Materials list
<!-- {"type": "checklist", "variant": "full"} -->

The list is checked against your inventory: a green tick - you have enough.

The button turns on **ghost blocks**: turquoise ones show what to place and where, red ones are foreign blocks in the way. The ghosts follow the block you look at; press **Y** to lock them and **N** to remove them. Near a Core the projection snaps onto it.

*The lower and upper halves of an Atlant are a single item; casing can be partly replaced by expanders.*

#### Exploded view
<!-- {"type": "image", "texture": "array_layers"} -->

Four layers, bottom to top. Your starter tower is already the central column, with the Core on top.

#### Layer 1 - the base
<!-- {"type": "blueprint", "layer": 0} -->

**Bushings** at the corners and in the center, **conduits** on the sides.

Bushings are the only places where the network leaves the array: attach pipes and devices to them.

#### Layer 2 - the Atlants' legs
<!-- {"type": "blueprint", "layer": 1} -->

Corners: **Atlants** (lower half).

Center: a **conduit**. Sides: **casing** or a **Channel Expander** (cycling in the diagram).

#### Layer 3 - the shoulders
<!-- {"type": "blueprint", "layer": 2} -->

Corners: Atlants (the upper half).

Center: a conduit. Sides: casing or expanders.

#### Layer 4 - the crown
<!-- {"type": "blueprint", "layer": 3} -->

Corners and sides: **casing**, center: the **Atlas Core**.

The core crystal rises above the ring of casings.

#### Building hints

Right-click the core to see what is missing: the block name, its coordinates and sparks at the right spot.

An Atlant is placed like a door - one item, two blocks. When the array is complete the Atlants turn to face outward by themselves.

The finished array gives **five lines of 64 channels** - 320 in all.

*The order of layers does not matter - only that everything stands in its place at the end.*

### Step 4: expanding channels
<!-- {"id": "array_expanders", "icon": "golem_logix:quantum_channel_expander", "x": 4, "y": 0, "parents": ["array_assembly"], "gold": true} -->

#### More channels

A basic array gives 5 lines of 64 - 320 channels. When that is not enough, put **Channel Expanders** instead of casing into the side cells of the two middle layers (**8 places** in total).

Each expander raises the capacity of **every one** of the five lines:

- **Channel Expander**: +16 per line,

- **Quantum Channel Expander**: +32 per line.

#### Expander slots
<!-- {"type": "image", "texture": "array_middle"} -->

The middle layers of the array. Windows in the side cells are expanders (turquoise basic, violet-and-gold quantum).

<!-- {"type": "crafting", "recipe": "golem_logix:channel_expander"} -->

Fits any of the eight side cells of layers 2 and 3.

<!-- {"type": "crafting", "recipe": "golem_logix:quantum_channel_expander"} -->

Built from a basic expander, gold and an **Interdimensional Occult Matrix**.

#### What you get
<!-- {"type": "table", "widths": [84, 64]} -->

| Expanders | Line / network |
|---|---|
| none | 64 / 320 |
| 4 basic | 128 / 640 |
| 8 basic | 192 / 960 |
| 4 quantum | 192 / 960 |
| 8 quantum | 320 / 1600 |
| mix 4+4 | 256 / 1280 |

The network capacity is the sum of its five lines.

#### Important

The expander must be **built into the wall**: the array is checked as a whole. If you pull a block out, the core reports what is missing, and until the array is whole again the network has the starter tower's 16 channels (if the central column still stands).

Pipes still limit their own stretches: to pass more than 64 channels through one bushing, run several pipes from it or use dense pipes.

### Cable Bushings and lines
<!-- {"id": "array_bushings", "icon": "golem_logix:cable_bushing", "x": 2, "y": -1, "parents": ["array_tower"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:cable_bushing"} -->

A brass “exit” socket of the array. Five bushings at the base are five independent lines.

#### Lines

Every bushing gives **one line**: **64 channels** in an array, **16** in a starter tower. All devices connected through this bushing (and through pipes behind it) share its line.

A corner bushing has two or three free faces: each can take its own pipe. The center bushing sits in a shaft, but a power source can be put under it.

Right-clicking a bushing prints in chat **how many channels are used** on its line and in the whole network; a Channel Counter fixed to it shows the same.

##### Tip

Spread heavy sections over **different bushings**: four corners give four lines, and the total capacity of the network is the sum of all lines.

#### Capacity
<!-- {"type": "table", "widths": [74, 74]} -->

| Line | Network (5 lines) |
|---|---|
| 64 (base) | 320 |
| +16 (1 exp.) | 400 |
| +128 (8 exp.) | 960 |
| +256 (8 quantum) | 1600 |

More on expansion in the article “Expanding channels”.

### Casing and conduits
<!-- {"id": "array_casing", "icon": "golem_logix:atlas_conduit", "x": 2, "y": 1, "parents": ["array_tower"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:atlas_casing"} -->

The iron walls of the monument: the side cells of the two middle layers and the whole crown. The top of every casing is inlaid with brass.

<!-- {"type": "crafting", "recipe": "golem_logix:atlas_conduit"} -->

A copper-and-rune bus: the base (sides) and the central column up to the core.

#### A quirk of the conduit

The conduit links **only to other array parts**: the core, bushings and other conduits. A pipe, terminal or cabinet placed against it **will not connect**.

This is so channels can leave only through bushings - otherwise line capacity could be bypassed.

Casing connects nothing - it only completes the monument.

### Atlants
<!-- {"id": "array_atlants", "icon": "golem_logix:atlas_atlant", "x": 3, "y": -1, "parents": ["array_assembly"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:atlas_atlant"} -->

An iron golem colossus two blocks tall. Four Atlants hold the array up by its corners.

#### About the statue

- Placed with **one item into two blocks**, so free space above is required.

- Both halves break together; the item drops once.

- The lower half stands on layer 2 of the array, the upper half on layer 3. Mix the halves up and the array will not count.

- When the array forms the Atlants turn themselves: northern ones face north, southern ones face south.

*Atlants give no channels and connect nothing - they are the monument's load-bearing supports.*

### When something is wrong
<!-- {"id": "array_diagnosis", "icon": "minecraft:spyglass", "x": 1, "y": 1, "parents": ["array_core"]} -->

#### Core messages
<!-- {"type": "table", "widths": [74, 74]} -->

| Message | Cause |
|---|---|
| Core alone | 8 ch., bottom face |
| Incomplete | a block is missing |
| Array OFFLINE | no Source |
| Another core | a core is already there |
| No channels | line or pipe full |

Right-click the core: it names the missing block and lights the spot with sparks.

## Network
<!-- {"id": "network", "icon": "golem_logix:basic_snow_pipe", "color": "9fe3f0"} -->
> pipes and channels

### Channels
<!-- {"id": "net_channels", "icon": "golem_logix:dense_frost_pipe", "x": 0, "y": 0, "gold": true} -->

#### What a channel is

Every network device (terminal, cabinet, bus, gateway...) takes **1 channel**. A channel is a path from the core to the device through a bushing and pipes.

A channel has four limits:

- **Total network capacity** - set by the core: alone 8, on a starter tower 16, in the array 320 and more.

- **Bushing line** - no more channels pass through one bushing than the line capacity.

- **Pipes** - a basic pipe passes 16 channels, a dense one 64.

- **Devices** - a device passes on 16, like a basic pipe.

A device that gets no channel stays **silent**: the terminal reports “no free channels”.

#### Who gets a channel first

Channels go out from the core along the **shortest path**: each device gets the route with the fewest blocks to the core.

**Dense runs go first**: devices on dense pipes, then on basic pipes, then devices that hang on other devices.

A device gets a channel only if **every block** on its path still has room; otherwise it waits.

*Channels never cross the array: each bushing counts only its own devices.*

#### Who takes channels

##### Take 1 channel

- Analog and Crafting terminals, Copper Drive Bay,

- Vacuum, Injector and Periscope buses,

- Encoder, Stamping Gateway, Cryo-Calculator, Recipe Monitor.

##### Take no channels

- Pipes, Cable Bushings, Atlas Conduits,

- Cooling Co-Processors, the Core, the Mana Saturator.

*The Portable Chronometer is not a device: it talks to the core directly and spends no channels.*

#### A channel from inside

A channel is not a wire, but a thread of frozen runes stretched from the core to a device, with icy glyphs running back and forth.

Each pipe, bushing, and node holds only as many threads as its cold can bear. An extra thread simply fails to freeze, leaving the device silent.

*The tiny snowman in the pipes keeps the threads, making sure none have thawed.*

### Cryogenic pipes
<!-- {"id": "net_pipes", "icon": "golem_logix:basic_snow_pipe", "x": 1, "y": 0, "parents": ["net_channels"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:basic_snow_pipe"} -->

A glass tube with brass bands. Passes **16 channels**.

<!-- {"type": "crafting", "recipe": "golem_logix:dense_frost_pipe"} -->

A thick pipe of deep blue ice with heavy bronze bands. Passes **64 channels**. Where a basic pipe meets it, a brass reducer washer closes its end: the thin tube enters it without a gap.

#### How pipes behave

- Pipes attach to all neighboring network blocks - devices and other pipes. Brass cuffs appear at the ends.

- While the network has power, the glass of every pipe **glows softly**. The glow is only for the eye: pipes do not light up the area and do not stop mobs from spawning.

- Inside a powered pipe a tiny snowman in a pumpkin head runs carrying a box - **one for the whole connected pipe network**. In thin pipes only its head is visible, in dense ones the whole snowman. Without Source it freezes and the glow goes out.

- Pipes do not connect to the array's internals (conduits, core).

##### Limit

A pipe passes a limited number of channels. Devices behind an overloaded pipe go silent - replace it with a dense one or lay a second line from another bushing.

### Placing devices
<!-- {"id": "net_devices", "icon": "golem_logix:copper_drive_bay", "x": 2, "y": 0, "parents": ["net_pipes"]} -->

#### Devices attach directly

A device needs no pipe if it stands against a bushing, another pipe or another network device: nodes conduct channels through each other.

So terminals and cabinets can be chained, but a device passes on **at most 16 channels** (like a basic pipe), its own included: the seventeenth device of a chain stays silent. Run a dense pipe along big rows of devices.

##### Source supply

A jar must touch any node (not necessarily the core). It takes no channel and does not carry the network. The Eternal Frost Source Resonator is attached the same way - but it powers chronometers, not the network.

#### A sample base
<!-- {"type": "image", "texture": "array_setup"} -->

An array with two cabinets, a terminal, a calculator, a gateway and a bus; a Resonator below the array.

### Planning a network
<!-- {"id": "net_plan", "icon": "minecraft:map", "x": 1, "y": 1, "parents": ["net_channels"]} -->

#### Avoiding the limit

- Count your devices: terminals, cabinets and buses take one channel each.

- More than 8 devices - complete the array (320).

- Many devices behind one exit - use different bushings or dense pipes.

- Hit the line limit - add expanders.

- Hit the network limit - quantum expanders.

*A basic array is often enough: a typical base of 30-60 devices lives comfortably on 320 channels.*

### Diagnostics
<!-- {"id": "net_diagnose", "icon": "minecraft:redstone_lamp", "x": 2, "y": 1, "parents": ["net_plan"]} -->

#### What a message means
<!-- {"type": "table", "widths": [66, 82]} -->

| Message | What to do |
|---|---|
| No Atlas Core | connect to the array |
| No Source | bring a source |
| No channels | pipes / expanders |
| Waiting for machine | return output by bus |
| No gateway | put the pattern in one |
| Paused | see the Redstone Module |

At the bottom of the terminal screen you see “Ch 12/64” - used and available channels; the tooltip shows everything else.

#### Lamps and counters

- Straight horizontal pipes carry a **nixie lamp** every sixth block: its digits show how many channels run through the pipe - cyan, amber and red as it fills.

- A **Channel Counter** shows the same number anywhere: on a bushing, a device or a pipe.

- Right-clicking a bushing prints its line and the whole network in chat; right-clicking the core tells you what to build next.

- A device without a channel stays silent: the terminal does not open, the bus stands still.

### Channel Counter
<!-- {"id": "net_counter", "icon": "golem_logix:channel_counter", "x": 3, "y": 1, "parents": ["net_diagnose"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:channel_counter"} -->

Three nixie tubes on a walnut base. They show the same number as the lamps on the pipes: how many channels run **through the spot the counter is fixed to**. Handy for keeping an eye on the network - or just for the look of a control room.

#### Where to fix it

It reads the block it is fixed to and picks its own mount:
- **on a block** (bushing, device, core) — a plain foot;
- **on a horizontal pipe** — a brass saddle facing you;
- **on the side** of a block or pipe — a small bracket shelf.

It takes no channels, and pipes do not connect to it.

#### Reading it

The digits glow on **both sides** of the tubes, so it reads from the front and from behind.

- &3cyan&r - there is room, &6amber&r - it is filling, &4red&r - (nearly) full;

- leading zeros stay dark; with no core or no Source every tube goes out.

**Right-click** switches to the **capacity** of that spot (in nixie orange) and back: 16 for a basic pipe or a device, 64 for a dense pipe.

### Pipe Plugs
<!-- {"id": "net_plugs", "icon": "golem_logix:pipe_plug", "x": 2, "y": -1, "parents": ["net_pipes"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:pipe_plug"} -->

A brass cap that closes one side of a pipe.

#### Keeping lines apart

Pipes join every network block next to them, so two lines side by side **merge into one**: channels take the shortest path and may crowd one line.

**Right-click a pipe** with a plug: the side closest to the click closes. A closed side connects to nothing.

Right-click a closed side to take the plug back.

#### When to plug

- Lines from different bushings touch: plug them apart, each keeps its own line.

- A dense trunk passes devices that should hang on a basic branch: plug the trunk's sides.

- A device must not take its channel from a neighbouring line: close the side facing it.

*A plug takes no channel and does not stop Source jars.*

### Frost Valve (Cryo-Diode)
<!-- {"id": "net_diode", "icon": "golem_logix:cryo_diode", "x": 2, "y": 1, "parents": ["net_pipes"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:cryo_diode"} -->

A directional frost diode between two networks.

#### Subnet isolation

The Frost Valve points from an **inlet face** (wide brass flange) to an **outlet face** (narrow copper nozzle).

It **strictly isolates** the two networks: channels, devices, cores, and storages do not cross between them. Each network runs independently under its own Atlas Core.

#### Passing Liquid Cryo-Source

While channels and data remain separated, the outlet side network automatically draws **Liquid Cryo-Source** from the inlet side network.

This allows a large main base network with deep Source reservoirs to supply small specialized subnets (such as isolated farming or autocrafting loops) without bridging their channel topologies.

### Cryo-Source Telegraph
<!-- {"id": "net_telegraph", "icon": "golem_logix:cryo_source_telegraph", "x": 3, "y": 0, "parents": ["net_devices"], "gold": true} -->

<!-- {"type": "crafting", "recipe": "golem_logix:cryo_source_telegraph"} -->

A rusty copper base 2x2 with a mast three blocks high: one item places it all, facing you. It carries 64 channels; pipes join its base.

#### Linking two telegraphs

1. Put **Aetherial Linkages** into the **Write** slot of the telegraph at your base: a whole stack is written at once.

2. Carry the card to a distant telegraph and put it into its **Memory** slot: the two link, and the outpost joins your network.

- While a card sits in Write, Memory is locked, and the other way round.

- One base telegraph takes any number of outposts: write a card for each.

#### Range
<!-- {"type": "table", "widths": [76, 72]} -->

| Card | Range |
|---|---|
| none | 200 blocks |
| Aether Resonance Core | the dimension |
| Void-Glass Prism | any dimension |

The better card of the two ends counts. Both telegraphs must be loaded.

#### Reset and upkeep

The big **RESET** button erases the card in Memory and gives the telegraph a new seal: every card written for it stops working.

A working link costs the Atlas Core **2 Source** a second.

*While the link works the operator keys Morse: the lamp lights with every press, a blue pulse runs up the mast.*

<!-- {"type": "crafting", "recipe": "golem_logix:aetherial_linkage"} -->

Two cards per craft. Blank cards record the telegraph whose Write slot they are put into, up to 16 at once.

<!-- {"type": "crafting", "recipe": "golem_logix:aether_resonance_core"} -->

Range card: the link reaches anywhere in the same dimension.

## Storage
<!-- {"id": "storage", "icon": "golem_logix:copper_drive_bay", "color": "e08c4a"} -->
> disks, cabinets, terminals

### Magnetic disks
<!-- {"id": "st_disks", "icon": "golem_logix:copper_disk_16k", "x": 0, "y": 0, "gold": true} -->

#### Pure capacity

A disk counts its capacity in **pieces**: a 1k disk holds 1,000 items of any kinds together - one type or a thousand different ones. There is no separate limit on the number of types.

##### Capacities

- 1k - 1,000 items, 4k - 4,000, 16k - 16,000, 64k - 64,000, **256k - 256,000**.

A disk can be pulled out, carried and put into another cabinet: all of its contents stay on it.

*The magnetic tape keeps not the things themselves but their frozen runes: a chest of iron on a disk weighs no more than a feather.*

<!-- {"type": "crafting", "recipe": "golem_logix:copper_disk_1k"} -->

The base of the line.

<!-- {"type": "crafting", "recipe": "golem_logix:copper_disk_4k"} -->

Three 1k disks and copper.

<!-- {"type": "crafting", "recipe": "golem_logix:copper_disk_16k"} -->

Three 4k disks, copper and matrices.

<!-- {"type": "crafting", "recipe": "golem_logix:copper_disk_64k"} -->

Three 16k disks, gold and matrices.

<!-- {"type": "crafting", "recipe": "golem_logix:copper_disk_256k"} -->

Three 64k disks, diamonds and cores.

### Cryo Fluid Disks
<!-- {"id": "st_fluid", "icon": "golem_logix:fluid_disk_16k", "x": 1, "y": -1, "parents": ["st_disks"]} -->

#### Cold disks

The same five capacities - 1k, 4k, 16k, 64k, **256k** - but in **millibuckets** (mB). 1000 mB is one bucket.

Fluid disks glow blue; item disks do not accept fluids and vice versa.

Any number of fluid types can be stored: water, lava, oil, milk...

<!-- {"type": "crafting", "recipe": "golem_logix:fluid_disk_1k"} -->

Glass and substrate.

<!-- {"type": "crafting", "recipe": "golem_logix:fluid_disk_4k"} -->

Three 1k disks and packed ice.

<!-- {"type": "crafting", "recipe": "golem_logix:fluid_disk_16k"} -->

<!-- {"type": "crafting", "recipe": "golem_logix:fluid_disk_64k"} -->

<!-- {"type": "crafting", "recipe": "golem_logix:fluid_disk_256k"} -->

### Copper Drive Bay
<!-- {"id": "st_bay", "icon": "golem_logix:copper_drive_bay", "x": 1, "y": 0, "parents": ["st_disks"], "gold": true} -->

<!-- {"type": "crafting", "recipe": "golem_logix:copper_drive_bay"} -->

A big copper cabinet with a glass front: **10 cassette slots** for item and fluid disks. Takes 4 blocks and 1 channel.

#### What it looks like

The cabinet is **2x2 blocks**: two cells wide and two tall, one block deep. It is placed with a single item, like a door: the lower left block goes where you click and the other three go to the right and above, so leave free room. The front faces you; **any of the four parts** connects it to the network.

Behind the glass **every inserted disk is a cassette with a colored label** in its slot, with **four fill LEDs** on it.

##### Label color = capacity

- Item: copper (1k), iron (4k), gold (16k), diamond (64k), amethyst (256k).

- Fluid: light blue (1k), ice (4k), blue ice (16k), lapis (64k), purpur (256k).

##### LED

&2Green&r - nearly empty, &6yellow&r - from 1/4, &6orange&r - from 1/2, &4red&r - over 3/4.

#### Archivists

While powered, **copper archivist golems** carry crates along shelves, cassette reels wind as disks fill, and violet runes pulse. Opening the window swings open its **glass doors** and gauge panel. Without Source, the cabinet freezes still.

##### Controls

- Right-click to open 2×5 disk slots and set **priority** (higher priority stores items first, lower priority draws first).

- Shift-click quickly moves disks; breaking the cabinet drops all disks with their contents intact.

### Analog Terminal
<!-- {"id": "st_terminal", "icon": "golem_logix:analog_terminal", "x": 2, "y": 0, "parents": ["st_bay"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:analog_terminal"} -->

A green-phosphor CRT screen. Shows everything in the network. Takes 1 channel. When the network starts, a golem waves at you across the whole screen, and then sets to hauling crates.

#### Controls
<!-- {"type": "table", "widths": [66, 82]} -->

| Action | Result |
|---|---|
| LMB on item | take a stack |
| RMB on item | take half |
| MMB on item | take one |
| Shift+LMB | stack to inventory |
| Click with item | insert all |
| RMB with item | insert one |
| Shift+click inv. | send to network |
| Ctrl+LMB | order crafting |

#### Search and sorting

The search box understands:

- plain text - by item name,

- **@mod** - by mod (for example @minecraft),

- **#text** - by tooltip text.

Buttons on the right of the window: **sort mode** (name/quantity/mod), **direction**, **list view** (both / items / fluids), **craftable only**.

At the bottom - the disk fill bar and “Ch used/capacity”; hover for details: items, fluids, channels, busy calculators.

The mouse wheel and the bar on the right scroll the list. **Tab** or **/** jumps straight into search.

#### Window size

The **“Size”** button on the right stretches the window vertically: normal, tall or **the whole screen** - the list gets as many rows as fit, and you see most of the network at once. The choice is remembered for all terminals and the Chronometer.

With a recipe viewer installed, hover an item or fluid in the list and press **R** for its recipes or **U** for its uses.

### Crafting Terminal
<!-- {"id": "st_crafting", "icon": "golem_logix:crafting_terminal", "x": 3, "y": 0, "parents": ["st_terminal"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:crafting_terminal"} -->

The same terminal, but with a 3×3 crafting grid and a result. Takes 1 channel. A golem with crates lives on the amber screen too.

#### Conveniences

- Used ingredients **refill themselves** from the network: take the result and the missing items in the grid are topped up.

- The “v” button on the left returns everything from the grid to the network.

- Shift-clicking the result crafts as many as possible.

- Closing the window also returns the grid to the network (and what does not fit goes to your inventory).

*In a recipe viewer the “+” button lays the recipe out in the grid from the network and your inventory (Shift - full stacks); what is nowhere to be found is marked red.*

#### Precraft

If something is missing for a recipe but the network **has patterns** for it, the recipe viewer's “+” button lights up turquoise.

**Ctrl + “+”** orders the missing items right away - one job for all of them (with Shift - for a full stack). Whatever cannot be crafted is listed in chat.

When the order is ready, an **“Order ready”** plate pops up in the corner of the screen with a chime - press “+” again and craft the recipe.

### Security Terminal
<!-- {"id": "st_security", "icon": "golem_logix:security_terminal", "x": 3, "y": 1, "parents": ["st_terminal"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:security_terminal"} -->

A heavy brass and steel security console with 16 card bays.

#### Multiplayer protection

When placed on a network, the player who placed the terminal becomes its **Owner**.

As long as an active Security Terminal is linked to the network, all other players are locked out of network interactions unless they hold a configured card in the terminal.

#### Identity Punch Cards

<!-- {"type": "crafting", "recipe": "golem_logix:identity_punch_card"} -->

Right-click a blank **Identity Punch Card** to bind it permanently to your name and UUID.

Insert a bound card into the Security Terminal. The Owner can click to toggle permissions for each card:

- **Insert** (`↓ Ins`): Deposit items or fluids into network storage.
- **Extract** (`↑ Ext`): Withdraw items or fluids from network storage.
- **Craft** (`⚙ Crf`): Place autocrafting orders or use the Crafting Terminal grid.
- **Build** (`⚒ Bld`): Break or place network blocks, and configure bus settings or patterns.

*Creative operators bypass all security restrictions.*

### Fluids in the terminal
<!-- {"id": "st_fluids_ui", "icon": "minecraft:bucket", "x": 3, "y": -1, "parents": ["st_terminal", "st_fluid"]} -->

#### Fluids in the list

By default the list shows items and fluids **together**. A fluid is counted in **buckets**: 1000 mB is “one piece” (a droplet marks the cell), the exact amount is in the tooltip. The “Both/Items/Fluids” button on the right chooses the view; in the Fluids view amounts are shown in mB.

##### Filling and pouring

- An **empty bucket** on the cursor + a click on a fluid row - the bucket is filled from the network.

- A **full bucket** on the cursor + a click on a fluid row - the fluid is poured into the network and you keep the empty bucket. In item mode **right-clicking with the bucket** on the list does the same.

Any container that understands fluids works (tank, cistern...).

Buses move fluids automatically (see the Buses section).

### Copper Tank
<!-- {"id": "st_tank", "icon": "golem_logix:copper_tank", "x": 2, "y": -1, "parents": ["st_fluid"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:copper_tank"} -->

A copper chest - what is left of a copper block once a copper golem rises from it - in four panes of glass. It holds **one fluid**, **16 000 mB**.

#### Filling and emptying

- A **bucket** or any other fluid container pours in or fills up with a right-click.

- The network's **buses** and other mods' pipes reach it from every side: an Export Bus fills it, an Import Bus empties it.

- An empty hand shows what is inside; a comparator reads how full it is.

- **Broken, it keeps its fluid**: the item carries the fluid and its capacity, and placed again it is the same tank.

*Tanks stand on each other in columns, and the fluid shows behind the glass as high as the tank is full.*

#### Bucket mode

**Sneak + right-click** with the tank in hand switches its **bucket mode**.

In bucket mode the tank is not placed: a right-click **scoops up** a bucket's worth of the fluid you look at (a source, a cauldron), or **pours one out** where you point if it cannot take more.

So one tank carries sixteen buckets of lava or water, and a joined one far more.

*Without pipe mods this is the simplest way to move a lake or a lava pool.*

#### Joining tanks
<!-- {"type": "crafting", "grid": ["golem_logix:copper_tank", "golem_logix:copper_tank", "", "", "", "", "", "", ""], "result": "golem_logix:copper_tank"} -->

Put two to nine tanks anywhere in a crafting grid: you get **one tank that holds as much as all of them** (up to 1 024 000 mB), with all their fluid. The fluid must be one and the same; empty tanks join any.

### Periscope
<!-- {"id": "st_periscope", "icon": "golem_logix:periscope_storage_bus", "x": 2, "y": 1, "parents": ["st_bay"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:periscope_storage_bus"} -->

A compact brass box with a runed monocle lens in front and an icy star on top. Takes 1 channel.

#### Containers as memory

Place the periscope **with its lens on a chest**, barrel or any container with items or fluids: its contents appear in the terminal as if they lay on a disk.

- Items are taken from and put into the chest through the terminal.

- The periscope sees **tanks with fluids** too: a tank is attached like a chest.

- Right-clicking the periscope opens the **priority** window: the higher the number, the earlier this storage receives items (cabinets and periscopes are ordered by the same number) and the later it gives them up.

- With a **Redstone Module** the storage is switched on and off by a signal.

- The periscope hauls nothing itself - it only “peers” inside.

*Handy for hooking up old warehouses you would hate to dismantle.*

#### Like a storage bus

The periscope's window has a **filter** of 9 slots, as a bus has: what may go into the container and out of it (a bucket stands for its fluid; empty - everything).

- The button with the frame / barrier on the left: an **allow list** (only what the filter names) or a **deny list** (everything but that).

- The **access** button: both ways (chest), **insert only** (hopper: the network fills it, the terminal does not show it - a dump) or **extract only** (dropper: the network empties it and puts nothing new in).

- With a **Fuzzy Card** the filter matches fuzzily, as in buses.

*A chest for cobblestone only, a high priority and an allow list - and the whole quarry ends up in it.*

## Buses
<!-- {"id": "buses", "icon": "golem_logix:vacuum_import_bus", "color": "d4a017"} -->
> vacuum and pneumatics

### Interaction buses
<!-- {"id": "bus_intro", "icon": "golem_logix:vacuum_import_bus", "x": 0, "y": 0, "gold": true} -->

#### How buses work

Buses connect the network to the outside world - chests, furnaces, machine inventories. Each takes 1 channel.

A bus faces **the container with its front**. Without upgrades it moves up to 16 items or 1000 mB of fluid at a time, two and a half times a second.

A bus is compact: it takes the half of the block next to the container. Its other sides have **pipe sockets** that grow into mini tubes to the block edge next to a network node.

Buses and pipes are **steps**: you walk onto them without jumping.

Three buses:

- **Vacuum Import Bus** - sucks from a container into the network;

- **Injector Export Bus** - pushes from the network into a container;

- **Periscope** - connects a container as storage (see the Storage section).

*Items are never shuffled in a circle: a bus does not put things back into the very chest it takes them from, even if a periscope links that chest.*

### Vacuum import bus
<!-- {"id": "bus_import", "icon": "golem_logix:vacuum_import_bus", "x": 1, "y": -1, "parents": ["bus_intro"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:vacuum_import_bus"} -->

A compact brass box; a turbine funnel in front sucks items in, a turquoise star on a rune plate on top.

#### Takes what is needed

- The bus scans the container's slots and pulls items into the network (if the disks have room).

- The same for fluids: it pumps them out of tanks.

- An **empty filter** means take everything. A filled one - only what is listed; with the **deny list** button (a barrier on the left) - everything but that.

- With only an item filter it does not take fluids, and vice versa.

- The bus gives priority to autocrafting jobs: expected results go straight to the cryo calculator - even if the storage is completely full.

- The bus window has a **priority**: with several buses in a network the one with the higher number works first.

*This is the “result return” for machines: attach it to the furnace output and ingots go straight into the network.*

### Injector export bus
<!-- {"id": "bus_export", "icon": "golem_logix:injector_export_bus", "x": 1, "y": 0, "parents": ["bus_intro"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:injector_export_bus"} -->

A compact brass box; a steel nozzle in front pushes items out, a violet star on a rune plate on top.

#### Outputs only by list

- Without a filter the bus **outputs nothing** - otherwise it would drain the whole network.

- List the items to deliver in the filter. The bus supplies them while the container accepts.

- A bucket in a filter slot - output that fluid.

- Before delivering it checks whether the item fits: the excess stays in the network.

- The bus priority sets the order: a higher number works earlier than the others in the network.

- With an **Autocraft Module** the bus orders what the network lacks from the calculators - and delivers it as soon as it is ready.

*Example: a bus with coal in its filter on a furnace - the furnace always has fuel.*

The **order** button on the left: **in order** (the first filter slot the network has goes first), **round robin** (one slot an operation, each after the last) or **at random**.

##### The slot window

**Middle-click (the mouse wheel) a filled filter slot**: its settings window opens - the same window in every bus, as the gear on the Golem Interface.

There you set how much to **keep in the container**: the bus tops it up to that number and no further - 16 coal in a furnace, a stack of bread in a chest. **0** exports without a limit, as before.

Type the number, or use the step and preset buttons; for a fluid in mB or in buckets (16B). Enter applies, **Clear** empties the slot. The number shows in the slot's corner.

### Mason's Formation Bus
<!-- {"id": "bus_formation", "icon": "golem_logix:formation_bus", "x": 2, "y": -1, "parents": ["bus_export"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:formation_bus"} -->

A whole copper block with a **copper golem fused into its front**. It takes the blocks named in its filter out of the network and **sets them down in front**, one at a time, while the spot is free. Takes 1 channel.

#### How it works

- Place it looking where the blocks should go: its front faces away from you. Pipes join it on every side but the front.

- The golem pushes the block out of the brass mouth; the push lasts the bus's interval, so with **four Compressed Co-Processors** it sets a block down every couple of ticks.

- If the spot is taken while it pushes, it holds the block and waits. A block that cannot stand there goes back into the network.

- Upgrades as on the Export Bus: an **Autocraft Module** orders a block the network lacks, a **Redstone Module**, a **Fuzzy Card**.

- The **order** button on the left: **in order** (the first filter slot the network has), **round robin** (slot after slot) or **at random**.

*Fused into its casing, the golem can never leave its post - which is why it lays blocks truer than any mason.*

### Hewer's Annihilation Bus
<!-- {"id": "bus_annihilation", "icon": "golem_logix:annihilation_bus", "x": 2, "y": -2, "parents": ["bus_import"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:annihilation_bus"} -->

A small **iron golem** in a brass niche works the block in front of it: it **breaks** it into the network, or **clicks** it with the item it holds. Takes 1 channel.

#### The tool

The window has a **tool slot**. Empty, the golem brings the **strength of every iron tool**: the best of the iron pickaxe, axe, shovel, hoe and sword for that block, never worn out.

Put a tool in and the golem uses it, enchantments and all (Fortune, Silk Touch, Efficiency) - and wears it. A tool about to break is put down instead.

- With an **Autocraft Module** the slot is **refilled** from the network with what you last put there: bone meal is topped up with whatever the network has, even a few; a tool is used till it breaks, and the least worn one of its kind takes its place. What the network lacks is ordered from the calculators **one at a time** - bone meal from bones, say - so the slot fills as fast as it is made. Take the item out by hand and the slot is left empty.

*A block the tool cannot harvest (obsidian for iron) is left alone.*

#### Clicks

Settings in the column left of the window, as on a clicker:

- **Left click**: mines the block like a player, as fast as the tool allows - Efficiency counts, co-processors do not fit here: a better tool is the only way to speed it up - cracks and all. The drops go into the network; while it cannot take them all, the golem waits.

- **Right click**: once an interval uses its item (or bare hand) on the block - a lever, shears on a pumpkin, a hoe, bone meal. What falls out goes into the network.

- **Attack**: at the weapon's own attack speed strikes the creatures in front with the tool (an iron sword when the slot is empty) at full strength and without knockback; the loot goes into the network. Players, pets and named creatures are never touched; spawn eggs in the filter choose the creatures, and **grown only** spares the young.

- **Sneaking**, the filter as an **allow or deny list** (a new bus has an empty deny list and works on every block; an **empty allow list** stops it - handy while you set it up), **grown only** (crops only when ripe) and the **Redstone Module**.

### Filters
<!-- {"id": "bus_filters", "icon": "minecraft:hopper", "x": 2, "y": 0, "parents": ["bus_import", "bus_export"]} -->

#### Setting up a filter

Right-clicking a bus opens a window with **9 ghost slots** - items placed in them are not consumed.

##### How to set

- Click with an item from the cursor - puts that item in the slot.

- LMB on a slot with an empty hand - +1 (the number does not matter for a filter).

- RMB - -1 or clear.

- Shift-click - clears the slot.

- Shift-click on an inventory item - puts a copy into the first free slot.

- **Middle-click** (the mouse wheel) on a filled slot opens the **slot window**, the same in every bus: with a Fuzzy Card a list of what may stand for the item - the item itself with any components, or one of its tags with how many items it holds; one click picks. Also clearing the slot, and on the Export Bus how much to keep in the container (see “Injector export bus”).

A **bucket (or any container holding fluid)** in a slot is a fluid filter. Because of this a “water bucket” item cannot be passed through a filter as an item: there it means water.

### Bus upgrades
<!-- {"id": "bus_upgrades", "icon": "golem_logix:compressed_coprocessor", "x": 2, "y": 1, "parents": ["bus_import", "bus_export"], "gold": true} -->

#### Upgrade slots

To the right of the bus window there is a column of **upgrade slots**, each for one kind of upgrade: its faint ghost shows which. Shift-clicking an upgrade in your inventory puts it into its free slot. The same holds in every machine with upgrades.

- **Compressed Co-Processor** - a speed upgrade: up to **4** in an import or export bus.

- **Autocraft Module** - export bus only: it orders what is missing from the Cryo-Calculators.

- **Redstone Module** - signal control (see “Redstone”).

- **Fuzzy Card** - fuzzy filters (see below).

The Periscope has two slots: the Redstone Module and the Fuzzy Card.

#### Compressed Co-Processor
<!-- {"type": "ritual", "station": "golem_logix:mana_saturator", "inputs": ["golem_logix:cooling_coprocessor"], "outputs": ["golem_logix:compressed_coprocessor"]} -->

> 1000 Source per ritual.

The Mana Saturator's golem flattens a Cooling Co-Processor into a thin pack of fins - a speed upgrade for buses and Cryo-Calculators.

#### Bus speed
<!-- {"type": "table", "widths": [60, 34, 54]} -->

| Upgrades | Per move | How often |
|---|---|---|
| none | 16 | 0.4 s |
| 1 | 32 | 0.3 s |
| 2 | 64 | 0.2 s |
| 3 | 96 | 0.1 s |
| 4 | 128 | 1 tick |

Fluids speed up the same way: 1000 mB per move without upgrades and up to 8000 mB with four.

<!-- {"type": "crafting", "recipe": "golem_logix:autocraft_module"} -->

An export bus with this module: if an item from the filter is not in the network but has a pattern, the bus orders crafting (a batch at most every 2 s) and delivers the result when it is ready. No new order while one is in progress.

<!-- {"type": "crafting", "recipe": "golem_logix:redstone_module"} -->

The Redstone Module: once installed, a mode button appears to the left of the window.

<!-- {"type": "crafting", "recipe": "golem_logix:fuzzy_card"} -->

Allows fuzzy filter matching in buses, the Periscope, or Interfaces: match items with any components (≈) or by tag (#). Pick tags in the slot window (middle-click or gear); Ctrl+click cycles tags.

### Redstone
<!-- {"id": "bus_redstone", "icon": "golem_logix:redstone_module", "x": 3, "y": 1, "parents": ["bus_upgrades"]} -->

#### Module modes
<!-- {"type": "table", "widths": [66, 82]} -->

| Mode | The machine works |
|---|---|
| Ignore | always |
| With signal | while powered |
| Without signal | while unpowered |
| Per pulse | one action per pulse |

The module decides when the machine may work. While it sits in an upgrade slot a mode button appears left of the machine window; each click picks the next mode.

#### For every machine

- **Import and export buses** - move items by signal; per pulse - one batch.

- **Periscope** - the storage is connected by signal (no pulse mode).

- **Stamping Gateway** - feeds ingredients by signal; per pulse - one batch.

- **Cryo-Calculator** - works on its job by signal (otherwise “Paused by redstone”); per pulse - one cycle.

- **Mana Saturator** - starts a ritual by signal; the module goes in with a right-click on the machine, the mode changes with another right-click with a module.

### Brass Stock Manometer
<!-- {"id": "bus_manometer", "icon": "golem_logix:stock_manometer", "x": 4, "y": 1, "parents": ["bus_redstone"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:stock_manometer"} -->

A directional brass dial with an animated analog gauge needle. Takes 1 channel.

#### Level emitter

The manometer monitors the network stock of a configured item or fluid (set in its ghost filter slot).

It emits redstone power (from all 6 sides or straight through connected conduits) according to its mode:

- **Above**: Emits signal (15) when the stock is strictly greater than the threshold.
- **Below**: Emits signal (15) when the stock is strictly less than the threshold.
- **Analog**: Emits a proportional signal from 0 to 15 based on `stock / threshold`.

#### Hysteresis and upgrades

Configure a **hysteresis window** (`threshold .. threshold + hyst`) to prevent rapid flickering on boundary values:
in Above mode, the signal turns on when above high threshold and off when falling below low threshold.

With an **Autocraft Module** installed, the manometer detects when an autocrafting order for the monitored item or fluid is active in the network, emitting redstone while crafting runs.

### Layouts
<!-- {"id": "bus_examples", "icon": "minecraft:furnace", "x": 3, "y": 0, "parents": ["bus_filters"]} -->

#### A furnace on autopilot

##### A self-running furnace

1. An export bus with coal in its filter faces the furnace from above - it supplies fuel.

2. A second export bus (with ore) faces the furnace from the side - it supplies ore.

3. An import bus faces the furnace from below and takes the ingots.

##### A factory

Feed ingredients to a machine with a Stamping Gateway and take the product with an import bus - this is the basis of **machine autocrafting**.

*Every bus takes a channel; with a dozen furnaces keep a reserve of channels.*

### Golem Interface
<!-- {"id": "bus_interface", "icon": "golem_logix:golem_interface", "x": 1, "y": 1, "parents": ["bus_intro"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:golem_interface"} -->

A copper cube with an intake and a network socket on **every** side. An import and an export bus in one block, on **one channel**.

#### Stock and intake

- The top row is **keep in stock**: drop a stack there and the interface keeps that many (left/right click: ±1).

- It takes what is missing from the network and returns the excess. The stock is taken from any side.

- Whatever is **inserted** goes into the network, crafting jobs first.

#### Fluids

Put a bucket in a stock slot and the interface keeps **the fluid itself**: a bucket = 1000 mB, up to 64 buckets. Pipes drain it from any side.

The switch under the slot: **drop** - fluid, **square** - the buckets themselves.

Fluid poured into the interface (by a pipe or a bucket) goes into the network.

#### Upgrades

Like the export bus: up to four **Compressed Co-Processors**, an **Autocraft Module** (orders stock the network lacks) and a **Redstone Module**.

While the interface works, the turbines in its intakes spin and the metronomes on its sides keep time - faster with speed upgrades.

## Autocrafting
<!-- {"id": "autocraft", "icon": "golem_logix:cryo_calculator", "color": "5ec8ff"} -->
> punch cards and cryo calculators

### From recipe to result
<!-- {"id": "ac_overview", "icon": "golem_logix:cryo_calculator", "x": 0, "y": 0, "gold": true} -->

#### The path of an order

Autocrafting is a chain of four links:

1. The **Encoder** punches a recipe onto a copper punch card.

2. The **Stamping Gateway** stores the cards and feeds ingredients into machines.

3. The **Cryo-Calculator** breaks an order into steps and carries them out.

4. You order the result in a **terminal** - it lands in the network.

#### What it can do

- Crafting-table 3×3 recipes are carried out right in the calculator.

- “Machine” recipes (furnaces, crushers, altars) go through the Gateway and an import bus.

- The recipe tree unfolds by itself: intermediates are taken from storage, the rest is made by other patterns.

- When an order is ready, whoever placed it gets a notification.

- An order always makes **the ordered amount** - even if such an item is already in storage.

#### The language of punch cards

A golem does not understand words. It understands holes.

A punch card is an order that cannot be misread: every punched cell is one rune of the recipe. The golem reads the card with its fingers, the way the blind read raised letters, and does exactly what is punched.

*That is why the masters guard their Encoder: one mistake on a card is a mistake in a thousand items.*

### Punch cards and the Encoder
<!-- {"id": "ac_cards", "icon": "golem_logix:pattern_encoder", "x": 1, "y": -1, "parents": ["ac_overview"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:copper_punch_card"} -->

Blank cards: four from paper and copper.

<!-- {"type": "crafting", "recipe": "golem_logix:pattern_encoder"} -->

The Encoder takes 1 channel, but encoding does not need the network.

#### How to encode

In the window: a 3×3 grid on the left, up to three outputs on the right, a blank-card slot and a finished-card slot.

##### “Crafting” mode

Lay out the recipe - the result appears by itself. No recipe - “Punch” refuses.

##### “Machine” mode

Specify the inputs (with amounts) and up to three outputs. Click +1, RMB -1, Shift clears.

##### Buttons

“Punch” writes the card, “Read” loads a card's recipe for editing, “Clear” wipes the grid.

An encoded card glints and lists its inputs and outputs in the tooltip. In a recipe viewer, “+” on a crafting recipe fills the grid; on a furnace or machine recipe it switches to “Machine” mode with the inputs and outputs.

#### Fuzzy mode

The **≈** button turns on **fuzzy mode**: the card accepts not only the exact item it was written with but also its relatives. The exact item is still taken first, the others only when it runs out.

##### Crafting table

Each cell takes what the recipe accepts there: any planks for a crafting table. The recipe always works.

#### Fuzzy machine inputs

An input takes the same item with any components (≈ on the slot).

A **middle-click** (Ctrl+click) on it chooses a tag (#): with **#c:glass_blocks** any glass will do. Each click goes to the next tag of the item, then back to none.

The card's tooltip lists the chosen tags.

### Stamping Gateway
<!-- {"id": "ac_provider", "icon": "golem_logix:pattern_provider", "x": 1, "y": 0, "parents": ["ac_overview"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:pattern_provider"} -->

Holds up to **9 cards** and feeds ingredients into adjacent machines. Takes 1 channel.

#### How it feeds

- Right-click and put in encoded cards - the patterns are at once available to the whole network.

- For machine recipes the Gateway looks for an adjacent **non-network** block with an inventory (furnace, crate, machine, or an assembled Mana Saturator) and puts all ingredients of one batch into it.

- With several neighbors it tries them in turn. If a machine cannot take everything at once, the batch is postponed and retried.

- The button to the left of the window picks the **output side**: all sides or a single one — then the gateway feeds only the machine on that side. Handy when machines stand side by side. The arrow on the block's faces points at the chosen side; a sun with rays means it feeds all sides.

- The result comes back into the **return slots** (the column on the right of the window) and goes straight into the network. The mod's machines put it there themselves (auto-output); for a furnace or another mod's machine let it push into the Gateway, or put a **Vacuum Import Bus** next to it.

- A **fluid result** goes into the Gateway's three **return tanks** of 16 buckets: a machine or a pipe pours it in from any side, and it goes straight on to the job or the network.

*Crafting-table recipes do not concern the gateway: the calculator carries them out.*

#### Waiting for the result

The **clock** button to the left of the window turns waiting on: until the result of the previous batch has come back to the job (through the return slots or an import bus), the next batch of ingredients **is not loaded**.

This is for machines that get confused by the ingredients of two recipes at once, or that make one item at a time.

Without waiting (a hopper on the button) the Gateway loads batches as soon as the machine accepts them.

The slot to the right of the window takes a **Redstone Module**.

### Cryo-Calculator
<!-- {"id": "ac_cpu", "icon": "golem_logix:cryo_calculator", "x": 1, "y": 1, "parents": ["ac_overview"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:cryo_calculator"} -->

An icy calculator with frost crystals. Carries out **one job** at a time. Takes 1 channel.

<!-- {"type": "crafting", "recipe": "golem_logix:cooling_coprocessor"} -->

The cooling co-processor is a finned radiator. Takes no channels; speeds up the **adjacent** calculator. While it works the fins pump like a flywheel: they squeeze and spread, smoothly gaining and losing momentum.

#### Speed

The calculator works in cycles - five times a second - and does up to **1 + 2×N** steps per cycle, where N is the number of co-processors touching it (up to 6). Each **Compressed Co-Processor** in the window's slots adds **4** more (up to four of them): up to 29 steps per cycle.

Each step costs **2 Source**.

##### The calculator window

Right-click shows the target, progress “steps/total”, status, number of co-processors, items inside and an “Abort” button. On the right - slots for Compressed Co-Processors and the Redstone Module.

*Several calculators work in parallel - each takes its own order.*

#### You can see it think

While a job is running:

- a **miniature model** of what it is making right now (the output of the current step) spins above the calculator;

- a **crew of snowmen** of different sizes builds it on the roof: they bend down for an ice part, lift it over their heads and toss it into the model;

- turquoise “matrix rain” runs down its side panels.

When the job ends the model melts away, the rain stops and the snowmen goof off: they look around, hop in circles, wave and doze.

##### The foreman

When co-processors **actually** speed the work up (more than one step done in a cycle), a snowman with a **black atlant head** appears and urges the others on: it waves its stick arm and points at the model. Nothing to speed up — the foreman leaves.

#### Job states
<!-- {"type": "table", "widths": [70, 78]} -->

| State | Meaning |
|---|---|
| Computing | working |
| Waiting for machine | needs an import bus |
| No gateway | no gateway has it |
| No machine | nothing at the gateway |

#### Job states (more)
<!-- {"type": "table", "widths": [70, 78]} -->

| State | Meaning |
|---|---|
| Not enough Source | no energy |
| Invalid pattern | recipe is gone |
| Delivering | storage full |
| Paused | Redstone Module |

*When an order finishes (or a job of 16 or more crafts completes), the calculator rings a bright typewriter bell.*

### Ordering in the terminal
<!-- {"id": "ac_order", "icon": "golem_logix:crafting_terminal", "x": 2, "y": 0, "parents": ["ac_cards", "ac_provider", "ac_cpu"], "gold": true} -->

#### How to order

1. In the terminal switch on the “Craft” filter: only items that have a pattern remain.

2. **Ctrl+LMB** on an item (or a click on an item with zero stock - it is marked “C”) opens the order window.

3. Enter the amount or press ±1, ±10, ±100.

4. “Preview” shows in chat what is needed and what is missing. “Start” launches the job.

Finished items land in storage, and an **“Order ready”** plate with the item and its count pops up in the corner of your screen; several orders take turns on it.

*From a recipe viewer the missing ingredients are ordered with one button: Ctrl + “+” (see “Crafting Terminal”).*

#### How the plan is built

The planner descends the recipe tree:

- takes what is needed from the network (surplus from earlier steps first);

- makes what is missing from patterns, and unfolds their ingredients in turn;

- marks cycles and trees that are too deep as “missing”.

Before starting, all raw materials are **reserved** - they leave the network into the calculator, so nobody can take them on the way. If you abort the job, everything returns to the network.

*Very large orders (tens of thousands of steps) are refused - split them into parts.*

### Cryo-Mainframe
<!-- {"id": "ac_mainframe", "icon": "golem_logix:cryo_mainframe", "x": 2, "y": 2, "parents": ["ac_cpu"], "gold": true} -->

<!-- {"type": "crafting", "recipe": "golem_logix:cryo_mainframe"} -->

A cabinet for **12 Cryo Calculators**: for when autocrafting outgrows rows of calculators wrapped in co-processors. The whole cabinet takes **one channel**.

#### Materials
<!-- {"type": "checklist", "variant": "mainframe"} -->

Checked against your inventory. The button shows ghost blocks: the Mainframe at the bottom middle, glass around it, casings behind, atlants at the sides.

The ghosts follow the block you look at; **Y** locks them, **N** cancels. The cabinet's front faces you.

#### Assembly

A 3 x 2 x 2 cabinet with two atlants at the sides — they are what holds it:

- front: the Cryo-Mainframe at the bottom middle with **5 glass blocks** around it;

- back row: **6 Atlas Casings**;

- at both sides of the front row — an **Atlant**.

The cabinet assembles itself (checked every second); right-click an unassembled one to learn what is missing and where. When it assembles the atlants are **built into** the cabinet: their torsos come straight out of its sides, the outer arm wraps around the front corner and grips the post, the inner one rests on the roof crest, and their heads still follow you.

Break any part and the cabinet comes apart, the other blocks come back (a broken atlant drops whole). A network pipe can go to any part.

#### Cores and the multiplier

The window has two shelves of six slots for Cryo Calculators and an upgrade column on the right.

- Every calculator inside gives **2 cores**. A core is an independent CPU: it takes its own order, just like a separate calculator. 12 calculators — 24 parallel orders on one channel.

- Every **Compressed Co-processor** (up to six) counts **twice** and speeds up **all cores at once**: a core does 1 + 8×N steps per cycle — up to 49 with six.

The Recipe Monitor lists the cabinet's cores as separate rows (#1, #2...).

*An icy circuit keeps the thoughts of twelve calculators from overheating and tangling with one another.*

#### Behind the glass

While anyone has the cabinet's window open, its **glass doors swing open**; close the window and they close.

Inside a turquoise matrix glows, the calculators you put in stand on the shelves, and in front of them **snowman crews** work — one per shelf, each building its own item. When co-processors really speed the work up, a foreman with an atlant head shows up below.

A **copper golem** turns the red valve wheel on top while the cabinet is busy, and a row of turquoise lights runs along the crest. Copper archivist golems walk the shelves bringing boxes of parts to the snowmen.

### Punch Card Catalog
<!-- {"id": "ac_catalog", "icon": "golem_logix:punch_card_catalog", "x": 2, "y": -1, "parents": ["ac_provider", "ac_cards"], "gold": true} -->

<!-- {"type": "crafting", "recipe": "golem_logix:punch_card_catalog"} -->

A filing cabinet for hundreds of punch cards with **crafting-table recipes** — no more rows of Stamping Gateways just to hold them. The whole cabinet takes **one channel**.

#### Materials
<!-- {"type": "checklist", "variant": "catalog"} -->

Checked against your inventory. The button shows ghost blocks: the Catalog at the bottom middle, bookshelves around it, copper blocks behind, copper blocks with copper golem statues at the sides.

The ghosts follow the block you look at; **Y** locks them, **N** cancels. The cabinet's front faces you.

#### Assembly

A 3 x 2 x 2 cabinet with two copper golems at the sides:

- front: the Punch Card Catalog at the bottom middle with **5 bookshelves** around it;

- back row: **6 copper blocks** (any oxidation, waxed too);

- at both sides of the front row — a copper block with a **copper golem statue** on it (any oxidation).

The cabinet assembles itself (checked every second); right-click an unassembled one to learn what is missing and where. When it assembles the statues come alive: the golems grow into the sides with a shoulder, hold the roof corner with the near arm and a punch card in the far one. They keep the statues' oxidation.

Break any part and the cabinet comes apart; every block comes back as it was (same oxidation and wax). The cards stay inside.

#### Places and gateways

The middle of the window is a scrolling grid of card places; on the right are a Redstone Module slot and, below it, the **Stamping Gateway slot**.

- Put ordinary Stamping Gateways there (up to a stack): **each one opens 27 places**. 64 gateways — 1728 cards in one cabinet.

- Places without gateways are locked (bars). Gateways whose places hold cards cannot be taken out — take the cards out first.

- Only cards with **crafting-table** recipes fit: the calculators carry them out themselves, no machine needed. Machine cards stay in gateways next to their machines.

- The search under the grid looks at the recipe result; the mouse wheel, the scrollbar and PageUp/PageDown scroll.

*Each gateway adds another rack of archive drawers.*

#### Redstone and drawers

With a **Redstone Module** the catalog's patterns are switched on and off by a signal (with signal, without signal, ignore).

While the window is open the drawers slide out one after another showing the cards, the letter drums on the roof flip, steam pours from the chimney and the golems wave their cards.

The turquoise screen in the middle searches the list for a card; the gauge under it shows how full the catalog is.

### Cryo Schematic Concentrator
<!-- {"id": "ac_concentrator", "icon": "golem_logix:schematic_concentrator", "x": 3, "y": -1, "parents": ["ac_catalog"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:schematic_concentrator"} -->

A rusty cube with a holographic screen showing **every punch card in the network** from all gateways and catalogs, grouped by machine with search. Swap cards remotely. Takes 1 channel.

#### Categories

Right-click to open a list of every Stamping Gateway of the network (with a channel) and the places of every Catalog, by category:

- **Crafting table** — gateways with only crafting cards, empty gateways with no machine, and all Catalogs;

- then by the **machine** a gateway feeds: its block's icon and name (all gateways at furnaces share one category);

- last — **No machine**: gateways with machine cards but no machine next to them (worth a look!).

Hover the icon to the left of a row to see where the gateway stands and which machine it feeds.

#### Working with cards

The window's slots are the gateways' real slots: cards can be **taken out and put in** right here.

Shift-click a card in your inventory to put it into the first free slot of its kind (crafting or machine).

The search at the bottom keeps only rows with matching results.

*While the window is open, holographic punch cards fan out in front of the screen.*

### Recipe Monitor
<!-- {"id": "ac_monitor", "icon": "golem_logix:recipe_monitor", "x": 2, "y": 1, "parents": ["ac_cpu"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:recipe_monitor"} -->

A brass cabinet with a big porthole behind which a violet vortex swirls. Takes 1 channel.

#### A desk for every order

Right-click opens the desk: on the left - **every Cryo-Calculator of the network** (what it makes, how far along, its state), on the right - the details of the selected one:

- what was ordered and how much,

- what is being made right now,

- how much is left of every step,

- what is expected from machines,

- the **“Abort”** button - everything inside the job returns to the network.

*Punch cards float in a circle inside the porthole - one for every running job.*

*When a craft is blocked (missing materials or waiting for an output), its indicator blinks red and ticks softly once a second.*

### Copper Order Ledger
<!-- {"id": "ac_stock_requester", "icon": "golem_logix:stock_requester", "x": 3, "y": 1, "parents": ["ac_monitor"]} -->

<!-- {"type": "crafting", "recipe": "golem_logix:stock_requester"} -->

A desk with a 3×3 copper order ledger. Takes 1 channel.

#### Maintaining network stock

The ledger keeps a specified reserve of up to 9 items or fluids in the network by automatically submitting autocrafting orders.

Set items and desired keep amounts in its 9 ghost filter slots.

#### Smart dispatch

- Dispatches small batches (up to 64 items or 1000 mB) so the network is not overwhelmed.
- Maintains at most 1 active job per configured item at a time, avoiding duplicate requests while prior batches are in flight.
- Slot status indicators in the corner of each cell:
  - **Green**: Stock satisfied or waiting.
  - **Cyan**: Autocrafting order currently running.
  - **Amber**: No pattern registered for this recipe.
  - **Red**: Missing ingredients in storage.

### Machine steps
<!-- {"id": "ac_machines", "icon": "minecraft:blast_furnace", "x": 3, "y": 0, "parents": ["ac_order"]} -->

#### To the machine and back

When a pattern is a machine recipe, the calculator hands a batch to the Gateway, which puts the ingredients into the adjacent machine. The calculator remembers: **waiting for the output of this batch**.

The output is returned by a **Vacuum Import Bus** at the machine. Seeing an awaited item, the bus hands it straight to the job - bypassing storage.

##### Example: ingots from ore

1. Pattern: 1 raw iron → 1 ingot (“Machine” mode).

2. The Gateway stands against the furnace; an import bus is attached to the furnace output.

3. Order “ingots ×64” - the Gateway loads ore, the bus returns ingots, the job counts them done.

### Liquid Cryo-Source
<!-- {"id": "ac_cryo", "icon": "golem_logix:fluid_ghost", "x": 3, "y": 1, "parents": ["ac_machines"]} -->

#### The network's own fluid

Liquid Cryo-Source is **not stored** anywhere: the network makes it from its Source the moment automation pumps it out, **1 mB for 1 Source**.

Disks do not take it and terminals do not show it. **No bucket** or other container can be filled with it by hand: only automation pumps it out.

#### Pumping it out
<!-- {"type": "image", "texture": "cryo_source", "size": 128} -->

Export Bus → tank, Gateway → machine.

#### Who pumps it

**Export Bus**: with cryo-source in its filter it pours it into the tank or machine in front of it.

**Stamping Gateway**: a pattern with a cryo-source ingredient pours it into the machine along with the items.

**Golem Interface**: a cryo-source stock slot keeps that much in its tank for pipes and machines.

#### Setting the fluid

Click a filter, stock or pattern slot with a **Cryo-Source Jar** (any Source jar works too) - Liquid Cryo-Source appears in it. From a recipe viewer it can also simply be dragged in.

A fluid in a slot counts mB: a click adds **250 mB**, a right-click takes 250 away, Shift-click clears the slot.

#### Fluids in patterns

In “Machine” mode the recipe viewer's “+” carries fluid ingredients and **fluid results** over with their amounts; by hand a fluid goes into an output cell as into an input. A fluid is never an ingredient or a result of the crafting table.

A fluid a pattern makes is ordered from the terminal like an item: it is marked “C”, the amount in mB. The machine gives it back into the Gateway's tanks (or an import bus pumps it out).

The Gateway pours fluids into the **same side** of the machine as the items, and only if it takes everything. The order planner shows fluids in mB.

Unused cryo-source (a cancelled job, an Interface's excess) is not returned: it goes back into the aether.

### Common problems
<!-- {"id": "ac_faq", "icon": "minecraft:barrier", "x": 4, "y": 0, "parents": ["ac_machines"]} -->

#### What to do if...

- **A job is stuck on “Waiting for machine”** - there is no import bus at the output or the output does not match the pattern.

- **No pattern** - the Gateway did not find a card: put it into a Gateway that is connected and has a channel.

- **No calculator** - all are busy or have no channel.

- **Not enough resources** - “Preview” lists exactly what.

- **Storage is full** - the job hangs on “Delivering” and finishes as soon as disk space frees up.

- **“Paused by redstone”** - check the signal and the Redstone Module mode.

*Any job can be aborted in the calculator window or in the Recipe Monitor: everything inside returns to the network.*

## Chronometer and late game
<!-- {"id": "endgame", "icon": "golem_logix:portable_chronometer", "color": "c08cff"} -->
> wireless access and the eternal aether

### Portable Chronometer
<!-- {"id": "eg_chrono", "icon": "golem_logix:portable_chronometer", "x": 0, "y": 0, "gold": true} -->

<!-- {"type": "crafting", "recipe": "golem_logix:portable_chronometer"} -->

Pocket watch with a mini oscilloscope: a wireless crafting terminal for your network. The wither skeleton skull powers its dark cathode tube - explaining the frantic Wither silhouette projected onto the screen.

#### Binding and use

1. **Shift+right-click on the Atlas Core** - the watch binds to it (the tooltip shows coordinates and world).

2. **Right-click in the air** - a network terminal opens **with a crafting grid**: everything the Crafting Terminal can do, including the “+” button and precraft.

- The chronometer takes no channels.

- While the window is open the range is checked constantly: leave the zone and the window closes.

- The network must be powered and the core loaded.

*The chronometer itself is a little brass device with a green screen: a flat Wither on it keeps trying to shoot a skull right through the glass - at you.*

#### Source charge

The chronometer runs on its own store of **Source** (up to 10,000): opening the window costs 20, and while it is open it uses 2 per second. The charge shows as a bar on the item and as a violet gauge next to the crafting grid.

**Right-click a Cryo-Source Jar** (or any Source jar) with the chronometer to charge it, as far as the jar allows.

If an **Eternal Frost Source Resonator** is attached to the network, the watch never needs charging again: the resonator sends it Source through the aether wherever you are.

*If the world has no way to get Source (neither Neo Vitae nor Ars Nouveau), no charge is needed at all.*

#### Hotkey

**Ctrl+E** opens the chronometer without taking it in hand: it can lie anywhere in your inventory or in an accessory slot. The key can be changed in the controls (the Golem Logix section).

*Where accessory slots exist, the watch gets a slot of its own - and your hotbar stays free.*

### Range and upgrades
<!-- {"id": "eg_tiers", "icon": "minecraft:ender_eye", "x": 1, "y": 0, "parents": ["eg_chrono"]} -->

#### Tiers
<!-- {"type": "table", "widths": [86, 62]} -->

| Tier | Range |
|---|---|
| Local | 200 blocks, same world |
| Dimension | anywhere in the same world |
| Cross-dimension | across any dimensions |

#### Chronometer: dimension
<!-- {"type": "crafting", "grid": ["golem_logix:portable_chronometer", "minecraft:ender_eye", "golem_logix:flattened_copper_matrix", "", "", "", "", "", ""], "result": "golem_logix:portable_chronometer"} -->

An upgrade in the crafting table; cell order does not matter. The binding and the charge are kept.

#### Chronometer: cross-dimension
<!-- {"type": "crafting", "grid": ["golem_logix:portable_chronometer", "golem_logix:interdimensional_occult_matrix", "", "", "", "", "", "", ""], "result": "golem_logix:portable_chronometer"} -->

Needs a chronometer of the “Dimension” tier: you cannot skip a step. Where the matrix comes from is a secret you have yet to uncover.

### Eternal Frost Source Resonator
<!-- {"id": "eg_resonator", "icon": "golem_logix:eternal_frost_resonator", "x": 2, "y": 0, "parents": ["eg_tiers"], "gold": true} -->

<!-- {"type": "crafting", "recipe": "golem_logix:eternal_frost_resonator"} -->

A rusty copper body in a brass frame with a big turquoise dial; above it a glass chamber where a snow vortex swirls and rises into the chimney; frosted pipes and icicles at the sides. It is placed facing you. Its recipe holds a clock (the link with the chronometers) and an **Interdimensional Occult Matrix**.

#### Source through the aether

The resonator draws Source from the ethereal vacuum and **sends it through the aether to the Portable Chronometers** bound to its network - they never need charging, even in another dimension.

Put it against any node of the network: snowflakes start falling around it — the link is up.

- It does **not feed the network**: the Core still takes Source from jars.

- It does **not fill Source jars**.

*One resonator is enough for every chronometer of the network.*

### Creative Source Jar
<!-- {"id": "eg_creative", "icon": "golem_logix:creative_source_jar", "x": 3, "y": 0, "parents": ["eg_resonator"]} -->

#### For testing

A bottle of turquoise Source with a cork. Creative mode only.

An infinite source for the network and for the jars next to it (it tops them up with 10,000 every second). A chronometer or a hammer charges to the top from it.

Handy for trying out a network layout without spending resources on Source.

## Secret Knowledge
<!-- {"id": "secrets", "icon": "minecraft:nether_star", "color": "b070ff"} -->
> not for everyone

### Secret knowledge
<!-- {"id": "secret_hint", "icon": "minecraft:nether_star", "x": 0, "y": 0, "secret": "partial"} -->

#### What the star whispered
<!-- {"when": "wither"} -->

The Wither is defeated, and its star still echoes the summoning. Its power can be caught not in battle but at the very start - while the Wither is only “assembling”.

But the knowledge is incomplete: what is that power for? It is sought by those for whom one world is too small.

*Try to open a Portable Chronometer in another dimension - where the network core can no longer be heard.*

#### A voice beyond the border
<!-- {"when": "dimension"} -->

The Chronometer could not reach the core: the border between worlds muffles any signal. To break through, you need a power that tears the fabric of dimensions itself.

Only one creature holds such power - the Wither.

*Defeat the Wither, and the secret will be revealed in full.*

#### Two trials
<!-- {"type": "tasks", "tasks": [{"id": "wither", "text": "Defeat the Wither"}, {"id": "dimension", "text": "Try to open a Chronometer in another dimension"}]} -->

The order does not matter. Each trial reveals half of the knowledge, both - the whole recipe.

#### The secret is revealed
<!-- {"when": "both"} -->

Both halves of the knowledge have come together. Read **“The Wither's secret”**, **“Interdimensional Occult Matrix”** and **“Plasma Temper”** - they have appeared next to this article on the map.

### The Wither's secret
<!-- {"id": "wither", "icon": "minecraft:nether_star", "x": 1, "y": 0, "parents": ["secret_hint"], "secret": "full"} -->

#### Imploded plasma

The strangest plate comes from the most dangerous mob. While the Wither is just being summoned it is still invulnerable and “assembling”. In those first moments it can be **imploded**.

Drop an anvil on the forming Wither before its summoning timer runs on (the first 4 seconds). Instead of a fight you get **Imploded Wither Plasma**, and the Wither vanishes.

*If you are late, the anvil merely hits a regular Wither - and nothing happens.*

Plasma is the key to the **Interdimensional Occult Matrix**.

#### Breaking the summoning
<!-- {"type": "ritual", "station": "minecraft:anvil", "inputs": ["minecraft:nether_star"], "outputs": ["golem_logix:imploded_wither_plasma"]} -->

> The first seconds of the Wither summoning.

Bring a stack of anvils and a careful build: keep a frame of blocks overhead so the anvil lands exactly.

### Plasma Temper
<!-- {"id": "plasma_temper", "icon": "golem_logix:plasma_temper", "x": 2, "y": 1, "parents": ["wither"], "secret": "full"} -->

<!-- {"type": "crafting", "recipe": "golem_logix:plasma_temper"} -->

The Wither's plasma does not let things fade. Sealed in a brass bezel on a tempered steel bar, it passes that on to whatever the golem holds.

#### The tool that never wears

An upgrade for **Hewer's Annihilation Bus**: put it into its upgrade slots.

The tool in the golem's slot then **never wears** - mining or clicking, a pickaxe, an axe or shears keep their durability, enchantments and all.

Even a tool on its last point of durability goes back to work.

*A netherite pickaxe with Fortune, tempered once, digs for ever.*

### Interdimensional Occult Matrix
<!-- {"id": "matrix", "icon": "golem_logix:interdimensional_occult_matrix", "x": 2, "y": 0, "parents": ["wither", "saturator"], "secret": "full"} -->

#### A gate between worlds
<!-- {"type": "ritual", "station": "golem_logix:mana_saturator", "inputs": ["golem_logix:imploded_wither_plasma"], "outputs": ["golem_logix:interdimensional_occult_matrix"]} -->

> 2500 Source per ritual.

One ritual turns the plasma into **one** matrix. It goes into:

- the Chronometer's cross-dimension upgrade,

- the **Quantum Channel Expander**,

- the **Eternal Frost Source Resonator**,

- the telegraph's **Void-Glass Prism**.

### Void-Glass Prism
<!-- {"id": "telegraph_prism", "icon": "golem_logix:void_glass_prism", "x": 3, "y": 0, "parents": ["matrix"], "secret": "full"} -->

<!-- {"type": "crafting", "recipe": "golem_logix:void_glass_prism"} -->

The range card without limits: a Cryo-Source Telegraph with it links to its partner in **any dimension**.

#### An outpost beyond the border

An outpost in the Nether or the End joins your base network through a pair of telegraphs: its buses, terminals and cabinets work as if they stood at home.

One prism on either end is enough. Both telegraphs must be loaded - keep the outpost's chunks loaded if it has to work while you are away.
