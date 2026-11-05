package com.example.ridealong.map.osrm;

import com.example.ridealong.domain.algorithm.PolylineCodec;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.HttpClient;
import com.example.ridealong.map.ProviderCallback;
import com.example.ridealong.map.RouteResult;
import com.example.ridealong.map.RoutingProvider;
import com.example.ridealong.utils.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** OSRM public demo server (free, no key). Fair-use only; FallbackRoutingProvider covers outages. */
public final class OsrmRoutingProvider implements RoutingProvider {
    private static final String BASE = "https://router.project-osrm.org/route/v1/driving/";

    @Override
    public String id() { return "osrm"; }

    @Override
    public void route(GeoCoord from, GeoCoord to, ProviderCallback<RouteResult> cb) {
        AppExecutors.io().execute(() -> {
            try {
                String url = BASE + String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.lng, from.lat, to.lng, to.lat)
                        + "?overview=full&geometries=polyline&alternatives=false&steps=false";
                JSONObject json = new JSONObject(HttpClient.get(url, null));
                if (!"Ok".equals(json.optString("code"))) throw new IOException("OSRM: " + json.optString("code"));
                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() == 0) throw new IOException("OSRM: no route");
                JSONObject r = routes.getJSONObject(0);
                List<GeoCoord> pts = PolylineCodec.decode(r.getString("geometry"));
                RouteResult res = new RouteResult(pts, r.getDouble("distance") / 1000.0,
                        (int) Math.round(r.getDouble("duration") / 60.0), false, id());
                AppExecutors.main(() -> cb.onSuccess(res));
            } catch (Exception e) {
                AppExecutors.main(() -> cb.onError(e));
            }
        });
    }
}