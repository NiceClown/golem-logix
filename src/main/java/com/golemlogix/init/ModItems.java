package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.items.*;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GolemLogixMod.MOD_ID);

    // Golem Crushed Plates (Early Game)
    public static final DeferredItem<ModItemWithTooltip> FLATTENED_IRON_CORE = ITEMS.register("flattened_iron_core",
            () -> new ModItemWithTooltip("tooltip.golem_logix.flattened_iron_core", new Item.Properties().stacksTo(64)));

    public static final DeferredItem<ModItemWithTooltip> FLATTENED_COPPER_MATRIX = ITEMS.register("flattened_copper_matrix",
            () -> new ModItemWithTooltip("tooltip.golem_logix.flattened_copper_matrix", new Item.Properties().stacksTo(64)));

    public static final DeferredItem<ModItemWithTooltip> FLATTENED_CRYO_SUBSTRATE = ITEMS.register("flattened_cryo_substrate",
            () -> new ModItemWithTooltip("tooltip.golem_logix.flattened_cryo_substrate", new Item.Properties().stacksTo(64)));

    // Golem minds: Dispel on a battered golem (Ars Nouveau) gives a raw mind, the Create mixer tears a block's mind
    public static final DeferredItem<ModItemWithTooltip> RAW_IRON_MIND = ITEMS.register("raw_iron_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.raw_mind", new Item.Properties().stacksTo(16)));

    public static final DeferredItem<ModItemWithTooltip> RAW_COPPER_MIND = ITEMS.register("raw_copper_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.raw_mind", new Item.Properties().stacksTo(16)));

    public static final DeferredItem<ModItemWithTooltip> RAW_SNOW_MIND = ITEMS.register("raw_snow_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.raw_mind", new Item.Properties().stacksTo(16)));

    public static final DeferredItem<ModItemWithTooltip> TORN_IRON_MIND = ITEMS.register("torn_iron_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.torn_mind", new Item.Properties().stacksTo(64)));

    public static final DeferredItem<ModItemWithTooltip> TORN_COPPER_MIND = ITEMS.register("torn_copper_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.torn_mind", new Item.Properties().stacksTo(64)));

    public static final DeferredItem<ModItemWithTooltip> TORN_SNOW_MIND = ITEMS.register("torn_snow_mind",
            () -> new ModItemWithTooltip("tooltip.golem_logix.torn_mind", new Item.Properties().stacksTo(64)));

    /** A fluid marker for ghost slots; never a real item (see FluidGhostItem). */
    public static final DeferredItem<FluidGhostItem> FLUID_GHOST = ITEMS.register("fluid_ghost",
            () -> new FluidGhostItem(new Item.Properties()));

    // Occult Catalysts & Secret Wither Drop
    public static final DeferredItem<ModItemWithTooltip> IMPLODED_WITHER_PLASMA = ITEMS.register("imploded_wither_plasma",
            () -> new ModItemWithTooltip("tooltip.golem_logix.imploded_wither_plasma", new Item.Properties().stacksTo(16)));

    public static final DeferredItem<ModItemWithTooltip> INTERDIMENSIONAL_OCCULT_MATRIX = ITEMS.register("interdimensional_occult_matrix",
            () -> new ModItemWithTooltip("tooltip.golem_logix.interdimensional_occult_matrix", new Item.Properties().stacksTo(16)));

    // Guide Book / Occult Grimoire
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = ITEMS.register("guide_book",
            () -> new GuideBookItem(new Item.Properties().stacksTo(1)));

    // Logistics & Autocrafting Items
    public static final DeferredItem<CopperPunchCardItem> COPPER_PUNCH_CARD = ITEMS.register("copper_punch_card",
            () -> new CopperPunchCardItem(new Item.Properties()));

    /** Pipe Plug: closes a side of a pipe so neighbouring lines do not merge. */
    public static final DeferredItem<PipePlugItem> PIPE_PLUG = ITEMS.register("pipe_plug",
            () -> new PipePlugItem(new Item.Properties().stacksTo(64)));

    /** Forge Hammer: forges plates on the anvil (an unassembled Saturator); charged with Source from a jar. */
    public static final DeferredItem<com.golemlogix.items.ForgeHammerItem> FORGE_HAMMER = ITEMS.register("forge_hammer",
            () -> new com.golemlogix.items.ForgeHammerItem(new Item.Properties()));

    public static final DeferredItem<PortableChronometerItem> PORTABLE_CHRONOMETER = ITEMS.register("portable_chronometer",
            () -> new PortableChronometerItem(new Item.Properties().stacksTo(1)));

    // Cryo-Source Telegraph cards: the link card and the two range cards (the same tiers as the Chronometer)
    public static final DeferredItem<com.golemlogix.items.TelegraphLinkCardItem> AETHERIAL_LINKAGE = ITEMS.register("aetherial_linkage",
            () -> new com.golemlogix.items.TelegraphLinkCardItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<com.golemlogix.items.TelegraphRangeCardItem> AETHER_RESONANCE_CORE = ITEMS.register("aether_resonance_core",
            () -> new com.golemlogix.items.TelegraphRangeCardItem(1, new Item.Properties().stacksTo(16)));

    public static final DeferredItem<com.golemlogix.items.TelegraphRangeCardItem> VOID_GLASS_PRISM = ITEMS.register("void_glass_prism",
            () -> new com.golemlogix.items.TelegraphRangeCardItem(2, new Item.Properties().stacksTo(16)));

    // Machine upgrades
    public static final DeferredItem<UpgradeItem> COMPRESSED_COPROCESSOR = ITEMS.register("compressed_coprocessor",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.SPEED, "tooltip.golem_logix.compressed_coprocessor",
                    new Item.Properties().stacksTo(16)));
    /** The Co-Processor on Create's assembly line (the transitional item of its sequenced assembly recipe). */
    public static final DeferredItem<com.golemlogix.items.IncompleteAssemblyItem> INCOMPLETE_COMPRESSED_COPROCESSOR =
            ITEMS.register("incomplete_compressed_coprocessor", () -> new com.golemlogix.items.IncompleteAssemblyItem(new Item.Properties()));

    public static final DeferredItem<UpgradeItem> AUTOCRAFT_MODULE = ITEMS.register("autocraft_module",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.CRAFTING, "tooltip.golem_logix.autocraft_module",
                    new Item.Properties().stacksTo(16)));

    public static final DeferredItem<UpgradeItem> REDSTONE_MODULE = ITEMS.register("redstone_module",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.REDSTONE, "tooltip.golem_logix.redstone_module",
                    new Item.Properties().stacksTo(16)));
    /** Fuzzy Card: the filters of a bus or an interface match fuzzily (any components, or a tag chosen on the filter). */
    public static final DeferredItem<UpgradeItem> FUZZY_CARD = ITEMS.register("fuzzy_card",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.FUZZY, "tooltip.golem_logix.fuzzy_card",
                    new Item.Properties().stacksTo(16)));

    // Magnetic Copper Storage Disks (Raw Item Count - RS style)
    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_1K = ITEMS.register("copper_disk_1k",
            () -> new MagneticDiskItem(1000, false, new Item.Properties()));

    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_4K = ITEMS.register("copper_disk_4k",
            () -> new MagneticDiskItem(4000, false, new Item.Properties()));

    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_16K = ITEMS.register("copper_disk_16k",
            () -> new MagneticDiskItem(16000, false, new Item.Properties()));

    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_64K = ITEMS.register("copper_disk_64k",
            () -> new MagneticDiskItem(64000, false, new Item.Properties()));

    public static final DeferredItem<MagneticDiskItem> COPPER_DISK_256K = ITEMS.register("copper_disk_256k",
            () -> new MagneticDiskItem(256000, false, new Item.Properties()));

    // Cryogenic Fluid Disks
    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_1K = ITEMS.register("fluid_disk_1k",
            () -> new MagneticDiskItem(1000, true, new Item.Properties()));

    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_4K = ITEMS.register("fluid_disk_4k",
            () -> new MagneticDiskItem(4000, true, new Item.Properties()));

    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_16K = ITEMS.register("fluid_disk_16k",
            () -> new MagneticDiskItem(16000, true, new Item.Properties()));

    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_64K = ITEMS.register("fluid_disk_64k",
            () -> new MagneticDiskItem(64000, true, new Item.Properties()));

    public static final DeferredItem<MagneticDiskItem> FLUID_DISK_256K = ITEMS.register("fluid_disk_256k",
            () -> new MagneticDiskItem(256000, true, new Item.Properties()));
}
