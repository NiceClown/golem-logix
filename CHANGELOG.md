# Changelog

## 1.0.1 — Golem Interface and a cosmetic pass

### Anvil, craft planner and polish (later in 1.0.1)

- **Mana Saturator before assembly is a magic anvil** (mid-game step; the 3x3x2 machine is its upgrade): a slate plinth with a rune
  line, a narrow bronze waist with a glowing rune belt, a wide steel body with a long horn and a heel. Right-click puts an iron
  block, a copper block or packed ice on it — only these three plates can be forged by hand — and the new **Forge Hammer**
  (left-click the anvil) forges it: 4 strikes give one plate (the machine gives two); right-click with an empty hand takes it.
- **Forge Hammer** (copper ingots, iron block, packed ice, sticks): a frost-iron hammer in copper bands with a golem mask. It runs on
  its own Source charge (4000, 100 per strike) and is charged like the Portable Chronometer — right-click a Source jar; it never
  drains jars by itself. It does not break the anvil. Guide book article.
- **Craft planner in the order window** (like AE2 / RS): while you type the amount the server plans the order and the window lists
  what is taken from the network, what will be crafted and what is missing (red, first). An order with missing resources cannot be
  started; a failed start (no free calculator, no power) shows the reason in the window instead of the chat.
- **JEI search sync is a terminal setting**: a "JEI" button next to the terminal cycles off / terminal → JEI / both ways (saved in
  the client config).
- **Pipe runners** no longer blink: the route is continuous (the runner is exactly at the block border when the next pipe takes
  over), time is computed without float rounding in old worlds, the cargo handed over is the one carried. Lonely pipes walk from
  outlet to outlet through the middle. Cargo in basic pipes stays inside the glass.
- **Warehouse docks** at pipe dead ends have their own roller-shutter gate with a hazard stripe and a lamp; ordinary dead ends get a
  redrawn plug — a clean brass flange with eight bolts, a dark stepped boss with a cryo sight glass, a small gauge and icicles.
  A lonely pipe between two devices no longer gets a plug over a device connection.
- **Minimum animation time** for the Cryo Calculator and Cryo-Mainframe now really works: instant orders keep the "working" state
  and the item for at least 2 seconds on all clients (the server holds it for display only; crafting speed is unchanged).
- **Mainframe copper golems are busy when idle**: the engineer wipes the door glass, oils the calculators, sweeps frost and reads
  the shift log; the operator polishes the matrix, sorts punch cards, winds a chronometer and stretches; the roof golem taps the
  gauge and polishes the valve wheel. They walk smoothly between jobs instead of popping into place.
### Punch Card Catalog, Schematic Concentrator and a design pass (later in 1.0.1)

