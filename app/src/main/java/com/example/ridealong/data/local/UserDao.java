package com.example.ridealong.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedUserEntity user);

    @Query("SELECT * FROM cached_users WHERE uid = :uid LIMIT 1")
    CachedUserEntity get(String uid);

    @Query("SELECT * FROM cached_users WHERE uid = :uid LIMIT 1")
    LiveData<CachedUserEntity> observe(String uid);

    @Query("DELETE FROM cached_users")
    void clear();
}