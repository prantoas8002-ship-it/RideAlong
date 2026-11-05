package com.example.ridealong.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.ridealong.R;

/** Reusable loading / empty / error state container used by every list screen. */
public class StateView extends FrameLayout {
    private View loading;
    private View empty;
    private View error;

    public StateView(Context context) { this(context, null); }

    public StateView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.view_state, this, true);
        loading = findViewById(R.id.state_loading);
        empty = findViewById(R.id.state_empty);
        error = findViewById(R.id.state_error);
        hide();
    }

    public void showLoading() {
        setVisibility(VISIBLE);
        loading.setVisibility(VISIBLE);
        empty.setVisibility(GONE);
        error.setVisibility(GONE);
    }

    public void showEmpty(@DrawableRes int icon, @StringRes int title, @StringRes int message) {
        showEmpty(icon, title, message, 0, null);
    }

    public void showEmpty(@DrawableRes int icon, @StringRes int title, @StringRes int message,
                          @StringRes int action, @Nullable Runnable onAction) {
        setVisibility(VISIBLE);
        loading.setVisibility(GONE);
        error.setVisibility(GONE);
        empty.setVisibility(VISIBLE);
        ((ImageView) empty.findViewById(R.id.empty_icon)).setImageResource(icon);
        ((TextView) empty.findViewById(R.id.empty_title)).setText(title);
        ((TextView) empty.findViewById(R.id.empty_message)).setText(message);
        Button b = empty.findViewById(R.id.empty_action);
        if (action != 0 && onAction != null) {
            b.setVisibility(VISIBLE);
            b.setText(action);
            b.setOnClickListener(v -> onAction.run());
        } else {
            b.setVisibility(GONE);
        }
    }

    public void showError(CharSequence message, @Nullable Runnable retry) {
        setVisibility(VISIBLE);
        loading.setVisibility(GONE);
        empty.setVisibility(GONE);
        error.setVisibility(VISIBLE);
        ((TextView) error.findViewById(R.id.error_message)).setText(message);
        Button b = error.findViewById(R.id.error_retry);
        b.setVisibility(retry == null ? GONE : VISIBLE);
        if (retry != null) b.setOnClickListener(v -> retry.run());
    }

    public void hide() { setVisibility(GONE); }
}