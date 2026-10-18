package com.white.wirerider.content.qte;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class ElectricQTEController {

    public enum QTEKey {
        SPACE("SPACE", "SPC"),
        LMB("LMB", "ЛКМ"),
        RMB("RMB", "ПКМ");

        public final String id;
        public final String displayName;

        QTEKey(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static QTEKey fromString(String str) {
            for (QTEKey k : values()) {
                if (k.id.equalsIgnoreCase(str)) return k;
            }
            return null;
        }
    }

    private final Player player;
    private final Random random = new Random();

    private static final int QTE_WINDOW_TICKS = 22;

    private static final int MIN_COOLDOWN_TICKS = 70;
    private static final int MAX_COOLDOWN_TICKS = 120;

    private int cooldownTicks;
    private QTEKey activePrompt = null;
    private int ticksRemaining = 0;
    private int combo = 0;
    private int comboAnimationTicks = 0;
    private float voltage = 0.0f;
    private float current = 0.0f;
    private float temperature = 20.0f;

    public ElectricQTEController(Player player) {
        this.player = player;
        this.cooldownTicks = 50 + random.nextInt(40);
    }

    public void updateEnvironment(float voltage, float current, float temperature) {
        this.voltage = voltage;
        this.current = current;
        this.temperature = temperature;
    }

    public void triggerImmediateHazard() {
        if (activePrompt == null && cooldownTicks > 10) {
            startQTE();
        }
    }

    public int tick(boolean hasVoltage) {
        if (!hasVoltage) {
            if (activePrompt != null) {
                clearPrompt();
            }
            return 0;
        }

        if (comboAnimationTicks > 0) {
            comboAnimationTicks--;
        }

        if (activePrompt == null) {
            cooldownTicks--;
            if (cooldownTicks <= 0) {
                startQTE();
            }
            return 0;
        } else {
            ticksRemaining--;

            if (ticksRemaining % 6 == 0) {
                player.playSound(SoundEvents.BEE_POLLINATE, 0.4f, 2.0f);
            }

            if (ticksRemaining <= 0) {
                return onFailedQTE();
            }
        }
        return 0;
    }

    private boolean tryArrestorDischarge() {
        if (player == null) return false;
        ItemStack wrench = com.white.wirerider.client.WireZiplineClientManager.getActiveWrench(player);
        boolean hasOnWrench = com.white.wirerider.content.item.SparkArrestorHandler.hasArrestor(wrench);
        boolean hasOnOff = player.getOffhandItem().getItem() instanceof com.white.wirerider.content.item.SparkArrestorItem;
        boolean hasOnMain = player.getMainHandItem().getItem() instanceof com.white.wirerider.content.item.SparkArrestorItem;

        if (!hasOnWrench && !hasOnOff && !hasOnMain) {
            return false;
        }

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.white.wirerider.network.WireRideActionPayload(
                com.white.wirerider.network.WireRideActionPayload.ACTION_ARRESTOR_DISCHARGE,
                (byte) 0,
                new net.minecraft.nbt.CompoundTag()
            )
        );

        com.white.wirerider.client.WireZiplineClientManager.addWrenchHeat(0.25f);

        player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.40F, 1.6F);
        player.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.30F, 1.9F);

        for (int i = 0; i < 12; i++) {
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.2, player.getZ(),
                (random.nextDouble() - 0.5) * 0.5, 0.2, (random.nextDouble() - 0.5) * 0.5);
        }
        for (int i = 0; i < 6; i++) {
            player.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                player.getX(), player.getY() + 1.2, player.getZ(),
                (random.nextDouble() - 0.5) * 0.1, 0.08, (random.nextDouble() - 0.5) * 0.1);
        }

        return true;
    }

    private void startQTE() {
        QTEKey[] all = QTEKey.values();
        this.activePrompt = all[random.nextInt(all.length)];
        this.ticksRemaining = QTE_WINDOW_TICKS;

        player.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35f, 2.4f);

        for (int i = 0; i < 6; i++) {
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.2, player.getZ(),
                (random.nextDouble() - 0.5) * 0.35, 0.15, (random.nextDouble() - 0.5) * 0.35);
        }
    }

    public int fail() {
        return onFailedQTE();
    }

    public int handleInput(QTEKey pressed) {
        if (activePrompt == null) return 0;

        if (pressed == activePrompt) {
            return onSuccessfulQTE();
        } else {
            return onFailedQTE();
        }
    }

    private int onSuccessfulQTE() {
        clearPrompt();
        resetCooldown();

        combo++;
        comboAnimationTicks = 12;
        float pitch = Math.min(2.0f, 1.2f + combo * 0.08f);
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45f, pitch);

        for (int i = 0; i < 6; i++) {
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                (random.nextDouble() - 0.5) * 0.25, 0.08, (random.nextDouble() - 0.5) * 0.25);
        }
        return 1;
    }

    private int onFailedQTE() {
        clearPrompt();
        resetCooldown();
        combo = 0;
        comboAnimationTicks = 0;

        if (tryArrestorDischarge()) {
            return 2;
        }

        player.playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0f, 0.5f);
        return -2;
    }

    private void clearPrompt() {
        this.activePrompt = null;
        this.ticksRemaining = 0;
    }

    private void resetCooldown() {
        int minCd = MIN_COOLDOWN_TICKS;
        int maxCd = MAX_COOLDOWN_TICKS;
        if (voltage > 30000.0f) {
            minCd = 50;
            maxCd = 90;
        } else if (voltage < 5000.0f) {
            minCd = 100;
            maxCd = 160;
        }
        if (player.level().isRainingAt(player.blockPosition())) {
            minCd = (int) (minCd * 0.75);
            maxCd = (int) (maxCd * 0.75);
        }
        this.cooldownTicks = minCd + random.nextInt(Math.max(1, maxCd - minCd));
    }

    public QTEKey getActivePrompt() {
        return activePrompt;
    }

    public boolean isQTEActive() {
        return activePrompt != null;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public int getMaxTicks() {
        return QTE_WINDOW_TICKS;
    }

    public int getCombo() {
        return combo;
    }

    public int getComboAnimationTicks() {
        return comboAnimationTicks;
    }
}

