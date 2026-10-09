package dev.skycraft.combat;

import dev.skycraft.SkyCraft;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.FireworkExplosion.Shape;

public final class MysteryBox {
   private static final String TAG = "wowcraft_box";
   private static final Find[] FOOD = new Find[]{new Find("bread", 4, 8, 10), new Find("cooked_beef", 3, 6, 8), new Find("cooked_porkchop", 3, 6, 6), new Find("baked_potato", 4, 8, 6), new Find("cooked_salmon", 3, 6, 5), new Find("golden_carrot", 2, 4, 3)};
   private static final Find[] RESOURCES = new Find[]{new Find("coal", 6, 16, 10), new Find("iron_ingot", 2, 6, 8), new Find("copper_ingot", 4, 10, 5), new Find("gold_ingot", 1, 4, 5), new Find("redstone", 4, 12, 5), new Find("lapis_lazuli", 4, 10, 4), new Find("emerald", 2, 5, 2), new Find("diamond", 1, 2, 1)};
   private static final Find[] BLOCKS = new Find[]{new Find("oak_log", 8, 16, 8), new Find("stone_bricks", 16, 32, 8), new Find("glass", 16, 32, 6), new Find("bricks", 16, 32, 6), new Find("white_wool", 8, 16, 5), new Find("quartz_block", 8, 16, 4), new Find("lantern", 2, 6, 4), new Find("bookshelf", 2, 4, 3), new Find("sea_lantern", 2, 4, 2)};
   private static final Find[] RARE = new Find[]{new Find("ender_pearl", 1, 3, 6), new Find("experience_bottle", 3, 8, 6), new Find("golden_apple", 1, 2, 5), new Find("name_tag", 1, 1, 3), new Find("diamond", 1, 3, 3), new Find("enchanted_golden_apple", 1, 1, 1)};
   private static final Find[] POTIONS = new Find[]{new Find("potion:healing", 1, 2, 8), new Find("splash:healing", 1, 2, 4), new Find("potion:strength", 1, 1, 6), new Find("potion:regeneration", 1, 1, 5), new Find("potion:swiftness", 1, 1, 4), new Find("tipped:harming", 4, 8, 5), new Find("firework", 3, 6, 3)};

   private MysteryBox() {
   }

