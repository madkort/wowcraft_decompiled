package dev.skycraft.client;

import dev.skycraft.SkyCraft;
import dev.skycraft.client.render.WorldExporter;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyWater;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.sdl.SDLVideo;

public final class SkyClient {
   private static final boolean SHOW_WINDOW = Boolean.getBoolean("skycraft.showWindow");
   private static final boolean START_HIDDEN = Boolean.getBoolean("skycraft.startHidden");
   private static boolean startedHidden;
   private static final SkyLink.SkyState sky = new SkyLink.SkyState();
   private static final SkyLink.McState mc = new SkyLink.McState();
   private static volatile boolean linked;
   private static boolean tookOver;
   private static boolean windowHidden;
   private static int appliedViewportW;
   private static int appliedViewportH;
   private static int hudViewportW;
   public static int guiFactor = 1;
   private static int lastTeleportSeq = -1;
   private static int teleportAck;
   private static boolean teleportPending;
   private static LocalPlayer lastPlayer;
   private static Vec3 holdPos;
   private static Vec3 unlinkedHold;
   private static long holdSince;
   private static long qpcFreq;
   private static LocalPlayer eyePlayer;
   private static float eyeSmoothed;
   private static long frameCounter;
   private static int lastPacedSeq;
   private static boolean skyrimStalled;
   private static int exporterErrors;
   private static final boolean QUIT_WITH_SKYRIM = Boolean.parseBoolean(System.getProperty("skycraft.quitWithSkyrim", "true"));
   private static long skyrimGoneSince;
   private static long nextSkyrimCheck;
   private static final long NEVER_CONNECTED_QUIT_MS = 600000L;
   private static final long STARTED_AT = System.currentTimeMillis();
   private static boolean gaveUpWaiting;
   private static final Identifier GHOST_SPEED = Identifier.fromNamespaceAndPath("skycraft", "ghost_speed");
   private static boolean wasRiding;
   private static Vec3 lastTeleport;

   private SkyClient() {
   }

   public static boolean linked() {
      return linked;
   }

   public static boolean tookOver() {
      return tookOver;
   }

   public static SkyLink.SkyState sky() {
      return sky;
   }

   public static void beginFrame() {
      SkyLink.poll();
      quitWithSkyrim(Minecraft.getInstance());
      if (START_HIDDEN && !startedHidden) {
         startedHidden = true;
         Minecraft minecraft = Minecraft.getInstance();
         hideWindowOnce(minecraft);
         minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set((double)0.0F);
         minecraft.getMusicManager().stopPlaying();
      }

      boolean nowLinked = SkyLink.active();
      if (nowLinked) {
         SkyLink.readSkyState(sky);
         SkyWater.refresh();
      } else {
         SkyWater.clear();
      }

      if (nowLinked != linked) {
         linked = nowLinked;
         SkyCraft.LOG.info("SkyCraft: Skyrim link {}", linked ? "up" : "down");
         if (linked) {
            tookOver = true;
            unlinkedHold = null;
            SkyCollision.startConsumer();
            applyLinkedOptions();
         } else {
            InputBridge.releaseAll();
            LocalPlayer player = Minecraft.getInstance().player;
            unlinkedHold = player != null ? player.position() : null;
         }
      }

      if (linked) {
         Minecraft minecraft = Minecraft.getInstance();
         hideWindowOnce(minecraft);
         applyViewportSize(minecraft);
         MirrorWorld.openWhenReady(minecraft);
         if (sky.menuOpen() || sky.loading()) {
            InputBridge.releaseAll();
         }

         InputBridge.drain(minecraft);
         ProxySync.frame(minecraft);
         LocalPlayer player = minecraft.player;
         if (player == null) {
            lastPlayer = null;
         } else {
            if (player != lastPlayer) {
               lastPlayer = player;
               teleportPending = true;
            }

            if (sky.teleportSeq != lastTeleportSeq) {
               lastTeleportSeq = sky.teleportSeq;
               teleportPending = true;
            }

            if (teleportPending && sky.inGame() && !sky.loading()) {
               requestTeleport(minecraft, sky.x, sky.y, sky.z, sky.yaw, sky.pitch);
               teleportAck = sky.teleportSeq;
               teleportPending = false;
               holdPos = new Vec3(sky.x, sky.y, sky.z);
            }

            boolean riding = (sky.flags & 32) != 0;
            if (!riding || wasRiding && player.tickCount % 40 != 0) {
               if (!riding && wasRiding) {
                  minecraft.gui.hud.setOverlayMessage(Component.literal("Off the ride").withStyle(ChatFormatting.AQUA), false);
               }
            } else {
               minecraft.gui.hud.setOverlayMessage(Component.literal("On board: riding to the next stop. Space: get off").withStyle(ChatFormatting.AQUA), false);
            }

            wasRiding = riding;
            if ((sky.flags & 16) != 0 && lastTeleport != null) {
               player.setDeltaMovement(Vec3.ZERO);
               player.stopFallFlying();
               player.setPos(lastTeleport.x, lastTeleport.y, lastTeleport.z);
               player.xo = lastTeleport.x;
               player.yo = lastTeleport.y;
               player.zo = lastTeleport.z;
               player.resetFallDistance();
            }

            if (minecraft.gui.screen() == null) {
               player.setYRot(sky.yaw);
               player.setXRot(sky.pitch);
               player.yRotO = sky.yaw;
               player.xRotO = sky.pitch;
            }

         }
      }
   }

