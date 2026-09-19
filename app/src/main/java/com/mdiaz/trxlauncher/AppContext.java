package com.mdiaz.trxlauncher;
import android.app.Application;import android.content.Context;
public class AppContext extends Application{private static Context app;@Override public void onCreate(){super.onCreate();app=getApplicationContext();}static Context get(){return app;}}
