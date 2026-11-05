package com.example.ridealong.map.google;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.MapViewController;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GoogleMapViewController implements MapViewController {
    private final MapView view;
    private final GoogleMap map;
    private final Map<String, Marker> markers = new HashMap<>();
    private final Map<String, Polyline> routes = new HashMap<>();
    private final Map<Integer, BitmapDescriptor> icons = new HashMap<>();

    GoogleMapViewController(MapView view, GoogleMap map) {
        this.view = view;
        this.map = map;
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
    }

    private static LatLng ll(GeoCoord c) { return new LatLng(c.lat, c.lng); }

    @Override
    public void setCenter(GeoCoord center, double zoom) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(ll(center), (float) zoom));
    }

    @Nullable
    @Override
    public GeoCoord getCenter() {
        LatLng t = map.getCameraPosition().target;
        return new GeoCoord(t.latitude, t.longitude);
    }

    private BitmapDescriptor icon(int res) {
        BitmapDescriptor d = icons.get(res);
        if (d != null) return d;
        Drawable dr = ContextCompat.getDrawable(view.getContext(), res);
        if (dr == null) return BitmapDescriptorFactory.defaultMarker();
        Bitmap b = Bitmap.createBitmap(dr.getIntrinsicWidth(), dr.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        dr.setBounds(0, 0, c.getWidth(), c.getHeight());
        dr.draw(c);
        d = BitmapDescriptorFactory.fromBitmap(b);
        icons.put(res, d);
        return d;
    }

    @Override
    public void setMarker(String id, GeoCoord position, String title, int iconRes) {
        Marker m = markers.get(id);
        if (m == null) {
            m = map.addMarker(new MarkerOptions().position(ll(position)).title(title).icon(icon(iconRes)).anchor(0.5f, 1f));
            if (m != null) markers.put(id, m);
        } else {
            m.setPosition(ll(position));
            m.setTitle(title);
        }
    }

    @Override
    public void removeMarker(String id) {
        Marker m = markers.remove(id);
        if (m != null) m.remove();
    }

    @Override
    public void setRoute(String id, List<GeoCoord> points, int color, float widthDp) {
        clearRoute(id);
        PolylineOptions o = new PolylineOptions().color(color).width(widthDp * view.getResources().getDisplayMetrics().density);
        for (GeoCoord c : points) o.add(ll(c));
        routes.put(id, map.addPolyline(o));
    }

    @Override
    public void clearRoute(String id) {
        Polyline p = routes.remove(id);
        if (p != null) p.remove();
    }

    @Override
    public void fitTo(List<GeoCoord> points, int paddingPx) {
        if (points == null || points.isEmpty()) return;
        if (points.size() == 1) {
            setCenter(points.get(0), 15);
            return;
        }
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        for (GeoCoord c : points) b.include(ll(c));
        LatLngBounds bounds = b.build();
        try {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx));
        } catch (IllegalStateException notLaidOut) {
            map.setOnMapLoadedCallback(() -> map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)));
        }
    }

    @Override
    public void setOnMapClickListener(@Nullable OnMapClick listener) {
        map.setOnMapClickListener(listener == null ? null : p -> listener.onClick(new GeoCoord(p.latitude, p.longitude)));
    }

    @Override
    public void setOnCameraIdleListener(@Nullable Runnable listener) {
        map.setOnCameraIdleListener(listener == null ? null : listener::run);
    }

    @Override
    public void setInteractive(boolean interactive) { map.getUiSettings().setAllGesturesEnabled(interactive); }

    @Override
    public void onResume() { view.onResume(); }

    @Override
    public void onPause() { view.onPause(); }

    @Override
    public void onDestroy() { view.onDestroy(); }
}