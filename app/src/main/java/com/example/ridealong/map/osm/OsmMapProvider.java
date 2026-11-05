package com.example.ridealong.map.osm;

import android.content.Context;
import android.view.ViewGroup;

import com.example.ridealong.map.MapProvider;
import com.example.ridealong.map.MapViewController;
import com.example.ridealong.map.ProviderCallback;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;

/** OpenStreetMap via osmdroid: free, no API key. Tiles follow the OSM tile usage policy (UA set in App). */
public final class OsmMapProvider implements MapProvider {
    @Override
    public String id() { return "osm"; }

    @Override
    public void create(Context context, ViewGroup container, ProviderCallback<MapViewController> ready) {
        MapView map = new MapView(context);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(4.0);
        map.setMaxZoomLevel(19.0);
        CopyrightOverlay copyright = new CopyrightOverlay(context);
        copyright.setTextSize(10);
        map.getOverlays().add(copyright);
        container.removeAllViews();
        container.addView(map, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ready.onSuccess(new OsmMapViewController(map));
    }
}