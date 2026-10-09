package dev.skycraft.client.mixin;

import dev.skycraft.client.SkyClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public abstract class LevelRendererMixin {
   @Inject(
      method = {"render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$skipLevel(CallbackInfo ci) {
      if (SkyClient.linked()) {
         ci.cancel();
      }

   }
}
