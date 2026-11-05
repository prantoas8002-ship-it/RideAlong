package com.example.ridealong.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.databinding.DialogReportBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Block / report confirmation dialogs used on ride details, profiles and chat. */
public final class SafetyDialogs {
    private SafetyDialogs() { }

    public static void block(View anchor, String userId, String name) {
        Context c = anchor.getContext();
        new MaterialAlertDialogBuilder(c)
                .setTitle(c.getString(R.string.confirm_block_title, name))
                .setMessage(R.string.confirm_block_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_block_user, (d, w) ->
                        ServiceLocator.get().safety().block(userId, name).addOnCompleteListener(t -> {
                            if (t.isSuccessful()) Ui.snack(anchor, R.string.user_blocked);
                            else Ui.error(anchor, t.getException());
                        }))
                .show();
    }

    public static void report(View anchor, String userId, String name, @Nullable String rideId) {
        Context c = anchor.getContext();
        DialogReportBinding rb = DialogReportBinding.inflate(LayoutInflater.from(c));
        new MaterialAlertDialogBuilder(c)
                .setTitle(c.getString(R.string.report_title, name))
                .setView(rb.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_report_user, (d, w) -> {
                    int id = rb.reasons.getCheckedRadioButtonId();
                    String reason = id == R.id.reason_harassment ? "HARASSMENT" : id == R.id.reason_noshow ? "NO_SHOW"
                            : id == R.id.reason_fake ? "FAKE_PROFILE" : id == R.id.reason_other ? "OTHER" : "UNSAFE";
                    ServiceLocator.get().safety().report(userId, reason, String.valueOf(rb.details.getText()).trim(), rideId)
                            .addOnCompleteListener(t -> {
                                if (t.isSuccessful()) Ui.snack(anchor, R.string.report_sent);
                                else Ui.error(anchor, t.getException());
                            });
                })
                .show();
    }
}