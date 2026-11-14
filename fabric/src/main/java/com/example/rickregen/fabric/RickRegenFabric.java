package com.example.rickregen.fabric;

import com.example.rickregen.RegenCommand;
import com.example.rickregen.RegenLogic;
import com.example.rickregen.RickRegen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class RickRegenFabric implements ModInitializer {
	public static final ResourceLocation SYNC_ID = RickRegen.id("regen_sync");

	@Override
	public void onInitialize() {
		RegenLogic.sender = (to, data) -> {
			FriendlyByteBuf buf = PacketByteBufs.create();
			data.write(buf);
			ServerPlayNetworking.send(to, SYNC_ID, buf);
		};

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player && RegenLogic.onDeath(player)));

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player && RegenLogic.isRegenerating(player)));

		ServerTickEvents.END_SERVER_TICK.register(RegenLogic::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RegenLogic.syncAllTo(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RegenLogic.clear());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(RegenCommand.build()));
	}
}
