package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyWater;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({FishingHook.class})
public abstract class FishingHookMixin {
   @WrapOperation(
      method = {"tick"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
)}
   )
   private FluidState skycraft$wowWater(Level level, BlockPos pos, Operation<FluidState> original) {
      FluidState state = (FluidState)original.call(new Object[]{level, pos});
      if (state.isEmpty() && SkyWater.active()) {
         FluidState water = SkyWater.fluidAt(level, pos);
         if (water != null) {
            return water;
         }
      }

      return state;
   }

   @WrapOperation(
      method = {"tick"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
)}
   )
   private float skycraft$wowWaterHeight(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
      float height = SkyWater.active() ? SkyWater.substitutedHeight(level, pos) : -1.0F;
      return height >= 0.0F ? height : (Float)original.call(new Object[]{state, level, pos});
   }

   @WrapOperation(
      method = {"getOpenWaterTypeForBlock"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
)}
   )
   private BlockState skycraft$wowOpenWater(Level level, BlockPos pos, Operation<BlockState> original) {
      BlockState state = (BlockState)original.call(new Object[]{level, pos});
      return state.isAir() && SkyWater.active() && SkyWater.fluidAt(level, pos) != null ? Blocks.WATER.defaultBlockState() : state;
   }

   @WrapOperation(
      method = {"catchingFish"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
)}
   )
   private BlockState skycraft$wowWaterSplash(ServerLevel level, BlockPos pos, Operation<BlockState> original) {
      BlockState state = (BlockState)original.call(new Object[]{level, pos});
      return state.isAir() && SkyWater.active() && SkyWater.fluidAt(level, pos) != null ? Blocks.WATER.defaultBlockState() : state;
   }
}
