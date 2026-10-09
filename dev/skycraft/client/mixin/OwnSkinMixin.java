package dev.skycraft.client.mixin;

import dev.skycraft.client.OwnSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractClientPlayer.class})
public abstract class OwnSkinMixin {
   @Inject(
      method = {"getSkin"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$ownSkin(CallbackInfoReturnable<PlayerSkin> cir) {
      if (this == Minecraft.getInstance().player) {
         PlayerSkin skin = OwnSkin.skin();
         if (skin != null) {
            cir.setReturnValue(skin);
         }
      }

   }
}
