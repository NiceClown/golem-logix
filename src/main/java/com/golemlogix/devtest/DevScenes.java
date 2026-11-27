package com.golemlogix.devtest;

import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.AtlasBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Scene builders for the screenshot rig (development only, not part of the release jar). */
final class DevScenes {
    private DevScenes() {
    }

    private static ItemStack card(com.golemlogix.data.PatternData data) {
        return com.golemlogix.items.CopperPunchCardItem.written(ModItems.COPPER_PUNCH_CARD.get(), data);
    }

    private static BlockPos pos(String[] a, int i) {
        return new BlockPos(Integer.parseInt(a[i]), Integer.parseInt(a[i + 1]), Integer.parseInt(a[i + 2]));
    }

    static void run(String name, String[] a, ServerLevel level, ServerPlayer player, Consumer<String> log) {
        switch (name) {
            case "array" -> {
                BlockPos base = pos(a, 0);
                for (AtlasBlueprint.Cell c : AtlasBlueprint.cells(AtlasBlueprint.Variant.FULL)) {
                    level.setBlock(base.offset(c.dx(), c.dy(), c.dz()), c.state(), 3);
                }
                level.setBlock(base.below(), ModBlocks.CREATIVE_SOURCE_JAR.get().defaultBlockState(), 3);
                refresh(level, base.above(3));
                log.accept("array built, core " + base.above(3));
            }
            case "arraygrad" -> {
                // arraygrad x y z : full Atlas Array with mixed Ice Conduit / Blue Expander / Quantum Expander pillars for 2.2.2 smooth gradients
                BlockPos base = pos(a, 0);
                for (AtlasBlueprint.Cell c : AtlasBlueprint.cells(AtlasBlueprint.Variant.FULL)) {
                    level.setBlock(base.offset(c.dx(), c.dy(), c.dz()), c.state(), 3);
                }
                int[][] edges = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                for (int[] e : edges) {
                    level.setBlock(base.offset(e[0], 0, e[1]), ModBlocks.ATLAS_CONDUIT.get().defaultBlockState(), 3);
                    level.setBlock(base.offset(e[0], 1, e[1]), ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState(), 3);
                    level.setBlock(base.offset(e[0], 2, e[1]), ModBlocks.QUANTUM_CHANNEL_EXPANDER.get().defaultBlockState(), 3);
                }
                level.setBlock(base.below(), ModBlocks.CREATIVE_SOURCE_JAR.get().defaultBlockState(), 3);
                com.golemlogix.system.AtlasStructure.dress(level, base.above(3), true);
                refresh(level, base.above(3));
                log.accept("arraygrad built, core " + base.above(3));
            }
            case "jar" -> {
                // jar x y z facing <1|2|3> [fill]: place Cryo-Source Jar (1), 2x2 Giga Jar (2), or 3x3 Antigravity Reactor (3)
                BlockPos base = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                int size = Integer.parseInt(a[4]);
                int fill = a.length > 5 ? Integer.parseInt(a[5]) : 25000;
                for (int dx = 0; dx < size; dx++) {
                    for (int dz = 0; dz < size; dz++) {
                        com.golemlogix.blocks.CryoJarBlock.place(level, base.offset(dx, 0, dz), facing, ModBlocks.CRYO_SOURCE_JAR.get());
                    }
                }
                if (size > 1) {
                    com.golemlogix.system.JarMerge.scan(level, base);
                }
                if (level.getBlockEntity(base) instanceof com.golemlogix.blockentity.CryoJarBlockEntity je) {
                    je.fill(fill, false);
                }
                log.accept("jar size=" + size + " at " + base + " facing " + facing);
            }
            case "distill" -> {
                // distill x y z facing [bloodMb]: place Cryo-Distillation Tower with coprocessors and Essentia Vitae
                BlockPos base = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                int blood = a.length > 4 ? Integer.parseInt(a[4]) : 8000;
                com.golemlogix.blocks.DistillationTowerBlock.place(level, base, facing, ModBlocks.CRYO_DISTILLATION_TOWER.get());
                if (level.getBlockEntity(base) instanceof com.golemlogix.blockentity.DistillationTowerBlockEntity te) {
                    for (int i = 0; i < te.getUpgrades().getContainerSize(); i++) {
                        te.getUpgrades().setItem(i, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));
                    }
                }
                var vitae = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse("neovitae:essentia_vitae_source"));
                var fh = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, base, net.minecraft.core.Direction.UP);
                if (fh != null && vitae != net.minecraft.world.level.material.Fluids.EMPTY) {
                    com.golemlogix.system.Transfers.insertFluid(fh, new net.neoforged.neoforge.fluids.FluidStack(vitae, blood), false);
                }
                log.accept("distill tower built at " + base);
            }
            case "mixer" -> {
                // mixer x y z facing: place Cryo-Mixer filled with water + Liquid Cryo-Source
                BlockPos base = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                com.golemlogix.blocks.CryoMixerBlock.place(level, base, facing, ModBlocks.CRYO_MIXER.get());
                var fh = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, base, net.minecraft.core.Direction.UP);
                if (fh != null) {
                    com.golemlogix.system.Transfers.insertFluid(fh, new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4000), false);
                    com.golemlogix.system.Transfers.insertFluid(fh, com.golemlogix.system.CryoSource.stack(4000), false);
                }
                log.accept("mixer built at " + base);
            }
            case "viscondenser" -> {
                // viscondenser x y z facing [thresholdPct]: place Vis Condenser with adjacent Cryo-Source Jar behind it
                BlockPos base = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                com.golemlogix.blocks.VisCondenserBlock.place(level, base, facing, ModBlocks.VIS_CONDENSER.get());
                if (level.getBlockEntity(base) instanceof com.golemlogix.blockentity.VisCondenserBlockEntity vc) {
                    if (a.length > 4) {
                        vc.setMode(com.golemlogix.blockentity.VisCondenserBlockEntity.MODE_PERCENT);
                        vc.setThreshold(Integer.parseInt(a[4]));
                    }
                }
                log.accept("viscondenser built at " + base + " facing " + facing);
            }
            case "cauldron" -> {
                // cauldron x y z facing [auram] [gelum] [blue|packed|ice|water]: place Essentia Cryo-Cauldron over cooling block
                BlockPos base = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                int auram = a.length > 4 ? Integer.parseInt(a[4]) : 48;
                int gelum = a.length > 5 ? Integer.parseInt(a[5]) : 48;
                String cool = a.length > 6 ? a[6] : "blue";
                BlockState below = switch (cool) {
                    case "packed" -> Blocks.PACKED_ICE.defaultBlockState();
                    case "ice" -> Blocks.ICE.defaultBlockState();
                    case "water" -> Blocks.WATER.defaultBlockState();
                    default -> Blocks.BLUE_ICE.defaultBlockState();
                };
                level.setBlock(base.below(), below, 3);
                com.golemlogix.blocks.EssentiaCauldronBlock.place(level, base, facing, ModBlocks.ESSENTIA_CAULDRON.get());
                if (level.getBlockEntity(base) instanceof com.golemlogix.blockentity.EssentiaCauldronBlockEntity ec) {
                    ec.addPrimary(auram, false);
                    ec.addCoolant(gelum, false);
                    ec.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 4));
                }
                log.accept("cauldron built at " + base + " facing " + facing + " auram=" + auram + " gelum=" + gelum + " cool=" + cool);
            }
            case "thaumshowcase" -> {
                // thaumshowcase x y z: compact visual-QA showcase with active Vis Condenser, brewing Essentia Cryo-Cauldron
                // (Blue Ice below + Thaumaturge tubes on left/right + connected Cryo-Source Jars), and boil-off Cauldron!
                BlockPos o = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
                for (int dx = -4; dx <= 12; dx++) {
                    for (int dz = -4; dz <= 6; dz++) {
                        level.setBlock(o.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                        for (int dy = 0; dy <= 5; dy++) {
                            level.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                // 1. Active Vis Condenser at o+(0,0,0) with Cryo-Source Jar to viewer's left at o+(1,0,0)
                com.golemlogix.blocks.VisCondenserBlock.place(level, o, facing, ModBlocks.VIS_CONDENSER.get());
                com.golemlogix.blocks.CryoJarBlock.place(level, o.offset(1, 0, 0), facing, ModBlocks.CRYO_SOURCE_JAR.get());
                if (level.getBlockEntity(o.offset(1, 0, 0)) instanceof com.golemlogix.blockentity.CryoJarBlockEntity je) {
                    je.fill(3800, false);
                }
                if (level.getBlockEntity(o) instanceof com.golemlogix.blockentity.VisCondenserBlockEntity vc) {
                    vc.setMode(com.golemlogix.blockentity.VisCondenserBlockEntity.MODE_PERCENT);
                    vc.setThreshold(25);
                }

                // 2. Actively brewing Essentia Cryo-Cauldron at o+(5,0,0) on Blue Ice, Cryo Jar behind (cleanly piped)
                BlockPos cp = o.offset(5, 0, 0);
                level.setBlock(cp.below(), Blocks.BLUE_ICE.defaultBlockState(), 3);
                com.golemlogix.blocks.EssentiaCauldronBlock.place(level, cp, facing, ModBlocks.ESSENTIA_CAULDRON.get());
                com.golemlogix.blocks.CryoJarBlock.place(level, cp.offset(0, 0, 1), facing, ModBlocks.CRYO_SOURCE_JAR.get());
                if (level.getBlockEntity(cp.offset(0, 0, 1)) instanceof com.golemlogix.blockentity.CryoJarBlockEntity je) {
                    je.fill(4200, false);
                }
                if (level.getBlockEntity(cp) instanceof com.golemlogix.blockentity.EssentiaCauldronBlockEntity ec) {
                    ec.addPrimary(64, false);
                    ec.addCoolant(64, false);
                    ec.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 2));
                }

                // 3. Boil-off Essentia Cryo-Cauldron at o+(9,0,0) over melted Water (Auram only -> Archmage panic!)
                BlockPos bp = o.offset(9, 0, 0);
                level.setBlock(bp.below(), Blocks.WATER.defaultBlockState(), 3);
                com.golemlogix.blocks.EssentiaCauldronBlock.place(level, bp, facing, ModBlocks.ESSENTIA_CAULDRON.get());
                if (level.getBlockEntity(bp) instanceof com.golemlogix.blockentity.EssentiaCauldronBlockEntity ec) {
                    ec.addPrimary(48, false);
                }
                log.accept("thaumshowcase built at " + o);
            }
            case "manometer" -> {
                // manometer x y z facing item threshold
                BlockPos at = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                level.setBlock(at, ModBlocks.STOCK_MANOMETER.get().defaultBlockState()
                        .setValue(com.golemlogix.blocks.StockManometerBlock.FACING, facing), 3);
                if (level.getBlockEntity(at) instanceof com.golemlogix.blockentity.StockManometerBlockEntity be) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[4]));
                    be.getFilter().setItem(0, new ItemStack(item));
                    be.setThreshold(Integer.parseInt(a[5]));
                    be.setMode(com.golemlogix.blockentity.StockManometerBlockEntity.MODE_ABOVE);
                }
                log.accept("manometer at " + at);
            }
            case "requester" -> {
                // requester x y z facing item keepAmount
                BlockPos at = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                level.setBlock(at, ModBlocks.STOCK_REQUESTER.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing), 3);
                if (level.getBlockEntity(at) instanceof com.golemlogix.blockentity.StockRequesterBlockEntity be) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[4]));
                    ItemStack ghost = new ItemStack(item);
                    ghost.set(com.golemlogix.init.ModDataComponents.KEEP_AMOUNT.get(), Integer.parseInt(a[5]));
                    be.getFilters().setItem(0, ghost);
                }
                log.accept("requester at " + at);
            }
            case "security" -> {
                // security x y z facing
                BlockPos at = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                level.setBlock(at, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing), 3);
                if (level.getBlockEntity(at) instanceof com.golemlogix.blockentity.SecurityTerminalBlockEntity be) {
                    be.setOwner(player);
                    be.getCards().setItem(0, com.golemlogix.items.IdentityPunchCardItem.of(
                            player.getUUID(), player.getScoreboardName(), com.golemlogix.security.SecurityPermission.ALL));
                }
                log.accept("security at " + at);
            }
            case "time" -> log.accept("gameTime=" + level.getGameTime());
            case "gearaudit" -> {
                for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                    ItemStack s = new ItemStack(item);
                    if (s.isDamageableItem() && s.getMaxStackSize() == 1) {
                        ItemStack w = s.copy(); w.setDamageValue(1);
                        var fl = com.golemlogix.system.Transfers.fluidOf(w);
                        if (!fl.isEmpty() || !com.golemlogix.system.Gear.is(w)) {
                            System.out.println("DBGGEAR " + item + " gear=" + com.golemlogix.system.Gear.is(w) + " fluid=" + fl);
                        }
                    } else if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath().contains("vengeance_pickaxe")) {
                        System.out.println("DBGGEAR " + item + " max=" + s.getMaxStackSize() + " damageable=" + s.isDamageableItem());
                    }
                }
            }
            case "gearfill" -> {
                com.golemlogix.system.GolemNetwork gn = com.golemlogix.system.GolemNetwork.get(level, pos(a, 0));
                ItemStack sword = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD); sword.setDamageValue(7); gn.insertItem(sword, false);
                ItemStack plate = new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE); plate.setDamageValue(3); gn.insertItem(plate, false);
                gn.insertItem(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD), false);
                gn.insertItem(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64), false);
                gn.insertItem(new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET), false);
            }
            case "rig" -> {
                // rig x y z : array + a pipe line to the east; terminal, calculator and provider with patterns on the side, a cabinet with one disk at the end
                BlockPos b = pos(a, 0);
                for (AtlasBlueprint.Cell c : AtlasBlueprint.cells(AtlasBlueprint.Variant.FULL)) {
                    level.setBlock(b.offset(c.dx(), c.dy(), c.dz()), c.state(), 3);
                }
                level.setBlock(b.below(), ModBlocks.CREATIVE_SOURCE_JAR.get().defaultBlockState(), 3);
                for (int x = 2; x <= 10; x++) {
                    level.setBlock(b.offset(x, 0, -1), ModBlocks.BASIC_SNOW_PIPE.get().defaultBlockState(), 3);
                }
                level.setBlock(b.offset(4, 0, -2), ModBlocks.ANALOG_TERMINAL.get().defaultBlockState()
                        .setValue(com.golemlogix.blocks.AnalogTerminalBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
                level.setBlock(b.offset(6, 0, -2), ModBlocks.CRYO_CALCULATOR.get().defaultBlockState(), 3);
                level.setBlock(b.offset(8, 0, -2), ModBlocks.PATTERN_PROVIDER.get().defaultBlockState(), 3);
                com.golemlogix.blocks.CopperDriveBayBlock.place(level, b.offset(12, 0, -1), net.minecraft.core.Direction.NORTH);
                if (level.getBlockEntity(b.offset(12, 0, -1)) instanceof CopperDriveBayBlockEntity bay) {
                    bay.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_64K.get()));
                    bay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_16K.get()));
                }
                if (level.getBlockEntity(b.offset(8, 0, -2)) instanceof com.golemlogix.blockentity.PatternProviderBlockEntity prov) {
                    java.util.List<ItemStack> logGrid = new java.util.ArrayList<>();
                    java.util.List<ItemStack> stickGrid = new java.util.ArrayList<>();
                    for (int i = 0; i < 9; i++) {
                        logGrid.add(i == 0 ? new ItemStack(Items.OAK_LOG) : ItemStack.EMPTY);
                        stickGrid.add(i == 0 || i == 3 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
                    }
                    prov.getContainer().setItem(0, card(new com.golemlogix.data.PatternData(true, logGrid, java.util.List.of(new ItemStack(Items.OAK_PLANKS, 4)))));
                    prov.getContainer().setItem(1, card(new com.golemlogix.data.PatternData(true, stickGrid, java.util.List.of(new ItemStack(Items.STICK, 4)))));
                }
                refresh(level, b.above(3));
                log.accept("rig built at " + b);
            }
            case "netput" -> {
                // netput x y z <item> <count>: put into the network (next to the block)
                com.golemlogix.system.GolemNetwork network = com.golemlogix.system.GolemNetwork.scan(level, pos(a, 0));
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                ItemStack rest = network.insertItem(new ItemStack(item, Integer.parseInt(a[4])), false);
                log.accept("netput " + a[3] + " x" + a[4] + " -> rest " + rest.getCount());
            }
            case "netcheck" -> {
                // netcheck x y z <item> <expected count>
                com.golemlogix.system.GolemNetwork network = com.golemlogix.system.GolemNetwork.scan(level, pos(a, 0));
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                long actual = network.itemCountOf(com.golemlogix.system.ItemKey.of(new ItemStack(item)));
                log.accept((actual == Long.parseLong(a[4]) ? "OK" : "ERR") + " netcheck " + a[3] + " = " + actual + " (expected " + a[4] + ")");
            }
            case "baycheck" -> {
                // baycheck x y z <slot> <item|empty>
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                if (!(be instanceof CopperDriveBayBlockEntity bay)) {
                    throw new IllegalStateException("no cabinet");
                }
                ItemStack in = bay.getDisks().get(Integer.parseInt(a[3]));
                String actual = in.isEmpty() ? "empty" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(in.getItem()).toString();
                log.accept((actual.equals(a[4]) ? "OK" : "ERR") + " baycheck slot " + a[3] + " = " + actual + " (expected " + a[4] + ")");
            }
            case "saturatordecks" -> {
                // saturatordecks x y z [item count]: the input and output decks, the anvil, Source, whether a blank counts as input;
                // with an item: OK if the output deck holds that many of it (the taker golem carries results there from the anvil)
                if (!(level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.ManaSaturatorBlockEntity sat)) {
                    throw new IllegalStateException("no saturator");
                }
                String verdict = "";
                if (a.length > 4) {
                    ItemStack out = sat.outputDeck();
                    boolean ok = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.getItem()).toString().equals(a[3])
                            && out.getCount() == Integer.parseInt(a[4]);
                    verdict = (ok ? "OK" : "ERR") + " ";
                }
                log.accept(verdict + "saturatordecks in=" + sat.inputDeck() + " out=" + sat.outputDeck() + " anvil=" + sat.getHeldItem()
                        + " source=" + sat.getSource() + " ironIsInput=" + com.golemlogix.blockentity.ManaSaturatorBlockEntity.isInput(level,
                        new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK)) + " recipes=" + com.golemlogix.system.Recipes.byType(level,
                        com.golemlogix.init.ModRecipes.SATURATOR_TYPE.get()).size());
            }
            case "diskstore" -> {
                // diskstore <uuid> <items> <fluid mB>: what the server's disk store holds for a disk (a world from 1.21.1 moves its old file)
                var disk = com.golemlogix.system.DiskStore.get(level.getServer()).getOrNull(java.util.UUID.fromString(a[0]));
                long items = disk == null ? -1 : disk.storedItems, fluids = disk == null ? -1 : disk.storedFluids;
                boolean ok = items == Long.parseLong(a[1]) && fluids == Long.parseLong(a[2]);
                log.accept((ok ? "OK" : "ERR") + " diskstore " + a[0] + ": " + items + " items, " + fluids + " mB (expected " + a[1] + ", " + a[2] + ")");
            }
            case "netfluidput" -> {
                // netfluidput x y z <fluid> <mB>
                com.golemlogix.system.GolemNetwork network = com.golemlogix.system.GolemNetwork.scan(level, pos(a, 0));
                var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                int filled = network.insertFluid(new net.neoforged.neoforge.fluids.FluidStack(fluid, Integer.parseInt(a[4])), false);
                log.accept("netfluidput " + a[3] + " -> " + filled);
            }
            case "carry" -> {
                // carry <item> <count>: an item on the cursor of the open window (synced)
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[0]));
                player.containerMenu.setCarried(new ItemStack(item, Integer.parseInt(a[1])));
                player.containerMenu.broadcastChanges();
            }
            case "netfluid" -> {
                // netfluid x y z <fluid> <expected mB>
                com.golemlogix.system.GolemNetwork network = com.golemlogix.system.GolemNetwork.scan(level, pos(a, 0));
                var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                long actual = 0;
                for (var f : network.listFluids()) {
                    if (f.getFluid() == fluid) {
                        actual += f.getAmount();
                    }
                }
                log.accept((actual == Long.parseLong(a[4]) ? "OK" : "ERR") + " netfluid " + a[3] + " = " + actual + " (expected " + a[4] + ")");
            }
            case "buscheck" -> {
                // buscheck x y z <filter slot> <item|empty>
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                if (!(be instanceof com.golemlogix.blockentity.BusBlockEntity bus)) {
                    throw new IllegalStateException("no bus");
                }
                ItemStack in = bus.getFilters().getItem(Integer.parseInt(a[3]));
                String actual = in.isEmpty() ? "empty" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(in.getItem()).toString();
                log.accept((actual.equals(a[4]) ? "OK" : "ERR") + " buscheck slot " + a[3] + " = " + actual + " (expected " + a[4] + ")");
            }
            case "give" -> {
                // give <item> <count> [slot]
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[0]));
                player.getInventory().setItem(a.length > 2 ? Integer.parseInt(a[2]) : 0, new ItemStack(item, Integer.parseInt(a[1])));
            }
            case "fillnet" -> {
                // fillnet x y z: puts various items and fluids into the network (next to the block) for terminal windows
                com.golemlogix.system.GolemNetwork network = com.golemlogix.system.GolemNetwork.scan(level, pos(a, 0));
                Item[] items = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND,
                        Items.EMERALD, Items.COPPER_INGOT, Items.REDSTONE,
                        Items.OAK_LOG, Items.COBBLESTONE, Items.STONE,
                        Items.SAND, Items.GLASS, Items.COAL,
                        Items.LAPIS_LAZULI, Items.QUARTZ, Items.AMETHYST_SHARD,
                        Items.ENDER_PEARL, Items.BLAZE_ROD, Items.OBSIDIAN,
                        Items.SNOWBALL, Items.PACKED_ICE, Items.CRAFTING_TABLE,
                        Items.CHEST, Items.BARREL, Items.PURPUR_BLOCK,
                        Items.END_ROD, Items.IRON_BLOCK, Items.COPPER_BLOCK};
                for (int i = 0; i < items.length; i++) {
                    network.insertItem(new ItemStack(items[i], 8 + (i * 37) % 600), false);
                }
                network.insertFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 7500), false);
                network.insertFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 2500), false);
                log.accept("network filled: " + network.listItems().size() + " item types, " + network.listFluids().size() + " fluids");
            }
            case "mode" -> {
                player.setGameMode("survival".equals(a[0]) ? net.minecraft.world.level.GameType.SURVIVAL : net.minecraft.world.level.GameType.CREATIVE);
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            case "giveall" -> {
                // giveall <offset>: 36 mod items into the inventory, starting from number <offset>
                int offset = a.length > 0 ? Integer.parseInt(a[0]) : 0;
                player.getInventory().clearContent();
                var entries = new java.util.ArrayList<>(com.golemlogix.init.ModItems.ITEMS.getEntries());
                for (int i = 0; i < 36 && offset + i < entries.size(); i++) {
                    player.getInventory().setItem(i, new ItemStack(entries.get(offset + i).get()));
                }
                log.accept("items total " + entries.size());
            }
            case "refresh" -> refresh(level, pos(a, 0));
            case "stock" -> stock(level, pos(a, 0));
            case "bay" -> {
                // bay x y z facing [stock]: a 2x2 cabinet, the main part at (x, y, z)
                BlockPos at = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                com.golemlogix.blocks.CopperDriveBayBlock.place(level, at, facing);
                if (a.length > 4) {
                    stock(level, at);
                }
            }
            case "saturator" -> {
                // saturator x y z facing [jar]: all machine blocks around the owner at (x, y, z); jar = a Source jar on the side
                BlockPos at = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                for (int i = 0; i < com.golemlogix.system.SaturatorStructure.CELLS; i++) {
                    BlockPos p = com.golemlogix.system.SaturatorStructure.cellPos(at, facing, i);
                    BlockState s = com.golemlogix.system.SaturatorStructure.required(i).defaultBlockState();
                    if (i == com.golemlogix.system.SaturatorStructure.CONTROLLER) {
                        s = s.setValue(com.golemlogix.blocks.ManaSaturatorBlock.FACING, facing);
                    }
                    level.setBlock(p, s, 3);
                }
                if (a.length > 4 && a[4].equals("jar")) {
                    BlockPos side = com.golemlogix.system.SaturatorStructure.cellPos(at, facing, com.golemlogix.system.SaturatorStructure.index(0, 0, 0))
                            .relative(com.golemlogix.system.SaturatorStructure.right(facing).getOpposite());
                    level.setBlock(side, ModBlocks.CREATIVE_SOURCE_JAR.get().defaultBlockState(), 3);
                }
                log.accept("saturator built at " + at + " facing " + facing);
            }
            case "telegraph" -> {
                // telegraph x y z facing: a whole Cryo-Source Telegraph, the main cell at (x, y, z)
                BlockPos at = pos(a, 0);
                com.golemlogix.blocks.TelegraphBlock.place(level, at, net.minecraft.core.Direction.byName(a[3]));
                log.accept("telegraph built at " + at);
            }
            case "telegraphlink" -> {
                // telegraphlink ax ay az bx by bz [range]: a card written at telegraph A goes into B's Memory; range 1/2 = a range card in A
                if (!(level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.TelegraphBlockEntity ta)
                        || !(level.getBlockEntity(pos(a, 3)) instanceof com.golemlogix.blockentity.TelegraphBlockEntity tb)) {
                    throw new IllegalStateException("no telegraph");
                }
                ta.slots().setItem(com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_WRITE, new ItemStack(ModItems.AETHERIAL_LINKAGE.get()));
                ItemStack card = ta.slots().removeItem(com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_WRITE, 1);
                tb.slots().setItem(com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_MEMORY, card);
                if (a.length > 6) {
                    ta.slots().setItem(com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_RANGE,
                            new ItemStack(a[6].equals("2") ? ModItems.VOID_GLASS_PRISM.get() : ModItems.AETHER_RESONANCE_CORE.get()));
                }
                log.accept("telegraph link " + com.golemlogix.system.TelegraphLinks.status(tb));
            }
            case "mainframe" -> {
                // mainframe x y z facing [calculators] [coprocessors]: the cabinet, glass, casings, atlants; contents inside
                BlockPos at = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                level.setBlock(at, ModBlocks.CRYO_MAINFRAME.get().defaultBlockState()
                        .setValue(com.golemlogix.blocks.CryoMainframeBlock.FACING, facing), 3);
                // in cell order: for atlants the lower half is placed before the upper one
                for (int i = 0; i < com.golemlogix.system.MainframeStructure.CELLS.length; i++) {
                    level.setBlock(com.golemlogix.system.MainframeStructure.cellPos(at, facing, i),
                            com.golemlogix.system.MainframeStructure.originalState(i, facing), 3);
                }
                com.golemlogix.system.MainframeStructure.form(level, at, facing);
                if (level.getBlockEntity(at) instanceof com.golemlogix.blockentity.CryoMainframeBlockEntity mf) {
                    int calcs = a.length > 4 ? Integer.parseInt(a[4]) : 0, cops = a.length > 5 ? Integer.parseInt(a[5]) : 0;
                    for (int i = 0; i < calcs; i++) {
                        mf.getCalculators().setItem(i, new ItemStack(ModBlocks.CRYO_CALCULATOR.get()));
                    }
                    for (int i = 0; i < cops; i++) {
                        mf.getUpgrades().setItem(i, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));
                    }
                    mf.setChanged();
                }
                log.accept("mainframe built at " + at + " facing " + facing);
            }
            case "bayopen" -> {
                // bayopen x y z 1|0: open or close the drive cabinet door, as if the window were open
                BlockState bs = level.getBlockState(pos(a, 0));
                CopperDriveBayBlockEntity bay = bs.getBlock() instanceof com.golemlogix.blocks.CopperDriveBayBlock
                        ? com.golemlogix.blocks.CopperDriveBayBlock.entityOf(level, pos(a, 0), bs) : null;
                if (bay == null) {
                    throw new IllegalStateException("no drive bay at " + pos(a, 0));
                }
                if (a[3].equals("1")) {
                    bay.getContainer().startOpen(player);
                } else {
                    bay.getContainer().stopOpen(player);
                }
                log.accept("bay at " + bay.getBlockPos() + " viewers " + bay.viewers());
            }
            case "catalog" -> {
                // catalog x y z facing [cards] [providers]: shelves, copper, statues (the right one oxidized); cards with crafting recipes inside
                BlockPos at = pos(a, 0);
                net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(a[3]);
                level.setBlock(at, ModBlocks.CARD_CATALOG.get().defaultBlockState()
                        .setValue(com.golemlogix.blocks.CardCatalogBlock.FACING, facing), 3);
                for (int i = 0; i < com.golemlogix.system.CatalogStructure.CELLS.length; i++) {
                    BlockState s = com.golemlogix.system.CatalogStructure.fallback(i).defaultBlockState();
                    if (i == 14) {
                        s = net.minecraft.world.level.block.Blocks.OXIDIZED_COPPER_GOLEM_STATUE.defaultBlockState();
                    } else if (i == 13) {
                        s = net.minecraft.world.level.block.Blocks.OXIDIZED_COPPER.defaultBlockState();
                    }
                    if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                        s = s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
                    }
                    level.setBlock(com.golemlogix.system.CatalogStructure.cellPos(at, facing, i), s, 3);
                }
                com.golemlogix.system.CatalogStructure.form(level, at, facing);
                if (level.getBlockEntity(at) instanceof com.golemlogix.blockentity.CardCatalogBlockEntity cat) {
                    int cards = a.length > 4 ? Integer.parseInt(a[4]) : 0, provs = a.length > 5 ? Integer.parseInt(a[5]) : 2;
                    cat.getProviders().setItem(0, new ItemStack(ModBlocks.PATTERN_PROVIDER.get(), provs));
                    Item[] outs = {Items.OAK_PLANKS, Items.STICK, Items.CHEST, Items.CRAFTING_TABLE, Items.TORCH, Items.LADDER, Items.BOWL,
                            Items.OAK_SLAB, Items.OAK_STAIRS, Items.OAK_FENCE, Items.BARREL, Items.IRON_BLOCK, Items.GOLD_BLOCK, Items.HOPPER};
                    for (int i = 0; i < Math.min(cards, cat.capacity()); i++) {
                        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
                        for (int k = 0; k < 9; k++) {
                            grid.add(k <= i % 4 ? new ItemStack(Items.OAK_LOG) : ItemStack.EMPTY);
                        }
                        cat.getCards().setItem(i, card(new com.golemlogix.data.PatternData(true, grid,
                                java.util.List.of(new ItemStack(outs[i % outs.length], 1 + i % 4)))));
                    }
                    cat.syncNow();
                }
                log.accept("catalog built at " + at + " facing " + facing);
            }
            case "catalogopen" -> {
                // catalogopen x y z 1|0: slide the drawers out or in, as if the window were open
                if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.CardCatalogBlockEntity cat) {
                    if (a[3].equals("1")) {
                        cat.startOpen();
                    } else {
                        cat.stopOpen();
                    }
                }
            }
            case "concentratoropen" -> {
                if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.SchematicConcentratorBlockEntity c) {
                    if (a[3].equals("1")) {
                        c.startOpen();
                    } else {
                        c.stopOpen();
                    }
                }
            }
            case "craftcard" -> {
                // craftcard x y z slot <result> <count> <9 grid cells, "-" = empty>: a crafting-table punch card into a provider
                if (!(level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.PatternProviderBlockEntity prov)) {
                    throw new IllegalStateException("no provider at " + pos(a, 0));
                }
                java.util.List<ItemStack> grid = new java.util.ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    String id = a[6 + i];
                    grid.add(id.equals("-") ? ItemStack.EMPTY
                            : new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id))));
                }
                ItemStack result;
                boolean isCrafting = !a[4].startsWith("fluid:");
                if (!isCrafting) {
                    var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(a[4].substring(6)));
                    result = com.golemlogix.items.FluidGhostItem.of(new net.neoforged.neoforge.fluids.FluidStack(fluid, Integer.parseInt(a[5])));
                } else {
                    result = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                            net.minecraft.resources.Identifier.parse(a[4])), Integer.parseInt(a[5]));
                }
                prov.getContainer().setItem(Integer.parseInt(a[3]), card(new com.golemlogix.data.PatternData(isCrafting, isCrafting ? grid : java.util.List.of(new ItemStack(Items.ICE)), java.util.List.of(result))));
                log.accept("craftcard " + a[4] + " in slot " + a[3]);
            }
            case "provfill" -> {
                // provfill x y z smelt|craft|mix: punch cards into a provider (ore smelting, crafting table ones, or Create mixing:
                // a block + 250 mB of Liquid Cryo-Source -> a torn mind)
                if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.PatternProviderBlockEntity prov && a[3].equals("wood")) {
                    // wood: a log into planks, planks into sticks (a long real order for the calculator's crews)
                    java.util.List<ItemStack> logGrid = new java.util.ArrayList<>();
                    java.util.List<ItemStack> stickGrid = new java.util.ArrayList<>();
                    for (int i = 0; i < 9; i++) {
                        logGrid.add(i == 0 ? new ItemStack(Items.OAK_LOG) : ItemStack.EMPTY);
                        stickGrid.add(i == 0 || i == 3 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
                    }
                    prov.getContainer().setItem(0, card(new com.golemlogix.data.PatternData(true, logGrid, java.util.List.of(new ItemStack(Items.OAK_PLANKS, 4)))));
                    prov.getContainer().setItem(1, card(new com.golemlogix.data.PatternData(true, stickGrid, java.util.List.of(new ItemStack(Items.STICK, 4)))));
                } else if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.PatternProviderBlockEntity prov && a[3].equals("mix")) {
                    Item[][] mix = {{Items.COPPER_BLOCK, com.golemlogix.init.ModItems.TORN_COPPER_MIND.get()},
                            {Items.IRON_BLOCK, com.golemlogix.init.ModItems.TORN_IRON_MIND.get()},
                            {Items.PACKED_ICE, com.golemlogix.init.ModItems.TORN_SNOW_MIND.get()}};
                    for (int i = 0; i < mix.length; i++) {
                        prov.getContainer().setItem(i, card(new com.golemlogix.data.PatternData(false,
                                java.util.List.of(new ItemStack(mix[i][0]), com.golemlogix.items.FluidGhostItem.of(com.golemlogix.system.CryoSource.stack(250))),
                                java.util.List.of(new ItemStack(mix[i][1])))));
                    }
                } else if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.PatternProviderBlockEntity prov) {
                    Item[][] smelt = {{Items.IRON_ORE, Items.IRON_INGOT}, {Items.GOLD_ORE, Items.GOLD_INGOT}, {Items.COPPER_ORE, Items.COPPER_INGOT},
                            {Items.SAND, Items.GLASS}, {Items.COBBLESTONE, Items.STONE}};
                    Item[] craft = {Items.CHEST, Items.TORCH, Items.LADDER, Items.BOWL};
                    boolean isSmelt = a[3].equals("smelt");
                    int n = isSmelt ? smelt.length : craft.length;
                    for (int i = 0; i < n; i++) {
                        com.golemlogix.data.PatternData d;
                        if (isSmelt) {
                            d = new com.golemlogix.data.PatternData(false, java.util.List.of(new ItemStack(smelt[i][0])), java.util.List.of(new ItemStack(smelt[i][1])));
                        } else {
                            java.util.List<ItemStack> grid = new java.util.ArrayList<>();
                            for (int k = 0; k < 9; k++) {
                                grid.add(k < 2 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
                            }
                            d = new com.golemlogix.data.PatternData(true, grid, java.util.List.of(new ItemStack(craft[i])));
                        }
                        prov.getContainer().setItem(i, card(d));
                    }
                }
            }
            case "anvilput" -> {
                // anvilput x y z <item>: put a blank on the anvil of an unassembled saturator
                if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.ManaSaturatorBlockEntity sat) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                    sat.placeOnAnvil(new ItemStack(item));
                }
            }
            case "anvilstrike" -> {
                // anvilstrike x y z: one strike of forging by hand (as a Forge Hammer left-click)
                if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.ManaSaturatorBlockEntity sat) {
                    ItemStack hammer = new ItemStack(com.golemlogix.init.ModItems.FORGE_HAMMER.get());
                    com.golemlogix.items.ForgeHammerItem.setCharge(hammer, com.golemlogix.items.ForgeHammerItem.MAX_CHARGE);
                    log.accept("strike -> " + sat.strike(level, pos(a, 0), player, hammer) + " hits " + sat.manualHits());
                }
            }
            case "mainframeopen" -> {
                // mainframeopen x y z 1|0: open or close the doors, as if the window were open
                if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.CryoMainframeBlockEntity mf) {
                    if (a[3].equals("1")) {
                        mf.startOpen();
                    } else {
                        mf.stopOpen();
                    }
                }
            }
            case "mainframework" -> {
                // mainframework x y z 1|0 [item1] [item2]: turn the cabinet's visual work on or off
                if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.CryoMainframeBlockEntity mf) {
                    mf.setVisualWorkForDev(a[3].equals("1"), a.length > 4 ? a[4] : null, a.length > 5 ? a[5] : null);
                }
            }
            case "calcwork" -> {
                // calcwork x y z 1|0 [item]: turn the calculator's visual work on or off
                if (level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.CryoCalculatorBlockEntity calc) {
                    calc.setVisualWorkForDev(a[3].equals("1"), a.length > 4 ? a[4] : null);
                }
            }
            case "saturatorput" -> {
                // saturatorput x y z <item>: put an item on the anvil (as a right-click with the item)
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                if (!(be instanceof com.golemlogix.blockentity.ManaSaturatorBlockEntity sat)) {
                    throw new IllegalStateException("no saturator");
                }
                // a blank goes to the left deck (the golems will carry it), minecraft:air clears the anvil and the decks
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                ItemStack stack = new ItemStack(item, a.length > 4 ? Integer.parseInt(a[4]) : 1);
                if (stack.isEmpty()) {
                    sat.setHeldItem(ItemStack.EMPTY);
                    sat.takeFromDecks();
                    sat.takeFromDecks();
                } else if (com.golemlogix.blockentity.ManaSaturatorBlockEntity.isInput(level, stack)) {
                    sat.addToDeck(stack.copyWithCount(Math.min(stack.getCount(), sat.roomInDeck(stack))));
                } else {
                    sat.setHeldItem(stack);
                }
            }
            case "saturatorheld" -> {
                // saturatorheld x y z <item> <count>: what lies on the anvil and how much Source is in the columns
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                if (!(be instanceof com.golemlogix.blockentity.ManaSaturatorBlockEntity sat)) {
                    throw new IllegalStateException("no saturator");
                }
                ItemStack held = sat.getHeldItem();
                String actual = held.isEmpty() ? "empty" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()) + " x" + held.getCount();
                String expected = a[3] + (a.length > 4 ? " x" + a[4] : "");
                log.accept((actual.equals(expected) ? "OK" : "ERR") + " saturatorheld = " + actual + " (expected " + expected + "), source " + sat.getSource());
            }
            case "saturatorcheck" -> {
                // saturatorcheck x y z formed|unformed
                BlockState s = level.getBlockState(pos(a, 0));
                String actual = s.getBlock() instanceof com.golemlogix.blocks.ManaSaturatorBlock
                        ? (s.getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED) ? "formed" : "unformed") : "missing";
                log.accept((actual.equals(a[3]) ? "OK" : "ERR") + " saturatorcheck = " + actual + " (expected " + a[3] + ")");
            }
            case "plug" -> {
                BlockPos p = pos(a, 0);
                net.minecraft.core.Direction side = net.minecraft.core.Direction.byName(a[3]);
                if (level.getBlockEntity(p) instanceof com.golemlogix.blockentity.PipeBlockEntity be) {
                    be.setPlugged(side, true);
                    if (level.getBlockState(p).getBlock() instanceof com.golemlogix.blocks.SnowPipeBlock pipe) {
                        level.setBlock(p, pipe.relink(level.getBlockState(p), level, p), 3);
                    }
                    log.accept("plugged " + p + " " + side);
                }
            }
            case "tower" -> {
                // tower x y z [full]: the starter tower (or the whole array) without a Source source; the core at (x, y+3, z) / (x, y+3, z) for the array
                BlockPos base = pos(a, 0);
                var variant = a.length > 3 && a[3].equals("full") ? AtlasBlueprint.Variant.FULL : AtlasBlueprint.Variant.TOWER;
                for (AtlasBlueprint.Cell c : AtlasBlueprint.cells(variant)) {
                    level.setBlock(base.offset(c.dx(), c.dy(), c.dz()), c.state(), 3);
                }
                log.accept(variant + " built at " + base);
            }
            case "corecheck" -> {
                // corecheck x y z on|off: whether the core is powered
                BlockPos at = pos(a, 0);
                refresh(level, at);
                String actual = level.getBlockEntity(at) instanceof AtlasCoreBlockEntity c ? (c.isPowered() ? "on" : "off") : "missing";
                String extra = level.getBlockEntity(at) instanceof AtlasCoreBlockEntity c
                        ? " channels " + c.getChannelsUsed() + "/" + c.getChannelCapacity() + " source/s " + c.getSourcePerSecond() + (c.isInfiniteSource() ? " (infinite)" : "")
                        : "";
                log.accept((actual.equals(a[3]) ? "OK" : "ERR") + " corecheck = " + actual + " (expected " + a[3] + ")" + extra);
            }
            case "sourcecheck" -> {
                // sourcecheck x y z <gt|lt|eq> <n>: how much Source the provider (an Ars Nouveau jar and the like) at the point gives
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                var s = be == null ? null : com.golemlogix.system.SourceHelper.asSupplier(be);
                long actual = s == null ? -1 : s.available();
                long n = Long.parseLong(a[4]);
                boolean ok = switch (a[3]) {
                    case "gt" -> actual > n;
                    case "lt" -> actual >= 0 && actual < n;
                    default -> actual == n;
                };
                log.accept((ok ? "OK" : "ERR") + " sourcecheck " + (be == null ? "no block entity" : be.getType().builtInRegistryHolder().key().identifier())
                        + " = " + actual + " (expected " + a[3] + " " + n + ")");
            }
            case "itemnear" -> {
                // itemnear x y z r <item> <expected count>: dropped items near the point
                BlockPos at = pos(a, 0);
                double r = Double.parseDouble(a[3]);
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[4]));
                int n = 0;
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(r))) {
                    if (e.getItem().is(item)) {
                        n += e.getItem().getCount();
                    }
                }
                log.accept((n == Integer.parseInt(a[5]) ? "OK" : "ERR") + " itemnear " + a[4] + " = " + n + " (expected " + a[5] + ")");
            }
            case "mobcheck" -> {
                // mobcheck x y z r <entity> <expected count> [hp]: living mobs nearby (and their health)
                BlockPos at = pos(a, 0);
                double r = Double.parseDouble(a[3]);
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(a[4]));
                var list = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(at).inflate(r), e -> e.getType() == type && e.isAlive());
                StringBuilder hp = new StringBuilder();
                for (var e : list) {
                    hp.append(' ').append(String.format(java.util.Locale.ROOT, "%.1f/%.1f", e.getHealth(), e.getMaxHealth()));
                }
                log.accept((list.size() == Integer.parseInt(a[5]) ? "OK" : "ERR") + " mobcheck " + a[4] + " = " + list.size() + " (expected " + a[5] + ")" + hp);
            }
            case "coolers" -> {
                // coolers x y z n: Cooling Co-Processors on the first n roof grilles of the Saturator (owner at x y z)
                BlockPos at = pos(a, 0);
                var facing = level.getBlockState(at).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FACING);
                int n = Integer.parseInt(a[3]);
                int[] vents = com.golemlogix.system.SaturatorStructure.VENTS;
                for (int i = 0; i < vents.length; i++) {
                    BlockPos p = com.golemlogix.system.SaturatorStructure.cellPos(at, facing, vents[i]);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof com.golemlogix.blocks.SaturatorPartBlock) {
                        level.setBlock(p, s.setValue(com.golemlogix.blocks.SaturatorPartBlock.COOLER, i < n), 3);
                    }
                }
                log.accept("coolers on the roof: " + n);
            }
            case "craft" -> {
                // craft x y z <item> <count>: an autocraft order on behalf of the player (a notification comes when it is ready)
                com.golemlogix.system.GolemNetwork network = com.golemlogix.system.GolemNetwork.scan(level, pos(a, 0));
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                java.util.Map<com.golemlogix.system.ItemKey, Long> targets = new java.util.LinkedHashMap<>();
                targets.put(com.golemlogix.system.ItemKey.of(new ItemStack(item)), Long.parseLong(a[4]));
                var r = network.startCrafting(targets, player.getUUID(), null);
                log.accept((r.type() == com.golemlogix.system.GolemNetwork.StartType.STARTED ? "OK" : "ERR") + " craft " + a[3] + " x" + a[4] + " -> " + r.type());
            }
            case "netfluidcheck" -> {
                // netfluidcheck x y z <fluid> <minimum mB>: how much fluid is in the network
                var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                var network = com.golemlogix.system.GolemNetwork.scan(level, pos(a, 0));
                int n = network.extractFluid(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1), Integer.MAX_VALUE, true).getAmount();
                log.accept((n >= Integer.parseInt(a[4]) ? "OK" : "ERR") + " netfluidcheck " + a[3] + " = " + n + " (at least " + a[4] + ")");
            }
            case "lightdark" -> {
                // lightdark x y z: zero the saved light of one cell, as a chunk saved when a full cube stood there holds it
                BlockPos at = pos(a, 0);
                var engine = level.getChunkSource().getLightEngine();
                net.minecraft.core.SectionPos sec = net.minecraft.core.SectionPos.of(at);
                for (var layer : net.minecraft.world.level.LightLayer.values()) {
                    var data = engine.getLayerListener(layer).getDataLayerData(sec);
                    if (data != null) {
                        var copy = data.copy();
                        copy.set(at.getX() & 15, at.getY() & 15, at.getZ() & 15, 0);
                        engine.queueSectionData(layer, sec, copy);
                    } else {
                        log.accept("lightdark: no " + layer + " data");
                    }
                }
                log.accept("lightdark done");
            }
            case "lightdarkclient" -> {
                // the client's copy of one cell only: the server still holds the right value, so it never sends an update
                BlockPos at = pos(a, 0);
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    var engine = mc.level.getLightEngine();
                    net.minecraft.core.SectionPos sec = net.minecraft.core.SectionPos.of(at);
                    for (var layer : net.minecraft.world.level.LightLayer.values()) {
                        var data = engine.getLayerListener(layer).getDataLayerData(sec);
                        if (data != null) {
                            var copy = data.copy();
                            copy.set(at.getX() & 15, at.getY() & 15, at.getZ() & 15, 0);
                            engine.queueSectionData(layer, sec, copy);
                        }
                    }
                    mc.level.setSectionDirtyWithNeighbors(sec.x(), sec.y(), sec.z());
                });
                log.accept("lightdarkclient queued");
            }
            case "lightlogclient" -> {
                BlockPos at = pos(a, 0);
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> com.golemlogix.GolemLogixMod.LOGGER.info("[lightclient] {}: client sky {} server sky {}", a[3],
                        mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at),
                        level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at)));
            }
            case "lightlog" -> {
                BlockPos at = pos(a, 0);
                log.accept("light " + a[3] + ": sky " + level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at) + " block "
                        + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at) + " state " + level.getBlockState(at).getBlock());
            }
            case "lightcheck" -> {
                BlockPos at = pos(a, 0);
                level.getChunkSource().getLightEngine().checkBlock(at);
                log.accept("lightcheck queued");
            }
            case "nbt" -> {
                // nbt x y z: block entity data (for setting up other mods' blocks in scenarios)
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                log.accept("nbt " + (be == null ? "none" : be.saveWithoutMetadata(level.registryAccess()).toString()));
            }
            case "fillfluid" -> {
                // fillfluid x y z <fluid> <mB>: pour fluid into a block (another mod's tank) through its fluid handler
                var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                var h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, pos(a, 0), net.minecraft.core.Direction.UP);
                int n = h == null ? -1 : com.golemlogix.system.Transfers.insertFluid(h,
                        new net.neoforged.neoforge.fluids.FluidStack(fluid, Integer.parseInt(a[4])), false);
                log.accept("fillfluid " + a[3] + " -> " + n);
            }
            case "insertitem" -> {
                // insertitem x y z <item> <count>: put items into a block (another mod's machine) through its item handler
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                var h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos(a, 0), net.minecraft.core.Direction.UP);
                ItemStack rest = h == null ? new ItemStack(item, Integer.parseInt(a[4]))
                        : com.golemlogix.system.Transfers.insertItem(h, new ItemStack(item, Integer.parseInt(a[4])), false, false);
                log.accept("insertitem " + a[3] + " rest " + rest.getCount());
            }
            case "busfilter" -> {
                // busfilter x y z <slot> <item | fluid:<fluid id>> [mB]: a bus filter slot (a fluid becomes a fluid ghost)
                if (!(level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.BusBlockEntity bus)) {
                    throw new IllegalStateException("no bus at " + pos(a, 0));
                }
                ItemStack ghost;
                if (a[4].startsWith("fluid:")) {
                    var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(a[4].substring(6)));
                    ghost = com.golemlogix.items.FluidGhostItem.of(new net.neoforged.neoforge.fluids.FluidStack(fluid, a.length > 5 ? Integer.parseInt(a[5]) : 250));
                } else {
                    ghost = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[4])));
                }
                bus.getFilters().setItem(Integer.parseInt(a[3]), ghost);
                log.accept("busfilter slot " + a[3] + " = " + a[4]);
            }
            case "fluidcheck" -> {
                // fluidcheck x y z <fluid> <minimum mB>: how much of this fluid is in the block (in all its tanks)
                var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(a[3]));
                var h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, pos(a, 0), null);
                int total = 0;
                for (int t = 0; h != null && t < h.size(); t++) {
                    if (h.getResource(t).is(fluid)) {
                        total += h.getAmountAsInt(t);
                    }
                }
                log.accept((total >= Integer.parseInt(a[4]) ? "OK" : "ERR") + " fluidcheck " + a[3] + " = " + total + " (at least " + a[4] + ")");
            }
            case "iface" -> {
                // iface x y z <slot> <item> <how many to keep>: Interface stock setting
                if (!(level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.InterfaceBlockEntity be)) {
                    throw new IllegalStateException("no interface at " + pos(a, 0));
                }
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[4]));
                be.getConfig().setItem(Integer.parseInt(a[3]), new ItemStack(item, Integer.parseInt(a[5])));
                log.accept("iface slot " + a[3] + " keeps " + a[5] + " " + a[4]);
            }
            case "ifacecheck" -> {
                // ifacecheck x y z <slot> <expected stock count>
                if (!(level.getBlockEntity(pos(a, 0)) instanceof com.golemlogix.blockentity.InterfaceBlockEntity be)) {
                    throw new IllegalStateException("no interface at " + pos(a, 0));
                }
                int actual = be.getStock().getItem(Integer.parseInt(a[3])).getCount();
                log.accept((actual == Integer.parseInt(a[4]) ? "OK" : "ERR") + " ifacecheck slot " + a[3] + " = " + actual + " (expected " + a[4] + ")");
            }
            case "upgrades" -> {
                // upgrades x y z <item>...: upgrades into the machine slots (bus, periscope, provider, calculator) in order
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                com.golemlogix.upgrade.UpgradeContainer c;
                if (be instanceof com.golemlogix.blockentity.BusBlockEntity b) {
                    c = b.getUpgrades();
                } else if (be instanceof com.golemlogix.blockentity.PeriscopeBlockEntity p) {
                    c = p.getUpgrades();
                } else if (be instanceof com.golemlogix.blockentity.PatternProviderBlockEntity p) {
                    c = p.getUpgrades();
                } else if (be instanceof com.golemlogix.blockentity.CryoCalculatorBlockEntity p) {
                    c = p.getUpgrades();
                } else if (be instanceof com.golemlogix.blockentity.InterfaceBlockEntity p) {
                    c = p.getUpgrades();
                } else if (be instanceof com.golemlogix.blockentity.DistillationTowerBlockEntity p) {
                    c = p.getUpgrades();
                } else {
                    throw new IllegalStateException("no machine with upgrades at " + pos(a, 0));
                }
                for (int i = 3; i < a.length && i - 3 < c.getContainerSize(); i++) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[i]));
                    c.setItem(i - 3, new ItemStack(item));
                }
                log.accept("upgrades set: " + (a.length - 3));
            }
            case "light" -> {
                // light x y z <n>: light from blocks at the point and the block's own emission there (pipes glow but give no light)
                BlockPos at = pos(a, 0);
                int actual = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at);
                log.accept((actual == Integer.parseInt(a[3]) ? "OK" : "ERR") + " light at " + at.toShortString() + " = " + actual
                        + " (expected " + a[3] + "), block emits " + level.getBlockState(at).getLightEmission(level, at));
            }
            case "chrono" -> {
                // chrono x y z [charge]: a Chronometer bound to the core at x y z, into the hand
                ItemStack c = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
                c.set(com.golemlogix.init.ModDataComponents.BOUND_CORE.get(), net.minecraft.core.GlobalPos.of(level.dimension(), pos(a, 0)));
                if (a.length > 3) {
                    com.golemlogix.items.PortableChronometerItem.setCharge(c, Integer.parseInt(a[3]));
                }
                player.getInventory().setItem(player.getInventory().getSelectedSlot(), c);
            }
            case "encoderfill" -> {
                // encoderfill x y z mode(0 crafting, 1 machine) fuzzy(0|1) item[*n][#tag] ... : the encoder's grid (from cell 0), mode and fuzzy mode
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                if (!(be instanceof com.golemlogix.blockentity.PatternEncoderBlockEntity enc)) {
                    throw new IllegalStateException("no encoder at " + pos(a, 0));
                }
                int mode = Integer.parseInt(a[3]);
                int fuzzy = Integer.parseInt(a[4]);
                enc.data().set(0, mode);
                enc.data().set(1, fuzzy);
                java.util.List<ItemStack> ins = new java.util.ArrayList<>();
                java.util.List<ItemStack> outs = new java.util.ArrayList<>();
                for (int i = 5; i < a.length && i - 5 < 12; i++) {
                    ItemStack s = ghostSpec(a[i]);
                    enc.getItems().setItem(i - 5, s);
                    if (i - 5 < 9) {
                        ins.add(s);
                    } else if (!s.isEmpty()) {
                        outs.add(s);
                    }
                }
                enc.getItems().setItem(com.golemlogix.menu.PatternEncoderMenu.CARD_IN,
                        card(new com.golemlogix.data.PatternData(mode == 0, ins, outs)));
            }
            case "filterset" -> {
                // filterset x y z slot item[*n][#tag] : a bus filter or an interface config slot
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                ItemStack ghost = ghostSpec(a[4]);
                if (be instanceof com.golemlogix.blockentity.BusBlockEntity bus) {
                    bus.getFilters().setItem(Integer.parseInt(a[3]), ghost);
                } else if (be instanceof com.golemlogix.blockentity.PeriscopeBlockEntity peri) {
                    peri.getFilters().setItem(Integer.parseInt(a[3]), ghost);
                } else if (be instanceof com.golemlogix.blockentity.FormationBusBlockEntity form) {
                    form.getFilters().setItem(Integer.parseInt(a[3]), ghost);
                } else if (be instanceof com.golemlogix.blockentity.AnnihilationBusBlockEntity anni) {
                    anni.getFilters().setItem(Integer.parseInt(a[3]), ghost);
                } else if (be instanceof com.golemlogix.blockentity.InterfaceBlockEntity itf) {
                    itf.getConfig().setItem(Integer.parseInt(a[3]), ghost);
                } else {
                    throw new IllegalStateException("no bus or interface at " + pos(a, 0));
                }
            }
            case "upgradeset" -> {
                // upgradeset x y z slot item : an upgrade into a bus or an interface
                BlockEntity be = level.getBlockEntity(pos(a, 0));
                ItemStack up = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(a[4])));
                if (be instanceof com.golemlogix.blockentity.BusBlockEntity bus) {
                    bus.getUpgrades().setItem(Integer.parseInt(a[3]), up);
                } else if (be instanceof com.golemlogix.blockentity.PeriscopeBlockEntity peri) {
                    peri.getUpgrades().setItem(Integer.parseInt(a[3]), up);
                } else if (be instanceof com.golemlogix.blockentity.FormationBusBlockEntity form) {
                    form.getUpgrades().setItem(Integer.parseInt(a[3]), up);
                } else if (be instanceof com.golemlogix.blockentity.AnnihilationBusBlockEntity anni) {
                    anni.getUpgrades().setItem(Integer.parseInt(a[3]), up);
                } else if (be instanceof com.golemlogix.blockentity.InterfaceBlockEntity itf) {
                    itf.getUpgrades().setItem(Integer.parseInt(a[3]), up);
                } else {
                    throw new IllegalStateException("no bus or interface at " + pos(a, 0));
                }
            }
            case "wearchrono" -> {
                // wearchrono: the Chronometer in the hand goes into the Curios Chronometer slot (Curios only)
                ItemStack c = player.getInventory().getSelectedItem().copy();
                if (!com.golemlogix.items.Chronometers.CURIOS || !com.golemlogix.items.Chronometers.is(c)
                        || !com.golemlogix.integration.curios.CuriosCompat.wear(player, c)) {
                    throw new IllegalStateException("no Curios Chronometer slot or no Chronometer in the hand");
                }
                player.getInventory().setItem(player.getInventory().getSelectedSlot(), ItemStack.EMPTY);
                log.accept("chronometer worn in the Curios slot");
            }
            case "flat" -> {
                // platform: dark floor and a backdrop wall
                BlockPos from = pos(a, 0), to = pos(a, 3);
                for (BlockPos p : BlockPos.betweenClosed(from, to)) {
                    level.setBlock(p, p.getY() == from.getY() ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
            case "biome" -> {
                // biome <id> [x z]: the nearest such biome (scouting a generated world)
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                        net.minecraft.resources.Identifier.parse(a[0]));
                BlockPos from = a.length > 2 ? new BlockPos(Integer.parseInt(a[1]), 64, Integer.parseInt(a[2])) : player.blockPosition();
                var found = level.findClosestBiome3d(h -> h.is(key), from, 6400, 32, 64);
                log.accept(found == null ? "biome " + a[0] + ": none" : "biome " + a[0] + " at " + found.getFirst().toShortString());
            }
            case "createbelt" -> {
                // createbelt x1 y1 z1 x2 y2 z2: a Create belt between two shafts, as the belt connector makes it (by reflection: Create
                // is only in the -Pcreate dev runs)
                try {
                    Class<?> c = Class.forName("com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem");
                    boolean ok = (boolean) c.getMethod("canConnect", net.minecraft.world.level.Level.class, BlockPos.class, BlockPos.class)
                            .invoke(null, level, pos(a, 0), pos(a, 3));
                    if (ok) {
                        c.getMethod("createBelts", net.minecraft.world.level.Level.class, BlockPos.class, BlockPos.class)
                                .invoke(null, level, pos(a, 0), pos(a, 3));
                    }
                    log.accept("createbelt " + (ok ? "made" : "cannot connect"));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("no Create belts", e);
                }
            }
            case "mapcsv" -> {
                // mapcsv x0 z0 w h step: heights, top blocks and biomes of an area into map_x0_z0.csv (scouting)
                int x0 = Integer.parseInt(a[0]), z0 = Integer.parseInt(a[1]), w = Integer.parseInt(a[2]), h = Integer.parseInt(a[3]);
                int step = Integer.parseInt(a[4]);
                StringBuilder sb = new StringBuilder("x,z,y,block,biome\n");
                for (int z = z0; z < z0 + h; z += step) {
                    for (int x = x0; x < x0 + w; x += step) {
                        var chunk = level.getChunk(x >> 4, z >> 4);
                        int y = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
                        BlockPos top = new BlockPos(x, y, z);
                        sb.append(x).append(',').append(z).append(',').append(y).append(',')
                                .append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(top).getBlock()).getPath())
                                .append(',').append(level.getBiome(top).unwrapKey().map(k -> k.identifier().getPath()).orElse("?")).append('\n');
                    }
                }
                java.nio.file.Path out = level.getServer().getServerDirectory().resolve("map_" + x0 + "_" + z0 + ".csv");
                try {
                    java.nio.file.Files.writeString(out, sb);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                log.accept("map written: " + out.toAbsolutePath());
            }
            case "surface" -> {
                // surface x z [x z ...]: the height and the block on top
                for (int i = 0; i + 1 < a.length; i += 2) {
                    int x = Integer.parseInt(a[i]), z = Integer.parseInt(a[i + 1]);
                    int y = level.getChunk(x >> 4, z >> 4).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            x & 15, z & 15);
                    log.accept("surface " + x + " " + z + ": y=" + y + " " + level.getBlockState(new BlockPos(x, y, z)).getBlock()
                            + " biome " + level.getBiome(new BlockPos(x, y, z)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
                }
            }
            default -> throw new IllegalArgumentException("unknown hook " + name);
        }
    }

    /** "minecraft:glass*2#c:glass_blocks": an item ghost with a count and a fuzzy tag. */
    private static ItemStack ghostSpec(String spec) {
        if (spec.equals("-")) {
            return ItemStack.EMPTY;
        }
        String tag = null;
        int hash = spec.indexOf('#');
        if (hash >= 0) {
            tag = spec.substring(hash + 1);
            spec = spec.substring(0, hash);
        }
        int count = 1;
        int star = spec.indexOf('*');
        if (star >= 0) {
            count = Integer.parseInt(spec.substring(star + 1));
            spec = spec.substring(0, star);
        }
        ItemStack s;
        if (spec.startsWith("fluid:")) {
            var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(spec.substring(6)));
            s = com.golemlogix.items.FluidGhostItem.of(new net.neoforged.neoforge.fluids.FluidStack(fluid, count));
        } else {
            s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(spec)), count);
        }
        return tag == null ? s : com.golemlogix.system.Fuzzy.withTag(s,
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse(tag)));
    }

    private static void refresh(ServerLevel level, BlockPos core) {
        BlockEntity be = level.getBlockEntity(core);
        if (be instanceof AtlasCoreBlockEntity c) {
            c.refresh(level, core);
        }
    }

    /** Puts a set of different disks into the cabinet. */
    private static void stock(ServerLevel level, BlockPos at) {
        BlockEntity be = level.getBlockEntity(at);
        if (!(be instanceof CopperDriveBayBlockEntity bay)) {
            throw new IllegalStateException("no cabinet at " + at);
        }
        List<Supplier<? extends Item>> disks = List.of(ModItems.COPPER_DISK_1K, ModItems.COPPER_DISK_4K, ModItems.FLUID_DISK_16K,
                ModItems.COPPER_DISK_64K, ModItems.FLUID_DISK_1K, ModItems.COPPER_DISK_256K, ModItems.FLUID_DISK_256K,
                ModItems.COPPER_DISK_16K, ModItems.FLUID_DISK_4K, ModItems.FLUID_DISK_64K);
        for (int i = 0; i < CopperDriveBayBlockEntity.SLOTS && i < disks.size(); i++) {
            bay.getContainer().setItem(i, new ItemStack(disks.get(i).get()));
        }
    }

    @SuppressWarnings("unused")
    private static BlockState state(Block b) {
        return b.defaultBlockState();
    }
}
