package com.example.ridealong.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.AppNotification;
import com.example.ridealong.data.repository.NotificationRepository;
import com.example.ridealong.databinding.FragmentListBinding;
import com.example.ridealong.databinding.ItemNotificationBinding;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.UiErrors;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {
    private FragmentListBinding b;
    private NotificationsViewModel vm;
    private final List<NotificationsViewModel.Row> rows = new ArrayList<>();
    private Adapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(NotificationsViewModel.class);
        adapter = new Adapter();
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        if (!ServiceLocator.get().settings().backgroundSync()) {
            b.headerNote.setVisibility(View.VISIBLE);
            b.headerNote.setText(R.string.notifications_background_note);
        }
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.menu_notifications, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() != R.id.action_mark_all_read) return false;
                vm.markAllRead();
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        b.state.showLoading();
        vm.rows().observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            if (r.isLoading()) {
                b.state.showLoading();
                return;
            }
            if (r.isError()) {
                b.state.showError(UiErrors.message(requireContext(), r.error), null);
                return;
            }
            rows.clear();
            if (r.data != null) rows.addAll(r.data);
            adapter.notifyDataSetChanged();
            if (rows.isEmpty()) b.state.showEmpty(R.drawable.ic_notifications, R.string.notifications_empty_title, R.string.notifications_empty_body);
            else b.state.hide();
        });
    }

    private static int iconFor(String type) {
        if (type == null) return R.drawable.ic_notifications;
        switch (type) {
            case Constants.N_JOIN_REQUEST: return R.drawable.ic_inbox;
            case Constants.N_REQUEST_ACCEPTED: return R.drawable.ic_check;
            case Constants.N_REQUEST_REJECTED:
            case Constants.N_REQUEST_CANCELLED:
            case Constants.N_RIDE_CANCELLED: return R.drawable.ic_close;
            case Constants.N_PARTICIPANT_LEFT: return R.drawable.ic_exit;
            case Constants.N_NEW_MESSAGE:
            case Constants.N_DIRECT_MESSAGE: return R.drawable.ic_chat;
            case Constants.N_MISSED_CALL: return R.drawable.ic_call;
            case Constants.N_RIDE_STARTED: return R.drawable.ic_navigation;
            case Constants.N_RIDE_COMPLETED: return R.drawable.ic_star;
            default: return R.drawable.ic_notifications;
        }
    }

    private void open(NotificationsViewModel.Row row) {
        vm.open(row);
        AppNotification n = row.n;
        if (Constants.N_MISSED_CALL.equals(n.type)) {
            String chat = n.args != null && n.args.size() > 2 ? n.args.get(2) : "";
            if (chat != null && !chat.isEmpty()) Nav.go(this, R.id.directChatFragment, Nav.chat(chat));
            else if (n.rideId != null) Nav.go(this, R.id.chatFragment, Nav.ride(n.rideId));
            return;
        }
        if (Constants.N_DIRECT_MESSAGE.equals(n.type) && NotificationsViewModel.directChatId(n) != null) {
            Nav.go(this, R.id.directChatFragment, Nav.chat(NotificationsViewModel.directChatId(n)));
            return;
        }
        if (n.rideId == null) return;
        if (Constants.N_NEW_MESSAGE.equals(n.type)) Nav.go(this, R.id.chatFragment, Nav.ride(n.rideId));
        else if (Constants.N_JOIN_REQUEST.equals(n.type)) Nav.go(this, R.id.rideRequestsFragment, Nav.ride(n.rideId));
        else if (Constants.N_RIDE_COMPLETED.equals(n.type)) Nav.go(this, R.id.ratingFragment, Nav.ride(n.rideId));
        else Nav.go(this, R.id.rideDetailsFragment, Nav.ride(n.rideId));
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final ItemNotificationBinding v;

            VH(ItemNotificationBinding v) {
                super(v.getRoot());
                this.v = v;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemNotificationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            NotificationsViewModel.Row row = rows.get(position);
            String[] tb = NotificationRepository.render(requireContext(), row.n);
            h.v.title.setText(tb[0]);
            h.v.body.setText(row.group.size() > 1 ? getString(R.string.notification_more, tb[1], row.group.size() - 1) : tb[1]);
            h.v.time.setText(row.n.createdAt == null ? "" : DateTimeUtils.relative(requireContext(), row.n.createdAt.toDate().getTime()));
            h.v.icon.setImageResource(iconFor(row.n.type));
            h.v.unreadDot.setVisibility(row.unread ? View.VISIBLE : View.INVISIBLE);
            h.v.title.setAlpha(row.unread ? 1f : 0.75f);
            h.v.root.setOnClickListener(v -> open(row));
        }

        @Override
        public int getItemCount() { return rows.size(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}