package com.example.ridealong.map;

import com.example.ridealong.domain.model.GeoCoord;

/** Driving route between two points. Network calls happen off the main thread. */
public interface RoutingProvider {
    String id();

    void route(GeoCoord from, GeoCoord to, ProviderCallback<RouteResult> callback);
}