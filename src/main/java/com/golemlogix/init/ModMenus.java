package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, GolemLogixMod.MOD_ID);

    public static final Supplier<MenuType<DriveBayMenu>> DRIVE_BAY =
            MENUS.register("drive_bay", () -> IMenuTypeExtension.create(DriveBayMenu::fromNetwork));

    public static final Supplier<MenuType<TerminalMenu>> TERMINAL =
            MENUS.register("terminal", () -> IMenuTypeExtension.create(TerminalMenu::fromNetwork));

    public static final Supplier<MenuType<BusMenu>> BUS =
            MENUS.register("bus", () -> IMenuTypeExtension.create(BusMenu::fromNetwork));

    public static final Supplier<MenuType<PatternEncoderMenu>> PATTERN_ENCODER =
            MENUS.register("pattern_encoder", () -> IMenuTypeExtension.create(PatternEncoderMenu::fromNetwork));

    public static final Supplier<MenuType<PatternProviderMenu>> PATTERN_PROVIDER =
            MENUS.register("pattern_provider", () -> IMenuTypeExtension.create(PatternProviderMenu::fromNetwork));

    public static final Supplier<MenuType<CalculatorMenu>> CALCULATOR =
            MENUS.register("calculator", () -> IMenuTypeExtension.create(CalculatorMenu::fromNetwork));

    public static final Supplier<MenuType<InterfaceMenu>> INTERFACE =
            MENUS.register("golem_interface", () -> IMenuTypeExtension.create(InterfaceMenu::fromNetwork));

    public static final Supplier<MenuType<MainframeMenu>> MAINFRAME =
            MENUS.register("cryo_mainframe", () -> IMenuTypeExtension.create(MainframeMenu::fromNetwork));

    public static final Supplier<MenuType<com.golemlogix.menu.DistillationTowerMenu>> DISTILLATION_TOWER =
            MENUS.register("cryo_distillation_tower", () -> IMenuTypeExtension.create(com.golemlogix.menu.DistillationTowerMenu::fromNetwork));

    public static final Supplier<MenuType<com.golemlogix.menu.CryoMixerMenu>> CRYO_MIXER =
            MENUS.register("cryo_mixer", () -> IMenuTypeExtension.create(com.golemlogix.menu.CryoMixerMenu::fromNetwork));

    public static final Supplier<MenuType<CardCatalogMenu>> CARD_CATALOG =
            MENUS.register("punch_card_catalog", () -> IMenuTypeExtension.create(CardCatalogMenu::fromNetwork));

    public static final Supplier<MenuType<ConcentratorMenu>> CONCENTRATOR =
            MENUS.register("schematic_concentrator", () -> IMenuTypeExtension.create(ConcentratorMenu::fromNetwork));

    public static final Supplier<MenuType<TelegraphMenu>> TELEGRAPH =
            MENUS.register("cryo_source_telegraph", () -> IMenuTypeExtension.create(TelegraphMenu::fromNetwork));

    public static final Supplier<MenuType<RecipeMonitorMenu>> RECIPE_MONITOR =
            MENUS.register("recipe_monitor", () -> IMenuTypeExtension.create(RecipeMonitorMenu::fromNetwork));
}
