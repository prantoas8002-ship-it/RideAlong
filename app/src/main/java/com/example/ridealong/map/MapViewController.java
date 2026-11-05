package com.example.ridealong.map;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.List;

/** Provider-independent map operations used by every screen. */
public interface MapViewController {
    interface OnMapClick {
        void onClick(GeoCoord point);
    }

    void setCenter(GeoCoord center, double zoom);

    @Nullable GeoCoord getCenter();

    void setMarker(String id, GeoCoord position, String title, @DrawableRes int icon);

    void removeMarker(String id);

    void setRoute(String id, List<GeoCoord> points, @ColorInt int color, float widthDp);

    void clearRoute(String id);

    /** Zooms to show all points (no-op for an empty list). */
    void fitTo(List<GeoCoord> points, int paddingPx);

    void setOnMapClickListener(@Nullable OnMapClick listener);

    /** Called when the user stops moving the map (used by the center-pin location picker). */
    void setOnCameraIdleListener(@Nullable Runnable listener);

    void setInteractive(boolean interactive);

    void onResume();

    void onPause();

    void onDestroy();
}