package com.example.rickregen;

import com.example.rickregen.ability.AbilityCooldownManager;
import com.example.rickregen.ability.AbilityCooldownManager.Ability;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = RickregenMod.MOD_ID)
public class RickregenHandler {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            // Caídas severas (20-30 bloques) -> 1 corazón + activación de regeneración si está lista
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                if (event.getAmount() >= 15.0f) {
                    player.setHealth(2.0f);
                    event.setCanceled(true);
                    if (AbilityCooldownManager.isReady(player.getUUID(), Ability.CUBIC_REGEN)) {
                        AbilityCooldownManager.triggerCooldown(player.getUUID(), Ability.CUBIC_REGEN);
                    }
                }
            }

            // Ataques demoledores (Gólem de Hierro) -> Estado de piernas rotas y arrastre
            if (event.getSource().getEntity() instanceof IronGolem) {
                if (player.getHealth() - event.getAmount() <= 4.0f) {
                    RickregenMod.LOGGER.info("Piernas rotas por impacto de Gólem: activando arrastre.");
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getHealth() <= 2.0f) {
                AABB searchArea = player.getBoundingBox().inflate(10.0D);
                List<Mob> nearbyMobs = player.level().getEntitiesOfClass(Mob.class, searchArea);

                for (Mob mob : nearbyMobs) {
                    if (mob.getTarget() == player) {
                        mob.setTarget(null);
                        mob.getNavigation().moveTo(
                            player.getX() - (mob.getX() - player.getX()),
                            player.getY(),
                            player.getZ() - (mob.getZ() - player.getZ()),
                            1.3D
                        );
                    }
                }
            }
        }
    }

    // Métodos de soporte requeridos por PlayerRendererMixin
    public static int getDismembermentStage(AbstractClientPlayer player) {
        return 0;
    }

    public static boolean isRegenerating(AbstractClientPlayer player) {
        return false;
    }

    public static float getCurrentLimbGrowthScale(AbstractClientPlayer player, int limbId, float partialTick) {
        return 1.0f;
    }
}
