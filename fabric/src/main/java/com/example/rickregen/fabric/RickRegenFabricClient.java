package com.example.rickregen.fabric;

import com.example.rickregen.client.ClientRegenData;
import com.example.rickregen.network.RegenSyncData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class RickRegenFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(RickRegenFabric.SYNC_ID, (client, handler, buf, responseSender) -> {
			RegenSyncData d = RegenSyncData.read(buf);
			client.execute(() -> ClientRegenData.update(d.playerId(), d.regenerating(), d.rickPrime()));
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRegenData.clear());
	}
}
