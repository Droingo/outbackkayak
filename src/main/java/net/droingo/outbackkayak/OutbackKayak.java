package net.droingo.outbackkayak;

import net.droingo.outbackkayak.event.ModInteractionEvents;
import net.droingo.outbackkayak.network.ModNetworking;
import net.droingo.outbackkayak.registry.ModEntities;
import net.droingo.outbackkayak.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import net.droingo.outbackkayak.event.RapidControllerParticleEvents;
import org.slf4j.LoggerFactory;
import net.droingo.outbackkayak.registry.ModBlocks;
import net.droingo.outbackkayak.event.PaddleBlockBreakEvents;

public class OutbackKayak implements ModInitializer {
    public static final String MOD_ID = "outbackkayak";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModEntities.register();
        ModItems.register();
        ModNetworking.register();
        ModInteractionEvents.register();
        RapidControllerParticleEvents.register();
        PaddleBlockBreakEvents.register();
        LOGGER.info("Outback Kayak loaded.");
    }
}