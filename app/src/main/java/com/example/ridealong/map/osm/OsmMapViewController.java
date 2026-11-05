package com.example.ridealong.map.osm;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.MapViewController;

import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class OsmMapViewController implements MapViewController {
    private final MapView map;
    private final Map<String, Marker> markers = new HashMap<>();
    private final Map<String, Polyline> routes = new HashMap<>();
    private OnMapClick clickListener;
    private Runnable idleListener;

    OsmMapViewController(MapView map) {
        this.map = map;
        map.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (clickListener != null) clickListener.onClick(new GeoCoord(p.getLatitude(), p.getLongitude()));
                return clickListener != null;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) { return false; }
        }));
        map.addMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                if (idleListener != null) idleListener.run();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                if (idleListener != null) idleListener.run();
                return false;
            }
        }, 450));
    }

    private static GeoPoint gp(GeoCoord c) { return new GeoPoint(c.lat, c.lng); }

    @Override
    public void setCenter(GeoCoord center, double zoom) {
        map.getController().setZoom(zoom);
        map.getController().setCenter(gp(center));
    }

    @Nullable
    @Override
    public GeoCoord getCenter() {
        org.osmdroid.api.IGeoPoint c = map.getMapCenter();
        return c == null ? null : new GeoCoord(c.getLatitude(), c.getLongitude());
    }

    @Override
    public void setMarker(String id, GeoCoord position, String title, int icon) {
        Marker m = markers.get(id);
        if (m == null) {
            m = new Marker(map);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            Drawable d = ContextCompat.getDrawable(map.getContext(), icon);
            if (d != null) m.setIcon(d);
            markers.put(id, m);
            map.getOverlays().add(m);
        }
        m.setPosition(gp(position));
        m.setTitle(title);
        map.invalidate();
    }

    @Override
    public void removeMarker(String id) {
        Marker m = markers.remove(id);
        if (m != null) {
            map.getOverlays().remove(m);
            map.invalidate();
        }
    }

    @Override
    public void setRoute(String id, List<GeoCoord> points, int color, float widthDp) {
        Polyline line = routes.get(id);
        if (line == null) {
            line = new Polyline(map);
            line.setInfoWindow(null);
            routes.put(id, line);
            map.getOverlays().add(1, line); // below markers
        }
        List<GeoPoint> pts = new ArrayList<>();
        for (GeoCoord c : points) pts.add(gp(c));
        line.setPoints(pts);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setStrokeWidth(widthDp * map.getResources().getDisplayMetrics().density);
        map.invalidate();
    }

    @Override
    public void clearRoute(String id) {
        Polyline line = routes.remove(id);
        if (line != null) {
            map.getOverlays().remove(line);
            map.invalidate();
        }
    }

    @Override
    public void fitTo(List<GeoCoord> points, int paddingPx) {
        if (points == null || points.isEmpty()) return;
        if (points.size() == 1) {
            setCenter(points.get(0), 15);
            return;
        }
        List<GeoPoint> gps = new ArrayList<>();
        for (GeoCoord c : points) gps.add(gp(c));
        BoundingBox box = BoundingBox.fromGeoPointsSafe(gps);
        Runnable fit = () -> map.zoomToBoundingBox(box, false, paddingPx);
        if (map.getWidth() > 0 && map.getHeight() > 0) fit.run();
        else map.addOnFirstLayoutListener((v, l, t, r, b) -> fit.run());
    }

    @Override
    public void setOnMapClickListener(@Nullable OnMapClick listener) { this.clickListener = listener; }

    @Override
    public void setOnCameraIdleListener(@Nullable Runnable listener) { this.idleListener = listener; }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void setInteractive(boolean interactive) {
        map.setMultiTouchControls(interactive);
        map.setOnTouchListener(interactive ? null : (v, e) -> true);
    }

    @Override
    public void onResume() { map.onResume(); }

    @Override
    public void onPause() { map.onPause(); }

    @Override
    public void onDestroy() { map.onDetach(); }
}