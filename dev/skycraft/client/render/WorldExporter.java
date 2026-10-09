package dev.skycraft.client.render;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skycraft.SkyCraft;
import dev.skycraft.client.SkyDigClient;
import dev.skycraft.client.mixin.ClientLevelAccessor;
import dev.skycraft.combat.OreNodes;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyClip;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyDig;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3fc;

public final class WorldExporter {
   private static final int SECTIONS_PER_FRAME = 12;
   private static final double ENTITY_RANGE = (double)96.0F;
   private static final LongLinkedOpenHashSet DIRTY = new LongLinkedOpenHashSet();
   private static final LongOpenHashSet SENT = new LongOpenHashSet();
   private static final LongOpenHashSet LIT = new LongOpenHashSet();
   private static final LongOpenHashSet SOLID = new LongOpenHashSet();
   private static final long[] SOLID_BITS = new long[64];
   private static final long[] CUT_BITS = new long[64];
   private static final LongOpenHashSet CUT = new LongOpenHashSet();
   private static long animationFrames;
   private static final LongOpenHashSet DUG = new LongOpenHashSet();
   private static final ByteBuffer LIGHTS;
   private static int sentGeneration;
   private static int meshesSent;
   private static ClientLevel sentLevel;
   private static SkyAtlas atlas;
   private static ModelBlockRenderer blockRenderer;
   private static FluidRenderer fluidRenderer;
   private static final MeshBuilder MESH;
   private static final List<SkyLink.WorldEntity> ENTITIES;
   private static final ItemStackRenderState ITEM_STATE;
   private static final Map<Item, float[]> ICONS;
   private static final Map<BlockState, float[]> CUBE_FACES;
   private static final RandomSource RANDOM;
   private static final double MARKER_RANGE = (double)10.0F;

   private WorldExporter() {
   }

   public static void markDirty(int sx, int sy, int sz) {
      synchronized(DIRTY) {
         DIRTY.add(SectionPos.asLong(sx, sy, sz));
      }
   }

   public static void markDirtyNow(int sx, int sy, int sz) {
      synchronized(DIRTY) {
         DIRTY.addAndMoveToFirst(SectionPos.asLong(sx, sy, sz));
      }
   }

   public static void frame(Minecraft minecraft, float partialTick) {
      ClientLevel level = minecraft.level;
      if (level != null && minecraft.player != null && SkyLink.active()) {
         if (sentGeneration != SkyLink.generation() || sentLevel != level || atlas == null || atlas.stale(minecraft)) {
            resendEverything(minecraft, level);
         }

         meshDirtySections(level);
         atlas.animate(System.nanoTime() / 50000000L, (region) -> {
            ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(region.x()).putInt(region.y()).putInt(region.w()).putInt(region.h()).flip();
            boolean sent = SkyLink.tryWriteRender(7, header, region.pixels());
            if (sent && ++animationFrames % 2000L == 1L) {
               SkyCraft.LOG.info("SkyCraft: {} animation frames sent", animationFrames);
            }

            return sent;
         });
         exportEntities(minecraft, level, partialTick);
         AvatarExporter.frame(minecraft, atlas, partialTick);
      }
   }

   private static void resendEverything(Minecraft minecraft, ClientLevel level) {
      sentGeneration = SkyLink.generation();
      sentLevel = level;
      atlas = SkyAtlas.build(minecraft);
      SkyCraft.LOG.info("SkyCraft: {} animated textures (water, lava, fire, ...) will play in Skyrim", atlas.animatedSprites());
      AvatarExporter.reset();
      ICONS.clear();
      CUBE_FACES.clear();
      boolean ao = (Boolean)minecraft.options.ambientOcclusion().get();
      blockRenderer = new ModelBlockRenderer(ao, true, minecraft.getBlockColors());
      fluidRenderer = new FluidRenderer(minecraft.getModelManager().getFluidStateModelSet());
      SkyLink.writeRender(3, ByteBuffer.allocate(0), (ByteBuffer)null);
      ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(atlas.width).putInt(atlas.height).flip();
      boolean ok = SkyLink.writeRender(1, header, atlas.pixels.duplicate().clear());
      SkyCraft.LOG.info("SkyCraft: sent {}x{} texture atlas to Skyrim ({})", new Object[]{atlas.width, atlas.height, ok ? "ok" : "FAILED"});
      SENT.clear();
      LIT.clear();
      SOLID.clear();
      CUT.clear();
      DUG.clear();
      SkyDigClient.resendAll();
      int radius = minecraft.options.getEffectiveRenderDistance() + 1;
      int pcx = SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
      int pcz = SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());

