package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** blocks/{blockerId_blockedId}. */
@IgnoreExtraProperties
public class Block {
    @DocumentId public String id;
    public String blockerId;
    public String blockedId;
    public String blockedName;
    public Timestamp createdAt;

    public Block() { }
}