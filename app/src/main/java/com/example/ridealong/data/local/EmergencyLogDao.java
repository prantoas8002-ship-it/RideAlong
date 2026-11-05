package com.example.ridealong.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EmergencyLogDao {
    @Insert
    long insert(EmergencyLogEntity e);

    @Query("UPDATE emergency_log SET synced = 1 WHERE localId = :id")
    void markSynced(long id);

    @Query("SELECT * FROM emergency_log WHERE userId = :uid ORDER BY createdMillis DESC")
    LiveData<List<EmergencyLogEntity>> observe(String uid);

    @Query("SELECT * FROM emergency_log WHERE synced = 0")
    List<EmergencyLogEntity> unsynced();

    @Query("DELETE FROM emergency_log")
    void clear();
}