package com.golemlogix.devtest;

import com.golemlogix.GolemLogixMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Developer rig: takes shots FROM THE GAME ITSELF (the Minecraft framebuffer) without touching the desktop or the user's input.
 * Turned on by the system property golemlogix.devshots=&lt;queue folder&gt; (gradle task runDevShots), not part of the release jar.
 *
 * The client creates a flat world itself, then watches the queue folder: every *.txt is a scenario, one command per line:
 * <pre>
 *   cmd &lt;server command&gt;      cam x y z yaw pitch (eyes)   wait N (ticks)   shot name   hook name args...
 *   use x y z   closeScreen   reload   gui on|off   fov N   log text   quit
 * </pre>
 * The result goes next to it: name.log (OK/ERR for every line). Shots land in screenshots/ of the game folder.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, value = Dist.CLIENT)
public final class DevShots {
    private static final String QUEUE = System.getProperty("golemlogix.devshots");
    /**
     * Network mode (gradle -PdevshotsServer=host:port runDevShots): the client joins a dedicated server instead of its own
     * world; cmd/hook/camera go to the server as commands (/golemlogix_dev hook ...), use/useitem as real player actions.
     */
    private static final String REMOTE = blankToNull(System.getProperty("golemlogix.devshots.server"));

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private interface Step {
        /** @return true when the step is done (called once per client tick). */
        boolean run(Minecraft mc) throws Exception;
    }

    private static long tick;
    private static boolean optionsApplied;
    private static boolean worldRequested;
    private static int reconnects;
    private static long levelSince = -1;
    private static boolean ready;
    private static Path queueDir;

    private static Path scriptFile;
    private static Path logFile;
    private static List<String> lines = new ArrayList<>();
    private static List<Step> steps;
    private static int stepIndex;
    private static long stepStart;

    private static String pendingShot;
    private static boolean shotTaken;

    private DevShots() {
    }

