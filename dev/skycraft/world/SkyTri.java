package dev.skycraft.world;

public final class SkyTri {
   public static final double WALKABLE_NY = 0.7;
   public final double ax;
   public final double ay;
   public final double az;
   public final double bx;
   public final double by;
   public final double bz;
   public final double cx;
   public final double cy;
   public final double cz;
   public final double nx;
   public final double ny;
   public final double nz;
   public final double minX;
   public final double minY;
   public final double minZ;
   public final double maxX;
   public final double maxY;
   public final double maxZ;
   public final boolean stairHelper;
   public final boolean walkable;
   public final boolean diggable;
   public final int material;
   public final boolean terrain;

   public SkyTri(float[] v, int o, boolean stairHelper) {
      this(v, o, stairHelper ? 1 : 0);
   }

   public SkyTri(float[] v, int o, int flags) {
      boolean stairHelper = (flags & 1) != 0;
      this.diggable = (flags & 2) != 0;
      this.material = flags >>> 8 & 255;
      this.terrain = (flags & 8) != 0;
      this.ax = (double)v[o];
      this.ay = (double)v[o + 1];
      this.az = (double)v[o + 2];
      this.bx = (double)v[o + 3];
      this.by = (double)v[o + 4];
      this.bz = (double)v[o + 5];
      this.cx = (double)v[o + 6];
      this.cy = (double)v[o + 7];
      this.cz = (double)v[o + 8];
      double ux = this.bx - this.ax;
      double uy = this.by - this.ay;
      double uz = this.bz - this.az;
      double wx = this.cx - this.ax;
      double wy = this.cy - this.ay;
      double wz = this.cz - this.az;
      double qx = uy * wz - uz * wy;
      double qy = uz * wx - ux * wz;
      double qz = ux * wy - uy * wx;
      double len = Math.sqrt(qx * qx + qy * qy + qz * qz);
      if (len < 1.0E-12) {
         this.nx = (double)0.0F;
         this.ny = (double)1.0F;
         this.nz = (double)0.0F;
      } else {
         this.nx = qx / len;
         this.ny = qy / len;
         this.nz = qz / len;
      }

      this.minX = Math.min(this.ax, Math.min(this.bx, this.cx));
      this.minY = Math.min(this.ay, Math.min(this.by, this.cy));
      this.minZ = Math.min(this.az, Math.min(this.bz, this.cz));
      this.maxX = Math.max(this.ax, Math.max(this.bx, this.cx));
      this.maxY = Math.max(this.ay, Math.max(this.by, this.cy));
      this.maxZ = Math.max(this.az, Math.max(this.bz, this.cz));
      this.stairHelper = stairHelper;
      this.walkable = stairHelper || Math.abs(this.ny) >= 0.7;
   }

   public boolean degenerate() {
      return this.maxX - this.minX < 1.0E-9 && this.maxZ - this.minZ < 1.0E-9;
   }

   public double heightAt(double x, double z) {
      if (!(x < this.minX - 1.0E-9) && !(x > this.maxX + 1.0E-9) && !(z < this.minZ - 1.0E-9) && !(z > this.maxZ + 1.0E-9) && !(Math.abs(this.ny) < 0.05)) {
         double d1 = edge(x, z, this.ax, this.az, this.bx, this.bz);
         double d2 = edge(x, z, this.bx, this.bz, this.cx, this.cz);
         double d3 = edge(x, z, this.cx, this.cz, this.ax, this.az);
         boolean hasNeg = d1 < -1.0E-12 || d2 < -1.0E-12 || d3 < -1.0E-12;
         boolean hasPos = d1 > 1.0E-12 || d2 > 1.0E-12 || d3 > 1.0E-12;
         return hasNeg && hasPos ? Double.NaN : this.ay - (this.nx * (x - this.ax) + this.nz * (z - this.az)) / this.ny;
      } else {
         return Double.NaN;
      }
   }

   private static double edge(double px, double pz, double x0, double z0, double x1, double z1) {
      return (x1 - x0) * (pz - z0) - (z1 - z0) * (px - x0);
   }
}
