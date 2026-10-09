package dev.skycraft.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skycraft.SkyCraft;
import dev.skycraft.client.mixin.SpriteContentsAccessor;
import dev.skycraft.client.mixin.TextureAtlasAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

final class SkyAtlas {
   final int width;
   final int height;
   final ByteBuffer pixels;
   private final int blockW;
   private final int blockH;
   private final int itemW;
   private final int itemH;
   final Object blockSprites;
   final Object itemSprites;
   private static final int CRACK_STAGES = 10;
   private static final int CRACK_SIZE = 16;
   private static final String[] ARROW_TEXTURES = new String[]{"arrow", "arrow_tipped", "arrow_spectral"};
   private static final int ARROW_X = 160;
   private static final int ARROW_W = 32;
   private final List<Animation> animations = new ArrayList();

   private SkyAtlas(TextureAtlas blocks, TextureAtlas items, ResourceManager resources) {
      TextureAtlasAccessor b = (TextureAtlasAccessor)blocks;
      TextureAtlasAccessor i = (TextureAtlasAccessor)items;
      this.blockW = b.skycraft$width();
      this.blockH = b.skycraft$height();
      this.itemW = i.skycraft$width();
      this.itemH = i.skycraft$height();
      this.width = Math.max(Math.max(this.blockW, this.itemW), 160 + ARROW_TEXTURES.length * 32);
      this.height = this.blockH + this.itemH + 16;
      this.pixels = ByteBuffer.allocateDirect(this.width * this.height * 4).order(ByteOrder.LITTLE_ENDIAN);

      for(TextureAtlasSprite sprite : b.skycraft$sprites().values()) {
         this.copy(sprite, this.blockW, this.blockH, 0);
      }

      for(TextureAtlasSprite sprite : i.skycraft$sprites().values()) {
         this.copy(sprite, this.itemW, this.itemH, this.blockH);
      }

      this.copyCracks(resources);
      this.copyArrows(resources);
      this.blockSprites = b.skycraft$sprites();
      this.itemSprites = i.skycraft$sprites();
   }

   static SkyAtlas build(Minecraft minecraft) {
      AtlasManager atlases = minecraft.getAtlasManager();
      return new SkyAtlas(atlases.getAtlasOrThrow(AtlasIds.BLOCKS), atlases.getAtlasOrThrow(AtlasIds.ITEMS), minecraft.getResourceManager());
   }

   private void copyCracks(ResourceManager resources) {
      int y0 = this.blockH + this.itemH;

      for(int stage = 0; stage < 10; ++stage) {
         Identifier id = Identifier.withDefaultNamespace("textures/block/destroy_stage_" + stage + ".png");
         Optional<Resource> resource = resources.getResource(id);
         if (!resource.isEmpty()) {
            try {
               InputStream in = ((Resource)resource.get()).open();

               try {
                  NativeImage image = NativeImage.read(in);

                  try {
                     for(int y = 0; y < 16; ++y) {
                        for(int x = 0; x < 16; ++x) {
                           int argb = image.getPixel(x * image.getWidth() / 16, y * image.getHeight() / 16);
                           int o = ((y0 + y) * this.width + stage * 16 + x) * 4;
                           this.pixels.put(o, (byte)(argb >> 16));
                           this.pixels.put(o + 1, (byte)(argb >> 8));
                           this.pixels.put(o + 2, (byte)argb);
                           this.pixels.put(o + 3, (byte)(argb >>> 24));
                        }
                     }
                  } catch (Throwable var14) {
                     if (image != null) {
                        try {
                           image.close();
                        } catch (Throwable var13) {
                           var14.addSuppressed(var13);
                        }
                     }

                     throw var14;
                  }

                  if (image != null) {
                     image.close();
                  }
               } catch (Throwable var15) {
                  if (in != null) {
                     try {
                        in.close();
                     } catch (Throwable var12) {
                        var15.addSuppressed(var12);
                     }
                  }

                  throw var15;
               }

               if (in != null) {
                  in.close();
               }
            } catch (IOException e) {
               SkyCraft.LOG.warn("SkyCraft: couldn't read {}", id, e);
            }
         }
      }

   }

