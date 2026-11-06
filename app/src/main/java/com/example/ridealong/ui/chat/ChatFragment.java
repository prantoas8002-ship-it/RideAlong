package com.example.ridealong.ui.chat;

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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.ridealong.R;
import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.data.model.GroupCall;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.GroupCallRepository;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.databinding.FragmentChatBinding;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.service.RealtimeNotificationListener;
import com.example.ridealong.service.call.GroupCallManager;
import com.example.ridealong.ui.call.GroupCallActivity;
import com.example.ridealong.ui.common.CallHelper;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Ride chat: members only (rules), real-time, full history (Room cache offline), call logging. */
public class ChatFragment extends Fragment {
    private FragmentChatBinding b;
    private ChatViewModel vm;
    private MessagesAdapter adapter;
    private String rideId;
    private ListenerRegistration groupCallsReg;
    @Nullable private GroupCall activeGroupCall;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentChatBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        rideId = requireArguments().getString(Constants.ARG_RIDE_ID);
        vm = new ViewModelProvider(this).get(ChatViewModel.class);
        vm.init(rideId);
        adapter = new MessagesAdapter(Me.uid());
        adapter.setOnMessageLongPress((m, mine) -> MessageActions.show(b.getRoot(), m, mine, new MessageActions.Handler() {
            @Override
            public void edit(ChatMessage msg, String newText) {
                com.example.ridealong.ServiceLocator.get().chat().editMessage(rideId, msg.id, newText)
                        .addOnFailureListener(e -> { if (b != null) Ui.error(b.getRoot(), e); });
            }

            @Override
            public void delete(ChatMessage msg) {
                com.example.ridealong.ServiceLocator.get().chat().deleteMessage(rideId, msg.id)
                        .addOnFailureListener(e -> { if (b != null) Ui.error(b.getRoot(), e); });
            }
        }));
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        b.messages.setLayoutManager(lm);
        b.messages.setAdapter(adapter);
        b.state.showLoading();

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) { inflater.inflate(R.menu.menu_chat, menu); }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_voice_call) {
                    chooseCallee(true);
                    return true;
                }
                if (item.getItemId() == R.id.action_call) {
                    chooseCallee(false);
                    return true;
                }
                if (item.getItemId() == R.id.action_group_call) {
                    groupCall();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        b.send.setOnClickListener(v -> {
            String text = b.input.getText().toString();
            if (text.trim().isEmpty()) return;
            vm.send(text);
            b.input.setText("");
        });

        vm.ride().observe(getViewLifecycleOwner(), r -> {
            Ride ride = r == null ? null : r.data;
            if (ride == null) return;
            vm.onRide(ride);
            b.banner.setText(getString(R.string.chat_banner, ride.pickupAddress, ride.dropAddress,
                    DateTimeUtils.formatDeparture(requireContext(), ride.departureMillis())));
            boolean member = ride.isMember(Me.uid());
            boolean closed = ride.statusEnum().isTerminal();
            b.inputBar.setVisibility(member && !closed ? View.VISIBLE : View.GONE);
            b.closedNote.setVisibility(member && closed ? View.VISIBLE : View.GONE);
            if (!member) b.state.showEmpty(R.drawable.ic_lock, R.string.title_chat, R.string.chat_not_member);
        });
        b.groupCallJoin.setOnClickListener(v -> {
            if (activeGroupCall != null) GroupCallActivity.join(requireContext(), activeGroupCall.id);
        });
        watchGroupCalls();
        vm.messages().observe(getViewLifecycleOwner(), this::render);
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
    }

    private void render(Resource<List<ChatMessage>> r) {
        if (r == null) return;
        Resource<Ride> ride = vm.ride().getValue();
        if (ride != null && ride.data != null && !ride.data.isMember(Me.uid())) return;
        if (r.isLoading() && r.data == null) {
            b.state.showLoading();
            return;
        }
        if (r.isError() && (r.data == null || r.data.isEmpty())) {
            b.state.showError(UiErrors.message(requireContext(), r.error), null);
            return;
        }
        List<ChatMessage> list = r.data == null ? new ArrayList<>() : r.data;
        adapter.submitList(list, () -> {
            if (b != null && !list.isEmpty()) b.messages.scrollToPosition(list.size() - 1);
        });
        if (list.isEmpty()) b.state.showEmpty(R.drawable.ic_chat, R.string.chat_empty_title, R.string.chat_empty_body);
        else b.state.hide();
        if (r.fromCache) b.banner.setText(getString(R.string.cached_data_notice));
    }

    private void chooseCallee(boolean voice) {
        Resource<Ride> rr = vm.ride().getValue();
        if (rr == null || rr.data == null) return;
        Ride ride = rr.data;
        List<User> others = new ArrayList<>();
        List<User> all = vm.members().getValue();
        if (all != null) for (User u : all) if (!u.uidOrId().equals(Me.uid())) others.add(u);
        if (others.isEmpty()) {
            Ui.snack(b.getRoot(), R.string.no_phone_available);
            return;
        }
        if (others.size() == 1) {
            callUser(voice, ride, others.get(0));
            return;
        }
        String[] names = new String[others.size()];
        for (int i = 0; i < others.size(); i++) names[i] = others.get(i).displayName();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.call_who)
                .setItems(names, (d, w) -> callUser(voice, ride, others.get(w)))
                .show();
    }

    private void callUser(boolean voice, Ride ride, User u) {
        if (voice) {
            com.example.ridealong.ui.call.CallActivity.startOutgoing(requireContext(), u.uidOrId(), u.displayName(), ride.id, "", false);
        } else {
            CallHelper.call(b.getRoot(), ride, u.uidOrId(), u.displayName());
        }
    }

    // ----- group video call -----

    /** Shows the "join" bar while a group call with someone actually in it is running for this ride. */
    private void watchGroupCalls() {
        String me = Me.uid();
        if (me == null) return;
        GroupCallRepository repo = new GroupCallRepository();
        groupCallsReg = repo.activeForMe(me).addSnapshotListener((snap, err) -> {
            if (b == null || err != null || snap == null) return;
            GroupCall newest = null;
            for (DocumentSnapshot d : snap.getDocuments()) {
                GroupCall g = d.toObject(GroupCall.class);
                if (g == null || !rideId.equals(g.rideId) || g.createdAt == null) continue;
                if (newest == null || g.createdAt.compareTo(newest.createdAt) > 0) newest = g;
            }
            GroupCall candidate = newest;
            if (candidate == null) {
                showGroupCall(null);
                return;
            }
            // Ignore calls everyone abandoned without hanging up (app killed): nobody fresh in members.
            repo.members(candidate.id).get().addOnSuccessListener(ms -> {
                long now = System.currentTimeMillis();
                boolean someone = false;
                for (DocumentSnapshot m : ms.getDocuments()) {
                    com.google.firebase.Timestamp seen = m.getTimestamp("seenAt");
                    if (seen != null && now - seen.toDate().getTime() < AppConfig.get().groupCallMemberStaleMs) someone = true;
                }
                showGroupCall(someone ? candidate : null);
            });
        });
    }

    private void showGroupCall(@Nullable GroupCall g) {
        if (b == null) return;
        activeGroupCall = g;
        Resource<Ride> rr = vm.ride().getValue();
        boolean member = rr != null && rr.data != null && rr.data.isMember(Me.uid());
        GroupCallManager.State cur = GroupCallManager.get(requireContext()).state().getValue();
        boolean alreadyIn = g != null && cur != null && g.id.equals(cur.callId) && GroupCallManager.get(requireContext()).isInCall();
        b.groupCallBar.setVisibility(g != null && member && !alreadyIn ? View.VISIBLE : View.GONE);
    }

    /** Join the running call for this ride, or start one with every ride member invited. */
    private void groupCall() {
        if (activeGroupCall != null) {
            GroupCallActivity.join(requireContext(), activeGroupCall.id);
            return;
        }
        Resource<Ride> rr = vm.ride().getValue();
        if (rr == null || rr.data == null) return;
        Ride ride = rr.data;
        if (!ride.isMember(Me.uid()) || ride.statusEnum().isTerminal()) return;
        ArrayList<String> invited = new ArrayList<>();
        invited.add(Me.uid());
        java.util.Set<String> hidden = com.example.ridealong.ServiceLocator.get().safety().hiddenUserIds();
        for (String m : ride.memberIds) {
            if (!m.equals(Me.uid()) && !hidden.contains(m) && invited.size() < 8) invited.add(m);
        }
        if (invited.size() < 2) {
            Ui.snack(b.getRoot(), R.string.group_call_no_members);
            return;
        }
        GroupCallActivity.start(requireContext(), ride.id, invited);
    }

    @Override
    public void onResume() {
        super.onResume();
        RealtimeNotificationListener.visibleChatRideId = rideId;
        vm.markRead();
    }

    @Override
    public void onPause() {
        RealtimeNotificationListener.visibleChatRideId = null;
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (groupCallsReg != null) groupCallsReg.remove();
        groupCallsReg = null;
        super.onDestroyView();
        b = null;
    }
}