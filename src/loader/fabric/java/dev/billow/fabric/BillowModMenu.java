package dev.billow.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.billow.client.gui.BillowSettingsScreen;
import net.minecraft.client.Minecraft;

public final class BillowModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new BillowSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
