package com.example.ridealong.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.ridealong.R;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.data.model.User;
import com.example.ridealong.databinding.FragmentHomeBinding;
import com.example.ridealong.databinding.ItemPendingRequestBinding;
import com.example.ridealong.ui.common.Avatars;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.RidesAdapter;
import com.example.ridealong.ui.common.UiErrors;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class HomeFragment extends Fragment {
    private static final int MAX_PENDING_ROWS = 3;
    private FragmentHomeBinding b;
    private HomeViewModel vm;
    private RidesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        vm.init();
        adapter = new RidesAdapter(RidesAdapter.Mode.MINE, item -> Nav.go(this, R.id.rideDetailsFragment, Nav.ride(item.ride.id)));
        b.activeList.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.activeList.setAdapter(adapter);
        b.refresh.setColorSchemeResources(R.color.ra_primary);
        b.refresh.setOnRefreshListener(() -> {
            vm.refreshUnread();
            b.refresh.setRefreshing(false);
        });

        b.find.setOnClickListener(v -> selectTab(R.id.discoverFragment));
        b.offer.setOnClickListener(v -> selectTab(R.id.postRideFragment));
        b.avatar.setOnClickListener(v -> selectTab(R.id.profileFragment));
        b.quickSos.setOnClickListener(v -> Nav.go(this, R.id.emergencyFragment));
        b.quickHistory.setOnClickListener(v -> Nav.go(this, R.id.rideHistoryFragment));

        b.greeting.setText(R.string.home_greeting_generic);
        vm.me().observe(getViewLifecycleOwner(), r -> {
            User u = r == null ? null : r.data;
            if (u == null) return;
            b.greeting.setText(u.firstName().isEmpty() ? getString(R.string.home_greeting_generic)
                    : getString(R.string.home_greeting, u.firstName()));
            Avatars.load(b.avatar, u.photoBase64);
        });
        vm.pending().observe(getViewLifecycleOwner(), r -> renderPending(r == null ? null : r.data));
        b.activeState.showLoading();
        vm.active().observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            if (r.isError()) {
                b.activeState.showError(UiErrors.message(requireContext(), r.error), null);
                return;
            }
            if (r.data == null) return;
            adapter.submitList(r.data);
            b.activeCached.setVisibility(r.fromCache ? View.VISIBLE : View.GONE);
            if (r.data.isEmpty()) {
                b.activeState.showEmpty(R.drawable.ic_car, R.string.home_empty_active_title, R.string.home_empty_active_body,
                        R.string.home_find_ride, () -> selectTab(R.id.discoverFragment));
            } else {
                b.activeState.hide();
            }
        });
    }

    private void renderPending(@Nullable List<RideRequest> list) {
        if (list == null || list.isEmpty()) {
            b.pendingCard.setVisibility(View.GONE);
            return;
        }
        b.pendingCard.setVisibility(View.VISIBLE);
        b.pendingCount.setText(getResources().getQuantityString(R.plurals.home_pending_count, list.size(), list.size()));
        b.pendingList.removeAllViews();
        for (int i = 0; i < Math.min(MAX_PENDING_ROWS, list.size()); i++) {
            RideRequest r = list.get(i);
            ItemPendingRequestBinding row = ItemPendingRequestBinding.inflate(getLayoutInflater(), b.pendingList, false);
            row.text.setText(getString(R.string.n_join_request_body, r.requesterName, r.rideDropAddress));
            row.getRoot().setOnClickListener(v -> Nav.go(this, R.id.rideRequestsFragment, Nav.ride(r.rideId)));
            b.pendingList.addView(row.getRoot());
        }
    }

    private void selectTab(int id) {
        BottomNavigationView nav = requireActivity().findViewById(R.id.bottom_nav);
        if (nav != null) nav.setSelectedItemId(id);
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.refreshUnread();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}