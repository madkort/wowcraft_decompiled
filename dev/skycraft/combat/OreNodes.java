package dev.skycraft.combat;

import dev.skycraft.SkyCraft;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.CoopNet;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class OreNodes {
   private static final Kind[] KINDS;
   private static final long TAKEN_HOLD_MS = 60000L;
   private static final double LISTED_RANGE = (double)36.0F;
   private static final int MAX_SIDE = 4;
   private static final int MAX_UP = 3;
   private static final Map<Integer, Placed> PLACED;
   private static final Map<Integer, Long> TAKEN;
   private static final Set<Integer> CLEANED;
   private static final Set<Long> HIDDEN;
   private static volatile List<Marker> markers;
   private static volatile Set<Long> REMOTE_HIDDEN;
   private static final Item[] HERB_CROPS;

   public static List<Marker> markers() {
      return markers;
   }

   public static boolean hidden(BlockPos pos) {
      long l = pos.asLong();
      return !HIDDEN.isEmpty() && HIDDEN.contains(l) || !REMOTE_HIDDEN.isEmpty() && REMOTE_HIDDEN.contains(l);
   }

   public static Set<Long> setRemoteHidden(long[] packed) {
      Set<Long> next = new HashSet();

      for(long l : packed) {
         next.add(l);
      }

      Set<Long> changed = new HashSet(next);
      changed.addAll(REMOTE_HIDDEN);
      Set<Long> both = new HashSet(next);
      both.retainAll(REMOTE_HIDDEN);
      changed.removeAll(both);
      REMOTE_HIDDEN = next;
      return changed;
   }

   private OreNodes() {
   }

   private static Map.@Nullable Entry<Integer, Placed> at(BlockPos pos) {
      for(Map.Entry<Integer, Placed> e : PLACED.entrySet()) {
         if (((Placed)e.getValue()).blocks().contains(pos)) {
            return e;
         }
      }

      return null;
   }

   private static Path nodesFile(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("wowcraft-nodes.dat");
   }

   private static void loadNodes(MinecraftServer server) {
      PLACED.clear();
      HIDDEN.clear();
      CLEANED.clear();
      Path file = nodesFile(server);

      try {
         if (!Files.exists(file, new LinkOption[0])) {
            return;
         }

         ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file));
         int count = b.getInt();

         for(int i = 0; i < count && b.remaining() >= 20; ++i) {
            int id = b.getInt();
            int kind = b.getInt();
            BlockPos primary = BlockPos.of(b.getLong());
            int n = b.getInt();
            List<BlockPos> blocks = new ArrayList(n);

            for(int k = 0; k < n && b.remaining() >= 8; ++k) {
               blocks.add(BlockPos.of(b.getLong()));
            }

            if (kind >= 0 && kind < KINDS.length) {
               Kind k = KINDS[kind];
               if ((k.use() != OreNodes.Use.STATION || blocks.size() >= 9) && (!k.herb() || blocks.size() >= 3)) {
                  PLACED.put(id, new Placed(blocks, primary, k));
                  CLEANED.add(id);

                  for(BlockPos pos : blocks) {
                     HIDDEN.add(pos.asLong());
                  }
               }
            }
         }

         SkyCraft.LOG.info("WowCraft: {} placed nodes from the world", PLACED.size());
      } catch (BufferUnderflowException | IOException e) {
         SkyCraft.LOG.warn("WowCraft: couldn't read {}", file, e);
      }

   }

   private static void saveNodes(MinecraftServer server) {
      Path file = nodesFile(server);
      List<Map.Entry<Integer, Placed>> nodes = new ArrayList(PLACED.entrySet());
      int size = 4;

      for(Map.Entry<Integer, Placed> e : nodes) {
         size += 20 + 8 * ((Placed)e.getValue()).blocks().size();
      }

      ByteBuffer b = ByteBuffer.allocate(size);
      b.putInt(nodes.size());

      for(Map.Entry<Integer, Placed> e : nodes) {
         Placed p = (Placed)e.getValue();
         int kind = Arrays.asList(KINDS).indexOf(p.kind());
         b.putInt((Integer)e.getKey()).putInt(kind).putLong(p.primary().asLong()).putInt(p.blocks().size());

         for(BlockPos pos : p.blocks()) {
            b.putLong(pos.asLong());
         }
      }

      try {
         Files.write(file, b.array(), new OpenOption[0]);
      } catch (IOException e) {
         SkyCraft.LOG.warn("WowCraft: couldn't write {}", file, e);
      }

   }

   public static void init() {
      ServerLifecycleEvents.SERVER_STARTING.register(OreNodes::loadNodes);
      ServerLifecycleEvents.SERVER_STOPPING.register(OreNodes::saveNodes);
      ServerTickEvents.END_SERVER_TICK.register((ServerTickEvents.EndTick)(server) -> {
         if (server.getTickCount() % 1200 == 0) {
            server.execute(() -> saveNodes(server));
         }

      });
      PlayerBlockBreakEvents.BEFORE.register((PlayerBlockBreakEvents.Before)(world, player, pos, state, blockEntity) -> {
         Map.Entry<Integer, Placed> e = at(pos);
         if (e != null && ((Placed)e.getValue()).kind().herb() && world instanceof ServerLevel level) {
            herbDrops(level, pos, ((Placed)e.getValue()).kind());
            clear(level, (Placed)e.getValue(), (BlockPos)null);
            PLACED.remove(e.getKey());
            int var10000 = (Integer)e.getKey();
            Kind var10001 = ((Placed)e.getValue()).kind();
            ServerPlayer var10002;
            if (player instanceof ServerPlayer sp) {
               var10002 = sp;
            } else {
               var10002 = null;
            }

            taken(var10000, var10001, var10002);
            return false;
         } else {
            return e == null || ((Placed)e.getValue()).kind().use() == OreNodes.Use.MINED || ((Placed)e.getValue()).kind().use() == OreNodes.Use.CHEST && pos.equals(((Placed)e.getValue()).primary());
         }
      });
      PlayerBlockBreakEvents.AFTER.register((PlayerBlockBreakEvents.After)(world, player, pos, state, blockEntity) -> {
         Map.Entry<Integer, Placed> e = at(pos);
         if (e != null) {
            clear((ServerLevel)world, (Placed)e.getValue(), pos);
            PLACED.remove(e.getKey());
            if (((Placed)e.getValue()).kind().use() == OreNodes.Use.MINED) {
               for(int i = 0; i < 2; ++i) {
                  Block.dropResources(state, world, pos, (BlockEntity)null, player, player.getMainHandItem());
               }
            }

            int var10000 = (Integer)e.getKey();
            Kind var10001 = ((Placed)e.getValue()).kind();
            ServerPlayer var10002;
            if (player instanceof ServerPlayer) {
               ServerPlayer sp = (ServerPlayer)player;
               var10002 = sp;
            } else {
               var10002 = null;
            }

            taken(var10000, var10001, var10002);
         }
      });
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hit) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         } else {
            Map.Entry<Integer, Placed> e = at(hit.getBlockPos());
            if (e != null && !hit.getBlockPos().equals(((Placed)e.getValue()).primary()) && player instanceof ServerPlayer) {
               ServerPlayer sp = (ServerPlayer)player;
               SkyrimActorEntity trader = SkyCombat.traderInSight(sp);
               if (trader != null) {
                  BlockTrader.open(sp, trader);
                  return InteractionResult.SUCCESS;
               }
            }

            if (e != null && ((Placed)e.getValue()).kind().use() != OreNodes.Use.MINED && !hit.getBlockPos().equals(((Placed)e.getValue()).primary())) {
               BlockPos primary = ((Placed)e.getValue()).primary();
               return world.getBlockState(primary).useWithoutItem(world, player, new BlockHitResult(Vec3.atCenterOf(primary), hit.getDirection(), primary, false));
            } else {
               return InteractionResult.PASS;
            }
         }
      });
   }

   private static void herbDrops(ServerLevel level, BlockPos pos, Kind kind) {
      RandomSource random = level.getRandom();
      int kinds = 2 + random.nextInt(2);

      for(int i = 0; i < kinds; ++i) {
         Item item = HERB_CROPS[random.nextInt(HERB_CROPS.length)];
         Block.popResource(level, pos, new ItemStack(item, 1 + random.nextInt(3)));
      }

      if (random.nextFloat() < 0.15F) {
         Block.popResource(level, pos, new ItemStack(kind.block()));
      }

   }

   private static void clear(ServerLevel level, Placed p, @Nullable BlockPos except) {
      for(BlockPos pos : p.blocks()) {
         HIDDEN.remove(pos.asLong());
         if (!pos.equals(except) && !level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
         }
      }

   }

   private static void taken(int id, Kind kind) {
      taken(id, kind, (ServerPlayer)null);
   }

   private static void taken(int id, Kind kind, @Nullable ServerPlayer by) {
      TAKEN.put(id, System.currentTimeMillis() + 60000L);
      CoopNet.event(by, 6, id, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0);
      SkyCraft.LOG.info("WowCraft: took {} ({})", Integer.toHexString(id), kind.block().getName().getString());
   }

   static void sync(ServerLevel level, List<SkyLink.Actor> actors, List<ServerPlayer> players) {
      long now = System.currentTimeMillis();
      TAKEN.values().removeIf((untilx) -> now > untilx + 60000L);
      Map<Integer, SkyLink.Actor> listed = new HashMap();

      for(SkyLink.Actor a : actors) {
         if ((a.flags() & 256) != 0 && a.level() >= 0 && a.level() < KINDS.length) {
            listed.put(a.formId(), a);
         }
      }

      Iterator<Map.Entry<Integer, Placed>> it = PLACED.entrySet().iterator();

      while(it.hasNext()) {
         Map.Entry<Integer, Placed> e = (Map.Entry)it.next();
         Placed p = (Placed)e.getValue();
         if (level.isLoaded(p.primary())) {
            if (p.kind().use() == OreNodes.Use.STATION && !level.getBlockState(p.primary()).is(p.kind().block())) {
               HIDDEN.add(p.primary().asLong());
               level.setBlock(p.primary(), p.kind().block().defaultBlockState(), 2);
            }

            if (p.kind().use() == OreNodes.Use.CHEST) {
               BlockEntity var10 = level.getBlockEntity(p.primary());
               if (var10 instanceof RandomizableContainerBlockEntity) {
                  RandomizableContainerBlockEntity chest = (RandomizableContainerBlockEntity)var10;
                  if (chest.getLootTable() == null && chest.isEmpty()) {
                     clear(level, p, (BlockPos)null);
                     taken((Integer)e.getKey(), p.kind());
                     it.remove();
                     continue;
                  }
               }
            }

            if (!listed.containsKey(e.getKey()) && players.stream().anyMatch((pl) -> p.primary().distToCenterSqr(pl.getX(), pl.getY(), pl.getZ()) < (double)1296.0F)) {
               clear(level, p, (BlockPos)null);
               it.remove();
            }
         }
      }

      for(SkyLink.Actor a : listed.values()) {
         if (!PLACED.containsKey(a.formId()) && level.isLoaded(BlockPos.containing((double)a.x(), (double)a.y(), (double)a.z()))) {
            Long until = (Long)TAKEN.get(a.formId());
            if (until == null || now >= until) {
               Kind kind = KINDS[a.level()];
               if (kind.use() == OreNodes.Use.TREE) {
                  BlockPos trunk = BlockPos.containing((double)a.x(), (double)a.y() + 0.3, (double)a.z());

                  for(int i = 0; i < 8; ++i) {
                     if (level.getBlockState(trunk.above(i)).is(kind.block())) {
                        level.removeBlock(trunk.above(i), false);
                     }
                  }

                  TAKEN.put(a.formId(), now + 3600000L);
               } else {
                  double sx = Math.max((double)a.width(), (double)0.5F);
                  double sy = Math.max((double)a.height(), (double)0.5F);
                  double sz = Math.max((double)a.healthFrac(), (double)0.5F);
                  double cx = (double)a.x() + sx * (double)0.5F;
                  double cz = (double)a.z() + sz * (double)0.5F;
                  BlockPos primary = BlockPos.containing(cx, (double)a.y() + 0.3, cz);
                  if (CLEANED.add(a.formId())) {
                     for(BlockPos pos : BlockPos.betweenClosed(primary.offset(-3, -1, -3), primary.offset(3, 3, 3))) {
                        BlockState s = level.getBlockState(pos);
                        boolean retired = kind.block() == Blocks.SMOKER && s.is(Blocks.CAMPFIRE);
                        if (!hidden(pos) && (s.is(kind.block()) || s.is(kind.placed()) || s.is(Blocks.BARRIER) || retired)) {
                           level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                     }
                  }

                  boolean herb = kind.herb();
                  List<BlockPos> volume = new ArrayList();
                  if (herb) {
                     volume.add(primary);
                     volume.add(primary.above());

                     for(Direction side : Plane.HORIZONTAL) {
                        volume.add(primary.relative(side));
                     }
                  } else {
                     double inset = kind.use() == OreNodes.Use.MINED ? (double)0.25F : (double)0.0F;
                     double top = kind.use() == OreNodes.Use.MINED ? 0.7 : (double)1.0F;
                     int x0 = Mth.floor((double)a.x() + sx * inset);
                     int x1 = Math.min(Mth.floor((double)a.x() + sx * ((double)1.0F - inset) - 0.05), x0 + 4 - 1);
                     int z0 = Mth.floor((double)a.z() + sz * inset);
                     int z1 = Math.min(Mth.floor((double)a.z() + sz * ((double)1.0F - inset) - 0.05), z0 + 4 - 1);
                     int y0 = primary.getY();
                     int y1 = Math.min(Mth.floor((double)a.y() + sy * top - 0.3), y0 + 3 - 1);

                     for(BlockPos pos : BlockPos.betweenClosed(new BlockPos(x0, y0, z0), new BlockPos(x1, Math.max(y0, y1), z1))) {
                        volume.add(pos.immutable());
                     }

                     if (!volume.contains(primary)) {
                        volume.add(primary);
                     }

                     if (kind.use() == OreNodes.Use.STATION) {
                        for(BlockPos pos : BlockPos.betweenClosed(primary.offset(-1, 0, -1), primary.offset(1, 1, 1))) {
                           if (!volume.contains(pos)) {
                              volume.add(pos.immutable());
                           }
                        }
                     }

                     if (kind.block() instanceof AbstractChestBlock) {
                        volume.remove(primary.above());
                     }
                  }

                  List<BlockPos> blocks = new ArrayList();

                  for(BlockPos pos : volume) {
                     BlockState here = level.getBlockState(pos);
                     boolean ours = here.is(kind.block()) || here.is(kind.placed()) || here.is(Blocks.BARRIER);
                     if (here.isAir() || here.canBeReplaced() || ours) {
                        Block block = kind.use() != OreNodes.Use.MINED && !pos.equals(primary) ? Blocks.BARRIER : kind.placed();
                        HIDDEN.add(pos.asLong());
                        if (ours) {
                           level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }

                        level.setBlock(pos, block.defaultBlockState(), 2);
                        blocks.add(pos);
                     }
                  }

                  if (!blocks.isEmpty() && blocks.contains(primary)) {
                     if (kind.use() == OreNodes.Use.CHEST) {
                        BlockEntity var57 = level.getBlockEntity(primary);
                        if (var57 instanceof RandomizableContainerBlockEntity) {
                           RandomizableContainerBlockEntity chest = (RandomizableContainerBlockEntity)var57;
                           chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, level.getRandom().nextLong());
                        }
                     }

                     PLACED.put(a.formId(), new Placed(blocks, primary, kind));
                  } else {
                     for(BlockPos pos : blocks) {
                        HIDDEN.remove(pos.asLong());
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                     }
                  }
               }
            }
         }
      }

      List<Marker> next = new ArrayList();

      for(Map.Entry<Integer, Placed> e : PLACED.entrySet()) {
         next.add(new Marker((Integer)e.getKey(), ((Placed)e.getValue()).primary(), ((Placed)e.getValue()).kind().block()));
      }

      markers = List.copyOf(next);
      CoopNet.shareHidden(level.getServer(), hiddenNow(), false);
   }

   public static long[] hiddenNow() {
      return HIDDEN.stream().mapToLong(Long::longValue).sorted().toArray();
   }

   static {
      KINDS = new Kind[]{new Kind(Blocks.COPPER_ORE, OreNodes.Use.MINED), new Kind(Blocks.COAL_ORE, OreNodes.Use.MINED), new Kind(Blocks.IRON_ORE, OreNodes.Use.MINED), new Kind(Blocks.LAPIS_ORE, OreNodes.Use.MINED), new Kind(Blocks.GOLD_ORE, OreNodes.Use.MINED), new Kind(Blocks.REDSTONE_ORE, OreNodes.Use.MINED), new Kind(Blocks.DIAMOND_ORE, OreNodes.Use.MINED), new Kind(Blocks.EMERALD_ORE, OreNodes.Use.MINED), new Kind(Blocks.ANCIENT_DEBRIS, OreNodes.Use.MINED), new Kind(Blocks.POPPY, OreNodes.Use.MINED), new Kind(Blocks.AZURE_BLUET, OreNodes.Use.MINED), new Kind(Blocks.ALLIUM, OreNodes.Use.MINED), new Kind(Blocks.CORNFLOWER, OreNodes.Use.MINED), new Kind(Blocks.OXEYE_DAISY, OreNodes.Use.MINED), new Kind(Blocks.BROWN_MUSHROOM, OreNodes.Use.MINED), new Kind(Blocks.RED_TULIP, OreNodes.Use.MINED), new Kind(Blocks.LILY_OF_THE_VALLEY, OreNodes.Use.MINED), new Kind(Blocks.DANDELION, OreNodes.Use.MINED), new Kind(Blocks.BLUE_ORCHID, OreNodes.Use.MINED), new Kind(Blocks.WHITE_TULIP, OreNodes.Use.MINED), new Kind(Blocks.ORANGE_TULIP, OreNodes.Use.MINED), new Kind(Blocks.WITHER_ROSE, OreNodes.Use.MINED), new Kind(Blocks.DEAD_BUSH, OreNodes.Use.MINED), new Kind(Blocks.CRIMSON_FUNGUS, OreNodes.Use.MINED), new Kind(Blocks.RED_MUSHROOM, OreNodes.Use.MINED), new Kind(Blocks.PINK_TULIP, OreNodes.Use.MINED), new Kind(Blocks.TORCHFLOWER, OreNodes.Use.MINED), new Kind(Blocks.FERN, OreNodes.Use.MINED), new Kind(Blocks.ANVIL, OreNodes.Use.STATION), new Kind(Blocks.FURNACE, OreNodes.Use.STATION), new Kind(Blocks.SMOKER, OreNodes.Use.STATION), new Kind(Blocks.ENDER_CHEST, OreNodes.Use.STATION), new Kind(Blocks.CHEST, OreNodes.Use.CHEST), new Kind(Blocks.OAK_LOG, OreNodes.Use.TREE), new Kind(Blocks.SPRUCE_LOG, OreNodes.Use.TREE), new Kind(Blocks.BIRCH_LOG, OreNodes.Use.TREE), new Kind(Blocks.DARK_OAK_LOG, OreNodes.Use.TREE), new Kind(Blocks.ACACIA_LOG, OreNodes.Use.TREE), new Kind(Blocks.JUNGLE_LOG, OreNodes.Use.TREE), new Kind(Blocks.MANGROVE_LOG, OreNodes.Use.TREE)};
      PLACED = new HashMap();
      TAKEN = new HashMap();
      CLEANED = new HashSet();
      HIDDEN = ConcurrentHashMap.newKeySet();
      markers = List.of();
      REMOTE_HIDDEN = Set.of();
      HERB_CROPS = new Item[]{Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.SWEET_BERRIES, Items.SUGAR_CANE};
   }

   private static enum Use {
      MINED,
      STATION,
      CHEST,
      TREE;

      // $FF: synthetic method
      private static Use[] $values() {
         return new Use[]{MINED, STATION, CHEST, TREE};
      }
   }

   private static record Kind(Block block, Use use) {
      boolean herb() {
         return this.use == OreNodes.Use.MINED && !this.block.defaultBlockState().isSolid();
      }

      Block placed() {
         return this.herb() ? Blocks.SHORT_GRASS : this.block;
      }
   }

   private static record Placed(List<BlockPos> blocks, BlockPos primary, Kind kind) {
   }

   public static record Marker(int id, BlockPos pos, Block block) {
   }
}
