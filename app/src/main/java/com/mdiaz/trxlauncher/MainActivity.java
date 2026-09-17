package com.mdiaz.trxlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private DashboardView dashboard;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setNavigationBarColor(0xff050607);
            getWindow().setStatusBarColor(0xff050607);
            dashboard = new DashboardView(this);
            setContentView(dashboard);
        } catch (Throwable error) {
            showStartupError(error);
        }
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
        } catch (Throwable ignored) {
            finish();
        }
    }

    public void openNavigation() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Home"));
        if (i.resolveActivity(getPackageManager()) == null)
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Home"));
        startActivity(i);
    }

    public void openSystemSettings() {
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    public List<AppEntry> installedApps() {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> rows = getPackageManager().queryIntentActivities(query, 0);
        List<AppEntry> result = new ArrayList<>();
        for (ResolveInfo r : rows) {
            ActivityInfo a = r.activityInfo;
            if (a == null || a.packageName == null || a.packageName.equals(getPackageName())) continue;
            try {
                CharSequence label = r.loadLabel(getPackageManager());
                result.add(new AppEntry(label == null ? a.packageName : label.toString(),
                    a.packageName, a.name, r.loadIcon(getPackageManager())));
            } catch (Throwable ignored) { }
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
