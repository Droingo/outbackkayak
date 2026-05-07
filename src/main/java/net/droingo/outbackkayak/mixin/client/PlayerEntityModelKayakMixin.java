package net.droingo.outbackkayak.mixin.client;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelKayakMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart leftPants;

    @Shadow
    @Final
    public ModelPart rightPants;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void outbackKayak$hidePantsWhenRidingKayak(
            T entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (entity.getVehicle() instanceof KayakEntity) {
            this.leftPants.visible = false;
            this.rightPants.visible = false;
        } else {
            this.leftPants.visible = true;
            this.rightPants.visible = true;
        }
    }
}