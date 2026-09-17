package com.mdiaz.trxlauncher;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.util.GeoPoint;

public class NavigationPanel extends FrameLayout {
    private static final int RED=0xffff2338;
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final MapView map;
    private final EditText destination;
    private final LinearLayout chooser;
    private final CheckBox remember;
    private MyLocationNewOverlay locationOverlay;

    public NavigationPanel(MainActivity context){
        super(context);
        activity=context;
        prefs=context.getSharedPreferences("launcher",Context.MODE_PRIVATE);
        Configuration.getInstance().setUserAgentValue(context.getPackageName()+"/TRXLauncher");
        setBackgroundColor(0xff05070a);

        map=new MapView(context);
        // Key-free OpenStreetMap tiles. The dark appearance is produced locally,
        // so the launcher never depends on a paid tile provider or API key.
        map.setTileSource(TileSourceFactory.MAPNIK);
        ColorMatrix darkTiles=new ColorMatrix(new float[]{
            -0.72f,0,0,0,210,
            0,-0.72f,0,0,210,
            0,0,-0.72f,0,210,
            0,0,0,1,0
        });
        map.getOverlayManager().getTilesOverlay().setColorFilter(
            new ColorMatrixColorFilter(darkTiles));
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(14.2);
        map.getController().setCenter(new GeoPoint(40.3323,-74.5819));
        addView(map,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

        destination=new EditText(context);
        destination.setHint("Where to?");
        destination.setSingleLine(true);
        destination.setTextColor(Color.WHITE);
        destination.setHintTextColor(0xff9ca1aa);
        destination.setTextSize(17);
        destination.setPadding(dp(22),0,dp(72),0);
        destination.setBackground(panel(0xee05070a,RED,2,18));
        LayoutParams searchLp=new LayoutParams(LayoutParams.MATCH_PARENT,dp(64),Gravity.TOP);
        searchLp.setMargins(dp(16),dp(16),dp(16),0);
        addView(destination,searchLp);

        Button route=button("➤",true);
        LayoutParams routeLp=new LayoutParams(dp(54),dp(54),Gravity.TOP|Gravity.RIGHT);
        routeLp.setMargins(0,dp(21),dp(21),0);
        addView(route,routeLp);
        route.setOnClickListener(v->showChooser());

        LinearLayout zoom=new LinearLayout(context);
        zoom.setOrientation(LinearLayout.VERTICAL);
        Button plus=button("+",false),minus=button("−",false);
        zoom.addView(plus,new LinearLayout.LayoutParams(dp(54),dp(54)));
        zoom.addView(minus,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LayoutParams zoomLp=new LayoutParams(dp(54),dp(108),Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        zoomLp.setMargins(0,0,dp(16),0);
        addView(zoom,zoomLp);
        plus.setOnClickListener(v->map.getController().zoomIn());
        minus.setOnClickListener(v->map.getController().zoomOut());

        Button locate=button("◎",false);
        LayoutParams locateLp=new LayoutParams(dp(54),dp(54),Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        locateLp.setMargins(0,dp(130),dp(16),0);
        addView(locate,locateLp);
        locate.setOnClickListener(v->recenter());

        Button home=button("⌂  HOME",true);
        home.setTextSize(15);
        LayoutParams homeLp=new LayoutParams(dp(150),dp(56),Gravity.LEFT|Gravity.BOTTOM);
        homeLp.setMargins(dp(16),0,0,dp(16));
        addView(home,homeLp);
        home.setOnClickListener(v->activity.openNavigation());

        TextView live=new TextView(context);
        live.setText("☝  LIVE MAP • PINCH TO ZOOM");
        live.setTextColor(0xffd0d3d8);
        live.setTextSize(12);
        live.setGravity(Gravity.CENTER);
        live.setBackground(panel(0xe80a0c10,0xff656a74,1,18));
        LayoutParams liveLp=new LayoutParams(dp(230),dp(42),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);
        liveLp.setMargins(0,0,0,dp(22));
        addView(live,liveLp);

        TextView credit=new TextView(context);
        credit.setText("© OpenStreetMap contributors");
        credit.setTextColor(0xffb9bdc5);credit.setTextSize(9);
        LayoutParams creditLp=new LayoutParams(LayoutParams.WRAP_CONTENT,dp(24),Gravity.RIGHT|Gravity.BOTTOM);
        creditLp.setMargins(0,0,dp(16),dp(18));
        addView(credit,creditLp);

        chooser=new LinearLayout(context);
        chooser.setOrientation(LinearLayout.VERTICAL);
        chooser.setPadding(dp(16),dp(8),dp(16),dp(8));
        chooser.setElevation(dp(32));
        chooser.setBackground(panel(0xff080a0e,0xff6a707a,1,20));
        LinearLayout titleRow=new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(context);
        title.setText("CHOOSE NAVIGATION");title.setTextColor(Color.WHITE);
        title.setTextSize(18);title.setTypeface(null,android.graphics.Typeface.BOLD);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(34),1));
        Button close=button("×",false);close.setTextSize(22);close.setContentDescription("Close navigation chooser");
        titleRow.addView(close,new LinearLayout.LayoutParams(dp(42),dp(34)));
        chooser.addView(titleRow,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(36)));

        LinearLayout apps=new LinearLayout(context);
        apps.setOrientation(LinearLayout.HORIZONTAL);
        Button waze=button("◉  WAZE",true);
        Button google=button("◆  GOOGLE MAPS",false);
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(58),1);
        half.setMargins(0,0,dp(8),0);apps.addView(waze,half);
        LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,dp(58),1);
        half2.setMargins(dp(8),0,0,0);apps.addView(google,half2);
        chooser.addView(apps,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(64)));

        remember=new CheckBox(context);
        remember.setText("Use as default");remember.setTextColor(Color.WHITE);remember.setTextSize(15);
        chooser.addView(remember,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(38)));
        chooser.setVisibility(GONE);
        LayoutParams chooserLp=new LayoutParams(LayoutParams.MATCH_PARENT,dp(154),Gravity.BOTTOM);
        chooserLp.setMargins(dp(10),0,dp(10),dp(4));
        addView(chooser,chooserLp);

        close.setOnClickListener(v->chooser.setVisibility(GONE));
        waze.setOnClickListener(v->launch(1));
        google.setOnClickListener(v->launch(0));
        chooser.setOnLongClickListener(v->{chooser.setVisibility(GONE);return true;});
        enableLocation();
    }

    public boolean closeChooser(){
        if(chooser.getVisibility()==VISIBLE){chooser.setVisibility(GONE);return true;}
        return false;
    }

    private void showChooser(){
        if(destination.getText().toString().trim().isEmpty()){
            Toast.makeText(activity,"Enter a destination",Toast.LENGTH_SHORT).show();return;
        }
        remember.setChecked(false);
        chooser.setVisibility(VISIBLE);
        chooser.bringToFront();
    }

    private void launch(int choice){
        if(remember.isChecked())prefs.edit().putInt("nav_choice",choice).apply();
        chooser.setVisibility(GONE);
        String address=destination.getText().toString().trim();
        if(choice==1)activity.openWazeNavigation(address);
        else activity.openGoogleMapsNavigation(address);
    }

    private void enableLocation(){
        if(activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            locationOverlay=new MyLocationNewOverlay(new GpsMyLocationProvider(activity),map);
            locationOverlay.enableMyLocation();
            map.getOverlays().add(locationOverlay);
            recenter();
        }catch(Throwable ignored){}
    }

    private void recenter(){
        try{
            if(locationOverlay!=null&&locationOverlay.getMyLocation()!=null){
                map.getController().animateTo(locationOverlay.getMyLocation());
                map.getController().setZoom(16.0);return;
            }
            LocationManager manager=(LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
            Location last=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(last==null)last=manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if(last!=null)map.getController().animateTo(new GeoPoint(last.getLatitude(),last.getLongitude()));
        }catch(Throwable ignored){}
    }

    private Button button(String text,boolean selected){
        Button b=new Button(activity);b.setText(text);b.setAllCaps(false);
        b.setTextColor(selected?Color.WHITE:0xffff5160);b.setTextSize(19);b.setGravity(Gravity.CENTER);
        b.setBackground(panel(0xf205070a,selected?RED:0xff9e1f2b,selected?2:1,16));
        return b;
    }

    private GradientDrawable panel(int color,int stroke,int width,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        d.setStroke(dp(width),stroke);return d;
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();map.onResume();}
    @Override protected void onDetachedFromWindow(){map.onPause();super.onDetachedFromWindow();}
}
