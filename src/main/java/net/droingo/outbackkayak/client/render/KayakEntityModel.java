package net.droingo.outbackkayak.client.render;

import net.droingo.outbackkayak.OutbackKayak;
import net.droingo.outbackkayak.entity.KayakEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class KayakEntityModel extends GeoModel<KayakEntity> {
    @Override
    public Identifier getModelResource(KayakEntity animatable) {
        return Identifier.of(OutbackKayak.MOD_ID, "geo/kayak.geo.json");
    }

    @Override
    public Identifier getTextureResource(KayakEntity animatable) {
        return Identifier.of(OutbackKayak.MOD_ID, "textures/entity/kayak.png");
    }

    @Override
    public Identifier getAnimationResource(KayakEntity animatable) {
        return Identifier.of(OutbackKayak.MOD_ID, "animations/kayak.animation.json");
    }
}