   private static void quitWithSkyrim(Minecraft minecraft) {
      int pid = SkyLink.skyrimPid();
      long now = System.currentTimeMillis();
      if (QUIT_WITH_SKYRIM && START_HIDDEN && pid == 0 && !tookOver && !gaveUpWaiting && now - STARTED_AT > 600000L) {
         gaveUpWaiting = true;
         SkyCraft.LOG.warn("SkyCraft: started hidden but Skyrim never connected in {} minutes; quitting", 10L);
         minecraft.stop();
      } else if (QUIT_WITH_SKYRIM && pid != 0 && now >= nextSkyrimCheck) {
         nextSkyrimCheck = now + 1000L;
         if ((Boolean)ProcessHandle.of((long)pid).map(ProcessHandle::isAlive).orElse(false)) {
            skyrimGoneSince = 0L;
         } else {
            if (skyrimGoneSince == 0L) {
               skyrimGoneSince = now;
            } else if (now - skyrimGoneSince > 5000L) {
               SkyCraft.LOG.info("SkyCraft: Skyrim (pid {}) has closed; saving and quitting", pid);
               minecraft.stop();
            }

         }
      }
   }

   public static void clientTick(Minecraft minecraft) {
      MirrorWorld.tick(minecraft);
      DiscordPresence.tick(minecraft);
      SkyDigClient.tick(minecraft);
      freezeWhileUnlinked(minecraft);
      holdUntilReady(minecraft);
      publishTick(minecraft);
      ghostSpeed(minecraft);
   }

   private static void ghostSpeed(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      if (player != null) {
         AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
         if (speed != null) {
            boolean ghost = SkyLink.active() && (sky.flags & 8) != 0;
            if (ghost && !speed.hasModifier(GHOST_SPEED)) {
               speed.addTransientModifier(new AttributeModifier(GHOST_SPEED, (double)0.5F, Operation.ADD_MULTIPLIED_TOTAL));
            } else if (!ghost && speed.hasModifier(GHOST_SPEED)) {
               speed.removeModifier(GHOST_SPEED);
            }

         }
      }
   }

   private static void freezeWhileUnlinked(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      if (!linked && tookOver && player != null) {
         if (unlinkedHold == null) {
            unlinkedHold = player.position();
         }

         player.setDeltaMovement(Vec3.ZERO);
         player.stopFallFlying();
         player.setPos(unlinkedHold.x, unlinkedHold.y, unlinkedHold.z);
         player.xo = unlinkedHold.x;
         player.yo = unlinkedHold.y;
         player.zo = unlinkedHold.z;
         player.resetFallDistance();
      }
   }

   private static void publishTick(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      if (linked && player != null) {
         if (qpcFreq == 0L) {
            qpcFreq = SkyLink.qpcFrequency();
         }

         float tickMs = minecraft.level != null ? minecraft.level.tickRateManager().millisecondsPerTick() : 50.0F;
         float remainder = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         mc.tickQpc = SkyLink.qpc() - (long)((double)(remainder * tickMs * (float)qpcFreq) / (double)1000.0F);
         mc.tickMs = tickMs;
         mc.prevX = player.xo;
         mc.prevY = player.yo;
         mc.prevZ = player.zo;
         mc.curX = player.getX();
         mc.curY = player.getY();
         mc.curZ = player.getZ();
         if (player != eyePlayer) {
            eyePlayer = player;
            eyeSmoothed = player.getEyeHeight();
         }

         mc.eyeHeightO = eyeSmoothed;
         eyeSmoothed += (player.getEyeHeight() - eyeSmoothed) * 0.5F;
         mc.eyeHeightT = eyeSmoothed;
         boolean bob = (Boolean)minecraft.options.bobView().get();
         ClientAvatarState avatar = player.avatarState();
         mc.walkDistO = bob ? avatar.getInterpolatedWalkDistance(0.0F) : 0.0F;
         mc.walkDist = bob ? avatar.getInterpolatedWalkDistance(1.0F) : 0.0F;
         mc.bobO = bob ? avatar.getInterpolatedBob(0.0F) : 0.0F;
         mc.bob = bob ? avatar.getInterpolatedBob(1.0F) : 0.0F;
         SkyLink.writeMcState(mc);
      }
   }

