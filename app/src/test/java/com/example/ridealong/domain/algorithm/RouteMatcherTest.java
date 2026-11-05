package com.example.ridealong.domain.algorithm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ridealong.domain.config.MatchingConfig;
import com.example.ridealong.domain.model.GeoCoord;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RouteMatcherTest {

    private static final double KM_PER_DEG = Math.PI * Haversine.EARTH_RADIUS_KM / 180.0;
    private static final long T0 = 1_800_000_000_000L;
    private static final GeoCoord START = new GeoCoord(0, 0);
    private static final GeoCoord END = new GeoCoord(0, 0.1);

    private RouteMatcher matcher;
    private MatchingConfig cfg;

    @Before
    public void setUp() {
        cfg = new MatchingConfig();
        matcher = new RouteMatcher(cfg);
    }

    private RouteMatcher.Candidate ride(String id, int seats, double maxDetour) {
        return new RouteMatcher.Candidate(id, START, END, Arrays.asList(START, END), T0, seats, maxDetour);
    }

    @Test
    public void perfectMatchScoresHigh() {
        RouteMatcher.Result r = matcher.evaluate(ride("a", 2, 2),
                new RouteMatcher.Request(START, END, T0, 1));
        assertTrue(r.compatible);
        assertEquals(RouteMatcher.Rejection.NONE, r.rejection);
        assertTrue("score " + r.score, r.score >= 95);
    }

    @Test
    public void scoreIsWithinZeroToHundred() {
        RouteMatcher.Result r = matcher.evaluate(ride("a", 2, 5),
                new RouteMatcher.Request(new GeoCoord(2.0 / KM_PER_DEG, 0.02), new GeoCoord(0, 0.09), T0 + 60 * 60000L, 1));
        assertTrue(r.compatible);
        assertTrue(r.score >= 0 && r.score <= 100);
    }

    @Test
    public void rejectsWhenNotEnoughSeats() {
        RouteMatcher.Result r = matcher.evaluate(ride("a", 1, 2), new RouteMatcher.Request(START, END, T0, 2));
        assertFalse(r.compatible);
        assertEquals(RouteMatcher.Rejection.NOT_ENOUGH_SEATS, r.rejection);
        assertEquals(0, r.score);
    }

    @Test
    public void rejectsFarPickup() {
        GeoCoord far = new GeoCoord(5.0 / KM_PER_DEG, 0); // 5 km north of start, max 3
        RouteMatcher.Result r = matcher.evaluate(ride("a", 2, 20), new RouteMatcher.Request(far, END, T0, 1));
        assertEquals(RouteMatcher.Rejection.PICKUP_TOO_FAR, r.rejection);
    }

    @Test
    public void rejectsLargeTimeDifference() {
        RouteMatcher.Result r = matcher.evaluate(ride("a", 2, 2),
                new RouteMatcher.Request(START, END, T0 + (cfg.maxTimeDifferenceMinutes + 1) * 60000L, 1));
        assertEquals(RouteMatcher.Rejection.TIME_MISMATCH, r.rejection);
    }

    @Test
    public void rejectsDetourAboveRideLimit() {
        GeoCoord offRoute = new GeoCoord(1.0 / KM_PER_DEG, 0.03); // 1 km off => ~2 km detour
        RouteMatcher.Result r = matcher.evaluate(ride("a", 2, 0.5),
                new RouteMatcher.Request(offRoute, new GeoCoord(0, 0.08), T0, 1));
        assertEquals(RouteMatcher.Rejection.DETOUR_TOO_LARGE, r.rejection);
        RouteMatcher.Result ok = matcher.evaluate(ride("b", 2, 3),
                new RouteMatcher.Request(offRoute, new GeoCoord(0, 0.08), T0, 1));
        assertTrue(ok.compatible);
        assertEquals(2.0, ok.detourKm, 0.3);
    }

    @Test
    public void rejectsWrongDirection() {
        RouteMatcher.Result r = matcher.evaluate(ride("a", 2, 20),
                new RouteMatcher.Request(new GeoCoord(0, 0.08), new GeoCoord(0, 0.02), T0, 1));
        assertEquals(RouteMatcher.Rejection.WRONG_DIRECTION, r.rejection);
    }

    @Test
    public void rejectsFarDestination() {
        // Drop-off 4 km north of the end of the route; pickup fine.
        GeoCoord drop = new GeoCoord(4.0 / KM_PER_DEG, 0.1);
        RouteMatcher.Result r = matcher.evaluate(ride("a", 2, 20), new RouteMatcher.Request(START, drop, T0, 1));
        assertEquals(RouteMatcher.Rejection.DESTINATION_TOO_FAR, r.rejection);
    }

    @Test
    public void closerPickupScoresHigher() {
        RouteMatcher.Request near = new RouteMatcher.Request(new GeoCoord(0.2 / KM_PER_DEG, 0.0), END, T0, 1);
        RouteMatcher.Request far = new RouteMatcher.Request(new GeoCoord(1.5 / KM_PER_DEG, 0.0), END, T0, 1);
        int sNear = matcher.evaluate(ride("a", 2, 5), near).score;
        int sFar = matcher.evaluate(ride("a", 2, 5), far).score;
        assertTrue(sNear > sFar);
    }

    @Test
    public void closerTimeScoresHigher() {
        int s1 = matcher.evaluate(ride("a", 2, 2), new RouteMatcher.Request(START, END, T0 + 5 * 60000L, 1)).score;
        int s2 = matcher.evaluate(ride("a", 2, 2), new RouteMatcher.Request(START, END, T0 + 60 * 60000L, 1)).score;
        assertTrue(s1 > s2);
    }

    @Test
    public void missingComponentsAreRenormalised() {
        // Only pickup given, at the ride start: pickup component is perfect so score is 100.
        RouteMatcher.Result r = matcher.evaluate(ride("a", 2, 2), new RouteMatcher.Request(START, null, null, 1));
        assertTrue(r.compatible);
        assertEquals(100, r.score);
    }

    @Test
    public void weightsAreConfigurable() {
        cfg.weightTime = 0; // ignore time entirely
        RouteMatcher m = new RouteMatcher(cfg);
        int s = m.evaluate(ride("a", 2, 2), new RouteMatcher.Request(START, END, T0 + 80 * 60000L, 1)).score;
        assertTrue(s >= 95);
    }

    @Test
    public void rankSortsCompatibleByScore() {
        RouteMatcher.Candidate good = ride("good", 2, 5);
        RouteMatcher.Candidate later = new RouteMatcher.Candidate("later", START, END, Arrays.asList(START, END),
                T0 + 70 * 60000L, 2, 5);
        RouteMatcher.Candidate full = ride("full", 0, 5);
        List<RouteMatcher.Result> ranked = matcher.rank(Arrays.asList(later, full, good),
                new RouteMatcher.Request(START, END, T0, 1));
        assertEquals(2, ranked.size());
        assertEquals("good", ranked.get(0).candidateId);
        assertEquals("later", ranked.get(1).candidateId);
    }
}
