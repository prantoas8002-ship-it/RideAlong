package com.example.ridealong.ui.rides;

import androidx.annotation.Nullable;

import com.example.ridealong.map.PlaceResult;

/**
 * In-memory "what is the rider looking for" shared between Discover and Ride Details, so the
 * details screen can show (and the join request can store) the same compatibility score.
 */
public final class SearchSession {
    @Nullable public static volatile PlaceResult origin;
    @Nullable public static volatile PlaceResult destination;
    @Nullable public static volatile Long desiredDepartureMillis;
    public static volatile int seatsNeeded = 1;

    private SearchSession() { }

    public static void clear() {
        origin = null;
        destination = null;
        desiredDepartureMillis = null;
        seatsNeeded = 1;
    }
}
