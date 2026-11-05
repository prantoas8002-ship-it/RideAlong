package com.example.ridealong.data.model;

import com.example.ridealong.domain.model.RequestStatus;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** rideRequests/{rideId_requesterId}. Accepted requests are the ride's participant records. */
@IgnoreExtraProperties
public class RideRequest {
    @DocumentId public String id;
    public String rideId;
    public String rideOwnerId;
    public String requesterId;
    public String requesterName;
    public int seats;
    public String status;
    public String message;
    public double compatibility;
    public double detourKm;
    public double sharedKm;
    public String rideDropAddress;
    public String ridePickupAddress;
    public Timestamp rideDepartureTime;
    public Timestamp createdAt;
    public Timestamp updatedAt;

    public RideRequest() { }

    @Exclude public RequestStatus statusEnum() { return RequestStatus.fromName(status); }
}