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
import com.example.ridealong.databinding.FragmentLoginBinding;
import com.example.ridealong.domain.validator.ProfileValidator;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.Ui;

public class LoginFragment extends Fragment {
    private FragmentLoginBinding b;
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
        b = FragmentLoginBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        b.signIn.setOnClickListener(v -> submit());
        b.password.setOnEditorActionListener((tv, id, ev) -> {
            submit();
            return true;
        });
        b.google.setOnClickListener(v -> google.launch());
        b.forgot.setOnClickListener(v -> Nav.go(this, R.id.forgotPasswordFragment));
        b.goRegister.setOnClickListener(v -> Nav.go(this, R.id.registerFragment));

        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            b.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE);
            b.signIn.setEnabled(!Boolean.TRUE.equals(busy));
            b.google.setEnabled(!Boolean.TRUE.equals(busy));
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
        String email = String.valueOf(b.email.getText()).trim();
        String pw = String.valueOf(b.password.getText());
        boolean ok = true;
        if (!ProfileValidator.isValidEmail(email)) {
            b.emailLayout.setError(getString(R.string.error_invalid_email));
            ok = false;
        } else b.emailLayout.setError(null);
        if (pw.isEmpty()) {
            b.passwordLayout.setError(getString(R.string.error_invalid_password));
            ok = false;
        } else b.passwordLayout.setError(null);
        if (ok) vm.signIn(email, pw);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}