package com.example.ridealong.domain.algorithm;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.List;

/** Great-circle distance on a spherical Earth. Used everywhere a straight-line distance is needed. */
public final class Haversine {
    public static final double EARTH_RADIUS_KM = 6371.0088;

    private Haversine() { }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
        return EARTH_RADIUS_KM * c;
    }

    public static double distanceKm(GeoCoord a, GeoCoord b) {
        return distanceKm(a.lat, a.lng, b.lat, b.lng);
    }

    /** Total length of a polyline in km. */
    public static double polylineLengthKm(List<GeoCoord> points) {
        double total = 0;
        if (points == null) return 0;
        for (int i = 1; i < points.size(); i++) total += distanceKm(points.get(i - 1), points.get(i));
        return total;
    }
}
