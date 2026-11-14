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
 * Animacion de regeneracion en tres fases:
 *  - 0%..15%: los brazos suben y la cabeza se echa hacia atras (suave)
 *  - 15%..85%: pose abierta con un temblor que va en aumento
 *  - 85%..100%: los brazos vuelven a su sitio
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	private static float rickregen$smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void rickregen$pose(LivingEntity entity, float limbSwing, float limbSwingAmount,
								float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		float p = ClientRegenData.progress(entity.getUUID());
		if (p < 0.0F) return;

		// 0 = pose normal, 1 = pose completamente abierta
		float open = rickregen$smooth(p / 0.15F) * (1.0F - rickregen$smooth((p - 0.85F) / 0.15F));
		if (open <= 0.0F) return;

		// El temblor crece con el tiempo
		float shakeAmp = (0.02F + 0.10F * p) * open;
		float shake = Mth.sin(ageInTicks * 1.9F) * shakeAmp;
		float shake2 = Mth.sin(ageInTicks * 2.7F + 1.3F) * shakeAmp;
		float armOpen = (float) (Math.PI / 2.0) + 0.12F;

		PlayerModel<?> m = (PlayerModel<?>) (Object) this;

		m.rightArm.xRot = Mth.lerp(open, m.rightArm.xRot, -0.15F + shake2);
		m.rightArm.yRot = Mth.lerp(open, m.rightArm.yRot, 0.0F);
		m.rightArm.zRot = Mth.lerp(open, m.rightArm.zRot, armOpen + shake);

		m.leftArm.xRot = Mth.lerp(open, m.leftArm.xRot, -0.15F - shake2);
		m.leftArm.yRot = Mth.lerp(open, m.leftArm.yRot, 0.0F);
		m.leftArm.zRot = Mth.lerp(open, m.leftArm.zRot, -armOpen - shake);

		// Cabeza hacia atras y cuerpo ligeramente arqueado
		m.head.xRot = Mth.lerp(open, m.head.xRot, -0.75F + shake2);
		m.head.zRot = Mth.lerp(open, m.head.zRot, shake);
		m.body.xRot = Mth.lerp(open, m.body.xRot, -0.12F);

		m.hat.copyFrom(m.head);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
	}
}
