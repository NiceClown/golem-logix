# How Golem Logix is made

Golem Logix is an author-driven project. Its idea, its world, its economy, every mechanic and every scene on screen
were conceived, specified, judged and accepted by its author, **NiceClown**, over three weeks of daily iteration.
Generative AI was used as a production tool, mostly for writing code, and the second half of this page says
exactly where.

## The author's work

### Concept and world
- The founding idea: "cold, occult, analog logistics", a storage network where every block is a working machine
  with a golem crew, and blocks mean something in the world instead of doing cold math in a cube.
- The project started from a design document written by the author.
- The lore of the setting, e.g. the Cryo-Distillation Tower that "freezes all the living warmth and chaos out of
  blood" and condenses it into icy Cryo-Source.
- The visual language: brass, copper, frost, runes, amber screens, golems at work.

### Progression and economy
- The plate economy at the heart of the mod: three base plates (iron core, copper matrix, cryo-substrate) are the
  only ones that can be made by hand; everything else is built on them.
- Four routes to plates, each with its place:
  - the anvil ritual on a battered golem;
  - hand forging on a magic anvil with a Forge Hammer that has its own Source charge (filled like the Chronometer,
    never drained from jars);
  - the Mana Saturator multiblock as the upgrade of that anvil, which must stay the cheapest route;
  - Create assembly lines of at least three devices and three passes.
- Progression gates:
  - the Saturator is locked behind the End (purpur, End rods), "still simple, but limited by the player's progress";
  - the hammer recipe needs an iron plate;
  - the secret recipe needs two deeds in any order: killing the Wither and opening a wireless terminal in another
    dimension, with a "secret knowledge" achievement in between;
  - the infinite-range telegraph card sits in the secret chapter.
- The network's growth in steps: the core alone gives 8 channels, the starter tower 16, the full Atlas Array 64 per
  bushing, and the channel expanders give 64 per face so players need not rebuild their base.
- Cross-mod balance:
  - Source Gems in the Saturator, twice as fast as the Imbuement Chamber but 33% dearer;
  - Neo Vitae blood as a Source supply for versions without Ars Nouveau, keeping both paths working;
  - the Copper Age Backport kept optional for pack makers.
- The Cryo-Mixer as an in-mod use of Liquid Cryo-Source: fast snow for snow golems (the plates for pipes), slow
  ice for Packed Ice and Cryo-Substrate.

### Network and logistics systems
- **Channels.**
  - Channel limits and their expansion.
  - Pipe Plugs to keep lines apart.
  - The decision to rebuild channels on Applied Energistics 2's proven model, with matching Jade tooltips.
  - Channel info on bushings in tooltips and chat.
- **Upgrades.**
  - One upgrade system for every machine: up to four speed upgrades, an Autocraft Module and a Redstone Module.
  - Compressed Co-Processors squeezed in the Saturator to speed up buses, calculators and the tower.
  - Priorities on every bus and cabinet.
- **The Golem Interface.**
  - Keeps stock and takes items into the network to save channels.
  - A per-slot item or fluid mode.
  - A gear button for detailed amounts.
  - A hand-fed "into the network" slot.
- **The Stamping Gateway.**
  - Wait-for-result mode.
  - A chosen feeding side shown by an arrow on the block, or a "sun" when it feeds every side.
  - Return slots to save buses and channels.
- **Auto-output,** on by default for every machine, with the Gateway first.
- **Autocrafting at scale.**
  - The Cryo Mainframe holds many calculators, with a multiplier.
  - The Punch Card Catalog stacks providers into one scrolling multiblock.
  - The Schematic Concentrator shows every provider grouped by machine.
  - The Recipe Monitor watches every job in the network.
- **The Cryo-Source Telegraph,** a 3-block relay to distant outposts:
  - link cards that record a telegraph;
  - two mutually exclusive, labelled slots (write and memory);
  - a big reset button;
  - range cards matching the Chronometer.
- **Terminals.**
  - A combined item-and-fluid mode on by default, with a bucket counted as one item.
  - Filling fluids with a bucket.
  - Windows that stretch to the full screen height.
  - JEI search as a switchable option.
  - Precraft with Ctrl+"+".
  - Order-ready notifications and a resource planner in the order window.
  - Right-click clears search.
- **Punch cards and the encoder.**
  - Blank cards stack until written.
  - The encoder saves blank cards, edits written ones, and Shift+right-click wipes them.
  - Holding Shift shows what a card makes.
- **The Portable Chronometer,** "almost the top of the mod's evolution":
  - a built-in crafting table;
  - charged from afar by the Eternal Frost Resonator (which never fills Ars jars);
  - a Curios slot and Ctrl+E;
  - cannot be moved while its window is open.
- **Pipe warehouses:** the author's own addition, built in his own pass between sessions from vanilla animations and
  built-in blocks, then polished together with the AI assistant.

### The life of the machines (scenes the author designed)
- **Snowmen in the pipes.**
  - A snowman runner carries the cargo through pipes, one per connected pipe network so performance holds.
  - Pipes glow softly while the network has Source, without blocking mob spawns.
- **Terminals and screens.**
  - The CRT terminal waves full-screen when the network starts.
  - Small golems haul blocks across the monitors.
