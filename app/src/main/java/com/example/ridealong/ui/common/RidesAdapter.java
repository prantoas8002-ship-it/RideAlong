package com.example.ridealong.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.example.ridealong.databinding.ItemRideCardBinding;
import com.example.ridealong.utils.DateTimeUtils;

/** Ride cards for Home, Discover and History. Poster profiles are lazy-loaded through the user cache. */
public class RidesAdapter extends ListAdapter<RideItem, RidesAdapter.VH> {

    public interface OnRideClick {
        void onClick(RideItem item);
    }

    public enum Mode { DISCOVER, MINE }

    private final OnRideClick click;
    private final Mode mode;

    public RidesAdapter(Mode mode, OnRideClick click) {
        super(new DiffUtil.ItemCallback<RideItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull RideItem a, @NonNull RideItem b) { return a.ride.id.equals(b.ride.id); }

            @Override
            public boolean areContentsTheSame(@NonNull RideItem a, @NonNull RideItem b) { return a.sameContent(b); }
        });
        this.mode = mode;
        this.click = click;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRideCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) { h.bind(getItem(position)); }

    final class VH extends RecyclerView.ViewHolder {
        final ItemRideCardBinding b;

        VH(ItemRideCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(RideItem item) {
            Context c = b.getRoot().getContext();
            Ride r = item.ride;
            b.pickup.setText(r.pickupAddress);
            b.drop.setText(r.dropAddress);
            b.time.setText(DateTimeUtils.formatDeparture(c, r.departureMillis()));
            b.seats.setText(c.getString(R.string.seats_left_short, r.seatsAvailable));
            b.cost.setText(RideUi.cost(c, r.costPerPerson));
            b.ownerName.setText(r.ownerName);

            User owner = item.owner;
            if (owner == null) {
                owner = ServiceLocator.get().users().peekCached(r.ownerId);
                if (owner == null) {
                    final String rideId = r.id;
                    ServiceLocator.get().users().getUser(r.ownerId).addOnSuccessListener(u -> {
                        int pos = getBindingAdapterPosition();
                        if (u != null && pos != RecyclerView.NO_POSITION && getItem(pos).ride.id.equals(rideId)) {
                            bindOwner(u);
                        }
                    });
                }
            }
            bindOwner(owner);

            if (mode == Mode.DISCOVER && item.match != null) {
                b.badge.setVisibility(View.VISIBLE);
                b.badge.setText(c.getString(R.string.match_score, item.match.score));
                b.badge.setBackgroundResource(R.drawable.bg_pill_secondary);
                b.badge.setTextColor(c.getColor(R.color.ra_on_secondary_container));
            } else if (mode == Mode.DISCOVER) {
                b.badge.setVisibility(View.GONE);
            } else {
                b.badge.setVisibility(View.VISIBLE);
                RideUi.styleStatus(b.badge, r.statusEnum());
            }

            StringBuilder extra = new StringBuilder();
            if (item.distanceKm != null) extra.append(c.getString(R.string.distance_away, item.distanceKm));
            if (item.match != null && !Double.isNaN(item.match.detourKm)) {
                if (extra.length() > 0) extra.append("  ·  ");
                extra.append(c.getString(R.string.detour_value, item.match.detourKm));
            }
            if (item.unread > 0) {
                if (extra.length() > 0) extra.append("  ·  ");
                extra.append(c.getString(R.string.chat_unread, (int) item.unread));
            }
            if (item.cached) {
                if (extra.length() > 0) extra.append("  ·  ");
                extra.append(c.getString(R.string.cached_label));
            }
            b.extra.setVisibility(extra.length() > 0 ? View.VISIBLE : View.GONE);
            b.extra.setText(extra);
            b.extra.setTextColor(c.getColor(item.unread > 0 ? R.color.ra_primary : R.color.ra_text_secondary));
            b.getRoot().setOnClickListener(v -> click.onClick(item));
        }

        private void bindOwner(User u) {
            Avatars.load(b.ownerAvatar, u == null ? null : u.photoBase64);
            RideUi.bindRating(b.ownerRating.ratingValue, b.ownerRating.ratingRides, u);
        }
    }
}