package dev.skycraft.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.skycraft.SkyCraft;
import dev.skycraft.WowCraftConfig;
import dev.skycraft.combat.SkyCombat;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import dev.skycraft.world.SkyCollision;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer.MultiplayerScope;
import net.minecraft.world.entity.player.Player;

public final class SkyCraftClient implements ClientModInitializer {
   public void onInitializeClient() {
      SkyLink.announceRunning();
      DiscordPresence.start();
      OwnSkin.start();
      CoopClient.init();
      ClientTickEvents.END_CLIENT_TICK.register(CoopClient::tick);
      ClientCommandRegistrationCallback.EVENT.register((ClientCommandRegistrationCallback)(dispatcher, context) -> {
         dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("join").then(ClientCommands.argument("link", StringArgumentType.greedyString()).executes((c) -> {
            String link = StringArgumentType.getString(c, "link");
            ((FabricClientCommandSource)c.getSource()).sendFeedback(Component.literal("Joining " + link.trim() + "..."));
            Minecraft.getInstance().execute(() -> MirrorWorld.joinFriend(Minecraft.getInstance(), link));
            return 1;
         })));
         dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("leave").executes((c) -> {
            Minecraft.getInstance().execute(() -> MirrorWorld.leaveFriend(Minecraft.getInstance()));
            return 1;
         }));
      });
      ClientTickEvents.END_CLIENT_TICK.register(SkyClient::clientTick);
      ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join)(handler, sender, minecraft) -> {
         String port = WowCraftConfig.get("lan_port", "SKYCRAFT_LAN_PORT");
         IntegratedServer server = minecraft.getSingleplayerServer();
         if (port != null && !port.isBlank() && server != null && !server.isPublished()) {
            minecraft.execute(() -> {
               if (WowCraftConfig.on("lan_offline", "SKYCRAFT_LAN_OFFLINE")) {
                  server.setUsesAuthentication(false);
               }

               boolean ok = server.publishServer(MultiplayerScope.LAN, false, Integer.parseInt(port.trim()));
               SkyCraft.LOG.info("SkyCraft: world opened to LAN on port {} ({}{})", new Object[]{port.trim(), ok ? "ok" : "FAILED", WowCraftConfig.on("lan_offline", "SKYCRAFT_LAN_OFFLINE") ? ", offline logins allowed" : ""});
            });
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(SkyNet.Died.TYPE, (payload, context) -> {
         if (SkyLink.active()) {
            SkyLink.pushEvent(2, payload.attackerFormId(), 0.0F, 0.0F, 0.0F, 0.0F, 0);
         }

      });
      EntityRendererRegistry.register(SkyCombat.SKYRIM_ACTOR, NoopRenderer::new);
      SkyCollision.setSmoothCollider((e) -> e instanceof Player && SkyClient.linked());
   }
}
