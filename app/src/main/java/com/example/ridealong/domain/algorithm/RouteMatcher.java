package com.example.ridealong.domain.algorithm;

import com.example.ridealong.domain.config.MatchingConfig;
import com.example.ridealong.domain.model.GeoCoord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Weighted route-compatibility scoring (0..100) between a passenger's trip and a posted ride.
 *
 * Components (weights from {@link MatchingConfig}, default 25/25/35/15):
 *  - pickup proximity: Haversine / point-to-route distance from the passenger pickup
 *  - departure time difference
 *  - route similarity: Dijkstra overlap ratio + point-to-segment closeness of pickup and drop
 *  - destination compatibility
 *
 * Hard rejections: seats, max pickup distance, max time difference, max detour (ride's own limit),
 * max destination distance, and travelling against the route direction.
 * Components the passenger did not provide (drop-off, desired time) are skipped and the
 * remaining weights are re-normalised.
 */
public final class RouteMatcher {

    public enum Rejection {
        NONE, NOT_ENOUGH_SEATS, PICKUP_TOO_FAR, TIME_MISMATCH, DETOUR_TOO_LARGE, DESTINATION_TOO_FAR, WRONG_DIRECTION
    }

    /** The posted ride, as seen by the matcher. */
    public static final class Candidate {
        public final String id;
        public final GeoCoord pickup;
        public final GeoCoord drop;
        public final List<GeoCoord> route;
        public final long departureMillis;
        public final int seatsAvailable;
        public final double maxDetourKm;

        public Candidate(String id, GeoCoord pickup, GeoCoord drop, List<GeoCoord> route,
                         long departureMillis, int seatsAvailable, double maxDetourKm) {
            this.id = id;
            this.pickup = pickup;
            this.drop = drop;
            List<GeoCoord> r = route == null ? new ArrayList<>() : new ArrayList<>(route);
            if (r.size() < 2) {
                r.clear();
                r.add(pickup);
                r.add(drop);
            }
            this.route = Collections.unmodifiableList(r);
            this.departureMillis = departureMillis;
            this.seatsAvailable = seatsAvailable;
            this.maxDetourKm = maxDetourKm;
        }
    }

    /** What the passenger wants. drop and desiredDepartureMillis are optional. */
    public static final class Request {
        public final GeoCoord pickup;
        public final GeoCoord drop;
        public final Long desiredDepartureMillis;
        public final int seatsNeeded;

        public Request(GeoCoord pickup, GeoCoord drop, Long desiredDepartureMillis, int seatsNeeded) {
            if (pickup == null) throw new IllegalArgumentException("pickup required");
            this.pickup = pickup;
            this.drop = drop;
            this.desiredDepartureMillis = desiredDepartureMillis;
            this.seatsNeeded = Math.max(1, seatsNeeded);
        }
    }

    public static final class Result {
        public final String candidateId;
        public final boolean compatible;
        public final Rejection rejection;
        /** 0..100, 0 when rejected. */
        public final int score;
        public final double pickupDistanceKm;
        public final double destinationDistanceKm;
        public final double timeDifferenceMinutes;
        public final double detourKm;
        public final double sharedKm;
        public final double fareDistanceKm;

        Result(String candidateId, boolean compatible, Rejection rejection, int score, double pickupDistanceKm,
               double destinationDistanceKm, double timeDifferenceMinutes, double detourKm, double sharedKm,
               double fareDistanceKm) {
            this.candidateId = candidateId;
            this.compatible = compatible;
            this.rejection = rejection;
            this.score = score;
            this.pickupDistanceKm = pickupDistanceKm;
            this.destinationDistanceKm = destinationDistanceKm;
            this.timeDifferenceMinutes = timeDifferenceMinutes;
            this.detourKm = detourKm;
            this.sharedKm = sharedKm;
            this.fareDistanceKm = fareDistanceKm;
        }
    }

    private final MatchingConfig config;
    private final DijkstraRouter router;

    public RouteMatcher(MatchingConfig config) {
        this.config = config;
        this.router = new DijkstraRouter(config.connectorEdgesPerEndpoint, config.densifyStepKm, config.maxGraphNodes);
    }

    public Result evaluate(Candidate ride, Request req) {
        double nan = Double.NaN;
        if (ride.seatsAvailable < req.seatsNeeded) {
            return reject(ride, Rejection.NOT_ENOUGH_SEATS, nan, nan, nan, nan);
        }

        double pickupDist = Math.min(Haversine.distanceKm(req.pickup, ride.pickup),
                GeometryUtils.pointToPolylineKm(req.pickup, ride.route));
        if (pickupDist > config.maxPickupDistanceKm) {
            return reject(ride, Rejection.PICKUP_TOO_FAR, pickupDist, nan, nan, nan);
        }

        double timeDiff = nan;
        if (req.desiredDepartureMillis != null) {
            timeDiff = Math.abs(ride.departureMillis - req.desiredDepartureMillis) / 60000.0;
            if (timeDiff > config.maxTimeDifferenceMinutes) {
                return reject(ride, Rejection.TIME_MISMATCH, pickupDist, nan, timeDiff, nan);
            }
        }

        DijkstraRouter.Analysis a = router.analyze(ride.route, req.pickup, req.drop);
        if (!a.reachable) return reject(ride, Rejection.DETOUR_TOO_LARGE, pickupDist, nan, timeDiff, nan);
        if (a.wrongDirection) return reject(ride, Rejection.WRONG_DIRECTION, pickupDist, nan, timeDiff, a.detourKm);
        if (a.detourKm > ride.maxDetourKm) {
            return reject(ride, Rejection.DETOUR_TOO_LARGE, pickupDist, nan, timeDiff, a.detourKm);
        }

        double destDist = nan;
        double weightSum = 0, total = 0;

        double pickupScore = clamp01(1 - pickupDist / config.maxPickupDistanceKm);
        total += config.weightPickup * pickupScore;
        weightSum += config.weightPickup;

        if (!Double.isNaN(timeDiff)) {
            double timeScore = clamp01(1 - timeDiff / config.maxTimeDifferenceMinutes);
            total += config.weightTime * timeScore;
            weightSum += config.weightTime;
        }

        if (req.drop != null) {
            destDist = Math.min(Haversine.distanceKm(req.drop, ride.drop),
                    GeometryUtils.pointToPolylineKm(req.drop, ride.route));
            if (destDist > config.maxDestinationDistanceKm) {
                return reject(ride, Rejection.DESTINATION_TOO_FAR, pickupDist, destDist, timeDiff, a.detourKm);
            }
            double direct = Haversine.distanceKm(req.pickup, req.drop);
            double overlapRatio = direct < 0.05 ? 1 : clamp01(a.sharedKm / direct);
            double perpPickup = GeometryUtils.pointToPolylineKm(req.pickup, ride.route);
            double perpDrop = GeometryUtils.pointToPolylineKm(req.drop, ride.route);
            double closeness = clamp01(1 - ((perpPickup + perpDrop) / 2) / config.maxPickupDistanceKm);
            double routeScore = 0.6 * overlapRatio + 0.4 * closeness;
            total += config.weightRoute * routeScore;
            weightSum += config.weightRoute;

            double destScore = clamp01(1 - destDist / config.maxDestinationDistanceKm);
            total += config.weightDestination * destScore;
            weightSum += config.weightDestination;
        }

        int score = weightSum <= 0 ? 0 : (int) Math.round(100 * total / weightSum);
        return new Result(ride.id, true, Rejection.NONE, Math.max(0, Math.min(100, score)), pickupDist, destDist,
                timeDiff, a.detourKm, a.sharedKm, a.fareDistanceKm);
    }

    /** Evaluates all candidates and returns compatible ones sorted by score (desc). */
    public List<Result> rank(List<Candidate> candidates, Request req) {
        List<Result> out = new ArrayList<>();
        for (Candidate c : candidates) {
            Result r = evaluate(c, req);
            if (r.compatible) out.add(r);
        }
        out.sort((x, y) -> Integer.compare(y.score, x.score));
        return out;
    }

    private static Result reject(Candidate ride, Rejection why, double pickupDist, double destDist, double timeDiff,
                                 double detour) {
        return new Result(ride.id, false, why, 0, pickupDist, destDist, timeDiff, detour, Double.NaN, Double.NaN);
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
}
