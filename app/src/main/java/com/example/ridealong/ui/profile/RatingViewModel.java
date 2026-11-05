package com.example.ridealong.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RatingViewModel extends ViewModel {
    private LiveData<Resource<Ride>> ride;
    private final MutableLiveData<List<User>> targets = new MutableLiveData<>();
    private final MutableLiveData<Set<String>> done = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Set<String>> working = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Event<Throwable>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> message = new MutableLiveData<>();
    private boolean loaded;

    public void init(String rideId) {
        if (ride != null) return;
        Me.preload();
        ride = ServiceLocator.get().rides().observeRide(rideId);
    }

    public LiveData<Resource<Ride>> ride() { return ride; }

    public LiveData<List<User>> targets() { return targets; }

    public LiveData<Set<String>> done() { return done; }

    public LiveData<Set<String>> working() { return working; }

    public LiveData<Event<Throwable>> error() { return error; }

    public LiveData<Event<Integer>> message() { return message; }

    public void onRide(Ride r) {
        if (loaded) return;
        loaded = true;
        String me = Me.uid();
        List<String> others = new ArrayList<>();
        for (String m : r.memberIds) if (!m.equals(me)) others.add(m);
        ServiceLocator.get().reviews().alreadyReviewed(r.id, me, others).addOnSuccessListener(done::setValue);
        List<Task<User>> tasks = new ArrayList<>();
        for (String o : others) tasks.add(ServiceLocator.get().users().getUser(o));
        Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
            List<User> out = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                User u = tasks.get(i).isSuccessful() ? tasks.get(i).getResult() : null;
                if (u == null) {
                    u = new User();
                    u.id = others.get(i);
                    u.uid = others.get(i);
                }
                out.add(u);
            }
            targets.setValue(out);
        });
    }

    public void submit(Ride r, String revieweeId, int stars, String comment) {
        Set<String> w = new HashSet<>(working.getValue());
        w.add(revieweeId);
        working.setValue(w);
        ServiceLocator.get().reviews().submit(r, revieweeId, stars, comment, Me.name()).addOnCompleteListener(t -> {
            Set<String> w2 = new HashSet<>(working.getValue());
            w2.remove(revieweeId);
            working.setValue(w2);
            if (t.isSuccessful()) {
                Set<String> d = new HashSet<>(done.getValue());
                d.add(revieweeId);
                done.setValue(d);
                message.setValue(new Event<>(com.example.ridealong.R.string.rating_submitted));
            } else {
                error.setValue(new Event<>(t.getException()));
            }
        });
    }
}