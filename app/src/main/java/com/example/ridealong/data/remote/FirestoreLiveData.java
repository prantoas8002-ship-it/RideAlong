package com.example.ridealong.data.remote;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.ridealong.data.repository.Resource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time Firestore listeners exposed as LiveData. The listener is attached only while the
 * LiveData has active observers. {@link Resource#fromCache} tells the UI whether data is live.
 */
public final class FirestoreLiveData {
    private FirestoreLiveData() { }

    public interface Mapper<T> {
        @Nullable T map(DocumentSnapshot d);
    }

    /** Callback for side effects (e.g. writing to Room) on fresh server data. */
    public interface OnFresh<T> {
        void onFresh(T value);
    }

    public static <T> Mapper<T> pojo(Class<T> cls) {
        return d -> d.toObject(cls, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
    }

    public static final class QueryLive<T> extends LiveData<Resource<List<T>>> {
        private final Query query;
        private final Mapper<T> mapper;
        private final OnFresh<List<T>> onFresh;
        private ListenerRegistration reg;

        QueryLive(Query query, Mapper<T> mapper, OnFresh<List<T>> onFresh) {
            this.query = query;
            this.mapper = mapper;
            this.onFresh = onFresh;
            setValue(Resource.loading(null));
        }

        @Override
        protected void onActive() {
            if (reg != null) return;
            reg = query.addSnapshotListener(MetadataChanges.INCLUDE, (snap, err) -> {
                if (err != null) {
                    Resource<List<T>> cur = getValue();
                    setValue(Resource.error(err, cur == null ? null : cur.data));
                    return;
                }
                if (snap == null) return;
                List<T> out = new ArrayList<>();
                for (DocumentSnapshot d : snap.getDocuments()) {
                    T v = mapper.map(d);
                    if (v != null) out.add(v);
                }
                boolean cache = snap.getMetadata().isFromCache();
                setValue(Resource.success(out, cache));
                if (!cache && onFresh != null) onFresh.onFresh(out);
            });
        }

        @Override
        protected void onInactive() {
            if (reg != null) {
                reg.remove();
                reg = null;
            }
        }
    }

    public static final class DocLive<T> extends LiveData<Resource<T>> {
        private final DocumentReference ref;
        private final Mapper<T> mapper;
        private final OnFresh<T> onFresh;
        private ListenerRegistration reg;

        DocLive(DocumentReference ref, Mapper<T> mapper, OnFresh<T> onFresh) {
            this.ref = ref;
            this.mapper = mapper;
            this.onFresh = onFresh;
            setValue(Resource.loading(null));
        }

        @Override
        protected void onActive() {
            if (reg != null) return;
            reg = ref.addSnapshotListener(MetadataChanges.INCLUDE, (snap, err) -> {
                if (err != null) {
                    Resource<T> cur = getValue();
                    setValue(Resource.error(err, cur == null ? null : cur.data));
                    return;
                }
                if (snap == null) return;
                T v = snap.exists() ? mapper.map(snap) : null;
                boolean cache = snap.getMetadata().isFromCache();
                setValue(Resource.success(v, cache));
                if (!cache && v != null && onFresh != null) onFresh.onFresh(v);
            });
        }

        @Override
        protected void onInactive() {
            if (reg != null) {
                reg.remove();
                reg = null;
            }
        }
    }

    public static <T> QueryLive<T> query(Query q, Class<T> cls) { return new QueryLive<>(q, pojo(cls), null); }

    public static <T> QueryLive<T> query(Query q, Class<T> cls, OnFresh<List<T>> onFresh) {
        return new QueryLive<>(q, pojo(cls), onFresh);
    }

    public static <T> DocLive<T> doc(DocumentReference ref, Class<T> cls) { return new DocLive<>(ref, pojo(cls), null); }

    public static <T> DocLive<T> doc(DocumentReference ref, Class<T> cls, OnFresh<T> onFresh) {
        return new DocLive<>(ref, pojo(cls), onFresh);
    }
}
