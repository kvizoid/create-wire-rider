package com.white.wirerider.compat.cee;

import com.george_vi.electroenergetics.CEEDamageTypes;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.white.wirerider.WireGeometry;
import com.white.wirerider.WireRiderMod;
import com.white.wirerider.WireRiderUtils;
import com.white.wirerider.compat.WireStatusSnapshot;
import com.white.wirerider.network.WireRideActionPayload;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Common and server-side compatibility for Create: Electro Energetics.
 * Must not reference client-only CEE classes (e.g. WireRenderer).
 */
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
        return new WireRideActionPayload(action, tag);
    }

    public static InWorldNodeConnection connectionFromTag(CompoundTag tag) {
        if (tag == null || !tag.contains("p1") || !tag.contains("p2")) return null;
        BlockPos pos1 = BlockPos.of(tag.getLong("p1"));
        int id1 = tag.getInt("i1");
        BlockPos pos2 = BlockPos.of(tag.getLong("p2"));
        int id2 = tag.getInt("i2");
        return new InWorldNodeConnection(new InWorldNode(id1, pos1), new InWorldNode(id2, pos2));
    }

    public static WireStatusSnapshot validateAndMeasure(ServerPlayer sp, CompoundTag tag, double maxDistSq) {
        InWorldNodeConnection conn = connectionFromTag(tag);
        if (conn == null) return null;

        ServerLevel level = sp.serverLevel();
        try {
            InfrastructureSavedData sd = InfrastructureSavedData.load(level);
            if (sd == null || sd.wireSimulationState == null) return null;

            ConnectionEntry entry = sd.wireSimulationState.getConnection(conn);
            if (entry == null || entry.wireData == null || entry.wireData.wireType() == null) return null;

            List<Vec3> points = (entry.points != null && entry.points.size() >= 2) ? entry.points : null;
            if (points == null) {
                Vec3 p1 = conn.node1().getPosition(level);
                Vec3 p2 = conn.node2().getPosition(level);
                if (p1 == null || p2 == null) return null;
                float sag = entry.wireData.getSag(p1.distanceTo(p2));
                points = QuadraticWireHelper.cablePoints(p1, p2, sag, 1.0F);
            }
            if (points == null || points.size() < 2) return null;

            Vec3 playerAnchor = sp.position().add(0.0, sp.getBoundingBox().getYsize(), 0.0);
            Vec3 closest = WireGeometry.getClosestPointOnWire(points, playerAnchor).position();
            if (closest.distanceToSqr(playerAnchor) > maxDistSq) {
                return null;
            }

            var wireType = entry.wireData.wireType();
            float temperature = entry.wireData.temperature;
            float voltage = 0.0f;
            float current = 0.0f;
            boolean energized = false;

            if (!wireType.isDecorative() && sd.ticker != null && sd.ticker.lastResults != null) {
                double v = Math.abs(entry.getWireVoltageToGround(sd.ticker.lastResults, conn.node1(), conn.node2()));
                if (v <= 0.0) {
                    try {
                        double v1 = Math.abs(sd.ticker.lastResults.getVoltageAt(conn.node1()));
                        double v2 = Math.abs(sd.ticker.lastResults.getVoltageAt(conn.node2()));
                        double vDiff = Math.abs(sd.ticker.lastResults.getVoltageAt(conn.node1(), conn.node2()));
                        v = Math.max(Math.max(v1, v2), vDiff);
                    } catch (Throwable ignored) {}
                }
                try {
                    current = (float) Math.abs(sd.ticker.lastResults.getCurrentThrough(conn.node1(), conn.node2()));
                } catch (Throwable ignored) {}

                voltage = (float) v;
                boolean hasInsulation = wireType.insulated();
                boolean brokenInsulation = entry.isOvervolted || (hasInsulation && v > wireType.maxInsulationVoltage());
                if (v > WireRiderUtils.getLiveVoltageThreshold() && (!hasInsulation || brokenInsulation)) {
                    energized = true;
                }
            }

            return new WireStatusSnapshot(energized, voltage, current, temperature);
        } catch (Throwable t) {
            WireRiderMod.LOGGER.warn("Failed to validate/measure CEE wire", t);
            return null;
        }
    }

    public static void applyServerShock(ServerPlayer sp, CompoundTag tag) {
        WireStatusSnapshot status = validateAndMeasure(sp, tag, 16.0 * 16.0);
        if (status == null || !status.energized()) return;

        float damage = WireRiderUtils.computeShockDamage(status.voltage());
        if (damage <= 0.0f) return;

        ServerLevel level = sp.serverLevel();
        DamageSource ds;
        try {
            var reg = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            var key = status.voltage() > 9900.0f ? CEEDamageTypes.HV_ELECTROCUTION : CEEDamageTypes.ELECTROCUTION;
            ds = new DamageSource(reg.getHolderOrThrow(key));
        } catch (Throwable t) {
            ds = sp.damageSources().lightningBolt();
        }

        sp.hurt(ds, damage);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.8f);
    }
}
