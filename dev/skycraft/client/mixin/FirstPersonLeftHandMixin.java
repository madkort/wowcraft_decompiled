package dev.skycraft.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skycraft.client.SkyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FirstPersonHandsAndItemsRenderer.class})
public abstract class FirstPersonLeftHandMixin {
   @Inject(
      method = {"submitArmWithItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void wowcraft$noLeftHandOverChat(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float a, float b, InteractionHand hand, float c, ItemStack stack, float d, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
      if (SkyClient.linked() && (SkyClient.sky().flags & 256) != 0) {
         LocalPlayer local = Minecraft.getInstance().player;
         HumanoidArm main = local != null ? local.getMainArm() : HumanoidArm.RIGHT;
         HumanoidArm side = hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
         if (side == HumanoidArm.LEFT) {
            ci.cancel();
         }

      }
   }
}
