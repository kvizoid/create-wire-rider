package com.white.wirerider.client;

import com.white.wirerider.content.qte.ElectricQTEController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class WireQTEOverlayRenderer {
    private static final ResourceLocation QTE_SLOT_SPRITE =
        ResourceLocation.fromNamespaceAndPath("create_wire_rider", "hud/qte_slot");

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!WireZiplineClientManager.isRiding()) return;
        ElectricQTEController qte = WireZiplineClientManager.getQteController();
        if (qte == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        int size = 22;
        int x = screenWidth / 2 + 91 + 6;
        int y = screenHeight - 22;
        Font font = mc.font;

        int combo = qte.getCombo();
        boolean qteActive = qte.isQTEActive();

        if (!qteActive && combo < 2) return;

        if (combo >= 2) {
            String comboText = combo + "x";
            int comboWidth = font.width(comboText);
            int comboX = x + (size - comboWidth) / 2;
            int comboY = qteActive ? (y - 16) : (y + 7);

            int comboColor;
            if (qte.getComboAnimationTicks() > 6) {
                comboColor = 0xFFFFFFFF;
            } else if (combo >= 10) {
                comboColor = 0xFFFF6B00;
            } else if (combo >= 5) {
                comboColor = 0xFF00E5FF;
            } else {
                comboColor = 0xFFFFD700;
            }

            guiGraphics.fill(comboX - 3, comboY - 1, comboX + comboWidth + 3, comboY + 8, 0x88000000);
            guiGraphics.drawString(font, comboText, comboX, comboY, comboColor, true);
        }

        if (qteActive) {
            ElectricQTEController.QTEKey prompt = qte.getActivePrompt();
            if (prompt == null) return;

            int ticksRemaining = qte.getTicksRemaining();
            int maxTicks = qte.getMaxTicks();
            float progress = Math.max(0.0f, (float) ticksRemaining / (float) maxTicks);
            boolean isWarning = ticksRemaining <= 8;

            guiGraphics.blitSprite(QTE_SLOT_SPRITE, x, y, size, size);

            if (isWarning && (ticksRemaining / 2) % 2 == 0) {
                guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x55FF2222);
            }

            int barW = 20;
            int barH = 2;
            int barX = x + 1;
            int barY = y - 5;
            int fillW = (int) (barW * progress);

            guiGraphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF000000);
            guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF1C1E24);
            if (fillW > 0) {
                int barColor = isWarning ? 0xFFFF3B30 : 0xFF00E5FF;
                guiGraphics.fill(barX, barY, barX + fillW, barY + barH, barColor);
            }

            String label = (prompt == ElectricQTEController.QTEKey.SPACE) ? "SPC" : prompt.displayName;
            int textWidth = font.width(label);
            int textX = x + (size - textWidth) / 2;
            int textY = y + (size - 8) / 2;

            int textColor = isWarning ? 0xFFFF4444 : 0xFFFFFFFF;
            guiGraphics.drawString(font, label, textX, textY, textColor, true);
        }
    }
}
