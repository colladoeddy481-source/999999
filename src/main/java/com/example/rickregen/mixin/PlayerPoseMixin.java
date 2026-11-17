package com.example.rickregen.mixin;

import com.example.rickregen.RickregenHandler;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sin las dos piernas el jugador adopta la pose de gateo de Minecraft (la misma de pasar por huecos
 * de un bloque), con su animacion de arrastrarse y la camara baja.
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("TAIL"))
    private void rickregen$forceCrawlPose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (RickregenHandler.shouldCrawl(self) && self.getPose() != Pose.SWIMMING) {
            self.setPose(Pose.SWIMMING);
        }
    }
}
