package com.example.ridealong.data.local;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads ONE contact the user picked from the system Contacts Content Provider
 * ({@link ContactsContract}) through a {@link ContentResolver}. Used to fill an emergency contact.
 * Requires {@code READ_CONTACTS} for the phone-number query.
 */
public final class DeviceContactsReader {

    public static final class PickedContact {
        @NonNull public final String name;
        /** Distinct numbers, the user's default ("super primary") number first. */
        @NonNull public final List<String> phones;

        PickedContact(@NonNull String name, @NonNull List<String> phones) {
            this.name = name;
            this.phones = phones;
        }
    }

    private DeviceContactsReader() { }

    /** @param contactUri the content:// URI returned by the contact picker. Null if it can't be read. */
    @WorkerThread
    @Nullable
    public static PickedContact read(@NonNull ContentResolver cr, @NonNull Uri contactUri) {
        String id = null;
        String name = "";
        boolean hasPhone = false;
        // 1) ContactsContract.Contacts table: id, display name, whether it has a number.
        String[] contactCols = {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER};
        try (Cursor c = cr.query(contactUri, contactCols, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            id = c.getString(0);
            name = c.getString(1) == null ? "" : c.getString(1);
            hasPhone = c.getInt(2) > 0;
        }
        List<String> phones = new ArrayList<>();
        if (!hasPhone || id == null) return new PickedContact(name, phones);

        // 2) ContactsContract.CommonDataKinds.Phone table: every number of that contact.
        Set<String> seen = new LinkedHashSet<>();
        String[] phoneCols = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor c = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, phoneCols,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id},
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC")) {
            while (c != null && c.moveToNext()) {
                String n = c.getString(0);
                if (n == null) continue;
                String key = n.replaceAll("[^0-9+]", "");
                if (!key.isEmpty() && seen.add(key)) phones.add(n.trim());
            }
        }
        return new PickedContact(name, phones);
    }
}
