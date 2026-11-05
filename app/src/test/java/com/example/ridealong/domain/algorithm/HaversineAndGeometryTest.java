package com.example.ridealong.domain.algorithm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.ridealong.domain.model.GeoCoord;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HaversineAndGeometryTest {

    private static final double KM_PER_DEG = Math.PI * Haversine.EARTH_RADIUS_KM / 180.0; // 111.195

    @Test
    public void zeroDistanceForSamePoint() {
        assertEquals(0.0, Haversine.distanceKm(23.81, 90.41, 23.81, 90.41), 1e-9);
    }

    @Test
    public void oneDegreeOfLatitude() {
        assertEquals(KM_PER_DEG, Haversine.distanceKm(0, 0, 1, 0), 1e-6);
    }

    @Test
    public void dhakaToChittagongIsAbout214Km() {
        double d = Haversine.distanceKm(new GeoCoord(23.8103, 90.4125), new GeoCoord(22.3569, 91.7832));
        assertEquals(214, d, 3);
    }

    @Test
    public void distanceIsSymmetric() {
        GeoCoord a = new GeoCoord(23.78, 90.27), b = new GeoCoord(23.74, 90.37);
        assertEquals(Haversine.distanceKm(a, b), Haversine.distanceKm(b, a), 1e-12);
    }

    @Test
    public void polylineLengthSumsSegments() {
        List<GeoCoord> line = Arrays.asList(new GeoCoord(0, 0), new GeoCoord(1, 0), new GeoCoord(2, 0));
        assertEquals(2 * KM_PER_DEG, Haversine.polylineLengthKm(line), 1e-6);
    }

    @Test
    public void pointToSegmentPerpendicular() {
        // Segment along the equator from lng 0 to 1; point 0.01 deg north of its middle.
        double d = GeometryUtils.pointToSegmentKm(new GeoCoord(0.01, 0.5), new GeoCoord(0, 0), new GeoCoord(0, 1));
        assertEquals(0.01 * KM_PER_DEG, d, 0.01);
    }

    @Test
    public void pointToSegmentBeyondEndUsesEndpoint() {
        GeoCoord a = new GeoCoord(0, 0), b = new GeoCoord(0, 0.1);
        GeoCoord p = new GeoCoord(0, 0.2);
        assertEquals(Haversine.distanceKm(p, b), GeometryUtils.pointToSegmentKm(p, a, b), 0.01);
    }

    @Test
    public void pointOnSegmentIsZero() {
        assertEquals(0, GeometryUtils.pointToSegmentKm(new GeoCoord(0, 0.05), new GeoCoord(0, 0), new GeoCoord(0, 0.1)), 1e-6);
    }

    @Test
    public void degenerateSegmentIsPointDistance() {
        GeoCoord a = new GeoCoord(23.8, 90.4), p = new GeoCoord(23.81, 90.4);
        assertEquals(Haversine.distanceKm(a, p), GeometryUtils.pointToSegmentKm(p, a, a), 0.005);
    }

    @Test
    public void projectOnPolylineFindsClosestSegment() {
        List<GeoCoord> line = Arrays.asList(new GeoCoord(0, 0), new GeoCoord(0, 0.1), new GeoCoord(0.1, 0.1));
        GeometryUtils.Projection pr = GeometryUtils.projectOnPolyline(new GeoCoord(0.05, 0.11), line);
        assertEquals(1, pr.segmentIndex);
        assertEquals(0.01 * KM_PER_DEG, pr.distanceKm, 0.01);
    }

    @Test
    public void densifyLimitsSegmentLength() {
        List<GeoCoord> line = Arrays.asList(new GeoCoord(0, 0), new GeoCoord(0, 0.1)); // ~11.1 km
        List<GeoCoord> dense = GeometryUtils.densify(line, 0.5, 1000);
        for (int i = 1; i < dense.size(); i++) {
            assertTrue(Haversine.distanceKm(dense.get(i - 1), dense.get(i)) <= 0.5 + 1e-6);
        }
        assertEquals(Haversine.polylineLengthKm(line), Haversine.polylineLengthKm(dense), 1e-6);
    }

    @Test
    public void densifyRespectsMaxPoints() {
        List<GeoCoord> line = Arrays.asList(new GeoCoord(0, 0), new GeoCoord(0, 1)); // ~111 km
        assertTrue(GeometryUtils.densify(line, 0.01, 100).size() <= 101);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCoordinateRejected() {
        new GeoCoord(91, 0);
    }
}
