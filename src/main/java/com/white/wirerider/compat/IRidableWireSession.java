package com.white.wirerider.compat;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public interface IRidableWireSession {
    byte TYPE_NONE = 0;
    byte TYPE_CEE = 1;
    byte TYPE_POWERGRID = 2;

    byte getWireType();

    List<Vec3> getPoints();

    boolean isValid();

    Object getWireIdentity();

    IRidableWireSession findConnectedWire(Vec3 currentEndPos, Level level, Vec3 travelDir, float steerX);

    default IRidableWireSession findConnectedWire(Vec3 currentEndPos, Level level) {
        return findConnectedWire(currentEndPos, level, Vec3.ZERO, 0.0f);
    }

    void sendStartPacket();

    void sendHeartbeatPacket();

    void sendShockPacket();
}

