package com.example.ridealong.map.google;

import androidx.annotation.Nullable;

import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.GeocodingProvider;
import com.example.ridealong.map.HttpClient;
import com.example.ridealong.map.PlaceResult;
import com.example.ridealong.map.ProviderCallback;
import com.example.ridealong.utils.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Google Geocoding REST API (needs a key with billing). Falls back to Nominatim on any error. */
public final class GoogleGeocodingProvider implements GeocodingProvider {
    private final String key;
    private final GeocodingProvider fallback;

    public GoogleGeocodingProvider(String key, GeocodingProvider fallback) {
        this.key = key;
        this.fallback = fallback;
    }

    @Override
    public String id() { return "google-geocoding"; }

    @Override
    public void search(String query, @Nullable GeoCoord near, ProviderCallback<List<PlaceResult>> cb) {
        AppExecutors.io().execute(() -> {
            try {
                String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + URLEncoder.encode(query, "UTF-8")
                        + (near == null ? "" : String.format(Locale.US, "&bounds=%.4f,%.4f|%.4f,%.4f",
                        near.lat - 0.5, near.lng - 0.5, near.lat + 0.5, near.lng + 0.5))
                        + "&key=" + key;
                List<PlaceResult> out = parse(new JSONObject(HttpClient.get(url, null)));
                AppExecutors.main(() -> cb.onSuccess(out));
            } catch (Exception e) {
                AppExecutors.main(() -> fallback.search(query, near, cb));
            }
        });
    }

    @Override
    public void reverse(GeoCoord p, ProviderCallback<PlaceResult> cb) {
        AppExecutors.io().execute(() -> {
            try {
                String url = String.format(Locale.US, "https://maps.googleapis.com/maps/api/geocode/json?latlng=%.6f,%.6f&key=%s",
                        p.lat, p.lng, key);
                List<PlaceResult> out = parse(new JSONObject(HttpClient.get(url, null)));
                if (out.isEmpty()) throw new java.io.IOException("no result");
                PlaceResult r = new PlaceResult(out.get(0).name, out.get(0).address, p.lat, p.lng);
                AppExecutors.main(() -> cb.onSuccess(r));
            } catch (Exception e) {
                AppExecutors.main(() -> fallback.reverse(p, cb));
            }
        });
    }

    private static List<PlaceResult> parse(JSONObject json) throws Exception {
        String status = json.optString("status");
        if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) throw new java.io.IOException(status);
        JSONArray results = json.optJSONArray("results");
        List<PlaceResult> out = new ArrayList<>();
        if (results == null) return out;
        for (int i = 0; i < results.length() && i < 8; i++) {
            JSONObject r = results.getJSONObject(i);
            String addr = r.optString("formatted_address");
            int comma = addr.indexOf(',');
            JSONObject loc = r.getJSONObject("geometry").getJSONObject("location");
            out.add(new PlaceResult(comma > 0 ? addr.substring(0, comma) : addr, addr, loc.getDouble("lat"), loc.getDouble("lng")));
        }
        return out;
    }
}