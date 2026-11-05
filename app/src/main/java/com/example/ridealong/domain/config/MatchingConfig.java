package com.example.ridealong.domain.config;

/**
 * Tunable weights and limits for route matching and discovery. Defaults are overridden at
 * runtime from the Firestore document {@code config/app} (field {@code matching}) when present.
 */
public final class MatchingConfig {
    public double weightPickup = 0.25;
    public double weightTime = 0.25;
    public double weightRoute = 0.35;
    public double weightDestination = 0.15;

    /** Passenger pickup must be within this distance of the ride's pickup or route. */
    public double maxPickupDistanceKm = 3.0;
    /** Passenger drop-off must be within this distance of the ride's destination or route. */
    public double maxDestinationDistanceKm = 3.0;
    /** Max |departure - desired time|. */
    public int maxTimeDifferenceMinutes = 90;
    /** Default nearby-feed radius (user can change it in Settings). */
    public double defaultSearchRadiusKm = 5.0;
    public double minSearchRadiusKm = 1.0;
    public double maxSearchRadiusKm = 25.0;

    /** Dijkstra graph parameters. */
    public int connectorEdgesPerEndpoint = 3;
    public double densifyStepKm = 0.15;
    public int maxGraphNodes = 600;

    public MatchingConfig copy() {
        MatchingConfig c = new MatchingConfig();
        c.weightPickup = weightPickup;
        c.weightTime = weightTime;
        c.weightRoute = weightRoute;
        c.weightDestination = weightDestination;
        c.maxPickupDistanceKm = maxPickupDistanceKm;
        c.maxDestinationDistanceKm = maxDestinationDistanceKm;
        c.maxTimeDifferenceMinutes = maxTimeDifferenceMinutes;
        c.defaultSearchRadiusKm = defaultSearchRadiusKm;
        c.minSearchRadiusKm = minSearchRadiusKm;
        c.maxSearchRadiusKm = maxSearchRadiusKm;
        c.connectorEdgesPerEndpoint = connectorEdgesPerEndpoint;
        c.densifyStepKm = densifyStepKm;
        c.maxGraphNodes = maxGraphNodes;
        return c;
    }
}
