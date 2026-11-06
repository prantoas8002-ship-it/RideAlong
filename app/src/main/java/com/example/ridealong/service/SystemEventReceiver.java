package com.example.ridealong.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Listens for system broadcast events that matter to a ride-sharing app:
 * <ul>
 *   <li>{@link Intent#ACTION_BATTERY_LOW} / {@link Intent#ACTION_BATTERY_OKAY}: live location sharing drains
 *       the battery, so the user is warned (banner + the sharing notification is updated).</li>
 *   <li>{@link PowerManager#ACTION_POWER_SAVE_MODE_CHANGED}: Battery Saver delays location updates.</li>
 *   <li>{@link Intent#ACTION_AIRPLANE_MODE_CHANGED}: rides, chat and calls need a connection.</li>
 * </ul>
 * These are registered at runtime (context-registered receiver) because Android 8+ no longer delivers
 * most implicit system broadcasts to receivers declared in the manifest.
 */
public class SystemEventReceiver extends BroadcastReceiver {

    /** Snapshot of the device state the app cares about. */
    public static final class State {
        public final boolean airplaneMode;
        public final boolean batteryLow;
        public final boolean powerSave;
        public final int batteryPercent;

        State(boolean airplaneMode, boolean batteryLow, boolean powerSave, int batteryPercent) {
            this.airplaneMode = airplaneMode;
            this.batteryLow = batteryLow;
            this.powerSave = powerSave;
            this.batteryPercent = batteryPercent;
        }
    }

    public interface Listener {
        /** @param action the broadcast that caused the change, or null for the initial state. */
        void onSystemStateChanged(@NonNull State state, @Nullable String action);
    }

    private static final int LOW_BATTERY_PERCENT = 15;
    private final Listener listener;
    private boolean registered;

    public SystemEventReceiver(@NonNull Listener listener) {
        this.listener = listener;
    }

    public static IntentFilter filter() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_LOW);
        f.addAction(Intent.ACTION_BATTERY_OKAY);
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        return f;
    }

    /** Registers the receiver and immediately reports the current state. */
    public void register(@NonNull Context c) {
        if (registered) return;
        ContextCompat.registerReceiver(c, this, filter(), ContextCompat.RECEIVER_NOT_EXPORTED);
        registered = true;
        listener.onSystemStateChanged(current(c, null), null);
    }

    public void unregister(@NonNull Context c) {
        if (!registered) return;
        try {
            c.unregisterReceiver(this);
        } catch (IllegalArgumentException ignored) {
            // already unregistered
        }
        registered = false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        listener.onSystemStateChanged(current(context, intent), intent.getAction());
    }

    /** Reads the current state; {@code trigger} (if any) overrides the battery-low flag. */
    @NonNull
    public static State current(@NonNull Context c, @Nullable Intent trigger) {
        boolean airplane;
        if (trigger != null && Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(trigger.getAction())) {
            airplane = trigger.getBooleanExtra("state", false);
        } else {
            airplane = Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }

        // ACTION_BATTERY_CHANGED is a sticky broadcast: registering with a null receiver returns the last value.
        Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int percent = -1;
        boolean charging = false;
        boolean low = false;
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) percent = Math.round(level * 100f / scale);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                low = battery.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false);
            } else {
                low = percent >= 0 && percent <= LOW_BATTERY_PERCENT && !charging;
            }
        }
        if (trigger != null && Intent.ACTION_BATTERY_LOW.equals(trigger.getAction())) low = true;
        if (trigger != null && Intent.ACTION_BATTERY_OKAY.equals(trigger.getAction())) low = false;

        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        boolean powerSave = pm != null && pm.isPowerSaveMode();
        return new State(airplane, low && !charging, powerSave, percent);
    }
}
