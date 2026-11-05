package com.example.ridealong.map.google;

import com.example.ridealong.domain.algorithm.PolylineCodec;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.HttpClient;
import com.example.ridealong.map.ProviderCallback;
import com.example.ridealong.map.RouteResult;
import com.example.ridealong.map.RoutingProvider;
import com.example.ridealong.utils.AppExecutors;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Google Routes API (computeRoutes). Requires a key with the Routes API enabled; otherwise falls back. */
public final class GoogleRoutesRoutingProvider implements RoutingProvider {
    private final String key;

    public GoogleRoutesRoutingProvider(String key) { this.key = key; }

    @Override
    public String id() { return "google-routes"; }

    @Override
    public void route(GeoCoord from, GeoCoord to, ProviderCallback<RouteResult> cb) {
        AppExecutors.io().execute(() -> {
            try {
                String body = String.format(Locale.US,
                        "{\"origin\":{\"location\":{\"latLng\":{\"latitude\":%.6f,\"longitude\":%.6f}}},"
                                + "\"destination\":{\"location\":{\"latLng\":{\"latitude\":%.6f,\"longitude\":%.6f}}},"
                                + "\"travelMode\":\"DRIVE\"}", from.lat, from.lng, to.lat, to.lng);
                Map<String, String> h = new HashMap<>();
                h.put("X-Goog-Api-Key", key);
                h.put("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline");
                JSONObject route = new JSONObject(HttpClient.post("https://routes.googleapis.com/directions/v2:computeRoutes", body, h))
                        .getJSONArray("routes").getJSONObject(0);
                String dur = route.optString("duration", "0s").replace("s", "");
                RouteResult r = new RouteResult(
                        PolylineCodec.decode(route.getJSONObject("polyline").getString("encodedPolyline")),
                        route.optDouble("distanceMeters", 0) / 1000.0,
                        (int) Math.round(Double.parseDouble(dur.isEmpty() ? "0" : dur) / 60.0), false, id());
                AppExecutors.main(() -> cb.onSuccess(r));
            } catch (Exception e) {
                AppExecutors.main(() -> cb.onError(e));
            }
        });
    }
}