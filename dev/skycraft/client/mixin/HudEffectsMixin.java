package dev.skycraft.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Hud.class})
public abstract class HudEffectsMixin {
   @Inject(
      method = {"extractEffects"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$notOverTheMinimap(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
      ci.cancel();
   }
}
