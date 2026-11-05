package com.example.ridealong.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.databinding.ActivityOnboardingBinding;
import com.example.ridealong.databinding.ItemOnboardingPageBinding;
import com.example.ridealong.ui.auth.AuthActivity;
import com.google.android.material.tabs.TabLayoutMediator;

/** Three-page introduction shown once. */
public class OnboardingActivity extends AppCompatActivity {
    private static final int[][] PAGES = {
            {R.drawable.ic_group, R.string.onboarding_1_title, R.string.onboarding_1_body},
            {R.drawable.ic_route, R.string.onboarding_2_title, R.string.onboarding_2_body},
            {R.drawable.ic_shield, R.string.onboarding_3_title, R.string.onboarding_3_body},
    };

    private ActivityOnboardingBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.pager.setAdapter(new PageAdapter());
        new TabLayoutMediator(b.dots, b.pager, (tab, pos) -> { }).attach();
        b.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                b.next.setText(position == PAGES.length - 1 ? R.string.action_get_started : R.string.action_next);
            }
        });
        b.next.setOnClickListener(v -> {
            int p = b.pager.getCurrentItem();
            if (p < PAGES.length - 1) b.pager.setCurrentItem(p + 1);
            else finishOnboarding();
        });
        b.skip.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        ServiceLocator.get().settings().setOnboardingDone();
        startActivity(new Intent(this, AuthActivity.class));
        finish();
    }

    private static final class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {
        static final class VH extends RecyclerView.ViewHolder {
            final ItemOnboardingPageBinding b;

            VH(ItemOnboardingPageBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            h.b.illustration.setImageResource(PAGES[position][0]);
            h.b.illustration.setBackgroundResource(position == 1 ? R.drawable.bg_circle_secondary_container
                    : R.drawable.bg_circle_primary_container);
            h.b.title.setText(PAGES[position][1]);
            h.b.body.setText(PAGES[position][2]);
        }

        @Override
        public int getItemCount() { return PAGES.length; }
    }
}