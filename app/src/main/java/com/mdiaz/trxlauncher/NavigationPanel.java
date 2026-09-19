package com.mdiaz.trxlauncher;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapColorScheme;
import com.google.android.libraries.navigation.ListenableResultFuture;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.NavigationView;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.Waypoint;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Google-powered, in-launcher turn-by-turn navigation console. */
public class NavigationPanel extends FrameLayout {
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final NavigationView navigationView;
    private final EditText destination;
    private final Button routeButton;
    private final Button stopButton;
    private final LinearLayout suggestions;
    private final TextView status;
    private final TextView modeBadge;
    private final Handler suggestionHandler = new Handler(Looper.getMainLooper());
    private final int accent;
    private Navigator navigator;
    private int suggestionRequest;
    private boolean selectingSuggestion;
    private boolean guiding;
    private boolean mapLoaded;
    private boolean panelStarted;
    private boolean panelResumed;

    public NavigationPanel(MainActivity context, Bundle state) {
        super(context);
        activity = context;
        prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        accent = currentAccent();
        setBackgroundColor(0xff05070a);

        navigationView = new NavigationView(context);
        navigationView.onCreate(state);
        addView(navigationView, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        destination = new EditText(context);
        destination.setHint("Where to?");
        destination.setSingleLine(true);
        destination.setTextColor(Color.WHITE);
        destination.setHintTextColor(0xff9ca1aa);
        destination.setTextSize(17);
        destination.setPadding(dp(22), 0, dp(72), 0);
        destination.setBackground(panel(0xee05070a, 0xff656a74, 2, 18));
        destination.setElevation(dp(10));
        LayoutParams searchLp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(64), Gravity.TOP);
        searchLp.setMargins(dp(16), dp(16), dp(16), 0);
        addView(destination, searchLp);

        suggestions = new LinearLayout(context);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestions.setPadding(dp(7), dp(5), dp(7), dp(7));
        suggestions.setBackground(panel(0xf5080a0d, accent, 1, 16));
        suggestions.setElevation(dp(20));
        suggestions.setVisibility(GONE);
        LayoutParams suggestionLp = new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP);
        suggestionLp.setMargins(dp(16), dp(88), dp(16), 0);
        addView(suggestions, suggestionLp);

