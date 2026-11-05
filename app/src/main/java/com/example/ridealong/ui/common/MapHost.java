package com.example.ridealong.ui.common;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.map.MapViewController;
import com.example.ridealong.map.ProviderCallback;
import com.example.ridealong.map.osm.OsmMapProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates the configured map inside a container and forwards lifecycle events to it.
 * If the configured provider fails (e.g. Google without Play services) it falls back to OSM.
 */
public final class MapHost implements DefaultLifecycleObserver {
    public interface Ready {
        void onReady(MapViewController map);
    }

    @Nullable private MapViewController controller;
    private final List<Ready> pending = new ArrayList<>();

    public MapHost(LifecycleOwner owner, ViewGroup container) {
        owner.getLifecycle().addObserver(this);
        ServiceLocator.get().maps().mapProvider().create(container.getContext(), container,
                new ProviderCallback<MapViewController>() {
                    @Override
                    public void onSuccess(MapViewController result) { setController(result); }

                    @Override
                    public void onError(Exception e) {
                        new OsmMapProvider().create(container.getContext(), container, new ProviderCallback<MapViewController>() {
                            @Override
                            public void onSuccess(MapViewController r) { setController(r); }

                            @Override
                            public void onError(Exception e2) { /* no map available */ }
                        });
                    }
                });
    }

    private void setController(MapViewController c) {
        controller = c;
        controller.onResume();
        for (Ready r : pending) r.onReady(c);
        pending.clear();
    }

    /** Runs now if the map is ready, otherwise once it becomes ready. */
    public void whenReady(Ready r) {
        if (controller != null) r.onReady(controller);
        else pending.add(r);
    }

    @Nullable
    public MapViewController get() { return controller; }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) { if (controller != null) controller.onResume(); }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) { if (controller != null) controller.onPause(); }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        if (controller != null) controller.onDestroy();
        controller = null;
        pending.clear();
        owner.getLifecycle().removeObserver(this);
    }
}