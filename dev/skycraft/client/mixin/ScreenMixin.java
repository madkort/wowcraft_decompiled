package dev.skycraft.client.mixin;

import dev.skycraft.client.SkyClient;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Screen.class})
public abstract class ScreenMixin {
   @Inject(
      method = {"isPauseScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$neverPauseWhileLinked(CallbackInfoReturnable<Boolean> cir) {
      if (SkyClient.linked()) {
         cir.setReturnValue(false);
      }

   }
}
