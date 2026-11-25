package com.white.wirerider;

import com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import com.white.wirerider.compat.WireStatusSnapshot;
import com.white.wirerider.compat.cee.CEECompat;
import com.white.wirerider.network.WireResumeRidePayload;
import com.white.wirerider.network.WireSyncStatusPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side tracking, validation, and per-world reconnect persistence for wire-riding sessions.
 */
public final class ServerWireRideManager {
    private static final String TAG_SAVED_RIDE = "WireRiderSavedRide";
    private static final String TAG_RESUME_GRACE = "WireRiderResumeGrace";
    private static final double MAX_VALIDATE_DIST_SQ = 12.0 * 12.0;
    private static final int HEARTBEAT_TIMEOUT_TICKS = 40;
    private static final int RESUME_GRACE_TICKS = 100;
    private static final int SHOCK_COOLDOWN_TICKS = 10;
    private static final int THERMAL_BURN_COOLDOWN_TICKS = 18;

    private static final class ActiveRide {
        CompoundTag wireData;
        long lastHeartbeatTick;
        long lastShockTick = -100;
        long lastBurnTick = -100;

        ActiveRide(CompoundTag wireData, long tick) {
            this.wireData = wireData.copy();
            this.lastHeartbeatTick = tick;
        }
    }

    private static final Map<UUID, ActiveRide> ACTIVE_RIDES = new ConcurrentHashMap<>();

    private ServerWireRideManager() {}

    public static void handleStartOrHeartbeat(ServerPlayer sp, CompoundTag extraData) {
        if (!WireRiderUtils.hasWrench(sp)) {
            stopRiding(sp);
            return;
        }

        WireStatusSnapshot snapshot = CEECompat.validateAndMeasure(sp, extraData, MAX_VALIDATE_DIST_SQ);
        if (snapshot == null) {
            stopRiding(sp);
            return;
        }

        long now = sp.level().getGameTime();
        ActiveRide ride = ACTIVE_RIDES.computeIfAbsent(sp.getUUID(), id -> new ActiveRide(extraData, now));
        ride.wireData = extraData.copy();
        ride.lastHeartbeatTick = now;

        CompoundTag savedRide = new CompoundTag();
        savedRide.put("Data", extraData.copy());
        savedRide.putDouble("X", sp.getX());
        savedRide.putDouble("Y", sp.getY());
        savedRide.putDouble("Z", sp.getZ());

        sp.getPersistentData().put(TAG_SAVED_RIDE, savedRide);
        sp.getPersistentData().remove(TAG_RESUME_GRACE);

        sp.fallDistance = 0.0f;
        ServerChainConveyorHandler.handleTTLPacket(sp);

        PacketDistributor.sendToPlayer(sp, new WireSyncStatusPayload(
            snapshot.energized(),
            snapshot.voltage(),
            snapshot.current(),
            snapshot.temperature()
        ));
    }

    public static void stopRiding(ServerPlayer sp) {
        ACTIVE_RIDES.remove(sp.getUUID());
        sp.fallDistance = 0.0f;
        sp.getPersistentData().remove(TAG_SAVED_RIDE);
        sp.getPersistentData().remove(TAG_RESUME_GRACE);
        ServerChainConveyorHandler.handleStopRidingPacket(sp);
    }

    public static void handleGroundShock(ServerPlayer sp, CompoundTag extraData) {
        if (!WireRiderConfig.COMMON.enableElectricHazards.get()) return;
        ActiveRide ride = ACTIVE_RIDES.get(sp.getUUID());
        if (ride == null) return;

        long now = sp.level().getGameTime();
        if (now - ride.lastShockTick < SHOCK_COOLDOWN_TICKS) return;
        ride.lastShockTick = now;

        CEECompat.applyServerShock(sp, extraData);
    }

    public static void handleThermalBurn(ServerPlayer sp, CompoundTag extraData) {
        if (!WireRiderConfig.COMMON.enableElectricHazards.get()) return;
        ActiveRide ride = ACTIVE_RIDES.get(sp.getUUID());
        if (ride == null) return;

        long now = sp.level().getGameTime();
        if (now - ride.lastBurnTick < THERMAL_BURN_COOLDOWN_TICKS) return;

        WireStatusSnapshot snapshot = CEECompat.validateAndMeasure(sp, extraData, 16.0 * 16.0);
        if (snapshot == null || snapshot.temperature() < WireRiderUtils.getWireBurnTemp()) return;

        ride.lastBurnTick = now;
        sp.setRemainingFireTicks(Math.max(sp.getRemainingFireTicks(), 40));
        sp.hurt(sp.damageSources().onFire(), 2.0F);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        ActiveRide ride = ACTIVE_RIDES.get(sp.getUUID());
        if (ride != null) {
            long now = sp.level().getGameTime();
            if (!WireRiderUtils.hasWrench(sp) || (now - ride.lastHeartbeatTick) > HEARTBEAT_TIMEOUT_TICKS) {
                stopRiding(sp);
            } else {
                sp.fallDistance = 0.0f;
            }
            return;
        }

        int grace = sp.getPersistentData().getInt(TAG_RESUME_GRACE);
        if (grace > 0) {
            sp.getPersistentData().putInt(TAG_RESUME_GRACE, grace - 1);
            sp.fallDistance = 0.0f;
            sp.resetFallDistance();
            sp.setDeltaMovement(0, 0, 0);
            ServerChainConveyorHandler.handleTTLPacket(sp);
            if (grace == 1) {
                sp.getPersistentData().remove(TAG_RESUME_GRACE);
                sp.getPersistentData().remove(TAG_SAVED_RIDE);
                ServerChainConveyorHandler.handleStopRidingPacket(sp);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ACTIVE_RIDES.remove(sp.getUUID());

        if (sp.getPersistentData().contains(TAG_SAVED_RIDE, 10)) {
            if (!WireRiderUtils.hasWrench(sp)) {
                sp.getPersistentData().remove(TAG_SAVED_RIDE);
                sp.getPersistentData().remove(TAG_RESUME_GRACE);
                return;
            }

            CompoundTag saved = sp.getPersistentData().getCompound(TAG_SAVED_RIDE);
            CompoundTag data = saved.getCompound("Data");
            double x = saved.getDouble("X");
            double y = saved.getDouble("Y");
            double z = saved.getDouble("Z");

            sp.getPersistentData().putInt(TAG_RESUME_GRACE, RESUME_GRACE_TICKS);
            sp.fallDistance = 0.0f;
            sp.resetFallDistance();
            sp.setDeltaMovement(0, 0, 0);
            ServerChainConveyorHandler.handleTTLPacket(sp);

            PacketDistributor.sendToPlayer(sp, new WireResumeRidePayload(data, x, y, z));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ACTIVE_RIDES.remove(sp.getUUID());
            ServerChainConveyorHandler.handleStopRidingPacket(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            stopRiding(sp);
        }
    }
}
