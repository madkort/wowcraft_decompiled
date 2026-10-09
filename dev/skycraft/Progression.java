package dev.skycraft;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

public final class Progression {
   private static final String[] TOOLS = new String[]{"wooden", "stone", "iron", "diamond", "netherite"};
   private static final String[] ARMOR = new String[]{"leather", "chainmail", "iron", "diamond", "netherite"};
   private static final String[] TOOL_KINDS = new String[]{"sword", "pickaxe", "axe", "shovel", "hoe"};
   private static final String[] ARMOR_KINDS = new String[]{"helmet", "chestplate", "leggings", "boots"};
   public static final int[] TIER_LEVELS = new int[]{1, 10, 20, 35, 50};
   static final String TIER_TAG = "wowcraft_tier_";
   static final String GUIDE_TAG = "wowcraft_guide_6";
   private static final String[] GUIDE = new String[]{"WowCraft\n\nMinecraft, played inside World of Warcraft.\n\nYour hearts are your life. You fight WoW's creatures with Minecraft weapons.", "This book has the keys and how things work.\n\nKeep it: it's yours on every character.", "WoW keys\n\nC: WoW cursor on/off (NPCs, loot, quests)\nEnter: WoW chat\nM: map\nL: quest log\nB: bags\nO: WoW menu", "Minecraft keys\n\nSpace: jump; get off a boat, zeppelin or tram\nE: inventory\nT: Minecraft chat\nEsc: Minecraft menu", "Your first quest\n\nFind an NPC with a yellow ! over its head. Press C, click it, Accept. Press C again to fight.", "A yellow ? means the quest is done: hand it in the same way.\n\nGreen names are friends: you can't hit them.", "Your inn\n\nTalk to an innkeeper (C, click) and pick \"Make this inn your home\". Your Hearthstone takes you back there.", "Traders\n\nInnkeepers and general goods vendors (green label) trade blocks and mystery boxes: right-click them.", "Gear\n\nGear doesn't level up by itself: craft it, or find it.\n\nMystery boxes give ores, diamonds, potions and ender pearls.", "Creatures drop loot and emeralds; quests reward gear.\n\nEach WoW character keeps its own Minecraft inventory.", "Gathering\n\nMine WoW's ore veins and pick its herbs (seeds and crops).\n\nTown anvils, forges and cooking fires work.", "Fish in WoW's lakes and seas; boats float on them.", "Travel\n\nFlight paths fly you.\n\nBoats, zeppelins, trams, lifts: step on while it's docked and it carries you. Space: get off.", "Your elytra and rockets fly anywhere.\n\nDying: you wake at the graveyard.", "Commands\n\nIn WoW chat (Enter):\n\n.goname Name: go to a player (WoW character name)\n.namego Name: bring a player to you (WoW character name)", ".levelup 10: gain 10 levels. To level a friend, first /target Name (their WoW character name).\n\n.tele Orgrimmar: go to a place\n.lookup tele storm: find places", ".go xyz X Y Z map: exact spot\n.gps: your coordinates\n.gm on / .gm off: creatures ignore you", "In Minecraft chat (T):\n/gamemode creative\n/gamemode survival", "Together\n\nHost from the WowCraft launcher. Your friend joins with the link it shows (Copy).", "The link is hidden in game, for streams.\n\nHave fun!"};

   private Progression() {
   }

   private static int reached(ServerPlayer player) {
      int best = 0;

      for(int t = 1; t < TOOLS.length; ++t) {
         if (player.entityTags().contains("wowcraft_tier_" + t)) {
            best = t;
         }
      }

      return best;
   }

   public static void reach(ServerPlayer player, int tier) {
      tier = Math.max(0, Math.min(tier, TOOLS.length - 1));
      if (tier > reached(player)) {
         player.addTag("wowcraft_tier_" + tier);
         int upgraded = 0;
         Inventory inventory = player.getInventory();

         for(int i = 0; i < 36; ++i) {
            ItemStack up = upgraded(inventory.getItem(i), tier);
            if (up != null) {
               inventory.setItem(i, up);
               ++upgraded;
            }
         }

         for(EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            ItemStack up = upgraded(player.getItemBySlot(slot), tier);
            if (up != null) {
               player.setItemSlot(slot, up);
               ++upgraded;
            }
         }

         String name = TOOLS[tier].equals("wooden") ? "wood" : TOOLS[tier];
         int var10001 = TIER_LEVELS[tier];
         player.sendOverlayMessage(Component.literal("Level " + var10001 + ": your gear is " + name + " now!").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(5635925))));
         player.level().playSound((Entity)null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
         SkyCraft.LOG.info("WowCraft: {} reached tier {} ({} pieces upgraded)", new Object[]{player.getName().getString(), tier, upgraded});
      }
   }

   private static ItemStack upgraded(ItemStack stack, int tier) {
      if (stack.isEmpty()) {
         return null;
      } else {
         String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
         int cut = id.indexOf(95);
         if (cut < 0) {
            return null;
         } else {
            String material = id.substring(0, cut);
            String kind = id.substring(cut + 1);
            String[] materials = List.of(TOOL_KINDS).contains(kind) ? TOOLS : (List.of(ARMOR_KINDS).contains(kind) ? ARMOR : null);
            if (materials == null) {
               return null;
            } else {
               int now = List.of(materials).indexOf(material);
               if (now >= 0 && now < tier) {
                  Item to = (Item)BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(materials[tier] + "_" + kind));
                  if (to == Items.AIR) {
                     return null;
                  } else {
                     ItemStack up = stack.transmuteCopy(to);
                     up.setDamageValue(0);
                     return up;
                  }
               } else {
                  return null;
               }
            }
         }
      }
   }

   public static void giveGuide(ServerPlayer player) {
      if (!player.entityTags().contains("wowcraft_guide_6")) {
         player.addTag("wowcraft_guide_6");
         if (!refreshGuide(player)) {
            if (!player.getInventory().add(guideBook())) {
               player.spawnAtLocation(player.level(), guideBook());
            }

         }
      }
   }

   public static boolean refreshGuide(ServerPlayer player) {
      Inventory inventory = player.getInventory();
      boolean found = false;

      for(int i = 0; i < inventory.getContainerSize(); ++i) {
         WrittenBookContent content = (WrittenBookContent)inventory.getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
         if (content != null && "WowCraft guide".equals(content.title().raw()) && "WowCraft".equals(content.author())) {
            inventory.setItem(i, guideBook());
            found = true;
         }
      }

      return found;
   }

   public static ItemStack guideBook() {
      ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
      List<Filterable<Component>> pages = new ArrayList();

      for(String page : GUIDE) {
         pages.add(Filterable.passThrough(Component.literal(page)));
      }

      book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("WowCraft guide"), "WowCraft", 0, pages, true));
      return book;
   }
}
