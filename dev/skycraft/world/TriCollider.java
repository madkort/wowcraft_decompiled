package dev.skycraft.world;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class TriCollider {
   private static final double FLOOR_RADIUS = 0.15;
   private static final double SUBSTEP = 0.1;
   private static final double AIR_STEP = 0.3;
   private static final double EPS = 1.0E-4;
   private static final double[][] FLOOR_SAMPLES = buildSamples();
   private static final double SWEEP_MARGIN = 0.06;
   private static final double SWEEP_TOUCH = 0.015;
   static boolean SWEEP = true;
   public static volatile int clipsRefused;
   public static volatile double @Nullable [] lastClip;
   private static final double[][] EDGE_SAMPLES = buildEdgeSamples();

   private TriCollider() {
   }

   private static double[][] buildSamples() {
      List<double[]> samples = new ArrayList();
      samples.add(new double[]{(double)0.0F, (double)0.0F});

      for(int i = 0; i < 8; ++i) {
         double a = (double)i * Math.PI / (double)4.0F;
         samples.add(new double[]{Math.cos(a) * 0.15, Math.sin(a) * 0.15});
      }

      for(int i = 0; i < 4; ++i) {
         double a = (Math.PI / 4D) + (double)i * Math.PI / (double)2.0F;
         samples.add(new double[]{Math.cos(a) * 0.15 * (double)0.5F, Math.sin(a) * 0.15 * (double)0.5F});
      }

      return (double[][])samples.toArray(new double[0][]);
   }

   public static double[] resolve(List<SkyTri> tris, double x0, double y0, double z0, double radius, double height, double step, boolean wasOnGround, double mx, double my, double mz) {
      if (tris.isEmpty()) {
         return new double[]{mx, my, mz};
      } else {
         double x = x0;
         double y = y0;
         double z = z0;
         double horizontal = Math.hypot(mx, mz);
         int steps = Math.max(1, (int)Math.ceil(horizontal / 0.1));
         double wallFrom = wasOnGround ? step : 0.02;
         double climb = wasOnGround ? step : 0.3;
         boolean hitWall = false;

         for(int i = 0; i < steps; ++i) {
            double px = x;
            double pz = z;
            double var57 = x + mx / (double)steps;
            double var58 = z + mz / (double)steps;
            double[] out = pushOutOfWalls(tris, var57, y, var58, radius, height, wallFrom, step, x, y + height * (double)0.5F, z);
            hitWall |= out[0] != var57 || out[1] != var58;
            x = out[0];
            z = out[1];
            if (out[2] != (double)0.0F && !stuck(tris, px, y, pz, radius, height, wallFrom, step)) {
               x = px;
               z = pz;
               hitWall = true;
            } else {
               double rise = floor(tris, x, y, z, false, height);
               if (rise > y + climb) {
                  x = px;
                  z = pz;
                  hitWall = true;
               } else {
                  double walk = support(tris, x, y, z, true, climb, radius);
                  if (walk > y) {
                     y = walk;
                  }
               }
            }
         }

         double climbed = y - y0;
         double dy = my;
         if (my > (double)0.0F) {
            double ceiling = ceilingAbove(tris, x, y + height, z, radius * 0.8);
            if (!Double.isNaN(ceiling)) {
               dy = Math.max((double)0.0F, Math.min(my, ceiling - (y + height)));
            }
         }

         double walkUp = wasOnGround ? step : 0.3;
         double floorWalk = support(tris, x, y, z, true, walkUp, radius);
         double floorAny = support(tris, x, y, z, false, 1.0E-4, radius);
         double floor = Math.max(floorWalk, floorAny);
         double targetY = y + dy;
         double outY;
         if (targetY <= floor) {
            outY = floor - y0;
         } else if (wasOnGround && dy <= (double)0.0F && floorWalk > Double.NEGATIVE_INFINITY && y - floorWalk <= Math.max(step, horizontal * (double)1.5F) && floorWalk >= floor(tris, x, y, z, false, 1.0E-4) - 1.0E-4) {
            outY = floorWalk - y0;
         } else {
            outY = climbed == (double)0.0F ? dy : (dy == my && dy >= climbed ? my : climbed + dy);
         }

         if (outY < climbed) {
            double[] out = pushOutOfWalls(tris, x, y0 + outY, z, radius, height, wallFrom, step, x, y + height * (double)0.5F, z);
            if (out[2] != (double)0.0F && !stuck(tris, x, y, z, radius, height, wallFrom, step)) {
               outY = climbed;
            } else if (out[0] != x || out[1] != z) {
               x = out[0];
               z = out[1];
               hitWall = true;
            }
         }

         double[] out = new double[]{hitWall ? x - x0 : mx, outY, hitWall ? z - z0 : mz};
         double t = SWEEP ? sweep(tris, x0, y0, z0, out, radius, height) : (double)1.0F;
         if (t < (double)1.0F) {
            double len = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
            double k = Math.max((double)0.0F, t - 0.06 / Math.max(len, 1.0E-6));
            ++clipsRefused;
            lastClip = new double[]{x0, y0, z0, mx, my, mz, out[0], out[1], out[2], wasOnGround ? (double)1.0F : (double)0.0F};
            return new double[]{out[0] * k, out[1] * k, out[2] * k};
         } else if (throughBody(tris, x0 + out[0], y0 + out[1], z0 + out[2], height) && !throughBody(tris, x0, y0, z0, height)) {
            ++clipsRefused;
            lastClip = new double[]{x0, y0, z0, mx, my, mz, out[0], out[1], out[2], wasOnGround ? (double)1.0F : (double)0.0F};
            if (!throughBody(tris, x0, y0 + Math.min(out[1], (double)0.0F), z0, height)) {
               return new double[]{(double)0.0F, Math.min(out[1], (double)0.0F), (double)0.0F};
            } else {
               return new double[]{(double)0.0F, (double)0.0F, (double)0.0F};
            }
         } else {
            return out;
         }
      }
   }

   static double sweep(List<SkyTri> tris, double x0, double y0, double z0, double[] move, double radius, double height) {
      double dx = move[0];
      double dy = move[1];
      double dz = move[2];
      if (dx * dx + dy * dy + dz * dz < 1.0E-10) {
         return (double)1.0F;
      } else {
         double r = radius * 0.6;
         double[][] offsets = new double[][]{{(double)0.0F, (double)0.0F}, {r, (double)0.0F}, {-r, (double)0.0F}, {(double)0.0F, r}, {(double)0.0F, -r}};
         double[] heights = new double[]{0.65, (double)1.0F, 1.35, height - 0.15};
         double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
         double ignore = 0.015 / len;
         double minX = Math.min(x0, x0 + dx) - radius;
         double maxX = Math.max(x0, x0 + dx) + radius;
         double minZ = Math.min(z0, z0 + dz) - radius;
         double maxZ = Math.max(z0, z0 + dz) + radius;
         double minY = Math.min(y0, y0 + dy) + 0.6;
         double maxY = Math.max(y0, y0 + dy) + height;
         double best = (double)1.0F;

         for(SkyTri t : tris) {
            if (!t.stairHelper && !(t.maxX < minX) && !(t.minX > maxX) && !(t.maxZ < minZ) && !(t.minZ > maxZ) && !(t.maxY < minY) && !(t.minY > maxY)) {
               double centre = (x0 - t.ax) * t.nx + (y0 + height * (double)0.5F - t.ay) * t.ny + (z0 - t.az) * t.nz;

               for(double[] o : offsets) {
                  for(double h : heights) {
                     double side = (x0 + o[0] - t.ax) * t.nx + (y0 + h - t.ay) * t.ny + (z0 + o[1] - t.az) * t.nz;
                     if (!(side * centre < (double)0.0F)) {
                        double hit = segmentHit(t, x0 + o[0], y0 + h, z0 + o[1], dx, dy, dz);
                        if (hit > ignore && hit < best) {
                           best = hit;
                        }
                     }
                  }
               }
            }
         }

         return best;
      }
   }

   private static double segmentHit(SkyTri t, double px, double py, double pz, double dx, double dy, double dz) {
      double e1x = t.bx - t.ax;
      double e1y = t.by - t.ay;
      double e1z = t.bz - t.az;
      double e2x = t.cx - t.ax;
      double e2y = t.cy - t.ay;
      double e2z = t.cz - t.az;
      double hx = dy * e2z - dz * e2y;
      double hy = dz * e2x - dx * e2z;
      double hz = dx * e2y - dy * e2x;
      double a = e1x * hx + e1y * hy + e1z * hz;
      if (Math.abs(a) < 1.0E-12) {
         return (double)2.0F;
      } else {
         double f = (double)1.0F / a;
         double sx = px - t.ax;
         double sy = py - t.ay;
         double sz = pz - t.az;
         double u = f * (sx * hx + sy * hy + sz * hz);
         if (!(u < (double)0.0F) && !(u > (double)1.0F)) {
            double qx = sy * e1z - sz * e1y;
            double qy = sz * e1x - sx * e1z;
            double qz = sx * e1y - sy * e1x;
            double v = f * (dx * qx + dy * qy + dz * qz);
            if (!(v < (double)0.0F) && !(u + v > (double)1.0F)) {
               double s = f * (e2x * qx + e2y * qy + e2z * qz);
               return s >= (double)0.0F && s <= (double)1.0F ? s : (double)2.0F;
            } else {
               return (double)2.0F;
            }
         } else {
            return (double)2.0F;
         }
      }
   }

   static boolean throughBody(List<SkyTri> tris, double x, double y, double z, double height) {
      double lo = y + 0.35;
      double hi = y + height - 0.15;

      for(SkyTri t : tris) {
         if (!t.stairHelper && !(t.maxY < lo) && !(t.minY > hi) && !(x < t.minX) && !(x > t.maxX) && !(z < t.minZ) && !(z > t.maxZ)) {
            double h = t.heightAt(x, z);
            if (!Double.isNaN(h) && h > lo && h < hi) {
               return true;
            }
         }
      }

      return false;
   }

   private static double floor(List<SkyTri> tris, double x, double y, double z, boolean walkableOnly, double maxAbove) {
      double best = Double.NEGATIVE_INFINITY;
      double limit = y + maxAbove;

      for(SkyTri t : tris) {
         if ((!walkableOnly || t.walkable) && !(t.minY > limit)) {
            for(double[] s : FLOOR_SAMPLES) {
               double h = t.heightAt(x + s[0], z + s[1]);
               if (!Double.isNaN(h) && h <= limit && h > best) {
                  best = h;
               }
            }
         }
      }

      return best;
   }

   private static double support(List<SkyTri> tris, double x, double y, double z, boolean walkableOnly, double maxAbove, double radius) {
      double best = floor(tris, x, y, z, walkableOnly, maxAbove);
      double limit = y + maxAbove;
      double edge = Double.NEGATIVE_INFINITY;

      for(SkyTri t : tris) {
         if (t.walkable && !(t.minY > limit) && !(t.maxY < best + (double)0.5F)) {
            for(double[] s : EDGE_SAMPLES) {
               double h = t.heightAt(x + s[0] * radius, z + s[1] * radius);
               if (!Double.isNaN(h) && h <= limit && h > edge) {
                  edge = h;
               }
            }
         }
      }

      return edge > best + (double)0.5F ? edge : best;
   }

   private static double[][] buildEdgeSamples() {
      double[][] out = new double[8][];

      for(int i = 0; i < 8; ++i) {
         double a = (double)i * Math.PI / (double)4.0F;
         out[i] = new double[]{Math.cos(a), Math.sin(a)};
      }

      return out;
   }

   private static double ceilingAbove(List<SkyTri> tris, double x, double head, double z, double r) {
      double best = Double.NaN;

      for(SkyTri t : tris) {
         if (!t.stairHelper && !(t.maxY < head - 0.05)) {
            for(double[] s : FLOOR_SAMPLES) {
               double h = t.heightAt(x + s[0] * r / 0.15, z + s[1] * r / 0.15);
               if (!Double.isNaN(h) && h >= head - 0.05 && (Double.isNaN(best) || h < best)) {
                  best = h;
               }
            }
         }
      }

      return best;
   }

   public static SkyTri ceilingTri(List<SkyTri> tris, double x, double head, double z, double r) {
      double best = Double.NaN;
      SkyTri found = null;

      for(SkyTri t : tris) {
         if (!t.stairHelper && !(t.maxY < head - 0.05)) {
            for(double[] s : FLOOR_SAMPLES) {
               double h = t.heightAt(x + s[0] * r / 0.15, z + s[1] * r / 0.15);
               if (!Double.isNaN(h) && h >= head - 0.05 && (Double.isNaN(best) || h < best)) {
                  best = h;
                  found = t;
               }
            }
         }
      }

      return found;
   }

   private static double[] pushOutOfWalls(List<SkyTri> tris, double x, double y, double z, double radius, double height, double wallFrom, double step, double prevX, double prevY, double prevZ) {
      double[] poly = new double[18];

      for(int iter = 0; iter < 4; ++iter) {
         double bestPen = (double)0.0F;
         double bestDx = (double)0.0F;
         double bestDz = (double)0.0F;

         for(SkyTri t : tris) {
            if (!t.stairHelper) {
               double lo = y + (t.walkable ? step : wallFrom);
               double hi = y + height - 0.02;
               if (!(t.maxY < lo) && !(t.minY > hi) && !(t.maxX < x - radius) && !(t.minX > x + radius) && !(t.maxZ < z - radius) && !(t.minZ > z + radius)) {
                  int n = clipToSlab(t, lo, hi, poly);
                  if (n != 0) {
                     double[] closest = closestXZ(poly, n, x, z);
                     double cx = closest[0];
                     double cz = closest[1];
                     double ddx = x - cx;
                     double ddz = z - cz;
                     double d = Math.sqrt(ddx * ddx + ddz * ddz);
                     double pen;
                     double dirX;
                     double dirZ;
                     if (d > 1.0E-6) {
                        pen = radius - d;
                        dirX = ddx / d;
                        dirZ = ddz / d;
                     } else {
                        double hl = Math.hypot(t.nx, t.nz);
                        if (hl < 1.0E-6) {
                           continue;
                        }

                        dirX = t.nx / hl;
                        dirZ = t.nz / hl;
                        double side = (prevX - t.ax) * t.nx + (prevY - t.ay) * t.ny + (prevZ - t.az) * t.nz;
                        if (side < (double)0.0F) {
                           dirX = -dirX;
                           dirZ = -dirZ;
                        }

                        pen = radius;
                     }

                     if (pen > bestPen) {
                        bestPen = pen;
                        bestDx = dirX;
                        bestDz = dirZ;
                     }
                  }
               }
            }
         }

         if (bestPen <= 1.0E-4) {
            return new double[]{x, z, (double)0.0F};
         }

         x += bestDx * (bestPen + 1.0E-4);
         z += bestDz * (bestPen + 1.0E-4);
      }

      return new double[]{x, z, (double)1.0F};
   }

   private static boolean stuck(List<SkyTri> tris, double x, double y, double z, double radius, double height, double wallFrom, double step) {
      return pushOutOfWalls(tris, x, y, z, radius, height, wallFrom, step, x, y + height * (double)0.5F, z)[2] != (double)0.0F;
   }

   private static int clipToSlab(SkyTri t, double lo, double hi, double[] out) {
      double[] a = new double[]{t.ax, t.ay, t.az, t.bx, t.by, t.bz, t.cx, t.cy, t.cz};
      double[] tmp = new double[18];
      int n = clipPlane(a, 3, tmp, lo, true);
      return n == 0 ? 0 : clipPlane(tmp, n, out, hi, false);
   }

   private static int clipPlane(double[] in, int n, double[] out, double level, boolean keepAbove) {
      int m = 0;

      for(int i = 0; i < n; ++i) {
         int j = (i + 1) % n;
         double ay = in[i * 3 + 1];
         double by = in[j * 3 + 1];
         boolean aIn = keepAbove ? ay >= level : ay <= level;
         boolean bIn = keepAbove ? by >= level : by <= level;
         if (aIn) {
            out[m * 3] = in[i * 3];
            out[m * 3 + 1] = ay;
            out[m * 3 + 2] = in[i * 3 + 2];
            ++m;
         }

         if (aIn != bIn) {
            double s = (level - ay) / (by - ay);
            out[m * 3] = in[i * 3] + (in[j * 3] - in[i * 3]) * s;
            out[m * 3 + 1] = level;
            out[m * 3 + 2] = in[i * 3 + 2] + (in[j * 3 + 2] - in[i * 3 + 2]) * s;
            ++m;
         }
      }

      return m;
   }

   private static double[] closestXZ(double[] poly, int n, double x, double z) {
      double area = (double)0.0F;

      for(int i = 0; i < n; ++i) {
         int j = (i + 1) % n;
         area += poly[i * 3] * poly[j * 3 + 2] - poly[j * 3] * poly[i * 3 + 2];
      }

      if (Math.abs(area) > 1.0E-9) {
         boolean inside = true;

         for(int i = 0; i < n && inside; ++i) {
            int j = (i + 1) % n;
            double cross = (poly[j * 3] - poly[i * 3]) * (z - poly[i * 3 + 2]) - (poly[j * 3 + 2] - poly[i * 3 + 2]) * (x - poly[i * 3]);
            inside = area > (double)0.0F ? cross >= -1.0E-12 : cross <= 1.0E-12;
         }

         if (inside) {
            return new double[]{x, z};
         }
      }

      double bestD = Double.MAX_VALUE;
      double bx = poly[0];
      double bz = poly[2];

      for(int i = 0; i < n; ++i) {
         int j = (i + 1) % n;
         double x0 = poly[i * 3];
         double z0 = poly[i * 3 + 2];
         double x1 = poly[j * 3];
         double z1 = poly[j * 3 + 2];
         double ex = x1 - x0;
         double ez = z1 - z0;
         double l2 = ex * ex + ez * ez;
         double s = l2 > 1.0E-12 ? Math.max((double)0.0F, Math.min((double)1.0F, ((x - x0) * ex + (z - z0) * ez) / l2)) : (double)0.0F;
         double px = x0 + ex * s;
         double pz = z0 + ez * s;
         double d = (px - x) * (px - x) + (pz - z) * (pz - z);
         if (d < bestD) {
            bestD = d;
            bx = px;
            bz = pz;
         }
      }

      return new double[]{bx, bz};
   }

   public static double groundAt(List<SkyTri> tris, double x, double y, double z, double maxAbove) {
      double f = floor(tris, x, y, z, false, maxAbove);
      return f == Double.NEGATIVE_INFINITY ? Double.NaN : f;
   }
}
