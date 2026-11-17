package com.example.rickregen.client;

import com.example.rickregen.RickregenMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = RickregenMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    public static final String CATEGORY = "key.categories.rickregen";

    public static final KeyMapping TELEPORT_DODGE_KEY = new KeyMapping(
            "key.rickregen.teleport_dodge",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    public static final KeyMapping DEPLOY_DRONES_KEY = new KeyMapping(
            "key.rickregen.deploy_drones",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    public static final KeyMapping LASER_BURST_KEY = new KeyMapping(
            "key.rickregen.laser_burst",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );

    public static final KeyMapping MANUAL_REGEN_KEY = new KeyMapping(
            "key.rickregen.manual_regen",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TELEPORT_DODGE_KEY);
        event.register(DEPLOY_DRONES_KEY);
        event.register(LASER_BURST_KEY);
        event.register(MANUAL_REGEN_KEY);
    }
}