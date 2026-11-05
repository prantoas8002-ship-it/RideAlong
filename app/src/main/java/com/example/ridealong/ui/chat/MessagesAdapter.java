package com.example.ridealong.ui.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ridealong.R;
import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.data.repository.ChatRepository;
import com.example.ridealong.databinding.ItemMessageInBinding;
import com.example.ridealong.databinding.ItemMessageOutBinding;
import com.example.ridealong.databinding.ItemMessageSystemBinding;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;

import java.util.Objects;

final class MessagesAdapter extends ListAdapter<ChatMessage, RecyclerView.ViewHolder> {
    private static final int OUT = 1, IN = 2, SYSTEM = 3;
    private final String myUid;

    MessagesAdapter(String myUid) {
        super(new DiffUtil.ItemCallback<ChatMessage>() {
            @Override
            public boolean areItemsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) { return Objects.equals(a.id, b.id); }

            @Override
            public boolean areContentsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
                return Objects.equals(a.text, b.text) && a.createdMillis() == b.createdMillis();
            }
        });
        this.myUid = myUid;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage m = getItem(position);
        if (Constants.MSG_CALL.equals(m.type) || Constants.MSG_SYSTEM.equals(m.type)) return SYSTEM;
        return Objects.equals(m.senderId, myUid) ? OUT : IN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (type == OUT) return new Out(ItemMessageOutBinding.inflate(inf, parent, false));
        if (type == IN) return new In(ItemMessageInBinding.inflate(inf, parent, false));
        return new Sys(ItemMessageSystemBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage m = getItem(position);
        Context c = holder.itemView.getContext();
        String time = DateTimeUtils.chatTime(c, m.createdMillis());
        if (holder instanceof Out) {
            ((Out) holder).b.text.setText(m.text);
            ((Out) holder).b.time.setText(time);
        } else if (holder instanceof In) {
            In h = (In) holder;
            h.b.text.setText(m.text);
            h.b.time.setText(time);
            h.b.sender.setText(m.senderName);
            // Hide repeated sender names in a run of messages from the same person.
            boolean sameAsPrev = position > 0 && Objects.equals(getItem(position - 1).senderId, m.senderId)
                    && getItemViewType(position - 1) == IN;
            h.b.sender.setVisibility(sameAsPrev ? android.view.View.GONE : android.view.View.VISIBLE);
        } else {
            Sys h = (Sys) holder;
            String text;
            if (Constants.MSG_SYSTEM.equals(m.type) && m.text != null && m.text.startsWith(ChatRepository.SYSTEM_JOINED + ":")) {
                text = c.getString(R.string.chat_system_joined, m.text.substring(ChatRepository.SYSTEM_JOINED.length() + 1));
            } else {
                text = m.text;
            }
            h.b.text.setText(text + "  ·  " + time);
            h.b.text.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    Constants.MSG_CALL.equals(m.type) ? R.drawable.ic_call : R.drawable.ic_info, 0, 0, 0);
        }
    }

    static final class Out extends RecyclerView.ViewHolder {
        final ItemMessageOutBinding b;

        Out(ItemMessageOutBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class In extends RecyclerView.ViewHolder {
        final ItemMessageInBinding b;

        In(ItemMessageInBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class Sys extends RecyclerView.ViewHolder {
        final ItemMessageSystemBinding b;

        Sys(ItemMessageSystemBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}