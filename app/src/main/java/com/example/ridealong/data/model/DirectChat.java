package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * directChats/{rideId_requesterId}: private conversation between a ride's poster and one requester.
 * Exists from the moment the request is sent, before (and after) it is accepted.
 */
@IgnoreExtraProperties
public class DirectChat {
    @DocumentId public String id;
    public String rideId;
    public String ownerId;
    public String ownerName;
    public String requesterId;
    public String requesterName;
    public String rideDropAddress;
    public List<String> participants = new ArrayList<>();
    public String lastMessage;
    public String lastSenderId;
    public Timestamp lastMessageAt;
    public Map<String, Timestamp> lastReadAt = new HashMap<>();
    public Timestamp createdAt;

    public DirectChat() { }

    @Exclude
    public String otherId(String me) { return me != null && me.equals(ownerId) ? requesterId : ownerId; }

    @Exclude
    public String otherName(String me) { return me != null && me.equals(ownerId) ? requesterName : ownerName; }

    /** A message from the other person newer than my read marker. */
    @Exclude
    public boolean isUnreadFor(String me) {
        if (me == null || lastMessage == null || lastMessage.isEmpty() || me.equals(lastSenderId) || lastMessageAt == null) {
            return false;
        }
        Timestamp read = lastReadAt == null ? null : lastReadAt.get(me);
        return read == null || lastMessageAt.compareTo(read) > 0;
    }
}