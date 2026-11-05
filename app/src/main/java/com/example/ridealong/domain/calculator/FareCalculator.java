package com.example.ridealong.domain.calculator;

import com.example.ridealong.domain.config.FareConfig;
import com.example.ridealong.domain.model.TransportType;

/**
 * Fare ESTIMATES (never the final price: the poster always types the real cost).
 */
public final class FareCalculator {
    private final FareConfig config;

    public FareCalculator(FareConfig config) {
        this.config = config;
    }

    /** Estimated fare for the whole vehicle (or one ticket for BUS). */
    public double estimateTripFare(TransportType type, double distanceKm) {
        if (distanceKm < 0 || Double.isNaN(distanceKm)) throw new IllegalArgumentException("distance must be >= 0");
        FareConfig.Rate r = config.rate(type);
        double fare = Math.max(r.minimumFare, r.baseFare + r.perKm * distanceKm);
        return round(fare);
    }

    /**
     * Estimated cost per person when {@code participants} people share the trip (poster included).
     * For non-split transport (bus) every rider pays a full ticket.
     */
    public double estimatePerPerson(TransportType type, double distanceKm, int participants) {
        if (participants < 1) throw new IllegalArgumentException("participants must be >= 1");
        FareConfig.Rate r = config.rate(type);
        double trip = estimateTripFare(type, distanceKm);
        if (!r.splitBetweenRiders) return trip;
        int riders = Math.min(participants, Math.max(1, r.capacity));
        return round(trip / riders);
    }

    /**
     * A passenger's distance-proportional share: they pay for the part of the route they ride
     * (fareDistanceKm from Dijkstra), split with the other riders.
     */
    public double estimatePassengerShare(TransportType type, double rideDistanceKm, double passengerDistanceKm,
                                         int participants) {
        if (rideDistanceKm <= 0) return estimatePerPerson(type, Math.max(0, passengerDistanceKm), participants);
        FareConfig.Rate r = config.rate(type);
        if (!r.splitBetweenRiders) return estimateTripFare(type, passengerDistanceKm);
        double perPersonFull = estimatePerPerson(type, rideDistanceKm, participants);
        double fraction = Math.max(0, Math.min(1, passengerDistanceKm / rideDistanceKm));
        double share = perPersonFull * fraction;
        double minShare = r.minimumFare / Math.max(1, Math.min(participants, r.capacity));
        return round(Math.max(minShare, share));
    }

    private double round(double v) {
        double step = config.roundTo <= 0 ? 1 : config.roundTo;
        return Math.max(0, Math.round(v / step) * step);
    }

    public String currencySymbol() { return config.currencySymbol; }
}
