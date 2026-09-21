package com.mdiaz.trxlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
    private Spinner media,displayMode,startupPage,backgroundStyle;
    private EditText home,work,coolantWarning,intakeWarning,voltageWarning,customHex;
    private android.widget.Switch alerts,onlineArtwork,reduceMotion,heroArtwork;
    private SeekBar iconSize,accentBrightness;
    private TextView iconSizeValue;
    private ImageView themePreview;
    private TextView themePreviewTitle;
    private int selectedTheme,accent;
    private final java.util.ArrayList<Button> themeButtons=new java.util.ArrayList<>();
    private final java.util.ArrayList<View> categoryPanels=new java.util.ArrayList<>();
    private final java.util.ArrayList<Button> categoryButtons=new java.util.ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("launcher",MODE_PRIVATE);
        selectedTheme=prefs.getInt("theme_choice",0);accent=currentAccent();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(8),dp(18),dp(8));root.setBackgroundColor(BG);root.setFitsSystemWindows(true);
        root.addView(settingsHeader(),new LinearLayout.LayoutParams(-1,dp(72)));

        LinearLayout cockpit=horizontal();LinearLayout.LayoutParams cockpitLp=new LinearLayout.LayoutParams(-1,0,1);cockpitLp.topMargin=dp(8);root.addView(cockpit,cockpitLp);
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);rail.setPadding(dp(8),dp(10),dp(8),dp(10));rail.setBackground(panelBackground());
        LinearLayout.LayoutParams railLp=new LinearLayout.LayoutParams(dp(178),-1);railLp.rightMargin=dp(12);cockpit.addView(rail,railLp);
        TextView railTitle=text("COMMANDS",11,MUTED,true);railTitle.setPadding(dp(10),0,0,dp(8));rail.addView(railTitle,new LinearLayout.LayoutParams(-1,dp(32)));
        FrameLayout stage=new FrameLayout(this);stage.setBackground(panelBackground());cockpit.addView(stage,new LinearLayout.LayoutParams(0,-1,1));

        LinearLayout appearance=category("// APPEARANCE","Choose a complete cockpit personality. Paint, landscape and accents move together.");
        appearance.addView(label("THEME PICKER"));
        LinearLayout themes=horizontal();String[] names={"TRX RED","BAJA AMBER","STEALTH SILVER","HYDRO BLUE","CUSTOM"};int[] themeArt={R.drawable.trx_hero_banner,R.drawable.trx_hero_baja,R.drawable.trx_hero_stealth,R.drawable.trx_hero_blue,R.drawable.trx_hero_banner};
        for(int i=0;i<names.length;i++){final int index=i;Button button=new Button(this);button.setText(names[i]);button.setTextSize(10);button.setTextColor(WHITE);button.setAllCaps(false);button.setGravity(Gravity.CENTER);button.setPadding(dp(4),dp(5),dp(4),dp(5));button.setCompoundDrawablePadding(dp(4));button.setCompoundDrawables(null,scaledDrawable(themeArt[i],112,58),null,null);button.setOnClickListener(v->{selectedTheme=index;accent=themeColor(index);updateThemeButtons();styleAccentControls();updateThemePreview();});themeButtons.add(button);themes.addView(button,weightHeight(112));}
        appearance.addView(themes);updateThemeButtons();

        FrameLayout previewFrame=new FrameLayout(this);previewFrame.setBackground(background(0xff07090c,0xff3b424c,12));
        themePreview=new ImageView(this);themePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);previewFrame.addView(themePreview,new FrameLayout.LayoutParams(-1,-1));
        android.view.View shade=new android.view.View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x10000000,0xd9000000}));previewFrame.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        themePreviewTitle=text("",14,WHITE,true);themePreviewTitle.setGravity(Gravity.BOTTOM|Gravity.LEFT);themePreviewTitle.setPadding(dp(16),0,dp(16),dp(13));previewFrame.addView(themePreviewTitle,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(-1,dp(162));previewParams.topMargin=dp(7);appearance.addView(previewFrame,previewParams);updateThemePreview();

        customHex=edit(String.format(java.util.Locale.US,"#%06X",prefs.getInt("custom_accent",0xffff2338)&0xffffff));
        customHex.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(selectedTheme==4){accent=themeColor(4);updateThemeButtons();styleAccentControls();updateThemePreview();}}public void afterTextChanged(android.text.Editable s){}});
        LinearLayout appearanceControls=horizontal();LinearLayout leftControls=new LinearLayout(this),rightControls=new LinearLayout(this);leftControls.setOrientation(LinearLayout.VERTICAL);rightControls.setOrientation(LinearLayout.VERTICAL);
        appearanceControls.addView(leftControls,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rc=new LinearLayout.LayoutParams(0,-2,1);rc.leftMargin=dp(8);appearanceControls.addView(rightControls,rc);appearance.addView(appearanceControls);
        addControl(leftControls,"CUSTOM ACCENT HEX",customHex);
        displayMode=spinner(new String[]{"Automatic day / night","Day cockpit","Night cockpit"});
        displayMode.setSelection(prefs.getInt("display_mode",0));addControl(rightControls,"DISPLAY MODE",displayMode);
        backgroundStyle=spinner(new String[]{"Carbon fiber","Topographic","Mountain silhouette"});
        backgroundStyle.setSelection(prefs.getInt("background_style",0));addControl(leftControls,"BACKGROUND STYLE",backgroundStyle);
        reduceMotion=toggle("Reduce animation",prefs.getBoolean("reduce_motion",false));addControl(rightControls,"REDUCE MOTION",reduceMotion);
        heroArtwork=toggle("Show full-width truck artwork",prefs.getBoolean("hero_artwork",true));addControl(leftControls,"HERO ARTWORK",heroArtwork);
        accentBrightness=new SeekBar(this);accentBrightness.setMax(100);accentBrightness.setProgress(prefs.getInt("accent_brightness",88));addControl(rightControls,"ACCENT BRIGHTNESS",accentBrightness);
        iconSize=new SeekBar(this);iconSize.setMax(40);iconSize.setProgress(Math.max(0,Math.min(40,prefs.getInt("app_icon_percent",100)-80)));
        iconSizeValue=text((iconSize.getProgress()+80)+"%",14,WHITE,true);iconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int progress,boolean fromUser){iconSizeValue.setText((progress+80)+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        LinearLayout iconRow=horizontal();iconRow.addView(iconSize,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams valueParams=new LinearLayout.LayoutParams(dp(58),dp(48));iconRow.addView(iconSizeValue,valueParams);addControl(leftControls,"APP ICON SIZE",iconRow);
        Button apply=action("APPLY COCKPIT THEME",true,false);rightControls.addView(apply,buttonParams());apply.setOnClickListener(v->{if(saveSettings(false))toast("Cockpit theme applied");});

        LinearLayout navigationPanel=category("// NAVIGATION","Google routing, saved destinations and map behavior.");
        navigationPanel.addView(infoCard("GOOGLE MAPS","Only map and navigation provider • embedded turn-by-turn",true),buttonParams());
        home=edit(prefs.getString("home_destination","Home"));addControl(navigationPanel,"HOME DESTINATION",home);
        work=edit(prefs.getString("work_destination","Work"));addControl(navigationPanel,"WORK DESTINATION",work);
        TextView navNote=infoCard("GOOGLE MAPS LIVE",checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED?"Location ready • turn-by-turn enabled":"Location permission required for live guidance",checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);navigationPanel.addView(navNote,buttonParams());

        LinearLayout mediaPanel=category("// MEDIA","Choose the default source and manage album artwork.");
        media=spinner(new String[]{"Spotify","YouTube Music","Apple Music","System Default"});media.setSelection(prefs.getInt("media_choice",0));addControl(mediaPanel,"PREFERRED MEDIA",media);
        onlineArtwork=toggle("Look up missing Up Next covers",prefs.getBoolean("online_artwork",true));addControl(mediaPanel,"QUEUE ARTWORK",onlineArtwork);
        mediaPanel.addView(infoCard("MEDIA SESSION",MediaBridge.hasAccess(this)?"Access connected":"Tap Permission Health to connect",MediaBridge.hasAccess(this)),buttonParams());
        Button clearArt=action("CLEAR ARTWORK CACHE",false,false);mediaPanel.addView(clearArt,buttonParams());clearArt.setOnClickListener(v->{MediaBridge.clearArtworkCache();toast("Artwork cache cleared");});

        LinearLayout vehicle=category("// VEHICLE & OBD","Pair OBDLink MX+ and set visual safety thresholds.");
        vehicle.addView(infoCard("OBDLINK MX+",ObdBridge.connected?"CONNECTED":"READY TO PAIR",ObdBridge.connected),buttonParams());
        Button obdSetup=action("SET UP OBDLINK MX+",true,false);vehicle.addView(obdSetup,buttonParams());obdSetup.setOnClickListener(v->ObdSetup.show(this));
        alerts=toggle("Enable visual gauge warnings",prefs.getBoolean("performance_alerts",true));addControl(vehicle,"WARNING DISPLAY",alerts);
        LinearLayout thresholds=horizontal();LinearLayout th1=new LinearLayout(this),th2=new LinearLayout(this),th3=new LinearLayout(this);th1.setOrientation(LinearLayout.VERTICAL);th2.setOrientation(LinearLayout.VERTICAL);th3.setOrientation(LinearLayout.VERTICAL);thresholds.addView(th1,new LinearLayout.LayoutParams(0,-2,1));thresholds.addView(th2,new LinearLayout.LayoutParams(0,-2,1));thresholds.addView(th3,new LinearLayout.LayoutParams(0,-2,1));vehicle.addView(thresholds);
        coolantWarning=numberEdit(prefs.getFloat("warn_coolant",235f));addControl(th1,"COOLANT °F",coolantWarning);
        intakeWarning=numberEdit(prefs.getFloat("warn_intake",170f));addControl(th2,"INTAKE °F",intakeWarning);
        voltageWarning=numberEdit(prefs.getFloat("warn_voltage",11.8f));addControl(th3,"LOW VOLTAGE",voltageWarning);

        LinearLayout launcherPanel=category("// LAUNCHER","Startup behavior and Android launcher role.");
        startupPage=spinner(new String[]{"Resume last page","Home","Navigation","Media","Performance","Apps"});
        int startup=prefs.getInt("startup_page",-1);startupPage.setSelection(startup<0?0:startup+1);addControl(launcherPanel,"STARTUP PAGE",startupPage);
        launcherPanel.addView(infoCard("DEFAULT HOME",isDefaultHome()?"TRX Launcher is active":"Android launcher role not selected",isDefaultHome()),buttonParams());
        Button launcher=action("SET AS DEFAULT LAUNCHER",true,false);launcherPanel.addView(launcher,buttonParams());launcher.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Throwable ignored){startActivity(new Intent(Settings.ACTION_SETTINGS));}});

        LinearLayout system=category("// SYSTEM","Permissions, history and first-run controls.");
        LinearLayout systemButtons=horizontal();Button systemLauncher=action("DEFAULT LAUNCHER",false,false),permissions=action("PERMISSION HEALTH",false,false);systemButtons.addView(systemLauncher,weightHeight(62));systemButtons.addView(permissions,weightHeight(62));system.addView(systemButtons);
        systemLauncher.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Throwable ignored){startActivity(new Intent(Settings.ACTION_SETTINGS));}});
        permissions.setOnClickListener(v->openMissingPermission());
        Button clearRuns=action("CLEAR 0–60 HISTORY",false,false);system.addView(clearRuns,buttonParams());clearRuns.setOnClickListener(v->{prefs.edit().remove("performance_runs").apply();toast("Performance history cleared");});
        Button reset=action("RESET FIRST-RUN EXPERIENCE",false,true);system.addView(reset,buttonParams());reset.setOnClickListener(v->{prefs.edit().putBoolean("first_run_complete",false).apply();startActivity(new Intent(this,FirstRunActivity.class));finish();});

        String[] categories={"APPEARANCE","NAVIGATION","MEDIA","VEHICLE & OBD","LAUNCHER","SYSTEM"};View[] panels={wrap(appearance),wrap(navigationPanel),wrap(mediaPanel),wrap(vehicle),wrap(launcherPanel),wrap(system)};
        for(int i=0;i<categories.length;i++){final int index=i;Button b=action(categories[i],false,false);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setPadding(dp(14),0,dp(8),0);b.setOnClickListener(v->selectCategory(index));categoryButtons.add(b);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,0,1);bp.setMargins(0,dp(3),0,dp(3));rail.addView(b,bp);categoryPanels.add(panels[i]);stage.addView(panels[i],new FrameLayout.LayoutParams(-1,-1));}
        selectCategory(0);
        root.addView(settingsDock(),new LinearLayout.LayoutParams(-1,dp(82)));
        setContentView(root);styleAccentControls();
    }

    private View settingsHeader(){
        LinearLayout bar=horizontal();bar.setPadding(dp(12),0,dp(10),0);bar.setBackground(background(0xff080b0f,accent,12));
        TextView ram=text("RAM",24,WHITE,true);bar.addView(ram,new LinearLayout.LayoutParams(dp(74),-1));
        TextView brand=text("TRX LAUNCHER  /  SETTINGS",15,accent,true);brand.setPadding(dp(12),0,0,0);bar.addView(brand,new LinearLayout.LayoutParams(0,-1,1));
        TextView version=text("v"+BuildConfig.VERSION_NAME,11,MUTED,true);version.setGravity(Gravity.CENTER);bar.addView(version,new LinearLayout.LayoutParams(dp(76),-1));
        Button done=action("SAVE & RETURN",true,false);done.setOnClickListener(v->saveAndClose());bar.addView(done,new LinearLayout.LayoutParams(dp(142),dp(50)));return bar;
    }
    private LinearLayout category(String title,String subtitle){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(14),dp(18),dp(18));
        panel.addView(text(title,22,WHITE,true),new LinearLayout.LayoutParams(-1,dp(34)));
        TextView note=text(subtitle,12,MUTED,false);note.setPadding(0,0,0,dp(8));panel.addView(note,new LinearLayout.LayoutParams(-1,dp(34)));return panel;
    }
    private View wrap(View content){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setOverScrollMode(View.OVER_SCROLL_NEVER);s.addView(content,new ScrollView.LayoutParams(-1,-2));return s;}
    private TextView infoCard(String title,String detail,boolean ready){TextView v=text(title+"   •   "+detail,13,ready?GREEN:0xffff7682,true);v.setPadding(dp(16),0,dp(16),0);v.setBackground(background(CARD,ready?0xff24663c:0xff6d2630,10));return v;}
    private void selectCategory(int selected){
        for(int i=0;i<categoryPanels.size();i++){categoryPanels.get(i).setVisibility(i==selected?View.VISIBLE:View.GONE);Button b=categoryButtons.get(i);b.setTextColor(i==selected?accent:WHITE);b.setBackground(background(i==selected?darken(accent,.25f):CARD,i==selected?accent:0xff343a42,10));}
    }
    private View settingsDock(){
        LinearLayout dock=horizontal();dock.setPadding(0,dp(8),0,0);String[] names={"⌂  HOME","➤  NAVIGATION","♫  MEDIA","◴  PERFORMANCE","▦  APPS"};
        for(int i=0;i<names.length;i++){final int page=i;Button b=action(names[i],false,false);b.setTextSize(11);b.setOnClickListener(v->{if(saveSettings(false)){prefs.edit().putInt("page",page).apply();setResult(RESULT_OK,new Intent());finish();}});dock.addView(b,weightHeight(68));}return dock;
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
        if(accentBrightness!=null){accentBrightness.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));accentBrightness.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));}
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
        if(!saveSettings(true))return;
        setResult(RESULT_OK,new Intent());finish();
    }
    private boolean saveSettings(boolean showErrors){
        int custom;try{custom=Color.parseColor(customHex.getText().toString().trim());}catch(Throwable ignored){customHex.setError("Use a color such as #FF2338");return false;}
        if(!validNumber(coolantWarning,100,300)||!validNumber(intakeWarning,0,300)||!validNumber(voltageWarning,8,16))return false;
        int startup=startupPage.getSelectedItemPosition()==0?-1:startupPage.getSelectedItemPosition()-1;
        prefs.edit().putInt("theme_choice",selectedTheme).putInt("custom_accent",custom).putString("custom_hex",customHex.getText().toString().trim())
            .putInt("display_mode",displayMode.getSelectedItemPosition()).putInt("app_icon_percent",iconSize.getProgress()+80).putInt("startup_page",startup)
            .putInt("background_style",backgroundStyle.getSelectedItemPosition()).putBoolean("reduce_motion",reduceMotion.isChecked())
            .putBoolean("hero_artwork",heroArtwork.isChecked()).putInt("accent_brightness",accentBrightness.getProgress())
            .putInt("media_choice",media.getSelectedItemPosition())
            .putString("home_destination",home.getText().toString().trim()).putString("work_destination",work.getText().toString().trim())
            .putBoolean("online_artwork",onlineArtwork.isChecked()).putBoolean("performance_alerts",alerts.isChecked())
            .putFloat("warn_coolant",number(coolantWarning,235f)).putFloat("warn_intake",number(intakeWarning,170f)).putFloat("warn_voltage",number(voltageWarning,11.8f)).apply();
        return true;
    }

    private void openMissingPermission(){
        if(!MediaBridge.hasAccess(this)){MediaBridge.requestAccess(this);return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},340);return;}
        if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){ObdSetup.show(this);return;}
        toast("Launcher permissions are healthy");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==ObdSetup.PERMISSION_REQUEST&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)ObdSetup.show(this);
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
    private Drawable scaledDrawable(int resource,int width,int height){Bitmap source=BitmapFactory.decodeResource(getResources(),resource);Bitmap scaled=Bitmap.createScaledBitmap(source,dp(width),dp(height),true);BitmapDrawable drawable=new BitmapDrawable(getResources(),scaled);drawable.setBounds(0,0,dp(width),dp(height));return drawable;}
    private Button action(String title,boolean primary,boolean danger){Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setTextColor(danger?0xffff7682:WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setBackground(background(primary?darken(accent,.28f):CARD,primary?accent:danger?0xff7a1722:0xff3c434c,12));return b;}
    private TextView text(String value,int size,int color,boolean bold){TextView r=new TextView(this);r.setText(value);r.setTextSize(size);r.setTextColor(color);r.setGravity(Gravity.CENTER_VERTICAL);if(bold)r.setTypeface(Typeface.DEFAULT_BOLD);return r;}
    private GradientDrawable panelBackground(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff141920,0xff080a0e});g.setCornerRadius(dp(16));g.setStroke(dp(1),0xff3a414a);return g;}
    private GradientDrawable background(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private float number(EditText field,float fallback){try{return Float.parseFloat(field.getText().toString().trim());}catch(Throwable ignored){return fallback;}}
    private boolean validNumber(EditText field,float min,float max){float v=number(field,Float.NaN);if(Float.isNaN(v)||Float.isInfinite(v)||v<min||v>max){field.setError("Enter a number from "+min+" to "+max);return false;}return true;}
    private void toast(String value){android.widget.Toast.makeText(this,value,android.widget.Toast.LENGTH_SHORT).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
