package dev.skycraft.client.mixin;

import dev.skycraft.client.SkyDigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Minecraft.class})
public abstract class MinecraftDigMixin {
   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$digStart(CallbackInfoReturnable<Boolean> cir) {
      Minecraft minecraft = (Minecraft)this;
      if (SkyDigClient.attack(minecraft)) {
         ItemStack held = minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
         minecraft.player.swing(InteractionHand.MAIN_HAND, held.getAttackAnimation(), false);
         cir.setReturnValue(true);
      }

   }

   @Inject(
      method = {"continueAttack"},
      at = {@At("HEAD")}
   )
   private void skycraft$digHold(boolean down, CallbackInfo ci) {
      if (down) {
         SkyDigClient.attack((Minecraft)this);
      }

   }
}
