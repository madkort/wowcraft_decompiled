package dev.skycraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skycraft.SkyCraft;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

public final class DiscordPresence {
   private static final String APP_ID = System.getProperty("skycraft.discordAppId", "1554889902309642280");
   private static final Pattern E4MC_LINK = Pattern.compile("[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.e4mc\\.link", 2);
   private static final int OP_HANDSHAKE = 0;
   private static final int OP_FRAME = 1;
   private static final int OP_CLOSE = 2;
   private static final int OP_PING = 3;
   private static final int OP_PONG = 4;
   private static final long START = System.currentTimeMillis() / 1000L;
   private static volatile @Nullable String hostLink;
   private static volatile @Nullable String wantedActivity;
   private static long nextDescribe;
   private static @Nullable RandomAccessFile pipe;
   private static @Nullable FileInputStream in;
   private static @Nullable String sentActivity;
   private static long lastSent;
   private static boolean loggedNoDiscord;
   private static int nonce;

   private DiscordPresence() {
   }

   public static void start() {
      if (!"0".equals(APP_ID) && !APP_ID.isBlank()) {
         Thread thread = new Thread(DiscordPresence::run, "SkyCraft Discord");
         thread.setDaemon(true);
         thread.start();
      } else {
         SkyCraft.LOG.info("SkyCraft: Discord Rich Presence off (no application id)");
      }
   }

   public static boolean mentionsLink(Component line) {
      if (E4MC_LINK.matcher(line.getString()).find()) {
         return true;
      } else {
         boolean[] found = new boolean[]{false};
         line.visit((style, text) -> {
            ClickEvent click = style.getClickEvent();
            if (click != null && E4MC_LINK.matcher(click.toString()).find()) {
               found[0] = true;
            }

            HoverEvent hover = style.getHoverEvent();
            if (hover != null && E4MC_LINK.matcher(hover.toString()).find()) {
               found[0] = true;
            }

            return Optional.empty();
         }, Style.EMPTY);
         return found[0];
      }
   }

   public static void onChat(Component message) {
      String link = findLink(message);
      if (link != null) {
         hostLink = link;
         SkyCraft.LOG.info("SkyCraft: this world is open to friends at {}", link);
         if (Minecraft.getInstance().getSingleplayerServer() != null) {
            try {
               Path file = Path.of("wowcraft-coop-link.txt");
               Path tmp = Path.of("wowcraft-coop-link.txt.tmp");
               Files.writeString(tmp, link);
               Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
               SkyCraft.LOG.warn("WowCraft: couldn't save the join link", e);
            }
         }
      }

   }

   private static @Nullable String findLink(Component message) {
      Matcher m = E4MC_LINK.matcher(message.getString());
      if (m.find()) {
         return m.group().toLowerCase();
      } else {
         for(Component part : message.toFlatList()) {
            ClickEvent var5 = part.getStyle().getClickEvent();
            if (var5 instanceof ClickEvent.CopyToClipboard) {
               ClickEvent.CopyToClipboard copy = (ClickEvent.CopyToClipboard)var5;
               m = E4MC_LINK.matcher(copy.value());
               if (m.find()) {
                  return m.group().toLowerCase();
               }
            }
         }

         return null;
      }
   }

   public static void tick(Minecraft minecraft) {
      long now = System.currentTimeMillis();
      if (now >= nextDescribe) {
         nextDescribe = now + 1000L;
         IntegratedServer server = minecraft.getSingleplayerServer();
         if (server == null || !server.isPublished()) {
            hostLink = null;
         }

         JsonObject activity = null;
         if (minecraft.player != null && minecraft.getConnection() != null) {
            int players = minecraft.getConnection().getOnlinePlayers().size();
            String friend = MirrorWorld.friendAddress(minecraft);
            activity = new JsonObject();
            activity.addProperty("details", "Playing World of Warcraft as a Minecraft player");
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", START);
            activity.add("timestamps", timestamps);
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", "skycraft");
            assets.addProperty("large_text", "SkyCraft");
            activity.add("assets", assets);
            String joinable = hostLink != null ? hostLink : friend;
            if (hostLink != null) {
               activity.addProperty("state", "Hosting a world");
            } else if (friend != null) {
               activity.addProperty("state", "In a friend's world");
            } else {
               activity.addProperty("state", "Playing solo");
            }

            if (joinable != null) {
               JsonObject party = new JsonObject();
               party.addProperty("id", "skycraft-" + Integer.toHexString(joinable.hashCode()));
               JsonArray size = new JsonArray();
               size.add(Math.max(players, 1));
               size.add(server != null ? Math.max(server.getMaxPlayers(), players + 1) : Math.max(100, players + 1));
               party.add("size", size);
               activity.add("party", party);
               JsonObject secrets = new JsonObject();
               secrets.addProperty("join", joinable);
               activity.add("secrets", secrets);
            }
         }

         JsonObject args = new JsonObject();
         args.addProperty("pid", ProcessHandle.current().pid());
         args.add("activity", activity);
         wantedActivity = args.toString();
      }
   }

