package com.mdiaz.trxlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int BG=0xff030507,PANEL=0xff0b0e12,CARD=0xff10141a,WHITE=0xfff5f5f7,MUTED=0xffaeb2ba,GREEN=0xff50dc83;
    private SharedPreferences prefs;
    private Spinner navigation,media,displayMode,startupPage;
    private EditText home,work,coolantWarning,intakeWarning,voltageWarning,customHex;
    private android.widget.Switch alerts,onlineArtwork;
    private SeekBar iconSize;
    private TextView iconSizeValue;
    private ImageView themePreview;
    private TextView themePreviewTitle;
    private int selectedTheme,accent;
    private final java.util.ArrayList<Button> themeButtons=new java.util.ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("launcher",MODE_PRIVATE);
        selectedTheme=prefs.getInt("theme_choice",1);accent=currentAccent();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(22),dp(24),dp(44));scroll.addView(root);

        TextView eyebrow=text("// TRX COMMAND SYSTEM  •  v1.8",12,accent,true);root.addView(eyebrow);
        TextView title=text("SETTINGS COMMAND CENTER",30,WHITE,true);title.setPadding(0,dp(4),0,0);root.addView(title);
        TextView subtitle=text("Personalize the cockpit, startup behavior, apps and vehicle alerts.",14,MUTED,false);subtitle.setPadding(0,dp(5),0,dp(18));root.addView(subtitle);

        LinearLayout health=horizontal();health.addView(statusCard("HOME","TRX DEFAULT",isDefaultHome()),weight());
        health.addView(statusCard("MEDIA",MediaBridge.hasAccess(this)?"CONNECTED":"ACCESS NEEDED",MediaBridge.hasAccess(this)),weight());
        boolean gps=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        health.addView(statusCard("LOCATION",gps?"READY":"ACCESS NEEDED",gps),weight());root.addView(health);

        LinearLayout appearance=section(root,"// APPEARANCE","Choose a complete cockpit personality. Truck paint, landscape and accents move together.");
        TextView themeLabel=label("THEME PICKER");appearance.addView(themeLabel);
        LinearLayout themes=horizontal();String[] names={"TRX RED","BAJA AMBER","STEALTH","OEM BLUE","CUSTOM"};
        for(int i=0;i<names.length;i++){final int index=i;Button button=new Button(this);button.setText(names[i]);button.setTextSize(11);button.setTextColor(WHITE);button.setAllCaps(false);button.setPadding(dp(3),0,dp(3),0);button.setOnClickListener(v->{selectedTheme=index;accent=themeColor(index);updateThemeButtons();styleAccentControls();updateThemePreview();});themeButtons.add(button);themes.addView(button,weightHeight(72));}
        appearance.addView(themes);updateThemeButtons();

        FrameLayout previewFrame=new FrameLayout(this);previewFrame.setBackground(background(0xff07090c,0xff3b424c,12));
        themePreview=new ImageView(this);themePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);previewFrame.addView(themePreview,new FrameLayout.LayoutParams(-1,-1));
        android.view.View shade=new android.view.View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x10000000,0xd9000000}));previewFrame.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        themePreviewTitle=text("",14,WHITE,true);themePreviewTitle.setGravity(Gravity.BOTTOM|Gravity.LEFT);themePreviewTitle.setPadding(dp(16),0,dp(16),dp(13));previewFrame.addView(themePreviewTitle,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(-1,dp(150));previewParams.topMargin=dp(8);appearance.addView(previewFrame,previewParams);updateThemePreview();

        customHex=edit(String.format(java.util.Locale.US,"#%06X",prefs.getInt("custom_accent",0xffff2338)&0xffffff));
        customHex.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(selectedTheme==4){accent=themeColor(4);updateThemeButtons();styleAccentControls();updateThemePreview();}}public void afterTextChanged(android.text.Editable s){}});
        addControl(appearance,"CUSTOM ACCENT HEX",customHex);
        displayMode=spinner(new String[]{"Automatic day / night","Day cockpit","Night cockpit"});
        displayMode.setSelection(prefs.getInt("display_mode",0));addControl(appearance,"DISPLAY MODE",displayMode);
        iconSize=new SeekBar(this);iconSize.setMax(40);iconSize.setProgress(Math.max(0,Math.min(40,prefs.getInt("app_icon_percent",100)-80)));
        iconSizeValue=text((iconSize.getProgress()+80)+"%",14,WHITE,true);iconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int progress,boolean fromUser){iconSizeValue.setText((progress+80)+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        LinearLayout iconRow=horizontal();iconRow.addView(iconSize,new LinearLayout.LayoutParams(0,dp(58),1));LinearLayout.LayoutParams valueParams=new LinearLayout.LayoutParams(dp(70),dp(58));iconRow.addView(iconSizeValue,valueParams);addControl(appearance,"APP ICON SIZE",iconRow);

        LinearLayout behavior=section(root,"// STARTUP & DEFAULTS","Control what appears when the Ottocast wakes up.");
        startupPage=spinner(new String[]{"Resume last page","Home","Navigation","Media","Performance","Apps"});
        int startup=prefs.getInt("startup_page",-1);startupPage.setSelection(startup<0?0:startup+1);addControl(behavior,"STARTUP PAGE",startupPage);
        navigation=spinner(new String[]{"Google Maps","Waze"});navigation.setSelection(prefs.getInt("nav_choice",0));addControl(behavior,"PREFERRED NAVIGATION",navigation);
        media=spinner(new String[]{"Spotify","YouTube Music","Apple Music","System Default"});media.setSelection(prefs.getInt("media_choice",0));addControl(behavior,"PREFERRED MEDIA",media);
        home=edit(prefs.getString("home_destination","Home"));addControl(behavior,"HOME DESTINATION",home);
        work=edit(prefs.getString("work_destination","Work"));addControl(behavior,"WORK DESTINATION",work);

        LinearLayout mediaPanel=section(root,"// MEDIA SYSTEM","Manage online queue artwork and the local cover cache.");
        onlineArtwork=toggle("Look up missing Up Next covers",prefs.getBoolean("online_artwork",true));addControl(mediaPanel,"QUEUE ARTWORK",onlineArtwork);
        Button clearArt=action("CLEAR ARTWORK CACHE",false,false);mediaPanel.addView(clearArt,buttonParams());clearArt.setOnClickListener(v->{MediaBridge.clearArtworkCache();toast("Artwork cache cleared");});

        LinearLayout performance=section(root,"// PERFORMANCE SAFETY","Visual reminders only; factory vehicle warnings always take priority.");
        alerts=toggle("Enable visual gauge warnings",prefs.getBoolean("performance_alerts",true));addControl(performance,"WARNING DISPLAY",alerts);
        coolantWarning=numberEdit(prefs.getFloat("warn_coolant",235f));addControl(performance,"COOLANT WARNING (°F)",coolantWarning);
        intakeWarning=numberEdit(prefs.getFloat("warn_intake",170f));addControl(performance,"INTAKE TEMPERATURE WARNING (°F)",intakeWarning);
        voltageWarning=numberEdit(prefs.getFloat("warn_voltage",11.8f));addControl(performance,"LOW-VOLTAGE WARNING (V)",voltageWarning);

        LinearLayout system=section(root,"// SYSTEM TOOLS","Launcher role, permissions, history and first-run controls.");
        LinearLayout systemButtons=horizontal();Button launcher=action("DEFAULT LAUNCHER",false,false),permissions=action("PERMISSION HEALTH",false,false);systemButtons.addView(launcher,weightHeight(62));systemButtons.addView(permissions,weightHeight(62));system.addView(systemButtons);
        launcher.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Throwable ignored){startActivity(new Intent(Settings.ACTION_SETTINGS));}});
        permissions.setOnClickListener(v->openMissingPermission());
        Button clearRuns=action("CLEAR 0–60 HISTORY",false,false);system.addView(clearRuns,buttonParams());clearRuns.setOnClickListener(v->{prefs.edit().remove("performance_runs").apply();toast("Performance history cleared");});
        Button reset=action("RESET FIRST-RUN EXPERIENCE",false,true);system.addView(reset,buttonParams());reset.setOnClickListener(v->{prefs.edit().putBoolean("first_run_complete",false).apply();startActivity(new Intent(this,FirstRunActivity.class));finish();});

        Button save=action("SAVE & RETURN TO TRX",true,false);LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(-1,dp(72));saveParams.topMargin=dp(18);root.addView(save,saveParams);save.setOnClickListener(v->saveAndClose());
        TextView footer=text("BUILT TO DOMINATE  //  SETTINGS APPLY AFTER RETURN",11,MUTED,true);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(16),0,0);root.addView(footer);
        setContentView(scroll);styleAccentControls();
    }

    private LinearLayout section(LinearLayout root,String heading,String subtitle){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(20),dp(18),dp(20),dp(20));panel.setBackground(panelBackground());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(16);root.addView(panel,params);
        panel.addView(text(heading,19,WHITE,true));TextView note=text(subtitle,12,MUTED,false);note.setPadding(0,dp(3),0,dp(10));panel.addView(note);return panel;
    }

    private View statusCard(String title,String value,boolean ready){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(12),dp(12),dp(12));card.setBackground(background(CARD,0xff30363e,12));
        card.addView(text(title,10,MUTED,true));TextView state=text(value,13,ready?GREEN:0xffff6573,true);state.setPadding(0,dp(4),0,0);card.addView(state);return card;
    }

    private void updateThemeButtons(){
        for(int i=0;i<themeButtons.size();i++){int color=themeColor(i);int fill=darken(color,i==selectedTheme?.30f:.13f);themeButtons.get(i).setBackground(background(fill,i==selectedTheme?color:0xff343a42,12));}
    }
    private void styleAccentControls(){
        if(iconSize!=null){iconSize.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));iconSize.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));}
    }

    private void updateThemePreview(){
        if(themePreview==null)return;
        themePreview.clearColorFilter();
        int image=R.drawable.trx_hero_banner;String title="TRX RED  //  SUNSET RIDGE";
        if(selectedTheme==1){image=R.drawable.trx_hero_baja;title="BAJA AMBER  //  DESERT DUSK";}
        else if(selectedTheme==2){image=R.drawable.trx_hero_stealth;title="STEALTH BLACK  //  MOON RIDGE";}
        else if(selectedTheme==3){image=R.drawable.trx_hero_blue;title="OEM BLUE  //  GLACIER NIGHT";}
        else if(selectedTheme==4){title="CUSTOM  //  NIGHT HORIZON";themePreview.setColorFilter(accent,android.graphics.PorterDuff.Mode.OVERLAY);}
        themePreview.setImageResource(image);if(themePreviewTitle!=null){themePreviewTitle.setText(title);themePreviewTitle.setTextColor(accent);}
    }

    private int themeColor(int index){if(index==1)return 0xffff9f1a;if(index==2)return 0xffd9dde3;if(index==3)return 0xff438cff;if(index==4){try{return Color.parseColor(customHex==null?prefs.getString("custom_hex","#FF2338"):customHex.getText().toString().trim());}catch(Throwable ignored){return prefs.getInt("custom_accent",0xffff2338);}}return 0xffff2338;}
    private int currentAccent(){return themeColor(selectedTheme);}
    private int darken(int color,float factor){return Color.rgb(Math.round(Color.red(color)*factor),Math.round(Color.green(color)*factor),Math.round(Color.blue(color)*factor));}

    private void saveAndClose(){
        int custom=0xffff2338;try{custom=Color.parseColor(customHex.getText().toString().trim());}catch(Throwable ignored){toast("Custom color must look like #FF2338");}
        int startup=startupPage.getSelectedItemPosition()==0?-1:startupPage.getSelectedItemPosition()-1;
        prefs.edit().putInt("theme_choice",selectedTheme).putInt("custom_accent",custom).putString("custom_hex",customHex.getText().toString().trim())
            .putInt("display_mode",displayMode.getSelectedItemPosition()).putInt("app_icon_percent",iconSize.getProgress()+80).putInt("startup_page",startup)
            .putInt("nav_choice",navigation.getSelectedItemPosition()).putInt("media_choice",media.getSelectedItemPosition())
            .putString("home_destination",home.getText().toString().trim()).putString("work_destination",work.getText().toString().trim())
            .putBoolean("online_artwork",onlineArtwork.isChecked()).putBoolean("performance_alerts",alerts.isChecked())
            .putFloat("warn_coolant",number(coolantWarning,235f)).putFloat("warn_intake",number(intakeWarning,170f)).putFloat("warn_voltage",number(voltageWarning,11.8f)).apply();
        setResult(RESULT_OK,new Intent());finish();
    }

    private void openMissingPermission(){
        if(!MediaBridge.hasAccess(this)){MediaBridge.requestAccess(this);return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},340);return;}
        toast("Launcher permissions are healthy");
    }
    private boolean isDefaultHome(){try{Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_HOME);ResolveInfo r=getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY);return r!=null&&r.activityInfo!=null&&getPackageName().equals(r.activityInfo.packageName);}catch(Throwable ignored){return false;}}

    private void addControl(LinearLayout root,String heading,View control){TextView label=label(heading);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);root.addView(label,lp);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(58));cp.topMargin=dp(6);root.addView(control,cp);}
    private TextView label(String value){return text(value,11,MUTED,true);}
    private LinearLayout horizontal(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);return row;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(72),1);p.setMargins(dp(4),0,dp(4),0);return p;}
    private LinearLayout.LayoutParams weightHeight(int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1);p.setMargins(dp(4),dp(5),dp(4),dp(5));return p;}
    private LinearLayout.LayoutParams buttonParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(60));p.topMargin=dp(10);return p;}

    private Spinner spinner(String[] values){
        Spinner result=new Spinner(this);result.setBackground(background(CARD,0xff343a42,10));
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){
            private void style(TextView v){v.setTextColor(WHITE);v.setTextSize(16);v.setPadding(dp(16),0,dp(16),0);v.setBackgroundColor(CARD);}
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convertView,parent);style(v);return v;}
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convertView,parent);style(v);v.setMinHeight(dp(52));return v;}
        };result.setAdapter(adapter);return result;
    }
    private EditText edit(String value){EditText r=new EditText(this);r.setText(value);r.setTextColor(WHITE);r.setHintTextColor(0xff6d727b);r.setTextSize(16);r.setSingleLine(true);r.setPadding(dp(16),0,dp(16),0);r.setBackground(background(CARD,0xff343a42,10));return r;}
    private EditText numberEdit(float value){EditText r=edit(value==Math.round(value)?String.valueOf(Math.round(value)):String.format(java.util.Locale.US,"%.1f",value));r.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);return r;}
    private android.widget.Switch toggle(String title,boolean checked){android.widget.Switch s=new android.widget.Switch(this);s.setText(title);s.setTextColor(WHITE);s.setTextSize(15);s.setChecked(checked);s.setPadding(dp(14),0,dp(14),0);s.setBackground(background(CARD,0xff343a42,10));return s;}
    private Button action(String title,boolean primary,boolean danger){Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setTextColor(danger?0xffff7682:WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setBackground(background(primary?darken(accent,.28f):CARD,primary?accent:danger?0xff7a1722:0xff3c434c,12));return b;}
    private TextView text(String value,int size,int color,boolean bold){TextView r=new TextView(this);r.setText(value);r.setTextSize(size);r.setTextColor(color);r.setGravity(Gravity.CENTER_VERTICAL);if(bold)r.setTypeface(Typeface.DEFAULT_BOLD);return r;}
    private GradientDrawable panelBackground(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff141920,0xff080a0e});g.setCornerRadius(dp(16));g.setStroke(dp(1),0xff3a414a);return g;}
    private GradientDrawable background(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private float number(EditText field,float fallback){try{return Float.parseFloat(field.getText().toString().trim());}catch(Throwable ignored){return fallback;}}
    private void toast(String value){android.widget.Toast.makeText(this,value,android.widget.Toast.LENGTH_SHORT).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
