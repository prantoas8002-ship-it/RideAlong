package com.example.ridealong.ui.rides;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.local.CacheMapper;
import com.example.ridealong.data.local.CachedRideEntity;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.RequestRepository;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.domain.algorithm.RouteMatcher;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.RequestStatus;
import com.example.ridealong.map.PlaceResult;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.List;

public class RideDetailsViewModel extends ViewModel {
    /** Ride plus whether it came from the offline cache. */
    public static final class RideState {
        public final Ride ride;
        public final boolean cached;

        RideState(Ride ride, boolean cached) {
            this.ride = ride;
            this.cached = cached;
        }
    }

    private String rideId;
    private final MediatorLiveData<Resource<RideState>> ride = new MediatorLiveData<>();
    private LiveData<Resource<RideRequest>> myRequest;
    private LiveData<Integer> pendingCount;
    private final MutableLiveData<User> owner = new MutableLiveData<>();
    private final MutableLiveData<List<User>> members = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<RouteMatcher.Result> match = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Throwable>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> message = new MutableLiveData<>();
    private String membersKey = "";

    public void init(String rideId) {
        if (this.rideId != null) return;
        this.rideId = rideId;
        String me = Me.uid();
        Me.preload();
        LiveData<Resource<Ride>> live = ServiceLocator.get().rides().observeRide(rideId);
        ride.addSource(live, r -> {
            if (r == null) return;
            if (r.data != null) {
                ride.setValue(Resource.success(new RideState(r.data, r.fromCache)));
                onRide(r.data);
            } else if (r.isError() || (r.isSuccess() && r.fromCache)) {
                loadFromRoom(r.error);
            } else if (r.isSuccess()) {
                ride.setValue(Resource.success(null));
            } else {
                ride.setValue(Resource.loading(null));
            }
        });
        myRequest = ServiceLocator.get().requests().observeMyRequest(rideId, me);
        pendingCount = Transformations.map(ServiceLocator.get().requests().observeRequestsForRide(rideId, me), res -> {
            int n = 0;
            if (res != null && res.data != null) for (RideRequest q : res.data) if (q.statusEnum() == RequestStatus.PENDING) n++;
            return n;
        });
    }

    private void loadFromRoom(@Nullable Throwable err) {
        AppExecutors.io().execute(() -> {
            CachedRideEntity e = ServiceLocator.get().db().rides().get(rideId);
            AppExecutors.main(() -> {
                if (e != null) {
                    Ride r = CacheMapper.toRide(e);
                    ride.setValue(Resource.success(new RideState(r, true)));
                    onRide(r);
                } else if (err != null) {
                    ride.setValue(Resource.error(err, null));
                } else {
                    ride.setValue(Resource.success(null));
                }
            });
        });
    }

    public LiveData<Resource<RideState>> ride() { return ride; }

    public LiveData<Resource<RideRequest>> myRequest() { return myRequest; }

    public LiveData<Integer> pendingCount() { return pendingCount; }

    public LiveData<User> owner() { return owner; }

    public LiveData<List<User>> members() { return members; }

    public LiveData<RouteMatcher.Result> match() { return match; }

    public LiveData<Boolean> busy() { return busy; }

    public LiveData<Event<Throwable>> error() { return error; }

    public LiveData<Event<Integer>> message() { return message; }

    @Nullable
    public Ride current() {
        Resource<RideState> r = ride.getValue();
        return r == null || r.data == null ? null : r.data.ride;
    }

    private void onRide(Ride r) {
        if (owner.getValue() == null || !r.ownerId.equals(owner.getValue().uidOrId())) {
            ServiceLocator.get().users().getUser(r.ownerId).addOnSuccessListener(owner::setValue);
        }
        String key = r.memberIds == null ? "" : String.join(",", r.memberIds);
        if (!key.equals(membersKey)) {
            membersKey = key;
            List<Task<User>> tasks = new ArrayList<>();
            for (String m : r.memberIds) tasks.add(ServiceLocator.get().users().getUser(m));
            Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
                List<User> out = new ArrayList<>();
                for (int i = 0; i < tasks.size(); i++) {
                    User u = tasks.get(i).isSuccessful() ? tasks.get(i).getResult() : null;
                    if (u == null) {
                        u = new User();
                        u.id = r.memberIds.get(i);
                        u.uid = r.memberIds.get(i);
                    }
                    out.add(u);
                }
                members.setValue(out);
            });
        }
        computeMatch(r);
    }

    private void computeMatch(Ride r) {
        PlaceResult o = SearchSession.origin;
        if (o == null || r.isOwner(Me.uid())) {
            match.setValue(null);
            return;
        }
        PlaceResult d = SearchSession.destination;
        AppExecutors.io().execute(() -> {
            RouteMatcher.Result res = new RouteMatcher(AppConfig.get().matching).evaluate(DiscoverViewModel.toCandidate(r),
                    new RouteMatcher.Request(o.coord(), d == null ? null : d.coord(), SearchSession.desiredDepartureMillis,
                            Math.max(1, Math.min(SearchSession.seatsNeeded, Math.max(1, r.seatsAvailable)))));
            AppExecutors.main(() -> match.setValue(res));
        });
    }

    // ---------------- actions ----------------

    private void run(Task<?> task, int successMsg) {
        busy.setValue(true);
        task.addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful()) {
                if (successMsg != 0) message.setValue(new Event<>(successMsg));
            } else {
                error.setValue(new Event<>(t.getException()));
            }
        });
    }

    public void requestJoin(int seats, String text) {
        Ride r = current();
        if (r == null) return;
        RequestRepository.MatchInfo info = null;
        RouteMatcher.Result m = match.getValue();
        if (m != null && m.compatible) {
            info = new RequestRepository.MatchInfo();
            info.compatibility = m.score;
            info.detourKm = Double.isNaN(m.detourKm) ? 0 : m.detourKm;
            info.sharedKm = Double.isNaN(m.sharedKm) ? 0 : m.sharedKm;
        }
        String uid = Me.uid();
        RequestRepository.MatchInfo finalInfo = info;
        busy.setValue(true);
        ServiceLocator.get().users().getPrivate(uid).continueWithTask(p -> {
            String phone = p.isSuccessful() && p.getResult() != null ? p.getResult().phone : "";
            return ServiceLocator.get().requests().sendRequest(r, seats, text, finalInfo, Me.name(), phone,
                    ServiceLocator.get().safety());
        }).addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful()) message.setValue(new Event<>(com.example.ridealong.R.string.request_sent));
            else error.setValue(new Event<>(t.getException()));
        });
    }

    public void cancelRequest() {
        Resource<RideRequest> r = myRequest.getValue();
        if (r == null || r.data == null) return;
        run(ServiceLocator.get().requests().cancelRequest(r.data, Me.name()), com.example.ridealong.R.string.request_cancelled);
    }

    public void leave() {
        Ride r = current();
        if (r == null) return;
        run(ServiceLocator.get().requests().leaveRide(r, Me.name()), com.example.ridealong.R.string.request_left_note);
    }

    public void start() {
        Ride r = current();
        if (r != null) run(ServiceLocator.get().rides().startRide(r), com.example.ridealong.R.string.ride_started_toast);
    }

    public void complete() {
        Ride r = current();
        if (r != null) run(ServiceLocator.get().rides().completeRide(r), com.example.ridealong.R.string.ride_completed_toast);
    }

    public void cancelRide(String reason) {
        Ride r = current();
        if (r != null) run(ServiceLocator.get().rides().cancelRide(r, reason), com.example.ridealong.R.string.ride_cancelled_toast);
    }
}