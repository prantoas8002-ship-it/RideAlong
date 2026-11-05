package com.example.ridealong.utils;

import androidx.annotation.Nullable;

/** One-shot LiveData payload (toasts, navigation) that is not re-delivered on rotation. */
public class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) { this.content = content; }

    @Nullable
    public T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() { return content; }
}
