package dev.skycraft.net;

import dev.skycraft.combat.SkyCombat;
import dev.skycraft.world.SkyDig;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SkyNet {
   private SkyNet() {
   }

   public static void init() {
      PayloadTypeRegistry.serverboundPlay().register(SkyNet.Hurt.TYPE, SkyNet.Hurt.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(SkyNet.DigOpen.TYPE, SkyNet.DigOpen.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(SkyNet.DigReveal.TYPE, SkyNet.DigReveal.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(SkyNet.DigOpen.TYPE, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> SkyDig.open(player, payload.world(), payload.pos(), payload.material()));
      });
      ServerPlayNetworking.registerGlobalReceiver(SkyNet.DigReveal.TYPE, (payload, context) -> {
         ServerPlayer player = context.player();
         int[] materials = payload.materials().stream().mapToInt(Integer::intValue).toArray();
         context.server().execute(() -> SkyDig.reveal(player, payload.world(), payload.cells(), materials));
      });
      PayloadTypeRegistry.clientboundPlay().register(SkyNet.Died.TYPE, SkyNet.Died.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(SkyNet.Hurt.TYPE, (payload, context) -> {
         ServerPlayer player = context.player();
         float damage = Math.max(0.0F, Math.min(payload.skyrimDamage(), 10000.0F));
         context.server().execute(() -> SkyCombat.hurtPlayer(player, payload.kind(), damage, payload.attackerFormId(), payload.flags()));
      });
   }

   public static boolean isHost(ServerPlayer player) {
      MinecraftServer server = player.level().getServer();
      return server != null && server.isSingleplayerOwner(player.nameAndId());
   }

   public static record Hurt(int kind, float skyrimDamage, int attackerFormId, int flags) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Hurt> TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("skycraft", "hurt"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Hurt> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Hurt::kind, ByteBufCodecs.FLOAT, Hurt::skyrimDamage, ByteBufCodecs.INT, Hurt::attackerFormId, ByteBufCodecs.VAR_INT, Hurt::flags, Hurt::new);
      }
   }

   public static record Died(int attackerFormId) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Died> TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("skycraft", "died"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Died> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.INT, Died::attackerFormId, Died::new);
      }
   }

   public static record DigOpen(int world, BlockPos pos, int material) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<DigOpen> TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("skycraft", "dig_open"));
      public static final StreamCodec<RegistryFriendlyByteBuf, DigOpen> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.INT, DigOpen::world, BlockPos.STREAM_CODEC, DigOpen::pos, ByteBufCodecs.VAR_INT, DigOpen::material, DigOpen::new);
      }
   }

   public static record DigReveal(int world, List<BlockPos> cells, List<Integer> materials) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<DigReveal> TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("skycraft", "dig_reveal"));
      public static final StreamCodec<RegistryFriendlyByteBuf, DigReveal> CODEC;

      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      static {
         CODEC = StreamCodec.composite(ByteBufCodecs.INT, DigReveal::world, BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(64)), DigReveal::cells, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), DigReveal::materials, DigReveal::new);
      }
   }
}
