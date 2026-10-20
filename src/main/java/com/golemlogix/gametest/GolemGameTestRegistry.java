package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Consumer;

/**
 * The game tests of GolemGameTests, registered the 1.21.5+ way: every test method is a test function in the registry, and a
 * test instance runs it in the empty structure with its time limit. Generated from the old @GameTest annotations; loaded only when
 * game tests are enabled (see GolemLogixMod), as this package is not in the release jar.
 */
public final class GolemGameTestRegistry {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, GolemLogixMod.MOD_ID);
    /** Test name, function, time limit (ticks), setup ticks. */
    /** A test method (some declare checked exceptions). */
    private interface Body {
        void run(GameTestHelper helper) throws Exception;
    }

    private record Test(String name, Body body, int maxTicks, int setupTicks) {
        Consumer<GameTestHelper> function() {
            return helper -> {
                try {
                    body.run(helper);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    private static final List<Test> TESTS = List.of(
            new Test("arraystructure", GolemGameTests::arrayStructure, 100, 0),
            new Test("arraycapacity", GolemGameTests::arrayCapacity, 100, 0),
            new Test("baretowerstart", GolemGameTests::bareTowerStart, 100, 0),
            new Test("lonecorelinksdown", GolemGameTests::loneCoreLinksDown, 100, 0),
            new Test("storageneedschannel", GolemGameTests::storageNeedsChannel, 100, 0),
            new Test("pipesandbusesaresteps", GolemGameTests::pipesAndBusesAreSteps, 40, 0),
            new Test("onlydeviceswithchannellookactive", GolemGameTests::onlyDevicesWithChannelLookActive, 100, 0),
            new Test("pipeplugseparateslines", GolemGameTests::pipePlugSeparatesLines, 100, 0),
            new Test("expanderfaceisanexit", GolemGameTests::expanderFaceIsAnExit, 100, 0),
            new Test("conduitsstayinside", GolemGameTests::conduitsStayInside, 100, 0),
            new Test("atlantplacesasdoubleblock", GolemGameTests::atlantPlacesAsDoubleBlock, 100, 0),
            new Test("storageroundtrip", GolemGameTests::storageRoundTrip, 200, 0),
            new Test("linelimit", GolemGameTests::lineLimit, 200, 0),
            new Test("pipelimit", GolemGameTests::pipeLimit, 200, 0),
            new Test("devicescarrylikebasicpipes", GolemGameTests::devicesCarryLikeBasicPipes, 200, 0),
            new Test("notransitthroughthearray", GolemGameTests::noTransitThroughTheArray, 200, 0),
            new Test("denserunscomefirst", GolemGameTests::denseRunsComeFirst, 200, 0),
            new Test("onecorerunsthenetwork", GolemGameTests::oneCoreRunsTheNetwork, 200, 0),
            new Test("peripheralsfollowchanges", GolemGameTests::peripheralsFollowChanges, 200, 0),
            new Test("telegraphlinksanoutpost", GolemGameTests::telegraphLinksAnOutpost, 200, 0),
            new Test("telegraphwritesastack", GolemGameTests::telegraphWritesAStack, 100, 0),
            new Test("telegraphreachesanotherdimension", GolemGameTests::telegraphReachesAnotherDimension, 200, 0),
            new Test("networkfollowschanges", GolemGameTests::networkFollowsChanges, 200, 0),
            new Test("busesmoveitems", GolemGameTests::busesMoveItems, 300, 0),
            new Test("copieddiskcountsonce", GolemGameTests::copiedDiskCountsOnce, 60, 0),
            new Test("bussleepsandwakes", GolemGameTests::busSleepsAndWakes, 200, 0),
            new Test("coresendscargosamples", GolemGameTests::coreSendsCargoSamples, 100, 0),
            new Test("interfacekeepsstockandaccepts", GolemGameTests::interfaceKeepsStockAndAccepts, 300, 0),
            new Test("interfacemenuamounts", GolemGameTests::interfaceMenuAmounts, 300, 0),
            new Test("provideroutputside", GolemGameTests::providerOutputSide, 60, 0),
            new Test("drivebaydoorfollowswindow", GolemGameTests::driveBayDoorFollowsWindow, 60, 0),
            new Test("mainframeassemblesandcrafts", GolemGameTests::mainframeAssemblesAndCrafts, 400, 0),
            new Test("catalogstorescraftingcards", GolemGameTests::catalogStoresCraftingCards, 300, 0),
            new Test("concentratorgroupsproviders", GolemGameTests::concentratorGroupsProviders, 100, 0),
            new Test("interfacecraftsmissingstock", GolemGameTests::interfaceCraftsMissingStock, 400, 0),
            new Test("autocrafttree", GolemGameTests::autocraftTree, 400, 0),
            new Test("calculatorforemanafterquickjob", GolemGameTests::calculatorForemanAfterQuickJob, 200, 0),
            new Test("fuzzycraftingtakessubstitutes", GolemGameTests::fuzzyCraftingTakesSubstitutes, 300, 0),
            new Test("fuzzyprocessingbytag", GolemGameTests::fuzzyProcessingByTag, 300, 0),
            new Test("fuzzycardonbuses", GolemGameTests::fuzzyCardOnBuses, 300, 0),
            new Test("fuzzycardoninterface", GolemGameTests::fuzzyCardOnInterface, 300, 0),
            new Test("autocraftprocessing", GolemGameTests::autocraftProcessing, 400, 0),
            new Test("networkneedssource", GolemGameTests::networkNeedsSource, 100, 0),
            new Test("formationorientsatlants", GolemGameTests::formationOrientsAtlants, 100, 0),
            new Test("fluiddisks", GolemGameTests::fluidDisks, 200, 0),
            new Test("interfaceacceptsfluids", GolemGameTests::interfaceAcceptsFluids, 200, 0),
            new Test("interfacestocksfluidperslot", GolemGameTests::interfaceStocksFluidPerSlot, 300, 0),
            new Test("interfacemoddedfluids", GolemGameTests::interfaceModdedFluids, 300, 0),
            new Test("diskstoreroundtrip", GolemGameTests::diskStoreRoundTrip, 100, 0),
            new Test("piperunnermovescontinuously", GolemGameTests::pipeRunnerMovesContinuously, 20, 0),
            new Test("craftplannerrows", GolemGameTests::craftPlannerRows, 40, 0),
            new Test("saturatoranvilforgesbyhand", GolemGameTests::saturatorAnvilForgesByHand, 120, 0),
            new Test("chronometerslotislocked", GolemGameTests::chronometerSlotIsLocked, 40, 0),
            new Test("terminalmenuactions", GolemGameTests::terminalMenuActions, 200, 0),
            new Test("craftingterminalgrid", GolemGameTests::craftingTerminalGrid, 200, 0),
            new Test("patternencoder", GolemGameTests::patternEncoder, 100, 0),
            new Test("jobpersistence", GolemGameTests::jobPersistence, 200, 0),
            new Test("dataiscomplete", GolemGameTests::dataIsComplete, 100, 0),
            new Test("chronometerupgrade", GolemGameTests::chronometerUpgrade, 100, 0),
            new Test("bookdataisvalid", GolemGameTests::bookDataIsValid, 100, 0),
            new Test("bayhastenslots", GolemGameTests::bayHasTenSlots, 100, 0),
            new Test("cabinetistwobytwo", GolemGameTests::cabinetIsTwoByTwo, 100, 0),
            new Test("cabinetbreaksasone", GolemGameTests::cabinetBreaksAsOne, 200, 0),
            new Test("cabinetplacementbyplayer", GolemGameTests::cabinetPlacementByPlayer, 100, 0),
            new Test("cabinetcountsonce", GolemGameTests::cabinetCountsOnce, 200, 0),
            new Test("busfilterkeepsslots", GolemGameTests::busFilterKeepsSlots, 100, 0),
            new Test("everyblockisharvestable", GolemGameTests::everyBlockIsHarvestable, 100, 0),
            new Test("pipelinksbykind", GolemGameTests::pipeLinksByKind, 100, 0),
            new Test("baykeepsslotpositions", GolemGameTests::bayKeepsSlotPositions, 100, 0),
            new Test("bushingreportsload", GolemGameTests::bushingReportsLoad, 200, 0),
            new Test("saturatorfillsfromnetwork", GolemGameTests::saturatorFillsFromNetwork, 300, 0),
            new Test("saturatorformsandrestores", GolemGameTests::saturatorFormsAndRestores, 200, 0),
            new Test("saturatorcrafts", GolemGameTests::saturatorCrafts, 420, 0),
            new Test("arssourcejarpowersnetwork", GolemGameTests::arsSourceJarPowersNetwork, 200, 0),
            new Test("saturatormakessourcegems", GolemGameTests::saturatorMakesSourceGems, 400, 0),
            new Test("saturatorautomation", GolemGameTests::saturatorAutomation, 440, 0),
            new Test("anvilritualirongolem", GolemGameTests::anvilRitualIronGolem, 100, 0),
            new Test("anvilritualsnowgolem", GolemGameTests::anvilRitualSnowGolem, 100, 0),
            new Test("anvilritualcoppergolem", GolemGameTests::anvilRitualCopperGolem, 100, 0),
            new Test("ritualscomefromdata", GolemGameTests::ritualsComeFromData, 20, 0),
            new Test("chronometerfoundanywhere", GolemGameTests::chronometerFoundAnywhere, 20, 0),
            new Test("anvilritualneedswoundedgolem", GolemGameTests::anvilRitualNeedsWoundedGolem, 100, 0),
            new Test("cryosourcefromnetwork", GolemGameTests::cryoSourceFromNetwork, 100, 0),
            new Test("fluidpatterntotals", GolemGameTests::fluidPatternTotals, 20, 0),
            new Test("exportbuspumpscryosource", GolemGameTests::exportBusPumpsCryoSource, 200, 0),
            new Test("interfacestockscryosource", GolemGameTests::interfaceStocksCryoSource, 200, 0),
            new Test("autocraftwithfluid", GolemGameTests::autocraftWithFluid, 400, 0),
            new Test("dispelunbindsgolemmind", GolemGameTests::dispelUnbindsGolemMind, 60, 0),
            new Test("arsdispelevent", GolemGameTests::arsDispelEvent, 40, 0),
            new Test("periscopeseestanks", GolemGameTests::periscopeSeesTanks, 200, 0),
            new Test("storagepriority", GolemGameTests::storagePriority, 300, 0),
            new Test("buspriorityrank", GolemGameTests::busPriorityRank, 200, 0),
            new Test("autocraftthroughsaturator", GolemGameTests::autocraftThroughSaturator, 700, 0),
            new Test("autocraftmachinethencrafting", GolemGameTests::autocraftMachineThenCrafting, 600, 0),
            new Test("autocraftwithfullstorage", GolemGameTests::autocraftWithFullStorage, 500, 0),
            new Test("blueprintmatchesarray", GolemGameTests::blueprintMatchesArray, 100, 0),
            new Test("saturatorblueprint", GolemGameTests::saturatorBlueprint, 100, 0),
            new Test("busskipsitsowncontainer", GolemGameTests::busSkipsItsOwnContainer, 300, 0),
            new Test("busupgradeslots", GolemGameTests::busUpgradeSlots, 100, 0),
            new Test("calculatorcompressedcoprocessors", GolemGameTests::calculatorCompressedCoprocessors, 100, 0),
            new Test("redstonemodes", GolemGameTests::redstoneModes, 100, 0),
            new Test("providerwaitsforresult", GolemGameTests::providerWaitsForResult, 500, 0),
            new Test("precraftseveraltargets", GolemGameTests::precraftSeveralTargets, 400, 0),
            new Test("saturatorcoolers", GolemGameTests::saturatorCoolers, 200, 0),
            new Test("saturatorredstonemodule", GolemGameTests::saturatorRedstoneModule, 400, 0),
            new Test("resonatoronlyfeedschronometers", GolemGameTests::resonatorOnlyFeedsChronometers, 200, 0),
            new Test("cryojarfeedsnetwork", GolemGameTests::cryoJarFeedsNetwork, 120, 0),
            new Test("distillationtowerfillsjar", GolemGameTests::distillationTowerFillsJar, 200, 0),
            new Test("cryomixersnowandice", GolemGameTests::cryoMixerSnowAndIce, 300, 0),
            new Test("providerreturnslots", GolemGameTests::providerReturnSlots, 100, 0),
            new Test("mixerautooutput", GolemGameTests::mixerAutoOutput, 200, 0),
            new Test("saturatorautooutput", GolemGameTests::saturatorAutoOutput, 700, 0)
    );

    private GolemGameTestRegistry() {
    }

    public static void register(IEventBus modBus) {
        for (Test t : TESTS) {
            FUNCTIONS.register(t.name(), () -> t.function());
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(GolemGameTestRegistry::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test t : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id(t.name()));
            event.registerTest(id(t.name()), new FunctionGameTestInstance(fn, new TestData<>(env, id("empty"), t.maxTicks(), t.setupTicks(), true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
    }
}
