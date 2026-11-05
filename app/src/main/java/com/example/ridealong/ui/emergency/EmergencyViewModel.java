package com.example.ridealong.ui.emergency;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.local.EmergencyLogEntity;
import com.example.ridealong.data.model.EmergencyContact;
import com.example.ridealong.data.model.PrivateInfo;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.LocationHelper;

import java.util.ArrayList;
import java.util.List;

public class EmergencyViewModel extends AndroidViewModel {
    public enum Phase { IDLE, LOCATING, READY }

    private final MutableLiveData<Phase> phase = new MutableLiveData<>(Phase.IDLE);
    private final MutableLiveData<Location> location = new MutableLiveData<>();
    private final MutableLiveData<List<EmergencyContact>> contacts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Ride> ride = new MutableLiveData<>();
    private LiveData<List<EmergencyLogEntity>> log;
    private String rideId;

    public EmergencyViewModel(@NonNull Application app) { super(app); }

    public void init(@Nullable String rideId) {
        if (log != null) return;
        this.rideId = rideId;
        String uid = Me.uid();
        log = ServiceLocator.get().emergency().observeLocal(uid);
        ServiceLocator.get().users().getPrivate(uid).addOnSuccessListener(p -> {
            PrivateInfo info = p;
            contacts.setValue(info == null || info.emergencyContacts == null ? new ArrayList<>() : info.emergencyContacts);
        });
        if (rideId != null) ServiceLocator.get().rides().getRide(rideId).addOnSuccessListener(ride::setValue);
    }

    public LiveData<Phase> phase() { return phase; }

    public LiveData<Location> location() { return location; }

    public LiveData<List<EmergencyContact>> contacts() { return contacts; }

    public LiveData<Ride> ride() { return ride; }

    public LiveData<List<EmergencyLogEntity>> log() { return log; }

    @Nullable
    public String rideId() { return rideId; }

    public void locate() {
        phase.setValue(Phase.LOCATING);
        LocationHelper.current(getApplication(), loc -> {
            location.setValue(loc);
            phase.setValue(Phase.READY);
        });
    }

    /** Called only after the dialer / SMS app was actually opened. */
    public void logAction(String action, @Nullable String contactName) {
        Location l = location.getValue();
        ServiceLocator.get().emergency().log(action, l == null ? null : l.getLatitude(), l == null ? null : l.getLongitude(),
                rideId, contactName);
    }
}