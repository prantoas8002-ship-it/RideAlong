package com.example.ridealong.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CachedMessageEntity> messages);

    @Query("SELECT * FROM cached_messages WHERE rideId = :rideId ORDER BY createdMillis ASC")
    List<CachedMessageEntity> getForRide(String rideId);

    @Query("DELETE FROM cached_messages")
    void clear();
}