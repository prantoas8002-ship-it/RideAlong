package com.example.ridealong.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared thread pools: IO for network/disk, main for UI callbacks. */
public final class AppExecutors {
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() { }

    public static ExecutorService io() { return IO; }

    public static void main(Runnable r) { MAIN.post(r); }

    public static void mainDelayed(Runnable r, long delayMs) { MAIN.postDelayed(r, delayMs); }

    public static void cancelMain(Runnable r) { MAIN.removeCallbacks(r); }

    public static Handler mainHandler() { return MAIN; }
}
