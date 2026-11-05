package com.example.ridealong.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.List;
import java.util.Locale;

/**
 * Dialer / SMS intents. ACTION_DIAL and ACTION_SENDTO only pre-fill: the user must press Call or
 * Send, so the app never claims something was sent. Returns false if no app can handle it.
 */
public final class IntentUtils {
    private IntentUtils() { }

    public static boolean openDialer(Context c, String phone) {
        Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)));
        return start(c, i);
    }

    public static boolean openSms(Context c, List<String> phones, String message) {
        StringBuilder to = new StringBuilder();
        for (String p : phones) {
            if (to.length() > 0) to.append(';');
            to.append(p);
        }
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(to.toString(), ";")));
        i.putExtra("sms_body", message);
        return start(c, i);
    }

    /** Shareable OpenStreetMap link for coordinates. */
    public static String osmLink(double lat, double lng) {
        return String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f",
                lat, lng, lat, lng);
    }

    private static boolean start(Context c, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