   private static void holdUntilReady(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      if (linked && player != null) {
         if (!sky.inGame() || sky.loading()) {
            if (holdPos == null) {
               holdPos = player.position();
            }

            teleportPending = true;
         }

         if (holdPos == null) {
            holdSince = 0L;
         } else {
            if (holdSince == 0L) {
               holdSince = System.currentTimeMillis();
            }

            int bx = (int)Math.floor(holdPos.x);
            int by = (int)Math.floor(holdPos.y);
            int bz = (int)Math.floor(holdPos.z);
            boolean known = SkyCollision.isKnown(bx, by - 1, bz) && SkyCollision.isKnown(bx, by, bz) && SkyCollision.isKnown(bx, by - 8, bz);
            boolean ready = known && (SkyCollision.hasSolidBelow(bx, by, bz, 12) || System.currentTimeMillis() - holdSince > 6000L);
            if (ready && sky.inGame() && !sky.loading()) {
               Vec3 safe = liftOutOfGeometry(player, holdPos);
               if (safe.y != holdPos.y) {
                  player.setPos(safe.x, safe.y, safe.z);
                  player.yo = safe.y;
                  SkyCraft.LOG.info("SkyCraft: lifted player {} blocks out of the ground", String.format("%.3f", safe.y - holdPos.y));
               }

               holdPos = null;
            } else {
               player.setDeltaMovement(Vec3.ZERO);
               player.stopFallFlying();
               player.setPos(holdPos.x, holdPos.y, holdPos.z);
               player.xo = holdPos.x;
               player.yo = holdPos.y;
               player.zo = holdPos.z;
               player.resetFallDistance();
            }
         }
      }
   }

   private static Vec3 liftOutOfGeometry(LocalPlayer player, Vec3 pos) {
      double ground = SkyCollider.groundAt(pos.x, pos.y, pos.z, (double)2.5F);
      return !Double.isNaN(ground) && ground > pos.y ? new Vec3(pos.x, ground, pos.z) : pos;
   }

