package com.example.ridealong.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.local.CacheMapper;
import com.example.ridealong.data.local.CachedRideEntity;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.RideItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dashboard: greeting, pending requests on my rides, active rides with unread chat counts. */
public class HomeViewModel extends ViewModel {
    private LiveData<Resource<User>> me;
    private LiveData<Resource<List<RideRequest>>> pending;
    private LiveData<Integer> unreadChats;
    private final MediatorLiveData<Resource<List<RideItem>>> active = new MediatorLiveData<>();
    private final Map<String, Long> unread = new HashMap<>();
    private List<Ride> lastRides = new ArrayList<>();
    private boolean lastCached;

    public void init() {
        if (me != null) return;
        String uid = Me.uid();
        me = ServiceLocator.get().users().observeUser(uid, true);
        pending = ServiceLocator.get().requests().observePendingForOwner(uid);
        unreadChats = androidx.lifecycle.Transformations.map(ServiceLocator.get().directChats().observeMyChats(uid), r -> {
            int n = 0;
            if (r != null && r.data != null) {
                for (com.example.ridealong.data.model.DirectChat c : r.data) if (c.isUnreadFor(uid)) n++;
            }
            return n;
        });
        LiveData<Resource<List<Ride>>> live = ServiceLocator.get().rides().observeMyRides(uid);
        LiveData<List<CachedRideEntity>> cached = ServiceLocator.get().rides().observeCachedRides(uid);
        active.addSource(live, r -> {
            if (r == null) return;
            if (r.data != null) {
                publish(r.data, r.fromCache);
            } else if (r.isError()) {
                List<CachedRideEntity> c = cached.getValue();
                if (c != null) publishCached(c);
                else active.setValue(Resource.error(r.error, null));
            }
        });
        // Offline fallback source (Room). Only used when the live listener has nothing.
        active.addSource(cached, c -> {
            Resource<List<Ride>> l = live.getValue();
            if ((l == null || l.data == null) && c != null && !c.isEmpty()) publishCached(c);
        });
    }

    private void publishCached(List<CachedRideEntity> c) {
        List<Ride> rides = new ArrayList<>();
        for (CachedRideEntity e : c) rides.add(CacheMapper.toRide(e));
        publish(rides, true);
    }

    private void publish(List<Ride> rides, boolean cached) {
        lastRides = rides;
        lastCached = cached;
        emit();
        if (!cached) refreshUnread();
    }

    private void emit() {
        List<RideItem> items = new ArrayList<>();
        for (Ride r : lastRides) {
            if (!r.statusEnum().isActive()) continue;
            Long n = unread.get(r.id);
            items.add(new RideItem(r, null, null, null, n == null ? 0 : n, lastCached));
        }
        // Soonest departure first for active rides.
        items.sort((a, b) -> Long.compare(a.ride.departureMillis(), b.ride.departureMillis()));
        active.setValue(Resource.success(items, lastCached));
    }

    public void refreshUnread() {
        String uid = Me.uid();
        for (Ride r : lastRides) {
            if (!r.statusEnum().isActive()) continue;
            ServiceLocator.get().chat().unreadCount(r.id, uid).addOnSuccessListener(n -> {
                Long prev = unread.put(r.id, n);
                if (prev == null || !prev.equals(n)) emit();
            });
        }
    }

    public LiveData<Resource<User>> me() { return me; }

    public LiveData<Resource<List<RideRequest>>> pending() { return pending; }

    /** Private conversations with an unread message from the other person. */
    public LiveData<Integer> unreadChats() { return unreadChats; }

    public LiveData<Resource<List<RideItem>>> active() { return active; }
}