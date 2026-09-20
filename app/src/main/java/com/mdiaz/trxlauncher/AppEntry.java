package com.mdiaz.trxlauncher;

import android.graphics.drawable.Drawable;

public final class AppEntry {
    public final String label, packageName, activityName;
    public final Drawable icon;
    public AppEntry(String label, String packageName, String activityName, Drawable icon) {
        this.label=label; this.packageName=packageName; this.activityName=activityName; this.icon=icon;
    }
}
