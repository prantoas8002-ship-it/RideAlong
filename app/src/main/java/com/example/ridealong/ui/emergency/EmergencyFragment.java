package com.example.ridealong.ui.emergency;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.ridealong.R;
import com.example.ridealong.data.local.EmergencyLogEntity;
import com.example.ridealong.data.model.EmergencyContact;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.databinding.FragmentEmergencyBinding;
import com.example.ridealong.databinding.ItemEmergencyLogBinding;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;
import com.example.ridealong.utils.IntentUtils;
import com.example.ridealong.utils.PermissionUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SOS: confirm → get location → open the dialer for a chosen contact and an SMS with an
 * OpenStreetMap location link. Android does not let normal apps place calls or send SMS
 * silently, so the user presses Call / Send themselves; the app never claims an alert was sent.
 */
public class EmergencyFragment extends Fragment {
    private FragmentEmergencyBinding b;
    private EmergencyViewModel vm;

    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> vm.locate());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentEmergencyBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(EmergencyViewModel.class);
        vm.init(getArguments() == null ? null : getArguments().getString(Constants.ARG_RIDE_ID));
        b.sosButton.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.pulse));
        b.sosButton.setOnClickListener(v -> confirm());
        b.call999.setOnClickListener(v -> {
            String number = com.example.ridealong.domain.config.AppConfig.get().emergencyNumber;
            if (IntentUtils.openDialer(requireContext(), number)) vm.logAction(Constants.SOS_999, number);
            else Ui.snack(b.getRoot(), R.string.sos_no_dialer);
        });
        b.stepDial.setOnClickListener(v -> chooseAndDial());
        b.stepSms.setOnClickListener(v -> openSms());

        vm.phase().observe(getViewLifecycleOwner(), p -> {
            b.progress.setVisibility(p == EmergencyViewModel.Phase.LOCATING ? View.VISIBLE : View.GONE);
            b.status.setVisibility(p == EmergencyViewModel.Phase.LOCATING ? View.VISIBLE : View.GONE);
            b.status.setText(R.string.sos_getting_location);
            b.stepsCard.setVisibility(p == EmergencyViewModel.Phase.READY ? View.VISIBLE : View.GONE);
            if (p == EmergencyViewModel.Phase.READY) chooseAndDial();
        });
        vm.location().observe(getViewLifecycleOwner(), loc -> b.locationText.setText(loc == null
                ? getString(R.string.sos_location_failed) : IntentUtils.osmLink(loc.getLatitude(), loc.getLongitude())));
        vm.log().observe(getViewLifecycleOwner(), this::renderLog);
    }

    private void confirm() {
        List<EmergencyContact> c = vm.contacts().getValue();
        if (c == null || c.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.title_emergency)
                    .setMessage(R.string.sos_no_contacts)
                    .setNegativeButton(R.string.action_close, null)
                    .setPositiveButton(R.string.menu_edit_profile, (d, w) -> Nav.go(this, R.id.editProfileFragment))
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sos_confirm_title)
                .setMessage(R.string.sos_confirm_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (d, w) -> {
                    if (PermissionUtils.hasAnyLocation(requireContext())) vm.locate();
                    else locationPermission.launch(PermissionUtils.FOREGROUND_LOCATION);
                })
                .show();
    }

    private void chooseAndDial() {
        List<EmergencyContact> contacts = vm.contacts().getValue();
        if (contacts == null || contacts.isEmpty()) {
            Ui.snack(b.getRoot(), R.string.sos_no_contacts);
            return;
        }
        if (contacts.size() == 1) {
            dial(contacts.get(0));
            return;
        }
        String[] names = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) names[i] = contacts.get(i).name + " · " + contacts.get(i).phone;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sos_choose_contact)
                .setItems(names, (d, w) -> dial(contacts.get(w)))
                .show();
    }

    private void dial(EmergencyContact c) {
        if (IntentUtils.openDialer(requireContext(), c.phone)) {
            vm.logAction(Constants.SOS_DIAL, c.name);
            b.status.setVisibility(View.VISIBLE);
            b.status.setText(getString(R.string.sos_dialer_opened, c.name));
        } else {
            Ui.snack(b.getRoot(), R.string.sos_no_dialer);
        }
    }

    private void openSms() {
        List<EmergencyContact> contacts = vm.contacts().getValue();
        if (contacts == null || contacts.isEmpty()) return;
        List<String> phones = new ArrayList<>();
        for (EmergencyContact c : contacts) phones.add(c.phone);
        Location loc = vm.location().getValue();
        String name = Me.name();
        String msg = loc == null ? getString(R.string.sos_sms_message_no_location, name)
                : getString(R.string.sos_sms_message, name, IntentUtils.osmLink(loc.getLatitude(), loc.getLongitude()));
        Ride r = vm.ride().getValue();
        if (r != null) msg += "\n" + getString(R.string.sos_ride_context, r.pickupAddress, r.dropAddress);
        if (IntentUtils.openSms(requireContext(), phones, msg)) {
            vm.logAction(Constants.SOS_SMS, String.join(", ", phones));
            b.status.setVisibility(View.VISIBLE);
            b.status.setText(R.string.sos_sms_opened);
        } else {
            Ui.snack(b.getRoot(), R.string.sos_no_sms_app);
        }
    }

    private void renderLog(List<EmergencyLogEntity> list) {
        b.history.removeAllViews();
        b.historyEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        if (list == null) return;
        for (EmergencyLogEntity e : list) {
            ItemEmergencyLogBinding row = ItemEmergencyLogBinding.inflate(getLayoutInflater(), b.history, false);
            int label = Constants.SOS_SMS.equals(e.action) ? R.string.sos_action_sms
                    : Constants.SOS_999.equals(e.action) ? R.string.sos_action_999 : R.string.sos_action_dial;
            row.action.setText(e.contactName == null ? getString(label) : getString(R.string.sos_log_action, getString(label), e.contactName));
            String where = e.lat == null ? "" : String.format(Locale.US, "  ·  %.5f, %.5f", e.lat, e.lng);
            row.meta.setText(getString(R.string.sos_log_meta, DateTimeUtils.formatDate(requireContext(), e.createdMillis),
                    DateTimeUtils.formatTime(requireContext(), e.createdMillis), where));
            b.history.addView(row.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}