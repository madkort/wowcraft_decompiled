package dev.skycraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skycraft.SkyCraft;
import dev.skycraft.link.SkyLink;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.LongStream;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class SkyDig {
   public static final AttachmentType<DugColumn> DUG;
   private static final double REACH = (double)8.0F;
   public static volatile boolean destruction;
   public static final int KEEP = -1;
   public static final int AIR = 0;
   public static final double BEDROCK_DEPTH = (double)5.0F;
   private static final int WHOLE = 25;
   private static final double SEARCH = (double)2.5F;
   private static final double LAND_REACH = (double)16.0F;
   private static final double[] SAMPLE;
   private static final double COLUMN_REACH = (double)96.0F;
   private static final SkyLink.SkyState SERVER_SKY;
   private static volatile int serverWorld;
   private static volatile long serverWorldAt;
   private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, VoxelShape>> WALLS;
   private static final VoxelShape NO_WALL;
   public static volatile int clientWorld;
   public static volatile @Nullable DugLookup clientDug;

   private SkyDig() {
   }

   public static void init() {
      SkyCraft.LOG.info("SkyCraft: digging into Skyrim registered ({})", DUG.identifier());
   }

   public static DugColumn column(LevelChunk chunk) {
      DugColumn column = (DugColumn)chunk.getAttached(DUG);
      return column != null ? column : SkyDig.DugColumn.EMPTY;
   }

   public static boolean isDug(Level level, int world, BlockPos pos) {
      return column(level.getChunkAt(pos)).isDug(world, pos.getX(), pos.getY(), pos.getZ());
   }

   private static boolean inReach(ServerPlayer player, BlockPos pos, double reach) {
      return player.getEyePosition().distanceToSqr((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)0.5F, (double)pos.getZ() + (double)0.5F) <= reach * reach;
   }

   public static void open(ServerPlayer player, int world, BlockPos pos, int material) {
      ServerLevel level = player.level();
      if (destruction && inReach(player, pos, (double)8.0F) && level.isLoaded(pos) && !player.isSpectator()) {
         LevelChunk chunk = level.getChunkAt(pos);
         DugColumn column = column(chunk);
         if (!column.isDug(world, pos.getX(), pos.getY(), pos.getZ())) {
            chunk.setAttached(DUG, column.with(world, pos.getX(), pos.getY(), pos.getZ()));
            BlockState state = materialState(material);
            if (!player.isCreative()) {
               ItemStack tool = player.getMainHandItem();
               ItemStack used = tool.copy();
               boolean harvest = player.hasCorrectToolForDrops(state);
               tool.mineBlock(level, state, pos, player);
               if (harvest) {
                  state.getBlock().playerDestroy(level, player, pos, state, (BlockEntity)null, used);
               }
            }

            level.levelEvent(2001, pos, Block.getId(state));
         }
      }
   }

   public static void reveal(ServerPlayer player, int world, List<BlockPos> cells, int[] materials) {
      if (destruction) {
         ServerLevel level = player.level();

         for(int i = 0; i < cells.size() && i < materials.length; ++i) {
            BlockPos pos = (BlockPos)cells.get(i);
            if (inReach(player, pos, (double)12.0F) && level.isLoaded(pos)) {
               digCell(level, world, pos, materials[i]);
            }
         }

      }
   }

   public static boolean markDug(ServerLevel level, int world, BlockPos pos) {
      LevelChunk chunk = level.getChunkAt(pos);
      DugColumn column = column(chunk);
      if (column.isDug(world, pos.getX(), pos.getY(), pos.getZ())) {
         return false;
      } else {
         chunk.setAttached(DUG, column.with(world, pos.getX(), pos.getY(), pos.getZ()));
         return true;
      }
   }

   public static void digCell(ServerLevel level, int world, BlockPos pos, int material) {
      if (markDug(level, world, pos)) {
         BlockState here = level.getBlockState(pos);
         if (material > 0 && (here.isAir() || here.canBeReplaced())) {
            level.setBlock(pos, blockFor(level, pos, material), 3);
         }

      }
   }

   public static BlockState materialState(int material) {
      BlockState var10000;
      switch (material) {
         case 1:
            var10000 = Blocks.GRASS_BLOCK.defaultBlockState();
            break;
         case 2:
            var10000 = Blocks.DIRT.defaultBlockState();
            break;
         case 3:
         default:
            var10000 = Blocks.STONE.defaultBlockState();
            break;
         case 4:
            var10000 = Blocks.COBBLESTONE.defaultBlockState();
            break;
         case 5:
            var10000 = Blocks.SNOW_BLOCK.defaultBlockState();
            break;
         case 6:
            var10000 = Blocks.PACKED_ICE.defaultBlockState();
            break;
         case 7:
            var10000 = Blocks.SAND.defaultBlockState();
            break;
         case 8:
            var10000 = Blocks.GRAVEL.defaultBlockState();
            break;
         case 9:
            var10000 = Blocks.MUD.defaultBlockState();
            break;
         case 10:
            var10000 = Blocks.OAK_LOG.defaultBlockState();
            break;
         case 11:
            var10000 = Blocks.SPRUCE_LOG.defaultBlockState();
            break;
         case 12:
            var10000 = Blocks.BIRCH_LOG.defaultBlockState();
            break;
         case 13:
            var10000 = Blocks.SPRUCE_PLANKS.defaultBlockState();
            break;
         case 14:
            var10000 = ((Block)Blocks.COPPER_BLOCK.waxed().unaffected()).defaultBlockState();
            break;
         case 15:
            var10000 = Blocks.GLASS.defaultBlockState();
            break;
         case 16:
            var10000 = Blocks.MOSS_BLOCK.defaultBlockState();
            break;
         case 17:
            var10000 = ((Block)Blocks.WOOL.pick(DyeColor.BROWN)).defaultBlockState();
            break;
         case 18:
            var10000 = Blocks.BONE_BLOCK.defaultBlockState();
            break;
         case 19:
            var10000 = Blocks.COBWEB.defaultBlockState();
            break;
         case 20:
            var10000 = ((Block)Blocks.CONCRETE_POWDER.pick(DyeColor.LIGHT_GRAY)).defaultBlockState();
            break;
         case 21:
            var10000 = Blocks.BEDROCK.defaultBlockState();
      }

      return var10000;
   }

   public static BlockState blockFor(ServerLevel level, BlockPos pos, int material) {
      return material != 3 && material != 0 ? materialState(material) : stoneOrOre(level.getSeed(), pos);
   }

   static BlockState stoneOrOre(long seed, BlockPos pos) {
      long cell = mix(seed ^ mix((long)(pos.getX() >> 1) * 73856093L ^ (long)(pos.getY() >> 1) * 19349663L ^ (long)(pos.getZ() >> 1) * 83492791L));
      long block = mix(cell ^ (long)(pos.getX() & 1 | (pos.getY() & 1) << 1 | (pos.getZ() & 1) << 2));
      if ((block & 255L) >= 150L) {
         return Blocks.STONE.defaultBlockState();
      } else {
         int roll = (int)((cell >>> 16) % 10000L);
         roll -= 330;
         if (roll < 0) {
            return Blocks.COAL_ORE.defaultBlockState();
         } else {
            roll -= 220;
            if (roll < 0) {
               return Blocks.IRON_ORE.defaultBlockState();
            } else {
               roll -= 170;
               if (roll < 0) {
                  return Blocks.COPPER_ORE.defaultBlockState();
               } else {
                  roll -= 80;
                  if (roll < 0) {
                     return Blocks.REDSTONE_ORE.defaultBlockState();
                  } else {
                     roll -= 45;
                     if (roll < 0) {
                        return Blocks.GOLD_ORE.defaultBlockState();
                     } else {
                        roll -= 35;
                        if (roll < 0) {
                           return Blocks.LAPIS_ORE.defaultBlockState();
                        } else {
                           roll -= 14;
                           if (roll < 0) {
                              return Blocks.DIAMOND_ORE.defaultBlockState();
                           } else {
                              roll -= 5;
                              return roll < 0 ? Blocks.EMERALD_ORE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static long mix(long z) {
      z = (z ^ z >>> 30) * -4658895280553007687L;
      z = (z ^ z >>> 27) * -7723592293110705685L;
      return z ^ z >>> 31;
   }

   public static int classify(int x, int y, int z) {
      Probe probe = (new Probe()).around((double)x, (double)y, (double)z, (double)(x + 1), (double)(y + 1), (double)(z + 1));
      int inside = 0;
      int centreMaterial = 3;
      double centreDepth = Double.POSITIVE_INFINITY;

      for(double sy : SAMPLE) {
         for(double sz : SAMPLE) {
            for(double sx : SAMPLE) {
               int r = probe.test((double)x + sx, (double)y + sy, (double)z + sz);
               if (r == -1) {
                  return -1;
               }

               if (r != 0) {
                  ++inside;
                  if (sx == (double)0.5F && sy == (double)0.5F && sz == (double)0.5F) {
                     centreMaterial = r;
                     centreDepth = probe.depth;
                  }
               }
            }
         }
      }

      if (inside < 25) {
         return 0;
      } else {
         SkyTri land = landAbove((double)x + (double)0.5F, (double)y + (double)0.5F, (double)z + (double)0.5F);
         if (land != null) {
            double depth = land.heightAt((double)x + (double)0.5F, (double)z + (double)0.5F) - ((double)y + (double)0.5F);
            if (depth >= (double)5.0F) {
               return 21;
            }

            if (Double.isInfinite(centreDepth)) {
               centreMaterial = land.material == 0 ? 3 : land.material;
               centreDepth = depth;
            }
         }

         return underground(centreMaterial, centreDepth);
      }
   }

   public static @Nullable SkyTri landAbove(double x, double y, double z) {
      List<SkyTri> column = new ArrayList();
      SkyCollision.originalSurfacesNear(new AABB(x - 0.01, y, z - 0.01, x + 0.01, y + (double)32.0F, z + 0.01), column);
      SkyTri best = null;
      double bestHeight = Double.POSITIVE_INFINITY;

      for(SkyTri t : column) {
         if (t.terrain) {
            double h = t.heightAt(x, z);
            if (!Double.isNaN(h) && h >= y && h < bestHeight) {
               bestHeight = h;
               best = t;
            }
         }
      }

      return best;
   }

   public static int underground(int surface, double depth) {
      int var10000;
      switch (surface) {
         case 1:
         case 2:
            var10000 = depth < (double)3.5F ? 2 : 3;
            break;
         case 3:
         default:
            var10000 = 3;
            break;
         case 4:
            var10000 = depth < (double)1.5F ? 4 : 3;
            break;
         case 5:
            var10000 = depth < (double)1.5F ? 5 : (depth < (double)3.5F ? 2 : 3);
            break;
         case 6:
            var10000 = depth < (double)2.5F ? 6 : 3;
            break;
         case 7:
            var10000 = depth < (double)3.5F ? 7 : 3;
            break;
         case 8:
            var10000 = depth < (double)2.5F ? 8 : 3;
            break;
         case 9:
            var10000 = depth < (double)3.5F ? 9 : 3;
            break;
         case 10:
         case 11:
         case 12:
            var10000 = surface;
            break;
         case 13:
            var10000 = depth < (double)2.5F ? 13 : 3;
            break;
         case 14:
            var10000 = depth < (double)1.5F ? 14 : 3;
            break;
         case 15:
         case 16:
         case 17:
         case 18:
         case 19:
            var10000 = depth < (double)1.0F ? surface : 3;
            break;
         case 20:
            var10000 = depth < (double)3.5F ? 20 : 3;
      }

      return var10000;
   }

   public static void wallsChanged() {
      WALLS.clear();
   }

   public static void wallsChanged(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      int reach = (int)Math.ceil((double)2.5F) + 1;
      int down = reach + (int)Math.ceil((double)16.0F);

      for(int sx = minX - reach >> 4; sx <= maxX + reach >> 4; ++sx) {
         for(int sy = minY - down >> 4; sy <= maxY + reach >> 4; ++sy) {
            for(int sz = minZ - reach >> 4; sz <= maxZ + reach >> 4; ++sz) {
               WALLS.remove(SectionPos.asLong(sx, sy, sz));
            }
         }
      }

   }

   private static int worldFor(Level level) {
      if (level.isClientSide()) {
         DugLookup lookup = clientDug;
         return lookup != null ? clientWorld : 0;
      } else {
         long now = System.currentTimeMillis();
         if (now - serverWorldAt > 250L) {
            synchronized(SERVER_SKY) {
               if (SkyLink.readSkyState(SERVER_SKY)) {
                  serverWorld = SERVER_SKY.worldId;
               }
            }

            serverWorldAt = now;
         }

         return serverWorld;
      }
   }

   private static @Nullable DugColumn columnFor(CollisionGetter level, int x, int z) {
      BlockGetter chunk = level.getChunkForCollisions(x >> 4, z >> 4);
      DugColumn var10000;
      if (chunk instanceof LevelChunk lc) {
         var10000 = (DugColumn)lc.getAttached(DUG);
      } else {
         var10000 = null;
      }

      return var10000;
   }

   public static @Nullable VoxelShape wallShape(CollisionGetter getter, BlockPos pos) {
      if (getter instanceof Level level) {
         if (SkyCollision.active()) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            DugColumn here = columnFor(getter, x, z);
            int world = worldFor(level);
            if (here != null && here.isDug(world, x, y, z)) {
               return null;
            }

            boolean nextToDug = false;

            for(Direction d : Direction.values()) {
               DugColumn column = d.getAxis() == Axis.Y ? here : columnFor(getter, x + d.getStepX(), z + d.getStepZ());
               if (column != null && column.isDug(world, x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())) {
                  nextToDug = true;
                  break;
               }
            }

            if (!nextToDug) {
               return null;
            }

            if (WALLS.size() > 2048) {
               WALLS.clear();
            }

            ConcurrentHashMap<Long, VoxelShape> shapes = (ConcurrentHashMap)WALLS.computeIfAbsent(SectionPos.asLong(x >> 4, y >> 4, z >> 4), (k) -> new ConcurrentHashMap());
            long key = pos.asLong();
            VoxelShape cached = (VoxelShape)shapes.get(key);
            if (cached != null) {
               return cached == NO_WALL ? null : cached;
            }

            VoxelShape shape = computeWall(x, y, z);
            shapes.put(key, shape == null ? NO_WALL : shape);
            return shape;
         }
      }

      return null;
   }

   private static @Nullable VoxelShape computeWall(int x, int y, int z) {
      Probe probe = (new Probe()).around((double)x, (double)y, (double)z, (double)(x + 1), (double)(y + 1), (double)(z + 1));
      if (!probe.land.isEmpty()) {
         double lowest = Double.POSITIVE_INFINITY;

         for(double[] c : new double[][]{{0.05, 0.05}, {0.95, 0.05}, {0.05, 0.95}, {0.95, 0.95}, {(double)0.5F, (double)0.5F}}) {
            double h = probe.landHeight((double)x + c[0], (double)z + c[1]);
            if (!Double.isNaN(h)) {
               lowest = Math.min(lowest, h);
            }
         }

         if (Double.isFinite(lowest) && lowest - (double)y > 0.05) {
            return lowest - (double)y >= 0.999 ? Shapes.block() : Shapes.box((double)0.0F, (double)0.0F, (double)0.0F, (double)1.0F, lowest - (double)y, (double)1.0F);
         }

         if (probe.tris.isEmpty()) {
            return null;
         }
      }

      return probe.test((double)x + (double)0.5F, (double)y + (double)0.5F, (double)z + (double)0.5F) > 0 ? Shapes.block() : null;
   }

   static void closestPoint(SkyTri t, double px, double py, double pz, double[] out) {
      double abx = t.bx - t.ax;
      double aby = t.by - t.ay;
      double abz = t.bz - t.az;
      double acx = t.cx - t.ax;
      double acy = t.cy - t.ay;
      double acz = t.cz - t.az;
      double apx = px - t.ax;
      double apy = py - t.ay;
      double apz = pz - t.az;
      double d1 = abx * apx + aby * apy + abz * apz;
      double d2 = acx * apx + acy * apy + acz * apz;
      if (d1 <= (double)0.0F && d2 <= (double)0.0F) {
         set(out, t.ax, t.ay, t.az);
      } else {
         double bpx = px - t.bx;
         double bpy = py - t.by;
         double bpz = pz - t.bz;
         double d3 = abx * bpx + aby * bpy + abz * bpz;
         double d4 = acx * bpx + acy * bpy + acz * bpz;
         if (d3 >= (double)0.0F && d4 <= d3) {
            set(out, t.bx, t.by, t.bz);
         } else {
            double vc = d1 * d4 - d3 * d2;
            if (vc <= (double)0.0F && d1 >= (double)0.0F && d3 <= (double)0.0F) {
               double v = d1 / (d1 - d3);
               set(out, t.ax + abx * v, t.ay + aby * v, t.az + abz * v);
            } else {
               double cpx = px - t.cx;
               double cpy = py - t.cy;
               double cpz = pz - t.cz;
               double d5 = abx * cpx + aby * cpy + abz * cpz;
               double d6 = acx * cpx + acy * cpy + acz * cpz;
               if (d6 >= (double)0.0F && d5 <= d6) {
                  set(out, t.cx, t.cy, t.cz);
               } else {
                  double vb = d5 * d2 - d1 * d6;
                  if (vb <= (double)0.0F && d2 >= (double)0.0F && d6 <= (double)0.0F) {
                     double w = d2 / (d2 - d6);
                     set(out, t.ax + acx * w, t.ay + acy * w, t.az + acz * w);
                  } else {
                     double va = d3 * d6 - d5 * d4;
                     if (va <= (double)0.0F && d4 - d3 >= (double)0.0F && d5 - d6 >= (double)0.0F) {
                        double w = (d4 - d3) / (d4 - d3 + (d5 - d6));
                        set(out, t.bx + (t.cx - t.bx) * w, t.by + (t.cy - t.by) * w, t.bz + (t.cz - t.bz) * w);
                     } else {
                        double denom = (double)1.0F / (va + vb + vc);
                        double v = vb * denom;
                        double w = vc * denom;
                        set(out, t.ax + abx * v + acx * w, t.ay + aby * v + acy * w, t.az + abz * v + acz * w);
                     }
                  }
               }
            }
         }
      }
   }

   private static void set(double[] out, double x, double y, double z) {
      out[0] = x;
      out[1] = y;
      out[2] = z;
   }

   static {
      DUG = AttachmentRegistry.builder().persistent(SkyDig.DugColumn.CODEC).syncWith(SkyDig.DugColumn.STREAM_CODEC, AttachmentSyncPredicate.all()).buildAndRegister(Identifier.fromNamespaceAndPath("skycraft", "dug"));
      destruction = true;
      SAMPLE = new double[]{0.16666666666666666, (double)0.5F, 0.8333333333333334};
      SERVER_SKY = new SkyLink.SkyState();
      WALLS = new ConcurrentHashMap();
      NO_WALL = Shapes.empty();
   }

   public static record DugSection(int world, int sectionY, long[] bits) {
      static final Codec<DugSection> CODEC = RecordCodecBuilder.create((i) -> i.group(Codec.INT.fieldOf("world").forGetter(DugSection::world), Codec.INT.fieldOf("y").forGetter(DugSection::sectionY), Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream).fieldOf("bits").forGetter(DugSection::bits)).apply(i, DugSection::new));
   }

   public static record DugColumn(List<DugSection> sections) {
      public static final DugColumn EMPTY = new DugColumn(List.of());
      static final Codec<DugColumn> CODEC;
      static final StreamCodec<ByteBuf, DugColumn> STREAM_CODEC;

      public long @Nullable [] bits(int world, int sectionY) {
         for(DugSection s : this.sections) {
            if (s.world == world && s.sectionY == sectionY && s.bits.length == 64) {
               return s.bits;
            }
         }

         return null;
      }

      public boolean isDug(int world, int x, int y, int z) {
         long[] bits = this.bits(world, y >> 4);
         int bit = (x & 15) + 16 * (z & 15) + 256 * (y & 15);
         return bits != null && (bits[bit >> 6] >>> (bit & 63) & 1L) != 0L;
      }

      DugColumn with(int world, int x, int y, int z) {
         int sy = y >> 4;
         int bit = (x & 15) + 16 * (z & 15) + 256 * (y & 15);
         List<DugSection> out = new ArrayList(this.sections.size() + 1);
         boolean found = false;

         for(DugSection s : this.sections) {
            if (s.world == world && s.sectionY == sy && s.bits.length == 64) {
               long[] bits = (long[])s.bits.clone();
               bits[bit >> 6] |= 1L << (bit & 63);
               out.add(new DugSection(world, sy, bits));
               found = true;
            } else {
               out.add(s);
            }
         }

         if (!found) {
            long[] bits = new long[64];
            bits[bit >> 6] |= 1L << (bit & 63);
            out.add(new DugSection(world, sy, bits));
         }

         return new DugColumn(List.copyOf(out));
      }

      static {
         CODEC = SkyDig.DugSection.CODEC.listOf().xmap(DugColumn::new, DugColumn::sections);
         STREAM_CODEC = new StreamCodec<ByteBuf, DugColumn>() {
            public SkyDig.DugColumn decode(ByteBuf buf) {
               int n = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
               List<SkyDig.DugSection> sections = new ArrayList(n);

               for(int i = 0; i < n; ++i) {
                  int world = buf.readInt();
                  int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
                  long[] bits = new long[64];

                  for(int k = 0; k < 64; ++k) {
                     bits[k] = buf.readLong();
                  }

                  sections.add(new SkyDig.DugSection(world, y, bits));
               }

               return new SkyDig.DugColumn(List.copyOf(sections));
            }

            public void encode(ByteBuf buf, SkyDig.DugColumn column) {
               ByteBufCodecs.VAR_INT.encode(buf, column.sections.size());

               for(SkyDig.DugSection s : column.sections) {
                  buf.writeInt(s.world);
                  ByteBufCodecs.VAR_INT.encode(buf, s.sectionY);

                  for(int k = 0; k < 64; ++k) {
                     buf.writeLong(s.bits.length == 64 ? s.bits[k] : 0L);
                  }
               }

            }
         };
      }
   }

   public static final class Probe {
      public final List<SkyTri> land = new ArrayList();
      public final List<SkyTri> tris = new ArrayList();
      private final List<SkyTri> gathered = new ArrayList();
      private final double[] q = new double[3];
      public @Nullable SkyTri surface;
      public double depth;
      private double boxMinX;
      private double boxMinY;
      private double boxMinZ;
      private double boxMaxX;
      private double boxMaxY;
      private double boxMaxZ;
      private @Nullable List<SkyTri> column;
      private final Long2ObjectOpenHashMap<List<SkyTri>> columns = new Long2ObjectOpenHashMap();
      private final Long2ObjectOpenHashMap<List<SkyTri>> nearCells = new Long2ObjectOpenHashMap();

      public Probe around(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
         this.gathered.clear();
         this.land.clear();
         this.tris.clear();
         this.column = null;
         this.columns.clear();
         this.nearCells.clear();
         this.boxMinX = minX;
         this.boxMinY = minY;
         this.boxMinZ = minZ;
         this.boxMaxX = maxX;
         this.boxMaxY = maxY;
         this.boxMaxZ = maxZ;
         SkyCollision.originalSurfacesNear(new AABB(minX - (double)2.5F, minY - (double)2.5F, minZ - (double)2.5F, maxX + (double)2.5F, maxY + (double)2.5F + (double)16.0F, maxZ + (double)2.5F), this.gathered);

         for(SkyTri t : this.gathered) {
            if (!t.stairHelper) {
               if (t.terrain) {
                  this.land.add(t);
               } else if (t.maxY >= minY - (double)2.5F && t.minY <= maxY + (double)2.5F) {
                  this.tris.add(t);
               }
            }
         }

         return this;
      }

      public double landHeight(double x, double z) {
         double best = Double.NaN;

         for(SkyTri t : this.land) {
            double h = t.heightAt(x, z);
            if (!Double.isNaN(h) && (Double.isNaN(best) || h > best)) {
               best = h;
               this.surface = t;
            }
         }

         return best;
      }

      public int test(double px, double py, double pz) {
         double ground = this.landHeight(px, pz);
         SkyTri landSurface = this.surface;
         if (!Double.isNaN(ground) && py < ground) {
            this.surface = landSurface;
            this.depth = ground - py;
            return landSurface.material == 0 ? 3 : landSurface.material;
         } else {
            SkyTri nearest = null;
            double best = (double)6.25F;
            double bx = (double)0.0F;
            double by = (double)0.0F;
            double bz = (double)0.0F;

            for(SkyTri t : this.nearCell(px, py, pz)) {
               double ex = Math.max((double)0.0F, Math.max(t.minX - px, px - t.maxX));
               double ey = Math.max((double)0.0F, Math.max(t.minY - py, py - t.maxY));
               double ez = Math.max((double)0.0F, Math.max(t.minZ - pz, pz - t.maxZ));
               if (!(ex * ex + ey * ey + ez * ez >= best)) {
                  SkyDig.closestPoint(t, px, py, pz, this.q);
                  double dx = px - this.q[0];
                  double dy = py - this.q[1];
                  double dz = pz - this.q[2];
                  double d2 = dx * dx + dy * dy + dz * dz;
                  if (d2 < best) {
                     best = d2;
                     nearest = t;
                     bx = this.q[0];
                     by = this.q[1];
                     bz = this.q[2];
                  }
               }
            }

            this.surface = nearest;
            if (nearest == null) {
               this.depth = Double.POSITIVE_INFINITY;
               return this.farFromSurfaces(px, py, pz, ground);
            } else if ((px - bx) * nearest.nx + (py - by) * nearest.ny + (pz - bz) * nearest.nz >= (double)0.0F) {
               return 0;
            } else if (!nearest.diggable) {
               return -1;
            } else {
               this.depth = Math.sqrt(best);
               return nearest.material == 0 ? 3 : nearest.material;
            }
         }
      }

      private int farFromSurfaces(double px, double py, double pz, double ground) {
         boolean landBelow = !Double.isNaN(ground);
         SkyTri above = null;
         double aboveY = Double.POSITIVE_INFINITY;

         for(SkyTri t : this.columnAt(px, pz)) {
            double h = t.heightAt(px, pz);
            if (!Double.isNaN(h)) {
               if (h > py) {
                  if (h < aboveY) {
                     aboveY = h;
                     above = t;
                  }
               } else if (t.terrain) {
                  landBelow = true;
               }
            }
         }

         if (above != null && above.ny > (double)0.0F) {
            this.surface = above;
            this.depth = aboveY - py;
            if (!above.diggable) {
               return -1;
            } else {
               return above.material == 0 ? 3 : above.material;
            }
         } else {
            return above == null && !landBelow ? 3 : 0;
         }
      }

      private List<SkyTri> nearCell(double px, double py, double pz) {
         int cx = (int)Math.floor(px);
         int cy = (int)Math.floor(py);
         int cz = (int)Math.floor(pz);
         long key = BlockPos.asLong(cx, cy, cz);
         List<SkyTri> list = (List)this.nearCells.get(key);
         if (list == null) {
            list = new ArrayList();

            for(SkyTri t : this.tris) {
               if (t.maxX >= (double)cx - (double)2.5F && t.minX <= (double)(cx + 1) + (double)2.5F && t.maxY >= (double)cy - (double)2.5F && t.minY <= (double)(cy + 1) + (double)2.5F && t.maxZ >= (double)cz - (double)2.5F && t.minZ <= (double)(cz + 1) + (double)2.5F) {
                  list.add(t);
               }
            }

            this.nearCells.put(key, list);
         }

         return list;
      }

      private List<SkyTri> columnAt(double x, double z) {
         if (this.column == null) {
            List<SkyTri> all = new ArrayList();
            SkyCollision.originalSurfacesNear(new AABB(Math.min(x, this.boxMinX), this.boxMinY - (double)96.0F, Math.min(z, this.boxMinZ), Math.max(x, this.boxMaxX), this.boxMaxY + (double)96.0F, Math.max(z, this.boxMaxZ)), all);
            this.column = new ArrayList();

            for(SkyTri t : all) {
               if (!t.stairHelper && Math.abs(t.ny) >= 0.05) {
                  this.column.add(t);
               }
            }
         }

         int bx = (int)Math.floor(x);
         int bz = (int)Math.floor(z);
         long key = (long)bx << 32 | (long)bz & 4294967295L;
         List<SkyTri> list = (List)this.columns.get(key);
         if (list == null) {
            list = new ArrayList();

            for(SkyTri t : this.column) {
               if (t.maxX >= (double)bx && t.minX <= (double)(bx + 1) && t.maxZ >= (double)bz && t.minZ <= (double)(bz + 1)) {
                  list.add(t);
               }
            }

            this.columns.put(key, list);
         }

         return list;
      }
   }

   public interface DugLookup {
      boolean isDug(int var1, int var2, int var3);
   }
}