   private void copyArrows(ResourceManager resources) {
      int y0 = this.blockH + this.itemH;

      for(int t = 0; t < ARROW_TEXTURES.length; ++t) {
         String var10000 = ARROW_TEXTURES[t];
         Identifier id = Identifier.withDefaultNamespace("textures/entity/projectiles/" + var10000 + ".png");
         Optional<Resource> resource = resources.getResource(id);
         if (!resource.isEmpty()) {
            try {
               InputStream in = ((Resource)resource.get()).open();

               try {
                  NativeImage image = NativeImage.read(in);

                  try {
                     for(int y = 0; y < 16; ++y) {
                        for(int x = 0; x < 32; ++x) {
                           int argb = image.getPixel(x * image.getWidth() / 32, y * image.getHeight() / 32);
                           int o = ((y0 + y) * this.width + 160 + t * 32 + x) * 4;
                           this.pixels.put(o, (byte)(argb >> 16));
                           this.pixels.put(o + 1, (byte)(argb >> 8));
                           this.pixels.put(o + 2, (byte)argb);
                           this.pixels.put(o + 3, (byte)(argb >>> 24));
                        }
                     }
                  } catch (Throwable var14) {
                     if (image != null) {
                        try {
                           image.close();
                        } catch (Throwable var13) {
                           var14.addSuppressed(var13);
                        }
                     }

                     throw var14;
                  }

                  if (image != null) {
                     image.close();
                  }
               } catch (Throwable var15) {
                  if (in != null) {
                     try {
                        in.close();
                     } catch (Throwable var12) {
                        var15.addSuppressed(var12);
                     }
                  }

                  throw var15;
               }

               if (in != null) {
                  in.close();
               }
            } catch (IOException e) {
               SkyCraft.LOG.warn("SkyCraft: couldn't read {}", id, e);
            }
         }
      }

   }

   float[] arrowUv(int variant) {
      float x0 = (float)(160 + Math.clamp((long)variant, 0, ARROW_TEXTURES.length - 1) * 32);
      float y0 = (float)(this.blockH + this.itemH);
      return new float[]{x0 / (float)this.width, y0 / (float)this.height, (x0 + 16.0F) / (float)this.width, (y0 + 5.0F) / (float)this.height, x0 / (float)this.width, (y0 + 5.0F) / (float)this.height, (x0 + 5.0F) / (float)this.width, (y0 + 10.0F) / (float)this.height};
   }

   float[] crackUv(int stage) {
      int s = Math.clamp((long)stage, 0, 9);
      float y0 = (float)(this.blockH + this.itemH) / (float)this.height;
      float y1 = (float)(this.blockH + this.itemH + 16) / (float)this.height;
      return new float[]{(float)(s * 16) / (float)this.width, y0, (float)((s + 1) * 16) / (float)this.width, y1};
   }

   float[] rect(TextureAtlasSprite s) {
      return new float[]{this.u(s, s.getU0()), this.v(s, s.getV0()), this.u(s, s.getU1()), this.v(s, s.getV1())};
   }

   boolean stale(Minecraft minecraft) {
      AtlasManager atlases = minecraft.getAtlasManager();
      return ((TextureAtlasAccessor)atlases.getAtlasOrThrow(AtlasIds.BLOCKS)).skycraft$sprites() != this.blockSprites || ((TextureAtlasAccessor)atlases.getAtlasOrThrow(AtlasIds.ITEMS)).skycraft$sprites() != this.itemSprites;
   }

