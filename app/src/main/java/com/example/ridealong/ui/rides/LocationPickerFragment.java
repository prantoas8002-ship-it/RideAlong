package com.example.ridealong.ui.rides;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.ridealong.R;
import com.example.ridealong.databinding.FragmentLocationPickerBinding;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.PlaceResult;
import com.example.ridealong.ui.common.MapHost;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.LocationHelper;
import com.example.ridealong.utils.PermissionUtils;

import java.util.Map;

/** Pick a location by moving the map under a centre pin or by searching (Nominatim / Google). */
public class LocationPickerFragment extends Fragment {
    private FragmentLocationPickerBinding b;
    private LocationPickerViewModel vm;
    private MapHost map;
    private PlaceSuggestionAdapter adapter;
    private String kind;
    private boolean suppressNextIdle;
    private final Runnable debounced = this::runSearch;

    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> r) -> {
                if (PermissionUtils.hasAnyLocation(requireContext())) centerOnMe();
                else Ui.snack(b.getRoot(), R.string.permission_denied_location);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentLocationPickerBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(LocationPickerViewModel.class);
        Bundle args = requireArguments();
        kind = args.getString(Constants.ARG_PICK_KIND, Constants.PICK_KIND_PICKUP);
        boolean pickup = Constants.PICK_KIND_PICKUP.equals(kind);
        requireActivity().setTitle(pickup ? R.string.title_pick_pickup : R.string.title_pick_destination);
        b.kindLabel.setText(pickup ? R.string.hint_pickup : R.string.hint_destination);
        b.centerPin.setImageResource(R.drawable.ic_center_pin);

        adapter = new PlaceSuggestionAdapter(p -> {
            Ui.hideKeyboard(b.getRoot());
            vm.clearSuggestions();
            vm.select(p);
            suppressNextIdle = true;
            map.whenReady(m -> m.setCenter(p.coord(), AppConfig.get().pickedLocationZoom));
        });
        b.suggestions.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.suggestions.setAdapter(adapter);

        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b1, int c1) { }
            @Override public void onTextChanged(CharSequence c, int a, int b1, int c1) { }
            @Override
            public void afterTextChanged(Editable e) {
                AppExecutors.cancelMain(debounced);
                AppExecutors.mainDelayed(debounced, AppConfig.get().searchDebounceMs); // Nominatim policy
            }
        });
        b.search.setOnEditorActionListener((v, id, ev) -> {
            AppExecutors.cancelMain(debounced);
            runSearch();
            return true;
        });

        map = new MapHost(getViewLifecycleOwner(), b.mapContainer);
        float startLat = args.getFloat(Constants.ARG_START_LAT, 0f), startLng = args.getFloat(Constants.ARG_START_LNG, 0f);
        map.whenReady(m -> {
            AppConfig cfg = AppConfig.get();
            if (startLat != 0f || startLng != 0f) {
                m.setCenter(new GeoCoord(startLat, startLng), cfg.pickedLocationZoom);
            } else {
                m.setCenter(new GeoCoord(cfg.defaultMapCenterLat, cfg.defaultMapCenterLng), cfg.defaultMapZoom);
                if (PermissionUtils.hasAnyLocation(requireContext())) centerOnMe();
            }
            m.setOnCameraIdleListener(() -> {
                if (suppressNextIdle) {
                    suppressNextIdle = false;
                    return;
                }
                GeoCoord c = m.getCenter();
                if (c != null) vm.resolve(c, getString(R.string.unnamed_location, c.lat, c.lng));
            });
        });

        b.myLocation.setOnClickListener(v -> {
            if (PermissionUtils.hasAnyLocation(requireContext())) centerOnMe();
            else locationPermission.launch(PermissionUtils.FOREGROUND_LOCATION);
        });
        b.confirm.setOnClickListener(v -> confirm());

        vm.suggestions().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            b.suggestions.setVisibility(list == null || list.isEmpty() ? View.GONE : View.VISIBLE);
        });
        vm.searchState().observe(getViewLifecycleOwner(), st -> {
            b.searchProgress.setVisibility(st == LocationPickerViewModel.SearchState.LOADING ? View.VISIBLE : View.GONE);
            if (st == LocationPickerViewModel.SearchState.EMPTY) {
                b.searchMessage.setVisibility(View.VISIBLE);
                b.searchMessage.setText(R.string.picker_no_results);
            } else if (st == LocationPickerViewModel.SearchState.FAILED) {
                b.searchMessage.setVisibility(View.VISIBLE);
                b.searchMessage.setText(R.string.picker_search_failed);
            } else {
                b.searchMessage.setVisibility(View.GONE);
            }
        });
        vm.selected().observe(getViewLifecycleOwner(), p -> {
            if (p != null) b.address.setText(p.address != null && !p.address.isEmpty() ? p.address : p.label());
            b.confirm.setEnabled(p != null);
        });
        vm.resolving().observe(getViewLifecycleOwner(), r -> {
            if (Boolean.TRUE.equals(r)) b.address.setText(R.string.picker_resolving);
        });
    }

    private void runSearch() {
        if (b == null) return;
        GeoCoord near = map.get() == null ? null : map.get().getCenter();
        vm.search(String.valueOf(b.search.getText()), near);
    }

    private void centerOnMe() {
        LocationHelper.current(requireContext(), loc -> {
            if (loc == null || b == null) return;
            map.whenReady(m -> m.setCenter(new GeoCoord(loc.getLatitude(), loc.getLongitude()), AppConfig.get().pickedLocationZoom));
        });
    }

    private void confirm() {
        PlaceResult p = vm.selected().getValue();
        if (p == null && map.get() != null && map.get().getCenter() != null) {
            GeoCoord c = map.get().getCenter();
            String label = getString(R.string.unnamed_location, c.lat, c.lng);
            p = new PlaceResult(label, label, c.lat, c.lng);
        }
        if (p == null) return;
        NavBackStackEntry prev = NavHostFragment.findNavController(this).getPreviousBackStackEntry();
        if (prev != null) {
            prev.getSavedStateHandle().set(Constants.PICK_KIND_PICKUP.equals(kind)
                    ? Constants.RESULT_PICKED_PICKUP : Constants.RESULT_PICKED_DROP, p);
        }
        Nav.back(this);
    }

    @Override
    public void onDestroyView() {
        AppExecutors.cancelMain(debounced);
        super.onDestroyView();
        b = null;
    }
}