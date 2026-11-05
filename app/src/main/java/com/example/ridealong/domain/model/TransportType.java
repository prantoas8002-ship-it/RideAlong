package com.example.ridealong.domain.model;

/** Vehicle used for the shared ride; drives the fare estimate. */
public enum TransportType {
    CNG, RICKSHAW, CAR, BUS;

    public static TransportType fromName(String name) {
        if (name == null) return CNG;
        for (TransportType t : values()) if (t.name().equalsIgnoreCase(name)) return t;
        return CNG;
    }
}
