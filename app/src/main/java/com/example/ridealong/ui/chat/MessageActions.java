package com.example.ridealong.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.example.ridealong.R;
import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.databinding.DialogTextInputBinding;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.utils.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Long-press menu for a chat message: Copy (anyone), Edit (mine, within 15 min), Delete for everyone (mine). */
final class MessageActions {
    interface Handler {
        void edit(ChatMessage m, String newText);

        void delete(ChatMessage m);
    }

    private MessageActions() { }

    static void show(View anchor, ChatMessage m, boolean mine, Handler handler) {
        Context c = anchor.getContext();
        boolean textMessage = Constants.MSG_TEXT.equals(m.type);
        boolean canEdit = mine && textMessage
                && System.currentTimeMillis() - m.createdMillis() < AppConfig.get().messageEditWindowMs;
        boolean canDelete = mine && textMessage;

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(c.getString(R.string.msg_action_copy));
        actions.add(() -> {
            ClipboardManager cb = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("message", m.text));
            Ui.snack(anchor, R.string.msg_copied);
        });
        if (mine && textMessage) {
            labels.add(c.getString(R.string.msg_action_edit));
            actions.add(() -> {
                if (!canEdit) {
                    Ui.snack(anchor, R.string.msg_edit_expired);
                    return;
                }
                showEdit(c, m, handler);
            });
        }
        if (canDelete) {
            labels.add(c.getString(R.string.msg_action_delete));
            actions.add(() -> new MaterialAlertDialogBuilder(c)
                    .setTitle(R.string.msg_delete_title)
                    .setMessage(R.string.msg_delete_body)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete, (d, w) -> handler.delete(m))
                    .show());
        }
        new MaterialAlertDialogBuilder(c)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private static void showEdit(Context c, ChatMessage m, Handler handler) {
        DialogTextInputBinding db = DialogTextInputBinding.inflate(LayoutInflater.from(c));
        db.input.setText(m.text);
        db.input.setSelection(db.input.getText() == null ? 0 : db.input.getText().length());
        db.inputLayout.setCounterEnabled(true);
        db.inputLayout.setCounterMaxLength(2000);
        new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.msg_edit_title)
                .setView(db.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String t = String.valueOf(db.input.getText()).trim();
                    if (!t.isEmpty() && !t.equals(m.text)) handler.edit(m, t);
                })
                .show();
    }
}