   private static void requestTeleport(Minecraft minecraft, double x, double y, double z, float yaw, float pitch) {
      LocalPlayer player = minecraft.player;
      lastTeleport = new Vec3(x, y, z);
      player.setPos(x, y, z);
      player.setDeltaMovement(Vec3.ZERO);
      player.resetFallDistance();
      player.stopFallFlying();
      IntegratedServer server = minecraft.getSingleplayerServer();
      if (server != null) {
         UUID uuid = player.getUUID();
         server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) {
               sp.teleportTo(x, y, z);
               sp.stopFallFlying();
               sp.setYRot(yaw);
               sp.setXRot(pitch);
               sp.resetFallDistance();
            }

         });
      }

      SkyCraft.LOG.info("SkyCraft: teleported to {} {} {}", new Object[]{x, y, z});
   }

   public static void afterRender() {
      if (linked) {
         Minecraft minecraft = Minecraft.getInstance();
         LocalPlayer player = minecraft.player;
         int flags = 0;
         if (player != null && minecraft.level != null) {
            float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 feet = player.getPosition(partial);
            Camera camera = minecraft.gameRenderer.mainCamera();
            flags |= 1;
            if (player.onGround()) {
               flags |= 4;
            }

            if (player.isShiftKeyDown()) {
               flags |= 8;
            }

            if (player.isSprinting()) {
               flags |= 16;
            }

            if (player.isDeadOrDying()) {
               flags |= 32;
            }

            if (player.isSwimming()) {
               flags |= 64;
            }

            if (player.getAbilities().flying) {
               flags |= 128;
            }

            mc.x = feet.x;
            mc.y = feet.y;
            mc.z = feet.z;
            mc.yaw = player.getYRot();
            mc.pitch = player.getXRot();
            Vec3 eye = camera.isDetached() ? player.getEyePosition(partial) : camera.position();
            mc.eyeHeight = (float)(eye.y - feet.y);
            mc.eyeX = eye.x;
            mc.eyeY = eye.y;
            mc.eyeZ = eye.z;
            mc.fov = camera.getFov();
            mc.cameraMode = minecraft.options.getCameraType().ordinal();
            mc.health = player.getHealth();
            mc.maxHealth = player.getMaxHealth();
            mc.mouseSensitivity = ((Double)minecraft.options.sensitivity().get()).floatValue();
            mc.cameraDistance = camera.isDetached() ? (float)camera.position().distanceTo(player.getEyePosition(partial)) : 0.0F;
            CameraEntityRenderState entityState = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.entityRenderState;
            boolean bob = (Boolean)minecraft.options.bobView().get() && entityState.isPlayer;
            mc.bobPhase = bob ? entityState.backwardsInterpolatedWalkDistance : 0.0F;
            mc.bobAmount = bob ? entityState.bob : 0.0F;
         }

         if (minecraft.gui.screen() != null && !(minecraft.gui.screen() instanceof LevelLoadingScreen)) {
            flags |= 2;
         }

         mc.flags = flags;
         mc.sensitivity = ((Double)minecraft.options.sensitivity().get()).floatValue();
         mc.teleportAck = holdPos == null ? teleportAck : teleportAck - 1;
         mc.guiScale = minecraft.getWindow().getGuiScale();
         mc.frameCounter = ++frameCounter;
         SkyLink.writeMcState(mc);
         if ((flags & 1) != 0) {
            try {
               WorldExporter.frame(minecraft, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            } catch (RuntimeException e) {
               if (exporterErrors++ < 5) {
                  SkyCraft.LOG.error("SkyCraft: world export failed", e);
               }
            }

            FrameExporter.capture(minecraft);
         }

      }
   }

   public static void paceFrame() {
      if (linked) {
         if (!skyrimStalled || SkyLink.skyStateSeq() >>> 1 != lastPacedSeq) {
            skyrimStalled = false;
            long deadline = System.nanoTime() + 25000000L;

            while(SkyLink.skyStateSeq() >>> 1 == lastPacedSeq && System.nanoTime() < deadline) {
               Thread.onSpinWait();
               if (deadline - System.nanoTime() > 2000000L) {
                  Thread.yield();
               }
            }

            int seqNow = SkyLink.skyStateSeq() >>> 1;
            skyrimStalled = seqNow == lastPacedSeq;
            lastPacedSeq = seqNow;
         }
      }
   }

   private static void applyLinkedOptions() {
      Minecraft minecraft = Minecraft.getInstance();
      Options options = minecraft.options;
      options.pauseOnLostFocus = false;
      options.vignette().set(false);
      options.enableVsync().set(false);
      options.framerateLimit().set(260);
      options.renderDistance().set(8);
      options.simulationDistance().set(8);
      options.autoJump().set(false);
      options.onboardAccessibility = false;
      if (options.tutorialStep != TutorialSteps.NONE) {
         minecraft.getTutorial().setStep(TutorialSteps.NONE);
      }

      options.getSoundSourceOptionInstance(SoundSource.MUSIC).set((double)0.0F);
      options.save();
   }

   private static void hideWindowOnce(Minecraft minecraft) {
      if (!windowHidden && !SHOW_WINDOW) {
         windowHidden = true;
         SDLVideo.SDL_HideWindow(minecraft.getWindow().handle());
         SkyCraft.LOG.info("SkyCraft: game window hidden (run with -Dskycraft.showWindow=true to keep it)");
      }
   }

   private static void applyViewportSize(Minecraft minecraft) {
      int w = Math.min(sky.viewportW, 3840);
      int h = Math.min(sky.viewportH, 2160);
      if (w > 0 && h > 0 && (w != appliedViewportW || h != appliedViewportH)) {
         appliedViewportW = w;
         appliedViewportH = h;
         if ((sky.flags & 64) == 0) {
            hudViewportW = w;
            guiFactor = 1;
         } else {
            guiFactor = hudViewportW > 0 ? Math.max(1, Math.round((float)w / (float)hudViewportW)) : 1;
         }

         minecraft.getWindow().setWindowed(w, h);
         SkyCraft.LOG.info("SkyCraft: sizing overlay to Skyrim viewport {}x{}", w, h);
      }
   }
}
