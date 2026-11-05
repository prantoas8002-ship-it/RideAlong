package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** reviews/{rideId_reviewerId_revieweeId}: unique and immutable. */
@IgnoreExtraProperties
public class Review {
    @DocumentId public String id;
    public String rideId;
    public String reviewerId;
    public String reviewerName;
    public String revieweeId;
    public int rating;
    public String comment;
    public Timestamp createdAt;

    public Review() { }
}