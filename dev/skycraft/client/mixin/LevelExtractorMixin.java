package dev.skycraft.client.mixin;

import dev.skycraft.client.render.WorldExporter;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelExtractor.class})
public abstract class LevelExtractorMixin {
   @Inject(
      method = {"setSectionDirty(IIIZ)V"},
      at = {@At("HEAD")}
   )
   private void skycraft$sectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo ci) {
      if (playerChanged) {
         WorldExporter.markDirtyNow(sectionX, sectionY, sectionZ);
      } else {
         WorldExporter.markDirty(sectionX, sectionY, sectionZ);
      }

   }
}
