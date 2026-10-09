package dev.billow.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.billow.client.BillowClient;
import dev.billow.client.render.BillowClouds;
import dev.billow.config.BillowConfig;
import dev.billow.core.CloudStyle;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Shadow
    private CloudRenderer.TextureData texture;

    @Unique
    private boolean billow$reentry;
    @Unique
    private CloudRenderer.TextureData billow$cellsFor;
    @Unique
    private boolean[] billow$cells;

    @Shadow
    public abstract void prepare(int color, CloudStatus cloudStatus, float bottomY, int range, Vec3 cameraPosition,
                                 long gameTime, float partialTicks);

    @Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"), cancellable = true)
    private void billow$prepare(int color, CloudStatus cloudStatus, float bottomY, int range, Vec3 cameraPosition,
                                long gameTime, float partialTicks, CallbackInfo ci) {
        if (billow$reentry) {
            return;
        }
        BillowConfig config = BillowClient.config();
        float height = config.height() > 0 ? config.height() : bottomY;
        int faded = ARGB.color(Math.round(ARGB.alpha(color) * config.opacity() / 100f), color);
        BillowClouds clouds = BillowClouds.get();
        ci.cancel();

        if (config.style() == CloudStyle.VANILLA) {
            clouds.deactivate();
            double ticks = gameTime * (config.speed() / 100.0);
            long whole = (long) Math.floor(ticks);
            float partial = (float) (ticks - whole) + partialTicks * config.speed() / 100f;
            billow$reentry = true;
            try {
                prepare(faded, cloudStatus, height, range, cameraPosition, whole, partial);
            } finally {
                billow$reentry = false;
            }
            return;
        }

        if (texture != null && texture != billow$cellsFor) {
            long[] packed = texture.cells();
            boolean[] cells = new boolean[packed.length];
            for (int i = 0; i < packed.length; i++) {
                cells[i] = packed[i] != 0L;
            }
            billow$cells = cells;
            billow$cellsFor = texture;
        }
        CloudRenderer.TextureData data = texture;
        clouds.prepare(data, data != null ? billow$cells : null, data != null ? data.width() : 0,
                data != null ? data.height() : 0, color, height, range, cameraPosition, gameTime, partialTicks);
    }

    @Inject(method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            at = @At("HEAD"), cancellable = true)
    private void billow$render(CloudStatus cloudStatus, RenderPass renderPass, CallbackInfo ci) {
        BillowClouds clouds = BillowClouds.get();
        if (clouds.active()) {
            clouds.render(renderPass);
            ci.cancel();
        }
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void billow$renderOit(CloudStatus cloudStatus, OitStage stage, GpuTextureView mainDepthTextureView,
                                  OitRenderPassProvider.Parameters params, CallbackInfo ci) {
        BillowClouds clouds = BillowClouds.get();
        if (clouds.active()) {
            clouds.renderOit(stage, mainDepthTextureView, params);
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void billow$close(CallbackInfo ci) {
        BillowClouds.get().close();
    }
}
