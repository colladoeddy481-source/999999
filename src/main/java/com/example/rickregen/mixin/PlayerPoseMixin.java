package com.example.rickregen.mixin;

import com.example.rickregen.RickregenHandler;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("TAIL"))
    private void rickregen$forceCrawlPose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        
        // Solo fuerza el gateo si el jugador debe arrastrarse y no está volando, 
        // usando Elytra o en estados críticos gestionados por Architectury / Punchy.
        if (RickregenHandler.shouldCrawl(self)) {
            Pose currentPose = self.getPose();
            if (currentPose != Pose.SWIMMING && currentPose != Pose.FALL_FLYING && !self.isFallFlying()) {
                self.setPose(Pose.SWIMMING);
            }
        }
    }
}
