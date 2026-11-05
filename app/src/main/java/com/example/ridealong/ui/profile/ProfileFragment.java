package com.example.ridealong.ui.profile;

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

import com.example.ridealong.R;
import com.example.ridealong.data.model.Review;
import com.example.ridealong.data.model.User;
import com.example.ridealong.databinding.FragmentProfileBinding;
import com.example.ridealong.databinding.ItemMenuRowBinding;
import com.example.ridealong.databinding.ItemReviewBinding;
import com.example.ridealong.ui.common.Avatars;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.SafetyDialogs;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;

import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding b;
    private ProfileViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentProfileBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(ProfileViewModel.class);
        vm.init(getArguments() == null ? null : getArguments().getString(Constants.ARG_USER_ID));
        b.state.showLoading();

        if (vm.isSelf()) {
            b.ownMenu.setVisibility(View.VISIBLE);
            row(b.rowEdit, R.drawable.ic_edit, R.string.menu_edit_profile, () -> Nav.go(this, R.id.editProfileFragment));
            row(b.rowHistory, R.drawable.ic_history, R.string.menu_history, () -> Nav.go(this, R.id.rideHistoryFragment));
            row(b.rowSos, R.drawable.ic_warning, R.string.menu_emergency, () -> Nav.go(this, R.id.emergencyFragment));
            row(b.rowSettings, R.drawable.ic_settings, R.string.menu_settings, () -> Nav.go(this, R.id.settingsFragment));
        } else {
            requireActivity().addMenuProvider(new MenuProvider() {
                @Override
                public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                    inflater.inflate(R.menu.menu_user_actions, menu);
                }

                @Override
                public boolean onMenuItemSelected(@NonNull MenuItem item) {
                    User u = vm.user().getValue() == null ? null : vm.user().getValue().data;
                    String name = u == null ? getString(R.string.unknown_user) : u.displayName();
                    if (item.getItemId() == R.id.action_block) {
                        SafetyDialogs.block(b.getRoot(), vm.userId(), name);
                        return true;
                    }
                    if (item.getItemId() == R.id.action_report) {
                        SafetyDialogs.report(b.getRoot(), vm.userId(), name, null);
                        return true;
                    }
                    return false;
                }
            }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        }

        vm.user().observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            if (r.isError() && r.data == null) {
                b.state.showError(UiErrors.message(requireContext(), r.error), null);
                return;
            }
            if (r.data == null) {
                if (!r.isLoading()) b.state.showEmpty(R.drawable.ic_person, R.string.unknown_user, R.string.error_generic);
                return;
            }
            b.state.hide();
            render(r.data, r.fromCache);
        });
        vm.reviews().observe(getViewLifecycleOwner(), r -> renderReviews(r == null ? null : r.data));
    }

    private void row(ItemMenuRowBinding row, int icon, int text, Runnable action) {
        row.rowIcon.setImageResource(icon);
        row.rowText.setText(text);
        row.getRoot().setOnClickListener(v -> action.run());
    }

    private void render(User u, boolean cached) {
        Avatars.load(b.avatar, u.photoBase64);
        b.name.setText(u.displayName().isEmpty() ? getString(R.string.unknown_user) : u.displayName());
        boolean org = u.organization != null && !u.organization.isEmpty();
        b.organization.setVisibility(org ? View.VISIBLE : View.GONE);
        b.organization.setText(u.organization);
        b.memberSince.setText(u.createdAt == null ? "" : getString(R.string.profile_member_since,
                DateTimeUtils.formatMonthYear(requireContext(), u.createdAt.toDate().getTime())));
        b.statRating.setText(u.ratingCount > 0 ? String.format(Locale.getDefault(), "%.1f", u.averageRating()) : getString(R.string.rating_none));
        b.statCompleted.setText(String.valueOf(u.completedRides));
        b.statReviews.setText(String.valueOf(u.ratingCount));
        b.cached.setVisibility(cached ? View.VISIBLE : View.GONE);
    }

    private void renderReviews(@Nullable List<Review> list) {
        b.reviews.removeAllViews();
        b.reviewsEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        if (list == null) return;
        for (Review rv : list) {
            ItemReviewBinding row = ItemReviewBinding.inflate(getLayoutInflater(), b.reviews, false);
            row.reviewer.setText(rv.reviewerName);
            row.stars.setRating(rv.rating);
            boolean has = rv.comment != null && !rv.comment.trim().isEmpty();
            row.comment.setVisibility(has ? View.VISIBLE : View.GONE);
            row.comment.setText(rv.comment);
            row.date.setText(rv.createdAt == null ? "" : DateTimeUtils.formatDate(requireContext(), rv.createdAt.toDate().getTime()));
            b.reviews.addView(row.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}