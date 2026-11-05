package com.example.ridealong.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.databinding.ActivityMainBinding;
import com.example.ridealong.service.RealtimeNotificationListener;
import com.example.ridealong.service.RealtimeSyncService;
import com.example.ridealong.ui.auth.AuthActivity;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.PermissionUtils;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashSet;
import java.util.Set;

/** Single activity hosting all signed-in screens (Navigation Component + bottom navigation). */
public class MainActivity extends AppCompatActivity {
    private static final Set<Integer> TOP_LEVEL = new HashSet<>();

    static {
        TOP_LEVEL.add(R.id.homeFragment);
        TOP_LEVEL.add(R.id.discoverFragment);
        TOP_LEVEL.add(R.id.postRideFragment);
        TOP_LEVEL.add(R.id.notificationsFragment);
        TOP_LEVEL.add(R.id.profileFragment);
    }

    private ActivityMainBinding b;
    private NavController nav;
    private AppBarConfiguration appBar;
    private Boolean wasOnline;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!FirebaseProvider.isAvailable() || FirebaseProvider.uidOrNull() == null) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host == null) return;
        nav = host.getNavController();
        appBar = new AppBarConfiguration.Builder(TOP_LEVEL).build();
        NavigationUI.setupActionBarWithNavController(this, nav, appBar);
        NavigationUI.setupWithNavController(b.bottomNav, nav);
        nav.addOnDestinationChangedListener((controller, dest, args) ->
                b.bottomNav.setVisibility(TOP_LEVEL.contains(dest.getId()) ? View.VISIBLE : View.GONE));

        observeConnectivity();
        observeBadge();
        // Keep the block list listener alive so feeds and requests are filtered.
        ServiceLocator.get().safety().observeHiddenUserIds(FirebaseProvider.uidOrNull()).observe(this, ids -> { });
        RealtimeNotificationListener.get(this).start();
        if (ServiceLocator.get().settings().backgroundSync()) RealtimeSyncService.start(this);
        askNotificationPermissionOnce();
        if (savedInstanceState == null) handleIntent(getIntent());
    }

    private void observeConnectivity() {
        ServiceLocator.get().network().online().observe(this, online -> {
            boolean on = Boolean.TRUE.equals(online);
            b.offlineBanner.setVisibility(on ? View.GONE : View.VISIBLE);
            if (on && Boolean.FALSE.equals(wasOnline)) {
                Snackbar.make(b.mainRoot, R.string.back_online, Snackbar.LENGTH_SHORT).setAnchorView(b.bottomNav).show();
            }
            wasOnline = on;
        });
    }

    private void observeBadge() {
        RealtimeNotificationListener.get(this).unreadCount().observe(this, count -> {
            if (count != null && count > 0) {
                BadgeDrawable badge = b.bottomNav.getOrCreateBadge(R.id.notificationsFragment);
                badge.setNumber(count);
                badge.setMaxCharacterCount(3);
                badge.setBackgroundColor(getColor(R.color.ra_error));
                badge.setVisible(true);
            } else {
                b.bottomNav.removeBadge(R.id.notificationsFragment);
            }
        });
    }

    private void askNotificationPermissionOnce() {
        if (!PermissionUtils.needsNotificationPermission() || PermissionUtils.hasNotificationPermission(this)) return;
        if (ServiceLocator.get().settings().askedNotificationPermission()) return;
        ServiceLocator.get().settings().setAskedNotificationPermission();
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /** Deep links from local notifications. */
    private void handleIntent(Intent intent) {
        if (intent == null || nav == null) return;
        if (intent.getBooleanExtra(Constants.EXTRA_OPEN_NOTIFICATIONS, false)) {
            nav.navigate(R.id.notificationsFragment);
            return;
        }
        String rideId = intent.getStringExtra(Constants.EXTRA_RIDE_ID);
        if (rideId == null) return;
        boolean chat = intent.getBooleanExtra(Constants.EXTRA_OPEN_CHAT, false);
        nav.navigate(R.id.rideDetailsFragment, Nav.ride(rideId), Nav.anim());
        if (chat) nav.navigate(R.id.chatFragment, Nav.ride(rideId), Nav.anim());
        intent.removeExtra(Constants.EXTRA_RIDE_ID);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return (nav != null && NavigationUI.navigateUp(nav, appBar)) || super.onSupportNavigateUp();
    }
}
