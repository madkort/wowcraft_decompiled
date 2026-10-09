package dev.skycraft.net;

import dev.skycraft.SkyCraft;
import dev.skycraft.WowCraftConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Properties;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class Accounts {
   private static final BigInteger N = new BigInteger("894B645E89E1535BBDAD5B8B290650530801B18EBFBF5E8FAB3C82872A3E9BB7", 16);
   private static final BigInteger G = BigInteger.valueOf(7L);
   private static final Path STORE = Path.of("wowcraft-guests.properties");
   private static final SecureRandom RANDOM = new SecureRandom();

   private Accounts() {
   }

   static void giveAccount(ServerPlayer player) {
      MinecraftServer server = player.level().getServer();
      if (!SkyNet.isHost(player) && !server.usesAuthentication() && !WowCraftConfig.on("allow_offline_guests", "WOWCRAFT_ALLOW_OFFLINE_GUESTS")) {
         SkyCraft.LOG.warn("WowCraft: {} joined an offline-mode world; no WoW account (anyone could claim that name)", player.getName().getString());
      } else {
         String uuid = player.getUUID().toString();
         String wanted = accountName(player.getName().getString());
         Thread t = new Thread(() -> {
            String[] account;
            try {
               account = ensure(uuid, wanted);
            } catch (Exception e) {
               SkyCraft.LOG.warn("WowCraft: couldn't make a WoW account for {}", wanted, e);
               return;
            }

            String user = account[0];
            String pass = account[1];
            player.level().getServer().execute(() -> {
               if (!player.hasDisconnected() && ServerPlayNetworking.canSend(player, CoopNet.Account.TYPE)) {
                  ServerPlayNetworking.send(player, new CoopNet.Account(user, pass));
                  SkyCraft.LOG.info("WowCraft: {} can log into WoW as {}", player.getName().getString(), user);
               }

            });
         }, "wowcraft-account");
         t.setDaemon(true);
         t.start();
      }
   }

   static String accountName(String minecraft) {
      String s = minecraft.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
      return s.isEmpty() ? "GUEST" : s.substring(0, Math.min(16, s.length()));
   }

   private static synchronized String[] ensure(String uuid, String wanted) throws IOException, InterruptedException {
      Properties known = new Properties();
      if (Files.exists(STORE, new LinkOption[0])) {
         InputStream in = Files.newInputStream(STORE);

         try {
            known.load(in);
         } catch (Throwable var12) {
            if (in != null) {
               try {
                  in.close();
               } catch (Throwable var10) {
                  var12.addSuppressed(var10);
               }
            }

            throw var12;
         }

         if (in != null) {
            in.close();
         }
      }

      String user = known.getProperty("account." + uuid);
      boolean fresh = user == null;
      if (fresh) {
         user = wanted;

         for(int i = 2; known.containsValue(user); ++i) {
            String n = String.valueOf(i);
            String var10000 = wanted.substring(0, Math.min(wanted.length(), 16 - n.length()));
            user = var10000 + n;
         }
      }

      String pass = known.getProperty("password." + user);
      if (pass == null) {
         pass = randomPassword();
      }

      run(accountSql(user, pass, 3));
      if (fresh || !pass.equals(known.getProperty("password." + user))) {
         known.setProperty("account." + uuid, user);
         known.setProperty("password." + user, pass);
         OutputStream out = Files.newOutputStream(STORE);

         try {
            known.store(out, "WowCraft co-op: each guest's WoW account (by Minecraft UUID) on this server");
         } catch (Throwable var11) {
            if (out != null) {
               try {
                  out.close();
               } catch (Throwable var9) {
                  var11.addSuppressed(var9);
               }
            }

            throw var11;
         }

         if (out != null) {
            out.close();
         }
      }

      return new String[]{user, pass};
   }

   private static String randomPassword() {
      String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < 12; ++i) {
         sb.append(letters.charAt(RANDOM.nextInt(letters.length())));
      }

      return sb.toString();
   }

   private static void run(String sql) throws IOException, InterruptedException {
      String client = (String)Objects.requireNonNullElse(WowCraftConfig.get("mariadb", "WOWCRAFT_MARIADB"), "mariadb");
      String port = (String)Objects.requireNonNullElse(WowCraftConfig.get("mariadb_port", "WOWCRAFT_MARIADB_PORT"), "3306");
      Process p = (new ProcessBuilder(new String[]{client, "-h", "127.0.0.1", "-P", port, "-u", "root", "realmd", "-e", sql})).redirectErrorStream(true).start();
      String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (p.waitFor() != 0) {
         throw new IOException("mariadb failed: " + out.strip());
      }
   }

   static String accountSql(String user, String pass, int gmlevel) {
      user = user.toUpperCase();
      pass = pass.toUpperCase();
      byte[] digest = sha1((user + ":" + pass).getBytes(StandardCharsets.UTF_8));

      BigInteger salt;
      do {
         byte[] raw = new byte[32];
         RANDOM.nextBytes(raw);
         salt = new BigInteger(1, raw);
      } while(salt.bitLength() <= 248);

      byte[] saltLe = littleEndian(salt);
      byte[] xHash = sha1(concat(saltLe, digest));
      BigInteger x = new BigInteger(1, reverse(xHash));
      BigInteger v = G.modPow(x, N);
      String s = salt.toString(16).toUpperCase();
      String vHex = v.toString(16).toUpperCase();
      return "INSERT INTO account (username, gmlevel, v, s, joindate) VALUES ('" + user + "', " + gmlevel + ", '" + vHex + "', '" + s + "', NOW()) ON DUPLICATE KEY UPDATE v = VALUES(v), s = VALUES(s);\nREPLACE INTO realmcharacters (realmid, acctid, numchars) SELECT realmlist.id, account.id, 0 FROM realmlist, account LEFT JOIN realmcharacters ON acctid = account.id WHERE acctid IS NULL;\nREPLACE INTO account_access (id, gmlevel, RealmID) SELECT id, " + gmlevel + ", -1 FROM account WHERE username = '" + user + "';\n";
   }

   private static byte[] sha1(byte[] in) {
      try {
         return MessageDigest.getInstance("SHA-1").digest(in);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   private static byte[] littleEndian(BigInteger n) {
      byte[] be = n.toByteArray();
      int start = be.length > 1 && be[0] == 0 ? 1 : 0;
      byte[] out = new byte[be.length - start];

      for(int i = 0; i < out.length; ++i) {
         out[i] = be[be.length - 1 - i];
      }

      return out;
   }

   private static byte[] reverse(byte[] in) {
      byte[] out = new byte[in.length];

      for(int i = 0; i < in.length; ++i) {
         out[i] = in[in.length - 1 - i];
      }

      return out;
   }

   private static byte[] concat(byte[] a, byte[] b) {
      byte[] out = new byte[a.length + b.length];
      System.arraycopy(a, 0, out, 0, a.length);
      System.arraycopy(b, 0, out, a.length, b.length);
      return out;
   }
}
