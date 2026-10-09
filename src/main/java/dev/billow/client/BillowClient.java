package dev.billow.client;

import dev.billow.config.BillowConfig;
import dev.billow.core.Sky;

import java.nio.file.Path;

public final class BillowClient {
    public static final String MOD_ID = "billow";

    private static BillowConfig config = new BillowConfig();
    private static Path configPath;

    private BillowClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(BillowConfig.FILE_NAME);
        config = BillowConfig.load(configPath);
    }

    public static BillowConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }

    public static Sky.Settings skySettings() {
        return new Sky.Settings(config.style(), config.thickness(), config.radius(), config.coverage(),
                config.shapesWithClouds());
    }
}
