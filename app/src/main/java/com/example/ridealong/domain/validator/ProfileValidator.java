package com.example.ridealong.domain.validator;

import java.util.List;
import java.util.regex.Pattern;

/** Input validation for auth and profile forms (pure Java). */
public final class ProfileValidator {
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_NAME = 80;
    public static final int MIN_EMERGENCY_CONTACTS = 1;
    public static final int MAX_EMERGENCY_CONTACTS = 5;

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** Bangladesh mobile: 01[3-9]XXXXXXXX with optional +88 / 88 prefix. */
    private static final Pattern BD_MOBILE = Pattern.compile("^(?:\\+?88)?01[3-9]\\d{8}$");
    /** Generic international fallback (E.164-ish) for non-BD numbers. */
    private static final Pattern INTL = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private ProfileValidator() { }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL.matcher(email.trim()).matches();
    }

    /** At least 8 chars with at least one letter and one digit. */
    public static boolean isValidPassword(String pw) {
        if (pw == null || pw.length() < MIN_PASSWORD) return false;
        boolean letter = false, digit = false;
        for (char c : pw.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        return letter && digit;
    }

    public static boolean isValidName(String name) {
        if (name == null) return false;
        String t = name.trim();
        return t.length() >= 2 && t.length() <= MAX_NAME;
    }

    /** Strips spaces, dashes and parentheses. */
    public static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[\\s\\-()]", "");
    }

    public static boolean isValidPhone(String phone) {
        String p = normalizePhone(phone);
        return BD_MOBILE.matcher(p).matches() || INTL.matcher(p).matches();
    }

    /** Emergency contact list: 1..5 entries, each with a name and a valid phone. */
    public static boolean isValidEmergencyContacts(List<String[]> contacts) {
        if (contacts == null || contacts.size() < MIN_EMERGENCY_CONTACTS || contacts.size() > MAX_EMERGENCY_CONTACTS) {
            return false;
        }
        for (String[] c : contacts) {
            if (c == null || c.length < 2 || !isValidName(c[0]) || !isValidPhone(c[1])) return false;
        }
        return true;
    }
}
