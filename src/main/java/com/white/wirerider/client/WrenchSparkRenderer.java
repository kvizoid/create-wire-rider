package com.white.wirerider.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.white.wirerider.WireRiderConfig;
import com.white.wirerider.WireRiderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Renders crisp, viewmodel-locked electric sparks right between the jaws of the held wrench in 1st person.
 */
public final class WrenchSparkRenderer {
    private static final Random RANDOM = new Random();

    private WrenchSparkRenderer() {}

    public static void renderHandSparks(RenderHandEvent event) {
        if (!WireZiplineClientManager.isRiding()) return;
        if (!WireRiderConfig.CLIENT.enableParticles.get()) return;

        boolean energized = WireZiplineClientManager.isEnergized();
        boolean hot = WireZiplineClientManager.getWireTemp() >= WireRiderUtils.getWireBurnTemp();
        if (!energized && !hot) return;

        ItemStack stack = event.getItemStack();
        if (!WireRiderUtils.isWrench(stack)) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // Only spark on the active riding wrench hand (main hand takes priority, matching WireRiderUtils)
        if (event.getHand() == InteractionHand.OFF_HAND && WireRiderUtils.isWrench(player.getMainHandItem())) {
            return;
        }

        HumanoidArm arm = (event.getHand() == InteractionHand.MAIN_HAND)
            ? player.getMainArm()
            : player.getMainArm().getOpposite();
        boolean leftHand = (arm == HumanoidArm.LEFT);
        int side = leftHand ? -1 : 1;

        PoseStack ps = event.getPoseStack();
        ps.pushPose();

        // Reproduce ItemInHandRenderer.renderArmWithItem transforms into item space
        float equip = event.getEquipProgress();
        float swing = event.getSwingProgress();
        float sqrtSwing = Mth.sqrt(swing);

        if (swing > 0.0f) {
            float swingX = -0.4f * Mth.sin(sqrtSwing * (float) Math.PI);
            float swingY = 0.2f * Mth.sin(sqrtSwing * ((float) Math.PI * 2F));
            float swingZ = -0.2f * Mth.sin(swing * (float) Math.PI);
            ps.translate(side * swingX, swingY, swingZ);
        }

        ps.translate(side * 0.56f, -0.52f + equip * -0.6f, -0.72f);

        if (swing > 0.0f) {
            float rotSwing = Mth.sin(swing * swing * (float) Math.PI);
            float rotSwingSqrt = Mth.sin(sqrtSwing * (float) Math.PI);
            ps.mulPose(Axis.YP.rotationDegrees(side * (45.0f + rotSwing * -20.0f)));
            ps.mulPose(Axis.ZP.rotationDegrees(side * rotSwingSqrt * -20.0f));
            ps.mulPose(Axis.XP.rotationDegrees(rotSwingSqrt * -80.0f));
            ps.mulPose(Axis.YP.rotationDegrees(side * -45.0f));
        }

        ItemDisplayContext ctx = leftHand
            ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, player, player.getId());
        model.getTransforms().getTransform(ctx).apply(leftHand, ps);

        // Model space is centered at (0,0,0) with extent [-0.5 .. +0.5].
        // Create wrench jaws sit at: upper jaw y≈+0.40, lower jaw y≈+0.28, x≈+0.12..+0.19, z≈0.
        long seed = (player.tickCount / 2L) * 31L + (leftHand ? 17L : 7L);
        RANDOM.setSeed(seed);

        float speedRatio = (float) Mth.clamp(WireZiplineClientManager.getCurrentSpeed() / 1.4, 0.25, 1.0);
        float snapBoost = WireZiplineClientManager.getMountSnapBoost();
        int arcCount = energized ? (2 + Math.round(speedRatio * 2.0f + snapBoost * 4.0f)) : 2;

        MultiBufferSource buffer = event.getMultiBufferSource();
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f mat = ps.last().pose();

