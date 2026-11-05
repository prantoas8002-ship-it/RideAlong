package com.example.ridealong.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ridealong.R;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.example.ridealong.databinding.FragmentListBinding;
import com.example.ridealong.databinding.ItemRatingTargetBinding;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.ui.common.Avatars;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.utils.Constants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rate every co-rider of a COMPLETED ride: 1-5 stars + optional review, once per person. */
public class RatingFragment extends Fragment {
    private FragmentListBinding b;
    private RatingViewModel vm;
    private final List<User> targets = new ArrayList<>();
    private Set<String> done = new HashSet<>();
    private Set<String> working = new HashSet<>();
    private final Adapter adapter = new Adapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(RatingViewModel.class);
        vm.init(requireArguments().getString(Constants.ARG_RIDE_ID));
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.headerNote.setVisibility(View.VISIBLE);
        b.headerNote.setText(R.string.rating_subtitle);
        b.state.showLoading();

        vm.ride().observe(getViewLifecycleOwner(), r -> {
            Ride ride = r == null ? null : r.data;
            if (ride == null) return;
            if (ride.statusEnum() != RideStatus.COMPLETED) {
                b.state.showEmpty(R.drawable.ic_star, R.string.rating_empty_title, R.string.rating_empty_body);
                return;
            }
            vm.onRide(ride);
        });
        vm.targets().observe(getViewLifecycleOwner(), list -> {
            targets.clear();
            if (list != null) targets.addAll(list);
            adapter.notifyDataSetChanged();
            if (targets.isEmpty()) b.state.showEmpty(R.drawable.ic_star, R.string.rating_empty_title, R.string.rating_empty_body);
            else b.state.hide();
        });
        vm.done().observe(getViewLifecycleOwner(), d -> {
            done = d;
            adapter.notifyDataSetChanged();
        });
        vm.working().observe(getViewLifecycleOwner(), w -> {
            working = w;
            adapter.notifyDataSetChanged();
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
        vm.message().observe(getViewLifecycleOwner(), e -> {
            Integer m = e.consume();
            if (m != null) Ui.snack(b.getRoot(), m);
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final ItemRatingTargetBinding v;

            VH(ItemRatingTargetBinding v) {
                super(v.getRoot());
                this.v = v;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemRatingTargetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            User u = targets.get(position);
            String uid = u.uidOrId();
            h.v.name.setText(u.displayName().isEmpty() ? getString(R.string.unknown_user) : u.displayName());
            Avatars.load(h.v.avatar, u.photoBase64);
            boolean isDone = done.contains(uid);
            h.v.stars.setIsIndicator(isDone);
            h.v.commentLayout.setVisibility(isDone ? View.GONE : View.VISIBLE);
            h.v.submit.setVisibility(isDone ? View.GONE : View.VISIBLE);
            h.v.done.setVisibility(isDone ? View.VISIBLE : View.GONE);
            h.v.submit.setEnabled(!working.contains(uid));
            h.v.submit.setOnClickListener(v -> {
                int stars = Math.round(h.v.stars.getRating());
                if (stars < 1) {
                    Ui.snack(b.getRoot(), R.string.rating_need_stars);
                    return;
                }
                Ride ride = vm.ride().getValue() == null ? null : vm.ride().getValue().data;
                if (ride != null) vm.submit(ride, uid, stars, String.valueOf(h.v.comment.getText()).trim());
            });
        }

        @Override
        public int getItemCount() { return targets.size(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}