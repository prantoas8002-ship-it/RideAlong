package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** liveLocations/{rideId}/members/{uid}: members only. */
@IgnoreExtraProperties
public class LiveLocation {
    @DocumentId public String userId;
    public String name;
    public double lat;
    public double lng;
    public float accuracy;
    public float bearing;
    public Timestamp updatedAt;

    public LiveLocation() { }
}