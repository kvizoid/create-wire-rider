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

        boolean inHand = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        if (!inHand) return;

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));

        renderHeatedQuads(poseStack, consumer, model.getQuads(null, null, RANDOM), heat);
        for (Direction direction : Direction.values()) {
            renderHeatedQuads(poseStack, consumer, model.getQuads(null, direction, RANDOM), heat);
        }
    }

    private static void renderHeatedQuads(
        PoseStack poseStack,
        VertexConsumer consumer,
        List<BakedQuad> quads,
        float heat
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
            if (tipFactor < 0.40f) continue;

            float t = Mth.clamp((tipFactor - 0.40f) / 0.50f, 0.0f, 1.0f);

            float r = 1.0f;
            float g = Mth.lerp(t, 0.16f, 0.85f);
            float b = Mth.lerp(t, 0.02f, 0.22f);
            float a = Mth.lerp(t, 0.35f, 0.92f) * heat;

            Direction dir = quad.getDirection();
            float nx = dir.getStepX() * 0.0006f;
            float ny = dir.getStepY() * 0.0006f;
            float nz = dir.getStepZ() * 0.0006f;

            for (int i = 0; i < 4; i++) {
                int offset = i * 8;
                float vx = Float.intBitsToFloat(vertices[offset]) + nx;
                float vy = Float.intBitsToFloat(vertices[offset + 1]) + ny;
                float vz = Float.intBitsToFloat(vertices[offset + 2]) + nz;
                float u = Float.intBitsToFloat(vertices[offset + 4]);
                float v = Float.intBitsToFloat(vertices[offset + 5]);

                consumer.addVertex(lastPose, vx, vy, vz)
                    .setColor(r, g, b, a)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0x00F000F0)
                    .setNormal(lastPose, dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
    }
}
