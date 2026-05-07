package net.droingo.outbackkayak.registry;

import net.droingo.outbackkayak.OutbackKayak;
import net.droingo.outbackkayak.entity.KayakEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<KayakEntity> KAYAK = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(OutbackKayak.MOD_ID, "kayak"),
            EntityType.Builder.create(KayakEntity::new, SpawnGroup.MISC)
                    .dimensions(1.375f, 0.5625f)
                    .maxTrackingRange(64)
                    .trackingTickInterval(1)
                    .build("kayak"));

    private ModEntities() {
    }

    public static void register() {
        OutbackKayak.LOGGER.info("Registering Outback Kayak entities.");
    }
}