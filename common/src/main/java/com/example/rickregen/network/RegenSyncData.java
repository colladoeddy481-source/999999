package com.example.rickregen.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Servidor -> cliente: estado de regeneracion de un jugador. */
public record RegenSyncData(UUID playerId, boolean regenerating, boolean rickPrime) {
	public void write(FriendlyByteBuf buf) {
		buf.writeUUID(playerId);
		buf.writeBoolean(regenerating);
		buf.writeBoolean(rickPrime);
	}

	public static RegenSyncData read(FriendlyByteBuf buf) {
		return new RegenSyncData(buf.readUUID(), buf.readBoolean(), buf.readBoolean());
	}
}
