package com.mdiaz.trxlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private FrameLayout stage; private NavigationScreen navigation; private View[] pages; private int page;
    private final String[] pageNames={"⌂\nHOME","➤\nNAVIGATION","♫\nMEDIA","◴\nPERFORMANCE","▦\nAPPS"};
    @Override public void onCreate(Bundle state){super.onCreate(state);ThemeStore.apply(this);getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);buildShell(state);}
    private void buildShell(Bundle state){
        LinearLayout root=Ui.col(this);root.setBackgroundColor(Ui.BG);
        root.addView(header(),new LinearLayout.LayoutParams(-1,Ui.dp(this,78)));
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(bottomNav(),new LinearLayout.LayoutParams(-1,Ui.dp(this,92)));setContentView(root);
        navigation=new NavigationScreen(this,state);
        pages=new View[]{new HomeScreen(this),navigation,new MediaScreen(this),new PerformanceScreen(this),new AppsScreen(this)};
        for(View screen:pages){screen.setVisibility(View.GONE);stage.addView(screen,new FrameLayout.LayoutParams(-1,-1));}
        showPage(0);
    }
    private View header(){
        LinearLayout h=Ui.row(this);h.setPadding(Ui.dp(this,28),0,Ui.dp(this,18),0);h.setBackground(Ui.bg(0xff090a0c,0xff351116,0,this));
        TextView brand=Ui.text(this,"RAM   |   TRX LAUNCHER",22,Color.WHITE,true);h.addView(brand,new LinearLayout.LayoutParams(0,-1,1));
        TextView weather=Ui.text(this,"67° · PLAINSBORO, NJ",13,0xffa9acb2,true);h.addView(weather,new LinearLayout.LayoutParams(-2,-1));
        TextView clock=Ui.text(this,new SimpleDateFormat("h:mm a",Locale.US).format(new Date()),22,Color.WHITE,true);clock.setPadding(Ui.dp(this,22),0,Ui.dp(this,20),0);h.addView(clock);
        Button settings=Ui.button(this,"⚙",false);settings.setContentDescription("TRX Launcher settings");settings.setOnClickListener(v->showSettings());h.addView(settings,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,54)));return h;
    }
    private View bottomNav(){
        LinearLayout bar=Ui.row(this);bar.setPadding(Ui.dp(this,10),Ui.dp(this,7),Ui.dp(this,10),Ui.dp(this,7));bar.setBackgroundColor(0xff090a0b);
        for(int i=0;i<pageNames.length;i++){final int p=i;Button b=Ui.button(this,pageNames[i],i==0);b.setTag("nav"+i);b.setOnClickListener(v->showPage(p));bar.addView(b,new LinearLayout.LayoutParams(0,-1,1));}return bar;
    }
    public void showPage(int p){page=p;View oldSettings=stage.findViewWithTag("settings-screen");if(oldSettings!=null)stage.removeView(oldSettings);if(p==1){requestLocationIfNeeded();navigation.ensureNavigator();}if(p==3)requestBluetoothIfNeeded();for(int i=0;i<pages.length;i++)pages[i].setVisibility(i==p?View.VISIBLE:View.GONE);
        ViewGroup bottom=(ViewGroup)((ViewGroup)stage.getParent()).getChildAt(2);for(int i=0;i<bottom.getChildCount();i++)bottom.getChildAt(i).setBackground(Ui.bg(i==p?Ui.RED:Ui.CARD2,i==p?0:0xff30343a,12,this));
    }
    public void openNavigation(String destination){showPage(1);if(destination!=null&&!destination.isBlank())navigation.routeTo(destination);}
    private void showSettings(){for(View screen:pages)screen.setVisibility(View.GONE);View settings=new SettingsScreen(this);settings.setTag("settings-screen");stage.addView(settings,new FrameLayout.LayoutParams(-1,-1));}
    public void requestMediaAccess(){startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
    private void requestLocationIfNeeded(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);}
    private void requestBluetoothIfNeeded(){if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},42);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==41&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)navigation.ensureNavigator();}
    @Override protected void onStart(){super.onStart();if(navigation!=null)navigation.onStart();}
    @Override protected void onResume(){super.onResume();if(navigation!=null)navigation.onResume();}
    @Override protected void onPause(){if(navigation!=null)navigation.onPause();super.onPause();}
    @Override protected void onStop(){if(navigation!=null)navigation.onStop();super.onStop();}
    @Override protected void onDestroy(){if(navigation!=null)navigation.onDestroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){if(navigation!=null)navigation.onSave(out);super.onSaveInstanceState(out);}
}
