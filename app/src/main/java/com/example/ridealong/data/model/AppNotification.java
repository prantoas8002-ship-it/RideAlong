package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * notifications/{id}. title/body are an English fallback; the recipient renders type + args with
 * its own string resources so notifications appear in the recipient's language.
 */
@IgnoreExtraProperties
public class AppNotification {
    @DocumentId public String id;
    public String userId;
    public String senderId;
    public String type;
    public String title;
    public String body;
    public String rideId;
    public List<String> args = new ArrayList<>();
    public boolean read;
    public Timestamp createdAt;

    public AppNotification() { }
}