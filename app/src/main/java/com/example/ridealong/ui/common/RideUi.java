package com.example.ridealong.ui.common;

import android.content.Context;
import android.widget.TextView;

import com.example.ridealong.R;
import com.example.ridealong.data.model.User;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.RequestStatus;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.domain.model.TransportType;

import java.util.Locale;

/** Formatting helpers shared by ride screens (status labels, costs, transport names). */
public final class RideUi {
    private RideUi() { }

    public static String statusLabel(Context c, RideStatus s) {
        switch (s) {
            case FULL: return c.getString(R.string.ride_status_full);
            case IN_PROGRESS: return c.getString(R.string.ride_status_in_progress);
            case COMPLETED: return c.getString(R.string.ride_status_completed);
            case CANCELLED: return c.getString(R.string.ride_status_cancelled);
            default: return c.getString(R.string.ride_status_open);
        }
    }

    /** Styles a pill TextView for a ride status. */
    public static void styleStatus(TextView pill, RideStatus s) {
        Context c = pill.getContext();
        pill.setText(statusLabel(c, s));
        int bg, fg;
        switch (s) {
            case IN_PROGRESS: bg = R.drawable.bg_pill_success; fg = R.color.ra_success; break;
            case FULL: bg = R.drawable.bg_pill_warning; fg = R.color.ra_warning; break;
            case CANCELLED: bg = R.drawable.bg_pill_error; fg = R.color.ra_error; break;
            case COMPLETED: bg = R.drawable.bg_pill; fg = R.color.ra_text_secondary; break;
            default: bg = R.drawable.bg_pill_primary; fg = R.color.ra_on_primary_container;
        }
        pill.setBackgroundResource(bg);
        pill.setTextColor(c.getColor(fg));
    }

    public static String requestStatusLabel(Context c, RequestStatus s) {
        switch (s) {
            case ACCEPTED: return c.getString(R.string.request_status_accepted);
            case REJECTED: return c.getString(R.string.request_status_rejected);
            case CANCELLED: return c.getString(R.string.request_status_cancelled);
            case LEFT: return c.getString(R.string.request_status_left);
            default: return c.getString(R.string.request_status_pending);
        }
    }

    public static String money(double amount) {
        if (amount == Math.rint(amount)) return String.format(Locale.getDefault(), "%,.0f", amount);
        return String.format(Locale.getDefault(), "%,.2f", amount);
    }

    public static String cost(Context c, double perPerson) {
        if (perPerson <= 0) return c.getString(R.string.cost_free);
        return c.getString(R.string.cost_per_person, AppConfig.get().fare.currencySymbol, money(perPerson));
    }

    public static String transportLabel(Context c, TransportType t) {
        switch (t) {
            case RICKSHAW: return c.getString(R.string.transport_rickshaw);
            case CAR: return c.getString(R.string.transport_car);
            case BUS: return c.getString(R.string.transport_bus);
            default: return c.getString(R.string.transport_cng);
        }
    }

    /** "4.8 · 12 rides" or "New · 0 rides". */
    public static void bindRating(TextView value, TextView rides, User u) {
        Context c = value.getContext();
        if (u == null || u.ratingCount <= 0) value.setText(R.string.rating_none);
        else value.setText(c.getString(R.string.rating_value, u.averageRating()));
        long completed = u == null ? 0 : u.completedRides;
        rides.setText(c.getResources().getQuantityString(R.plurals.rides_count, (int) completed, (int) completed));
    }
}