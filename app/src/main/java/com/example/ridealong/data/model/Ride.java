package com.example.ridealong.data.model;

import com.example.ridealong.domain.algorithm.PolylineCodec;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.domain.model.TransportType;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

/** rides/{rideId}. memberIds = owner + accepted participants (used by rules for chat/location access). */
@IgnoreExtraProperties
public class Ride {
    @DocumentId public String id;
    public String ownerId;
    public String ownerName;
    public double pickupLat;
    public double pickupLng;
    public String pickupAddress;
    public String pickupGeohash;
    public double dropLat;
    public double dropLng;
    public String dropAddress;
    public String dropGeohash;
    public Timestamp departureTime;
    public double costPerPerson;
    public int totalSeats;
    public int seatsAvailable;
    public double maxDetourKm;
    public String comment;
    public String transportType;
    public String status;
    public List<String> memberIds = new ArrayList<>();
    public String routePolyline;
    public double routeDistanceKm;
    public int routeDurationMin;
    public boolean routeApproximate;
    public String cancelReason;
    public String lastRequestId;
    public Timestamp createdAt;
    public Timestamp updatedAt;
    public Timestamp startedAt;
    public Timestamp completedAt;

    public Ride() { }

    @Exclude public RideStatus statusEnum() { return RideStatus.fromName(status); }

    @Exclude public TransportType transportEnum() { return TransportType.fromName(transportType); }

    @Exclude public GeoCoord pickup() { return new GeoCoord(pickupLat, pickupLng); }

    @Exclude public GeoCoord drop() { return new GeoCoord(dropLat, dropLng); }

    @Exclude public long departureMillis() { return departureTime == null ? 0 : departureTime.toDate().getTime(); }

    @Exclude public boolean isOwner(String uid) { return uid != null && uid.equals(ownerId); }

    @Exclude public boolean isMember(String uid) { return uid != null && memberIds != null && memberIds.contains(uid); }

    @Exclude public boolean isParticipant(String uid) { return isMember(uid) && !isOwner(uid); }

    /** Route polyline (decoded); falls back to a straight pickup→drop line. */
    @Exclude
    public List<GeoCoord> routePoints() {
        List<GeoCoord> pts = PolylineCodec.decode(routePolyline);
        if (pts.size() < 2) {
            pts = new ArrayList<>();
            pts.add(pickup());
            pts.add(drop());
        }
        return pts;
    }
}