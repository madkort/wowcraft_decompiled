package dev.skycraft.mixin;

import dev.skycraft.SkyCraft;
import dev.skycraft.world.SkyCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FlowingFluid.class})
public abstract class FlowingFluidMixin {
   private static final float FALLING_SURFACE = 0.8888889F;
   private static final float MARGIN = 0.05F;
   private static long skycraft$lastLog;
   private static int skycraft$logged;

   @Inject(
      method = {"canPassThroughWall"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void skycraft$skyrimWall(Direction direction, BlockGetter level, BlockPos sourcePos, BlockState sourceState, BlockPos targetPos, BlockState targetState, CallbackInfoReturnable<Boolean> cir) {
      if (targetState.isAir() && SkyCollision.active() && direction != Direction.UP) {
         if (!SkyCollision.isKnown(targetPos.getX(), targetPos.getY(), targetPos.getZ())) {
            refused("unknown region", direction, sourcePos, sourceState, targetPos, 0.0F);
            cir.setReturnValue(false);
         } else if (direction == Direction.DOWN) {
            if (SkyCollision.hasGeometry(sourcePos)) {
               refused("resting on ground", direction, sourcePos, sourceState, targetPos, SkyCollision.groundTop(sourcePos));
               cir.setReturnValue(false);
            } else {
               float top = SkyCollision.groundTop(targetPos);
               if (top >= 0.8388889F) {
                  refused("ground below", direction, sourcePos, sourceState, targetPos, top);
                  cir.setReturnValue(false);
               }

            }
         } else if (!SkyCollision.hasGeometry(targetPos)) {
            if (SkyCollision.hasGeometry(targetPos.above())) {
               refused("under ground", direction, sourcePos, sourceState, targetPos, 1.0F);
               cir.setReturnValue(false);
            }

         } else {
            FluidState fluid = sourceState.getFluidState();
            float surface;
            if (fluid.isEmpty()) {
               surface = 0.8F;
            } else if ((Boolean)fluid.getValue(FlowingFluid.FALLING)) {
               surface = 0.7777778F;
            } else {
               int drop = fluid.is(FluidTags.LAVA) ? 2 : 1;
               surface = (float)Math.max(0, fluid.getAmount() - drop) / 9.0F;
            }

            float top = SkyCollision.groundTop(targetPos);
            boolean flatOrDownhill = top <= SkyCollision.groundTop(sourcePos) + 0.13F;
            if (!flatOrDownhill && top >= surface - 0.05F) {
               refused("ground beside", direction, sourcePos, sourceState, targetPos, top);
               cir.setReturnValue(false);
            }

         }
      }
   }

   private static void refused(String why, Direction direction, BlockPos sourcePos, BlockState sourceState, BlockPos targetPos, float ground) {
      long now = System.currentTimeMillis();
      if (now - skycraft$lastLog > 1000L) {
         skycraft$lastLog = now;
         skycraft$logged = 0;
      }

      if (skycraft$logged++ < 4) {
         FluidState fluid = sourceState.getFluidState();
         SkyCraft.LOG.info("SkyCraft: fluid flow refused ({}): {} -> {} going {}, fluid {} amount {}, Skyrim ground height there {}", new Object[]{why, sourcePos.toShortString(), targetPos.toShortString(), direction, fluid.getType(), fluid.getAmount(), String.format("%.2f", ground)});
      }

   }
}
