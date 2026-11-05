package com.example.ridealong.ui.common;

import androidx.annotation.Nullable;

import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.example.ridealong.domain.algorithm.RouteMatcher;

import java.util.Objects;

/** A ride row plus optional poster profile, match result, distance and unread count. */
public final class RideItem {
    public final Ride ride;
    @Nullable public final User owner;
    @Nullable public final RouteMatcher.Result match;
    @Nullable public final Double distanceKm;
    public final long unread;
    public final boolean cached;

    public RideItem(Ride ride, @Nullable User owner, @Nullable RouteMatcher.Result match, @Nullable Double distanceKm,
                    long unread, boolean cached) {
        this.ride = ride;
        this.owner = owner;
        this.match = match;
        this.distanceKm = distanceKm;
        this.unread = unread;
        this.cached = cached;
    }

    public RideItem withOwner(User u) { return new RideItem(ride, u, match, distanceKm, unread, cached); }

    public RideItem withUnread(long n) { return new RideItem(ride, owner, match, distanceKm, n, cached); }

    boolean sameContent(RideItem o) {
        return Objects.equals(ride.status, o.ride.status) && ride.seatsAvailable == o.ride.seatsAvailable
                && ride.costPerPerson == o.ride.costPerPerson && ride.departureMillis() == o.ride.departureMillis()
                && Objects.equals(ride.dropAddress, o.ride.dropAddress)
                && (owner == null) == (o.owner == null)
                && (owner == null || (owner.ratingCount == o.owner.ratingCount && Objects.equals(owner.photoBase64, o.owner.photoBase64)))
                && unread == o.unread && cached == o.cached
                && (match == null ? o.match == null : o.match != null && match.score == o.match.score);
    }
}