package com.mdiaz.trxlauncher;
import android.content.Intent;import android.graphics.Color;import android.widget.*;
final class PerformanceScreen extends ScrollView{
    private final MainActivity a;PerformanceScreen(MainActivity c){super(c);a=c;setFillViewport(true);show(0);try{a.startForegroundService(new Intent(a,ObdService.class));}catch(Throwable ignored){}}
    private void show(int tab){removeAllViews();LinearLayout root=Ui.col(a);root.setPadding(24,18,24,22);addView(root);root.addView(Ui.text(a,"PRECISION PERFORMANCE",11,Ui.RED,true));root.addView(Ui.text(a,"Every heartbeat of the truck.",32,Color.WHITE,true));root.addView(Ui.text(a,"Live OBDLink MX+ data and a focused GPS acceleration timer.",14,0xffa5a8ae,false));root.addView(Ui.tabs(a,new String[]{"Live Data","0–60","Gauges","History"},tab,this::show),new LinearLayout.LayoutParams(-1,76));
        TextView state=Ui.text(a,"MX+  •  "+ObdService.status,14,ObdService.connected?0xff68d391:Ui.RED,true);root.addView(state);
        if(tab==0||tab==2){String[][] vals={{"RPM",ObdService.rpm,"RPM"},{"COOLANT",ObdService.coolant,"°F"},{"INTAKE",ObdService.intake,"°F"},{"BATTERY",ObdService.voltage,"V"},{"ENGINE LOAD",ObdService.load,"%"},{"TRANS TEMP","--","PID VERIFY"}};for(int x=0;x<vals.length;x+=3){LinearLayout row=Ui.row(a);for(int n=x;n<Math.min(x+3,vals.length);n++)row.addView(Ui.metric(a,vals[n][0],vals[n][1],vals[n][2]),new LinearLayout.LayoutParams(0,160,1));root.addView(row);}}
        else if(tab==1){root.addView(Ui.text(a,"0–60 GPS TIMER\n\n--.- s        READY\n\nTimer begins automatically above 1 MPH",28,Color.WHITE,true),new LinearLayout.LayoutParams(-1,360));}
        else root.addView(Ui.text(a,"Run history will appear after the first completed timed run.",18,Color.WHITE,false));}
}
