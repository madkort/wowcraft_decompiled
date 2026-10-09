package dev.skycraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

public final class CharacterGear {
   private static final String CHAR_TAG = "wowcraft_char_";

   private CharacterGear() {
   }

   private static Path file(ServerPlayer player, long character) {
      Path var10000 = player.level().getServer().getWorldPath(LevelResource.ROOT).resolve("wowcraft-characters");
      String var10001 = String.valueOf(player.getUUID());
      return var10000.resolve(var10001 + "_" + Long.toHexString(character) + ".dat");
   }

   private static long current(ServerPlayer player) {
      for(String tag : player.entityTags()) {
         if (tag.startsWith("wowcraft_char_")) {
            try {
               return Long.parseUnsignedLong(tag.substring("wowcraft_char_".length()), 16);
            } catch (NumberFormatException var4) {
            }
         }
      }

      return 0L;
   }

   public static void playing(ServerPlayer player, long character) {
      long was = current(player);
      if (character != 0L && was != character) {
         if (was != 0L) {
            player.removeTag("wowcraft_char_" + Long.toHexString(was));
         }

         player.addTag("wowcraft_char_" + Long.toHexString(character));
         if (was == 0L) {
            SkyCraft.LOG.info("WowCraft: {}'s Minecraft things belong to WoW character {}", player.getName().getString(), Long.toHexString(character));
         } else {
            save(player, was);
            clear(player);
            if (!load(player, character)) {
               for(String tag : Set.copyOf(player.entityTags())) {
                  if (tag.startsWith("wowcraft_tier_") || tag.equals("skycraft_builder_kit") || tag.equals("wowcraft_flight_kit") || tag.equals("wowcraft_guide_6")) {
                     player.removeTag(tag);
                  }
               }

               SkyCraft.giveStarterKit(player);
               SkyCraft.giveBuilderKit(player);
               SkyCraft.giveFlightKit(player);
               Progression.giveGuide(player);
               SkyCraft.LOG.info("WowCraft: WoW character {} starts fresh in Minecraft", Long.toHexString(character));
            }

         }
      }
   }

   private static void clear(ServerPlayer player) {
      player.getInventory().clearContent();

      for(EquipmentSlot slot : EquipmentSlot.values()) {
         if (slot.getType() == Type.HUMANOID_ARMOR || slot == EquipmentSlot.OFFHAND || slot == EquipmentSlot.MAINHAND) {
            player.setItemSlot(slot, ItemStack.EMPTY);
         }
      }

      player.experienceLevel = 0;
      player.experienceProgress = 0.0F;
      player.totalExperience = 0;

      for(String tag : Set.copyOf(player.entityTags())) {
         if (tag.startsWith("wowcraft_tier_")) {
            player.removeTag(tag);
         }
      }

   }

   private static void save(ServerPlayer player, long character) {
      RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
      CompoundTag root = new CompoundTag();
      ListTag items = new ListTag();
      Inventory inventory = player.getInventory();

      for(int i = 0; i < 36; ++i) {
         put(items, ops, "slot", i, inventory.getItem(i));
      }

      for(EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
         put(items, ops, "equip", slot.ordinal(), player.getItemBySlot(slot));
      }

      root.put("items", items);
      root.putInt("level", player.experienceLevel);
      root.putFloat("progress", player.experienceProgress);
      root.putInt("total", player.totalExperience);
      ListTag tags = new ListTag();

      for(String tag : player.entityTags()) {
         if (tag.startsWith("wowcraft_tier_")) {
            tags.add(StringTag.valueOf(tag));
         }
      }

      root.put("tags", tags);
      Path path = file(player, character);

      try {
         Files.createDirectories(path.getParent());
         Path tmp = path.resolveSibling(String.valueOf(path.getFileName()) + ".tmp");
         NbtIo.writeCompressed(root, tmp);
         Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
         SkyCraft.LOG.info("WowCraft: put away WoW character {}'s Minecraft things", Long.toHexString(character));
      } catch (IOException e) {
         SkyCraft.LOG.warn("WowCraft: couldn't save {}", path, e);
      }

   }

   private static void put(ListTag items, RegistryOps<Tag> ops, String where, int index, ItemStack stack) {
      if (!stack.isEmpty()) {
         ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent((tag) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("where", where);
            entry.putInt("index", index);
            entry.put("item", tag);
            items.add(entry);
         });
      }
   }

   private static boolean load(ServerPlayer player, long character) {
      Path path = file(player, character);
      if (!Files.exists(path, new LinkOption[0])) {
         return false;
      } else {
         try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            Inventory inventory = player.getInventory();
            EquipmentSlot[] slots = EquipmentSlot.values();

            for(Tag t : root.getListOrEmpty("items")) {
               if (t instanceof CompoundTag) {
                  CompoundTag entry = (CompoundTag)t;
                  ItemStack stack = (ItemStack)ItemStack.CODEC.parse(ops, entry.get("item")).result().orElse(ItemStack.EMPTY);
                  int index = entry.getIntOr("index", -1);
                  if ("slot".equals(entry.getStringOr("where", "")) && index >= 0 && index < 36) {
                     inventory.setItem(index, stack);
                  } else if (index >= 0 && index < slots.length) {
                     player.setItemSlot(slots[index], stack);
                  }
               }
            }

            player.experienceLevel = root.getIntOr("level", 0);
            player.experienceProgress = root.getFloatOr("progress", 0.0F);
            player.totalExperience = root.getIntOr("total", 0);
            Set<String> tags = new HashSet();

            for(Tag t : root.getListOrEmpty("tags")) {
               Optional var10000 = t.asString();
               Objects.requireNonNull(tags);
               var10000.ifPresent(tags::add);
            }

            Objects.requireNonNull(player);
            tags.forEach(player::addTag);
            Progression.refreshGuide(player);
            SkyCraft.LOG.info("WowCraft: WoW character {}'s Minecraft things are back", Long.toHexString(character));
            return true;
         } catch (IOException e) {
            SkyCraft.LOG.warn("WowCraft: couldn't read {}", path, e);
            return false;
         }
      }
   }
}
