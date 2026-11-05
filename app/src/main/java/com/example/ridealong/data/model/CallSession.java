package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Map;

/** calls/{id}: WebRTC signaling for one in-app voice call. Only the two parties can read it. */
@IgnoreExtraProperties
public class CallSession {
    public static final String RINGING = "RINGING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String BUSY = "BUSY";
    public static final String CANCELLED = "CANCELLED";
    public static final String MISSED = "MISSED";
    public static final String ENDED = "ENDED";

    @DocumentId public String id;
    public String callerId;
    public String callerName;
    public String calleeId;
    public String calleeName;
    public String rideId;
    /** Private chat id ("" for a call between ride members). */
    public String chatId;
    public String status;
    /** true = video call (camera + audio). */
    public boolean video;
    public Map<String, String> offer;
    public Map<String, String> answer;
    public Timestamp createdAt;
    public Timestamp answeredAt;
    public Timestamp endedAt;

    public CallSession() { }
}