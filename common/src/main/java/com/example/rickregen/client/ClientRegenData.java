package com.example.rickregen.client;

import com.example.rickregen.RickRegen;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Estado de regeneracion que el cliente conoce de cada jugador. */
public final class ClientRegenData {
	public static final ResourceLocation RICK_PRIME_TEXTURE = RickRegen.id("textures/entity/rick_prime.png");

	/** Duracion de la animacion en nanosegundos (8 segundos, igual que en el servidor). */
	private static final double DURATION_NANOS = 8_000_000_000.0;

	private record Info(boolean regenerating, boolean rickPrime, long startNanos) {}

	private static final Map<UUID, Info> DATA = new HashMap<>();

	private ClientRegenData() {}

	public static void update(UUID id, boolean regenerating, boolean rickPrime) {
		Info old = DATA.get(id);
		long start = (old != null && old.regenerating() && regenerating) ? old.startNanos() : System.nanoTime();
		DATA.put(id, new Info(regenerating, rickPrime, start));
	}

	public static boolean isRegenerating(UUID id) {
		Info i = DATA.get(id);
		return i != null && i.regenerating();
	}

	/** Progreso de la animacion de 0 a 1, o -1 si el jugador no esta regenerando. */
	public static float progress(UUID id) {
		Info i = DATA.get(id);
		if (i == null || !i.regenerating()) return -1.0F;
		double p = (System.nanoTime() - i.startNanos()) / DURATION_NANOS;
		return (float) Math.min(p, 1.0);
	}

	public static boolean isRickPrime(UUID id) {
		Info i = DATA.get(id);
		return i != null && i.rickPrime();
	}

	public static void clear() {
		DATA.clear();
	}
}
