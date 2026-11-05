package com.example.ridealong.ui.rides;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.ridealong.R;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.databinding.FragmentDiscoverBinding;
import com.example.ridealong.databinding.SheetFiltersBinding;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.PlaceResult;
import com.example.ridealong.ui.common.MapHost;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.RideItem;
import com.example.ridealong.ui.common.RidesAdapter;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.LocationHelper;
import com.example.ridealong.utils.PermissionUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class DiscoverFragment extends Fragment {
    private FragmentDiscoverBinding b;
    private DiscoverViewModel vm;
    private RidesAdapter adapter;
    private MapHost map;
    private final Set<String> mapMarkers = new HashSet<>();

    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> useDeviceLocation());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentDiscoverBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(DiscoverViewModel.class);
        adapter = new RidesAdapter(RidesAdapter.Mode.DISCOVER, item -> Nav.go(this, R.id.rideDetailsFragment, Nav.ride(item.ride.id)));
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.refresh.setColorSchemeResources(R.color.ra_primary);
        b.refresh.setOnRefreshListener(() -> {
            if (vm.hasOrigin()) vm.load();
            else b.refresh.setRefreshing(false);
        });

        b.origin.setOnClickListener(v -> openPicker(Constants.PICK_KIND_PICKUP, vm.origin().getValue()));
        b.destination.setOnClickListener(v -> openPicker(Constants.PICK_KIND_DROP, vm.destination().getValue()));
        b.clearDestination.setOnClickListener(v -> vm.setDestination(null));
        b.filters.setOnClickListener(v -> showFilters());
        b.viewToggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            boolean mapMode = id == R.id.toggle_map;
            b.mapContainer.setVisibility(mapMode ? View.VISIBLE : View.GONE);
            b.refresh.setVisibility(mapMode ? View.GONE : View.VISIBLE);
            if (mapMode) renderMap();
        });

        observePicker();
        vm.origin().observe(getViewLifecycleOwner(), p -> renderHeader());
        vm.destination().observe(getViewLifecycleOwner(), p -> {
            b.destination.setText(p == null ? "" : p.label());
            b.clearDestination.setVisibility(p == null ? View.GONE : View.VISIBLE);
        });
        vm.filters().observe(getViewLifecycleOwner(), f -> {
            int n = f.activeCount();
            b.filters.setText(n == 0 ? getString(R.string.action_filters) : getResources().getQuantityString(R.plurals.filter_active_count, n, n));
        });
        vm.results().observe(getViewLifecycleOwner(), this::render);

        if (!vm.hasOrigin()) {
            if (PermissionUtils.hasAnyLocation(requireContext())) useDeviceLocation();
            else showLocationNeeded();
        }
    }

    private void observePicker() {
        NavBackStackEntry entry = NavHostFragment.findNavController(this).getCurrentBackStackEntry();
        if (entry == null) return;
        SavedStateHandle h = entry.getSavedStateHandle();
        h.<PlaceResult>getLiveData(Constants.RESULT_PICKED_PICKUP).observe(getViewLifecycleOwner(), p -> {
            if (p == null) return;
            vm.setOrigin(p, false);
            h.remove(Constants.RESULT_PICKED_PICKUP);
        });
        h.<PlaceResult>getLiveData(Constants.RESULT_PICKED_DROP).observe(getViewLifecycleOwner(), p -> {
            if (p == null) return;
            vm.setDestination(p);
            h.remove(Constants.RESULT_PICKED_DROP);
        });
    }

    private void renderHeader() {
        PlaceResult o = vm.origin().getValue();
        boolean device = Boolean.TRUE.equals(vm.originIsDevice().getValue());
        String label = o == null ? getString(R.string.hint_my_pickup) : device ? getString(R.string.discover_your_location) : o.label();
        b.origin.setText(label);
        b.subtitle.setText(o == null ? "" : getString(R.string.discover_subtitle, vm.radiusKm(), label));
    }

    private void openPicker(String kind, @Nullable PlaceResult current) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_PICK_KIND, kind);
        PlaceResult anchor = current != null ? current : vm.origin().getValue();
        if (anchor != null) {
            args.putFloat(Constants.ARG_START_LAT, (float) anchor.lat);
            args.putFloat(Constants.ARG_START_LNG, (float) anchor.lng);
        }
        Nav.go(this, R.id.locationPickerFragment, args);
    }

    private void useDeviceLocation() {
        if (!PermissionUtils.hasAnyLocation(requireContext())) {
            showLocationNeeded();
            return;
        }
        b.state.showLoading();
        LocationHelper.current(requireContext(), loc -> {
            if (b == null) return;
            if (loc == null) {
                showLocationNeeded();
                return;
            }
            String label = getString(R.string.discover_your_location);
            vm.setOrigin(new PlaceResult(label, label, loc.getLatitude(), loc.getLongitude()), true);
        });
    }

    private void showLocationNeeded() {
        b.refresh.setRefreshing(false);
        if (PermissionUtils.hasAnyLocation(requireContext())) {
            b.state.showEmpty(R.drawable.ic_my_location, R.string.discover_location_needed_title,
                    R.string.discover_location_needed_body, R.string.action_choose_pickup,
                    () -> openPicker(Constants.PICK_KIND_PICKUP, null));
        } else {
            b.state.showEmpty(R.drawable.ic_my_location, R.string.discover_location_needed_title,
                    R.string.discover_location_needed_body, R.string.action_grant_location,
                    () -> locationPermission.launch(PermissionUtils.FOREGROUND_LOCATION));
        }
    }

    private void render(Resource<List<RideItem>> r) {
        if (r == null) return;
        b.refresh.setRefreshing(r.isLoading() && r.data != null);
        if (r.isLoading() && (r.data == null || r.data.isEmpty())) {
            b.state.showLoading();
            return;
        }
        if (r.isError()) {
            b.state.showError(UiErrors.message(requireContext(), r.error), vm::load);
            return;
        }
        List<RideItem> items = r.data == null ? new ArrayList<>() : r.data;
        adapter.submitList(items);
        if (items.isEmpty()) {
            b.state.showEmpty(R.drawable.ic_car, R.string.discover_empty_title, R.string.discover_empty_body,
                    R.string.home_offer_ride, () -> Nav.go(this, R.id.postRideFragment));
        } else {
            b.state.hide();
        }
        if (b.mapContainer.getVisibility() == View.VISIBLE) renderMap();
    }

    private void renderMap() {
        if (map == null) map = new MapHost(getViewLifecycleOwner(), b.mapContainer);
        map.whenReady(m -> {
            for (String id : mapMarkers) m.removeMarker(id);
            mapMarkers.clear();
            List<GeoCoord> pts = new ArrayList<>();
            PlaceResult o = vm.origin().getValue();
            if (o != null) {
                m.setMarker("me", o.coord(), o.label(), R.drawable.marker_me);
                mapMarkers.add("me");
                pts.add(o.coord());
            }
            Resource<List<RideItem>> res = vm.results().getValue();
            if (res != null && res.data != null) {
                for (RideItem it : res.data) {
                    String id = "ride_" + it.ride.id;
                    String title = it.ride.ownerName + " → " + it.ride.dropAddress;
                    m.setMarker(id, it.ride.pickup(), title, R.drawable.marker_pickup);
                    mapMarkers.add(id);
                    pts.add(it.ride.pickup());
                }
            }
            if (!pts.isEmpty()) m.fitTo(pts, Ui.dp(requireContext(), 48));
        });
    }

    private void showFilters() {
        RideFilters current = vm.filters().getValue() == null ? new RideFilters() : vm.filters().getValue().copy();
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        SheetFiltersBinding fb = SheetFiltersBinding.inflate(getLayoutInflater());
        dialog.setContentView(fb.getRoot());
        fb.filterDestination.setText(current.destinationContains);
        fb.seatsSlider.setValue(Math.max(1, Math.min(8, current.minSeats)));
        if (current.maxCost != null) fb.filterMaxCost.setText(String.format(java.util.Locale.US, "%.0f", current.maxCost));
        int chip = current.window == RideFilters.TimeWindow.ONE_HOUR ? R.id.time_1h
                : current.window == RideFilters.TimeWindow.THREE_HOURS ? R.id.time_3h
                : current.window == RideFilters.TimeWindow.TODAY ? R.id.time_today : R.id.time_any;
        fb.timeGroup.check(chip);
        fb.reset.setOnClickListener(v -> {
            vm.setFilters(new RideFilters());
            dialog.dismiss();
        });
        fb.apply.setOnClickListener(v -> {
            RideFilters f = new RideFilters();
            f.destinationContains = String.valueOf(fb.filterDestination.getText()).trim();
            f.minSeats = Math.round(fb.seatsSlider.getValue());
            String mc = String.valueOf(fb.filterMaxCost.getText()).trim();
            try {
                f.maxCost = mc.isEmpty() ? null : Double.parseDouble(mc.replace(',', '.'));
            } catch (NumberFormatException e) {
                f.maxCost = null;
            }
            int id = fb.timeGroup.getCheckedChipId();
            f.window = id == R.id.time_1h ? RideFilters.TimeWindow.ONE_HOUR : id == R.id.time_3h ? RideFilters.TimeWindow.THREE_HOURS
                    : id == R.id.time_today ? RideFilters.TimeWindow.TODAY : RideFilters.TimeWindow.ANY;
            vm.setFilters(f);
            dialog.dismiss();
        });
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b != null) renderHeader();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        map = null;
        mapMarkers.clear();
        b = null;
    }
}