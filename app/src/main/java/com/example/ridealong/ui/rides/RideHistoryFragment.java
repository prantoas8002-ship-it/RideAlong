package com.example.ridealong.ui.rides;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.local.CacheMapper;
import com.example.ridealong.data.local.CachedRideEntity;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.databinding.FragmentListBinding;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.RideItem;
import com.example.ridealong.ui.common.RidesAdapter;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Posted + joined rides from Firestore, cached in Room. Offline it shows the Room copy
 * (read-only) with a "cached" notice.
 */
public class RideHistoryFragment extends Fragment {
    private FragmentListBinding b;
    private RidesAdapter adapter;
    private List<Ride> all = new ArrayList<>();
    private boolean cached;
    private int tab;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        String me = Me.uid();
        adapter = new RidesAdapter(RidesAdapter.Mode.MINE, item -> Nav.go(this, R.id.rideDetailsFragment, Nav.ride(item.ride.id)));
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.tabs.setVisibility(View.VISIBLE);
        b.tabs.addTab(b.tabs.newTab().setText(R.string.history_all));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.history_posted));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.history_joined));
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                tab = t.getPosition();
                render();
            }

            @Override public void onTabUnselected(TabLayout.Tab t) { }

            @Override public void onTabReselected(TabLayout.Tab t) { }
        });
        b.state.showLoading();

        LiveData<Resource<List<Ride>>> live = ServiceLocator.get().rides().observeMyRides(me);
        LiveData<List<CachedRideEntity>> room = ServiceLocator.get().rides().observeCachedRides(me);
        live.observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            if (r.data != null && (!r.fromCache || !r.data.isEmpty())) {
                all = r.data;
                cached = r.fromCache;
                render();
            } else if (r.isError() || r.fromCache) {
                List<CachedRideEntity> c = room.getValue();
                if (c != null) useRoom(c);
            }
        });
        room.observe(getViewLifecycleOwner(), c -> {
            Resource<List<Ride>> l = live.getValue();
            if ((l == null || l.data == null || l.data.isEmpty()) && c != null && !c.isEmpty()) useRoom(c);
        });
    }

    private void useRoom(List<CachedRideEntity> c) {
        List<Ride> rides = new ArrayList<>();
        for (CachedRideEntity e : c) rides.add(CacheMapper.toRide(e));
        all = rides;
        cached = true;
        render();
    }

    private void render() {
        if (b == null) return;
        String me = Me.uid();
        List<RideItem> items = new ArrayList<>();
        for (Ride r : all) {
            boolean posted = r.isOwner(me);
            if (tab == 1 && !posted) continue;
            if (tab == 2 && posted) continue;
            items.add(new RideItem(r, null, null, null, 0, cached));
        }
        adapter.submitList(items);
        b.headerNote.setVisibility(cached ? View.VISIBLE : View.GONE);
        b.headerNote.setText(R.string.cached_data_notice);
        if (items.isEmpty()) b.state.showEmpty(R.drawable.ic_history, R.string.history_empty_title, R.string.history_empty_body);
        else b.state.hide();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}