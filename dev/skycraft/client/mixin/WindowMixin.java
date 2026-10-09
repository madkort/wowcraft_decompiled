package dev.skycraft.client.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.skycraft.client.SkyClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Window.class})
public abstract class WindowMixin {
   @Inject(
      method = {"isFocused"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$focused(CallbackInfoReturnable<Boolean> cir) {
      if (SkyClient.tookOver()) {
         cir.setReturnValue(SkyClient.linked());
      }

   }

   @Inject(
      method = {"isIconified"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$notIconified(CallbackInfoReturnable<Boolean> cir) {
      if (SkyClient.linked()) {
         cir.setReturnValue(false);
      }

   }

   @Inject(
      method = {"calculateScale"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void wowcraft$hiresScale(int guiScale, boolean forceUnicode, CallbackInfoReturnable<Integer> cir) {
      int factor = SkyClient.guiFactor;
      if (factor > 1 && guiScale != 0) {
         Window self = (Window)this;
         int w = self.getWidth();
         int h = self.getHeight();

         int max;
         for(max = 1; max < w && max < h && w / (max + 1) >= 320 && h / (max + 1) >= 240; ++max) {
         }

         int scale = Math.min(cir.getReturnValueI() * factor, max);
         if (forceUnicode && scale % 2 != 0 && scale > 1) {
            --scale;
         }

         cir.setReturnValue(Math.max(1, scale));
      }
   }
}
