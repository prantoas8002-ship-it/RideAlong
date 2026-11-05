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
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.databinding.FragmentChatBinding;
import com.example.ridealong.service.RealtimeNotificationListener;
import com.example.ridealong.ui.common.CallHelper;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Ride chat: members only (rules), real-time, full history (Room cache offline), call logging. */
public class ChatFragment extends Fragment {
    private FragmentChatBinding b;
    private ChatViewModel vm;
    private MessagesAdapter adapter;
    private String rideId;

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
            com.example.ridealong.ui.call.CallActivity.startOutgoing(requireContext(), u.uidOrId(), u.displayName(), ride.id, "");
        } else {
            CallHelper.call(b.getRoot(), ride, u.uidOrId(), u.displayName());
        }
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
        super.onDestroyView();
        b = null;
    }
}