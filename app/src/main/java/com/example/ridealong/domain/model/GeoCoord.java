package com.example.ridealong.domain.model;

import java.io.Serializable;
import java.util.Locale;

/** Immutable latitude/longitude pair (WGS84 degrees). Pure Java so domain code stays testable. */
public final class GeoCoord implements Serializable {
    private static final long serialVersionUID = 1L;

    public final double lat;
    public final double lng;

    public GeoCoord(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid coordinate: " + lat + "," + lng);
        }
        this.lat = lat;
        this.lng = lng;
    }

    public static boolean isValid(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoCoord)) return false;
        GeoCoord g = (GeoCoord) o;
        return Double.compare(g.lat, lat) == 0 && Double.compare(g.lng, lng) == 0;
    }

    @Override
    public int hashCode() {
        long a = Double.doubleToLongBits(lat), b = Double.doubleToLongBits(lng);
        return (int) (a ^ (a >>> 32)) * 31 + (int) (b ^ (b >>> 32));
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%.6f,%.6f", lat, lng);
    }
}
