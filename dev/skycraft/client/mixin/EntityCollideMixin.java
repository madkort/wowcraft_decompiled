package dev.skycraft.client.mixin;

import dev.skycraft.client.SkyClient;
import dev.skycraft.client.SkyCollider;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class EntityCollideMixin {
   @Inject(
      method = {"collide"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void skycraft$smoothSkyrimCollision(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
      if (this instanceof LocalPlayer player) {
         if (SkyClient.linked() && !player.noPhysics) {
            cir.setReturnValue(SkyCollider.collide(player, (Vec3)cir.getReturnValue()));
         }
      }

   }
}
