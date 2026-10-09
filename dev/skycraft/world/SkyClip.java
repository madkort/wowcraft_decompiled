package dev.skycraft.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class SkyClip {
   private static final ThreadLocal<List<SkyTri>> SCRATCH = ThreadLocal.withInitial(ArrayList::new);
   private static final SkyTri STONE_WALL = new SkyTri(new float[9], 0, 770);

   private SkyClip() {
   }

   public static BlockHitResult refine(Vec3 from, Vec3 to, BlockHitResult vanilla, Use use) {
      SkyRay.Hit hit = cast(from, to);
      if (use == SkyClip.Use.PICK) {
         double limit = hit != null ? hit.t() : (double)1.0F;
         if (vanilla.getType() != Type.MISS) {
            limit = Math.min(limit, Math.sqrt(from.distanceToSqr(vanilla.getLocation()) / Math.max(from.distanceToSqr(to), 1.0E-9)));
         }

         SkyRay.Hit wall = digWall(from, to, limit);
         if (wall != null) {
            hit = wall;
            vanilla = BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
         }
      }

      if (hit == null) {
         return vanilla;
      } else {
         Vec3 location = new Vec3(hit.x(), hit.y(), hit.z());
         if (vanilla.getType() != Type.MISS && from.distanceToSqr(vanilla.getLocation()) <= from.distanceToSqr(location)) {
            return vanilla;
         } else {
            Direction face = Direction.values()[SkyRay.dominantFace(hit.nx(), hit.ny(), hit.nz())];
            int[] cell = use == SkyClip.Use.PICK ? SkyRay.placementCell(hit) : SkyRay.surfaceCell(hit);
            return new SkyrimHitResult(location, face, new BlockPos(cell[0], cell[1], cell[2]), hit);
         }
      }
   }

   static SkyRay.Hit digWall(Vec3 from, Vec3 to, double limit) {
      SkyDig.DugLookup dug = SkyDig.clientDug;
      if (dug == null) {
         return null;
      } else {
         double dx = to.x - from.x;
         double dy = to.y - from.y;
         double dz = to.z - from.z;
         int x = (int)Math.floor(from.x);
         int y = (int)Math.floor(from.y);
         int z = (int)Math.floor(from.z);
         int stepX = dx > (double)0.0F ? 1 : -1;
         int stepY = dy > (double)0.0F ? 1 : -1;
         int stepZ = dz > (double)0.0F ? 1 : -1;
         double tDeltaX = dx == (double)0.0F ? Double.POSITIVE_INFINITY : Math.abs((double)1.0F / dx);
         double tDeltaY = dy == (double)0.0F ? Double.POSITIVE_INFINITY : Math.abs((double)1.0F / dy);
         double tDeltaZ = dz == (double)0.0F ? Double.POSITIVE_INFINITY : Math.abs((double)1.0F / dz);
         double tMaxX = dx == (double)0.0F ? Double.POSITIVE_INFINITY : (dx > (double)0.0F ? (double)(x + 1) - from.x : from.x - (double)x) * tDeltaX;
         double tMaxY = dy == (double)0.0F ? Double.POSITIVE_INFINITY : (dy > (double)0.0F ? (double)(y + 1) - from.y : from.y - (double)y) * tDeltaY;
         double tMaxZ = dz == (double)0.0F ? Double.POSITIVE_INFINITY : (dz > (double)0.0F ? (double)(z + 1) - from.z : from.z - (double)z) * tDeltaZ;
         boolean wasDug = dug.isDug(x, y, z);
         SkyDig.Probe probe = null;

         for(int i = 0; i < 64; ++i) {
            double nx = (double)0.0F;
            double ny = (double)0.0F;
            double nz = (double)0.0F;
            double t;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
               t = tMaxX;
               tMaxX += tDeltaX;
               x += stepX;
               nx = (double)(-stepX);
            } else if (tMaxY <= tMaxZ) {
               t = tMaxY;
               tMaxY += tDeltaY;
               y += stepY;
               ny = (double)(-stepY);
            } else {
               t = tMaxZ;
               tMaxZ += tDeltaZ;
               z += stepZ;
               nz = (double)(-stepZ);
            }

            if (t > limit) {
               return null;
            }

            boolean isDug = dug.isDug(x, y, z);
            if (wasDug && !isDug) {
               double px = from.x + dx * t;
               double py = from.y + dy * t;
               double pz = from.z + dz * t;
               if (probe == null) {
                  probe = (new SkyDig.Probe()).around(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z), Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z));
               }

               if (probe.test(px - nx * 0.02, py - ny * 0.02, pz - nz * 0.02) > 0) {
                  return new SkyRay.Hit(t, px, py, pz, nx, ny, nz, probe.surface != null ? probe.surface : STONE_WALL);
               }
            }

            wasDug = isDug;
         }

         return null;
      }
   }

   public static SkyRay.Hit cast(Vec3 from, Vec3 to) {
      List<SkyTri> tris = (List)SCRATCH.get();
      tris.clear();
      SkyCollision.trianglesNear((new AABB(from, to)).inflate(0.01), tris);
      if (tris.isEmpty()) {
         return null;
      } else {
         SkyRay.Hit hit = SkyRay.cast(tris, from.x, from.y, from.z, to.x, to.y, to.z);
         tris.clear();
         return hit;
      }
   }

   public static enum Use {
      PROJECTILE,
      PICK;

      // $FF: synthetic method
      private static Use[] $values() {
         return new Use[]{PROJECTILE, PICK};
      }
   }

   public static final class SkyrimHitResult extends BlockHitResult {
      public final double nx;
      public final double ny;
      public final double nz;
      public final SkyRay.Hit hit;

      public SkyrimHitResult(Vec3 location, Direction direction, BlockPos pos, SkyRay.Hit hit) {
         super(location, direction, pos, false);
         this.nx = hit.nx();
         this.ny = hit.ny();
         this.nz = hit.nz();
         this.hit = hit;
      }
   }
}
