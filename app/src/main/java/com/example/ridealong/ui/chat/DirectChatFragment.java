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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.ridealong.R;
import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.data.model.DirectChat;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.databinding.FragmentChatBinding;
import com.example.ridealong.service.RealtimeNotificationListener;
import com.example.ridealong.ui.common.CallHelper;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Private real-time chat between a ride's poster and one requester (works before acceptance).
 * The Call action opens the dialer with the other person's number and logs the call here.
 */
public class DirectChatFragment extends Fragment {
    private FragmentChatBinding b;
    private DirectChatViewModel vm;
    private MessagesAdapter adapter;
    private String chatId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentChatBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        chatId = requireArguments().getString(Constants.ARG_CHAT_ID);
        vm = new ViewModelProvider(this).get(DirectChatViewModel.class);
        vm.init(chatId);
        adapter = new MessagesAdapter(Me.uid());
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        b.messages.setLayoutManager(lm);
        b.messages.setAdapter(adapter);
        b.state.showLoading();

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) { inflater.inflate(R.menu.menu_direct_chat, menu); }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                DirectChat c = vm.current();
                if (c == null) return false;
                String me = Me.uid();
                if (item.getItemId() == R.id.action_voice_call || item.getItemId() == R.id.action_video_call) {
                    // In-app internet call: no phone number is shared or needed.
                    com.example.ridealong.ui.call.CallActivity.startOutgoing(requireContext(), c.otherId(me), c.otherName(me),
                            c.rideId, c.id, item.getItemId() == R.id.action_video_call);
                    return true;
                }
                if (item.getItemId() == R.id.action_call) {
                    CallHelper.dial(b.getRoot(), c.rideId, c.otherId(me), c.otherName(me), vm::logCall);
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
        b.banner.setOnClickListener(v -> {
            DirectChat c = vm.current();
            if (c != null) Nav.go(this, R.id.rideDetailsFragment, Nav.ride(c.rideId));
        });

        vm.chat().observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            if (r.isSuccess() && r.data == null && !r.fromCache) {
                b.state.showEmpty(R.drawable.ic_lock, R.string.chats_title, R.string.chat_not_member);
                b.inputBar.setVisibility(View.GONE);
                return;
            }
            DirectChat c = r.data;
            if (c == null) return;
            String me = Me.uid();
            String other = c.otherName(me) == null || c.otherName(me).isEmpty() ? getString(R.string.unknown_user) : c.otherName(me);
            if (requireActivity() instanceof AppCompatActivity && ((AppCompatActivity) requireActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(other);
            }
            b.banner.setText(getString(R.string.direct_chat_note, other, c.rideDropAddress));
            boolean member = c.participants.contains(me);
            b.inputBar.setVisibility(member ? View.VISIBLE : View.GONE);
            renderMessages(vm.messages().getValue());
        });
        vm.messages().observe(getViewLifecycleOwner(), this::renderMessages);
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
    }

    private void renderMessages(@Nullable Resource<List<ChatMessage>> r) {
        if (r == null || b == null) return;
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
        if (list.isEmpty()) {
            DirectChat c = vm.current();
            String other = c == null ? getString(R.string.unknown_user) : c.otherName(Me.uid());
            b.state.showEmpty(R.drawable.ic_chat, R.string.chat_empty_title, R.string.chats_empty_title);
            android.widget.TextView msg = b.state.findViewById(R.id.empty_message);
            if (msg != null) msg.setText(getString(R.string.direct_chat_empty_body, other));
        } else {
            b.state.hide();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        RealtimeNotificationListener.visibleDirectChatId = chatId;
        vm.markRead();
    }

    @Override
    public void onPause() {
        RealtimeNotificationListener.visibleDirectChatId = null;
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}