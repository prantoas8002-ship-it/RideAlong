package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** emergencyEvents/{id}: the user's own SOS history. */
@IgnoreExtraProperties
public class EmergencyEvent {
    @DocumentId public String id;
    public String userId;
    public String action;
    public Double lat;
    public Double lng;
    public String rideId;
    public String contactName;
    public Timestamp createdAt;

    public EmergencyEvent() { }
}