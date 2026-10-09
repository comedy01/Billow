package dev.billow.neoforge;

import dev.billow.client.BillowClient;
import dev.billow.client.gui.BillowSettingsScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = BillowClient.MOD_ID, dist = Dist.CLIENT)
public final class BillowNeoForge {
    public BillowNeoForge(IEventBus modBus, ModContainer container) {
        BillowClient.init(FMLPaths.CONFIGDIR.get());

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new BillowSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
