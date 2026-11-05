package com.example.ridealong.ui.profile;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.EmergencyContact;
import com.example.ridealong.data.repository.UserRepository;
import com.example.ridealong.databinding.FragmentEditProfileBinding;
import com.example.ridealong.databinding.ItemEmergencyContactEditBinding;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.validator.ProfileValidator;
import com.example.ridealong.ui.auth.AuthActivity;
import com.example.ridealong.ui.common.Avatars;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.utils.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Used for first-time profile setup (auth graph, setupMode=true) and later editing (main graph). */
public class EditProfileFragment extends Fragment {
    private FragmentEditProfileBinding b;
    private EditProfileViewModel vm;
    private boolean setupMode;
    private Uri cameraUri;
    private final List<ItemEmergencyContactEditBinding> contactRows = new ArrayList<>();
    private String[] genderLabels;
    private static final String[] GENDER_KEYS = {"male", "female", "other", "prefer_not"};

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) vm.processPhoto(uri);
            });
    private final ActivityResultLauncher<Uri> takePhoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && cameraUri != null) vm.processPhoto(cameraUri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentEditProfileBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        setupMode = getArguments() != null && getArguments().getBoolean(Constants.ARG_SETUP_MODE, false);
        vm = new ViewModelProvider(this).get(EditProfileViewModel.class);
        b.title.setText(setupMode ? R.string.profile_setup_title : R.string.edit_profile_title);
        b.title.setVisibility(setupMode ? View.VISIBLE : View.GONE);
        b.subtitle.setVisibility(setupMode ? View.VISIBLE : View.GONE);
        if (setupMode) {
            // Profile setup is mandatory: back leaves the app instead of skipping it.
            requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() { requireActivity().finish(); }
            });
        }

        genderLabels = new String[]{getString(R.string.gender_male), getString(R.string.gender_female),
                getString(R.string.gender_other), getString(R.string.gender_prefer_not)};
        b.gender.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, genderLabels));

        b.changePhoto.setOnClickListener(v -> choosePhoto());
        b.avatar.setOnClickListener(v -> choosePhoto());
        b.addContact.setOnClickListener(v -> addContactRow(null));
        b.save.setOnClickListener(v -> save());

        vm.photo().observe(getViewLifecycleOwner(), p -> Avatars.load(b.avatar, p));
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            b.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE);
            b.save.setEnabled(!Boolean.TRUE.equals(busy));
        });
        vm.photoFailed().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() != null) Ui.snack(b.getRoot(), R.string.photo_processing_failed);
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
        vm.saved().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() == null) return;
            if (setupMode && requireActivity() instanceof AuthActivity) {
                ((AuthActivity) requireActivity()).openMain();
            } else {
                Ui.snack(b.getRoot(), R.string.profile_saved);
                Nav.back(this);
            }
        });
        vm.loaded().observe(getViewLifecycleOwner(), this::fill);
        if (s == null || contactRows.isEmpty()) vm.load();
    }

    private void fill(EditProfileViewModel.Loaded l) {
        if (l == null || !contactRows.isEmpty()) return;
        FirebaseUser fu = ServiceLocator.get().auth().currentUser();
        if (l.user != null) {
            b.name.setText(l.user.fullName);
            b.organization.setText(l.user.organization);
            int gi = genderIndex(l.user.gender);
            if (gi >= 0) b.gender.setText(genderLabels[gi], false);
        } else if (fu != null && fu.getDisplayName() != null) {
            b.name.setText(fu.getDisplayName());
        }
        if (l.info != null) {
            b.phone.setText(l.info.phone);
            if (l.info.emergencyContacts != null) for (EmergencyContact c : l.info.emergencyContacts) addContactRow(c);
        } else if (fu != null && fu.getPhoneNumber() != null) {
            b.phone.setText(fu.getPhoneNumber());
        }
        if (contactRows.isEmpty()) addContactRow(null);
    }

    private int genderIndex(String key) {
        if (key == null) return -1;
        for (int i = 0; i < GENDER_KEYS.length; i++) if (GENDER_KEYS[i].equals(key)) return i;
        return -1;
    }

    private void addContactRow(@Nullable EmergencyContact c) {
        if (contactRows.size() >= AppConfig.get().maxEmergencyContacts) {
            Ui.snack(b.getRoot(), R.string.error_contacts_max);
            return;
        }
        ItemEmergencyContactEditBinding row = ItemEmergencyContactEditBinding.inflate(getLayoutInflater(), b.contactsContainer, false);
        if (c != null) {
            row.contactName.setText(c.name);
            row.contactPhone.setText(c.phone);
        }
        row.remove.setOnClickListener(v -> {
            b.contactsContainer.removeView(row.getRoot());
            contactRows.remove(row);
            b.addContact.setEnabled(true);
        });
        b.contactsContainer.addView(row.getRoot());
        contactRows.add(row);
        b.addContact.setEnabled(contactRows.size() < AppConfig.get().maxEmergencyContacts);
    }

    private void choosePhoto() {
        boolean hasPhoto = vm.photo().getValue() != null && !vm.photo().getValue().isEmpty();
        String[] items = hasPhoto
                ? new String[]{getString(R.string.photo_from_camera), getString(R.string.photo_from_gallery), getString(R.string.photo_remove)}
                : new String[]{getString(R.string.photo_from_camera), getString(R.string.photo_from_gallery)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.photo_source_title)
                .setItems(items, (d, which) -> {
                    if (which == 0) launchCamera();
                    else if (which == 1) pickPhoto.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                    else vm.removePhoto();
                })
                .show();
    }

    private void launchCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no cache dir");
            File f = new File(dir, "avatar_capture.jpg");
            cameraUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", f);
            takePhoto.launch(cameraUri);
        } catch (Exception e) {
            Ui.snack(b.getRoot(), R.string.photo_processing_failed);
        }
    }

    private void save() {
        Ui.hideKeyboard(b.getRoot());
        String name = String.valueOf(b.name.getText()).trim();
        String phone = ProfileValidator.normalizePhone(String.valueOf(b.phone.getText()));
        boolean ok = true;
        b.nameLayout.setError(ProfileValidator.isValidName(name) ? null : getString(R.string.error_invalid_name));
        ok &= ProfileValidator.isValidName(name);
        b.phoneLayout.setError(ProfileValidator.isValidPhone(phone) ? null : getString(R.string.error_invalid_phone));
        ok &= ProfileValidator.isValidPhone(phone);

        List<String[]> raw = new ArrayList<>();
        List<EmergencyContact> contacts = new ArrayList<>();
        for (ItemEmergencyContactEditBinding row : contactRows) {
            String cn = String.valueOf(row.contactName.getText()).trim();
            String cp = ProfileValidator.normalizePhone(String.valueOf(row.contactPhone.getText()));
            if (cn.isEmpty() && cp.isEmpty()) continue;
            row.contactNameLayout.setError(ProfileValidator.isValidName(cn) ? null : getString(R.string.error_invalid_name));
            row.contactPhoneLayout.setError(ProfileValidator.isValidPhone(cp) ? null : getString(R.string.error_invalid_phone));
            raw.add(new String[]{cn, cp});
            contacts.add(new EmergencyContact(cn, cp));
        }
        boolean contactsOk = ProfileValidator.isValidEmergencyContacts(raw);
        b.contactsError.setVisibility(contactsOk ? View.GONE : View.VISIBLE);
        b.contactsError.setText(R.string.error_contacts_required);
        ok &= contactsOk;
        if (!ok) return;

        UserRepository.ProfileInput in = new UserRepository.ProfileInput();
        in.fullName = name;
        in.phone = phone;
        in.organization = String.valueOf(b.organization.getText()).trim();
        String g = String.valueOf(b.gender.getText());
        in.gender = null;
        for (int i = 0; i < genderLabels.length; i++) if (genderLabels[i].equals(g)) in.gender = GENDER_KEYS[i];
        FirebaseUser fu = ServiceLocator.get().auth().currentUser();
        in.email = fu == null ? null : fu.getEmail();
        in.emergencyContacts = contacts;
        vm.save(in);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        contactRows.clear();
        b = null;
    }
}