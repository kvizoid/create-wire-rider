package com.white.wirerider.compat.powergrid;

import com.white.wirerider.WireRiderMod;
import com.white.wirerider.client.WireZiplineClientManager;
import com.white.wirerider.compat.IRidableWireSession;
import com.white.wirerider.network.WireRideActionPayload;
import com.white.wirerider.network.WireSyncStatusPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;

import java.util.List;

public class PowerGridCompat {

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(PowerGridCompat.class);
        WireRiderMod.LOGGER.info("Create: Wire Rider initialized Power Grid compatibility module!");
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof HangingWireEntity wire) {
            Player player = event.getEntity();
            if (player.isShiftKeyDown()) return;

            ItemStack stack = event.getItemStack();
            if (!WireZiplineClientManager.isWrench(stack)) return;

            if (WireZiplineClientManager.isRiding()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            if (event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND) {
                PowerGridWireSession session = new PowerGridWireSession(wire, event.getLevel(), player.position());
                if (session.isValid()) {
                    WireZiplineClientManager.embark(session);
                    player.swing(event.getHand());
                }
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    public static WireRideActionPayload createActionPayload(int action, int entityId) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", entityId);
        return new WireRideActionPayload(action, IRidableWireSession.TYPE_POWERGRID, tag);
    }

    public static void handleServerRideSync(ServerPlayer sp, CompoundTag tag) {
        if (tag == null || !tag.contains("id")) return;
        int entityId = tag.getInt("id");

        ServerLevel level = sp.serverLevel();
        Entity ent = level.getEntity(entityId);
        if (ent instanceof HangingWireEntity wire) {
            boolean isInsulated = wire.isInsulated();
            float temperature = wire.getTemperature();
            float current = wire.current();
            float voltage = 0.0f;

            ElectricWire electricWire = wire.getWire();
            if (electricWire != null) {
                IElectricNode n1 = electricWire.getNode1();
                IElectricNode n2 = electricWire.getNode2();
                double v1 = (n1 != null) ? Math.abs(n1.getVoltage()) : 0.0;
                double v2 = (n2 != null) ? Math.abs(n2.getVoltage()) : 0.0;
                voltage = (float) Math.max(v1, v2);
                if (voltage <= 0.0) {
                    voltage = (float) Math.abs(electricWire.potentialDifference());
                }
            }

            boolean energized = (!isInsulated && voltage > 50.0f);

            PacketDistributor.sendToPlayer(sp, new WireSyncStatusPayload(
                energized, voltage, current, temperature, 0.5f
            ));
        }
    }

    public static void handleServerShock(ServerPlayer sp, CompoundTag tag) {
        if (tag == null || !tag.contains("id")) return;
        int entityId = tag.getInt("id");

        ServerLevel level = sp.serverLevel();
        Entity ent = level.getEntity(entityId);
        double voltage = 0.0;

        if (ent instanceof HangingWireEntity wire) {
            ElectricWire electricWire = wire.getWire();
            if (electricWire != null) {
                IElectricNode n1 = electricWire.getNode1();
                IElectricNode n2 = electricWire.getNode2();
                double v1 = (n1 != null) ? Math.abs(n1.getVoltage()) : 0.0;
                double v2 = (n2 != null) ? Math.abs(n2.getVoltage()) : 0.0;
                voltage = Math.max(v1, v2);
                if (voltage <= 0.0) {
                    voltage = Math.abs(electricWire.potentialDifference());
                }
            }
        }

        float damage = 4.0f;
        if (voltage > 0.0) {
            damage = (float) Math.max(4.0, voltage / 2500.0);
        }

        DamageSource ds = sp.damageSources().lightningBolt();
        sp.hurt(ds, damage);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.8f);
    }

    public static IRidableWireSession findConnectedWire(Vec3 currentEndPos, Level level) {
        return findConnectedWire(currentEndPos, level, Vec3.ZERO, 0.0f);
    }

    public static IRidableWireSession findConnectedWire(Vec3 currentEndPos, Level level, Vec3 travelDir, float steerX) {
        if (level == null || currentEndPos == null) return null;
        AABB aabb = new AABB(
            currentEndPos.x - 2.2, currentEndPos.y - 2.2, currentEndPos.z - 2.2,
            currentEndPos.x + 2.2, currentEndPos.y + 2.2, currentEndPos.z + 2.2
        );
        List<HangingWireEntity> wires = level.getEntitiesOfClass(
            HangingWireEntity.class, aabb,
            e -> e.isAlive() && !e.isRemoved()
        );

        IRidableWireSession best = null;
        double bestScore = -100.0;

        Vec3 unitTravel = (travelDir != null && travelDir.lengthSqr() > 1.0E-6)
            ? travelDir.normalize()
            : Vec3.ZERO;
        Vec3 right = unitTravel.lengthSqr() > 0 ? unitTravel.cross(new Vec3(0, 1, 0)).normalize() : Vec3.ZERO;

        for (HangingWireEntity wire : wires) {
            Vec3 p1 = wire.terminalPos1;
            Vec3 p2 = wire.terminalPos2;
            if (p1 == null || p2 == null) continue;

            double d1 = p1.distanceToSqr(currentEndPos);
            double d2 = p2.distanceToSqr(currentEndPos);
            if (d1 < 1.44 || d2 < 1.44) {
                Vec3 outDir = (d1 <= d2) ? p2.subtract(p1).normalize() : p1.subtract(p2).normalize();
                double score = 1.0;
                if (unitTravel.lengthSqr() > 0) {
                    double forwardDot = outDir.dot(unitTravel);
                    if (forwardDot < -0.35) {
                        score = forwardDot * 5.0;
                    } else {
                        double lateralDot = right.dot(outDir);
                        score = forwardDot * 3.0 + (steerX * lateralDot * 2.5);
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = new PowerGridWireSession(wire, level, currentEndPos);
                }
            }
        }
        return best;
    }

    public static IRidableWireSession searchHoldToGrab(Vec3 handsPos, double grabDistSq, Level level) {
        return searchHoldToGrab(handsPos, grabDistSq, level, handsPos, Vec3.ZERO, null);
    }

    public static IRidableWireSession searchHoldToGrab(Vec3 handsPos, double grabDistSq, Level level, Vec3 camPos, Vec3 camLook, Object excludedWire) {
        if (level == null || handsPos == null) return null;
        AABB aabb = new AABB(
            handsPos.x - 4.0, handsPos.y - 4.0, handsPos.z - 4.0,
            handsPos.x + 4.0, handsPos.y + 4.0, handsPos.z + 4.0
        );
        List<HangingWireEntity> wires = level.getEntitiesOfClass(
            HangingWireEntity.class, aabb,
            e -> e.isAlive() && !e.isRemoved()
        );

        IRidableWireSession best = null;
        double bestScore = Double.MAX_VALUE;

        for (HangingWireEntity wire : wires) {
            if (excludedWire != null && (excludedWire.equals(wire.getId()) || excludedWire.equals(wire))) continue;

            List<Vec3> points = PowerGridWireSession.calculatePoints(wire);
            if (points.size() < 2) continue;

            double rayDist = WireZiplineClientManager.getRayDistanceToWire(points, camPos, camLook);
            if (rayDist > 0.65) continue;

            var query = WireZiplineClientManager.getClosestPointOnWire(points, handsPos);
            double distSq = query.position().distanceToSqr(handsPos);
            if (distSq <= grabDistSq) {
                double score = rayDist * 3.0 + Math.sqrt(distSq);
                if (score < bestScore) {
                    bestScore = score;
                    best = new PowerGridWireSession(wire, level, handsPos);
                }
            }
        }
        return best;
    }

    public static IRidableWireSession findParallelWire(Vec3 handsPos, Vec3 desiredLateralDir, Vec3 currentTangent, Object currentWire, Level level) {
        if (level == null || handsPos == null || desiredLateralDir == null) return null;
        AABB aabb = new AABB(
            handsPos.x - 4.0, handsPos.y - 3.0, handsPos.z - 4.0,
            handsPos.x + 4.0, handsPos.y + 3.0, handsPos.z + 4.0
        );
        List<HangingWireEntity> wires = level.getEntitiesOfClass(
            HangingWireEntity.class, aabb,
            e -> e.isAlive() && !e.isRemoved()
        );

        IRidableWireSession best = null;
        double bestScore = -1000.0;

        for (HangingWireEntity wire : wires) {
            if (currentWire != null && (currentWire.equals(wire.getId()) || currentWire.equals(wire))) continue;

            List<Vec3> points = PowerGridWireSession.calculatePoints(wire);
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
                best = new PowerGridWireSession(wire, level, pt);
            }
        }
        return best;
    }
}

