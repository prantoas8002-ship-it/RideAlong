package com.example.ridealong.ui.rides;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.PrivateInfo;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.RideRepository;
import com.example.ridealong.domain.algorithm.PolylineCodec;
import com.example.ridealong.domain.calculator.FareCalculator;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.domain.model.TransportType;
import com.example.ridealong.domain.validator.RideInputValidator;
import com.example.ridealong.domain.validator.SeatValidator;
import com.example.ridealong.map.PlaceResult;
import com.example.ridealong.map.ProviderCallback;
import com.example.ridealong.map.RouteResult;
import com.example.ridealong.utils.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.Collections;
import java.util.Set;

public class PostRideViewModel extends AndroidViewModel {
    private final MutableLiveData<PlaceResult> pickup = new MutableLiveData<>();
    private final MutableLiveData<PlaceResult> drop = new MutableLiveData<>();
    private final MutableLiveData<Long> departure = new MutableLiveData<>();
    private final MutableLiveData<TransportType> transport = new MutableLiveData<>(TransportType.CNG);
    private final MutableLiveData<Integer> seats = new MutableLiveData<>(AppConfig.get().postDefaultSeats);
    private final MutableLiveData<RouteResult> route = new MutableLiveData<>();
    private final MutableLiveData<Boolean> routeLoading = new MutableLiveData<>(false);
    private final MediatorLiveData<Double> estimate = new MediatorLiveData<>();
    private final MutableLiveData<Set<RideInputValidator.Error>> errors = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Throwable>> failure = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> posted = new MutableLiveData<>();
    private int routeSeq;

    public PostRideViewModel(@NonNull Application app) {
        super(app);
        estimate.addSource(route, r -> recompute());
        estimate.addSource(transport, t -> recompute());
        estimate.addSource(seats, s -> recompute());
    }

    public LiveData<PlaceResult> pickup() { return pickup; }

    public LiveData<PlaceResult> drop() { return drop; }

    public LiveData<Long> departure() { return departure; }

    public LiveData<TransportType> transport() { return transport; }

    public LiveData<Integer> seats() { return seats; }

    public LiveData<RouteResult> route() { return route; }

    public LiveData<Boolean> routeLoading() { return routeLoading; }

    /** Estimated cost per person (ESTIMATE ONLY), or null without a route. */
    public LiveData<Double> estimate() { return estimate; }

    public LiveData<Set<RideInputValidator.Error>> errors() { return errors; }

    public LiveData<Boolean> busy() { return busy; }

    public LiveData<Event<Throwable>> failure() { return failure; }

    public LiveData<Event<String>> posted() { return posted; }

    public void setPickup(PlaceResult p) {
        pickup.setValue(p);
        refreshRoute();
    }

    public void setDrop(PlaceResult p) {
        drop.setValue(p);
        refreshRoute();
    }

    public void setDeparture(long millis) { departure.setValue(millis); }

    public void setTransport(TransportType t) { transport.setValue(t); }

    public void changeSeats(int delta) {
        int cur = seats.getValue() == null ? 1 : seats.getValue();
        int next = Math.max(SeatValidator.MIN_SEATS, Math.min(SeatValidator.MAX_SEATS, cur + delta));
        seats.setValue(next);
    }

    public int participants() {
        Integer s = seats.getValue();
        return (s == null ? 1 : s) + 1; // poster + riders
    }

    private void refreshRoute() {
        PlaceResult a = pickup.getValue(), b = drop.getValue();
        route.setValue(null);
        if (a == null || b == null) return;
        int seq = ++routeSeq;
        routeLoading.setValue(true);
        ServiceLocator.get().maps().routing().route(a.coord(), b.coord(), new ProviderCallback<RouteResult>() {
            @Override
            public void onSuccess(RouteResult r) {
                if (seq != routeSeq) return;
                routeLoading.setValue(false);
                route.setValue(r);
            }

            @Override
            public void onError(Exception e) { // FallbackRoutingProvider never errors, but stay safe
                if (seq != routeSeq) return;
                routeLoading.setValue(false);
            }
        });
    }

    private void recompute() {
        RouteResult r = route.getValue();
        if (r == null) {
            estimate.setValue(null);
            return;
        }
        FareCalculator fc = ServiceLocator.get().fareCalculator();
        estimate.setValue(fc.estimatePerPerson(transport.getValue(), r.distanceKm, participants()));
    }

    public void submit(Double cost, Double detourKm, String comment) {
        RideInputValidator.Input in = new RideInputValidator.Input();
        in.pickup = pickup.getValue() == null ? null : pickup.getValue().coord();
        in.drop = drop.getValue() == null ? null : drop.getValue().coord();
        in.departureMillis = departure.getValue();
        in.seats = seats.getValue();
        in.costPerPerson = cost;
        in.maxDetourKm = detourKm;
        in.comment = comment;
        Set<RideInputValidator.Error> e = RideInputValidator.validate(in, System.currentTimeMillis());
        errors.setValue(e);
        if (!e.isEmpty()) return;

        String uid = ServiceLocator.get().auth().uid();
        if (uid == null) return;
        busy.setValue(true);
        Task<User> tu = ServiceLocator.get().users().getUser(uid);
        Task<PrivateInfo> tp = ServiceLocator.get().users().getPrivate(uid);
        Tasks.whenAllComplete(tu, tp).onSuccessTask(all -> {
            RideRepository.RideDraft d = new RideRepository.RideDraft();
            d.pickup = in.pickup;
            d.drop = in.drop;
            d.pickupAddress = pickup.getValue().label();
            d.dropAddress = drop.getValue().label();
            d.departureMillis = in.departureMillis;
            d.costPerPerson = cost;
            d.seats = in.seats;
            d.maxDetourKm = detourKm;
            d.comment = comment;
            d.transportType = transport.getValue().name();
            RouteResult r = route.getValue();
            if (r == null) r = com.example.ridealong.map.StraightLineRouting.route(in.pickup, in.drop);
            AppConfig cfg = AppConfig.get();
            java.util.List<GeoCoord> pts = PolylineCodec.simplify(r.points, cfg.routeSimplifyToleranceKm, cfg.routeMaxStoredPoints);
            d.routePolyline = PolylineCodec.encode(pts);
            d.routeDistanceKm = r.distanceKm;
            d.routeDurationMin = r.durationMin;
            d.routeApproximate = r.approximate;
            User u = tu.isSuccessful() ? tu.getResult() : null;
            PrivateInfo p = tp.isSuccessful() ? tp.getResult() : null;
            d.ownerName = u == null ? "" : u.displayName();
            d.ownerPhone = p == null ? "" : p.phone;
            return ServiceLocator.get().rides().postRide(d);
        }).addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful()) {
                posted.setValue(new Event<>(t.getResult()));
                reset();
            } else {
                failure.setValue(new Event<>(t.getException()));
            }
        });
    }

    private void reset() {
        routeSeq++;
        pickup.setValue(null);
        drop.setValue(null);
        departure.setValue(null);
        route.setValue(null);
        seats.setValue(AppConfig.get().postDefaultSeats);
        errors.setValue(Collections.emptySet());
    }
}