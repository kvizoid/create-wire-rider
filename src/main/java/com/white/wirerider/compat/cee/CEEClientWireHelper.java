package com.white.wirerider.compat.cee;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.white.wirerider.WireGeometry;
import com.white.wirerider.WireRiderUtils;
import com.white.wirerider.client.WireZiplineClientManager;
import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client-only Electro Energetics wire queries (accesses WireRenderer and WireZiplineClientManager).
 */
public final class CEEClientWireHelper {
    private CEEClientWireHelper() {}

    public record HighlightTarget(List<Vec3> points, Vec3 grabPoint) {}

    public record LookedAtWire(InWorldNodeConnection connection, Vec3 hitPoint) {}

    public static LookedAtWire findLookedAtWire(Player player, Level level, double maxRange) {
        if (player == null || level == null) return null;
        Vec3 camPos = player.getEyePosition();
        Vec3 camLook = player.getLookAngle();

        LookedAtWire best = null;
        double bestRayDist = 0.28;

        for (Pair<InWorldNodeConnection, WireData> pair : WireRenderer.getAllConnections()) {
            InWorldNodeConnection conn = pair.getFirst();
            if (conn == null) continue;

            List<Vec3> points = calculatePoints(conn, level);
            WireGeometry.RayHit hit = WireGeometry.findRayHitOnWire(points, camPos, camLook, maxRange);
            if (hit != null && hit.rayDistance() < bestRayDist) {
                bestRayDist = hit.rayDistance();
                best = new LookedAtWire(conn, hit.wirePoint());
            }
        }
        return best;
    }

    public static boolean isRiding() {
        return WireZiplineClientManager.isRiding();
    }

    public static boolean tryEmbarkTargeted(Level level, Player player, InteractionHand hand) {
        if (WireZiplineClientManager.isRiding()) return false;
        NodeConnectionPoint targetedPoint = WireInteractionHandler.targetedPoint;
        if (targetedPoint == null) return false;

        ItemStack stack = player.getItemInHand(hand);
        if (!WireRiderUtils.isWrench(stack)) {
            if (hand != InteractionHand.MAIN_HAND || !WireRiderUtils.isWrench(player.getOffhandItem())) {
                return false;
            }
        }

        CEEWireSession session = new CEEWireSession(targetedPoint.connection(), level, player.position());
        if (session.isValid()) {
            WireZiplineClientManager.embark(session);
            player.swing(hand);
            return true;
        }
        return false;
    }

    public static CEEWireSession tryBuildResumeSession(CompoundTag data, Level level, Vec3 savedPos) {
        InWorldNodeConnection conn = CEECompat.connectionFromTag(data);
        if (conn == null || level == null) return null;
        if (WireRenderer.getConnectionData(conn) == null) return null;
        CEEWireSession session = new CEEWireSession(conn, level, savedPos);
        return session.isValid() ? session : null;
    }

    public static HighlightTarget getTargetedHighlight(Level level) {
        NodeConnectionPoint point = WireInteractionHandler.targetedPoint;
        Vec3 pos = WireInteractionHandler.targetedPos;
        if (point == null || pos == null || level == null) return null;
        List<Vec3> pts = calculatePoints(point.connection(), level);
        if (pts == null || pts.size() < 2) return null;
        return new HighlightTarget(pts, pos);
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

    public static CEEWireSession searchHoldToGrab(
        Vec3 handsPos,
        double grabDistSq,
        Level level,
        Vec3 camPos,
        Vec3 camLook,
        InWorldNodeConnection excludedWire
    ) {
        CEEWireSession best = null;
        double bestScore = Double.MAX_VALUE;

        for (Pair<InWorldNodeConnection, WireData> pair : WireRenderer.getAllConnections()) {
            InWorldNodeConnection conn = pair.getFirst();
            if (conn == null || conn.equals(excludedWire)) continue;

            List<Vec3> points = calculatePoints(conn, level);
            if (points == null || points.size() < 2) continue;

            double rayDist = WireGeometry.getRayDistanceToWire(points, camPos, camLook);
            if (rayDist > 0.65) continue;

            var query = WireGeometry.getClosestPointOnWire(points, handsPos);
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

    public static CEEWireSession findParallelWire(
        Vec3 handsPos,
        Vec3 desiredLateralDir,
        Vec3 currentTangent,
        InWorldNodeConnection currentWire,
        Level level
    ) {
        if (level == null || handsPos == null || desiredLateralDir == null) return null;

        CEEWireSession best = null;
        double bestScore = -1000.0;

        for (Pair<InWorldNodeConnection, WireData> pair : WireRenderer.getAllConnections()) {
            InWorldNodeConnection conn = pair.getFirst();
            if (conn == null || conn.equals(currentWire)) continue;

            List<Vec3> points = calculatePoints(conn, level);
            WireGeometry.ParallelMatch match = WireGeometry.scoreParallelCandidate(handsPos, desiredLateralDir, currentTangent, points);
            if (match != null && match.score() > bestScore) {
                bestScore = match.score();
                best = new CEEWireSession(conn, level, match.closestPoint());
            }
        }
        return best;
    }

    public static CEEWireSession findConnectedWire(
        CEEWireSession current,
        Vec3 currentEndPos,
        Level level,
        Vec3 travelDir,
        float steerX,
        InWorldNodeConnection excludedWire
    ) {
        if (level == null || currentEndPos == null) return null;

        InWorldNodeConnection currentConn = (current != null) ? current.getConnection() : null;
        InWorldNode currentEndNode = null;
        if (currentConn != null) {
            Vec3 cp1 = currentConn.node1().getPosition(level);
            Vec3 cp2 = currentConn.node2().getPosition(level);
            if (cp1 != null && cp2 != null) {
                currentEndNode = (cp1.distanceToSqr(currentEndPos) <= cp2.distanceToSqr(currentEndPos))
                    ? currentConn.node1()
                    : currentConn.node2();
            }
        }

        Vec3 unitTravel = (travelDir != null && travelDir.lengthSqr() > 1.0E-6) ? travelDir.normalize() : Vec3.ZERO;
        Vec3 right = unitTravel.lengthSqr() > 0 ? unitTravel.cross(new Vec3(0, 1, 0)).normalize() : Vec3.ZERO;

        CEEWireSession best = null;
        double bestScore = -10.0;

        for (Pair<InWorldNodeConnection, WireData> pair : WireRenderer.getAllConnections()) {
            InWorldNodeConnection other = pair.getFirst();
            if (other == null) continue;
            if (other.equals(currentConn) || other.equals(excludedWire)) continue;

            Vec3 p1 = other.node1().getPosition(level);
            Vec3 p2 = other.node2().getPosition(level);
            if (p1 == null || p2 == null) continue;

            boolean exactNode = (currentEndNode != null) &&
                (currentEndNode.equals(other.node1()) || currentEndNode.equals(other.node2()));

            double score = WireGeometry.scoreConnectedCandidate(currentEndPos, p1, p2, exactNode, unitTravel, right, steerX);
            if (score > bestScore) {
                bestScore = score;
                best = new CEEWireSession(other, level, currentEndPos);
            }
        }

        return best;
    }
}
