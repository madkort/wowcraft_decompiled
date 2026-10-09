package dev.skycraft;

import dev.skycraft.combat.SkyCombat;
import dev.skycraft.net.CoopNet;
import dev.skycraft.net.SkyNet;
import dev.skycraft.world.SkyDig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkyCraft implements ModInitializer {
   public static final String MOD_ID = "skycraft";
   public static final String WORLD_NAME = "SkyCraft";
   public static final Logger LOG = LoggerFactory.getLogger("skycraft");
   static final String KIT2_TAG = "skycraft_builder_kit";
   static final String FLIGHT_TAG = "wowcraft_flight_kit";

   public void onInitialize() {
      SkyCombat.init();
      SkyNet.init();
      CoopNet.init();
      SkyDig.init();
      ServerLifecycleEvents.SERVER_STARTED.register(SkyCraft::configureServer);
      ServerPlayConnectionEvents.JOIN.register((ServerPlayConnectionEvents.Join)(handler, sender, server) -> {
         giveStarterKit(handler.getPlayer());
         giveBuilderKit(handler.getPlayer());
         giveFlightKit(handler.getPlayer());
         Progression.giveGuide(handler.getPlayer());
         if ("1".equals(System.getenv("WOWCRAFT_TEST_SURVIVAL"))) {
            handler.getPlayer().setGameMode(GameType.SURVIVAL);
         }

         dressTestGuest(handler.getPlayer());
      });
   }

   private static void configureServer(MinecraftServer server) {
      GameRules rules = server.getGameRules();
      rules.set(GameRules.ADVANCE_TIME, false, server);
      rules.set(GameRules.ADVANCE_WEATHER, false, server);
      rules.set(GameRules.SPAWN_MOBS, false, server);
      rules.set(GameRules.SPAWN_MONSTERS, false, server);
      rules.set(GameRules.SPAWN_PHANTOMS, false, server);
      rules.set(GameRules.SPAWN_PATROLS, false, server);
      rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
      rules.set(GameRules.PLAYER_MOVEMENT_CHECK, false, server);
      rules.set(GameRules.KEEP_INVENTORY, true, server);
      rules.set(GameRules.IMMEDIATE_RESPAWN, true, server);
      rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
      server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
      LOG.info("SkyCraft: mirror world configured");
   }

   private static void dressTestGuest(ServerPlayer player) {
      if (player.getName().getString().startsWith("Guest")) {
         RandomSource random = player.getRandom();
         EquipmentSlot[] slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
         Item[][] pieces = new Item[][]{{Items.IRON_HELMET, Items.DIAMOND_HELMET}, {Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE}, {Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS}, {Items.IRON_BOOTS, Items.DIAMOND_BOOTS}};

         for(int i = 0; i < slots.length; ++i) {
            player.setItemSlot(slots[i], new ItemStack(pieces[i][random.nextBoolean() ? 1 : 0]));
         }

         LOG.info("SkyCraft: dressed test guest {} in iron and diamond", player.getName().getString());
      }
   }

   static void giveStarterKit(ServerPlayer player) {
      if (player.getInventory().isEmpty()) {
         player.getInventory().add(new ItemStack(Items.STONE_SWORD));
         player.getInventory().add(new ItemStack(Items.STONE_PICKAXE));
         player.getInventory().add(new ItemStack(Items.STONE_AXE));
         player.getInventory().add(new ItemStack(Items.STONE_SHOVEL));
         player.getInventory().add(new ItemStack(Items.BOW));
         player.getInventory().add(new ItemStack(Items.ARROW, 16));
         player.getInventory().add(new ItemStack(Items.BREAD, 16));
         player.getInventory().add(new ItemStack(Items.TORCH, 16));
         LOG.info("SkyCraft: gave starter kit to {}", player.getName().getString());
      }
   }

   static void giveBuilderKit(ServerPlayer player) {
      if (!player.entityTags().contains("skycraft_builder_kit")) {
         equipIfEmpty(player, EquipmentSlot.HEAD, Items.LEATHER_HELMET);
         equipIfEmpty(player, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE);
         equipIfEmpty(player, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS);
         equipIfEmpty(player, EquipmentSlot.FEET, Items.LEATHER_BOOTS);
         Inventory inventory = player.getInventory();
         inventory.add(new ItemStack(Items.COBBLESTONE, 32));
         inventory.add(new ItemStack(Items.OAK_LOG, 16));
         inventory.add(new ItemStack(Items.CRAFTING_TABLE));
         inventory.add(new ItemStack(Items.LADDER, 8));
         player.addTag("skycraft_builder_kit");
         LOG.info("SkyCraft: gave builder kit to {}", player.getName().getString());
      }
   }

   static void giveFlightKit(ServerPlayer player) {
      Inventory inventory = player.getInventory();
      if (!player.entityTags().contains("wowcraft_flight_kit")) {
         ItemStack elytra = new ItemStack(Items.ELYTRA);
         elytra.enchant(player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
         inventory.add(elytra);
         player.addTag("wowcraft_flight_kit");
         LOG.info("WowCraft: gave an elytra to {}", player.getName().getString());
      }

      int rockets = inventory.countItem(Items.FIREWORK_ROCKET);

      for(int missing = 128 - rockets; missing > 0; missing -= 64) {
         inventory.add(new ItemStack(Items.FIREWORK_ROCKET, Math.min(64, missing)));
      }

   }

   private static void equipIfEmpty(ServerPlayer player, EquipmentSlot slot, Item item) {
      if (player.getItemBySlot(slot).isEmpty()) {
         player.setItemSlot(slot, new ItemStack(item));
      } else {
         player.getInventory().add(new ItemStack(item));
      }

   }
}
