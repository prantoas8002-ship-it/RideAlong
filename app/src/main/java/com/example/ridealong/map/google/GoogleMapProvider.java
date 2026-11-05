package com.example.ridealong.map.google;

import android.content.Context;
import android.view.ViewGroup;

import com.example.ridealong.map.MapProvider;
import com.example.ridealong.map.MapViewController;
import com.example.ridealong.map.ProviderCallback;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;

/** Google Maps SDK. Only selectable when MAPS_API_KEY is configured (Settings hides it otherwise). */
public final class GoogleMapProvider implements MapProvider {
    @Override
    public String id() { return "google"; }

    @Override
    public void create(Context context, ViewGroup container, ProviderCallback<MapViewController> ready) {
        try {
            MapsInitializer.initialize(context.getApplicationContext());
            MapView view = new MapView(context);
            view.onCreate(null);
            container.removeAllViews();
            container.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            view.getMapAsync(map -> ready.onSuccess(new GoogleMapViewController(view, map)));
        } catch (Exception e) {
            ready.onError(e);
        }
    }
}