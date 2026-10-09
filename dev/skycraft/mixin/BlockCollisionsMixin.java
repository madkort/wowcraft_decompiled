package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyDig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BlockCollisions.class})
public abstract class BlockCollisionsMixin {
   @WrapOperation(
      method = {"computeNext"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
)}
   )
   private VoxelShape skycraft$addSkyrimShape(CollisionContext context, BlockState state, CollisionGetter level, BlockPos pos, Operation<VoxelShape> original) {
      VoxelShape blockShape = (VoxelShape)original.call(new Object[]{context, state, level, pos});
      if (state.isAir()) {
         VoxelShape wall = SkyDig.wallShape(level, pos);
         if (wall != null) {
            blockShape = blockShape.isEmpty() ? wall : Shapes.or(blockShape, wall);
         }
      }

      if (context instanceof EntityCollisionContext entityContext) {
         if (SkyCollision.usesSmoothCollider(entityContext.getEntity())) {
            return blockShape;
         }
      }

      VoxelShape sky = SkyCollision.shapeAt(pos);
      if (sky == null) {
         return blockShape;
      } else {
         return blockShape.isEmpty() ? sky : Shapes.or(blockShape, sky);
      }
   }
}
