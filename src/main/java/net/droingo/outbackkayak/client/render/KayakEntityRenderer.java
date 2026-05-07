package net.droingo.outbackkayak.client.render;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
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
        poseStack.push();

        float renderYaw = MathHelper.lerpAngleDegrees(partialTick, entity.prevYaw, entity.getYaw());

        // Model was backwards, so we use -renderYaw.
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-renderYaw));

        super.render(entity, 0.0f, partialTick, poseStack, bufferSource, packedLight);

        poseStack.pop();
    }
}