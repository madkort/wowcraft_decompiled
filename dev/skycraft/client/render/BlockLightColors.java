package dev.skycraft.client.render;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class BlockLightColors {
   private static final int HAZARD_FIRE = 268435456;
   private static final int HAZARD_LAVA = 536870912;
   private static final int HAZARD_MAGMA = 805306368;

   private BlockLightColors() {
   }

   static int of(BlockState state) {
      Block block = state.getBlock();
      String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
      if (id.contains("soul")) {
         int kind = id.contains("lantern") ? 0 : 1;
         return pack(90, 210, 255, kind) | (id.endsWith("fire") ? 268435456 : 0);
      } else if (id.contains("lava")) {
         return pack(255, 105, 25, 2) | 536870912;
      } else if (id.equals("magma_block")) {
         return pack(255, 105, 25, 2) | 805306368;
      } else if (id.contains("redstone_lamp")) {
         return pack(255, 215, 150, 0);
      } else if (id.contains("redstone")) {
         return pack(255, 55, 30, 0);
      } else if (id.contains("jack_o_lantern")) {
         return pack(255, 165, 60, 1);
      } else if (id.contains("torch")) {
         return pack(255, 185, 105, 1);
      } else if (id.contains("lantern") && !id.contains("sea")) {
         return pack(255, 195, 120, 1);
      } else if (id.contains("campfire")) {
         return pack(255, 165, 85, 1) | 268435456;
      } else if (id.equals("fire")) {
         return pack(255, 145, 55, 1) | 268435456;
      } else if (id.contains("candle")) {
         return pack(255, 180, 105, 1);
      } else if (!id.contains("furnace") && !id.equals("smoker")) {
         if (id.equals("glowstone")) {
            return pack(255, 215, 140, 0);
         } else if (id.equals("shroomlight")) {
            return pack(255, 175, 105, 0);
         } else if (!id.equals("sea_lantern") && !id.equals("beacon") && !id.equals("end_rod")) {
            if (id.equals("conduit")) {
               return pack(160, 225, 255, 0);
            } else if (id.contains("ochre_froglight")) {
               return pack(255, 220, 140, 0);
            } else if (id.contains("verdant_froglight")) {
               return pack(170, 255, 150, 0);
            } else if (id.contains("pearlescent_froglight")) {
               return pack(255, 190, 240, 0);
            } else if (id.contains("amethyst")) {
               return pack(190, 130, 255, 0);
            } else if (!id.contains("crying_obsidian") && !id.equals("nether_portal") && !id.equals("respawn_anchor") && !id.equals("dragon_egg")) {
               if (!id.startsWith("end_portal") && !id.equals("end_gateway")) {
                  if (id.contains("sculk")) {
                     return pack(60, 200, 220, 0);
                  } else if (!id.contains("glow") && !id.contains("cave_vines")) {
                     if (id.contains("copper_bulb")) {
                        return pack(255, 190, 130, 0);
                     } else {
                        return !id.contains("spawner") && !id.equals("vault") && !id.equals("creaking_heart") ? pack(255, 225, 190, 0) : pack(255, 165, 90, 1);
                     }
                  } else {
                     return pack(255, 210, 130, 0);
                  }
               } else {
                  return pack(120, 230, 200, 0);
               }
            } else {
               return pack(150, 65, 255, 0);
            }
         } else {
            return pack(205, 235, 255, 0);
         }
      } else {
         return pack(255, 150, 60, 1);
      }
   }

   private static int pack(int r, int g, int b, int kind) {
      return r | g << 8 | b << 16 | kind << 24;
   }
}
