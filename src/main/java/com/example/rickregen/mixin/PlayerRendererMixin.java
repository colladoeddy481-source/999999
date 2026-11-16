package com.example.rickregen.mixin;

import com.example.rickregen.RickregenHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void rickregen$updateLimbVisibilityAndGrowth(AbstractClientPlayer player, CallbackInfo ci) {
        PlayerRenderer renderer = (PlayerRenderer) (Object) this;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        int stage = RickregenHandler.getDismembermentStage(player);
        boolean regenerating = RickregenHandler.isRegenerating(player);

        rickregen$resetPartScale(model.head, model.hat);
        rickregen$resetPartScale(model.leftArm, model.leftSleeve);
        rickregen$resetPartScale(model.rightArm, model.rightSleeve);
        rickregen$resetPartScale(model.leftLeg, model.leftPants);
        rickregen$resetPartScale(model.rightLeg, model.rightPants);

        if (regenerating) {
            float partial = Minecraft.getInstance().getPartialTick();
            rickregen$applyGrowth(model.head, model.hat, RickregenHandler.getCurrentLimbGrowthScale(player, 5, partial));
            rickregen$applyGrowth(model.leftArm, model.leftSleeve, RickregenHandler.getCurrentLimbGrowthScale(player, 4, partial));
            rickregen$applyGrowth(model.rightArm, model.rightSleeve, RickregenHandler.getCurrentLimbGrowthScale(player, 3, partial));
            rickregen$applyGrowth(model.leftLeg, model.leftPants, RickregenHandler.getCurrentLimbGrowthScale(player, 2, partial));
            rickregen$applyGrowth(model.rightLeg, model.rightPants, RickregenHandler.getCurrentLimbGrowthScale(player, 1, partial));
            return;
        }

        boolean showRightLeg = stage < 1;
        model.rightLeg.visible = showRightLeg;
        model.rightPants.visible = showRightLeg;

        boolean showLeftLeg = stage < 2;
        model.leftLeg.visible = showLeftLeg;
        model.leftPants.visible = showLeftLeg;

        boolean showRightArm = stage < 3;
        model.rightArm.visible = showRightArm;
        model.rightSleeve.visible = showRightArm;

        boolean showLeftArm = stage < 4;
        model.leftArm.visible = showLeftArm;
        model.leftSleeve.visible = showLeftArm;

        boolean showHead = stage < 5;
        model.head.visible = showHead;
        model.hat.visible = showHead;
    }

    @Unique
    private void rickregen$resetPartScale(ModelPart base, ModelPart overlay) {
        base.xScale = 1.0F;
        base.yScale = 1.0F;
        base.zScale = 1.0F;
        overlay.xScale = 1.0F;
        overlay.yScale = 1.0F;
        overlay.zScale = 1.0F;
    }

    @Unique
    private void rickregen$applyGrowth(ModelPart base, ModelPart overlay, float scale) {
        if (scale <= 0.02F) {
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
        if (RickregenHandler.isLimbMissing(player, RickregenHandler.RIGHT_ARM, Minecraft.getInstance().getPartialTick())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void rickregen$hideFirstPersonLeftHand(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, AbstractClientPlayer player, CallbackInfo ci) {
        if (RickregenHandler.isLimbMissing(player, RickregenHandler.LEFT_ARM, Minecraft.getInstance().getPartialTick())) {
            ci.cancel();
        }
    }
}
