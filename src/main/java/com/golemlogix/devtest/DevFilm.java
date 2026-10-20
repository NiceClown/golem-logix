package com.golemlogix.devtest;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Film mode of the rig: the camera flies smoothly along a path of keyframes (Catmull-Rom through them, eased) or around a point,
 * and every frame goes to ffmpeg as a video (run/devshots/film/name.mp4). Time is game time (client ticks + the partial tick), so
 * the video plays at the game's own pace however fast frames are rendered; slow the game down (rate=) and there are more frames per
 * tick to choose from, so the motion stays smooth. The camera is the player, put in place before every frame.
 * <pre>
 *   campath clear | key x y z yaw pitch [fov] [roll] | look tx ty tz dist yaw pitch [fov] [roll]
 *   campath orbit tx ty tz dist pitch yaw0 yaw1 [fov] | ease smooth|linear|in|out | hold f (0..1) | free
 *   film name ticks [fps=60] [rate=20] [crf=14]   (starts the take, the scenario goes on: timed events can follow)
 *   filmwait                                       (until the take is written)
 * </pre>
 */
final class DevFilm {
    /** A keyframe: the eye position, where it looks, the field of view (0 = the options' one) and the roll. */
    record Key(double x, double y, double z, float yaw, float pitch, float fov, float roll) {
    }

    private record Frame(byte[] data, int copies) {
    }

    private static final Frame END = new Frame(new byte[0], 0);

    private static final List<Key> PATH = new ArrayList<>();
    /** An orbit instead of keys: tx ty tz dist pitch yaw0 yaw1 fov. */
    private static double[] orbit;
    private static String ease = "smooth";
    /** Where the camera stays while no take runs (a fraction of the path); NaN = the camera is free. */
    private static double holdAt = Double.NaN;
    /** The pose of the current frame (for the camera angle and FOV events). */
    private static Key current;

    // the take
    private static boolean rolling;
    private static String takeName;
    private static int length;
    private static int fps;
    private static long ticks;
    private static long frame;
    private static long totalFrames;
    private static float lastT;
    private static float rate = 20f;
    private static int width;
    private static int height;
    private static ByteBuffer pixels;
    private static BlockingQueue<Frame> queue;
    private static BlockingQueue<byte[]> free;
    private static long rendered;
    private static long repeated;
    private static long startNanos;
    private static volatile boolean writing;
    private static volatile String writeError;
    private static Consumer<String> log = s -> { };

    private DevFilm() {
    }

    // ---------------------------------------------------------------------------------------------- the path

