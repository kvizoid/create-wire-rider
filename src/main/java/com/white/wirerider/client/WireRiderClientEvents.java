package com.white.wirerider.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "create_wire_rider", value = Dist.CLIENT)
public class WireRiderClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        WireZiplineClientManager.tick();
        WireGogglesOverlayRenderer.tick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        WireTargetHighlightRenderer.render(event);
        WireHudOverlayRenderer.updateWorldProjection(event);
        WireGogglesOverlayRenderer.updateWorldProjection(event);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        WireHudOverlayRenderer.render(event.getGuiGraphics(), event.getPartialTick());
        WireGogglesOverlayRenderer.render(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        WrenchSparkRenderer.renderHandSparks(event);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float roll = WireZiplineClientManager.getCameraRoll(event.getPartialTick());
        if (roll != 0.0f) {
            event.setRoll(event.getRoll() + roll);
        }
        float pitchOffset = WireZiplineClientManager.getCameraPitchOffset(event.getPartialTick());
        if (pitchOffset != 0.0f) {
            event.setPitch(event.getPitch() + pitchOffset);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WireZiplineClientManager.resetOnLogout();
        WireGogglesOverlayRenderer.reset();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!WireZiplineClientManager.isRiding()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (event.getAction() != GLFW.GLFW_PRESS) return;

        if (event.getKey() == mc.options.keyShift.getKey().getValue()) {
            WireZiplineClientManager.disembark(false);
        } else if (event.getKey() == mc.options.keyJump.getKey().getValue()) {
            WireZiplineClientManager.jumpFromWire();
        } else if (event.getKey() == mc.options.keyLeft.getKey().getValue()) {
            WireZiplineClientManager.attemptLaneSwitch(-1.0f);
        } else if (event.getKey() == mc.options.keyRight.getKey().getValue()) {
            WireZiplineClientManager.attemptLaneSwitch(1.0f);
        }
    }
}
