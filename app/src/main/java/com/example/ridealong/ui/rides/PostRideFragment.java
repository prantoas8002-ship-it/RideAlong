package com.example.ridealong.ui.rides;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.fragment.NavHostFragment;

import com.example.ridealong.R;
import com.example.ridealong.databinding.FragmentPostRideBinding;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.domain.model.TransportType;
import com.example.ridealong.domain.validator.RideInputValidator;
import com.example.ridealong.map.PlaceResult;
import com.example.ridealong.map.RouteResult;
import com.example.ridealong.ui.common.MapHost;
import com.example.ridealong.ui.common.Nav;
import com.example.ridealong.ui.common.RideUi;
import com.example.ridealong.ui.common.Ui;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.DateTimeUtils;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class PostRideFragment extends Fragment {
    private FragmentPostRideBinding b;
    private PostRideViewModel vm;
    private MapHost map;
    /** Date chosen in the date picker, waiting for a time. */
    private Long pendingDateUtc;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentPostRideBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        vm = new ViewModelProvider(this).get(PostRideViewModel.class);
        if (s == null && b.detour.getText() != null && b.detour.getText().length() == 0) {
            b.detour.setText(String.format(Locale.getDefault(), "%.1f", AppConfig.get().postDefaultMaxDetourKm));
        }

        b.pickup.setOnClickListener(v -> openPicker(Constants.PICK_KIND_PICKUP, vm.pickup().getValue()));
        b.drop.setOnClickListener(v -> openPicker(Constants.PICK_KIND_DROP, vm.drop().getValue()));
        b.date.setOnClickListener(v -> pickDate());
        b.time.setOnClickListener(v -> pickDate());
        b.seatsMinus.setOnClickListener(v -> vm.changeSeats(-1));
        b.seatsPlus.setOnClickListener(v -> vm.changeSeats(1));
        b.seatsMinus.setContentDescription(getString(R.string.cd_decrease_seats));
        b.seatsPlus.setContentDescription(getString(R.string.cd_increase_seats));
        b.transportGroup.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            vm.setTransport(id == R.id.chip_rickshaw ? TransportType.RICKSHAW : id == R.id.chip_car ? TransportType.CAR
                    : id == R.id.chip_bus ? TransportType.BUS : TransportType.CNG);
        });
        b.useEstimate.setOnClickListener(v -> {
            Double est = vm.estimate().getValue();
            if (est != null) b.cost.setText(RideUi.money(est).replace(",", ""));
        });
        b.post.setOnClickListener(v -> submit());

        observePickerResults();
        vm.pickup().observe(getViewLifecycleOwner(), p -> {
            b.pickup.setText(p == null ? "" : p.label());
            drawRoute();
        });
        vm.drop().observe(getViewLifecycleOwner(), p -> {
            b.drop.setText(p == null ? "" : p.label());
            drawRoute();
        });
        vm.departure().observe(getViewLifecycleOwner(), ms -> {
            b.date.setText(ms == null ? "" : DateTimeUtils.formatDate(requireContext(), ms));
            b.time.setText(ms == null ? "" : DateTimeUtils.formatTime(requireContext(), ms));
        });
        vm.seats().observe(getViewLifecycleOwner(), n -> {
            b.seatsValue.setText(getString(R.string.seats_value, n));
            b.seatsMinus.setEnabled(n > com.example.ridealong.domain.validator.SeatValidator.MIN_SEATS);
            b.seatsPlus.setEnabled(n < com.example.ridealong.domain.validator.SeatValidator.MAX_SEATS);
        });
        vm.routeLoading().observe(getViewLifecycleOwner(), l -> {
            if (Boolean.TRUE.equals(l)) {
                b.routeSummary.setVisibility(View.VISIBLE);
                b.routeSummary.setText(R.string.route_loading);
            }
        });
        vm.route().observe(getViewLifecycleOwner(), r -> {
            if (r != null) {
                b.routeSummary.setVisibility(View.VISIBLE);
                b.routeSummary.setText(r.approximate ? getString(R.string.route_summary_approx, r.distanceKm)
                        : getString(R.string.route_summary, r.distanceKm, r.durationMin));
            } else if (!Boolean.TRUE.equals(vm.routeLoading().getValue())) {
                b.routeSummary.setVisibility(View.GONE);
            }
            drawRoute();
        });
        vm.estimate().observe(getViewLifecycleOwner(), est -> {
            RouteResult r = vm.route().getValue();
            if (est == null || r == null) {
                b.fareEstimate.setText(R.string.fare_estimate_need_route);
                b.useEstimate.setEnabled(false);
            } else {
                b.fareEstimate.setText(getString(R.string.fare_estimate, AppConfig.get().fare.currencySymbol,
                        RideUi.money(est), RideUi.transportLabel(requireContext(), vm.transport().getValue()),
                        r.distanceKm, vm.participants()));
                b.useEstimate.setEnabled(true);
            }
        });
        vm.errors().observe(getViewLifecycleOwner(), this::showErrors);
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            b.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE);
            b.post.setEnabled(!Boolean.TRUE.equals(busy));
        });
        vm.failure().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null) Ui.error(b.getRoot(), t);
        });
        vm.posted().observe(getViewLifecycleOwner(), e -> {
            String id = e.consume();
            if (id == null) return;
            b.cost.setText("");
            b.comment.setText("");
            Ui.snack(b.getRoot(), R.string.ride_posted);
            Nav.go(this, R.id.rideDetailsFragment, Nav.ride(id));
        });
    }

    private void observePickerResults() {
        NavBackStackEntry entry = NavHostFragment.findNavController(this).getCurrentBackStackEntry();
        if (entry == null) return;
        SavedStateHandle h = entry.getSavedStateHandle();
        h.<PlaceResult>getLiveData(Constants.RESULT_PICKED_PICKUP).observe(getViewLifecycleOwner(), p -> {
            if (p == null) return;
            vm.setPickup(p);
            h.remove(Constants.RESULT_PICKED_PICKUP);
        });
        h.<PlaceResult>getLiveData(Constants.RESULT_PICKED_DROP).observe(getViewLifecycleOwner(), p -> {
            if (p == null) return;
            vm.setDrop(p);
            h.remove(Constants.RESULT_PICKED_DROP);
        });
    }

    private void openPicker(String kind, @Nullable PlaceResult current) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_PICK_KIND, kind);
        PlaceResult anchor = current != null ? current : vm.pickup().getValue();
        if (anchor != null) {
            args.putFloat(Constants.ARG_START_LAT, (float) anchor.lat);
            args.putFloat(Constants.ARG_START_LNG, (float) anchor.lng);
        }
        Nav.go(this, R.id.locationPickerFragment, args);
    }

    private void drawRoute() {
        PlaceResult a = vm.pickup().getValue(), d = vm.drop().getValue();
        if (a == null && d == null) {
            b.mapCard.setVisibility(View.GONE);
            return;
        }
        b.mapCard.setVisibility(View.VISIBLE);
        if (map == null) map = new MapHost(getViewLifecycleOwner(), b.mapContainer);
        map.whenReady(m -> {
            m.setInteractive(false);
            if (a != null) m.setMarker("pickup", a.coord(), a.label(), R.drawable.marker_pickup);
            else m.removeMarker("pickup");
            if (d != null) m.setMarker("drop", d.coord(), d.label(), R.drawable.marker_drop);
            else m.removeMarker("drop");
            RouteResult r = vm.route().getValue();
            if (r != null) {
                m.setRoute("route", r.points, requireContext().getColor(R.color.ra_primary), 5f);
                m.fitTo(r.points, Ui.dp(requireContext(), 36));
            } else {
                m.clearRoute("route");
                if (a != null && d != null) m.fitTo(Arrays.asList(a.coord(), d.coord()), Ui.dp(requireContext(), 36));
                else {
                    GeoCoord c = a != null ? a.coord() : d.coord();
                    m.setCenter(c, AppConfig.get().pickedLocationZoom - 1);
                }
            }
        });
    }

    private void pickDate() {
        long todayUtc = MaterialDatePicker.todayInUtcMilliseconds();
        MaterialDatePicker<Long> dp = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.hint_departure_date)
                .setSelection(todayUtc)
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.from(todayUtc)).build())
                .build();
        dp.addOnPositiveButtonClickListener(sel -> {
            pendingDateUtc = sel;
            pickTime();
        });
        dp.show(getChildFragmentManager(), "date");
    }

    private void pickTime() {
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MINUTE, 30);
        MaterialTimePicker tp = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(requireContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .setTitleText(R.string.hint_departure_time)
                .build();
        tp.addOnPositiveButtonClickListener(v -> {
            if (pendingDateUtc == null) return;
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(pendingDateUtc);
            Calendar local = Calendar.getInstance();
            local.clear();
            local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), tp.getHour(), tp.getMinute(), 0);
            vm.setDeparture(local.getTimeInMillis());
        });
        tp.show(getChildFragmentManager(), "time");
    }

    private static Double parse(CharSequence s) {
        if (s == null || s.toString().trim().isEmpty()) return null;
        try {
            return Double.parseDouble(s.toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private void submit() {
        Ui.hideKeyboard(b.getRoot());
        vm.submit(parse(b.cost.getText()), parse(b.detour.getText()), String.valueOf(b.comment.getText()).trim());
    }

    private void showErrors(Set<RideInputValidator.Error> e) {
        b.pickupLayout.setError(e.contains(RideInputValidator.Error.PICKUP_REQUIRED) ? getString(R.string.error_pickup_required) : null);
        String dropErr = e.contains(RideInputValidator.Error.DROP_REQUIRED) ? getString(R.string.error_drop_required)
                : e.contains(RideInputValidator.Error.SAME_LOCATION) ? getString(R.string.error_same_location) : null;
        b.dropLayout.setError(dropErr);
        String timeErr = e.contains(RideInputValidator.Error.TIME_REQUIRED) ? getString(R.string.error_time_required)
                : e.contains(RideInputValidator.Error.TIME_IN_PAST) ? getString(R.string.error_time_past)
                : e.contains(RideInputValidator.Error.TIME_TOO_FAR) ? getString(R.string.error_time_far) : null;
        b.dateLayout.setError(timeErr);
        b.seatsError.setVisibility(e.contains(RideInputValidator.Error.SEATS_OUT_OF_RANGE) ? View.VISIBLE : View.GONE);
        b.seatsError.setText(R.string.error_seats_range);
        String costErr = e.contains(RideInputValidator.Error.COST_REQUIRED) ? getString(R.string.error_cost_required)
                : e.contains(RideInputValidator.Error.COST_NEGATIVE) ? getString(R.string.error_cost_negative)
                : e.contains(RideInputValidator.Error.COST_TOO_HIGH) ? getString(R.string.error_cost_high) : null;
        b.costLayout.setError(costErr);
        b.detourLayout.setError(e.contains(RideInputValidator.Error.DETOUR_OUT_OF_RANGE) ? getString(R.string.error_detour_range) : null);
        b.commentLayout.setError(e.contains(RideInputValidator.Error.COMMENT_TOO_LONG) ? getString(R.string.error_comment_long) : null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        map = null;
        b = null;
    }
}