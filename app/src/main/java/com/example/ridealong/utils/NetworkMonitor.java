package com.example.ridealong.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** App-wide connectivity state used for the offline banner and offline-only behaviour. */
public final class NetworkMonitor {
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private final ConnectivityManager cm;

    public NetworkMonitor(Context context) {
        cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        online.setValue(computeOnline());
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) { online.postValue(true); }

                @Override
                public void onLost(@NonNull Network network) { online.postValue(computeOnline()); }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                    online.postValue(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
                }
            });
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Callback limit reached or permission issue: fall back to on-demand checks.
        }
    }

    private boolean computeOnline() {
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public LiveData<Boolean> online() { return online; }

    public boolean isOnline() {
        Boolean v = online.getValue();
        return v == null ? computeOnline() : v;
    }
}
