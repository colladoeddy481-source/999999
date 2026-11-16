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

    private static final Map<UUID, Integer> CLIENT_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SERVER_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SAVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_STAGE = new ConcurrentHashMap<>();

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.1F);

    public static final int TOTAL_REGEN_TICKS = 100;
    public static final long SAVE_COOLDOWN_TICKS = 600L;

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

    private static float easeOutCubic(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    public static float getCurrentLimbGrowthScale(Player player, int limbStageIndex, float partialTick) {
        if (!isRegenerating(player)) {
            return getDismembermentStage(player) >= limbStageIndex ? 0.0F : 1.0F;
        }
        float ticks = Math.min(getRegenTicks(player) + partialTick, (float) TOTAL_REGEN_TICKS);

        int startTick;
        int duration = 20;

        switch (limbStageIndex) {
            case 5 -> startTick = 12;
            case 4 -> startTick = 32;
            case 3 -> startTick = 52;
            case 2 -> startTick = 70;
            case 1 -> {
                startTick = 82;
                duration = 18;
            }
            default -> {
                return 1.0F;
            }
        }

        if (ticks < startTick) return 0.0F;
        if (ticks >= startTick + duration) return 1.0F;
        return easeOutCubic((ticks - startTick) / (float) duration);
    }

    public static boolean isLimbMissing(Player player, int limbIndex, float partialTick) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (isRegenerating(player)) {
            return getCurrentLimbGrowthScale(player, limbIndex, partialTick) <= 0.02F;
        }
        return getDismembermentStage(player) >= limbIndex;
    }

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
     * Posicion exacta del muñon adaptada perfectamente a la postura SWIMMING nativa de Minecraft 1.20.1.
     */
    public static Vec3 getLimbOffset(Player player, int limbIndex, float bodyYawDeg, boolean crawling) {
        double yaw = Math.toRadians(bodyYawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // Adelante
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // Derecha

        if (crawling) {
            // En postura SWIMMING nativa, el cuerpo esta horizontal a lo largo del eje longitudinal del jugador
            switch (limbIndex) {
                case RIGHT_LEG -> { return new Vec3(rx * 0.22 + fx * (-0.75), 0.15, rz * 0.22 + fz * (-0.75)); }
                case LEFT_LEG  -> { return new Vec3(rx * (-0.22) + fx * (-0.75), 0.15, rz * (-0.22) + fz * (-0.75)); }
                case RIGHT_ARM -> { return new Vec3(rx * 0.32 + fx * (-0.15), 0.20, rz * 0.32 + fz * (-0.15)); }
                case LEFT_ARM  -> { return new Vec3(rx * (-0.32) + fx * (-0.15), 0.20, rz * (-0.32) + fz * (-0.15)); }
                default        -> { return new Vec3(fx * 0.55, 0.25, fz * 0.55); } // HEAD
            }
        }

        // De pie
        double side = (limbIndex == RIGHT_LEG || limbIndex == RIGHT_ARM) ? 1.0 : -1.0;
        if (limbIndex == HEAD) {
            return new Vec3(0.0, 1.44, 0.0);
        }
        double sideDist = (limbIndex <= 2) ? 0.12 : 0.34;
        double upright = (limbIndex <= 2) ? 0.72 : 1.32;
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
                            player.getX(), player.getY() + 0.5, player.getZ(),
                            5, 0.35, 0.45, 0.35, 0.02
                    );
                }

                if (currentTick == 20 || currentTick == 40 || currentTick == 60 || currentTick == 80 || currentTick == 100) {
                    double yOffset = isCrawling(player) ? 0.25 : ((currentTick == 20) ? 1.5 : 1.0);
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

    private static void tickMobilityAndBlood(Player player, ServerLevel level) {
        UUID uuid = player.getUUID();
        int stage = getDismembermentStage(player);

        if (stage >= 1) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, stage >= 2 ? 0 : 1, false, false, false));
        }

        Integer lastObj = LAST_STAGE.put(uuid, stage);
        int last = lastObj == null ? 0 : lastObj;

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