- **New multiblock: Punch Card Catalog.** A 3 x 2 x 2 filing cabinet — the Catalog at the bottom middle of the front, five
  bookshelves around it, six copper blocks behind (any oxidation, waxed too) — with a copper block and a **copper golem statue** at
  each side. On assembly the statues come alive: two big copper golems (with the statues' oxidation) grow into the cabinet's sides,
  hold the roof corners and carry punch cards. It stores punch cards with **crafting-table recipes** (the calculators carry them out
  themselves), so they no longer need rows of Pattern Providers. Capacity comes from ordinary **Pattern Providers** put into its
  slot under the Redstone Module slot: 27 places each, up to a stack (64 → 1728 cards). Places without providers are locked;
  providers whose places hold cards cannot be taken out. A scrolling grid with a search by result; a Redstone Module switches the
  catalog's patterns on and off. While the window is open the drawers slide out showing the cards, the letter drums on the roof
  flip and steam pours from the chimney. Breaking any part returns every block as it was. One channel, recipe, Jade tooltip,
  guide book article with a materials checklist and a ghost blueprint. Config: catalogSlotsPerProvider.
- **New block: Cryo Schematic Concentrator** (like the RS autocrafting manager / AE2 pattern access terminal): every Pattern
  Provider of the network and every Catalog's places in one scrolling window, grouped — crafting-table cards together, the rest by
  the machine each provider feeds (block icon and name), and "No machine" for providers with machine cards but nothing to feed.
  Cards can be taken out and put in right there; Shift+click sends a card to the first free slot of the right kind; search by
  result. A rusty cube with a round holographic screen: rings turn on it, and while the window is open holographic cards fan out.
- **Pattern Provider: output side.** A button picks where it feeds: all sides (default, as before) or a single side — handy when
  machines stand side by side. The arrow on the faces now points at that side; a sun with rays means all sides.
- **Eternal Frost Resonator redesign** to match the rest of the network: a rusty copper body in a brass frame with a big turquoise
  dial, a glass chamber with a swirling snow vortex that rises into a chimney, frosted white pipes and icicles. It faces you.
- **Cryo-Mainframe atlants** now really stick out of the cabinet: their torsos come out of its sides, the outer arm wraps around
  the front corner, the inner one rests on the roof crest, frosted pipes curl out of copper housings at their feet. **More golems:**
  a copper golem on the roof turns the valve wheel, copper archivists walk the shelves bringing parts to the snowmen.
- **Pattern Encoder animation:** its digits change now and then, and the paper moves like in a typewriter.
- **Portable Chronometer stays put while its window is open:** its inventory slot is locked (turquoise frame) — clicks,
  Shift+click, Q and swapping it out with a hotbar key or F are ignored, so the window no longer closes unexpectedly.
- **Mana Saturator is faster:** 5 s per ritual (+40% per cooler, under 2 s with five). Blanks queue on the left deck (up to four of
  one kind) and the two carrier golems work independently — one takes the plates away while the other already brings the next
  blank, so the smith hardly waits.
### Cryo-Mainframe, channel fixes and snowmen (later in 1.0.1)

- **New multiblock: Cryo-Mainframe** (late mid-game). A 3 x 2 x 2 cabinet — the Mainframe at the bottom middle of the front, five
  glass blocks around it, six Atlas Casings behind — held at both sides by two atlants. Up to 12 Cryo Calculators go inside: each
  one gives **2 independent cores** (24 parallel orders), each Compressed Co-processor (up to six) **counts twice** and speeds up
  every core (1 + 8×N steps per cycle). The whole cabinet uses **one channel**; a pipe can attach to any part. The glass doors swing
  open while anyone has its window open; inside a turquoise matrix glows, the calculators stand on two shelves and snowman crews
  build what the cores make. A valve wheel on top turns while it works. Recipe, Jade tooltip, guide book article; the Recipe
  Monitor lists the cores as separate rows (#1, #2...). Multipliers are in the config.
- **Cryo-Mainframe atlants are built in:** when the cabinet assembles, the atlants become part of the multiblock like the
  Saturator's blocks: they step onto a shared plinth, hold a brass cornice spanning the whole cabinet with their raised arms, are
  strapped to its sides and still follow you with their heads. Breaking any part takes it apart again (a broken atlant drops
  whole). The guide book page has a materials checklist and a ghost blueprint, like the Saturator.
- **Drive Bay glass doors open** while anyone has its window open: the showcase door swings out on its left hinges, the narrow
  gauge-panel door on its right ones. (The bay model is now drawn in the cutout layer; the glass is drawn by its renderer.)
- **Channels:** devices beyond a line's capacity no longer look (or act) active — only the ones that got a channel light up. A basic
  pipe from a bushing now really limits the line to 16. **Channel Expanders** also act as exits: 64 channels out of their outer face
  (128 for the quantum one).
- **Pipe Plug** (copper ingot + snowball → 4): right-click a pipe side to close it, so neighbouring pipes do not join and channels do
  not merge; click again to take the plug back. The plug is drawn as a brass cap on that side.
- **Jade** shows channels on every pipe, expander and channel-using device (used/maximum, no core, no power, no free channel).
- **Eternal Frost Resonator** visibly connects to pipes.
- **JEI:** the search in terminals and JEI's search box follow each other; items and fluids can be dragged from JEI into filter,
  "keep in stock" and encoder slots without owning them (a fluid becomes its filled bucket).
- **Golem Interface:** a gear under every "keep in stock" slot (or a click on the slot with an empty hand) opens a settings window:
  amount in items or mB (type it, "16B" means 16 buckets), quick buttons -64…+64 or -10B…+10B, presets, fluid/item mode, Clear.
  The mouse wheel and empty-hand clicks no longer change or empty the slots. The "Accepted into the network" row takes items by hand
  (and by Shift+click from the inventory) — they go straight into the network.
- **Pattern Encoder** keeps its blank cards; put an encoded card into the output slot to edit the recipe and rewrite it in place;
  Shift+right-click with an encoded card in hand erases it. Encoded cards show their result while Shift is held (like AE2 patterns).
- **Upgrade slots** show translucent projections of the upgrades they accept.
- **Mana Saturator:** the blank appears on the left rear deck and a small copper golem carries it to the anvil; when the smith is
  done a second copper golem carries the plates to the right deck, where they are taken from. Rituals are a bit slower (base ~6 s,
  +37% per cooler) so the animations play out. A lamp-holding copper golem walks on its roof too.
- **Cryo Calculator:** the ice hills are replaced by a crew of snowmen of different sizes that toss ice parts into the hologram of
  the item; idle, they look around, hop, wave and doze. When co-processors actually speed the job up, a foreman with a black atlant
  head appears and urges them on.
- **Snowmen hold their boxes** with their stick arms (pipe runners too).
- **Recipe Monitor** roof: a ribbed steel deck with a brass rim and a rune circle instead of a plank-like brass plate.
- Fixed: the settings and crafting-order windows were blurred along with the world behind them.

- **New block: Golem Interface** (like the AE2/RS interface). One network channel for both directions:
  - *Keep in stock*: nine ghost slots with amounts (drop a stack to set item and amount, left/right click +-1). The interface takes
    what is missing from the network and returns the excess; hoppers, pipes of other mods and machines take the stock from any side.
  - *Intake*: anything inserted from any side goes into the network, crafting jobs that wait for it first. Intake cannot be
    pulled back out, so a hopper under the interface only ever gets the stock.
  - Upgrades like the export bus: up to four Compressed Co-Processors (faster, more per move), an Autocraft Module (orders stock
    the network lacks) and a Redstone Module.
  - Looks: a copper-and-brass cube with an intake and network socket on all six sides, filing drawers, live gauges, runes and
    frost. Flat metronomes in relief on the four side faces; while it works the intake turbines spin and the metronome pendulums
    swing, faster with co-processors.
  - Recipe: copper ingots, hoppers, an import bus, an export bus and a Flattened Copper Matrix. Guide book article in "Buses".
- **Punch cards:** blank cards stack to 64; an encoded card stacks to 1.
- **Golem Interface takes fluids** from any side (pipes, buckets): a 16-bucket intake tank that empties into the network.
- **Golem Interface keeps fluids in stock:** a bucket in a "keep in stock" slot means the fluid itself (one bucket = 1000 mB,
  up to 64 buckets per slot); pipes drain it from any side. A switch under each slot flips it between fluid and item mode
  (keep the buckets themselves).
- **Mana Saturator with roof coolers no longer runs dry:** Source is pulled twice as often and scales with the coolers (up to
  120 Source per tick with five); when the network has less than asked, it takes what there is instead of nothing.
- **Dense pipe at a bus or device:** the end is closed with a solid octagonal brass flange; the earlier washer with a hole and
  rotated corner tiles left gaps.
- **Living machines:** every gear on every machine texture turns, fans spin, amber screens count, glowing pipe windows blink in
  random order. Animated textures now declare their frame size, which fixes the squashed, too-fast animation on the back of the
  Drive Bay and the flicker on its left wall.
- **Drive Bay:** cassettes are big (reels fill the window) and sit forward on the shelves; the middle partition has a doorway in
  every row with brass saloon doors that swing open for the archivist golems. The see-through gap at the right edge is closed.
- **Mana Saturator:** the pedestal has a rune column, brass collars, four frosty cryo tubes and a rune ring on its base; on the back
  the gears and fan turn, the amber screen counts and the pipe windows on the right blink.
- **Crafting Terminal:** the side shows a saw cutting a board (sawdust falls) and a swinging hammer; on top a flat copper golem carries
  blocks between the cells of the 3x3 work plate.
- **Atlas Array:** atlant heads turn to follow the nearest player; one violet glyph per face of the formed array wanders over its
  casing; a blue light runs along each conduit and fills the line.
- **Recipe Monitor:** the vortex sits exactly in the porthole; side panels have blinking signal lights; the crystal on the roof is
  replaced by a small copper golem holding a lamp: violet when idle (and it strolls about the roof), blinking green while crafting,
  steady red if a job is stuck.
- **Bus at a dense pipe:** the dense pipe now ends in a brass washer around the bus's mini tube instead of a wide hollow sleeve.
- **Cryo-Calculator window:** the missing "steps per cycle" text is back and the job contents stay inside their frame.
- **Compact buses.** Import, export and periscope buses fit in half a block: a 6.4-unit body against the container with the working
  head in front. Side pipes leave from the middle of the body, the back pipe from its rear face; the selection box follows the model.
- **Buses and pipes are steps.** Their collision stops at half a block, so you walk onto them without jumping (the selection box is
  unchanged).
- **Runes glow.** Runes and glowing inlays on every machine get a soft light of their own: invisible in daylight, gently lit at night.
  More rune shapes (30 instead of 18) and each place on a texture picks its own glyph. The Atlas casing, atlants, cryo panels, the
  resonator and brass plates got engraved runes.
- **Gauges move.** Every pressure gauge needle sways smoothly (interpolated animation); each device keeps its own tempo: the
  Saturator forge breathes fast, the Recipe Monitor at a medium pace, the Drive Bay slowly.
- **Mana Saturator:** the liquid in the Source columns is solid and smooth (no more square tiles); the network port on the front is
  smaller, so the golem window and gauges of the crate stay visible; the anvil under the smith has a real anvil shape (foot, waist,
  face, horn and heel) in cold bluish steel with frost and icicles.
- **Guide book:** category tabs are bookmarks tucked under the cover and always fit between the brass corners; the corners are drawn
  over the content, and the search field no longer overlaps them.
- **Item icons redrawn in 32x32:** cassettes in perspective with tape reels, fluid capsules with a meniscus and bubbles, the punch
  card, the three flattened plates in isometry, the Wither plasma, the occult matrix and the guide book.

## 1.0.0 — first release

### Second playtest round (October 2026)

- **Multiplayer fixed and tested on a dedicated server.** Joining a server timed out: on join the client rebuilds the cache of every
  block state, and each of the 16,384 pipe states assembled its shape from boxes on every call. Pipe shapes are now precomputed
  (64 combinations), so joining works again and worlds load faster. A dedicated-server run with Ars Nouveau, JEI and Jade went through
  terminals, autocrafting with the "order ready" toast, the Recipe Monitor, JEI precraft, the Chronometer, bus upgrades, the secret
  state and the Mana Saturator with no errors on either side.
- **Pipes up close:** the octagonal brass rings and sleeves are closed now. Corner plates sit exactly on the chamfer and have front
  faces, so the rings no longer fall apart into loose planks with "Y" joints (most visible where dense pipes meet buses). The
  basic-to-dense joint no longer flickers: the dense side draws only the reducer washer, sized so nothing shares a plane.
- **Portable Chronometer** looks like a device now (not a flask): a brass handheld with a winding crown and a green screen. The Wither
  animation moved from the window onto the item itself, in the hotbar, the inventory and the hand: it charges, fires a skull at
  you, and the glass flashes and cracks. The window is the crafting terminal layout with a violet Source gauge. The item no longer
  dips in your hand every time its charge changes.
- **Cryo-Calculator** takes up to four Compressed Co-Processors in its upgrade column, +4 steps per cycle each (twice an adjacent
  co-processor; up to 29 steps per cycle with six around it). Its window shows the speed with small icons; "Abort" is hidden while
  idle.
- **Guide book:** the secret articles moved to their own section, **"Secret Knowledge"**, whose tab appears after the first trial.
  Tabs with unread articles show a pulsing marker.


**Machines**
- **Bus upgrades.** Import and export buses have six upgrade slots: up to four **Compressed Co-Processors** (speed: up to 8x per move,
  down to every tick), an **Autocraft Module** (export bus orders what the network lacks and delivers it when ready) and a **Redstone
  Module**. The periscope, pattern provider and cryo calculator take a Redstone Module too; the Mana Saturator takes it by right-click.
  Modes as in AE2/RS: ignore, with signal, without signal, one action per pulse. A Compressed Co-Processor is made by squeezing a
  Cooling Co-Processor in the Mana Saturator.
- **Pattern Provider waiting mode**: the next batch of ingredients is loaded only after the result of the previous one came back.
- **Recipe Monitor** — a new brass cabinet with a swirling portal: punch cards orbit inside for every running job; its window lists every
  calculator of the network with targets, progress, the current step, steps left, awaited machine outputs and an Abort button
  (JEI R/U work on its items).
- **"Order ready" notification** with a chime for whoever ordered the craft (terminal, precraft).
- **Precraft**: Ctrl + JEI "+" in a crafting terminal or the Chronometer orders all missing craftable ingredients as one job.
- Buses never shuffle items in a circle: a bus does not put items back into the container it works with, even when a periscope links it
  (this also stopped the endless injector clicking). The export bus tops up existing stacks first.

**Mana Saturator**
- A small iron golem now forges the blank on the anvil with five blows per ritual (and offers you a poppy when there is nothing to do).
- Five roof vents take **Cooling Co-Processors** from above (radiator with a fan); each adds +40% speed, all five make the ritual 3x faster.
- Network connectors on every side except the roof: front, back, both sides and bottom.

**Cryo-Calculator and co-processor**
- While working, a miniature of the current step's output floats and spins above the calculator, the ice hills on its roof rise and sink
  in random order and turquoise "matrix rain" runs down its side panels; everything spins up and down smoothly.
- The Cooling Co-Processor's fins pump like a flywheel while its calculator works, with a smooth start and run-down.

**Terminals and the Chronometer**
- Terminal windows can be stretched vertically (normal / tall / full screen), remembered for all terminals.
- JEI R and U work on items and fluids in the terminal list; JEI no longer covers the side buttons and upgrade columns.
- The Portable Chronometer opens a crafting terminal by default and runs on its own Source charge (charge it from a Source jar); a green
  screen beside the grid shows a flat Wither trying to shoot a skull into the glass.
- **Eternal Frost Resonator** reworked: it sends Source through the aether to the network's chronometers (they never need charging), but
  no longer powers the network and no longer fills Ars Nouveau jars. New recipe with a clock.

**Secret recipe**
- The Wither plasma and the Interdimensional Occult Matrix are hidden in the book and in JEI until two trials are done, in any order:
  defeat the Wither and try to open a Chronometer in another dimension. Each trial grants "Secret knowledge obtained" and a book page
  with the other trial; both reveal the full recipe.

**Visuals**
- Pipes glow softly while the network is powered (no block light, mobs still spawn); a brass reducer washer closes the gap where a
  basic pipe meets a dense one.
- Fixed: the Atlas Array's crate-carrying golems walked off the top of the array.
- Fixed: parts of models above or beside their block (calculator hills, monitor crystal and gauges, core crystal, cabinet item, pipe
  flanges) sampled neighbouring textures.

**Guide book**
- New articles: secret knowledge, bus upgrades, redstone, Recipe Monitor; new pages on roof cooling, window size, precraft, waiting mode,
  the calculator animation and Chronometer charge. Config and "system" remarks removed.
- Table cells wrap inside their columns (long cells used to be cut off), bold text no longer spills over line breaks, no page ends with a
  stray line; a layout audit checks this for both languages.

**Fixes found while testing**
- JEI integration failed to load when the plugin touched mod items too early (now created lazily).
- Calculator and Recipe Monitor block updates were ignored by the client (floating model, cards and status did not update).

### Storage and network
- Atlas Array multiblock (3x3x4), channel lines through cable bushings, channel expanders, Source power with Ars Nouveau (optional).
- **Drive Cabinet is now a 2x2 multiblock with 10 cassette slots** (it was a single block with 6). One item places all four parts like a
  door; any part opens the window, breaks the whole cabinet and connects to the network; the cabinet costs one channel.
- Item and fluid disks (1k-256k) stored server-side; bucket pouring in terminals; priorities for cabinets, buses and periscopes.
- Fixed: disks in a cabinet (and filters in buses/pattern providers) no longer shift to the first slots after a world reload.

### Mana Saturator is now a 3x3x2 multiblock
- The head (the old Saturator block) is a compact press on its own; build an **End Rod** (the base of the press) above a **Dense Frost Pipe**
  (the central crate) under it, and fifteen **Atlas Casings** around, and the blocks turn into one machine by themselves: two glass
  columns that fill with Source, an anvil with a golem smith, a violet crystal. Break any part and everything goes back to ordinary
  blocks. Clicking any part works as clicking the head.
- Source comes from the network through any connector (if the network has a core) or from jars next to the machine.
- The guide book lists the materials and projects ghost blocks of the machine (`Y` / `N`); the ghost follows your facing.

### Terminals
- The list now shows **items and fluids together** (default). A fluid counts in buckets: 1000 mB is one "item", with a droplet
  marker. Click a fluid with an empty container to fill it, with a full one to pour it into the network. The button cycles
  both / items only / fluids only.
- Analog terminal, crafting terminal and pattern encoder have their own sides, backs and tops: a CRT bulge and cooling grille, a tool rack
  with gears and a work plate, a card magazine and a paper reel.

### Visuals (redone from the new reference art)
- Cabinet: bronze frame with carved friezes, brass corner blocks, rune plaques, iron mesh compartment, glass door with gauges, frost.
  Dynamic parts: cassettes with colored labels and LEDs, spinning reels with tape that winds with the fill level, copper archivist
  golems, pulsing runes.
- Buses: compact brass bodies (8x8x6) with a working end in front (turbine funnel, injector nozzle, periscope lens), a rune plate with a
  star on top, and **a pipe socket on every side**; when a pipe, another bus or a device is next to a bus, the socket grows into a mini
  glass tube all the way to the neighbour.
- The back of the Drive Cabinet is one solid panel (cryo coil, amber screens, fan, louvres, rune tablet, pipes) instead of four tiles.
- Pipes: smooth octagonal translucent tubes, brass bands, brass cage junctions, sleeves only at device connections, dense pipe in deep
  blue ice with bronze bands; snowmen (dense: a real snow golem model) run through each pipe network.
- Copper golem puppets (CC0 textures from Copper Age Backport) orbit the core and carry crates around the array top.
- All textures are sized for full mip-mapping; a resource audit tool (`tools/Audit.java`) checks references and sizes.

### Fixes found by looking at the game
- Golems and crates above the Atlas Core were rendered black (light was sampled inside the core block); the core and the cooling coprocessor no longer count as opaque cubes.
- Cramped or overlapping text in the terminal, pattern provider, bus and periscope windows.

### Items and rituals
- Anvil crushing, Wither plasma ritual, Mana Saturator (purpur block + end rod recipe).
- Portable Chronometer, punch cards, pattern encoder/provider, cryo calculator with coprocessors, autocrafting.

### Guide book
- Searchable research map with zoom, material checklists with ghost-block projection (`Y`/`N`), remembers the last page, ru + en.

### Integrations
- JEI categories, Jade tooltips (both optional).

### Quality
- 59 game tests on a real server (with and without the optional mods), plus end-to-end scenarios on a real client — including a
  client connected to a dedicated server (terminal clicks, autocrafting request, bucket, cabinet and bus windows, ghost-key priority); a development screenshot harness (`gradle runDevShots`) that is excluded from the release jar.
