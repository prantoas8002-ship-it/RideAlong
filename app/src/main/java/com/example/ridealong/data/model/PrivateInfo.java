package com.example.ridealong.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

/** users/{uid}/private/info: readable by the owner only. */
@IgnoreExtraProperties
public class PrivateInfo {
    public String phone;
    public String email;
    public List<EmergencyContact> emergencyContacts = new ArrayList<>();
    public Timestamp updatedAt;

    public PrivateInfo() { }
}