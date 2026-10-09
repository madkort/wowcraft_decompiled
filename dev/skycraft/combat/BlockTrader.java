package dev.skycraft.combat;

import dev.skycraft.Progression;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

public final class BlockTrader implements Merchant {
   private static final Trade[] TRADES = new Trade[]{new Trade(1, "cobblestone", 64), new Trade(1, "stone", 64), new Trade(1, "dirt", 64), new Trade(1, "sand", 64), new Trade(1, "gravel", 32), new Trade(1, "glass", 32), new Trade(1, "torch", 32), new Trade(1, "ladder", 32), new Trade(1, "oak_planks", 64), new Trade(1, "spruce_planks", 64), new Trade(1, "birch_planks", 64), new Trade(1, "dark_oak_planks", 64), new Trade(1, "cherry_planks", 64), new Trade(1, "mangrove_planks", 64), new Trade(1, "oak_log", 32), new Trade(1, "spruce_log", 32), new Trade(1, "dark_oak_log", 32), new Trade(1, "stripped_oak_log", 32), new Trade(1, "stone_bricks", 48), new Trade(1, "mossy_stone_bricks", 32), new Trade(1, "smooth_stone", 48), new Trade(1, "bricks", 32), new Trade(1, "sandstone", 48), new Trade(1, "smooth_sandstone", 32), new Trade(1, "deepslate_bricks", 32), new Trade(1, "deepslate_tiles", 32), new Trade(1, "polished_deepslate", 32), new Trade(1, "polished_andesite", 48), new Trade(1, "polished_granite", 48), new Trade(1, "polished_diorite", 48), new Trade(1, "polished_blackstone_bricks", 32), new Trade(1, "mud_bricks", 32), new Trade(1, "tuff_bricks", 32), new Trade(1, "calcite", 32), new Trade(1, "terracotta", 32), new Trade(1, "white_wool", 32), new Trade(1, "white_concrete", 32), new Trade(1, "light_gray_concrete", 32), new Trade(1, "gray_concrete", 32), new Trade(1, "black_concrete", 32), new Trade(1, "red_concrete", 32), new Trade(1, "blue_concrete", 32), new Trade(2, "quartz_block", 32), new Trade(2, "quartz_pillar", 16), new Trade(2, "prismarine_bricks", 16), new Trade(2, "dark_prismarine", 16), new Trade(3, "copper_block", 16), new Trade(3, "cut_copper", 16), new Trade(3, "oxidized_cut_copper", 16), new Trade(2, "lantern", 8), new Trade(2, "soul_lantern", 8), new Trade(1, "chain", 16), new Trade(1, "iron_chain", 16), new Trade(3, "glowstone", 16), new Trade(3, "sea_lantern", 8), new Trade(1, "glass_pane", 32), new Trade(1, "iron_bars", 16), new Trade(1, "oak_fence", 24), new Trade(1, "spruce_fence", 24), new Trade(1, "oak_trapdoor", 8), new Trade(1, "oak_door", 4), new Trade(1, "spruce_door", 4), new Trade(1, "flower_pot", 8), new Trade(3, "bookshelf", 8), new Trade(1, "oak_leaves", 32), new Trade(1, "moss_block", 16), new Trade(1, "white_bed", 1), new Trade(1, "chest", 4), new Trade(1, "barrel", 4), new Trade(1, "crafting_table", 2), new Trade(1, "furnace", 2), new Trade(1, "campfire", 2), new Trade(1, "coal", 16), new Trade(2, "coal_block", 4), new Trade(1, "bread", 16)};
   private final Entity at;
   private final MerchantOffers offers = new MerchantOffers();
   private @Nullable Player trading;

   private static Item item(String id) {
      return (Item)BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
   }

   private BlockTrader(Entity at) {
      this.at = at;
      this.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), Progression.guideBook(), Integer.MAX_VALUE, 0, 0.0F));
      this.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), MysteryBox.box(1), Integer.MAX_VALUE, 0, 0.0F));
      this.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 8), MysteryBox.box(2), Integer.MAX_VALUE, 0, 0.0F));
      this.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 24), MysteryBox.box(3), Integer.MAX_VALUE, 0, 0.0F));

      for(Trade t : TRADES) {
         Item item = item(t.id());
         if (item != Items.AIR) {
            this.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, t.emeralds()), new ItemStack(item, t.count()), Integer.MAX_VALUE, 0, 0.0F));
         }
      }

   }

   public static void open(ServerPlayer player, Entity at) {
      BlockTrader trader = new BlockTrader(at);
      trader.setTradingPlayer(player);
      trader.openTradingScreen(player, Component.literal("Building supplies"), 1);
   }

   public void setTradingPlayer(@Nullable Player player) {
      this.trading = player;
   }

   public @Nullable Player getTradingPlayer() {
      return this.trading;
   }

   public MerchantOffers getOffers() {
      return this.offers;
   }

   public void overrideOffers(MerchantOffers offers) {
   }

   public void notifyTrade(MerchantOffer offer) {
      offer.increaseUses();
      offer.resetUses();
   }

   public void notifyTradeUpdated(ItemStack stack) {
   }

   public int getVillagerXp() {
      return 0;
   }

   public void overrideXp(int xp) {
   }

   public boolean showProgressBar() {
      return false;
   }

   public SoundEvent getNotifyTradeSound() {
      return SoundEvents.VILLAGER_YES;
   }

   public boolean isClientSide() {
      return false;
   }

   public boolean stillValid(Player player) {
      return this.trading == player && !this.at.isRemoved() && player.isWithinEntityInteractionRange(this.at, (double)4.0F);
   }

   private static record Trade(int emeralds, String id, int count) {
   }
}
