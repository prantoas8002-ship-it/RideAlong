package com.example.ridealong.map;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.Collections;
import java.util.List;

/** A driving route. approximate=true means a straight-line Haversine fallback (offline / routing failed). */
public final class RouteResult {
    public final List<GeoCoord> points;
    public final double distanceKm;
    public final int durationMin;
    public final boolean approximate;
    public final String provider;

    public RouteResult(List<GeoCoord> points, double distanceKm, int durationMin, boolean approximate, String provider) {
        this.points = Collections.unmodifiableList(points);
        this.distanceKm = distanceKm;
        this.durationMin = durationMin;
        this.approximate = approximate;
        this.provider = provider;
    }
}