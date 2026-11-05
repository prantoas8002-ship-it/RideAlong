package com.example.ridealong.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** rides/{rideId}/contacts/{uid}: the phone a co-rider uses for the Call button. Members only. */
@IgnoreExtraProperties
public class RideContact {
    @DocumentId public String userId;
    public String name;
    public String phone;

    public RideContact() { }
}