package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerGamePacketListenerImpl.class})
public abstract class MovementCheckMixin {
   private static final double JUMP = (double)32.0F;
   @Shadow
   public ServerPlayer player;

   @Inject(
      method = {"shouldCheckPlayerMovement"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skycraft$noPlayerSpeedCheck(boolean elytra, CallbackInfoReturnable<Boolean> cir) {
      cir.setReturnValue(false);
   }

   @WrapOperation(
      method = {"handleMoveVehicle"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"
)}
   )
   private boolean skycraft$noVehicleSpeedCheck(ServerGamePacketListenerImpl self, Operation<Boolean> original) {
      return true;
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
   shift = Shift.AFTER
)},
      cancellable = true
   )
   private void skycraft$jumpIsATeleport(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
      if (packet.hasPosition() && !this.player.isPassenger()) {
         double x = packet.getX(this.player.getX());
         double y = packet.getY(this.player.getY());
         double z = packet.getZ(this.player.getZ());
         if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            double dx = x - this.player.getX();
            double dy = y - this.player.getY();
            double dz = z - this.player.getZ();
            if (!(dx * dx + dy * dy + dz * dz < (double)1024.0F)) {
               this.player.stopFallFlying();
               this.player.teleportTo(x, y, z);
               this.player.resetFallDistance();
               ci.cancel();
            }
         }
      }
   }
}