   private void copy(TextureAtlasSprite sprite, int atlasW, int atlasH, int yOffset) {
      NativeImage image = ((SpriteContentsAccessor)sprite.contents()).skycraft$originalImage();
      int w = Math.min(sprite.contents().width(), image.getWidth());
      int h = Math.min(sprite.contents().height(), image.getHeight());
      int imageX = Math.round(sprite.getU0() * (float)atlasW);
      int imageY = Math.round(sprite.getV0() * (float)atlasH);
      int pad = Math.max(0, Math.min(imageX - sprite.getX(), imageY - sprite.getY()));

      for(int y = -pad; y < h + pad; ++y) {
         int ty = imageY + y + yOffset;
         if (ty >= 0 && ty < this.height) {
            int sy = Math.clamp((long)y, 0, h - 1);

            for(int x = -pad; x < w + pad; ++x) {
               int tx = imageX + x;
               if (tx >= 0 && tx < this.width) {
                  int argb = image.getPixel(Math.clamp((long)x, 0, w - 1), sy);
                  int o = (ty * this.width + tx) * 4;
                  this.pixels.put(o, (byte)(argb >> 16));
                  this.pixels.put(o + 1, (byte)(argb >> 8));
                  this.pixels.put(o + 2, (byte)argb);
                  this.pixels.put(o + 3, (byte)(argb >>> 24));
               }
            }
         }
      }

      Animation animation = animationOf(sprite.contents());
      if (animation != null) {
         animation.image = image;
         animation.x = imageX;
         animation.y = imageY + yOffset;
         animation.w = w;
         animation.h = h;
         animation.pad = pad;
         this.animations.add(animation);
      }

   }

   private static Animation animationOf(SpriteContents contents) {
      try {
         Field field = SpriteContents.class.getDeclaredField("animatedTexture");
         field.setAccessible(true);
         Object animated = field.get(contents);
         if (animated == null) {
            return null;
         } else {
            Class<?> type = animated.getClass();
            Field framesField = type.getDeclaredField("frames");
            Field rowField = type.getDeclaredField("frameRowSize");
            Field interpolateField = type.getDeclaredField("interpolateFrames");
            framesField.setAccessible(true);
            rowField.setAccessible(true);
            interpolateField.setAccessible(true);
            List<?> frames = (List)framesField.get(animated);
            if (frames.size() < 2) {
               return null;
            } else {
               Animation a = new Animation();
               a.index = new int[frames.size()];
               a.time = new int[frames.size()];

               for(int k = 0; k < frames.size(); ++k) {
                  Object frame = frames.get(k);
                  Field indexField = frame.getClass().getDeclaredField("index");
                  Field timeField = frame.getClass().getDeclaredField("time");
                  indexField.setAccessible(true);
                  timeField.setAccessible(true);
                  a.index[k] = indexField.getInt(frame);
                  a.time[k] = Math.max(1, timeField.getInt(frame));
                  a.cycle += a.time[k];
               }

               a.rowSize = Math.max(1, rowField.getInt(animated));
               a.interpolate = interpolateField.getBoolean(animated);
               return a;
            }
         }
      } catch (RuntimeException | ReflectiveOperationException var13) {
         return null;
      }
   }

   int animatedSprites() {
      return this.animations.size();
   }

