package com.example.ridealong.domain.algorithm;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * Point-to-segment / point-to-polyline distances and polyline resampling.
 * Uses a local equirectangular projection around the query point: accurate to well under 1%
 * for the city-scale distances (a few km) RideAlong deals with.
 */
public final class GeometryUtils {
    private GeometryUtils() { }

    /** Result of projecting a point onto a polyline. */
    public static final class Projection {
        public final double distanceKm;   // perpendicular (or end-point) distance
        public final int segmentIndex;    // segment i is points[i] -> points[i+1]
        public final double t;            // 0..1 position along that segment
        public final GeoCoord closest;

        Projection(double distanceKm, int segmentIndex, double t, GeoCoord closest) {
            this.distanceKm = distanceKm;
            this.segmentIndex = segmentIndex;
            this.t = t;
            this.closest = closest;
        }
    }

    /** Shortest distance (km) from p to the segment a-b. */
    public static double pointToSegmentKm(GeoCoord p, GeoCoord a, GeoCoord b) {
        return projectOnSegment(p, a, b)[0];
    }

    /** Returns {distanceKm, t, closestLat, closestLng}. */
    static double[] projectOnSegment(GeoCoord p, GeoCoord a, GeoCoord b) {
        double kmPerDegLat = Math.PI * Haversine.EARTH_RADIUS_KM / 180.0;
        double kmPerDegLng = kmPerDegLat * Math.cos(Math.toRadians(p.lat));
        double ax = (a.lng - p.lng) * kmPerDegLng, ay = (a.lat - p.lat) * kmPerDegLat;
        double bx = (b.lng - p.lng) * kmPerDegLng, by = (b.lat - p.lat) * kmPerDegLat;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : -(ax * dx + ay * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx, cy = ay + t * dy;
        double cLat = p.lat + cy / kmPerDegLat;
        double cLng = p.lng + (kmPerDegLng == 0 ? 0 : cx / kmPerDegLng);
        return new double[]{Math.sqrt(cx * cx + cy * cy), t, cLat, cLng};
    }

    /** Projects p onto the closest segment of the polyline. Returns null for an empty polyline. */
    public static Projection projectOnPolyline(GeoCoord p, List<GeoCoord> line) {
        if (line == null || line.isEmpty()) return null;
        if (line.size() == 1) return new Projection(Haversine.distanceKm(p, line.get(0)), 0, 0, line.get(0));
        double best = Double.MAX_VALUE;
        int bestIdx = 0;
        double bestT = 0, bestLat = 0, bestLng = 0;
        for (int i = 0; i < line.size() - 1; i++) {
            double[] r = projectOnSegment(p, line.get(i), line.get(i + 1));
            if (r[0] < best) {
                best = r[0];
                bestIdx = i;
                bestT = r[1];
                bestLat = r[2];
                bestLng = r[3];
            }
        }
        return new Projection(best, bestIdx, bestT, new GeoCoord(clampLat(bestLat), clampLng(bestLng)));
    }

    public static double pointToPolylineKm(GeoCoord p, List<GeoCoord> line) {
        Projection pr = projectOnPolyline(p, line);
        return pr == null ? Double.MAX_VALUE : pr.distanceKm;
    }

    /**
     * Inserts intermediate points so no segment is longer than maxSegmentKm, capped at maxPoints.
     * Needed so Dijkstra connector edges can attach near a passenger even on a sparse
     * (e.g. straight-line fallback) route.
     */
    public static List<GeoCoord> densify(List<GeoCoord> line, double maxSegmentKm, int maxPoints) {
        List<GeoCoord> out = new ArrayList<>();
        if (line == null || line.isEmpty()) return out;
        double total = Haversine.polylineLengthKm(line);
        double step = Math.max(maxSegmentKm, total / Math.max(1, maxPoints - 1));
        out.add(line.get(0));
        for (int i = 1; i < line.size(); i++) {
            GeoCoord a = line.get(i - 1), b = line.get(i);
            double d = Haversine.distanceKm(a, b);
            int pieces = (int) Math.ceil(d / step);
            for (int k = 1; k < pieces; k++) {
                double f = (double) k / pieces;
                out.add(new GeoCoord(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f));
            }
            out.add(b);
        }
        return out;
    }

    private static double clampLat(double v) { return Math.max(-90, Math.min(90, v)); }

    private static double clampLng(double v) { return Math.max(-180, Math.min(180, v)); }
}
