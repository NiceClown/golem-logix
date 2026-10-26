# Changelog

## 2.0.4 (26.1.2) — Copper Atlants and a dressed Atlas Array

- **The Atlants are redrawn in the game's own style**: iron golem statues clad in copper gone green, with vines like the vanilla
  iron golem's, a glowing rune on the chest and cyan eyes; they hold the array up with raised arms. The same figures carry the
  Cryo-Mainframe, and the snowman foreman of a sped-up Cryo-Calculator wears the new copper head.
- **The Atlas Array in oxidized copper**: rune plates on the casings, ice pipes in the conduits. An assembled array grows a
  column of glowing ice between the Atlants on every side (unless a channel expander stands there), icicles under the top layer
  and a balcony rail of brass and spruce around the top.
- **The Atlant's head is the vanilla iron golem's**: tall, with the long nose and the heavy brow, in copper with cyan eyes.
- **The Cryo-Mainframe's shelves** have one floor picture each instead of a pattern of rune tiles: a deck of bronze plates with
  snow drifted against the walls, hoarfrost and a dim rune under the crew's projection.
- **All block textures share one pixel density** (32 px a block), so neighbouring blocks no longer mix fine and coarse pixels.
- 112 game tests.

## 2.0.3 (26.1.2) — Machines hand out their work; the buses come alive

- **Auto-output, on by default.** The Mana Saturator and the Cryo-Mixer hand out what they make by themselves, twice a second: into
  a neighbouring Pattern Provider first, otherwise into any neighbouring inventory (a chest, a hopper, an Interface). The mixer
  switches it with a new button to the left of its window; the Saturator, which has no window, with a right-click of the Forge
  Hammer (a right-click with an empty hand shows the state). The guide explains it on the pages of both machines.
- **Return slots on the Pattern Provider (Stamping Gateway).** A column of three slots on the right of its window: whatever lands
  there, from the machines' auto-output, a hopper or a player, goes straight into the network (to the job waiting for it first).
  A machine fed by a provider no longer needs its own Import Bus and channel. Automation can only put items in; the punch cards
  stay out of reach.
- **The buses come alive**: the Vacuum Import Bus turns its turbine, the Injector Export Bus blinks its violet dot, and an ender
  eye behind the Periscope's monocle looks around, each bus at its own pace (the item shows the eye looking straight ahead).
- The guide's pages no longer leave one or two lines alone on the next page (the core, the array hints, channels, disks, the
  Redstone Module).
- 112 game tests.

## 2.0.2 (26.1.2) — The Cryo-Mixer

- **New block: the Cryo-Mixer.** Two blocks high, with a water flask and a Liquid Cryo-Source flask on top, a copper module with a
  turning gear, louvred brass sides and a copper golem at the valve. It freezes water with Cryo-Source in two modes switched in its
  window: **snow** (a snow block a second from 250 mB of water and 25 mB of Cryo-Source) for snow golems and the plates for pipes,
  or **ice** (a block of ice every 5 seconds from 1000 + 100 mB) to pack into Packed Ice for Cryo-Substrate. Up to four Compressed
  Co-Processors speed it up (four make it five times faster), a Redstone Module puts it on a signal. Water goes in by bucket or by
  any pipe, Cryo-Source through an Export Bus from the network's Source; the snow or ice waits in the tray for the window or an
  Import Bus. A guide entry in the Rituals chapter, a JEI page with both modes.
- **JEI:** the Mana Saturator's page draws a small projection of the whole multiblock; a new page shows forging by hand on the
  anvil (a block and four Forge Hammer strikes make a plate).
- **The guide no longer leans on other mods**: the channels, devices, disks, fuzzy cards, orders and the concentrator are explained
  on their own, without "like in AE2/RS".
