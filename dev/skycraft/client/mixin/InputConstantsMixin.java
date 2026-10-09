package dev.skycraft.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.skycraft.client.InputBridge;
import dev.skycraft.client.SkyClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({InputConstants.class})
public abstract class InputConstantsMixin {
   @Inject(
      method = {"isKeyDown"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void skycraft$isKeyDown(int key, CallbackInfoReturnable<Boolean> cir) {
      if (SkyClient.tookOver()) {
         cir.setReturnValue(InputBridge.isKeyDown(key));
      }

   }

   @Inject(
      method = {"grabMouse"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void skycraft$grabMouse(Window window, double xpos, double ypos, CallbackInfo ci) {
      if (SkyClient.tookOver()) {
         ci.cancel();
      }

   }

   @Inject(
      method = {"releaseMouse"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void skycraft$releaseMouse(Window window, double xpos, double ypos, CallbackInfo ci) {
      if (SkyClient.tookOver()) {
         ci.cancel();
      }

   }
}
