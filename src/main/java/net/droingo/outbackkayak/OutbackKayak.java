package net.droingo.outbackkayak;

import net.droingo.outbackkayak.network.ModNetworking;
import net.droingo.outbackkayak.registry.ModEntities;
import net.droingo.outbackkayak.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutbackKayak implements ModInitializer {
    public static final String MOD_ID = "outbackkayak";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModItems.register();
        ModNetworking.register();

        LOGGER.info("Outback Kayak loaded.");
    }
}