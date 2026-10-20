package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.items.*;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GolemLogixMod.MOD_ID);

    // Golem Crushed Plates (Early Game)
    public static final DeferredItem<ModItemWithTooltip> FLATTENED_IRON_CORE = item("flattened_iron_core",
            () -> new ModItemWithTooltip("tooltip.golem_logix.flattened_iron_core", props().stacksTo(64)));

    public static final DeferredItem<ModItemWithTooltip> FLATTENED_COPPER_MATRIX = item("flattened_copper_matrix",
            () -> new ModItemWithTooltip("tooltip.golem_logix.flattened_copper_matrix", props().stacksTo(64)));

    public static final DeferredItem<ModItemWithTooltip> FLATTENED_CRYO_SUBSTRATE = item("flattened_cryo_substrate",
            () -> new ModItemWithTooltip("tooltip.golem_logix.flattened_cryo_substrate", props().stacksTo(64)));

    // Golem minds: Dispel on a battered golem (Ars Nouveau) gives a raw mind, the Create mixer tears a block's mind
    public static final DeferredItem<ModItemWithTooltip> RAW_IRON_MIND = item("raw_iron_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.raw_mind", props().stacksTo(16)));

    public static final DeferredItem<ModItemWithTooltip> RAW_COPPER_MIND = item("raw_copper_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.raw_mind", props().stacksTo(16)));

    public static final DeferredItem<ModItemWithTooltip> RAW_SNOW_MIND = item("raw_snow_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.raw_mind", props().stacksTo(16)));

    public static final DeferredItem<ModItemWithTooltip> TORN_IRON_MIND = item("torn_iron_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.torn_mind", props().stacksTo(64)));

    public static final DeferredItem<ModItemWithTooltip> TORN_COPPER_MIND = item("torn_copper_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.torn_mind", props().stacksTo(64)));

    public static final DeferredItem<ModItemWithTooltip> TORN_SNOW_MIND = item("torn_snow_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.torn_mind", props().stacksTo(64)));

    /** A fluid marker for ghost slots; never a real item (see FluidGhostItem). */
    public static final DeferredItem<FluidGhostItem> FLUID_GHOST = item("fluid_ghost",
            () -> new FluidGhostItem(props()));

    // Occult Catalysts & Secret Wither Drop
    public static final DeferredItem<ModItemWithTooltip> IMPLODED_WITHER_PLASMA = item("imploded_wither_plasma",
            () -> new ModItemWithTooltip("tooltip.golem_logix.imploded_wither_plasma", props().stacksTo(16)));

    public static final DeferredItem<ModItemWithTooltip> INTERDIMENSIONAL_OCCULT_MATRIX = item("interdimensional_occult_matrix",
            () -> new ModItemWithTooltip("tooltip.golem_logix.interdimensional_occult_matrix", props().stacksTo(16)));

    // Guide Book / Occult Grimoire
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = item("guide_book",
            () -> new GuideBookItem(props().stacksTo(1)));

    // Logistics & Autocrafting Items
    public static final DeferredItem<CopperPunchCardItem> COPPER_PUNCH_CARD = item("copper_punch_card",
            () -> new CopperPunchCardItem(props()));

    /** Pipe Plug: closes a side of a pipe so neighbouring lines do not merge. */
    public static final DeferredItem<PipePlugItem> PIPE_PLUG = item("pipe_plug",
            () -> new PipePlugItem(props().stacksTo(64)));

    /** Forge Hammer: forges plates on the anvil (an unassembled Saturator); charged with Source from a jar. */
    public static final DeferredItem<com.golemlogix.items.ForgeHammerItem> FORGE_HAMMER = item("forge_hammer",
            () -> new com.golemlogix.items.ForgeHammerItem(props()));

    public static final DeferredItem<PortableChronometerItem> PORTABLE_CHRONOMETER = item("portable_chronometer",
            () -> new PortableChronometerItem(props().stacksTo(1)));

    // Cryo-Source Telegraph cards: the link card and the two range cards (the same tiers as the Chronometer)
    public static final DeferredItem<com.golemlogix.items.TelegraphLinkCardItem> AETHERIAL_LINKAGE = item("aetherial_linkage",
            () -> new com.golemlogix.items.TelegraphLinkCardItem(props().stacksTo(16)));

    public static final DeferredItem<com.golemlogix.items.TelegraphRangeCardItem> AETHER_RESONANCE_CORE = item("aether_resonance_core",
            () -> new com.golemlogix.items.TelegraphRangeCardItem(1, props().stacksTo(16)));

    public static final DeferredItem<com.golemlogix.items.TelegraphRangeCardItem> VOID_GLASS_PRISM = item("void_glass_prism",
            () -> new com.golemlogix.items.TelegraphRangeCardItem(2, props().stacksTo(16)));

    // Machine upgrades
    public static final DeferredItem<UpgradeItem> COMPRESSED_COPROCESSOR = item("compressed_coprocessor",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.SPEED, "tooltip.golem_logix.compressed_coprocessor",
                    props().stacksTo(16)));
    /** The Co-Processor on Create's assembly line (the transitional item of its sequenced assembly recipe). */
    public static final DeferredItem<com.golemlogix.items.IncompleteAssemblyItem> INCOMPLETE_COMPRESSED_COPROCESSOR =
            item("incomplete_compressed_coprocessor", () -> new com.golemlogix.items.IncompleteAssemblyItem(props()));

    public static final DeferredItem<UpgradeItem> AUTOCRAFT_MODULE = item("autocraft_module",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.CRAFTING, "tooltip.golem_logix.autocraft_module",
                    props().stacksTo(16)));

    public static final DeferredItem<UpgradeItem> REDSTONE_MODULE = item("redstone_module",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.REDSTONE, "tooltip.golem_logix.redstone_module",
                    props().stacksTo(16)));
    /** Fuzzy Card: the filters of a bus or an interface match fuzzily (any components, or a tag chosen on the filter). */
    public static final DeferredItem<UpgradeItem> FUZZY_CARD = item("fuzzy_card",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.FUZZY, "tooltip.golem_logix.fuzzy_card",
                    props().stacksTo(16)));

    // Magnetic Copper Storage Disks (Raw Item Count - RS style)
    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_1K = item("copper_disk_1k",
            () -> new MagneticDiskItem(1000, false, props()));

    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_4K = item("copper_disk_4k",
            () -> new MagneticDiskItem(4000, false, props()));

    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_16K = item("copper_disk_16k",
            () -> new MagneticDiskItem(16000, false, props()));

    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_64K = item("copper_disk_64k",
            () -> new MagneticDiskItem(64000, false, props()));

    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_256K = item("copper_disk_256k",
            () -> new MagneticDiskItem(256000, false, props()));

    // Cryogenic Fluid Disks
    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_1K = item("fluid_disk_1k",
            () -> new MagneticDiskItem(1000, true, props()));

    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_4K = item("fluid_disk_4k",
            () -> new MagneticDiskItem(4000, true, props()));

    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_16K = item("fluid_disk_16k",
            () -> new MagneticDiskItem(16000, true, props()));

    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_64K = item("fluid_disk_64k",
            () -> new MagneticDiskItem(64000, true, props()));

    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_256K = item("fluid_disk_256k",
            () -> new MagneticDiskItem(256000, true, props()));

    /** The item being built right now: since 1.21.2 its properties must carry its id. */
    private static net.minecraft.resources.Identifier building;

    /** Properties of the item being registered, with its id set. */
    private static Item.Properties props() {
        return new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ITEM, building));
    }

    private static <T extends Item> DeferredItem<T> item(String name, java.util.function.Supplier<T> supplier) {
        return ITEMS.register(name, id -> {
            building = id;
            try {
                return supplier.get();
            } finally {
                building = null;
            }
        });
    }
}
