package com.white.wirerider.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CClient;
import com.white.wirerider.WireRiderUtils;
import com.white.wirerider.compat.cee.CEEClientWireHelper;
import com.white.wirerider.compat.cee.CEECompat;
import com.white.wirerider.network.WireInspectRequestPayload;
import com.white.wirerider.network.WireInspectStatusPayload;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Create Engineer's Goggles telemetry overlay tethered directly to the looked-at wire in 3D space.
 */
public final class WireGogglesOverlayRenderer {
    private static CEEClientWireHelper.LookedAtWire lookedAtWire = null;
    private static InWorldNodeConnection statusConn = null;
    private static boolean statusEnergized = false;
    private static boolean statusInsulated = false;
    private static boolean statusOvervolted = false;
    private static float statusVoltage = 0.0f;
    private static float statusCurrent = 0.0f;
    private static float statusTemp = 20.0f;

    private static int requestTicker = 0;
    private static int hoverTicks = 0;

    private static float projectedX = 0.0f;
    private static float projectedY = 0.0f;
    private static boolean hasValidProjection = false;

    private static float smoothBoxX = 0.0f;
    private static float smoothBoxY = 0.0f;
    private static boolean boxPosInitialized = false;
    private static long lastFrameNanos = 0L;
    private static ItemStack gogglesIcon = null;