   void animate(long tick, Predicate<Region> send) {
      for(Animation a : this.animations) {
         long t = Math.floorMod(tick, (long)a.cycle);

         int i;
         for(i = 0; t >= (long)a.time[i]; ++i) {
            t -= (long)a.time[i];
         }

         long key = a.interpolate ? (long)i << 20 | t : (long)i;
         if (key != a.shown) {
            int next = (i + 1) % a.index.length;
            float blend = a.interpolate ? (float)t / (float)a.time[i] : 0.0F;
            int pw = a.w + 2 * a.pad;
            int ph = a.h + 2 * a.pad;
            ByteBuffer out = ByteBuffer.allocateDirect(pw * ph * 4).order(ByteOrder.LITTLE_ENDIAN);
            int fx0 = a.index[i] % a.rowSize * a.w;
            int fy0 = a.index[i] / a.rowSize * a.h;
            int fx1 = a.index[next] % a.rowSize * a.w;
            int fy1 = a.index[next] / a.rowSize * a.h;

            for(int y = -a.pad; y < a.h + a.pad; ++y) {
               int sy = Math.clamp((long)y, 0, a.h - 1);

               for(int x = -a.pad; x < a.w + a.pad; ++x) {
                  int sx = Math.clamp((long)x, 0, a.w - 1);
                  int c = a.image.getPixel(fx0 + sx, fy0 + sy);
                  if (blend > 0.0F) {
                     c = mix(c, a.image.getPixel(fx1 + sx, fy1 + sy), blend);
                  }

                  out.put((byte)(c >> 16)).put((byte)(c >> 8)).put((byte)c).put((byte)(c >>> 24));
               }
            }

            out.flip();
            if (send.test(new Region(a.x - a.pad, a.y - a.pad, pw, ph, out))) {
               a.shown = key;
            }
         }
      }

   }

   private static int mix(int a, int b, float t) {
      int r = 0;

      for(int shift = 0; shift < 32; shift += 8) {
         int ca = a >>> shift & 255;
         int cb = b >>> shift & 255;
         r |= (Math.round((float)ca + (float)(cb - ca) * t) & 255) << shift;
      }

      return r;
   }

   static NativeImage image(TextureAtlas atlas) {
      TextureAtlasAccessor a = (TextureAtlasAccessor)atlas;
      int w = a.skycraft$width();
      int h = a.skycraft$height();
      NativeImage out = new NativeImage(w, h, true);

      for(TextureAtlasSprite sprite : a.skycraft$sprites().values()) {
         NativeImage image = ((SpriteContentsAccessor)sprite.contents()).skycraft$originalImage();
         int sw = Math.min(sprite.contents().width(), image.getWidth());
         int sh = Math.min(sprite.contents().height(), image.getHeight());
         int imageX = Math.round(sprite.getU0() * (float)w);
         int imageY = Math.round(sprite.getV0() * (float)h);
         int pad = Math.max(0, Math.min(imageX - sprite.getX(), imageY - sprite.getY()));

         for(int y = -pad; y < sh + pad; ++y) {
            for(int x = -pad; x < sw + pad; ++x) {
               int tx = imageX + x;
               int ty = imageY + y;
               if (tx >= 0 && tx < w && ty >= 0 && ty < h) {
                  out.setPixel(tx, ty, image.getPixel(Math.clamp((long)x, 0, sw - 1), Math.clamp((long)y, 0, sh - 1)));
               }
            }
         }
      }

      return out;
   }

   float u(TextureAtlasSprite sprite, float u) {
      return u * (float)(isItem(sprite) ? this.itemW : this.blockW) / (float)this.width;
   }

   float v(TextureAtlasSprite sprite, float v) {
      return isItem(sprite) ? ((float)this.blockH + v * (float)this.itemH) / (float)this.height : v * (float)this.blockH / (float)this.height;
   }

   float blockU(float u) {
      return u * (float)this.blockW / (float)this.width;
   }

   float blockV(float v) {
      return v * (float)this.blockH / (float)this.height;
   }

   float itemU(float u) {
      return u * (float)this.itemW / (float)this.width;
   }

   float itemV(float v) {
      return ((float)this.blockH + v * (float)this.itemH) / (float)this.height;
   }

   private static boolean isItem(TextureAtlasSprite sprite) {
      return sprite.atlasLocation().equals(TextureAtlas.LOCATION_ITEMS);
   }

   private static final class Animation {
      NativeImage image;
      int x;
      int y;
      int w;
      int h;
      int pad;
      int[] index;
      int[] time;
      int cycle;
      int rowSize;
      boolean interpolate;
      long shown = Long.MIN_VALUE;
   }

   static record Region(int x, int y, int w, int h, ByteBuffer pixels) {
   }
}
