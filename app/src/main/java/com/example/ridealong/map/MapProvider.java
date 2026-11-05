package com.example.ridealong.map;

import android.content.Context;
import android.view.ViewGroup;

/** Creates an interactive map inside a container. Implementations: OSM (default) and Google (needs key). */
public interface MapProvider {
    String id();

    /** Adds the map view to {@code container}; {@code ready} is called once the map can be used. */
    void create(Context context, ViewGroup container, ProviderCallback<MapViewController> ready);
}