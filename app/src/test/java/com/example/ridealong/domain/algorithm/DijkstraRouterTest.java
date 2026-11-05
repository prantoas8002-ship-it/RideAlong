package com.example.ridealong.domain.algorithm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ridealong.domain.model.GeoCoord;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class DijkstraRouterTest {

    private static final double KM_PER_DEG = Math.PI * Haversine.EARTH_RADIUS_KM / 180.0;
    /** Straight east-west route along the equator, lng 0 → 0.1 (~11.12 km). */
    private static final List<GeoCoord> ROUTE = Arrays.asList(new GeoCoord(0, 0), new GeoCoord(0, 0.1));

    @Test
    public void classicGraphShortestPath() {
        DijkstraRouter.Graph g = new DijkstraRouter.Graph();
        for (int i = 0; i < 5; i++) g.addNode();
        g.addEdge(0, 1, 4, false);
        g.addEdge(0, 2, 1, false);
        g.addEdge(2, 1, 2, false);
        g.addEdge(1, 3, 1, false);
        g.addEdge(2, 3, 5, false);
        // node 4 unreachable
        DijkstraRouter.ShortestPaths sp = DijkstraRouter.dijkstra(g, 0);
        assertEquals(3, sp.dist[1], 1e-9);
        assertEquals(4, sp.dist[3], 1e-9);
        assertEquals(Arrays.asList(0, 2, 1, 3), sp.pathTo(3));
        assertTrue(Double.isInfinite(sp.dist[4]));
        assertTrue(sp.pathTo(4).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeWeightsRejected() {
        DijkstraRouter.Graph g = new DijkstraRouter.Graph();
        g.addNode();
        g.addNode();
        g.addEdge(0, 1, -1, false);
    }

    @Test
    public void passengerOnRouteHasSharedDistanceAndNoDetour() {
        DijkstraRouter r = new DijkstraRouter();
        DijkstraRouter.Analysis a = r.analyze(ROUTE, new GeoCoord(0, 0.02), new GeoCoord(0, 0.08));
        assertTrue(a.reachable);
        assertFalse(a.wrongDirection);
        assertEquals(0.1 * KM_PER_DEG, a.baseRouteKm, 0.01);
        assertEquals(0.06 * KM_PER_DEG, a.sharedKm, 0.2);
        assertEquals(0.06 * KM_PER_DEG, a.fareDistanceKm, 0.2);
        assertTrue("detour " + a.detourKm, a.detourKm < 0.3);
    }

    @Test
    public void offRoutePickupAddsRoundTripDetour() {
        DijkstraRouter r = new DijkstraRouter();
        double offsetKm = 1.0;
        GeoCoord pickup = new GeoCoord(offsetKm / KM_PER_DEG, 0.03);
        DijkstraRouter.Analysis a = r.analyze(ROUTE, pickup, new GeoCoord(0, 0.08));
        assertEquals(2 * offsetKm, a.detourKm, 0.3); // go there and come back
        assertTrue(a.connectorKm >= offsetKm - 0.05);
    }

    @Test
    public void detectsWrongDirection() {
        DijkstraRouter r = new DijkstraRouter();
        DijkstraRouter.Analysis a = r.analyze(ROUTE, new GeoCoord(0, 0.08), new GeoCoord(0, 0.02));
        assertTrue(a.wrongDirection);
    }

    @Test
    public void pickupOnlyAnalysis() {
        DijkstraRouter r = new DijkstraRouter();
        DijkstraRouter.Analysis a = r.analyze(ROUTE, new GeoCoord(0, 0.05), null);
        assertTrue(a.reachable);
        assertTrue(a.detourKm < 0.2);
        assertEquals(0, a.sharedKm, 1e-9);
    }

    @Test
    public void bentRouteSharedDistanceFollowsTheRoute() {
        // L-shaped route: east then north. Passenger rides around the corner.
        List<GeoCoord> lRoute = Arrays.asList(new GeoCoord(0, 0), new GeoCoord(0, 0.05), new GeoCoord(0.05, 0.05));
        DijkstraRouter r = new DijkstraRouter();
        DijkstraRouter.Analysis a = r.analyze(lRoute, new GeoCoord(0, 0.03), new GeoCoord(0.03, 0.05));
        // along the route: 0.02 east + 0.03 north = 0.05 deg, longer than the straight diagonal
        assertEquals(0.05 * KM_PER_DEG, a.sharedKm, 0.25);
        assertFalse(a.wrongDirection);
    }

    @Test(expected = IllegalArgumentException.class)
    public void routeNeedsTwoPoints() {
        new DijkstraRouter().analyze(Arrays.asList(new GeoCoord(0, 0)), new GeoCoord(0, 0), null);
    }
}
