package com.white.wirerider.client;

import com.simibubi.create.AllTags.AllItemTags;
import com.white.wirerider.WireRiderTags;
import com.white.wirerider.compat.IRidableWireSession;
import com.white.wirerider.compat.cee.CEECompat;
import com.white.wirerider.compat.powergrid.PowerGridCompat;
import com.white.wirerider.content.WrenchLubricationHandler;
import com.white.wirerider.content.qte.ElectricQTEController;
import com.white.wirerider.network.WireRideActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class WireZiplineClientManager {
    public static IRidableWireSession currentSession = null;
    public static List<Vec3> wirePoints = null;
    public static boolean isEnergized = false;
    public static float currentVoltage = 0.0f;
    public static float currentAmps = 0.0f;
    public static float wireTemp = 20.0f;
    public static float dangerousDistance = 0.5f;
    private static int groundedTimer = 0;
    private static int tickCounter = 0;
    private static int remountCooldown = 0;
    private static Object lastExitedWire = null;
    private static int lastWireCooldown = 0;
    private static int laneSwitchCooldown = 0;
    private static int unenergizedGraceTicks = 0;
    private static ElectricQTEController qteController = null;
    private static Vec3 currentNormal = Vec3.ZERO;
    private static float wrenchHeat = 0.0f;
    private static int overheatBurnTicks = 0;
    private static int continuousRideTicks = 0;
    private static boolean wasRidingBeforeDisconnect = false;
    private static int reconnectMountTicks = 0;
    private static Player lastPlayerInstance = null;
    private static Vec3 savedResumePos = null;
    private static boolean isLoggingOut = false;

    public static void saveResumeOnLogout() {
        isLoggingOut = true;
        wasRidingBeforeDisconnect = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            savedResumePos = mc.player.position();
            writeResumeFile(savedResumePos);
        }
    }

    public static void onLogin() {
        isLoggingOut = false;
    }

    private static void writeResumeFile(Vec3 pos) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameDirectory != null && pos != null) {
                java.nio.file.Path file = mc.gameDirectory.toPath().resolve(".wire_rider_resume");
                String line = String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f", pos.x, pos.y, pos.z);
                java.nio.file.Files.writeString(file, line);
            }
        } catch (Throwable ignored) {}
    }

    private static void deleteResumeFile() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameDirectory != null) {
                java.nio.file.Path file = mc.gameDirectory.toPath().resolve(".wire_rider_resume");
                java.nio.file.Files.deleteIfExists(file);
            }
        } catch (Throwable ignored) {}
    }

    private static void checkResumeOnLogin(Minecraft mc) {
        boolean shouldResume = wasRidingBeforeDisconnect;
        try {
            if (mc.gameDirectory != null) {
                java.nio.file.Path file = mc.gameDirectory.toPath().resolve(".wire_rider_resume");
                if (java.nio.file.Files.exists(file)) {
                    String content = java.nio.file.Files.readString(file).trim();
                    String[] parts = content.split(",");
                    if (parts.length >= 3) {
                        double x = Double.parseDouble(parts[0]);
                        double y = Double.parseDouble(parts[1]);
                        double z = Double.parseDouble(parts[2]);
                        savedResumePos = new Vec3(x, y, z);
                    }
                    shouldResume = true;
                    java.nio.file.Files.deleteIfExists(file);
                }
            }
        } catch (Throwable ignored) {}

        if (shouldResume && hasWrench(mc.player)) {
            reconnectMountTicks = 160;
        } else {
            savedResumePos = null;
            reconnectMountTicks = 0;
        }
        wasRidingBeforeDisconnect = false;
    }

    public static float getWrenchHeat() {
        return wrenchHeat;
    }

    public static void addWrenchHeat(float amount) {
        wrenchHeat = Math.min(1.0f, wrenchHeat + amount);
    }

    public static boolean isLastExitedWire(Object identity) {
        return lastWireCooldown > 0 && lastExitedWire != null && lastExitedWire.equals(identity);
    }

    public static boolean isRiding() {
        return currentSession != null;
    }

    public static ElectricQTEController getQteController() {
        return qteController;
    }

    public static void updateElectricalStatus(boolean energized, float voltage, float current, float temperature, float dangerousDist) {
        isEnergized = energized;
        currentVoltage = voltage;
        currentAmps = current;
        wireTemp = temperature;
        dangerousDistance = dangerousDist;

        if (!isEnergized) {
            unenergizedGraceTicks++;
            if (unenergizedGraceTicks > 15) {
                qteController = null;
            }
        } else {
            unenergizedGraceTicks = 0;
            Minecraft mc = Minecraft.getInstance();
            if (qteController == null && mc.player != null) {
                qteController = new ElectricQTEController(mc.player);
            }
            if (qteController != null) {
                qteController.updateEnvironment(voltage, current, temperature);
            }
        }
    }

    public static void handleQTEResult(int result) {
        if (result == 1) {

        } else if (result == 2) {
        } else if (result < 0) {
            if (currentSession != null) {
                currentSession.sendShockPacket();
            }
            disembark(false);
        }
    }

    public static boolean isWrench(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return AllItemTags.CHAIN_RIDEABLE.matches(stack)
            || stack.is(WireRiderTags.WRENCHES)
            || stack.is(WireRiderTags.ZIPLINE_TOOLS)
            || stack.getItem().getDescriptionId().contains("wrench")
            || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("wrench");
    }

    public static ItemStack getActiveWrench(Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (isWrench(player.getMainHandItem())) return player.getMainHandItem();
        if (isWrench(player.getOffhandItem())) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static boolean hasWrench(Player player) {
        return !getActiveWrench(player).isEmpty();
    }

    public static void embark(IRidableWireSession session) {
        if (session == null || !session.isValid()) return;
        if (isRiding()) return;
        if (remountCooldown > 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<Vec3> points = session.getPoints();
        if (points == null || points.size() < 2) return;

        currentSession = session;
        wirePoints = points;
        groundedTimer = 0;
        overheatBurnTicks = 0;
        tickCounter = 0;
        currentNormal = Vec3.ZERO;
        lastExitedWire = null;
        lastWireCooldown = 0;
        laneSwitchCooldown = 0;
        wasRidingBeforeDisconnect = true;
        reconnectMountTicks = 0;
        savedResumePos = null;
        writeResumeFile(mc.player.position());

        isEnergized = false;
        currentVoltage = 0.0f;
        qteController = null;

        mc.player.getAbilities().flying = false;
        mc.player.stopFallFlying();

        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1.0F, 0.5F));
        session.sendStartPacket();
    }

    public static void jumpFromWire() {
        if (currentSession == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (qteController != null && qteController.isQTEActive()) {
            currentSession.sendShockPacket();
        }

        overheatBurnTicks = 0;
        wasRidingBeforeDisconnect = false;
        savedResumePos = null;
        deleteResumeFile();

        lastExitedWire = currentSession.getWireIdentity();
        lastWireCooldown = 12;
        remountCooldown = 6;

        Vec3 vel = mc.player.getDeltaMovement();
        Vec3 normal = currentNormal;
        double speedAlong = vel.dot(normal);

        Vec3 look = mc.player.getLookAngle();
        Vec3 lookHoriz = new Vec3(look.x, 0.0, look.z);
        if (lookHoriz.lengthSqr() > 1e-4) {
            lookHoriz = lookHoriz.normalize();
        } else {
            lookHoriz = new Vec3(0, 0, 1);
        }

        Vec3 jumpDir;
        if (Math.abs(speedAlong) > 0.05) {
            Vec3 wireHoriz = new Vec3(normal.x, 0.0, normal.z);
            if (wireHoriz.lengthSqr() > 1e-4) {
                wireHoriz = wireHoriz.normalize().scale(Math.signum(speedAlong));
                jumpDir = wireHoriz.scale(0.60).add(lookHoriz.scale(0.40)).normalize();
            } else {
                jumpDir = lookHoriz;
            }
        } else {
            jumpDir = lookHoriz;
        }

        double horizSpeed = Math.max(Math.abs(speedAlong) * 1.45 + 0.35, 0.85);
        double jumpPower = 0.65;
        if (mc.player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP)) {
            jumpPower += 0.12 * (mc.player.getEffect(net.minecraft.world.effect.MobEffects.JUMP).getAmplifier() + 1);
        }

        Vec3 jumpVel = new Vec3(jumpDir.x * horizSpeed, jumpPower, jumpDir.z * horizSpeed);
        mc.player.setDeltaMovement(jumpVel);
        mc.player.fallDistance = 0.0F;

        PacketDistributor.sendToServer(WireRideActionPayload.stop());
        mc.player.playSound(SoundEvents.WIND_CHARGE_BURST.value(), 0.9F, 1.2F);
        mc.player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.1F);

        if (mc.level != null) {
            for (int i = 0; i < 8; i++) {
                mc.level.addParticle(ParticleTypes.CLOUD, mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ(),
                    -jumpDir.x * 0.2 + (Math.random() - 0.5) * 0.15,
                    0.05 + Math.random() * 0.08,
                    -jumpDir.z * 0.2 + (Math.random() - 0.5) * 0.15);
            }
        }

        currentSession = null;
        wirePoints = null;
        qteController = null;
        groundedTimer = 0;
        currentNormal = Vec3.ZERO;
    }

    public static void disembark(boolean slingshot) {
        if (currentSession == null) return;
        Minecraft mc = Minecraft.getInstance();

        if (qteController != null && qteController.isQTEActive()) {
            currentSession.sendShockPacket();
        }

        if (!isLoggingOut) {
            wasRidingBeforeDisconnect = false;
            savedResumePos = null;
            deleteResumeFile();
        }

        if (mc.player != null) {
            lastExitedWire = currentSession.getWireIdentity();
            lastWireCooldown = 12;
            remountCooldown = 8;

            Vec3 vel = mc.player.getDeltaMovement();
            if (slingshot) {
                if (vel.lengthSqr() > 0.01) {
                    mc.player.setDeltaMovement(vel.scale(1.2).add(0.0, 0.42, 0.0));
                } else {
                    mc.player.setDeltaMovement(new Vec3(0.0, 0.42, 0.0));
                }
            } else {
                mc.player.setDeltaMovement(new Vec3(vel.x * 0.6, Math.min(vel.y, 0.0), vel.z * 0.6));
            }
            mc.player.fallDistance = 0.0F;
        }

        PacketDistributor.sendToServer(WireRideActionPayload.stop());
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.75F, 0.35F));

        currentSession = null;
        wirePoints = null;
        qteController = null;
        groundedTimer = 0;
        overheatBurnTicks = 0;
        currentNormal = Vec3.ZERO;
        laneSwitchCooldown = 0;
        unenergizedGraceTicks = 0;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (mc.player != lastPlayerInstance) {
            lastPlayerInstance = mc.player;
            checkResumeOnLogin(mc);
        }

        if (remountCooldown > 0) {
            remountCooldown--;
        }
        if (laneSwitchCooldown > 0) {
            laneSwitchCooldown--;
        }
        if (lastWireCooldown > 0) {
            lastWireCooldown--;
            if (lastWireCooldown == 0) {
                lastExitedWire = null;
            }
        }

        if (currentSession != null) {
            ridingTick();
        } else {
            groundedTimer = 0;

            if (reconnectMountTicks > 0) {
                reconnectMountTicks--;
                if (hasWrench(mc.player)) {
                    if (savedResumePos != null) {
                        mc.player.setPos(savedResumePos.x, savedResumePos.y, savedResumePos.z);
                    }
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    mc.player.fallDistance = 0.0F;

                    float chainYOffset = 0.66F * mc.player.getScale();
                    Vec3 searchPos = (savedResumePos != null ? savedResumePos : mc.player.position())
                        .add(0.0, mc.player.getBoundingBox().getYsize() + chainYOffset, 0.0);
                    double grabDistSq = 8.0 * 8.0;

                    IRidableWireSession resumeSession = null;
                    if (ModList.get().isLoaded("electroenergetics")) {
                        resumeSession = CEECompat.findClosestWire(searchPos, grabDistSq, mc.level);
                    }
                    if (resumeSession == null && ModList.get().isLoaded("powergrid")) {
                        resumeSession = PowerGridCompat.findClosestWire(searchPos, grabDistSq, mc.level);
                    }

                    if (resumeSession != null && resumeSession.isValid()) {
                        embark(resumeSession);
                        reconnectMountTicks = 0;
                        savedResumePos = null;
                        deleteResumeFile();
                    } else if (reconnectMountTicks == 0) {
                        savedResumePos = null;
                        deleteResumeFile();
                    }
                } else {
                    reconnectMountTicks = 0;
                    savedResumePos = null;
                    deleteResumeFile();
                }
            }

            if (wrenchHeat > 0.0f) {
                boolean inWaterOrRain = mc.player.isInWaterOrRain() || mc.level.isRainingAt(mc.player.blockPosition());
                if (inWaterOrRain) {
                    if (wrenchHeat > 0.12f && mc.player.tickCount % 4 == 0) {
                        mc.player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.25f, 1.5f + (float) Math.random() * 0.3f);
                        mc.level.addParticle(ParticleTypes.SMOKE, mc.player.getX(), mc.player.getY() + 1.2, mc.player.getZ(), 0.0, 0.03, 0.0);
                    }
                    wrenchHeat = Math.max(0.0f, wrenchHeat - 0.030f);
                } else {
                    wrenchHeat = Math.max(0.0f, wrenchHeat - 0.0083f);
                }
                continuousRideTicks = Math.max(0, continuousRideTicks - 4);
            }

            if (remountCooldown == 0 && mc.options.keyUse.isDown() && !mc.player.isShiftKeyDown() && hasWrench(mc.player)) {
                holdUseSearch();
            }
        }
    }

    private static void ridingTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (!hasWrench(player)) {
            disembark(false);
            return;
        }

        float chainYOffset = 0.66F * player.getScale();
        Vec3 handsPos = player.position().add(0.0, player.getBoundingBox().getYsize() + chainYOffset, 0.0);

        ClosestQuery query = getClosestPointOnWire(wirePoints, handsPos);
        Vec3 target = query.position();
        Vec3 diff = target.subtract(handsPos);
        Vec3 normal = query.normal();
        currentNormal = normal;

        Vec3 delta = player.getDeltaMovement();
        double currentSpeed = Math.abs(delta.dot(normal));

        if (player.onGround() && currentSpeed < 0.25) {
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

        double endThreshold = Math.max(1.3, currentSpeed * 1.5);
        double endThresholdSq = endThreshold * endThreshold;
        boolean isStart = query.position().distanceToSqr(wirePoints.get(0)) < endThresholdSq;
        boolean isEnd = query.position().distanceToSqr(wirePoints.get(wirePoints.size() - 1)) < endThresholdSq;

        float steerRight = -player.input.leftImpulse;
        if (laneSwitchCooldown == 0 && Math.abs(steerRight) > 0.5) {
            attemptLaneSwitch(steerRight);
        }

        if (delta.lengthSqr() > 1.0E-6) {
            double dotNormal = delta.normalize().dot(normal);
            if (isEnd && dotNormal > 0.4) {
                IRidableWireSession next = currentSession.findConnectedWire(wirePoints.get(wirePoints.size() - 1), mc.level, delta, steerRight);
                if (next != null && next.isValid()) {
                    switchSession(next);
                    return;
                } else {
                    jumpFromWire();
                    return;
                }
            } else if (isStart && dotNormal < -0.4) {
                IRidableWireSession next = currentSession.findConnectedWire(wirePoints.get(0), mc.level, delta, steerRight);
                if (next != null && next.isValid()) {
                    switchSession(next);
                    return;
                } else {
                    jumpFromWire();
                    return;
                }
            }
        }

        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3.0;
        reach = Math.max(6.0, reach);
        if (diff.lengthSqr() > reach * reach) {
            disembark(false);
            return;
        }

        double diffAlong = diff.dot(normal);
        Vec3 perpDiff = diff.subtract(normal.scale(diffAlong));

        double speedAlong = delta.dot(normal);

        double forward = player.input.forwardImpulse;
        if (Math.abs(forward) > 0.05) {
            Vec3 look = player.getLookAngle();
            double lookDot = look.dot(normal);
            double driveDir = (lookDot >= 0) ? 1.0 : -1.0;
            double power = (player.isSprinting() ? 0.075 : 0.052) * forward;
            speedAlong += driveDir * power;
        }

        boolean inWaterOrRain = player.isInWaterOrRain() || mc.level.isRainingAt(player.blockPosition());
        ItemStack wrench = getActiveWrench(player);
        boolean lubricated = WrenchLubricationHandler.isLubricated(wrench);

        speedAlong += (-normal.y * 0.038);
        double friction = 0.985;
        if (inWaterOrRain) {
            friction = 0.993;
        } else if (lubricated) {
            friction = 0.991;
        }
        speedAlong *= friction;
        speedAlong = Mth.clamp((float) speedAlong, -2.1F, 2.1F);

        Vec3 perpCorrection = perpDiff.scale(0.85);
        if (perpCorrection.lengthSqr() > 0.5 * 0.5) {
            perpCorrection = perpCorrection.normalize().scale(0.5);
        }

        Vec3 newDelta = normal.scale(speedAlong).add(perpCorrection);
        player.setDeltaMovement(newDelta);
        player.fallDistance = 0.0F;

        if (qteController != null && (isEnergized || unenergizedGraceTicks <= 15)) {
            int qteRes = qteController.tick(true);
            if (qteRes != 0) {
                handleQTEResult(qteRes);
                if (currentSession == null || wirePoints == null || wirePoints.size() < 2) return;
            }
        }

        if (++tickCounter % 10 == 0) {
            currentSession.sendHeartbeatPacket();
            writeResumeFile(player.position());
        }

        if (lubricated) {
            WrenchLubricationHandler.tickLubrication(wrench, inWaterOrRain);
            continuousRideTicks = 0;
            wrenchHeat = Math.max(0.0f, wrenchHeat - 0.025f);
        } else if (inWaterOrRain) {
            continuousRideTicks = Math.max(0, continuousRideTicks - 8);
            if (wrenchHeat > 0.0f) {
                if (wrenchHeat > 0.12f && tickCounter % 4 == 0) {
                    player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.25f, 1.5f + (float) Math.random() * 0.3f);
                    mc.level.addParticle(ParticleTypes.SMOKE, target.x, target.y - 0.05, target.z,
                        (Math.random() - 0.5) * 0.05, 0.04, (Math.random() - 0.5) * 0.05);
                }
                wrenchHeat = Math.max(0.0f, wrenchHeat - 0.030f);
            }
        } else if (currentSpeed > 0.15) {
            continuousRideTicks++;
            int heatThreshold = isEnergized ? 500 : 900;
            if (continuousRideTicks > heatThreshold) {
                float heatRate = isEnergized ? 0.0038f : 0.0028f;
                wrenchHeat += heatRate;
                if (wrenchHeat > 1.0f) {
                    wrenchHeat = 1.0f;
                }
            }
        } else {
            continuousRideTicks = Math.max(0, continuousRideTicks - 4);
            wrenchHeat = Math.max(0.0f, wrenchHeat - 0.0083f);
        }

        if (wrenchHeat >= 0.85f && wrenchHeat < 1.0f) {
            if (tickCounter % 12 == 0) {
                mc.level.addParticle(ParticleTypes.SMOKE, target.x, target.y - 0.02, target.z, 0.0, 0.02, 0.0);
            }
            if (tickCounter % 20 == 0) {
                player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.12f, 1.8f);
            }
        }

        if (wrenchHeat >= 1.0f) {
            overheatBurnTicks++;
            if (overheatBurnTicks >= 20 || overheatBurnTicks == 1) {
                if (overheatBurnTicks >= 20) {
                    overheatBurnTicks = 1;
                }
                PacketDistributor.sendToServer(
                    new WireRideActionPayload(WireRideActionPayload.ACTION_OVERHEAT_BURN, (byte) 0, new net.minecraft.nbt.CompoundTag())
                );
                player.playSound(SoundEvents.PLAYER_HURT_ON_FIRE, 0.8f, 1.0f);
                player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.2f);
                player.hurtTime = 10;
            }
            for (int i = 0; i < 2; i++) {
                mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, target.x, target.y, target.z,
                    (Math.random() - 0.5) * 0.08, 0.06, (Math.random() - 0.5) * 0.08);
                mc.level.addParticle(ParticleTypes.FLAME, target.x, target.y, target.z,
                    (Math.random() - 0.5) * 0.04, 0.03, (Math.random() - 0.5) * 0.04);
            }
        } else {
            overheatBurnTicks = 0;
        }

        if (isEnergized && wirePoints != null && wirePoints.size() >= 2 && currentSession != null) {
            double speed = delta.length();
            if (speed > 0.04) {
                int count = Math.min(4, 1 + (int)(speed * 3.0));
                Vec3 sparkDir = delta.normalize().scale(-0.15);
                for (int i = 0; i < count; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, target.x, target.y - 0.03, target.z,
                        sparkDir.x + (Math.random() - 0.5) * 0.1,
                        sparkDir.y + Math.random() * 0.08,
                        sparkDir.z + (Math.random() - 0.5) * 0.1);
                }
            }

            if (wireTemp > 35.0f && tickCounter % 3 == 0) {
                mc.level.addParticle(ParticleTypes.SMOKE, target.x, target.y + 0.05, target.z,
                    0.0, 0.05, 0.0);
            }
            if (wireTemp > 75.0f && tickCounter % 3 == 0) {
                mc.level.addParticle(ParticleTypes.FLAME, target.x, target.y + 0.05, target.z,
                    (Math.random() - 0.5) * 0.05, 0.02, (Math.random() - 0.5) * 0.05);
            }

            boolean isRaining = mc.level.isRainingAt(player.blockPosition());
            if (isRaining && tickCounter % 2 == 0) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, target.x, target.y, target.z,
                    (Math.random() - 0.5) * 0.22, 0.06, (Math.random() - 0.5) * 0.22);
            }

            double distToNode1 = query.position().distanceToSqr(wirePoints.get(0));
            double distToNode2 = query.position().distanceToSqr(wirePoints.get(wirePoints.size() - 1));
            boolean nearStart = distToNode1 < 4.0;
            boolean nearEnd = distToNode2 < 4.0;

            if (nearStart || nearEnd) {
                Vec3 polePos = nearStart ? wirePoints.get(0) : wirePoints.get(wirePoints.size() - 1);
                Vec3 wrenchPos = target;

                for (double t = 0.0; t <= 1.0; t += 0.25) {
                    double px = Mth.lerp(t, wrenchPos.x, polePos.x) + (Math.random() - 0.5) * 0.15;
                    double py = Mth.lerp(t, wrenchPos.y, polePos.y) + (Math.random() - 0.5) * 0.15;
                    double pz = Mth.lerp(t, wrenchPos.z, polePos.z) + (Math.random() - 0.5) * 0.15;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz,
                        (Math.random() - 0.5) * 0.12, 0.04, (Math.random() - 0.5) * 0.12);
                }

                if (tickCounter % 4 == 0) {
                    mc.player.playSound(SoundEvents.FLINTANDSTEEL_USE, 0.8f, 2.0f);
                }

                if (currentVoltage > 5000.0f && Math.min(distToNode1, distToNode2) < 1.0 && qteController != null && !qteController.isQTEActive()) {
                    if (tickCounter % 15 == 0) {
                        qteController.triggerImmediateHazard();
                    }
                }
            }

            GroundHit ground = checkGroundUnder(mc.level, player.position());
            if (ground != null && ground.distance < 2.8) {
                Vec3 start = player.position().add(0, 0.2, 0);
                Vec3 end = ground.hitPos;

                for (double t = 0.0; t <= 1.0; t += 0.2) {
                    double px = Mth.lerp(t, start.x, end.x) + (Math.random() - 0.5) * 0.15;
                    double py = Mth.lerp(t, start.y, end.y);
                    double pz = Mth.lerp(t, start.z, end.z) + (Math.random() - 0.5) * 0.15;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz,
                        (Math.random() - 0.5) * 0.08, -0.05, (Math.random() - 0.5) * 0.08);
                }

                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 0.05, end.z,
                    (Math.random() - 0.5) * 0.3, 0.1, (Math.random() - 0.5) * 0.3);

                if (ground.isWater) {
                    mc.level.addParticle(ParticleTypes.SPLASH, end.x, end.y + 0.05, end.z,
                        (Math.random() - 0.5) * 0.2, 0.15, (Math.random() - 0.5) * 0.2);
                }

                if (tickCounter % 5 == 0) {
                    mc.player.playSound(SoundEvents.FLINTANDSTEEL_USE, 0.7f, 1.8f);
                }

                if (ground.distance < 0.85 && tickCounter % 8 == 0) {
                    currentSession.sendShockPacket();
                    disembark(false);
                    return;
                }
            }
        }
    }

    private static class GroundHit {
        public final double distance;
        public final boolean isWater;
        public final Vec3 hitPos;

        public GroundHit(double distance, boolean isWater, Vec3 hitPos) {
            this.distance = distance;
            this.isWater = isWater;
            this.hitPos = hitPos;
        }
    }

    private static GroundHit checkGroundUnder(Level level, Vec3 pos) {
        BlockPos base = BlockPos.containing(pos);
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos bp = base.below(dy);
            var state = level.getBlockState(bp);
            if (!state.isAir()) {
                double topY = bp.getY() + 1.0;
                double dist = pos.y - topY;
                boolean water = state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                return new GroundHit(dist, water, new Vec3(pos.x, topY, pos.z));
            }
        }
        return null;
    }

    private static void switchSession(IRidableWireSession next) {
        Minecraft mc = Minecraft.getInstance();
        double speed = 0.0;
        if (mc.player != null && currentNormal.lengthSqr() > 0.01) {
            speed = Math.abs(mc.player.getDeltaMovement().dot(currentNormal));
        }
        lastExitedWire = currentSession != null ? currentSession.getWireIdentity() : null;
        lastWireCooldown = 10;

        currentSession = next;
        wirePoints = next.getPoints();
        if (mc.player != null && wirePoints != null && wirePoints.size() >= 2) {
            float chainYOffset = 0.66F * mc.player.getScale();
            Vec3 handsPos = mc.player.position().add(0.0, mc.player.getBoundingBox().getYsize() + chainYOffset, 0.0);
            ClosestQuery q = getClosestPointOnWire(wirePoints, handsPos);
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

        float chainYOffset = 0.66F * player.getScale();
        Vec3 handsPos = player.position().add(0.0, player.getBoundingBox().getYsize() + chainYOffset, 0.0);
        Vec3 vel = player.getDeltaMovement();
        Vec3 normal = currentNormal;
        double speedAlong = vel.dot(normal);

        Vec3 forwardDir;
        if (Math.abs(speedAlong) > 0.05) {
            forwardDir = normal.scale(Math.signum(speedAlong));
        } else {
            Vec3 look = player.getLookAngle();
            forwardDir = new Vec3(look.x, 0.0, look.z).normalize();
        }

        Vec3 right = forwardDir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 desiredLateralDir = (dir > 0) ? right : right.scale(-1.0);

        Object currentWireId = currentSession.getWireIdentity();
        IRidableWireSession next = null;

        if (ModList.get().isLoaded("electroenergetics")) {
            next = CEECompat.findParallelWire(handsPos, desiredLateralDir, normal, currentWireId, mc.level);
        }
        if (next == null && ModList.get().isLoaded("powergrid")) {
            next = PowerGridCompat.findParallelWire(handsPos, desiredLateralDir, normal, currentWireId, mc.level);
        }

        if (next != null && next.isValid()) {
            switchSession(next);
            laneSwitchCooldown = 10;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1.3F, 0.45F));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_SHOOT, 1.6F, 0.3F));

            Vec3 lateralNudge = desiredLateralDir.scale(0.22);
            player.setDeltaMovement(player.getDeltaMovement().add(lateralNudge));
            return true;
        }

        return false;
    }

    private static void holdUseSearch() {
        if (remountCooldown > 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float chainYOffset = 0.66F * mc.player.getScale();
        Vec3 handsPos = mc.player.position().add(0.0, mc.player.getBoundingBox().getYsize() + chainYOffset, 0.0);
        Vec3 camPos = mc.player.getEyePosition();
        Vec3 camLook = mc.player.getLookAngle();
        double grabDistSq = 3.8 * 3.8;
        Object excluded = (lastWireCooldown > 0) ? lastExitedWire : null;

        IRidableWireSession bestSession = null;
        double bestScore = Double.MAX_VALUE;

        if (ModList.get().isLoaded("electroenergetics")) {
            IRidableWireSession ceeSession = CEECompat.searchHoldToGrab(handsPos, grabDistSq, mc.level, camPos, camLook, excluded);
            if (ceeSession != null) {
                double score = getWireTargetScore(ceeSession.getPoints(), handsPos, camPos, camLook);
                if (score < bestScore) {
                    bestScore = score;
                    bestSession = ceeSession;
                }
            }
        }

        if (ModList.get().isLoaded("powergrid")) {
            IRidableWireSession pgSession = PowerGridCompat.searchHoldToGrab(handsPos, grabDistSq, mc.level, camPos, camLook, excluded);
            if (pgSession != null) {
                double score = getWireTargetScore(pgSession.getPoints(), handsPos, camPos, camLook);
                if (score < bestScore) {
                    bestScore = score;
                    bestSession = pgSession;
                }
            }
        }

        if (bestSession != null) {
            embark(bestSession);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static double getWireTargetScore(List<Vec3> points, Vec3 handsPos, Vec3 camPos, Vec3 camLook) {
        if (points == null || points.size() < 2) return Double.MAX_VALUE;
        double rayDist = getRayDistanceToWire(points, camPos, camLook);
        ClosestQuery query = getClosestPointOnWire(points, handsPos);
        double handsDist = query.position().distanceTo(handsPos);
        return rayDist * 3.0 + handsDist;
    }

    public static double getRayDistanceToWire(List<Vec3> points, Vec3 rayStart, Vec3 rayDir) {
        if (points == null || points.size() < 2 || rayStart == null || rayDir == null) return Double.MAX_VALUE;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            double dist = getRayDistanceToSegment(rayStart, rayDir, a, b);
            if (dist < minDistance) {
                minDistance = dist;
            }
        }
        return minDistance;
    }

    public static double getRayDistanceToSegment(Vec3 rayStart, Vec3 rayDir, Vec3 segA, Vec3 segB) {
        Vec3 u = rayDir.normalize();
        Vec3 segDiff = segB.subtract(segA);
        double segLen = segDiff.length();
        if (segLen < 1.0E-6) {
            Vec3 w = segA.subtract(rayStart);
            double s = Math.max(0.0, w.dot(u));
            return rayStart.add(u.scale(s)).distanceTo(segA);
        }
        Vec3 v = segDiff.scale(1.0 / segLen);
        Vec3 w = rayStart.subtract(segA);

        double a = 1.0;
        double b = u.dot(v);
        double c = 1.0;
        double d = u.dot(w);
        double e = v.dot(w);
        double denom = a * c - b * b;
        double sc, tc;

        if (denom < 1.0E-5) {
            sc = Math.max(0.0, d);
            tc = 0.0;
        } else {
            sc = (b * e - c * d) / denom;
            tc = (a * e - b * d) / denom;
        }

        sc = Math.max(0.0, sc);
        if (sc > 5.5) return Double.MAX_VALUE;

        tc = Mth.clamp(tc, 0.0, segLen);

        Vec3 pRay = rayStart.add(u.scale(sc));
        Vec3 pSeg = segA.add(v.scale(tc));
        return pRay.distanceTo(pSeg);
    }

    public static ClosestQuery getClosestPointOnWire(List<Vec3> points, Vec3 pos) {
        double minDistanceSquared = Double.MAX_VALUE;
        Vec3 minPoint = points.get(0);
        Vec3 minNormal = new Vec3(0.0, 0.0, 1.0);

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 diff = b.subtract(a);
            double len = diff.length();
            if (len < 1.0E-6) continue;
            Vec3 dir = diff.scale(1.0 / len);

            Vec3 toPos = pos.subtract(a);
            double along = Mth.clamp(toPos.dot(dir), 0.0, len);
            Vec3 pt = a.add(dir.scale(along));
            double distSq = pt.distanceToSqr(pos);
            if (distSq < minDistanceSquared) {
                minDistanceSquared = distSq;
                minPoint = pt;
                minNormal = dir;
            }
        }
        return new ClosestQuery(minPoint, minNormal);
    }

    public record ClosestQuery(Vec3 position, Vec3 normal) {}
}
