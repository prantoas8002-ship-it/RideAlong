package com.example.ridealong.map;

import androidx.annotation.Nullable;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.List;

/** Place search and reverse geocoding. */
public interface GeocodingProvider {
    String id();

    void search(String query, @Nullable GeoCoord near, ProviderCallback<List<PlaceResult>> callback);

    void reverse(GeoCoord point, ProviderCallback<PlaceResult> callback);
}