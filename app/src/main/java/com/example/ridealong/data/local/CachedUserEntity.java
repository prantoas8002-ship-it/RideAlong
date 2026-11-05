package com.example.ridealong.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Cached profile (own profile includes private fields for offline viewing). */
@Entity(tableName = "cached_users")
public class CachedUserEntity {
    @PrimaryKey @NonNull public String uid = "";
    public String fullName;
    public String organization;
    public String gender;
    public String photoBase64;
    public long ratingSum;
    public long ratingCount;
    public long completedRides;
    public boolean profileComplete;
    public long createdAtMillis;
    /** Only set for the signed-in user. */
    public String phone;
    public String email;
    public String emergencyContactsJson;
    public boolean isSelf;
    public long cachedAt;
}