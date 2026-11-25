package com.white.wirerider.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.white.wirerider.WireRiderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders sleek, crisp 3D electric lightning arcs using midpoint-displaced 3D prisms
 * via {@link RenderType#lightning()}.
 */
public final class WireArcRenderer {
    private WireArcRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!WireRiderConfig.CLIENT.enableParticles.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean riding = WireZiplineClientManager.isRiding();
        boolean energized = WireZiplineClientManager.isEnergized();
        Vec3 groundEnd = WireZiplineClientManager.getGroundArcEnd();
        float groundFade = WireZiplineClientManager.getGroundArcFade();
        boolean groundWater = WireZiplineClientManager.isGroundArcWater();
        Vec3 polePos = WireZiplineClientManager.getNearbyPolePos();
        float snapBoost = WireZiplineClientManager.getMountSnapBoost();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        boolean drawGround = groundEnd != null && groundFade > 0.02f;
        boolean drawPole = riding && energized && polePos != null;
        boolean drawContact = riding && energized && (!firstPerson || snapBoost > 0.05f);

        if (!drawGround && !drawPole && !drawContact) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = event.getCamera().getPosition();
        Vec3 playerPos = mc.player.getPosition(partialTick);
        Vec3 anchorPos = riding ? WireZiplineClientManager.getInterpolatedWireAnchor(partialTick) : playerPos.add(0.0, 1.35, 0.0);

        // ~28 Hz crisp arc snap cadence (36 ms per shape) so lightning looks fluid at high FPS
        long nowNanos = System.nanoTime();
        long phase = nowNanos / 36_000_000L;
        float subPhase = (nowNanos % 36_000_000L) / 36_000_000.0f;
        float flicker = 0.86f + 0.14f * Mth.sin(subPhase * (float) Math.PI);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f mat = ps.last().pose();

        // 1. Ground / water discharge lightning (1 main bolt + 1 thinner secondary over water)
        if (drawGround) {
            int boltCount = groundWater ? 2 : 1;
            for (int b = 0; b < boltCount; b++) {
                long seed = phase * 31L + b * 97L + 17L;
                Vec3 arcStart = playerPos.add(
                    signedRand(seed + 1) * 0.12,
                    0.20 + b * 0.14,
                    signedRand(seed + 3) * 0.12
                );
                Vec3 arcEnd = groundEnd.add(
                    signedRand(seed + 4) * (groundWater ? 0.22 : 0.12),
                    0.02,
                    signedRand(seed + 5) * (groundWater ? 0.22 : 0.12)
                );
                double len = arcStart.distanceTo(arcEnd);
                double maxOffset = Math.min(0.13, len * 0.085);
                float radius = (b == 0) ? 0.010f : 0.0075f;
                float intensity = groundFade * flicker * ((b == 0) ? 1.0f : 0.80f);

                drawBolt(
                    vc, mat,
                    arcStart, arcEnd,
                    2,
                    maxOffset,
                    radius,
                    seed,
                    intensity
                );
            }
        }

        // 2. Pole / insulator arc (only when genuinely close to the pole node)
        if (drawPole && anchorPos.distanceToSqr(polePos) < 2.25) {
            long seed = phase * 53L + 701L;
            double len = anchorPos.distanceTo(polePos);
            drawBolt(
                vc, mat,
                anchorPos, polePos,
                2,
                Math.min(0.11, len * 0.08),
                0.009f,
                seed,
                0.90f * flicker
            );
        }

        // 3. 3rd-person wrench/wire contact arc (compact, tight around the jaws)
        if (drawContact) {
            Vec3 normal = WireZiplineClientManager.getCurrentNormal();
            int wisps = snapBoost > 0.05f ? 2 : 1;
            for (int w = 0; w < wisps; w++) {
                long seed = phase * 43L + w * 89L + 1201L;
                Vec3 dir = new Vec3(
                    signedRand(seed + 1) * 0.75,
                    signedRand(seed + 2) * 0.55 + 0.10,
                    signedRand(seed + 3) * 0.75
                );
                if (normal.lengthSqr() > 0.01) {
                    dir = dir.add(normal.scale(signedRand(seed + 4) * 0.9));
                }
                if (dir.lengthSqr() < 1e-4) dir = new Vec3(0, 1, 0);

                double reach = (0.11 + hash01(seed + 5) * 0.11) * (1.0 + snapBoost * 0.40);
                Vec3 wispEnd = anchorPos.add(dir.normalize().scale(reach));
                float intensity = Mth.clamp((0.80f + snapBoost * 0.22f) * flicker, 0.55f, 1.0f);

                drawBolt(
                    vc, mat,
                    anchorPos, wispEnd,
                    2,
                    reach * 0.08,
                    0.0052f * (1.0f + snapBoost * 0.20f),
                    seed,
                    intensity
                );
            }
        }

        buffers.endBatch(RenderType.lightning());
        ps.popPose();
    }

    private static void drawBolt(
        VertexConsumer vc,
        Matrix4f mat,
        Vec3 start,
        Vec3 end,
        int depth,
        double initialOffset,
        float coreRadius,
        long seed,
        float intensity
    ) {
        if (start.distanceToSqr(end) < 0.002) return;

        Vec3 dir = end.subtract(start).normalize();
        Vec3 upRef = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 basisU = dir.cross(upRef).normalize();
        Vec3 basisV = dir.cross(basisU).normalize();

        List<Vec3> pts = new ArrayList<>((1 << depth) + 1);
        pts.add(start);
        subdivideBolt(pts, start, end, basisU, basisV, initialOffset, depth, seed, 1L);
        pts.add(end);

        int outerAlpha = Mth.clamp(Math.round(92 * intensity), 8, 255);
        int coreAlpha = Mth.clamp(Math.round(235 * intensity), 18, 255);
        drawPrismChain(vc, mat, pts, coreRadius * 1.95f, 75, 180, 255, outerAlpha);
        drawPrismChain(vc, mat, pts, coreRadius, 240, 252, 255, coreAlpha);
    }

    private static void subdivideBolt(
        List<Vec3> out,
        Vec3 a,
        Vec3 b,
        Vec3 basisU,
        Vec3 basisV,
        double maxOffset,
        int depth,
        long seed,
        long nodeId
    ) {
        if (depth <= 0) return;

        long s = seed + nodeId * 1315423911L;
        Vec3 mid = a.lerp(b, 0.5)
            .add(basisU.scale(signedRand(s + 1) * maxOffset))
            .add(basisV.scale(signedRand(s + 2) * maxOffset));

        subdivideBolt(out, a, mid, basisU, basisV, maxOffset * 0.50, depth - 1, seed, nodeId * 2L);
        out.add(mid);
        subdivideBolt(out, mid, b, basisU, basisV, maxOffset * 0.50, depth - 1, seed, nodeId * 2L + 1L);
    }

    private static void drawPrismChain(
        VertexConsumer vc,
        Matrix4f mat,
        List<Vec3> pts,
        float radius,
        int r, int g, int b, int alpha
    ) {
        int segs = pts.size() - 1;
        for (int i = 0; i < segs; i++) {
            Vec3 p0 = pts.get(i);
            Vec3 p1 = pts.get(i + 1);
            Vec3 seg = p1.subtract(p0);
            double segLen = seg.length();
            if (segLen < 1e-4) continue;

            Vec3 dir = seg.scale(1.0 / segLen);
            Vec3 upRef = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u = dir.cross(upRef).normalize();
            Vec3 v = dir.cross(u).normalize();

            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float r0 = radius * Mth.lerp(t0, 1.0f, 0.52f);
            float r1 = radius * Mth.lerp(t1, 1.0f, 0.52f);

            int a0 = Mth.clamp(Math.round(alpha * Mth.lerp(t0, 1.0f, 0.60f)), 6, 255);
            int a1 = Mth.clamp(Math.round(alpha * Mth.lerp(t1, 1.0f, 0.60f)), 6, 255);

            Vec3 u0 = u.scale(r0);
            Vec3 v0 = v.scale(r0);
            Vec3 u1 = u.scale(r1);
            Vec3 v1 = v.scale(r1);

            Vec3 c00 = p0.subtract(u0).subtract(v0);
            Vec3 c01 = p0.add(u0).subtract(v0);
            Vec3 c02 = p0.add(u0).add(v0);
            Vec3 c03 = p0.subtract(u0).add(v0);

            Vec3 c10 = p1.subtract(u1).subtract(v1);
            Vec3 c11 = p1.add(u1).subtract(v1);
            Vec3 c12 = p1.add(u1).add(v1);
            Vec3 c13 = p1.subtract(u1).add(v1);

            addQuad(vc, mat, c00, c01, c11, c10, r, g, b, a0, a1);
            addQuad(vc, mat, c01, c02, c12, c11, r, g, b, a0, a1);
            addQuad(vc, mat, c02, c03, c13, c12, r, g, b, a0, a1);
            addQuad(vc, mat, c03, c00, c10, c13, r, g, b, a0, a1);
        }
    }

    private static void addQuad(
        VertexConsumer vc,
        Matrix4f mat,
        Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
        int r, int g, int b, int a0, int a1
    ) {
        vc.addVertex(mat, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r, g, b, a0);
        vc.addVertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, a0);
        vc.addVertex(mat, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, a1);
        vc.addVertex(mat, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, a1);
    }

    private static double hash01(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return ((z >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    private static double signedRand(long seed) {
        return hash01(seed) * 2.0 - 1.0;
    }
}
