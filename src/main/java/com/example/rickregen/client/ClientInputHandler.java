package com.example.rickregen.client;

import com.example.rickregen.RickregenMod;
import com.example.rickregen.ability.AbilityCooldownManager;
import com.example.rickregen.ability.AbilityCooldownManager.Ability;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = RickregenMod.MOD_ID, value = Dist.CLIENT)
public class ClientInputHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        AbilityCooldownManager.tickPlayerCooldowns(player.getUUID());

        // 1. Teletransporte de Evasión con estela azul celeste (Tecla V)
        if (KeyBindings.TELEPORT_DODGE_KEY.consumeClick()) {
            if (AbilityCooldownManager.isReady(player.getUUID(), Ability.TELEPORT_DODGE)) {
                executeTeleportDodge(player);
                AbilityCooldownManager.triggerCooldown(player.getUUID(), Ability.TELEPORT_DODGE);
            }
        }

        // 2. Despliegue de Drones de Combate (Tecla G)
        if (KeyBindings.DEPLOY_DRONES_KEY.consumeClick()) {
            if (AbilityCooldownManager.isReady(player.getUUID(), Ability.DEPLOY_DRONES)) {
                RickregenMod.LOGGER.info("Drones de combate desplegados.");
                AbilityCooldownManager.triggerCooldown(player.getUUID(), Ability.DEPLOY_DRONES);
            }
        }

        // 3. Ráfaga Láser Cibernética (Tecla R)
        if (KeyBindings.LASER_BURST_KEY.consumeClick()) {
            if (AbilityCooldownManager.isReady(player.getUUID(), Ability.LASER_BURST)) {
                RickregenMod.LOGGER.info("Disparando ráfaga láser de energía.");
                AbilityCooldownManager.triggerCooldown(player.getUUID(), Ability.LASER_BURST);
            }
        }

        // 4. Regeneración Cúbica Manual / Forzada (Tecla H)
        if (KeyBindings.MANUAL_REGEN_KEY.consumeClick()) {
            if (AbilityCooldownManager.isReady(player.getUUID(), Ability.CUBIC_REGEN)) {
                RickregenMod.LOGGER.info("Activando secuencia de regeneración cúbica lenta.");
                AbilityCooldownManager.triggerCooldown(player.getUUID(), Ability.CUBIC_REGEN);
            }
        }
    }

    private static void executeTeleportDodge(Player player) {
        Vec3 look = player.getLookAngle();
        // Desplazamiento rápido lateral/frontal de 4 bloques
        Vec3 dodgeVec = new Vec3(-look.z, 0, look.x).normalize().scale(4.0);
        
        // Genera estela borrosa de color azul celeste (#00D2FF) en la posición original
        DustParticleOptions cyanTrail = new DustParticleOptions(new Vector3f(0.0f, 0.82f, 1.0f), 1.8f);
        for (int i = 0; i < 25; i++) {
            double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.8;
            double offsetY = player.getRandom().nextDouble() * 1.8;
            double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.8;
            player.level().addParticle(cyanTrail, player.getX() + offsetX, player.getY() + offsetY, player.getZ() + offsetZ, 0, 0, 0);
        }

        player.setPos(player.getX() + dodgeVec.x, player.getY(), player.getZ() + dodgeVec.z);
    }
}