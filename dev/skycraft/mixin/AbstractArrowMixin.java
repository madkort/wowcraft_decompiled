package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyClip;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractArrow.class})
public abstract class AbstractArrowMixin {
   @Unique
   private Vec3 skycraft$hitAt;

   @WrapOperation(
      method = {"tick"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/level/Level;clipIncludingBorder(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
)}
   )
   private BlockHitResult skycraft$hitSkyrim(Level level, ClipContext context, Operation<BlockHitResult> original) {
      return SkyClip.refine(context.getFrom(), context.getTo(), (BlockHitResult)original.call(new Object[]{level, context}), SkyClip.Use.PROJECTILE);
   }

   @Inject(
      method = {"onHitEntity"},
      at = {@At("HEAD")}
   )
   private void skycraft$rememberHit(EntityHitResult hitResult, CallbackInfo ci) {
      this.skycraft$hitAt = hitResult.getLocation();
   }

   @WrapOperation(
      method = {"onHitEntity"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/entity/LivingEntity;setArrowCount(I)V"
)}
   )
   private void skycraft$stickInSkyrimActor(LivingEntity mob, int count, Operation<Void> original) {
      original.call(new Object[]{mob, count});
      if (mob instanceof SkyrimActorEntity actor) {
         if (this.skycraft$hitAt != null && SkyLink.active()) {
            AbstractArrow self = (AbstractArrow)this;
            Vec3 v = self.getDeltaMovement();
            float yaw = (float)(Mth.atan2(v.x, v.z) * (double)(180F / (float)Math.PI));
            float pitch = (float)(Mth.atan2(v.y, v.horizontalDistance()) * (double)(180F / (float)Math.PI));
            byte var10000;
            if (self instanceof SpectralArrow) {
               var10000 = 2;
            } else {
               label18: {
                  if (self instanceof Arrow) {
                     Arrow tippable = (Arrow)self;
                     if (tippable.getColor() > 0) {
                        var10000 = 1;
                        break label18;
                     }
                  }

                  var10000 = 0;
               }
            }

            int texture = var10000;
            Vec3 at = this.skycraft$hitAt;
            SkyLink.pushEvent(4, actor.formId(), (float)at.x, (float)at.y, (float)at.z, yaw, Float.floatToRawIntBits(pitch), texture);
            return;
         }
      }

   }
}
