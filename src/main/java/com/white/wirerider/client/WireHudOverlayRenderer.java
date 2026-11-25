package com.white.wirerider.client;

import com.white.wirerider.WireRiderConfig;
import com.white.wirerider.WireRiderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Create-styled brass hazard badge that sits beside the crosshair in 1st person
 * and smoothly glides to attach to the wrench in 3rd person.
 */
public class WireHudOverlayRenderer {
    // 13x13 Create-style chamfered brass hazard badge
    // '.' transparent, 'O' dark shadow outline, 'H' bright brass highlight, 'B' brass mid, 'D' brass shade,
    // 'C' inner core, '!' exclamation mark
    private static final String[] BADGE_SPRITE = {
        "...OOOOOOO...",
        "..OHHHHHBDO..",
        ".OHCCCCCCBDO.",
        ".OHCC!!CCBDO.",
        "OHCCC!!CCCBDO",
        "OHCCC!!CCCBDO",
        "OBCCC!!CCCBDO",
        "OBCCCCCCCCBDO",
        "OBCCC!!CCCBDO",
        ".OBCC!!CCBDO.",
        ".OBCCCCCCBDO.",
        "..ODDDDDDDO..",
        "...OOOOOOO..."
    };

    private static float smoothX = 0.0f;
    private static float smoothY = 0.0f;
    private static float smoothScale = 1.0f;
    private static boolean positionInitialized = false;
    private static long lastFrameNanos = 0L;

    private static float projectedWrenchX = 0.0f;
    private static float projectedWrenchY = 0.0f;
    private static boolean hasValidProjection = false;

