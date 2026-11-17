package com.example.rickregen.client;

import com.example.rickregen.RickregenMod;
import com.example.rickregen.ability.AbilityCooldownManager;
import com.example.rickregen.ability.AbilityCooldownManager.Ability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Muestra en pantalla (HUD) las habilidades activas, su tecla asignada y el tiempo de recarga en tiempo real.
 */
@Mod.EventBusSubscriber(modid = RickregenMod.MOD_ID, value = Dist.CLIENT)
public class AbilityHudOverlay {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int x = 10;
        int y = 10;

        graphics.drawString(mc.font, "§b[SISTEMA RICK PRIME]", x, y, 0x00D2FF, true);
        y += 12;

        for (Ability ability : Ability.values()) {
            float remainingSec = AbilityCooldownManager.getRemainingSeconds(player.getUUID(), ability);
            String statusText;
            int color;

            if (remainingSec <= 0.0f) {
                statusText = ability.getDisplayName() + ": §aLISTO";
                color = 0x00FF66; // Verde cibernético
            } else {
                statusText = String.format("%s: §c%.1fs", ability.getDisplayName(), remainingSec);
                color = 0xFF2222; // Rojo láser
            }

            graphics.drawString(mc.font, statusText, x, y, color, true);
            y += 11;
        }
    }
}