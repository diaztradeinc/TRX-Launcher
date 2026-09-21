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
    private volatile Location weatherLocation;
    private long weatherRequestedAt;
    private LocationManager locationManager;
    private FrameLayout root;
    private FrameLayout mapPanel;
    private NavigationView mapView;
    private GoogleMap googleMap;
    private Navigator navigator;
    private EditText destinationInput;
    private TextView mapStatus;
    private boolean mapDark;
    private int lastCompensatedVolume=-1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::handleBack);
        if(!getSharedPreferences("launcher",MODE_PRIVATE).getBoolean("first_run_complete",false)){
            startActivity(new Intent(this,FirstRunActivity.class));finish();return;
        }
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
            if (mapPanel instanceof NavigationPanel)
                ((NavigationPanel) mapPanel).onCreatePanel();
            root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
                if((r-l)!=(or-ol)||(b-t)!=(ob-ot))showLiveMap(dashboard.currentPage()==1);
            });
            dashboard.post(() -> showLiveMap(dashboard.currentPage()==1));
            startGps();
            fetchWeather();
        } catch (Throwable error) {
            showStartupError(error);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (mapPanel instanceof NavigationPanel) {((NavigationPanel)mapPanel).onResumePanel();((NavigationPanel)mapPanel).applyTheme();}
        MediaBridge.ensureConnected(this);
        ObdBridge.start(this);
        if (dashboard != null) { dashboard.reloadTheme(); dashboard.reloadMediaApps(); dashboard.reloadApps(); dashboard.postInvalidate(); }
    }

    @Override protected void onStart(){super.onStart();if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onStartPanel();}
    @Override protected void onPause(){if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onPausePanel();super.onPause();}
    @Override protected void onStop(){if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onStopPanel();super.onStop();}
    @Override public void onLowMemory(){super.onLowMemory();if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onLowMemoryPanel();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onConfigurationChangedPanel(config);}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onSaveInstanceStatePanel(out);}

    private void setupLiveMap(Bundle state){
        mapPanel=new NavigationPanel(this,state);
        // INVISIBLE keeps the native map surface measured without drawing it.
        mapPanel.setVisibility(View.INVISIBLE);
        mapPanel.setElevation(12f);
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
        openGoogleMapsNavigation(destination);
    }

    public void openGoogleMapsNavigation(String destination){
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
        boolean compact=dashboard!=null&&dashboard.currentPage()==0&&dashboard.currentSection()==0;
        visible=visible||compact;
        if(visible){
            int w=Math.max(1,root.getWidth()),h=Math.max(1,root.getHeight());
            int topInset=0,bottomInset=0;
            try{
                android.view.WindowInsets wi=root.getRootWindowInsets();
                if(wi!=null){
                    if(android.os.Build.VERSION.SDK_INT>=30){
                        android.graphics.Insets bars=wi.getInsets(android.view.WindowInsets.Type.systemBars());
                        topInset=bars.top;bottomInset=bars.bottom;
                    }else{
                        topInset=wi.getSystemWindowInsetTop();bottomInset=wi.getSystemWindowInsetBottom();
                    }
                }
            }catch(Throwable ignored){}
            int usable=Math.max(1,h-topInset-bottomInset);
            int side=Math.round(w*(compact?32f:18f)/1080f);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(
                Math.max(1,compact?Math.round(w*648f/1080f):w-side*2),Math.max(1,Math.round(usable*(compact?884f:1118f)/1440f)));
            lp.leftMargin=side;
            lp.topMargin=topInset+Math.round(usable*164f/1440f);
            mapPanel.setLayoutParams(lp);
            if(mapPanel.getParent()==null)root.addView(mapPanel);
            mapPanel.setVisibility(View.VISIBLE);mapPanel.bringToFront();
            if(mapPanel instanceof NavigationPanel){((NavigationPanel)mapPanel).setCompact(compact);((NavigationPanel)mapPanel).onShownPanel();}
        }else{
            if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onHiddenPanel();
            mapPanel.setVisibility(View.INVISIBLE);
        }
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
        Location fix=weatherLocation;
        if(fix==null){weatherCondition="WAITING FOR LOCATION";return;}
        weatherRequestedAt=android.os.SystemClock.elapsedRealtime();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude="+fix.getLatitude()+"&longitude="+fix.getLongitude()+"&current=temperature_2m,weather_code&temperature_unit=fahrenheit");
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
            weatherLocation=new Location(location);
            if(weatherRequestedAt==0||android.os.SystemClock.elapsedRealtime()-weatherRequestedAt>900000)fetchWeather();
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
        if(requestCode==ObdSetup.PERMISSION_REQUEST&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)ObdSetup.show(this);
        if (requestCode == 41 && results.length > 0 &&
            results[0] == PackageManager.PERMISSION_GRANTED) {startGps();if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onResumePanel();}
    }

    private void handleBack(){
        if(mapPanel instanceof NavigationPanel&&((NavigationPanel)mapPanel).closeChooser())return;
        moveTaskToBack(true);
    }
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){handleBack();}

    @Override protected void onDestroy() {
        try {
            if (locationManager != null) locationManager.removeUpdates(gpsListener);
        } catch (Throwable ignored) { }
        if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).onDestroyPanel();
        ObdBridge.stop();
        super.onDestroy();
    }

    public void openNavigation() {
        String destination=getSharedPreferences("launcher",MODE_PRIVATE)
            .getString("home_destination","Home");
        openNavigationTo(destination);
    }

    public void openNavigationTo(String destination) {
        try {
            Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(
                "google.navigation:q="+Uri.encode(destination)+"&mode=d"));
            i.setPackage("com.google.android.apps.maps");
            if(i.resolveActivity(getPackageManager())==null)
                i=new Intent(Intent.ACTION_VIEW,Uri.parse(
                    "https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(destination)+"&travelmode=driving"));
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

    public void requestMediaAccess() {
        if (MediaBridge.hasAccess(this)) {
            MediaBridge.ensureConnected(this);
            android.widget.Toast.makeText(this,"TRX media controls are connected",android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Connect TRX Media Controls")
            .setMessage("One Android confirmation lets TRX Launcher show artwork, track details, queues, and playback controls. This is required only once and can be turned off later in system settings.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Continue",(dialog,which)->openMediaAccessSettings())
            .show();
    }

    private void openMediaAccessSettings() {
        try {
            Intent intent;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new android.content.ComponentName(this, MediaBridge.class).flattenToString());
            } else {
                intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            }
            startActivity(intent);
        } catch (Throwable ignored) {
            MediaBridge.requestAccess(this);
        }
    }

    public void openMediaAppPicker(){
        try{startActivity(new Intent(this,MediaAppPickerActivity.class));}
        catch(Throwable ignored){openSystemSettings();}
    }

    public void openHomeQuickAppPicker(){
        try{
            Intent picker=new Intent(this,MediaAppPickerActivity.class);
            picker.putExtra("selection_key","home_quick_apps");
            picker.putExtra("picker_title","HOME QUICK LAUNCH");
            picker.putExtra("picker_help","Choose up to five apps for the shortcut bar below your Home map.");
            picker.putExtra("picker_hint","HOME SHORTCUTS  •  TAP TO SELECT");
            picker.putExtra("picker_save","SAVE QUICK LAUNCH");
            picker.putExtra("max_selection",5);
            startActivity(picker);
        }catch(Throwable ignored){openSystemSettings();}
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

    public void showAppSearch(String current){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setText(current==null?"":current);input.setHint("App name");input.setSelectAllOnFocus(true);
        int pad=dp(20);FrameLayout holder=new FrameLayout(this);holder.setPadding(pad,0,pad,0);holder.addView(input,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT));
        new android.app.AlertDialog.Builder(this).setTitle("Search installed apps").setView(holder)
            .setPositiveButton("Search",(dialog,which)->{if(dashboard!=null)dashboard.setAppSearch(input.getText().toString());})
            .setNeutralButton("Clear",(dialog,which)->{if(dashboard!=null)dashboard.setAppSearch("");})
            .setNegativeButton("Cancel",null).show();
        input.requestFocus();
    }

    public void showAppOptions(AppEntry app){
        final android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);panel.setOrientation(android.widget.LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(14),dp(18),dp(12));
        GradientDrawable panelBg=new GradientDrawable();panelBg.setColor(0xff0b0d10);panelBg.setCornerRadius(dp(18));panelBg.setStroke(dp(2),0xffff2338);panel.setBackground(panelBg);
        android.widget.LinearLayout header=new android.widget.LinearLayout(this);header.setOrientation(android.widget.LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(2),dp(2),dp(2),dp(10));
        android.widget.ImageView icon=new android.widget.ImageView(this);icon.setImageDrawable(app.icon);header.addView(icon,new android.widget.LinearLayout.LayoutParams(dp(50),dp(50)));
        android.widget.LinearLayout titles=new android.widget.LinearLayout(this);titles.setOrientation(android.widget.LinearLayout.VERTICAL);titles.setPadding(dp(14),0,0,0);
        TextView title=new TextView(this);title.setText("TRX OPTIONS");title.setTextColor(Color.WHITE);title.setTextSize(20);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);title.setSingleLine(true);
        TextView subtitle=new TextView(this);subtitle.setText(app.label.toUpperCase(Locale.US));subtitle.setTextColor(0xffff2338);subtitle.setTextSize(11);subtitle.setLetterSpacing(.16f);subtitle.setSingleLine(true);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(title);titles.addView(subtitle);header.addView(titles,new android.widget.LinearLayout.LayoutParams(0,FrameLayout.LayoutParams.WRAP_CONTENT,1));panel.addView(header);
        boolean favorite=isAppFavorite(app);
        addAppMenuAction(panel,"OPEN APP",Color.WHITE,()->launch(app),dialog);
        addAppMenuAction(panel,favorite?"REMOVE FAVORITE":"ADD TO FAVORITES",Color.WHITE,()->toggleAppFavorite(app),dialog);
        addAppMenuAction(panel,"ADD TO MEDIA SOURCES",Color.WHITE,()->addAppToMediaSources(app),dialog);
        addAppMenuAction(panel,"APP INFORMATION",Color.WHITE,()->openAppInformation(app),dialog);
        addAppMenuAction(panel,"UNINSTALL",0xffff4b5e,()->requestAppUninstall(app),dialog);
        dialog.setContentView(panel);dialog.setCanceledOnTouchOutside(true);dialog.show();
        android.view.Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);android.view.WindowManager.LayoutParams lp=window.getAttributes();lp.dimAmount=.72f;lp.width=Math.min(dp(500),Math.round(getResources().getDisplayMetrics().widthPixels*.62f));lp.height=android.view.WindowManager.LayoutParams.WRAP_CONTENT;lp.gravity=Gravity.CENTER;window.setAttributes(lp);}
    }
    private void addAppMenuAction(android.widget.LinearLayout panel,String label,int color,Runnable action,android.app.Dialog dialog){
        View divider=new View(this);divider.setBackgroundColor(0xff2c3138);panel.addView(divider,new android.widget.LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,dp(1)));
        TextView row=new TextView(this);row.setText(label);row.setTextColor(color);row.setTextSize(15);row.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),0,dp(12),0);row.setBackgroundColor(Color.TRANSPARENT);
        android.widget.LinearLayout.LayoutParams params=new android.widget.LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,dp(48));panel.addView(row,params);
        row.setOnClickListener(v->{dialog.dismiss();action.run();});
    }

    private boolean isAppFavorite(AppEntry app){return getSharedPreferences("launcher",MODE_PRIVATE).getStringSet("favorite_apps",java.util.Collections.emptySet()).contains(app.packageName);}
    private void toggleAppFavorite(AppEntry app){
        java.util.Set<String> saved=getSharedPreferences("launcher",MODE_PRIVATE).getStringSet("favorite_apps",java.util.Collections.emptySet());
        java.util.Set<String> next=new java.util.LinkedHashSet<>(saved);boolean added;
        if(next.contains(app.packageName)){next.remove(app.packageName);added=false;}else{next.add(app.packageName);added=true;}
        getSharedPreferences("launcher",MODE_PRIVATE).edit().putStringSet("favorite_apps",next).apply();
        if(dashboard!=null)dashboard.reloadApps();Toast.makeText(this,added?"Added to favorites":"Removed from favorites",Toast.LENGTH_SHORT).show();
    }
    private void addAppToMediaSources(AppEntry app){
        android.content.SharedPreferences store=getSharedPreferences("launcher",MODE_PRIVATE);
        java.util.Set<String> packages=new java.util.LinkedHashSet<>();
        String raw=store.getString("media_apps","");
        if(raw.isEmpty())for(AppEntry selected:selectedMediaApps())packages.add(selected.packageName);else for(String pkg:raw.split(","))if(!pkg.trim().isEmpty())packages.add(pkg.trim());
        boolean added=packages.add(app.packageName);store.edit().putString("media_apps",android.text.TextUtils.join(",",packages)).apply();
        if(dashboard!=null)dashboard.reloadMediaApps();Toast.makeText(this,added?"Added to media sources":"Already in media sources",Toast.LENGTH_SHORT).show();
    }
    private void openAppInformation(AppEntry app){
        try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.packageName)));}
        catch(Throwable error){Toast.makeText(this,"App information unavailable",Toast.LENGTH_SHORT).show();}
    }
    private void requestAppUninstall(AppEntry app){
        try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.packageName)));}
        catch(Throwable error){Toast.makeText(this,"This app cannot be uninstalled",Toast.LENGTH_LONG).show();}
    }
    public void toggleAppFavoriteFromDashboard(AppEntry app){toggleAppFavorite(app);}
    public void openAppInformationFromDashboard(AppEntry app){openAppInformation(app);}
    public void requestAppUninstallFromDashboard(AppEntry app){requestAppUninstall(app);}


    public void openSettingsScreen(){
        try{startActivity(new Intent(this,SettingsActivity.class));}
        catch(Throwable ignored){openSystemSettings();}
    }
    public void navigationSection(int tab){
        if(mapPanel instanceof NavigationPanel)((NavigationPanel)mapPanel).showSection(tab);
    }
    public void refreshWeather(){fetchWeather();}
    public void openObdSetup(){ObdSetup.show(this);}
    public String routeMetric(boolean arrival){return mapPanel instanceof NavigationPanel?((NavigationPanel)mapPanel).routeMetric(arrival):arrival?"--:--":"-- MI";}

    public float mediaVolumeLevel(){try{android.media.AudioManager m=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);return m.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)/(float)Math.max(1,m.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));}catch(Throwable ignored){return 0;}}
    public void setMediaVolumeLevel(float level){try{android.media.AudioManager m=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);int max=Math.max(1,m.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)),value=Math.max(0,Math.min(max,Math.round(level*max)));m.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,value,0);if(autoVolumeEnabled())getSharedPreferences("launcher",MODE_PRIVATE).edit().putInt("auto_volume_base",value).apply();lastCompensatedVolume=value;}catch(Throwable ignored){}}
    public String audioRouteName(){try{android.media.AudioManager m=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);for(android.media.AudioDeviceInfo d:m.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)){int t=d.getType();if(t==android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP||t==android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO||t==android.media.AudioDeviceInfo.TYPE_USB_DEVICE||t==android.media.AudioDeviceInfo.TYPE_USB_HEADSET||t==android.media.AudioDeviceInfo.TYPE_HDMI){CharSequence n=d.getProductName();return(n==null?"CONNECTED AUDIO":n.toString()).toUpperCase(Locale.US);}}}catch(Throwable ignored){}return "DEVICE SPEAKERS";}
    public void openAudioRouteSettings(){try{startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Throwable ignored){openSystemSettings();}}
    public void openSoundSettings(){try{startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));}catch(Throwable ignored){openSystemSettings();}}
    public int mediaProfile(){return getSharedPreferences("launcher",MODE_PRIVATE).getInt("media_profile",0);}
    public void setMediaProfile(int profile){getSharedPreferences("launcher",MODE_PRIVATE).edit().putInt("media_profile",Math.max(0,Math.min(2,profile))).apply();if(profile==1&&mediaVolumeLevel()>.42f)setMediaVolumeLevel(.42f);else if(profile==2&&mediaVolumeLevel()>.68f)setMediaVolumeLevel(.68f);if(dashboard!=null)dashboard.invalidate();}
    public boolean autoVolumeEnabled(){return getSharedPreferences("launcher",MODE_PRIVATE).getBoolean("auto_volume",false);}
    public void toggleAutoVolume(){android.content.SharedPreferences st=getSharedPreferences("launcher",MODE_PRIVATE);boolean enabled=!st.getBoolean("auto_volume",false);android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);int current=am==null?0:am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);st.edit().putBoolean("auto_volume",enabled).putInt("auto_volume_base",current).apply();lastCompensatedVolume=-1;if(dashboard!=null)dashboard.invalidate();}
    public void applySpeedCompensation(float mph){if(!autoVolumeEnabled())return;try{android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);int max=Math.max(1,am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)),base=getSharedPreferences("launcher",MODE_PRIVATE).getInt("auto_volume_base",am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));int step=mph>=70?3:mph>=55?2:mph>=35?1:0,desired=Math.min(max,base+step);if(desired!=lastCompensatedVolume){am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,desired,0);lastCompensatedVolume=desired;}}catch(Throwable ignored){}}

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

    public void openQuickPanel(int kind){
        try{
            Intent intent;
            if(kind==0){
                intent=android.os.Build.VERSION.SDK_INT>=29?
                    new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY):
                    new Intent(Settings.ACTION_WIFI_SETTINGS);
            }else if(kind==1)intent=new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            else if(kind==2)intent=new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            else intent=new Intent(Settings.ACTION_SOUND_SETTINGS);
            startActivity(intent);
        }catch(Throwable error){openSystemSettings();}
    }

    public void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Throwable ignored) { }
    }

    public List<AppEntry> installedApps() {
        PackageManager pm=getPackageManager();java.util.LinkedHashMap<String,AppEntry> found=new java.util.LinkedHashMap<>();
        String[] categories={Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_LEANBACK_LAUNCHER};
        for(String category:categories){
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo r:pm.queryIntentActivities(query,0)){
                ActivityInfo a=r.activityInfo;if(a==null||a.packageName==null||a.packageName.equals(getPackageName())||isAlternateMap(a.packageName)||found.containsKey(a.packageName))continue;
                try{CharSequence label=r.loadLabel(pm);found.put(a.packageName,new AppEntry(label==null?a.packageName:label.toString(),a.packageName,a.name,r.loadIcon(pm)));}catch(Throwable ignored){}
            }
        }
        try{
            for(android.content.pm.ApplicationInfo info:pm.getInstalledApplications(0)){
                if(info.packageName.equals(getPackageName())||isAlternateMap(info.packageName)||found.containsKey(info.packageName))continue;
                Intent launch=pm.getLaunchIntentForPackage(info.packageName);if(launch==null)launch=pm.getLeanbackLaunchIntentForPackage(info.packageName);
                android.content.ComponentName component=launch==null?null:launch.getComponent();if(component==null)continue;
                CharSequence label=pm.getApplicationLabel(info);found.put(info.packageName,new AppEntry(label==null?info.packageName:label.toString(),info.packageName,component.getClassName(),info.loadIcon(pm)));
            }
        }catch(Throwable ignored){}
        List<AppEntry> result=new ArrayList<>(found.values());Collections.sort(result,Comparator.comparing(x->x.label.toLowerCase(Locale.US)));return result;
    }
    private boolean isAlternateMap(String packageName){return "com.waze".equals(packageName)||"com.here.app.maps".equals(packageName)||"com.mapquest.android.ace".equals(packageName)||"com.tomtom.gplay.navapp".equals(packageName);}

    public void launch(AppEntry app) {
        try {
            Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.packageName,app.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch(Throwable first) {
            try{Intent fallback=getPackageManager().getLaunchIntentForPackage(app.packageName);if(fallback==null)fallback=getPackageManager().getLeanbackLaunchIntentForPackage(app.packageName);if(fallback!=null){fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(fallback);}}
            catch(Throwable ignored){}
        }
    }}
