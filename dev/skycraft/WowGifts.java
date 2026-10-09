package dev.skycraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public final class WowGifts {
   public static final String[] TABLE = new String[]{"experience", "beef", "porkchop", "mutton", "chicken", "rabbit", "cod", "salmon", "cooked_beef", "cooked_porkchop", "cooked_mutton", "cooked_chicken", "cooked_rabbit", "cooked_cod", "cooked_salmon", "emerald", "bread", "golden_apple", "golden_carrot", "stone_sword", "iron_sword", "diamond_sword", "netherite_sword", "stone_axe", "iron_axe", "diamond_axe", "netherite_axe", "bow", "crossbow", "shield", "arrow", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "bone", "rotten_flesh", "string", "spider_eye", "leather", "feather", "gunpowder", "redstone", "glowstone_dust", "iron_nugget", "gold_nugget", "iron_ingot", "coal", "ink_sac", "blaze_powder", "ender_pearl", "slime_ball", "torch"};

   private WowGifts() {
   }

   public static void init() {
      ServerTickEvents.END_SERVER_TICK.register((ServerTickEvents.EndTick)(server) -> {
         for(ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive() && !player.isSpectator()) {
               for(ExperienceOrb orb : player.level().getEntitiesOfClass(ExperienceOrb.class, player.getBoundingBox().inflate((double)2.5F))) {
                  player.takeXpDelay = 0;
                  orb.playerTouch(player);
               }
            }
         }

      });
   }

   public static void give(ServerPlayer player, int code, int count, int enchant) {
      if (code >= 0 && code < TABLE.length && count > 0) {
         if (code == 0) {
            player.giveExperiencePoints(count);
         } else {
            Item item = (Item)BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(TABLE[code]));
            ItemStack stack = new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize() * 4));
            if (enchant > 0) {
               enchant(player, stack, Math.min(enchant, 3));
            }

            String name = stack.getHoverName().getString();
            int n = stack.getCount();

            while(!stack.isEmpty()) {
               ItemStack part = stack.split(Math.min(stack.getCount(), stack.getMaxStackSize()));
               if (!player.getInventory().add(part)) {
                  player.spawnAtLocation(player.level(), part);
               }
            }

            SkyCraft.LOG.info("WowCraft: gave {} x {}", n, name);
            player.level().playSound((Entity)null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
         }
      }
   }

   public static void drop(ServerPlayer player, int code, int count, double x, double y, double z) {
      if (code >= 0 && code < TABLE.length && count > 0) {
         if (player.distanceToSqr(x, y, z) > (double)4096.0F) {
            x = player.getX();
            y = player.getY() + (double)0.5F;
            z = player.getZ();
         }

         if (code == 0) {
            ExperienceOrb.award(player.level(), new Vec3(x, y, z), count);
         } else {
            Item item = (Item)BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(TABLE[code]));
            ItemStack stack = new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize()));
            ServerLevel level = player.level();
            ItemEntity entity = new ItemEntity(level, x, y, z, stack, level.getRandom().nextGaussian() * 0.08, (double)0.25F, level.getRandom().nextGaussian() * 0.08);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
            SkyCraft.LOG.info("WowCraft: dropped {} x {} at {} {} {}", new Object[]{stack.getCount(), stack.getHoverName().getString(), (int)x, (int)y, (int)z});
         }
      }
   }

   private static void enchant(ServerPlayer player, ItemStack stack, int level) {
      String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
      ResourceKey<Enchantment> main;
      if (!id.endsWith("sword") && !id.endsWith("axe")) {
         if (id.equals("bow")) {
            main = Enchantments.POWER;
         } else if (id.equals("crossbow")) {
            main = Enchantments.QUICK_CHARGE;
         } else if (!id.endsWith("helmet") && !id.endsWith("chestplate") && !id.endsWith("leggings") && !id.endsWith("boots")) {
            main = Enchantments.UNBREAKING;
         } else {
            main = Enchantments.PROTECTION;
         }
      } else {
         main = Enchantments.SHARPNESS;
      }

      Registry<Enchantment> lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
      stack.enchant(lookup.getOrThrow(main), level);
      if (main != Enchantments.UNBREAKING && level >= 2) {
         stack.enchant(lookup.getOrThrow(Enchantments.UNBREAKING), level - 1);
      }

   }
}
