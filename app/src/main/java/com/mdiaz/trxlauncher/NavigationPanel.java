package com.mdiaz.trxlauncher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Software-rendered embedded navigation for automotive Android boxes. */
public class NavigationPanel extends FrameLayout implements LocationListener {
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final WebView mapWeb;
    private final EditText destination;
    private final Button routeButton;
    private final Button stopButton;
    private final LinearLayout suggestions;
    private final TextView status;
    private final TextView modeBadge;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int accent;
    private LocationManager locationManager;
    private TextToSpeech speech;
    private Location lastLocation;
    private boolean mapReady;
    private boolean guiding;
    private int suggestionRequest;
    private boolean selectingSuggestion;

    public NavigationPanel(MainActivity context, Bundle ignored) {
        super(context);
        activity = context;
        prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        accent = currentAccent();
        setBackgroundColor(0xff080a0d);

        mapWeb = new WebView(context);
        mapWeb.setBackgroundColor(0xff0b1016);
        // Avoid the broken native GL surface on the Ottocast P3 Pro.
        mapWeb.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        WebSettings settings = mapWeb.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " TRXLauncher/2.1");
        mapWeb.addJavascriptInterface(new MapBridge(), "TRX");
        mapWeb.setWebChromeClient(new WebChromeClient());
        mapWeb.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    status.setVisibility(VISIBLE);
                    status.setText("MAP CONNECTION ERROR • TAP TO RETRY");
                }
            }
        });
        addView(mapWeb, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

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
        LayoutParams suggestionLp = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT, Gravity.TOP);
        suggestionLp.setMargins(dp(16), dp(88), dp(16), 0);
        addView(suggestions, suggestionLp);

        routeButton = button("➤", true);
        routeButton.setContentDescription("Start navigation");
        LayoutParams routeLp = new LayoutParams(dp(54), dp(54), Gravity.TOP | Gravity.RIGHT);
        routeLp.setMargins(0, dp(21), dp(21), 0);
        addView(routeButton, routeLp);

        status = new TextView(context);
        status.setText("STARTING TRX NAVIGATION…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackground(panel(0xf2080a0d, accent, 1, 14));
        status.setElevation(dp(12));
        status.setOnClickListener(v -> reloadMap());
        status.setOnLongClickListener(v -> { openGoogleMapsFallback(); return true; });
        LayoutParams statusLp = new LayoutParams(dp(350), dp(48), Gravity.CENTER);
        addView(status, statusLp);

        Button home = button("⌂  HOME", true);
        home.setTextSize(14);
        LayoutParams homeLp = new LayoutParams(dp(150), dp(56), Gravity.LEFT | Gravity.BOTTOM);
        homeLp.setMargins(dp(16), 0, 0, dp(16));
        addView(home, homeLp);
        home.setOnClickListener(v -> beginNavigation(prefs.getString("home_destination", "Home")));

        modeBadge = new TextView(context);
        modeBadge.setText("TRX LIVE GPS  •  PINCH TO ZOOM");
        modeBadge.setTextColor(0xffd0d3d8);
        modeBadge.setTextSize(11);
        modeBadge.setGravity(Gravity.CENTER);
        modeBadge.setBackground(panel(0xe80a0c10, 0xff656a74, 1, 18));
        LayoutParams liveLp = new LayoutParams(dp(280), dp(42), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        liveLp.setMargins(0, 0, 0, dp(22));
        addView(modeBadge, liveLp);

        stopButton = button("■  END", false);
        stopButton.setTextSize(13);
        stopButton.setTextColor(0xffff7883);
        stopButton.setVisibility(GONE);
        LayoutParams stopLp = new LayoutParams(dp(120), dp(56), Gravity.RIGHT | Gravity.BOTTOM);
        stopLp.setMargins(0, 0, dp(16), dp(16));
        addView(stopButton, stopLp);

        routeButton.setOnClickListener(v -> beginNavigation(destination.getText().toString()));
        stopButton.setOnClickListener(v -> stopGuidance());
        destination.setImeOptions(EditorInfo.IME_ACTION_GO);
        destination.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) { beginNavigation(destination.getText().toString()); return true; }
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
            if (!focused) handler.postDelayed(() -> suggestions.setVisibility(GONE), 180);
        });

        speech = new TextToSpeech(context, result -> {
            if (result == TextToSpeech.SUCCESS) speech.setLanguage(Locale.US);
        });
        reloadMap();
    }

    private void reloadMap() {
        mapReady = false;
        status.setVisibility(VISIBLE);
        status.setText("LOADING TRX MAP…");
        mapWeb.loadDataWithBaseURL("https://trx.local/", mapHtml(), "text/html", "UTF-8", null);
        handler.postDelayed(() -> {
            if (!mapReady) status.setText("MAP NETWORK TIMEOUT • TAP TO RETRY • HOLD FOR GOOGLE MAPS");
        }, 20000);
    }

    private String mapHtml() {
        String color = String.format(Locale.US, "#%06X", 0xFFFFFF & accent);
        return ("""
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>html,body,#map{height:100%;margin:0;background:#0b1016} .leaflet-control-attribution{font-size:8px;background:#090b0dcc!important;color:#aaa}.leaflet-control-attribution a{color:__ACCENT__}.leaflet-bar a{background:#090b0d;color:white;border-color:#444}.truck{width:18px;height:18px;background:__ACCENT__;border:3px solid white;border-radius:50%;box-shadow:0 0 0 5px #0008,0 0 18px __ACCENT__}.turn{position:absolute;left:16px;right:16px;top:92px;z-index:900;background:#080a0df2;border:1px solid __ACCENT__;border-radius:14px;padding:10px 14px;color:white;font:700 14px sans-serif;display:none}.sub{color:#aeb3bc;font-size:11px;margin-top:3px}</style>
</head><body><div id="map"></div><div id="turn" class="turn"><div id="instruction"></div><div id="distance" class="sub"></div></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
let map,me,routeLine,destination,steps=[],stepIndex=0,lastLat=40.3323,lastLon=-74.5819,following=true;
function boot(){try{map=L.map('map',{zoomControl:true,preferCanvas:true}).setView([lastLat,lastLon],13);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);const icon=L.divIcon({className:'',html:'<div class="truck"></div>',iconSize:[24,24],iconAnchor:[12,12]});me=L.marker([lastLat,lastLon],{icon}).addTo(map);map.on('dragstart',()=>following=false);map.on('zoomstart',()=>following=false);setTimeout(()=>{map.invalidateSize();TRX.mapReady()},500)}catch(e){TRX.mapError(String(e))}}
function updatePosition(lat,lon,bearing,speed){lastLat=lat;lastLon=lon;if(me)me.setLatLng([lat,lon]);if(following&&map)map.panTo([lat,lon],{animate:true});if(steps.length)advanceSteps(lat,lon)}
function routeTo(lat,lon,title){destination={lat,lon,title};following=true;document.getElementById('turn').style.display='block';document.getElementById('instruction').textContent='Calculating route…';const u='https://router.project-osrm.org/route/v1/driving/'+lastLon+','+lastLat+';'+lon+','+lat+'?overview=full&geometries=geojson&steps=true';fetch(u).then(r=>{if(!r.ok)throw Error('Routing service '+r.status);return r.json()}).then(j=>{if(!j.routes||!j.routes.length)throw Error('No route found');const r=j.routes[0];if(routeLine)map.removeLayer(routeLine);routeLine=L.geoJSON(r.geometry,{style:{color:'__ACCENT__',weight:7,opacity:.9}}).addTo(map);map.fitBounds(routeLine.getBounds(),{padding:[60,60]});steps=[];r.legs.forEach(l=>l.steps.forEach(s=>steps.push(s)));stepIndex=0;showStep();TRX.routeReady(Math.round(r.distance),Math.round(r.duration))}).catch(e=>TRX.routeError(String(e)))}
function instructionFor(s){const m=s.maneuver||{},type=(m.type||'continue').replaceAll('_',' '),mod=(m.modifier||'');if(type==='arrive')return'Arrive at destination';if(type==='depart')return'Depart'+(s.name?' on '+s.name:'');if(type==='roundabout')return'Enter roundabout'+(s.name?' toward '+s.name:'');return cap(type+(mod?' '+mod:'')+(s.name?' onto '+s.name:''))}
function cap(x){return x.charAt(0).toUpperCase()+x.slice(1)}
function showStep(){if(stepIndex>=steps.length)return;const s=steps[stepIndex],txt=instructionFor(s);document.getElementById('instruction').textContent=txt;document.getElementById('distance').textContent=formatDistance(s.distance);TRX.speak(txt)}
function advanceSteps(lat,lon){if(stepIndex>=steps.length)return;const p=steps[stepIndex].maneuver.location,d=meters(lat,lon,p[1],p[0]);document.getElementById('distance').textContent=formatDistance(d);if(d<35&&stepIndex<steps.length-1){stepIndex++;showStep()}else if(stepIndex===steps.length-1&&d<30){TRX.arrived();steps=[]}}
function meters(a,b,c,d){const R=6371000,x=(c-a)*Math.PI/180,y=(d-b)*Math.PI/180,q=Math.sin(x/2)**2+Math.cos(a*Math.PI/180)*Math.cos(c*Math.PI/180)*Math.sin(y/2)**2;return 2*R*Math.asin(Math.sqrt(q))}
function formatDistance(m){return m<300?Math.max(10,Math.round(m/10)*10)+' ft':(m/1609.344).toFixed(1)+' mi'}
function clearRoute(){if(routeLine){map.removeLayer(routeLine);routeLine=null}steps=[];document.getElementById('turn').style.display='none';following=true;if(map)map.setView([lastLat,lastLon],15)}
window.addEventListener('load',boot);
</script></body></html>
""").replace("__ACCENT__", color);
    }

    private final class MapBridge {
        @JavascriptInterface public void mapReady() { activity.runOnUiThread(() -> {
            mapReady = true;
            status.setText("TRX MAP READY");
            handler.postDelayed(() -> { if (mapReady && !guiding) status.setVisibility(GONE); }, 700);
            startLocation();
        }); }
        @JavascriptInterface public void mapError(String error) { activity.runOnUiThread(() -> {
            status.setVisibility(VISIBLE); status.setText("MAP ERROR • TAP TO RETRY");
        }); }
        @JavascriptInterface public void routeReady(int meters, int seconds) { activity.runOnUiThread(() -> {
            guiding = true;
            destination.setVisibility(GONE); routeButton.setVisibility(GONE); stopButton.setVisibility(VISIBLE);
            status.setVisibility(GONE); modeBadge.setText("VOICE GUIDANCE ACTIVE  •  " + formatMiles(meters));
        }); }
        @JavascriptInterface public void routeError(String error) { activity.runOnUiThread(() -> {
            status.setVisibility(VISIBLE); status.setText("ROUTE SERVICE UNAVAILABLE • TAP TO RETRY");
        }); }
        @JavascriptInterface public void speak(String words) { activity.runOnUiThread(() -> speakNow(words)); }
        @JavascriptInterface public void arrived() { activity.runOnUiThread(() -> {
            speakNow("You have arrived at your destination");
            modeBadge.setText("DESTINATION REACHED");
        }); }
    }

    private void startLocation() {
        try {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                status.setVisibility(VISIBLE); status.setText("LOCATION PERMISSION REQUIRED"); return;
            }
            locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return;
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            lastLocation = gps != null ? gps : network;
            if (lastLocation != null) pushLocation(lastLocation);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 2, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, this);
        } catch (Throwable ignored) { }
    }

    @Override public void onLocationChanged(Location location) { lastLocation = location; pushLocation(location); }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @SuppressWarnings("deprecation") @Override public void onStatusChanged(String p, int s, Bundle e) { }

    private void pushLocation(Location location) {
        if (!mapReady || location == null) return;
        String js = String.format(Locale.US, "updatePosition(%.7f,%.7f,%.1f,%.1f)",
            location.getLatitude(), location.getLongitude(), location.getBearing(), location.getSpeed());
        mapWeb.evaluateJavascript(js, null);
    }

    private void beginNavigation(String raw) {
        String query = raw == null ? "" : raw.trim();
        if (query.isEmpty()) { Toast.makeText(activity, "Enter a destination", Toast.LENGTH_SHORT).show(); return; }
        if (!mapReady) { status.setVisibility(VISIBLE); status.setText("MAP IS STILL LOADING…"); return; }
        if ("Home".equalsIgnoreCase(query)) query = prefs.getString("home_destination", "Home");
        if ("Work".equalsIgnoreCase(query)) query = prefs.getString("work_destination", "Work");
        final String address = query;
        hideKeyboard(); suggestions.setVisibility(GONE);
        status.setVisibility(VISIBLE); status.setText("FINDING " + address.toUpperCase(Locale.US) + "…");
        new Thread(() -> {
            try {
                List<Address> matches = new Geocoder(activity, Locale.US).getFromLocationName(address, 1);
                if (matches == null || matches.isEmpty()) throw new IllegalArgumentException();
                Address found = matches.get(0);
                String js = String.format(Locale.US, "routeTo(%.7f,%.7f,%s)", found.getLatitude(),
                    found.getLongitude(), JSONObject.quote(address));
                activity.runOnUiThread(() -> { rememberDestination(address); mapWeb.evaluateJavascript(js, null); });
            } catch (Throwable error) {
                activity.runOnUiThread(() -> { status.setVisibility(VISIBLE); status.setText("DESTINATION NOT FOUND • TRY A FULL ADDRESS"); });
            }
        }, "trx-route-search").start();
    }

    private void stopGuidance() {
        guiding = false; mapWeb.evaluateJavascript("clearRoute()", null);
        destination.setVisibility(VISIBLE); routeButton.setVisibility(VISIBLE); stopButton.setVisibility(GONE);
        modeBadge.setText("TRX LIVE GPS  •  PINCH TO ZOOM");
        Toast.makeText(activity, "TRX guidance ended", Toast.LENGTH_SHORT).show();
    }

    private void speakNow(String words) {
        if (speech != null && words != null && !words.isEmpty())
            speech.speak(words, TextToSpeech.QUEUE_FLUSH, null, "trx-nav");
    }

    private void openGoogleMapsFallback() {
        try {
            String q = destination.getText().toString().trim();
            Uri uri = q.isEmpty() ? Uri.parse("https://www.google.com/maps")
                : Uri.parse("google.navigation:q=" + Uri.encode(q) + "&mode=d");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            if (!q.isEmpty()) intent.setPackage("com.google.android.apps.maps");
            activity.startActivity(intent);
        } catch (Throwable error) { Toast.makeText(activity, "Google Maps is unavailable", Toast.LENGTH_LONG).show(); }
    }

    private void scheduleAddressSuggestions(String raw) {
        String query = raw == null ? "" : raw.trim(); final int request = ++suggestionRequest;
        if (query.length() < 3) { suggestions.removeAllViews(); suggestions.setVisibility(GONE); return; }
        handler.postDelayed(() -> {
            if (request != suggestionRequest || !destination.hasFocus()) return;
            new Thread(() -> loadAddressSuggestions(query, request), "trx-address-search").start();
        }, 300);
    }

    private void loadAddressSuggestions(String query, int request) {
        java.util.List<Address> found = new java.util.ArrayList<>();
        try { List<Address> matches = new Geocoder(activity, Locale.US).getFromLocationName(query, 4); if (matches != null) found.addAll(matches); }
        catch (Throwable ignored) { }
        activity.runOnUiThread(() -> renderAddressSuggestions(found, request));
    }

    private void renderAddressSuggestions(List<Address> found, int request) {
        if (request != suggestionRequest || !destination.hasFocus()) return;
        suggestions.removeAllViews(); if (found == null || found.isEmpty()) { suggestions.setVisibility(GONE); return; }
        HashSet<String> added = new HashSet<>();
        for (Address a : found) {
            String full = a.getMaxAddressLineIndex() >= 0 ? a.getAddressLine(0) : null;
            if (TextUtils.isEmpty(full) || !added.add(full)) continue;
            TextView row = new TextView(activity); row.setText("●  " + full); row.setTextColor(Color.WHITE);
            row.setTextSize(13); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14), 0, dp(12), 0);
            row.setBackground(panel(0xff090b0e, 0xff292d33, 1, 11));
            suggestions.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(54)));
            final String selected = full;
            row.setOnClickListener(v -> { selectingSuggestion = true; destination.setText(selected); selectingSuggestion = false;
                suggestions.setVisibility(GONE); destination.clearFocus(); hideKeyboard(); beginNavigation(selected); });
        }
        suggestions.setVisibility(suggestions.getChildCount() == 0 ? GONE : VISIBLE); suggestions.bringToFront();
    }

    private void hideKeyboard() { InputMethodManager k = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE); if (k != null) k.hideSoftInputFromWindow(destination.getWindowToken(), 0); }
    private void rememberDestination(String value) { if (value != null && !value.trim().isEmpty()) prefs.edit().putString("recent_destinations", value.trim()).apply(); }
    private String formatMiles(int meters) { return String.format(Locale.US, "%.1f MI", meters / 1609.344); }

    public boolean closeChooser() { if (suggestions.getVisibility() == VISIBLE) { suggestions.setVisibility(GONE); return true; } if (guiding) { stopGuidance(); return true; } return false; }
    public void onCreatePanel() { }
    public void onStartPanel() { }
    public void onResumePanel() { mapWeb.onResume(); mapWeb.resumeTimers(); if (mapReady) startLocation(); }
    public void onPausePanel() { mapWeb.onPause(); }
    public void onStopPanel() { }
    public void onShownPanel() { mapWeb.onResume(); mapWeb.resumeTimers(); mapWeb.evaluateJavascript("if(map)map.invalidateSize()", null); }
    public void onHiddenPanel() { }
    public void onConfigurationChangedPanel(android.content.res.Configuration config) { mapWeb.evaluateJavascript("if(map)map.invalidateSize()", null); }
    public void onLowMemoryPanel() { }
    public void onSaveInstanceStatePanel(Bundle out) { }
    public void onDestroyPanel() {
        handler.removeCallbacksAndMessages(null);
        try { if (locationManager != null) locationManager.removeUpdates(this); } catch (Throwable ignored) { }
        if (speech != null) { speech.stop(); speech.shutdown(); }
        mapWeb.removeJavascriptInterface("TRX"); mapWeb.destroy();
    }

    private Button button(String text, boolean selected) { Button b = new Button(activity); b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(19); b.setGravity(Gravity.CENTER); b.setBackground(panel(0xff050608, selected ? accent : 0xff5f646d, selected ? 2 : 1, 16)); b.setElevation(dp(selected ? 9 : 6)); return b; }
    private GradientDrawable panel(int color, int stroke, int width, int radius) { int top = color == 0xff050608 ? 0xff24272c : 0xff171a1f; GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, color, 0xff030405}); d.setCornerRadius(dp(radius)); d.setStroke(dp(width), stroke); return d; }
    private int currentAccent() { int t = prefs.getInt("theme_choice", 1); if (t == 1) return 0xffff9f1a; if (t == 2) return 0xffd9dde3; if (t == 3) return 0xff438cff; if (t == 4) return prefs.getInt("custom_accent", 0xffff2338); return 0xffff2338; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
