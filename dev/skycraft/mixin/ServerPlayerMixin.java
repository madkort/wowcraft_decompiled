package dev.skycraft.mixin;

import dev.skycraft.SkyCraft;
import dev.skycraft.combat.SkyCombat;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerPlayer.class})
public abstract class ServerPlayerMixin {
   @Inject(
      method = {"crit"},
      at = {@At("HEAD")}
   )
   private void skycraft$critSkyrim(Entity entity, CallbackInfo ci) {
      if (entity instanceof SkyrimActorEntity proxy) {
         proxy.markCritical();
      }

   }

   @Inject(
      method = {"die"},
      at = {@At("HEAD")}
   )
   private void skycraft$diesInSkyrim(DamageSource source, CallbackInfo ci) {
      ServerPlayer self = (ServerPlayer)this;
      int attacker = SkyCombat.attackerFormId(source);
      if (!SkyNet.isHost(self)) {
         if (ServerPlayNetworking.canSend(self, SkyNet.Died.TYPE)) {
            ServerPlayNetworking.send(self, new SkyNet.Died(attacker));
         }

         SkyCraft.LOG.info("SkyCraft: guest {} died ({}); telling their Skyrim", self.getPlainTextName(), source.getMsgId());
      } else {
         if (SkyLink.active()) {
            SkyLink.pushEvent(2, attacker, 0.0F, 0.0F, 0.0F, 0.0F, 0);
            SkyCraft.LOG.info("SkyCraft: player died ({}); telling Skyrim", source.getMsgId());
         }

      }
   }
}
