package com.mdiaz.trxlauncher;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
    private final LinearLayout suggestions;
    private final LinearLayout chooser;
    private final CheckBox remember;
    private final Handler suggestionHandler=new Handler(Looper.getMainLooper());
    private int suggestionRequest;
    private boolean selectingSuggestion;
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
        destination.setBackground(panel(0xee05070a,0xff5f646d,2,18));
        destination.setElevation(dp(8));
        LayoutParams searchLp=new LayoutParams(LayoutParams.MATCH_PARENT,dp(64),Gravity.TOP);
        searchLp.setMargins(dp(16),dp(16),dp(16),0);
        addView(destination,searchLp);

        suggestions=new LinearLayout(context);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestions.setPadding(dp(7),dp(5),dp(7),dp(7));
        suggestions.setBackground(panel(0xf5080a0d,RED,1,16));
        suggestions.setElevation(dp(18));
        suggestions.setVisibility(GONE);
        LayoutParams suggestionLp=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT,Gravity.TOP);
        suggestionLp.setMargins(dp(16),dp(88),dp(16),0);
        addView(suggestions,suggestionLp);

        destination.setImeOptions(EditorInfo.IME_ACTION_GO);
        destination.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_GO){
                suggestions.setVisibility(GONE);
                showChooser();
                return true;
            }
            return false;
        });
        destination.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){}
            @Override public void afterTextChanged(Editable value){
                if(selectingSuggestion)return;
                scheduleAddressSuggestions(value.toString());
            }
        });
        destination.setOnFocusChangeListener((v,focused)->{
            if(!focused)suggestionHandler.postDelayed(()->suggestions.setVisibility(GONE),180);
        });

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

    private void scheduleAddressSuggestions(String raw){
        final String query=raw==null?"":raw.trim();
        final int request=++suggestionRequest;
        if(query.length()<3){
            suggestions.removeAllViews();
            suggestions.setVisibility(GONE);
            return;
        }
        suggestionHandler.postDelayed(()->{
            if(request!=suggestionRequest||!destination.hasFocus())return;
            new Thread(()->loadAddressSuggestions(query,request),"trx-address-search").start();
        },320);
    }

    private void loadAddressSuggestions(String query,int request){
        final java.util.List<Address> found=new java.util.ArrayList<>();
        try{
            if(Geocoder.isPresent()){
                java.util.List<Address> matches=new Geocoder(activity,java.util.Locale.US)
                    .getFromLocationName(query,4);
                if(matches!=null)found.addAll(matches);
            }
        }catch(Throwable ignored){}
        activity.runOnUiThread(()->renderAddressSuggestions(found,request));
    }

    private void renderAddressSuggestions(java.util.List<Address> found,int request){
        if(request!=suggestionRequest||!destination.hasFocus())return;
        suggestions.removeAllViews();
        if(found==null||found.isEmpty()){
            suggestions.setVisibility(GONE);
            return;
        }

        TextView heading=new TextView(activity);
        heading.setText("SUGGESTED DESTINATIONS");
        heading.setTextColor(RED);
        heading.setTextSize(10);
        heading.setLetterSpacing(.14f);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14),0,dp(10),0);
        suggestions.addView(heading,new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,dp(28)));

        java.util.HashSet<String> added=new java.util.HashSet<>();
        for(Address address:found){
            String full=address.getMaxAddressLineIndex()>=0?address.getAddressLine(0):null;
            if(TextUtils.isEmpty(full)){
                StringBuilder built=new StringBuilder();
                if(!TextUtils.isEmpty(address.getThoroughfare()))built.append(address.getThoroughfare());
                if(!TextUtils.isEmpty(address.getLocality())){
                    if(built.length()>0)built.append(", ");
                    built.append(address.getLocality());
                }
                if(!TextUtils.isEmpty(address.getAdminArea())){
                    if(built.length()>0)built.append(", ");
                    built.append(address.getAdminArea());
                }
                full=built.toString();
            }
            if(TextUtils.isEmpty(full)||!added.add(full))continue;
            String primary=address.getFeatureName();
            if(TextUtils.isEmpty(primary))primary=address.getThoroughfare();
            if(TextUtils.isEmpty(primary))primary=address.getLocality();
            if(TextUtils.isEmpty(primary))primary=full;
            addAddressSuggestion(primary,full);
        }
        if(suggestions.getChildCount()<=1){
            suggestions.removeAllViews();
            suggestions.setVisibility(GONE);
        }else{
            suggestions.setVisibility(VISIBLE);
            suggestions.bringToFront();
        }
    }

    private void addAddressSuggestion(String primary,String full){
        LinearLayout row=new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),0,dp(10),0);
        row.setBackground(panel(0xff090b0e,0xff292d33,1,11));

        TextView marker=new TextView(activity);
        marker.setText("●");
        marker.setTextColor(RED);
        marker.setTextSize(13);
        marker.setGravity(Gravity.CENTER);
        row.addView(marker,new LinearLayout.LayoutParams(dp(34),dp(54)));

        LinearLayout copy=new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=new TextView(activity);
        name.setText(primary.toUpperCase(java.util.Locale.US));
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setTypeface(null,android.graphics.Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView address=new TextView(activity);
        address.setText(full);
        address.setTextColor(0xffaeb3bc);
        address.setTextSize(11);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(24)));
        copy.addView(address,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(20)));
        row.addView(copy,new LinearLayout.LayoutParams(0,dp(50),1));

        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,dp(56));
        rowLp.setMargins(0,dp(2),0,dp(2));
        suggestions.addView(row,rowLp);
        row.setOnClickListener(v->{
            selectingSuggestion=true;
            suggestionRequest++;
            destination.setText(full);
            destination.setSelection(destination.length());
            selectingSuggestion=false;
            suggestions.removeAllViews();
            suggestions.setVisibility(GONE);
            InputMethodManager keyboard=(InputMethodManager)activity
                .getSystemService(Context.INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(destination.getWindowToken(),0);
            destination.clearFocus();
            showChooser();
        });
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
        b.setTextColor(Color.WHITE);b.setTextSize(19);b.setGravity(Gravity.CENTER);
        b.setBackground(panel(0xff050608,selected?RED:0xff5f646d,selected?2:1,16));
        b.setElevation(dp(selected?9:6));
        return b;
    }

    private GradientDrawable panel(int color,int stroke,int width,int radius){
        int top=color==0xff050608?0xff24272c:0xff171a1f;
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{top,color,0xff030405});
        d.setCornerRadius(dp(radius));d.setStroke(dp(width),stroke);return d;
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();map.onResume();}
    @Override protected void onDetachedFromWindow(){map.onPause();super.onDetachedFromWindow();}
}
