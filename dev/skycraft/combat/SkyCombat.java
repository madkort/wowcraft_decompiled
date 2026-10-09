package dev.skycraft.combat;

import dev.skycraft.SkyCraft;
import dev.skycraft.WowGifts;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.CoopNet;
import dev.skycraft.net.SkyNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SkyCombat {
   public static final ResourceKey<EntityType<?>> SKYRIM_ACTOR_KEY;
   public static final EntityType<SkyrimActorEntity> SKYRIM_ACTOR;
   public static final float SKYRIM_TO_MC_DAMAGE = 5.0F;
   private static final Map<Integer, SkyrimActorEntity> PROXIES;
   private static final List<SkyLink.Actor> ACTORS;
   private static final List<SkyLink.Actor> HOST_ACTORS;

   private SkyCombat() {
   }

   public static void init() {
      FabricDefaultAttributeRegistry.register(SKYRIM_ACTOR, LivingEntity.createLivingAttributes());
      ServerTickEvents.END_SERVER_TICK.register(SkyCombat::serverTick);
      OreNodes.init();
      WowGifts.init();
      MysteryBox.init();
   }

   public static @Nullable SkyrimActorEntity traderInSight(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.entityInteractionRange() + (double)1.0F));
      SkyrimActorEntity best = null;
      double bestDistance = Double.MAX_VALUE;

      for(SkyrimActorEntity proxy : PROXIES.values()) {
         if (proxy.trader() && proxy.level() == player.level()) {
            Optional<Vec3> at = proxy.getBoundingBox().clip(eye, end);
            if (at.isPresent() && ((Vec3)at.get()).distanceTo(eye) < bestDistance) {
               bestDistance = ((Vec3)at.get()).distanceTo(eye);
               best = proxy;
            }
         }
      }

      return best;
   }

   public static @Nullable SkyrimActorEntity proxy(int formId) {
      return (SkyrimActorEntity)PROXIES.get(formId);
   }

   private static void serverTick(MinecraftServer server) {
      List<ServerPlayer> players = server.getPlayerList().getPlayers();
      if (SkyLink.active() && !players.isEmpty()) {
         ServerLevel level = ((ServerPlayer)players.getFirst()).level();

         for(ServerPlayer player : players) {
            pickUpNearby(player);
         }

         if (SkyLink.readActors(ACTORS)) {
            HOST_ACTORS.clear();
            HOST_ACTORS.addAll(ACTORS);
         }

         List<SkyLink.Actor> all = new ArrayList(HOST_ACTORS);
         Set<Integer> seen = new HashSet();

         for(SkyLink.Actor a : all) {
            seen.add(a.formId());
         }

         for(SkyLink.Actor a : CoopNet.remoteActors()) {
            if (seen.add(a.formId())) {
               all.add(a);
            }
         }

         ACTORS.clear();
         ACTORS.addAll(all);
         sync(level);
         OreNodes.sync(level, ACTORS, players);

         for(SkyrimActorEntity proxy : PROXIES.values()) {
            UUID attacker = proxy.attacker();
            float[] hit = proxy.takeHit();
            if (hit != null && (hit[0] > 0.0F || hit[3] > 0.0F)) {
               ServerPlayer by = attacker == null ? null : server.getPlayerList().getPlayer(attacker);
               CoopNet.event(by, 1, proxy.formId(), hit[0], hit[1], hit[2], hit[3], Float.floatToRawIntBits(hit[4]), Float.floatToRawIntBits(hit[5]));
               SkyCraft.LOG.info("SkyCraft: hit {} for {} (knockback {})", new Object[]{proxy.getName().getString(), hit[0], hit[3]});
            }
         }

      } else {
         removeAll();
      }
   }

   private static float width(SkyLink.Actor a) {
      return (a.flags() & 512) != 0 ? Math.max(a.width(), 3.5F) : a.width();
   }

   private static void sync(ServerLevel level) {
      Map<Integer, SkyLink.Actor> live = new HashMap();

      for(SkyLink.Actor a : ACTORS) {
         if (!a.dead() && (a.flags() & 256) == 0) {
            live.put(a.formId(), a);
         }
      }

      Iterator<Map.Entry<Integer, SkyrimActorEntity>> it = PROXIES.entrySet().iterator();

      while(it.hasNext()) {
         Map.Entry<Integer, SkyrimActorEntity> e = (Map.Entry)it.next();
         SkyrimActorEntity proxy = (SkyrimActorEntity)e.getValue();
         if (!live.containsKey(e.getKey()) || proxy.isRemoved() || proxy.level() != level) {
            proxy.discard();
            it.remove();
         }
      }

      int before = PROXIES.size();

      for(SkyLink.Actor a : live.values()) {
         SkyrimActorEntity proxy = (SkyrimActorEntity)PROXIES.get(a.formId());
         if (proxy == null) {
            if (level.isLoaded(BlockPos.containing((double)a.x(), (double)a.y(), (double)a.z()))) {
               proxy = new SkyrimActorEntity(SKYRIM_ACTOR, level);
               proxy.setFormId(a.formId());
               proxy.setTrader((a.flags() & 512) != 0);
               proxy.setFriendly((a.flags() & 1024) != 0);
               proxy.setSize(width(a), a.height());
               proxy.snapTo((double)a.x(), (double)a.y(), (double)a.z(), a.yaw(), 0.0F);
               if (!a.name().isEmpty()) {
                  proxy.setCustomName(Component.literal(a.name()));
               }

               if (level.addFreshEntity(proxy)) {
                  PROXIES.put(a.formId(), proxy);
               }
            }
         } else {
            proxy.setTrader((a.flags() & 512) != 0);
            proxy.setFriendly((a.flags() & 1024) != 0);
            proxy.setSize(width(a), a.height());
            proxy.setPos((double)a.x(), (double)a.y(), (double)a.z());
            proxy.setYRot(a.yaw());
            proxy.setYHeadRot(a.yaw());
            stepOnTriggers(level, proxy);
         }
      }

      if (PROXIES.size() != before && (PROXIES.size() % 5 == 0 || PROXIES.size() < 5)) {
         SkyCraft.LOG.info("SkyCraft: {} Skyrim actors mirrored as hittable stand-ins", PROXIES.size());
      }

   }

   private static void stepOnTriggers(ServerLevel level, SkyrimActorEntity proxy) {
      AABB box = proxy.getBoundingBox().deflate(1.0E-5);
      BlockPos from = BlockPos.containing(box.minX, box.minY, box.minZ);
      BlockPos to = BlockPos.containing(box.maxX, box.maxY, box.maxZ);

      for(BlockPos pos : BlockPos.betweenClosed(from, to)) {
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof BasePressurePlateBlock || state.getBlock() instanceof TripWireBlock) {
            state.entityInside(level, pos, proxy, InsideBlockEffectApplier.NOOP, true);
         }
      }

   }

   private static void pickUpNearby(ServerPlayer player) {
      if (player.isAlive() && !player.isSpectator()) {
         for(Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate((double)1.25F, (double)1.0F, (double)1.25F))) {
            if (!entity.isRemoved() && (entity instanceof ItemEntity || entity instanceof AbstractArrow)) {
               entity.playerTouch(player);
            }
         }

      }
   }

   private static void removeAll() {
      if (!PROXIES.isEmpty()) {
         PROXIES.values().forEach(Entity::discard);
         PROXIES.clear();
      }
   }

   public static void hurtPlayer(ServerPlayer player, int kind, float skyrimDamage, int attackerFormId, int flags) {
      if (player.isAlive() && !(skyrimDamage <= 0.0F)) {
         ServerLevel level = player.level();
         SkyrimActorEntity attacker = (SkyrimActorEntity)PROXIES.get(attackerFormId);
         if (attacker != null && attacker.distanceToSqr(player) > (double)576.0F) {
            attacker = null;
         }

         DamageSources sources = level.damageSources();
         DamageSource var10000;
         switch (kind) {
            case 0 -> var10000 = attacker != null ? sources.mobAttack(attacker) : sources.generic();
            case 1 -> var10000 = attacker != null ? sources.mobProjectile(attacker, attacker) : sources.generic();
            case 2 -> var10000 = attacker != null ? sources.indirectMagic(attacker, attacker) : sources.magic();
            default -> var10000 = sources.generic();
         }

         DamageSource source = var10000;
         float damage = skyrimDamage / 5.0F;
         float healthBefore = player.getHealth();
         boolean blocking = player.isBlocking();
         boolean hurt = player.hurtServer(level, source, damage);
         trainDefence(player, damage, blocking && player.getHealth() >= healthBefore - 0.001F);
         SkyCraft.LOG.info("SkyCraft: Skyrim hit the player for {} ({} Minecraft): health {} -> {}{}", new Object[]{skyrimDamage, damage, healthBefore, player.getHealth(), hurt ? "" : " (blocked/immune)"});
         if (hurt && attacker != null && (flags & 2) != 0 && !player.isBlocking()) {
            player.knockback((double)0.5F, attacker.getX() - player.getX(), attacker.getZ() - player.getZ(), source, damage);
         }

      }
   }

   private static void trainDefence(ServerPlayer player, float damage, boolean blocked) {
      if (SkyNet.isHost(player) && !(damage <= 0.0F)) {
         if (blocked) {
            SkyLink.pushEvent(5, 9, damage, 0.0F, 0.0F, 0.0F, 0);
         } else {
            int light = 0;
            int heavy = 0;

            for(EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
               ItemStack stack = player.getItemBySlot(slot);
               if (!stack.isEmpty()) {
                  String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                  if (!path.startsWith("iron_") && !path.startsWith("diamond_") && !path.startsWith("netherite_")) {
                     ++light;
                  } else {
                     ++heavy;
                  }
               }
            }

            if (light + heavy > 0) {
               SkyLink.pushEvent(5, heavy > light ? 11 : 12, damage * (float)(light + heavy) / 4.0F, 0.0F, 0.0F, 0.0F, 0);
            }

         }
      }
   }

   public static int attackerFormId(DamageSource source) {
      Entity var2 = source.getEntity();
      int var10000;
      if (var2 instanceof SkyrimActorEntity proxy) {
         var10000 = proxy.formId();
      } else {
         var10000 = 0;
      }

      return var10000;
   }

   static {
      SKYRIM_ACTOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("skycraft", "skyrim_actor"));
      SKYRIM_ACTOR = (EntityType)Registry.register(BuiltInRegistries.ENTITY_TYPE, SKYRIM_ACTOR_KEY, Builder.of(SkyrimActorEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).noSave().noSummon().noLootTable().clientTrackingRange(10).updateInterval(1).build(SKYRIM_ACTOR_KEY));
      PROXIES = new HashMap();
      ACTORS = new ArrayList();
      HOST_ACTORS = new ArrayList();
   }
}
