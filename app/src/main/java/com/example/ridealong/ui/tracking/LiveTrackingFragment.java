package com.example.ridealong.ui.tracking;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.LiveLocation;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.databinding.FragmentLiveTrackingBinding;
import com.example.ridealong.domain.algorithm.Haversine;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.service.GeofenceManager;
import com.example.ridealong.service.LocationShareService;
import com.example.ridealong.ui.common.MapHost;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;
import com.example.ridealong.utils.PermissionUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Live map for the ride's members. Sharing requires: explicit consent (once), foreground
 * location, notification permission (Android 13+, for the service notification). Background
 * location is optional and only used for geofencing; without it the service's in-app distance
 * checks are used instead.
 */
public class LiveTrackingFragment extends Fragment {
    private FragmentLiveTrackingBinding b;
    private LiveTrackingViewModel vm;
    private MapHost map;
    private String rideId;
    private final Set<String> markerIds = new HashSet<>();
    private boolean fittedOnce;

    private final ActivityResultLauncher<String[]> fgPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (PermissionUtils.hasAnyLocation(requireContext())) afterForegroundGranted();
                else Ui.snack(b.getRoot(), R.string.permission_denied_location);
            });
    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> startService());
    private final ActivityResultLauncher<String> bgPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> registerGeofences());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentLiveTrackingBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        rideId = requireArguments().getString(Constants.ARG_RIDE_ID);
        vm = new ViewModelProvider(this).get(LiveTrackingViewModel.class);
        vm.init(rideId);
        map = new MapHost(getViewLifecycleOwner(), b.mapContainer);

        b.toggle.setOnClickListener(v -> {
            if (rideId.equals(LocationShareService.sharingRideId().getValue())) LocationShareService.stop(requireContext());
            else beginSharingFlow();
        });
        b.recenter.setOnClickListener(v -> fitAll());

        vm.ride().observe(getViewLifecycleOwner(), r -> {
            Ride ride = r == null ? null : r.data;
            if (ride == null) return;
            map.whenReady(m -> {
                m.setRoute("route", ride.routePoints(), requireContext().getColor(R.color.ra_primary), 5f);
                m.setMarker("pickup", ride.pickup(), ride.pickupAddress, R.drawable.marker_pickup);
                m.setMarker("drop", ride.drop(), ride.dropAddress, R.drawable.marker_drop);
                if (!fittedOnce) {
                    fittedOnce = true;
                    m.fitTo(ride.routePoints(), Ui.dp(requireContext(), 56));
                }
            });
            renderControls();
        });
        vm.locations().observe(getViewLifecycleOwner(), this::renderLocations);
        LocationShareService.sharingRideId().observe(getViewLifecycleOwner(), id -> renderControls());
    }

    private void renderControls() {
        if (b == null) return;
        Resource<Ride> rr = vm.ride().getValue();
        Ride ride = rr == null ? null : rr.data;
        boolean sharingThis = rideId.equals(LocationShareService.sharingRideId().getValue());
        boolean allowed = LiveTrackingViewModel.sharingAllowed(ride, Me.uid());
        b.sharingStatus.setText(sharingThis ? R.string.tracking_sharing_on : R.string.tracking_sharing_off);
        b.sharingDot.setBackgroundResource(sharingThis ? R.drawable.bg_pill_success : R.drawable.bg_pill);
        b.toggle.setText(sharingThis ? R.string.action_stop_sharing : R.string.action_start_sharing);
        b.toggle.setIconResource(sharingThis ? R.drawable.ic_stop : R.drawable.ic_navigation);
        b.toggle.setEnabled(sharingThis || allowed);
        if (!allowed && !sharingThis) {
            b.note.setVisibility(View.VISIBLE);
            b.note.setText(R.string.tracking_not_allowed);
        } else if (sharingThis && !GeofenceManager.canUseGeofencing(requireContext())) {
            b.note.setVisibility(View.VISIBLE);
            b.note.setText(R.string.geofence_fallback_note);
        } else {
            b.note.setVisibility(View.GONE);
        }
    }

    private void renderLocations(Resource<List<LiveLocation>> r) {
        if (r == null || b == null) return;
        List<LiveLocation> list = r.data == null ? new ArrayList<>() : r.data;
        String me = Me.uid();
        Resource<Ride> rr = vm.ride().getValue();
        Ride ride = rr == null ? null : rr.data;
        map.whenReady(m -> {
            Set<String> seen = new HashSet<>();
            for (LiveLocation l : list) {
                String id = "u_" + l.userId;
                seen.add(id);
                boolean isMe = l.userId.equals(me);
                m.setMarker(id, new GeoCoord(l.lat, l.lng), isMe ? getString(R.string.you) : l.name,
                        isMe ? R.drawable.marker_me : R.drawable.marker_person);
            }
            for (String old : markerIds) if (!seen.contains(old)) m.removeMarker(old);
            markerIds.clear();
            markerIds.addAll(seen);
        });

        StringBuilder sb = new StringBuilder();
        LiveLocation mine = null;
        for (LiveLocation l : list) {
            if (l.userId.equals(me)) mine = l;
            String when = l.updatedAt == null ? getString(R.string.time_just_now)
                    : DateTimeUtils.relative(requireContext(), l.updatedAt.toDate().getTime());
            if (sb.length() > 0) sb.append('\n');
            sb.append(getString(R.string.tracking_last_update, l.userId.equals(me) ? getString(R.string.you) : l.name, when));
        }
        b.sharers.setText(sb.length() == 0 ? getString(R.string.tracking_no_one_sharing) : sb.toString());
        if (mine != null && ride != null) {
            GeoCoord p = new GeoCoord(mine.lat, mine.lng);
            String d = ride.isParticipant(me) && ride.statusEnum() != com.example.ridealong.domain.model.RideStatus.IN_PROGRESS
                    ? getString(R.string.tracking_distance_to_pickup, Haversine.distanceKm(p, ride.pickup()))
                    : getString(R.string.tracking_distance_to_destination, Haversine.distanceKm(p, ride.drop()));
            b.distances.setVisibility(View.VISIBLE);
            b.distances.setText(d);
        } else {
            b.distances.setVisibility(View.GONE);
        }
    }

    private void fitAll() {
        Resource<Ride> rr = vm.ride().getValue();
        List<GeoCoord> pts = new ArrayList<>();
        if (rr != null && rr.data != null) pts.addAll(rr.data.routePoints());
        Resource<List<LiveLocation>> lr = vm.locations().getValue();
        if (lr != null && lr.data != null) for (LiveLocation l : lr.data) pts.add(new GeoCoord(l.lat, l.lng));
        map.whenReady(m -> m.fitTo(pts, Ui.dp(requireContext(), 56)));
    }

    // ----- sharing flow: consent → foreground location → notifications → service → (optional) background/geofence -----

    private void beginSharingFlow() {
        if (!ServiceLocator.get().settings().hasLocationConsent()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.tracking_consent_title)
                    .setMessage(R.string.tracking_consent_body)
                    .setNegativeButton(R.string.action_not_now, null)
                    .setPositiveButton(R.string.action_start_sharing, (d, w) -> {
                        ServiceLocator.get().settings().setLocationConsent(true);
                        requestForeground();
                    })
                    .show();
        } else {
            requestForeground();
        }
    }

    private void requestForeground() {
        if (PermissionUtils.hasAnyLocation(requireContext())) afterForegroundGranted();
        else fgPermission.launch(PermissionUtils.FOREGROUND_LOCATION);
    }

    private void afterForegroundGranted() {
        if (PermissionUtils.needsNotificationPermission() && !PermissionUtils.hasNotificationPermission(requireContext())) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startService();
        }
    }

    private void startService() {
        try {
            LocationShareService.start(requireContext(), rideId);
        } catch (Exception e) {
            Ui.error(b.getRoot(), e);
            return;
        }
        if (GeofenceManager.canUseGeofencing(requireContext())) registerGeofences();
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && PermissionUtils.hasFineLocation(requireContext())) askBackground();
    }

    private void askBackground() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.permission_background_title)
                .setMessage(R.string.permission_background_body)
                .setNegativeButton(R.string.action_not_now, (d, w) -> renderControls())
                .setPositiveButton(R.string.action_allow, (d, w) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                    }
                })
                .show();
    }

    private void registerGeofences() {
        Resource<Ride> rr = vm.ride().getValue();
        if (rr != null && rr.data != null) GeofenceManager.register(requireContext(), rr.data, Me.uid());
        renderControls();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        markerIds.clear();
        map = null;
        b = null;
    }
}