    public static void updateWorldProjection(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (!WireZiplineClientManager.isRiding()) {
            hasValidProjection = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson()) {
            hasValidProjection = false;
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 worldPos = WireZiplineClientManager.getInterpolatedWireAnchor(partialTick);
        Vec3 camPos = event.getCamera().getPosition();

        Vector4f clip = new Vector4f(
            (float) (worldPos.x - camPos.x),
            (float) (worldPos.y - camPos.y),
            (float) (worldPos.z - camPos.z),
            1.0f
        );

        Matrix4f mv = event.getModelViewMatrix();
        Matrix4f proj = event.getProjectionMatrix();
        clip.mul(mv);
        clip.mul(proj);

        if (clip.w <= 0.05f) {
            hasValidProjection = false;
            return;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();

        projectedWrenchX = (ndcX * 0.5f + 0.5f) * guiW + 10.0f;
        projectedWrenchY = (0.5f - ndcY * 0.5f) * guiH - 6.0f;
        hasValidProjection = true;
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!WireZiplineClientManager.isRiding() || !WireRiderConfig.CLIENT.showHudOverlay.get()) {
            positionInitialized = false;
            hasValidProjection = false;
            lastFrameNanos = 0L;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        boolean energized = WireZiplineClientManager.isEnergized();
        boolean groundWarn = WireZiplineClientManager.hasGroundArcWarning();
        float stall = WireZiplineClientManager.getStallCharge();
        boolean hot = WireZiplineClientManager.getWireTemp() >= WireRiderUtils.getWireBurnTemp();

        if (!energized && !hot && !groundWarn && stall <= 0.01f) {
            positionInitialized = false;
            return;
        }

        int guiW = guiGraphics.guiWidth();
        int guiH = guiGraphics.guiHeight();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        float targetX = guiW / 2.0f - 24.0f;
        float targetY = guiH / 2.0f - 6.5f;
        float targetScale = 1.0f;

        if (!firstPerson && hasValidProjection) {
            targetX = Mth.clamp(projectedWrenchX, 6.0f, guiW - 20.0f);
            targetY = Mth.clamp(projectedWrenchY, 6.0f, guiH - 22.0f);
            targetScale = 0.92f;
        }

        long now = System.nanoTime();
        double dt = (lastFrameNanos == 0L) ? 0.016 : Math.min(0.1, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;

        if (!positionInitialized) {
            smoothX = targetX;
            smoothY = targetY;
            smoothScale = targetScale;
            positionInitialized = true;
        } else {
            float alpha = 1.0f - (float) Math.exp(-15.0 * dt);
            smoothX = Mth.lerp(alpha, smoothX, targetX);
            smoothY = Mth.lerp(alpha, smoothY, targetY);
            smoothScale = Mth.lerp(alpha, smoothScale, targetScale);
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float time = mc.player.tickCount + partialTick;

        boolean critical = groundWarn || hot || stall > 0.25f;
        // High-contrast smooth pulse so danger flashing is immediately readable
        float pulseSpeed = critical ? 0.85f : 0.30f;
        float pulse = 0.5f + 0.5f * Mth.sin(time * pulseSpeed);

        int outlineCol = critical && pulse > 0.55f ? 0xFF5A0A06 : 0xEE181310;
        int brassHi = critical ? lerpColor(0xFFE8BA68, 0xFFFFE58A, pulse) : 0xFFE6B968;
        int brassMid = critical ? lerpColor(0xFFC78B38, 0xFFE86A24, pulse) : 0xFFC78E3B;
        int brassDark = critical ? lerpColor(0xFF8A541C, 0xFFA62814, pulse) : 0xFF8A5822;

        int coreCol;
        int markCol;
        if (critical) {
            // Alternate sharply between deep slate-red and vivid glowing crimson/orange
            coreCol = lerpColor(0xFF2B110E, 0xFFE62B1E, pulse);
            markCol = lerpColor(0xFFFFD8A8, 0xFFFFFFFF, pulse);
        } else {
            // Live wire baseline: dark Create slate with warm pulsing amber-gold alert
            coreCol = lerpColor(0xFF211B16, 0xFF523212, pulse * 0.7f);
            markCol = lerpColor(0xFFFFB830, 0xFFFFF0A8, pulse);
        }

        float shakeX = (critical && pulse > 0.6f) ? Mth.sin(time * 2.3f) * 0.6f : 0.0f;
        float shakeY = (critical && pulse > 0.6f) ? Mth.cos(time * 2.9f) * 0.6f : 0.0f;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(smoothX + shakeX, smoothY + shakeY, 0.0f);
        guiGraphics.pose().scale(smoothScale, smoothScale, 1.0f);

        // Outer warning glow halo when pulsing critically
        if (critical && pulse > 0.45f) {
            int glowAlpha = (int) ((pulse - 0.45f) / 0.55f * 130.0f);
            int glowCol = (glowAlpha << 24) | 0xFF3B1E;
            guiGraphics.fill(2, -1, 11, 14, glowCol);
            guiGraphics.fill(-1, 2, 14, 11, glowCol);
        }

        for (int row = 0; row < BADGE_SPRITE.length; row++) {
            String line = BADGE_SPRITE[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == '.') continue;
                int color = switch (c) {
                    case 'O' -> outlineCol;
                    case 'H' -> brassHi;
                    case 'B' -> brassMid;
                    case 'D' -> brassDark;
                    case 'C' -> coreCol;
                    case '!' -> markCol;
                    default -> 0;
                };
                guiGraphics.fill(col, row, col + 1, row + 1, color);
            }
        }

        // Compact stall-buildup bar tucked right under the brass badge
        if (stall > 0.03f) {
            int barW = 11;
            int fillW = Math.max(1, Math.round(barW * Mth.clamp(stall, 0.0f, 1.0f)));
            int barCol = lerpColor(0xFFFFB830, 0xFFFF2E1F, stall);
            guiGraphics.fill(0, 14, 13, 17, 0xDD181310);
            guiGraphics.fill(1, 15, 1 + fillW, 16, barCol);
        }

        guiGraphics.pose().popPose();
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int aA = (a >>> 24) & 0xFF, aR = (a >>> 16) & 0xFF, aG = (a >>> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF, bR = (b >>> 16) & 0xFF, bG = (b >>> 8) & 0xFF, bB = b & 0xFF;
        int rA = Math.round(Mth.lerp(t, aA, bA));
        int rR = Math.round(Mth.lerp(t, aR, bR));
        int rG = Math.round(Mth.lerp(t, aG, bG));
        int rB = Math.round(Mth.lerp(t, aB, bB));
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }
}
