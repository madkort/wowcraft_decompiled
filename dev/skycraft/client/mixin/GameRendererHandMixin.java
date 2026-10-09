package dev.skycraft.client.mixin;

import dev.skycraft.client.SkyClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GameRenderer.class})
public abstract class GameRendererHandMixin {
   @Inject(
      method = {"renderItemInHand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void wowcraft$noHandOverBags(CallbackInfo ci) {
      if (SkyClient.linked() && (SkyClient.sky().flags & 128) != 0) {
         ci.cancel();
      }

   }
}