    static void campath(String[] t) {
        switch (t[1]) {
            case "clear" -> {
                PATH.clear();
                orbit = null;
                holdAt = Double.NaN;
            }
            case "key" -> addKey(Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4]),
                    Float.parseFloat(t[5]), Float.parseFloat(t[6]), opt(t, 7), opt(t, 8));
            case "look" -> {
                double tx = Double.parseDouble(t[2]), ty = Double.parseDouble(t[3]), tz = Double.parseDouble(t[4]);
                double dist = Double.parseDouble(t[5]);
                float yaw = Float.parseFloat(t[6]), pitch = Float.parseFloat(t[7]);
                Vec3 f = forward(yaw, pitch);
                addKey(tx - f.x * dist, ty - f.y * dist, tz - f.z * dist, yaw, pitch, opt(t, 8), opt(t, 9));
            }
            case "orbit" -> {
                PATH.clear();
                orbit = new double[8];
                for (int i = 0; i < 7; i++) {
                    orbit[i] = Double.parseDouble(t[2 + i]);
                }
                orbit[7] = opt(t, 9);
            }
            case "ease" -> ease = t[2];
            case "hold" -> holdAt = Double.parseDouble(t[2]);
            case "free" -> holdAt = Double.NaN;
            default -> throw new IllegalArgumentException("campath " + t[1]);
        }
    }

    private static float opt(String[] t, int i) {
        return t.length > i ? Float.parseFloat(t[i]) : 0f;
    }

    /** Keys after the first one turn the short way: their yaw is unwrapped next to the previous key's. */
    private static void addKey(double x, double y, double z, float yaw, float pitch, float fov, float roll) {
        orbit = null;
        if (!PATH.isEmpty()) {
            float prev = PATH.get(PATH.size() - 1).yaw();
            yaw = prev + Mth.wrapDegrees(yaw - prev);
        }
        PATH.add(new Key(x, y, z, yaw, pitch, fov, roll));
    }

    private static Vec3 forward(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    private static double eased(double s) {
        s = Mth.clamp(s, 0.0, 1.0);
        return switch (ease) {
            case "linear" -> s;
            case "in" -> s * s;
            case "out" -> 1 - (1 - s) * (1 - s);
            default -> 0.5 - 0.5 * Math.cos(Math.PI * s);
        };
    }

    /** The camera at fraction s of the path (null: no path). */
    private static Key pose(double s) {
        s = eased(s);
        if (orbit != null) {
            float yaw = (float) Mth.lerp(s, orbit[5], orbit[6]), pitch = (float) orbit[4];
            Vec3 f = forward(yaw, pitch);
            return new Key(orbit[0] - f.x * orbit[3], orbit[1] - f.y * orbit[3], orbit[2] - f.z * orbit[3], yaw, pitch,
                    (float) orbit[7], 0f);
        }
        int n = PATH.size();
        if (n == 0) {
            return null;
        }
        if (n == 1) {
            return PATH.get(0);
        }
        double u = s * (n - 1);
        int i = Math.min((int) u, n - 2);
        double f = u - i;
        Key a = PATH.get(Math.max(0, i - 1)), b = PATH.get(i), c = PATH.get(i + 1), d = PATH.get(Math.min(n - 1, i + 2));
        return new Key(cr(a.x, b.x, c.x, d.x, f), cr(a.y, b.y, c.y, d.y, f), cr(a.z, b.z, c.z, d.z, f),
                (float) cr(a.yaw, b.yaw, c.yaw, d.yaw, f), (float) cr(a.pitch, b.pitch, c.pitch, d.pitch, f),
                (float) Mth.lerp(f, b.fov, c.fov), (float) cr(a.roll, b.roll, c.roll, d.roll, f));
    }

    /** Uniform Catmull-Rom between b and c. */
    private static double cr(double a, double b, double c, double d, double t) {
        return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (3 * b - a - 3 * c + d) * t * t * t);
    }

    // ---------------------------------------------------------------------------------------------- frames

    static void clientTick() {
        if (rolling) {
            ticks++;
        }
    }

    /** Before the frame: the camera (the player) goes where the path says for this moment. */
    static void beforeFrame(Minecraft mc, float partial) {
        current = null;
        double s;
        if (rolling) {
            lastT = ticks + partial;
            s = lastT / length;
        } else if (!Double.isNaN(holdAt)) {
            s = holdAt;
        } else {
            return;
        }
        Key k = pose(s);
        LocalPlayer p = mc.player;
        if (k == null || p == null) {
            return;
        }
        current = k;
        double feet = k.y() - p.getEyeHeight();
        p.setPos(k.x(), feet, k.z());
        p.xo = p.xOld = k.x();
        p.yo = p.yOld = feet;
        p.zo = p.zOld = k.z();
        p.setDeltaMovement(Vec3.ZERO);
        p.setYRot(k.yaw());
        p.yRotO = k.yaw();
        p.setXRot(k.pitch());
        p.xRotO = k.pitch();
        p.yHeadRot = p.yHeadRotO = k.yaw();
    }

    /** The exact angles (the roll too): the player's own are rounded through the network. */
    static void cameraAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        if (current != null) {
            e.setYaw(current.yaw());
            e.setPitch(current.pitch());
            e.setRoll(current.roll());
        }
    }

    static void fov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov e) {
        if (current != null && current.fov() > 0 && e.usedConfiguredFov()) {
            e.setFOV(current.fov());
        }
    }

    /** After the frame: it fills every video frame whose moment has come (more than one if rendering lags behind). */
    static void afterFrame(Minecraft mc) {
        if (!rolling) {
            return;
        }
        rendered++;
        double ticksPerFrame = 20.0 / fps;
        if (lastT + 1e-4 < frame * ticksPerFrame) {
            return;
        }
        RenderTarget target = mc.getMainRenderTarget();
        if (target.width != width || target.height != height) {
            log.accept("ERR film " + takeName + ": the window changed size, the take stops");
            stop(mc);
            return;
        }
        int copies = 0;
        while (frame < totalFrames && frame * ticksPerFrame <= lastT + 1e-4) {
            frame++;
            copies++;
        }
        try {
            byte[] data = free.take();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((com.mojang.blaze3d.opengl.GlTexture) target.getColorTexture()).glId());
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            pixels.get(0, data);
            queue.put(new Frame(data, copies));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        repeated += copies - 1;
        if (frame >= totalFrames) {
            stop(mc);
        }
    }

    // ---------------------------------------------------------------------------------------------- the take

    static void start(Minecraft mc, String[] t, Consumer<String> logger) throws Exception {
        if (rolling || writing) {
            throw new IllegalStateException("a take is still running");
        }
        log = logger;
        takeName = t[1];
        length = Integer.parseInt(t[2]);
        fps = 60;
        rate = 20f;
        int crf = 14;
        for (int i = 3; i < t.length; i++) {
            String[] kv = t[i].split("=");
            switch (kv[0]) {
                case "fps" -> fps = Integer.parseInt(kv[1]);
                case "rate" -> rate = Float.parseFloat(kv[1]);
                case "crf" -> crf = Integer.parseInt(kv[1]);
                default -> throw new IllegalArgumentException("film " + t[i]);
            }
        }
        RenderTarget target = mc.getMainRenderTarget();
        width = target.width;
        height = target.height;
        totalFrames = (long) length * fps / 20;
        if (pixels == null || pixels.capacity() != width * height * 4) {
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }
            pixels = MemoryUtil.memAlloc(width * height * 4);
        }
        free = new ArrayBlockingQueue<>(6);
        for (int i = 0; i < 6; i++) {
            free.add(new byte[width * height * 4]);
        }
        queue = new ArrayBlockingQueue<>(8);

        Path dir = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        Path out = dir.resolve("film").resolve(takeName + ".mp4");
        Files.createDirectories(out.getParent());
        List<String> cmd = List.of(ffmpeg(dir), "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgba",
                "-s", width + "x" + height, "-r", String.valueOf(fps), "-i", "-", "-vf", "vflip", "-c:v", "libx264",
                "-preset", "fast", "-crf", String.valueOf(crf), "-pix_fmt", "yuv420p", out.toString());
        File errLog = out.resolveSibling(takeName + ".ffmpeg.log").toFile();
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(errLog).start();
        writing = true;
        writeError = null;
        Thread writer = new Thread(() -> write(process), "DevFilm writer");
        writer.setDaemon(true);
        writer.start();

        // a steady picture: no bobbing, no speed FOV, frames as fast as they come
        mc.options.bobView().set(false);
        mc.options.fovEffectScale().set(0.0);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.getWindow().updateVsync(false);
        setRate(mc, rate);
        ticks = 0;
        frame = 0;
        rendered = 0;
        repeated = 0;
        lastT = 0;
        startNanos = System.nanoTime();
        rolling = true;
        log.accept(String.format(Locale.ROOT, "film %s: %d ticks, %dx%d, %d fps, rate %.1f", takeName, length, width, height, fps, rate));
    }

    private static void write(Process process) {
        try (OutputStream out = new BufferedOutputStream(process.getOutputStream(), 1 << 22)) {
            while (true) {
                Frame f = queue.take();
                if (f == END) {
                    break;
                }
                for (int i = 0; i < f.copies(); i++) {
                    out.write(f.data());
                }
                free.put(f.data());
            }
        } catch (Exception e) {
            writeError = e.toString();
        }
        try {
            int code = process.waitFor();
            if (code != 0 && writeError == null) {
                writeError = "ffmpeg exit " + code;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        writing = false;
    }

    private static void stop(Minecraft mc) {
        rolling = false;
        current = null;
        setRate(mc, 20f);
        try {
            queue.put(END);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        double secs = (System.nanoTime() - startNanos) / 1e9;
        log.accept(String.format(Locale.ROOT, "film %s rolled: %d frames from %d rendered (%.1f fps real), %d repeated, %.1f s",
                takeName, frame, rendered, rendered / secs, repeated, secs));
    }

    /** True when the last take is written (the step of filmwait). */
    static boolean done() {
        if (rolling || writing) {
            return false;
        }
        if (writeError != null) {
            log.accept("ERR film " + takeName + ": " + writeError);
            writeError = null;
        }
        return true;
    }

    private static void setRate(Minecraft mc, float r) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.tickRateManager().setTickRate(r));
        }
    }

    private static String ffmpeg(Path gameDir) {
        String prop = System.getProperty("golemlogix.ffmpeg");
        if (prop != null) {
            return prop;
        }
        // run/devshots -> the project's tools/ffmpeg
        Path p = gameDir.getParent().getParent().resolve("tools/ffmpeg/bin/ffmpeg.exe");
        if (!Files.exists(p)) {
            GolemLogixMod.LOGGER.warn("[DevShots] no ffmpeg at {}, trying the PATH", p);
            return "ffmpeg";
        }
        return p.toString();
    }
}
