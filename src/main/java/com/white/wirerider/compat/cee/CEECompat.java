package com.white.wirerider.compat.cee;

import com.george_vi.electroenergetics.CEEDamageTypes;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.white.wirerider.WireRiderMod;
import com.white.wirerider.client.WireZiplineClientManager;
import com.white.wirerider.compat.IRidableWireSession;
import com.white.wirerider.network.WireRideActionPayload;
import com.white.wirerider.network.WireSyncStatusPayload;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class CEECompat {
    public static final ResourceKey<Registry<WireInteractionBehaviour>> WIRE_INTERACTION_REGISTRY_KEY =
        ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("electroenergetics", "wire_interaction_behaviour"));

    private static final DeferredRegister<WireInteractionBehaviour> WIRE_INTERACTIONS =
        DeferredRegister.create(WIRE_INTERACTION_REGISTRY_KEY, WireRiderMod.MOD_ID);

    static {
        WIRE_INTERACTIONS.register("wrench_ride", WrenchRideBehaviour::new);
    }

    public static void init(IEventBus modEventBus) {
        WIRE_INTERACTIONS.register(modEventBus);
    }

    public static WireRideActionPayload createActionPayload(int action, InWorldNodeConnection conn) {
        CompoundTag tag = new CompoundTag();
        if (conn != null) {
            tag.putLong("p1", conn.node1().sourcePos().asLong());
            tag.putInt("i1", conn.node1().id());
            tag.putLong("p2", conn.node2().sourcePos().asLong());
            tag.putInt("i2", conn.node2().id());
        }
        return new WireRideActionPayload(action, IRidableWireSession.TYPE_CEE, tag);
    }

    public static InWorldNodeConnection connectionFromTag(CompoundTag tag) {
        if (tag == null || !tag.contains("p1") || !tag.contains("p2")) return null;
        BlockPos pos1 = BlockPos.of(tag.getLong("p1"));
        int id1 = tag.getInt("i1");
        BlockPos pos2 = BlockPos.of(tag.getLong("p2"));
        int id2 = tag.getInt("i2");
        return new InWorldNodeConnection(new InWorldNode(id1, pos1), new InWorldNode(id2, pos2));
    }

    public static void handleServerRideSync(ServerPlayer sp, CompoundTag tag) {
        InWorldNodeConnection conn = connectionFromTag(tag);
        if (conn == null) return;

        ServerLevel level = sp.serverLevel();
        boolean energized = false;
        float voltage = 0.0f;
        float current = 0.0f;
        float temperature = 20.0f;
        float dangerousDistance = 0.5f;

        try {
            InfrastructureSavedData sd = InfrastructureSavedData.load(level);
            if (sd != null && sd.wireSimulationState != null) {
                ConnectionEntry entry = sd.wireSimulationState.getConnection(conn);
                if (entry != null && entry.wireData != null && entry.wireData.wireType() != null) {
                    var wireType = entry.wireData.wireType();
                    temperature = entry.wireData.temperature;
                    dangerousDistance = (float) Math.sqrt(Math.max(0.1, entry.dangerousDistanceSqr));

                    if (!wireType.isDecorative()) {
                        double v = 0.0;
                        if (sd.ticker != null && sd.ticker.lastResults != null) {
                            v = Math.abs(entry.getWireVoltageToGround(sd.ticker.lastResults, conn.node1(), conn.node2()));
                            if (v <= 0.0) {
                                try {
                                    double v1 = Math.abs(sd.ticker.lastResults.getVoltageAt(conn.node1()));
                                    double v2 = Math.abs(sd.ticker.lastResults.getVoltageAt(conn.node2()));
                                    v = Math.max(v1, v2);
                                } catch (Throwable ignored) {}
                            }
                            try {
                                current = (float) Math.abs(sd.ticker.lastResults.getCurrentThrough(conn.node1(), conn.node2()));
                            } catch (Throwable ignored) {}
                        }
                        voltage = (float) v;

                        boolean hasInsulation = wireType.insulated();
                        boolean brokenInsulation = entry.isOvervolted || (hasInsulation && v > wireType.maxInsulationVoltage());

                        if (v > 50.0 && (!hasInsulation || brokenInsulation)) {
                            energized = true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            WireRiderMod.LOGGER.warn("Failed to check CEE wire circuit status", t);
        }

        PacketDistributor.sendToPlayer(sp, new WireSyncStatusPayload(
            energized, voltage, current, temperature, dangerousDistance
        ));
    }

    public static void handleServerShock(ServerPlayer sp, CompoundTag tag) {
        InWorldNodeConnection conn = connectionFromTag(tag);
        ServerLevel level = sp.serverLevel();
        double voltage = 0.0;

        if (conn != null) {
            try {
                InfrastructureSavedData sd = InfrastructureSavedData.load(level);
                if (sd != null && sd.wireSimulationState != null) {
                    ConnectionEntry entry = sd.wireSimulationState.getConnection(conn);
                    if (entry != null) {
                        if (sd.ticker != null && sd.ticker.lastResults != null) {
                            voltage = Math.abs(entry.getWireVoltageToGround(sd.ticker.lastResults, conn.node1(), conn.node2()));
                        }
                        if (voltage <= 0.0 && entry.wireData != null && entry.wireData.wireType() != null) {
                            voltage = entry.wireData.wireType().maxInsulationVoltage();
                        }
                    }
                }
            } catch (Throwable t) {
                WireRiderMod.LOGGER.warn("Failed to calculate CEE wire voltage for shock damage", t);
            }
        }

        float damage = 4.0f;
        if (voltage > 0.0) {
            damage = (float) Math.max(4.0, voltage / 2500.0);
        }

        DamageSource ds;
        try {
            var reg = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            var key = voltage > 9900.0 ? CEEDamageTypes.HV_ELECTROCUTION : CEEDamageTypes.ELECTROCUTION;
            ds = new DamageSource(reg.getHolderOrThrow(key));
        } catch (Throwable t) {
            ds = sp.damageSources().lightningBolt();
        }

        sp.hurt(ds, damage);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.8f);
    }

    public static IRidableWireSession searchHoldToGrab(Vec3 handsPos, double grabDistSq, Level level) {
        return searchHoldToGrab(handsPos, grabDistSq, level, handsPos, Vec3.ZERO, null);
    }

    public static IRidableWireSession searchHoldToGrab(Vec3 handsPos, double grabDistSq, Level level, Vec3 camPos, Vec3 camLook, Object excludedWire) {
        IRidableWireSession best = null;
        double bestScore = Double.MAX_VALUE;

        for (Pair<InWorldNodeConnection, WireData> pair : WireRenderer.getAllConnections()) {
            InWorldNodeConnection conn = pair.getFirst();
            if (conn == null) continue;
            if (excludedWire != null && excludedWire.equals(conn)) continue;

            List<Vec3> points = CEEWireSession.calculatePoints(conn, level);
            if (points == null || points.size() < 2) continue;

            double rayDist = WireZiplineClientManager.getRayDistanceToWire(points, camPos, camLook);
            if (rayDist > 0.65) continue;

            var query = WireZiplineClientManager.getClosestPointOnWire(points, handsPos);
            double distSq = query.position().distanceToSqr(handsPos);
            if (distSq <= grabDistSq) {
                double score = rayDist * 3.0 + Math.sqrt(distSq);
                if (score < bestScore) {
                    bestScore = score;
                    best = new CEEWireSession(conn, level, handsPos);
                }
            }
        }
        return best;
    }

    public static IRidableWireSession findParallelWire(Vec3 handsPos, Vec3 desiredLateralDir, Vec3 currentTangent, Object currentWire, Level level) {
        if (level == null || handsPos == null || desiredLateralDir == null) return null;
        IRidableWireSession best = null;
        double bestScore = -1000.0;

        for (Pair<InWorldNodeConnection, WireData> pair : WireRenderer.getAllConnections()) {
            InWorldNodeConnection conn = pair.getFirst();
            if (conn == null) continue;
            if (currentWire != null && currentWire.equals(conn)) continue;

            List<Vec3> points = CEEWireSession.calculatePoints(conn, level);
            if (points == null || points.size() < 2) continue;

            var query = WireZiplineClientManager.getClosestPointOnWire(points, handsPos);
            Vec3 pt = query.position();
            Vec3 toPt = pt.subtract(handsPos);
            double distSq = toPt.lengthSqr();
            if (distSq > 3.6 * 3.6 || distSq < 0.25 * 0.25) continue;
            if (Math.abs(toPt.y) > 2.0) continue;

            double lateralProj = toPt.dot(desiredLateralDir);
            if (lateralProj < 0.25) continue;

            double alignDot = Math.abs(query.normal().dot(currentTangent));
            if (alignDot < 0.35) continue;

            double score = (lateralProj * 2.0) - (toPt.length() * 1.5) + (alignDot * 2.0);
            if (score > bestScore) {
                bestScore = score;
                best = new CEEWireSession(conn, level, pt);
            }
        }
        return best;
    }
}

