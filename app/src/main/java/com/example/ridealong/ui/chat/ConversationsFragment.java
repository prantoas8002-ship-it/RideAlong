package com.example.ridealong.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.DirectChat;
import com.example.ridealong.data.model.User;
import com.example.ridealong.databinding.FragmentListBinding;
import com.example.ridealong.databinding.ItemConversationBinding;
import com.example.ridealong.ui.common.Avatars;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

/** Inbox of private chats (poster <-> requester), newest activity first, opened from Home. */
public class ConversationsFragment extends Fragment {
    private FragmentListBinding b;
    private final List<DirectChat> items = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.state.showLoading();
        ServiceLocator.get().directChats().observeMyChats(Me.uid()).observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            if (r.isLoading() && r.data == null) {
                b.state.showLoading();
                return;
            }
            if (r.isError() && r.data == null) {
                b.state.showError(UiErrors.message(requireContext(), r.error), null);
                return;
            }
            items.clear();
            if (r.data != null) {
                for (DirectChat d : r.data) {
                    if (!ServiceLocator.get().safety().hiddenUserIds().contains(d.otherId(Me.uid()))) items.add(d);
                }
            }
            adapter.notifyDataSetChanged();
            if (items.isEmpty()) b.state.showEmpty(R.drawable.ic_chat, R.string.chats_empty_title, R.string.chats_empty_body);
            else b.state.hide();
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final ItemConversationBinding v;

            VH(ItemConversationBinding v) {
                super(v.getRoot());
                this.v = v;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemConversationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DirectChat d = items.get(position);
            String me = Me.uid();
            String other = d.otherId(me);
            String name = d.otherName(me);
            h.v.name.setText(name == null || name.isEmpty() ? getString(R.string.unknown_user) : name);
            h.v.ride.setText(getString(R.string.conversation_ride, d.rideDropAddress));
            boolean empty = d.lastMessage == null || d.lastMessage.isEmpty();
            h.v.lastMessage.setText(empty ? getString(R.string.conversation_no_messages)
                    : me != null && me.equals(d.lastSenderId) ? getString(R.string.chat_system_line, getString(R.string.you), d.lastMessage)
                    : d.lastMessage);
            h.v.time.setText(d.lastMessageAt == null ? "" : DateTimeUtils.relative(requireContext(), d.lastMessageAt.toDate().getTime()));
            boolean unread = d.isUnreadFor(me);
            h.v.unreadDot.setVisibility(unread ? View.VISIBLE : View.GONE);
            h.v.lastMessage.setTextColor(requireContext().getColor(unread ? R.color.ra_text : R.color.ra_text_secondary));
            User u = ServiceLocator.get().users().peekCached(other);
            Avatars.load(h.v.avatar, u == null ? null : u.photoBase64);
            if (u == null) {
                ServiceLocator.get().users().getUser(other).addOnSuccessListener(user -> {
                    int pos = h.getBindingAdapterPosition();
                    if (user != null && pos != RecyclerView.NO_POSITION && items.get(pos).id.equals(d.id)) {
                        Avatars.load(h.v.avatar, user.photoBase64);
                    }
                });
            }
            h.v.root.setOnClickListener(v -> Nav.go(ConversationsFragment.this, R.id.directChatFragment, Nav.chat(d.id)));
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}