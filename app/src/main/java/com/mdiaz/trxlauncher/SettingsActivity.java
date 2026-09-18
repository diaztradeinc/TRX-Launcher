package com.mdiaz.trxlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int BG=0xff06080b, PANEL=0xff111419, RED=0xffff2338, WHITE=0xfff5f5f7;
    private SharedPreferences prefs;
    private Spinner navigation;
    private Spinner media;
    private EditText home;
    private EditText work;
    private EditText coolantWarning;
    private EditText intakeWarning;
    private EditText voltageWarning;
    private android.widget.Switch alerts;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("launcher",MODE_PRIVATE);

        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28),dp(26),dp(28),dp(40));
        scroll.addView(root);

        TextView title=text("TRX LAUNCHER SETTINGS",28,WHITE,true);
        root.addView(title);
        TextView subtitle=text("Personalize navigation, media and destinations",15,0xffaeb2ba,false);
        subtitle.setPadding(0,dp(4),0,dp(24));
        root.addView(subtitle);

        navigation=spinner(new String[]{"Google Maps","Waze"});
        addField(root,"PREFERRED NAVIGATION",navigation);
        navigation.setSelection(prefs.getInt("nav_choice",0));

        media=spinner(new String[]{"Spotify","YouTube Music","Apple Music","System Default"});
        addField(root,"PREFERRED MEDIA",media);
        media.setSelection(prefs.getInt("media_choice",0));

        home=edit(prefs.getString("home_destination","Home"));
        addField(root,"HOME DESTINATION",home);

        work=edit(prefs.getString("work_destination","Work"));
        addField(root,"WORK DESTINATION",work);

        TextView performanceTitle=text("LIVE PERFORMANCE ALERTS",18,RED,true);
        LinearLayout.LayoutParams performanceTitleParams=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        performanceTitleParams.topMargin=dp(30);
        root.addView(performanceTitle,performanceTitleParams);

        alerts=new android.widget.Switch(this);
        alerts.setText("Enable visual gauge warnings");
        alerts.setTextColor(WHITE);alerts.setTextSize(16);
        alerts.setChecked(prefs.getBoolean("performance_alerts",true));
        addField(root,"WARNING DISPLAY",alerts);

        coolantWarning=numberEdit(prefs.getFloat("warn_coolant",235f));
        addField(root,"COOLANT WARNING (°F)",coolantWarning);
        intakeWarning=numberEdit(prefs.getFloat("warn_intake",170f));
        addField(root,"INTAKE TEMPERATURE WARNING (°F)",intakeWarning);
        voltageWarning=numberEdit(prefs.getFloat("warn_voltage",11.8f));
        addField(root,"LOW-VOLTAGE WARNING (V)",voltageWarning);

        TextView warningNote=text("Display warnings only — they do not replace factory vehicle warnings.",12,0xff8f949d,false);
        warningNote.setPadding(0,dp(10),0,0);root.addView(warningNote);

        Button save=new Button(this);
        save.setText("SAVE SETTINGS");
        save.setTextColor(WHITE);
        save.setTextSize(17);
        save.setAllCaps(false);
        save.setBackgroundColor(RED);
        LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(62));
        saveParams.topMargin=dp(26);
        root.addView(save,saveParams);
        save.setOnClickListener(v->saveAndClose());

        Button mediaAccess=new Button(this);
        mediaAccess.setText("MEDIA NOTIFICATION ACCESS");
        mediaAccess.setTextColor(WHITE);
        mediaAccess.setTextSize(15);
        mediaAccess.setAllCaps(false);
        mediaAccess.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams accessParams=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(58));
        accessParams.topMargin=dp(14);
        root.addView(mediaAccess,accessParams);
        mediaAccess.setOnClickListener(v->MediaBridge.requestAccess(this));

        Button clearRuns=new Button(this);
        clearRuns.setText("CLEAR 0–60 HISTORY");
        clearRuns.setTextColor(0xffff6573);clearRuns.setTextSize(15);clearRuns.setAllCaps(false);
        clearRuns.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams clearParams=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(58));
        clearParams.topMargin=dp(12);root.addView(clearRuns,clearParams);
        clearRuns.setOnClickListener(v->{
            prefs.edit().remove("performance_runs").apply();
            android.widget.Toast.makeText(this,"Performance history cleared",android.widget.Toast.LENGTH_SHORT).show();
        });

        setContentView(scroll);
    }

    private void saveAndClose(){
        prefs.edit()
            .putInt("nav_choice",navigation.getSelectedItemPosition())
            .putInt("media_choice",media.getSelectedItemPosition())
            .putString("home_destination",home.getText().toString().trim())
            .putString("work_destination",work.getText().toString().trim())
            .putBoolean("performance_alerts",alerts.isChecked())
            .putFloat("warn_coolant",number(coolantWarning,235f))
            .putFloat("warn_intake",number(intakeWarning,170f))
            .putFloat("warn_voltage",number(voltageWarning,11.8f))
            .apply();
        setResult(RESULT_OK,new Intent());
        finish();
    }

    private void addField(LinearLayout root,String label,View control){
        TextView heading=text(label,14,0xffaeb2ba,true);
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin=dp(18);
        root.addView(heading,headingParams);
        LinearLayout.LayoutParams controlParams=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(58));
        controlParams.topMargin=dp(8);
        root.addView(control,controlParams);
    }

    private Spinner spinner(String[] values){
        Spinner result=new Spinner(this);
        result.setBackgroundColor(PANEL);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(
            this,android.R.layout.simple_spinner_dropdown_item,values){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){
                TextView view=(TextView)super.getView(position,convertView,parent);
                view.setTextColor(WHITE);view.setTextSize(17);view.setPadding(dp(18),0,dp(18),0);
                return view;
            }
        };
        result.setAdapter(adapter);
        return result;
    }

    private EditText edit(String value){
        EditText result=new EditText(this);
        result.setText(value);
        result.setTextColor(WHITE);
        result.setHintTextColor(0xff6d727b);
        result.setTextSize(17);
        result.setSingleLine(true);
        result.setPadding(dp(18),0,dp(18),0);
        result.setBackgroundColor(PANEL);
        return result;
    }

    private EditText numberEdit(float value){
        EditText result=edit(value==Math.round(value)?String.valueOf(Math.round(value)):String.format(java.util.Locale.US,"%.1f",value));
        result.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return result;
    }

    private float number(EditText field,float fallback){
        try{return Float.parseFloat(field.getText().toString().trim());}
        catch(Throwable ignored){return fallback;}
    }

    private TextView text(String value,int size,int color,boolean bold){
        TextView result=new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        result.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)result.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return result;
    }

    private int dp(int value){
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
}
