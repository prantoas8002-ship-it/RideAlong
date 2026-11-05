package com.example.ridealong.ui.rides;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.domain.algorithm.Haversine;
import com.example.ridealong.domain.algorithm.RouteMatcher;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.PlaceResult;
import com.example.ridealong.ui.common.RideItem;
import com.example.ridealong.utils.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * Nearby feed: geohash range queries (RideRepository) → filters → RouteMatcher compatibility
 * (Dijkstra-based overlap/detour) → sorted by score, then departure time.
 */
public class DiscoverViewModel extends ViewModel {
    private final MutableLiveData<PlaceResult> origin = new MutableLiveData<>();
    private final MutableLiveData<Boolean> originIsDevice = new MutableLiveData<>(false);
    private final MutableLiveData<PlaceResult> destination = new MutableLiveData<>();
    private final MutableLiveData<RideFilters> filters = new MutableLiveData<>(new RideFilters());
    private final MutableLiveData<Resource<List<RideItem>>> results = new MutableLiveData<>();
    private int seq;

    public LiveData<PlaceResult> origin() { return origin; }

    public LiveData<Boolean> originIsDevice() { return originIsDevice; }

    public LiveData<PlaceResult> destination() { return destination; }

    public LiveData<RideFilters> filters() { return filters; }

    public LiveData<Resource<List<RideItem>>> results() { return results; }

    public boolean hasOrigin() { return origin.getValue() != null; }

    public void setOrigin(PlaceResult p, boolean fromDevice) {
        origin.setValue(p);
        originIsDevice.setValue(fromDevice);
        load();
    }

    public void setDestination(PlaceResult p) {
        destination.setValue(p);
        load();
    }

    public void setFilters(RideFilters f) {
        filters.setValue(f);
        load();
    }

    public double radiusKm() { return ServiceLocator.get().settings().getSearchRadiusKm(); }

    public void load() {
        PlaceResult o = origin.getValue();
        if (o == null) return;
        PlaceResult dest = destination.getValue();
        RideFilters f = filters.getValue() == null ? new RideFilters() : filters.getValue().copy();
        int mySeq = ++seq;
        Resource<List<RideItem>> cur = results.getValue();
        results.setValue(Resource.loading(cur == null ? null : cur.data));

        SearchSession.origin = o;
        SearchSession.destination = dest;
        SearchSession.seatsNeeded = f.minSeats;
        SearchSession.desiredDepartureMillis = f.window == RideFilters.TimeWindow.ANY ? null : System.currentTimeMillis();

        String me = ServiceLocator.get().auth().uid();
        ServiceLocator.get().rides().nearbyOpenRides(o.coord(), radiusKm()).addOnCompleteListener(t -> {
            if (mySeq != seq) return;
            if (!t.isSuccessful()) {
                results.setValue(Resource.error(t.getException(), null));
                return;
            }
            List<Ride> rides = t.getResult();
            AppExecutors.io().execute(() -> {
                List<RideItem> items = rank(rides, o.coord(), dest == null ? null : dest.coord(), f, me);
                AppExecutors.main(() -> {
                    if (mySeq == seq) results.setValue(Resource.success(items));
                });
            });
        });
    }

    static List<RideItem> rank(List<Ride> rides, GeoCoord from, GeoCoord to, RideFilters f, String me) {
        RouteMatcher matcher = new RouteMatcher(AppConfig.get().matching);
        RouteMatcher.Request req = new RouteMatcher.Request(from, to, SearchSession.desiredDepartureMillis, f.minSeats);
        long now = System.currentTimeMillis();
        List<RideItem> out = new ArrayList<>();
        for (Ride r : rides) {
            if (me != null && r.isOwner(me)) continue;      // my own rides aren't "discoverable" to me
            if (!f.accepts(r, now)) continue;
            RouteMatcher.Result m = matcher.evaluate(toCandidate(r), req);
            if (!m.compatible) continue;                     // breaks max pickup/time/seats/detour/direction
            out.add(new RideItem(r, null, m, Haversine.distanceKm(from, r.pickup()), 0, false));
        }
        out.sort((a, b) -> {
            int c = Integer.compare(b.match.score, a.match.score);
            return c != 0 ? c : Long.compare(a.ride.departureMillis(), b.ride.departureMillis());
        });
        return out;
    }

    public static RouteMatcher.Candidate toCandidate(Ride r) {
        return new RouteMatcher.Candidate(r.id, r.pickup(), r.drop(), r.routePoints(), r.departureMillis(),
                r.seatsAvailable, r.maxDetourKm);
    }
}