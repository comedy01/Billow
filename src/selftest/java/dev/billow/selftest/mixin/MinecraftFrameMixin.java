package dev.billow.selftest.mixin;

import dev.billow.selftest.BillowSelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void billowSelftest$frame(boolean advanceGameTime, CallbackInfo ci) {
        BillowSelfTest.frame();
    }
}
