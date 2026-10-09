package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyWater;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractBoat.class})
public abstract class BoatWaterMixin {
   private static final double LIFT = 0.3;
   private static final double DRAFT = 0.15;

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void skycraft$staysAfloat(CallbackInfo ci) {
      AbstractBoat boat = (AbstractBoat)this;
      if (SkyWater.active()) {
         double surface = SkyWater.surfaceAt(Mth.floor(boat.getX()), Mth.floor(boat.getZ()));
         if (!Double.isNaN(surface)) {
            double floor = surface + 0.3 - 0.15;
            if (boat.getY() < floor && boat.getY() > surface - (double)8.0F) {
               Vec3 v = boat.getDeltaMovement();
               boat.setDeltaMovement(v.x, Math.max(v.y, Math.min(0.2, (floor - boat.getY()) * (double)0.5F)), v.z);
            }

         }
      }
   }

   @WrapOperation(
      method = {"checkInWater", "isUnderwater", "getWaterLevelAbove"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
)}
   )
   private FluidState skycraft$hostWater(Level level, BlockPos pos, Operation<FluidState> original) {
      FluidState state = (FluidState)original.call(new Object[]{level, pos});
      if (state.isEmpty() && SkyWater.active()) {
         FluidState water = SkyWater.fluidAt(level, pos, 0.3);
         if (water != null) {
            return water;
         }
      }

      return state;
   }

   @WrapOperation(
      method = {"checkInWater", "isUnderwater", "getWaterLevelAbove"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
)},
      require = 0
   )
   private float skycraft$hostWaterHeight(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
      float height = SkyWater.active() ? SkyWater.substitutedHeight(level, pos, 0.3) : -1.0F;
      return height >= 0.0F ? height : (Float)original.call(new Object[]{state, level, pos});
   }
}
