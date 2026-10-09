package dev.skycraft.client;

import dev.skycraft.CharacterGear;
import dev.skycraft.Progression;
import dev.skycraft.SkyCraft;
import dev.skycraft.WowGifts;
import dev.skycraft.combat.SkyCombat;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.sdl.SDLKeyboard;
import org.slf4j.Logger;

public final class InputBridge {
   private static final boolean[] KEYS = new boolean[512];
   private static final boolean[] BUTTONS = new boolean[8];
   private static double cursorX;
   private static double cursorY;
   private static int modifiers;
   private static int clickLogs;

   private InputBridge() {
   }

   public static boolean isKeyDown(int scancode) {
      return scancode >= 0 && scancode < KEYS.length && KEYS[scancode];
   }

   public static void drain(Minecraft minecraft) {
      SkyLink.drainInput((type, code, a, b, c) -> dispatch(minecraft, type, code, a, b, c));
   }

   private static void dispatch(Minecraft minecraft, int type, int code, int a, int b, int c) {
      long handle = minecraft.getWindow().handle();
      switch (type) {
         case 1:
            key(minecraft, handle, code, a != 0);
            break;
         case 2:
            if (code > 0 && code < BUTTONS.length) {
               BUTTONS[code] = a != 0;
            }

            if (a != 0 && clickLogs++ < 20) {
               HitResult hit = minecraft.hitResult;
               Logger var10000 = SkyCraft.LOG;
               Object[] var10002 = new Object[]{code, hit == null ? "null" : hit.getType(), null, null, null};
               Object var10005;
               if (hit instanceof EntityHitResult) {
                  EntityHitResult eh = (EntityHitResult)hit;
                  var10005 = eh.getEntity().getName().getString();
               } else {
                  var10005 = hit == null ? "" : hit.getLocation();
               }

               var10002[2] = var10005;
               var10002[3] = minecraft.mouseHandler.isMouseGrabbed();
               var10002[4] = minecraft.gui.screen();
               var10000.info("SkyCraft: click {} -> {} {} (grabbed {}, screen {})", var10002);
            }

            minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(code, modifiers), a != 0 ? 1 : 0);
            break;
         case 3:
            minecraft.mouseHandler.onScroll(handle, (double)0.0F, (double)a / (double)120.0F);
            break;
         case 4:
            double dx = (double)a - cursorX;
            double dy = (double)b - cursorY;
            cursorX = (double)a;
            cursorY = (double)b;
            minecraft.mouseHandler.onMove(handle, (double)a, (double)b, dx, dy);
            break;
         case 5:
            if (minecraft.gui.screen() != null) {
               minecraft.keyboardHandler.textInput(handle, new String(Character.toChars(a)));
            }
            break;
         case 6:
            releaseAll();
            break;
         case 7:
            hurt(minecraft, code, (float)a / 100.0F, b, c);
            break;
         case 8:
            if (minecraft.gui.screen() == null && minecraft.player != null) {
               releaseAll();
               minecraft.gui.setScreen(new PauseScreen(true));
            }
            break;
         case 9:
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server == null) {
               CoopClient.give(code, a, b);
            } else if (minecraft.player != null) {
               UUID uuid = minecraft.player.getUUID();
               server.execute(() -> {
                  ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                  if (player != null) {
                     WowGifts.give(player, code, a, b);
                  }

               });
            }
            break;
         case 10:
            int item = code & 255;
            int count = code >>> 8;
            double x = (double)a / (double)8.0F;
            double y = (double)b / (double)8.0F;
            double z = (double)c / (double)8.0F;
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server == null) {
               CoopClient.drop(item, count, a, b, c);
            } else if (minecraft.player != null) {
               UUID uuid = minecraft.player.getUUID();
               server.execute(() -> {
                  ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                  if (player != null) {
                     WowGifts.drop(player, item, count, x, y, z);
                  }

               });
            }
            break;
         case 11:
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server == null) {
               CoopClient.tier(code);
            } else if (minecraft.player != null) {
               UUID uuid = minecraft.player.getUUID();
               server.execute(() -> {
                  ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                  if (player != null) {
                     Progression.reach(player, code);
                  }

               });
            }
            break;
         case 12:
            long character = (long)a & 4294967295L | (long)b << 32;
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server == null) {
               CoopClient.character(character);
            } else if (minecraft.player != null) {
               UUID uuid = minecraft.player.getUUID();
               server.execute(() -> {
                  ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                  if (player != null) {
                     CharacterGear.playing(player, character);
                  }

               });
            }
      }

   }

   private static void hurt(Minecraft minecraft, int kind, float skyrimDamage, int attacker, int flags) {
      IntegratedServer server = minecraft.getSingleplayerServer();
      if (minecraft.player != null) {
         if (server == null) {
            if (ClientPlayNetworking.canSend(SkyNet.Hurt.TYPE)) {
               ClientPlayNetworking.send(new SkyNet.Hurt(kind, skyrimDamage, attacker, flags));
            }

         } else {
            UUID uuid = minecraft.player.getUUID();
            server.execute(() -> {
               ServerPlayer player = server.getPlayerList().getPlayer(uuid);
               if (player != null) {
                  SkyCombat.hurtPlayer(player, kind, skyrimDamage, attacker, flags);
               }

            });
         }
      }
   }

   private static void key(Minecraft minecraft, long handle, int scancode, boolean down) {
      if (scancode > 0 && scancode < KEYS.length) {
         boolean wasDown = KEYS[scancode];
         KEYS[scancode] = down;
         updateModifiers();
         int action = down ? (wasDown ? -1 : 1) : 0;
         int keycode = SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short)modifiers, true);
         minecraft.keyboardHandler.keyPress(handle, action, new KeyEvent(scancode, keycode, modifiers));
      }
   }

   private static void updateModifiers() {
      int m = 0;
      if (KEYS[225]) {
         m |= 1;
      }

      if (KEYS[229]) {
         m |= 2;
      }

      if (KEYS[224]) {
         m |= 64;
      }

      if (KEYS[228]) {
         m |= 128;
      }

      if (KEYS[226]) {
         m |= 256;
      }

      if (KEYS[230]) {
         m |= 512;
      }

      modifiers = m;
   }

   public static void releaseAll() {
      Minecraft minecraft = Minecraft.getInstance();
      long handle = minecraft.getWindow().handle();

      for(int sc = 0; sc < KEYS.length; ++sc) {
         if (KEYS[sc]) {
            KEYS[sc] = false;
            updateModifiers();
            minecraft.keyboardHandler.keyPress(handle, 0, new KeyEvent(sc, SDLKeyboard.SDL_GetKeyFromScancode(sc, (short)0, true), modifiers));
         }
      }

      for(int button = 1; button < BUTTONS.length; ++button) {
         if (BUTTONS[button]) {
            BUTTONS[button] = false;
            minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(button, 0), 0);
         }
      }

   }
}
