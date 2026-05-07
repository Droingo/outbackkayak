package net.droingo.outbackkayak.client.render;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class KayakEntityRenderer extends GeoEntityRenderer<KayakEntity> {
    public KayakEntityRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new KayakEntityModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public void render(
            KayakEntity entity,
            float entityYaw,
            float partialTick,
            MatrixStack poseStack,
            VertexConsumerProvider bufferSource,
            int packedLight
    ) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}