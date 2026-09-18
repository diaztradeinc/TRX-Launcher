package com.mdiaz.trxlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.util.Locale;

public final class FirstRunActivity extends Activity {
    private static final int REQ_LOCATION=201, REQ_NOTIFICATIONS=202, REQ_BLUETOOTH=203, REQ_MEDIA=204, REQ_HOME=205;
    private FirstRunView firstRunView;
    private int currentStep;
    private boolean sequenceRunning,awaitingExternal;
    private long externalLaunchAt;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff030405);getWindow().setNavigationBarColor(0xff030405);
        firstRunView=new FirstRunView(this);setContentView(firstRunView);refreshStates();
    }

    void beginSetup(){firstRunView.showSetup();}

    void beginPermissionSequence(){
        if(sequenceRunning)return;sequenceRunning=true;currentStep=0;requestNext();
    }

    private void requestNext(){
        refreshStates();
        while(currentStep<5&&permissionGranted(currentStep)){firstRunView.setPermissionState(currentStep,1);currentStep++;}
        if(currentStep>=5){sequenceRunning=false;firstRunView.showComplete();return;}
        firstRunView.setActiveStep(currentStep);
        switch(currentStep){
            case 0:
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);break;
            case 1:
                if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
                else{firstRunView.setPermissionState(currentStep,1);currentStep++;requestNext();}break;
            case 2:
                if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BLUETOOTH);
                else{firstRunView.setPermissionState(currentStep,1);currentStep++;requestNext();}break;
            case 3:
                awaitingExternal=true;externalLaunchAt=SystemClock.elapsedRealtime();
                try{startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),REQ_MEDIA);}
                catch(Throwable error){try{startActivityForResult(new Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS"),REQ_MEDIA);}catch(Throwable ignored){markExternal(false);}}break;
            case 4:
                requestHomeRole();break;
        }
    }

    private void requestHomeRole(){
        if(Build.VERSION.SDK_INT>=29){
            RoleManager manager=(RoleManager)getSystemService(Context.ROLE_SERVICE);
            if(manager!=null&&manager.isRoleAvailable(RoleManager.ROLE_HOME)){
                awaitingExternal=true;externalLaunchAt=SystemClock.elapsedRealtime();
                startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME),REQ_HOME);return;
            }
        }
        awaitingExternal=true;externalLaunchAt=SystemClock.elapsedRealtime();
        try{startActivityForResult(new Intent(Settings.ACTION_HOME_SETTINGS),REQ_HOME);}catch(Throwable error){markExternal(false);}
    }

    private boolean permissionGranted(int step){
        switch(step){
            case 0:return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            case 1:return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
            case 2:return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
            case 3:
                String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
                return enabled!=null&&enabled.contains(getPackageName());
            case 4:
                if(Build.VERSION.SDK_INT>=29){RoleManager manager=(RoleManager)getSystemService(Context.ROLE_SERVICE);return manager!=null&&manager.isRoleHeld(RoleManager.ROLE_HOME);}
                Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);android.content.pm.ResolveInfo result=getPackageManager().resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);return result!=null&&result.activityInfo!=null&&getPackageName().equals(result.activityInfo.packageName);
            default:return false;
        }
    }

    private void refreshStates(){if(firstRunView==null)return;for(int i=0;i<5;i++)firstRunView.setPermissionState(i,permissionGranted(i)?1:0);}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        int step=requestCode==REQ_LOCATION?0:requestCode==REQ_NOTIFICATIONS?1:requestCode==REQ_BLUETOOTH?2:-1;
        if(step>=0){firstRunView.setPermissionState(step,permissionGranted(step)?1:2);currentStep=step+1;new Handler(Looper.getMainLooper()).postDelayed(this::requestNext,260);}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_MEDIA){currentStep=4;markExternal(permissionGranted(3));}
        else if(requestCode==REQ_HOME){currentStep=5;markExternal(permissionGranted(4));}
    }

    @Override protected void onResume(){
        super.onResume();
        if(awaitingExternal&&SystemClock.elapsedRealtime()-externalLaunchAt>500){
            int step=currentStep;if(step==3){currentStep=4;markExternal(permissionGranted(3));}
            else if(step==4){currentStep=5;markExternal(permissionGranted(4));}
        }
    }

    private void markExternal(boolean granted){
        awaitingExternal=false;int completed=Math.max(0,currentStep-1);firstRunView.setPermissionState(completed,granted?1:2);
        new Handler(Looper.getMainLooper()).postDelayed(this::requestNext,260);
    }

    void finishSetup(){
        getSharedPreferences("launcher",MODE_PRIVATE).edit().putBoolean("first_run_complete",true).apply();
        Intent launch=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(launch);overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);finish();
    }

    @Override public void onBackPressed(){if(firstRunView!=null&&firstRunView.isSetup()){firstRunView.showIntro();sequenceRunning=false;awaitingExternal=false;}else super.onBackPressed();}
}

