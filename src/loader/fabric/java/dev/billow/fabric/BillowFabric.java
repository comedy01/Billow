package dev.billow.fabric;

import dev.billow.client.BillowClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class BillowFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BillowClient.init(FabricLoader.getInstance().getConfigDir());
    }
}
