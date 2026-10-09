package dev.skycraft.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skycraft.SkyCraft;
import dev.skycraft.client.OwnSkin;
import dev.skycraft.client.mixin.RenderSetupAccessor;
import dev.skycraft.client.mixin.RenderTypeAccessor;
import dev.skycraft.client.mixin.TextureBindingAccessor;
import dev.skycraft.client.mixin.TextureManagerAccessor;
import dev.skycraft.combat.OreNodes;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.link.SkyLink;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

final class AvatarExporter implements SubmitNodeCollector {
   private static final int SOLID = 121;
   private static final int PARTICLE = 9;
   private static final int PARTICLE_BLENDED = 10;
   private static final int UV_RAW = 0;
   private static final int UV_BLOCK_ATLAS = 1;
   private static final int UV_ITEM_ATLAS = 2;
   private static final Direction[] FACES_AND_NONE;
   private static final double SCENE_RANGE = (double)64.0F;
   private static final int SCENE_MAX_ENTITIES = 48;
   private static final double BLOCK_ENTITY_RANGE = (double)48.0F;
   private static final int SCENE_MAX_BLOCK_ENTITIES = 256;
   private static boolean warnedBlockEntity;
   private static final Map<Identifier, Integer> TEXTURE_IDS;
   private static final Set<Identifier> UNUSABLE;
   private static int nextTextureId;
   private static @Nullable ModelBlockRenderer movingBlocks;
   private static boolean warnedEntity;
   private static final AvatarExporter AVATAR;
   private static final AvatarExporter SCENE;
   private static final AvatarExporter RAGDOLL;
   private static long nextRagdollNanos;
   private final Map<Long, Batch> batches = new HashMap();
   private final Capture capture = new Capture();
   private boolean shown;
   private SkyAtlas atlas;
   private float offX;
   private float offY;
   private float offZ;
   private static int whiteId;

   private AvatarExporter() {
   }

   private Batch whiteBatch() {
      if (whiteId < 0) {
         int id = nextTextureId++;
         ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(id).putInt(1).putInt(1).putInt(0).flip();
         ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).flip();
         if (!SkyLink.writeRender(4, header, pixel)) {
            --nextTextureId;
            return null;
         }

         whiteId = id;
      }

