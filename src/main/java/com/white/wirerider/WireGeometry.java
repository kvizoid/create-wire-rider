package com.white.wirerider;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Pure geometric calculations for catenary wire polylines, shared between compat modules and server validation.
 */
public final class WireGeometry {
    public static final double MAX_NODE_JOIN_DIST_SQ = 6.25; // 2.5 blocks

    private WireGeometry() {}

    public record ClosestQuery(Vec3 position, Vec3 normal) {}

    public record ParallelMatch(Vec3 closestPoint, double score) {}

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

            double along = Mth.clamp(pos.subtract(a).dot(dir), 0.0, len);
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

    public static double distanceSqToSegment(Vec3 pos, Vec3 a, Vec3 b) {
        Vec3 diff = b.subtract(a);
        double lenSq = diff.lengthSqr();
        if (lenSq < 1.0E-6) return pos.distanceToSqr(a);
        double t = Mth.clamp(pos.subtract(a).dot(diff) / lenSq, 0.0, 1.0);
        Vec3 proj = a.add(diff.scale(t));
        return proj.distanceToSqr(pos);
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
            double dist = getRayDistanceToSegment(rayStart, rayDir, points.get(i), points.get(i + 1));
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

        double b = u.dot(v);
        double d = u.dot(w);
        double e = v.dot(w);
        double denom = 1.0 - b * b;
        double sc, tc;

        if (denom < 1.0E-5) {
            sc = Math.max(0.0, d);
            tc = 0.0;
        } else {
            sc = (b * e - d) / denom;
            tc = (e - b * d) / denom;
        }

        sc = Math.max(0.0, sc);
        if (sc > 5.5) return Double.MAX_VALUE;

        tc = Mth.clamp(tc, 0.0, segLen);
        Vec3 pRay = rayStart.add(u.scale(sc));
        Vec3 pSeg = segA.add(v.scale(tc));
        return pRay.distanceTo(pSeg);
    }

    public static double scoreConnectedCandidate(
        Vec3 currentEndPos,
        Vec3 p1,
        Vec3 p2,
        boolean exactNode,
        Vec3 unitTravel,
        Vec3 right,
        float steerX
    ) {
        double d1 = p1.distanceToSqr(currentEndPos);
        double d2 = p2.distanceToSqr(currentEndPos);
        double minDistSq = Math.min(d1, d2);
        if (minDistSq > MAX_NODE_JOIN_DIST_SQ) return Double.NEGATIVE_INFINITY;

        Vec3 outVec = (d1 <= d2) ? p2.subtract(p1) : p1.subtract(p2);
        if (outVec.lengthSqr() < 1.0E-6) return Double.NEGATIVE_INFINITY;
        Vec3 outDir = outVec.normalize();

        double score = exactNode ? 25.0 : (-minDistSq * 4.0);
        if (unitTravel != null && unitTravel.lengthSqr() > 1.0E-6) {
            double forwardDot = outDir.dot(unitTravel);
            if (forwardDot < -0.25) {
                score += forwardDot * 25.0;
            } else {
                score += forwardDot * 15.0;
                if (right != null && right.lengthSqr() > 1.0E-6 && Math.abs(steerX) > 0.1f) {
                    score += steerX * right.dot(outDir) * 8.0;
                }
            }
        }
        return score;
    }

    public static ParallelMatch scoreParallelCandidate(
        Vec3 handsPos,
        Vec3 desiredLateralDir,
        Vec3 currentTangent,
        List<Vec3> candidatePoints
    ) {
        if (candidatePoints == null || candidatePoints.size() < 2) return null;
        ClosestQuery query = getClosestPointOnWire(candidatePoints, handsPos);
        Vec3 pt = query.position();
        Vec3 toPt = pt.subtract(handsPos);
        double distSq = toPt.lengthSqr();
        if (distSq > 3.6 * 3.6 || distSq < 0.25 * 0.25) return null;
        if (Math.abs(toPt.y) > 2.0) return null;

        double lateralProj = toPt.dot(desiredLateralDir);
        if (lateralProj < 0.25) return null;

        double alignDot = Math.abs(query.normal().dot(currentTangent));
        if (alignDot < 0.35) return null;

        double score = (lateralProj * 2.0) - (toPt.length() * 1.5) + (alignDot * 2.0);
        return new ParallelMatch(pt, score);
    }
}
