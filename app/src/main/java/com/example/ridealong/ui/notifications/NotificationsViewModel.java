package com.example.ridealong.ui.notifications;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.AppNotification;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsViewModel extends ViewModel {
    /** One row; chat messages for the same ride are collapsed into the newest one. */
    public static final class Row {
        public final AppNotification n;
        public final List<AppNotification> group;
        public final boolean unread;

        Row(AppNotification n, List<AppNotification> group) {
            this.n = n;
            this.group = group;
            boolean u = false;
            for (AppNotification a : group) if (!a.read) u = true;
            this.unread = u;
        }
    }

    private LiveData<Resource<List<AppNotification>>> raw;
    private LiveData<Resource<List<Row>>> rows;

    public LiveData<Resource<List<Row>>> rows() {
        if (rows == null) {
            raw = ServiceLocator.get().notifications().observe(Me.uid());
            rows = Transformations.map(raw, r -> {
                if (r == null || r.data == null) {
                    return r == null ? Resource.loading(null)
                            : r.isError() ? Resource.<List<Row>>error(r.error, null) : Resource.<List<Row>>loading(null);
                }
                List<Row> out = new ArrayList<>();
                Map<String, Row> chats = new HashMap<>();
                Map<String, List<AppNotification>> groups = new HashMap<>();
                for (AppNotification n : r.data) {
                    String key = groupKey(n);
                    if (key != null) {
                        List<AppNotification> g = groups.get(key);
                        if (g == null) {
                            g = new ArrayList<>();
                            groups.put(key, g);
                            Row row = new Row(n, g);
                            chats.put(key, row);
                            out.add(row);
                        }
                        g.add(n);
                    } else {
                        List<AppNotification> single = new ArrayList<>();
                        single.add(n);
                        out.add(new Row(n, single));
                    }
                }
                // Recompute unread flags now that groups are complete.
                List<Row> fixed = new ArrayList<>();
                for (Row row : out) fixed.add(new Row(row.n, row.group));
                return Resource.success(fixed, r.fromCache);
            });
        }
        return rows;
    }

    /** Messages collapse into one row per conversation (ride group chat, or private chat). */
    private static String groupKey(AppNotification n) {
        if (Constants.N_NEW_MESSAGE.equals(n.type) && n.rideId != null) return "ride:" + n.rideId;
        if (Constants.N_DIRECT_MESSAGE.equals(n.type)) return "dm:" + directChatId(n);
        return null;
    }

    public static String directChatId(AppNotification n) {
        return n.args != null && n.args.size() > 2 ? n.args.get(2) : null;
    }

    public void open(Row row) {
        for (AppNotification n : row.group) if (!n.read && n.id != null) ServiceLocator.get().notifications().markRead(n.id);
    }

    public void markAllRead() {
        if (raw == null || raw.getValue() == null || raw.getValue().data == null) return;
        ServiceLocator.get().notifications().markAllRead(raw.getValue().data);
    }
}