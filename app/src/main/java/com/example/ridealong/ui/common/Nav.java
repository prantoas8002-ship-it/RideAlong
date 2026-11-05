package com.example.ridealong.ui.common;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.ridealong.R;
import com.example.ridealong.utils.Constants;

/** Navigation shortcuts with consistent transition animations. */
public final class Nav {
    private Nav() { }

    public static NavOptions anim() {
        return new NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.slide_out_left)
                .setPopEnterAnim(R.anim.slide_in_left)
                .setPopExitAnim(R.anim.slide_out_right)
                .build();
    }

    public static void go(Fragment f, @IdRes int dest, Bundle args) {
        NavController nc = NavHostFragment.findNavController(f);
        try {
            nc.navigate(dest, args, anim());
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // double tap / destination not reachable from current graph state
        }
    }

    public static void go(Fragment f, @IdRes int dest) { go(f, dest, null); }

    public static Bundle ride(String rideId) {
        Bundle b = new Bundle();
        b.putString(Constants.ARG_RIDE_ID, rideId);
        return b;
    }

    public static Bundle user(String userId) {
        Bundle b = new Bundle();
        b.putString(Constants.ARG_USER_ID, userId);
        return b;
    }

    public static void back(Fragment f) { NavHostFragment.findNavController(f).navigateUp(); }
}