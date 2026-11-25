package com.white.wirerider.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.white.wirerider.WireGeometry;
import com.white.wirerider.WireRiderConfig;
import com.white.wirerider.WireRiderUtils;
import com.white.wirerider.compat.cee.CEEClientWireHelper;
import com.white.wirerider.compat.cee.CEEWireSession;
import com.white.wirerider.network.WireRideActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class WireZiplineClientManager {
    private static CEEWireSession currentSession = null;
    private static List<Vec3> wirePoints = null;

    private static boolean isEnergized = false;
    private static float currentVoltage = 0.0f;
    private static float currentAmps = 0.0f;
    private static float wireTemp = 20.0f;
    private static boolean groundArcWarning = false;
    private static float stallCharge = 0.0f;

    private static int groundedTimer = 0;
    private static int tickCounter = 0;
    private static int remountCooldown = 0;
    private static int laneSwitchCooldown = 0;
    private static int thermalBurnTicks = 0;
    private static InWorldNodeConnection lastExitedWire = null;
    private static int lastWireCooldown = 0;
    private static Vec3 currentNormal = Vec3.ZERO;

    private static double currentSpeed = 0.0;
    private static float prevCameraRoll = 0.0f;
    private static float cameraRoll = 0.0f;
    private static float laneSwitchRollImpulse = 0.0f;
    private static float prevCameraPitch = 0.0f;
    private static float cameraPitch = 0.0f;
    private static float jumpPitchImpulse = 0.0f;
    private static WireSlideSoundInstance slideSound = null;

    private static int pendingResumeTicks = 0;
    private static net.minecraft.nbt.CompoundTag pendingResumeData = null;
    private static Vec3 pendingResumePos = null;

    public static boolean isRiding() {
        return currentSession != null;
    }

    public static boolean isEnergized() {
        return isEnergized;
    }

    public static float getCurrentVoltage() {
        return currentVoltage;
    }

    public static float getCurrentAmps() {
        return currentAmps;
    }

    public static float getWireTemp() {
        return wireTemp;
    }

    public static boolean hasGroundArcWarning() {
        return groundArcWarning;
    }

    public static float getStallCharge() {
        return stallCharge;
    }

    public static double getCurrentSpeed() {
        return currentSpeed;
    }

    public static Vec3 getInterpolatedWireAnchor(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Vec3.ZERO;
        float chainYOffset = 0.66F * mc.player.getScale();
        Vec3 handsPos = mc.player.getPosition(partialTick)
            .add(0.0, mc.player.getBoundingBox().getYsize() + chainYOffset, 0.0);
        if (wirePoints != null && wirePoints.size() >= 2) {
            return WireGeometry.getClosestPointOnWire(wirePoints, handsPos).position();
        }
        return handsPos;
    }

    public static float getCameraRoll(double partialTick) {
        double strength = WireRiderConfig.CLIENT.cameraRollStrength.get();
        if (strength <= 0.001) return 0.0f;
        float interpolated = Mth.lerp((float) partialTick, prevCameraRoll, cameraRoll);
        if (Math.abs(interpolated) < 0.01f) return 0.0f;
        return (float) (interpolated * strength);
    }

    public static float getCameraPitchOffset(double partialTick) {
        double strength = WireRiderConfig.CLIENT.cameraRollStrength.get();
        if (strength <= 0.001) return 0.0f;
        float interpolated = Mth.lerp((float) partialTick, prevCameraPitch, cameraPitch);
        if (Math.abs(interpolated) < 0.01f) return 0.0f;
        return (float) (interpolated * strength);
    }

    public static void updateElectricalStatus(boolean energized, float voltage, float current, float temperature) {
        isEnergized = energized;
        currentVoltage = voltage;
        currentAmps = current;
        wireTemp = temperature;
    }

    public static void scheduleReconnectResume(net.minecraft.nbt.CompoundTag wireData, double x, double y, double z) {
        if (isRiding()) return;
        pendingResumeTicks = 100;
        pendingResumeData = wireData != null ? wireData.copy() : new net.minecraft.nbt.CompoundTag();
        pendingResumePos = new Vec3(x, y, z);
    }

    public static void resetOnLogout() {
        stopSlideSound();
        clearSessionFields();
        pendingResumeTicks = 0;
        pendingResumeData = null;
        pendingResumePos = null;
        remountCooldown = 0;
        lastExitedWire = null;
        lastWireCooldown = 0;
        prevCameraRoll = 0.0f;
        cameraRoll = 0.0f;
        laneSwitchRollImpulse = 0.0f;
        prevCameraPitch = 0.0f;
        cameraPitch = 0.0f;
        jumpPitchImpulse = 0.0f;
    }

    private static void startSlideSound(Minecraft mc) {
        if (WireRiderConfig.CLIENT.slideSoundVolume.get() <= 0.001) return;
        if (slideSound != null && !slideSound.isStopped()) return;
        slideSound = new WireSlideSoundInstance();
        mc.getSoundManager().play(slideSound);
    }

    private static void stopSlideSound() {
        if (slideSound != null) {
            Minecraft.getInstance().getSoundManager().stop(slideSound);
            slideSound = null;
        }
    }

    public static void embark(CEEWireSession session) {
        if (session == null || !session.isValid()) return;
        if (isRiding() || remountCooldown > 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<Vec3> points = session.getPoints();
        if (points == null || points.size() < 2) return;

        currentSession = session;
        wirePoints = points;
        groundedTimer = 0;
        thermalBurnTicks = 0;
        tickCounter = 0;
        currentSpeed = 0.0;
        currentNormal = Vec3.ZERO;
        lastExitedWire = null;
        lastWireCooldown = 0;
        laneSwitchCooldown = 0;
        laneSwitchRollImpulse = 0.0f;
        jumpPitchImpulse = 2.5f;
        groundArcWarning = false;
        stallCharge = 0.0f;

        isEnergized = false;
        currentVoltage = 0.0f;
        currentAmps = 0.0f;
        wireTemp = 20.0f;

        mc.player.getAbilities().flying = false;
        mc.player.stopFallFlying();

        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.9F, 0.35F));
        startSlideSound(mc);
        session.sendStartPacket();
    }

    public static void jumpFromWire() {
        if (currentSession == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        lastExitedWire = currentSession.getConnection();
        lastWireCooldown = 12;
        remountCooldown = 6;

        Vec3 vel = mc.player.getDeltaMovement();
        Vec3 normal = currentNormal;
        double speedAlong = vel.dot(normal);

        Vec3 look = mc.player.getLookAngle();
        Vec3 lookHoriz = new Vec3(look.x, 0.0, look.z);
        lookHoriz = (lookHoriz.lengthSqr() > 1e-4) ? lookHoriz.normalize() : new Vec3(0, 0, 1);

        Vec3 jumpDir;
        if (Math.abs(speedAlong) > 0.05) {
            Vec3 wireHoriz = new Vec3(normal.x, 0.0, normal.z);
            if (wireHoriz.lengthSqr() > 1e-4) {
                wireHoriz = wireHoriz.normalize().scale(Math.signum(speedAlong));
                jumpDir = wireHoriz.scale(0.60).add(lookHoriz.scale(0.40)).normalize();

                Vec3 right = wireHoriz.cross(new Vec3(0, 1, 0));
                if (right.lengthSqr() > 1e-4) {
                    laneSwitchRollImpulse = (float) (lookHoriz.dot(right.normalize()) * 4.5);
                }
            } else {
                jumpDir = lookHoriz;
            }
        } else {
            jumpDir = lookHoriz;
        }

        jumpPitchImpulse = -6.5f;

        double horizSpeed = Math.max(Math.abs(speedAlong) * 1.45 + 0.35, 0.85);
        double jumpPower = 0.65;
        if (mc.player.hasEffect(MobEffects.JUMP)) {
            jumpPower += 0.12 * (mc.player.getEffect(MobEffects.JUMP).getAmplifier() + 1);
        }

        mc.player.setDeltaMovement(new Vec3(jumpDir.x * horizSpeed, jumpPower, jumpDir.z * horizSpeed));
        mc.player.fallDistance = 0.0F;

        PacketDistributor.sendToServer(WireRideActionPayload.stop());
        mc.player.playSound(SoundEvents.WIND_CHARGE_BURST.value(), 0.45F, 1.15F);
        mc.player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.40F, 1.1F);

        if (mc.level != null && WireRiderConfig.CLIENT.enableParticles.get()) {
            for (int i = 0; i < 8; i++) {
                mc.level.addParticle(ParticleTypes.CLOUD, mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ(),
                    -jumpDir.x * 0.2 + (Math.random() - 0.5) * 0.15,
                    0.05 + Math.random() * 0.08,
                    -jumpDir.z * 0.2 + (Math.random() - 0.5) * 0.15);
            }
        }

        stopSlideSound();
        clearSessionFields();
    }

    public static void disembark(boolean preserveMomentum) {
        if (currentSession == null) return;
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null) {
            lastExitedWire = currentSession.getConnection();
            lastWireCooldown = 12;
            remountCooldown = 8;

            Vec3 vel = mc.player.getDeltaMovement();
            if (preserveMomentum) {
                if (vel.lengthSqr() > 0.01) {
                    mc.player.setDeltaMovement(vel.scale(1.2).add(0.0, 0.42, 0.0));
                } else {
                    mc.player.setDeltaMovement(new Vec3(0.0, 0.42, 0.0));
                }
                jumpPitchImpulse = -4.5f;
            } else {
                mc.player.setDeltaMovement(new Vec3(vel.x * 0.6, Math.min(vel.y, 0.0), vel.z * 0.6));
                jumpPitchImpulse = 4.2f;
            }
            mc.player.fallDistance = 0.0F;
        }

        PacketDistributor.sendToServer(WireRideActionPayload.stop());
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.75F, 0.25F));

        stopSlideSound();
        clearSessionFields();
    }

    private static void clearSessionFields() {
        currentSession = null;
        wirePoints = null;
        groundedTimer = 0;
        thermalBurnTicks = 0;
        currentSpeed = 0.0;
        groundArcWarning = false;
        stallCharge = 0.0f;
        isEnergized = false;
        currentVoltage = 0.0f;
        currentAmps = 0.0f;
        wireTemp = 20.0f;
        currentNormal = Vec3.ZERO;
        laneSwitchCooldown = 0;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        prevCameraRoll = cameraRoll;
        prevCameraPitch = cameraPitch;
        laneSwitchRollImpulse *= 0.80f;
        if (Math.abs(laneSwitchRollImpulse) < 0.02f) laneSwitchRollImpulse = 0.0f;
        jumpPitchImpulse *= 0.78f;
        if (Math.abs(jumpPitchImpulse) < 0.02f) jumpPitchImpulse = 0.0f;

        if (remountCooldown > 0) remountCooldown--;
        if (laneSwitchCooldown > 0) laneSwitchCooldown--;
        if (lastWireCooldown > 0) {
            lastWireCooldown--;
            if (lastWireCooldown == 0) lastExitedWire = null;
        }

        if (currentSession != null) {
            pendingResumeTicks = 0;
            pendingResumeData = null;
            ridingTick(mc, mc.player);
        } else {
            cameraRoll = Mth.lerp(0.28f, cameraRoll, laneSwitchRollImpulse);
            cameraPitch = Mth.lerp(0.32f, cameraPitch, jumpPitchImpulse);
            groundedTimer = 0;
            currentSpeed = 0.0;

            if (pendingResumeTicks > 0) {
                pendingResumeTicks--;
                if (!WireRiderUtils.hasWrench(mc.player)) {
                    pendingResumeTicks = 0;
                    pendingResumeData = null;
                    PacketDistributor.sendToServer(WireRideActionPayload.stop());
                } else {
                    if (pendingResumePos != null) {
                        mc.player.setPos(pendingResumePos.x, pendingResumePos.y, pendingResumePos.z);
                    }
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    mc.player.fallDistance = 0.0F;

                    CEEWireSession resumeSession = CEEClientWireHelper.tryBuildResumeSession(
                        pendingResumeData, mc.level,
                        pendingResumePos != null ? pendingResumePos : mc.player.position()
                    );
                    if (resumeSession != null && resumeSession.isValid()) {
                        pendingResumeTicks = 0;
                        pendingResumeData = null;
                        pendingResumePos = null;
                        embark(resumeSession);
                        return;
                    }
                    if (pendingResumeTicks == 0) {
                        pendingResumeData = null;
                        pendingResumePos = null;
                        PacketDistributor.sendToServer(WireRideActionPayload.stop());
                    }
                }
            } else if (remountCooldown == 0 && mc.options.keyUse.isDown() && !mc.player.isShiftKeyDown() && WireRiderUtils.hasWrench(mc.player)) {
                holdUseSearch(mc, mc.player);
            }
        }
    }

    private static Vec3 getHandsPos(LocalPlayer player) {
        float chainYOffset = 0.66F * player.getScale();
        return player.position().add(0.0, player.getBoundingBox().getYsize() + chainYOffset, 0.0);
    }

    private static void ridingTick(Minecraft mc, LocalPlayer player) {
        if (mc.isPaused()) return;

        if (!WireRiderUtils.hasWrench(player)) {
            disembark(false);
            return;
        }

        Vec3 handsPos = getHandsPos(player);
        WireGeometry.ClosestQuery query = WireGeometry.getClosestPointOnWire(wirePoints, handsPos);
        Vec3 target = query.position();
        Vec3 diff = target.subtract(handsPos);
        Vec3 normal = query.normal();
        currentNormal = normal;

        Vec3 delta = player.getDeltaMovement();
        double speedAlongAbs = Math.abs(delta.dot(normal));
        currentSpeed = speedAlongAbs;

        if (player.onGround() && speedAlongAbs < 0.25) {
            groundedTimer++;
        } else {
            groundedTimer = 0;
        }

        if (groundedTimer > 5 || player.getAbilities().flying
            || currentSession == null || !currentSession.isValid()
            || wirePoints == null || wirePoints.size() < 2) {
            disembark(false);
            return;
        }

        double endThreshold = Math.max(1.3, speedAlongAbs * 1.5);
        double endThresholdSq = endThreshold * endThreshold;
        boolean isStart = target.distanceToSqr(wirePoints.get(0)) < endThresholdSq;
        boolean isEnd = target.distanceToSqr(wirePoints.get(wirePoints.size() - 1)) < endThresholdSq;

        float steerRight = -player.input.leftImpulse;
        if (laneSwitchCooldown == 0 && Math.abs(steerRight) > 0.5f) {
            attemptLaneSwitch(steerRight);
        }

        if (delta.lengthSqr() > 1.0E-6) {
            double dotNormal = delta.normalize().dot(normal);
            if (isEnd && dotNormal > 0.4) {
                CEEWireSession next = CEEClientWireHelper.findConnectedWire(
                    currentSession, wirePoints.get(wirePoints.size() - 1), mc.level, delta, steerRight, lastExitedWire
                );
                if (next != null && next.isValid()) {
                    switchSession(next);
                    return;
                } else {
                    jumpFromWire();
                    return;
                }
            } else if (isStart && dotNormal < -0.4) {
                CEEWireSession next = CEEClientWireHelper.findConnectedWire(
                    currentSession, wirePoints.get(0), mc.level, delta, steerRight, lastExitedWire
                );
                if (next != null && next.isValid()) {
                    switchSession(next);
                    return;
                } else {
                    jumpFromWire();
                    return;
                }
            }
        }

        double reach = Math.max(6.0, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3.0);
        if (diff.lengthSqr() > reach * reach) {
            disembark(false);
            return;
        }

        double diffAlong = diff.dot(normal);
        Vec3 perpDiff = diff.subtract(normal.scale(diffAlong));
        double speedAlong = delta.dot(normal);

        double forward = player.input.forwardImpulse;
        if (Math.abs(forward) > 0.05) {
            double driveDir = (player.getLookAngle().dot(normal) >= 0) ? 1.0 : -1.0;
            double power = (player.isSprinting() ? 0.075 : 0.052) * forward;
            speedAlong += driveDir * power;
        }

        boolean inWaterOrRain = player.isInWaterOrRain() || mc.level.isRainingAt(player.blockPosition());
        speedAlong += (-normal.y * 0.038);
        speedAlong *= inWaterOrRain ? 0.993 : 0.985;
        float maxSpeed = WireRiderConfig.COMMON.maxRideSpeed.get().floatValue();
        speedAlong = Mth.clamp((float) speedAlong, -maxSpeed, maxSpeed);

        Vec3 perpCorrection = perpDiff.scale(0.85);
        if (perpCorrection.lengthSqr() > 0.5 * 0.5) {
            perpCorrection = perpCorrection.normalize().scale(0.5);
        }

        player.setDeltaMovement(normal.scale(speedAlong).add(perpCorrection));
        player.fallDistance = 0.0F;

        // Dynamic camera roll & slope pitch
        float speedFactor = (float) Mth.clamp(Math.abs(speedAlong) / 1.4, 0.0, 1.0);
        Vec3 lookHoriz = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        float bankRoll = 0.0f;
        float slopePitch = 0.0f;
        if (Math.abs(speedAlong) > 0.1) {
            double slopeY = Math.signum(speedAlong) * normal.y;
            slopePitch = (float) Mth.clamp(-slopeY * 4.5 * speedFactor, -3.5, 4.5);

            if (lookHoriz.lengthSqr() > 1e-4) {
                Vec3 travelHoriz = new Vec3(normal.x, 0.0, normal.z).scale(Math.signum(speedAlong));
                if (travelHoriz.lengthSqr() > 1e-4) {
                    Vec3 right = travelHoriz.normalize().cross(new Vec3(0, 1, 0));
                    double sideLook = lookHoriz.normalize().dot(right);
                    bankRoll = (float) (sideLook * 2.2 * speedFactor);
                }
            }
        }
        float targetRoll = Mth.clamp(laneSwitchRollImpulse + bankRoll, -8.0f, 8.0f);
        float targetPitch = Mth.clamp(jumpPitchImpulse + slopePitch, -8.0f, 8.0f);
        cameraRoll = Mth.lerp(0.28f, cameraRoll, targetRoll);
        cameraPitch = Mth.lerp(0.28f, cameraPitch, targetPitch);

        if (++tickCounter % 10 == 0) {
            currentSession.sendHeartbeatPacket();
        }

        handlePhysicalHazards(mc, player, target, delta, inWaterOrRain);
    }

    private static void handlePhysicalHazards(Minecraft mc, LocalPlayer player, Vec3 target, Vec3 delta, boolean inWaterOrRain) {
        boolean particlesEnabled = WireRiderConfig.CLIENT.enableParticles.get();
        boolean hazardsEnabled = WireRiderConfig.COMMON.enableElectricHazards.get();
        float electricVol = WireRiderConfig.CLIENT.electricSoundVolume.get().floatValue();

        float burnTemp = WireRiderUtils.getWireBurnTemp();
        float criticalTemp = WireRiderUtils.getWireCriticalTemp();

        if (particlesEnabled && wireTemp >= 45.0f && tickCounter % 3 == 0) {
            mc.level.addParticle(ParticleTypes.SMOKE, target.x, target.y - 0.1, target.z, 0.0, 0.04, 0.0);
        }

        if (hazardsEnabled && wireTemp >= burnTemp) {
            if (particlesEnabled && tickCounter % 3 == 0) {
                mc.level.addParticle(ParticleTypes.FLAME, target.x, target.y - 0.15, target.z,
                    (Math.random() - 0.5) * 0.05, -0.02, (Math.random() - 0.5) * 0.05);
            }
            thermalBurnTicks++;
            if (thermalBurnTicks == 1 || thermalBurnTicks >= 20) {
                thermalBurnTicks = 1;
                currentSession.sendThermalBurnPacket();
                player.playSound(SoundEvents.PLAYER_HURT_ON_FIRE, 0.5f, 1.1f);
                if (wireTemp >= criticalTemp) {
                    disembark(false);
                    return;
                }
            }
        } else {
            thermalBurnTicks = 0;
        }

        if (!isEnergized) {
            groundArcWarning = false;
            stallCharge = 0.0f;
            return;
        }

        // World-space sparks at the wire contact point for 3rd-person / multiplayer trail
        // (1st-person jaw sparks are rendered directly in viewmodel space by WrenchSparkRenderer)
        if (particlesEnabled && !mc.options.getCameraType().isFirstPerson()) {
            double speed = delta.length();
            if (speed > 0.04 || tickCounter % 3 == 0) {
                int count = speed > 0.04 ? Math.min(3, 1 + (int) (speed * 2.0)) : 1;
                Vec3 sparkDir = speed > 0.04 ? delta.normalize().scale(-0.10) : Vec3.ZERO;
                for (int i = 0; i < count; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, target.x, target.y - 0.03, target.z,
                        sparkDir.x + (Math.random() - 0.5) * 0.10,
                        sparkDir.y + Math.random() * 0.06,
                        sparkDir.z + (Math.random() - 0.5) * 0.10);
                }
            }
        }

        // Pole / insulator arc sparks
        if (particlesEnabled) {
            double distToNode1 = target.distanceToSqr(wirePoints.get(0));
            double distToNode2 = target.distanceToSqr(wirePoints.get(wirePoints.size() - 1));
            if (distToNode1 < 4.0 || distToNode2 < 4.0) {
                Vec3 polePos = (distToNode1 <= distToNode2) ? wirePoints.get(0) : wirePoints.get(wirePoints.size() - 1);
                for (double t = 0.0; t <= 1.0; t += 0.25) {
                    double px = Mth.lerp(t, target.x, polePos.x) + (Math.random() - 0.5) * 0.15;
                    double py = Mth.lerp(t, target.y, polePos.y) + (Math.random() - 0.5) * 0.15;
                    double pz = Mth.lerp(t, target.z, polePos.z) + (Math.random() - 0.5) * 0.15;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz,
                        (Math.random() - 0.5) * 0.10, -0.02, (Math.random() - 0.5) * 0.10);
                }
            }
        }

        if (!hazardsEnabled) {
            groundArcWarning = false;
            stallCharge = 0.0f;
            return;
        }

        // Stall arc buildup: cannot idle indefinitely on a live high-voltage bare wire
        if (WireRiderConfig.COMMON.enableStallArcHazard.get()
            && currentVoltage >= WireRiderConfig.COMMON.stallMinVoltage.get()) {
            if (currentSpeed < 0.08) {
                float seconds = WireRiderConfig.COMMON.stallBuildupSeconds.get().floatValue();
                float step = 1.0f / Math.max(20.0f, seconds * 20.0f);
                if (inWaterOrRain) step *= 1.4f;
                stallCharge = Math.min(1.0f, stallCharge + step);

                if (stallCharge > 0.45f && electricVol > 0.01f && tickCounter % 8 == 0) {
                    player.playSound(SoundEvents.FLINTANDSTEEL_USE, 0.16f * electricVol, 1.15f + stallCharge * 0.35f);
                }
                if (stallCharge >= 1.0f) {
                    currentSession.sendShockPacket();
                    if (particlesEnabled) {
                        for (int i = 0; i < 12; i++) {
                            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                                player.getX(), player.getY() + 1.0, player.getZ(),
                                (Math.random() - 0.5) * 0.4, 0.15, (Math.random() - 0.5) * 0.4);
                        }
                    }
                    disembark(false);
                    return;
                }
            } else {
                stallCharge = Math.max(0.0f, stallCharge - 0.08f);
            }
        } else {
            stallCharge = 0.0f;
        }

        // Ground / water short-circuit arc hazard
        double baseShockDist = WireRiderConfig.COMMON.groundShockDistance.get();
        double shockDist = inWaterOrRain ? baseShockDist + 0.55 : baseShockDist;
        double warnDist = shockDist + 1.45;
        GroundHit ground = checkGroundUnder(mc.level, player.position());

        if (player.isInWater() || (ground != null && ground.distance < shockDist)) {
            groundArcWarning = true;
            currentSession.sendShockPacket();
            if (particlesEnabled) {
                for (int i = 0; i < 14; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 0.9, player.getZ(),
                        (Math.random() - 0.5) * 0.45, 0.15, (Math.random() - 0.5) * 0.45);
                }
            }
            disembark(false);
            return;
        }

        if (ground != null && ground.distance < warnDist) {
            groundArcWarning = true;
            if (particlesEnabled) {
                Vec3 start = player.position().add(0, 0.2, 0);
                Vec3 end = ground.hitPos;
                for (double t = 0.0; t <= 1.0; t += 0.2) {
                    double px = Mth.lerp(t, start.x, end.x) + (Math.random() - 0.5) * 0.15;
                    double py = Mth.lerp(t, start.y, end.y);
                    double pz = Mth.lerp(t, start.z, end.z) + (Math.random() - 0.5) * 0.15;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz,
                        (Math.random() - 0.5) * 0.08, -0.05, (Math.random() - 0.5) * 0.08);
                }
                if (ground.isWater) {
                    mc.level.addParticle(ParticleTypes.SPLASH, end.x, end.y + 0.05, end.z,
                        (Math.random() - 0.5) * 0.2, 0.15, (Math.random() - 0.5) * 0.2);
                }
            }
            if (electricVol > 0.01f && tickCounter % 9 == 0) {
                player.playSound(SoundEvents.FLINTANDSTEEL_USE, 0.22f * electricVol, 1.35f);
            }
        } else {
            groundArcWarning = false;
        }
    }

    private record GroundHit(double distance, boolean isWater, Vec3 hitPos) {}

    private static GroundHit checkGroundUnder(Level level, Vec3 pos) {
        BlockPos base = BlockPos.containing(pos);
        for (int dy = 0; dy <= 5; dy++) {
            BlockPos bp = base.below(dy);
            BlockState state = level.getBlockState(bp);
            var fluid = state.getFluidState();
            if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
                double topY = bp.getY() + fluid.getHeight(level, bp);
                double dist = pos.y - topY;
                if (dist >= -0.5) {
                    return new GroundHit(Math.max(0.0, dist), true, new Vec3(pos.x, topY, pos.z));
                }
            }
            VoxelShape shape = state.getCollisionShape(level, bp);
            if (!shape.isEmpty()) {
                double topY = bp.getY() + shape.max(Direction.Axis.Y);
                double dist = pos.y - topY;
                if (dist >= -0.5) {
                    return new GroundHit(Math.max(0.0, dist), false, new Vec3(pos.x, topY, pos.z));
                }
            }
        }
        return null;
    }

    private static void switchSession(CEEWireSession next) {
        Minecraft mc = Minecraft.getInstance();
        double speed = 0.0;
        if (mc.player != null && currentNormal.lengthSqr() > 0.01) {
            speed = Math.abs(mc.player.getDeltaMovement().dot(currentNormal));
        }
        lastExitedWire = currentSession != null ? currentSession.getConnection() : null;
        lastWireCooldown = 10;

        currentSession = next;
        wirePoints = next.getPoints();
        if (mc.player != null && wirePoints != null && wirePoints.size() >= 2) {
            Vec3 handsPos = getHandsPos(mc.player);
            WireGeometry.ClosestQuery q = WireGeometry.getClosestPointOnWire(wirePoints, handsPos);
            currentNormal = q.normal();
            double forwardSpeed = Math.max(0.35, speed);
            mc.player.setDeltaMovement(currentNormal.scale(forwardSpeed));
        }
        currentSession.sendStartPacket();
    }

    public static boolean attemptLaneSwitch(float dir) {
        if (currentSession == null || laneSwitchCooldown > 0) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;

        Vec3 handsPos = getHandsPos(player);
        Vec3 vel = player.getDeltaMovement();
        Vec3 normal = currentNormal;
        double speedAlong = vel.dot(normal);

        Vec3 forwardDir;
        if (Math.abs(speedAlong) > 0.05) {
            forwardDir = normal.scale(Math.signum(speedAlong));
        } else {
            Vec3 look = player.getLookAngle();
            Vec3 horiz = new Vec3(look.x, 0.0, look.z);
            forwardDir = horiz.lengthSqr() > 1e-4 ? horiz.normalize() : new Vec3(0, 0, 1);
        }

        Vec3 right = forwardDir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) return false;
        right = right.normalize();
        Vec3 desiredLateralDir = (dir > 0) ? right : right.scale(-1.0);

        CEEWireSession next = CEEClientWireHelper.findParallelWire(
            handsPos, desiredLateralDir, normal, currentSession.getConnection(), mc.level
        );

        if (next != null && next.isValid()) {
            switchSession(next);
            laneSwitchCooldown = 10;
            laneSwitchRollImpulse = (dir > 0) ? 5.5f : -5.5f;
            jumpPitchImpulse = 1.8f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1.2F, 0.30F));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_SHOOT, 1.4F, 0.22F));

            player.setDeltaMovement(player.getDeltaMovement().add(desiredLateralDir.scale(0.22)));
            return true;
        }
        return false;
    }

    private static void holdUseSearch(Minecraft mc, LocalPlayer player) {
        Vec3 handsPos = getHandsPos(player);
        Vec3 camPos = player.getEyePosition();
        Vec3 camLook = player.getLookAngle();
        double grabDistSq = 3.8 * 3.8;
        InWorldNodeConnection excluded = (lastWireCooldown > 0) ? lastExitedWire : null;

        CEEWireSession bestSession = CEEClientWireHelper.searchHoldToGrab(
            handsPos, grabDistSq, mc.level, camPos, camLook, excluded
        );

        if (bestSession != null) {
            embark(bestSession);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }
}
