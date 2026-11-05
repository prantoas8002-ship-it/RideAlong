package com.example.ridealong.map;

import com.example.ridealong.domain.model.GeoCoord;

import java.io.Serializable;

/** A geocoded place (search hit, reverse geocode or a manually dropped pin). */
public final class PlaceResult implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final String address;
    public final double lat;
    public final double lng;

    public PlaceResult(String name, String address, double lat, double lng) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }

    public GeoCoord coord() { return new GeoCoord(lat, lng); }

    /** Short label for forms and cards. */
    public String label() {
        if (name != null && !name.isEmpty()) return name;
        return address == null ? "" : address;
    }
}