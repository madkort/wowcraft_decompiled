package dev.skycraft.client;

import dev.skycraft.client.render.WorldExporter;
import dev.skycraft.net.SkyNet;
import dev.skycraft.world.SkyClip;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyDig;
import dev.skycraft.world.SkyRay;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public final class SkyDigClient {
   private static final int REVEAL_RANGE = 24;
   private static final int REVEAL_TRIES = 60;
   private static @Nullable BlockPos mining;
   private static float progress;
   private static int miningTick;
   private static int hits;
   private static int ticks;
   private static final Long2IntLinkedOpenHashMap REVEAL = new Long2IntLinkedOpenHashMap();
   private static final Long2ObjectOpenHashMap<SkyDig.DugColumn> SEEN = new Long2ObjectOpenHashMap();
   private static final SkyDig.DugColumn NONE = new SkyDig.DugColumn(List.of());
   private static int seenWorld = Integer.MIN_VALUE;
   private static int pollTicks;

   private SkyDigClient() {
   }

   public static int world() {
      return SkyClient.sky().worldId;
   }

   public static boolean attack(Minecraft minecraft) {
      HitResult var2 = minecraft.hitResult;
      if (var2 instanceof SkyClip.SkyrimHitResult result) {
         if (minecraft.level != null && minecraft.player != null && minecraft.gameMode != null) {
            GameType mode = minecraft.gameMode.getPlayerMode();
            if (mode != GameType.ADVENTURE && mode != GameType.SPECTATOR && (SkyDig.destruction || !minecraft.hasSingleplayerServer())) {
               SkyRay.Hit hit = result.hit;
               if (hit.tri() != null && hit.tri().diggable) {
                  int[] cell = SkyRay.surfaceCell(hit);
                  BlockPos pos = new BlockPos(cell[0], cell[1], cell[2]);
                  BlockState here = minecraft.level.getBlockState(pos);
                  if (!here.isAir() && !here.canBeReplaced()) {
                     return false;
                  }

                  if (SkyDig.isDug(minecraft.level, world(), pos)) {
                     return false;
                  }

                  if (pos.equals(mining) && miningTick == ticks) {
                     return true;
                  }

                  if (!pos.equals(mining) || ticks - miningTick > 2) {
                     mining = pos;
                     progress = 0.0F;
                     hits = 0;
                  }

                  miningTick = ticks;
                  int material = hit.tri().material == 0 ? 3 : hit.tri().material;
                  BlockState state = SkyDig.materialState(material);
                  progress += minecraft.player.getAbilities().instabuild ? 1.0F : state.getDestroyProgress(minecraft.player, minecraft.level, pos);
                  if (hits++ % 4 == 0) {
                     SoundType sound = state.getSoundType();
                     minecraft.getSoundManager().play(new SimpleSoundInstance(sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F, SoundInstance.createUnseededRandom(), pos));
                     minecraft.particleEngine.add((new TerrainParticle(minecraft.level, hit.x() + hit.nx() * 0.05, hit.y() + hit.ny() * 0.05, hit.z() + hit.nz() * 0.05, (double)0.0F, (double)0.0F, (double)0.0F, state, pos)).setPower(0.2F).scale(0.6F));
                  }

                  if (progress >= 1.0F) {
                     ClientPlayNetworking.send(new SkyNet.DigOpen(world(), pos, material));
                     REVEAL.put(pos.asLong(), 60);
                     mining = null;
                     progress = 0.0F;
                  }

                  return true;
               }

               return false;
            }

            return false;
         }
      }

      return false;
   }

   public static void blockChanged(ClientLevel level, BlockPos pos, BlockState before, BlockState after) {
      if (!before.isAir() && (after.isAir() || after.canBeReplaced())) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.player != null && !(minecraft.player.blockPosition().distSqr(pos) > (double)576.0F)) {
            if (SkyDig.isDug(level, world(), pos) && (SkyDig.destruction || !minecraft.hasSingleplayerServer())) {
               REVEAL.put(pos.asLong(), 60);
            }

         }
      }
   }

   public static void tick(Minecraft minecraft) {
      ++ticks;
      ClientLevel level = minecraft.level;
      if (level != null && minecraft.player != null) {
         int world = world();
         SkyDig.clientWorld = world;
         SkyDig.clientDug = (x, y, z) -> SkyDig.isDug(level, world, new BlockPos(x, y, z));
         reveal(level);
         if (++pollTicks % 5 == 0) {
            pollDug(minecraft, level);
         }

         SkyCollision.takeChangedRegions((key) -> {
            int rx = BlockPos.getX(key);
            int ry = BlockPos.getY(key);
            int rz = BlockPos.getZ(key);
            SkyDig.wallsChanged(rx, ry, rz, rx + 7, ry + 7, rz + 7);

            for(int sx = rx - 1 >> 4; sx <= rx + 8 >> 4; ++sx) {
               for(int sz = rz - 1 >> 4; sz <= rz + 8 >> 4; ++sz) {
                  LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
                  if (chunk != null) {
                     for(int sy = ry - 1 >> 4; sy <= ry + 8 >> 4; ++sy) {
                        if (dugBits(chunk, sy) != null) {
                           WorldExporter.markDirty(sx, sy, sz);
                        }
                     }
                  }
               }
            }

         });
      } else {
         REVEAL.clear();
         SEEN.clear();
         SkyDig.clientDug = null;
      }
   }

   private static void reveal(ClientLevel level) {
      if (!REVEAL.isEmpty()) {
         int world = world();
         List<BlockPos> cells = new ArrayList();
         List<Integer> materials = new ArrayList();
         LongOpenHashSet queued = new LongOpenHashSet();
         long[] keys = REVEAL.keySet().toLongArray();

         for(long key : keys) {
            BlockPos pos = BlockPos.of(key);
            boolean waiting = false;

            for(Direction d : Direction.values()) {
               BlockPos n = pos.relative(d);
               if (!queued.contains(n.asLong()) && !SkyDig.isDug(level, world, n)) {
                  if (!SkyCollision.isKnown(n.getX(), n.getY(), n.getZ())) {
                     waiting = true;
                  } else {
                     int material = SkyDig.classify(n.getX(), n.getY(), n.getZ());
                     if (material > 0) {
                        BlockState state = level.getBlockState(n);
                        if (!state.isAir() && !state.canBeReplaced()) {
                           material = 0;
                        }

                        queued.add(n.asLong());
                        cells.add(n);
                        materials.add(material);
                     }
                  }
               }
            }

            int tries = REVEAL.get(key) - 1;
            if (waiting && tries > 0) {
               REVEAL.put(key, tries);
            } else {
               REVEAL.remove(key);
            }
         }

         for(int i = 0; i < cells.size(); i += 64) {
            int end = Math.min(cells.size(), i + 64);
            ClientPlayNetworking.send(new SkyNet.DigReveal(world, List.copyOf(cells.subList(i, end)), List.copyOf(materials.subList(i, end))));
         }

      }
   }

   public static long @Nullable [] dugBits(LevelChunk chunk, int sectionY) {
      SkyDig.DugColumn column = (SkyDig.DugColumn)chunk.getAttached(SkyDig.DUG);
      return column == null ? null : column.bits(world(), sectionY);
   }

   public static void resendAll() {
      SEEN.clear();
   }

   private static void pollDug(Minecraft minecraft, ClientLevel level) {
      int world = world();
      boolean worldChanged = world != seenWorld;
      seenWorld = world;
      if (worldChanged) {
         SkyDig.wallsChanged();
      }

      int radius = minecraft.options.getEffectiveRenderDistance() + 1;
      int pcx = minecraft.player.getBlockX() >> 4;
      int pcz = minecraft.player.getBlockZ() >> 4;

      for(int cx = pcx - radius; cx <= pcx + radius; ++cx) {
         for(int cz = pcz - radius; cz <= pcz + radius; ++cz) {
            long key = ChunkPos.pack(cx, cz);
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) {
               SEEN.remove(key);
            } else {
               SkyDig.DugColumn column = (SkyDig.DugColumn)chunk.getAttached(SkyDig.DUG);
               if (column == null) {
                  column = NONE;
               }

               SkyDig.DugColumn previous = (SkyDig.DugColumn)SEEN.get(key);
               if (previous != column || worldChanged) {
                  SEEN.put(key, column);

                  for(SkyDig.DugColumn c : new SkyDig.DugColumn[]{column, previous == null ? NONE : previous}) {
                     for(SkyDig.DugSection s : c.sections()) {
                        if (worldChanged || s.world() == world) {
                           WorldExporter.markDirtyNow(cx, s.sectionY(), cz);
                           WorldExporter.markDirty(cx - 1, s.sectionY(), cz);
                           WorldExporter.markDirty(cx + 1, s.sectionY(), cz);
                           WorldExporter.markDirty(cx, s.sectionY() - 1, cz);
                           WorldExporter.markDirty(cx, s.sectionY() + 1, cz);
                           WorldExporter.markDirty(cx, s.sectionY(), cz - 1);
                           WorldExporter.markDirty(cx, s.sectionY(), cz + 1);
                        }
                     }
                  }
               }
            }
         }
      }

      if (SEEN.size() > 8192) {
         SEEN.clear();
      }

   }
}
