package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** chats/{rideId}/messages/{id}. Immutable. type: TEXT | CALL | SYSTEM. */
@IgnoreExtraProperties
public class ChatMessage {
    @DocumentId public String id;
    public String senderId;
    public String senderName;
    public String text;
    public String type;
    public Timestamp createdAt;
    /** Set when the sender edited or deleted the message. */
    public Timestamp editedAt;
    /** Soft delete: text is emptied and the bubble shows "This message was deleted". */
    public boolean deleted;
    @Exclude public boolean pending;

    public ChatMessage() { }

    @Exclude public boolean isEdited() { return editedAt != null && !deleted; }

    @Exclude public long createdMillis() { return createdAt == null ? System.currentTimeMillis() : createdAt.toDate().getTime(); }
}