package com.mdiaz.trxlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private DashboardView dashboard;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setNavigationBarColor(0xff050607);
        getWindow().setStatusBarColor(0xff050607);
        enterImmersive();
        try {
            dashboard = new DashboardView(this);
            setContentView(dashboard);
        } catch (Throwable error) {
            android.widget.TextView diagnostic = new android.widget.TextView(this);
            diagnostic.setBackgroundColor(0xff050607);
            diagnostic.setTextColor(0xffff1d32);
            diagnostic.setTextSize(18f);
            diagnostic.setPadding(30,60,30,30);
            diagnostic.setText("TRX LAUNCHER STARTUP ERROR\n\n" + error.getClass().getName() + "\n" + String.valueOf(error.getMessage()));
            setContentView(diagnostic);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    private void enterImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    public void openNavigation() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Home"));
        if (i.resolveActivity(getPackageManager()) == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Home"));
        startActivity(i);
    }

    public void openSystemSettings() { startActivity(new Intent(Settings.ACTION_SETTINGS)); }

    public List<AppEntry> installedApps() {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> rows = getPackageManager().queryIntentActivities(query, 0);
        List<AppEntry> result = new ArrayList<>();
        for (ResolveInfo r : rows) {
            ActivityInfo a = r.activityInfo;
            if (a.packageName.equals(getPackageName())) continue;
            result.add(new AppEntry(r.loadLabel(getPackageManager()).toString(), a.packageName,
                a.name, r.loadIcon(getPackageManager())));
        }
        Collections.sort(result, Comparator.comparing(x -> x.label.toLowerCase()));
        return result;
    }

    public void launch(AppEntry app) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }
}
