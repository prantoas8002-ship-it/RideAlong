package com.example.ridealong.ui.requests;

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
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.databinding.FragmentListBinding;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.Constants;

/** Poster's view of join requests: accept (transaction) / decline. */
public class RideRequestsFragment extends Fragment {
    private FragmentListBinding b;
    private RideRequestsViewModel vm;
    private RequestsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(RideRequestsViewModel.class);
        vm.init(requireArguments().getString(Constants.ARG_RIDE_ID));
        adapter = new RequestsAdapter(new RequestsAdapter.Listener() {
            @Override
            public void onAccept(RideRequest r) { vm.accept(r); }

            @Override
            public void onReject(RideRequest r) { vm.reject(r); }

            @Override
            public void onProfile(RideRequest r) { Nav.go(RideRequestsFragment.this, R.id.userProfileFragment, Nav.user(r.requesterId)); }

            @Override
            public void onMessage(RideRequest r) {
                // Opens (creating if needed) the private chat with this requester, accepted or not.
                com.example.ridealong.ServiceLocator.get().directChats()
                        .ensureChat(r.rideId, r.rideOwnerId, com.example.ridealong.ui.common.Me.name(), r.requesterId,
                                r.requesterName, r.rideDropAddress)
                        .addOnCompleteListener(t -> {
                            if (b == null) return;
                            if (t.isSuccessful()) Nav.go(RideRequestsFragment.this, R.id.directChatFragment, Nav.chat(t.getResult()));
                            else Ui.error(b.getRoot(), t.getException());
                        });
            }
        });
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.state.showLoading();

        vm.ride().observe(getViewLifecycleOwner(), r -> {
            Ride ride = r == null ? null : r.data;
            if (ride == null) return;
            adapter.setRideState(ride.seatsAvailable, ride.statusEnum() == RideStatus.OPEN);
            b.headerNote.setVisibility(View.VISIBLE);
            b.headerNote.setText(getString(R.string.seats_left, ride.seatsAvailable, ride.totalSeats));
        });
        vm.requests().observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            if (r.isLoading() && r.data == null) {
                b.state.showLoading();
                return;
            }
            if (r.isError()) {
                b.state.showError(UiErrors.message(requireContext(), r.error), null);
                return;
            }
            adapter.submit(r.data);
            if (r.data == null || r.data.isEmpty()) {
                b.state.showEmpty(R.drawable.ic_inbox, R.string.requests_empty_title, R.string.requests_empty_body);
            } else {
                b.state.hide();
            }
        });
        vm.working().observe(getViewLifecycleOwner(), adapter::setWorking);
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
        vm.message().observe(getViewLifecycleOwner(), e -> {
            Integer m = e.consume();
            if (m != null) Ui.snack(b.getRoot(), m);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}