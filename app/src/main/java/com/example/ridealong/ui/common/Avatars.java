package com.example.ridealong.ui.common;

import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.ridealong.R;
import com.example.ridealong.utils.ImageUtils;

/** Loads Base64 avatars (Glide caches decoded bitmaps in memory) with a person placeholder. */
public final class Avatars {
    private Avatars() { }

    public static void load(ImageView view, @Nullable String base64) {
        byte[] bytes = ImageUtils.decodeBase64(base64);
        int pad = view.getResources().getDimensionPixelSize(R.dimen.space_s);
        if (bytes == null) {
            Glide.with(view).clear(view);
            view.setPadding(pad, pad, pad, pad);
            view.setImageResource(R.drawable.ic_person);
            view.setColorFilter(view.getContext().getColor(R.color.ra_primary));
            return;
        }
        view.setPadding(0, 0, 0, 0);
        view.clearColorFilter();
        Glide.with(view).load(bytes)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // bytes are already local; memory cache only
                .placeholder(R.drawable.ic_person)
                .centerCrop()
                .into(view);
    }
}