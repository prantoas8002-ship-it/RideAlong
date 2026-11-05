package com.example.ridealong.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.data.model.DirectChat;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Event;

import java.util.List;

public class DirectChatViewModel extends ViewModel {
    private String chatId;
    private LiveData<Resource<DirectChat>> chat;
    private final MediatorLiveData<Resource<List<ChatMessage>>> messages = new MediatorLiveData<>();
    private final MutableLiveData<Event<Throwable>> error = new MutableLiveData<>();
    private boolean serverDelivered;

    public void init(String chatId) {
        if (this.chatId != null) return;
        this.chatId = chatId;
        Me.preload();
        chat = ServiceLocator.get().directChats().observeChat(chatId);
        messages.addSource(ServiceLocator.get().directChats().observeMessages(chatId), r -> {
            if (r == null) return;
            if (r.isSuccess() && !r.fromCache) serverDelivered = true;
            if (!serverDelivered && (r.isError() || (r.isSuccess() && r.fromCache && (r.data == null || r.data.isEmpty())))) {
                AppExecutors.io().execute(() -> {
                    List<ChatMessage> cached = ServiceLocator.get().directChats().cachedMessages(chatId);
                    AppExecutors.main(() -> {
                        if (serverDelivered) return;
                        messages.setValue(cached.isEmpty() ? r : Resource.success(cached, true));
                    });
                });
            } else {
                messages.setValue(r);
                if (r.isSuccess() && !r.fromCache) markRead();
            }
        });
    }

    public LiveData<Resource<DirectChat>> chat() { return chat; }

    public LiveData<Resource<List<ChatMessage>>> messages() { return messages; }

    public LiveData<Event<Throwable>> error() { return error; }

    public DirectChat current() {
        Resource<DirectChat> r = chat == null ? null : chat.getValue();
        return r == null ? null : r.data;
    }

    public void send(String text) {
        DirectChat c = current();
        if (c == null || text.trim().isEmpty()) return;
        ServiceLocator.get().directChats().send(c, text, Me.name()).addOnFailureListener(e -> error.setValue(new Event<>(e)));
    }

    public void logCall(String text) {
        DirectChat c = current();
        if (c != null) ServiceLocator.get().directChats().logCall(c, text, Me.name());
    }

    public void markRead() {
        String me = Me.uid();
        DirectChat c = current();
        if (me != null && c != null && c.participants.contains(me)) ServiceLocator.get().directChats().markRead(chatId, me);
    }
}