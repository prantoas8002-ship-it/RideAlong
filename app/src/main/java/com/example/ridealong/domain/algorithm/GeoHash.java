package com.example.ridealong.domain.algorithm;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Self-contained GeoHash implementation (encode, decode, neighbours, radius query bounds).
 * Rides store the pickup geohash; nearby discovery runs Firestore range queries over the
 * cells returned by {@link #queryBounds(GeoCoord, double)}.
 */
public final class GeoHash {
    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    public static final int MAX_PRECISION = 12;

    /** Approximate cell height (km) for precision 1..12 (lat error*2). Width varies with latitude. */
    private static final double[] CELL_HEIGHT_KM = {
            5003.8, 625.4, 156.4, 19.5, 4.89, 0.61, 0.153, 0.019, 0.0048, 0.0006, 0.00015, 0.000019
    };

    private GeoHash() { }

    public static String encode(double lat, double lng, int precision) {
        if (precision < 1 || precision > MAX_PRECISION) throw new IllegalArgumentException("precision 1..12");
        if (!GeoCoord.isValid(lat, lng)) throw new IllegalArgumentException("invalid coordinate");
        double latMin = -90, latMax = 90, lngMin = -180, lngMax = 180;
        StringBuilder sb = new StringBuilder(precision);
        boolean evenBit = true;
        int bit = 0, ch = 0;
        while (sb.length() < precision) {
            if (evenBit) {
                double mid = (lngMin + lngMax) / 2;
                if (lng >= mid) { ch = (ch << 1) | 1; lngMin = mid; } else { ch = ch << 1; lngMax = mid; }
            } else {
                double mid = (latMin + latMax) / 2;
                if (lat >= mid) { ch = (ch << 1) | 1; latMin = mid; } else { ch = ch << 1; latMax = mid; }
            }
            evenBit = !evenBit;
            if (++bit == 5) {
                sb.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return sb.toString();
    }

    public static String encode(GeoCoord c, int precision) { return encode(c.lat, c.lng, precision); }

    /** Returns {latMin, lngMin, latMax, lngMax}. */
    public static double[] bounds(String hash) {
        if (hash == null || hash.isEmpty()) throw new IllegalArgumentException("empty geohash");
        double latMin = -90, latMax = 90, lngMin = -180, lngMax = 180;
        boolean evenBit = true;
        for (int i = 0; i < hash.length(); i++) {
            int idx = BASE32.indexOf(Character.toLowerCase(hash.charAt(i)));
            if (idx < 0) throw new IllegalArgumentException("invalid geohash char: " + hash.charAt(i));
            for (int n = 4; n >= 0; n--) {
                int bitN = (idx >> n) & 1;
                if (evenBit) {
                    double mid = (lngMin + lngMax) / 2;
                    if (bitN == 1) lngMin = mid; else lngMax = mid;
                } else {
                    double mid = (latMin + latMax) / 2;
                    if (bitN == 1) latMin = mid; else latMax = mid;
                }
                evenBit = !evenBit;
            }
        }
        return new double[]{latMin, lngMin, latMax, lngMax};
    }

    /** Center of the geohash cell. */
    public static GeoCoord decode(String hash) {
        double[] b = bounds(hash);
        return new GeoCoord((b[0] + b[2]) / 2, (b[1] + b[3]) / 2);
    }

    /** Neighbour in a direction: dLat/dLng each -1, 0 or 1. Wraps longitude; clamps at the poles. */
    public static String neighbor(String hash, int dLat, int dLng) {
        double[] b = bounds(hash);
        double h = b[2] - b[0], w = b[3] - b[1];
        double lat = (b[0] + b[2]) / 2 + dLat * h;
        double lng = (b[1] + b[3]) / 2 + dLng * w;
        if (lat > 90 || lat < -90) return hash; // no neighbour across a pole
        if (lng > 180) lng -= 360;
        if (lng < -180) lng += 360;
        return encode(lat, lng, hash.length());
    }

    /** The 8 surrounding cells (N, NE, E, SE, S, SW, W, NW), de-duplicated near the poles. */
    public static List<String> neighbors(String hash) {
        int[][] dirs = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        Set<String> set = new LinkedHashSet<>();
        for (int[] d : dirs) {
            String n = neighbor(hash, d[0], d[1]);
            if (!n.equals(hash)) set.add(n);
        }
        return new ArrayList<>(set);
    }

    /** Largest precision whose cell is still at least radiusKm tall (so 3x3 cells cover the circle). */
    public static int precisionForRadius(double radiusKm) {
        for (int p = MAX_PRECISION; p >= 1; p--) {
            if (CELL_HEIGHT_KM[p - 1] >= radiusKm) return p;
        }
        return 1;
    }

    /**
     * Range bounds [start, end] for prefix queries covering a circle of radiusKm around center.
     * Query each pair with orderBy(geohash).startAt(start).endAt(end), then post-filter by
     * true distance (cells are larger than the circle).
     */
    public static List<String[]> queryBounds(GeoCoord center, double radiusKm) {
        int precision = precisionForRadius(Math.max(0.01, radiusKm));
        String c = encode(center, precision);
        Set<String> cells = new LinkedHashSet<>();
        cells.add(c);
        cells.addAll(neighbors(c));
        List<String[]> out = new ArrayList<>();
        for (String cell : cells) out.add(new String[]{cell, cell + "~"});
        return out;
    }
}
