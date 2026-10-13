package com.crabmods.companiondog.client;

import com.crabmods.companiondog.client.model.CompanionDogModel;
import com.crabmods.companiondog.client.model.DogAppearance;
import com.crabmods.companiondog.entity.CompanionDogAction;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.entity.CompanionDogPose;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

public final class CompanionDogRenderer extends MobRenderer<CompanionDogEntity, CompanionDogModel> {
    private final DogAppearance appearance;

    public CompanionDogRenderer(EntityRendererProvider.Context context) {
        this(context, DogAppearance.load(context.getResourceManager()));
    }
    private CompanionDogRenderer(EntityRendererProvider.Context context, DogAppearance appearance) {
        super(context, new CompanionDogModel(appearance.bake(), appearance.definition().motionScale()), appearance.definition().hasGeometry() ? 0.3F : 0);
        this.appearance = appearance;
        addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, CompanionDogEntity dog,
                               float swing, float amount, float partial, float age, float yaw, float pitch) {
                if (dog.isTame() && !dog.isInvisible() && appearance.definition().textures().containsKey("collar")) {
                    renderColoredCutoutModel(getParentModel(), appearance.texture("collar"), pose, buffers, light, dog,
                            0xFF000000 | dog.getCollarColor().getTextureDiffuseColor());
                }
                if (dog.getMouthItem().isEmpty() || dog.isInvisible()) return;
                String key = dog.getMouthItem().is(CompanionDogRegistries.TENNIS_BALL.get()) ? "tennis_ball"
                        : dog.getMouthItem().is(CompanionDogRegistries.FRISBEE.get()) ? "frisbee" : "default";
                var socket = appearance.definition().socket(key);
                pose.pushPose();
                getParentModel().translateToMouth(pose);
                pose.translate(socket.offset().x() / 16, socket.offset().y() / 16, socket.offset().z() / 16);
                pose.mulPose(Axis.XP.rotationDegrees(socket.rotation().x()));
                pose.mulPose(Axis.YP.rotationDegrees(socket.rotation().y()));
                pose.mulPose(Axis.ZP.rotationDegrees(socket.rotation().z()));
                float itemScale = socket.scale() / appearance.definition().scale();
                pose.scale(itemScale, itemScale, itemScale);
                context.getItemInHandRenderer().renderItem(dog, dog.getMouthItem(), ItemDisplayContext.GROUND,
                        false, pose, buffers, light);
                pose.popPose();
            }
        });
    }
    @Override
    public void render(CompanionDogEntity dog, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (appearance.definition().hasGeometry()) super.render(dog, yaw, partial, pose, buffers, light);
    }
    @Override
    protected void scale(CompanionDogEntity dog, PoseStack pose, float partial) {
        float scale = appearance.definition().scale();
        pose.scale(scale, scale, scale);
    }
    @Override
    public ResourceLocation getTextureLocation(CompanionDogEntity dog) {
        CompanionDogAction action = dog.getClientAction(0);
        if (dog.poseWeight(CompanionDogPose.SLEEP, 0) > 0.5F || action == CompanionDogAction.YAWN && dog.getClientActionProgress(0) > 0.2F) return appearance.texture("sleep");
        if (action == CompanionDogAction.PET || action == CompanionDogAction.BELLY_RUB || action == CompanionDogAction.SCRATCH || action == CompanionDogAction.EAT || dog.poseWeight(CompanionDogPose.BELLY, 0) > 0.5F) return appearance.texture("happy");
        return appearance.texture(!dog.hasDebugAnimation() && (dog.tickCount + dog.getId() * 31) % 97 < 3 ? "blink" : "normal");
    }
}