    private static void log(String text) {
        GolemLogixMod.LOGGER.info("[DevShots] {}", text);
        if (logFile != null) {
            try {
                Files.writeString(logFile, text + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {
                // the log is optional
            }
        }
    }

    // -------------------------------------------------------------------------------------------- ticks

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (QUEUE == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        tick++;
        DevFilm.clientTick();
        if (!optionsApplied) {
            applyOptions(mc);
        }
        if (mc.level == null || mc.player == null) {
            ready = false;
            levelSince = -1;
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.setScreen(new TitleScreen());
            } else if (REMOTE != null && worldRequested && mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen
                    && reconnects < 3) {
                // network: the server dropped the join, try again
                reconnects++;
                worldRequested = false;
                log("disconnected, reconnect " + reconnects);
                mc.setScreen(new TitleScreen());
            } else if (mc.screen instanceof TitleScreen && !worldRequested && tick > 60) {
                worldRequested = true;
                if (REMOTE != null) {
                    log("connecting to " + REMOTE);
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc,
                            net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(REMOTE),
                            new net.minecraft.client.multiplayer.ServerData("DevShots", REMOTE,
                                    net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
                } else {
                    createWorld(mc);
                }
            }
            return;
        }
        if (levelSince < 0) {
            levelSince = tick;
        }
        if (!ready) {
            if (tick - levelSince > 100 && (REMOTE != null || mc.getSingleplayerServer() != null)) {
                ready = true;
                if (REMOTE != null) {
                    prepareRemote(mc);
                } else {
                    prepareWorld(mc);
                }
                log("READY");
            }
            return;
        }
        DevKeys.pollConflict();
        if (mc.screen != null && !keepScreen) {
            mc.setScreen(null);
        }
        if (steps == null) {
            pickScript();
            return;
        }
        if (stepIndex >= steps.size()) {
            finishScript();
            return;
        }
        try {
            if (steps.get(stepIndex).run(mc)) {
                stepIndex++;
                stepStart = tick;
            } else if (tick - stepStart > 20 * 180) {
                log("ERR timeout on line " + (stepIndex + 1) + ": " + lines.get(stepIndex));
                stepIndex++;
                stepStart = tick;
            }
        } catch (Throwable t) {
            log("ERR line " + (stepIndex + 1) + " (" + lines.get(stepIndex) + "): " + t);
            GolemLogixMod.LOGGER.error("[DevShots] step failed", t);
            stepIndex++;
            stepStart = tick;
        }
    }

    private static boolean keepScreen;

    @SubscribeEvent
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        if (QUEUE != null) {
            DevFilm.beforeFrame(Minecraft.getInstance(), event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (QUEUE != null) {
            DevFilm.cameraAngles(event);
        }
    }

    @SubscribeEvent
    public static void onFov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        if (QUEUE != null) {
            DevFilm.fov(event);
        }
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (QUEUE == null) {
            return;
        }
        DevFilm.afterFrame(Minecraft.getInstance());
        if (pendingShot == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String name = pendingShot;
        pendingShot = null;
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, msg -> {
        });
        shotTaken = true;
    }

    // ---------------------------------------------------------------------------------- setup

    private static void applyOptions(Minecraft mc) {
        optionsApplied = true;
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.options.hideGui = true;
        mc.options.fov().set(70);
        mc.options.renderDistance().set(10);
        queueDir = Path.of(QUEUE);
        try {
            Files.createDirectories(queueDir);
        } catch (Exception e) {
            GolemLogixMod.LOGGER.error("[DevShots] cannot create queue dir", e);
        }
        GolemLogixMod.LOGGER.info("[DevShots] active, queue = {}", queueDir);
    }

    private static void createWorld(Minecraft mc) {
        log("creating world");
        LevelSettings settings = new LevelSettings("DevShots", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        // a flat world, or (golemlogix.devshots.world=normal:<seed>) a generated one to film in real landscapes
        String world = System.getProperty("golemlogix.devshots.world", "flat");
        boolean normal = world.startsWith("normal");
        long seed = normal && world.contains(":") ? Long.parseLong(world.substring(world.indexOf(':') + 1)) : 20261009L;
        WorldOptions options = new WorldOptions(seed, false, false);
        String id = "devshots_" + System.currentTimeMillis();
        var preset = normal ? WorldPresets.NORMAL : WorldPresets.FLAT;
        mc.createWorldOpenFlows().createFreshLevel(id, settings, options,
                access -> access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).value().createWorldDimensions(),
                mc.screen);
    }

    private static void prepareWorld(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            // noon, clear and still: by commands (since 26.1 time and weather have their own clock and data)
            var quiet = server.createCommandSourceStack().withSuppressedOutput();
            for (String c : List.of("gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
                    "time set 6000", "weather clear")) {
                server.getCommands().performPrefixedCommand(quiet, c);
            }
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
    }

    /** Network mode: the same world settings, but as commands to the server (the player is an operator), flight on the client. */
    private static void prepareRemote(Minecraft mc) {
        for (String c : new String[]{"gamemode creative", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                "gamerule doMobSpawning false", "time set 6000", "weather clear"}) {
            mc.player.connection.sendCommand(c);
        }
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
    }

    /** Server replies to /golemlogix_dev come to chat marked "[dev]" and go into the scenario log. */
    @SubscribeEvent
    public static void onChat(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event) {
        if (QUEUE == null) {
            return;
        }
        String text = event.getMessage().getString();
        if (text.startsWith("[dev] ")) {
            log(text.substring(6));
            event.setCanceled(true);
        }
    }

    /** Network step: a command to the server (without "/"), then a couple of ticks for the reply. */
    private static Step remoteCommand(String command) {
        boolean[] sent = {false};
        return mc -> {
            if (!sent[0]) {
                sent[0] = true;
                mc.player.connection.sendCommand(command);
                return false;
            }
            return tick - stepStart >= 4;
        };
    }

    /** Network camera: a teleport by command (eyes at height y), flight is enabled on the client. */
    private static Step remoteTeleport(double x, double y, double z, float yaw, float pitch) {
        return remoteCommand(String.format(Locale.ROOT, "tp @s %.4f %.4f %.4f %.3f %.3f", x, y - 1.62, z, yaw, pitch));
    }

    // ------------------------------------------------------------------------------------ scenarios

    private static void pickScript() {
        if (tick % 10 != 0) {
            return;
        }
        try (Stream<Path> list = Files.list(queueDir)) {
            Path next = list.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().findFirst().orElse(null);
            if (next == null) {
                return;
            }
            scriptFile = next;
            String base = next.getFileName().toString();
            logFile = queueDir.resolve(base.substring(0, base.length() - 4) + ".log");
            Files.deleteIfExists(logFile);
            lines = new ArrayList<>();
            steps = new ArrayList<>();
            for (String raw : Files.readAllLines(next, StandardCharsets.UTF_8)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                lines.add(line);
                try {
                    steps.add(parse(line));
                } catch (Exception bad) {
                    String why = bad.toString();
                    steps.add(mc -> {
                        log("ERR cannot parse '" + line + "': " + why);
                        return true;
                    });
                }
            }
            stepIndex = 0;
            stepStart = tick;
            log("BEGIN " + base);
        } catch (Exception e) {
            GolemLogixMod.LOGGER.error("[DevShots] cannot read script", e);
        }
    }

    private static void finishScript() {
        log("END");
        try {
            Path done = scriptFile.resolveSibling(scriptFile.getFileName().toString().replaceAll("\\.txt$", ".done"));
            Files.move(scriptFile, done, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            GolemLogixMod.LOGGER.error("[DevShots] cannot finish script", e);
        }
        steps = null;
        scriptFile = null;
        logFile = null;
    }

    private static Step waitTicks(int n) {
        long[] start = {-1};
        return mc -> {
            if (start[0] < 0) {
                start[0] = tick;
            }
            return tick - start[0] >= n;
        };
    }

    /** Run a task on the server thread; the step finishes when it is done (and passes on an error). */
    private static Step onServer(ServerTask task) {
        CompletableFuture<Void>[] future = new CompletableFuture[1];
        return mc -> {
            if (future[0] == null) {
                MinecraftServer server = mc.getSingleplayerServer();
                CompletableFuture<Void> f = new CompletableFuture<>();
                future[0] = f;
                server.execute(() -> {
                    try {
                        task.run(server, server.overworld(), server.getPlayerList().getPlayers().get(0));
                        f.complete(null);
                    } catch (Throwable t) {
                        f.completeExceptionally(t);
                    }
                });
                return false;
            }
            if (!future[0].isDone()) {
                return false;
            }
            future[0].join();
            return tick - stepStart >= 2;
        };
    }

    private interface ServerTask {
        void run(MinecraftServer server, ServerLevel level, ServerPlayer player) throws Exception;
    }

    private static Step parse(String line) {
        String[] t = line.split("\\s+");
        String op = t[0].toLowerCase(Locale.ROOT);
        String rest = line.length() > op.length() ? line.substring(op.length()).strip() : "";
        switch (op) {
            case "wait":
                return waitTicks(Integer.parseInt(t[1]));
            case "log":
                return mc -> {
                    log(rest);
                    return true;
                };
            case "cmd":
                if (REMOTE != null) {
                    return remoteCommand(rest.startsWith("/") ? rest.substring(1) : rest);
                }
                return onServer((server, level, player) -> {
                    var source = player.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER).withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(source, rest);
                });
            case "cam": {
                double x = Double.parseDouble(t[1]), y = Double.parseDouble(t[2]), z = Double.parseDouble(t[3]);
                float yaw = Float.parseFloat(t[4]), pitch = Float.parseFloat(t[5]);
                if (REMOTE != null) {
                    return remoteTeleport(x, y, z, yaw, pitch);
                }
                return onServer((server, level, player) -> {
                    player.getAbilities().flying = true;
                    player.setNoGravity(true);
                    player.teleportTo(level, x, y - 1.62, z, java.util.Set.of(), yaw, pitch, true);
                });
            }
            case "look": {
                // look tx ty tz dist yaw pitch: the camera at distance dist from the point, aimed at it
                double tx = Double.parseDouble(t[1]), ty = Double.parseDouble(t[2]), tz = Double.parseDouble(t[3]);
                double dist = Double.parseDouble(t[4]);
                float yaw = Float.parseFloat(t[5]), pitch = Float.parseFloat(t[6]);
                double fx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
                double fy = -Math.sin(Math.toRadians(pitch));
                double fz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
                double ex = tx - fx * dist, ey = ty - fy * dist, ez = tz - fz * dist;
                if (REMOTE != null) {
                    return remoteTeleport(ex, ey, ez, yaw, pitch);
                }
                return onServer((server, level, player) -> {
                    player.getAbilities().flying = true;
                    player.setNoGravity(true);
                    player.teleportTo(level, ex, ey - 1.62, ez, java.util.Set.of(), yaw, pitch, true);
                });
            }
            case "gui":
                return mc -> {
                    mc.options.hideGui = t[1].equalsIgnoreCase("off");
                    return true;
                };
            case "fov": {
                int fov = Integer.parseInt(t[1]);
                return mc -> {
                    mc.options.fov().set(fov);
                    return true;
                };
            }
            case "shot": {
                String name = t[1];
                boolean[] requested = {false};
                return mc -> {
                    if (!requested[0]) {
                        requested[0] = true;
                        shotTaken = false;
                        pendingShot = name;
                        return false;
                    }
                    if (shotTaken && tick - stepStart >= 12) {
                        log("OK shot " + name + (mc.screen != null ? " [screen " + mc.screen.getClass().getSimpleName() + "]" : ""));
                        return true;
                    }
                    return false;
                };
            }
            case "reload": {
                CompletableFuture<Void>[] f = new CompletableFuture[1];
                return mc -> {
                    if (f[0] == null) {
                        f[0] = mc.reloadResourcePacks();
                        return false;
                    }
                    if (!f[0].isDone()) {
                        return false;
                    }
                    f[0].join();
                    // the loading overlay (Mojang logo) does not fade at once
                    return mc.getOverlay() == null && tick - stepStart >= 30;
                };
            }
            case "book": {
                // book <entry id | -> [spread] [section]: opens the book right at the given place
                String id = t.length > 1 && !t[1].equals("-") ? t[1] : "";
                int spread = t.length > 2 ? Integer.parseInt(t[2]) : 0;
                int category = t.length > 3 ? Integer.parseInt(t[3]) : 0;
                return mc -> {
                    keepScreen = true;
                    try {
                        var f = com.golemlogix.client.book.BookScreen.class.getDeclaredField("lastState");
                        f.setAccessible(true);
                        f.set(null, new String[]{Integer.toString(category), id, Integer.toString(spread)});
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                    mc.setScreen(new com.golemlogix.client.book.BookScreen());
                    return true;
                };
            }
            case "ghost": {
                // ghost start full|tower | clear
                String mode = t[1];
                return mc -> {
                    switch (mode) {
                        case "start" -> com.golemlogix.client.ghost.GhostProjection.start(
                                t.length > 2 && t[2].equals("tower") ? com.golemlogix.system.AtlasBlueprint.Variant.TOWER
                                        : t.length > 2 && t[2].equals("saturator") ? com.golemlogix.system.AtlasBlueprint.Variant.SATURATOR
                                        : t.length > 2 && t[2].equals("mainframe") ? com.golemlogix.system.AtlasBlueprint.Variant.MAINFRAME
                                        : t.length > 2 && t[2].equals("catalog") ? com.golemlogix.system.AtlasBlueprint.Variant.CATALOG
                                        : com.golemlogix.system.AtlasBlueprint.Variant.FULL);
                        case "clear" -> com.golemlogix.client.ghost.GhostProjection.clear();
                        default -> throw new IllegalArgumentException("ghost " + mode);
                    }
                    return true;
                };
            }
            case "key": {
                // key Y|N : a press inside the game; a tick later the "foreign" binding is polled as usual
                int code = t[1].equalsIgnoreCase("Y") ? org.lwjgl.glfw.GLFW.GLFW_KEY_Y : org.lwjgl.glfw.GLFW.GLFW_KEY_N;
                return mc -> {
                    DevKeys.press(code);
                    DevKeys.release(code);
                    return true;
                };
            }
            case "check": {
                // check <name> <expected>
                String what = t[1], expected = String.join(" ", java.util.Arrays.copyOfRange(t, 2, t.length));
                return mc -> {
                    String actual = switch (what) {
                        case "ghostActive" -> String.valueOf(com.golemlogix.client.ghost.GhostProjection.isActive());
                        case "ghostLocked" -> String.valueOf(com.golemlogix.client.ghost.GhostProjection.isLocked());
                        case "conflictClicks" -> String.valueOf(DevKeys.conflictClicks);
                        case "carried" -> {
                            ItemStack c = mc.player.containerMenu.getCarried();
                            yield c.isEmpty() ? "empty" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.getItem()) + " x" + c.getCount();
                        }
                        case "inventory" -> {
                            // check inventory <item>=<count>
                            String[] kv = expected.split("=");
                            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(kv[0]));
                            int n = 0;
                            for (ItemStack s : mc.player.getInventory().getNonEquipmentItems()) {
                                if (s.is(item)) {
                                    n += s.getCount();
                                }
                            }
                            yield kv[0] + "=" + n;
                        }
                        case "conflictReset" -> {
                            DevKeys.conflictClicks = 0;
                            yield expected;
                        }
                        default -> throw new IllegalArgumentException("check " + what);
                    };
                    log((actual.equals(expected) ? "OK" : "ERR") + " check " + what + " = " + actual + " (expected " + expected + ")");
                    return true;
                };
            }
            case "uiclick": {
                // uiclick list <index> <button> : a mouse click on the terminal list inside the screen itself (no OS input)
                // index is a number or name:<part of the name> (the list row with that name, including a fluid)
                String which = t[2];
                int button = Integer.parseInt(t[3]);
                return mc -> {
                    if (!(mc.screen instanceof com.golemlogix.client.TerminalScreen screen)) {
                        throw new IllegalStateException("terminal screen is not open: " + mc.screen);
                    }
                    int idx = which.startsWith("name:") ? screen.indexOfName(which.substring(5)) : Integer.parseInt(which);
                    if (idx < 0) {
                        throw new IllegalStateException("no list row " + which);
                    }
                    double x = screen.getGuiLeft() + com.golemlogix.menu.TerminalMenu.LIST_X + (idx % com.golemlogix.menu.TerminalMenu.COLS) * 18 + 9;
                    double y = screen.getGuiTop() + com.golemlogix.menu.TerminalMenu.LIST_Y + (idx / com.golemlogix.menu.TerminalMenu.COLS) * 18 + 9;
                    var click = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(button, 0));
                    screen.mouseClicked(click, false);
                    screen.mouseReleased(click);
                    return true;
                };
            }
            case "uiscroll": {
                // uiscroll <x> <y> <steps> : mouse wheel at a window point (from the top left corner of the panel); steps > 0 scroll down
                double ux = Double.parseDouble(t[1]), uy = Double.parseDouble(t[2]);
                int steps = Integer.parseInt(t[3]);
                return mc -> {
                    if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
                        throw new IllegalStateException("no container screen: " + mc.screen);
                    }
                    for (int i = 0; i < Math.abs(steps); i++) {
                        screen.mouseScrolled(screen.getGuiLeft() + ux, screen.getGuiTop() + uy, 0, steps > 0 ? -1 : 1);
                    }
                    return true;
                };
            }
            case "uiat": {
                // uiat <x> <y> <button> : a click at a window point (from the top left corner of the panel) of the open container screen
                double ux = Double.parseDouble(t[1]), uy = Double.parseDouble(t[2]);
                int button = Integer.parseInt(t[3]);
                return mc -> {
                    if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
                        throw new IllegalStateException("no container screen: " + mc.screen);
                    }
                    double x = screen.getGuiLeft() + ux, y = screen.getGuiTop() + uy;
                    var click = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(button, 0));
                    screen.mouseClicked(click, false);
                    screen.mouseReleased(click);
                    return true;
                };
            }
            case "devshift": {
                // devshift 1|0 : punch cards in the inventory show their result, as with Shift held
                boolean on = t[1].equals("1");
                return mc -> {
                    com.golemlogix.client.PunchCardModel.devShift = on;
                    return true;
                };
            }
            case "uibutton": {
                // uibutton <part of the label> : press a mod button on the open screen
                return mc -> {
                    for (var w : mc.screen.children()) {
                        if (w instanceof com.golemlogix.client.GolemButton b && b.getMessage().getString().contains(rest)) {
                            b.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                            return true;
                        }
                    }
                    throw new IllegalStateException("no button containing \"" + rest + "\" on " + mc.screen.getClass().getSimpleName());
                };
            }
            case "slotclick": {
                // slotclick <menu slot number> <button> : like a click on a slot of the open window
                int slot = Integer.parseInt(t[1]), button = Integer.parseInt(t[2]);
                return mc -> {
                    if (mc.player == null || mc.gameMode == null || mc.player.containerMenu == null) {
                        throw new IllegalStateException("no open menu");
                    }
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, button,
                            net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                    return true;
                };
            }
            case "useheld": {
                // useheld x y z : right-click on a block with the held item, like a player (client useItemOn, a packet to the server)
                int ux = Integer.parseInt(t[1]), uy = Integer.parseInt(t[2]), uz = Integer.parseInt(t[3]);
                return mc -> {
                    BlockPos pos = new BlockPos(ux, uy, uz);
                    var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                    log("useheld " + pos.toShortString() + " -> " + result + " with " + mc.player.getMainHandItem());
                    return true;
                };
            }
            case "attack": {
                // attack x y z : left-click on a block, like a player (client startDestroyBlock, with LeftClickBlock events)
                int ax = Integer.parseInt(t[1]), ay = Integer.parseInt(t[2]), az = Integer.parseInt(t[3]);
                return mc -> {
                    BlockPos pos = new BlockPos(ax, ay, az);
                    boolean r = mc.gameMode.startDestroyBlock(pos, Direction.UP);
                    mc.gameMode.stopDestroyBlock();
                    log("attack " + pos.toShortString() + " -> " + r);
                    return true;
                };
            }
            case "planner": {
                // planner <item> <amount> : open the order window with the planner over the open terminal
                String itemId = t[1];
                int amount = Integer.parseInt(t[2]);
                return mc -> {
                    if (!(mc.screen instanceof com.golemlogix.client.TerminalScreen ts)
                            || !(mc.player.containerMenu instanceof com.golemlogix.menu.TerminalMenu menu)) {
                        throw new IllegalStateException("terminal screen is not open");
                    }
                    keepScreen = true;
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId));
                    var screen = new com.golemlogix.client.CraftRequestScreen(ts, menu, new ItemStack(item));
                    mc.setScreen(screen);
                    screen.setAmountForDev(amount);
                    return true;
                };
            }
            case "craftreq": {
                // craftreq <item> <amount> : the same as the "Order" button in the order window
                String itemId = t[1];
                int amount = Integer.parseInt(t[2]);
                return mc -> {
                    var menu = mc.player.containerMenu;
                    if (!(menu instanceof com.golemlogix.menu.TerminalMenu)) {
                        throw new IllegalStateException("terminal menu is not open");
                    }
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId));
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                            new com.golemlogix.network.CraftRequestPayload(menu.containerId, new ItemStack(item), amount, false, 0));
                    return true;
                };
            }
            case "jeitransfer": {
                // jeitransfer <recipe id> <expected: ok|COSMETIC|USER|INTERNAL> [max] [ctrl] : JEI "+" in the open window
                // (ctrl is like Ctrl + "+": order what is missing through autocrafting)
                String recipe = t[1], expected = t[2];
                java.util.List<String> flags = java.util.Arrays.asList(t).subList(3, t.length);
                boolean max = flags.contains("max");
                boolean ctrl = flags.contains("ctrl");
                return mc -> {
                    String actual = com.golemlogix.integration.jei.GolemLogixJeiPlugin.devTransfer(
                            net.minecraft.resources.Identifier.parse(recipe), max, ctrl);
                    log((actual.equals(expected) ? "OK" : "ERR") + " jeitransfer " + recipe + " = " + actual + " (expected " + expected + ")");
                    return true;
                };
            }
            case "jeifilter": {
                // jeifilter <text> : the JEI search line (for example @golem_logix); empty resets it
                return mc -> {
                    if (!net.neoforged.fml.ModList.get().isLoaded("jei") || !com.golemlogix.integration.jei.GolemLogixJeiPlugin.setFilter(rest)) {
                        throw new IllegalStateException("JEI is not ready (start with -Mods)");
                    }
                    return true;
                };
            }
            case "jei": {
                // jei <item> [uses] : the JEI recipe window for an item (needs -PtestMods)
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(t[1]));
                boolean uses = t.length > 2 && t[2].equals("uses");
                return mc -> {
                    if (!net.neoforged.fml.ModList.get().isLoaded("jei")) {
                        throw new IllegalStateException("JEI is not loaded (start with -Mods)");
                    }
                    keepScreen = true;
                    if (!com.golemlogix.integration.jei.GolemLogixJeiPlugin.showRecipes(new ItemStack(item), uses)) {
                        throw new IllegalStateException("JEI runtime is not ready");
                    }
                    return true;
                };
            }
            case "jeitype": {
                // jeitype <namespace:category> [item] : one JEI recipe category, or only its recipes making the item (needs -PtestMods)
                var uid = net.minecraft.resources.Identifier.parse(t[1]);
                ItemStack only = t.length > 2 ? new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                        net.minecraft.resources.Identifier.parse(t[2]))) : ItemStack.EMPTY;
                return mc -> {
                    keepScreen = true;
                    if (!com.golemlogix.integration.jei.GolemLogixJeiPlugin.showType(uid, only)) {
                        throw new IllegalStateException("no JEI category " + uid);
                    }
                    return true;
                };
            }
            case "chronokey":
                // chronokey: what the "open Portable Chronometer" key (Ctrl+E) sends; the rig cannot hold Ctrl down
                return mc -> {
                    keepScreen = true;
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new com.golemlogix.network.OpenChronometerPayload());
                    return true;
                };
            case "jeioverlay": {
                // jeioverlay on|off : JEI's item list beside windows (by reflection: JEI is only in the -PtestMods runs)
                boolean on = t[1].equalsIgnoreCase("on");
                return mc -> {
                    Class<?> internal = Class.forName("mezz.jei.common.Internal");
                    Class<?> toggle = Class.forName("mezz.jei.common.config.IClientToggleState");
                    Object state = internal.getMethod("getClientToggleState").invoke(null);
                    boolean now = (boolean) toggle.getMethod("isOverlayEnabled").invoke(state);
                    if (now != on) {
                        toggle.getMethod("toggleOverlayEnabled").invoke(state);
                    }
                    return true;
                };
            }
            case "notoasts":
                // notoasts : clears the toasts (recipe unlocks, "order ready", advancements) before a shot
                return mc -> {
                    mc.getToastManager().clear();
                    return true;
                };
            case "mouse": {
                // mouse x y : where screens think the cursor is (window pixels), e.g. away from buttons with tooltips;
                // only the game's own variable changes, the OS cursor is not touched
                double mx = Double.parseDouble(t[1]), my = Double.parseDouble(t[2]);
                return mc -> {
                    try {
                        var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
                        var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
                        fx.setAccessible(true);
                        fy.setAccessible(true);
                        fx.setDouble(mc.mouseHandler, mx);
                        fy.setDouble(mc.mouseHandler, my);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("cannot set the cursor position", e);
                    }
                    return true;
                };
            }
            case "chat":
                // chat : the chat window with its history (messages sent on joining)
                return mc -> {
                    keepScreen = true;
                    mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
                    return true;
                };
            case "inv":
                return mc -> {
                    keepScreen = true;
                    mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                    return true;
                };
            case "closescreen":
                return mc -> {
                    keepScreen = false;
                    mc.setScreen(null);
                    return true;
                };
            case "use": {
                int x = Integer.parseInt(t[1]), y = Integer.parseInt(t[2]), z = Integer.parseInt(t[3]);
                if (REMOTE != null) {
                    // over the network it is a real player click: a packet to the server, the server's reply opens the window
                    return mc -> {
                        keepScreen = true;
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
                        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                        log("use (remote) " + pos.toShortString() + " -> " + result);
                        return true;
                    };
                }
                Step inner = onServer((server, level, player) -> {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
                    var result = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
                    log("use " + pos.toShortString() + " -> " + result + " block=" + level.getBlockState(pos).getBlock().getDescriptionId()
                            + " container=" + (player.containerMenu == null ? "null" : player.containerMenu.getClass().getSimpleName()));
                });
                return mc -> {
                    keepScreen = true;
                    return inner.run(mc);
                };
            }
            case "burst": {
                // burst name frames [every N ticks] [orbit tx ty tz dist pitch yaw0 yawStep] : a series of shots name_000.. for a GIF;
                // with orbit the camera flies around the point before every shot
                String name = t[1];
                int count = Integer.parseInt(t[2]);
                int every = t.length > 3 ? Integer.parseInt(t[3]) : 2;
                boolean orbit = t.length > 4 && t[4].equals("orbit");
                double ox = orbit ? Double.parseDouble(t[5]) : 0, oy = orbit ? Double.parseDouble(t[6]) : 0, oz = orbit ? Double.parseDouble(t[7]) : 0;
                double dist = orbit ? Double.parseDouble(t[8]) : 0;
                float pitch = orbit ? Float.parseFloat(t[9]) : 0, yaw0 = orbit ? Float.parseFloat(t[10]) : 0, step = orbit ? Float.parseFloat(t[11]) : 0;
                int[] taken = {0};
                long[] next = {-1};
                boolean[] moved = {false};
                return mc -> {
                    if (next[0] < 0) {
                        next[0] = tick;
                    }
                    if (taken[0] < count && pendingShot == null) {
                        if (orbit && !moved[0]) {
                            moved[0] = true;
                            float yaw = yaw0 + step * taken[0];
                            double fx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
                            double fy = -Math.sin(Math.toRadians(pitch));
                            double fz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
                            double ex = ox - fx * dist, ey = oy - fy * dist, ez = oz - fz * dist;
                            MinecraftServer server = mc.getSingleplayerServer();
                            server.execute(() -> {
                                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                                p.teleportTo(server.overworld(), ex, ey - 1.62, ez, java.util.Set.of(), yaw, pitch, true);
                            });
                            next[0] = tick + Math.max(2, every);
                        }
                        if (tick >= next[0]) {
                            pendingShot = String.format(Locale.ROOT, "%s_%03d", name, taken[0]);
                            taken[0]++;
                            moved[0] = false;
                            next[0] = tick + every;
                        }
                    }
                    if (taken[0] >= count && pendingShot == null) {
                        log("OK burst " + name + " " + count);
                        return true;
                    }
                    return false;
                };
            }
            case "jeiat": {
                // jeiat <index | name:part> [uses] : the JEI R (or U) key over a terminal list slot, through the same lookup
                // of the ingredient under the cursor that JEI asks the window for (the OS cursor does not move)
                String which = t[1];
                boolean uses = t.length > 2 && t[2].equals("uses");
                return mc -> {
                    if (!(mc.screen instanceof com.golemlogix.client.TerminalScreen screen)) {
                        throw new IllegalStateException("terminal screen is not open: " + mc.screen);
                    }
                    int idx = which.startsWith("name:") ? screen.indexOfName(which.substring(5)) : Integer.parseInt(which);
                    double x = screen.getGuiLeft() + com.golemlogix.menu.TerminalMenu.LIST_X + (idx % com.golemlogix.menu.TerminalMenu.COLS) * 18 + 9;
                    double y = screen.getGuiTop() + com.golemlogix.menu.TerminalMenu.LIST_Y + (idx / com.golemlogix.menu.TerminalMenu.COLS) * 18 + 9;
                    var hit = idx < 0 ? null : screen.ingredientAt(x, y);
                    if (hit == null || !(hit.ingredient() instanceof ItemStack stack)) {
                        log("ERR jeiat " + which + " -> " + (hit == null ? "nothing under the cursor" : hit.ingredient()));
                        return true;
                    }
                    log("OK jeiat " + which + " -> " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    keepScreen = true;
                    if (!com.golemlogix.integration.jei.GolemLogixJeiPlugin.showRecipes(stack, uses)) {
                        throw new IllegalStateException("JEI runtime is not ready");
                    }
                    return true;
                };
            }
            case "jeicount": {
                // jeicount anvil|saturator <n> : how many recipes of the category JEI shows (secret ones are hidden)
                boolean anvil = t[1].equals("anvil");
                int expected = Integer.parseInt(t[2]);
                return mc -> {
                    int actual = com.golemlogix.integration.jei.GolemLogixJeiPlugin.visibleRecipes(anvil);
                    log((actual == expected ? "OK" : "ERR") + " jeicount " + t[1] + " = " + actual + " (expected " + expected + ")");
                    return true;
                };
            }
            case "useon": {
                // useon x y z [face]: right-click the block with the held item (a reagent onto an apparatus, a tablet into a brazier)
                int x = Integer.parseInt(t[1]), y = Integer.parseInt(t[2]), z = Integer.parseInt(t[3]);
                Direction face = t.length > 4 ? Direction.byName(t[4]) : Direction.UP;
                Step inner = onServer((server, level, player) -> {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
                    ItemStack held = player.getMainHandItem();
                    var result = player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit);
                    log("useon " + pos.toShortString() + " with " + held.getItem() + " -> " + result);
                });
                return mc -> {
                    keepScreen = true;
                    return inner.run(mc);
                };
            }
            case "useitem": {
                // useitem : right-click with the held item (into the air), like a player
                if (REMOTE != null) {
                    return mc -> {
                        keepScreen = true;
                        var result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                        log("useitem (remote) " + mc.player.getMainHandItem().getItem() + " -> " + result);
                        return true;
                    };
                }
                Step inner = onServer((server, level, player) -> {
                    ItemStack held = player.getMainHandItem();
                    var result = player.gameMode.useItem(player, level, held, InteractionHand.MAIN_HAND);
                    log("useitem " + held.getItem() + " -> " + result
                            + " container=" + (player.containerMenu == null ? "null" : player.containerMenu.getClass().getSimpleName()));
                });
                return mc -> {
                    keepScreen = true;
                    return inner.run(mc);
                };
            }
            case "bookaudit": {
                // bookaudit : layout of all book entries in the current language (widow lines, no room for text)
                return mc -> {
                    var problems = com.golemlogix.client.book.BookScreen.auditLayout();
                    for (String p : problems) {
                        log("WARN book " + mc.getLanguageManager().getSelected() + ": " + p);
                    }
                    log((problems.isEmpty() ? "OK" : "ERR") + " bookaudit " + mc.getLanguageManager().getSelected() + ": " + problems.size() + " problem(s)");
                    return true;
                };
            }
            case "lang": {
                // lang <code> : game language (with a resource reload)
                String code = t[1];
                CompletableFuture<Void>[] f = new CompletableFuture[1];
                return mc -> {
                    if (f[0] == null) {
                        mc.getLanguageManager().setSelected(code);
                        mc.options.languageCode = code;
                        f[0] = mc.reloadResourcePacks();
                        return false;
                    }
                    if (!f[0].isDone()) {
                        return false;
                    }
                    f[0].join();
                    return mc.getOverlay() == null && tick - stepStart >= 30;
                };
            }
            case "guiscale": {
                // guiscale <n> : interface scale (0 = auto); the open window is rebuilt
                int scale = Integer.parseInt(t[1]);
                return mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeGui();
                    return true;
                };
            }
            case "termsize": {
                // termsize normal|tall|full : terminal window height (like the "Size" button), the open window is rebuilt
                var size = com.golemlogix.config.GolemClientConfig.TerminalSize.valueOf(t[1].toUpperCase(Locale.ROOT));
                return mc -> {
                    com.golemlogix.config.GolemClientConfig.TERMINAL_SIZE.set(size);
                    if (mc.screen != null) {
                        mc.screen.resize(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                    }
                    return true;
                };
            }
            case "hook": {
                String name = t[1];
                String[] args = java.util.Arrays.copyOfRange(t, 2, t.length);
                if (REMOTE != null) {
                    return remoteCommand("golemlogix_dev hook " + rest);
                }
                return onServer((server, level, player) -> DevScenes.run(name, args, level, player, DevShots::log));
            }
            case "campath":
                return mc -> {
                    DevFilm.campath(t);
                    return true;
                };
            case "film":
                return mc -> {
                    DevFilm.start(mc, t, DevShots::log);
                    return true;
                };
            case "filmwait":
                return mc -> DevFilm.done();
            case "quit":
                return mc -> {
                    log("quit");
                    finishScript();
                    mc.stop();
                    return true;
                };
            default:
                throw new IllegalArgumentException("unknown command: " + op);
        }
    }
}
