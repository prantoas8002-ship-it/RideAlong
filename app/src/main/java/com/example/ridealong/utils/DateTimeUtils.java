package com.example.ridealong.utils;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import com.example.ridealong.R;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;

/** Locale-aware date/time formatting. Uses java.util so it works on API 24 without desugaring. */
public final class DateTimeUtils {
    private DateTimeUtils() { }

    public static long millis(Timestamp t) { return t == null ? 0 : t.toDate().getTime(); }

    /** "Today, 5:30 PM" / "Tomorrow, 8:00 AM" / "Mon, 12 Nov, 9:15 AM". */
    public static String formatDeparture(Context c, long millis) {
        String time = DateFormat.getTimeFormat(c).format(new Date(millis));
        return relativeDay(c, millis) + ", " + time;
    }

    private static String relativeDay(Context c, long millis) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        int dayDiff = then.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR)
                + 365 * (then.get(Calendar.YEAR) - now.get(Calendar.YEAR));
        if (dayDiff == 0 || dayDiff == 1 || dayDiff == -1) {
            return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS,
                    DateUtils.FORMAT_SHOW_WEEKDAY).toString();
        }
        return DateUtils.formatDateTime(c, millis, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_NO_YEAR);
    }

    public static String formatTime(Context c, long millis) {
        return DateFormat.getTimeFormat(c).format(new Date(millis));
    }

    public static String formatDate(Context c, long millis) {
        return DateUtils.formatDateTime(c, millis, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH);
    }

    public static String formatMonthYear(Context c, long millis) {
        return DateUtils.formatDateTime(c, millis, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY
                | DateUtils.FORMAT_SHOW_YEAR);
    }

    /** "5 min ago", "just now". */
    public static String relative(Context c, long millis) {
        long now = System.currentTimeMillis();
        if (Math.abs(now - millis) < 60_000) return c.getString(R.string.time_just_now);
        return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }

    /** Chat bubble time: time only for today, otherwise short date + time. */
    public static String chatTime(Context c, long millis) {
        if (DateUtils.isToday(millis)) return formatTime(c, millis);
        return DateUtils.formatDateTime(c, millis, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH
                | DateUtils.FORMAT_NO_YEAR) + " " + formatTime(c, millis);
    }
}