   private static void run() {
      while(true) {
         try {
            if (pipe == null && !connect()) {
               Thread.sleep(15000L);
            } else {
               String wanted = wantedActivity;
               long now = System.currentTimeMillis();
               if (wanted != null && !wanted.equals(sentActivity) && now - lastSent > 5000L) {
                  command("SET_ACTIVITY", JsonParser.parseString(wanted).getAsJsonObject(), (String)null);
                  sentActivity = wanted;
                  lastSent = now;
               }

               readAvailable();
               Thread.sleep(250L);
            }
         } catch (IOException e) {
            SkyCraft.LOG.info("SkyCraft: Discord connection closed ({}); retrying later", e.getMessage());
            close();
         } catch (InterruptedException var4) {
            return;
         } catch (RuntimeException e) {
            SkyCraft.LOG.warn("SkyCraft: Discord Rich Presence error", e);
            close();
         }
      }
   }

   private static boolean connect() throws IOException, InterruptedException {
      int i = 0;

      while(true) {
         if (i < 10) {
            try {
               pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
            } catch (IOException var5) {
               ++i;
               continue;
            }

            in = new FileInputStream(pipe.getFD());
            JsonObject hello = new JsonObject();
            hello.addProperty("v", 1);
            hello.addProperty("client_id", APP_ID);
            write(0, hello);
            long deadline = System.currentTimeMillis() + 5000L;

            while(System.currentTimeMillis() < deadline) {
               JsonObject msg = readFrame();
               if (msg != null && "READY".equals(str(msg, "evt"))) {
                  command("SUBSCRIBE", (JsonObject)null, "ACTIVITY_JOIN");
                  SkyCraft.LOG.info("SkyCraft: connected to Discord (Rich Presence)");
                  loggedNoDiscord = false;
                  sentActivity = null;
                  return true;
               }

               if (msg == null) {
                  Thread.sleep(100L);
               }
            }

            close();
            return false;
         }

         if (!loggedNoDiscord) {
            loggedNoDiscord = true;
            SkyCraft.LOG.info("SkyCraft: Discord isn't running; Rich Presence will connect when it is");
         }

         return false;
      }
   }

   private static void command(String cmd, @Nullable JsonObject args, @Nullable String evt) throws IOException {
      JsonObject frame = new JsonObject();
      frame.addProperty("cmd", cmd);
      frame.addProperty("nonce", Integer.toString(++nonce));
      if (args != null) {
         frame.add("args", args);
      }

      if (evt != null) {
         frame.addProperty("evt", evt);
      }

      write(1, frame);
   }

   private static void readAvailable() throws IOException {
      JsonObject msg;
      while((msg = readFrame()) != null) {
         if ("DISPATCH".equals(str(msg, "cmd")) && "ACTIVITY_JOIN".equals(str(msg, "evt")) && msg.has("data")) {
            String secret = str(msg.getAsJsonObject("data"), "secret");
            if (secret != null && E4MC_LINK.matcher(secret).matches()) {
               SkyCraft.LOG.info("SkyCraft: joining {} from Discord", secret);
               Minecraft.getInstance().execute(() -> MirrorWorld.joinFriend(Minecraft.getInstance(), secret));
            }
         } else if ("ERROR".equals(str(msg, "evt"))) {
            SkyCraft.LOG.warn("SkyCraft: Discord says {}", msg.get("data"));
         }
      }

   }

   private static void write(int op, JsonObject payload) throws IOException {
      byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
      ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
      frame.putInt(op).putInt(body.length).put(body);
      pipe.write(frame.array());
   }

   private static @Nullable JsonObject readFrame() throws IOException {
      while(in.available() >= 8) {
         byte[] header = new byte[8];
         pipe.readFully(header);
         ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
         int op = h.getInt();
         int length = h.getInt();
         byte[] body = new byte[length];
         pipe.readFully(body);
         String text = new String(body, StandardCharsets.UTF_8);
         switch (op) {
            case 1:
               return JsonParser.parseString(text).getAsJsonObject();
            case 2:
               throw new IOException("Discord closed the connection: " + text);
            case 3:
               ByteBuffer pong = ByteBuffer.allocate(8 + length).order(ByteOrder.LITTLE_ENDIAN);
               pong.putInt(4).putInt(length).put(body);
               pipe.write(pong.array());
         }
      }

      return null;
   }

   private static @Nullable String str(JsonObject o, String key) {
      return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
   }

   private static void close() {
      try {
         if (pipe != null) {
            pipe.close();
         }
      } catch (IOException var1) {
      }

      pipe = null;
      in = null;
      sentActivity = null;
   }
}
