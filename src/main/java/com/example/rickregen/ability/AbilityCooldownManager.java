package com.example.rickregen.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestiona todas las habilidades activas y sus tiempos de recarga (en ticks: 20 ticks = 1 segundo).
 */
public class AbilityCooldownManager {

    public enum Ability {
        TELEPORT_DODGE("Evasión Celeste [V]", 120),      // 6 segundos de recarga
        DEPLOY_DRONES("Drones Láser [G]", 600),         // 30 segundos de recarga
        LASER_BURST("Ráfaga Láser [R]", 80),            // 4 segundos de recarga
        CUBIC_REGEN("Regeneración Cúbica [H]", 1200);   // 60 segundos de recarga

        private final String displayName;
        private final int maxCooldownTicks;

        Ability(String displayName, int maxCooldownTicks) {
            this.displayName = displayName;
            this.maxCooldownTicks = maxCooldownTicks;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getMaxCooldownTicks() {
            return maxCooldownTicks;
        }
    }

    private static final Map<UUID, Map<Ability, Integer>> playerCooldowns = new HashMap<>();

    public static void tickPlayerCooldowns(UUID playerId) {
        Map<Ability, Integer> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return;

        for (Ability ability : Ability.values()) {
            int current = cooldowns.getOrDefault(ability, 0);
            if (current > 0) {
                cooldowns.put(ability, current - 1);
            }
        }
    }

    public static boolean isReady(UUID playerId, Ability ability) {
        return getRemainingTicks(playerId, ability) <= 0;
    }

    public static void triggerCooldown(UUID playerId, Ability ability) {
        playerCooldowns.computeIfAbsent(playerId, k -> new HashMap<>())
                       .put(ability, ability.getMaxCooldownTicks());
    }

    public static int getRemainingTicks(UUID playerId, Ability ability) {
        Map<Ability, Integer> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return 0;
        return cooldowns.getOrDefault(ability, 0);
    }

    public static float getRemainingSeconds(UUID playerId, Ability ability) {
        return getRemainingTicks(playerId, ability) / 20.0f;
    }
}