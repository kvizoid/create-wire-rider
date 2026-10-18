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

            if (prompt == ElectricQTEController.QTEKey.LMB) {
                renderMouseIcon(guiGraphics, x, y, true, isWarning);
            } else if (prompt == ElectricQTEController.QTEKey.RMB) {
                renderMouseIcon(guiGraphics, x, y, false, isWarning);
            } else if (prompt == ElectricQTEController.QTEKey.SPACE) {
                renderSpaceIcon(guiGraphics, x, y, isWarning);
            }
        }
    }

    private static void renderMouseIcon(GuiGraphics g, int x, int y, boolean isLeft, boolean isWarning) {
        int mx = x + 6;
        int my = y + 4;
        int activeColor = isWarning ? 0xFFFF3B30 : 0xFF00E5FF;
        int inactiveColor = 0xFF353942;
        int bodyColor = 0xFF23262D;
        int borderColor = 0xFF101216;

        g.fill(mx + 1, my, mx + 9, my + 14, borderColor);
        g.fill(mx, my + 1, mx + 10, my + 13, borderColor);

        g.fill(mx + 1, my + 6, mx + 9, my + 13, bodyColor);

        g.fill(mx + 4, my + 1, mx + 5, my + 6, borderColor);
        g.fill(mx + 1, my + 6, mx + 9, my + 7, borderColor);

        int leftCol = isLeft ? activeColor : inactiveColor;
        g.fill(mx + 1, my + 1, mx + 4, my + 6, leftCol);

        int rightCol = !isLeft ? activeColor : inactiveColor;
        g.fill(mx + 5, my + 1, mx + 9, my + 6, rightCol);

        if (isLeft) {
            g.fill(mx + 2, my + 2, mx + 4, my + 3, 0xFFFFFFFF);
        } else {
            g.fill(mx + 6, my + 2, mx + 8, my + 3, 0xFFFFFFFF);
        }
    }

    private static void renderSpaceIcon(GuiGraphics g, int x, int y, boolean isWarning) {
        int sx = x + 3;
        int sy = y + 6;
        int activeColor = isWarning ? 0xFFFF3B30 : 0xFF00E5FF;
        int borderColor = 0xFF101216;
        int keyColor = 0xFF23262D;
        int bevelColor = 0xFF3E434F;

        g.fill(sx, sy, sx + 16, sy + 10, borderColor);
        g.fill(sx + 1, sy + 1, sx + 15, sy + 9, keyColor);
        g.fill(sx + 1, sy + 1, sx + 15, sy + 2, bevelColor);

        g.fill(sx + 3, sy + 6, sx + 13, sy + 7, activeColor);
        g.fill(sx + 3, sy + 4, sx + 4, sy + 6, activeColor);
        g.fill(sx + 12, sy + 4, sx + 13, sy + 6, activeColor);

        g.fill(sx + 7, sy + 4, sx + 9, sy + 5, activeColor);
    }
}
