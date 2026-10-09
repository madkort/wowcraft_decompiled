package dev.skycraft.client;

import dev.skycraft.SkyCraft;
import dev.skycraft.client.render.WorldExporter;
import dev.skycraft.combat.OreNodes;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.CoopNet;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class CoopClient {
   public static final Path ACCOUNT_FILE = Path.of("wowcraft-coop-account.properties");
   private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
   private static final Map<Integer, Socket> SOCKETS = new ConcurrentHashMap();
   private static final List<ServerSocket> LISTENERS = new ArrayList();
   private static int actorTicks;
   private static final Path QUIT_REQUEST = Path.of("wowcraft-quit.request");
   private static final long STARTED = ManagementFactory.getRuntimeMXBean().getStartTime();

   private CoopClient() {
   }

   public static boolean guest() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft.getConnection() != null && minecraft.getSingleplayerServer() == null;
   }

   public static void init() {
      ClientPlayNetworking.registerGlobalReceiver(CoopNet.Event.TYPE, (p, context) -> SkyLink.pushEvent(p.kind(), p.formId(), p.a(), p.b(), p.c(), p.d(), p.flags(), p.weapon()));
      ClientPlayNetworking.registerGlobalReceiver(CoopNet.Hidden.TYPE, (p, context) -> {
         ByteBuffer b = ByteBuffer.wrap(p.packed()).order(ByteOrder.LITTLE_ENDIAN);
         long[] packed = new long[b.remaining() / 8];

         for(int i = 0; i < packed.length; ++i) {
            packed[i] = b.getLong();
         }

         for(long l : OreNodes.setRemoteHidden(packed)) {
            BlockPos pos = BlockPos.of(l);
            WorldExporter.markDirty(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()));
         }

      });
      ClientPlayNetworking.registerGlobalReceiver(CoopNet.Account.TYPE, (p, context) -> {
         try {
            Path tmp = ACCOUNT_FILE.resolveSibling(String.valueOf(ACCOUNT_FILE.getFileName()) + ".tmp");
            Files.writeString(tmp, "user=" + p.user() + "\npass=" + p.pass() + "\n");
            Files.move(tmp, ACCOUNT_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            SkyCraft.LOG.info("WowCraft: the host made our WoW account ({}); saved for the launcher", p.user());
         } catch (IOException e) {
            SkyCraft.LOG.warn("WowCraft: couldn't save the co-op account", e);
         }

      });
      ClientPlayNetworking.registerGlobalReceiver(CoopNet.TunnelDown.TYPE, (p, context) -> {
         Socket socket = (Socket)SOCKETS.get(p.id());
         if (socket != null) {
            try {
               socket.getOutputStream().write(p.data());
               socket.getOutputStream().flush();
            } catch (IOException var4) {
               closeLocal(p.id(), true);
            }
         }

      });
      ClientPlayNetworking.registerGlobalReceiver(CoopNet.TunnelCloseDown.TYPE, (p, context) -> closeLocal(p.id(), false));
      ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join)(handler, sender, minecraft) -> {
         if (minecraft.getSingleplayerServer() == null) {
            listen(Integer.getInteger("wowcraft.tunnel.realm", 3724), 3724);
            listen(Integer.getInteger("wowcraft.tunnel.world", 8085), 8085);
         }

      });
      ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect)(handler, minecraft) -> stop());
   }

   public static void tick(Minecraft minecraft) {
      if (actorTicks % 20 == 0 && Files.exists(QUIT_REQUEST, new LinkOption[0])) {
         boolean stale;
         try {
            stale = Files.getLastModifiedTime(QUIT_REQUEST).toMillis() < STARTED;
            Files.delete(QUIT_REQUEST);
         } catch (IOException var3) {
            stale = false;
         }

         if (stale) {
            SkyCraft.LOG.info("WowCraft: an old quit request from before this game started; ignored");
         } else {
            SkyCraft.LOG.info("WowCraft: the launcher asked us to quit; saving and stopping");
            minecraft.stop();
         }
      } else if (++actorTicks % 4 == 0 && guest() && ClientPlayNetworking.canSend(CoopNet.Actors.TYPE)) {
         List<SkyLink.Actor> actors = new ArrayList();
         if (SkyLink.readActors(actors)) {
            ClientPlayNetworking.send(new CoopNet.Actors(SkyLink.packActors(actors)));
         }

      }
   }

   public static boolean give(int code, int count, int enchant) {
      if (guest() && ClientPlayNetworking.canSend(CoopNet.Give.TYPE)) {
         ClientPlayNetworking.send(new CoopNet.Give(code, count, enchant));
         return true;
      } else {
         return false;
      }
   }

   public static boolean character(long character) {
      if (guest() && ClientPlayNetworking.canSend(CoopNet.Character.TYPE)) {
         ClientPlayNetworking.send(new CoopNet.Character(character));
         return true;
      } else {
         return false;
      }
   }

   public static boolean tier(int tier) {
      if (guest() && ClientPlayNetworking.canSend(CoopNet.Tier.TYPE)) {
         ClientPlayNetworking.send(new CoopNet.Tier(tier));
         return true;
      } else {
         return false;
      }
   }

   public static boolean drop(int code, int count, int x8, int y8, int z8) {
      if (guest() && ClientPlayNetworking.canSend(CoopNet.Drop.TYPE)) {
         ClientPlayNetworking.send(new CoopNet.Drop(code, count, x8, y8, z8));
         return true;
      } else {
         return false;
      }
   }

   private static void listen(int local, int port) {
      ServerSocket server;
      try {
         server = new ServerSocket(local, 8, InetAddress.getLoopbackAddress());
      } catch (IOException var6) {
         SkyCraft.LOG.warn("WowCraft: port {} is taken here; WoW co-op through Minecraft is off", port);
         return;
      }

      synchronized(LISTENERS) {
         LISTENERS.add(server);
      }

      SkyCraft.LOG.info("WowCraft: carrying WoW port {} to the host (here on {})", port, local);
      Thread accept = new Thread(() -> {
         while(!server.isClosed()) {
            try {
               Socket socket = server.accept();
               socket.setTcpNoDelay(true);
               int id = NEXT_ID.getAndIncrement();
               SOCKETS.put(id, socket);
               send(new CoopNet.TunnelOpen(id, port));
               pump(id, socket);
            } catch (IOException var4) {
               return;
            }
         }

      }, "wowcraft-coop-" + port);
      accept.setDaemon(true);
      accept.start();
   }

   private static void pump(int id, Socket socket) {
      Thread t = new Thread(() -> {
         try {
            Socket twrVar0$ = socket;

            try {
               InputStream in = socket.getInputStream();
               byte[] buf = new byte[16384];

               int n;
               while((n = in.read(buf)) > 0) {
                  send(new CoopNet.TunnelUp(id, Arrays.copyOf(buf, n)));
               }
            } catch (Throwable var12) {
               if (socket != null) {
                  try {
                     twrVar0$.close();
                  } catch (Throwable x2) {
                     var12.addSuppressed(x2);
                  }
               }

               throw var12;
            }

            if (socket != null) {
               socket.close();
            }
         } catch (IOException var13) {
         } finally {
            if (SOCKETS.remove(id) != null) {
               send(new CoopNet.TunnelClose(id));
            }

         }

      }, "wowcraft-coop-conn-" + id);
      t.setDaemon(true);
      t.start();
   }

   private static void send(CustomPacketPayload payload) {
      Minecraft.getInstance().execute(() -> {
         if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
         }

      });
   }

   private static void closeLocal(int id, boolean tell) {
      Socket socket = (Socket)SOCKETS.remove(id);
      if (socket != null) {
         try {
            socket.close();
         } catch (IOException var4) {
         }

         if (tell) {
            send(new CoopNet.TunnelClose(id));
         }
      }

   }

   private static void stop() {
      synchronized(LISTENERS) {
         for(ServerSocket s : LISTENERS) {
            try {
               s.close();
            } catch (IOException var5) {
            }
         }

         LISTENERS.clear();
      }

      for(Integer id : List.copyOf(SOCKETS.keySet())) {
         closeLocal(id, false);
      }

      OreNodes.setRemoteHidden(new long[0]);
   }
}
