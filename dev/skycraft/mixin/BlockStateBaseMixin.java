package dev.skycraft.mixin;

import dev.skycraft.combat.OreNodes;
import dev.skycraft.world.SkyCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockBehaviour.BlockStateBase.class})
public abstract class BlockStateBaseMixin {
   @Inject(
      method = {"getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$herbTarget(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
      if (((BlockBehaviour.BlockStateBase)this).is(Blocks.SHORT_GRASS) && OreNodes.hidden(pos)) {
         cir.setReturnValue(Shapes.block());
      }

   }

   @Inject(
      method = {"isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/SupportType;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$skyrimIsSturdy(BlockGetter level, BlockPos pos, Direction direction, SupportType type, CallbackInfoReturnable<Boolean> cir) {
      if (((BlockBehaviour.BlockStateBase)this).isAir()) {
         boolean sturdy = direction == Direction.UP ? SkyCollision.supportsFromBelow(pos.above()) : SkyCollision.solidFraction(pos) >= 0.4F;
         if (sturdy) {
            cir.setReturnValue(true);
         }

      }
   }
}