        for (int i = 0; i < arcCount; i++) {
            float x0 = 0.11f + (RANDOM.nextFloat() - 0.5f) * 0.08f;
            float y0 = 0.28f + RANDOM.nextFloat() * 0.03f;
            float z0 = (RANDOM.nextFloat() - 0.5f) * 0.07f;

            float x1 = 0.13f + (RANDOM.nextFloat() - 0.5f) * 0.10f;
            float y1 = 0.38f + RANDOM.nextFloat() * 0.04f;
            float z1 = (RANDOM.nextFloat() - 0.5f) * 0.07f;

            float mx = (x0 + x1) * 0.5f + (RANDOM.nextFloat() - 0.5f) * 0.06f;
            float my = (y0 + y1) * 0.5f + (RANDOM.nextFloat() - 0.5f) * 0.03f;
            float mz = (z0 + z1) * 0.5f + (RANDOM.nextFloat() - 0.5f) * 0.06f;

            if (energized) {
                int r = (snapBoost > 0.2f) ? 210 : 140;
                int g = (snapBoost > 0.2f) ? 245 : 235;
                int b = 255;
                drawBeamQuad(vc, mat, x0, y0, z0, mx, my, mz, 0.008f + snapBoost * 0.004f, r, g, b, 210);
                drawBeamQuad(vc, mat, mx, my, mz, x1, y1, z1, 0.008f + snapBoost * 0.004f, 255, 255, 255, 230);
            } else {
                drawBeamQuad(vc, mat, x0, y0, z0, mx, my, mz, 0.010f, 255, 110, 30, 190);
            }
        }

        // Tiny glowing spark flecks flying off the jaws
        int flecks = energized ? (3 + Math.round(speedRatio * 3.0f)) : 2;
        for (int i = 0; i < flecks; i++) {
            float fx = 0.14f + (RANDOM.nextFloat() - 0.5f) * 0.18f;
            float fy = 0.34f + (RANDOM.nextFloat() - 0.5f) * 0.16f;
            float fz = (RANDOM.nextFloat() - 0.5f) * 0.14f;
            float s = 0.009f + RANDOM.nextFloat() * 0.007f;
            if (energized) {
                drawSparkDiamond(vc, mat, fx, fy, fz, s, 210, 248, 255, 220);
            } else {
                drawSparkDiamond(vc, mat, fx, fy, fz, s, 255, 165, 50, 200);
            }
        }

        ps.popPose();
    }

    private static void drawBeamQuad(
        VertexConsumer vc,
        Matrix4f mat,
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float halfWidth,
        int r, int g, int b, int a
    ) {
        vc.addVertex(mat, x0 - halfWidth, y0, z0 - halfWidth).setColor(r, g, b, a);
        vc.addVertex(mat, x0 + halfWidth, y0, z0 + halfWidth).setColor(r, g, b, a);
        vc.addVertex(mat, x1 + halfWidth, y1, z1 + halfWidth).setColor(r, g, b, a);
        vc.addVertex(mat, x1 - halfWidth, y1, z1 - halfWidth).setColor(r, g, b, a);

        vc.addVertex(mat, x0 - halfWidth, y0, z0 + halfWidth).setColor(r, g, b, a);
        vc.addVertex(mat, x0 + halfWidth, y0, z0 - halfWidth).setColor(r, g, b, a);
        vc.addVertex(mat, x1 + halfWidth, y1, z1 - halfWidth).setColor(r, g, b, a);
        vc.addVertex(mat, x1 - halfWidth, y1, z1 + halfWidth).setColor(r, g, b, a);
    }

    private static void drawSparkDiamond(
        VertexConsumer vc,
        Matrix4f mat,
        float x, float y, float z,
        float s,
        int r, int g, int b, int a
    ) {
        vc.addVertex(mat, x, y + s, z).setColor(r, g, b, a);
        vc.addVertex(mat, x - s, y, z).setColor(r, g, b, a);
        vc.addVertex(mat, x, y - s, z).setColor(r, g, b, a);
        vc.addVertex(mat, x + s, y, z).setColor(r, g, b, a);

        vc.addVertex(mat, x, y + s, z).setColor(r, g, b, a);
        vc.addVertex(mat, x, y, z - s).setColor(r, g, b, a);
        vc.addVertex(mat, x, y - s, z).setColor(r, g, b, a);
        vc.addVertex(mat, x, y, z + s).setColor(r, g, b, a);
    }
}