        destination.setImeOptions(EditorInfo.IME_ACTION_GO);
        destination.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) {
                beginNavigation(destination.getText().toString());
                return true;
            }
            return false;
        });
        destination.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                if (!selectingSuggestion) scheduleAddressSuggestions(value.toString());
            }
        });
        destination.setOnFocusChangeListener((v, focused) -> {
            if (!focused) suggestionHandler.postDelayed(() -> suggestions.setVisibility(GONE), 180);
        });

        routeButton = button("➤", true);
        routeButton.setContentDescription("Start navigation");
        LayoutParams routeLp = new LayoutParams(dp(54), dp(54), Gravity.TOP | Gravity.RIGHT);
        routeLp.setMargins(0, dp(21), dp(21), 0);
        addView(routeButton, routeLp);
        routeButton.setOnClickListener(v -> beginNavigation(destination.getText().toString()));

        status = new TextView(context);
        status.setText("CONNECTING TO GOOGLE NAVIGATION…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackground(panel(0xf2080a0d, accent, 1, 14));
        status.setElevation(dp(12));
        LayoutParams statusLp = new LayoutParams(dp(330), dp(48), Gravity.CENTER);
        addView(status, statusLp);

        Button home = button("⌂  HOME", true);
        home.setTextSize(14);
        LayoutParams homeLp = new LayoutParams(dp(150), dp(56), Gravity.LEFT | Gravity.BOTTOM);
        homeLp.setMargins(dp(16), 0, 0, dp(16));
        addView(home, homeLp);
        home.setOnClickListener(v -> beginNavigation(prefs.getString("home_destination", "Home")));

        modeBadge = new TextView(context);
        modeBadge.setText("GOOGLE LIVE TRAFFIC  •  PINCH TO ZOOM");
        modeBadge.setTextColor(0xffd0d3d8);
        modeBadge.setTextSize(11);
        modeBadge.setGravity(Gravity.CENTER);
        modeBadge.setBackground(panel(0xe80a0c10, 0xff656a74, 1, 18));
        LayoutParams liveLp = new LayoutParams(dp(270), dp(42), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        liveLp.setMargins(0, 0, 0, dp(22));
        addView(modeBadge, liveLp);

        stopButton = button("■  END", false);
        stopButton.setTextSize(13);
        stopButton.setTextColor(0xffff7883);
        stopButton.setVisibility(GONE);
        LayoutParams stopLp = new LayoutParams(dp(120), dp(56), Gravity.RIGHT | Gravity.BOTTOM);
        stopLp.setMargins(0, 0, dp(16), dp(16));
        addView(stopButton, stopLp);
        stopButton.setOnClickListener(v -> stopGuidance());

        initializeMap();
        initializeNavigator();
    }

    private void initializeMap() {
        navigationView.getMapAsync(map -> {
            map.getUiSettings().setZoomGesturesEnabled(true);
            map.getUiSettings().setScrollGesturesEnabled(true);
            map.getUiSettings().setRotateGesturesEnabled(true);
            map.getUiSettings().setCompassEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            map.setTrafficEnabled(true);
            try { map.setMapColorScheme(MapColorScheme.DARK); }
            catch (Throwable ignored) { }
            LatLng start = new LatLng(40.3323, -74.5819);
            try {
                if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                    map.setMyLocationEnabled(true);
                    LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
                    Location last = manager == null ? null : manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (last == null && manager != null)
                        last = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (last != null) start = new LatLng(last.getLatitude(), last.getLongitude());
                }
            } catch (Throwable ignored) { }
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 14.2f));
            map.setOnMapLoadedCallback(() -> {
                mapLoaded = true;
                if (navigator != null && !guiding) status.setVisibility(GONE);
            });
            status.postDelayed(() -> {
                if (!mapLoaded && !guiding) {
                    status.setVisibility(VISIBLE);
                    status.setText("BASEMAP NOT LOADED • CHECK NETWORK + GOOGLE KEY");
                }
            }, 9000);
        });
    }

    private void initializeNavigator() {
        NavigationApi.getNavigator(activity, new NavigationApi.NavigatorListener() {
            @Override public void onNavigatorReady(Navigator ready) {
                navigator = ready;
                navigationView.setNavigationUiEnabled(true);
                navigationView.setHeaderEnabled(true);
                navigationView.setEtaCardEnabled(true);
                navigationView.setRecenterButtonEnabled(true);
                navigationView.setSpeedometerEnabled(true);
                navigationView.setSpeedLimitIconEnabled(true);
                status.setText("GOOGLE NAVIGATION READY • LOADING BASEMAP…");
                if (mapLoaded && !guiding) status.setVisibility(GONE);
            }

            @Override public void onError(int errorCode) {
                status.setVisibility(VISIBLE);
                if (errorCode == NavigationApi.ErrorCode.NOT_AUTHORIZED)
                    status.setText("GOOGLE KEY NOT AUTHORIZED • ENABLE NAVIGATION SDK");
                else if (errorCode == NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED)
                    status.setText("GOOGLE NAVIGATION TERMS MUST BE ACCEPTED");
                else if (errorCode == NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING)
                    status.setText("LOCATION PERMISSION REQUIRED FOR NAVIGATION");
                else status.setText("GOOGLE NAVIGATION ERROR • " + errorCode);
            }
        });
    }

    private void beginNavigation(String rawDestination) {
        String query = rawDestination == null ? "" : rawDestination.trim();
        if (query.isEmpty()) {
            Toast.makeText(activity, "Enter a destination", Toast.LENGTH_SHORT).show();
            return;
        }
        if (navigator == null) {
            Toast.makeText(activity, "Google Navigation is still connecting", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("Home".equalsIgnoreCase(query)) query = prefs.getString("home_destination", "Home");
        if ("Work".equalsIgnoreCase(query)) query = prefs.getString("work_destination", "Work");
        final String address = query;
        hideKeyboard();
        suggestions.setVisibility(GONE);
        status.setText("BUILDING ROUTE TO " + address.toUpperCase(Locale.US) + "…");
        status.setVisibility(VISIBLE);
        new Thread(() -> {
            try {
                List<Address> matches = new Geocoder(activity, Locale.US).getFromLocationName(address, 1);
                if (matches == null || matches.isEmpty()) throw new IllegalArgumentException("Destination not found");
                Address found = matches.get(0);
                Waypoint waypoint = new Waypoint.Builder()
                    .setLatLng(found.getLatitude(), found.getLongitude())
                    .setTitle(address)
                    .setVehicleStopover(true)
                    .build();
                RoutingOptions options = new RoutingOptions();
                options.travelMode(RoutingOptions.TravelMode.DRIVING);
                activity.runOnUiThread(() -> {
                    ListenableResultFuture<Navigator.RouteStatus> route =
                        navigator.setDestination(waypoint, options);
                    route.setOnResultListener(routeStatus -> {
                        if (routeStatus == Navigator.RouteStatus.OK) {
                            navigator.startGuidance();
                            guiding = true;
                            rememberDestination(address);
                            destination.setVisibility(GONE);
                            routeButton.setVisibility(GONE);
                            stopButton.setVisibility(VISIBLE);
                            modeBadge.setText("LIVE TRAFFIC  •  VOICE GUIDANCE ACTIVE");
                            status.setVisibility(GONE);
                        } else {
                            status.setVisibility(VISIBLE);
                            status.setText("ROUTE UNAVAILABLE • " + routeStatus);
                        }
                    });
                });
            } catch (Throwable error) {
                activity.runOnUiThread(() -> {
                    status.setVisibility(VISIBLE);
                    status.setText("DESTINATION NOT FOUND • TRY A FULL ADDRESS");
                });
            }
        }, "trx-google-route").start();
    }

    private void stopGuidance() {
        try {
            if (navigator != null) {
                navigator.stopGuidance();
                navigator.clearDestinations();
            }
        } catch (Throwable ignored) { }
        guiding = false;
        destination.setVisibility(VISIBLE);
        routeButton.setVisibility(VISIBLE);
        stopButton.setVisibility(GONE);
        modeBadge.setText("GOOGLE LIVE TRAFFIC  •  PINCH TO ZOOM");
        Toast.makeText(activity, "TRX guidance ended", Toast.LENGTH_SHORT).show();
    }

    public boolean closeChooser() {
        if (suggestions.getVisibility() == VISIBLE) {
            suggestions.setVisibility(GONE);
            return true;
        }
        if (guiding) {
            stopGuidance();
            return true;
        }
        return false;
    }

    private void scheduleAddressSuggestions(String raw) {
        final String query = raw == null ? "" : raw.trim();
        final int request = ++suggestionRequest;
        if (query.length() < 3) {
            suggestions.removeAllViews();
            suggestions.setVisibility(GONE);
            return;
        }
        suggestionHandler.postDelayed(() -> {
            if (request != suggestionRequest || !destination.hasFocus()) return;
            new Thread(() -> loadAddressSuggestions(query, request), "trx-address-search").start();
        }, 300);
    }

    private void loadAddressSuggestions(String query, int request) {
        final java.util.List<Address> found = new java.util.ArrayList<>();
        try {
            if (Geocoder.isPresent()) {
                List<Address> matches = new Geocoder(activity, Locale.US)
                    .getFromLocationName(query, 4);
                if (matches != null) found.addAll(matches);
            }
        } catch (Throwable ignored) { }
        activity.runOnUiThread(() -> renderAddressSuggestions(found, request));
    }

    private void renderAddressSuggestions(List<Address> found, int request) {
        if (request != suggestionRequest || !destination.hasFocus()) return;
        suggestions.removeAllViews();
        if (found == null || found.isEmpty()) {
            suggestions.setVisibility(GONE);
            return;
        }

        TextView heading = new TextView(activity);
        heading.setText("SUGGESTED DESTINATIONS");
        heading.setTextColor(accent);
        heading.setTextSize(10);
        heading.setLetterSpacing(.14f);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), 0, dp(10), 0);
        suggestions.addView(heading, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(28)));

        HashSet<String> added = new HashSet<>();
        for (Address address : found) {
            String full = address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : null;
            if (TextUtils.isEmpty(full)) {
                StringBuilder built = new StringBuilder();
                if (!TextUtils.isEmpty(address.getThoroughfare())) built.append(address.getThoroughfare());
                if (!TextUtils.isEmpty(address.getLocality())) {
                    if (built.length() > 0) built.append(", ");
                    built.append(address.getLocality());
                }
                if (!TextUtils.isEmpty(address.getAdminArea())) {
                    if (built.length() > 0) built.append(", ");
                    built.append(address.getAdminArea());
                }
                full = built.toString();
            }
            if (TextUtils.isEmpty(full) || !added.add(full)) continue;
            String primary = address.getFeatureName();
            if (TextUtils.isEmpty(primary)) primary = address.getThoroughfare();
            if (TextUtils.isEmpty(primary)) primary = address.getLocality();
            if (TextUtils.isEmpty(primary)) primary = full;
            addAddressSuggestion(primary, full);
        }
        if (suggestions.getChildCount() <= 1) {
            suggestions.removeAllViews();
            suggestions.setVisibility(GONE);
        } else {
            suggestions.setVisibility(VISIBLE);
            suggestions.bringToFront();
        }
    }

    private void addAddressSuggestion(String primary, String full) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(10), 0);
        row.setBackground(panel(0xff090b0e, 0xff292d33, 1, 11));

        TextView marker = new TextView(activity);
        marker.setText("●");
        marker.setTextColor(accent);
        marker.setTextSize(13);
        marker.setGravity(Gravity.CENTER);
        row.addView(marker, new LinearLayout.LayoutParams(dp(34), dp(54)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(activity);
        name.setText(primary.toUpperCase(Locale.US));
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView address = new TextView(activity);
        address.setText(full);
        address.setTextColor(0xffaeb3bc);
        address.setTextSize(11);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(24)));
        copy.addView(address, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(20)));
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(50), 1));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56));
        rowLp.setMargins(0, dp(2), 0, dp(2));
        suggestions.addView(row, rowLp);
        row.setOnClickListener(v -> {
            selectingSuggestion = true;
            suggestionRequest++;
            destination.setText(full);
            destination.setSelection(destination.length());
            selectingSuggestion = false;
            suggestions.removeAllViews();
            suggestions.setVisibility(GONE);
            hideKeyboard();
            destination.clearFocus();
            beginNavigation(full);
        });
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) activity
            .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(destination.getWindowToken(), 0);
    }

    private void rememberDestination(String value) {
        if (value == null || value.trim().isEmpty()) return;
        String selected = value.trim();
        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        ordered.add(selected);
        String old = prefs.getString("recent_destinations", "");
        if (old != null) for (String item : old.split("\\n"))
            if (!item.trim().isEmpty()) ordered.add(item.trim());
        StringBuilder saved = new StringBuilder();
        int count = 0;
        for (String item : ordered) {
            if (count++ >= 3) break;
            if (saved.length() > 0) saved.append('\n');
            saved.append(item);
        }
        prefs.edit().putString("recent_destinations", saved.toString()).apply();
    }

    public void onStartPanel() {
        if (!panelStarted) { navigationView.onStart(); panelStarted = true; }
    }
    public void onResumePanel() {
        onStartPanel();
        if (!panelResumed) { navigationView.onResume(); panelResumed = true; }
    }
    public void onPausePanel() {
        if (panelResumed) { navigationView.onPause(); panelResumed = false; }
    }
    public void onStopPanel() {
        onPausePanel();
        if (panelStarted) { navigationView.onStop(); panelStarted = false; }
    }
    public void onShownPanel() {
        onResumePanel();
        navigationView.requestLayout();
        navigationView.invalidate();
    }
    public void onHiddenPanel() { onPausePanel(); }
    public void onConfigurationChangedPanel(android.content.res.Configuration config) {
        navigationView.onConfigurationChanged(config);
    }
    public void onLowMemoryPanel() { navigationView.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW); }
    public void onSaveInstanceStatePanel(Bundle out) { navigationView.onSaveInstanceState(out); }
    public void onDestroyPanel() {
        suggestionHandler.removeCallbacksAndMessages(null);
        navigationView.onDestroy();
    }

    private Button button(String text, boolean selected) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(19);
        button.setGravity(Gravity.CENTER);
        button.setBackground(panel(0xff050608, selected ? accent : 0xff5f646d, selected ? 2 : 1, 16));
        button.setElevation(dp(selected ? 9 : 6));
        return button;
    }

    private GradientDrawable panel(int color, int stroke, int width, int radius) {
        int top = color == 0xff050608 ? 0xff24272c : 0xff171a1f;
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{top, color, 0xff030405});
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(width), stroke);
        return drawable;
    }

    private int currentAccent() {
        int theme = prefs.getInt("theme_choice", 1);
        if (theme == 1) return 0xffff9f1a;
        if (theme == 2) return 0xffd9dde3;
        if (theme == 3) return 0xff438cff;
        if (theme == 4) return prefs.getInt("custom_accent", 0xffff2338);
        return 0xffff2338;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
