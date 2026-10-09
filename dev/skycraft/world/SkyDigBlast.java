package dev.skycraft.world;

import dev.skycraft.WowCraftConfig;
import dev.skycraft.link.SkyLink;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SkyDigBlast {
   private static final float BUILDING_RESISTANCE = 1200.0F;
   private static final double[] SAMPLE = new double[]{0.2, (double)0.5F, 0.8};
   private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
   private final ServerLevel level;
   private final int world;
   private final SkyDig.Probe probe;
   private final Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
   private final List<BlockPos> opened = new ArrayList();

   private SkyDigBlast(ServerLevel level, int world, Vec3 center, float radius) {
      this.level = level;
      this.world = world;
      double reach = (double)radius * 1.4 + (double)2.0F;
      this.probe = (new SkyDig.Probe()).around(center.x - reach, center.y - reach, center.z - reach, center.x + reach, center.y + reach, center.z + reach);
      this.cells.defaultReturnValue(Integer.MIN_VALUE);
   }

   public static @Nullable SkyDigBlast begin(ServerExplosion explosion) {
      Vec3 c = explosion.center();
      if (!WowCraftConfig.on("explosions_dig", "WOWCRAFT_EXPLOSIONS_DIG")) {
         return null;
      } else {
         return SkyDig.destruction && SkyCollision.active() && SkyCollision.isKnown((int)Math.floor(c.x), (int)Math.floor(c.y), (int)Math.floor(c.z)) && SkyLink.readSkyState(SKY) ? new SkyDigBlast(explosion.level(), SKY.worldId, c, explosion.radius()) : null;
      }
   }

   private int material(BlockPos pos) {
      long key = pos.asLong();
      int known = this.cells.get(key);
      if (known != Integer.MIN_VALUE) {
         return known;
      } else {
         int result = 0;
         if (this.level.getBlockState(pos).isAir() && !SkyDig.isDug(this.level, this.world, pos)) {
            for(double sy : SAMPLE) {
               for(double sz : SAMPLE) {
                  for(double sx : SAMPLE) {
                     int r = this.probe.test((double)pos.getX() + sx, (double)pos.getY() + sy, (double)pos.getZ() + sz);
                     if (r == -1) {
                        result = -1;
                        break;
                     }

                     if (r > 0 && result == 0) {
                        result = SkyDig.underground(r, Math.max(this.probe.depth, (double)0.5F));
                     }
                  }
               }
            }
         }

         this.cells.put(key, result);
         return result;
      }
   }

   public Optional<Float> resistance(BlockPos pos, Optional<Float> vanilla) {
      if (vanilla.isPresent()) {
         return vanilla;
      } else {
         int m = this.material(pos);
         if (m == -1) {
            return Optional.of(1200.0F);
         } else {
            return m > 0 ? Optional.of(SkyDig.materialState(m).getBlock().getExplosionResistance()) : vanilla;
         }
      }
   }

   public void materialize(List<BlockPos> targets, boolean breaksBlocks) {
      if (breaksBlocks) {
         for(BlockPos pos : targets) {
            int m = this.material(pos);
            if (m > 0) {
               SkyDig.markDug(this.level, this.world, pos);
               this.level.setBlock(pos, SkyDig.blockFor(this.level, pos, m), 18);
               this.opened.add(pos.immutable());
            }
         }

      }
   }

   public void finish() {
      if (!this.opened.isEmpty()) {
         LongOpenHashSet done = new LongOpenHashSet();

         for(BlockPos pos : this.opened) {
            done.add(pos.asLong());
         }

         for(BlockPos pos : this.opened) {
            for(Direction d : Direction.values()) {
               BlockPos n = pos.relative(d);
               if (done.add(n.asLong()) && !SkyDig.isDug(this.level, this.world, n)) {
                  int material = SkyDig.classify(n.getX(), n.getY(), n.getZ());
                  if (material > 0) {
                     SkyDig.digCell(this.level, this.world, n, material);
                  }
               }
            }
         }

      }
   }
}
