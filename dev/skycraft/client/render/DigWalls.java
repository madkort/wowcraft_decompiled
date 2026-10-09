package dev.skycraft.client.render;

import dev.skycraft.world.SkyDig;
import dev.skycraft.world.SkyTri;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

final class DigWalls {
   private static final Direction[] WALLS = Direction.values();
   private static final int STEPS = 4;
   private static final double INTO = 0.02;

   private DigWalls() {
   }

   static void add(ClientLevel level, int sx, int sy, int sz, long[] dug, SkyDig.DugLookup lookup, Faces faces, Out out) {
      SkyDig.Probe probe = new SkyDig.Probe();
      Emitter emit = new Emitter(sx, sy, sz, out);
      BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

      for(int word = 0; word < 64; ++word) {
         long bits = dug[word];

         while(bits != 0L) {
            int bit = word * 64 + Long.numberOfTrailingZeros(bits);
            bits &= bits - 1L;
            int x = sx * 16 + (bit & 15);
            int y = sy * 16 + (bit >> 8);
            int z = sz * 16 + (bit >> 4 & 15);
            cell.set(x, y, z);
            if (!level.getBlockState(cell).canOcclude()) {
               emit.light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, cell), level.getBrightness(LightLayer.SKY, cell));

               for(Direction dir : WALLS) {
                  int nx = x + dir.getStepX();
                  int ny = y + dir.getStepY();
                  int nz = z + dir.getStepZ();
                  if (!lookup.isDug(nx, ny, nz) && !level.getBlockState(new BlockPos(nx, ny, nz)).canOcclude()) {
                     probe.around((double)nx, (double)ny, (double)nz, (double)(nx + 1), (double)(ny + 1), (double)(nz + 1));
                     emit.rects = null;
                     emit.material = -1;
                     face(probe, dir, x, y, z, faces, emit);
                  }
               }
            }
         }
      }

   }

   private static void face(SkyDig.Probe probe, Direction dir, int x, int y, int z, Faces faces, Emitter emit) {
      boolean vertical = dir.getAxis() != Axis.Y;
      if (!probe.land.isEmpty()) {
         if (vertical) {
            landSide(probe, dir, x, y, z, faces, emit);
         } else {
            landFlat(probe, dir == Direction.UP, x, y, z, faces, emit);
         }
      }

      if (probe.land.isEmpty() || !probe.tris.isEmpty()) {
         sampled(probe, dir, x, y, z, faces, emit);
      }

   }

   private static double[] onFace(Direction dir, int x, int y, int z, double u, double v) {
      double[] var10000;
      switch (dir) {
         case NORTH -> var10000 = new double[]{(double)x + u, (double)y + v, (double)z};
         case SOUTH -> var10000 = new double[]{(double)(x + 1) - u, (double)y + v, (double)(z + 1)};
         case WEST -> var10000 = new double[]{(double)x, (double)y + v, (double)(z + 1) - u};
         case EAST -> var10000 = new double[]{(double)(x + 1), (double)y + v, (double)z + u};
         case UP -> var10000 = new double[]{(double)x + u, (double)(y + 1), (double)z + v};
         case DOWN -> var10000 = new double[]{(double)x + u, (double)y, (double)z + v};
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static int rectFor(Direction dir) {
      return dir == Direction.UP ? 8 : (dir == Direction.DOWN ? 4 : 0);
   }

   private static void landSide(SkyDig.Probe probe, Direction dir, int x, int y, int z, Faces faces, Emitter emit) {
      List<double[]> segments = new ArrayList();

      for(SkyTri t : probe.land) {
         double[] seg = cross(t, dir, x, z);
         if (seg != null) {
            segments.add(seg);
         }
      }

      if (!segments.isEmpty()) {
         List<Double> cuts = new ArrayList();
         cuts.add((double)0.0F);
         cuts.add((double)1.0F);

         for(double[] s : segments) {
            if (s[0] > (double)0.0F && s[0] < (double)1.0F) {
               cuts.add(s[0]);
            }

            if (s[2] > (double)0.0F && s[2] < (double)1.0F) {
               cuts.add(s[2]);
            }
         }

         cuts.sort(Double::compare);
         Direction normal = dir.getOpposite();

         for(int i = 0; i + 1 < cuts.size(); ++i) {
            double u0 = (Double)cuts.get(i);
            double u1 = (Double)cuts.get(i + 1);
            if (!(u1 - u0 < 1.0E-6)) {
               double[] seg = covering(segments, (u0 + u1) * (double)0.5F);
               if (seg != null) {
                  double h0 = at(seg, u0) - (double)y;
                  double h1 = at(seg, u1) - (double)y;
                  List<Double> us = new ArrayList(List.of(u0, u1));

                  for(double level : new double[]{(double)0.0F, (double)1.0F}) {
                     if ((h0 - level) * (h1 - level) < (double)0.0F) {
                        us.add(u0 + (u1 - u0) * (level - h0) / (h1 - h0));
                     }
                  }

                  us.sort(Double::compare);

                  for(int k = 0; k + 1 < us.size(); ++k) {
                     double a = (Double)us.get(k);
                     double b = (Double)us.get(k + 1);
                     double ha = Math.min((double)1.0F, h0 + (h1 - h0) * (a - u0) / (u1 - u0));
                     double hb = Math.min((double)1.0F, h0 + (h1 - h0) * (b - u0) / (u1 - u0));
                     if (!(ha <= 1.0E-4) || !(hb <= 1.0E-4)) {
                        ha = Math.max(ha, (double)0.0F);
                        hb = Math.max(hb, (double)0.0F);
                        if (!emit.texture(faces, surfaceMaterial(seg), (double)0.5F)) {
                           return;
                        }

                        List<double[]> world = List.of(onFace(dir, x, y, z, a, (double)0.0F), onFace(dir, x, y, z, b, (double)0.0F), onFace(dir, x, y, z, b, hb), onFace(dir, x, y, z, a, ha));
                        List<double[]> uvs = List.of(new double[]{a, (double)1.0F}, new double[]{b, (double)1.0F}, new double[]{b, (double)1.0F - hb}, new double[]{a, (double)1.0F - ha});
                        emit.polygon(world, uvs, rectFor(dir), normal);
                     }
                  }
               }
            }
         }

      }
   }

   private static int surfaceMaterial(double[] seg) {
      return (int)seg[4];
   }

   private static double[] cross(SkyTri t, Direction dir, int x, int z) {
      boolean alongX = dir == Direction.NORTH || dir == Direction.SOUTH;
      double var10000;
      switch (dir) {
         case NORTH -> var10000 = (double)z;
         case SOUTH -> var10000 = (double)(z + 1);
         case WEST -> var10000 = (double)x;
         default -> var10000 = (double)(x + 1);
      }

      double plane = var10000;
      double[][] v = new double[][]{{t.ax, t.ay, t.az}, {t.bx, t.by, t.bz}, {t.cx, t.cy, t.cz}};
      List<double[]> hits = new ArrayList(2);

      for(int i = 0; i < 3; ++i) {
         double[] p = v[i];
         double[] q = v[(i + 1) % 3];
         double dp = (alongX ? p[2] : p[0]) - plane;
         double dq = (alongX ? q[2] : q[0]) - plane;
         if (dp < (double)0.0F && dq >= (double)0.0F || dp >= (double)0.0F && dq < (double)0.0F) {
            double f = dp / (dp - dq);
            double along = alongX ? p[0] + (q[0] - p[0]) * f : p[2] + (q[2] - p[2]) * f;
            double h = p[1] + (q[1] - p[1]) * f;
            hits.add(new double[]{along, h});
         }
      }

      if (hits.size() < 2) {
         return null;
      } else {
         double[] a = (double[])hits.get(0);
         double[] b = (double[])hits.get(1);
         double ua = toU(dir, x, z, a[0]);
         double ub = toU(dir, x, z, b[0]);
         if (ua > ub) {
            return new double[]{ub, b[1], ua, a[1], (double)t.material};
         } else {
            return new double[]{ua, a[1], ub, b[1], (double)t.material};
         }
      }
   }

   private static double toU(Direction dir, int x, int z, double along) {
      double var10000;
      switch (dir) {
         case NORTH -> var10000 = along - (double)x;
         case SOUTH -> var10000 = (double)(x + 1) - along;
         case WEST -> var10000 = (double)(z + 1) - along;
         default -> var10000 = along - (double)z;
      }

      return var10000;
   }

   private static double[] covering(List<double[]> segments, double u) {
      double[] best = null;
      double bestH = Double.NEGATIVE_INFINITY;

      for(double[] s : segments) {
         if (u >= s[0] - 1.0E-9 && u <= s[2] + 1.0E-9 && s[2] - s[0] > 1.0E-9) {
            double h = at(s, u);
            if (h > bestH) {
               bestH = h;
               best = s;
            }
         }
      }

      return best;
   }

   private static double at(double[] s, double u) {
      return s[1] + (s[3] - s[1]) * (u - s[0]) / (s[2] - s[0]);
   }

   private static void landFlat(SkyDig.Probe probe, boolean ceiling, int x, int y, int z, Faces faces, Emitter emit) {
      double level = ceiling ? (double)(y + 1) : (double)y;
      Direction dir = ceiling ? Direction.UP : Direction.DOWN;
      Direction normal = ceiling ? Direction.DOWN : Direction.UP;

      for(SkyTri t : probe.land) {
         if (!(t.maxX < (double)x) && !(t.minX > (double)(x + 1)) && !(t.maxZ < (double)z) && !(t.minZ > (double)(z + 1)) && !(t.maxY <= level) && !(Math.abs(t.ny) < 1.0E-6)) {
            List<double[]> poly = new ArrayList(List.of(new double[]{t.ax, t.az, t.ay}, new double[]{t.bx, t.bz, t.by}, new double[]{t.cx, t.cz, t.cy}));
            List<double[]> var18 = clip(poly, (px) -> px[0] - (double)x);
            var18 = clip(var18, (px) -> (double)(x + 1) - px[0]);
            var18 = clip(var18, (px) -> px[1] - (double)z);
            var18 = clip(var18, (px) -> (double)(z + 1) - px[1]);
            var18 = clip(var18, (px) -> px[2] - level);
            if (var18.size() >= 3) {
               if (!emit.texture(faces, t.material, (double)0.5F)) {
                  return;
               }

               List<double[]> world = new ArrayList(var18.size());
               List<double[]> uvs = new ArrayList(var18.size());

               for(double[] p : var18) {
                  world.add(new double[]{p[0], level, p[1]});
                  uvs.add(new double[]{p[0] - (double)x, p[1] - (double)z});
               }

               emit.polygon(world, uvs, rectFor(dir), normal);
            }
         }
      }

   }

   private static List<double[]> clip(List<double[]> in, ToDoubleFunction<double[]> f) {
      List<double[]> out = new ArrayList(in.size() + 2);

      for(int i = 0; i < in.size(); ++i) {
         double[] a = (double[])in.get(i);
         double[] b = (double[])in.get((i + 1) % in.size());
         double fa = f.applyAsDouble(a);
         double fb = f.applyAsDouble(b);
         if (fa >= (double)0.0F) {
            out.add(a);
         }

         if (fa >= (double)0.0F != fb >= (double)0.0F) {
            double s = fa / (fa - fb);
            out.add(new double[]{a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s, a[2] + (b[2] - a[2]) * s});
         }
      }

      return out;
   }

   private static void sampled(SkyDig.Probe probe, Direction dir, int x, int y, int z, Faces faces, Emitter emit) {
      double ix = (double)dir.getStepX() * 0.02;
      double iy = (double)dir.getStepY() * 0.02;
      double iz = (double)dir.getStepZ() * 0.02;
      double step = (double)0.25F;
      Direction normal = dir.getOpposite();

      for(int i = 0; i < 4; ++i) {
         for(int j = 0; j < 4; ++j) {
            double u = ((double)i + (double)0.5F) * step;
            double v = ((double)j + (double)0.5F) * step;
            double[] p = onFace(dir, x, y, z, u, v);
            double px = p[0] + ix;
            double py = p[1] + iy;
            double pz = p[2] + iz;
            double ground = probe.landHeight(px, pz);
            if (Double.isNaN(ground) || !(py < ground)) {
               int material = probe.test(px, py, pz);
               if (material > 0 && emit.texture(faces, material, probe.depth)) {
                  double u0 = (double)i * step;
                  double u1 = u0 + step;
                  double v0 = (double)j * step;
                  double v1 = v0 + step;
                  boolean flat = dir.getAxis() == Axis.Y;
                  List<double[]> world = List.of(onFace(dir, x, y, z, u0, v0), onFace(dir, x, y, z, u1, v0), onFace(dir, x, y, z, u1, v1), onFace(dir, x, y, z, u0, v1));
                  List<double[]> uvs = flat ? List.of(new double[]{u0, v0}, new double[]{u1, v0}, new double[]{u1, v1}, new double[]{u0, v1}) : List.of(new double[]{u0, (double)1.0F - v0}, new double[]{u1, (double)1.0F - v0}, new double[]{u1, (double)1.0F - v1}, new double[]{u0, (double)1.0F - v1});
                  emit.polygon(world, uvs, rectFor(dir), normal);
               }
            }
         }
      }

   }

   private static final class Emitter {
      final int sx;
      final int sy;
      final int sz;
      final Out out;
      int light;
      float[] rects;
      int material;
      final float[] xyz = new float[12];
      final float[] uv = new float[8];

      Emitter(int sx, int sy, int sz, Out out) {
         this.sx = sx;
         this.sy = sy;
         this.sz = sz;
         this.out = out;
      }

      boolean texture(Faces faces, int material, double depth) {
         if (this.rects == null) {
            this.material = material;
            this.rects = faces.of(SkyDig.materialState(SkyDig.underground(material, Math.max(depth, (double)0.5F))));
         }

         return this.rects != null;
      }

      void polygon(List<double[]> world, List<double[]> faceUv, int rect, Direction normal) {
         for(int i = 1; i + 1 < world.size(); i += 2) {
            int a = 0;
            int c = i + 1;
            int d = Math.min(i + 2, world.size() - 1);
            int[] ids = new int[]{a, i, c, d};

            for(int k = 0; k < 4; ++k) {
               double[] p = (double[])world.get(ids[k]);
               double[] t = (double[])faceUv.get(ids[k]);
               this.xyz[k * 3] = (float)(p[0] - (double)(this.sx * 16));
               this.xyz[k * 3 + 1] = (float)(p[1] - (double)(this.sy * 16));
               this.xyz[k * 3 + 2] = (float)(p[2] - (double)(this.sz * 16));
               this.uv[k * 2] = this.rects[rect] + (float)t[0] * (this.rects[rect + 2] - this.rects[rect]);
               this.uv[k * 2 + 1] = this.rects[rect + 1] + (float)t[1] * (this.rects[rect + 3] - this.rects[rect + 1]);
            }

            this.out.quad(this.xyz, this.uv, this.light, normal);
         }

      }
   }

   interface Faces {
      float[] of(BlockState var1);
   }

   interface Out {
      void quad(float[] var1, float[] var2, int var3, Direction var4);
   }
}
