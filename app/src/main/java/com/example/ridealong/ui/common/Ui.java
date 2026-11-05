package com.example.ridealong.ui.common;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.StringRes;

import com.example.ridealong.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/** Small UI conveniences. */
public final class Ui {
    private Ui() { }

    public static void snack(View anchor, CharSequence msg) {
        if (anchor == null) return;
        Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG).show();
    }

    public static void snack(View anchor, @StringRes int msg) {
        if (anchor == null) return;
        Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG).show();
    }

    public static void error(View anchor, Throwable t) {
        if (anchor == null) return;
        snack(anchor, UiErrors.message(anchor.getContext(), t));
    }

    public static void confirm(Context c, @StringRes int title, @StringRes int message, @StringRes int positive,
                               Runnable onConfirm) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(positive, (d, w) -> onConfirm.run())
                .show();
    }

    public static void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static int dp(Context c, int dp) { return Math.round(dp * c.getResources().getDisplayMetrics().density); }
}