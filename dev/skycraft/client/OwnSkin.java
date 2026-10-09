package dev.skycraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.skycraft.SkyCraft;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

public final class OwnSkin {
   private static volatile byte @Nullable [] png;
   private static volatile boolean slim;
   private static @Nullable PlayerSkin skin;
   private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("skycraft", "own_skin");
   private static volatile boolean pending;

   private OwnSkin() {
   }

   public static void start() {
      String name = System.getenv("WOWCRAFT_SKIN");
      if (name != null && !name.isBlank()) {
         pending = true;
         Thread t = new Thread(() -> {
            try {
               HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
               String id = JsonParser.parseString(get(http, "https://api.mojang.com/users/profiles/minecraft/" + name.trim())).getAsJsonObject().get("id").getAsString();
               JsonObject profile = JsonParser.parseString(get(http, "https://sessionserver.mojang.com/session/minecraft/profile/" + id)).getAsJsonObject();
               String value = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
               JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("textures");
               JsonObject skinJson = textures.getAsJsonObject("SKIN");
               String url = skinJson.get("url").getAsString();
               slim = skinJson.has("metadata") && "slim".equals(skinJson.getAsJsonObject("metadata").get("model").getAsString());
               HttpResponse<byte[]> skin = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15L)).build(), BodyHandlers.ofByteArray());
               png = (byte[])skin.body();
               SkyCraft.LOG.info("WowCraft: using {}'s skin", name);
            } catch (Exception e) {
               SkyCraft.LOG.warn("WowCraft: couldn't fetch {}'s skin; keeping the default", name, e);
            } finally {
               pending = false;
            }

         }, "wowcraft-skin");
         t.setDaemon(true);
         t.start();
      }
   }

   private static String get(HttpClient http, String url) throws Exception {
      return (String)http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15L)).build(), BodyHandlers.ofString()).body();
   }

   public static boolean pending() {
      return pending;
   }

   public static @Nullable NativeImage image() {
      byte[] bytes = png;
      if (bytes == null) {
         return null;
      } else {
         try {
            return NativeImage.read(bytes);
         } catch (IOException var2) {
            return null;
         }
      }
   }

   public static @Nullable PlayerSkin skin() {
      if (skin == null && png != null) {
         Minecraft minecraft = Minecraft.getInstance();
         if (!minecraft.isSameThread()) {
            return null;
         } else {
            NativeImage image = image();
            if (image == null) {
               png = null;
               return null;
            } else {
               minecraft.getTextureManager().register(TEXTURE, new DynamicTexture(() -> "wowcraft own skin", image));
               skin = PlayerSkin.insecure(new ClientAsset.ResourceTexture(TEXTURE, TEXTURE), (ClientAsset.Texture)null, (ClientAsset.Texture)null, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
               SkyCraft.LOG.info("WowCraft: our player wears the fetched skin ({} arms)", slim ? "slim" : "wide");
               return skin;
            }
         }
      } else {
         return skin;
      }
   }

   public static Identifier texture() {
      return TEXTURE;
   }

   public static boolean isDefaultSkin(Identifier texture) {
      return texture.getPath().startsWith("textures/entity/player/");
   }
}
