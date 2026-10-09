package dev.billow.selftest.fabric;

import dev.billow.selftest.BillowSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class BillowSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BillowSelfTest test = new BillowSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
