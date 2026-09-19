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
import android.util.Log;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.navigation.ListenableResultFuture;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.NavigationView;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.Waypoint;
import com.google.android.libraries.navigation.StylingOptions;
import com.google.android.gms.maps.model.FollowMyLocationOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Google-powered, in-launcher turn-by-turn navigation console. */
public class NavigationPanel extends FrameLayout {
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final NavigationView navigationView;
    private final Bundle initialState;
    private final EditText destination;
    private final Button routeButton;
    private final Button stopButton;
    private final LinearLayout suggestions;
    private final ScrollView suggestionScroller;
    private final LinearLayout commandBar;
    private final LinearLayout mapTools;
    private final TextView status;
    private final TextView modeBadge;
    private final Handler suggestionHandler = new Handler(Looper.getMainLooper());
    private int accent;
    private PlacesClient places;
    private AutocompleteSessionToken placesSession;
    private final java.util.Map<String,String> placeIds=new java.util.HashMap<>();
    private Navigator navigator;
    private GoogleMap googleMap;
    private boolean trafficEnabled = true;
    private boolean satelliteEnabled;
    private int suggestionRequest;
    private boolean selectingSuggestion;
    private boolean guiding;
    private boolean mapLoaded;
    private boolean panelStarted;
    private boolean panelResumed;
    private boolean activityStarted;
    private boolean activityResumed;
    private boolean panelVisible;
    private boolean initialized;
    private boolean navigatorRequested;
    private boolean mapRequested;
    private int mapAttempt;

    public NavigationPanel(MainActivity context, Bundle state) {
        super(context);
        activity = context;
        prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        initialState = state == null ? null : new Bundle(state);
        accent = currentAccent();
        setBackgroundColor(0xff05070a);

        navigationView = new NavigationView(context);
        // Match the last Google build that rendered successfully: create the
        // NavigationView immediately, before either asynchronous SDK request.
        navigationView.onCreate(initialState);
        initialized = true;
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
        suggestionScroller = new ScrollView(context);
        suggestionScroller.setFillViewport(true);
        suggestionScroller.setClipToPadding(false);
        suggestionScroller.setElevation(dp(26));
        suggestionScroller.setVisibility(GONE);
        suggestionScroller.addView(suggestions, new ScrollView.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams suggestionLp = new LayoutParams(
            LayoutParams.MATCH_PARENT, dp(340), Gravity.TOP);
        suggestionLp.setMargins(dp(16), dp(88), dp(16), 0);
        addView(suggestionScroller, suggestionLp);

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
            if (!focused) suggestionHandler.postDelayed(() -> suggestionScroller.setVisibility(GONE), 220);
        });

        routeButton = button("➤", true);
        routeButton.setContentDescription("Start navigation");
        LayoutParams routeLp = new LayoutParams(dp(54), dp(54), Gravity.TOP | Gravity.RIGHT);
        routeLp.setMargins(0, dp(21), dp(21), 0);
        addView(routeButton, routeLp);
        routeButton.setOnClickListener(v -> beginNavigation(destination.getText().toString()));

        commandBar = new LinearLayout(context);
        commandBar.setOrientation(LinearLayout.HORIZONTAL);
        commandBar.setGravity(Gravity.CENTER);
        commandBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        commandBar.setBackground(panel(0xe807090c, 0xff3f454e, 1, 16));
        commandBar.setElevation(dp(8));
        addCommand("⌂  HOME", () -> beginNavigation("Home"));
        addCommand("▣  WORK", () -> beginNavigation("Work"));
        addCommand("↻  RECENT", this::showRecentDestinations);
        LayoutParams commandLp = new LayoutParams(dp(390), dp(54), Gravity.LEFT | Gravity.TOP);
        commandLp.setMargins(dp(16), dp(92), 0, 0);
        addView(commandBar, commandLp);

