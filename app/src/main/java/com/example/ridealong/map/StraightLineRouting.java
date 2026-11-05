package com.example.ridealong.map;

import com.example.ridealong.domain.algorithm.Haversine;
import com.example.ridealong.domain.model.GeoCoord;

import java.util.Arrays;

/** Offline fallback: Haversine straight line, assuming ~20 km/h average city speed. Never fails. */
public final class StraightLineRouting {
    private static final double CITY_KMH = 20.0;

    private StraightLineRouting() { }

    public static RouteResult route(GeoCoord from, GeoCoord to) {
        double km = Haversine.distanceKm(from, to);
        int minutes = (int) Math.max(1, Math.round(km / CITY_KMH * 60));
        return new RouteResult(Arrays.asList(from, to), km, minutes, true, "haversine");
    }
}