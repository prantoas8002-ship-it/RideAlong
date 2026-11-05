package com.example.ridealong.domain.algorithm;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * Encoded Polyline Algorithm Format (precision 5), as returned by OSRM/ORS/Google.
 * Routes are stored encoded on the ride document to keep it small.
 */
public final class PolylineCodec {
    private PolylineCodec() { }

    public static List<GeoCoord> decode(String encoded) {
        List<GeoCoord> out = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return out;
        int index = 0, lat = 0, lng = 0, len = encoded.length();
        try {
            while (index < len) {
                int result = 0, shift = 0, b;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                lat += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
                result = 0;
                shift = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                lng += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
                double dLat = lat / 1e5, dLng = lng / 1e5;
                if (GeoCoord.isValid(dLat, dLng)) out.add(new GeoCoord(dLat, dLng));
            }
        } catch (StringIndexOutOfBoundsException e) {
            // Malformed/truncated input: return what decoded cleanly instead of crashing.
        }
        return out;
    }

    public static String encode(List<GeoCoord> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0, prevLng = 0;
        if (points == null) return "";
        for (GeoCoord p : points) {
            long lat = Math.round(p.lat * 1e5);
            long lng = Math.round(p.lng * 1e5);
            encodeValue(lat - prevLat, sb);
            encodeValue(lng - prevLng, sb);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    private static void encodeValue(long v, StringBuilder sb) {
        v = v < 0 ? ~(v << 1) : (v << 1);
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }

    /**
     * Douglas-Peucker simplification with a tolerance, then uniform thinning to maxPoints.
     * Keeps stored routes small (Firestore 1 MiB doc limit) while preserving shape.
     */
    public static List<GeoCoord> simplify(List<GeoCoord> pts, double toleranceKm, int maxPoints) {
        if (pts == null || pts.size() <= 2) return pts == null ? new ArrayList<>() : new ArrayList<>(pts);
        boolean[] keep = new boolean[pts.size()];
        keep[0] = keep[pts.size() - 1] = true;
        douglasPeucker(pts, 0, pts.size() - 1, toleranceKm, keep);
        List<GeoCoord> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) if (keep[i]) out.add(pts.get(i));
        if (out.size() > maxPoints && maxPoints >= 2) {
            List<GeoCoord> thin = new ArrayList<>();
            double step = (out.size() - 1) / (double) (maxPoints - 1);
            for (int i = 0; i < maxPoints; i++) thin.add(out.get((int) Math.round(i * step)));
            return thin;
        }
        return out;
    }

    private static void douglasPeucker(List<GeoCoord> pts, int first, int last, double tol, boolean[] keep) {
        // Iterative stack to avoid deep recursion on long routes.
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{first, last});
        while (!stack.isEmpty()) {
            int[] r = stack.pop();
            int a = r[0], b = r[1];
            double maxD = -1;
            int idx = -1;
            for (int i = a + 1; i < b; i++) {
                double d = GeometryUtils.pointToSegmentKm(pts.get(i), pts.get(a), pts.get(b));
                if (d > maxD) { maxD = d; idx = i; }
            }
            if (idx != -1 && maxD > tol) {
                keep[idx] = true;
                stack.push(new int[]{a, idx});
                stack.push(new int[]{idx, b});
            }
        }
    }
}
