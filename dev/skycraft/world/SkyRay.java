package dev.skycraft.world;

import java.util.List;

public final class SkyRay {
   private SkyRay() {
   }

   public static Hit cast(List<SkyTri> tris, double fx, double fy, double fz, double tx, double ty, double tz) {
      double dx = tx - fx;
      double dy = ty - fy;
      double dz = tz - fz;
      double best = Double.POSITIVE_INFINITY;
      SkyTri hitTri = null;

      for(SkyTri tri : tris) {
         if (!tri.stairHelper) {
            double t = intersect(tri, fx, fy, fz, dx, dy, dz);
            if (t >= (double)0.0F && t <= (double)1.0F && t < best) {
               best = t;
               hitTri = tri;
            }
         }
      }

      if (hitTri == null) {
         return null;
      } else {
         double nx = hitTri.nx;
         double ny = hitTri.ny;
         double nz = hitTri.nz;
         if (nx * dx + ny * dy + nz * dz > (double)0.0F) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
         }

         return new Hit(best, fx + dx * best, fy + dy * best, fz + dz * best, nx, ny, nz, hitTri);
      }
   }

   static double intersect(SkyTri tri, double ox, double oy, double oz, double dx, double dy, double dz) {
      double e1x = tri.bx - tri.ax;
      double e1y = tri.by - tri.ay;
      double e1z = tri.bz - tri.az;
      double e2x = tri.cx - tri.ax;
      double e2y = tri.cy - tri.ay;
      double e2z = tri.cz - tri.az;
      double px = dy * e2z - dz * e2y;
      double py = dz * e2x - dx * e2z;
      double pz = dx * e2y - dy * e2x;
      double det = e1x * px + e1y * py + e1z * pz;
      if (Math.abs(det) < 1.0E-12) {
         return (double)-1.0F;
      } else {
         double inv = (double)1.0F / det;
         double sx = ox - tri.ax;
         double sy = oy - tri.ay;
         double sz = oz - tri.az;
         double u = (sx * px + sy * py + sz * pz) * inv;
         if (!(u < -1.0E-9) && !(u > 1.000000001)) {
            double qx = sy * e1z - sz * e1y;
            double qy = sz * e1x - sx * e1z;
            double qz = sx * e1y - sy * e1x;
            double v = (dx * qx + dy * qy + dz * qz) * inv;
            return !(v < -1.0E-9) && !(u + v > 1.000000001) ? (e2x * qx + e2y * qy + e2z * qz) * inv : (double)-1.0F;
         } else {
            return (double)-1.0F;
         }
      }
   }

   public static int[] placementCell(Hit hit) {
      double out = 0.4;
      return new int[]{(int)Math.floor(hit.x + hit.nx * out), (int)Math.floor(hit.y + hit.ny * out), (int)Math.floor(hit.z + hit.nz * out)};
   }

   public static int[] surfaceCell(Hit hit) {
      double in = 0.01;
      return new int[]{(int)Math.floor(hit.x - hit.nx * in), (int)Math.floor(hit.y - hit.ny * in), (int)Math.floor(hit.z - hit.nz * in)};
   }

   public static int dominantFace(double nx, double ny, double nz) {
      double ax = Math.abs(nx);
      double ay = Math.abs(ny);
      double az = Math.abs(nz);
      if (ay >= ax && ay >= az) {
         return ny >= (double)0.0F ? 1 : 0;
      } else if (ax >= az) {
         return nx >= (double)0.0F ? 5 : 4;
      } else {
         return nz >= (double)0.0F ? 3 : 2;
      }
   }

   public static record Hit(double t, double x, double y, double z, double nx, double ny, double nz, SkyTri tri) {
   }
}
