package com.example.rickregen;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RickregenHandler {

    // Cliente y servidor comparten JVM en mundos de un jugador: cada lado necesita su PROPIO contador.
    private static final Map<UUID, Integer> CLIENT_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SERVER_TICKS = new ConcurrentHashMap<>();
    // Ultima vez (en ticks del mundo) que una regeneracion te salvo de morir
    private static final Map<UUID, Long> LAST_SAVE = new ConcurrentHashMap<>();
    // Ultima etapa de extremidades perdidas vista en el servidor (para detectar "acabas de perder una")
    private static final Map<UUID, Integer> LAST_STAGE = new ConcurrentHashMap<>();

    // Sangre: polvo rojo oscuro
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.1F);

    public static final int TOTAL_REGEN_TICKS = 100;
    public static final long SAVE_COOLDOWN_TICKS = 600L;

    // Indices de extremidad: 1 pierna derecha, 2 pierna izquierda, 3 brazo derecho, 4 brazo izquierdo, 5 cabeza
    public static final int RIGHT_LEG = 1, LEFT_LEG = 2, RIGHT_ARM = 3, LEFT_ARM = 4, HEAD = 5;

    private static Map<UUID, Integer> ticksMap(Player player) {
        return player.level().isClientSide ? CLIENT_TICKS : SERVER_TICKS;
    }

    public static boolean isRegenerating(Player player) {
        return ticksMap(player).containsKey(player.getUUID());
    }

    public static int getRegenTicks(Player player) {
        return ticksMap(player).getOrDefault(player.getUUID(), 0);
    }

    public static void resetRegenerationState(Player player) {
        if (player != null) {
            ticksMap(player).remove(player.getUUID());
            if (!player.level().isClientSide) {
                LAST_SAVE.remove(player.getUUID());
                LAST_STAGE.remove(player.getUUID());
            }
        }
    }

    /** Golpe mortal: si puede, cancela la muerte, deja 1 de vida y arranca la regeneracion. */
    public static boolean tryCheatDeath(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        UUID uuid = player.getUUID();

        if (SERVER_TICKS.containsKey(uuid)) {
            player.setHealth(1.0F);
            return true;
        }

        long now = player.level().getGameTime();
        Long last = LAST_SAVE.get(uuid);
        if (last != null && now - last < SAVE_COOLDOWN_TICKS) {
            return false;
        }

        LAST_SAVE.put(uuid, now);
        player.setHealth(1.0F);
        SERVER_TICKS.put(uuid, 0);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    /**
     * Etapa de extremidades perdidas (0 a 5):
     * 6 corazones: pierna derecha | 5: pierna izquierda | 4: brazo derecho | 3: brazo izquierdo | 2: cabeza.
     * Durante la regeneracion va bajando una etapa cada segundo.
     */
    public static int getDismembermentStage(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return 0;
        }

        Integer ticks = ticksMap(player).get(player.getUUID());
        if (ticks != null) {
            if (ticks < 20) return 5;
            else if (ticks < 40) return 4;
            else if (ticks < 60) return 3;
            else if (ticks < 80) return 2;
            else if (ticks < 100) return 1;
            else return 0;
        }

        float health = player.getHealth();

        if (health <= 4.0F) {
            return 5;
        } else if (health <= 6.0F) {
            return 4;
        } else if (health <= 8.0F) {
            return 3;
        } else if (health <= 10.0F) {
            return 2;
        } else if (health <= 12.0F) {
            return 1;
        }

        return 0;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.2F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    public static float getCurrentLimbGrowthScale(Player player, int limbStageIndex, float partialTick) {
        if (!isRegenerating(player)) {
            return 1.0F;
        }
        float ticks = Math.min(getRegenTicks(player) + partialTick, (float) TOTAL_REGEN_TICKS);

        int startTick;
        int duration = 20;

        switch (limbStageIndex) {
            case 5 -> startTick = 15;
            case 4 -> startTick = 35;
            case 3 -> startTick = 55;
            case 2 -> startTick = 75;
            case 1 -> {
                startTick = 85;
                duration = 15;
            }
            default -> {
                return 1.0F;
            }
        }

        if (ticks < startTick) return 0.0F;
        if (ticks >= startTick + duration) return 1.0F;
        return easeOutBack((ticks - startTick) / (float) duration);
    }

    /** true si esa extremidad esta ahora mismo desprendida (y por tanto hay que dibujar su pieza suelta y su cable). */
    public static boolean isLimbMissing(Player player, int limbIndex, float partialTick) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (isRegenerating(player)) {
            return getCurrentLimbGrowthScale(player, limbIndex, partialTick) <= 0.01F;
        }
        return getDismembermentStage(player) >= limbIndex;
    }

    /** Sin ambas piernas el jugador se arrastra (pose de gateo). Los brazos perdidos despues no lo cambian. */
    public static boolean isCrawling(Player player) {
        return getDismembermentStage(player) >= 2;
    }

    public static boolean shouldCrawl(Player player) {
        return isCrawling(player)
                && !player.isInWater()
                && !player.isPassenger()
                && !player.isSleeping()
                && !player.isFallFlying()
                && !player.getAbilities().flying;
    }

    /**
     * Posicion del "muñon" de una extremidad respecto a los pies del jugador, en coordenadas del mundo.
     * bodyYawDeg es la rotacion del cuerpo.
     */
    public static Vec3 getLimbOffset(Player player, int limbIndex, float bodyYawDeg, boolean crawling) {
        double yaw = Math.toRadians(bodyYawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // delante
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // derecha del jugador

        double side;
        double sideDist;
        double upright;
        double crawlShift;
        switch (limbIndex) {
            case RIGHT_LEG -> { side = 1; sideDist = 0.12; upright = 0.75; crawlShift = -0.55; }
            case LEFT_LEG -> { side = -1; sideDist = 0.12; upright = 0.75; crawlShift = -0.55; }
            case RIGHT_ARM -> { side = 1; sideDist = 0.37; upright = 1.35; crawlShift = 0.30; }
            case LEFT_ARM -> { side = -1; sideDist = 0.37; upright = 1.35; crawlShift = 0.30; }
            default -> { side = 0; sideDist = 0.0; upright = 1.45; crawlShift = 0.45; }
        }

        if (crawling) {
            return new Vec3(fx * crawlShift + rx * side * sideDist, 0.25, fz * crawlShift + rz * side * sideDist);
        }
        return new Vec3(rx * side * sideDist, upright, rz * side * sideDist);
    }

    public static void onPlayerTick(Player player) {
        if (player.isDeadOrDying()) {
            resetRegenerationState(player);
            return;
        }

        Map<UUID, Integer> map = ticksMap(player);
        UUID uuid = player.getUUID();
        float health = player.getHealth();

        // Iniciar la regeneracion al llegar a 1 corazon o menos
        if (!map.containsKey(uuid) && health <= 2.0F && health > 0.0F) {
            map.put(uuid, 0);

            if (!player.level().isClientSide && player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(
                        null, player.blockPosition(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                        0.8F, 1.4F
                );
            }
        }

        // Movilidad reducida y sangre (solo servidor)
        if (!player.level().isClientSide && player.level() instanceof ServerLevel serverLevel) {
            tickMobilityAndBlood(player, serverLevel);
        }

        Integer current = map.get(uuid);
        if (current == null) {
            return;
        }

        int currentTick = current + 1;
        map.put(uuid, currentTick);

        if (!player.level().isClientSide) {
            if (player.getHealth() < player.getMaxHealth()) {
                player.heal(0.20F);
            }

            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 2, false, false));

            if (player.level() instanceof ServerLevel serverLevel) {
                if (currentTick % 4 == 0) {
                    serverLevel.sendParticles(
                            ParticleTypes.HAPPY_VILLAGER,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            5, 0.35, 0.55, 0.35, 0.02
                    );
                }

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

        if (currentTick >= TOTAL_REGEN_TICKS) {
            map.remove(uuid);
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

    /** Lentitud segun las piernas perdidas + sangre al perder una extremidad y goteo mientras falte. */
    private static void tickMobilityAndBlood(Player player, ServerLevel level) {
        UUID uuid = player.getUUID();
        int stage = getDismembermentStage(player);

        // Una pierna perdida: -30% de velocidad. Sin piernas se arrastra (el gateo ya es lento de por si).
        if (stage >= 1) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, stage >= 2 ? 0 : 1, false, false, false));
        }

        Integer lastObj = LAST_STAGE.put(uuid, stage);
        int last = lastObj == null ? 0 : lastObj;

        // Acabas de perder una o mas extremidades por dano: chorro de sangre en cada muñon nuevo
        if (!isRegenerating(player) && stage > last) {
            for (int idx = last + 1; idx <= stage; idx++) {
                Vec3 off = getLimbOffset(player, idx, player.yBodyRot, isCrawling(player));
                level.sendParticles(BLOOD,
                        player.getX() + off.x, player.getY() + off.y, player.getZ() + off.z,
                        25, 0.12, 0.12, 0.12, 0.08);
            }
            level.playSound(null, player.blockPosition(),
                    SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
        }

        // Goteo continuo de cada extremidad que falta
        if (player.tickCount % 5 == 0) {
            for (int idx = 1; idx <= 5; idx++) {
                if (isLimbMissing(player, idx, 0.0F)) {
                    Vec3 off = getLimbOffset(player, idx, player.yBodyRot, isCrawling(player));
                    level.sendParticles(BLOOD,
                            player.getX() + off.x, player.getY() + off.y, player.getZ() + off.z,
                            2, 0.04, 0.04, 0.04, 0.0);
                }
            }
        }
    }
}
