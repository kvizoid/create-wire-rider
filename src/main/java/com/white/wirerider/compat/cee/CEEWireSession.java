package com.white.wirerider.compat.cee;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.white.wirerider.network.WireRideActionPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CEEWireSession {
    private final InWorldNodeConnection connection;
    private final List<Vec3> points;

    public CEEWireSession(InWorldNodeConnection connection, Level level, Vec3 entryPos) {
        this.connection = connection;
        List<Vec3> rawPoints = CEEClientWireHelper.calculatePoints(connection, level);
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

    public List<Vec3> getPoints() {
        return points;
    }

    public boolean isValid() {
        return connection != null && points.size() >= 2;
    }

    public void sendStartPacket() {
        PacketDistributor.sendToServer(CEECompat.createActionPayload(WireRideActionPayload.ACTION_START, connection));
    }

    public void sendHeartbeatPacket() {
        PacketDistributor.sendToServer(CEECompat.createActionPayload(WireRideActionPayload.ACTION_HEARTBEAT, connection));
    }

    public void sendShockPacket() {
        PacketDistributor.sendToServer(CEECompat.createActionPayload(WireRideActionPayload.ACTION_GROUND_SHOCK, connection));
    }

    public void sendThermalBurnPacket() {
        PacketDistributor.sendToServer(CEECompat.createActionPayload(WireRideActionPayload.ACTION_THERMAL_BURN, connection));
    }
}
