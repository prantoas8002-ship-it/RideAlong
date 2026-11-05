package com.example.ridealong.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.local.CacheMapper;
import com.example.ridealong.data.local.CachedUserEntity;
import com.example.ridealong.data.model.Review;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.ui.common.Me;

import java.util.List;

/** Own or another user's public profile; own profile falls back to the Room cache offline. */
public class ProfileViewModel extends ViewModel {
    private String userId;
    private boolean self;
    private final MediatorLiveData<Resource<User>> user = new MediatorLiveData<>();
    private LiveData<Resource<List<Review>>> reviews;

    public void init(String requestedId) {
        if (userId != null) return;
        String me = Me.uid();
        userId = requestedId == null ? me : requestedId;
        self = userId != null && userId.equals(me);
        LiveData<Resource<User>> live = ServiceLocator.get().users().observeUser(userId, self);
        user.addSource(live, r -> {
            if (r == null) return;
            if (r.data == null && self && (r.isError() || r.fromCache)) return; // let the Room source answer
            user.setValue(r);
        });
        if (self) {
            LiveData<CachedUserEntity> cached = ServiceLocator.get().users().observeCached(userId);
            user.addSource(cached, e -> {
                Resource<User> cur = user.getValue();
                if (e != null && (cur == null || cur.data == null)) user.setValue(Resource.success(CacheMapper.toUser(e), true));
            });
        }
        reviews = ServiceLocator.get().reviews().observeReviewsFor(userId);
    }

    public boolean isSelf() { return self; }

    public String userId() { return userId; }

    public LiveData<Resource<User>> user() { return user; }

    public LiveData<Resource<List<Review>>> reviews() { return reviews; }
}