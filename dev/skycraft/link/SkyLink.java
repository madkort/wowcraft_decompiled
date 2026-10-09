package dev.skycraft.link;

import dev.skycraft.SkyCraft;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.Linker.Option;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SkyLink {
   private static final int FILE_MAP_ALL_ACCESS = 983071;
   private static final long HEARTBEAT_TIMEOUT_MS = 8000L;
   private static final VarHandle INT;
   private static final VarHandle LONG;
   private static final MethodHandle OPEN_FILE_MAPPING;
   private static final StructLayout CALL_STATE;
   private static final VarHandle LAST_ERROR;
   private static final MemorySegment OPEN_STATE;
   private static int lastOpenError;
   private static final MethodHandle MAP_VIEW_OF_FILE;
   private static final MethodHandle GET_TICK_COUNT64;
   private static final MethodHandle GET_CURRENT_PROCESS_ID;
   private static final MethodHandle QUERY_PERFORMANCE_COUNTER;
   private static final MethodHandle QUERY_PERFORMANCE_FREQUENCY;
   private static final MethodHandle CREATE_MUTEX;
   private static MemorySegment runningMutex;
   private static final MemorySegment QPC_OUT;
   private static volatile MemorySegment shm;
   private static long lastOpenAttempt;
   private static int skyrimPid;
   private static volatile int generation;
   private static int overlayBack;

   public static synchronized void announceRunning() {
      if (runningMutex == null) {
         try {
            Arena arena = Arena.ofConfined();

            try {
               MemorySegment name = arena.allocateFrom(Proto.MAPPING_NAME + "_minecraft", StandardCharsets.UTF_16LE);
               runningMutex = CREATE_MUTEX.invokeExact(MemorySegment.NULL, 0, name);
            } catch (Throwable var4) {
               if (arena != null) {
                  try {
                     arena.close();
                  } catch (Throwable var3) {
                     var4.addSuppressed(var3);
                  }
               }

               throw var4;
            }

            if (arena != null) {
               arena.close();
            }
         } catch (Throwable t) {
            SkyCraft.LOG.warn("SkyCraft: couldn't create the running-Minecraft mutex", t);
         }

      }
   }

   private SkyLink() {
   }

   public static boolean active() {
      MemorySegment s = shm;
      if (s == null) {
         return false;
      } else {
         long beat = LONG.getAcquire(s, 16L);
         return tickCount() - beat < 8000L;
      }
   }

   public static int generation() {
      return generation;
   }

   public static int skyrimPid() {
      return skyrimPid;
   }

   public static MemorySegment segment() {
      return shm;
   }

   public static void poll() {
      if (shm != null) {
         LONG.setRelease(shm, 24L, tickCount());
         int pid = shm.get(ValueLayout.JAVA_INT, 8L);
         if (pid != skyrimPid) {
            skyrimPid = pid;
            overlayBack = 1;
            ++generation;
            SkyCraft.LOG.info("SkyCraft: Skyrim instance changed (pid {})", pid);
         }

      } else {
         long now = System.currentTimeMillis();
         if (now - lastOpenAttempt >= 1000L) {
            lastOpenAttempt = now;

            try {
               Arena arena = Arena.ofConfined();

               label105: {
                  label120: {
                     label106: {
                        try {
                           MemorySegment name = arena.allocateFrom(Proto.MAPPING_NAME, StandardCharsets.UTF_16LE);
                           MemorySegment handle = OPEN_FILE_MAPPING.invokeExact(OPEN_STATE, 983071, 0, name);
                           if (handle.address() == 0L) {
                              int error = LAST_ERROR.get(OPEN_STATE, 0L);
                              if (error != lastOpenError) {
                                 lastOpenError = error;
                                 SkyCraft.LOG.info("SkyCraft: can't open Skyrim's shared memory yet (Windows error {}{})", error, error == 2 ? ": Skyrim hasn't created it yet" : (error == 5 ? ": access denied; is Skyrim running as administrator?" : ""));
                              }
                              break label120;
                           }

                           MemorySegment view = MAP_VIEW_OF_FILE.invokeExact(handle, 983071, 0, 0, 0L);
                           if (view.address() == 0L) {
                              SkyCraft.LOG.error("SkyCraft: MapViewOfFile failed");
                              break label105;
                           }

                           MemorySegment seg = view.reinterpret(200327168L);
                           int magic = seg.get(ValueLayout.JAVA_INT, 0L);
                           int version = seg.get(ValueLayout.JAVA_INT, 4L);
                           if (magic == 1129925459 && version == 11) {
                              seg.set(ValueLayout.JAVA_INT, 12L, GET_CURRENT_PROCESS_ID.invokeExact());
                              LONG.setRelease(seg, 24L, tickCount());
                              skyrimPid = seg.get(ValueLayout.JAVA_INT, 8L);
                              ++generation;
                              shm = seg;
                              SkyCraft.LOG.info("SkyCraft: linked to Skyrim (pid {})", seg.get(ValueLayout.JAVA_INT, 8L));
                              break label106;
                           }

                           SkyCraft.LOG.error("SkyCraft: protocol mismatch (magic {} version {}); expected version {}", new Object[]{Integer.toHexString(magic), version, 11});
                        } catch (Throwable var10) {
                           if (arena != null) {
                              try {
                                 arena.close();
                              } catch (Throwable var9) {
                                 var10.addSuppressed(var9);
                              }
                           }

                           throw var10;
                        }

                        if (arena != null) {
                           arena.close();
                        }

                        return;
                     }

                     if (arena != null) {
                        arena.close();
                     }

                     return;
                  }

                  if (arena != null) {
                     arena.close();
                  }

                  return;
               }

               if (arena != null) {
                  arena.close();
               }

            } catch (Throwable t) {
               SkyCraft.LOG.error("SkyCraft: failed to open shared memory", t);
            }
         }
      }
   }

   public static synchronized long qpc() {
      try {
         int ok = QUERY_PERFORMANCE_COUNTER.invokeExact(QPC_OUT);
         return QPC_OUT.get(ValueLayout.JAVA_LONG, 0L);
      } catch (Throwable t) {
         throw new RuntimeException(t);
      }
   }

   public static synchronized long qpcFrequency() {
      try {
         int ok = QUERY_PERFORMANCE_FREQUENCY.invokeExact(QPC_OUT);
         return QPC_OUT.get(ValueLayout.JAVA_LONG, 0L);
      } catch (Throwable t) {
         throw new RuntimeException(t);
      }
   }

   public static long tickCount() {
      try {
         return GET_TICK_COUNT64.invokeExact();
      } catch (Throwable t) {
         throw new RuntimeException(t);
      }
   }

   public static WaterGrid readWaterGrid() {
      MemorySegment s = shm;
      if (s == null) {
         return null;
      } else {
         long b = 1024L;
         WaterGrid out = new WaterGrid();

         for(int attempt = 0; attempt < 100; ++attempt) {
            int seq1 = INT.getAcquire(s, b + 0L);
            if ((seq1 & 1) == 0 && seq1 != 0) {
               out.originX = s.get(ValueLayout.JAVA_INT, b + 4L);
               out.originZ = s.get(ValueLayout.JAVA_INT, b + 8L);
               out.worldId = s.get(ValueLayout.JAVA_INT, b + 12L);

               for(int i = 0; i < out.surface.length; ++i) {
                  out.surface[i] = s.get(ValueLayout.JAVA_FLOAT, b + 16L + (long)i * 4L);
               }

               VarHandle.loadLoadFence();
               if (INT.getAcquire(s, b + 0L) == seq1) {
                  return out;
               }
            } else {
               Thread.onSpinWait();
               if (seq1 == 0) {
                  return null;
               }
            }
         }

         return null;
      }
   }

   public static boolean readSkyState(SkyState out) {
      MemorySegment s = shm;
      if (s == null) {
         return false;
      } else {
         long b = 256L;

         for(int attempt = 0; attempt < 1000; ++attempt) {
            int seq1 = INT.getAcquire(s, b + 0L);
            if ((seq1 & 1) != 0) {
               if (attempt > 100) {
                  Thread.yield();
               } else {
                  Thread.onSpinWait();
               }
            } else {
               out.flags = s.get(ValueLayout.JAVA_INT, b + 4L);
               out.worldId = s.get(ValueLayout.JAVA_INT, b + 8L);
               out.collisionEpoch = s.get(ValueLayout.JAVA_INT, b + 12L);
               out.x = s.get(ValueLayout.JAVA_DOUBLE, b + 16L);
               out.y = s.get(ValueLayout.JAVA_DOUBLE, b + 24L);
               out.z = s.get(ValueLayout.JAVA_DOUBLE, b + 32L);
               out.yaw = s.get(ValueLayout.JAVA_FLOAT, b + 40L);
               out.pitch = s.get(ValueLayout.JAVA_FLOAT, b + 44L);
               out.teleportSeq = s.get(ValueLayout.JAVA_INT, b + 48L);
               out.viewportW = s.get(ValueLayout.JAVA_INT, b + 52L);
               out.viewportH = s.get(ValueLayout.JAVA_INT, b + 56L);
               out.gameHour = s.get(ValueLayout.JAVA_FLOAT, b + 60L);
               VarHandle.loadLoadFence();
               int seq2 = INT.getAcquire(s, b + 0L);
               if (seq1 == seq2) {
                  out.seq = seq1;
                  return true;
               }
            }
         }

         return false;
      }
   }

   public static int skyStateSeq() {
      MemorySegment s = shm;
      return s == null ? 0 : INT.getAcquire(s, 256L);
   }

   public static void writeMcState(McState st) {
      MemorySegment s = shm;
      if (s != null) {
         long b = 512L;
         int seq = s.get(ValueLayout.JAVA_INT, b + 0L);
         INT.setRelease(s, b + 0L, seq + 1);
         VarHandle.storeStoreFence();
         s.set(ValueLayout.JAVA_INT, b + 4L, st.flags);
         s.set(ValueLayout.JAVA_DOUBLE, b + 8L, st.x);
         s.set(ValueLayout.JAVA_DOUBLE, b + 16L, st.y);
         s.set(ValueLayout.JAVA_DOUBLE, b + 24L, st.z);
         s.set(ValueLayout.JAVA_FLOAT, b + 32L, st.yaw);
         s.set(ValueLayout.JAVA_FLOAT, b + 36L, st.pitch);
         s.set(ValueLayout.JAVA_FLOAT, b + 40L, st.eyeHeight);
         s.set(ValueLayout.JAVA_FLOAT, b + 44L, st.sensitivity);
         s.set(ValueLayout.JAVA_INT, b + 48L, st.teleportAck);
         s.set(ValueLayout.JAVA_INT, b + 52L, st.guiScale);
         s.set(ValueLayout.JAVA_LONG, b + 56L, st.frameCounter);
         s.set(ValueLayout.JAVA_FLOAT, b + 64L, st.fov);
         s.set(ValueLayout.JAVA_FLOAT, b + 68L, st.bobPhase);
         s.set(ValueLayout.JAVA_FLOAT, b + 72L, st.bobAmount);
         s.set(ValueLayout.JAVA_DOUBLE, b + 80L, st.eyeX);
         s.set(ValueLayout.JAVA_DOUBLE, b + 88L, st.eyeY);
         s.set(ValueLayout.JAVA_DOUBLE, b + 96L, st.eyeZ);
         s.set(ValueLayout.JAVA_LONG, b + 104L, st.tickQpc);
         s.set(ValueLayout.JAVA_DOUBLE, b + 112L, st.prevX);
         s.set(ValueLayout.JAVA_DOUBLE, b + 112L + 8L, st.prevY);
         s.set(ValueLayout.JAVA_DOUBLE, b + 112L + 16L, st.prevZ);
         s.set(ValueLayout.JAVA_DOUBLE, b + 136L, st.curX);
         s.set(ValueLayout.JAVA_DOUBLE, b + 136L + 8L, st.curY);
         s.set(ValueLayout.JAVA_DOUBLE, b + 136L + 16L, st.curZ);
         s.set(ValueLayout.JAVA_FLOAT, b + 160L, st.eyeHeightO);
         s.set(ValueLayout.JAVA_FLOAT, b + 164L, st.eyeHeightT);
         s.set(ValueLayout.JAVA_FLOAT, b + 168L, st.walkDistO);
         s.set(ValueLayout.JAVA_FLOAT, b + 172L, st.walkDist);
         s.set(ValueLayout.JAVA_FLOAT, b + 176L, st.bobO);
         s.set(ValueLayout.JAVA_FLOAT, b + 180L, st.bob);
         s.set(ValueLayout.JAVA_FLOAT, b + 184L, st.tickMs);
         s.set(ValueLayout.JAVA_INT, b + 192L, st.cameraMode);
         s.set(ValueLayout.JAVA_FLOAT, b + 196L, st.cameraDistance);
         s.set(ValueLayout.JAVA_FLOAT, b + 200L, st.health);
         s.set(ValueLayout.JAVA_FLOAT, b + 204L, st.maxHealth);
         s.set(ValueLayout.JAVA_FLOAT, b + 208L, st.mouseSensitivity);
         INT.setRelease(s, b + 0L, seq + 2);
      }
   }

   public static void drainInput(InputSink sink) {
      MemorySegment s = shm;
      if (s != null) {
         long base = 4096L;
         long head = LONG.getAcquire(s, base + 0L);
         long tail = s.get(ValueLayout.JAVA_LONG, base + 64L);
         if (head - tail > 4096L) {
            tail = head - 4096L;
         }

         while(tail < head) {
            long e = base + 128L + (tail & 4095L) * 16L;
            int type = Short.toUnsignedInt(s.get(ValueLayout.JAVA_SHORT, e));
            int code = Short.toUnsignedInt(s.get(ValueLayout.JAVA_SHORT, e + 2L));
            int a = s.get(ValueLayout.JAVA_INT, e + 4L);
            int b = s.get(ValueLayout.JAVA_INT, e + 8L);
            int c = s.get(ValueLayout.JAVA_INT, e + 12L);
            ++tail;
            sink.accept(type, code, a, b, c);
         }

         LONG.setRelease(s, base + 64L, tail);
      }
   }

   public static byte[] packActors(List<Actor> actors) {
      ByteBuffer b = ByteBuffer.allocate(4 + actors.size() * 40).order(ByteOrder.LITTLE_ENDIAN);
      b.putInt(actors.size());

      for(Actor a : actors) {
         b.putInt(a.formId()).putInt(a.flags()).putFloat(a.x()).putFloat(a.y()).putFloat(a.z()).putFloat(a.yaw()).putFloat(a.width()).putFloat(a.height()).putFloat(a.healthFrac()).putInt(a.level());
      }

      return b.array();
   }

   public static List<Actor> unpackActors(byte[] packed) {
      ByteBuffer b = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
      List<Actor> out = new ArrayList();
      if (b.remaining() < 4) {
         return out;
      } else {
         int n = Math.min(b.getInt(), 256);

         for(int i = 0; i < n && b.remaining() >= 40; ++i) {
            out.add(new Actor(b.getInt(), b.getInt(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getInt(), ""));
         }

         return out;
      }
   }

   public static boolean readActors(List<Actor> out) {
      out.clear();
      MemorySegment s = shm;
      if (s == null) {
         return false;
      } else {
         long b = 73728L;

         for(int attempt = 0; attempt < 16; ++attempt) {
            int seq1 = INT.getAcquire(s, b + 0L);
            if ((seq1 & 1) != 0) {
               Thread.onSpinWait();
            } else {
               int count = Math.min(s.get(ValueLayout.JAVA_INT, b + 4L), 256);

               for(int i = 0; i < count; ++i) {
                  long r = b + 64L + (long)i * 64L;
                  out.add(new Actor(s.get(ValueLayout.JAVA_INT, r), s.get(ValueLayout.JAVA_INT, r + 4L), s.get(ValueLayout.JAVA_FLOAT, r + 8L), s.get(ValueLayout.JAVA_FLOAT, r + 12L), s.get(ValueLayout.JAVA_FLOAT, r + 16L), s.get(ValueLayout.JAVA_FLOAT, r + 20L), s.get(ValueLayout.JAVA_FLOAT, r + 24L), s.get(ValueLayout.JAVA_FLOAT, r + 28L), s.get(ValueLayout.JAVA_FLOAT, r + 32L), Short.toUnsignedInt(s.get(ValueLayout.JAVA_SHORT, r + 36L)), readName(s, r + 40L, 24)));
               }

               VarHandle.loadLoadFence();
               if (INT.getAcquire(s, b + 0L) == seq1) {
                  return true;
               }

               out.clear();
            }
         }

         return false;
      }
   }

   private static String readName(MemorySegment s, long off, int max) {
      byte[] bytes = new byte[max];

      int n;
      byte b;
      for(n = 0; n < max; bytes[n++] = b) {
         b = s.get(ValueLayout.JAVA_BYTE, off + (long)n);
         if (b == 0) {
            break;
         }
      }

      return new String(bytes, 0, n, StandardCharsets.UTF_8);
   }

   public static void pushEvent(int type, int formId, float a, float b, float c, float d, int flags) {
      pushEvent(type, formId, a, b, c, d, flags, 0);
   }

   public static synchronized void pushEvent(int type, int formId, float a, float b, float c, float d, int flags, int weapon) {
      MemorySegment s = shm;
      if (s != null) {
         long base = 94208L;
         long head = s.get(ValueLayout.JAVA_LONG, base + 0L);
         long tail = LONG.getAcquire(s, base + 64L);
         if (head - tail < 512L) {
            long e = base + 128L + (head & 511L) * 32L;
            s.set(ValueLayout.JAVA_INT, e, type);
            s.set(ValueLayout.JAVA_INT, e + 4L, formId);
            s.set(ValueLayout.JAVA_FLOAT, e + 8L, a);
            s.set(ValueLayout.JAVA_FLOAT, e + 12L, b);
            s.set(ValueLayout.JAVA_FLOAT, e + 16L, c);
            s.set(ValueLayout.JAVA_FLOAT, e + 20L, d);
            s.set(ValueLayout.JAVA_INT, e + 24L, flags);
            s.set(ValueLayout.JAVA_INT, e + 28L, weapon);
            LONG.setRelease(s, base + 0L, head + 1L);
         }
      }
   }

   public static void writeWorldEntities(List<WorldEntity> entities, float[] selection) {
      MemorySegment s = shm;
      if (s != null) {
         long b = 114688L;
         int seq = s.get(ValueLayout.JAVA_INT, b + 0L);
         INT.setRelease(s, b + 0L, seq + 1);
         VarHandle.storeStoreFence();
         int count = Math.min(entities.size(), 160);
         s.set(ValueLayout.JAVA_INT, b + 4L, count);
         s.set(ValueLayout.JAVA_INT, b + 8L, selection != null ? 1 : 0);
         if (selection != null) {
            for(int i = 0; i < 6; ++i) {
               s.set(ValueLayout.JAVA_FLOAT, b + 12L + (long)i * 4L, selection[i]);
            }
         }

         for(int i = 0; i < count; ++i) {
            WorldEntity w = (WorldEntity)entities.get(i);
            long r = b + 64L + (long)i * 96L;
            s.set(ValueLayout.JAVA_INT, r, w.kind());
            s.set(ValueLayout.JAVA_INT, r + 4L, w.id());
            s.set(ValueLayout.JAVA_FLOAT, r + 8L, w.x());
            s.set(ValueLayout.JAVA_FLOAT, r + 12L, w.y());
            s.set(ValueLayout.JAVA_FLOAT, r + 16L, w.z());
            s.set(ValueLayout.JAVA_FLOAT, r + 20L, w.yaw());
            s.set(ValueLayout.JAVA_FLOAT, r + 24L, w.pitch());
            s.set(ValueLayout.JAVA_FLOAT, r + 28L, w.scale());

            for(int k = 0; k < 3; ++k) {
               s.set(ValueLayout.JAVA_FLOAT, r + 32L + (long)k * 4L, w.ext() != null ? w.ext()[k] : 0.0F);
            }

            for(int k = 0; k < 12; ++k) {
               s.set(ValueLayout.JAVA_FLOAT, r + 44L + (long)k * 4L, w.uv() != null && k < w.uv().length ? w.uv()[k] : 0.0F);
            }

            s.set(ValueLayout.JAVA_INT, r + 92L, w.tint());
         }

         INT.setRelease(s, b + 0L, seq + 2);
      }
   }

   public static boolean writeRender(int type, ByteBuffer header, ByteBuffer body) {
      return writeRender(type, header, body, 500);
   }

   public static boolean tryWriteRender(int type, ByteBuffer header, ByteBuffer body) {
      return writeRender(type, header, body, 1);
   }

   private static synchronized boolean writeRender(int type, ByteBuffer header, ByteBuffer body, int attempts) {
      MemorySegment s = shm;
      if (s == null) {
         return false;
      } else {
         int payload = header.remaining() + (body != null ? body.remaining() : 0);
         long msgBytes = (long)(8 + payload + 7) & -8L;
         if (msgBytes > 33554368L) {
            SkyCraft.LOG.warn("SkyCraft: render message too large ({} bytes)", msgBytes);
            return false;
         } else {
            long base = 133218304L;

            for(int attempt = 0; attempt < attempts; ++attempt) {
               long head = s.get(ValueLayout.JAVA_LONG, base + 0L);
               long tail = LONG.getAcquire(s, base + 64L);
               long pos = head % 67108736L;
               long pad = pos + msgBytes > 67108736L ? 67108736L - pos : 0L;
               if (67108736L - (head - tail) >= msgBytes + pad) {
                  if (pad > 0L) {
                     s.set(ValueLayout.JAVA_INT, base + 128L + pos, 0);
                     s.set(ValueLayout.JAVA_INT, base + 128L + pos + 4L, 0);
                     head += pad;
                     pos = 0L;
                  }

                  long at = base + 128L + pos;
                  s.set(ValueLayout.JAVA_INT, at, type);
                  s.set(ValueLayout.JAVA_INT, at + 4L, payload);
                  MemorySegment.copy(MemorySegment.ofBuffer(header), 0L, s, at + 8L, (long)header.remaining());
                  if (body != null && body.remaining() > 0) {
                     MemorySegment.copy(MemorySegment.ofBuffer(body), 0L, s, at + 8L + (long)header.remaining(), (long)body.remaining());
                  }

                  LONG.setRelease(s, base + 0L, head + msgBytes);
                  return true;
               }

               if (attempt + 1 >= attempts) {
                  break;
               }

               try {
                  Thread.sleep(2L);
               } catch (InterruptedException var21) {
                  return false;
               }
            }

            return false;
         }
      }
   }

   public static long overlayBackSlotOffset() {
      return 33685504L + (long)overlayBack * 33177600L;
   }

   public static void publishOverlay(int width, int height, boolean bottomUp, long frameId) {
      MemorySegment s = shm;
      if (s != null) {
         long hdr = 832L + (long)overlayBack * 64L;
         s.set(ValueLayout.JAVA_INT, hdr + 0L, width);
         s.set(ValueLayout.JAVA_INT, hdr + 4L, height);
         s.set(ValueLayout.JAVA_INT, hdr + 8L, bottomUp ? 1 : 0);
         s.set(ValueLayout.JAVA_LONG, hdr + 16L, frameId);
         int old = INT.getAndSet(s, 768L, overlayBack | 4);
         overlayBack = old & 3;
         LONG.getAndAdd(s, 776L, 1L);
      }
   }

   public static long collisionHead() {
      MemorySegment s = shm;
      return s == null ? 0L : LONG.getAcquire(s, 131072L);
   }

   public static long collisionTail() {
      MemorySegment s = shm;
      return s == null ? 0L : s.get(ValueLayout.JAVA_LONG, 131136L);
   }

   public static void setCollisionTail(long tail) {
      MemorySegment s = shm;
      if (s != null) {
         LONG.setRelease(s, 131136L, tail);
      }

   }

   static {
      INT = ValueLayout.JAVA_INT.varHandle();
      LONG = ValueLayout.JAVA_LONG.varHandle();
      CALL_STATE = Option.captureStateLayout();
      LAST_ERROR = CALL_STATE.varHandle(new MemoryLayout.PathElement[]{PathElement.groupElement("GetLastError")});
      OPEN_STATE = Arena.global().allocate(CALL_STATE);
      lastOpenError = -1;
      QPC_OUT = Arena.global().allocate(ValueLayout.JAVA_LONG);
      Linker linker = Linker.nativeLinker();
      SymbolLookup k32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
      OPEN_FILE_MAPPING = linker.downcallHandle((MemorySegment)k32.find("OpenFileMappingW").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Option.captureCallState("GetLastError"));
      MAP_VIEW_OF_FILE = linker.downcallHandle((MemorySegment)k32.find("MapViewOfFile").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
      GET_TICK_COUNT64 = linker.downcallHandle((MemorySegment)k32.find("GetTickCount64").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG));
      GET_CURRENT_PROCESS_ID = linker.downcallHandle((MemorySegment)k32.find("GetCurrentProcessId").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT));
      QUERY_PERFORMANCE_COUNTER = linker.downcallHandle((MemorySegment)k32.find("QueryPerformanceCounter").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      QUERY_PERFORMANCE_FREQUENCY = linker.downcallHandle((MemorySegment)k32.find("QueryPerformanceFrequency").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      CREATE_MUTEX = linker.downcallHandle((MemorySegment)k32.find("CreateMutexW").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      overlayBack = 1;
   }

   public static final class SkyState {
      public int seq;
      public int flags;
      public int worldId;
      public int collisionEpoch;
      public double x;
      public double y;
      public double z;
      public float yaw;
      public float pitch;
      public int teleportSeq;
      public int viewportW;
      public int viewportH;
      public float gameHour;

      public boolean inGame() {
         return (this.flags & 1) != 0;
      }

      public boolean menuOpen() {
         return (this.flags & 2) != 0;
      }

      public boolean loading() {
         return (this.flags & 4) != 0;
      }
   }

   public static final class WaterGrid {
      public int originX;
      public int originZ;
      public int worldId;
      public final int size = 16;
      public final float[] surface = new float[256];
   }

   public static final class McState {
      public int flags;
      public double x;
      public double y;
      public double z;
      public float yaw;
      public float pitch;
      public float eyeHeight;
      public float sensitivity;
      public int teleportAck;
      public int guiScale;
      public long frameCounter;
      public float fov;
      public float bobPhase;
      public float bobAmount;
      public double eyeX;
      public double eyeY;
      public double eyeZ;
      public long tickQpc;
      public double prevX;
      public double prevY;
      public double prevZ;
      public double curX;
      public double curY;
      public double curZ;
      public float eyeHeightO;
      public float eyeHeightT;
      public float walkDistO;
      public float walkDist;
      public float bobO;
      public float bob;
      public float tickMs = 50.0F;
      public int cameraMode;
      public float cameraDistance;
      public float health;
      public float maxHealth;
      public float mouseSensitivity = 0.5F;
   }

   public static record Actor(int formId, int flags, float x, float y, float z, float yaw, float width, float height, float healthFrac, int level, String name) {
      public boolean dead() {
         return (this.flags & 2) != 0;
      }

      public boolean hostile() {
         return (this.flags & 1) != 0;
      }
   }

   public static record WorldEntity(int kind, int id, float x, float y, float z, float yaw, float pitch, float scale, float[] ext, float[] uv, int tint) {
   }

   public interface InputSink {
      void accept(int var1, int var2, int var3, int var4, int var5);
   }
}