- Right-clicking the search field of a terminal or of the guide clears it.
- The mod's advancement tab shows its packed ice background again.
- **The Schematic Concentrator's hologram is back on its screen.** On 26.1 one nearly opaque texel (the glint on the glass) put
  the whole front into the translucent layer, which the game draws after the hologram and over it; the glow itself is added to
  the light again as on 1.21.1 (the game's own glow now blends as translucent). The Eternal Frost Resonator's vortex uses the same
  glow. The faces of the Recipe Monitor's portal and the Interface's metronome had the same stray texels and are solid again.
- The Cryo-Mixer window squeezes a line that is too long for its panel (the ice pace, longer translations).
- 109 game tests.

## 2.0.1 (26.1.2) — The core grows step by step; the tower pipes its jars

- **Three steps of the Atlas Core.** The core alone runs a small network of **8 channels** and connects only through its bottom
  face (a pipe, a bushing or a device right under it). On a **starter tower** (two conduits and a Cable Bushing under it) it gives
  **16 channels**; the whole Atlas Array keeps its 64 channels a bushing, and the expanders raise them as before. Right-clicking the
  core and Jade say which step it is on and what to build next.
- **The guide's Atlas chapter goes step by step**: the core, the starter tower, the whole array, the expanders, with the bushings,
  conduits, Atlants and diagnostics branching off.
- **The Cryo-Distillation Tower pipes its jars**: from the riser on its head a pipe runs over to every Cryo-Source Jar standing right
  against the plinth (on any side) and drops into the jar's vent. The guide now says plainly that jars must stand against the tower.
- **Network pipes dock onto Cryo-Source Jars** (and the Creative Source Jar) with a brass socket, on the sides and on the cap.
- **The copper golems step aside**: when a jar, a tower or a pipe stands where a golem of the tower or a jar would be, the golem,
  its bracket and its valve move to the first free side.
- The flask of the Cryo-Source Jar and the glass of the tower's column no longer flicker (they lay in the same planes as the frame
  rings).
- 108 game tests.

## 2.0.0 (26.1.2) — Golem Logix on Minecraft 26.1.2

The main line of Golem Logix now runs on Minecraft 26.1.2 and NeoForge 26.1.2 (Java 25). The 1.21.1 version stays in the branch
`mc-1.21.1` for critical fixes; jars are named after the game version (`golem_logix-26.1.2-2.0.0.jar`).

- **Copper golems are the game's own.** Copper Age Backport is no longer needed or mentioned: the anvil ritual and the Dispel take
  the vanilla copper golem, the Punch Card Catalog the vanilla copper golem statues, and the golem puppets on the machines wear the
  game's copper golem textures. The warning about a game without copper golems is gone with it.
- Everything of 2.0.0 for 1.21.1 is here too: the Cryo-Distillation Tower's window with four Compressed Co-Processor slots and the
  mounted platforms of the tower and the Cryo-Source Jar.
- Integrations: JEI 29, Jade, Curios 15 and Neo Vitae. Ars Nouveau and Create have no 26.1.2 builds yet: their recipes and
  integrations stay in the mod and wake up once they are ported. Without Ars Nouveau, Source comes from the Cryo-Distillation Tower.
- **Worlds from 1.21.1**: the disk contents move to the new save file (`data/golem_logix/disks.dat`) on the first load.
- **Data packs**: recipe ingredients use the 26.1 format (an item id, a `#tag` or a list of ids instead of `{"item": ...}`);
  `golem_logix:saturator` and `golem_logix:anvil_crushing` recipes written for 1.21.1 need the same change.
- The JEI anvil page draws the golem pressed down under the anvil (26.1 draws entities in GUIs into their own picture).
- The empty Curios Chronometer slot shows the outline of our Chronometer instead of a pocket watch.
- 107 game tests, all passing with and without JEI, Jade, Curios and Neo Vitae.

## 2.0.0 (1.21.1) — The tower window; the last big release for 1.21.1

From 2.0.0 the main line of Golem Logix is Minecraft 26.1.2 (see its own changelog in the main branch). This 1.21.1 build stays
for critical fixes only; jars are now named after the game version (`golem_logix-1.21.1-2.0.0.jar`).

- **The Cryo-Distillation Tower has a window** (right-click with an empty hand): the blood tank, the pace (mB of blood and Source a
  second), what it is doing (distilling, no blood, no jar with room). A column of **four slots for Compressed Co-Processors**: each
  one distils 20 mB more a second (config `distillCoprocessorBonus`), so four make the tower five times faster for a hungry
  network. They drop when the tower is broken.
