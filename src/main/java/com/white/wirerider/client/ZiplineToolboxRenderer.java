package com.white.wirerider.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.white.wirerider.content.toolbox.ZiplineToolboxEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ZiplineToolboxRenderer extends EntityRenderer<ZiplineToolboxEntity> {
    private static final ItemStack CHAIN_STACK = new ItemStack(Items.CHAIN);

    public ZiplineToolboxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(ZiplineToolboxEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack item = entity.getItem();
        if (item.isEmpty()) return;

        poseStack.pushPose();

        Vec3 tangent = entity.getTangentAt(entity.getProgress());
        if (!entity.isForward()) {
            tangent = tangent.scale(-1.0);
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-tangent.x, tangent.z));
        float pitch = (float) Math.toDegrees(Math.asin(-tangent.y));

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        poseStack.pushPose();
        poseStack.translate(0.0, 0.24, 0.0);
        poseStack.scale(0.55F, 0.55F, 0.55F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            CHAIN_STACK,
            ItemDisplayContext.GROUND,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            entity.level(),
            entity.getId()
        );
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0, -0.05, 0.0);
        poseStack.scale(0.85F, 0.85F, 0.85F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            item,
            ItemDisplayContext.FIXED,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            entity.level(),
            entity.getId()
        );
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ZiplineToolboxEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