        mapTools = new LinearLayout(context);
        mapTools.setOrientation(LinearLayout.VERTICAL);
        mapTools.setGravity(Gravity.CENTER);
        mapTools.setPadding(dp(5), dp(5), dp(5), dp(5));
        mapTools.setBackground(panel(0xe807090c, 0xff4e545e, 1, 16));
        mapTools.setElevation(dp(9));
        addMapTool("＋", () -> zoomBy(1f), "Zoom in");
        addMapTool("−", () -> zoomBy(-1f), "Zoom out");
        addMapTool("◎", this::recenterMap, "Recenter map");
        addMapTool("T", this::toggleTraffic, "Toggle live traffic");
        addMapTool("L", this::toggleMapLayer, "Toggle map layer");
        LayoutParams toolsLp = new LayoutParams(dp(58), dp(280), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        toolsLp.setMargins(0, 0, dp(16), 0);
        addView(mapTools, toolsLp);

        status = new TextView(context);
        status.setText("CONNECTING TO GOOGLE NAVIGATION…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackground(panel(0xf2080a0d, accent, 1, 14));
        status.setElevation(dp(12));
        status.setOnClickListener(v -> {
            if (!mapLoaded) retryMap();
        });
        status.setOnLongClickListener(v -> {
            if (!mapLoaded) {
                openGoogleMapsFallback();
                return true;
            }
            return false;
        });
        LayoutParams statusLp = new LayoutParams(dp(330), dp(48), Gravity.CENTER);
        addView(status, statusLp);

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

        // Rendering and Navigator authorization are intentionally independent.
        // A Navigator delay or authorization response must never block basemap tiles.
        navigationView.post(this::initializeMap);
        navigationView.post(this::initializeNavigator);
    }

    /**
     * NavigationView owns a SurfaceView. Automotive Android boxes can create a
     * permanently blank surface when the SDK is asked for a map before the
     * view is attached, measured, started and resumed. Wait for a real surface,
     * authorize Navigation first, then request the map.
     */
    public void onCreatePanel() {
        if (initialized) return;
        try {
            initialized = true;
            navigationView.onCreate(initialState);
        } catch (Throwable error) {
            initialized = false;
            showInitializationError("VIEW", error);
        }
    }

    private void ensureInitialized() {
        onCreatePanel();
        if (!initialized) return;
        if (!isAttachedToWindow() || getWidth() == 0 || getHeight() == 0
            || navigationView.getWidth() == 0 || navigationView.getHeight() == 0) {
            status.setText("WAITING FOR MAP SURFACE…");
            postDelayed(this::ensureInitialized, 50);
            return;
        }
        if (activityStarted) startView();
        if (activityResumed) resumeView();
        navigationView.post(this::initializeMap);
        navigationView.post(this::initializeNavigator);
    }

    private void initializeNavigator() {
        if (navigator != null) return;
        if (navigatorRequested) return;
        navigatorRequested = true;
        status.setVisibility(VISIBLE);
        status.setText("AUTHORIZING GOOGLE NAVIGATION…");

        try {
            NavigationApi.getNavigator(activity, new NavigationApi.NavigatorListener() {
                @Override public void onNavigatorReady(Navigator ready) {
                    navigatorRequested = false;
                    navigator = ready;
                    try {
                        navigationView.setNavigationUiEnabled(true);
                        navigationView.setHeaderEnabled(true);
                        navigationView.setEtaCardEnabled(true);
                        navigationView.setRecenterButtonEnabled(true);
                        navigationView.setSpeedometerEnabled(true);
                        navigationView.setSpeedLimitIconEnabled(true);
                        if (mapLoaded) {
                            status.setText("GOOGLE MAP READY");
                            if (!guiding) status.postDelayed(() -> status.setVisibility(GONE), 600);
                        } else {
                            status.setText("GOOGLE AUTHORIZED • MAP RENDERER CONNECTING…");
                        }
                    } catch (Throwable error) {
                        showInitializationError("NAV VIEW", error);
                    }
                }

                @Override public void onError(int errorCode) {
                    navigatorRequested = false;
                    status.setVisibility(VISIBLE);
                    if (errorCode == NavigationApi.ErrorCode.NOT_AUTHORIZED)
                        status.setText("GOOGLE KEY NOT AUTHORIZED • CODE " + errorCode);
                    else if (errorCode == NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED)
                        status.setText("GOOGLE NAVIGATION TERMS NOT ACCEPTED • CODE " + errorCode);
                    else if (errorCode == NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING)
                        status.setText("LOCATION PERMISSION REQUIRED • CODE " + errorCode);
                    else status.setText("GOOGLE NAVIGATION ERROR • CODE " + errorCode);
                    Log.e("TRXNavigation", "NavigationApi error code " + errorCode);
                }
            });
        } catch (Throwable error) {
            navigatorRequested = false;
            showInitializationError("NAV API", error);
        }
    }

    private void initializeMap() {
        if (!initialized || mapRequested || mapLoaded) return;
        mapRequested = true;
        final int attempt = ++mapAttempt;
        status.setVisibility(VISIBLE);
        status.setText("REQUESTING GOOGLE MAP RENDERER • ATTEMPT " + attempt);

        try {
            navigationView.getMapAsync(map -> {
                try {
                    googleMap = map;
                    map.getUiSettings().setZoomGesturesEnabled(true);
                    map.getUiSettings().setScrollGesturesEnabled(true);
                    map.getUiSettings().setRotateGesturesEnabled(true);
                    map.getUiSettings().setCompassEnabled(true);
                    map.getUiSettings().setMyLocationButtonEnabled(true);
                    map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    try { map.setMapStyle(MapStyleOptions.loadRawResourceStyle(activity, R.raw.map_dark)); }
                    catch (Throwable ignored) { }
                    map.setTrafficEnabled(trafficEnabled);

                    LatLng start = new LatLng(40.3323, -74.5819);
                    try {
                        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                            map.setMyLocationEnabled(true);
                            LocationManager manager = (LocationManager) activity
                                .getSystemService(Context.LOCATION_SERVICE);
                            Location last = manager == null ? null
                                : manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (last == null && manager != null)
                                last = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            if (last != null)
                                start = new LatLng(last.getLatitude(), last.getLongitude());
                        }
                    } catch (Throwable locationError) {
                        Log.w("TRXNavigation", "Location setup failed", locationError);
                    }

                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 14.2f));
                    status.setText("MAP CONNECTED • LOADING BASEMAP TILES…");
                    map.setOnMapLoadedCallback(() -> {
                        if (attempt != mapAttempt) return;
                        mapLoaded = true;
                        mapRequested = false;
                        status.setText("GOOGLE MAP READY");
                        if (!guiding) status.postDelayed(() -> {
                            if (mapLoaded && !guiding) status.setVisibility(GONE);
                        }, 800);
                    });
                } catch (Throwable error) {
                    mapRequested = false;
                    showInitializationError("MAP SETUP", error);
                }
            });
        } catch (Throwable error) {
            mapRequested = false;
            showInitializationError("GET MAP", error);
            return;
        }

        status.postDelayed(() -> {
            if (attempt == mapAttempt && !mapLoaded && !guiding) {
                mapRequested = false;
                status.setVisibility(VISIBLE);
                status.setText("MAP RENDERER TIMEOUT • TAP TO RETRY • HOLD FOR GOOGLE MAPS");
                Log.e("TRXNavigation", "Map renderer timed out on attempt " + attempt
                    + "; view=" + navigationView.getWidth() + "x" + navigationView.getHeight()
                    + "; attached=" + navigationView.isAttachedToWindow());
            }
        }, 30000);
    }

    private void retryMap() {
        if (mapLoaded) return;
        mapRequested = false;
        mapAttempt++;
        status.setVisibility(VISIBLE);
        status.setText("RESTARTING MAP RENDERER…");
        try {
            navigationView.requestLayout();
            navigationView.invalidate();
            navigationView.postDelayed(() -> {
                if (activityStarted) startView();
                if (activityResumed) resumeView();
                initializeMap();
                if (navigator == null) initializeNavigator();
            }, 350);
        } catch (Throwable error) {
            showInitializationError("RETRY", error);
        }
    }

    private void showInitializationError(String stage, Throwable error) {
        mapRequested = false;
        String name = error == null ? "UNKNOWN" : error.getClass().getSimpleName();
        String detail = error == null ? "" : error.getMessage();
        if (detail == null || detail.trim().isEmpty()) detail = name;
        detail = detail.replace('\n', ' ').trim();
        if (detail.length() > 48) detail = detail.substring(0, 48);
        status.setVisibility(VISIBLE);
        status.setText(stage + " ERROR • " + detail.toUpperCase(Locale.US));
        Log.e("TRXNavigation", stage + " initialization failure", error);
    }

    private void openGoogleMapsFallback() {
        try {
            android.content.Intent launch = activity.getPackageManager()
                .getLaunchIntentForPackage("com.google.android.apps.maps");
            if (launch != null) {
                activity.startActivity(launch);
            } else {
                activity.startActivity(new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.google.com/maps")));
            }
        } catch (Throwable error) {
            Toast.makeText(activity, "Google Maps is unavailable", Toast.LENGTH_LONG).show();
        }
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
        final String placeId=placeIds.get(address);
        hideKeyboard();
        suggestionScroller.setVisibility(GONE);
        status.setText("BUILDING ROUTE TO " + address.toUpperCase(Locale.US) + "…");
        status.setVisibility(VISIBLE);
        new Thread(() -> {
            try {
                Waypoint waypoint;
                if(placeId!=null)waypoint=new Waypoint.Builder().setPlaceIdString(placeId).setTitle(address).setVehicleStopover(true).build();
                else{
                    List<Address> matches=new Geocoder(activity,Locale.US).getFromLocationName(address,1);
                    if(matches==null||matches.isEmpty())throw new IllegalArgumentException("Destination not found");
                    Address found=matches.get(0);
                    waypoint=new Waypoint.Builder().setLatLng(found.getLatitude(),found.getLongitude()).setTitle(address).setVehicleStopover(true).build();
                }
                RoutingOptions options = new RoutingOptions();
                options.travelMode(RoutingOptions.TravelMode.DRIVING);
                activity.runOnUiThread(() -> {
                    ListenableResultFuture<Navigator.RouteStatus> route =
                        navigator.setDestination(waypoint, options);
                    route.setOnResultListener(routeStatus -> {
                        if (routeStatus == Navigator.RouteStatus.OK) {
                            navigator.startGuidance();
                            guiding = true;
                            followRoad();
                            placesSession=null;
                            rememberDestination(address);
                            destination.setVisibility(GONE);
                            routeButton.setVisibility(GONE);
                            commandBar.setVisibility(GONE);
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
        commandBar.setVisibility(VISIBLE);
        stopButton.setVisibility(GONE);
        modeBadge.setText("GOOGLE LIVE TRAFFIC  •  PINCH TO ZOOM");
        Toast.makeText(activity, "TRX guidance ended", Toast.LENGTH_SHORT).show();
    }

    public boolean closeChooser() {
        if (suggestionScroller.getVisibility() == VISIBLE) {
            suggestionScroller.setVisibility(GONE);
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
            suggestionScroller.setVisibility(GONE);
            return;
        }
        suggestionHandler.postDelayed(() -> {
            if (request != suggestionRequest || !destination.hasFocus()) return;
            loadGoogleSuggestions(query,request);
        }, 300);
    }

    private void loadGoogleSuggestions(String query,int request){
        try{
            if(places==null){
                if(!Places.isInitialized())Places.initializeWithNewPlacesApiEnabled(activity.getApplicationContext(),BuildConfig.MAPS_API_KEY);
                places=Places.createClient(activity);
            }
            if(placesSession==null)placesSession=AutocompleteSessionToken.newInstance();
            places.findAutocompletePredictions(FindAutocompletePredictionsRequest.builder().setQuery(query).setSessionToken(placesSession).build())
                .addOnSuccessListener(response->{
                    if(request!=suggestionRequest)return;
                    suggestions.removeAllViews();placeIds.clear();
                    for(com.google.android.libraries.places.api.model.AutocompletePrediction item:response.getAutocompletePredictions()){
                        String full=item.getFullText(null).toString();placeIds.put(full,item.getPlaceId());
                        addAddressSuggestion(item.getPrimaryText(null).toString(),full);
                    }
                    TextView attribution=new TextView(activity);attribution.setText("Google Maps");attribution.setTextColor(Color.WHITE);attribution.setPadding(dp(14),dp(8),dp(14),dp(8));suggestions.addView(attribution);
                    suggestionScroller.setVisibility(VISIBLE);suggestionScroller.bringToFront();
                }).addOnFailureListener(error->{
                    if(request!=suggestionRequest)return;
                    status.setText("GOOGLE PLACES UNAVAILABLE • CHECK API ACCESS / CONNECTION");status.setVisibility(VISIBLE);
                });
        }catch(RuntimeException error){status.setText("GOOGLE PLACES SETUP REQUIRED");status.setVisibility(VISIBLE);}
    }

    private void followRoad(){
        if(googleMap!=null)googleMap.followMyLocation(GoogleMap.CameraPerspective.TILTED,
            FollowMyLocationOptions.builder().setZoomLevel(18f).build());
    }

    public void applyTheme(){
        accent=currentAccent();
        navigationView.setStylingOptions(new StylingOptions()
            .primaryDayModeThemeColor(0xff151519).primaryNightModeThemeColor(0xff09090c)
            .secondaryDayModeThemeColor(0xff420a12).secondaryNightModeThemeColor(0xff420a12)
            .headerLargeManeuverIconColor(accent).headerSmallManeuverIconColor(accent)
            .headerInstructionsTextColor(Color.WHITE).headerDistanceValueTextColor(Color.WHITE)
            .headerDistanceUnitsTextColor(Color.WHITE).headerNextStepTextColor(Color.WHITE)
            .headerGuidanceRecommendedLaneColor(accent));
        destination.setBackground(panel(0xee05070a,accent,2,18));
        routeButton.setBackground(panel(0xff420a12,accent,2,14));
    }

    private void loadAddressSuggestions(String query, int request) {
        final java.util.List<Address> found = new java.util.ArrayList<>();
        try {
            if (Geocoder.isPresent()) {
                List<Address> matches = new Geocoder(activity, Locale.US)
                    .getFromLocationName(query, 8);
                if (matches != null) found.addAll(matches);
            }
        } catch (Throwable ignored) { }
        activity.runOnUiThread(() -> renderAddressSuggestions(found, request));
    }

    private void renderAddressSuggestions(List<Address> found, int request) {
        if (request != suggestionRequest || !destination.hasFocus()) return;
        suggestions.removeAllViews();
        if (found == null || found.isEmpty()) {
            suggestionScroller.setVisibility(GONE);
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
            suggestionScroller.setVisibility(GONE);
        } else {
            suggestionScroller.setVisibility(VISIBLE);
            suggestionScroller.bringToFront();
            suggestionScroller.scrollTo(0, 0);
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
        row.addView(marker, new LinearLayout.LayoutParams(dp(34), dp(68)));

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
        address.setSingleLine(false);
        address.setMaxLines(2);
        address.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(25)));
        copy.addView(address, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(36)));
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(64), 1));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(70));
        rowLp.setMargins(0, dp(2), 0, dp(2));
        suggestions.addView(row, rowLp);
        row.setOnClickListener(v -> {
            selectingSuggestion = true;
            suggestionRequest++;
            destination.setText(full);
            destination.setSelection(destination.length());
            selectingSuggestion = false;
            suggestions.removeAllViews();
            suggestionScroller.setVisibility(GONE);
            hideKeyboard();
            destination.clearFocus();
            beginNavigation(full);
        });
    }

    private void addCommand(String label, Runnable action) {
        Button control = button(label, false);
        control.setTextSize(11);
        control.setMinWidth(0);
        control.setMinimumWidth(0);
        control.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        commandBar.addView(control, lp);
        control.setOnClickListener(v -> action.run());
    }

    private void addMapTool(String label, Runnable action, String description) {
        Button control = button(label, false);
        control.setTextSize(19);
        control.setContentDescription(description);
        control.setMinWidth(0);
        control.setMinimumWidth(0);
        control.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), 0, 1);
        lp.setMargins(0, dp(2), 0, dp(2));
        mapTools.addView(control, lp);
        control.setOnClickListener(v -> action.run());
    }

    private void zoomBy(float amount) {
        if (googleMap != null) googleMap.animateCamera(CameraUpdateFactory.zoomBy(amount));
    }

    private void recenterMap() {
        if (googleMap == null) return;
        if (guiding) {
            followRoad();
            return;
        }
        try {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "Location permission is required", Toast.LENGTH_SHORT).show();
                return;
            }
            LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
            Location last = manager == null ? null : manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null && manager != null)
                last = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(last.getLatitude(), last.getLongitude()), 15.4f));
        } catch (Throwable error) {
            Toast.makeText(activity, "Current location is not available yet", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTraffic() {
        trafficEnabled = !trafficEnabled;
        if (googleMap != null) googleMap.setTrafficEnabled(trafficEnabled);
        modeBadge.setText(trafficEnabled
            ? "GOOGLE LIVE TRAFFIC  •  PINCH TO ZOOM"
            : "TRAFFIC OFF  •  PINCH TO ZOOM");
        Toast.makeText(activity, trafficEnabled ? "Live traffic on" : "Live traffic off", Toast.LENGTH_SHORT).show();
    }

    private void toggleMapLayer() {
        satelliteEnabled = !satelliteEnabled;
        if (googleMap != null) {
            googleMap.setMapType(satelliteEnabled ? GoogleMap.MAP_TYPE_HYBRID : GoogleMap.MAP_TYPE_NORMAL);
            if (!satelliteEnabled) try {
                googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(activity, R.raw.map_dark));
            } catch (Throwable ignored) { }
        }
        Toast.makeText(activity, satelliteEnabled ? "Hybrid map" : "Dark road map", Toast.LENGTH_SHORT).show();
    }

    private void showRecentDestinations() {
        suggestions.removeAllViews();
        TextView heading = new TextView(activity);
        heading.setText("RECENT DESTINATIONS");
        heading.setTextColor(accent);
        heading.setTextSize(10);
        heading.setLetterSpacing(.14f);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), 0, dp(10), 0);
        suggestions.addView(heading, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(30)));
        String raw = prefs.getString("recent_destinations", "");
        int count = 0;
        if (!TextUtils.isEmpty(raw)) for (String value : raw.split("\\n")) {
            if (!TextUtils.isEmpty(value.trim())) {
                addAddressSuggestion(value.trim(), value.trim());
                count++;
            }
        }
        if (count == 0) {
            TextView empty = new TextView(activity);
            empty.setText("Your routed destinations will appear here");
            empty.setTextColor(0xffb6bac2);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(16), 0, dp(16), 0);
            suggestions.addView(empty, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(64)));
        }
        suggestionScroller.setVisibility(VISIBLE);
        suggestionScroller.bringToFront();
        routeButton.bringToFront();
    }

    public void showSection(int tab) {
        hideKeyboard();
        suggestionScroller.setVisibility(GONE);
        if(tab==0)return;
        if(tab==1){showRecentDestinations();return;}
        if(tab==2){
            String[] saved={prefs.getString("home_destination",""),prefs.getString("work_destination","")};
            new android.app.AlertDialog.Builder(activity).setTitle("Saved destinations")
                .setItems(new String[]{"Home: "+saved[0],"Work: "+saved[1],"Edit saved destinations"},(d,i)->{
                    if(i==2)activity.openSettingsScreen();else beginNavigation(saved[i]);
                }).show();return;
        }
        new android.app.AlertDialog.Builder(activity).setTitle("Map options")
            .setItems(new String[]{trafficEnabled?"Turn traffic off":"Turn traffic on",satelliteEnabled?"Road map":"Satellite + labels","Recenter / follow","End guidance"},(d,i)->{
                if(i==0)toggleTraffic();else if(i==1)toggleMapLayer();else if(i==2)recenterMap();else stopGuidance();
            }).show();
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
        activityStarted = true;
        onCreatePanel();
        startView();
    }
    public void onResumePanel() {
        activityResumed = true;
        onCreatePanel();
        startView();
        resumeView();
        if (panelVisible) ensureInitialized();
    }
    public void onPausePanel() {
        activityResumed = false;
        pauseView();
    }
    public void onStopPanel() {
        activityStarted = false;
        activityResumed = false;
        pauseView();
        stopView();
    }
    public void onShownPanel() {
        panelVisible = true;
        ensureInitialized();
        if (activityStarted) startView();
        if (activityResumed) resumeView();
        navigationView.post(() -> {
            navigationView.requestLayout();
            navigationView.invalidate();
        });
    }
    public void onHiddenPanel() {
        // Keep NavigationView started and resumed while the Activity is alive.
        // Repeatedly destroying an automotive SurfaceView between tabs can leave
        // the Google renderer permanently white on some Android boxes.
        panelVisible = false;
    }
    private void startView() {
        if (initialized && !panelStarted) {
            navigationView.onStart();
            panelStarted = true;
        }
    }
    private void resumeView() {
        if (initialized && !panelResumed) {
            navigationView.onResume();
            panelResumed = true;
        }
    }
    private void pauseView() {
        if (initialized && panelResumed) {
            navigationView.onPause();
            panelResumed = false;
        }
    }
    private void stopView() {
        if (initialized && panelStarted) {
            navigationView.onStop();
            panelStarted = false;
        }
    }
    public void onConfigurationChangedPanel(android.content.res.Configuration config) {
        if (initialized) navigationView.onConfigurationChanged(config);
    }
    public void onLowMemoryPanel() { if (initialized) navigationView.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW); }
    public void onSaveInstanceStatePanel(Bundle out) { if (initialized) navigationView.onSaveInstanceState(out); }
    public void onDestroyPanel() {
        suggestionHandler.removeCallbacksAndMessages(null);
        if (initialized) navigationView.onDestroy();
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
