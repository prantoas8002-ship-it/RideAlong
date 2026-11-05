package com.example.ridealong.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_messages", indices = {@Index("rideId")})
public class CachedMessageEntity {
    @PrimaryKey @NonNull public String id = "";
    public String rideId;
    public String senderId;
    public String senderName;
    public String text;
    public String type;
    public long createdMillis;
    public long editedMillis;
    public boolean deleted;
}