- **The drive bay.**
  - Spinning tape reels show disks being read.
  - Doors in the middle wall let the archivist golems walk through instead of through textures.
  - The back panel is alive.
- **Calculators.**
  - Snowmen of different sizes assemble a projection of the item being crafted, and do something cute when idle.
  - An Atlant-headed foreman appears only when co-processors really speed the work up.
  - Co-processors pump like flywheels and start and stop smoothly.
  - Animations have a minimum length.
- **The Atlas Array.**
  - Atlant heads follow the player.
  - A violet light wanders each face.
  - A blue light fills the bushing's strip.
  - Copper golems circle the core.
- **The Mana Saturator.**
  - An iron golem smith hammers the blanks.
  - Two independent copper golems bring the blanks and take the plates away.
  - The decks show the queue and the results.
  - Gears, fans and amber screens on the back.
  - A lamp golem on the roof.
- **The Recipe Monitor.** A copper golem carries a lamp: violet when idle, blinking green at work, red on failure;
  it walks the platform when nothing is crafting.
- **The Cryo Mainframe.** The glass opens when the window opens; Atlants grow out of the cabinet as in the concept.
- **The Cryo-Source Telegraph.** An operator golem taps Morse on a key in rhythm; a lamp lights with each stroke
  and a pulse runs up the mast.
- **The Chronometer.** A flat Wither on its green screen keeps firing at you.
- **The buses come alive.** A turbine spins, an injector blinks, and a monocle has an ender eye that looks around.
- **Golems step aside** when a jar, a tower or a pipe stands where they would.

### Guide book and usability
- A guide in the spirit of Occultism and old Thaumcraft, with detailed descriptions of every process.
- Zoom with the mouse wheel.
- A materials checklist that checks the player's inventory.
- Ghost blocks that show where each block goes, fixed and cancelled with Y and N (with priority over other mods'
  keys).
- The book remembers the last page.
- No config or system talk in the book, no references to other storage mods.
- The core chapter built as consecutive steps.
- JEI pages designed by the author:
  - the anvil ritual laid out as anvil, arrow, golem, hearts, equals, plates;
  - a projection of the whole Saturator;
  - hand forging shown as a block plus four hammer strikes.

### Art direction and quality control
- The visual concept of each signature machine: the Saturator, the Interface, the Mainframe, the Catalog, the
  Concentrator, the Telegraph and its cards, the jar and the tower, and the Cryo-Mixer. The author described each one
  (form, crew, how it moves), steered internal concept images through his prompts and picked the ones to build from.
  These images were only private references for the pixel art (see below); none of them is in the mod or on its pages.
- References for the cabinet, the pipes and the buses.
- Concrete form decisions:
  - compact half-block buses with sockets on every side and mini-tubes between them;
  - flat metronomes on the Interface;
  - a solid back for the cabinet;
  - platforms under the tower and the jars;
  - snowmen holding their crates in their hands.
- Every model, texture and animation was judged in game. The author rejected crude versions, found flicker, holes,
  misplaced golems and broken animations, and sent them back until they were right.
- Playtesting in singleplayer and on a server, an external audit of common storage-mod pitfalls, and attention to
  server performance with this many animations.

### Direction of the project
- Versions and numbering, the port to Minecraft 26.1.2, which integrations exist (JEI, Jade, Curios, Ars Nouveau,
  Create, Neo Vitae).
- An English-first repository with the Russian translation kept, and the MIT licence.

## Where generative AI was used

- **Code.** Most of the Java code was written with AI coding tools, mainly Claude by Anthropic,
  from the author's written requests, plans and idea notes, then checked by the author in game. The repository
  history shows this openly (`Co-Authored-By` lines).
- **Textures and models: code-driven pixel art, no image generators.** No text-to-image model was used for any texture,
  model, icon or image in the mod. The pixel art is drawn by the project's own procedural tools (`tools/*.java`:
  palettes, shapes, runes, animation frames). Those tools were written with AI assistance to the author's art
  direction, and the author accepted every result on screen.
- **The project icon** is an in-game screenshot of the Atlas Core structure, taken by the author.
  (The small logo inside the mod, shown in the mod list, is drawn by the same procedural tools as the textures.)
- **Concept references.** Most of the private concept images that guided the machines' look were made with an image
  generator (Google Gemini) from the author's descriptions. They were used only as mood boards: the mod's textures and
  models are not traced or converted from them, and they are not published anywhere.
- **Texts.** The guide's prose, tooltips, changelog and README were drafted with AI from the author's descriptions of
  the mechanics.
  The Russian translation was proofread by the author, a native speaker.
- **Automated tests.** 112 in-game tests check the mechanics on every build; they were written with AI assistance
  alongside the code.
- **Videos and previews.** All footage is real in-game recording of the mod, with no generated or retouched imagery.
  The editing, captions and synthesised music were produced by scripts written with AI assistance.

## What is NOT AI

- No AI-generated images in the mod, on the project page or in the gallery (the private concept references above
  were never published).
- No generative AI inside the mod: golems run on ordinary game logic; the mod calls no AI service.
- No AI "screenshots": every picture and clip shows the game as it actually runs.
