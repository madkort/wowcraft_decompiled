package dev.skycraft.client.mixin;

import dev.skycraft.client.SkyClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelLoadingScreen.class})
public abstract class LevelLoadingScreenMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$invisible(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (SkyClient.linked()) {
         ci.cancel();
      }

   }

   @Inject(
      method = {"extractBackground"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$noBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (SkyClient.linked()) {
         ci.cancel();
      }

   }
}
