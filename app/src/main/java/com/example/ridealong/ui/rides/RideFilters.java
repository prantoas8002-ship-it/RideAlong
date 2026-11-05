package com.example.ridealong.ui.rides;

import androidx.annotation.Nullable;

import com.example.ridealong.data.model.Ride;

import java.util.Calendar;
import java.util.Locale;

/** User-chosen discovery filters (applied client-side after the geohash query). */
public final class RideFilters {
    public enum TimeWindow { ANY, ONE_HOUR, THREE_HOURS, TODAY }

    public String destinationContains = "";
    public TimeWindow window = TimeWindow.ANY;
    public int minSeats = 1;
    @Nullable public Double maxCost;

    public RideFilters copy() {
        RideFilters f = new RideFilters();
        f.destinationContains = destinationContains;
        f.window = window;
        f.minSeats = minSeats;
        f.maxCost = maxCost;
        return f;
    }

    public int activeCount() {
        int n = 0;
        if (destinationContains != null && !destinationContains.trim().isEmpty()) n++;
        if (window != TimeWindow.ANY) n++;
        if (minSeats > 1) n++;
        if (maxCost != null) n++;
        return n;
    }

    public boolean accepts(Ride r, long now) {
        if (r.seatsAvailable < minSeats) return false;
        if (maxCost != null && r.costPerPerson > maxCost) return false;
        if (destinationContains != null && !destinationContains.trim().isEmpty()) {
            String needle = destinationContains.trim().toLowerCase(Locale.ROOT);
            String hay = r.dropAddress == null ? "" : r.dropAddress.toLowerCase(Locale.ROOT);
            if (!hay.contains(needle)) return false;
        }
        long dep = r.departureMillis();
        switch (window) {
            case ONE_HOUR: return dep <= now + 3600_000L;
            case THREE_HOURS: return dep <= now + 3 * 3600_000L;
            case TODAY: {
                Calendar end = Calendar.getInstance();
                end.set(Calendar.HOUR_OF_DAY, 23);
                end.set(Calendar.MINUTE, 59);
                end.set(Calendar.SECOND, 59);
                return dep <= end.getTimeInMillis();
            }
            default: return true;
        }
    }
}