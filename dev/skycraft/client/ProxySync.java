package dev.skycraft.client;

import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.link.SkyLink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

final class ProxySync {
   private static final List<SkyLink.Actor> ACTORS = new ArrayList();
   private static final Map<Integer, SkyLink.Actor> BY_ID = new HashMap();

   private ProxySync() {
   }

   static void frame(Minecraft minecraft) {
      if (minecraft.level != null && SkyLink.readActors(ACTORS)) {
         BY_ID.clear();

         for(SkyLink.Actor a : ACTORS) {
            BY_ID.put(a.formId(), a);
         }

         for(Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof SkyrimActorEntity) {
               SkyrimActorEntity proxy = (SkyrimActorEntity)entity;
               SkyLink.Actor a = (SkyLink.Actor)BY_ID.get(proxy.formId());
               if (a != null) {
                  proxy.setSize(a.width(), a.height());
                  proxy.setPos((double)a.x(), (double)a.y(), (double)a.z());
                  proxy.xo = (double)a.x();
                  proxy.yo = (double)a.y();
                  proxy.zo = (double)a.z();
                  proxy.setYRot(a.yaw());
                  proxy.yRotO = a.yaw();
               }
            }
         }

      }
   }
}
