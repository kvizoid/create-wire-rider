package com.white.wirerider.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.white.wirerider.WireRiderConfig;
import com.white.wirerider.WireRiderUtils;
import com.white.wirerider.compat.cee.CEEClientWireHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders a sleek, Create-styled brass highlight fade around the grab point and a pulsing clamp ring
 * when aiming at any ridable wire with a wrench.
 */
public final class WireTargetHighlightRenderer {
    private static final double HIGHLIGHT_RADIUS = 2.6;

    private WireTargetHighlightRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!WireRiderConfig.CLIENT.enableWireHighlight.get()) return;
        if (WireZiplineClientManager.isRiding()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (mc.player.isShiftKeyDown() || !WireRiderUtils.hasWrench(mc.player)) return;

        CEEClientWireHelper.HighlightTarget target = CEEClientWireHelper.getTargetedHighlight(mc.level);
        if (target == null || target.points().size() < 2) return;

        Vec3 camPos = event.getCamera().getPosition();
        Vec3 grab = target.grabPoint();
        List<Vec3> pts = target.points();

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float time = mc.player.tickCount + partialTick;
        float pulse = 0.5f + 0.5f * Mth.sin(time * 0.25f);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f mat = ps.last().pose();

        // 1. Soft Create-brass glow fading gently along ~2.6 blocks on each side of the grab point
        Vec3 grabTangent = new Vec3(0, 0, 1);
        double minSegDistSq = Double.MAX_VALUE;
        for (int i = 0; i < pts.size() - 1; i++) {
            Vec3 a = pts.get(i);
            Vec3 b = pts.get(i + 1);
            Vec3 mid = a.add(b).scale(0.5);
            double dSq = mid.distanceToSqr(grab);
            if (dSq < minSegDistSq) {
                minSegDistSq = dSq;
                Vec3 diff = b.subtract(a);
                if (diff.lengthSqr() > 1e-6) {
                    grabTangent = diff.normalize();
                }
            }

            double dA = a.distanceTo(grab);
            double dB = b.distanceTo(grab);
            if (dA > HIGHLIGHT_RADIUS && dB > HIGHLIGHT_RADIUS) continue;

            float wA = (float) Mth.clamp(1.0 - dA / HIGHLIGHT_RADIUS, 0.0, 1.0);
            float wB = (float) Mth.clamp(1.0 - dB / HIGHLIGHT_RADIUS, 0.0, 1.0);
            int alphaA = Math.round(wA * (135 + pulse * 45));
            int alphaB = Math.round(wB * (135 + pulse * 45));
            if (alphaA < 4 && alphaB < 4) continue;

            drawRibbon(vc, mat, a, b, 0.022f,
                235, 178, 72, alphaA,
                235, 178, 72, alphaB);
        }

        // 2. Crisp pulsing brass diamond ring perpendicular to the wire at the grab point
        Vec3 upRef = Math.abs(grabTangent.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = grabTangent.cross(upRef).normalize();
        Vec3 v = grabTangent.cross(u).normalize();

        double ringRadius = 0.095 + pulse * 0.022;
        int ringAlpha = Math.round(170 + pulse * 65);
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            double ang0 = (Math.PI * 2.0 * i) / steps + time * 0.05;
            double ang1 = (Math.PI * 2.0 * (i + 1)) / steps + time * 0.05;
            Vec3 p0 = grab.add(u.scale(Math.cos(ang0) * ringRadius)).add(v.scale(Math.sin(ang0) * ringRadius));
            Vec3 p1 = grab.add(u.scale(Math.cos(ang1) * ringRadius)).add(v.scale(Math.sin(ang1) * ringRadius));
            drawRibbon(vc, mat, p0, p1, 0.014f,
                255, 224, 130, ringAlpha,
                255, 224, 130, ringAlpha);
        }

        buffers.endBatch(RenderType.lightning());
        ps.popPose();
    }

    private static void drawRibbon(
        VertexConsumer vc,
        Matrix4f mat,
        Vec3 a, Vec3 b,
        float halfThickness,
        int rA, int gA, int bA, int aA,
        int rB, int gB, int bB, int aB
    ) {
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z;
        float bx = (float) b.x, by = (float) b.y, bz = (float) b.z;

        vc.addVertex(mat, ax, ay - halfThickness, az).setColor(rA, gA, bA, aA);
        vc.addVertex(mat, ax, ay + halfThickness, az).setColor(rA, gA, bA, aA);
        vc.addVertex(mat, bx, by + halfThickness, bz).setColor(rB, gB, bB, aB);
        vc.addVertex(mat, bx, by - halfThickness, bz).setColor(rB, gB, bB, aB);

        vc.addVertex(mat, ax - halfThickness, ay, az - halfThickness).setColor(rA, gA, bA, aA);
        vc.addVertex(mat, ax + halfThickness, ay, az + halfThickness).setColor(rA, gA, bA, aA);
        vc.addVertex(mat, bx + halfThickness, by, bz + halfThickness).setColor(rB, gB, bB, aB);
        vc.addVertex(mat, bx - halfThickness, by, bz - halfThickness).setColor(rB, gB, bB, aB);
    }
}
