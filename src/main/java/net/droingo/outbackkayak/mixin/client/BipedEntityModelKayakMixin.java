package net.droingo.outbackkayak.mixin.client;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelKayakMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart leftLeg;

    @Shadow
    @Final
    public ModelPart rightLeg;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void outbackKayak$hideLegsWhenRidingKayak(
            T entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (entity.getVehicle() instanceof KayakEntity) {
            this.leftLeg.visible = false;
            this.rightLeg.visible = false;
        } else {
            this.leftLeg.visible = true;
            this.rightLeg.visible = true;
        }
    }
}