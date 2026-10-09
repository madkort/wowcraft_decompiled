package dev.skycraft.client;

import dev.skycraft.SkyCraft;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyTri;
import dev.skycraft.world.TriCollider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SkyCollider {
   private static long lastCeilingLog;
   private static @Nullable Vec3 fallStart;
   private static int fallsDumped;
   private static int clipsSeen;
   private static int clipsDumped;
   private static long lastClipLog;
   private static long lastUnstreamedLog;

   private SkyCollider() {
   }

   public static Vec3 collide(LocalPlayer player, Vec3 move) {
      AABB box = player.getBoundingBox();
      double step = (double)player.maxUpStep();
      List<SkyTri> tris = new ArrayList();
      SkyCollision.trianglesNear(box.expandTowards(move).inflate((double)1.0F, (double)1.0F + step, (double)1.0F), tris);
      logUnstreamed(box, move);
      watchFall(player, box);
      if (tris.isEmpty()) {
         return move;
      } else {
         double[] r = TriCollider.resolve(tris, (box.minX + box.maxX) * (double)0.5F, box.minY, (box.minZ + box.maxZ) * (double)0.5F, box.getXsize() * (double)0.5F, box.getYsize(), step, player.onGround(), move.x, move.y, move.z);
         if (TriCollider.clipsRefused != clipsSeen) {
            clipsSeen = TriCollider.clipsRefused;
            logClip(tris);
         }

         if (r[0] == move.x && r[1] == move.y && r[2] == move.z) {
            return move;
         } else {
            if (move.y > 0.05 && r[1] < move.y * (double)0.5F) {
               logCeiling(tris, box, r);
            }

            return Entity.collideBoundingBox(player, new Vec3(r[0], r[1], r[2]), box, player.level(), List.of());
         }
      }
   }

   private static void watchFall(LocalPlayer player, AABB box) {
      Vec3 feet = new Vec3((box.minX + box.maxX) * (double)0.5F, box.minY, (box.minZ + box.maxZ) * (double)0.5F);
      if (!player.onGround() && !player.isFallFlying() && !player.getAbilities().flying && !player.isInWater()) {
         if (fallStart != null && !(fallStart.y - feet.y < (double)8.0F) && fallsDumped < 3) {
            ++fallsDumped;
            Vec3 start = fallStart;
            fallStart = null;
            List<SkyTri> near = new ArrayList();
            SkyCollision.trianglesNear(new AABB(start.x - (double)4.0F, start.y - (double)3.0F, start.z - (double)4.0F, start.x + (double)4.0F, start.y + (double)4.0F, start.z + (double)4.0F), near);
            StringBuilder out = new StringBuilder();
            out.append("# fall from ").append(start.x).append(' ').append(start.y).append(' ').append(start.z).append(" to ").append(feet.x).append(' ').append(feet.y).append(' ').append(feet.z).append('\n');
            out.append("# regions here: start ").append(SkyCollision.hasRegionAt(start.x, start.y, start.z)).append('\n');
            out.append("# triangles within 4 blocks of the start: ax ay az bx by bz cx cy cz walkable terrain stairHelper").append('\n');

            for(SkyTri t : near) {
               out.append(t.ax).append(' ').append(t.ay).append(' ').append(t.az).append(' ').append(t.bx).append(' ').append(t.by).append(' ').append(t.bz).append(' ').append(t.cx).append(' ').append(t.cy).append(' ').append(t.cz).append(' ').append(t.walkable ? 1 : 0).append(' ').append(t.terrain ? 1 : 0).append(' ').append(t.stairHelper ? 1 : 0).append('\n');
            }

            try {
               Files.writeString(Path.of("wowcraft-fall-" + fallsDumped + ".txt"), out);
               SkyCraft.LOG.info("WowCraft: a fall from {} {} {} ({} triangles near its start) saved to wowcraft-fall-{}.txt", new Object[]{fmt(start.x), fmt(start.y), fmt(start.z), near.size(), fallsDumped});
            } catch (IOException e) {
               SkyCraft.LOG.warn("WowCraft: couldn't write the fall dump", e);
            }

         }
      } else {
         fallStart = player.onGround() ? feet : null;
      }
   }

   private static void logClip(List<SkyTri> tris) {
      double[] c = TriCollider.lastClip;
      if (c != null) {
         long now = System.currentTimeMillis();
         if (now - lastClipLog >= 1000L) {
            lastClipLog = now;
            SkyCraft.LOG.info("WowCraft: refused a move into WoW's geometry at {} {} {} (asked {} {} {}, on ground {}; {} so far)", new Object[]{fmt(c[0]), fmt(c[1]), fmt(c[2]), fmt(c[3]), fmt(c[4]), fmt(c[5]), c[9] != (double)0.0F, TriCollider.clipsRefused});
         }

         if (clipsDumped < 3) {
            ++clipsDumped;
            StringBuilder out = new StringBuilder();
            out.append("# start x y z, asked x y z, would-be x y z, on ground").append('\n');

            for(double v : c) {
               out.append(v).append(' ');
            }

            out.append('\n').append("# triangles: ax ay az bx by bz cx cy cz walkable terrain stairHelper").append('\n');

            for(SkyTri t : tris) {
               out.append(t.ax).append(' ').append(t.ay).append(' ').append(t.az).append(' ').append(t.bx).append(' ').append(t.by).append(' ').append(t.bz).append(' ').append(t.cx).append(' ').append(t.cy).append(' ').append(t.cz).append(' ').append(t.walkable ? 1 : 0).append(' ').append(t.terrain ? 1 : 0).append(' ').append(t.stairHelper ? 1 : 0).append('\n');
            }

            try {
               Files.writeString(Path.of("wowcraft-clip-" + clipsDumped + ".txt"), out);
            } catch (IOException e) {
               SkyCraft.LOG.warn("WowCraft: couldn't write the clip dump", e);
            }

         }
      }
   }

   private static void logUnstreamed(AABB box, Vec3 move) {
      if (!(move.horizontalDistanceSqr() < 0.01) || !(Math.abs(move.y) < 0.1)) {
         double x = (box.minX + box.maxX) * (double)0.5F + move.x;
         double z = (box.minZ + box.maxZ) * (double)0.5F + move.z;
         double feet = box.minY + move.y;
         double head = box.maxY + move.y;
         if (!SkyCollision.hasRegionAt(x, feet, z) || !SkyCollision.hasRegionAt(x, head, z)) {
            long now = System.currentTimeMillis();
            if (now - lastUnstreamedLog >= 1000L) {
               lastUnstreamedLog = now;
               SkyCraft.LOG.info("WowCraft: moving where WoW's collision hasn't arrived: {} {} {} (speed {} blocks/tick)", new Object[]{fmt(x), fmt(feet), fmt(z), fmt(move.length())});
            }
         }
      }
   }

   private static void logCeiling(List<SkyTri> tris, AABB box, double[] r) {
      long now = System.currentTimeMillis();
      if (now - lastCeilingLog >= 1000L) {
         lastCeilingLog = now;
         double x = (box.minX + box.maxX) * (double)0.5F + r[0];
         double z = (box.minZ + box.maxZ) * (double)0.5F + r[2];
         double head = box.minY + r[1] + box.getYsize();
         SkyTri t = TriCollider.ceilingTri(tris, x, head, z, box.getXsize() * (double)0.5F * 0.8);
         if (t == null) {
            SkyCraft.LOG.info("WowCraft: jump cut at {} {} {} by no triangle (a Minecraft block?)", new Object[]{fmt(x), fmt(head), fmt(z)});
         } else {
            SkyCraft.LOG.info("WowCraft: jump cut at {} {} {} by a {} triangle ({} {} {} / {} {} {} / {} {} {}, normal y {}, material {})", new Object[]{fmt(x), fmt(head), fmt(z), t.terrain ? "terrain" : "object", fmt(t.ax), fmt(t.ay), fmt(t.az), fmt(t.bx), fmt(t.by), fmt(t.bz), fmt(t.cx), fmt(t.cy), fmt(t.cz), fmt(t.ny), t.material});
         }
      }
   }

   private static String fmt(double v) {
      return String.format(Locale.ROOT, "%.2f", v);
   }

   public static double groundAt(double x, double y, double z, double maxAbove) {
      List<SkyTri> tris = new ArrayList();
      SkyCollision.trianglesNear(new AABB(x - (double)1.0F, y - (double)4.0F, z - (double)1.0F, x + (double)1.0F, y + maxAbove + (double)1.0F, z + (double)1.0F), tris);
      return TriCollider.groundAt(tris, x, y, z, maxAbove);
   }
}
