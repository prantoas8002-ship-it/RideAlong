package com.example.ridealong.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.databinding.ActivitySplashBinding;
import com.example.ridealong.ui.auth.AuthActivity;
import com.example.ridealong.ui.onboarding.OnboardingActivity;

/**
 * Routes to: config screen (no google-services.json) → onboarding (first run) → auth →
 * profile setup (incomplete profile) → main.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding b = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.logo.setAlpha(0f);
        b.logo.animate().alpha(1f).setDuration(400).start();

        if (!FirebaseProvider.isAvailable()) {
            b.progress.setVisibility(View.GONE);
            b.configCard.setVisibility(View.VISIBLE);
            return;
        }
        if (!ServiceLocator.get().settings().isOnboardingDone()) {
            go(new Intent(this, OnboardingActivity.class));
            return;
        }
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) {
            go(new Intent(this, AuthActivity.class));
            return;
        }
        ServiceLocator.get().users().isProfileComplete(uid).addOnCompleteListener(t -> {
            boolean complete = t.isSuccessful() && Boolean.TRUE.equals(t.getResult());
            go(complete ? new Intent(this, MainActivity.class) : AuthActivity.profileSetupIntent(this));
        });
    }

    private void go(Intent i) {
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}