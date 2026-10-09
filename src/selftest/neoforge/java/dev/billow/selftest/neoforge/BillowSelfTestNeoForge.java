package dev.billow.selftest.neoforge;

import dev.billow.selftest.BillowSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "billow_selftest", dist = Dist.CLIENT)
public final class BillowSelfTestNeoForge {
    public BillowSelfTestNeoForge() {
        BillowSelfTest test = new BillowSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
