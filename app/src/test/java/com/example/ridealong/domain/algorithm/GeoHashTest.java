package com.example.ridealong.domain.algorithm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ridealong.domain.model.GeoCoord;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GeoHashTest {

    @Test
    public void encodesKnownValues() {
        assertEquals("ezs42", GeoHash.encode(42.6, -5.6, 5));
        assertEquals("u4pruydqqvj", GeoHash.encode(57.64911, 10.40744, 11));
    }

    @Test
    public void decodeReturnsPointInsideCell() {
        String h = GeoHash.encode(23.8103, 90.4125, 9);
        double[] b = GeoHash.bounds(h);
        GeoCoord c = GeoHash.decode(h);
        assertTrue(c.lat >= b[0] && c.lat <= b[2]);
        assertTrue(c.lng >= b[1] && c.lng <= b[3]);
        assertTrue(Haversine.distanceKm(c, new GeoCoord(23.8103, 90.4125)) < 0.01);
    }

    @Test
    public void roundTripIsStable() {
        String h = GeoHash.encode(23.7461, 90.3742, 8);
        GeoCoord c = GeoHash.decode(h);
        assertEquals(h, GeoHash.encode(c, 8));
    }

    @Test
    public void prefixPropertyHolds() {
        String h9 = GeoHash.encode(23.78, 90.27, 9);
        String h5 = GeoHash.encode(23.78, 90.27, 5);
        assertTrue(h9.startsWith(h5));
    }

    @Test
    public void hasEightDistinctNeighbors() {
        String h = GeoHash.encode(23.81, 90.41, 6);
        List<String> n = GeoHash.neighbors(h);
        assertEquals(8, n.size());
        Set<String> s = new HashSet<>(n);
        assertEquals(8, s.size());
        assertFalse(s.contains(h));
        for (String x : n) assertEquals(h.length(), x.length());
    }

    @Test
    public void neighborIsReversible() {
        String h = GeoHash.encode(23.81, 90.41, 7);
        assertEquals(h, GeoHash.neighbor(GeoHash.neighbor(h, 1, 0), -1, 0));
        assertEquals(h, GeoHash.neighbor(GeoHash.neighbor(h, 0, 1), 0, -1));
    }

    @Test
    public void knownNeighbor() {
        // Classic reference: east neighbour of "ezs42" is "ezs43", north is "ezs48".
        assertEquals("ezs43", GeoHash.neighbor("ezs42", 0, 1));
        assertEquals("ezs48", GeoHash.neighbor("ezs42", 1, 0));
    }

    @Test
    public void precisionForRadius() {
        assertEquals(5, GeoHash.precisionForRadius(3));   // cell ~4.9 km
        assertEquals(4, GeoHash.precisionForRadius(10));  // cell ~19.5 km
        assertEquals(6, GeoHash.precisionForRadius(0.5)); // cell ~0.61 km
    }

    @Test
    public void queryBoundsCoverNearbyPoint() {
        GeoCoord center = new GeoCoord(23.8103, 90.4125);
        GeoCoord near = new GeoCoord(23.8300, 90.4300); // ~2.8 km away
        List<String[]> bounds = GeoHash.queryBounds(center, 5);
        String nearHash = GeoHash.encode(near, 9);
        boolean covered = false;
        for (String[] b : bounds) {
            if (nearHash.compareTo(b[0]) >= 0 && nearHash.compareTo(b[1]) <= 0) covered = true;
        }
        assertTrue(covered);
        assertEquals(9, bounds.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidChars() {
        GeoHash.bounds("abc!"); // 'a' and '!' are not base32 geohash characters
    }
}
