package com.example.ridealong.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Local SOS log: kept even if the Firestore write could not be delivered. */
@Entity(tableName = "emergency_log")
public class EmergencyLogEntity {
    @PrimaryKey(autoGenerate = true) public long localId;
    public String userId;
    public String action;
    public Double lat;
    public Double lng;
    public String rideId;
    public String contactName;
    public long createdMillis;
    public boolean synced;
}