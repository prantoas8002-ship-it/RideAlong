package com.example.ridealong.domain.config;

import com.example.ridealong.domain.model.TransportType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bangladesh fare rates (BDT) used for ESTIMATES only. Every value is editable here or via the
 * Firestore document {@code config/app} (field {@code fareRates.<TYPE>.<field>}).
 *
 * Defaults are rough reference values for Dhaka:
 *  - CNG auto-rickshaw: metered/negotiated, roughly base + per-km
 *  - Rickshaw: negotiated, short trips
 *  - Car: app ride-hailing style pricing
 *  - Bus: per-seat government-style per-km rate with a minimum ticket (not split between riders)
 */
public final class FareConfig {

    public static final class Rate {
        public double baseFare;
        public double perKm;
        public double minimumFare;
        /** Max riders that can share one vehicle (used to clamp the split). */
        public int capacity;
        /** true = one vehicle fare is split between riders; false = every rider pays their own ticket. */
        public boolean splitBetweenRiders;

        public Rate(double baseFare, double perKm, double minimumFare, int capacity, boolean splitBetweenRiders) {
            this.baseFare = baseFare;
            this.perKm = perKm;
            this.minimumFare = minimumFare;
            this.capacity = capacity;
            this.splitBetweenRiders = splitBetweenRiders;
        }
    }

    public final Map<TransportType, Rate> rates = new EnumMap<>(TransportType.class);
    /** Estimates are rounded to this many taka. */
    public double roundTo = 5;
    public String currencySymbol = "৳";

    public FareConfig() {
        rates.put(TransportType.CNG, new Rate(40, 18, 60, 3, true));
        rates.put(TransportType.RICKSHAW, new Rate(20, 20, 30, 2, true));
        rates.put(TransportType.CAR, new Rate(60, 30, 150, 4, true));
        rates.put(TransportType.BUS, new Rate(0, 2.45, 10, 40, false));
    }

    public Rate rate(TransportType t) {
        Rate r = rates.get(t);
        return r != null ? r : rates.get(TransportType.CNG);
    }
}
