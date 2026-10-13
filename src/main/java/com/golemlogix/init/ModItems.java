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

    /** Заглушка трубы: закрывает сторону трубы, чтобы соседние линии не сливались. */
    public static final DeferredItem<PipePlugItem> PIPE_PLUG = ITEMS.register("pipe_plug",
            () -> new PipePlugItem(new Item.Properties().stacksTo(64)));

    /** Кузнечный молот: им куют пластины на наковальне (несобранном Насыщателе); заряжается Source от банки. */
    public static final DeferredItem<com.golemlogix.items.ForgeHammerItem> FORGE_HAMMER = ITEMS.register("forge_hammer",
            () -> new com.golemlogix.items.ForgeHammerItem(new Item.Properties()));

    public static final DeferredItem<PortableChronometerItem> PORTABLE_CHRONOMETER = ITEMS.register("portable_chronometer",
            () -> new PortableChronometerItem(new Item.Properties().stacksTo(1)));

    // Machine upgrades
    public static final DeferredItem<UpgradeItem> COMPRESSED_COPROCESSOR = ITEMS.register("compressed_coprocessor",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.SPEED, "tooltip.golem_logix.compressed_coprocessor",
                    new Item.Properties().stacksTo(16)));

    public static final DeferredItem<UpgradeItem> AUTOCRAFT_MODULE = ITEMS.register("autocraft_module",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.CRAFTING, "tooltip.golem_logix.autocraft_module",
                    new Item.Properties().stacksTo(16)));

    public static final DeferredItem<UpgradeItem> REDSTONE_MODULE = ITEMS.register("redstone_module",
            () -> new UpgradeItem(com.golemlogix.upgrade.UpgradeType.REDSTONE, "tooltip.golem_logix.redstone_module",
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