   private static ItemStack make(Find find, int count) {
      String id = find.id();
      int cut = id.indexOf(58);
      if (cut > 0) {
         String kind = id.substring(0, cut);
         Holder var10000;
         switch (id.substring(cut + 1)) {
            case "healing" -> var10000 = Potions.HEALING;
            case "strength" -> var10000 = Potions.STRENGTH;
            case "regeneration" -> var10000 = Potions.REGENERATION;
            case "swiftness" -> var10000 = Potions.SWIFTNESS;
            default -> var10000 = Potions.HARMING;
         }

         Holder<Potion> holder = var10000;
         Item var14;
         switch (kind) {
            case "splash" -> var14 = Items.SPLASH_POTION;
            case "tipped" -> var14 = Items.TIPPED_ARROW;
            default -> var14 = Items.POTION;
         }

         Item item = var14;
         ItemStack stack = new ItemStack(item, count);
         stack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder));
         return stack;
      } else if (id.equals("firework")) {
         ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET, count);
         FireworkExplosion burst = new FireworkExplosion(Shape.LARGE_BALL, IntList.of(16733474), IntList.of(), false, false);
         stack.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(burst, burst)));
         return stack;
      } else {
         Item item = (Item)BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
         return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
      }
   }

   public static ItemStack box(int tier) {
      ItemStack stack = new ItemStack(Items.CHEST);
      CompoundTag tag = new CompoundTag();
      tag.putInt("wowcraft_box", tier);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(tier >= 3 ? "Legendary mystery box" : (tier >= 2 ? "Rich mystery box" : "Mystery box")).withStyle(tier >= 3 ? ChatFormatting.GOLD : (tier >= 2 ? ChatFormatting.AQUA : ChatFormatting.WHITE)));
      if (tier >= 2) {
         stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      }

      return stack;
   }

   public static int tier(ItemStack stack) {
      if (!stack.is(Items.CHEST)) {
         return 0;
      } else {
         CustomData data = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
         return data == null ? 0 : data.copyTag().getIntOr("wowcraft_box", 0);
      }
   }

   public static void init() {
      UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> use(player, hand));
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hit) -> use(player, hand));
   }

   private static InteractionResult use(Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      int tier = tier(stack);
      if (tier == 0) {
         return InteractionResult.PASS;
      } else {
         if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            open(sp, tier);
            stack.consume(1, player);
         }

         return InteractionResult.SUCCESS;
      }
   }

   private static void open(ServerPlayer player, int tier) {
      ServerLevel level = player.level();
      RandomSource random = level.getRandom();
      int[] weights = tier >= 3 ? new int[]{0, 20, 0, 45, 35} : (tier >= 2 ? new int[]{10, 36, 18, 14, 22} : new int[]{40, 18, 40, 2, 0});
      Find[][] pools = new Find[][]{FOOD, RESOURCES, BLOCKS, RARE, POTIONS};
      int count = tier >= 3 ? 3 : (tier >= 2 ? 4 : 3 + random.nextInt(2));
      StringBuilder got = new StringBuilder();
      List<ItemStack> sure = new ArrayList();
      if (tier == 2) {
         sure.add(new ItemStack(Items.ENDER_PEARL, 2 + random.nextInt(3)));
      } else if (tier >= 3) {
         sure.add(new ItemStack(Items.ENDER_PEARL, 8 + random.nextInt(9)));
         sure.add(new ItemStack(Items.DIAMOND, 2 + random.nextInt(3)));
         sure.add(random.nextFloat() < 0.35F ? new ItemStack(Items.ENCHANTED_GOLDEN_APPLE) : new ItemStack(Items.GOLDEN_APPLE, 2 + random.nextInt(3)));
         if (random.nextFloat() < 0.25F) {
            sure.add(new ItemStack(Items.TOTEM_OF_UNDYING));
         }
      }

      for(ItemStack loot : sure) {
         got.append(loot.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(loot.getItem()).getPath()).append(", ");
         if (!player.getInventory().add(loot)) {
            player.spawnAtLocation(level, loot);
         }
      }

      for(int i = 0; i < count; ++i) {
         Find find = pick(pick(pools, weights, random), random);
         ItemStack loot = make(find, find.min() + random.nextInt(find.max() - find.min() + 1));
         if (!loot.isEmpty()) {
            got.append(loot.getCount()).append(' ').append(find.id()).append(", ");
            if (!player.getInventory().add(loot)) {
               player.spawnAtLocation(level, loot);
            }
         }
      }

      level.playSound((Entity)null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.6F, 1.2F);
      if (tier >= 3) {
         level.playSound((Entity)null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
         level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.2, player.getZ(), 60, 0.6, 0.8, 0.6, 0.4);
         level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + (double)1.5F, player.getZ(), 40, 0.8, 0.8, 0.8, 0.15);
         player.sendOverlayMessage(Component.literal("LEGENDARY!").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}));
      } else {
         level.playSound((Entity)null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.6F);
         level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + (double)1.0F, player.getZ(), 14, 0.4, (double)0.5F, 0.4, 0.05);
      }

      SkyCraft.LOG.info("WowCraft: {} opened a {} mystery box: {}", new Object[]{player.getName().getString(), tier >= 3 ? "legendary" : (tier >= 2 ? "rich" : "plain"), got});
   }

   private static Find[] pick(Find[][] pools, int[] weights, RandomSource random) {
      int total = 0;

      for(int w : weights) {
         total += w;
      }

      int roll = random.nextInt(total);

      for(int i = 0; i < pools.length; ++i) {
         roll -= weights[i];
         if (roll < 0) {
            return pools[i];
         }
      }

      return pools[0];
   }

   private static Find pick(Find[] pool, RandomSource random) {
      int total = 0;

      for(Find f : pool) {
         total += f.weight();
      }

      int roll = random.nextInt(total);

      for(Find f : pool) {
         roll -= f.weight();
         if (roll < 0) {
            return f;
         }
      }

      return pool[0];
   }

   private static record Find(String id, int min, int max, int weight) {
   }
}
