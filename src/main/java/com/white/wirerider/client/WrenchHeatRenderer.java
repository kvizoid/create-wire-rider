package com.white.wirerider.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class WrenchHeatRenderer {
    private static final RandomSource RANDOM = RandomSource.create(42L);

    public static void render(
        ItemStack stack,
        ItemDisplayContext displayContext,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource buffer,
        BakedModel model
    ) {
        if (stack.isEmpty() || model == null) return;

        float heat = WireZiplineClientManager.getWrenchHeat();
        if (heat <= 0.01f) return;

        if (!WireZiplineClientManager.isWrench(stack)) return;

        boolean isFirstPerson = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean isThirdPerson = displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        if (!isFirstPerson && !isThirdPerson) return;

        float primaryScale = isFirstPerson ? 1.018f : 1.055f;

        poseStack.pushPose();
        poseStack.scale(primaryScale, primaryScale, primaryScale);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));

        renderHeatedQuads(poseStack, consumer, model.getQuads(null, null, RANDOM), heat, 1.0f);
        for (Direction direction : Direction.values()) {
            renderHeatedQuads(poseStack, consumer, model.getQuads(null, direction, RANDOM), heat, 1.0f);
        }

        if (heat > 0.35f) {
            float bloomScale = isFirstPerson ? 1.015f : 1.035f;
            poseStack.scale(bloomScale, bloomScale, bloomScale);
            float bloomIntensity = (heat - 0.35f) * 0.45f;
            renderHeatedQuads(poseStack, consumer, model.getQuads(null, null, RANDOM), heat, bloomIntensity);
            for (Direction direction : Direction.values()) {
                renderHeatedQuads(poseStack, consumer, model.getQuads(null, direction, RANDOM), heat, bloomIntensity);
            }
        }

        poseStack.popPose();
    }

    private static void renderHeatedQuads(
        PoseStack poseStack,
        VertexConsumer consumer,
        List<BakedQuad> quads,
        float heat,
        float intensityMultiplier
    ) {
        if (quads == null || quads.isEmpty()) return;

        PoseStack.Pose lastPose = poseStack.last();

        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            if (vertices.length < 32) continue;

            float y0 = Float.intBitsToFloat(vertices[1]);
            float y1 = Float.intBitsToFloat(vertices[9]);
            float y2 = Float.intBitsToFloat(vertices[17]);
            float y3 = Float.intBitsToFloat(vertices[25]);
            float avgY = (y0 + y1 + y2 + y3) * 0.25f;

            float x0 = Float.intBitsToFloat(vertices[0]);
            float x1 = Float.intBitsToFloat(vertices[8]);
            float x2 = Float.intBitsToFloat(vertices[16]);
            float x3 = Float.intBitsToFloat(vertices[24]);
            float avgX = (x0 + x1 + x2 + x3) * 0.25f;

            float tipFactor = Math.max(avgY, (avgX + avgY) * 0.55f);
            if (tipFactor < 0.35f) continue;

            float t = Mth.clamp((tipFactor - 0.35f) / 0.55f, 0.0f, 1.0f);

            float r = 1.0f;
            float g = Mth.lerp(t * t, 0.18f, 0.88f);
            float b = Mth.lerp(t * t, 0.01f, 0.28f);

            float baseAlpha = Mth.lerp(t, 0.30f, 0.95f) * heat * intensityMultiplier;
            float a = Mth.clamp(baseAlpha, 0.0f, 1.0f);

            consumer.putBulkData(lastPose, quad, r, g, b, a, 0x00F000F0, OverlayTexture.NO_OVERLAY);
        }
    }
}
