package com.example.ridealong.ui.tracking;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.LiveLocation;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.RideStatus;

import java.util.List;

public class LiveTrackingViewModel extends ViewModel {
    private LiveData<Resource<Ride>> ride;
    private LiveData<Resource<List<LiveLocation>>> locations;

    public void init(String rideId) {
        if (ride != null) return;
        ride = ServiceLocator.get().rides().observeRide(rideId);
        locations = ServiceLocator.get().liveLocations().observe(rideId);
    }

    public LiveData<Resource<Ride>> ride() { return ride; }

    public LiveData<Resource<List<LiveLocation>>> locations() { return locations; }

    /** Mirrors the liveLocations rule: in-progress, or open/full and departing within the window. */
    public static boolean sharingAllowed(Ride r, String uid) {
        if (r == null || !r.isMember(uid)) return false;
        RideStatus s = r.statusEnum();
        if (s == RideStatus.IN_PROGRESS) return true;
        if (s != RideStatus.OPEN && s != RideStatus.FULL) return false;
        return r.departureMillis() - System.currentTimeMillis() <= AppConfig.get().sharingWindowBeforeDepartureMs;
    }
}