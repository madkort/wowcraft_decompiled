package dev.skycraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.skycraft.SkyCraft;
import dev.skycraft.link.SkyLink;
import java.lang.foreign.MemorySegment;
import net.minecraft.client.Minecraft;

public final class FrameExporter {
   private static final int STAGING = 3;
   private static final int FREE = 0;
   private static final int PENDING = 1;
   private static final int READY = 2;
   private static final Staging[] staging = new Staging[3];
   private static long nextFrameId = 1L;
   private static boolean loggedFormat;
   private static int lastWidth;
   private static int lastHeight;
   private static int settleFrames;

   private FrameExporter() {
   }

   public static void capture(Minecraft minecraft) {
      shipReadyFrames();
      RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
      GpuTexture color = target.getColorTexture();
      if (color != null) {
         int width = target.width;
         int height = target.height;
         if (width <= 3840 && height <= 2160) {
            if (width != lastWidth || height != lastHeight) {
               lastWidth = width;
               lastHeight = height;
               settleFrames = 3;
            }

            if (settleFrames > 0) {
               --settleFrames;
            } else {
               if (!loggedFormat) {
                  loggedFormat = true;
                  SkyCraft.LOG.info("SkyCraft: overlay capture {}x{} format {}", new Object[]{width, height, color.getFormat()});
               }

               Staging slot = null;

               for(Staging s : staging) {
                  if (s != null && s.state == 0) {
                     slot = s;
                     break;
                  }
               }

               if (slot == null) {
                  for(int i = 0; i < 3; ++i) {
                     if (staging[i] == null) {
                        staging[i] = slot = new Staging();
                        break;
                     }
                  }
               }

               if (slot != null) {
                  long bytes = (long)width * (long)height * 4L;
                  if (slot.buffer == null || slot.width != width || slot.height != height) {
                     if (slot.buffer != null) {
                        slot.buffer.close();
                     }

                     slot.buffer = RenderSystem.getDevice().createBuffer(() -> "SkyCraft overlay readback", 9, bytes);
                     slot.width = width;
                     slot.height = height;
                  }

                  slot.state = 1;
                  slot.frameId = (long)(nextFrameId++);
                  RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(color, slot.buffer, 0L, () -> slot.state = 2, 0);
               }
            }
         }
      }
   }

   private static void shipReadyFrames() {
      Staging newest = null;

      for(Staging s : staging) {
         if (s != null && s.state == 2 && (newest == null || s.frameId > newest.frameId)) {
            newest = s;
         }
      }

      if (newest != null) {
         MemorySegment shm = SkyLink.segment();
         if (shm != null) {
            long bytes = (long)newest.width * (long)newest.height * 4L;
            GpuBufferSlice.MappedView view = newest.buffer.map(true, false);

            try {
               MemorySegment src = MemorySegment.ofBuffer(view.data());
               MemorySegment.copy(src, 0L, shm, SkyLink.overlayBackSlotOffset(), Math.min(bytes, src.byteSize()));
            } catch (Throwable var8) {
               if (view != null) {
                  try {
                     view.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (view != null) {
               view.close();
            }

            SkyLink.publishOverlay(newest.width, newest.height, true, newest.frameId);
         }

         for(Staging s : staging) {
            if (s != null && s.state == 2 && s.frameId <= newest.frameId) {
               s.state = 0;
            }
         }

      }
   }

   private static final class Staging {
      GpuBuffer buffer;
      int width;
      int height;
      volatile int state = 0;
      long frameId;
   }
}
