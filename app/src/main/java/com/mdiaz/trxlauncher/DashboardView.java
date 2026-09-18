package com.mdiaz.trxlauncher;

import android.content.Context;
import android.content.SharedPreferences;
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
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DashboardView extends View {
    private static final int RED=0xffff2338, DEEP_RED=0xff6e0713, WHITE=0xfff5f5f7;
    private static final int MUTED=0xffaeb2ba, PANEL=0xf20a0c10, LINE=0xff555b64;
    private static final String[] PAGES={"Home","Navigation","Media","Performance","Apps"};
    private final MainActivity activity;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final Handler clock=new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final Bitmap hero;
    private final Bitmap defaultMediaArt;
    private List<AppEntry> apps=new ArrayList<>();
    private List<AppEntry> displayApps=new ArrayList<>();
    private List<AppEntry> mediaApps=new ArrayList<>();
    private boolean favoriteAppsOnly;
    private String appSearch="";
    private android.widget.OverScroller appScroller;
    private android.view.VelocityTracker appVelocity;
    private int heldAppIndex=-1;
    private boolean appLongPressOpened;
    private final Runnable openHeldAppOptions=new Runnable(){public void run(){
        if(page==4&&heldAppIndex>=0&&heldAppIndex<displayApps.size()){
            appLongPressOpened=true;touchX=touchY=-1;
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            activity.showAppOptions(displayApps.get(heldAppIndex));invalidate();
        }
    }};
    private int page, appPage, safeTop, safeBottom;
    private float W,H,u,usableH,downX,downY,touchX=-1,touchY=-1,appScroll,scrollAtDown,mediaScroll,mediaScrollAtDown;
    private long launchAt=SystemClock.uptimeMillis(), transitionAt, lastMediaRefresh, runStarted;
    private float speedMph, zeroToSixty;
    private boolean runArmed, runActive;
    private int transitionDirection;
    private final Runnable ticker=new Runnable(){public void run(){invalidate();clock.postDelayed(this,33);}};

    public DashboardView(MainActivity context){
        super(context);activity=context;setFocusable(true);
        appScroller=new android.widget.OverScroller(context);
        prefs=context.getSharedPreferences("launcher",Context.MODE_PRIVATE);
        page=Math.max(0,Math.min(4,prefs.getInt("page",0)));
        hero=BitmapFactory.decodeResource(getResources(),R.drawable.trx_hero_banner);
        defaultMediaArt=BitmapFactory.decodeResource(getResources(),R.drawable.default_media_art);
        reloadMediaApps();clock.post(ticker);
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());safeTop=bars.top;safeBottom=bars.bottom;}
        else{safeTop=insets.getSystemWindowInsetTop();safeBottom=insets.getSystemWindowInsetBottom();}
        invalidate();return insets;
    }
    @Override protected void onDetachedFromWindow(){clock.removeCallbacks(ticker);clock.removeCallbacks(openHeldAppOptions);if(appVelocity!=null){appVelocity.recycle();appVelocity=null;}super.onDetachedFromWindow();}
    @Override public void computeScroll(){super.computeScroll();if(appScroller!=null&&appScroller.computeScrollOffset()){appScroll=appScroller.getCurrY();postInvalidateOnAnimation();}}
    @Override protected void onDraw(Canvas c){try{drawLauncher(c);}catch(Throwable error){c.drawColor(0xff050607);p.setColor(RED);p.setTextSize(28);c.drawText("TRX LAUNCHER DIAGNOSTIC",30,90,p);p.setColor(WHITE);p.setTextSize(18);c.drawText(error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage()),30,135,p);}}

    private void drawLauncher(Canvas c){
        W=getWidth();H=getHeight();u=W/1080f;usableH=Math.max(1,H-safeTop-safeBottom);c.drawColor(0xff050608);carbon(c);status(c);
        if(SystemClock.uptimeMillis()-lastMediaRefresh>1000){MediaBridge.refresh(activity);lastMediaRefresh=SystemClock.uptimeMillis();}
        float intro=Math.min(1f,(SystemClock.uptimeMillis()-launchAt)/900f),slide=0;
        if(transitionAt>0){float q=Math.min(1f,(SystemClock.uptimeMillis()-transitionAt)/360f);q=1-(1-q)*(1-q)*(1-q);slide=transitionDirection*(1-q)*W;if(q>=1)transitionAt=0;}
        c.save();c.translate(slide,sy(34)*(1-intro));drawPage(c,intro);c.restore();dock(c);
    }
    private float x(float n){return n*u;}private float y(float n){return safeTop+n*usableH/1440f;}private float sy(float n){return n*usableH/1440f;}
    private void paint(int color,float size,boolean bold){p.setColor(color);p.setTextSize(x(size));p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setStyle(Paint.Style.FILL);p.setShader(null);p.setAlpha(255);}
    private void text(Canvas c,String s,float xx,float yy,float size,int color,boolean bold){paint(color,size,bold);c.drawText(s,x(xx),y(yy),p);}
    private void line(Canvas c,float a,float b,float d,float e,int color,float width){p.setShader(null);p.setColor(color);p.setAlpha(255);p.setStrokeWidth(x(width));p.setStyle(Paint.Style.STROKE);c.drawLine(x(a),y(b),x(d),y(e),p);p.setStyle(Paint.Style.FILL);}
    private void raisedBox(Canvas c,RectF q,boolean selected,float radius){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(0xaa000000);
        RectF shadow=new RectF(q);shadow.offset(0,x(5));c.drawRoundRect(shadow,x(radius+2),x(radius+2),p);
        p.setShader(new LinearGradient(q.left,q.top,q.left,q.bottom,
            selected?0xff24272c:0xff1b1e22,0xff050608,Shader.TileMode.CLAMP));
        c.drawRoundRect(q,x(radius),x(radius),p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(3));p.setColor(selected?RED:0xff4b5058);
        c.drawRoundRect(q,x(radius),x(radius),p);
        RectF inner=new RectF(q);inner.inset(x(6),x(6));p.setStrokeWidth(x(1));
        p.setColor(selected?0xff8f1724:0xff777d86);c.drawRoundRect(inner,x(Math.max(3,radius-5)),x(Math.max(3,radius-5)),p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(q.left,q.top,q.right,q.top,0x00ff2338,
            selected?0xffff2338:0x006f747d,Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(q.left+x(22),q.top,q.right-x(22),q.top+x(4)),x(2),x(2),p);
        p.setShader(null);
    }
    private void carbon(Canvas c){p.setColor(0xff07090c);c.drawRect(0,safeTop,W,H-safeBottom,p);p.setStrokeWidth(x(1));p.setColor(0x221f252b);for(float i=-H;i<W+H;i+=x(34)){c.drawLine(i,safeTop,i+H,H-safeBottom,p);c.drawLine(i+x(8),safeTop,i+H+x(8),H-safeBottom,p);}}
    private void status(Canvas c){RectF r=new RectF(0,y(0),W,y(62));p.setShader(new LinearGradient(0,y(0),W,y(0),0xff07090b,0xff15181c,Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);text(c,"RAM",30,41,27,WHITE,true);text(c,"TRX LAUNCHER",126,40,17,RED,true);text(c,activity.weatherTemp()+"  •  PLAINSBORO, NJ",565,39,16,MUTED,true);String now=new SimpleDateFormat("h:mm a",Locale.US).format(new Date());p.setTextAlign(Paint.Align.RIGHT);text(c,now,1045,41,24,WHITE,true);p.setTextAlign(Paint.Align.LEFT);line(c,0,61,1080,61,RED,2);}
    private void drawPage(Canvas c,float intro){switch(page){case 0:home(c,intro);break;case 1:navigation(c);break;case 2:media(c);break;case 3:performance(c);break;default:apps(c);}}

    public void onSpeedChanged(float mph){speedMph=mph;if(runArmed&&!runActive&&mph>=1f){runActive=true;runStarted=SystemClock.elapsedRealtime();}if(runActive&&mph>=60f){zeroToSixty=(SystemClock.elapsedRealtime()-runStarted)/1000f;runActive=false;runArmed=false;}invalidate();}
    private void armRun(){zeroToSixty=0;runActive=false;runArmed=true;}
    private void resetRun(){zeroToSixty=0;runActive=false;runArmed=false;}
    private String runTime(){if(runActive)return String.format(Locale.US,"%.1f s",(SystemClock.elapsedRealtime()-runStarted)/1000f);if(zeroToSixty>0)return String.format(Locale.US,"%.1f s",zeroToSixty);return "--.- s";}
    private String trim(String value,int max){if(value==null)return "";return value.length()>max?value.substring(0,max-1)+"…":value;}
    public int currentPage(){return page;}
    public void reloadMediaApps(){try{mediaApps=activity.selectedMediaApps();}catch(Throwable ignored){mediaApps=new ArrayList<>();}invalidate();}
    private void marquee(Canvas c,String value,float l,float t,float r,float size,int color,boolean bold){
        if(value==null||value.isEmpty())value="No media playing";
        paint(color,size,bold);float width=p.measureText(value),available=x(r-l);
        c.save();c.clipRect(x(l),y(t-size-8),x(r),y(t+10));
        if(width<=available)c.drawText(value,x(l),y(t),p);
        else{float gap=x(70),cycle=width+gap,offset=(SystemClock.uptimeMillis()/28f)%cycle;c.drawText(value,x(l)-offset,y(t),p);c.drawText(value,x(l)-offset+cycle,y(t),p);}
        c.restore();
    }

    private void mountains(Canvas c,float top,float bottom){p.setColor(0xff10151b);path.reset();path.moveTo(0,y(bottom));path.lineTo(0,y(top+150));path.lineTo(x(180),y(top+45));path.lineTo(x(345),y(top+135));path.lineTo(x(510),y(top+20));path.lineTo(x(690),y(top+145));path.lineTo(x(860),y(top+55));path.lineTo(W,y(top+142));path.lineTo(W,y(bottom));path.close();c.drawPath(path,p);}
    private void hero(Canvas c,float top,float bottom,float intro){if(hero==null)return;float reveal=1-(1-intro)*(1-intro),targetRatio=W/Math.max(1f,sy(bottom-top)),sourceRatio=(float)hero.getWidth()/hero.getHeight();Rect src;if(sourceRatio>targetRatio){int crop=(int)((hero.getWidth()-hero.getHeight()*targetRatio)/2f);src=new Rect(crop,0,hero.getWidth()-crop,hero.getHeight());}else{int crop=(int)((hero.getHeight()-hero.getWidth()/targetRatio)/2f);src=new Rect(0,crop,hero.getWidth(),hero.getHeight()-crop);}float lift=sy(22)*(1-reveal);p.setAlpha((int)(255*reveal));c.drawBitmap(hero,src,new RectF(0,y(top)+lift,W,y(bottom)+lift),p);p.setAlpha(255);p.setShader(new LinearGradient(0,y(top),0,y(bottom),0x00000000,0xd907090c,Shader.TileMode.CLAMP));c.drawRect(0,y(top),W,y(bottom),p);p.setShader(null);}
    private void panel(Canvas c,float l,float t,float r,float b,String title){RectF q=new RectF(x(l),y(t),x(r),y(b));raisedBox(c,q,false,12);if(!title.isEmpty()){line(c,l+16,t+45,r-16,t+45,0xff42474f,1);text(c,title,l+18,t+32,18,WHITE,true);}line(c,l+28,b-5,r-28,b-5,0xff8f1724,2);}
    private float introGauge(){return Math.max(0,Math.min(1f,(SystemClock.uptimeMillis()-launchAt-250)/1000f));}
    private void gauge(Canvas c,float l,float t,float r,String label,String value,String unit,float level){RectF q=new RectF(x(l),y(t),x(r),y(t+138));raisedBox(c,q,false,10);p.setStyle(Paint.Style.STROKE);RectF arc=new RectF(x(l+24),y(t+46),x(r-24),y(t+154));p.setStrokeWidth(x(7));p.setColor(0xff333840);c.drawArc(arc,195,150,false,p);p.setColor(RED);c.drawArc(arc,195,Math.max(8,150*level*introGauge()),false,p);p.setStyle(Paint.Style.FILL);text(c,label,l+18,t+32,14,MUTED,true);text(c,value,l+18,t+92,31,WHITE,true);text(c,unit,r-58,t+92,13,MUTED,true);}

    private void quickControl(Canvas c,float l,float r,String icon,String label,String state,float level){
        RectF q=new RectF(x(l),y(370),x(r),y(455));raisedBox(c,q,false,9);
        text(c,icon,l+16,421,25,WHITE,true);text(c,label,l+58,405,14,WHITE,true);
        text(c,state,l+58,431,11,MUTED,false);
        float barL=r-86,barR=r-18,barY=423;
        p.setColor(0xff3c424a);c.drawRoundRect(new RectF(x(barL),y(barY),x(barR),y(barY+7)),x(4),x(4),p);
        p.setColor(RED);c.drawRoundRect(new RectF(x(barL),y(barY),x(barL+(barR-barL)*Math.max(0,Math.min(1,level))),y(barY+7)),x(4),x(4),p);
    }
    private void compactGauge(Canvas c,float l,float r,String label,String value,String unit,float level){
        RectF q=new RectF(x(l),y(915),x(r),y(1058));raisedBox(c,q,false,9);
        text(c,label,l+16,945,13,MUTED,true);
        RectF arc=new RectF(x(l+24),y(954),x(r-24),y(1068));
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(7));p.setColor(0xff333840);c.drawArc(arc,195,150,false,p);
        p.setColor(RED);c.drawArc(arc,195,Math.max(7,150*level*introGauge()),false,p);p.setStyle(Paint.Style.FILL);
        paint(WHITE,27,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(value,x((l+r)/2),y(1020),p);
        paint(MUTED,11,true);c.drawText(unit,x((l+r)/2),y(1043),p);p.setTextAlign(Paint.Align.LEFT);
    }
    private float deviceLevel(int kind){
        try{
            if(kind==0){
                android.net.ConnectivityManager cm=(android.net.ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
                android.net.Network n=cm.getActiveNetwork();android.net.NetworkCapabilities caps=cm.getNetworkCapabilities(n);
                return caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)?1f:0f;
            }
            if(kind==1){
                android.bluetooth.BluetoothAdapter adapter=android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                return adapter!=null&&adapter.isEnabled()?1f:0f;
            }
            if(kind==2)return android.provider.Settings.System.getInt(activity.getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,128)/255f;
            android.media.AudioManager audio=(android.media.AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
            int max=Math.max(1,audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
            return audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)/(float)max;
        }catch(Throwable ignored){return 0f;}
    }
    private String deviceState(int kind,float level){
        if(kind==0)return level>.5f?"Connected":"Tap to connect";
        if(kind==1)return level>.5f?"Connected":"Tap to connect";
        return Math.round(level*100)+"%";
    }
    private String recentDestination(int index){
        String raw=prefs.getString("recent_destinations","");
        if(raw==null||raw.isEmpty())return "";
        String[] items=raw.split("\\n");
        return index>=0&&index<items.length?items[index].trim():"";
    }
    private String destinationName(String value){
        if(value==null||value.isEmpty())return "SEARCH DESTINATION";
        int comma=value.indexOf(',');return trim((comma>0?value.substring(0,comma):value).toUpperCase(Locale.US),25);
    }
    private void destinationRow(Canvas c,float top,String icon,String label,String detail){
        RectF row=new RectF(x(42),y(top),x(493),y(top+42));p.setColor(0xff0b0e12);c.drawRoundRect(row,x(8),x(8),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(1));p.setColor(0xff353a42);c.drawRoundRect(row,x(8),x(8),p);p.setStyle(Paint.Style.FILL);
        text(c,icon,56,top+28,15,WHITE,true);text(c,label,92,top+27,13,WHITE,true);
        p.setTextAlign(Paint.Align.RIGHT);text(c,detail,474,top+27,11,MUTED,false);p.setTextAlign(Paint.Align.LEFT);
    }
    private String formatMediaTime(long millis){
        if(millis<0)millis=0;long seconds=millis/1000;
        return String.format(Locale.US,"%d:%02d",seconds/60,seconds%60);
    }
    private void mediaProgress(Canvas c,float l,float t,float r){
        long duration=MediaBridge.durationMs,position=MediaBridge.currentPositionMs();
        float level=duration>0?Math.max(0,Math.min(1,position/(float)duration)):0;
        p.setColor(0xff3b4047);c.drawRoundRect(new RectF(x(l),y(t),x(r),y(t+7)),x(4),x(4),p);
        p.setColor(RED);c.drawRoundRect(new RectF(x(l),y(t),x(l+(r-l)*level),y(t+7)),x(4),x(4),p);
        text(c,formatMediaTime(position),l,t+27,10,MUTED,false);
        p.setTextAlign(Paint.Align.RIGHT);text(c,duration>0?formatMediaTime(duration):"--:--",r,t+27,10,MUTED,false);p.setTextAlign(Paint.Align.LEFT);
    }

    private void home(Canvas c,float intro){
        hero(c,63,360,intro);
        text(c,"COMMAND CENTER",34,102,13,MUTED,true);
        text(c,"BUILT TO",747,112,17,WHITE,true);text(c,"DOMINATE",869,112,17,RED,true);

        float wifi=deviceLevel(0),bluetooth=deviceLevel(1),brightness=deviceLevel(2),volume=deviceLevel(3);
        quickControl(c,18,270,"⌁","Wi-Fi",deviceState(0,wifi),wifi);
        quickControl(c,280,532,"ᛒ","Bluetooth",deviceState(1,bluetooth),bluetooth);
        quickControl(c,542,794,"☀","Brightness",deviceState(2,brightness),brightness);
        quickControl(c,804,1062,"◖","Volume",deviceState(3,volume),volume);

        panel(c,18,468,520,900,"//  NAVIGATION");
        text(c,"VIEW MAP  ›",403,501,11,MUTED,true);
        text(c,"Tap to begin navigation",42,555,17,MUTED,false);
        text(c,"HOME",42,607,38,WHITE,true);
        text(c,"⌂",359,594,28,RED,true);
        path.reset();path.moveTo(x(320),y(690));path.cubicTo(x(350),y(620),x(422),y(690),x(468),y(610));
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(8));p.setColor(DEEP_RED);c.drawPath(path,p);
        p.setStrokeWidth(x(3));p.setColor(RED);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        text(c,"RECENT DESTINATIONS",42,722,11,MUTED,true);
        String recent=recentDestination(0);
        destinationRow(c,738,"⌂","HOME","GO");
        destinationRow(c,785,"▣","WORK","GO");
        destinationRow(c,832,"●",destinationName(recent),recent.isEmpty()?"SEARCH":"GO");

        panel(c,540,468,1062,900,"//  MEDIA");
        text(c,"NOW PLAYING",945,501,10,MUTED,true);
        RectF art=new RectF(x(563),y(538),x(747),y(722));p.setColor(0xff16080b);c.drawRoundRect(art,x(10),x(10),p);
        if(MediaBridge.artwork!=null)c.drawBitmap(MediaBridge.artwork,null,art,p);
        else if(defaultMediaArt!=null)c.drawBitmap(defaultMediaArt,null,art,p);
        marquee(c,MediaBridge.title,770,578,1035,23,WHITE,true);
        marquee(c,MediaBridge.artist,770,612,1035,14,MUTED,false);
        mediaProgress(c,770,650,1034);
        button(c,749,734,832,820,"|◀",false);
        button(c,842,721,939,833,MediaBridge.playing?"Ⅱ":"▶",true);
        button(c,949,734,1037,820,"▶|",false);
        text(c,MediaBridge.hasAccess(activity)?"TRX MEDIA CONTROLS CONNECTED":"TAP PLAY TO ENABLE MEDIA",565,872,11,MediaBridge.hasAccess(activity)?MUTED:RED,true);

        compactGauge(c,18,267,"BOOST","0","PSI",.08f);
        compactGauge(c,280,529,"COOLANT","194","°F",.62f);
        compactGauge(c,542,791,"TRANS","178","°F",.55f);
        compactGauge(c,804,1062,"BATTERY","14.4","V",.72f);

        panel(c,18,1074,520,1292,"//  WEATHER");
        text(c,activity.weatherTemp(),45,1166,45,WHITE,true);
        text(c,activity.weatherCondition(),45,1207,17,MUTED,true);
        text(c,"PLAINSBORO, NJ",45,1242,13,MUTED,false);
        text(c,"☀",370,1202,54,0xffffc849,false);

        panel(c,540,1074,1062,1292,"//  PERFORMANCE");
        text(c,"0–60",570,1150,14,MUTED,true);text(c,runTime(),570,1213,38,WHITE,true);
        line(c,760,1127,760,1250,0xff41464e,1);
        text(c,"GPS SPEED",800,1150,14,MUTED,true);text(c,Math.round(speedMph)+" MPH",800,1213,38,WHITE,true);
    }
    private void navigation(Canvas c){hero(c,63,236,1);text(c,"NAVIGATION",38,125,36,WHITE,true);text(c,"Plainsboro, NJ  •  "+activity.weatherTemp()+"  •  "+activity.weatherCondition(),38,170,20,MUTED,false);panel(c,18,248,1062,1290,"");p.setColor(0xff111820);c.drawRect(x(34),y(266),x(1046),y(1270),p);for(int i=0;i<9;i++)line(c,40,330+i*104,1040,286+i*110,0xff303d47,4);for(int i=0;i<7;i++)line(c,95+i*148,270,65+i*151,1260,0xff27323a,3);path.reset();path.moveTo(x(470),y(1240));path.cubicTo(x(380),y(1050),x(690),y(800),x(590),y(610));path.cubicTo(x(540),y(510),x(700),y(430),x(760),y(300));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(12));p.setColor(DEEP_RED);c.drawPath(path,p);p.setStrokeWidth(x(5));p.setColor(RED);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);panel(c,50,290,505,490,"NEXT TURN");text(c,"0.8 mi",80,380,43,WHITE,true);text(c,"Turn right onto Scudders Mill Rd",80,432,16,MUTED,false);button(c,744,1125,1007,1218,"OPEN MAPS",true);}
    private void media(Canvas c){hero(c,63,250,1);text(c,"MEDIA",38,137,39,WHITE,true);panel(c,18,270,1062,875,"NOW PLAYING");RectF art=new RectF(x(48),y(334),x(470),y(756));p.setShader(new LinearGradient(art.left,art.top,art.right,art.bottom,0xff5a0710,0xff111318,Shader.TileMode.CLAMP));c.drawRoundRect(art,x(14),x(14),p);p.setShader(null);if(MediaBridge.artwork!=null)c.drawBitmap(MediaBridge.artwork,null,art,p);else if(defaultMediaArt!=null)c.drawBitmap(defaultMediaArt,null,art,p);marquee(c,MediaBridge.artist,520,395,1030,18,MUTED,true);marquee(c,MediaBridge.title,520,460,1030,32,WHITE,true);text(c,MediaBridge.hasAccess(activity)?"Android MediaSession connected":"Tap play to enable media access",520,508,17,MUTED,false);button(c,520,630,690,742,"◀",false);button(c,710,610,880,762,MediaBridge.playing?"Ⅱ":"▶",true);button(c,900,630,1030,742,"▶|",false);panel(c,18,895,1062,1290,"MEDIA SOURCES");mediaShelf(c);text(c,"Swipe left or right • Add any installed audio app",46,1218,17,MUTED,false);}
    private void performance(Canvas c){hero(c,63,270,1);text(c,"PERFORMANCE",38,130,36,WHITE,true);gauge(c,18,285,258,"GPS SPEED",String.valueOf(Math.round(speedMph)),"MPH",Math.min(1,speedMph/120f));gauge(c,274,285,514,"RPM","--","RPM",.03f);gauge(c,530,285,770,"COOLANT","--","°F",.03f);gauge(c,786,285,1062,"TRANS TEMP","--","°F",.03f);panel(c,18,442,520,825,"0–60 GPS TIMER");text(c,"0–60",60,538,18,MUTED,true);text(c,runTime(),60,610,49,WHITE,true);text(c,"STATUS",280,538,18,MUTED,true);text(c,runActive?"RUNNING":(runArmed?"ARMED":"READY"),280,610,31,runActive?RED:WHITE,true);button(c,55,685,245,785,"START",true);button(c,270,685,475,785,"RESET",false);panel(c,540,442,1062,825,"ACCELERATION");for(int i=0;i<5;i++)line(c,570,520+i*58,1035,520+i*58,0xff30343a,1);for(int i=0;i<6;i++)line(c,590+i*85,500,590+i*85,790,0xff30343a,1);text(c,"Timer begins automatically above 1 MPH",615,655,18,MUTED,false);panel(c,18,845,1062,1135,"LIVE DATA");gauge(c,38,900,280,"GPS SPEED",String.valueOf(Math.round(speedMph)),"MPH",Math.min(1,speedMph/120f));gauge(c,294,900,536,"ENGINE LOAD","--","%",.03f);gauge(c,550,900,792,"INTAKE TEMP","--","°F",.03f);gauge(c,806,900,1042,"BATTERY","--","V",.03f);panel(c,18,1150,1062,1290,"SESSION HISTORY");text(c,zeroToSixty>0?"Last 0–60: "+String.format(Locale.US,"%.1f seconds",zeroToSixty):"No completed runs",50,1235,20,MUTED,false);}
    private void apps(Canvas c){
        hero(c,63,216,1);text(c,"ALL APPS",38,126,38,WHITE,true);
        text(c,displayApps.size()+" OF "+apps.size()+" INSTALLED",38,173,16,MUTED,true);
        panel(c,18,228,1062,1290,"");
        button(c,36,260,555,330,appSearch.isEmpty()?"⌕   SEARCH INSTALLED APPS":"⌕   "+trim(appSearch,28),false);
        button(c,570,260,704,330,"ALL",!favoriteAppsOnly);
        button(c,718,260,902,330,"★ FAVORITES",favoriteAppsOnly);
        button(c,916,260,1044,330,"⚙",false);
        text(c,"Swipe vertically • Hold an app for options",42,365,15,MUTED,false);
        c.save();c.clipRect(x(28),y(382),x(1008),y(1267));
        for(int i=0;i<displayApps.size();i++){
            int col=i%5,row=i/5;float l=40+col*195,t=395+row*155-appScroll;
            if(t>-5&&t<1265)appTile(c,displayApps.get(i),l,t,l+165,t+135);
        }
        c.restore();
        drawAppRail(c);
        if(displayApps.isEmpty())text(c,favoriteAppsOnly?"No favorite apps yet":"No matching apps",60,435,23,MUTED,false);
    }
    private void drawAppRail(Canvas c){
        List<Character> letters=appLetters();if(letters.isEmpty())return;
        float top=400,bottom=1245,step=(bottom-top)/Math.max(1,letters.size()-1);
        paint(MUTED,12,true);p.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<letters.size();i++)c.drawText(String.valueOf(letters.get(i)),x(1033),y(top+i*step),p);
        p.setTextAlign(Paint.Align.LEFT);line(c,1009,382,1009,1265,0xff3f454e,1);
    }
    private void mediaShelf(Canvas c){
        float tileW=178,gap=20,start=42-mediaScroll,top=965,bottom=1165;
        c.save();c.clipRect(x(32),y(945),x(1048),y(1180));
        for(int i=0;i<mediaApps.size();i++){
            float l=start+i*(tileW+gap);if(l+tileW<20||l>1060)continue;
            AppEntry app=mediaApps.get(i);RectF q=new RectF(x(l),y(top),x(l+tileW),y(bottom));
            p.setColor(0xff090b0f);c.drawRoundRect(q,x(16),x(16),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(1.5f));p.setColor(0xff6e1721);
            c.drawRoundRect(q,x(16),x(16),p);p.setStyle(Paint.Style.FILL);
            int cx=(int)x(l+tileW/2),iy=(int)y(top+25),sz=(int)x(78);
            try{app.icon.setBounds(cx-sz/2,iy,cx+sz/2,iy+sz);app.icon.draw(c);}catch(Throwable ignored){}
            paint(WHITE,14,true);p.setTextAlign(Paint.Align.CENTER);
            c.drawText(trim(app.label,17),cx,y(bottom-25),p);p.setTextAlign(Paint.Align.LEFT);
        }
        float addL=start+mediaApps.size()*(tileW+gap);RectF add=new RectF(x(addL),y(top),x(addL+tileW),y(bottom));
        p.setColor(0xff090b0f);c.drawRoundRect(add,x(16),x(16),p);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(x(2));p.setColor(RED);c.drawRoundRect(add,x(16),x(16),p);p.setStyle(Paint.Style.FILL);
        paint(RED,42,false);p.setTextAlign(Paint.Align.CENTER);c.drawText("+",x(addL+tileW/2),y(top+88),p);
        paint(RED,14,true);c.drawText("ADD APP",x(addL+tileW/2),y(bottom-25),p);p.setTextAlign(Paint.Align.LEFT);
        c.restore();
    }

    private void button(Canvas c,float l,float t,float r,float b,String label,boolean active){RectF q=new RectF(x(l),y(t),x(r),y(b));boolean hit=touchX>=q.left&&touchX<=q.right&&touchY>=q.top&&touchY<=q.bottom;raisedBox(c,q,active||hit,12);paint(WHITE,15,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(label,(q.left+q.right)/2,(q.top+q.bottom)/2+x(6),p);p.setTextAlign(Paint.Align.LEFT);}
    private void arrow(Canvas c,float xx,float yy){p.setColor(RED);path.reset();path.moveTo(x(xx),y(yy));path.lineTo(x(xx+72),y(yy+42));path.lineTo(x(xx),y(yy+84));path.close();c.drawPath(path,p);}
    private void appTile(Canvas c,AppEntry a,float l,float t,float r,float b){
        float scale=Math.min(u,usableH/1440f);Drawable icon=a.icon;int cx=(int)x((l+r)/2),top=(int)y(t+5),sz=Math.max(1,Math.round(78*scale));
        try{icon.setBounds(cx-sz/2,top,cx+sz/2,top+sz);icon.draw(c);}catch(Throwable ignored){}
        drawAppLabel(c,a.label,cx,l+5,r-5,b,scale);
        if(isFavorite(a)){p.setShader(null);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(13*scale);p.setColor(RED);p.setTextAlign(Paint.Align.RIGHT);c.drawText("★",x(r-5),y(t+15),p);p.setTextAlign(Paint.Align.LEFT);}
    }
    private void drawAppLabel(Canvas c,String label,float center,float l,float r,float bottom,float scale){
        String value=label==null?"":label.trim();p.setShader(null);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(14*scale);p.setColor(WHITE);p.setTextAlign(Paint.Align.CENTER);
        float max=x(r-l);if(p.measureText(value)<=max){c.drawText(value,center,y(bottom-12),p);p.setTextAlign(Paint.Align.LEFT);return;}
        int best=-1;float bestDistance=Float.MAX_VALUE;for(int i=1;i<value.length()-1;i++)if(value.charAt(i)==' '){float distance=Math.abs(i-value.length()/2f);if(distance<bestDistance){best=i;bestDistance=distance;}}
        String first,second;if(best>0){first=value.substring(0,best);second=value.substring(best+1);}else{int cut=Math.max(1,value.length()/2);first=value.substring(0,cut);second=value.substring(cut);}
        while(first.length()>1&&p.measureText(first)>max)first=first.substring(0,first.length()-1);while(second.length()>1&&p.measureText(second)>max)second=second.substring(0,second.length()-1);
        if(p.measureText(second)>max&&second.length()>2)second=second.substring(0,second.length()-2)+"…";
        c.drawText(first,center,y(bottom-27),p);c.drawText(second,center,y(bottom-9),p);p.setTextAlign(Paint.Align.LEFT);
    }
    private void dockIcon(Canvas c,int kind,float cx,float cy,int color){paint(color,17,true);p.setTextAlign(Paint.Align.CENTER);String icon=kind==0?"◆":kind==1?"➤":kind==2?"♫":kind==3?"⌁":"▦";c.drawText(icon,x(cx),y(cy),p);p.setTextAlign(Paint.Align.LEFT);}
    private void dock(Canvas c){float top=1302;for(int i=0;i<5;i++){float l=i*216,r=l+216,cx=(l+r)/2,lift=page==i?0:7;RectF q=new RectF(x(l+7),y(top+5+lift),x(r-7),H-safeBottom-x(7));raisedBox(c,q,page==i,18);int color=WHITE;dockIcon(c,i,cx,1345+lift,color);paint(color,14,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(PAGES[i],x(cx),y(1392+lift),p);p.setTextAlign(Paint.Align.LEFT);if(page==i)line(c,l+54,1307,r-54,1307,RED,4);}}
    private void selectPage(int next,int direction){next=Math.max(0,Math.min(4,next));if(next==page)return;page=next;transitionDirection=direction;transitionAt=SystemClock.uptimeMillis();prefs.edit().putInt("page",page).apply();activity.showLiveMap(page==1);if(page==4)loadApps();invalidate();}
    private void loadApps(){try{apps=activity.installedApps();refreshDisplayedApps();}catch(Throwable ignored){apps=new ArrayList<>();displayApps=new ArrayList<>();appScroll=0;}}
    public void reloadApps(){loadApps();invalidate();}
    public void setAppSearch(String query){appSearch=query==null?"":query.trim();appScroll=0;refreshDisplayedApps();invalidate();}
    private void refreshDisplayedApps(){
        displayApps=new ArrayList<>();String needle=appSearch.toLowerCase(Locale.US);
        for(AppEntry app:apps)if((!favoriteAppsOnly||isFavorite(app))&&(needle.isEmpty()||app.label.toLowerCase(Locale.US).contains(needle)))displayApps.add(app);
        float rows=(displayApps.size()+4)/5f,max=Math.max(0,395+rows*155-1260);appScroll=Math.max(0,Math.min(max,appScroll));
    }
    private boolean isFavorite(AppEntry app){return prefs.getStringSet("favorite_apps",java.util.Collections.emptySet()).contains(app.packageName);}
    private List<Character> appLetters(){List<Character> result=new ArrayList<>();for(AppEntry app:displayApps){char letter=Character.toUpperCase(app.label.charAt(0));if(!result.contains(letter))result.add(letter);}return result;}
    private void jumpToLetter(int letterIndex){
        List<Character> letters=appLetters();if(letters.isEmpty())return;letterIndex=Math.max(0,Math.min(letters.size()-1,letterIndex));char target=letters.get(letterIndex);
        for(int i=0;i<displayApps.size();i++)if(Character.toUpperCase(displayApps.get(i).label.charAt(0))==target){float rows=(displayApps.size()+4)/5f,max=Math.max(0,395+rows*155-1260);appScroll=Math.max(0,Math.min(max,(i/5)*155));break;}invalidate();
    }
    private int appIndexAt(float xx,float yy){
        float localX=xx-40,localY=yy-395+appScroll;if(localX<0||localY<0)return -1;
        int col=(int)(localX/195),row=(int)(localY/155);if(col<0||col>=5||localX-col*195>165||localY-row*155>135)return -1;
        int index=row*5+col;return index<displayApps.size()?index:-1;
    }
    private int maxAppScroll(){float rows=(displayApps.size()+4)/5f;return Math.max(0,Math.round(395+rows*155-1260));}
    private void startAppTracking(MotionEvent e){
        if(appScroller!=null&&!appScroller.isFinished())appScroller.abortAnimation();
        if(appVelocity!=null)appVelocity.recycle();appVelocity=android.view.VelocityTracker.obtain();appVelocity.addMovement(e);
        float xx=e.getX()/Math.max(.01f,u),yy=(e.getY()-safeTop)*1440f/Math.max(1,usableH);
        heldAppIndex=appIndexAt(xx,yy);appLongPressOpened=false;clock.removeCallbacks(openHeldAppOptions);
        if(heldAppIndex>=0)clock.postDelayed(openHeldAppOptions,480);
    }
    private void cancelHeldApp(){clock.removeCallbacks(openHeldAppOptions);heldAppIndex=-1;}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            downX=e.getX();downY=e.getY();scrollAtDown=appScroll;mediaScrollAtDown=mediaScroll;touchX=downX;touchY=downY;
            if(page==4)startAppTracking(e);invalidate();return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE){
            touchX=e.getX();touchY=e.getY();if(page==4&&appVelocity!=null)appVelocity.addMovement(e);
            float movedX=Math.abs(e.getX()-downX),movedY=Math.abs(e.getY()-downY);
            if(movedX>x(16)||movedY>x(16))cancelHeldApp();
            if(page==2&&e.getY()>y(930)&&e.getY()<y(1200)){float max=Math.max(0,(mediaApps.size()+1)*198-1000);mediaScroll=Math.max(0,Math.min(max,mediaScrollAtDown+(downX-e.getX())/Math.max(.01f,u)));}
            if(page==4&&e.getY()>y(365)){appScroll=Math.max(0,Math.min(maxAppScroll(),scrollAtDown+(downY-e.getY())/Math.max(.01f,usableH)*1440f));}
            invalidate();return true;
        }
        if(e.getAction()!=MotionEvent.ACTION_UP&&e.getAction()!=MotionEvent.ACTION_CANCEL)return true;
        clock.removeCallbacks(openHeldAppOptions);
        float upX=e.getX(),upY=e.getY(),xx=upX/u,yy=(upY-safeTop)*1440f/usableH;
        float moveX=Math.abs(upX-downX),moveY=Math.abs(upY-downY);touchX=touchY=-1;
        if(appLongPressOpened){cancelHeldApp();if(appVelocity!=null){appVelocity.recycle();appVelocity=null;}return true;}
        if(page==4&&appVelocity!=null){appVelocity.addMovement(e);appVelocity.computeCurrentVelocity(1000);float vy=appVelocity.getYVelocity();if(moveY>x(24)&&Math.abs(vy)>180){int logicalVelocity=Math.round(-vy*1440f/Math.max(1,usableH));appScroller.fling(0,Math.round(appScroll),0,logicalVelocity,0,0,0,maxAppScroll());postInvalidateOnAnimation();}}
        if(appVelocity!=null){appVelocity.recycle();appVelocity=null;}cancelHeldApp();
        if(yy>=1300){selectPage((int)(xx/216),xx/216>page?1:-1);return true;}
        if(page==0&&yy>365&&yy<462){
            int quick=Math.max(0,Math.min(3,(int)((xx-18)/262)));activity.openQuickPanel(quick);return true;
        }
        if(page==0&&xx<530&&yy>468&&yy<905){
            String recent=recentDestination(0);
            if(yy>=730&&yy<782){activity.openNavigation();return true;}
            if(yy>=782&&yy<829){activity.openNavigationTo(prefs.getString("work_destination","Work"));return true;}
            if(yy>=829){if(recent.isEmpty())selectPage(1,1);else activity.openNavigationTo(recent);return true;}
            selectPage(1,1);return true;
        }
        if(page==0&&xx>=530&&yy>468&&yy<905){
            if(!MediaBridge.hasAccess(activity)){MediaBridge.requestAccess(activity);return true;}
            if(yy>705){if(xx<837)MediaBridge.previous(activity);else if(xx<944)MediaBridge.toggle(activity);else MediaBridge.next(activity);}
            else selectPage(2,1);return true;
        }
        if(page==0&&yy>900&&yy<1068){selectPage(3,1);return true;}
        if(page==0&&xx>530&&yy>1070&&yy<1295){selectPage(3,1);return true;}
        if(page==1&&xx>700&&yy>1080){activity.openNavigation();return true;}
        if(page==2&&yy>600&&yy<790){if(xx<700)MediaBridge.previous(activity);else if(xx<895)MediaBridge.toggle(activity);else MediaBridge.next(activity);return true;}
        if(page==2&&yy>930&&yy<1190){if(moveX>x(24)){invalidate();return true;}int item=(int)((xx-42+mediaScroll)/198);if(item>=0&&item<mediaApps.size())activity.launch(mediaApps.get(item));else if(item==mediaApps.size())activity.openMediaAppPicker();return true;}
        if(page==3&&yy>665&&yy<810){if(xx<258)armRun();else if(xx<520)resetRun();return true;}
        if(page==4){
            if(yy>250&&yy<342&&moveX<x(18)&&moveY<x(18)){
                if(xx<565){activity.showAppSearch(appSearch);return true;}
                if(xx<712){favoriteAppsOnly=false;appScroll=0;refreshDisplayedApps();invalidate();return true;}
                if(xx<910){favoriteAppsOnly=true;appScroll=0;refreshDisplayedApps();invalidate();return true;}
                activity.openSettingsScreen();return true;
            }
            if(xx>995&&yy>365&&yy<1280&&moveX<x(18)&&moveY<x(18)){List<Character> letters=appLetters();if(!letters.isEmpty()){int li=Math.round((yy-400)/Math.max(1,(1245-400f)/Math.max(1,letters.size()-1)));jumpToLetter(li);}return true;}
            if(moveX>x(24)||moveY>x(24)){invalidate();return true;}
            int index=appIndexAt(xx,yy);if(index>=0)activity.launch(displayApps.get(index));
        }
        invalidate();return true;
    }

}
