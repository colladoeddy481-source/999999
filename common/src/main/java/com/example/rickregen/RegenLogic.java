package com.example.rickregen;

import com.example.rickregen.network.RegenSyncData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Logica de servidor de la regeneracion, compartida por todos los loaders.
 * Cada loader llama a estos metodos desde sus eventos y asigna {@link #sender}.
 * El estado vive en memoria (no se guarda al cerrar el mundo).
 */
public final class RegenLogic {
	/** Duracion de la animacion en ticks (160 = 8 segundos). */
	public static final int DURATION = 160;
	/** Cuantas veces una muerte se convierte en regeneracion. */
	public static final int MAX_DEATH_REGENS = 12;

	/** Cada loader define como se envia el paquete al cliente. */
	public interface Sender {
		void send(ServerPlayer to, RegenSyncData payload);
	}

	public static Sender sender = (to, payload) -> {};

	private static final class State {
		int ticksLeft;
		boolean rickPrime;
		int deathRegens;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private RegenLogic() {}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	public static boolean isRegenerating(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.ticksLeft > 0;
	}

	public static void clear() {
		STATES.clear();
	}

	/** @return true si la muerte debe cancelarse (el jugador regenera). */
	public static boolean onDeath(ServerPlayer player) {
		State s = state(player);
		if (s.ticksLeft > 0) {
			player.setHealth(1.0F);
			return true;
		}
		if (s.deathRegens < MAX_DEATH_REGENS) {
			s.deathRegens++;
			player.setHealth(1.0F);
			start(player);
			return true;
		}
		return false;
	}

	public static boolean start(ServerPlayer player) {
		State s = state(player);
		if (s.ticksLeft > 0) return false;
		s.ticksLeft = DURATION;
		player.setGlowingTag(true);
		player.clearFire();
		player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
		broadcast(player.server, player.getUUID(), s);
		return true;
	}

	public static void resetSkin(ServerPlayer player) {
		State s = state(player);
		s.rickPrime = false;
		broadcast(player.server, player.getUUID(), s);
	}

	/** Al entrar un jugador, enviarle el estado de todos los demas. */
	public static void syncAllTo(ServerPlayer target) {
		for (Map.Entry<UUID, State> e : STATES.entrySet()) {
			State s = e.getValue();
			sender.send(target, new RegenSyncData(e.getKey(), s.ticksLeft > 0, s.rickPrime));
		}
	}

	public static void tick(MinecraftServer server) {
		for (UUID id : List.copyOf(STATES.keySet())) {
			State s = STATES.get(id);
			if (s == null || s.ticksLeft <= 0) continue;
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) continue;

			s.ticksLeft--;
			int elapsed = DURATION - s.ticksLeft;

			// Inmovil
			player.setDeltaMovement(0, 0, 0);
			player.hurtMarked = true;
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 10, false, false, false));

			// Particulas doradas: manos + cabeza, mas intensas con el tiempo
			ServerLevel level = player.serverLevel();
			double x = player.getX(), y = player.getY(), z = player.getZ();
			double yaw = Math.toRadians(player.getYRot());
			double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
			int count = 2 + (int) (elapsed / (float) DURATION * 10);

			for (int side = -1; side <= 1; side += 2) {
				double hx = x + rightX * 0.9 * side;
				double hz = z + rightZ * 0.9 * side;
				level.sendParticles(ParticleTypes.END_ROD, hx, y + 1.4, hz, count, 0.08, 0.15, 0.08, 0.06);
				level.sendParticles(ParticleTypes.FLAME, hx, y + 1.4, hz, count / 2, 0.05, 0.1, 0.05, 0.03);
			}
			level.sendParticles(ParticleTypes.END_ROD, x, y + 2.0, z, count, 0.12, 0.25, 0.12, 0.08);
			level.sendParticles(ParticleTypes.FLAME, x, y + 2.0, z, count / 2, 0.1, 0.15, 0.1, 0.04);

			// Espiral doble de energia que sube alrededor del cuerpo
			double angle = elapsed * 0.35;
			double radius = 0.9 + 0.3 * Math.sin(elapsed * 0.05);
			double rise = (elapsed % 40) / 40.0 * 2.3;
			for (int k = 0; k < 2; k++) {
				double a = angle + k * Math.PI;
				level.sendParticles(ParticleTypes.END_ROD,
					x + Math.cos(a) * radius, y + rise, z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
			}

			if (s.ticksLeft % 40 == 0 && s.ticksLeft > 0) {
				level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
			}

			if (s.ticksLeft == 0) {
				finish(player, s);
			}
		}
	}

	private static void finish(ServerPlayer player, State s) {
		s.rickPrime = true;
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.setGlowingTag(false);

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.4, 0.8, 0.4, 0.15);
		// Anillo de llamas que sale expandido hacia afuera
		for (int i = 0; i < 36; i++) {
			double a = i * (Math.PI * 2.0 / 36.0);
			level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(),
				0, Math.cos(a), 0.05, Math.sin(a), 0.35);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

		broadcast(player.server, player.getUUID(), s);
	}

	private static void broadcast(MinecraftServer server, UUID id, State s) {
		RegenSyncData payload = new RegenSyncData(id, s.ticksLeft > 0, s.rickPrime);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			sender.send(p, payload);
		}
	}
}
