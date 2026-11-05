package com.example.ridealong.map;

import android.content.Context;

import com.example.ridealong.BuildConfig;
import com.example.ridealong.data.repository.SettingsRepository;
import com.example.ridealong.map.google.GoogleGeocodingProvider;
import com.example.ridealong.map.google.GoogleMapProvider;
import com.example.ridealong.map.google.GoogleRoutesRoutingProvider;
import com.example.ridealong.map.nominatim.NominatimGeocodingProvider;
import com.example.ridealong.map.ors.OrsRoutingProvider;
import com.example.ridealong.map.osm.OsmMapProvider;
import com.example.ridealong.map.osrm.OsrmRoutingProvider;
import com.example.ridealong.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses providers from ONE setting (Settings → Map provider). Free stack by default:
 * osmdroid + OSRM + Nominatim. Google implementations are used only when MAPS_API_KEY exists;
 * OpenRouteService only when ORS_API_KEY exists. The app always builds and runs without keys.
 */
public final class MapProviderFactory {
    private final Context app;
    private final SettingsRepository settings;
    private final GeocodingProvider nominatim = new NominatimGeocodingProvider();
    private final RoutingProvider osrm = new OsrmRoutingProvider();

    public MapProviderFactory(Context app, SettingsRepository settings) {
        this.app = app;
        this.settings = settings;
    }

    private boolean useGoogle() {
        return Constants.MAP_PROVIDER_GOOGLE.equals(settings.getMapProvider()) && !BuildConfig.MAPS_API_KEY.isEmpty();
    }

    public MapProvider mapProvider() {
        return useGoogle() ? new GoogleMapProvider() : new OsmMapProvider();
    }

    public RoutingProvider routing() {
        List<RoutingProvider> chain = new ArrayList<>();
        if (useGoogle()) chain.add(new GoogleRoutesRoutingProvider(BuildConfig.MAPS_API_KEY));
        if (!BuildConfig.ORS_API_KEY.isEmpty()) chain.add(new OrsRoutingProvider(BuildConfig.ORS_API_KEY));
        chain.add(osrm);
        return new FallbackRoutingProvider(chain);
    }

    public GeocodingProvider geocoding() {
        return useGoogle() ? new GoogleGeocodingProvider(BuildConfig.MAPS_API_KEY, nominatim) : nominatim;
    }
}