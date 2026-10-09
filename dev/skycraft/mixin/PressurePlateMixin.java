package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({PressurePlateBlock.class, WeightedPressurePlateBlock.class})
public abstract class PressurePlateMixin {
   @Unique
   private static final AABB SKYCRAFT$TOUCH = new AABB((double)0.0625F, (double)0.0F, (double)0.0625F, (double)0.9375F, (double)1.0F, (double)0.9375F);

   @WrapOperation(
      method = {"getSignalStrength"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/phys/AABB;move(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;"
)}
   )
   private AABB skycraft$wholeBlock(AABB touch, BlockPos pos, Operation<AABB> original) {
      return (AABB)original.call(new Object[]{SKYCRAFT$TOUCH, pos});
   }
}
