package dev.skycraft.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PauseScreen.class})
public abstract class PauseScreenMixin {
   @Shadow
   private @Nullable Button disconnectButton;

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void skycraft$noLeavingTheWorld(CallbackInfo ci) {
      if (this.disconnectButton != null) {
         this.disconnectButton.visible = false;
         this.disconnectButton.active = false;
      }

   }
}
