package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyDigBlast;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerExplosion.class})
public abstract class ServerExplosionMixin {
   @Unique
   private @Nullable SkyDigBlast skycraft$blast;

   @Inject(
      method = {"explode"},
      at = {@At("HEAD")}
   )
   private void skycraft$begin(CallbackInfoReturnable<Integer> cir) {
      this.skycraft$blast = SkyDigBlast.begin((ServerExplosion)this);
   }

   @WrapOperation(
      method = {"calculateExplodedPositions"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;getBlockExplosionResistance(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Ljava/util/Optional;"
)}
   )
   private Optional<Float> skycraft$skyrimResists(ExplosionDamageCalculator calculator, Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid, Operation<Optional<Float>> original) {
      Optional<Float> vanilla = (Optional)original.call(new Object[]{calculator, explosion, level, pos, block, fluid});
      return this.skycraft$blast != null ? this.skycraft$blast.resistance(pos, vanilla) : vanilla;
   }

   @ModifyVariable(
      method = {"explode"},
      at = {@At("STORE")},
      ordinal = 0
   )
   private List<BlockPos> skycraft$skyrimBreaks(List<BlockPos> targets) {
      if (this.skycraft$blast != null) {
         ServerExplosion self = (ServerExplosion)this;
         this.skycraft$blast.materialize(targets, self.getBlockInteraction() != BlockInteraction.KEEP && self.getBlockInteraction() != BlockInteraction.TRIGGER_BLOCK);
      }

      return targets;
   }

   @Inject(
      method = {"explode"},
      at = {@At("RETURN")}
   )
   private void skycraft$tellSkyrim(CallbackInfoReturnable<Integer> cir) {
      if (this.skycraft$blast != null) {
         this.skycraft$blast.finish();
         this.skycraft$blast = null;
      }

      if (SkyLink.active()) {
         ServerExplosion self = (ServerExplosion)this;
         Vec3 center = self.center();
         SkyLink.pushEvent(3, 0, (float)center.x, (float)center.y, (float)center.z, self.radius(), 0);
      }
   }
}
