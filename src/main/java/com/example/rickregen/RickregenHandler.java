package com.example.rickregen;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RickregenHandler {

    // Rastrea el estado de regeneracion activa por jugador (para ambos lados: cliente y servidor)
    private static final Map<UUID, Integer> REGEN_TICKS = new ConcurrentHashMap<>();
    // Duracion total de la secuencia de regeneracion gradual: 100 ticks = 5 segundos (20 ticks por extremidad)
    public static final int TOTAL_REGEN_TICKS = 100;

    public static boolean isRegenerating(Player player) {
        return REGEN_TICKS.containsKey(player.getUUID());
    }

    public static int getRegenTicks(Player player) {
        return REGEN_TICKS.getOrDefault(player.getUUID(), 0);
    }

    public static void resetRegenerationState(Player player) {
        if (player != null) {
            REGEN_TICKS.remove(player.getUUID());
        }
    }

    /**
     * Calcula que extremidades estan visibles segun los corazones o el progreso de la animacion de regeneracion:
     *
     * MODO DANO (sin regeneracion activa):
     * - > 6 corazones (> 12.0F): Etapa 0 (Cuerpo completo)
     * - 6 corazones (<= 12.0F): Etapa 1 (Pierde un pie / pierna derecha)
     * - 5 corazones (<= 10.0F): Etapa 2 (Pierde el otro pie / pierna izquierda)
     * - 4 corazones (<= 8.0F):  Etapa 3 (Pierde una mano / brazo derecho)
     * - 3 corazones (<= 6.0F):  Etapa 4 (Pierde la otra mano / brazo izquierdo)
     * - 2 corazones (<= 4.0F):  Etapa 5 (Pierde la cabeza)
     *
     * MODO REGENERACION GRADUAL (se activa al tocar 1 corazon <= 2.0F):
     * Dura 100 ticks (5 segundos) y va trayendo de vuelta una extremidad cada segundo (20 ticks):
     * - Ticks 0..19:   Etapa 5 (Solo torso - iniciando regeneracion)
     * - Ticks 20..39:  Etapa 4 (Regenera la CABEZA)
     * - Ticks 40..59:  Etapa 3 (Regenera la MANO IZQUIERDA)
     * - Ticks 60..79:  Etapa 2 (Regenera la MANO DERECHA)
     * - Ticks 80..99:  Etapa 1 (Regenera el PIE IZQUIERDO)
     * - Tick >= 100:   Etapa 0 (Regenera el PIE DERECHO - cuerpo 100% completo)
     */
    public static int getDismembermentStage(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return 0;
        }

        UUID uuid = player.getUUID();
        if (REGEN_TICKS.containsKey(uuid)) {
            int ticks = REGEN_TICKS.get(uuid);
            if (ticks < 20) return 5;       // 0.0s - 1.0s: Solo torso
            else if (ticks < 40) return 4;  // 1.0s - 2.0s: Vuelve la cabeza
            else if (ticks < 60) return 3;  // 2.0s - 3.0s: Vuelve la mano izquierda
            else if (ticks < 80) return 2;  // 3.0s - 4.0s: Vuelve la mano derecha
            else if (ticks < 100) return 1; // 4.0s - 5.0s: Vuelve el pie izquierdo
            else return 0;                  // 5.0s: Vuelve el pie derecho (completo)
        }

        float health = player.getHealth();

        if (health <= 2.0F && health > 0.0F) {
            return 5; // Justo antes de arrancar el contador
        } else if (health <= 4.0F) {
            return 5; // 2 corazones: sin pies, manos ni cabeza
        } else if (health <= 6.0F) {
            return 4; // 3 corazones: sin pies ni manos
        } else if (health <= 8.0F) {
            return 3; // 4 corazones: sin pies ni mano derecha
        } else if (health <= 10.0F) {
            return 2; // 5 corazones: sin ambos pies
        } else if (health <= 12.0F) {
            return 1; // 6 corazones: sin pie derecho
        }

        return 0;
    }

    /**
     * Progreso continuo (0.0F a 1.0F) de crecimiento de la extremidad que se esta regenerando actualmente.
     * Permite que la extremidad aparezca escalando suavemente de 0% a 100% de tamano (animacion organica).
     */
    public static float getCurrentLimbGrowthScale(Player player, int limbStageIndex) {
        if (!isRegenerating(player)) {
            return 1.0F;
        }
        int ticks = getRegenTicks(player);
        // Cada extremidad tarda 20 ticks (1 segundo) en crecer completamente
        // limbStageIndex: 5 = cabeza (ticks 20-40), 4 = brazo izq (40-60), 3 = brazo der (60-80), 2 = pierna izq (80-100), 1 = pierna der (90-100)
        int startTick;
        int duration = 20;

        switch (limbStageIndex) {
            case 5 -> startTick = 15;  // Cabeza crece entre tick 15 y 35
            case 4 -> startTick = 35;  // Mano izquierda crece entre tick 35 y 55
            case 3 -> startTick = 55;  // Mano derecha crece entre tick 55 y 75
            case 2 -> startTick = 75;  // Pie izquierdo crece entre tick 75 y 95
            case 1 -> {
                startTick = 85;        // Pie derecho crece entre tick 85 y 100
                duration = 15;
            }
            default -> {
                return 1.0F;
            }
        }

        if (ticks < startTick) return 0.0F;
        if (ticks >= startTick + duration) return 1.0F;
        return (float) (ticks - startTick) / (float) duration;
    }

    public static void onPlayerTick(Player player) {
        if (player.isDeadOrDying()) {
            resetRegenerationState(player);
            return;
        }

        UUID uuid = player.getUUID();
        float health = player.getHealth();

        // Iniciar la secuencia de regeneracion gradual al llegar a 1 corazon (2.0F) o menos
        if (!REGEN_TICKS.containsKey(uuid) && health <= 2.0F && health > 0.0F) {
            REGEN_TICKS.put(uuid, 0);

            if (!player.level().isClientSide && player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(
                        null, player.blockPosition(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                        0.8F, 1.4F
                );
            }
        }

        // Si la regeneracion gradual esta en curso, avanzar tick a tick (tanto en cliente como en servidor)
        if (REGEN_TICKS.containsKey(uuid)) {
            int currentTick = REGEN_TICKS.get(uuid) + 1;
            REGEN_TICKS.put(uuid, currentTick);

            if (!player.level().isClientSide) {
                // Curar progresivamente a lo largo de los 100 ticks (~0.18F por tick = 18 puntos = 9 corazones)
                if (player.getHealth() < player.getMaxHealth()) {
                    player.heal(0.20F);
                }

                // Escudo breve durante los 5 segundos de reconstruccion para evitar interrupciones fatales
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 2, false, false));

                if (player.level() instanceof ServerLevel serverLevel) {
                    // Particulas continuas de reconstruccion celular
                    if (currentTick % 4 == 0) {
                        serverLevel.sendParticles(
                                ParticleTypes.HAPPY_VILLAGER,
                                player.getX(), player.getY() + 1.0, player.getZ(),
                                5, 0.35, 0.55, 0.35, 0.02
                        );
                    }

                    // Destello y sonido organico cada vez que reaparece una extremidad (ticks 20, 40, 60, 80, 100)
                    if (currentTick == 20 || currentTick == 40 || currentTick == 60 || currentTick == 80 || currentTick == 100) {
                        double yOffset = (currentTick == 20) ? 1.6 : (currentTick <= 60 ? 1.1 : 0.4);
                        serverLevel.sendParticles(
                                ParticleTypes.COMPOSTER,
                                player.getX(), player.getY() + yOffset, player.getZ(),
                                14, 0.3, 0.3, 0.3, 0.05
                        );
                        serverLevel.playSound(
                                null, player.blockPosition(),
                                SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS,
                                0.7F, 1.2F + (currentTick / 200.0F)
                        );
                    }
                }
            }

            // Finalizar secuencia al completar los 100 ticks (5 segundos)
            if (currentTick >= TOTAL_REGEN_TICKS) {
                REGEN_TICKS.remove(uuid);
                if (!player.level().isClientSide) {
                    player.setHealth(player.getMaxHealth());
                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(
                                null, player.blockPosition(),
                                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                                0.5F, 1.6F
                        );
                    }
                }
            }
        }
    }
}
