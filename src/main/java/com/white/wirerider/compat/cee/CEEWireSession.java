package com.white.wirerider.compat.cee;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.white.wirerider.compat.IRidableWireSession;
import com.white.wirerider.network.WireRideActionPayload;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CEEWireSession implements IRidableWireSession {
    private final InWorldNodeConnection connection;
    private final List<Vec3> points;

    public CEEWireSession(InWorldNodeConnection connection, Level level) {
        this(connection, level, null);
    }

    public CEEWireSession(InWorldNodeConnection connection, Level level, Vec3 entryPos) {
        this.connection = connection;
        List<Vec3> rawPoints = calculatePoints(connection, level);
        if (rawPoints != null && rawPoints.size() >= 2 && entryPos != null) {

            double dStart = rawPoints.get(0).distanceToSqr(entryPos);
            double dEnd = rawPoints.get(rawPoints.size() - 1).distanceToSqr(entryPos);
            if (dEnd < dStart) {
                List<Vec3> reversed = new ArrayList<>(rawPoints);
                Collections.reverse(reversed);
                this.points = Collections.unmodifiableList(reversed);
            } else {
                this.points = Collections.unmodifiableList(rawPoints);
            }
        } else {
            this.points = rawPoints != null ? Collections.unmodifiableList(rawPoints) : List.of();
        }
    }

    public InWorldNodeConnection getConnection() {
        return connection;
    }

    @Override
    public byte getWireType() {
        return TYPE_CEE;
    }

    @Override
    public List<Vec3> getPoints() {
        return points;
    }

    @Override
    public boolean isValid() {
        return connection != null && points.size() >= 2;
    }

    @Override
    public Object getWireIdentity() {
        return connection;
    }

    @Override
    public IRidableWireSession findConnectedWire(Vec3 currentEndPos, Level level, Vec3 travelDir, float steerX) {
        if (connection == null || level == null || currentEndPos == null) return null;

        IRidableWireSession best = null;
        double bestScore = -100.0;

        Vec3 unitTravel = (travelDir != null && travelDir.lengthSqr() > 1.0E-6)
            ? travelDir.normalize()
            : Vec3.ZERO;
        Vec3 right = unitTravel.lengthSqr() > 0 ? unitTravel.cross(new Vec3(0, 1, 0)).normalize() : Vec3.ZERO;

        InWorldNode currentEndNode = null;
        Vec3 cp1 = connection.node1().getPosition(level);
        Vec3 cp2 = connection.node2().getPosition(level);
        if (cp1 != null && cp2 != null) {
            currentEndNode = (cp1.distanceToSqr(currentEndPos) <= cp2.distanceToSqr(currentEndPos))
                ? connection.node1()
                : connection.node2();
        }

        for (Pair<InWorldNodeConnection, WireData> pair : WireRenderer.getAllConnections()) {
            InWorldNodeConnection other = pair.getFirst();
            if (other == null || other.equals(connection) || com.white.wirerider.client.WireZiplineClientManager.isLastExitedWire(other)) continue;

            Vec3 p1 = other.node1().getPosition(level);
            Vec3 p2 = other.node2().getPosition(level);
            if (p1 == null || p2 == null) continue;

            double d1 = p1.distanceToSqr(currentEndPos);
            double d2 = p2.distanceToSqr(currentEndPos);
            if (d1 < 10.0 || d2 < 10.0) {
                Vec3 outDir = (d1 <= d2) ? p2.subtract(p1).normalize() : p1.subtract(p2).normalize();
                double minDistSq = Math.min(d1, d2);
                boolean exactNode = (currentEndNode != null) &&
                    (currentEndNode.equals(other.node1()) || currentEndNode.equals(other.node2()));

                double score = 0.0;
                if (exactNode) {
                    score += 25.0;
                } else {
                    score -= minDistSq * 2.5;
                }

                if (unitTravel.lengthSqr() > 0) {
                    double forwardDot = outDir.dot(unitTravel);
                    if (forwardDot < -0.2) {
                        score += forwardDot * 25.0;
                    } else {
                        double lateralDot = right.dot(outDir);
                        score += forwardDot * 15.0;
                        if (Math.abs(steerX) > 0.1) {
                            score += steerX * lateralDot * 8.0;
                        }
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = new CEEWireSession(other, level, currentEndPos);
                }
            }
        }

        if (bestScore < 0.2 && ModList.get().isLoaded("powergrid")) {
            IRidableWireSession pgNext = com.white.wirerider.compat.powergrid.PowerGridCompat.findConnectedWire(currentEndPos, level, travelDir, steerX);
            if (pgNext != null) return pgNext;
        }

        return best;
    }

    @Override
    public void sendStartPacket() {
        PacketDistributor.sendToServer(CEECompat.createActionPayload(WireRideActionPayload.ACTION_START, connection));
    }

    @Override
    public void sendHeartbeatPacket() {
        PacketDistributor.sendToServer(CEECompat.createActionPayload(WireRideActionPayload.ACTION_HEARTBEAT, connection));
    }

    @Override
    public void sendShockPacket() {
        PacketDistributor.sendToServer(CEECompat.createActionPayload(WireRideActionPayload.ACTION_SHOCK, connection));
    }

    public static List<Vec3> calculatePoints(InWorldNodeConnection connection, Level level) {
        if (connection == null || level == null) return null;
        Vec3 p1 = connection.node1().getPosition(level);
        Vec3 p2 = connection.node2().getPosition(level);
        if (p1 == null || p2 == null) return null;

        float sag = 0.5F;
        WireData wd = WireRenderer.getConnectionData(connection);
        if (wd != null) {
            sag = wd.getSag(p1.distanceTo(p2));
        }

        return QuadraticWireHelper.cablePoints(p1, p2, sag, 1.0F);
    }
}

