package dev.skycraft.client;

import dev.skycraft.SkyCraft;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jspecify.annotations.Nullable;

public final class MirrorWorld {
   private static final ResourceKey<WorldPreset> PRESET;
   private static boolean attempted;
   private static long lastLog;
   private static @Nullable String sessionJoin;
   private static @Nullable String pendingNote;

   private MirrorWorld() {
   }

   private static @Nullable String joinAddress(Minecraft minecraft) {
      Path file = minecraft.gameDirectory.toPath().resolve("config").resolve("skycraft.properties");
      Properties props = new Properties();

      try {
         if (!Files.exists(file, new LinkOption[0])) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "# SkyCraft\n# To play in a friend's world instead of your own: put their address after join=\n# (the link e4mc shows them when they open their world to LAN), then restart Minecraft.\njoin=\n");
         }

         BufferedReader in = Files.newBufferedReader(file);

         try {
            props.load(in);
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
      } catch (IOException e) {
         SkyCraft.LOG.warn("SkyCraft: couldn't read {}", file, e);
         return null;
      }

      String join = props.getProperty("join", "").trim();
      return join.isEmpty() ? null : join;
   }

   public static void joinFriend(Minecraft minecraft, String link) {
      String address = link.trim().replaceFirst("^[A-Za-z]+://", "").replaceAll("/+$", "");
      if (!address.isEmpty()) {
         SkyCraft.LOG.info("SkyCraft: /join {}", address);
         sessionJoin = address;
         leaveWorld(minecraft);
      }
   }

   public static @Nullable String friendAddress(Minecraft minecraft) {
      if (sessionJoin != null) {
         return sessionJoin;
      } else {
         ServerData server = minecraft.isLocalServer() ? null : minecraft.getCurrentServer();
         return server != null ? server.ip : null;
      }
   }

   public static void leaveFriend(Minecraft minecraft) {
      if (sessionJoin == null) {
         minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("You're already in your own world."));
      } else {
         SkyCraft.LOG.info("SkyCraft: /leave {}", sessionJoin);
         sessionJoin = null;
         pendingNote = "Back in your own world.";
         leaveWorld(minecraft);
      }
   }

   private static void leaveWorld(Minecraft minecraft) {
      attempted = false;
      minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
      minecraft.gui.setScreen(new TitleScreen());
   }

   public static void tick(Minecraft minecraft) {
      if (pendingNote != null && minecraft.player != null) {
         minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(pendingNote));
         pendingNote = null;
      }

   }

   public static void openWhenReady(Minecraft minecraft) {
      if (minecraft.gui.screen() instanceof DisconnectedScreen && minecraft.level == null) {
         pendingNote = sessionJoin != null ? "Couldn't stay in " + sessionJoin + " (check the link, and that your friend's world is still open to LAN). You're back in your own world." : "Disconnected. You're back in your own world.";
         SkyCraft.LOG.info("SkyCraft: disconnected; back to the mirror world");
         sessionJoin = null;
         attempted = false;
         minecraft.gui.setScreen(new TitleScreen());
      } else {
         if (attempted && minecraft.level == null && minecraft.gui.screen() != null && System.currentTimeMillis() - lastLog > 5000L) {
            lastLog = System.currentTimeMillis();
            SkyCraft.LOG.info("SkyCraft: still not in the mirror world; current screen {}", minecraft.gui.screen().getClass().getName());
         }

         if (!attempted && minecraft.level == null && minecraft.gui.overlay() == null) {
            if (!(minecraft.gui.screen() instanceof TitleScreen)) {
               if (minecraft.gui.screen() != null && System.currentTimeMillis() - lastLog > 5000L) {
                  lastLog = System.currentTimeMillis();
                  SkyCraft.LOG.info("SkyCraft: waiting on screen {} before opening the mirror world", minecraft.gui.screen().getClass().getName());
               }

               if (minecraft.gui.screen() == null || minecraft.gui.screen().getClass().getName().contains("Onboarding")) {
                  minecraft.gui.setScreen(new TitleScreen());
               }

            } else {
               TitleScreen title = (TitleScreen)minecraft.gui.screen();
               attempted = true;
               String join = sessionJoin != null ? sessionJoin : joinAddress(minecraft);
               if (join != null) {
                  SkyCraft.LOG.info("SkyCraft: joining {}", join);
                  pendingNote = "Joined " + join + ". Type /leave to go back to your own world.";
                  ConnectScreen.startConnecting(title, minecraft, ServerAddress.parseString(join), new ServerData("SkyCraft", join, Type.OTHER), false, (TransferState)null);
               } else if (minecraft.getLevelSource().levelExists("SkyCraft")) {
                  SkyCraft.LOG.info("SkyCraft: opening mirror world");
                  minecraft.createWorldOpenFlows().openWorld("SkyCraft", () -> minecraft.gui.setScreen(title));
               } else {
                  SkyCraft.LOG.info("SkyCraft: creating mirror world");
                  LevelSettings settings = new LevelSettings("SkyCraft", GameType.SURVIVAL, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
                  minecraft.createWorldOpenFlows().createFreshLevel("SkyCraft", settings, new WorldOptions(0L, false, false), (registries) -> ((WorldPreset)registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(PRESET).value()).createWorldDimensions(), title);
               }
            }
         }
      }
   }

   static {
      PRESET = ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("skycraft", "mirror"));
   }
}
