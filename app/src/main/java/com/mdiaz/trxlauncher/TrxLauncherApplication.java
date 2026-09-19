package com.mdiaz.trxlauncher;

import android.app.Application;
import android.text.TextUtils;

import com.google.android.libraries.navigation.NavigationApi;

/** Initializes the Navigation SDK before any NavigationView or Navigator is created. */
public final class TrxLauncherApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        if (!TextUtils.isEmpty(BuildConfig.MAPS_API_KEY)) {
            NavigationApi.setApiKey(BuildConfig.MAPS_API_KEY);
        }
    }
}
