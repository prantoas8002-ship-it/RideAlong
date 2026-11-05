package com.example.ridealong.domain.validator;

import com.example.ridealong.domain.algorithm.Haversine;
import com.example.ridealong.domain.model.GeoCoord;

import java.util.EnumSet;
import java.util.Set;

/** Validates the Post Ride form. Returns error codes so the UI layer maps them to string resources. */
public final class RideInputValidator {

    public enum Error {
        PICKUP_REQUIRED, DROP_REQUIRED, SAME_LOCATION, TIME_REQUIRED, TIME_IN_PAST, TIME_TOO_FAR,
        SEATS_OUT_OF_RANGE, COST_REQUIRED, COST_NEGATIVE, COST_TOO_HIGH, DETOUR_OUT_OF_RANGE, COMMENT_TOO_LONG
    }

    public static final double MIN_LOCATION_SEPARATION_KM = 0.1;
    public static final long MIN_LEAD_MILLIS = 60_000L;            // at least 1 minute in the future
    public static final long MAX_ADVANCE_MILLIS = 30L * 24 * 3600 * 1000; // 30 days
    public static final double MAX_COST = 100_000;
    public static final double MAX_DETOUR_KM = 20;
    public static final int MAX_COMMENT = 500;

    public static final class Input {
        public GeoCoord pickup;
        public GeoCoord drop;
        public Long departureMillis;
        public Integer seats;
        public Double costPerPerson;
        public Double maxDetourKm;
        public String comment;
    }

    private RideInputValidator() { }

    public static Set<Error> validate(Input in, long nowMillis) {
        Set<Error> errors = EnumSet.noneOf(Error.class);
        if (in.pickup == null) errors.add(Error.PICKUP_REQUIRED);
        if (in.drop == null) errors.add(Error.DROP_REQUIRED);
        if (in.pickup != null && in.drop != null
                && Haversine.distanceKm(in.pickup, in.drop) < MIN_LOCATION_SEPARATION_KM) {
            errors.add(Error.SAME_LOCATION);
        }
        if (in.departureMillis == null) {
            errors.add(Error.TIME_REQUIRED);
        } else if (in.departureMillis < nowMillis + MIN_LEAD_MILLIS) {
            errors.add(Error.TIME_IN_PAST);
        } else if (in.departureMillis > nowMillis + MAX_ADVANCE_MILLIS) {
            errors.add(Error.TIME_TOO_FAR);
        }
        if (in.seats == null || !SeatValidator.isValidTotalSeats(in.seats)) errors.add(Error.SEATS_OUT_OF_RANGE);
        if (in.costPerPerson == null || Double.isNaN(in.costPerPerson)) {
            errors.add(Error.COST_REQUIRED);
        } else if (in.costPerPerson < 0) {
            errors.add(Error.COST_NEGATIVE);
        } else if (in.costPerPerson > MAX_COST) {
            errors.add(Error.COST_TOO_HIGH);
        }
        if (in.maxDetourKm == null || in.maxDetourKm < 0 || in.maxDetourKm > MAX_DETOUR_KM
                || Double.isNaN(in.maxDetourKm)) {
            errors.add(Error.DETOUR_OUT_OF_RANGE);
        }
        if (in.comment != null && in.comment.length() > MAX_COMMENT) errors.add(Error.COMMENT_TOO_LONG);
        return errors;
    }
}
