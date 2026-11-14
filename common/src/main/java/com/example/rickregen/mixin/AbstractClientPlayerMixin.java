package com.example.rickregen.mixin;

import com.example.rickregen.client.ClientRegenData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cambia la skin del jugador a Rick Prime (modelo de brazos anchos). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
	private void rickregen$skin(CallbackInfoReturnable<ResourceLocation> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (ClientRegenData.isRickPrime(self.getUUID())) {
			cir.setReturnValue(ClientRegenData.RICK_PRIME_TEXTURE);
		}
	}

	@Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
	private void rickregen$model(CallbackInfoReturnable<String> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (ClientRegenData.isRickPrime(self.getUUID())) {
			cir.setReturnValue("default");
		}
	}
}
