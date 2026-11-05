package com.example.ridealong.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Loading / success / error wrapper exposed to the UI. {@code fromCache} labels cached vs live data. */
public final class Resource<T> {
    public enum Status { LOADING, SUCCESS, ERROR }

    @NonNull public final Status status;
    @Nullable public final T data;
    @Nullable public final Throwable error;
    public final boolean fromCache;

    private Resource(@NonNull Status status, @Nullable T data, @Nullable Throwable error, boolean fromCache) {
        this.status = status;
        this.data = data;
        this.error = error;
        this.fromCache = fromCache;
    }

    public static <T> Resource<T> loading(@Nullable T data) { return new Resource<>(Status.LOADING, data, null, false); }

    public static <T> Resource<T> success(@Nullable T data) { return new Resource<>(Status.SUCCESS, data, null, false); }

    public static <T> Resource<T> success(@Nullable T data, boolean fromCache) {
        return new Resource<>(Status.SUCCESS, data, null, fromCache);
    }

    public static <T> Resource<T> error(@NonNull Throwable e, @Nullable T data) {
        return new Resource<>(Status.ERROR, data, e, false);
    }

    public boolean isLoading() { return status == Status.LOADING; }

    public boolean isSuccess() { return status == Status.SUCCESS; }

    public boolean isError() { return status == Status.ERROR; }
}
