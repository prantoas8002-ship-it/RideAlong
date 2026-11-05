package com.example.ridealong.data.local;

import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.data.model.EmergencyContact;
import com.example.ridealong.data.model.PrivateInfo;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.google.firebase.Timestamp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/** Converts between Firestore models and Room entities. */
public final class CacheMapper {
    private CacheMapper() { }

    public static CachedUserEntity toEntity(User u, PrivateInfo p, boolean self) {
        CachedUserEntity e = new CachedUserEntity();
        e.uid = u.uidOrId();
        e.fullName = u.fullName;
        e.organization = u.organization;
        e.gender = u.gender;
        e.photoBase64 = u.photoBase64;
        e.ratingSum = u.ratingSum;
        e.ratingCount = u.ratingCount;
        e.completedRides = u.completedRides;
        e.profileComplete = u.profileComplete;
        e.createdAtMillis = u.createdAt == null ? 0 : u.createdAt.toDate().getTime();
        e.isSelf = self;
        if (p != null) {
            e.phone = p.phone;
            e.email = p.email;
            e.emergencyContactsJson = contactsToJson(p.emergencyContacts);
        }
        e.cachedAt = System.currentTimeMillis();
        return e;
    }

    public static User toUser(CachedUserEntity e) {
        User u = new User();
        u.id = e.uid;
        u.uid = e.uid;
        u.fullName = e.fullName;
        u.organization = e.organization;
        u.gender = e.gender;
        u.photoBase64 = e.photoBase64;
        u.ratingSum = e.ratingSum;
        u.ratingCount = e.ratingCount;
        u.completedRides = e.completedRides;
        u.profileComplete = e.profileComplete;
        if (e.createdAtMillis > 0) u.createdAt = new Timestamp(new Date(e.createdAtMillis));
        return u;
    }

    public static PrivateInfo toPrivate(CachedUserEntity e) {
        PrivateInfo p = new PrivateInfo();
        p.phone = e.phone;
        p.email = e.email;
        p.emergencyContacts = jsonToContacts(e.emergencyContactsJson);
        return p;
    }

    public static CachedRideEntity toEntity(Ride r, String forUid) {
        CachedRideEntity e = new CachedRideEntity();
        e.id = r.id;
        e.ownerUid = forUid;
        e.rideOwnerId = r.ownerId;
        e.ownerName = r.ownerName;
        e.pickupAddress = r.pickupAddress;
        e.pickupLat = r.pickupLat;
        e.pickupLng = r.pickupLng;
        e.dropAddress = r.dropAddress;
        e.dropLat = r.dropLat;
        e.dropLng = r.dropLng;
        e.departureMillis = r.departureMillis();
        e.costPerPerson = r.costPerPerson;
        e.totalSeats = r.totalSeats;
        e.seatsAvailable = r.seatsAvailable;
        e.maxDetourKm = r.maxDetourKm;
        e.status = r.status;
        e.transportType = r.transportType;
        e.routePolyline = r.routePolyline;
        e.routeDistanceKm = r.routeDistanceKm;
        e.routeDurationMin = r.routeDurationMin;
        e.memberIdsCsv = r.memberIds == null ? "" : String.join(",", r.memberIds);
        e.comment = r.comment;
        e.cachedAt = System.currentTimeMillis();
        return e;
    }

    public static Ride toRide(CachedRideEntity e) {
        Ride r = new Ride();
        r.id = e.id;
        r.ownerId = e.rideOwnerId;
        r.ownerName = e.ownerName;
        r.pickupAddress = e.pickupAddress;
        r.pickupLat = e.pickupLat;
        r.pickupLng = e.pickupLng;
        r.dropAddress = e.dropAddress;
        r.dropLat = e.dropLat;
        r.dropLng = e.dropLng;
        r.departureTime = new Timestamp(new Date(e.departureMillis));
        r.costPerPerson = e.costPerPerson;
        r.totalSeats = e.totalSeats;
        r.seatsAvailable = e.seatsAvailable;
        r.maxDetourKm = e.maxDetourKm;
        r.status = e.status;
        r.transportType = e.transportType;
        r.routePolyline = e.routePolyline;
        r.routeDistanceKm = e.routeDistanceKm;
        r.routeDurationMin = e.routeDurationMin;
        r.memberIds = e.memberIdsCsv == null || e.memberIdsCsv.isEmpty()
                ? new ArrayList<>() : new ArrayList<>(Arrays.asList(e.memberIdsCsv.split(",")));
        r.comment = e.comment;
        return r;
    }

    public static CachedMessageEntity toEntity(ChatMessage m, String rideId) {
        CachedMessageEntity e = new CachedMessageEntity();
        e.id = m.id;
        e.rideId = rideId;
        e.senderId = m.senderId;
        e.senderName = m.senderName;
        e.text = m.text;
        e.type = m.type;
        e.createdMillis = m.createdMillis();
        e.editedMillis = m.editedAt == null ? 0 : m.editedAt.toDate().getTime();
        e.deleted = m.deleted;
        return e;
    }

    public static ChatMessage toMessage(CachedMessageEntity e) {
        ChatMessage m = new ChatMessage();
        m.id = e.id;
        m.senderId = e.senderId;
        m.senderName = e.senderName;
        m.text = e.text;
        m.type = e.type;
        m.createdAt = new Timestamp(new Date(e.createdMillis));
        if (e.editedMillis > 0) m.editedAt = new Timestamp(new Date(e.editedMillis));
        m.deleted = e.deleted;
        return m;
    }

    public static String contactsToJson(List<EmergencyContact> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (EmergencyContact c : list) {
                try {
                    arr.put(new JSONObject().put("name", c.name).put("phone", c.phone));
                } catch (JSONException ignored) {
                    // skip malformed entry
                }
            }
        }
        return arr.toString();
    }

    public static List<EmergencyContact> jsonToContacts(String json) {
        List<EmergencyContact> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new EmergencyContact(o.optString("name"), o.optString("phone")));
            }
        } catch (JSONException ignored) {
            // corrupt cache: return what parsed
        }
        return out;
    }
}