package dev.billow.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.billow.core.CloudStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class BillowConfig {
    public static final String FILE_NAME = "billow.json";

    public static final int DEFAULT_HEIGHT = 0;
    public static final int MIN_HEIGHT = 64;
    public static final int MAX_HEIGHT = 512;
    public static final int MIN_THICKNESS = 2;
    public static final int MAX_THICKNESS = 16;
    public static final int MAX_SPEED = 500;

    private static final Logger LOGGER = LogManager.getLogger("billow");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String style = CloudStyle.ROUNDED.id();
    private int height = DEFAULT_HEIGHT;
    private int thickness = 6;
    private int roundness = 70;
    private int speed = 100;
    private int opacity = 100;
    private int coverage = 45;
    private boolean shapesWithClouds = true;

    public CloudStyle style() {
        return CloudStyle.byId(style);
    }

    public void setStyle(CloudStyle value) {
        style = value.id();
    }

    public int height() {
        return height <= 0 ? 0 : clamp(height, MIN_HEIGHT, MAX_HEIGHT);
    }

    public void setHeight(int value) {
        height = value < MIN_HEIGHT ? 0 : clamp(value, MIN_HEIGHT, MAX_HEIGHT);
    }

    public int thickness() {
        return clamp(thickness, MIN_THICKNESS, MAX_THICKNESS);
    }

    public void setThickness(int value) {
        thickness = clamp(value, MIN_THICKNESS, MAX_THICKNESS);
    }

    public int roundness() {
        return clamp(roundness, 0, 100);
    }

    public void setRoundness(int value) {
        roundness = clamp(value, 0, 100);
    }

    public int speed() {
        return clamp(speed, 0, MAX_SPEED);
    }

    public void setSpeed(int value) {
        speed = clamp(value, 0, MAX_SPEED);
    }

    public int opacity() {
        return clamp(opacity, 10, 100);
    }

    public void setOpacity(int value) {
        opacity = clamp(value, 10, 100);
    }

    public int coverage() {
        return clamp(coverage, 0, 100);
    }

    public void setCoverage(int value) {
        coverage = clamp(value, 0, 100);
    }

    public boolean shapesWithClouds() {
        return shapesWithClouds;
    }

    public void setShapesWithClouds(boolean value) {
        shapesWithClouds = value;
    }

    public float radius() {
        return roundness() / 100f * Math.min(thickness() * 0.5f, 4.5f);
    }

    public void resetToDefaults() {
        BillowConfig fresh = new BillowConfig();
        style = fresh.style;
        height = fresh.height;
        thickness = fresh.thickness;
        roundness = fresh.roundness;
        speed = fresh.speed;
        opacity = fresh.opacity;
        coverage = fresh.coverage;
        shapesWithClouds = fresh.shapesWithClouds;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static BillowConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            BillowConfig fresh = new BillowConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            BillowConfig loaded = GSON.fromJson(reader, BillowConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            BillowConfig fresh = new BillowConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.write(temp, (GSON.toJson(this) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
