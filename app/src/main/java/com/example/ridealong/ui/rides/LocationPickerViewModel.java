package com.example.ridealong.ui.rides;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.map.GeocodingProvider;
import com.example.ridealong.map.PlaceResult;
import com.example.ridealong.map.ProviderCallback;

import java.util.List;

public class LocationPickerViewModel extends ViewModel {
    public enum SearchState { IDLE, LOADING, EMPTY, FAILED, RESULTS }

    private final GeocodingProvider geocoder = ServiceLocator.get().maps().geocoding();
    private final MutableLiveData<List<PlaceResult>> suggestions = new MutableLiveData<>();
    private final MutableLiveData<SearchState> searchState = new MutableLiveData<>(SearchState.IDLE);
    private final MutableLiveData<PlaceResult> selected = new MutableLiveData<>();
    private final MutableLiveData<Boolean> resolving = new MutableLiveData<>(false);
    private int searchSeq;
    private int reverseSeq;

    public LiveData<List<PlaceResult>> suggestions() { return suggestions; }

    public LiveData<SearchState> searchState() { return searchState; }

    public LiveData<PlaceResult> selected() { return selected; }

    public LiveData<Boolean> resolving() { return resolving; }

    public void search(String query, GeoCoord near) {
        int seq = ++searchSeq;
        if (query == null || query.trim().length() < 3) {
            suggestions.setValue(null);
            searchState.setValue(SearchState.IDLE);
            return;
        }
        searchState.setValue(SearchState.LOADING);
        geocoder.search(query, near, new ProviderCallback<List<PlaceResult>>() {
            @Override
            public void onSuccess(List<PlaceResult> result) {
                if (seq != searchSeq) return; // a newer query superseded this one
                suggestions.setValue(result);
                searchState.setValue(result.isEmpty() ? SearchState.EMPTY : SearchState.RESULTS);
            }

            @Override
            public void onError(Exception e) {
                if (seq != searchSeq) return;
                suggestions.setValue(null);
                searchState.setValue(SearchState.FAILED);
            }
        });
    }

    public void clearSuggestions() {
        searchSeq++;
        suggestions.setValue(null);
        searchState.setValue(SearchState.IDLE);
    }

    /** User picked a search hit: keep its name, no reverse geocode needed. */
    public void select(PlaceResult p) {
        reverseSeq++;
        resolving.setValue(false);
        selected.setValue(p);
    }

    /** Map settled at a new centre: reverse-geocode it (Nominatim is rate-limited inside the provider). */
    public void resolve(GeoCoord center, String fallbackLabel) {
        int seq = ++reverseSeq;
        PlaceResult raw = new PlaceResult(fallbackLabel, fallbackLabel, center.lat, center.lng);
        selected.setValue(raw);
        resolving.setValue(true);
        geocoder.reverse(center, new ProviderCallback<PlaceResult>() {
            @Override
            public void onSuccess(PlaceResult result) {
                if (seq != reverseSeq) return;
                resolving.setValue(false);
                selected.setValue(result);
            }

            @Override
            public void onError(Exception e) {
                if (seq != reverseSeq) return;
                resolving.setValue(false); // keep the coordinate label; picking still works offline
            }
        });
    }
}