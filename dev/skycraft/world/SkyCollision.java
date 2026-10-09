package dev.skycraft.world;

import dev.skycraft.SkyCraft;
import dev.skycraft.link.SkyLink;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class SkyCollision {
   public static final int REGION_SIZE = 8;
   private static final ConcurrentHashMap<Long, VoxelShape> SHAPES = new ConcurrentHashMap();
   private static final ConcurrentHashMap<Long, Integer> FILL = new ConcurrentHashMap();
   private static final int FILL_LOWER = 1024;
   private static final int FILL_UPPER = 2048;
   private static final int FILL_TOP_SHIFT = 12;
   private static final ConcurrentHashMap<Long, SkyTri[]> TRIS = new ConcurrentHashMap();
   private static final ConcurrentHashMap<Long, SkyTri[]> GHOSTS = new ConcurrentHashMap();
   private static final ConcurrentHashMap<Long, Long> TRI_HASH = new ConcurrentHashMap();
   private static final ConcurrentLinkedQueue<Long> CHANGED = new ConcurrentLinkedQueue();
   private static volatile Predicate<Entity> smoothCollider = (e) -> false;
   private static final Set<Long> KNOWN_REGIONS = ConcurrentHashMap.newKeySet();
   private static volatile int epoch = -1;
   private static Thread consumer;

   public static void takeChangedRegions(LongConsumer out) {
      Long key;
      while((key = (Long)CHANGED.poll()) != null) {
         out.accept(key);
      }

   }

   private SkyCollision() {
   }

   public static @Nullable VoxelShape shapeAt(BlockPos pos) {
      return SHAPES.isEmpty() ? null : (VoxelShape)SHAPES.get(pos.asLong());
   }

   public static void setSmoothCollider(Predicate<Entity> predicate) {
      smoothCollider = predicate;
   }

   public static boolean usesSmoothCollider(@Nullable Entity entity) {
      return entity != null && smoothCollider.test(entity);
   }

   public static void trianglesNear(AABB box, List<SkyTri> out) {
      if (!TRIS.isEmpty()) {
         int rx0 = Math.floorDiv((int)Math.floor(box.minX), 8);
         int rx1 = Math.floorDiv((int)Math.floor(box.maxX), 8);
         int ry0 = Math.floorDiv((int)Math.floor(box.minY), 8);
         int ry1 = Math.floorDiv((int)Math.floor(box.maxY), 8);
         int rz0 = Math.floorDiv((int)Math.floor(box.minZ), 8);
         int rz1 = Math.floorDiv((int)Math.floor(box.maxZ), 8);

         for(int rx = rx0; rx <= rx1; ++rx) {
            for(int ry = ry0; ry <= ry1; ++ry) {
               for(int rz = rz0; rz <= rz1; ++rz) {
                  SkyTri[] tris = (SkyTri[])TRIS.get(regionKey(rx, ry, rz));
                  if (tris != null) {
                     for(SkyTri t : tris) {
                        if (t.maxX >= box.minX && t.minX <= box.maxX && t.maxY >= box.minY && t.minY <= box.maxY && t.maxZ >= box.minZ && t.minZ <= box.maxZ) {
                           out.add(t);
                        }
                     }
                  }
               }
            }
         }

      }
   }

   public static boolean hasRegionAt(double x, double y, double z) {
      return TRIS.containsKey(regionKey(Math.floorDiv((int)Math.floor(x), 8), Math.floorDiv((int)Math.floor(y), 8), Math.floorDiv((int)Math.floor(z), 8)));
   }

   public static void originalSurfacesNear(AABB box, List<SkyTri> out) {
      trianglesNear(box, out);
      near(GHOSTS, box, out);
   }

   private static void near(ConcurrentHashMap<Long, SkyTri[]> store, AABB box, List<SkyTri> out) {
      if (!store.isEmpty()) {
         int rx0 = Math.floorDiv((int)Math.floor(box.minX), 8);
         int rx1 = Math.floorDiv((int)Math.floor(box.maxX), 8);
         int ry0 = Math.floorDiv((int)Math.floor(box.minY), 8);
         int ry1 = Math.floorDiv((int)Math.floor(box.maxY), 8);
         int rz0 = Math.floorDiv((int)Math.floor(box.minZ), 8);
         int rz1 = Math.floorDiv((int)Math.floor(box.maxZ), 8);

         for(int rx = rx0; rx <= rx1; ++rx) {
            for(int ry = ry0; ry <= ry1; ++ry) {
               for(int rz = rz0; rz <= rz1; ++rz) {
                  SkyTri[] tris = (SkyTri[])store.get(regionKey(rx, ry, rz));
                  if (tris != null) {
                     for(SkyTri t : tris) {
                        if (t.maxX >= box.minX && t.minX <= box.maxX && t.maxY >= box.minY && t.minY <= box.maxY && t.maxZ >= box.minZ && t.minZ <= box.maxZ) {
                           out.add(t);
                        }
                     }
                  }
               }
            }
         }

      }
   }

   public static boolean isKnown(int x, int y, int z) {
      return KNOWN_REGIONS.contains(regionKey(Math.floorDiv(x, 8), Math.floorDiv(y, 8), Math.floorDiv(z, 8)));
   }

   public static boolean hasSolidBelow(int x, int y, int z, int depth) {
      for(int dy = 0; dy <= depth; ++dy) {
         for(int dx = -1; dx <= 1; ++dx) {
            for(int dz = -1; dz <= 1; ++dz) {
               if (SHAPES.containsKey(BlockPos.asLong(x + dx, y - dy, z + dz))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static float solidFraction(BlockPos pos) {
      Integer fill = FILL.isEmpty() ? null : (Integer)FILL.get(pos.asLong());
      return fill == null ? 0.0F : (float)(fill & 1023) / 512.0F;
   }

   public static boolean hasGeometry(BlockPos pos) {
      return !FILL.isEmpty() && FILL.containsKey(pos.asLong());
   }

   public static float groundTop(BlockPos pos) {
      Integer fill = FILL.isEmpty() ? null : (Integer)FILL.get(pos.asLong());
      return fill == null ? 0.0F : (float)((fill >> 12 & 7) + 1) / 8.0F;
   }

   public static boolean supportsFromBelow(BlockPos pos) {
      if (FILL.isEmpty()) {
         return false;
      } else {
         Integer here = (Integer)FILL.get(pos.asLong());
         if (here != null && (here & 1024) != 0) {
            return true;
         } else {
            Integer below = (Integer)FILL.get(BlockPos.asLong(pos.getX(), pos.getY() - 1, pos.getZ()));
            return below != null && (below & 2048) != 0;
         }
      }
   }

   public static int blockCount() {
      return SHAPES.size();
   }

   public static int regionCount() {
      return KNOWN_REGIONS.size();
   }

   public static boolean active() {
      return !KNOWN_REGIONS.isEmpty();
   }

   private static long regionKey(int rx, int ry, int rz) {
      return BlockPos.asLong(rx, ry, rz);
   }

   public static synchronized void startConsumer() {
      if (consumer == null) {
         consumer = new Thread(SkyCollision::consumeLoop, "SkyCraft collision");
         consumer.setDaemon(true);
         consumer.start();
      }
   }

   private static void consumeLoop() {
      while(true) {
         try {
            if (!drainOnce()) {
               Thread.sleep(2L);
            }
         } catch (InterruptedException var3) {
            return;
         } catch (Throwable t) {
            SkyCraft.LOG.error("SkyCraft: collision consumer error", t);

            try {
               Thread.sleep(500L);
            } catch (InterruptedException var2) {
               return;
            }
         }
      }
   }

   private static boolean drainOnce() {
      MemorySegment s = SkyLink.segment();
      if (s == null) {
         return false;
      } else {
         long head = SkyLink.collisionHead();
         long tail = SkyLink.collisionTail();
         if (tail >= head) {
            return false;
         } else {
            long data = 131200L;

            while(tail < head) {
               long pos = tail % 33554304L;
               int type = s.get(ValueLayout.JAVA_INT, data + pos);
               int payloadBytes = s.get(ValueLayout.JAVA_INT, data + pos + 4L);
               if (type == 0) {
                  tail += 33554304L - pos;
               } else {
                  long payload = data + pos + 8L;
                  switch (type) {
                     case 1 -> clear(s.get(ValueLayout.JAVA_INT, payload));
                     case 2 -> readRegion(s, payload);
                     case 3 -> readTris(s, payload);
                     default -> SkyCraft.LOG.warn("SkyCraft: unknown collision message {}", type);
                  }

                  tail += align8((long)(8 + payloadBytes));
               }
            }

            SkyLink.setCollisionTail(tail);
            return true;
         }
      }
   }

   private static long align8(long v) {
      return v + 7L & -8L;
   }

   private static void adoptEpochIfFresh(int msgEpoch) {
      if (epoch == -1) {
         epoch = msgEpoch;
         SkyCraft.LOG.info("SkyCraft: joined collision epoch {} already in progress", msgEpoch);
      }

   }

   private static void clear(int newEpoch) {
      SHAPES.clear();
      FILL.clear();
      TRIS.clear();
      GHOSTS.clear();
      TRI_HASH.clear();
      KNOWN_REGIONS.clear();
      epoch = newEpoch;
      SkyCraft.LOG.info("SkyCraft: collision cleared (epoch {})", newEpoch);
   }

   private static void readRegion(MemorySegment s, long p) {
      int minX = s.get(ValueLayout.JAVA_INT, p);
      int minY = s.get(ValueLayout.JAVA_INT, p + 4L);
      int minZ = s.get(ValueLayout.JAVA_INT, p + 8L);
      int maxX = s.get(ValueLayout.JAVA_INT, p + 12L);
      int maxY = s.get(ValueLayout.JAVA_INT, p + 16L);
      int maxZ = s.get(ValueLayout.JAVA_INT, p + 20L);
      int msgEpoch = s.get(ValueLayout.JAVA_INT, p + 24L);
      int count = s.get(ValueLayout.JAVA_INT, p + 28L);
      adoptEpochIfFresh(msgEpoch);
      if (msgEpoch == epoch) {
         HashMap<Long, VoxelShape> fresh = new HashMap(count * 2);
         HashMap<Long, Integer> freshFill = new HashMap(count * 2);
         long e = p + 32L;

         for(int i = 0; i < count; e += 80L) {
            int x = s.get(ValueLayout.JAVA_INT, e);
            int y = s.get(ValueLayout.JAVA_INT, e + 4L);
            int z = s.get(ValueLayout.JAVA_INT, e + 8L);
            VoxelShape shape = buildShape(s, e + 16L);
            if (shape != null) {
               long key = BlockPos.asLong(x, y, z);
               fresh.put(key, shape);
               freshFill.put(key, fillInfo(s, e + 16L));
            }

            ++i;
         }

         for(int x = minX; x <= maxX; ++x) {
            for(int y = minY; y <= maxY; ++y) {
               for(int z = minZ; z <= maxZ; ++z) {
                  long key = BlockPos.asLong(x, y, z);
                  VoxelShape shape = (VoxelShape)fresh.get(key);
                  if (shape != null) {
                     SHAPES.put(key, shape);
                     FILL.put(key, (Integer)freshFill.get(key));
                  } else {
                     SHAPES.remove(key);
                     FILL.remove(key);
                  }
               }
            }
         }

         for(int rx = Math.floorDiv(minX, 8); rx <= Math.floorDiv(maxX, 8); ++rx) {
            for(int ry = Math.floorDiv(minY, 8); ry <= Math.floorDiv(maxY, 8); ++ry) {
               for(int rz = Math.floorDiv(minZ, 8); rz <= Math.floorDiv(maxZ, 8); ++rz) {
                  KNOWN_REGIONS.add(regionKey(rx, ry, rz));
               }
            }
         }

      }
   }

   private static void readTris(MemorySegment s, long p) {
      int minX = s.get(ValueLayout.JAVA_INT, p);
      int minY = s.get(ValueLayout.JAVA_INT, p + 4L);
      int minZ = s.get(ValueLayout.JAVA_INT, p + 8L);
      int msgEpoch = s.get(ValueLayout.JAVA_INT, p + 24L);
      int count = s.get(ValueLayout.JAVA_INT, p + 28L);
      adoptEpochIfFresh(msgEpoch);
      if (msgEpoch == epoch) {
         SkyTri[] tris = new SkyTri[count];
         List<SkyTri> ghosts = new ArrayList();
         float[] v = new float[9];
         int kept = 0;
         long hash = (long)count;
         long e = p + 32L;

         for(int i = 0; i < count; e += 40L) {
            for(int k = 0; k < 9; ++k) {
               v[k] = s.get(ValueLayout.JAVA_FLOAT, e + (long)k * 4L);
               hash = hash * 31L + (long)Float.floatToRawIntBits(v[k]);
            }

            int flags = s.get(ValueLayout.JAVA_INT, e + 36L);
            hash = hash * 31L + (long)flags;
            SkyTri t = new SkyTri(v, 0, flags);
            if (!t.degenerate()) {
               if ((flags & 4) != 0) {
                  ghosts.add(t);
               } else {
                  tris[kept++] = t;
               }
            }

            ++i;
         }

         long region = regionKey(Math.floorDiv(minX, 8), Math.floorDiv(minY, 8), Math.floorDiv(minZ, 8));
         if (ghosts.isEmpty()) {
            GHOSTS.remove(region);
         } else {
            GHOSTS.put(region, (SkyTri[])ghosts.toArray(new SkyTri[0]));
         }

         TRIS.put(region, (SkyTri[])Arrays.copyOf(tris, kept));
         Long before = (Long)TRI_HASH.put(region, hash);
         if (before == null || before != hash) {
            CHANGED.add(BlockPos.asLong(minX, minY, minZ));
         }

      }
   }

   public static int triangleCount() {
      int n = 0;

      for(SkyTri[] t : TRIS.values()) {
         n += t.length;
      }

      return n;
   }

   private static int fillInfo(MemorySegment s, long bitsOff) {
      int count = 0;
      int info = 0;
      int top = 0;

      for(int y = 0; y < 8; ++y) {
         long layer = s.get(ValueLayout.JAVA_LONG, bitsOff + (long)y * 8L);
         count += Long.bitCount(layer);
         if (layer != 0L) {
            info |= y < 4 ? 1024 : 2048;
            top = y;
         }
      }

      return info | count | top << 12;
   }

   private static @Nullable VoxelShape buildShape(MemorySegment s, long bitsOff) {
      boolean any = false;
      boolean full = true;
      long[] layers = new long[8];

      for(int y = 0; y < 8; ++y) {
         layers[y] = s.get(ValueLayout.JAVA_LONG, bitsOff + (long)y * 8L);
         any |= layers[y] != 0L;
         full &= layers[y] == -1L;
      }

      if (!any) {
         return null;
      } else if (full) {
         return Shapes.block();
      } else {
         BitSetDiscreteVoxelShape discrete = new BitSetDiscreteVoxelShape(8, 8, 8);

         for(int y = 0; y < 8; ++y) {
            long layer = layers[y];

            while(layer != 0L) {
               int bit = Long.numberOfTrailingZeros(layer);
               layer &= layer - 1L;
               discrete.fill(bit & 7, y, bit >>> 3);
            }
         }

         return new CubeVoxelShape(discrete);
      }
   }
}
