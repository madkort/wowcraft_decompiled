package dev.skycraft;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class WowCraftConfig {
   private WowCraftConfig() {
   }

   public static String get(String key, String env) {
      String value = System.getenv(env);
      if (value != null && !value.isBlank()) {
         return value.trim();
      } else {
         Path file = FabricLoader.getInstance().getConfigDir().resolve("skycraft.properties");
         if (!Files.exists(file, new LinkOption[0])) {
            return null;
         } else {
            Properties props = new Properties();

            try {
               Reader in = Files.newBufferedReader(file);

               try {
                  props.load(in);
               } catch (Throwable var9) {
                  if (in != null) {
                     try {
                        in.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (in != null) {
                  in.close();
               }
            } catch (IOException var10) {
               return null;
            }

            String v = props.getProperty(key, "").trim();
            return v.isEmpty() ? null : v;
         }
      }
   }

   public static boolean on(String key, String env) {
      String v = get(key, env);
      return v != null && !v.equals("0") && !v.equalsIgnoreCase("false");
   }
}
