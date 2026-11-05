package com.example.ridealong.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.ridealong.R;
import com.example.ridealong.databinding.FragmentRegisterBinding;
import com.example.ridealong.domain.validator.ProfileValidator;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.Ui;

public class RegisterFragment extends Fragment {
    private FragmentRegisterBinding b;
    private AuthViewModel vm;
    private GoogleSignInHelper google;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        google = new GoogleSignInHelper(this, vm);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentRegisterBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        b.back.setOnClickListener(v -> Nav.back(this));
        b.goLogin.setOnClickListener(v -> Nav.back(this));
        b.signUp.setOnClickListener(v -> submit());
        b.google.setOnClickListener(v -> google.launch());
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            b.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE);
            b.signUp.setEnabled(!Boolean.TRUE.equals(busy));
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
        vm.signedIn().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() != null) ((AuthActivity) requireActivity()).onSignedIn(NavHostFragment.findNavController(this));
        });
    }

    private void submit() {
        Ui.hideKeyboard(b.getRoot());
        String name = String.valueOf(b.name.getText()).trim();
        String email = String.valueOf(b.email.getText()).trim();
        String pw = String.valueOf(b.password.getText());
        String confirm = String.valueOf(b.confirm.getText());
        boolean ok = true;
        b.nameLayout.setError(ProfileValidator.isValidName(name) ? null : getString(R.string.error_invalid_name));
        ok &= ProfileValidator.isValidName(name);
        b.emailLayout.setError(ProfileValidator.isValidEmail(email) ? null : getString(R.string.error_invalid_email));
        ok &= ProfileValidator.isValidEmail(email);
        b.passwordLayout.setError(ProfileValidator.isValidPassword(pw) ? null : getString(R.string.error_invalid_password));
        ok &= ProfileValidator.isValidPassword(pw);
        b.confirmLayout.setError(pw.equals(confirm) ? null : getString(R.string.error_password_mismatch));
        ok &= pw.equals(confirm);
        if (ok) vm.register(name, email, pw);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}