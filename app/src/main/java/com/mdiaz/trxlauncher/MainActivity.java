package com.mdiaz.trxlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private DashboardView dashboard;
    private volatile float speedMph;
    private volatile String weatherTemp = "--°";
    private volatile String weatherCondition = "WEATHER UNAVAILABLE";
    private LocationManager locationManager;
    private FrameLayout root;
    private FrameLayout mapPanel;
    private MapView mapView;
    private GoogleMap googleMap;
    private TextView mapStatus;
    private boolean mapDark;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setNavigationBarColor(0xff050607);
            getWindow().setStatusBarColor(0xff050607);
            root = new FrameLayout(this);
            dashboard = new DashboardView(this);
            root.addView(dashboard,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
            setContentView(root);
            setupLiveMap(state);
            dashboard.post(() -> showLiveMap(dashboard.currentPage()==1));
            startGps();
            fetchWeather();
        } catch (Throwable error) {
            showStartupError(error);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        MediaBridge.ensureConnected(this);
        if (dashboard != null) dashboard.postInvalidate();
    }

    @Override protected void onStart(){super.onStart();if(mapView!=null)mapView.onStart();}
    @Override protected void onPause(){if(mapView!=null)mapView.onPause();super.onPause();}
    @Override protected void onStop(){if(mapView!=null)mapView.onStop();super.onStop();}
    @Override public void onLowMemory(){super.onLowMemory();if(mapView!=null)mapView.onLowMemory();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(mapView!=null)mapView.onSaveInstanceState(out);}

    private void setupLiveMap(Bundle state){
        mapDark=getSharedPreferences("launcher",MODE_PRIVATE).getBoolean("map_dark",true);
        mapPanel=new FrameLayout(this);
        mapPanel.setBackgroundColor(0xff080a0d);
        mapPanel.setVisibility(View.GONE);
        mapPanel.setElevation(12f);
        mapView=new MapView(this);
        mapView.onCreate(state);
        mapPanel.addView(mapView,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));

        mapStatus=new TextView(this);
        mapStatus.setText("CONNECTING TO GOOGLE MAPS…\nIf this remains visible, enable Maps SDK for Android and billing for the demo key.");
        mapStatus.setTextColor(0xffff2338);mapStatus.setTextSize(14);mapStatus.setGravity(Gravity.CENTER);
        mapStatus.setBackgroundColor(0xdd090b0e);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,dp(86),Gravity.TOP);
        mapPanel.addView(mapStatus,sp);

        Button home=mapButton("⌂",true);home.setContentDescription("Navigate Home");
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.BOTTOM|Gravity.LEFT);
        hp.setMargins(dp(14),0,0,dp(14));mapPanel.addView(home,hp);
        home.setOnClickListener(v->openNavigation());

        Button maps=mapButton("➤",false);maps.setContentDescription("Open Google Maps");
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.BOTTOM|Gravity.RIGHT);
        mp.setMargins(0,0,dp(14),dp(14));mapPanel.addView(maps,mp);
        maps.setOnClickListener(v->openNavigation());

        mapView.getMapAsync(map->{
            googleMap=map;
            map.getUiSettings().setZoomGesturesEnabled(true);
            map.getUiSettings().setScrollGesturesEnabled(true);
            map.getUiSettings().setRotateGesturesEnabled(true);
            map.getUiSettings().setCompassEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
            applyMapStyle();
            map.setOnMapLoadedCallback(()->{if(mapStatus!=null)mapStatus.setVisibility(View.GONE);});
            map.setOnMapLongClickListener(point->{mapDark=!mapDark;
                getSharedPreferences("launcher",MODE_PRIVATE).edit().putBoolean("map_dark",mapDark).apply();
                applyMapStyle();});
            LatLng start=new LatLng(40.33,-74.58);
            try{
                if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                    map.setMyLocationEnabled(true);
                    Location last=locationManager==null?null:locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if(last!=null)start=new LatLng(last.getLatitude(),last.getLongitude());
                }
            }catch(Throwable ignored){}
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(start,13.5f));
        });
    }

    private Button mapButton(String label,boolean primary){
        Button button=new Button(this);button.setText(label);button.setTextColor(Color.WHITE);
        button.setTextSize(22);button.setAllCaps(false);button.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(12));
        bg.setColor(0xee050608);bg.setStroke(dp(primary?2:1),0xffff2338);
        button.setBackground(bg);return button;
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private void applyMapStyle(){
        if(googleMap==null)return;
        try{googleMap.setMapStyle(mapDark?
            MapStyleOptions.loadRawResourceStyle(this,R.raw.map_dark):new MapStyleOptions("[]"));
        }catch(Throwable ignored){}
    }

    public void showLiveMap(boolean visible){
        if(mapPanel==null||root==null)return;
        if(visible){
            int w=Math.max(1,root.getWidth()),h=Math.max(1,root.getHeight());
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(
                w-Math.round(w*36f/1080f),Math.round(h*1042f/1440f));
            lp.leftMargin=Math.round(w*18f/1080f);lp.topMargin=Math.round(h*248f/1440f);
            mapPanel.setLayoutParams(lp);
            if(mapPanel.getParent()==null)root.addView(mapPanel);
            mapPanel.setVisibility(View.VISIBLE);mapPanel.bringToFront();
        }else mapPanel.setVisibility(View.GONE);
    }

    private void showStartupError(Throwable error) {
        try {
            android.widget.TextView diagnostic = new android.widget.TextView(this);
            diagnostic.setBackgroundColor(0xff050607);
            diagnostic.setTextColor(0xffff1d32);
            diagnostic.setTextSize(18f);
            diagnostic.setPadding(30,60,30,30);
            diagnostic.setText("TRX LAUNCHER STARTUP ERROR\n\n" +
                error.getClass().getName() + "\n" + String.valueOf(error.getMessage()));
            setContentView(diagnostic);
        } catch (Throwable ignored) { finish(); }
    }

    public float speedMph() { return speedMph; }
    public String weatherTemp() { return weatherTemp; }
    public String weatherCondition() { return weatherCondition; }

    private void fetchWeather() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=40.33&longitude=-74.58&current=temperature_2m,weather_code&temperature_unit=fahrenheit");
                connection = (HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setRequestProperty("User-Agent","TRX-Launcher/0.3.2");
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
                int current = json.indexOf("\"current\":");
                double temp = numberAfter(json,"\"temperature_2m\":",current);
                int code = (int)numberAfter(json,"\"weather_code\":",current);
                weatherTemp = Math.round(temp) + "°";
                weatherCondition = weatherName(code);
            } catch (Throwable ignored) {
                weatherTemp = "--°";
                weatherCondition = "WEATHER UNAVAILABLE";
            } finally {
                if (connection != null) connection.disconnect();
                if (dashboard != null) dashboard.postInvalidate();
            }
        },"trx-weather").start();
    }

    private static double numberAfter(StringBuilder text,String key,int from) {
        int at = text.indexOf(key,Math.max(0,from));
        if (at < 0) return 0;
        at += key.length();
        int end = at;
        while (end < text.length() &&
            "-.0123456789".indexOf(text.charAt(end)) >= 0) end++;
        try { return Double.parseDouble(text.substring(at,end)); }
        catch (Throwable ignored) { return 0; }
    }

    private static String weatherName(int code) {
        if (code == 0) return "CLEAR";
        if (code <= 3) return "PARTLY CLOUDY";
        if (code <= 48) return "FOG";
        if (code <= 67) return "RAIN";
        if (code <= 77) return "SNOW";
        if (code <= 82) return "SHOWERS";
        if (code <= 86) return "SNOW SHOWERS";
        return "THUNDERSTORMS";
    }

    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            speedMph = location.hasSpeed() ?
                Math.max(0,location.getSpeed()*2.2369363f) : 0;
            if (dashboard != null) dashboard.onSpeedChanged(speedMph);
        }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
        @Override public void onStatusChanged(String provider,int status,Bundle extras) { }
    };

    private void startGps() {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},41);
                return;
            }
            locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                try {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,250,0,gpsListener);
                } catch (Throwable ignored) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,500,0,gpsListener);
                }
            }
        } catch (Throwable ignored) { }
    }

    @Override public void onRequestPermissionsResult(
        int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode == 41 && results.length > 0 &&
            results[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @Override protected void onDestroy() {
        try {
            if (locationManager != null) locationManager.removeUpdates(gpsListener);
        } catch (Throwable ignored) { }
        if(mapView!=null)mapView.onDestroy();
        super.onDestroy();
    }

    public void openNavigation() {
        String destination=getSharedPreferences("launcher",MODE_PRIVATE)
            .getString("home_destination","Home");
        openNavigationTo(destination);
    }

    public void openNavigationTo(String destination) {
        try {
            int choice=getSharedPreferences("launcher",MODE_PRIVATE)
                .getInt("nav_choice",0);
            Intent i;
            if(choice==1){
                i=new Intent(Intent.ACTION_VIEW,Uri.parse(
                    "https://waze.com/ul?q="+Uri.encode(destination)+"&navigate=yes"));
                i.setPackage("com.waze");
            }else{
                i=new Intent(Intent.ACTION_VIEW,Uri.parse(
                    "google.navigation:q="+Uri.encode(destination)));
            }
            if(i.resolveActivity(getPackageManager())==null)
                i=new Intent(Intent.ACTION_VIEW,Uri.parse(
                    "geo:0,0?q="+Uri.encode(destination)));
            startActivity(i);
        } catch(Throwable ignored){openSystemSettings();}
    }

    public void openMedia() {
        int choice=getSharedPreferences("launcher",MODE_PRIVATE)
            .getInt("media_choice",0);
        if(choice==0&&launchPackage("com.spotify.music"))return;
        if(choice==1&&launchPackage("com.google.android.apps.youtube.music"))return;
        if(choice==2&&launchPackage("com.apple.android.music"))return;
        if(launchPackage("com.spotify.music"))return;
        if(launchPackage("com.google.android.apps.youtube.music"))return;
        if(launchPackage("com.apple.android.music"))return;
        try{
            startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_MUSIC));
        }catch(Throwable ignored){openSystemSettings();}
    }

    public void openSettingsScreen(){
        try{startActivity(new Intent(this,SettingsActivity.class));}
        catch(Throwable ignored){openSystemSettings();}
    }

    public void openMediaSource(int source) {
        switch (source) {
            case 0:
                if (!launchPackage("com.spotify.music")) openMedia();
                break;
            case 1:
                if (!launchPackage("com.google.android.apps.youtube.music") &&
                    !launchPackage("com.google.android.youtube")) openMedia();
                break;
            case 2:
                if (!launchPackage("com.apple.android.music")) openMedia();
                break;
            case 3:
                try {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (Throwable ignored) { openSystemSettings(); }
                break;
            default: openMedia();
        }
    }

    private boolean launchPackage(String packageName) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(packageName);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    public void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Throwable ignored) { }
    }

    public List<AppEntry> installedApps() {
        Intent query = new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> rows =
            getPackageManager().queryIntentActivities(query,0);
        List<AppEntry> result = new ArrayList<>();
        for (ResolveInfo r : rows) {
            ActivityInfo a = r.activityInfo;
            if (a == null || a.packageName == null ||
                a.packageName.equals(getPackageName())) continue;
            try {
                CharSequence label = r.loadLabel(getPackageManager());
                result.add(new AppEntry(
                    label == null ? a.packageName : label.toString(),
                    a.packageName,a.name,r.loadIcon(getPackageManager())));
            } catch (Throwable ignored) { }
        }
        Collections.sort(result,
            Comparator.comparing(x -> x.label.toLowerCase()));
        return result;
    }

    public void launch(AppEntry app) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName,app.activityName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) { }
    }
}
