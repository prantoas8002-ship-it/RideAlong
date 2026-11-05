package com.example.ridealong.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Ride history / active rides cache for offline, read-only viewing. */
@Entity(tableName = "cached_rides", indices = {@Index("ownerUid"), @Index("departureMillis")})
public class CachedRideEntity {
    @PrimaryKey @NonNull public String id = "";
    /** Which signed-in user cached this (so accounts on a shared phone don't mix). */
    public String ownerUid;
    public String rideOwnerId;
    public String ownerName;
    public String pickupAddress;
    public double pickupLat;
    public double pickupLng;
    public String dropAddress;
    public double dropLat;
    public double dropLng;
    public long departureMillis;
    public double costPerPerson;
    public int totalSeats;
    public int seatsAvailable;
    public double maxDetourKm;
    public String status;
    public String transportType;
    public String routePolyline;
    public double routeDistanceKm;
    public int routeDurationMin;
    public String memberIdsCsv;
    public String comment;
    public long cachedAt;
}