package com.example.rickregen.mixin;

import com.example.rickregen.client.ClientRegenData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animaciones de Rick Prime para el modelo vanilla del jugador.
 * Se aplican solo cuando el jugador ya se transformo en Rick Prime.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    private static float rickregen$smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void rickregen$animate(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;

        // Conserva la animacion de transformacion que ya tenia el mod.
        float regen = ClientRegenData.progress(entity.getUUID());
        if (regen >= 0.0F) {
            float open = rickregen$smooth(regen / 0.15F)
                    * (1.0F - rickregen$smooth((regen - 0.85F) / 0.15F));
            if (open > 0.0F) {
                float shakeAmp = (0.02F + 0.10F * regen) * open;
                float shake = Mth.sin(ageInTicks * 1.9F) * shakeAmp;
                float shake2 = Mth.sin(ageInTicks * 2.7F + 1.3F) * shakeAmp;
                float armOpen = (float) (Math.PI / 2.0) + 0.12F;

                model.rightArm.xRot = Mth.lerp(open, model.rightArm.xRot, -0.15F + shake2);
                model.rightArm.yRot = Mth.lerp(open, model.rightArm.yRot, 0.0F);
                model.rightArm.zRot = Mth.lerp(open, model.rightArm.zRot, armOpen + shake);
                model.leftArm.xRot = Mth.lerp(open, model.leftArm.xRot, -0.15F - shake2);
                model.leftArm.yRot = Mth.lerp(open, model.leftArm.yRot, 0.0F);
                model.leftArm.zRot = Mth.lerp(open, model.leftArm.zRot, -armOpen - shake);
                model.head.xRot = Mth.lerp(open, model.head.xRot, -0.75F + shake2);
                model.head.zRot = Mth.lerp(open, model.head.zRot, shake);
                model.body.xRot = Mth.lerp(open, model.body.xRot, -0.12F);

                model.hat.copyFrom(model.head);
                model.jacket.copyFrom(model.body);
                model.leftSleeve.copyFrom(model.leftArm);
                model.rightSleeve.copyFrom(model.rightArm);
            }
            return;
        }

        // Las animaciones especiales solo se ejecutan tras convertirse en Rick Prime.
        if (!ClientRegenData.isRickPrime(entity.getUUID())) return;

        float movement = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float idle = 1.0F - movement;
        float time = ageInTicks;

        // Idle: respiracion tranquila, cabeza ladeada y postura confiada.
        model.body.xRot += Mth.sin(time * 0.08F) * 0.018F * idle;
        model.head.zRot += Mth.sin(time * 0.055F) * 0.025F * idle;
        model.head.xRot += Mth.sin(time * 0.075F) * 0.012F * idle;
        model.rightArm.zRot += 0.035F * idle;
        model.leftArm.zRot -= 0.025F * idle;

        // Caminar: balanceo ligeramente mas marcado y hombros con peso.
        float walk = Mth.cos(limbSwing * 0.6662F) * movement;
        model.rightArm.xRot += walk * 0.12F;
        model.leftArm.xRot -= walk * 0.12F;
        model.body.yRot += Mth.sin(limbSwing * 0.5F) * 0.035F * movement;

        // Correr: Rick inclina el torso hacia delante.
        if (entity.isSprinting()) {
            model.body.xRot -= 0.12F * movement;
            model.head.xRot -= 0.035F * movement;
            model.rightArm.xRot -= 0.10F * movement;
            model.leftArm.xRot -= 0.10F * movement;
        }

        // Salto/caida: ligera pose de equilibrio en el aire.
        if (!entity.onGround()) {
            float air = Mth.clamp((float) Math.abs(entity.getDeltaMovement().y) * 1.5F, 0.0F, 1.0F);
            model.body.xRot -= 0.045F * air;
            model.rightArm.zRot += 0.08F * air;
            model.leftArm.zRot -= 0.08F * air;
        }

        // Golpe: giro corto del torso sincronizado con el swing vanilla.
        float attack = entity.getAttackAnim(0.0F);
        if (attack > 0.0F) {
            float punch = Mth.sin(attack * (float) Math.PI);
            model.body.yRot += punch * 0.28F;
            model.head.yRot += punch * -0.10F;
        }

        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }
}
