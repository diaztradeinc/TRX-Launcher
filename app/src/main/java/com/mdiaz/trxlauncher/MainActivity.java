package com.mdiaz.trxlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Address;
import android.location.Geocoder;
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
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.libraries.navigation.NavigationView;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.Waypoint;
import com.google.android.libraries.navigation.ListenableResultFuture;
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
import java.util.Locale;

public class MainActivity extends Activity {
    private DashboardView dashboard;
    private volatile float speedMph;
    private volatile String weatherTemp = "--°";
    private volatile String weatherCondition = "WEATHER UNAVAILABLE";
    private LocationManager locationManager;
    private FrameLayout root;
    private FrameLayout mapPanel;
    private NavigationView mapView;
    private GoogleMap googleMap;
    private Navigator navigator;
    private EditText destinationInput;
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
            root.addView(mapPanel);
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
        if (dashboard != null) { dashboard.reloadMediaApps(); dashboard.postInvalidate(); }
    }

    @Override protected void onStart(){super.onStart();if(mapView!=null)mapView.onStart();}
    @Override protected void onPause(){if(mapView!=null)mapView.onPause();super.onPause();}
    @Override protected void onStop(){if(mapView!=null)mapView.onStop();super.onStop();}
    @Override public void onLowMemory(){super.onLowMemory();if(mapView!=null)mapView.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(mapView!=null)mapView.onConfigurationChanged(config);}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(mapView!=null)mapView.onSaveInstanceState(out);}

    private void setupLiveMap(Bundle state){
        mapDark=getSharedPreferences("launcher",MODE_PRIVATE).getBoolean("map_dark",true);
        mapPanel=new FrameLayout(this);
        mapPanel.setBackgroundColor(0xff080a0d);
        mapPanel.setVisibility(View.GONE);
        mapPanel.setElevation(12f);
        TextView consoleTitle=new TextView(this);
        consoleTitle.setText("TRX NAVIGATION\\nChoose Waze or Google Maps");
        consoleTitle.setTextColor(Color.WHITE);consoleTitle.setTextSize(24);consoleTitle.setGravity(Gravity.CENTER);
        mapPanel.addView(consoleTitle,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));

        destinationInput=new EditText(this);
        destinationInput.setHint("Where to?");
        destinationInput.setSingleLine(true);destinationInput.setTextColor(Color.WHITE);
        destinationInput.setHintTextColor(0xff8f949d);destinationInput.setTextSize(15);
        destinationInput.setPadding(dp(18),0,dp(70),0);
        GradientDrawable searchBg=new GradientDrawable();searchBg.setColor(0xee090b0f);
        searchBg.setCornerRadius(dp(14));searchBg.setStroke(dp(2),0xffff2338);
        destinationInput.setBackground(searchBg);
        FrameLayout.LayoutParams searchParams=new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,dp(58),Gravity.TOP);
        searchParams.setMargins(dp(14),dp(14),dp(14),0);mapPanel.addView(destinationInput,searchParams);

        Button go=mapButton("GO",true);
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(dp(62),dp(50),Gravity.TOP|Gravity.RIGHT);
        gp.setMargins(0,dp(18),dp(18),0);mapPanel.addView(go,gp);
        go.setOnClickListener(v->openPreferredNavigation(destinationInput.getText().toString()));

        mapStatus=new TextView(this);
        mapStatus.setText("INITIALIZING TRX NAVIGATION…");
        mapStatus.setTextColor(0xffff2338);mapStatus.setTextSize(14);mapStatus.setGravity(Gravity.CENTER);
        mapStatus.setBackgroundColor(0xdd090b0e);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,dp(54),Gravity.CENTER);
        mapPanel.addView(mapStatus,sp);

        Button home=mapButton("⌂",true);home.setContentDescription("Navigate Home");
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.BOTTOM|Gravity.LEFT);
        hp.setMargins(dp(14),0,0,dp(14));mapPanel.addView(home,hp);
        home.setOnClickListener(v->openNavigationTo(getSharedPreferences("launcher",MODE_PRIVATE).getString("home_destination","Home")));

        Button maps=mapButton("G",false);maps.setContentDescription("Open destination in Google Maps");
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.BOTTOM|Gravity.RIGHT);
        mp.setMargins(0,0,dp(14),dp(14));mapPanel.addView(maps,mp);
        maps.setOnClickListener(v->openGoogleMapsNavigation(destinationInput.getText().toString()));

        Button waze=mapButton("W",true);waze.setContentDescription("Open destination in Waze");
        FrameLayout.LayoutParams wp=new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        wp.setMargins(0,0,0,dp(14));mapPanel.addView(waze,wp);
        waze.setOnClickListener(v->openWazeNavigation(destinationInput.getText().toString()));

        mapStatus.setText("API-FREE NAVIGATION • SELECT WAZE OR GOOGLE MAPS");
        mapStatus.setVisibility(View.VISIBLE);
        /*
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
        }); */
    }

    private void initializeNavigator(){
        NavigationApi.getNavigator(this,new NavigationApi.NavigatorListener(){
            @Override public void onNavigatorReady(Navigator ready){
                navigator=ready;
                mapView.setNavigationUiEnabled(true);
                mapView.setHeaderEnabled(true);
                mapView.setEtaCardEnabled(true);
                mapView.setRecenterButtonEnabled(true);
                mapView.setSpeedometerEnabled(true);
                mapView.setSpeedLimitIconEnabled(true);
                if(mapStatus!=null){mapStatus.setText("NAVIGATION READY • LOADING MAP…");mapStatus.setVisibility(View.VISIBLE);}
            }
            @Override public void onError(int errorCode){
                if(mapStatus!=null){mapStatus.setVisibility(View.VISIBLE);
                    mapStatus.setText(errorCode==NavigationApi.ErrorCode.NOT_AUTHORIZED?
                        "NAVIGATION KEY NOT AUTHORIZED":"NAVIGATION SETUP ERROR • "+errorCode);}
            }
        });
    }

    public void startInternalNavigation(String destination){
        if(destination==null||destination.trim().isEmpty()){Toast.makeText(this,"Enter a destination",Toast.LENGTH_SHORT).show();return;}
        if(navigator==null){Toast.makeText(this,"Navigation is still initializing",Toast.LENGTH_SHORT).show();return;}
        String query=destination.trim();
        if("Home".equalsIgnoreCase(query))query=getSharedPreferences("launcher",MODE_PRIVATE).getString("home_destination","Home");
        if("Work".equalsIgnoreCase(query))query=getSharedPreferences("launcher",MODE_PRIVATE).getString("work_destination","Work");
        final String address=query;
        if(mapStatus!=null){mapStatus.setText("FINDING "+address.toUpperCase()+"…");mapStatus.setVisibility(View.VISIBLE);}
        new Thread(()->{
            try{
                List<Address> matches=new Geocoder(this,Locale.US).getFromLocationName(address,1);
                if(matches==null||matches.isEmpty())throw new IllegalArgumentException("Destination not found");
                Address found=matches.get(0);
                Waypoint waypoint=new Waypoint.Builder()
                    .setLatLng(found.getLatitude(),found.getLongitude())
                    .setTitle(address).setVehicleStopover(true).build();
                RoutingOptions options=new RoutingOptions();
                options.travelMode(RoutingOptions.TravelMode.DRIVING);
                runOnUiThread(()->{
                    ListenableResultFuture<Navigator.RouteStatus> route=navigator.setDestination(waypoint,options);
                    route.setOnResultListener(status->{
                        if(status==Navigator.RouteStatus.OK){
                            navigator.startGuidance();destinationInput.setVisibility(View.GONE);
                            if(mapStatus!=null)mapStatus.setVisibility(View.GONE);
                        }else if(mapStatus!=null){mapStatus.setVisibility(View.VISIBLE);
                            mapStatus.setText("ROUTE UNAVAILABLE • "+status);}
                    });
                });
            }catch(Throwable error){runOnUiThread(()->{if(mapStatus!=null){mapStatus.setVisibility(View.VISIBLE);
                mapStatus.setText("OPENING DESTINATION IN GOOGLE MAPS…");}
                openGoogleMapsNavigation(address);});}
        },"trx-route").start();
    }

    private void openPreferredNavigation(String destination){
        int choice=getSharedPreferences("launcher",MODE_PRIVATE).getInt("nav_choice",0);
        if(choice==1)openWazeNavigation(destination);else openGoogleMapsNavigation(destination);
    }

    private void openWazeNavigation(String destination){
        String address=destination==null?"":destination.trim();
        if(address.isEmpty()){Toast.makeText(this,"Enter a destination",Toast.LENGTH_SHORT).show();return;}
        try{
            Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://waze.com/ul?q="+Uri.encode(address)+"&navigate=yes"));
            intent.setPackage("com.waze");
            if(intent.resolveActivity(getPackageManager())!=null){startActivity(intent);return;}
            Toast.makeText(this,"Waze is not installed • opening Google Maps",Toast.LENGTH_LONG).show();
            openGoogleMapsNavigation(address);
        }catch(Throwable error){openGoogleMapsNavigation(address);}
    }

    private void openGoogleMapsNavigation(String destination){
        String address=destination==null?"":destination.trim();
        if(address.isEmpty()){Toast.makeText(this,"Enter a destination",Toast.LENGTH_SHORT).show();return;}
        try{
            Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(address)+"&mode=d"));
            intent.setPackage("com.google.android.apps.maps");
            if(intent.resolveActivity(getPackageManager())!=null){startActivity(intent);return;}
            Intent web=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(address)+"&travelmode=driving"));
            startActivity(web);
        }catch(Throwable error){Toast.makeText(this,"Google Maps is unavailable",Toast.LENGTH_LONG).show();}
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
            MapStyleOptions.loadRawResourceStyle(this,R.raw.map_dark):null);
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
            diagnostic.setText("TRX LAUNCHER STARTUP ERROR\\n\\n" +
                error.getClass().getName() + "\\n" + String.valueOf(error.getMessage()));
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

    public void openMediaAppPicker(){
        try{startActivity(new Intent(this,MediaAppPickerActivity.class));}
        catch(Throwable ignored){openSystemSettings();}
    }

    public List<AppEntry> selectedMediaApps(){
        String raw=getSharedPreferences("launcher",MODE_PRIVATE).getString("media_apps","");
        List<AppEntry> all=installedApps(),result=new ArrayList<>();
        if(raw.isEmpty()){
            String[] defaults={"com.spotify.music","com.google.android.apps.youtube.music","com.apple.android.music"};
            for(String pkg:defaults)for(AppEntry app:all)if(pkg.equals(app.packageName)){result.add(app);break;}
        }else for(String pkg:raw.split(","))for(AppEntry app:all)
            if(pkg.trim().equals(app.packageName)){result.add(app);break;}
        return result;
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