      for(int cx = pcx - radius; cx <= pcx + radius; ++cx) {
         for(int cz = pcz - radius; cz <= pcz + radius; ++cz) {
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk != null) {
               LevelChunkSection[] sections = chunk.getSections();

               for(int i = 0; i < sections.length; ++i) {
                  if (!sections[i].hasOnlyAir()) {
                     markDirty(cx, chunk.getSectionYFromSectionIndex(i), cz);
                  }
               }
            }
         }
      }

   }

   private static void meshDirtySections(ClientLevel level) {
      long deadline = System.nanoTime() + 3000000L;
      int meshed = 0;

      while(meshed < 12 && System.nanoTime() < deadline) {
         long key;
         synchronized(DIRTY) {
            if (DIRTY.isEmpty()) {
               return;
            }

            key = DIRTY.removeFirstLong();
         }

         if (meshSection(level, key)) {
            ++meshed;
         }
      }

   }

   private static LevelChunkSection sectionAt(ClientLevel level, LevelChunk chunk, int sy) {
      int index = level.getSectionIndexFromSectionY(sy);
      return index >= 0 && index < chunk.getSections().length ? chunk.getSections()[index] : null;
   }

   private static boolean meshSection(ClientLevel level, long key) {
      int sx = SectionPos.x(key);
      int sy = SectionPos.y(key);
      int sz = SectionPos.z(key);
      LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
      if (chunk == null) {
         return false;
      } else {
         LevelChunkSection section = sectionAt(level, chunk, sy);
         boolean empty = section == null || section.hasOnlyAir();
         long[] dug = SkyDigClient.dugBits(chunk, sy);
         int dugCount = 0;
         if (dug != null) {
            for(long word : dug) {
               dugCount += Long.bitCount(word);
            }
         }

         if (empty && !SENT.contains(key) && !LIT.contains(key) && !SOLID.contains(key) && dugCount == 0 && !DUG.contains(key)) {
            return false;
         } else {
            MESH.reset();
            LIGHTS.clear();
            int lightCount = 0;
            Arrays.fill(SOLID_BITS, 0L);
            int solidCount = 0;
            Arrays.fill(CUT_BITS, 0L);
            int cutCount = 0;
            MESH.cardinal = level.cardinalLighting();
            if (!empty) {
               BlockPos origin = SectionPos.of(sx, sy, sz).origin();
               BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

               for(int y = 0; y < 16; ++y) {
                  for(int z = 0; z < 16; ++z) {
                     for(int x = 0; x < 16; ++x) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        BlockState state = chunk.getBlockState(pos);
                        if (!state.isAir() && !OreNodes.hidden(pos)) {
                           if (!state.getCollisionShape(level, pos).isEmpty()) {
                              int bit = x + 16 * z + 256 * y;
                              long[] var10000 = SOLID_BITS;
                              var10000[bit >> 6] |= 1L << (bit & 63);
                              ++solidCount;
                              if (state.canOcclude() && state.isCollisionShapeFullBlock(level, pos)) {
                                 var10000 = CUT_BITS;
                                 var10000[bit >> 6] |= 1L << (bit & 63);
                                 ++cutCount;
                              }
                           }

                           int emission = state.getLightEmission();
                           if (emission > 0) {
                              LIGHTS.put((byte)x).put((byte)y).put((byte)z).put((byte)emission).putInt(BlockLightColors.of(state));
                              ++lightCount;
                           }

                           FluidState fluid = state.getFluidState();
                           if (!fluid.isEmpty()) {
                              MESH.fluidGround = SkyCollision.groundTop(pos);
                              MESH.fluidBaseY = y;
                              fluidRenderer.tesselate(level, pos, MESH, state, fluid);
                              MESH.fluidGround = 0.0F;
                           }

                           if (state.getRenderShape() == RenderShape.MODEL) {
                              BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
                              blockRenderer.tesselateBlock(MESH, (float)x, (float)y, (float)z, level, pos.immutable(), state, model, state.getSeed(pos));
                           }
                        }
                     }
                  }
               }
            }

            SkyDig.DugLookup digLookup = SkyDig.clientDug;
            if (dugCount > 0 && digLookup != null) {
               Minecraft minecraft = Minecraft.getInstance();
               DigWalls.Faces var10006 = (st) -> cubeFaces(minecraft, st);
               MeshBuilder var10007 = MESH;
               Objects.requireNonNull(var10007);
               DigWalls.add(level, sx, sy, sz, dug, digLookup, var10006, var10007::wall);
            }

            if (MESH.vertexCount() == 0 && !SENT.contains(key) && lightCount == 0 && !LIT.contains(key) && solidCount == 0 && !SOLID.contains(key) && dugCount == 0 && !DUG.contains(key)) {
               return true;
            } else {
               ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(MESH.vertexCount()).flip();
               if (SkyLink.writeRender(2, header, MESH.bytes())) {
                  if (MESH.vertexCount() > 0) {
                     SENT.add(key);
                  } else {
                     SENT.remove(key);
                  }

                  if (lightCount > 0 || LIT.contains(key)) {
                     ByteBuffer lightHeader = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(lightCount).flip();
                     if (!SkyLink.writeRender(8, lightHeader, LIGHTS.flip())) {
                        markDirty(sx, sy, sz);
                     } else if (lightCount > 0) {
                        LIT.add(key);
                     } else {
                        LIT.remove(key);
                     }
                  }

                  if (solidCount > 0 || SOLID.contains(key)) {
                     ByteBuffer solidHeader = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(solidCount).flip();
                     ByteBuffer bits = ByteBuffer.allocate(solidCount > 0 ? 512 : 0).order(ByteOrder.LITTLE_ENDIAN);
                     if (solidCount > 0) {
                        for(long word : SOLID_BITS) {
                           bits.putLong(word);
                        }
                     }

                     if (!SkyLink.writeRender(10, solidHeader, bits.flip())) {
                        markDirty(sx, sy, sz);
                     } else if (solidCount > 0) {
                        SOLID.add(key);
                     } else {
                        SOLID.remove(key);
                     }
                  }

                  if (cutCount > 0 || CUT.contains(key)) {
                     ByteBuffer cutHeader = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(cutCount).flip();
                     ByteBuffer bits = ByteBuffer.allocate(cutCount > 0 ? 512 : 0).order(ByteOrder.LITTLE_ENDIAN);
                     if (cutCount > 0) {
                        for(long word : CUT_BITS) {
                           bits.putLong(word);
                        }
                     }

                     if (!SkyLink.writeRender(12, cutHeader, bits.flip())) {
                        markDirty(sx, sy, sz);
                     } else if (cutCount > 0) {
                        CUT.add(key);
                     } else {
                        CUT.remove(key);
                     }
                  }

                  if (dugCount > 0 || DUG.contains(key)) {
                     ByteBuffer dugHeader = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(dugCount).putInt(SkyDigClient.world()).putInt(0).flip();
                     ByteBuffer bits = ByteBuffer.allocate(dugCount > 0 ? 512 : 0).order(ByteOrder.LITTLE_ENDIAN);
                     if (dugCount > 0) {
                        for(long word : dug) {
                           bits.putLong(word);
                        }
                     }

                     if (!SkyLink.writeRender(11, dugHeader, bits.flip())) {
                        markDirty(sx, sy, sz);
                     } else if (dugCount > 0) {
                        DUG.add(key);
                     } else {
                        DUG.remove(key);
                     }
                  }

                  if (++meshesSent <= 10 || meshesSent % 200 == 0) {
                     SkyCraft.LOG.info("SkyCraft: block mesh for section {} {} {}: {} vertices ({} sections in Skyrim)", new Object[]{sx, sy, sz, MESH.vertexCount(), SENT.size()});
                  }
               } else {
                  markDirty(sx, sy, sz);
               }

               return true;
            }
         }
      }
   }

   private static void exportEntities(Minecraft minecraft, ClientLevel level, float partialTick) {
      ENTITIES.clear();
      Vec3 eye = minecraft.player.getEyePosition(partialTick);

      for(Entity e : level.entitiesForRendering()) {
         if (ENTITIES.size() < 160 && !(e.distanceToSqr(eye) > (double)9216.0F)) {
            Vec3 p = e.getPosition(partialTick);
            if (e instanceof AbstractArrow) {
               AbstractArrow arrow = (AbstractArrow)e;
               boolean trident = arrow instanceof ThrownTrident;
               int kind = trident ? 3 : 1;
               float yaw = Mth.rotLerp(partialTick, arrow.yRotO, arrow.getYRot());
               float pitch = Mth.lerp(partialTick, arrow.xRotO, arrow.getXRot());
               float[] var25;
               if (trident) {
                  var25 = (float[])ICONS.computeIfAbsent(Items.TRIDENT, (ix) -> iconUv(minecraft, level, new ItemStack(ix)));
               } else {
                  SkyAtlas var10000 = atlas;
                  byte var10001;
                  if (arrow instanceof SpectralArrow) {
                     var10001 = 2;
                  } else {
                     label110: {
                        if (arrow instanceof Arrow) {
                           Arrow tippable = (Arrow)arrow;
                           if (tippable.getColor() > 0) {
                              var10001 = 1;
                              break label110;
                           }
                        }

                        var10001 = 0;
                     }
                  }

                  var25 = var10000.arrowUv(var10001);
               }

               float[] uv = var25;
               if (uv != null) {
                  ENTITIES.add(new SkyLink.WorldEntity(kind, e.getId(), (float)p.x, (float)p.y, (float)p.z, yaw, pitch, 1.0F, (float[])null, uv, 0));
               }
            } else if (e instanceof ItemEntity) {
               ItemEntity item = (ItemEntity)e;
               float bob = Mth.sin((double)(((float)item.getAge() + partialTick) / 10.0F + item.bobOffs)) * 0.1F + 0.1F;
               float spin = ItemEntity.getSpin((float)item.getAge() + partialTick, item.bobOffs) * (180F / (float)Math.PI);
               addItem(minecraft, level, e, item.getItem(), p.add((double)0.0F, (double)bob, (double)0.0F), spin);
            } else {
               if (e instanceof FireworkRocketEntity) {
                  FireworkRocketEntity rocket = (FireworkRocketEntity)e;
                  if (minecraft.player.isFallFlying() && rocket.distanceToSqr(minecraft.player) < (double)4.0F && !minecraft.gameRenderer.mainCamera().isDetached()) {
                     continue;
                  }
               }

               if (e instanceof FishingHook) {
                  FishingHook hook = (FishingHook)e;
                  addFishing(minecraft, hook, p, partialTick);
                  continue;
               }

               if (e instanceof ItemSupplier) {
                  ItemSupplier supplier = (ItemSupplier)e;
                  float[] uv = iconUv(minecraft, level, supplier.getItem());
                  if (uv != null && ENTITIES.size() < 160) {
                     Vec3 c = p.add((double)0.0F, (double)e.getBbHeight() * (double)0.5F, (double)0.0F);
                     ENTITIES.add(new SkyLink.WorldEntity(2, e.getId(), (float)c.x, (float)c.y, (float)c.z, 0.0F, 0.0F, 0.5F, (float[])null, uv, 2));
                  }
               }
            }

            if (e instanceof Player && e != minecraft.player && ENTITIES.size() < 160) {
               String name = e.getName().getString();
               float[] chars = new float[16];

               for(int i = 0; i < Math.min(16, name.length()); ++i) {
                  chars[i] = (float)name.charAt(i);
               }

               ENTITIES.add(new SkyLink.WorldEntity(7, e.getId(), (float)p.x, (float)p.y, (float)p.z, 0.0F, 0.0F, chars[15], Arrays.copyOfRange(chars, 0, 3), Arrays.copyOfRange(chars, 3, 15), 0));
            }

            if (!(e instanceof AbstractArrow) && !(e instanceof ItemEntity) && !(e instanceof ItemSupplier) && e instanceof LivingEntity && !(e instanceof SkyrimActorEntity) && !e.isInvisible() && (e != minecraft.player || minecraft.gameRenderer.mainCamera().isDetached())) {
               ENTITIES.add(new SkyLink.WorldEntity(6, e.getId(), (float)p.x, (float)p.y, (float)p.z, 0.0F, 0.0F, e.getBbWidth(), (float[])null, (float[])null, 0));
            }
         }
      }

      addCracks(level);
      SkyLink.writeWorldEntities(ENTITIES, selection(minecraft, level));
   }

   private static void addItem(Minecraft minecraft, ClientLevel level, Entity e, ItemStack stack, Vec3 p, float yaw) {
      addItem(minecraft, level, e.getId(), stack, p, yaw);
   }

   private static void addItem(Minecraft minecraft, ClientLevel level, int id, ItemStack stack, Vec3 p, float yaw) {
      Item var7 = stack.getItem();
      if (var7 instanceof BlockItem blockItem) {
         BlockState state = blockItem.getBlock().defaultBlockState();
         if (state.getRenderShape() == RenderShape.MODEL && Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
            float[] faces = cubeFaces(minecraft, state);
            if (faces != null) {
               float size = 0.25F;
               int tint = cubeTint(minecraft, state);
               ENTITIES.add(new SkyLink.WorldEntity(4, id, (float)p.x, (float)(p.y + (double)size * (double)0.5F + 0.02), (float)p.z, yaw, 0.0F, size, (float[])null, faces, tint));
               return;
            }
         }
      }

      float[] uv = iconUv(minecraft, level, stack);
      if (uv != null) {
         ENTITIES.add(new SkyLink.WorldEntity(2, id, (float)p.x, (float)p.y + 0.25F, (float)p.z, yaw, 0.0F, 0.5F, (float[])null, uv, 0));
      }

   }

   private static void addFishing(Minecraft minecraft, FishingHook hook, Vec3 p, float partialTick) {
      if (ENTITIES.size() + 2 <= 160) {
         float[] white = cubeFaces(minecraft, block("white_wool"));
         float[] red = cubeFaces(minecraft, block("red_wool"));
         if (white != null && red != null) {
            float[] faces = (float[])(([F)white).clone();
            System.arraycopy(red, 4, faces, 4, 4);
            float size = 0.16F;
            ENTITIES.add(new SkyLink.WorldEntity(4, hook.getId(), (float)p.x, (float)p.y, (float)p.z, 0.0F, 0.0F, size, (float[])null, faces, 0));
            Player owner = hook.getPlayerOwner();
            if (owner != null) {
               boolean right = owner.getMainArm() == HumanoidArm.RIGHT == (owner.getMainHandItem().getItem() instanceof FishingRodItem);
               float yaw = Mth.lerp(partialTick, owner.yRotO, owner.getYRot()) * ((float)Math.PI / 180F);
               float pitch = Mth.lerp(partialTick, owner.xRotO, owner.getXRot()) * ((float)Math.PI / 180F);
               Vec3 forward = new Vec3((double)(-Mth.sin((double)yaw) * Mth.cos((double)pitch)), (double)(-Mth.sin((double)pitch)), (double)(Mth.cos((double)yaw) * Mth.cos((double)pitch)));
               Vec3 side = (new Vec3((double)(-Mth.cos((double)yaw)), (double)0.0F, (double)(-Mth.sin((double)yaw)))).scale(right ? (double)1.0F : (double)-1.0F);
               Vec3 tip;
               if (owner == minecraft.player && !minecraft.gameRenderer.mainCamera().isDetached()) {
                  tip = owner.getEyePosition(partialTick).add(forward.scale(0.6)).add(side.scale(0.35)).add((double)0.0F, (double)-0.25F, (double)0.0F);
               } else {
                  Vec3 body = owner.getPosition(partialTick);
                  tip = body.add((double)0.0F, (double)owner.getBbHeight() * 0.85, (double)0.0F).add(forward.scale(0.9)).add(side.scale(0.35)).add((double)0.0F, 0.6, (double)0.0F);
               }

               float[] black = cubeFaces(minecraft, block("black_wool"));
               float[] lineUv = black == null ? Arrays.copyOfRange(white, 0, 4) : Arrays.copyOfRange(black, 0, 4);
               ENTITIES.add(new SkyLink.WorldEntity(8, hook.getId() ^ 1073741824, (float)p.x, (float)(p.y + (double)size), (float)p.z, 0.0F, 0.0F, 1.0F, new float[]{(float)tip.x, (float)tip.y, (float)tip.z}, lineUv, owner == minecraft.player ? 1 : 0));
            }
         }
      }
   }

   private static BlockState block(String id) {
      return ((Block)BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id))).defaultBlockState();
   }

   private static float[] cubeFaces(Minecraft minecraft, BlockState state) {
      return (float[])CUBE_FACES.computeIfAbsent(state, (s) -> {
         BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(s);
         ArrayList<BlockStateModelPart> parts = new ArrayList();
         RANDOM.setSeed(42L);
         model.collectParts(RANDOM, parts);
         float[] out = new float[12];
         Direction[] dirs = new Direction[]{Direction.NORTH, Direction.UP, Direction.DOWN};

         for(int f = 0; f < 3; ++f) {
            TextureAtlasSprite sprite = null;

            for(BlockStateModelPart part : parts) {
               List<BakedQuad> quads = part.getQuads(dirs[f]);
               if (!quads.isEmpty()) {
                  sprite = ((BakedQuad)quads.getFirst()).materialInfo().sprite();
                  break;
               }
            }

            if (sprite == null) {
               return null;
            }

            System.arraycopy(atlas.rect(sprite), 0, out, f * 4, 4);
         }

         return out;
      });
   }

   private static int cubeTint(Minecraft minecraft, BlockState state) {
      List<BlockTintSource> sources = minecraft.getBlockColors().getTintSources(state);
      if (!sources.isEmpty() && sources.getFirst() != null) {
         int argb = ((BlockTintSource)sources.getFirst()).color(state);
         return -16777216 | (argb & 255) << 16 | argb & '\uff00' | argb >> 16 & 255;
      } else {
         return 0;
      }
   }

   private static void addMarkers(Minecraft minecraft, ClientLevel level, Vec3 eye) {
      float t = (float)(System.nanoTime() / 1000000L % 1000000L) / 1000.0F;

      for(OreNodes.Marker m : OreNodes.markers()) {
         Vec3 at = Vec3.atBottomCenterOf(m.pos());
         if (!(at.distanceToSqr(eye) > (double)100.0F) && ENTITIES.size() < 160) {
            ItemStack icon = new ItemStack(m.block().asItem());
            float bob = Mth.sin((double)(t * 2.0F + (float)m.id())) * 0.08F;
            addItem(minecraft, level, -m.id(), icon, at.add((double)0.0F, 1.35 + (double)bob, (double)0.0F), (t * 60.0F + (float)m.id()) % 360.0F);
         }
      }

   }

   private static void addCracks(ClientLevel level) {
      ObjectIterator var1 = ((ClientLevelAccessor)level).skycraft$destroyingBlocks().values().iterator();

      while(var1.hasNext()) {
         BlockDestructionProgress progress = (BlockDestructionProgress)var1.next();
         int stage = progress.getProgress();
         if (stage >= 0 && stage <= 9 && ENTITIES.size() < 160) {
            BlockPos pos = progress.getPos();
            if (!OreNodes.hidden(pos)) {
               VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
               if (!shape.isEmpty()) {
                  AABB box = shape.bounds().move(pos).inflate(0.004);
                  ENTITIES.add(new SkyLink.WorldEntity(5, pos.hashCode(), (float)box.minX, (float)box.minY, (float)box.minZ, 0.0F, 0.0F, 1.0F, new float[]{(float)box.getXsize(), (float)box.getYsize(), (float)box.getZsize()}, atlas.crackUv(stage), 0));
               }
            }
         }
      }

   }

   private static float[] iconUv(Minecraft minecraft, ClientLevel level, ItemStack stack) {
      minecraft.getItemModelResolver().updateForTopItem(ITEM_STATE, stack, ItemDisplayContext.GROUND, level, (ItemOwner)null, 0);
      RANDOM.setSeed(0L);
      Material.Baked material = ITEM_STATE.pickParticleMaterial(RANDOM);
      return material == null ? null : atlas.rect(material.sprite());
   }

   private static float[] selection(Minecraft minecraft, ClientLevel level) {
      HitResult hit = minecraft.hitResult;
      if (hit instanceof BlockHitResult blockHit) {
         if (hit.getType() == Type.BLOCK && minecraft.gui.screen() == null) {
            BlockPos pos = blockHit.getBlockPos();
            if (blockHit instanceof SkyClip.SkyrimHitResult) {
               if (!(minecraft.player.getMainHandItem().getItem() instanceof BlockItem)) {
                  return null;
               }

               return new float[]{(float)pos.getX(), (float)pos.getY(), (float)pos.getZ(), (float)(pos.getX() + 1), (float)(pos.getY() + 1), (float)(pos.getZ() + 1)};
            }

            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            if (shape.isEmpty()) {
               return null;
            }

            AABB box = shape.bounds().move(pos);
            return new float[]{(float)box.minX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.maxZ};
         }
      }

      return null;
   }

   static {
      LIGHTS = ByteBuffer.allocate(32768).order(ByteOrder.LITTLE_ENDIAN);
      sentGeneration = Integer.MIN_VALUE;
      MESH = new MeshBuilder();
      ENTITIES = new ArrayList();
      ITEM_STATE = new ItemStackRenderState();
      ICONS = new HashMap();
      CUBE_FACES = new HashMap();
      RANDOM = RandomSource.create();
   }

   private static final class MeshBuilder implements BlockQuadOutput, FluidRenderer.Output, VertexConsumer {
      private ByteBuffer buf;
      private int vertices;
      private final float[] fq;
      private int fqCount;
      private boolean fluidTranslucent;
      CardinalLighting cardinal;
      float fluidGround;
      int fluidBaseY;

      private MeshBuilder() {
         this.buf = ByteBuffer.allocateDirect(1048576).order(ByteOrder.LITTLE_ENDIAN);
         this.fq = new float[32];
         this.cardinal = CardinalLighting.DEFAULT;
      }

      void reset() {
         this.buf.clear();
         this.vertices = 0;
         this.fqCount = 0;
      }

      int vertexCount() {
         return this.vertices;
      }

      ByteBuffer bytes() {
         return this.buf.duplicate().flip();
      }

      private void ensure(int bytes) {
         if (this.buf.remaining() < bytes) {
            ByteBuffer bigger = ByteBuffer.allocateDirect(Math.max(this.buf.capacity() * 2, this.buf.position() + bytes)).order(ByteOrder.LITTLE_ENDIAN);
            this.buf.flip();
            bigger.put(this.buf);
            this.buf = bigger;
         }

      }

      private void vertex(float x, float y, float z, float u, float v, int argb, int light, int flags) {
         this.buf.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
         this.buf.put((byte)(argb >> 16)).put((byte)(argb >> 8)).put((byte)argb).put((byte)(argb >>> 24));
         int block = light >> 4 & 15;
         int sky = light >> 20 & 15;
         this.buf.putInt(block | sky << 8);
         this.buf.putInt(flags);
         ++this.vertices;
      }

      private static int flags(boolean translucent, Direction normal) {
         return (translucent ? 2 : 1) | (normal == null ? 0 : normal.ordinal() + 1 << 4);
      }

      private static int unshade(int argb, float shade) {
         if (!(shade >= 0.999F) && !(shade <= 0.0F)) {
            int r = Math.min(255, Math.round((float)(argb >> 16 & 255) / shade));
            int g = Math.min(255, Math.round((float)(argb >> 8 & 255) / shade));
            int b = Math.min(255, Math.round((float)(argb & 255) / shade));
            return argb & -16777216 | r << 16 | g << 8 | b;
         } else {
            return argb;
         }
      }

      void wall(float[] xyz, float[] uv, int light, Direction normal) {
         this.ensure(192);
         int flags = flags(false, normal);

         for(int k : new int[]{0, 1, 2, 0, 2, 3}) {
            this.vertex(xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2], uv[k * 2], uv[k * 2 + 1], -1, light, flags);
         }

      }

      public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
         this.ensure(192);
         TextureAtlasSprite sprite = quad.materialInfo().sprite();
         boolean translucent = quad.materialInfo().layer().translucent();
         int emission = quad.materialInfo().lightEmission();
         Direction override = quad.materialInfo().shadeDirectionOverride();
         Direction normal = override == Direction.UP && quad.direction() != Direction.UP ? null : (override != null ? override : quad.direction());
         float shade = this.cardinal.byFace(override != null ? override : quad.direction());
         int flags = flags(translucent, normal);

         for(int k : new int[]{0, 1, 2, 0, 2, 3}) {
            Vector3fc p = quad.position(k);
            long uv = quad.packedUV(k);
            float u = WorldExporter.atlas.u(sprite, UVPair.unpackU(uv));
            float v = WorldExporter.atlas.v(sprite, UVPair.unpackV(uv));
            this.vertex(p.x() + x, p.y() + y, p.z() + z, u, v, unshade(instance.getColor(k), shade), instance.getLightCoordsWithEmission(k, emission), flags);
         }

      }

      public VertexConsumer getBuilder(ChunkSectionLayer layer) {
         this.fluidTranslucent = layer.translucent();
         return this;
      }

      public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
         int o = this.fqCount * 8;
         if (this.fluidGround > 0.0F) {
            float t = Math.max(0.0F, Math.min(1.0F, y - (float)this.fluidBaseY));
            y = (float)this.fluidBaseY + this.fluidGround + t * (1.0F - this.fluidGround);
         }

         this.fq[o] = x;
         this.fq[o + 1] = y;
         this.fq[o + 2] = z;
         this.fq[o + 3] = WorldExporter.atlas.blockU(u);
         this.fq[o + 4] = WorldExporter.atlas.blockV(v);
         this.fq[o + 5] = Float.intBitsToFloat(color);
         this.fq[o + 6] = Float.intBitsToFloat(light);
         if (++this.fqCount == 4) {
            this.fqCount = 0;
            this.ensure(192);
            Direction normal = nx == 0.0F && ny == 0.0F && nz == 0.0F ? null : Direction.getApproximateNearest(nx, ny, nz);
            float shade = normal == null ? 1.0F : (normal.getAxis() == Axis.Y ? this.cardinal.byFace(normal) : this.cardinal.up() * this.cardinal.byFace(normal));
            int flags = flags(this.fluidTranslucent, normal);

            for(int k : new int[]{0, 1, 2, 0, 2, 3}) {
               int b = k * 8;
               this.vertex(this.fq[b], this.fq[b + 1], this.fq[b + 2], this.fq[b + 3], this.fq[b + 4], unshade(Float.floatToRawIntBits(this.fq[b + 5]), shade), Float.floatToRawIntBits(this.fq[b + 6]), flags);
            }
         }

      }

      public VertexConsumer addVertex(float x, float y, float z) {
         throw new UnsupportedOperationException();
      }

      public VertexConsumer setColor(int r, int g, int b, int a) {
         return this;
      }

      public VertexConsumer setColor(int color) {
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      public VertexConsumer setUv3(float u, float v) {
         return this;
      }

      public VertexConsumer setNormal(float x, float y, float z) {
         return this;
      }

      public VertexConsumer setLineWidth(float width) {
         return this;
      }
   }
}
