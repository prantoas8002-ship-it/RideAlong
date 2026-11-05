package com.example.ridealong.ui.requests;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.data.model.User;
import com.example.ridealong.databinding.ItemRequestBinding;
import com.example.ridealong.domain.model.RequestStatus;
import com.example.ridealong.ui.common.Avatars;
import com.example.ridealong.ui.common.RideUi;
import com.example.ridealong.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.VH> {
    interface Listener {
        void onAccept(RideRequest r);

        void onReject(RideRequest r);

        void onProfile(RideRequest r);
    }

    private final List<RideRequest> items = new ArrayList<>();
    private final Listener listener;
    private Set<String> working = new HashSet<>();
    private int seatsAvailable = Integer.MAX_VALUE;
    private boolean rideOpen = true;

    RequestsAdapter(Listener listener) { this.listener = listener; }

    void submit(List<RideRequest> list) {
        items.clear();
        if (list != null) items.addAll(list);
        // Pending first, then accepted, then the rest; newest first inside each group (query order).
        items.sort((a, b) -> Integer.compare(rank(a.statusEnum()), rank(b.statusEnum())));
        notifyDataSetChanged();
    }

    void setWorking(Set<String> w) {
        working = w == null ? new HashSet<>() : w;
        notifyDataSetChanged();
    }

    void setRideState(int seats, boolean open) {
        seatsAvailable = seats;
        rideOpen = open;
        notifyDataSetChanged();
    }

    private static int rank(RequestStatus s) {
        return s == RequestStatus.PENDING ? 0 : s == RequestStatus.ACCEPTED ? 1 : 2;
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemRequestBinding b;

        VH(ItemRequestBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRequestBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RideRequest r = items.get(position);
        Context c = h.b.getRoot().getContext();
        h.b.name.setText(r.requesterName);
        RequestStatus st = r.statusEnum();
        h.b.status.setText(RideUi.requestStatusLabel(c, st));
        int bg = st == RequestStatus.PENDING ? R.drawable.bg_pill_warning : st == RequestStatus.ACCEPTED ? R.drawable.bg_pill_success
                : R.drawable.bg_pill;
        int fg = st == RequestStatus.PENDING ? R.color.ra_warning : st == RequestStatus.ACCEPTED ? R.color.ra_success
                : R.color.ra_text_secondary;
        h.b.status.setBackgroundResource(bg);
        h.b.status.setTextColor(c.getColor(fg));

        StringBuilder d = new StringBuilder(c.getString(R.string.request_seats, r.seats));
        if (r.compatibility > 0) d.append("  ·  ").append(c.getString(R.string.match_score, (int) Math.round(r.compatibility)));
        if (r.detourKm > 0) d.append("  ·  ").append(c.getString(R.string.detour_value, r.detourKm));
        if (r.createdAt != null) d.append("  ·  ").append(DateTimeUtils.relative(c, r.createdAt.toDate().getTime()));
        h.b.details.setText(d);
        boolean hasMsg = r.message != null && !r.message.trim().isEmpty();
        h.b.message.setVisibility(hasMsg ? View.VISIBLE : View.GONE);
        h.b.message.setText(r.message);

        boolean pending = st == RequestStatus.PENDING;
        h.b.actions.setVisibility(pending ? View.VISIBLE : View.GONE);
        boolean busy = working.contains(r.id);
        h.b.accept.setEnabled(!busy && rideOpen && r.seats <= seatsAvailable);
        h.b.reject.setEnabled(!busy);
        h.b.accept.setOnClickListener(v -> listener.onAccept(r));
        h.b.reject.setOnClickListener(v -> listener.onReject(r));
        h.b.header.setOnClickListener(v -> listener.onProfile(r));

        User cached = ServiceLocator.get().users().peekCached(r.requesterId);
        bindUser(h, cached);
        if (cached == null) {
            ServiceLocator.get().users().getUser(r.requesterId).addOnSuccessListener(u -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && items.get(pos).requesterId.equals(r.requesterId)) bindUser(h, u);
            });
        }
    }

    private void bindUser(VH h, User u) {
        Avatars.load(h.b.avatar, u == null ? null : u.photoBase64);
        RideUi.bindRating(h.b.rating.ratingValue, h.b.rating.ratingRides, u);
    }

    @Override
    public int getItemCount() { return items.size(); }
}