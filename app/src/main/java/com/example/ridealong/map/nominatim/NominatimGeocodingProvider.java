package com.example.ridealong.map.nominatim;

import android.util.LruCache;

import androidx.annotation.Nullable;

import com.example.ridealong.domain.config.AppConfig;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nominatim (OpenStreetMap) search + reverse geocoding, following the usage policy:
 * max 1 request/second (enforced here on a single thread), identifying User-Agent (HttpClient),
 * results cached, and the UI debounces typing (AppConfig.searchDebounceMs).
 */
public final class NominatimGeocodingProvider implements GeocodingProvider {
    private static final String BASE = "https://nominatim.openstreetmap.org/";
    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor();
    private static long lastRequestAt;

    private final LruCache<String, List<PlaceResult>> searchCache = new LruCache<>(64);
    private final LruCache<String, PlaceResult> reverseCache = new LruCache<>(128);

    @Override
    public String id() { return "nominatim"; }

    private static void throttle() throws InterruptedException {
        long wait = lastRequestAt + AppConfig.get().nominatimMinIntervalMs - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        lastRequestAt = System.currentTimeMillis();
    }

    private static Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Accept-Language", Locale.getDefault().getLanguage() + ",en");
        return h;
    }

    @Override
    public void search(String query, @Nullable GeoCoord near, ProviderCallback<List<PlaceResult>> cb) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 3) {
            cb.onSuccess(Collections.emptyList());
            return;
        }
        String key = q.toLowerCase(Locale.ROOT) + (near == null ? "" : String.format(Locale.US, "@%.2f,%.2f", near.lat, near.lng));
        List<PlaceResult> cached = searchCache.get(key);
        if (cached != null) {
            cb.onSuccess(cached);
            return;
        }
        SERIAL.execute(() -> {
            try {
                throttle();
                StringBuilder url = new StringBuilder(BASE).append("search?format=jsonv2&addressdetails=0&limit=")
                        .append(AppConfig.get().geocodingResultLimit)
                        .append("&q=").append(URLEncoder.encode(q, "UTF-8"));
                if (near != null) {
                    double d = 0.5; // ~50 km bias box; bounded=0 still allows results outside
                    url.append(String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=0",
                            near.lng - d, near.lat + d, near.lng + d, near.lat - d));
                }
                JSONArray arr = new JSONArray(HttpClient.get(url.toString(), headers()));
                List<PlaceResult> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parse(arr.getJSONObject(i)));
                searchCache.put(key, out);
                AppExecutors.main(() -> cb.onSuccess(out));
            } catch (Exception e) {
                AppExecutors.main(() -> cb.onError(e));
            }
        });
    }

    @Override
    public void reverse(GeoCoord p, ProviderCallback<PlaceResult> cb) {
        String key = String.format(Locale.US, "%.5f,%.5f", p.lat, p.lng);
        PlaceResult cached = reverseCache.get(key);
        if (cached != null) {
            cb.onSuccess(cached);
            return;
        }
        SERIAL.execute(() -> {
            try {
                throttle();
                String url = String.format(Locale.US, BASE + "reverse?format=jsonv2&zoom=18&lat=%.6f&lon=%.6f", p.lat, p.lng);
                JSONObject o = new JSONObject(HttpClient.get(url, headers()));
                if (o.has("error")) throw new java.io.IOException(o.optString("error"));
                PlaceResult r = parse(o);
                PlaceResult fixed = new PlaceResult(r.name, r.address, p.lat, p.lng); // keep the exact pin
                reverseCache.put(key, fixed);
                AppExecutors.main(() -> cb.onSuccess(fixed));
            } catch (Exception e) {
                AppExecutors.main(() -> cb.onError(e));
            }
        });
    }

    private static PlaceResult parse(JSONObject o) {
        String display = o.optString("display_name", "");
        String name = o.optString("name", "");
        if (name.isEmpty()) {
            int comma = display.indexOf(',');
            name = comma > 0 ? display.substring(0, comma) : display;
        }
        return new PlaceResult(name, display, Double.parseDouble(o.optString("lat", "0")),
                Double.parseDouble(o.optString("lon", "0")));
    }
}