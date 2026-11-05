package com.example.ridealong.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.databinding.ActivityAuthBinding;
import com.example.ridealong.ui.MainActivity;

/** Hosts login, register, forgot password and first-time profile setup. */
public class AuthActivity extends AppCompatActivity {
    private static final String EXTRA_SETUP = "start_profile_setup";

    public static Intent profileSetupIntent(Context c) {
        return new Intent(c, AuthActivity.class).putExtra(EXTRA_SETUP, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityAuthBinding b = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.auth_nav_host);
        if (host == null) return;
        NavController nc = host.getNavController();
        // Always set the graph: on recreation NavController restores its saved back stack onto it.
        NavGraph graph = nc.getNavInflater().inflate(R.navigation.nav_auth);
        boolean setup = getIntent().getBooleanExtra(EXTRA_SETUP, false) && ServiceLocator.get().auth().isSignedIn();
        graph.setStartDestination(setup ? R.id.profileSetupFragment : R.id.loginFragment);
        nc.setGraph(graph);
    }

    /** After sign-in: finished profile → main, otherwise profile setup. */
    public void onSignedIn(NavController nc) {
        String uid = ServiceLocator.get().auth().uid();
        if (uid == null) return;
        ServiceLocator.get().users().isProfileComplete(uid).addOnCompleteListener(t -> {
            if (isFinishing()) return;
            if (t.isSuccessful() && Boolean.TRUE.equals(t.getResult())) {
                openMain();
            } else {
                nc.navigate(R.id.profileSetupFragment);
            }
        });
    }

    public void openMain() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}