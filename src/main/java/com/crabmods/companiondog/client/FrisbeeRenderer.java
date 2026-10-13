package com.crabmods.companiondog.client;

import com.crabmods.companiondog.entity.FrisbeeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** World-oriented disc, never a camera-facing projectile sprite. */
public final class FrisbeeRenderer extends EntityRenderer<FrisbeeEntity> {
    private final ItemRenderer items;

    public FrisbeeRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
        shadowRadius = 0.2F;
    }

    @Override
    public void render(FrisbeeEntity disc, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial, disc.yRotO, disc.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.clamp(Mth.lerp(partial, disc.xRotO, disc.getXRot()), -25, 25)));
        pose.mulPose(Axis.YP.rotationDegrees((disc.tickCount + partial) * 24));
        items.renderStatic(disc.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, disc.level(), disc.getId());
        pose.popPose();
        super.render(disc, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FrisbeeEntity disc) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