- **The golems' platforms are mounted**: on the tower and the Cryo-Source Jar the little platform reaches the frame, is bolted to it
  with a brass bracket and rests on two brass struts down to the plinth.

## 1.1.9 — Cryo-Distillation: Source from blood

Source without Ars Nouveau (for packs and versions where it is not there), with Neo Vitae. Ars Nouveau jars keep working: both feed
the same network.

- **Cryo-Distillation Tower** (three blocks high, placed as one): pour **Essentia Vitae** into any of its blocks (pipes from an Ara
  Vitae, our buses, buckets; it only takes blood and never gives it back). It literally freezes the living warmth and chaos out of
  the blood: 20 mB a second become 20 Source, poured into the jars touching it. A coil inside glows from blood red to ice cyan,
  frost breathes from the louvres, a copper golem turns the valve.
- **Cryo-Source Jar** (two blocks high): holds 10 000 Source and feeds the network, the Saturator, the Forge Hammer and the
  Chronometer like an Ars Nouveau jar; a comparator reads its level, a click shows it.
- Both are crafted only with Neo Vitae installed (the tower needs a Tabula Rasa from the Ara Vitae); with Neo Vitae the network
  needs Source even without Ars Nouveau. The blood is the fluid tag `golem_logix:cryo_distillable` (a modpack can add another
  mod's blood); the rate, Source per mB and the jar size are in the config.
- A new guide entry (Cryo-Distillation), JEI info pages; 2 new game tests (108 in all), `-Pvitae` runs them with Neo Vitae.

## 1.1.8 — The foreman comes back

- **The atlant-headed foreman shows up again on a big speed-up.** In the Cryo-Calculator and the Cryo-Mainframe he was called only
  while the order was still running, and the cycle that finished it ended the speed-up in the same tick: with enough co-processors an
  order is over in one or two cycles, so he never came. An order the co-processors sped up now calls him even when it is over at once.
- **He stays at least 3 seconds** once called, and the snowman crew keeps working while he is there, so he no longer flickers in and
  out on short orders.
- 1 new game test (106 in all); the mainframe test checks him too.

## 1.1.7 — The anvil ritual in JEI, animated

- **The anvil ritual page in JEI is now a little scene** (like Flux Networks' in-world crafting): the anvil hangs above the golem's
  model, an arrow points down at it, the anvil falls, the golem flinches red and flattens with a burst of sparks, and its plate
  lights up under an "=" below. An arrow to the left of the golem points at how battered it must be, in hearts with the HP below
  (½ – 2 hearts, "1-4 HP" for the iron and the copper golem). The Wither's ritual keeps its condition as text.

## 1.1.6 — Fuzzy mode and the Fuzzy Card

Like RS's fuzzy mode and AE2's substitutions and Fuzzy Card, with one rule for the whole mod: a fuzzy slot takes the same item with
any components (damage, enchantments, a name), or any item of a tag chosen on it; the exact item is still taken first.

- **Pattern Encoder: fuzzy mode** (the new **≈** button). A crafting table pattern then takes in each cell what its recipe accepts
  there (any planks for a crafting table), so the recipe always still works; a machine pattern's input takes any components, and a
  **middle-click** (or Ctrl+click) on it goes through the item's tags (`#c:glass_blocks`: any glass for "glass"). Slots show ≈ or #,
  the tooltip says what they take; the punch card's tooltip shows the mode and the tags. Patterns written before stay exact.
- **Autocrafting uses it**: the planner counts the substitutes in storage and in leftovers, crafts the encoded item (or a substitute
  with a pattern) when they run out, and the job sends the very items it took to the machine or the crafting grid.
- **Fuzzy Card** (a new upgrade; glass, copper, a punch card and a Copper Matrix): in the import and export buses and the Interface
  the filters match fuzzily; a middle-click on a filter chooses its tag. The export bus and the Interface take the exact item first,
  then whatever matches; the export bus and the Interface got a 7th upgrade slot for it.
- 4 new game tests (105 in all): birch planks for a crafting table encoded with oak, red glass into a machine for "glass", the card
  on both buses (and nothing without it), the Interface keeping blue glass for "glass".

## 1.1.5 — Progression fixes

A review of the mod's progression (what opens when, and from which paths) found three things:

- **The Create assembly line needs the End now.** It made Compressed Co-Processors (bus speed upgrades, calculator upgrades, the
  Cryo-Mainframe) available in the Nether (the Deployer needs brass), while the Saturator that makes them is an End machine (purpur,
  End rods). A Deployer step with a **popped chorus fruit** keeps them after the End; the line still keeps the Saturator free.
- **The book no longer says to build the Saturator "as early as possible"**: it needs purpur and End rods. Until then plates come
  from the anvil, with Ars Nouveau also the Dispel, with Create the mixer.
- **No copper golem, no silent dead end.** Copper Age Backport is optional, but every way to the first Copper Matrix needs a copper
  golem (the Saturator needs a matrix itself, Create's mixer a running network). If the game has no copper golem and no recipe for
  the matrix, every player who joins is told what to install; the book says it too.
- 1 new game test (101 in all).

## 1.1.4 — Curios and a hotkey for the Chronometer, data-driven rituals, a Create assembly line

### Portable Chronometer

- **Curios integration**: with Curios installed players get a **Chronometer** slot (its own icon); a Chronometer worn there works
  like one in the inventory (charge, Resonator, range).
- **Ctrl+E** (rebindable, "Golem Logix" in the controls) opens the Chronometer without taking it in hand: the one in a hand, in the
  Curios slot or anywhere in the inventory. Its inventory slot stays locked while the window is open, wherever it is.

### For modpacks

- **Copper Age Backport is optional** (recommended for new players). Any mod's copper golem works: the entity tag
  `golem_logix:copper_golems`, or any mob with "copper_golem" in its id; the Punch Card Catalog's statues have the block tag
  `golem_logix:copper_golem_statues`.
- **The rituals are recipes now**: `golem_logix:saturator` (the Mana Saturator and forging by hand) and
  `golem_logix:anvil_crushing` (the anvil on a battered golem), changeable with data packs or KubeJS; JEI shows what the recipes
  say. See README, "For modpacks".

### Recipes

- **Create: a Co-Processor assembly line** — a Cooling Co-Processor on a belt passes a Spout (250 mB of Liquid Cryo-Source), a
  Deployer (an iron sheet) and a Press, three times round; nine of ten come out as Compressed Co-Processors, the rest as scraps.
  About as dear as the Saturator (Source plus iron) but it keeps the Saturator free; plates stay the Saturator's job.
- **Ars Nouveau: Blue Ice imbuement** — the Imbuement Chamber charges a Blue Ice block (three amethyst shards or quartz on the
  pedestals, 1000 Source) into two Cryo-Substrates: dearer than the Saturator, but it needs no golem and no machine.
- **Forge Hammer**: the stick in the middle is now an iron plate (a Flattened Iron Core or any iron sheet, tag
  `golem_logix:iron_plates`).

### Reliability and server load

- **Fixed: a possible crash in single player.** The snowmen in the pipes picked their cargo by reading the server's disk storage
  from the render thread while the server wrote it. The Atlas Core now sends a few samples of the network's contents with its
  update, and nothing reads the disks off the server thread.
- **Idle buses sleep**: a bus whose last operation moved nothing waits longer and longer (up to a second); a change next to it, a
  new filter or upgrade, or (for export buses) anything entering the network wakes it at once.
- **Buses and periscopes keep their target's handlers** (NeoForge capability caches) instead of looking them up on every operation.
- **A copied disk** (same id, e.g. a creative middle-click) is no longer counted twice: the network mounts the first one only.
- 5 new game tests (100 in all), also run without Copper Age Backport (`-PnoBackport`).

## 1.1.3 — Telegraph fixes

- **The Write slot takes a whole stack** of Aetherial Linkages (up to 16) and writes them all at once, so a growing base needs no
  card shuffling. Blank cards clicked onto written ones join them instead of swapping places with them; the window counts the
  written cards.
- **Shift-click sorts linkage cards**: blank cards go to Write, a written card goes to Memory. Before, a card brought from the base
  could be shift-clicked into the outpost's Write slot and rewritten by accident.
- **Fixed: the operator struck past the key.** The key stood under the golem's other side with the knob away from the hand. It is
  now a straight key in front of the right hand: the lever runs from the pivot towards the operator, and the hand comes down right on
  the knob. When the telegraph is idle the hand hangs beside the key.
- 1 new game test (95 in all): a stack is written, blank cards join written ones, shift-click never rewrites a written card.

## 1.1.2 — Cryo-Source Telegraph, a lighter server

### Cryo-Source Telegraph

- **A remote relay for outposts**: a rusty copper base 2x2 with a mast three blocks high (one item places it all, facing you). Two
  linked telegraphs join their networks, so an outpost far from the base runs on the base's Atlas Core: terminals, buses and
  cabinets at the outpost work as if they were next to the core. A telegraph carries 64 channels and takes none itself.
- **Linking with a card**: put an **Aetherial Linkage** into the **Write** slot of the base telegraph (the card records it), then into
  the **Memory** slot of a distant one. Write and Memory exclude each other: while one holds a card the other is locked and crossed out.
  The slots are labelled, an empty slot explains itself on hover, and the window shows the link, the coordinates, how many outposts
  are linked to this telegraph and whether the message goes through.
- **A big RESET button** erases the card in Memory and gives the telegraph a new seal: every card written for it stops working.
- **Range like the Portable Chronometer's**: 200 blocks without a card; the **Aether Resonance Core** reaches the whole dimension;
  the **Void-Glass Prism** reaches any dimension (a secret recipe: it opens with the other secrets and has its own secret page).
  The better card of the two ends counts. Both telegraphs must be loaded.
- **Upkeep**: every working link costs the Atlas Core 2 Source a second (config `telegraphSourcePerSecond`).
- **Animated, on the client only**: the Source strikes the violet crystal on the mast (it flashes and hums), the golem operator keys
  "GOLEM" in Morse, the desk lamp lights with every press, a blue pulse runs up the mast and the dish sweeps the sky.
- Guide book entry (with the range table), JEI recipes, both languages. 2 new game tests: an outpost works through a link, and
  a Nether outpost works on an overworld core with the prism (and is out of range without it).

### Server performance

- **The network is no longer walked every 8 ticks.** It is rebuilt only when a node or a peripheral next to it changes (a placed or
  broken pipe, device, Source jar, chest in front of a periscope), with a safety rescan every 5 seconds.
- **Bus order is cached**: buses sorted themselves by priority every tick (quadratic in the number of buses).
- **The Atlas Core** uses the cached network instead of walking it every second, and sends its look to clients only when it changes.
- **The Card Catalog** compared all 1728 slots every tick; now only after its contents change.
- **The pattern index** is rebuilt only when a pattern is added or removed, and only the drive cabinets whose disks changed are marked
  for saving.
- **Cryo Calculators** switch their lights without neighbour updates.
- All animations (snowmen, golems, reels, screens, the telegraph) run on the client; the server sends at most one "active" flag a
  second per machine.
- 1 new game test: the network follows a Source jar and a chest that are placed and broken next to it.

### Guide book

- **New entry "Pipe Plugs"**: how to keep two lines apart, when to plug a pipe, and that a plug takes no channel.

## 1.1.1 — AE2-style channels, Source Gems in the Saturator

### Channels rewritten after Applied Energistics 2

- **New channel assignment** (`system/ChannelCalculation`), the way AE2 hands out channels: a walk from the controller (the Atlas
  Core with its conduits) gives every device the shortest route; dense carriers go first (dense pipes, Cable Bushings, Channel
  Expanders), then basic pipes, then devices; a device gets a channel only if the network and every block on its route still have
  room. An independent implementation for this mod: no AE2 code is included (see README, "Credits and license").
- **A device carries as much as a basic pipe** (16, its own channel included), as AE2 devices carry as much as a normal cable.
  Config `deviceChannels` (array section). Long rows of devices need a dense pipe along them.
- **Fixed: channels crossed the array.** Devices behind a corner bushing were routed through the central bushing and the expanders,
  so their lines showed foreign channels and could fill up. Now each bushing and each expander counts only its own devices.
- **Fixed: a client network in the server's cache.** The pipe renderer's network (walked over the client's copy of the world) could
  land in the cache shared with the server in single player, and for a few ticks buses and terminals worked on client block entities;
  both threads also wrote into one map. The server and the client now keep separate caches per level.
- **Fixed: two cores.** Every core believed it ran the network. Now the same core is chosen from every device (a complete array
  before a tower, then the lowest position); the other one is idle and says so.
- **The network follows the world at once.** Placing, breaking or replacing a node makes the remembered networks stale in the same
  tick (as an AE2 grid repaths), and at the end of the tick the networks around get new channels and lights: devices cut off from the
  core go dark immediately instead of glowing forever.
- **Jade tooltips like AE2's**, with the numbers from the server's network: "Device Online", "Device Missing Channel" (with the full
  block that stopped it), "Device Offline" (no core or no Source), "X of Y Channels" on pipes and on devices that carry others, the
  line and the whole network on a bushing, the exit on an expander. Before, the tooltip walked the network on the client.
- 5 new game tests (90 in all): devices carry like basic pipes, no transit through the array, dense runs first, one core runs the
  network, the network follows changes. `lineLimit` and `pipeLimit` use combs of terminals along dense pipes.

### Mana Saturator makes Source Gems (Ars Nouveau)

- The assembled Saturator runs the **Imbuement Chamber's Source Gem recipes** (amethyst shard, amethyst block, lapis; read from the
  game's recipes, so modpack changes apply): **twice as fast** as the chamber (2.5 s per gem, 5 s per Source Gem block without
  coolers) for **a third more Source** (665 instead of 500, 2660 instead of 2000). Coolers on the roof speed it up as usual; buses and
  Pattern Providers feed it like the plates. JEI shows them in the Saturator category, the book has an entry for them.
- The Saturator pulls Source faster (150 per 5 ticks instead of 100), enough for a Source Gem block every 5 seconds.

### Cryo-Mainframe

- The copper golems no longer walk into the calculators: the second shelf's golem polished the back wall and sorted cards right
  inside the row of calculators, and two work spots were too close to them.

## 1.0.1 — Golem Interface and a cosmetic pass

### Liquid Cryo-Source, fluid autocrafting, Ars Nouveau and Create (later in 1.0.1)

- **Liquid Cryo-Source**, the network's own fluid. It is never stored: the network makes it from its Source (1 mB for 1 Source,
  `cryoSourceCost` in the common config) the moment automation pumps it out. Disks reject it, terminals do not list it, and it has
  no bucket: only an **Export Bus**, a **Pattern Provider** or a **Golem Interface** can pump it out. Leftovers are not returned.
- **Fluids in patterns and filters.** A fluid in a ghost slot is a fluid with an amount in mB: JEI "+" carries fluid ingredients of
  machine recipes into the encoder, JEI drag puts fluids into filters and stock slots (Liquid Cryo-Source also by clicking with a
  Source Jar), a click changes a fluid slot by 250 mB. The Pattern Provider pours the fluids into the same machine side as the items,
  only when the machine takes everything; the planner counts fluids in mB and Liquid Cryo-Source by the Source available.
- **The Dispel method (Ars Nouveau).** Your first plate made on an anvil (the falling anvil or the Forge Hammer) opens it (advancement
  "A Mind in the Metal" and a new book entry): a Dispel cast at a battered golem turns it into a **raw mind** (iron, copper, snow).
  On an Enchanting Apparatus a raw mind and six ingots (or snow blocks) give **two plates** for 1000 Source: better than the anvil,
  dearer than the Saturator.
- **Create.** A Mechanical Mixer turns a block (copper, iron, packed ice) and 250 mB of Liquid Cryo-Source in a Basin into a **torn
  mind**; a Mechanical Press turns it into a plate. Both steps run as autocrafting. Book entry shown with Create installed.
- New book entries with an in-game illustration; 7 new game tests (85 in all).

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
