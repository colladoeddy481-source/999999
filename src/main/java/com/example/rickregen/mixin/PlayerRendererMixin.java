package com.example.rickregen.mixin;

import com.example.rickregen.RickregenHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    /**
     * Aplica visibilidad y animacion de escala gradual (de 0% a 100%) cuando cada extremidad
     * vuelve a crecer durante la regeneracion de Rick Prime, respetando siempre la skin del jugador.
     */
    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void rickregen$updateLimbVisibilityAndGrowth(AbstractClientPlayer player, CallbackInfo ci) {
        PlayerRenderer renderer = (PlayerRenderer) (Object) this;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        int stage = RickregenHandler.getDismembermentStage(player);
        boolean regenerating = RickregenHandler.isRegenerating(player);

        // Restablecer escalas por defecto antes de aplicar animacion
        resetPartScale(model.head, model.hat);
        resetPartScale(model.leftArm, model.leftSleeve);
        resetPartScale(model.rightArm, model.rightSleeve);
        resetPartScale(model.leftLeg, model.leftPants);
        resetPartScale(model.rightLeg, model.rightPants);

        if (regenerating) {
            // Animacion gradual: cada extremidad crece de 0.0 a 1.0 en su turno
            applyGrowth(model.head, model.hat, RickregenHandler.getCurrentLimbGrowthScale(player, 5));
            applyGrowth(model.leftArm, model.leftSleeve, RickregenHandler.getCurrentLimbGrowthScale(player, 4));
            applyGrowth(model.rightArm, model.rightSleeve, RickregenHandler.getCurrentLimbGrowthScale(player, 3));
            applyGrowth(model.leftLeg, model.leftPants, RickregenHandler.getCurrentLimbGrowthScale(player, 2));
            applyGrowth(model.rightLeg, model.rightPants, RickregenHandler.getCurrentLimbGrowthScale(player, 1));
            return;
        }

        // Modo dano normal por corazones:
        // 6 corazones (stage 1+): pierde pie derecho
        boolean showRightLeg = stage < 1;
        model.rightLeg.visible = showRightLeg;
        model.rightPants.visible = showRightLeg;

        // 5 corazones (stage 2+): pierde pie izquierdo
        boolean showLeftLeg = stage < 2;
        model.leftLeg.visible = showLeftLeg;
        model.leftPants.visible = showLeftLeg;

        // 4 corazones (stage 3+): pierde mano derecha
        boolean showRightArm = stage < 3;
        model.rightArm.visible = showRightArm;
        model.rightSleeve.visible = showRightArm;

        // 3 corazones (stage 4+): pierde mano izquierda
        boolean showLeftArm = stage < 4;
        model.leftArm.visible = showLeftArm;
        model.leftSleeve.visible = showLeftArm;

        // 2 corazones (stage 5): pierde cabeza
        boolean showHead = stage < 5;
        model.head.visible = showHead;
        model.hat.visible = showHead;
    }

    private void resetPartScale(ModelPart base, ModelPart overlay) {
        base.xScale = 1.0F;
        base.yScale = 1.0F;
        base.zScale = 1.0F;
        overlay.xScale = 1.0F;
        overlay.yScale = 1.0F;
        overlay.zScale = 1.0F;
    }

    private void applyGrowth(ModelPart base, ModelPart overlay, float scale) {
        if (scale <= 0.01F) {
            base.visible = false;
            overlay.visible = false;
        } else {
            base.visible = true;
            overlay.visible = true;
            base.xScale = scale;
            base.yScale = scale;
            base.zScale = scale;
            overlay.xScale = scale;
            overlay.yScale = scale;
            overlay.zScale = scale;
        }
    }

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void rickregen$hideFirstPersonRightHand(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, AbstractClientPlayer player, CallbackInfo ci) {
        int stage = RickregenHandler.getDismembermentStage(player);
        if (stage >= 3) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void rickregen$hideFirstPersonLeftHand(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, AbstractClientPlayer player, CallbackInfo ci) {
        int stage = RickregenHandler.getDismembermentStage(player);
        if (stage >= 4) {
            ci.cancel();
        }
    }
}
