package com.example.ridealong.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.ridealong.R;
import com.example.ridealong.databinding.FragmentForgotPasswordBinding;
import com.example.ridealong.domain.validator.ProfileValidator;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.Ui;

/** Sends Firebase's password-reset LINK by email (the UI says so explicitly). */
public class ForgotPasswordFragment extends Fragment {
    private FragmentForgotPasswordBinding b;
    private AuthViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentForgotPasswordBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        b.back.setOnClickListener(v -> Nav.back(this));
        b.send.setOnClickListener(v -> {
            Ui.hideKeyboard(b.getRoot());
            String email = String.valueOf(b.email.getText()).trim();
            if (!ProfileValidator.isValidEmail(email)) {
                b.emailLayout.setError(getString(R.string.error_invalid_email));
                return;
            }
            b.emailLayout.setError(null);
            vm.sendReset(email);
        });
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            b.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE);
            b.send.setEnabled(!Boolean.TRUE.equals(busy));
        });
        vm.resetSent().observe(getViewLifecycleOwner(), e -> {
            String email = e.consume();
            if (email == null) return;
            b.sentCard.setVisibility(View.VISIBLE);
            b.sentText.setText(getString(R.string.reset_link_sent, email));
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}