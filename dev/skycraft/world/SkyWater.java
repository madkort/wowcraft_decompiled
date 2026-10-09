package dev.skycraft.world;

import dev.skycraft.link.SkyLink;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

public final class SkyWater {
   private static volatile @Nullable Grid grid;
   private static final int REMEMBERED = 65536;
   private static final Map<Long, Float> SEEN = Collections.synchronizedMap(new LinkedHashMap<Long, Float>(4096, 0.75F, true) {
      protected boolean removeEldestEntry(Map.Entry<Long, Float> eldest) {
         return this.size() > 65536;
      }
   });
   private static final double LAVA_OFFSET = (double)100000.0F;

   private static long key(int x, int z) {
      return (long)x << 32 ^ (long)z & 4294967295L;
   }

   private SkyWater() {
   }

   public static void refresh() {
      SkyLink.WaterGrid read = SkyLink.readWaterGrid();
      if (read != null) {
         int var10002 = read.originX;
         int var10003 = read.originZ;
         Objects.requireNonNull(read);
         Grid fresh = new Grid(var10002, var10003, 16, read.surface);
         Grid old = grid;
         if (old != null && Math.abs(old.originX() - fresh.originX()) + Math.abs(old.originZ() - fresh.originZ()) > 256) {
            SEEN.clear();
         }

         if (old == null || old.originX() != fresh.originX() || old.originZ() != fresh.originZ() || !Arrays.equals(old.surface(), fresh.surface())) {
            for(int dz = 0; dz < fresh.size(); ++dz) {
               for(int dx = 0; dx < fresh.size(); ++dx) {
                  float v = fresh.surface()[dz * fresh.size() + dx];
                  SEEN.put(key(fresh.originX() + dx, fresh.originZ() + dz), v < -1.0E20F ? Float.NaN : v);
               }
            }
         }

         grid = fresh;
      }

   }

   public static void clear() {
      grid = null;
      SEEN.clear();
   }

   public static boolean active() {
      return grid != null;
   }

   public static double surfaceAt(int x, int z) {
      double raw = rawAt(x, z);
      return raw > (double)50000.0F ? raw - (double)100000.0F : raw;
   }

   public static boolean lavaAt(int x, int z) {
      return rawAt(x, z) > (double)50000.0F;
   }

   private static double rawAt(int x, int z) {
      Grid g = grid;
      if (g == null) {
         return Double.NaN;
      } else {
         int dx = x - g.originX();
         int dz = z - g.originZ();
         if (dx >= 0 && dz >= 0 && dx < g.size() && dz < g.size()) {
            float s = g.surface()[dz * g.size() + dx];
            return s < -1.0E20F ? Double.NaN : (double)s;
         } else {
            Float seen = (Float)SEEN.get(key(x, z));
            return seen == null ? Double.NaN : (double)seen;
         }
      }
   }

   public static float depthIn(BlockPos pos) {
      return depthIn(pos, (double)0.0F);
   }

   public static float depthIn(BlockPos pos, double lift) {
      double s = surfaceAt(pos.getX(), pos.getZ()) + lift;
      if (Double.isNaN(s)) {
         return 0.0F;
      } else {
         double h = s - (double)pos.getY();
         return h < 0.02 ? 0.0F : (float)Math.min((double)1.0F, h);
      }
   }

   public static boolean anyIn(int x0, int y0, int z0, int x1, int y1, int z1) {
      if (grid == null) {
         return false;
      } else {
         for(int x = x0; x <= x1; ++x) {
            for(int z = z0; z <= z1; ++z) {
               double s = surfaceAt(x, z);
               if (!Double.isNaN(s) && s > (double)y0) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public static @Nullable FluidState fluidAt(BlockGetter level, BlockPos pos) {
      return fluidAt(level, pos, (double)0.0F);
   }

   public static @Nullable FluidState fluidAt(BlockGetter level, BlockPos pos, double lift) {
      if (!(depthIn(pos, lift) <= 0.0F) && level.getBlockState(pos).isAir()) {
         return lavaAt(pos.getX(), pos.getZ()) ? Fluids.LAVA.getSource(false) : Fluids.WATER.getSource(false);
      } else {
         return null;
      }
   }

   public static float substitutedHeight(BlockGetter level, BlockPos pos) {
      return substitutedHeight(level, pos, (double)0.0F);
   }

   public static float substitutedHeight(BlockGetter level, BlockPos pos, double lift) {
      float depth = depthIn(pos, lift);
      return !(depth <= 0.0F) && level.getFluidState(pos).isEmpty() && level.getBlockState(pos).isAir() ? depth : -1.0F;
   }

   private static record Grid(int originX, int originZ, int size, float[] surface) {
   }
}
