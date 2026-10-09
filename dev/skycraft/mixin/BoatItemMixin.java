package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyWater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BoatItem.class})
public abstract class BoatItemMixin {
   @WrapOperation(
      method = {"use"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/item/BoatItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"
)}
   )
   private BlockHitResult skycraft$wowWater(Level level, Player player, ClipContext.Fluid fluid, Operation<BlockHitResult> original) {
      BlockHitResult hit = (BlockHitResult)original.call(new Object[]{level, player, fluid});
      if (!SkyWater.active()) {
         return hit;
      } else {
         Vec3 eye = player.getEyePosition();
         Vec3 look = player.getViewVector(1.0F);
         double reach = player.blockInteractionRange();
         double limit = hit.getType() == Type.MISS ? reach : hit.getLocation().distanceTo(eye);

         for(double d = (double)0.0F; d <= limit; d += 0.1) {
            Vec3 p = eye.add(look.scale(d));
            double surface = SkyWater.surfaceAt(Mth.floor(p.x), Mth.floor(p.z));
            if (!Double.isNaN(surface) && p.y <= surface && !SkyWater.lavaAt(Mth.floor(p.x), Mth.floor(p.z))) {
               Vec3 at = new Vec3(p.x, surface, p.z);
               return new BlockHitResult(at, Direction.UP, BlockPos.containing(p.x, surface - 0.01, p.z), false);
            }
         }

         return hit;
      }
   }
}