      return this.batch(whiteId, 0, 121);
   }

   static void reset() {
      TEXTURE_IDS.clear();
      UNUSABLE.clear();
      nextTextureId = 1;
      whiteId = -1;
      AVATAR.shown = false;
      SCENE.shown = false;
      RAGDOLL.shown = false;
      nextRagdollNanos = 0L;
      movingBlocks = null;
   }

   static void frame(Minecraft minecraft, SkyAtlas atlas, float partialTick) {
      AVATAR.exportAvatar(minecraft, atlas, partialTick);
      SCENE.exportScene(minecraft, atlas, partialTick);
      long now = System.nanoTime();
      if (now >= nextRagdollNanos) {
         nextRagdollNanos = now + 1000000000L;
         RAGDOLL.exportRagdoll(minecraft, atlas, partialTick);
      }

   }

   private void exportAvatar(Minecraft minecraft, SkyAtlas atlas, float partialTick) {
      this.atlas = atlas;
      LocalPlayer player = minecraft.player;
      Camera camera = minecraft.gameRenderer.mainCamera();
      Entity vehicle = player == null ? null : player.getVehicle();
      if (player != null && (camera.isDetached() || vehicle != null)) {
         this.begin();

         try {
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            dispatcher.prepare(camera, minecraft.crosshairPickEntity);
            CameraRenderState cameraState = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
            if (camera.isDetached()) {
               EntityRenderState state = dispatcher.extractEntity(player, partialTick);
               dispatcher.submit(state, cameraState, (double)0.0F, (double)0.0F, (double)0.0F, new PoseStack(), this);
            }

            if (vehicle != null) {
               Vec3 rel = vehicle.getPosition(partialTick).subtract(player.getPosition(partialTick));
               dispatcher.submit(dispatcher.extractEntity(vehicle, partialTick), cameraState, rel.x, rel.y, rel.z, new PoseStack(), this);
            }
         } catch (RuntimeException e) {
            SkyCraft.LOG.warn("SkyCraft: couldn't capture the player model", e);
            return;
         }

         this.send(5, (double[])null);
      } else {
         this.sendEmpty(5, false);
      }
   }

   private void exportRagdoll(Minecraft minecraft, SkyAtlas atlas, float partialTick) {
      this.atlas = atlas;
      LocalPlayer player = minecraft.player;
      if (player != null && !player.isDeadOrDying() && !player.isSpectator()) {
         this.begin();

         try {
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            Camera camera = minecraft.gameRenderer.mainCamera();
            dispatcher.prepare(camera, minecraft.crosshairPickEntity);
            EntityRenderState state = dispatcher.extractEntity(player, partialTick);
            state.ageInTicks = 0.0F;
            state.nameTag = null;
            if (state instanceof LivingEntityRenderState) {
               LivingEntityRenderState living = (LivingEntityRenderState)state;
               living.bodyRot = living.yRot = living.xRot = 0.0F;
               living.deathTime = 0.0F;
               living.walkAnimationPos = living.walkAnimationSpeed = 0.0F;
               living.hasRedOverlay = false;
               living.isAutoSpinAttack = false;
               living.pose = Pose.STANDING;
            }

            if (state instanceof ArmedEntityRenderState) {
               ArmedEntityRenderState armed = (ArmedEntityRenderState)state;
               armed.rightHandItemState.clear();
               armed.leftHandItemState.clear();
               armed.currentSwing = null;
               armed.swingAnimation = 0.0F;
            }

            if (state instanceof HumanoidRenderState) {
               HumanoidRenderState humanoid = (HumanoidRenderState)state;
               humanoid.swimAmount = 0.0F;
               humanoid.isCrouching = humanoid.isFallFlying = humanoid.isVisuallySwimming = humanoid.isPassenger = humanoid.isUsingItem = false;
            }

            if (state instanceof AvatarRenderState) {
               AvatarRenderState avatar = (AvatarRenderState)state;
               avatar.arrowCount = avatar.stingerCount = 0;
               avatar.parrotOnLeftShoulder = avatar.parrotOnRightShoulder = null;
            }

            CameraRenderState cameraState = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
            dispatcher.submit(state, cameraState, (double)0.0F, (double)0.0F, (double)0.0F, new PoseStack(), this);
         } catch (RuntimeException e) {
            SkyCraft.LOG.warn("SkyCraft: couldn't capture the player's body for the ragdoll", e);
            return;
         }

         this.sendParts(9);
      }
   }

   private static int partAt(float x, float y) {
      if (y >= 1.5F) {
         return 1;
      } else if (y >= 0.75F) {
         return x < -0.255F ? 3 : (x > 0.255F ? 4 : 2);
      } else {
         return x < 0.0F ? 5 : 6;
      }
   }

   private void sendParts(int message) {
      this.capture.flush();

      record Group(Batch batch, int part, int[] quads, int count) {
      }

      List<Group> groups = new ArrayList();
      int vertices = 0;

      for(Batch b : this.batches.values()) {
         int quadCount = b.count / 4;
         if (quadCount != 0) {
            int[][] byPart = new int[7][quadCount];
            int[] counts = new int[7];

            int part;
            for(int q = 0; q < quadCount; byPart[part][counts[part]++] = q++) {
               float cx = 0.0F;
               float cy = 0.0F;

               for(int k = 0; k < 4; ++k) {
                  int o = (q * 4 + k) * 8;
                  cx += Float.intBitsToFloat(b.data[o]);
                  cy += Float.intBitsToFloat(b.data[o + 1]);
               }

               part = partAt(cx * 0.25F, cy * 0.25F);
            }

            for(int part = 1; part < 7; ++part) {
               if (counts[part] > 0) {
                  groups.add(new Group(b, part, byPart[part], counts[part]));
                  vertices += counts[part] * 6;
               }
            }
         }
      }

      if (!groups.isEmpty()) {
         ByteBuffer header = ByteBuffer.allocate(8 + groups.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
         header.putInt(groups.size()).putInt(vertices);
         ByteBuffer body = ByteBuffer.allocateDirect(vertices * 32).order(ByteOrder.LITTLE_ENDIAN);
         int first = 0;

         for(Group g : groups) {
            int count = g.count() * 6;
            header.putInt(g.batch().texture).putInt(first).putInt(count).putInt((g.batch().translucent ? 1 : 0) | g.part() << 8);

            for(int i = 0; i < g.count(); ++i) {
               g.batch().writeQuad(body, g.quads()[i]);
            }

            first += count;
         }

         header.flip();
         body.flip();
         if (SkyLink.tryWriteRender(message, header, body)) {
            this.shown = true;
         }

      }
   }

   private void exportScene(Minecraft minecraft, SkyAtlas atlas, float partialTick) {
      this.atlas = atlas;
      ClientLevel level = minecraft.level;
      LocalPlayer player = minecraft.player;
      if (level != null && player != null) {
         Camera camera = minecraft.gameRenderer.mainCamera();
         Vec3 cam = camera.position();
         double[] origin = new double[]{Math.floor(cam.x), Math.floor(cam.y), Math.floor(cam.z)};
         this.begin();
         EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
         dispatcher.prepare(camera, minecraft.crosshairPickEntity);
         CameraRenderState cameraState = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
         PoseStack pose = new PoseStack();
         int entities = 0;

         for(Entity e : level.entitiesForRendering()) {
            if (e != player && e != player.getVehicle() && !(e instanceof ItemEntity) && !(e instanceof AbstractArrow) && !(e instanceof ItemSupplier) && !(e instanceof SkyrimActorEntity) && !(e.distanceToSqr(cam) > (double)4096.0F) && entities < 48) {
               ++entities;

               try {
                  EntityRenderState state = dispatcher.extractEntity(e, partialTick);
                  dispatcher.submit(state, cameraState, state.x - origin[0], state.y - origin[1], state.z - origin[2], pose, this);
               } catch (RuntimeException ex) {
                  if (!warnedEntity) {
                     warnedEntity = true;
                     SkyCraft.LOG.warn("SkyCraft: couldn't capture {} for Skyrim", e, ex);
                  }
               }
            }
         }

         this.submitBlockEntities(minecraft, level, cam, origin, partialTick, cameraState, pose);
         this.capture.flush();
         this.offX = (float)(cam.x - origin[0]);
         this.offY = (float)(cam.y - origin[1]);
         this.offZ = (float)(cam.z - origin[2]);

         try {
            for(ParticleGroupRenderState group : minecraft.gameRenderer.gameRenderState().levelRenderState.particlesRenderState.particles) {
               group.submit(this, cameraState);
            }
         } catch (RuntimeException ex) {
            SkyCraft.LOG.warn("SkyCraft: couldn't capture particles for Skyrim", ex);
         }

         this.capture.flush();
         this.offX = this.offY = this.offZ = 0.0F;
         this.send(6, origin);
      }
   }

   private void submitBlockEntities(Minecraft minecraft, ClientLevel level, Vec3 cam, double[] origin, float partialTick, CameraRenderState cameraState, PoseStack pose) {
      BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
      dispatcher.prepare(cam);
      double range2 = (double)2304.0F;
      int count = 0;
      int cx0 = (int)Math.floor((cam.x - (double)48.0F) / (double)16.0F);
      int cx1 = (int)Math.floor((cam.x + (double)48.0F) / (double)16.0F);
      int cz0 = (int)Math.floor((cam.z - (double)48.0F) / (double)16.0F);
      int cz1 = (int)Math.floor((cam.z + (double)48.0F) / (double)16.0F);

      for(int cx = cx0; cx <= cx1; ++cx) {
         for(int cz = cz0; cz <= cz1; ++cz) {
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk != null) {
               for(BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                  BlockPos pos = blockEntity.getBlockPos();
                  if (!blockEntity.isRemoved() && !(pos.distToCenterSqr(cam) > range2) && count < 256 && !OreNodes.hidden(pos)) {
                     try {
                        BlockEntityRenderState state = dispatcher.tryExtractRenderState(blockEntity, partialTick, (ModelFeatureRenderer.CrumblingOverlay)null, false);
                        if (state == null) {
                           state = dispatcher.tryExtractRenderState(blockEntity, partialTick, (ModelFeatureRenderer.CrumblingOverlay)null, true);
                        }

                        if (state != null) {
                           ++count;
                           pose.pushPose();
                           pose.translate((double)pos.getX() - origin[0], (double)pos.getY() - origin[1], (double)pos.getZ() - origin[2]);
                           dispatcher.submit(state, pose, this, cameraState);
                           pose.popPose();
                        }
                     } catch (RuntimeException ex) {
                        if (!warnedBlockEntity) {
                           warnedBlockEntity = true;
                           SkyCraft.LOG.warn("SkyCraft: couldn't capture {} at {} for Skyrim", new Object[]{blockEntity.getType(), pos, ex});
                        }
                     }
                  }
               }
            }
         }
      }

   }

   private void begin() {
      for(Batch b : this.batches.values()) {
         b.clear();
      }

   }

   private void send(int message, double @Nullable [] origin) {
      this.capture.flush();
      List<Batch> used = new ArrayList();
      int vertices = 0;

      for(Batch b : this.batches.values()) {
         if (b.count >= 4) {
            used.add(b);
            vertices += b.count / 4 * 6;
         }
      }

      if (used.isEmpty()) {
         this.sendEmpty(message, origin != null);
      } else {
         ByteBuffer header = ByteBuffer.allocate((origin != null ? 24 : 0) + 8 + used.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
         if (origin != null) {
            header.putDouble(origin[0]).putDouble(origin[1]).putDouble(origin[2]);
         }

         header.putInt(used.size()).putInt(vertices);
         ByteBuffer body = ByteBuffer.allocateDirect(vertices * 32).order(ByteOrder.LITTLE_ENDIAN);
         int first = 0;

         for(Batch b : used) {
            int count = b.count / 4 * 6;
            header.putInt(b.texture).putInt(first).putInt(count).putInt(b.translucent ? 1 : 0);
            b.writeTriangles(body);
            first += count;
         }

         header.flip();
         body.flip();
         if (SkyLink.tryWriteRender(message, header, body)) {
            this.shown = true;
         }

      }
   }

   private void sendEmpty(int message, boolean withOrigin) {
      if (this.shown) {
         ByteBuffer header = ByteBuffer.allocate((withOrigin ? 24 : 0) + 8).order(ByteOrder.LITTLE_ENDIAN);
         if (withOrigin) {
            header.putDouble((double)0.0F).putDouble((double)0.0F).putDouble((double)0.0F);
         }

         header.putInt(0).putInt(0).flip();
         this.shown = !SkyLink.writeRender(message, header, (ByteBuffer)null);
      }
   }

   private Batch batch(int texture, int uvMode, int flags) {
      long key = (long)texture << 16 | (long)uvMode << 8 | (long)flags;
      return (Batch)this.batches.computeIfAbsent(key, (k) -> new Batch(texture, uvMode, flags));
   }

   private @Nullable Batch batchFor(RenderType renderType) {
      RenderTypeAccessor type = (RenderTypeAccessor)renderType;
      String name = type.skycraft$name();
      if (!name.contains("glint") && !name.contains("outline") && !name.contains("shadow")) {
         Object binding = ((RenderSetupAccessor)type.skycraft$state()).skycraft$textures().get("Sampler0");
         if (binding == null) {
            return null;
         } else {
            Identifier texture = ((TextureBindingAccessor)binding).skycraft$location();
            if (texture.equals(TextureAtlas.LOCATION_BLOCKS)) {
               return this.batch(0, 1, 121);
            } else if (texture.equals(TextureAtlas.LOCATION_ITEMS)) {
               return this.batch(0, 2, 121);
            } else {
               int id = textureId(texture);
               return id < 0 ? null : this.batch(id, 0, 121);
            }
         }
      } else {
         return null;
      }
   }

   private static int textureId(Identifier texture) {
      Integer known = (Integer)TEXTURE_IDS.get(texture);
      if (known != null) {
         return known;
      } else if (UNUSABLE.contains(texture)) {
         return -1;
      } else {
         NativeImage image = readTexture(texture);
         if (image == null) {
            UNUSABLE.add(texture);
            SkyCraft.LOG.info("SkyCraft: texture {} isn't available to Skyrim; what uses it is left out", texture);
            return -1;
         } else {
            int id = nextTextureId++;
            NativeImage var4 = image;

            byte var12;
            label102: {
               try {
                  int w = image.getWidth();
                  int h = image.getHeight();
                  ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);

                  for(int y = 0; y < h; ++y) {
                     for(int x = 0; x < w; ++x) {
                        int argb = image.getPixel(x, y);
                        pixels.put((byte)(argb >> 16)).put((byte)(argb >> 8)).put((byte)argb).put((byte)(argb >>> 24));
                     }
                  }

                  pixels.flip();
                  LocalPlayer self = Minecraft.getInstance().player;
                  boolean mine = texture.equals(OwnSkin.texture()) || self != null && texture.equals(self.getSkin().body().texturePath());
                  int own = mine ? 1 : 0;
                  if (mine && !OwnSkin.isDefaultSkin(texture)) {
                     try {
                        Path tmp = Path.of("wowcraft-skin.png.tmp");
                        image.writeToFile(tmp);
                        Files.move(tmp, Path.of("wowcraft-skin.png"), StandardCopyOption.REPLACE_EXISTING);
                        if (self != null) {
                           Files.writeString(Path.of("wowcraft-name.txt"), self.getName().getString());
                        }
                     } catch (IOException e) {
                        SkyCraft.LOG.warn("WowCraft: couldn't save our skin for WoW", e);
                     }
                  }

                  ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(id).putInt(w).putInt(h).putInt(own).flip();
                  if (!SkyLink.writeRender(4, header, pixels)) {
                     --nextTextureId;
                     var12 = -1;
                     break label102;
                  }
               } catch (Throwable var15) {
                  if (image != null) {
                     try {
                        var4.close();
                     } catch (Throwable var13) {
                        var15.addSuppressed(var13);
                     }
                  }

                  throw var15;
               }

               if (image != null) {
                  image.close();
               }

               TEXTURE_IDS.put(texture, id);
               SkyCraft.LOG.info("SkyCraft: sent texture {} to Skyrim (id {})", texture, id);
               return id;
            }

            if (image != null) {
               image.close();
            }

            return var12;
         }
      }
   }

   private static @Nullable NativeImage readTexture(Identifier texture) {
      Minecraft minecraft = Minecraft.getInstance();
      Optional<Resource> resource = minecraft.getResourceManager().getResource(texture);
      if (resource.isPresent()) {
         try {
            InputStream in = ((Resource)resource.get()).open();

            NativeImage var11;
            try {
               var11 = NativeImage.read(in);
            } catch (Throwable var7) {
               if (in != null) {
                  try {
                     in.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (in != null) {
               in.close();
            }

            return var11;
         } catch (IOException e) {
            SkyCraft.LOG.warn("SkyCraft: couldn't read {}", texture, e);
            return null;
         }
      } else {
         AbstractTexture registered = (AbstractTexture)((TextureManagerAccessor)minecraft.getTextureManager()).skycraft$byPath().get(texture);
         if (registered instanceof DynamicTexture) {
            DynamicTexture dynamic = (DynamicTexture)registered;
            if (dynamic.getPixels() != null) {
               NativeImage copy = new NativeImage(dynamic.getPixels().getWidth(), dynamic.getPixels().getHeight(), false);
               copy.copyFrom(dynamic.getPixels());
               return copy;
            }
         }

         if (registered instanceof TextureAtlas) {
            TextureAtlas atlas = (TextureAtlas)registered;
            return SkyAtlas.image(atlas);
         } else {
            return null;
         }
      }
   }

   private void addQuad(Batch batch, Matrix4f pose, BakedQuad quad, int[] tintLayers, int lightCoords, int overlayCoords) {
      BakedQuad.MaterialInfo material = quad.materialInfo();
      int layer = material.isTinted() ? material.tintIndex() : -1;
      int color = layer >= 0 && layer < tintLayers.length ? tintLayers[layer] : -1;
      TextureAtlasSprite sprite = material.sprite();
      Vector3f p = new Vector3f();

      for(int k = 0; k < 4; ++k) {
         pose.transformPosition(quad.position(k), p);
         long uv = quad.packedUV(k);
         batch.add(p.x(), p.y(), p.z(), this.atlas.u(sprite, UVPair.unpackU(uv)), this.atlas.v(sprite, UVPair.unpackV(uv)), color, lightCoords, overlayCoords);
      }

   }

   public OrderedSubmitNodeCollector order(int order) {
      return this;
   }

   public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int tintedColor, @Nullable UvMapping uvMapping, int outlineColor) {
      Batch batch = this.batchFor(renderType);
      if (batch != null) {
         this.capture.begin(batch);
         VertexConsumer buffer = (VertexConsumer)(uvMapping != null ? uvMapping.wrap(this.capture) : this.capture);
         model.setupAnim(state);
         model.renderToBuffer(poseStack, buffer, lightCoords, overlayCoords, tintedColor);
         this.capture.flush();
      }
   }

   public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, ItemQuads quads, ItemStackRenderState.FoilType foilType) {
      this.capture.flush();
      Batch batch = this.batch(0, 0, 121);
      Matrix4f pose = poseStack.last().pose();

      for(BakedQuad quad : quads.all()) {
         this.addQuad(batch, pose, quad, tintLayers, lightCoords, overlayCoords);
      }

   }

   public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
      this.capture.flush();
      Batch batch = this.batch(0, 0, 121);
      Matrix4f pose = poseStack.last().pose();

      for(BlockStateModelPart part : parts) {
         for(Direction face : FACES_AND_NONE) {
            for(BakedQuad quad : part.getQuads(face)) {
               this.addQuad(batch, pose, quad, tintLayers, lightCoords, overlayCoords);
            }
         }
      }

   }

   public void submitBlockModel(PoseStack poseStack, Function<ChunkSectionLayer, RenderType> renderTypes, boolean translucentLayer, List<BlockStateModelPart> parts, @Nullable Mesh mesh, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
      this.submitBlockModel(poseStack, (RenderType)null, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
      this.addMesh(poseStack.last().pose(), mesh, lightCoords, overlayCoords);
   }

   public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, ItemQuads quads, @Nullable MeshView mesh, ItemStackRenderState.FoilType foilType) {
      this.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
      this.addMesh(poseStack.last().pose(), mesh, lightCoords, overlayCoords);
   }

   public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, @Nullable Mesh mesh, int progress, boolean isBlockTranslucent) {
   }

   private void addMesh(Matrix4f pose, @Nullable MeshView mesh, int lightCoords, int overlayCoords) {
      if (mesh != null && mesh.size() != 0) {
         Vector3f p = new Vector3f();
         mesh.forEach((quad) -> {
            Batch batch = this.batch(0, quad.atlas() == QuadAtlas.ITEM ? 2 : 1, 121);

            for(int k = 0; k < 4; ++k) {
               pose.transformPosition(quad.x(k), quad.y(k), quad.z(k), p);
               int light = Math.max(quad.lightmap(k), lightCoords);
               batch.add(p.x(), p.y(), p.z(), quad.u(k), quad.v(k), quad.color(k), light, overlayCoords);
            }

         });
      }
   }

   public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int outlineColor) {
      this.capture.flush();
      Minecraft minecraft = Minecraft.getInstance();
      if (movingBlocks == null) {
         movingBlocks = new ModelBlockRenderer(false, true, minecraft.getBlockColors());
      }

      BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state.blockState);
      Batch batch = this.batch(0, 0, 121);
      Matrix4f pose = new Matrix4f(poseStack.last().pose());
      Vector3f p = new Vector3f();
      movingBlocks.tesselateBlock((x, y, z, quad, instance) -> {
         TextureAtlasSprite sprite = quad.materialInfo().sprite();
         int emission = quad.materialInfo().lightEmission();

         for(int k = 0; k < 4; ++k) {
            Vector3fc q = quad.position(k);
            pose.transformPosition(q.x() + x, q.y() + y, q.z() + z, p);
            long uv = quad.packedUV(k);
            batch.add(p.x(), p.y(), p.z(), this.atlas.u(sprite, UVPair.unpackU(uv)), this.atlas.v(sprite, UVPair.unpackV(uv)), instance.getColor(k), instance.getLightCoordsWithEmission(k, emission), OverlayTexture.NO_OVERLAY);
         }

      }, 0.0F, 0.0F, 0.0F, state, state.blockPos, state.blockState, model, state.blockState.getSeed(state.randomSeedPos));
   }

   public void submitQuadParticleGroup(QuadParticleRenderState particles) {
      this.capture.flush();

      for(SingleQuadParticle.Layer layer : particles.layers()) {
         Identifier texture = layer.textureAtlasLocation();
         int flags = layer.translucent() ? 10 : 9;
         Batch batch;
         if (texture.equals(TextureAtlas.LOCATION_BLOCKS)) {
            batch = this.batch(0, 1, flags);
         } else if (texture.equals(TextureAtlas.LOCATION_ITEMS)) {
            batch = this.batch(0, 2, flags);
         } else {
            int id = textureId(texture);
            if (id < 0) {
               continue;
            }

            batch = this.batch(id, 0, flags);
         }

         this.capture.begin(batch);
         particles.buildLayer(layer, this.capture);
         this.capture.flush();
      }

   }

   public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
   }

   public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, CameraRenderState camera) {
   }

   public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
   }

   public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode, int lightCoords) {
   }

   public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
   }

   public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leash) {
      Batch batch = this.whiteBatch();
      if (batch != null && leash.offset != null && leash.start != null && leash.end != null) {
         Matrix4f pose = (new Matrix4f(poseStack.last().pose())).translate((float)leash.offset.x, (float)leash.offset.y, (float)leash.offset.z);
         float dx = (float)(leash.end.x - leash.start.x);
         float dy = (float)(leash.end.y - leash.start.y);
         float dz = (float)(leash.end.z - leash.start.z);
         float horiz = (float)Math.sqrt((double)(dx * dx + dz * dz));
         float half = 0.025F;
         Vector3f side = horiz > 1.0E-4F ? new Vector3f(dz / horiz * half, 0.0F, -dx / horiz * half) : new Vector3f(half, 0.0F, 0.0F);
         Vector3f up = new Vector3f(0.0F, half, 0.0F);
         int steps = 24;

         for(Vector3f across : new Vector3f[]{side, up}) {
            for(int k = 0; k < 24; ++k) {
               float t0 = (float)k / 24.0F;
               float t1 = (float)(k + 1) / 24.0F;
               Vector3f a = leashPoint(dx, dy, dz, t0);
               Vector3f b = leashPoint(dx, dy, dz, t1);
               float shade = k % 2 == 0 ? 1.0F : 0.7F;
               int argb = -16777216 | (int)(0.5F * shade * 255.0F) << 16 | (int)(0.4F * shade * 255.0F) << 8 | (int)(0.3F * shade * 255.0F);
               int block = (int)Mth.lerp(t0, (float)leash.startBlockLight, (float)leash.endBlockLight);
               int sky = (int)Mth.lerp(t0, (float)leash.startSkyLight, (float)leash.endSkyLight);
               int light = block << 4 | sky << 20;
               Vector3f[] quad = new Vector3f[]{(new Vector3f(a)).sub(across), (new Vector3f(b)).sub(across), (new Vector3f(b)).add(across), (new Vector3f(a)).add(across)};

               for(Vector3f q : quad) {
                  pose.transformPosition(q);
                  batch.add(q.x, q.y, q.z, 0.5F, 0.5F, argb, light, OverlayTexture.NO_OVERLAY);
               }
            }
         }

      }
   }

   private static Vector3f leashPoint(float dx, float dy, float dz, float t) {
      float y = dy > 0.0F ? dy * t * t : dy - dy * (1.0F - t) * (1.0F - t);
      return new Vector3f(dx * t, y, dz * t);
   }

   public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
   }

   public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
   }

   public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
   }

   public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
   }

   public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
   }

   static {
      FACES_AND_NONE = new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
      TEXTURE_IDS = new HashMap();
      UNUSABLE = new HashSet();
      nextTextureId = 1;
      AVATAR = new AvatarExporter();
      SCENE = new AvatarExporter();
      RAGDOLL = new AvatarExporter();
      whiteId = -1;
   }

   private final class Batch {
      final int texture;
      final int uvMode;
      final int flags;
      final boolean translucent;
      int[] data;
      int count;

      Batch(int texture, int uvMode, int flags) {
         Objects.requireNonNull(AvatarExporter.this);
         super();
         this.data = new int[2048];
         this.texture = texture;
         this.uvMode = uvMode;
         this.flags = flags;
         this.translucent = (flags & 2) != 0;
      }

      void clear() {
         this.count = 0;
      }

      void add(float x, float y, float z, float u, float v, int argb, int light, int overlay) {
         if ((this.count + 1) * 8 > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
         }

         if (this.uvMode == 1) {
            u = AvatarExporter.this.atlas.blockU(u);
            v = AvatarExporter.this.atlas.blockV(v);
         } else if (this.uvMode == 2) {
            u = AvatarExporter.this.atlas.itemU(u);
            v = AvatarExporter.this.atlas.itemV(v);
         }

         if ((overlay >>> 16 & '\uffff') < 8) {
            int r = argb >> 16 & 255;
            int g = (int)((float)(argb >> 8 & 255) * 0.55F);
            int b = (int)((float)(argb & 255) * 0.55F);
            argb = argb & -16777216 | r << 16 | g << 8 | b;
         }

         if ((overlay & '\uffff') >= 8) {
            light = 15728880;
         }

         int o = this.count * 8;
         this.data[o] = Float.floatToRawIntBits(x + AvatarExporter.this.offX);
         this.data[o + 1] = Float.floatToRawIntBits(y + AvatarExporter.this.offY);
         this.data[o + 2] = Float.floatToRawIntBits(z + AvatarExporter.this.offZ);
         this.data[o + 3] = Float.floatToRawIntBits(u);
         this.data[o + 4] = Float.floatToRawIntBits(v);
         this.data[o + 5] = argb;
         this.data[o + 6] = light >> 4 & 15 | (light >> 20 & 15) << 8;
         this.data[o + 7] = this.flags;
         ++this.count;
      }

      void writeTriangles(ByteBuffer out) {
         for(int q = 0; q + 4 <= this.count; q += 4) {
            this.writeQuad(out, q / 4);
         }

      }

      void writeQuad(ByteBuffer out, int quad) {
         for(int k : new int[]{0, 1, 2, 0, 2, 3}) {
            int o = (quad * 4 + k) * 8;
            out.putInt(this.data[o]).putInt(this.data[o + 1]).putInt(this.data[o + 2]).putInt(this.data[o + 3]).putInt(this.data[o + 4]);
            int argb = this.data[o + 5];
            out.put((byte)(argb >> 16)).put((byte)(argb >> 8)).put((byte)argb).put((byte)(argb >>> 24));
            out.putInt(this.data[o + 6]).putInt(this.data[o + 7]);
         }

      }
   }

   private final class Capture implements VertexConsumer {
      private Batch batch;
      private boolean pending;
      private float x;
      private float y;
      private float z;
      private float u;
      private float v;
      private int color;
      private int light;
      private int overlay;

      private Capture() {
         Objects.requireNonNull(AvatarExporter.this);
         super();
      }

      void begin(Batch b) {
         this.flush();
         this.batch = b;
      }

      void flush() {
         if (this.pending && this.batch != null) {
            this.batch.add(this.x, this.y, this.z, this.u, this.v, this.color, this.light, this.overlay);
         }

         this.pending = false;
      }

      public VertexConsumer addVertex(float x, float y, float z) {
         this.flush();
         this.x = x;
         this.y = y;
         this.z = z;
         this.color = -1;
         this.light = 15728880;
         this.overlay = OverlayTexture.NO_OVERLAY;
         this.pending = true;
         return this;
      }

      public VertexConsumer setColor(int r, int g, int b, int a) {
         this.color = a << 24 | r << 16 | g << 8 | b;
         return this;
      }

      public VertexConsumer setColor(int color) {
         this.color = color;
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         this.u = u;
         this.v = v;
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         this.overlay = u & '\uffff' | v << 16;
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         this.light = u & '\uffff' | v << 16;
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
