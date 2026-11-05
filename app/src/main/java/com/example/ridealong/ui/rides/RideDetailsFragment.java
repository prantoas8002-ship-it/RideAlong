package com.example.ridealong.ui.rides;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.databinding.DialogRequestBinding;
import com.example.ridealong.databinding.DialogTextInputBinding;
import com.example.ridealong.databinding.FragmentRideDetailsBinding;
import com.example.ridealong.databinding.ItemParticipantBinding;
import com.example.ridealong.domain.algorithm.RouteMatcher;
import com.example.ridealong.domain.calculator.FareCalculator;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.domain.model.RequestStatus;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.ui.common.Avatars;
import com.example.ridealong.ui.common.CallHelper;
import com.example.ridealong.ui.common.MapHost;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.RideUi;
import com.example.ridealong.ui.common.SafetyDialogs;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class RideDetailsFragment extends Fragment {
    private FragmentRideDetailsBinding b;
    private RideDetailsViewModel vm;
    private MapHost map;
    private String rideId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentRideDetailsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        rideId = requireArguments().getString(Constants.ARG_RIDE_ID);
        vm = new ViewModelProvider(this).get(RideDetailsViewModel.class);
        vm.init(rideId);
        map = new MapHost(getViewLifecycleOwner(), b.mapContainer);
        b.state.showLoading();

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                Ride r = vm.current();
                if (r != null && !r.isOwner(Me.uid())) inflater.inflate(R.menu.menu_user_actions, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                Ride r = vm.current();
                if (r == null) return false;
                if (item.getItemId() == R.id.action_block) {
                    SafetyDialogs.block(b.getRoot(), r.ownerId, r.ownerName);
                    return true;
                }
                if (item.getItemId() == R.id.action_report) {
                    SafetyDialogs.report(b.getRoot(), r.ownerId, r.ownerName, r.id);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        b.ownerCard.setOnClickListener(v -> {
            Ride r = vm.current();
            if (r != null) Nav.go(this, R.id.userProfileFragment, Nav.user(r.ownerId));
        });
        b.chat.setOnClickListener(v -> Nav.go(this, R.id.chatFragment, Nav.ride(rideId)));
        b.track.setOnClickListener(v -> Nav.go(this, R.id.liveTrackingFragment, Nav.ride(rideId)));
        b.sos.setOnClickListener(v -> Nav.go(this, R.id.emergencyFragment, Nav.ride(rideId)));
        b.messageOwner.setOnClickListener(v -> {
            Ride r = vm.current();
            if (r == null) return;
            ServiceLocator.get().directChats()
                    .ensureChat(r.id, r.ownerId, r.ownerName, Me.uid(), Me.name(), r.dropAddress)
                    .addOnCompleteListener(t -> {
                        if (b == null) return;
                        if (t.isSuccessful()) Nav.go(this, R.id.directChatFragment, Nav.chat(t.getResult()));
                        else Ui.error(b.getRoot(), t.getException());
                    });
        });

        vm.ride().observe(getViewLifecycleOwner(), this::renderRide);
        vm.myRequest().observe(getViewLifecycleOwner(), r -> renderActions());
        vm.pendingCount().observe(getViewLifecycleOwner(), n -> renderActions());
        vm.owner().observe(getViewLifecycleOwner(), this::renderOwner);
        vm.members().observe(getViewLifecycleOwner(), this::renderMembers);
        vm.match().observe(getViewLifecycleOwner(), this::renderMatch);
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            b.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE);
            boolean enabled = !Boolean.TRUE.equals(busy);
            b.primaryAction.setEnabled(enabled);
            b.secondaryAction.setEnabled(enabled);
            b.dangerAction.setEnabled(enabled);
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
        vm.message().observe(getViewLifecycleOwner(), e -> {
            Integer m = e.consume();
            if (m != null) Ui.snack(b.getRoot(), m);
        });
    }

    private void renderRide(Resource<RideDetailsViewModel.RideState> res) {
        if (res == null) return;
        if (res.isLoading()) {
            b.state.showLoading();
            return;
        }
        if (res.isError()) {
            b.state.showError(UiErrors.message(requireContext(), res.error), null);
            return;
        }
        if (res.data == null) {
            b.state.showEmpty(R.drawable.ic_car, R.string.error_ride_closed, R.string.ride_not_joinable);
            return;
        }
        b.state.hide();
        Ride r = res.data.ride;
        b.cached.setVisibility(res.data.cached ? View.VISIBLE : View.GONE);
        RideUi.styleStatus(b.status, r.statusEnum());
        b.cost.setText(RideUi.costAmount(requireContext(), r.costPerPerson));
        b.costUnit.setVisibility(r.costPerPerson > 0 ? View.VISIBLE : View.GONE);
        b.departure.setText(DateTimeUtils.formatDeparture(requireContext(), r.departureMillis()));
        b.pickup.setText(r.pickupAddress);
        b.drop.setText(r.dropAddress);
        b.chipSeats.setText(getString(R.string.seats_left, r.seatsAvailable, r.totalSeats));
        b.chipTransport.setText(RideUi.transportLabel(requireContext(), r.transportEnum()));
        b.chipDetour.setText(getString(R.string.max_detour_value, r.maxDetourKm));
        b.chipDistance.setText(r.routeApproximate ? getString(R.string.route_summary_approx, r.routeDistanceKm)
                : getString(R.string.route_summary, r.routeDistanceKm, r.routeDurationMin));
        boolean hasComment = r.comment != null && !r.comment.trim().isEmpty();
        b.commentLabel.setVisibility(hasComment ? View.VISIBLE : View.GONE);
        b.comment.setVisibility(hasComment ? View.VISIBLE : View.GONE);
        b.comment.setText(r.comment);
        b.ownerName.setText(r.ownerName);
        requireActivity().invalidateOptionsMenu();

        List<GeoCoord> pts = r.routePoints();
        map.whenReady(m -> {
            m.setMarker("pickup", r.pickup(), r.pickupAddress, R.drawable.marker_pickup);
            m.setMarker("drop", r.drop(), r.dropAddress, R.drawable.marker_drop);
            m.setRoute("route", pts, requireContext().getColor(R.color.ra_primary), 5f);
            if (SearchSession.origin != null && !r.isOwner(Me.uid())) {
                m.setMarker("me", SearchSession.origin.coord(), getString(R.string.you), R.drawable.marker_me);
            }
            m.fitTo(pts, Ui.dp(requireContext(), 40));
        });
        renderActions();
    }

    private void renderOwner(User u) {
        if (u == null) return;
        Avatars.load(b.ownerAvatar, u.photoBase64);
        b.ownerName.setText(u.displayName());
        boolean org = u.organization != null && !u.organization.isEmpty();
        b.ownerOrg.setVisibility(org ? View.VISIBLE : View.GONE);
        b.ownerOrg.setText(u.organization);
        RideUi.bindRating(b.ownerRating.ratingValue, b.ownerRating.ratingRides, u);
    }

    private void renderMatch(RouteMatcher.Result m) {
        Ride r = vm.current();
        if (m == null || r == null || !m.compatible) {
            b.matchCard.setVisibility(View.GONE);
            return;
        }
        b.matchCard.setVisibility(View.VISIBLE);
        double shared = Double.isNaN(m.sharedKm) ? 0 : m.sharedKm;
        double detour = Double.isNaN(m.detourKm) ? 0 : m.detourKm;
        b.matchInfo.setText(getString(R.string.details_match_info, m.score, shared, detour));
        if (!Double.isNaN(m.fareDistanceKm) && m.fareDistanceKm > 0 && r.routeDistanceKm > 0) {
            FareCalculator fc = ServiceLocator.get().fareCalculator();
            double share = fc.estimatePassengerShare(r.transportEnum(), r.routeDistanceKm, m.fareDistanceKm, r.totalSeats + 1);
            b.matchShare.setVisibility(View.VISIBLE);
            b.matchShare.setText(getString(R.string.details_estimated_share, AppConfig.get().fare.currencySymbol, RideUi.money(share)));
        } else {
            b.matchShare.setVisibility(View.GONE);
        }
    }

    private void renderMembers(List<User> users) {
        Ride r = vm.current();
        b.participants.removeAllViews();
        boolean member = r != null && r.isMember(Me.uid());
        b.participantsLabel.setVisibility(member ? View.VISIBLE : View.GONE);
        b.participants.setVisibility(member ? View.VISIBLE : View.GONE);
        if (!member || users == null) return;
        String me = Me.uid();
        for (User u : users) {
            ItemParticipantBinding row = ItemParticipantBinding.inflate(getLayoutInflater(), b.participants, false);
            String uid = u.uidOrId();
            boolean isMe = uid.equals(me);
            row.name.setText(isMe ? getString(R.string.you) : u.displayName().isEmpty() ? getString(R.string.unknown_user) : u.displayName());
            Avatars.load(row.avatar, u.photoBase64);
            RideUi.bindRating(row.rating.ratingValue, row.rating.ratingRides, u);
            if (r.isOwner(uid)) {
                row.badge.setVisibility(View.VISIBLE);
                row.badge.setText(R.string.owner_badge);
            }
            row.call.setVisibility(isMe || r.statusEnum().isTerminal() ? View.GONE : View.VISIBLE);
            row.call.setOnClickListener(v -> CallHelper.call(b.getRoot(), r, uid, u.displayName()));
            row.getRoot().setOnClickListener(v -> {
                if (!isMe) Nav.go(this, R.id.userProfileFragment, Nav.user(uid));
            });
            b.participants.addView(row.getRoot());
        }
    }

    /** Decides which actions the viewer sees for the current ride state and their role. */
    private void renderActions() {
        Ride r = vm.current();
        if (r == null || b == null) return;
        String me = Me.uid();
        RideStatus st = r.statusEnum();
        boolean owner = r.isOwner(me);
        boolean participant = r.isParticipant(me);
        hideActions();

        if (owner || participant) {
            b.memberActions.setVisibility(View.VISIBLE);
            b.track.setVisibility(st.isTerminal() ? View.GONE : View.VISIBLE);
            b.sos.setVisibility(st.isTerminal() ? View.GONE : View.VISIBLE);
            renderMembers(vm.members().getValue());
        }

        if (owner) {
            Integer pending = vm.pendingCount().getValue();
            int n = pending == null ? 0 : pending;
            switch (st) {
                case OPEN:
                case FULL:
                    primary(n > 0 ? getResources().getQuantityString(R.plurals.action_view_requests_count, n, n)
                            : getString(R.string.action_view_requests), () -> Nav.go(this, R.id.rideRequestsFragment, Nav.ride(r.id)));
                    secondary(R.string.action_start_ride, () -> Ui.confirm(requireContext(), R.string.confirm_start_title,
                            R.string.confirm_start_body, R.string.action_start_ride, vm::start));
                    danger(R.string.action_cancel_ride, this::confirmCancelRide);
                    break;
                case IN_PROGRESS:
                    primary(getString(R.string.action_complete_ride), () -> Ui.confirm(requireContext(), R.string.confirm_complete_title,
                            R.string.confirm_complete_body, R.string.action_complete_ride, vm::complete));
                    danger(R.string.action_cancel_ride, this::confirmCancelRide);
                    break;
                case COMPLETED:
                    if (r.memberIds.size() > 1) primary(getString(R.string.action_rate_riders), () -> Nav.go(this, R.id.ratingFragment, Nav.ride(r.id)));
                    note(R.string.ride_status_completed);
                    break;
                case CANCELLED:
                    note(R.string.ride_status_cancelled);
                    break;
            }
            return;
        }

        if (participant) {
            b.messageOwner.setVisibility(View.VISIBLE); // private chat with the poster
            switch (st) {
                case OPEN:
                case FULL:
                    note(R.string.request_accepted_note);
                    danger(R.string.action_leave_ride, () -> Ui.confirm(requireContext(), R.string.confirm_leave_title,
                            R.string.confirm_leave_body, R.string.action_leave_ride, vm::leave));
                    break;
                case IN_PROGRESS:
                    note(R.string.ride_status_in_progress);
                    break;
                case COMPLETED:
                    primary(getString(R.string.action_rate_riders), () -> Nav.go(this, R.id.ratingFragment, Nav.ride(r.id)));
                    break;
                case CANCELLED:
                    note(R.string.n_cancelled_title);
                    break;
            }
            return;
        }

        // Not a member: request flow.
        Resource<RideRequest> req = vm.myRequest().getValue();
        RequestStatus rs = req == null || req.data == null ? null : req.data.statusEnum();
        b.messageOwner.setVisibility(rs == RequestStatus.PENDING ? View.VISIBLE : View.GONE);
        if (rs == RequestStatus.PENDING) {
            note(R.string.request_pending_note);
            secondary(R.string.action_cancel_request, vm::cancelRequest);
        } else if (rs == RequestStatus.REJECTED) {
            note(R.string.request_rejected_note);
        } else if (rs == RequestStatus.LEFT) {
            note(R.string.request_left_note);
        } else if (st == RideStatus.OPEN && r.seatsAvailable > 0) {
            primary(getString(R.string.action_request_join), () -> showRequestDialog(r));
        } else if (st == RideStatus.FULL) {
            note(R.string.ride_full_note);
        } else {
            note(R.string.ride_not_joinable);
        }
    }

    private void hideActions() {
        b.primaryAction.setVisibility(View.GONE);
        b.secondaryAction.setVisibility(View.GONE);
        b.dangerAction.setVisibility(View.GONE);
        b.memberActions.setVisibility(View.GONE);
        b.note.setVisibility(View.GONE);
        b.messageOwner.setVisibility(View.GONE);
    }

    private void primary(String text, Runnable action) {
        b.primaryAction.setVisibility(View.VISIBLE);
        b.primaryAction.setText(text);
        b.primaryAction.setOnClickListener(v -> action.run());
    }

    private void secondary(int text, Runnable action) {
        b.secondaryAction.setVisibility(View.VISIBLE);
        b.secondaryAction.setText(text);
        b.secondaryAction.setOnClickListener(v -> action.run());
    }

    private void danger(int text, Runnable action) {
        b.dangerAction.setVisibility(View.VISIBLE);
        b.dangerAction.setText(text);
        b.dangerAction.setOnClickListener(v -> action.run());
    }

    private void note(int text) {
        b.note.setVisibility(View.VISIBLE);
        b.note.setText(text);
    }

    private void showRequestDialog(Ride r) {
        DialogRequestBinding db = DialogRequestBinding.inflate(getLayoutInflater());
        int max = Math.max(1, r.seatsAvailable);
        if (max > 1) {
            db.seats.setValueTo(max);
            db.seats.setValue(Math.max(1, Math.min(max, SearchSession.seatsNeeded)));
        } else {
            db.seats.setVisibility(View.GONE);
            db.seatsLabel.setVisibility(View.GONE);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.request_dialog_title)
                .setView(db.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_request_join, (d, w) -> {
                    int seats = max > 1 ? Math.round(db.seats.getValue()) : 1;
                    vm.requestJoin(seats, String.valueOf(db.message.getText()).trim());
                })
                .show();
    }

    private void confirmCancelRide() {
        DialogTextInputBinding db = DialogTextInputBinding.inflate(getLayoutInflater());
        db.inputLayout.setHint(getString(R.string.hint_cancel_reason));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_cancel_ride_title)
                .setMessage(R.string.confirm_cancel_ride_body)
                .setView(db.getRoot())
                .setNegativeButton(R.string.action_no, null)
                .setPositiveButton(R.string.action_cancel_ride, (d, w) -> vm.cancelRide(String.valueOf(db.input.getText()).trim()))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        map = null;
        b = null;
    }
}
