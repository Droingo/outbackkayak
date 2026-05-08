package net.droingo.outbackkayak;

import net.droingo.outbackkayak.client.CarryLookSlowdown;
import net.droingo.outbackkayak.client.ModKeybinds;
import net.droingo.outbackkayak.client.render.KayakEntityRenderer;
import net.droingo.outbackkayak.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class OutbackKayakClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.KAYAK, KayakEntityRenderer::new);
        ModKeybinds.register();
        CarryLookSlowdown.register();
    }
}