package com.example.ridealong.data.repository;

import androidx.lifecycle.LiveData;

import com.example.ridealong.data.model.Review;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.model.RideStatus;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reviews: one per (ride, reviewer, reviewee), immutable. The reviewee's rating counters are
 * incremented in the SAME batch as the review; rules verify the increment equals the stars, so
 * the average is never a blindly trusted client total.
 */
public class ReviewRepository {
    private DocumentReference reviewRef(String id) {
        return FirebaseProvider.db().collection(FirestorePaths.REVIEWS).document(id);
    }

    public Task<Void> submit(Ride ride, String revieweeId, int rating, String comment, String myName) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        if (ride.statusEnum() != RideStatus.COMPLETED) return Tasks.forException(new DomainException(DomainException.Reason.INVALID_TRANSITION));
        if (!ride.isMember(uid) || !ride.isMember(revieweeId) || uid.equals(revieweeId)) {
            return Tasks.forException(new DomainException(DomainException.Reason.NOT_MEMBER));
        }
        int stars = Math.max(1, Math.min(5, rating));
        String id = FirestorePaths.reviewId(ride.id, uid, revieweeId);
        DocumentReference ref = reviewRef(id);
        return ref.get().onSuccessTask(existing -> {
            if (existing != null && existing.exists()) {
                return Tasks.<Void>forException(new DomainException(DomainException.Reason.ALREADY_RATED));
            }
            Map<String, Object> r = new HashMap<>();
            r.put("rideId", ride.id);
            r.put("reviewerId", uid);
            r.put("reviewerName", myName);
            r.put("revieweeId", revieweeId);
            r.put("rating", stars);
            r.put("comment", comment == null ? "" : comment.trim());
            r.put("createdAt", FieldValue.serverTimestamp());

            Map<String, Object> u = new HashMap<>();
            u.put("ratingSum", FieldValue.increment(stars));
            u.put("ratingCount", FieldValue.increment(1));
            u.put("lastReviewId", id);

            WriteBatch b = FirebaseProvider.db().batch();
            b.set(ref, r);
            b.update(FirebaseProvider.db().collection(FirestorePaths.USERS).document(revieweeId), u);
            return b.commit();
        });
    }

    /** Which of the candidates I already reviewed for this ride. */
    public Task<Set<String>> alreadyReviewed(String rideId, String uid, List<String> candidates) {
        List<Task<DocumentSnapshot>> gets = new ArrayList<>();
        for (String c : candidates) gets.add(reviewRef(FirestorePaths.reviewId(rideId, uid, c)).get());
        return Tasks.whenAllComplete(gets).continueWith(t -> {
            Set<String> done = new HashSet<>();
            for (int i = 0; i < gets.size(); i++) {
                Task<DocumentSnapshot> g = gets.get(i);
                if (g.isSuccessful() && g.getResult() != null && g.getResult().exists()) done.add(candidates.get(i));
            }
            return done;
        });
    }

    public LiveData<Resource<List<Review>>> observeReviewsFor(String uid) {
        Query q = FirebaseProvider.db().collection(FirestorePaths.REVIEWS).whereEqualTo("revieweeId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50);
        return FirestoreLiveData.query(q, Review.class);
    }
}