final class FirstRunView extends View {
    private static final int RED=0xffff2338,WHITE=0xfff6f7f9,MUTED=0xffa4aab4,GREEN=0xff50dc83;
    private final FirstRunActivity activity;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final Handler ticker=new Handler(Looper.getMainLooper());
    private final Bitmap hero;
    private final int[] states={0,0,0,0,0};
    private final String[] names={"PRECISE LOCATION","NOTIFICATIONS","NEARBY DEVICES","MEDIA ACCESS","DEFAULT LAUNCHER"};
    private final String[] reasons={"Navigation, weather and GPS speed","Media sessions and important alerts","OBDLink MX+ and Bluetooth accessories","Track details and playback controls","Permanent use as your Home screen"};
    private final String[] icons={"●","◆","ᛒ","♫","⌂"};
    private int phase,activeStep=-1,safeTop,safeBottom;
    private float W,H,sx,sy,scale,downX,downY;
    private long phaseAt=SystemClock.uptimeMillis();
    private final Runnable animate=new Runnable(){public void run(){invalidate();ticker.postDelayed(this,33);}};

    FirstRunView(FirstRunActivity context){
        super(context);activity=context;setFocusable(true);hero=BitmapFactory.decodeResource(getResources(),R.drawable.trx_first_run_hero);ticker.post(animate);
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());safeTop=bars.top;safeBottom=bars.bottom;}
        else{safeTop=insets.getSystemWindowInsetTop();safeBottom=insets.getSystemWindowInsetBottom();}
        invalidate();return insets;
    }
    @Override protected void onDetachedFromWindow(){ticker.removeCallbacks(animate);super.onDetachedFromWindow();}
    private float x(float value){return value*sx;}private float y(float value){return safeTop+value*sy;}
    private void text(Canvas c,String value,float xx,float yy,float size,int color,boolean bold){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size*scale);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextAlign(Paint.Align.LEFT);c.drawText(value,x(xx),y(yy),p);}
    private void box(Canvas c,float l,float t,float r,float b,int fill,int stroke,float radius){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(fill);RectF q=new RectF(x(l),y(t),x(r),y(b));c.drawRoundRect(q,radius*scale,radius*scale,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f*scale);p.setColor(stroke);c.drawRoundRect(q,radius*scale,radius*scale,p);p.setStyle(Paint.Style.FILL);}
    private float ease(float duration){float q=Math.max(0,Math.min(1,(SystemClock.uptimeMillis()-phaseAt)/duration));return 1-(1-q)*(1-q)*(1-q);}

    @Override protected void onDraw(Canvas c){
        W=getWidth();H=getHeight();sx=W/1080f;sy=Math.max(1,H-safeTop-safeBottom)/1440f;scale=Math.min(sx,sy);c.drawColor(0xff030405);
        drawHero(c);if(phase==0)drawIntro(c);else if(phase==1)drawSetup(c);else drawComplete(c);
    }

    private void drawHero(Canvas c){
        float bottom=phase==1?655:720;if(hero!=null){
            float target=W/Math.max(1,y(bottom)-y(0)),source=(float)hero.getWidth()/hero.getHeight();Rect src;
            if(source>target){int crop=(int)((hero.getWidth()-hero.getHeight()*target)/2);src=new Rect(crop,0,hero.getWidth()-crop,hero.getHeight());}
            else{int crop=(int)((hero.getHeight()-hero.getWidth()/target)/2);src=new Rect(0,crop,hero.getWidth(),hero.getHeight()-crop);}
            float zoom=1.045f-.045f*Math.min(1,(SystemClock.uptimeMillis()-phaseAt)/3500f);c.save();c.scale(zoom,zoom,W/2,y(bottom)/2);c.drawBitmap(hero,src,new RectF(0,y(0),W,y(bottom)),p);c.restore();
        }
        p.setShader(new LinearGradient(0,y(0),0,y(bottom),0x00000000,0xff030405,Shader.TileMode.CLAMP));c.drawRect(0,y(0),W,y(bottom),p);p.setShader(null);
        float scan=Math.min(1,(SystemClock.uptimeMillis()-phaseAt)/1900f);if(scan<1){p.setColor(0x99ff2338);p.setStrokeWidth(2*scale);c.drawLine(0,y(35+bottom*scan),W,y(35+bottom*scan),p);}
        text(c,"RAM",30,48,25,WHITE,true);
        String badge="FIRST-RUN SETUP  •  v1.1";p.setTextSize(12*scale);p.setTypeface(Typeface.DEFAULT_BOLD);float badgeWidth=p.measureText(badge)/sx+30;float left=1080-30-badgeWidth;
        box(c,left,18,1050,58,0xdd090b0e,0xff7a1722,20);text(c,badge,left+15,45,12,0xffff6573,true);
        float rise=(1-ease(850))*25;text(c,"WELCOME TO",390,276+rise,14,RED,true);text(c,"TRX",260,356+rise,58,WHITE,true);text(c,"LAUNCHER",442,356+rise,58,RED,true);
        box(c,330,382,750,430,0xcc050608,RED,4);text(c,"SUPERCHARGED",353,413,14,RED,true);text(c,"6.2L V8",590,413,14,WHITE,true);
    }

    private void drawIntro(Canvas c){
        box(c,24,650,1056,1360,0xf20b0e12,0xff424851,20);
        text(c,"YOUR TRUCK. YOUR COMMAND CENTER.",58,714,27,WHITE,true);
        text(c,"A purpose-built launcher for navigation, media, performance",58,754,15,MUTED,false);
        text(c,"tools, and every app you use on the road.",58,782,15,MUTED,false);
        feature(c,58,828,500,918,"➤","INTEGRATED NAVIGATION");feature(c,530,828,1022,918,"♫","LIVE MEDIA CONTROLS");
        feature(c,58,938,500,1028,"⌁","PERFORMANCE PAGES");feature(c,530,938,1022,1028,"▦","COMPLETE APP DRAWER");
        primary(c,58,1160,1022,1280,"BEGIN ONE-TIME SETUP");
        text(c,"This screen appears once after installation.",334,1330,12,MUTED,false);
    }

    private void feature(Canvas c,float l,float t,float r,float b,String icon,String label){box(c,l,t,r,b,0xff090b0e,0xff30363e,12);text(c,icon,l+22,t+57,22,RED,true);text(c,label,l+72,t+54,14,WHITE,true);}
    private void primary(Canvas c,float l,float t,float r,float b,String label){box(c,l,t,r,b,0xff3b0d14,RED,13);p.setTextAlign(Paint.Align.CENTER);p.setShader(null);p.setColor(WHITE);p.setTextSize(17*scale);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(label,x((l+r)/2),y((t+b)/2+6),p);p.setTextAlign(Paint.Align.LEFT);}

    private void drawSetup(Canvas c){
        box(c,24,600,1056,1380,0xf50b0e12,0xff424851,20);
        text(c,"ENABLE THE FULL EXPERIENCE",58,657,25,WHITE,true);text(c,"Android permission prompts appear one at a time. You remain in control.",58,690,13,MUTED,false);
        int granted=0;for(int value:states)if(value==1)granted++;p.setColor(0xff262b32);c.drawRoundRect(new RectF(x(58),y(716),x(1022),y(722)),3*scale,3*scale,p);p.setColor(RED);c.drawRoundRect(new RectF(x(58),y(716),x(58+(964*granted/5f)),y(722)),3*scale,3*scale,p);
        float intro=ease(550);
        for(int i=0;i<5;i++){float top=746+i*100+(1-intro)*(16+i*3);permission(c,i,58,top,1022,top+82);}
        primary(c,58,1270,1022,1360,activeStep>=0?"GRANT "+names[Math.min(activeStep,4)]:"GRANT REQUIRED ACCESS");
    }

    private void permission(Canvas c,int index,float l,float t,float r,float b){
        boolean granted=states[index]==1,denied=states[index]==2,active=index==activeStep;int stroke=granted?0xff305a40:active?RED:0xff30363e;
        box(c,l,t,r,b,granted?0xff09140d:0xff090b0e,stroke,11);box(c,l+12,t+13,l+68,b-13,granted?0xff12331f:0xff1b1f25,0x00000000,10);
        text(c,granted?"✓":icons[index],l+31,t+53,19,granted?GREEN:RED,true);text(c,names[index],l+88,t+34,14,WHITE,true);text(c,reasons[index],l+88,t+59,12,MUTED,false);
        String status=granted?"GRANTED":denied?"NOT GRANTED":index==4?"RECOMMENDED":"REQUIRED";int color=granted?GREEN:denied?0xffff7a86:MUTED;
        p.setTextAlign(Paint.Align.RIGHT);p.setShader(null);p.setColor(color);p.setTextSize(11*scale);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(status,x(r-18),y(t+48),p);p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawComplete(Canvas c){
        p.setColor(0xee030405);c.drawRect(0,y(0),W,H-safeBottom,p);box(c,300,430,780,910,0xff0b0e12,RED,24);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3*scale);p.setColor(RED);c.drawCircle(W/2,y(545),58*scale,p);p.setStyle(Paint.Style.FILL);
        text(c,"✓",510,565,44,RED,true);text(c,"SETUP COMPLETE",372,660,28,WHITE,true);text(c,"TRX Launcher is ready to dominate.",372,705,14,MUTED,false);primary(c,350,760,730,850,"LAUNCH TRX");
    }

    boolean isSetup(){return phase==1;}
    void showSetup(){phase=1;activeStep=-1;phaseAt=SystemClock.uptimeMillis();invalidate();}
    void showIntro(){phase=0;activeStep=-1;phaseAt=SystemClock.uptimeMillis();invalidate();}
    void setActiveStep(int step){activeStep=step;invalidate();}
    void setPermissionState(int index,int value){if(index>=0&&index<states.length){states[index]=value;invalidate();}}
    void showComplete(){phase=2;activeStep=-1;phaseAt=SystemClock.uptimeMillis();invalidate();}

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;float xx=e.getX()/Math.max(.01f,sx),yy=(e.getY()-safeTop)/Math.max(.01f,sy);
        if(Math.abs(e.getX()-downX)>20*scale||Math.abs(e.getY()-downY)>20*scale)return true;
        if(phase==0&&yy>1130&&yy<1310){activity.beginSetup();return true;}
        if(phase==1&&yy>1240&&yy<1390){activity.beginPermissionSequence();return true;}
        if(phase==2&&yy>730&&yy<890){activity.finishSetup();return true;}
        return true;
    }
}
