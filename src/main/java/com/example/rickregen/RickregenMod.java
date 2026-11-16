package com.example.rickregen;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(RickregenMod.MOD_ID)
public class RickregenMod {
    public static final String MOD_ID = "rickregen";

    public RickregenMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RickregenHandler.onPlayerTick(event.player);
        }
    }

    /** Un golpe mortal activa la regeneracion en lugar de matar al jugador (con espera entre usos). */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && RickregenHandler.tryCheatDeath(player)) {
            event.setCanceled(true);
        }
    }

    /** Con piernas perdidas se salta menos: una pierna, 60% del salto; sin piernas, no se salta. */
    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player) {
            int stage = RickregenHandler.getDismembermentStage(player);
            Vec3 m = player.getDeltaMovement();
            if (stage >= 2) {
                player.setDeltaMovement(m.x, 0.0D, m.z);
            } else if (stage == 1) {
                player.setDeltaMovement(m.x, m.y * 0.6D, m.z);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        RickregenHandler.resetRegenerationState(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RickregenHandler.resetRegenerationState(event.getEntity());
    }
}
