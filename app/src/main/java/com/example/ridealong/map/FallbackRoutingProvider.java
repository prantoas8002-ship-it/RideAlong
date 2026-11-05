package com.example.ridealong.map;

import android.util.Log;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.ArrayList;
import java.util.List;

/** Tries each provider in order; if all fail (offline, server down) returns a Haversine straight line. */
public final class FallbackRoutingProvider implements RoutingProvider {
    private static final String TAG = "Routing";
    private final List<RoutingProvider> chain;

    public FallbackRoutingProvider(List<RoutingProvider> chain) { this.chain = new ArrayList<>(chain); }

    @Override
    public String id() { return chain.isEmpty() ? "haversine" : chain.get(0).id(); }

    @Override
    public void route(GeoCoord from, GeoCoord to, ProviderCallback<RouteResult> callback) {
        attempt(0, from, to, callback);
    }

    private void attempt(int i, GeoCoord from, GeoCoord to, ProviderCallback<RouteResult> cb) {
        if (i >= chain.size()) {
            cb.onSuccess(StraightLineRouting.route(from, to));
            return;
        }
        chain.get(i).route(from, to, new ProviderCallback<RouteResult>() {
            @Override
            public void onSuccess(RouteResult result) {
                if (result == null || result.points.size() < 2) attempt(i + 1, from, to, cb);
                else cb.onSuccess(result);
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, chain.get(i).id() + " failed, trying next", e);
                attempt(i + 1, from, to, cb);
            }
        });
    }
}