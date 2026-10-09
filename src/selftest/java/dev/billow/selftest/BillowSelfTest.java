package dev.billow.selftest;

import dev.billow.client.BillowClient;
import dev.billow.client.gui.BillowSettingsScreen;
import dev.billow.client.render.BillowClouds;
import dev.billow.config.BillowConfig;
import dev.billow.core.CloudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class BillowSelfTest {
    private static final String WORLD = "billow-selftest";
    private static final int TIMEOUT = 2400;

    private static volatile int frames;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;
    private int patience;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                ServerCompat.hideChat(mc);
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, true, 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    public static void frame() {
        frames++;
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        nameShots(mc);
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        BillowConfig config = BillowClient.config();
        config.resetToDefaults();
        BillowClient.saveConfig();
        mc.stop();
    }

    private void plan(Minecraft mc) {
        BillowConfig config = BillowClient.config();
        String only = System.getProperty("billow.style");

        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded"));
        then(40, () -> {
            config.resetToDefaults();
            run(mc, "time set 6000");
            run(mc, "weather clear");
            run(mc, "effect give @p minecraft:night_vision infinite 0 true");
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        });

        for (CloudStyle style : CloudStyle.values()) {
            if (only != null && !only.equalsIgnoreCase(style.id())) {
                continue;
            }
            then(5, () -> {
                config.setStyle(style);
                look(mc, 0, 0, ServerCompat.FLOOR, -28, 0);
            });
            settle(mc, style);
            then(10, () -> screenshot(mc, style.id() + "-below"));
            then(5, () -> look(mc, 0, 0, 300, 40, 0));
            settle(mc, style);
            then(10, () -> screenshot(mc, style.id() + "-above"));
            then(5, () -> look(mc, 0, 0, 205, 25, 0));
            settle(mc, style);
            then(10, () -> {
                screenshot(mc, style.id() + "-level");
                if (style != CloudStyle.VANILLA) {
                    log(style.id() + ": " + BillowClouds.get().drawnTiles() + " tiles, "
                            + BillowClouds.get().drawnQuads() + " quads, " + mc.getFps() + " fps");
                    check(BillowClouds.get().active(), style.id() + " did not take over the clouds");
                    check(BillowClouds.get().drawnQuads() > 0, style.id() + " drew no clouds");
                } else {
                    check(!BillowClouds.get().active(), "Billow drew over vanilla clouds");
                }
            });
        }

        if (only == null) {
            then(5, () -> {
                config.setStyle(CloudStyle.ROUNDED);
                config.setHeight(140);
                look(mc, 0, 0, ServerCompat.FLOOR, -28, 0);
            });
            settle(mc, CloudStyle.ROUNDED);
            then(10, () -> screenshot(mc, "rounded-height-140"));
            then(5, () -> {
                config.setHeight(0);
                config.setStyle(CloudStyle.VANILLA);
                config.setHeight(300);
            });
            then(30, () -> screenshot(mc, "vanilla-height-300"));
            then(5, () -> {
                config.resetToDefaults();
                config.setStyle(CloudStyle.SHAPES);
                config.setShapesWithClouds(false);
                look(mc, 0, 0, 330, 55, 0);
            });
            settle(mc, CloudStyle.SHAPES);
            then(10, () -> screenshot(mc, "shapes-only-above"));

            then(5, () -> {
                config.resetToDefaults();
                if (mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screens.open(mc, new BillowSettingsScreen(null, mc.options));
            });
            then(20, () -> {
                check(Screens.current(mc) instanceof BillowSettingsScreen, "the settings screen did not open");
                screenshot(mc, "settings");
            });
            then(5, () -> Screens.open(mc, null));
        }
        then(20, () -> {
        });
    }

    private void settle(Minecraft mc, CloudStyle style) {
        then(20, () -> {
        });
        within(1200, () -> style == CloudStyle.VANILLA || !BillowClouds.get().busy(), () -> {
        });
        then(20, () -> {
        });
        within(600, () -> style == CloudStyle.VANILLA || !BillowClouds.get().busy(), () -> {
        });
    }

    private static void look(Minecraft mc, double x, double z, double y, float pitch, float yaw) {
        run(mc, "gamemode spectator @p");
        run(mc, String.format(Locale.ROOT, "tp @p %.1f %.1f %.1f %.1f %.1f", x, y, z, yaw, pitch));
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> ServerCompat.runCommand(server, command));
    }

    private void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        ServerCompat.screenshot(mc, Screens.renderTarget(mc));
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "bl-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[billow-selftest] " + message);
    }
}
