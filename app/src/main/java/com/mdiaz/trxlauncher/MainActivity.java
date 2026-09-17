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
import java.io.BufferedReader;\nimport java.io.InputStreamReader;\nimport java.net.HttpURLConnection;\nimport java.net.URL;\nimport java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private DashboardView dashboard;
    private volatile float speedMph;\n    private volatile String weatherTemp = "--°";\n    private volatile String weatherCondition = "WEATHER UNAVAILABLE";
    private LocationManager locationManager;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setNavigationBarColor(0xff050607);
            getWindow().setStatusBarColor(0xff050607);
            dashboard = new DashboardView(this);
            setContentView(dashboard);
            startGps();\n            fetchWeather();
        } catch (Throwable error) { showStartupError(error); }
    }

    @Override protected void onResume() {\n        super.onResume();\n        MediaBridge.ensureConnected(this);\n        if (dashboard != null) dashboard.postInvalidate();\n    }\n\n    private void showStartupError(Throwable error) {
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

    public float speedMph() { return speedMph; }\n    public String weatherTemp() { return weatherTemp; }\n    public String weatherCondition() { return weatherCondition; }\n\n    private void fetchWeather() {\n        new Thread(() -> {\n            HttpURLConnection connection = null;\n            try {\n                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=40.33&longitude=-74.58&current=temperature_2m,weather_code&temperature_unit=fahrenheit");\n                connection = (HttpURLConnection)url.openConnection();\n                connection.setConnectTimeout(6000);\n                connection.setReadTimeout(6000);\n                connection.setRequestProperty("User-Agent","TRX-Launcher/0.3.2");\n                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));\n                StringBuilder json = new StringBuilder();\n                String line;\n                while ((line = reader.readLine()) != null) json.append(line);\n                int current = json.indexOf("\\\"current\\\":");\n                double temp = numberAfter(json,"\\\"temperature_2m\\\":",current);\n                int code = (int)numberAfter(json,"\\\"weather_code\\\":",current);\n                weatherTemp = Math.round(temp) + "°";\n                weatherCondition = weatherName(code);\n            } catch (Throwable ignored) {\n                weatherTemp = "--°";\n                weatherCondition = "WEATHER UNAVAILABLE";\n            } finally {\n                if (connection != null) connection.disconnect();\n                if (dashboard != null) dashboard.postInvalidate();\n            }\n        },"trx-weather").start();\n    }\n\n    private static double numberAfter(StringBuilder text,String key,int from) {\n        int at=text.indexOf(key,Math.max(0,from));\n        if(at<0)return 0;at+=key.length();int end=at;\n        while(end<text.length()&&"-.0123456789".indexOf(text.charAt(end))>=0)end++;\n        try{return Double.parseDouble(text.substring(at,end));}catch(Throwable ignored){return 0;}\n    }\n\n    private static String weatherName(int code) {\n        if(code==0)return "CLEAR";if(code<=3)return "PARTLY CLOUDY";if(code<=48)return "FOG";\n        if(code<=67)return "RAIN";if(code<=77)return "SNOW";if(code<=82)return "SHOWERS";\n        if(code<=86)return "SNOW SHOWERS";return "THUNDERSTORMS";\n    }

    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            speedMph = location.hasSpeed() ? Math.max(0,location.getSpeed()*2.2369363f) : 0;
            if (dashboard != null) dashboard.onSpeedChanged(speedMph);
        }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
        @Override public void onStatusChanged(String provider,int status,Bundle extras) { }
    };

    private void startGps() {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},41);
                return;
            }
            locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,250,0,gpsListener); }
                catch (Throwable ignored) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,500,0,gpsListener);
                }
            }
        } catch (Throwable ignored) { }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode == 41 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            startGps();
    }

    @Override protected void onDestroy() {
        try { if (locationManager != null) locationManager.removeUpdates(gpsListener); }
        catch (Throwable ignored) { }
        super.onDestroy();
    }

    public void openNavigation() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q=Home"));
            if (i.resolveActivity(getPackageManager()) == null)
                i = new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q=Home"));
            startActivity(i);
        } catch (Throwable ignored) { openSystemSettings(); }
    }

    public void openMedia() {
        if (launchPackage("com.spotify.music")) return;
        if (launchPackage("com.google.android.apps.youtube.music")) return;
        if (launchPackage("com.apple.android.music")) return;
        try { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)); }
        catch (Throwable ignored) { openSystemSettings(); }
    }

    public void openMediaSource(int source) {
        switch (source) {
            case 0: if (!launchPackage("com.spotify.music")) openMedia(); break;
            case 1: if (!launchPackage("com.google.android.apps.youtube.music") &&
                !launchPackage("com.google.android.youtube")) openMedia(); break;
            case 2: if (!launchPackage("com.apple.android.music")) openMedia(); break;
            case 3:
                try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
                catch (Throwable ignored) { openSystemSettings(); }
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
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> rows = getPackageManager().queryIntentActivities(query,0);
        List<AppEntry> result = new ArrayList<>();
        for (ResolveInfo r : rows) {
            ActivityInfo a = r.activityInfo;
            if (a == null || a.packageName == null || a.packageName.equals(getPackageName())) continue;
            try {
                CharSequence label = r.loadLabel(getPackageManager());
                result.add(new AppEntry(label == null ? a.packageName : label.toString(),
                    a.packageName,a.name,r.loadIcon(getPackageManager())));
            } catch (Throwable ignored) { }
        }
        Collections.sort(result,Comparator.comparing(x -> x.label.toLowerCase()));
        return result;
    }

    public void launch(AppEntry app) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName,app.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) { }
    }
}
