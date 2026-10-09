package dev.skycraft.combat;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SkyrimActorEntity extends LivingEntity {
   private static final EntityDataAccessor<Integer> FORM_ID;
   private static final EntityDataAccessor<Float> WIDTH;
   private static final EntityDataAccessor<Float> HEIGHT;
   private float pendingDamage;
   private int pendingFlags;
   private int pendingWeapon;
   private double pushX;
   private double pushZ;
   private float pushStrength;
   private boolean hitThisTick;
   private boolean trader;
   private boolean friendly;
   private @Nullable UUID attacker;

   public SkyrimActorEntity(EntityType<? extends SkyrimActorEntity> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.noPhysics = true;
      this.setInvisible(true);
      this.setSilent(true);
   }

   public int formId() {
      return (Integer)this.entityData.get(FORM_ID);
   }

   public void setFormId(int formId) {
      this.entityData.set(FORM_ID, formId);
   }

   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(FORM_ID, 0);
      builder.define(WIDTH, 0.6F);
      builder.define(HEIGHT, 1.8F);
   }

   public void setSize(float width, float height) {
      if (Math.abs((Float)this.entityData.get(WIDTH) - width) > 0.01F || Math.abs((Float)this.entityData.get(HEIGHT) - height) > 0.01F) {
         this.entityData.set(WIDTH, width);
         this.entityData.set(HEIGHT, height);
         this.refreshDimensions();
      }

   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
      super.onSyncedDataUpdated(accessor);
      if (WIDTH.equals(accessor) || HEIGHT.equals(accessor)) {
         this.refreshDimensions();
      }

   }

   protected EntityDimensions getDefaultDimensions(Pose pose) {
      return EntityDimensions.scalable((Float)this.entityData.get(WIDTH), (Float)this.entityData.get(HEIGHT));
   }

   protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
      if (!this.isInvulnerableTo(level, source) && !(dmg <= 0.0F)) {
         this.pendingDamage += dmg;
         Entity var5 = source.getEntity();
         if (var5 instanceof Player) {
            Player player = (Player)var5;
            this.attacker = player.getUUID();
         }

         if (source.getDirectEntity() instanceof Projectile) {
            this.pendingFlags |= 2;
         }

         this.pendingWeapon = weaponClass(source);
         if (source.is(DamageTypeTags.IS_FIRE)) {
            this.pendingFlags |= 8;
         }

         this.hitThisTick = true;
         this.getCombatTracker().recordDamage(source, dmg);
      }
   }

   public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
      double len = Math.sqrt(xd * xd + zd * zd);
      if (len > 1.0E-6 && power > (double)this.pushStrength) {
         this.pushStrength = (float)power;
         this.pushX = -xd / len;
         this.pushZ = -zd / len;
      }

      this.hitThisTick = true;
   }

   public void markCritical() {
      this.pendingFlags |= 1;
   }

   private static int weaponClass(DamageSource source) {
      if (source.getDirectEntity() instanceof ThrownTrident) {
         return 4;
      } else if (source.getDirectEntity() instanceof Projectile) {
         return 5;
      } else {
         ItemStack weapon = source.getWeaponItem();
         if (weapon == null) {
            Entity var3 = source.getEntity();
            if (var3 instanceof LivingEntity) {
               LivingEntity attacker = (LivingEntity)var3;
               weapon = attacker.getMainHandItem();
            }
         }

         if (weapon != null && !weapon.isEmpty()) {
            if (weapon.is(ItemTags.SWORDS)) {
               return 1;
            } else if (weapon.is(ItemTags.AXES)) {
               return 2;
            } else {
               return weapon.is(Items.TRIDENT) ? 4 : 3;
            }
         } else {
            return 0;
         }
      }
   }

   public @Nullable UUID attacker() {
      return this.attacker;
   }

   public float[] takeHit() {
      if (!this.hitThisTick) {
         return null;
      } else {
         float[] hit = new float[]{this.pendingDamage, (float)this.pushX, (float)this.pushZ, this.pushStrength, Float.intBitsToFloat(this.pendingFlags), Float.intBitsToFloat(this.pendingWeapon)};
         this.pendingDamage = 0.0F;
         this.pendingFlags = 0;
         this.pushX = this.pushZ = (double)0.0F;
         this.pushStrength = 0.0F;
         this.hitThisTick = false;
         return hit;
      }
   }

   public void tick() {
      this.baseTick();
      this.setHealth(this.getMaxHealth());
   }

   public void setTrader(boolean trader) {
      this.trader = trader;
   }

   public boolean trader() {
      return this.trader;
   }

   public void setFriendly(boolean friendly) {
      this.friendly = friendly;
   }

   public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
      return this.friendly || super.isInvulnerableTo(level, source);
   }

   public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
      if (this.trader && hand == InteractionHand.MAIN_HAND) {
         if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            BlockTrader.open(sp, this);
         }

         return InteractionResult.SUCCESS;
      } else {
         return super.interact(player, hand, at);
      }
   }

   public boolean isPushable() {
      return false;
   }

   protected void doPush(Entity entity) {
   }

   public boolean canBeCollidedWith(@Nullable Entity other) {
      return false;
   }

   public boolean shouldShowName() {
      return false;
   }

   public boolean shouldBeSaved() {
      return false;
   }

   protected @Nullable SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   protected @Nullable SoundEvent getDeathSound() {
      return null;
   }

   public HumanoidArm getMainArm() {
      return HumanoidArm.RIGHT;
   }

   static {
      FORM_ID = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.INT);
      WIDTH = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.FLOAT);
      HEIGHT = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.FLOAT);
   }
}
