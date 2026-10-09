package dev.skycraft.net;

import dev.skycraft.CharacterGear;
import dev.skycraft.Progression;
import dev.skycraft.SkyCraft;
import dev.skycraft.WowCraftConfig;
import dev.skycraft.WowGifts;
import dev.skycraft.combat.OreNodes;
import dev.skycraft.link.SkyLink;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class CoopNet {
   public static final int REALM_PORT = 3724;
   public static final int WORLD_PORT = 8085;
   public static final int CHUNK = 16384;
   private static final Map<UUID, Remote> REMOTE = new ConcurrentHashMap();
   private static byte[] lastHidden = new byte[0];

   private CoopNet() {
   }

   private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
      return new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("skycraft", name));
   }

   public static List<SkyLink.Actor> remoteActors() {
      long now = System.currentTimeMillis();
      REMOTE.values().removeIf((rx) -> now - rx.at() > 3000L);
      List<SkyLink.Actor> out = new ArrayList();

      for(Remote r : REMOTE.values()) {
         out.addAll(r.actors());
      }

      return out;
   }

   public static void event(@Nullable ServerPlayer player, int kind, int formId, float a, float b, float c, float d, int flags, int weapon) {
      if (player != null && !SkyNet.isHost(player)) {
         if (ServerPlayNetworking.canSend(player, CoopNet.Event.TYPE)) {
            ServerPlayNetworking.send(player, new Event(kind, formId, a, b, c, d, flags, weapon));
         }
      } else {
         SkyLink.pushEvent(kind, formId, a, b, c, d, flags, weapon);
      }

   }

   public static void shareHidden(MinecraftServer server, long[] hidden, boolean force) {
      ByteBuffer buf = ByteBuffer.allocate(hidden.length * 8).order(ByteOrder.LITTLE_ENDIAN);

      for(long l : hidden) {
         buf.putLong(l);
      }

      byte[] packed = buf.array();
      if (force || !Arrays.equals(packed, lastHidden)) {
         lastHidden = packed;

         for(ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!SkyNet.isHost(p) && ServerPlayNetworking.canSend(p, CoopNet.Hidden.TYPE)) {
               ServerPlayNetworking.send(p, new Hidden(packed));
            }
         }

      }
   }

   public static void init() {
      PayloadTypeRegistry.serverboundPlay().register(CoopNet.Actors.TYPE, CoopNet.Actors.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CoopNet.Give.TYPE, CoopNet.Give.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CoopNet.Drop.TYPE, CoopNet.Drop.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CoopNet.Tier.TYPE, CoopNet.Tier.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CoopNet.Character.TYPE, CoopNet.Character.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CoopNet.TunnelOpen.TYPE, CoopNet.TunnelOpen.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CoopNet.TunnelUp.TYPE, CoopNet.TunnelUp.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(CoopNet.TunnelClose.TYPE, CoopNet.TunnelClose.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(CoopNet.Event.TYPE, CoopNet.Event.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(CoopNet.Hidden.TYPE, CoopNet.Hidden.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(CoopNet.Account.TYPE, CoopNet.Account.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(CoopNet.TunnelDown.TYPE, CoopNet.TunnelDown.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(CoopNet.TunnelCloseDown.TYPE, CoopNet.TunnelCloseDown.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(CoopNet.Actors.TYPE, (payload, context) -> REMOTE.put(context.player().getUUID(), new Remote(SkyLink.unpackActors(payload.packed()), System.currentTimeMillis())));
      ServerPlayNetworking.registerGlobalReceiver(CoopNet.Give.TYPE, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> WowGifts.give(player, payload.code(), Math.min(payload.count(), 256), Math.min(payload.enchant(), 3)));
      });
      ServerPlayNetworking.registerGlobalReceiver(CoopNet.Character.TYPE, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> CharacterGear.playing(player, payload.character()));
      });
      ServerPlayNetworking.registerGlobalReceiver(CoopNet.Tier.TYPE, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> Progression.reach(player, payload.tier()));
      });
      ServerPlayNetworking.registerGlobalReceiver(CoopNet.Drop.TYPE, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> WowGifts.drop(player, payload.code(), Math.min(payload.count(), 64), (double)payload.x8() / (double)8.0F, (double)payload.y8() / (double)8.0F, (double)payload.z8() / (double)8.0F));
      });
      ServerPlayNetworking.registerGlobalReceiver(CoopNet.TunnelOpen.TYPE, (payload, context) -> CoopNet.Tunnel.open(context.player(), payload.id(), payload.port()));
      ServerPlayNetworking.registerGlobalReceiver(CoopNet.TunnelUp.TYPE, (payload, context) -> CoopNet.Tunnel.write(context.player(), payload.id(), payload.data()));
      ServerPlayNetworking.registerGlobalReceiver(CoopNet.TunnelClose.TYPE, (payload, context) -> CoopNet.Tunnel.close(context.player(), payload.id()));
      ServerPlayConnectionEvents.JOIN.register((ServerPlayConnectionEvents.Join)(handler, sender, server) -> {
         ServerPlayer player = handler.getPlayer();
         if (SkyNet.isHost(player)) {
            if (WowCraftConfig.on("host_account", "WOWCRAFT_HOST_ACCOUNT")) {
               Accounts.giveAccount(player);
            }

         } else {
            shareHidden(server, OreNodes.hiddenNow(), true);
            Accounts.giveAccount(player);
         }
      });
      ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayConnectionEvents.Disconnect)(handler, server) -> {
         REMOTE.remove(handler.getPlayer().getUUID());
         CoopNet.Tunnel.closeAll(handler.getPlayer().getUUID());
      });
   }

   public static record Actors(byte[] packed) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Actors> TYPE = CoopNet.<Actors>typeOf("coop_actors");
      public static final StreamCodec<RegistryFriendlyByteBuf, Actors> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, Actors::packed, Actors::new);
      }
   }

   public static record Event(int kind, int formId, float a, float b, float c, float d, int flags, int weapon) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Event> TYPE = CoopNet.<Event>typeOf("coop_event");
      public static final StreamCodec<RegistryFriendlyByteBuf, Event> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Event::kind, ByteBufCodecs.INT, Event::formId, ByteBufCodecs.FLOAT, Event::a, ByteBufCodecs.FLOAT, Event::b, ByteBufCodecs.FLOAT, Event::c, ByteBufCodecs.FLOAT, Event::d, ByteBufCodecs.INT, Event::flags, ByteBufCodecs.INT, Event::weapon, Event::new);
      }
   }

   public static record Give(int code, int count, int enchant) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Give> TYPE = CoopNet.<Give>typeOf("coop_give");
      public static final StreamCodec<RegistryFriendlyByteBuf, Give> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Give::code, ByteBufCodecs.VAR_INT, Give::count, ByteBufCodecs.VAR_INT, Give::enchant, Give::new);
      }
   }

   public static record Character(long character) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Character> TYPE = CoopNet.<Character>typeOf("coop_character");
      public static final StreamCodec<RegistryFriendlyByteBuf, Character> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_LONG, Character::character, Character::new);
      }
   }

   public static record Tier(int tier) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Tier> TYPE = CoopNet.<Tier>typeOf("coop_tier");
      public static final StreamCodec<RegistryFriendlyByteBuf, Tier> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Tier::tier, Tier::new);
      }
   }

   public static record Drop(int code, int count, int x8, int y8, int z8) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Drop> TYPE = CoopNet.<Drop>typeOf("coop_drop");
      public static final StreamCodec<RegistryFriendlyByteBuf, Drop> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Drop::code, ByteBufCodecs.VAR_INT, Drop::count, ByteBufCodecs.VAR_INT, Drop::x8, ByteBufCodecs.VAR_INT, Drop::y8, ByteBufCodecs.VAR_INT, Drop::z8, Drop::new);
      }
   }

   public static record Hidden(byte[] packed) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Hidden> TYPE = CoopNet.<Hidden>typeOf("coop_hidden");
      public static final StreamCodec<RegistryFriendlyByteBuf, Hidden> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, Hidden::packed, Hidden::new);
      }
   }

   public static record Account(String user, String pass) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Account> TYPE = CoopNet.<Account>typeOf("coop_account");
      public static final StreamCodec<RegistryFriendlyByteBuf, Account> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Account::user, ByteBufCodecs.STRING_UTF8, Account::pass, Account::new);
      }
   }

   public static record TunnelOpen(int id, int port) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<TunnelOpen> TYPE = CoopNet.<TunnelOpen>typeOf("coop_tunnel_open");
      public static final StreamCodec<RegistryFriendlyByteBuf, TunnelOpen> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TunnelOpen::id, ByteBufCodecs.VAR_INT, TunnelOpen::port, TunnelOpen::new);
      }
   }

   public static record TunnelDown(int id, byte[] data) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<TunnelDown> TYPE = CoopNet.<TunnelDown>typeOf("coop_tunnel_down");
      public static final StreamCodec<RegistryFriendlyByteBuf, TunnelDown> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TunnelDown::id, ByteBufCodecs.BYTE_ARRAY, TunnelDown::data, TunnelDown::new);
      }
   }

   public static record TunnelUp(int id, byte[] data) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<TunnelUp> TYPE = CoopNet.<TunnelUp>typeOf("coop_tunnel_up");
      public static final StreamCodec<RegistryFriendlyByteBuf, TunnelUp> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TunnelUp::id, ByteBufCodecs.BYTE_ARRAY, TunnelUp::data, TunnelUp::new);
      }
   }

   public static record TunnelClose(int id) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<TunnelClose> TYPE = CoopNet.<TunnelClose>typeOf("coop_tunnel_close_up");
      public static final StreamCodec<RegistryFriendlyByteBuf, TunnelClose> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TunnelClose::id, TunnelClose::new);
      }
   }

   public static record TunnelCloseDown(int id) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<TunnelCloseDown> TYPE = CoopNet.<TunnelCloseDown>typeOf("coop_tunnel_close_down");
      public static final StreamCodec<RegistryFriendlyByteBuf, TunnelCloseDown> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TunnelCloseDown::id, TunnelCloseDown::new);
      }
   }

   private static record Remote(List<SkyLink.Actor> actors, long at) {
   }

   static final class Tunnel {
      private static final Map<Key, Socket> SOCKETS = new ConcurrentHashMap();

      static void open(ServerPlayer player, int id, int port) {
         if (port == 3724 || port == 8085) {
            Key key = new Key(player.getUUID(), id);

            Socket socket;
            try {
               socket = new Socket(InetAddress.getLoopbackAddress(), port);
               socket.setTcpNoDelay(true);
            } catch (IOException var6) {
               SkyCraft.LOG.warn("WowCraft: the WoW server isn't answering on port {} for {}", port, player.getName().getString());
               player.level().getServer().execute(() -> ServerPlayNetworking.send(player, new TunnelCloseDown(id)));
               return;
            }

            SOCKETS.put(key, socket);
            Thread t = new Thread(() -> {
               try {
                  Socket twrVar0$ = socket;

                  try {
                     InputStream in = socket.getInputStream();
                     byte[] buf = new byte[16384];

                     int n;
                     while((n = in.read(buf)) > 0) {
                        byte[] data = Arrays.copyOf(buf, n);
                        player.level().getServer().execute(() -> {
                           if (!player.hasDisconnected()) {
                              ServerPlayNetworking.send(player, new TunnelDown(id, data));
                           }

                        });
                     }
                  } catch (Throwable var15) {
                     if (socket != null) {
                        try {
                           twrVar0$.close();
                        } catch (Throwable x2) {
                           var15.addSuppressed(x2);
                        }
                     }

                     throw var15;
                  }

                  if (socket != null) {
                     socket.close();
                  }
               } catch (IOException var16) {
               } finally {
                  if (SOCKETS.remove(key) != null) {
                     player.level().getServer().execute(() -> {
                        if (!player.hasDisconnected()) {
                           ServerPlayNetworking.send(player, new TunnelCloseDown(id));
                        }

                     });
                  }

               }

            }, "wowcraft-tunnel-" + id);
            t.setDaemon(true);
            t.start();
         }
      }

      static void write(ServerPlayer player, int id, byte[] data) {
         Socket socket = (Socket)SOCKETS.get(new Key(player.getUUID(), id));
         if (socket != null) {
            try {
               OutputStream out = socket.getOutputStream();
               out.write(data);
               out.flush();
            } catch (IOException var5) {
               close(player, id);
            }

         }
      }

      static void close(ServerPlayer player, int id) {
         Socket socket = (Socket)SOCKETS.remove(new Key(player.getUUID(), id));
         if (socket != null) {
            try {
               socket.close();
            } catch (IOException var4) {
            }
         }

      }

      static void closeAll(UUID player) {
         for(Map.Entry<Key, Socket> e : SOCKETS.entrySet()) {
            if (((Key)e.getKey()).player().equals(player)) {
               try {
                  ((Socket)e.getValue()).close();
               } catch (IOException var4) {
               }
            }
         }

      }

      private static record Key(UUID player, int id) {
      }
   }
}
