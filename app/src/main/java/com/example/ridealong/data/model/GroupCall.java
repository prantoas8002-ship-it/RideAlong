package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * groupCalls/{id}: a group video call among ride members (mesh WebRTC). Who is in the call right
 * now lives in the members subcollection; this document only says who may join and whether it is live.
 */
@IgnoreExtraProperties
public class GroupCall {
    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    @DocumentId public String id;
    public String rideId;
    public String hostId;
    public String hostName;
    public String status;
    public boolean video;
    /** Ride members at the moment the call started (only they can read or join it). */
    public List<String> invited = new ArrayList<>();
    public Timestamp createdAt;
    public Timestamp endedAt;

    public GroupCall() { }

    public boolean isActive() { return ACTIVE.equals(status); }
}
