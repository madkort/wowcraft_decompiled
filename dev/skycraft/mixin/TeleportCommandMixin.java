package dev.skycraft.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.commands.TeleportCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TeleportCommand.class, SpreadPlayersCommand.class})
public abstract class TeleportCommandMixin {
   private static final String WHY = "Minecraft's teleports are off in WowCraft (they'd split you from your WoW character). Use WoW's instead, in WoW chat: .tele <place> (e.g. .tele stormwind) or .go xyz <x> <y> <z>.";

   @Inject(
      method = {"register"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void skycraft$wowTeleportsOnly(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
      for(String name : new String[]{"tp", "teleport", "spreadplayers"}) {
         if (dispatcher.getRoot().getChild(name) == null) {
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(name).executes((c) -> skycraft$refuse((CommandSourceStack)c.getSource()))).then(Commands.argument("anything", StringArgumentType.greedyString()).executes((c) -> skycraft$refuse((CommandSourceStack)c.getSource()))));
         }
      }

      ci.cancel();
   }

   private static int skycraft$refuse(CommandSourceStack source) {
      source.sendFailure(Component.literal("Minecraft's teleports are off in WowCraft (they'd split you from your WoW character). Use WoW's instead, in WoW chat: .tele <place> (e.g. .tele stormwind) or .go xyz <x> <y> <z>."));
      return 0;
   }
}