    private static ItemStack getGogglesIcon() {
        if (gogglesIcon == null) {
            gogglesIcon = new ItemStack(
                BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "goggles"))
            );
        }
        return gogglesIcon;
    }

    private WireGogglesOverlayRenderer() {}

    public static void reset() {
        lookedAtWire = null;
        statusConn = null;
        hasValidProjection = false;
        boxPosInitialized = false;
        hoverTicks = 0;
        requestTicker = 0;
        lastFrameNanos = 0L;
    }

    public static void updateInspectStatus(WireInspectStatusPayload payload) {
        InWorldNodeConnection conn = CEECompat.connectionFromTag(payload.wireTag());
        if (conn == null || lookedAtWire == null || !conn.equals(lookedAtWire.connection())) {
            return;
        }
        statusConn = conn;
        statusEnergized = payload.energized();
        statusInsulated = payload.insulated();
        statusOvervolted = payload.overvolted();
        statusVoltage = payload.voltage();
        statusCurrent = payload.current();
        statusTemp = payload.temperature();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) {
            reset();
            return;
        }

        if (!GogglesItem.isWearingGoggles(mc.player)) {
            reset();
            return;
        }

        double maxRange = Math.max(6.0, mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 2.0);
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            double blockDist = mc.hitResult.getLocation().distanceTo(mc.player.getEyePosition());
            maxRange = Math.min(maxRange, blockDist + 0.35);
        }

        CEEClientWireHelper.LookedAtWire found = CEEClientWireHelper.findLookedAtWire(mc.player, mc.level, maxRange);
        if (found == null) {
            lookedAtWire = null;
            statusConn = null;
            hasValidProjection = false;
            boxPosInitialized = false;
            hoverTicks = 0;
            requestTicker = 0;
            return;
        }

        boolean switchedWire = (lookedAtWire == null || !found.connection().equals(lookedAtWire.connection()));
        lookedAtWire = found;
        hoverTicks++;

        if (switchedWire) {
            statusConn = null;
            requestTicker = 0;
        }

        if (requestTicker++ % 5 == 0) {
            PacketDistributor.sendToServer(new WireInspectRequestPayload(CEECompat.tagFromConnection(found.connection())));
        }
    }

    public static void updateWorldProjection(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (lookedAtWire == null) {
            hasValidProjection = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Vec3 worldPos = lookedAtWire.hitPoint();
        Vec3 camPos = event.getCamera().getPosition();

        Vector4f clip = new Vector4f(
            (float) (worldPos.x - camPos.x),
            (float) (worldPos.y - camPos.y),
            (float) (worldPos.z - camPos.z),
            1.0f
        );

        Matrix4f mv = event.getModelViewMatrix();
        Matrix4f proj = event.getProjectionMatrix();
        mv.transform(clip);
        proj.transform(clip);

        if (clip.w <= 0.05f) {
            hasValidProjection = false;
            return;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();

        projectedX = (ndcX * 0.5f + 0.5f) * guiW;
        projectedY = (0.5f - ndcY * 0.5f) * guiH;
        hasValidProjection = true;
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (lookedAtWire == null || !hasValidProjection) return;
        if (statusConn == null || !statusConn.equals(lookedAtWire.connection())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (!GogglesItem.isWearingGoggles(mc.player)) return;

        List<FormattedText> lines = buildTooltipLines();
        int maxTextWidth = 0;
        for (FormattedText line : lines) {
            maxTextWidth = Math.max(maxTextWidth, mc.font.width(line));
        }

        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();
        int boxW = maxTextWidth + 24;
        int boxH = lines.size() * 10 + 12;

        // Place tooltip neatly right-above (or left if near right edge) of the wire point
        boolean placeLeft = (projectedX + 22 + boxW > screenW - 8);
        float targetX = placeLeft ? (projectedX - boxW - 14.0f) : (projectedX + 20.0f);
        float targetY = projectedY - boxH * 0.5f;

        targetX = Mth.clamp(targetX, 18.0f, Math.max(18.0f, screenW - boxW - 8.0f));
        targetY = Mth.clamp(targetY, 10.0f, Math.max(10.0f, screenH - boxH - 10.0f));

        long now = System.nanoTime();
        double dt = (lastFrameNanos == 0L) ? 0.016 : Math.min(0.1, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;

        if (!boxPosInitialized) {
            smoothBoxX = targetX;
            smoothBoxY = targetY;
            boxPosInitialized = true;
        } else {
            float alpha = 1.0f - (float) Math.exp(-18.0 * dt);
            smoothBoxX = Mth.lerp(alpha, smoothBoxX, targetX);
            smoothBoxY = Mth.lerp(alpha, smoothBoxY, targetY);
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float fade = Mth.clamp((hoverTicks + partialTick) / 8.0f, 0.0f, 1.0f);

        CClient cfg = AllConfigs.client();
        boolean custom = cfg.overlayCustomColor.get();
        Color background = custom
            ? new Color(cfg.overlayBackgroundColor.get())
            : BoxElement.COLOR_VANILLA_BACKGROUND.scaleAlpha(0.75f);
        Color borderTop = custom
            ? new Color(cfg.overlayBorderColorTop.get())
            : BoxElement.COLOR_VANILLA_BORDER.getFirst().copy();
        Color borderBot = custom
            ? new Color(cfg.overlayBorderColorBot.get())
            : BoxElement.COLOR_VANILLA_BORDER.getSecond().copy();

        if (fade < 1.0f) {
            background = background.scaleAlpha(fade);
            borderTop = borderTop.scaleAlpha(fade);
            borderBot = borderBot.scaleAlpha(fade);
        }

        int drawX = Math.round(smoothBoxX);
        int drawY = Math.round(smoothBoxY);
        int anchorX = Math.round(Mth.clamp(projectedX, 4.0f, screenW - 4.0f));
        int anchorY = Math.round(Mth.clamp(projectedY, 4.0f, screenH - 4.0f));

        // Tether point + subtle brass connector line from wire to tooltip box
        int tetherCol = statusEnergized ? 0xFFE5AA42 : 0xFF9C8E7A;
        int tetherAlpha = Math.round(fade * 210.0f);
        int lineCol = (tetherAlpha << 24) | (tetherCol & 0x00FFFFFF);

        int boxEdgeX = placeLeft ? (drawX + maxTextWidth + 6) : (drawX - 10);
        int boxEdgeY = drawY + boxH / 2 - 4;
        drawTether(guiGraphics, anchorX, anchorY, boxEdgeX, boxEdgeY, lineCol);

        // Anchor dot on the wire
        guiGraphics.fill(anchorX - 2, anchorY - 2, anchorX + 2, anchorY + 2, (tetherAlpha << 24) | 0x181310);
        guiGraphics.fill(anchorX - 1, anchorY - 1, anchorX + 1, anchorY + 1, lineCol);

        guiGraphics.pose().pushPose();
        RemovedGuiUtils.drawHoveringText(
            guiGraphics,
            lines,
            drawX - 12,
            drawY,
            screenW,
            screenH,
            -1,
            background.getRGB(),
            borderTop.getRGB(),
            borderBot.getRGB(),
            mc.font
        );

        // Render Create Goggles icon in front of the tooltip box (drawHoveringText draws at z=400)
        guiGraphics.pose().translate(0.0f, 0.0f, 500.0f);
        guiGraphics.renderFakeItem(getGogglesIcon(), drawX - 18, drawY - 6);
        guiGraphics.pose().popPose();
    }

    private static void drawTether(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i += 2) {
            float t = (float) i / steps;
            int px = Math.round(Mth.lerp(t, x0, x1));
            int py = Math.round(Mth.lerp(t, y0, y1));
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static List<FormattedText> buildTooltipLines() {
        List<FormattedText> lines = new ArrayList<>();
        lines.add(Component.literal("    ").append(
            Component.translatable("goggles.create_wire_rider.header").withStyle(ChatFormatting.WHITE)
        ));

        String voltageStr = (statusVoltage >= 1000.0f)
            ? String.format(Locale.ROOT, "%.2f kV", statusVoltage / 1000.0f)
            : String.format(Locale.ROOT, "%.0f V", statusVoltage);
        ChatFormatting voltColor = (statusVoltage > WireRiderUtils.getLiveVoltageThreshold())
            ? (statusEnergized ? ChatFormatting.GOLD : ChatFormatting.AQUA)
            : ChatFormatting.GRAY;

        lines.add(Component.literal("    ")
            .append(Component.translatable("goggles.create_wire_rider.voltage").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" "))
            .append(Component.literal(voltageStr).withStyle(voltColor)));

        String currentStr = String.format(Locale.ROOT, "%.1f A", statusCurrent);
        ChatFormatting ampColor = statusCurrent > 0.1f ? ChatFormatting.AQUA : ChatFormatting.GRAY;
        lines.add(Component.literal("    ")
            .append(Component.translatable("goggles.create_wire_rider.current").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" "))
            .append(Component.literal(currentStr).withStyle(ampColor)));

        String tempStr = String.format(Locale.ROOT, "%d°C", Math.round(statusTemp));
        ChatFormatting tempColor = statusTemp >= WireRiderUtils.getWireBurnTemp()
            ? ChatFormatting.RED
            : (statusTemp >= 45.0f ? ChatFormatting.GOLD : ChatFormatting.GREEN);
        lines.add(Component.literal("    ")
            .append(Component.translatable("goggles.create_wire_rider.temperature").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" "))
            .append(Component.literal(tempStr).withStyle(tempColor)));

        Component insState;
        if (statusOvervolted) {
            insState = Component.translatable("goggles.create_wire_rider.insulation.overvolted").withStyle(ChatFormatting.RED);
        } else if (statusInsulated) {
            insState = Component.translatable("goggles.create_wire_rider.insulation.safe").withStyle(ChatFormatting.GREEN);
        } else {
            insState = Component.translatable("goggles.create_wire_rider.insulation.bare")
                .withStyle(statusEnergized ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
        }
        lines.add(Component.literal("    ")
            .append(Component.translatable("goggles.create_wire_rider.insulation").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" "))
            .append(insState));

        return lines;
    }
}
