package dev.skycraft.client.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({IntegratedServer.class})
public abstract class IntegratedServerMixin {
   @Inject(
      method = {"getMaxPlayers"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$morePlayers(CallbackInfoReturnable<Integer> cir) {
      cir.setReturnValue(100);
   }
}
