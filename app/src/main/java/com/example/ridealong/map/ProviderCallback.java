package com.example.ridealong.map;

/** Result callback for map providers; always invoked on the main thread. */
public interface ProviderCallback<T> {
    void onSuccess(T result);

    void onError(Exception e);
}