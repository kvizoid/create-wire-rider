package com.white.wirerider.client;

import com.white.wirerider.content.qte.ElectricQTEController;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "create_wire_rider", value = Dist.CLIENT)
public class WireRiderClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        WireZiplineClientManager.tick();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        WireQTEOverlayRenderer.render(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (WireZiplineClientManager.isRiding()) {
            WireZiplineClientManager.saveResumeOnLogout();
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        WireZiplineClientManager.onLogin();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!WireZiplineClientManager.isRiding()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            ElectricQTEController qte = WireZiplineClientManager.getQteController();
            boolean qteActive = (qte != null && qte.isQTEActive());

            if (event.getKey() == mc.options.keyShift.getKey().getValue()) {
                if (qteActive) {
                    WireZiplineClientManager.handleQTEResult(qte.fail());
                } else {
                    WireZiplineClientManager.disembark(false);
                }
                return;
            }

            if (event.getKey() == mc.options.keyJump.getKey().getValue()) {
                if (qteActive) {
                    int res = qte.handleInput(ElectricQTEController.QTEKey.SPACE);
                    WireZiplineClientManager.handleQTEResult(res);
                } else {
                    WireZiplineClientManager.jumpFromWire();
                }
                return;
            }

            if (event.getKey() == mc.options.keyLeft.getKey().getValue()) {
                if (!qteActive) {
                    WireZiplineClientManager.attemptLaneSwitch(-1.0f);
                }
                return;
            } else if (event.getKey() == mc.options.keyRight.getKey().getValue()) {
                if (!qteActive) {
                    WireZiplineClientManager.attemptLaneSwitch(1.0f);
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!WireZiplineClientManager.isRiding()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        ElectricQTEController qte = WireZiplineClientManager.getQteController();
        if (qte != null && qte.isQTEActive()) {
            ElectricQTEController.QTEKey key = null;
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                key = ElectricQTEController.QTEKey.LMB;
            } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                key = ElectricQTEController.QTEKey.RMB;
            }

            if (key != null) {
                int res = qte.handleInput(key);
                WireZiplineClientManager.handleQTEResult(res);
                event.setCanceled(true);
            }
        }
    }
}
