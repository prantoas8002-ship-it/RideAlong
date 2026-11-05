package com.example.ridealong.ui.rides;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ridealong.databinding.ItemPlaceSuggestionBinding;
import com.example.ridealong.map.PlaceResult;

import java.util.ArrayList;
import java.util.List;

final class PlaceSuggestionAdapter extends RecyclerView.Adapter<PlaceSuggestionAdapter.VH> {
    interface OnPick {
        void onPick(PlaceResult place);
    }

    private final List<PlaceResult> items = new ArrayList<>();
    private final OnPick onPick;

    PlaceSuggestionAdapter(OnPick onPick) { this.onPick = onPick; }

    void submit(List<PlaceResult> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemPlaceSuggestionBinding b;

        VH(ItemPlaceSuggestionBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemPlaceSuggestionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PlaceResult p = items.get(position);
        h.b.placeName.setText(p.label());
        h.b.placeAddress.setText(p.address);
        h.b.getRoot().setOnClickListener(v -> onPick.onPick(p));
    }

    @Override
    public int getItemCount() { return items.size(); }
}