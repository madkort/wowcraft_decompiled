package dev.skycraft.client;

import dev.skycraft.SkyCraft;
import dev.skycraft.world.SkyDig;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

public final class DestructionToggle {
   private static final String KEY = "destruction";

   private DestructionToggle() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("skycraft.properties");
   }

   public static void register() {
      load();
      ScreenEvents.AFTER_INIT.register((ScreenEvents.AfterInit)(minecraft, screen, width, height) -> {
         if (screen instanceof PauseScreen pause) {
            if (pause.showsPauseMenu() && minecraft.player != null) {
               Screens.getWidgets(screen).add(button(minecraft));
            }
         }

      });
   }

   private static Button button(Minecraft minecraft) {
      boolean host = minecraft.hasSingleplayerServer();
      Button button = Button.builder(label(), (b) -> {
         SkyDig.destruction = !SkyDig.destruction;
         b.setMessage(label());
         save();
         SkyCraft.LOG.info("SkyCraft: Skyrim destruction {}", SkyDig.destruction ? "on" : "off");
      }).bounds(4, 4, 150, 20).tooltip(Tooltip.create(Component.literal(host ? "Mining Skyrim's ground and rocks, and explosions, dig into Skyrim's world. Holes already dug stay either way." : "In a friend's world, their setting decides."))).build();
      button.active = host;
      return button;
   }

   private static Component label() {
      return Component.literal("Skyrim destruction: " + (SkyDig.destruction ? "On" : "Off"));
   }

   private static void load() {
      Properties props = new Properties();

      try {
         BufferedReader in = Files.newBufferedReader(file());

         try {
            props.load(in);
         } catch (Throwable var5) {
            if (in != null) {
               try {
                  in.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (in != null) {
            in.close();
         }
      } catch (NoSuchFileException var6) {
         return;
      } catch (IOException e) {
         SkyCraft.LOG.warn("SkyCraft: couldn't read {}", file(), e);
         return;
      }

      SkyDig.destruction = !"false".equalsIgnoreCase(props.getProperty("destruction", "true").trim());
   }

   private static void save() {
      Path file = file();
      String line = "destruction=" + SkyDig.destruction;

      try {
         List<String> lines = Files.exists(file, new LinkOption[0]) ? new ArrayList(Files.readAllLines(file)) : new ArrayList(List.of("# SkyCraft"));
         boolean found = false;

         for(int i = 0; i < lines.size(); ++i) {
            if (((String)lines.get(i)).trim().startsWith("destruction=")) {
               lines.set(i, line);
               found = true;
            }
         }

         if (!found) {
            lines.add("# Mining and explosions dig into Skyrim's world (the pause menu's \"Skyrim destruction\" button).");
            lines.add(line);
         }

         Files.createDirectories(file.getParent());
         Files.write(file, lines);
      } catch (IOException e) {
         SkyCraft.LOG.warn("SkyCraft: couldn't save {}", file, e);
      }

   }
}
