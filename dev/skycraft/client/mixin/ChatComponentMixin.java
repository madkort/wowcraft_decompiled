package dev.skycraft.client.mixin;

import dev.skycraft.client.DiscordPresence;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({ChatComponent.class})
public abstract class ChatComponentMixin {
   @ModifyVariable(
      method = {"addMessage"},
      at = {@At("HEAD")},
      argsOnly = true,
      ordinal = 0
   )
   private Component skycraft$watchForLink(Component contents) {
      DiscordPresence.onChat(contents);
      return (Component)(DiscordPresence.mentionsLink(contents) ? Component.literal("WowCraft: your world is open to friends. Their link is in the WowCraft launcher (Copy), hidden so a stream doesn't show it.") : contents);
   }
}
