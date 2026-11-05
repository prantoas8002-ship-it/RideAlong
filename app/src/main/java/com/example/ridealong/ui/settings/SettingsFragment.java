package com.example.ridealong.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.ridealong.BuildConfig;
import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Block;
import com.example.ridealong.data.repository.SettingsRepository;
import com.example.ridealong.databinding.DialogTextInputBinding;
import com.example.ridealong.databinding.FragmentSettingsBinding;
import com.example.ridealong.databinding.ItemBlockedUserBinding;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.service.RealtimeSyncService;
import com.example.ridealong.ui.auth.AuthActivity;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.utils.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding b;
    private SettingsViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentSettingsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(SettingsViewModel.class);
        SettingsRepository settings = ServiceLocator.get().settings();

        boolean googleAvailable = SettingsRepository.isGoogleMapsAvailable();
        b.mapGoogle.setEnabled(googleAvailable);
        b.googleNote.setVisibility(googleAvailable ? View.GONE : View.VISIBLE);
        b.mapProvider.check(Constants.MAP_PROVIDER_GOOGLE.equals(settings.getMapProvider()) ? R.id.map_google : R.id.map_osm);
        b.mapProvider.setOnCheckedChangeListener((g, id) ->
                settings.setMapProvider(id == R.id.map_google ? Constants.MAP_PROVIDER_GOOGLE : Constants.MAP_PROVIDER_OSM));

        AppConfig cfg = AppConfig.get();
        b.radius.setValueFrom((float) cfg.matching.minSearchRadiusKm);
        b.radius.setValueTo((float) cfg.matching.maxSearchRadiusKm);
        b.radius.setValue((float) Math.round(settings.getSearchRadiusKm()));
        b.radiusValue.setText(getString(R.string.settings_radius_value, settings.getSearchRadiusKm()));
        b.radius.addOnChangeListener((sl, value, fromUser) -> {
            settings.setSearchRadiusKm(value);
            b.radiusValue.setText(getString(R.string.settings_radius_value, (double) value));
        });

        b.notifyRequests.setChecked(settings.notifyRequests());
        b.notifyRequests.setOnCheckedChangeListener((v, c) -> settings.setNotifyRequests(c));
        b.notifyMessages.setChecked(settings.notifyMessages());
        b.notifyMessages.setOnCheckedChangeListener((v, c) -> settings.setNotifyMessages(c));
        b.backgroundSync.setChecked(settings.backgroundSync());
        b.backgroundSync.setOnCheckedChangeListener((v, c) -> {
            settings.setBackgroundSync(c);
            if (c) RealtimeSyncService.start(requireContext());
            else RealtimeSyncService.stop(requireContext());
        });

        b.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        b.logout.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.confirm_logout)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.settings_logout, (d, w) -> vm.logout())
                .show());
        b.deleteAccount.setOnClickListener(v -> Ui.confirm(requireContext(), R.string.settings_delete_confirm_title,
                R.string.settings_delete_confirm_body, R.string.action_delete, vm::deleteAccount));

        vm.blocks().observe(getViewLifecycleOwner(), r -> renderBlocks(r == null ? null : r.data));
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            b.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE);
            b.deleteAccount.setEnabled(!Boolean.TRUE.equals(busy));
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
        vm.account().observe(getViewLifecycleOwner(), e -> {
            SettingsViewModel.AccountEvent ev = e.consume();
            if (ev == null) return;
            if (ev == SettingsViewModel.AccountEvent.NEEDS_REAUTH) {
                askReauth();
                return;
            }
            if (ev == SettingsViewModel.AccountEvent.DELETED) Ui.snack(b.getRoot(), R.string.settings_account_deleted);
            startActivity(new Intent(requireContext(), AuthActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            requireActivity().finish();
        });
    }

    /** Firebase requires a recent sign-in to delete an account. */
    private void askReauth() {
        if (!ServiceLocator.get().auth().isPasswordUser()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.settings_delete_reauth)
                    .setPositiveButton(R.string.settings_logout, (d, w) -> vm.logout())
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }
        DialogTextInputBinding db = DialogTextInputBinding.inflate(getLayoutInflater());
        db.inputLayout.setHint(getString(R.string.hint_reauth_password));
        db.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_delete_confirm_title)
                .setMessage(R.string.settings_delete_reauth)
                .setView(db.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> vm.reauthAndDelete(String.valueOf(db.input.getText())))
                .show();
    }

    private void renderBlocks(@Nullable List<Block> list) {
        b.blockedList.removeAllViews();
        b.blockedEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        if (list == null) return;
        for (Block bl : list) {
            ItemBlockedUserBinding row = ItemBlockedUserBinding.inflate(getLayoutInflater(), b.blockedList, false);
            row.name.setText(bl.blockedName == null || bl.blockedName.isEmpty() ? getString(R.string.unknown_user) : bl.blockedName);
            row.unblock.setOnClickListener(v -> vm.unblock(bl.blockedId));
            b.blockedList.addView(row.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}