package com.example.ridealong.ui.requests;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.Event;
import com.google.android.gms.tasks.Task;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RideRequestsViewModel extends ViewModel {
    private LiveData<Resource<Ride>> ride;
    private LiveData<Resource<List<RideRequest>>> requests;
    private final MutableLiveData<Set<String>> working = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Event<Throwable>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> message = new MutableLiveData<>();

    public void init(String rideId) {
        if (ride != null) return;
        ride = ServiceLocator.get().rides().observeRide(rideId);
        requests = ServiceLocator.get().requests().observeRequestsForRide(rideId, Me.uid());
        Me.preload();
    }

    public LiveData<Resource<Ride>> ride() { return ride; }

    public LiveData<Resource<List<RideRequest>>> requests() { return requests; }

    /** Request ids with an operation in flight (buttons disabled). */
    public LiveData<Set<String>> working() { return working; }

    public LiveData<Event<Throwable>> error() { return error; }

    public LiveData<Event<Integer>> message() { return message; }

    public void accept(RideRequest r) {
        track(r, ServiceLocator.get().requests().accept(r, Me.name()), com.example.ridealong.R.string.request_accepted_toast);
    }

    public void reject(RideRequest r) {
        track(r, ServiceLocator.get().requests().reject(r, Me.name()), com.example.ridealong.R.string.request_rejected_toast);
    }

    private void track(RideRequest r, Task<Void> task, int msg) {
        Set<String> w = new HashSet<>(working.getValue());
        w.add(r.id);
        working.setValue(w);
        task.addOnCompleteListener(t -> {
            Set<String> w2 = new HashSet<>(working.getValue());
            w2.remove(r.id);
            working.setValue(w2);
            if (t.isSuccessful()) message.setValue(new Event<>(msg));
            else error.setValue(new Event<>(t.getException()));
        });
    }
}