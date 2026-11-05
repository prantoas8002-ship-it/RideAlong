package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** Public profile: users/{uid}. Rating/completed counters change only via rules-validated batches. */
@IgnoreExtraProperties
public class User {
    @DocumentId public String id;
    public String uid;
    public String fullName;
    public String fullNameLower;
    public String organization;
    public String gender;
    public String photoBase64;
    public long ratingSum;
    public long ratingCount;
    public long completedRides;
    public boolean profileComplete;
    public Timestamp createdAt;
    public Timestamp updatedAt;

    public User() { }

    @Exclude
    public String uidOrId() { return uid != null ? uid : id; }

    /** Average from the counters maintained atomically with each review document. */
    @Exclude
    public double averageRating() { return ratingCount <= 0 ? 0 : (double) ratingSum / ratingCount; }

    @Exclude
    public String displayName() { return fullName == null || fullName.trim().isEmpty() ? "" : fullName.trim(); }

    @Exclude
    public String firstName() {
        String n = displayName();
        int i = n.indexOf(' ');
        return i > 0 ? n.substring(0, i) : n;
    }
}