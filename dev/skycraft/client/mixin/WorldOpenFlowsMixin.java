package dev.skycraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({WorldOpenFlows.class})
public abstract class WorldOpenFlowsMixin {
   @WrapOperation(
      method = {"openWorldCheckWorldStemCompatibility"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;askForBackup(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;ZLjava/lang/Runnable;Ljava/lang/Runnable;)V"
)}
   )
   private void skycraft$skipBackupPrompt(WorldOpenFlows self, LevelStorageSource.LevelStorageAccess access, boolean oldCustomized, Runnable proceed, Runnable cancel, Operation<Void> original) {
      if ("SkyCraft".equals(access.getLevelId())) {
         proceed.run();
      } else {
         original.call(new Object[]{self, access, oldCustomized, proceed, cancel});
      }

   }
}
