package com.white.wirerider.client;

import com.white.wirerider.WireRiderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Soft, continuous zipline sliding sound loop scaled gently by speed and client config.
 */
public class WireSlideSoundInstance extends AbstractTickableSoundInstance {
    private float smoothSpeed = 0.0f;

    public WireSlideSoundInstance() {
        super(SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.01f;
        this.pitch = 0.85f;
    }

    @Override
    public boolean canPlaySound() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !player.isSilent() && WireRiderConfig.CLIENT.slideSoundVolume.get() > 0.001;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        double cfgVol = WireRiderConfig.CLIENT.slideSoundVolume.get();

        if (!WireZiplineClientManager.isRiding() || player == null || player.isRemoved() || cfgVol <= 0.001) {
            this.stop();
            return;
        }

        if (mc.isPaused()) {
            this.volume = 0.001f;
            return;
        }

        float targetSpeed = (float) WireZiplineClientManager.getCurrentSpeed();
        this.smoothSpeed = Mth.lerp(0.2f, this.smoothSpeed, targetSpeed);

        if (this.smoothSpeed < 0.04f) {
            this.volume = 0.001f;
            this.pitch = 0.80f;
            return;
        }

        float speedRatio = Mth.clamp(this.smoothSpeed / 1.8f, 0.0f, 1.0f);
        // Kept deliberately soft (max 0.20 at config=1.0, ~0.07 at default 0.35) so it never fatigues ears.
        this.volume = (float) ((0.03f + speedRatio * 0.17f) * cfgVol);
        this.pitch = 0.82f + speedRatio * 0.34f;
    }
}
