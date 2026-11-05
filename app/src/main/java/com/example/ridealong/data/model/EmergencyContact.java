package com.example.ridealong.data.model;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class EmergencyContact {
    public String name;
    public String phone;

    public EmergencyContact() { }

    public EmergencyContact(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }
}