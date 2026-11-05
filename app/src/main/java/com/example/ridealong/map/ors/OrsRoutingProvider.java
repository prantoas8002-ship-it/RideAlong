package com.example.ridealong.map.ors;

import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.HttpClient;
import com.example.ridealong.map.ProviderCallback;
import com.example.ridealong.map.RouteResult;
import com.example.ridealong.map.RoutingProvider;
import com.example.ridealong.utils.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** OpenRouteService (free key). Used only when ORS_API_KEY is set in local.properties. */
public final class OrsRoutingProvider implements RoutingProvider {
    private final String key;

    public OrsRoutingProvider(String key) { this.key = key; }

    @Override
    public String id() { return "ors"; }

    @Override
    public void route(GeoCoord from, GeoCoord to, ProviderCallback<RouteResult> cb) {
        AppExecutors.io().execute(() -> {
            try {
                String url = String.format(Locale.US,
                        "https://api.openrouteservice.org/v2/directions/driving-car?api_key=%s&start=%.6f,%.6f&end=%.6f,%.6f",
                        key, from.lng, from.lat, to.lng, to.lat);
                JSONObject feature = new JSONObject(HttpClient.get(url, null)).getJSONArray("features").getJSONObject(0);
                JSONArray coords = feature.getJSONObject("geometry").getJSONArray("coordinates");
                List<GeoCoord> pts = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    pts.add(new GeoCoord(c.getDouble(1), c.getDouble(0)));
                }
                JSONObject summary = feature.getJSONObject("properties").getJSONObject("summary");
                RouteResult r = new RouteResult(pts, summary.optDouble("distance", 0) / 1000.0,
                        (int) Math.round(summary.optDouble("duration", 0) / 60.0), false, id());
                AppExecutors.main(() -> cb.onSuccess(r));
            } catch (Exception e) {
                AppExecutors.main(() -> cb.onError(e));
            }
        });
    }
}