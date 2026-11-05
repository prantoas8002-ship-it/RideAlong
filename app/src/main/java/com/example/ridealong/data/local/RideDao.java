package com.example.ridealong.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CachedRideEntity> rides);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedRideEntity ride);

    @Query("SELECT * FROM cached_rides WHERE ownerUid = :uid ORDER BY departureMillis DESC")
    LiveData<List<CachedRideEntity>> observeForUser(String uid);

    @Query("SELECT * FROM cached_rides WHERE ownerUid = :uid ORDER BY departureMillis DESC")
    List<CachedRideEntity> getForUser(String uid);

    @Query("SELECT * FROM cached_rides WHERE id = :id LIMIT 1")
    CachedRideEntity get(String id);

    @Query("DELETE FROM cached_rides WHERE ownerUid = :uid")
    void clearForUser(String uid);

    @Query("DELETE FROM cached_rides")
    void clear();
}