package com.example.ridealong.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends ViewModel {
    private String rideId;
    private LiveData<Resource<Ride>> ride;
    private final MediatorLiveData<Resource<List<ChatMessage>>> messages = new MediatorLiveData<>();
    private final MutableLiveData<Event<Throwable>> error = new MutableLiveData<>();
    private final MutableLiveData<List<User>> members = new MutableLiveData<>(new ArrayList<>());
    private String membersKey = "";
    private boolean serverDelivered;

    public void init(String rideId) {
        if (this.rideId != null) return;
        this.rideId = rideId;
        Me.preload();
        ride = ServiceLocator.get().rides().observeRide(rideId);
        messages.addSource(ServiceLocator.get().chat().observeMessages(rideId), r -> {
            if (r == null) return;
            if (r.isSuccess() && !r.fromCache) serverDelivered = true;
            if (!serverDelivered && (r.isError() || (r.isSuccess() && r.fromCache && (r.data == null || r.data.isEmpty())))) {
                // Offline or no access to the live listener: show the Room history (read-only).
                AppExecutors.io().execute(() -> {
                    List<ChatMessage> cached = ServiceLocator.get().chat().cachedMessages(rideId);
                    AppExecutors.main(() -> {
                        if (serverDelivered) return;
                        if (!cached.isEmpty()) messages.setValue(Resource.success(cached, true));
                        else messages.setValue(r);
                    });
                });
            } else {
                messages.setValue(r);
                if (r.isSuccess() && !r.fromCache) markRead();
            }
        });
    }

    public LiveData<Resource<Ride>> ride() { return ride; }

    public LiveData<Resource<List<ChatMessage>>> messages() { return messages; }

    public LiveData<Event<Throwable>> error() { return error; }

    public LiveData<List<User>> members() { return members; }

    public void onRide(Ride r) {
        String key = String.join(",", r.memberIds);
        if (key.equals(membersKey)) return;
        membersKey = key;
        List<Task<User>> tasks = new ArrayList<>();
        for (String m : r.memberIds) tasks.add(ServiceLocator.get().users().getUser(m));
        Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
            List<User> out = new ArrayList<>();
            for (Task<User> u : tasks) if (u.isSuccessful() && u.getResult() != null) out.add(u.getResult());
            members.setValue(out);
        });
    }

    public void send(String text) {
        Resource<Ride> r = ride.getValue();
        if (r == null || r.data == null || text.trim().isEmpty()) return;
        ServiceLocator.get().chat().send(r.data, text, Me.name()).addOnFailureListener(e -> error.setValue(new Event<>(e)));
    }

    public void markRead() {
        String me = Me.uid();
        if (me != null && rideId != null) ServiceLocator.get().chat().markRead(rideId, me);
    }
}