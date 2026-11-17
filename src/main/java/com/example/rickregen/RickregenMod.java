package com.example.rickregen;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RickregenMod.MOD_ID)
public class RickregenMod {
    public static final String MOD_ID = "rickregen";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public RickregenMod() {
        LOGGER.info("Inicializando Rickregen v2.0 (Habilidades activas + HUD de recarga)...");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Sistemas de combate y regeneración de Rick Prime cargados.");
    }
}