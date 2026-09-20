package com.mdiaz.trxlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
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
    private static final int WHITE=0xfff5f5f7, PANEL=0xf20a0c10, LINE=0xff555b64;
    private int RED=0xffff2338, DEEP_RED=0xff6e0713, MUTED=0xffaeb2ba, BACKGROUND=0xff050608;
    private long lastThemeRefresh;
    private static final String[] PAGES={"Home","Navigation","Media","Performance","Apps"};
    private final MainActivity activity;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final Handler clock=new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final Bitmap heroRed,heroBaja,heroStealth,heroBlue;
    private final Bitmap defaultMediaArt;
    private List<AppEntry> apps=new ArrayList<>();
    private List<AppEntry> displayApps=new ArrayList<>();
    private List<AppEntry> mediaApps=new ArrayList<>();
    private boolean favoriteAppsOnly;
    private int appCategory,selectedAppIndex=-1;
    private String appSearch="",selectedAppPackage="";
    private android.widget.OverScroller appScroller;
    private android.view.VelocityTracker appVelocity;
    private int heldAppIndex=-1;
    private boolean appLongPressOpened;
    private final Runnable openHeldAppOptions=new Runnable(){public void run(){
        if(page==4&&heldAppIndex>=0&&heldAppIndex<displayApps.size()){
            appLongPressOpened=true;selectedAppIndex=heldAppIndex;
            selectedAppPackage=displayApps.get(heldAppIndex).packageName;touchX=touchY=-1;
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);invalidate();
        }
    }};
    private int page, appPage, safeTop, safeBottom;
    private final int[] sections = new int[5];
    private float W,H,u,usableH,downX,downY,touchX=-1,touchY=-1,appScroll,scrollAtDown,mediaScroll,mediaScrollAtDown;
    private long launchAt=SystemClock.uptimeMillis(), transitionAt, lastMediaRefresh, runStarted;
    private float speedMph, zeroToSixty;
    private final java.util.ArrayList<Float> speedTrace=new java.util.ArrayList<>();
    private long lastGpsAt;
    private boolean runArmed, runActive;
    private int transitionDirection;
    private final Runnable ticker=new Runnable(){public void run(){invalidate();clock.postDelayed(this,33);}};

    public DashboardView(MainActivity context){
        super(context);activity=context;setFocusable(true);
        appScroller=new android.widget.OverScroller(context);
        prefs=context.getSharedPreferences("launcher",Context.MODE_PRIVATE);
        if(!prefs.getBoolean("trx_launcher_v4_migrated",false)){
            prefs.edit().putInt("theme_choice",0).putBoolean("trx_launcher_v4_migrated",true).apply();
        }
        reloadTheme();
        int startup=prefs.getInt("startup_page",-1);
        page=startup<0?Math.max(0,Math.min(4,prefs.getInt("page",0))):Math.max(0,Math.min(4,startup));
        heroRed=BitmapFactory.decodeResource(getResources(),R.drawable.trx_hero_banner);
        heroBaja=BitmapFactory.decodeResource(getResources(),R.drawable.trx_hero_baja);
        heroStealth=BitmapFactory.decodeResource(getResources(),R.drawable.trx_hero_stealth);
        heroBlue=BitmapFactory.decodeResource(getResources(),R.drawable.trx_hero_blue);
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
    public void reloadTheme(){
        int choice=prefs.getInt("theme_choice",1);
        if(choice==1)RED=0xffff9f1a;
        else if(choice==2)RED=0xffd9dde3;
        else if(choice==3)RED=0xff438cff;
        else if(choice==4)RED=prefs.getInt("custom_accent",0xffff2338);
        else RED=0xffff2338;
        float[] hsv=new float[3];android.graphics.Color.colorToHSV(RED,hsv);hsv[2]=Math.max(.12f,hsv[2]*.42f);hsv[1]=Math.min(1f,hsv[1]*1.12f);DEEP_RED=android.graphics.Color.HSVToColor(hsv);
        int display=prefs.getInt("display_mode",0),hour=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean night=display==2||(display==0&&(hour<7||hour>=19));
        BACKGROUND=night?0xff020305:0xff07090d;MUTED=night?0xff9da2ab:0xffb8bdc5;lastThemeRefresh=SystemClock.uptimeMillis();invalidate();
    }

    @Override protected void onDraw(Canvas c){try{if(SystemClock.uptimeMillis()-lastThemeRefresh>60000)reloadTheme();drawLauncher(c);}catch(Throwable error){c.drawColor(0xff050607);p.setColor(RED);p.setTextSize(28);c.drawText("TRX LAUNCHER DIAGNOSTIC",30,90,p);p.setColor(WHITE);p.setTextSize(18);c.drawText(error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage()),30,135,p);}}

    private void drawLauncher(Canvas c){
        W=getWidth();H=getHeight();u=W/1080f;usableH=Math.max(1,H-safeTop-safeBottom);c.drawColor(BACKGROUND);carbon(c);status(c);
        if(SystemClock.uptimeMillis()-lastMediaRefresh>1000){MediaBridge.refresh(activity);lastMediaRefresh=SystemClock.uptimeMillis();}
        float intro=Math.min(1f,(SystemClock.uptimeMillis()-launchAt)/900f),slide=0;
        if(transitionAt>0){float q=Math.min(1f,(SystemClock.uptimeMillis()-transitionAt)/360f);q=1-(1-q)*(1-q)*(1-q);slide=transitionDirection*(1-q)*W;if(q>=1)transitionAt=0;}
        c.save();c.translate(slide,sy(34)*(1-intro));drawPage(c,intro);c.restore();dock(c);
    }
    private float x(float n){return n*u;}private float y(float n){return safeTop+n*usableH/1440f;}private float sy(float n){return n*usableH/1440f;}
    private void paint(int color,float size,boolean bold){p.setColor(color);p.setTextSize(x(size));p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setStyle(Paint.Style.FILL);p.setShader(null);p.setAlpha(255);}
    private void text(Canvas c,String s,float xx,float yy,float size,int color,boolean bold){paint(color,size,bold);c.drawText(s,x(xx),y(yy),p);}
    private void fittedText(Canvas c,String s,float left,float baseline,float right,float size,int color,boolean bold){paint(color,size,bold);android.text.TextPaint tp=new android.text.TextPaint(p);String fitted=android.text.TextUtils.ellipsize(s==null?"":s,tp,x(right-left),android.text.TextUtils.TruncateAt.END).toString();c.drawText(fitted,x(left),y(baseline),p);}
    private void line(Canvas c,float a,float b,float d,float e,int color,float width){p.setShader(null);p.setColor(color);p.setAlpha(255);p.setStrokeWidth(x(width));p.setStyle(Paint.Style.STROKE);c.drawLine(x(a),y(b),x(d),y(e),p);p.setStyle(Paint.Style.FILL);}
    private void raisedBox(Canvas c,RectF q,boolean selected,float radius){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(0xaa000000);
        RectF shadow=new RectF(q);shadow.offset(0,x(5));c.drawRoundRect(shadow,x(radius+2),x(radius+2),p);
        p.setShader(new LinearGradient(q.left,q.top,q.left,q.bottom,
            selected?0xff292b2d:0xff16191d,0xff020304,Shader.TileMode.CLAMP));
        c.drawRoundRect(q,x(radius),x(radius),p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(selected?2:1));p.setColor(selected?RED:0xff555b63);
        c.drawRoundRect(q,x(radius),x(radius),p);
        RectF inner=new RectF(q);inner.inset(x(6),x(6));p.setStrokeWidth(x(1));
        p.setColor(selected?((RED&0x00ffffff)|0x88000000):0xff2e343b);c.drawRoundRect(inner,x(Math.max(3,radius-5)),x(Math.max(3,radius-5)),p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(q.left,q.top,q.right,q.top,RED&0x00ffffff,
            selected?RED:0x006f747d,Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(q.left+x(22),q.top,q.right-x(22),q.top+x(selected?4:2)),x(2),x(2),p);
        p.setShader(null);
    }
    private void carbon(Canvas c){p.setColor(0xff07090c);c.drawRect(0,safeTop,W,H-safeBottom,p);p.setStrokeWidth(x(1));p.setColor(0x221f252b);for(float i=-H;i<W+H;i+=x(34)){c.drawLine(i,safeTop,i+H,H-safeBottom,p);c.drawLine(i+x(8),safeTop,i+H+x(8),H-safeBottom,p);}}
    private void status(Canvas c){
        RectF r=new RectF(0,y(0),W,y(72));p.setShader(new LinearGradient(0,y(0),W,y(0),0xff020304,0xff101317,Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);
        String now=new SimpleDateFormat("h:mm a",Locale.US).format(new Date());
        text(c,"RAM",28,46,24,WHITE,true);line(c,118,20,118,50,RED,3);text(c,"TRX LAUNCHER",138,44,15,RED,true);
        paint(MUTED,13,true);p.setTextAlign(Paint.Align.RIGHT);c.drawText(activity.weatherTemp()+"  •  LOCAL WEATHER",x(825),y(43),p);
        paint(WHITE,22,true);c.drawText(now,x(1028),y(46),p);p.setTextAlign(Paint.Align.LEFT);
        line(c,0,71,1080,71,(RED&0x00ffffff)|0x99000000,2);
    }
    private void drawPage(Canvas c,float intro){switch(page){case 0:home(c,intro);break;case 1:navigation(c);break;case 2:media(c);break;case 3:performance(c);break;default:apps(c);}drawSection(c);}

    private void drawSection(Canvas c){
        int tab=sections[page];
        if(tab==0||page==1||page==4)return;
        p.setColor(BACKGROUND);c.drawRect(x(18),y(164),x(1062),y(1295),p);
        if(page==0){
            if(tab==1){
                panel(c,32,174,1048,1278,"DRIVE");
                text(c,Math.round(speedMph)+" MPH",70,470,82,WHITE,true);
                text(c,"LIVE GPS SPEED",74,520,17,RED,true);
                text(c,"Vehicle systems remain factory controlled",70,570,20,MUTED,false);
                button(c,70,690,500,810,"OPEN NAVIGATION",true);button(c,550,690,1000,810,"PERFORMANCE",false);
            }else if(tab==2){
                panel(c,32,174,1048,1278,"QUICK CONTROLS");
                String[] labels={"BLUETOOTH / AUDIO","SOUND SETTINGS","MEDIA SOURCES","SYSTEM SETTINGS"};
                for(int i=0;i<4;i++)button(c,70,285+i*190,1010,430+i*190,labels[i],false);
            }else{
                panel(c,32,174,1048,1278,"WEATHER • CURRENT LOCATION");
                text(c,activity.weatherTemp(),70,535,90,WHITE,true);text(c,activity.weatherCondition(),70,625,28,MUTED,false);
                button(c,70,770,1010,900,"REFRESH WEATHER",true);
            }return;
        }
        if(page==2){
            if(tab==1){
                panel(c,32,174,1048,1278,"UP NEXT");
                String[] titles=MediaBridge.queueTitles;
                if(titles.length==0){text(c,"This player has not shared a queue.",70,345,26,WHITE,true);text(c,"Open the player to view or edit its queue.",70,405,20,MUTED,false);}
                for(int i=0;i<Math.min(6,titles.length);i++)button(c,60,250+i*140,1020,365+i*140,trim(titles[i],70),false);
                button(c,60,1140,1020,1250,"OPEN PLAYER",true);
            }else if(tab==2){
                panel(c,32,174,1048,1278,"MEDIA SOURCES");
                text(c,"Swipe the source shelf • Tap an app to open",65,285,20,MUTED,false);
                mediaShelf(c);button(c,65,365,1015,485,"ADD / REMOVE SOURCES",true);
            }else{
                panel(c,32,174,1048,1090,"AUDIO");
                text(c,activity.audioRouteName(),65,315,28,WHITE,true);
                button(c,65,385,1015,505,"CHANGE AUDIO OUTPUT",false);
                button(c,65,550,1015,670,"SYSTEM SOUND / EQUALIZER",false);
                text(c,"Profiles set volume caps; EQ support depends on your player.",65,790,20,MUTED,false);
                drawDriveSound(c);
            }return;
        }
        if(page==3){
            if(tab==1){
                panel(c,32,174,1048,1278,"0–60 GPS TIMER • CLOSED COURSE ONLY");
                text(c,runTime(),70,510,82,WHITE,true);text(c,runActive?"RUNNING":runArmed?"ARMED — STARTS ABOVE 1 MPH":"STOP BEFORE ARMING",70,585,24,RED,true);
                button(c,70,700,490,830,"ARM",true);button(c,550,700,1010,830,"RESET",false);
                text(c,"GPS estimate; not a calibrated performance instrument.",70,1020,20,MUTED,false);
            }else if(tab==2){
                String[] labels={"RPM","BOOST","COOLANT","INTAKE TEMP","BATTERY","ENGINE LOAD"};
                float[] values={ObdBridge.rpm,ObdBridge.boostPsi,ObdBridge.coolantF,ObdBridge.intakeF,ObdBridge.batteryV,ObdBridge.engineLoad};
                String[] units={"RPM","PSI","°F","°F","V","%"};
                float[] limits={7000,15,260,200,16,100};
                for(int i=0;i<6;i++){float left=32+(i%2)*516,top=184+(i/2)*305;metricCard(c,left,top,left+500,labels[i],obdText(values[i],i==1||i==4?1:0),units[i],obdLevel(values[i],0,limits[i]));warningBadge(c,left,top,left+500,labels[i]);}
                button(c,60,1190,1020,1270,"CONNECT / PAIR OBDLINK",false);
            }else{
                panel(c,32,174,1048,1278,"SAVED 0–60 RUNS");
                String raw=prefs.getString("performance_runs","");String[] runs=raw.isEmpty()?new String[0]:raw.split("\\|");
                if(runs.length==0)text(c,"No completed runs yet",65,330,24,MUTED,false);
                for(int i=0;i<runs.length;i++)text(c,(i+1)+".  "+runs[i]+" seconds",65,300+i*72,23,WHITE,false);
                button(c,60,1160,1020,1250,"CLEAR HISTORY",false);
            }
        }
    }

    private boolean touchSection(float xx,float yy){
        int tab=sections[page];if(tab==0||yy<164)return false;
        if(page==0){
            if(tab==1&&yy>690&&yy<830)selectPage(xx<520?1:3,1);
            if(tab==2&&yy>285&&yy<1000){int i=(int)((yy-285)/190);if(i==0)activity.openAudioRouteSettings();else if(i==1)activity.openSoundSettings();else if(i==2)activity.openMediaAppPicker();else activity.openSystemSettings();}
            if(tab==3&&yy>770&&yy<900)activity.refreshWeather();return true;
        }
        if(page==2){
            if(tab==1){if(yy>1140)activity.openMedia();else if(yy>=250)MediaBridge.playQueueItem(activity,(int)((yy-250)/140));}
            else if(tab==2){if(yy>365&&yy<485)activity.openMediaAppPicker();else if(yy>960){int i=(int)((xx-42+mediaScroll)/200);if(i>=0&&i<mediaApps.size())activity.launch(mediaApps.get(i));else activity.openMediaAppPicker();}}
            else if(tab==3){if(yy>385&&yy<505)activity.openAudioRouteSettings();else if(yy>550&&yy<670)activity.openSoundSettings();else if(yy>1198){if(xx<512)activity.setMediaProfile(xx<210?0:xx<360?1:2);else if(xx<620)activity.openSoundSettings();else if(xx<840)activity.setMediaVolumeLevel(Math.max(0,Math.min(1,(xx-634)/194)));else activity.toggleAutoVolume();}}
            return true;
        }
        if(page==3){
            if(tab==1&&yy>700&&yy<830){if(xx<520){if(speedMph<1)armRun();else android.widget.Toast.makeText(activity,"Stop before arming the timer",android.widget.Toast.LENGTH_SHORT).show();}else resetRun();}
            if(tab==2&&yy>1190)activity.openObdSetup();
            if(tab==3&&yy>1160)new android.app.AlertDialog.Builder(activity).setTitle("Clear saved runs?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,i)->{prefs.edit().remove("performance_runs").apply();invalidate();}).show();
            return true;
        }return false;
    }

    public void onSpeedChanged(float mph){speedMph=mph;lastGpsAt=SystemClock.elapsedRealtime();speedTrace.add(mph);if(speedTrace.size()>120)speedTrace.remove(0);activity.applySpeedCompensation(mph);if(runArmed&&!runActive&&mph>=1f){runActive=true;runStarted=SystemClock.elapsedRealtime();}if(runActive&&mph>=60f){zeroToSixty=(SystemClock.elapsedRealtime()-runStarted)/1000f;runActive=false;runArmed=false;saveCompletedRun(zeroToSixty);}invalidate();}
    private void saveCompletedRun(float seconds){
        if(seconds<=0||seconds>60)return;
        String old=prefs.getString("performance_runs","");
        String entry=String.format(Locale.US,"%.2f",seconds);
        String next=entry+(old==null||old.isEmpty()?"":"|"+old);
        String[] values=next.split("\\|");
        StringBuilder limited=new StringBuilder();
        for(int i=0;i<Math.min(10,values.length);i++){
            if(i>0)limited.append('|');limited.append(values[i]);
        }
        prefs.edit().putString("performance_runs",limited.toString()).apply();
    }
    private float[] runStats(){
        String raw=prefs.getString("performance_runs","");
        if(raw==null||raw.isEmpty())return new float[]{0,0,0};
        String[] values=raw.split("\\|");float last=0,best=Float.MAX_VALUE;int count=0;
        for(String value:values)try{float item=Float.parseFloat(value);if(item>0){if(count==0)last=item;best=Math.min(best,item);count++;}}catch(Throwable ignored){}
        return new float[]{last,best==Float.MAX_VALUE?0:best,count};
    }
    private boolean performanceAlerts(){return prefs.getBoolean("performance_alerts",true);}
    private float warningValue(String key,float fallback){return prefs.getFloat(key,fallback);}
    private boolean gaugeWarning(String label){
        if(!performanceAlerts()||!ObdBridge.connected)return false;
        if("COOLANT".equals(label))return !Float.isNaN(ObdBridge.coolantF)&&ObdBridge.coolantF>=warningValue("warn_coolant",235f);
        if("INTAKE TEMP".equals(label))return !Float.isNaN(ObdBridge.intakeF)&&ObdBridge.intakeF>=warningValue("warn_intake",170f);
        if("BATTERY".equals(label))return !Float.isNaN(ObdBridge.batteryV)&&ObdBridge.batteryV<=warningValue("warn_voltage",11.8f);
        return false;
    }
    private void warningBadge(Canvas c,float l,float t,float r,String label){
        if(!gaugeWarning(label))return;
        float pulse=.55f+.45f*(float)Math.sin(SystemClock.uptimeMillis()/180.0);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(4));p.setColor(RED);p.setAlpha((int)(130+125*pulse));
        c.drawRoundRect(new RectF(x(l),y(t),x(r),y(t+138)),x(10),x(10),p);
        p.setStyle(Paint.Style.FILL);p.setAlpha(255);
        p.setTextAlign(Paint.Align.RIGHT);text(c,"WARNING",r-14,t+31,10,RED,true);p.setTextAlign(Paint.Align.LEFT);
    }

    private void armRun(){if(speedMph>=1||lastGpsAt==0||SystemClock.elapsedRealtime()-lastGpsAt>3000){android.widget.Toast.makeText(activity,"Stop with a current GPS fix before arming",android.widget.Toast.LENGTH_SHORT).show();return;}zeroToSixty=0;runActive=false;runArmed=true;speedTrace.clear();}
    private void resetRun(){zeroToSixty=0;runActive=false;runArmed=false;}
    private String runTime(){if(runActive)return String.format(Locale.US,"%.1f s",(SystemClock.elapsedRealtime()-runStarted)/1000f);if(zeroToSixty>0)return String.format(Locale.US,"%.1f s",zeroToSixty);return "--.- s";}
    private String trim(String value,int max){if(value==null)return "";return value.length()>max?value.substring(0,max-1)+"…":value;}
    public int currentPage(){return page;}
    public int currentSection(){return sections[page];}
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
    private Bitmap themedHero(){
        int choice=prefs.getInt("theme_choice",1);
        if(choice==1&&heroBaja!=null)return heroBaja;
        if(choice==2&&heroStealth!=null)return heroStealth;
        if(choice==3&&heroBlue!=null)return heroBlue;
        return heroRed;
    }
    private void hero(Canvas c,float top,float bottom,float intro){
        Bitmap hero=themedHero();if(hero==null)return;
        float reveal=1-(1-intro)*(1-intro),targetRatio=W/Math.max(1f,sy(bottom-top)),sourceRatio=(float)hero.getWidth()/hero.getHeight();
        Rect src;if(sourceRatio>targetRatio){int crop=(int)((hero.getWidth()-hero.getHeight()*targetRatio)/2f);src=new Rect(crop,0,hero.getWidth()-crop,hero.getHeight());}else{int crop=(int)((hero.getHeight()-hero.getWidth()/targetRatio)/2f);src=new Rect(0,crop,hero.getWidth(),hero.getHeight()-crop);}
        float lift=sy(22)*(1-reveal);RectF target=new RectF(0,y(top)+lift,W,y(bottom)+lift);p.setAlpha((int)(255*reveal));c.drawBitmap(hero,src,target,p);p.setAlpha(255);
        if(prefs.getInt("theme_choice",1)==4){p.setColor((RED&0x00ffffff)|0x30000000);c.drawRect(target,p);}
        p.setShader(new LinearGradient(0,y(top),0,y(bottom),0x00000000,0xd907090c,Shader.TileMode.CLAMP));c.drawRect(0,y(top),W,y(bottom),p);p.setShader(null);
    }
    private void panel(Canvas c,float l,float t,float r,float b,String title){RectF q=new RectF(x(l),y(t),x(r),y(b));raisedBox(c,q,false,12);if(!title.isEmpty()){line(c,l+16,t+45,r-16,t+45,0xff42474f,1);text(c,title,l+18,t+32,18,WHITE,true);}line(c,l+28,b-5,r-28,b-5,(RED&0x00ffffff)|0xbb000000,2);}
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

    private String weatherIcon(){
        String condition=activity.weatherCondition();
        condition=condition==null?"":condition.toUpperCase(Locale.US);
        if(condition.contains("THUNDER"))return "ϟ";
        if(condition.contains("SNOW")||condition.contains("ICE"))return "❄";
        if(condition.contains("RAIN")||condition.contains("DRIZZLE")||condition.contains("SHOWER"))return "☂";
        if(condition.contains("CLOUD")||condition.contains("FOG")||condition.contains("OVERCAST"))return "☁";
        return "☀";
    }

    private void home(Canvas c,float intro){
        sectionTabs(c,32,82,new String[]{"Dashboard","Drive","Controls","Weather"},0);
        panel(c,32,164,742,1284,"");
        text(c,"GOOGLE NAVIGATION",56,206,15,WHITE,true);
        text(c,"LIVE MAP • TAP NAVIGATION FOR SEARCH AND GUIDANCE",56,235,11,MUTED,true);

        panel(c,762,164,1048,672,"");text(c,"NOW PLAYING",786,208,14,MUTED,true);
        text(c,MediaBridge.hasAccess(activity)?"● CONNECTED":"● SETUP REQUIRED",786,238,11,MediaBridge.hasAccess(activity)?0xff55d88a:RED,true);
        RectF art=new RectF(x(786),y(265),x(1024),y(503));p.setColor(0xff18080a);c.drawRoundRect(art,x(16),x(16),p);if(MediaBridge.artwork!=null)c.drawBitmap(MediaBridge.artwork,null,art,p);else if(defaultMediaArt!=null)c.drawBitmap(defaultMediaArt,null,art,p);
        fittedText(c,MediaBridge.title,786,545,1024,18,WHITE,true);fittedText(c,MediaBridge.artist,786,577,1024,14,MUTED,false);
        button(c,786,596,852,654,"|◀",false);button(c,861,586,949,662,MediaBridge.playing?"Ⅱ":"▶",true);button(c,958,596,1024,654,"▶|",false);

        panel(c,762,690,1048,1284,"");text(c,"LIVE VEHICLE",786,734,14,MUTED,true);
        text(c,ObdBridge.connected?"● OBDLINK MX+":"● GPS MODE",786,765,11,ObdBridge.connected?0xff55d88a:MUTED,true);
        vehicleValue(c,786,810,"GPS SPEED",Math.round(speedMph)+" MPH");vehicleValue(c,786,925,"COOLANT",obdText(ObdBridge.coolantF,0)+"°F");
        vehicleValue(c,786,1040,"BATTERY",obdText(ObdBridge.batteryV,1)+" V");vehicleValue(c,786,1155,"BOOST",obdText(ObdBridge.boostPsi,1)+" PSI");
    }

    private void miniStat(Canvas c,float l,float top,float r,float bottom,String label,String value){RectF q=new RectF(x(l),y(top),x(r),y(bottom));raisedBox(c,q,false,10);text(c,label,l+14,top+26,9,MUTED,true);text(c,value,l+14,top+56,17,WHITE,true);}
    private void vehicleValue(Canvas c,float l,float top,String label,String value){line(c,l,top,l,top+90,RED,3);text(c,value,l+14,top+40,24,WHITE,true);text(c,label,l+14,top+70,9,MUTED,true);}
    private void sectionTabs(Canvas c,float l,float top,String[] labels,int selected){
        selected=page==4?selected:sections[page];
        raisedBox(c,new RectF(x(l),y(top),x(1048),y(top+70)),false,18);
        float width=(900-l)/labels.length;
        for(int i=0;i<labels.length;i++){float a=l+i*width,b=a+width;if(i==selected){p.setColor(RED);c.drawRoundRect(new RectF(x(a+8),y(top+8),x(b-8),y(top+62)),x(13),x(13),p);}paint(i==selected?WHITE:MUTED,15,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(labels[i],x((a+b)/2),y(top+44),p);}
        paint(RED,14,true);c.drawText("SETTINGS",x(974),y(top+44),p);p.setTextAlign(Paint.Align.LEFT);
    }

    private void precisionGauge(Canvas c,float l,float r,String label,String value,String unit,float level){
        RectF q=new RectF(x(l),y(940),x(r),y(1285));raisedBox(c,q,false,10);text(c,label,l+24,984,11,MUTED,true);line(c,l+24,999,l+72,999,RED,3);
        RectF arc=new RectF(x(l+36),y(1025),x(r-36),y(1218));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(12));p.setColor(0xff242930);c.drawArc(arc,155,230,false,p);p.setColor(RED);c.drawArc(arc,155,Math.max(9,230*Math.max(0,Math.min(1,level))),false,p);p.setStyle(Paint.Style.FILL);
        paint(WHITE,34,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(value,x((l+r)/2),y(1178),p);paint(MUTED,12,true);c.drawText(unit,x((l+r)/2),y(1208),p);p.setTextAlign(Paint.Align.LEFT);
    }
    private void navigation(Canvas c){sectionTabs(c,32,82,new String[]{"Route","Recents","Favorites","Map Options"},0);panel(c,18,164,1062,1288,"");}
    private void media(Canvas c){
        sectionTabs(c,32,82,new String[]{"Now Playing","Queue","Sources","Audio"},0);
        panel(c,32,164,246,1284,"");text(c,"SOURCES",56,208,14,MUTED,true);
        for(int i=0;i<3;i++){float t=245+i*150;RectF q=new RectF(x(52),y(t),x(226),y(t+120));raisedBox(c,q,i==0,12);String icon=i==0?"♫":i==1?"ᛒ":"+";String label=i==0?(mediaApps.isEmpty()?"MEDIA":trim(mediaApps.get(0).label,14)):i==1?"BLUETOOTH":"ADD SOURCE";text(c,icon,72,t+70,28,i==0?RED:MUTED,true);fittedText(c,label,118,t+68,214,13,i==0?WHITE:MUTED,true);}
        panel(c,262,164,770,1284,"");
        drawPlaybackDial(c,516,550,170);
        fittedText(c,MediaBridge.title,300,790,734,28,WHITE,true);fittedText(c,MediaBridge.artist,300,835,734,18,MUTED,false);
        mediaProgress(c,300,875,734);button(c,310,955,405,1050,"|◀",false);button(c,435,935,597,1070,MediaBridge.playing?"Ⅱ":"▶",true);button(c,627,955,722,1050,"▶|",false);
        text(c,MediaBridge.hasAccess(activity)?"MEDIA SESSION CONNECTED":"ONE-TIME MEDIA SETUP REQUIRED",300,1198,13,MediaBridge.hasAccess(activity)?0xff55d88a:RED,true);
        panel(c,786,164,1048,1284,"");text(c,"UP NEXT",810,208,14,MUTED,true);text(c,"● LIVE",958,208,11,0xff55d88a,true);drawCompactQueue(c,810,255);
    }

    private void drawCompactQueue(Canvas c,float l,float top){String[] titles=MediaBridge.queueTitles,artists=MediaBridge.queueArtists;for(int i=0;i<6;i++){float t=top+i*155;RectF thumb=new RectF(x(l),y(t),x(l+72),y(t+72));p.setShader(new LinearGradient(thumb.left,thumb.top,thumb.right,thumb.bottom,0xff411018,DEEP_RED,Shader.TileMode.CLAMP));c.drawRoundRect(thumb,x(10),x(10),p);p.setShader(null);text(c,"♫",l+24,t+46,20,WHITE,true);String title=i<titles.length?titles[i]:(i==0?trim(MediaBridge.title,14):"Available in player");String artist=i<artists.length?artists[i]:(i==0?trim(MediaBridge.artist,14):"");fittedText(c,title,l+84,t+31,1024,14,WHITE,true);fittedText(c,artist,l+84,t+61,1024,12,MUTED,false);line(c,l,t+112,1024,t+112,0xff30353c,1);}}
    private void drawMediaSources(Canvas c){
        panel(c,18,238,150,1138,"SOURCES");
        for(int i=0;i<4;i++){float top=305+i*195;RectF q=new RectF(x(34),y(top),x(134),y(top+170));raisedBox(c,q,i==0,12);
            if(i<2&&i<mediaApps.size()){AppEntry app=mediaApps.get(i);int cx=(int)x(84),iy=(int)y(top+20),sz=(int)x(58);try{app.icon.setBounds(cx-sz/2,iy,cx+sz/2,iy+sz);app.icon.draw(c);}catch(Throwable ignored){}paint(WHITE,10,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(trim(app.label,12),x(84),y(top+130),p);}
            else{String icon=i==2?"ᛒ":"+",label=i==2?"BLUETOOTH":"ADD SOURCE";paint(i==3?RED:WHITE,i==3?31:27,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(icon,x(84),y(top+68),p);paint(i==3?RED:WHITE,9,true);c.drawText(label,x(84),y(top+132),p);}p.setTextAlign(Paint.Align.LEFT);
        }
    }
    private void drawNowPlayingCockpit(Canvas c){
        panel(c,160,238,720,1138,"//  NOW PLAYING");text(c,MediaBridge.hasAccess(activity)?"●  MEDIA SESSION CONNECTED":"●  MEDIA ACCESS REQUIRED",500,272,9,MediaBridge.hasAccess(activity)?0xff50dc83:RED,true);
        drawPlaybackDial(c,440,620,180);
        paint(MUTED,12,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(trim(MediaBridge.artist.toUpperCase(Locale.US),34),x(440),y(315),p);paint(WHITE,22,true);c.drawText(trim(MediaBridge.title.toUpperCase(Locale.US),34),x(440),y(350),p);p.setTextAlign(Paint.Align.LEFT);
        drawWaveform(c,190,866,690,70);text(c,"DRAG WAVEFORM TO SEEK",190,973,9,MUTED,true);
    }
    private void drawPlaybackDial(Canvas c,float cx,float cy,float radius){
        long duration=MediaBridge.durationMs,position=MediaBridge.currentPositionMs();float level=duration>0?Math.max(0,Math.min(1,position/(float)duration)):0;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(24));p.setColor(0xff1c2026);c.drawCircle(x(cx),y(cy),x(radius),p);p.setStrokeWidth(x(5));p.setColor(0xff5c626b);c.drawCircle(x(cx),y(cy),x(radius-12),p);
        RectF arc=new RectF(x(cx-radius+22),y(cy)-x(radius-22),x(cx+radius-22),y(cy)+x(radius-22));p.setStrokeWidth(x(8));p.setColor(0xff2f343b);c.drawArc(arc,-90,360,false,p);p.setColor(RED);c.drawArc(arc,-90,360*level,false,p);p.setStyle(Paint.Style.FILL);
        float ar=Math.max(96,radius-54);RectF art=new RectF(x(cx-ar),y(cy)-x(ar),x(cx+ar),y(cy)+x(ar));path.reset();path.addCircle(x(cx),y(cy),x(ar),Path.Direction.CW);c.save();c.clipPath(path);p.setColor(0xff28080d);c.drawRect(art,p);if(MediaBridge.artwork!=null)c.drawBitmap(MediaBridge.artwork,null,art,p);else if(defaultMediaArt!=null)c.drawBitmap(defaultMediaArt,null,art,p);c.restore();
        p.setColor(0x99000000);c.drawCircle(x(cx),y(cy),x(58),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(3));p.setColor(RED);c.drawCircle(x(cx),y(cy),x(58),p);p.setStyle(Paint.Style.FILL);paint(WHITE,35,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(MediaBridge.playing?"Ⅱ":"▶",x(cx),y(cy)+x(12),p);p.setTextAlign(Paint.Align.LEFT);
    }
    private void drawWaveform(Canvas c,float l,float top,float r,float height){
        long duration=MediaBridge.durationMs,position=MediaBridge.currentPositionMs();float level=duration>0?Math.max(0,Math.min(1,position/(float)duration)):0,mid=top+height/2;
        for(int i=0;i<82;i++){float xx=l+(r-l)*i/81f,amp=8+(float)(Math.abs(Math.sin(i*.73)+Math.sin(i*.21))*14);line(c,xx,mid-amp,xx,mid+amp,xx<=l+(r-l)*level?RED:0xff5b6169,2);}
        float knob=l+(r-l)*level;p.setColor(WHITE);c.drawCircle(x(knob),y(mid),x(9),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(3));p.setColor(RED);c.drawCircle(x(knob),y(mid),x(12),p);p.setStyle(Paint.Style.FILL);
        text(c,formatMediaTime(position),l,top+height+24,11,WHITE,true);p.setTextAlign(Paint.Align.RIGHT);text(c,duration>0?"-"+formatMediaTime(Math.max(0,duration-position)):"--:--",r,top+height+24,11,WHITE,true);p.setTextAlign(Paint.Align.LEFT);
    }
    private void drawMediaQueue(Canvas c){
        panel(c,730,238,1062,835,"//  UP NEXT");p.setTextAlign(Paint.Align.RIGHT);text(c,"LIVE QUEUE",1034,270,9,MUTED,true);p.setTextAlign(Paint.Align.LEFT);
        String[] titles=MediaBridge.queueTitles,artists=MediaBridge.queueArtists;Bitmap[] images=MediaBridge.queueArtwork;
        if(titles.length==0){text(c,"QUEUE UNAVAILABLE",760,390,16,WHITE,true);text(c,"Active player did not publish",760,426,11,MUTED,false);text(c,"its MediaSession queue.",760,450,11,MUTED,false);return;}
        for(int i=0;i<Math.min(3,titles.length);i++){
            float top=320+i*150;RectF thumb=new RectF(x(758),y(top),x(834),y(top+76));
            p.setShader(new LinearGradient(thumb.left,thumb.top,thumb.left,thumb.bottom,0xff2a2f36,0xff080a0d,Shader.TileMode.CLAMP));c.drawRoundRect(thumb,x(9),x(9),p);p.setShader(null);
            if(i<images.length&&images[i]!=null){path.reset();path.addRoundRect(thumb,x(9),x(9),Path.Direction.CW);c.save();c.clipPath(path);c.drawBitmap(images[i],null,thumb,p);c.restore();}
            else{paint(RED,25,true);p.setTextAlign(Paint.Align.CENTER);c.drawText("♫",(thumb.left+thumb.right)/2,(thumb.top+thumb.bottom)/2+x(9),p);p.setTextAlign(Paint.Align.LEFT);}
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(1));p.setColor(0xff3b424b);c.drawRoundRect(thumb,x(9),x(9),p);p.setStyle(Paint.Style.FILL);
            text(c,trim(titles[i],20),850,top+31,13,WHITE,true);text(c,trim(i<artists.length?artists[i]:"",22),850,top+57,10,MUTED,false);line(c,756,top+112,1038,top+112,0xff343940,1);
        }
    }
    private void drawAudioRoute(Canvas c){panel(c,730,845,1062,1138,"//  AUDIO ROUTE");text(c,"ᛒ",762,955,33,WHITE,true);text(c,trim(activity.audioRouteName(),25),820,934,13,WHITE,true);text(c,"●  CONNECTED OUTPUT",820,970,10,0xff50dc83,true);button(c,890,1022,1034,1092,"CHANGE",false);}
    private void drawDriveSound(Canvas c){
        panel(c,18,1148,1062,1292,"//  DRIVE SOUND");int profile=activity.mediaProfile();button(c,40,1198,205,1270,"PERFORMANCE",profile==0);button(c,218,1198,353,1270,"NIGHT",profile==1);button(c,366,1198,512,1270,"PASSENGER",profile==2);button(c,528,1198,610,1270,"EQ",false);text(c,"VOLUME",634,1208,9,MUTED,true);
        float level=activity.mediaVolumeLevel();p.setColor(0xff363b43);c.drawRoundRect(new RectF(x(634),y(1238),x(828),y(1246)),x(4),x(4),p);p.setColor(RED);c.drawRoundRect(new RectF(x(634),y(1238),x(634+194*level),y(1246)),x(4),x(4),p);p.setColor(WHITE);c.drawCircle(x(634+194*level),y(1242),x(9),p);
        text(c,"AUTO VOLUME",852,1208,9,MUTED,true);text(c,activity.autoVolumeEnabled()?"SPEED COMP ON":"SPEED COMP OFF",852,1265,9,activity.autoVolumeEnabled()?0xff50dc83:MUTED,true);RectF toggle=new RectF(x(970),y(1224),x(1034),y(1254));p.setColor(activity.autoVolumeEnabled()?RED:0xff41464e);c.drawRoundRect(toggle,x(15),x(15),p);p.setColor(WHITE);c.drawCircle(x(activity.autoVolumeEnabled()?1018:986),y(1239),x(11),p);
    }
    private boolean handleMediaTouch(float xx,float yy,float moveX,float moveY){
        if(moveX>x(24)||moveY>x(24))return true;
        if(xx<250&&yy>235&&yy<700){int slot=(int)((yy-245)/150);if(slot==0&&!mediaApps.isEmpty())activity.launch(mediaApps.get(0));else if(slot==1)activity.openAudioRouteSettings();else activity.openMediaAppPicker();return true;}
        if(!MediaBridge.hasAccess(activity)){activity.requestMediaAccess();return true;}
        if(xx>330&&xx<705&&yy>350&&yy<730){MediaBridge.toggle(activity);return true;}
        if(xx>786&&yy>245&&yy<1190){MediaBridge.playQueueItem(activity,(int)((yy-255)/155));return true;}
        if(yy>925&&yy<1080&&xx>290&&xx<745){if(xx<420)MediaBridge.previous(activity);else if(xx>615)MediaBridge.next(activity);else MediaBridge.toggle(activity);return true;}
        if(xx>295&&xx<740&&yy>850&&yy<925){long duration=MediaBridge.durationMs;if(duration>0)MediaBridge.seekTo(activity,Math.round(duration*Math.max(0,Math.min(1,(xx-300)/434f))));return true;}
        return false;
    }
    private String obdText(float value,int decimals){
        if(Float.isNaN(value))return "--";
        return decimals==0?String.valueOf(Math.round(value)):String.format(Locale.US,"%."+decimals+"f",value);
    }
    private float obdLevel(float value,float min,float max){
        if(Float.isNaN(value)||max<=min)return .02f;
        return Math.max(.02f,Math.min(1f,(value-min)/(max-min)));
    }

    private void performance(Canvas c){
        sectionTabs(c,32,82,new String[]{"Live Data","0–60","Gauges","History"},0);
        metricCard(c,32,174,278,"RPM",obdText(ObdBridge.rpm,0),"RPM",obdLevel(ObdBridge.rpm,0,7000));metricCard(c,290,174,536,"BOOST",obdText(ObdBridge.boostPsi,1),"PSI",obdLevel(ObdBridge.boostPsi,0,15));metricCard(c,548,174,794,"COOLANT",obdText(ObdBridge.coolantF,0),"°F",obdLevel(ObdBridge.coolantF,100,240));metricCard(c,806,174,1048,"BATTERY",obdText(ObdBridge.batteryV,1),"V",obdLevel(ObdBridge.batteryV,11,15));
        panel(c,32,390,430,1070,"");text(c,"0–60 GPS TIMER",54,438,15,MUTED,true);text(c,"● "+(runActive?"RUNNING":runArmed?"ARMED":"READY"),285,438,12,0xff55d88a,true);text(c,runTime(),54,585,58,WHITE,true);text(c,runArmed?"ARMED":"AUTOMATIC ABOVE 1 MPH",54,640,15,0xff55d88a,true);button(c,54,710,210,815,"START",true);button(c,226,710,398,815,"RESET",false);
        drawSpeedTrace(c);
        panel(c,32,1088,1048,1284,"");text(c,trim(ObdBridge.status,70),54,1135,15,ObdBridge.connected?0xff55d88a:MUTED,true);float[] history=runStats();text(c,"LAST",54,1190,12,MUTED,true);text(c,history[0]>0?String.format(Locale.US,"%.2f s",history[0]):"--",54,1242,28,WHITE,true);text(c,"BEST",290,1190,12,MUTED,true);text(c,history[1]>0?String.format(Locale.US,"%.2f s",history[1]):"--",290,1242,28,WHITE,true);text(c,"RUNS",526,1190,12,MUTED,true);text(c,String.valueOf((int)history[2]),526,1242,28,WHITE,true);text(c,ObdBridge.connected?"MX+ CONNECTED":"TAP GAUGES TO CONNECT MX+",760,1230,13,RED,true);
    }
    private void drawSpeedTrace(Canvas c){
        panel(c,446,390,1048,1070,"");text(c,"GPS SPEED • LAST 120 FIXES",470,438,15,MUTED,true);
        boolean fresh=lastGpsAt>0&&SystemClock.elapsedRealtime()-lastGpsAt<3000;
        text(c,fresh?"LIVE":"NO FIX",950,438,12,fresh?0xff55d88a:MUTED,true);
        for(int i=0;i<6;i++)line(c,470,505+i*90,1024,505+i*90,0xff292e35,1);
        if(speedTrace.size()<2){text(c,"WAITING FOR GPS DATA",500,750,20,MUTED,true);return;}
        float maximum=60;for(float speed:speedTrace)maximum=Math.max(maximum,speed);
        path.reset();for(int i=0;i<speedTrace.size();i++){float px=x(485+i*525f/119),py=y(990-440*speedTrace.get(i)/maximum);if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(3));p.setColor(RED);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        text(c,"0–"+Math.round(maximum)+" MPH",470,1040,13,MUTED,false);
    }
    private void metricCard(Canvas c,float l,float top,float r,String label,String value,String unit,float level){RectF q=new RectF(x(l),y(top),x(r),y(top+195));raisedBox(c,q,false,12);text(c,label,l+20,top+38,14,MUTED,true);text(c,value,l+20,top+112,38,WHITE,true);p.setTextAlign(Paint.Align.RIGHT);text(c,unit,r-20,top+112,14,MUTED,true);p.setTextAlign(Paint.Align.LEFT);p.setColor(0xff343940);c.drawRoundRect(new RectF(x(l+20),y(top+158),x(r-20),y(top+168)),x(5),x(5),p);p.setColor(RED);c.drawRoundRect(new RectF(x(l+20),y(top+158),x(l+20+(r-l-40)*Math.max(.02f,level)),y(top+168)),x(5),x(5),p);}
    private void apps(Canvas c){
        sectionTabs(c,32,82,new String[]{"All","Driving","Media","Tools"},favoriteAppsOnly?0:appCategory);
        button(c,32,174,914,254,appSearch.isEmpty()?"⌕   SEARCH ALL APPS":"⌕   "+trim(appSearch,28),false);miniStat(c,930,174,1048,254,"LIBRARY",apps.size()+" APPS");
        panel(c,32,270,804,1278,"");
        c.save();c.clipRect(x(42),y(285),x(790),y(1265));
        for(int i=0;i<displayApps.size();i++){
            int col=i%4,row=i/4;float l=48+col*184,t=290+row*170-appScroll;
            if(t>255&&t<1270)appTile(c,displayApps.get(i),l,t,l+160,t+154);
        }
        c.restore();
        panel(c,822,270,1048,1278,"");text(c,"QUICK LAUNCH",846,312,14,MUTED,true);drawQuickLaunchV4(c);
        if(displayApps.isEmpty())text(c,favoriteAppsOnly?"NO FAVORITES YET":"NO APPS IN THIS CATEGORY",58,375,20,MUTED,true);
    }
    private void drawQuickLaunchV4(Canvas c){List<AppEntry> quick=quickApps();for(int i=0;i<Math.min(6,quick.size());i++){float t=345+i*145;RectF q=new RectF(x(842),y(t),x(1028),y(t+112));raisedBox(c,q,false,12);AppEntry a=quick.get(i);int sz=(int)x(54),cx=(int)x(878),top=(int)y(t+28);try{a.icon.setBounds(cx-sz/2,top,cx+sz/2,top+sz);a.icon.draw(c);}catch(Throwable ignored){}fittedText(c,a.label,916,t+66,1018,13,WHITE,true);}}
    private String currentAppSection(){
        if(favoriteAppsOnly)return "★  FAVORITES";
        if(appCategory==1)return "DRIVING";if(appCategory==2)return "MEDIA";if(appCategory==3)return "TOOLS";return "A–Z";
    }
    private void drawAppRail(Canvas c){
        List<Character> letters=appLetters();if(letters.isEmpty())return;
        float top=418,bottom=1238,step=(bottom-top)/Math.max(1,letters.size()-1);
        RectF rail=new RectF(x(816),y(398),x(852),y(1268));p.setColor(0x99080a0d);c.drawRoundRect(rail,x(14),x(14),p);
        int active=Math.max(0,Math.min(letters.size()-1,Math.round((appScroll/Math.max(1,maxAppScroll()))*(letters.size()-1))));
        p.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<letters.size();i++){if(i==active){p.setColor(RED);c.drawCircle(x(834),y(top+i*step-4),x(4),p);}paint(i==active?RED:MUTED,i==active?14:10,true);c.drawText(String.valueOf(letters.get(i)),x(834),y(top+i*step),p);}
        p.setTextAlign(Paint.Align.LEFT);
    }
    private void drawQuickLaunch(Canvas c){
        RectF rail=new RectF(x(862),y(260),x(1046),y(1268));raisedBox(c,rail,false,12);
        text(c,"QUICK LAUNCH //",880,302,13,WHITE,true);line(c,878,320,1030,320,0xff3e444c,1);
        List<AppEntry> quick=quickApps();
        for(int i=0;i<quick.size();i++){
            AppEntry app=quick.get(i);float top=340+i*158;RectF slot=new RectF(x(880),y(top),x(1028),y(top+134));
            p.setShader(new LinearGradient(slot.left,slot.top,slot.left,slot.bottom,0xff292f36,0xff05070a,Shader.TileMode.CLAMP));c.drawRoundRect(slot,x(12),x(12),p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(1));p.setColor(0xff414851);c.drawRoundRect(slot,x(12),x(12),p);p.setStyle(Paint.Style.FILL);
            line(c,898,top+8,1010,top+8,0x557f8791,1);
            int cx=(int)x(954),sz=(int)x(60),iy=(int)y(top+16);try{app.icon.setBounds(cx-sz/2,iy,cx+sz/2,iy+sz);app.icon.draw(c);}catch(Throwable ignored){}
            RectF labelBand=new RectF(x(888),y(top+88),x(1020),y(top+126));p.setColor(0xe6090b0f);c.drawRoundRect(labelBand,x(8),x(8),p);
            line(c,896,top+88,1012,top+88,0xff3a4149,1);paint(WHITE,10,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(trim(app.label,15),x(954),y(top+113),p);p.setTextAlign(Paint.Align.LEFT);
        }
        button(c,880,1116,1028,1176,"TRX SETTINGS",true);
        button(c,880,1188,1028,1250,"✎  EDIT",false);
    }
    private List<AppEntry> quickApps(){
        List<AppEntry> result=new ArrayList<>();
        for(AppEntry app:apps)if(isFavorite(app)&&!containsPackage(result,app.packageName)&&result.size()<5)result.add(app);
        String[] priority={"com.google.android.apps.maps","com.waze","com.spotify.music","com.google.android.apps.youtube.music","com.android.chrome","camera","music"};
        for(String token:priority)for(AppEntry app:apps)if(result.size()<5&&(app.packageName.equals(token)||app.packageName.toLowerCase(Locale.US).contains(token))&&!containsPackage(result,app.packageName)){result.add(app);break;}
        for(AppEntry app:apps)if(result.size()<5&&!containsPackage(result,app.packageName))result.add(app);
        return result;
    }
    private boolean containsPackage(List<AppEntry> list,String pkg){for(AppEntry app:list)if(app.packageName.equals(pkg))return true;return false;}
    private AppEntry selectedApp(){for(AppEntry app:apps)if(app.packageName.equals(selectedAppPackage))return app;return null;}
    private float appActionTop(){int index=-1;for(int i=0;i<displayApps.size();i++)if(displayApps.get(i).packageName.equals(selectedAppPackage)){index=i;break;}if(index<0)return 0;float top=290+(index/4)*170-appScroll+106;return Math.max(305,Math.min(1168,top));}
    private void drawAppActions(Canvas c){
        AppEntry selected=selectedApp();if(selected==null)return;float top=appActionTop();
        RectF q=new RectF(x(116),y(top),x(796),y(top+82));p.setColor(0xf20b0e12);c.drawRoundRect(q,x(13),x(13),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(2));p.setColor(0xff7b838d);c.drawRoundRect(q,x(13),x(13),p);p.setStyle(Paint.Style.FILL);
        line(c,342,top+14,342,top+68,0xff343a42,1);line(c,568,top+14,568,top+68,0xff343a42,1);
        paint(WHITE,11,true);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(isFavorite(selected)?"★  UNFAVORITE":"★  FAVORITE",x(229),y(top+49),p);
        c.drawText("ⓘ  INFO",x(455),y(top+49),p);p.setColor(0xffff5b6c);c.drawText("▱  UNINSTALL",x(682),y(top+49),p);p.setTextAlign(Paint.Align.LEFT);
    }
    private void mediaShelf(Canvas c){
        float tileW=184,gap=16,start=42-mediaScroll,top=978,bottom=1228;
        c.save();c.clipRect(x(32),y(968),x(1048),y(1240));
        for(int i=0;i<mediaApps.size();i++){
            float l=start+i*(tileW+gap);if(l+tileW<20||l>1060)continue;
            AppEntry app=mediaApps.get(i);RectF q=new RectF(x(l),y(top),x(l+tileW),y(bottom));raisedBox(c,q,false,15);
            int cx=(int)x(l+tileW/2),iy=(int)y(top+28),sz=(int)x(92);
            try{app.icon.setBounds(cx-sz/2,iy,cx+sz/2,iy+sz);app.icon.draw(c);}catch(Throwable ignored){}
            paint(MUTED,10,true);p.setTextAlign(Paint.Align.CENTER);c.drawText("MEDIA APP",cx,y(top+150),p);
            paint(WHITE,13,true);c.drawText(trim(app.label,17),cx,y(bottom-28),p);p.setTextAlign(Paint.Align.LEFT);
        }
        float addL=start+mediaApps.size()*(tileW+gap);RectF add=new RectF(x(addL),y(top),x(addL+tileW),y(bottom));raisedBox(c,add,true,15);
        paint(RED,44,false);p.setTextAlign(Paint.Align.CENTER);c.drawText("+",x(addL+tileW/2),y(top+105),p);
        paint(MUTED,10,true);c.drawText("CUSTOMIZE",x(addL+tileW/2),y(top+150),p);
        paint(WHITE,13,true);c.drawText("ADD MEDIA APP",x(addL+tileW/2),y(bottom-28),p);p.setTextAlign(Paint.Align.LEFT);
        c.restore();
    }
    private void button(Canvas c,float l,float t,float r,float b,String label,boolean active){RectF q=new RectF(x(l),y(t),x(r),y(b));boolean hit=touchX>=q.left&&touchX<=q.right&&touchY>=q.top&&touchY<=q.bottom;raisedBox(c,q,active||hit,12);paint(WHITE,15,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(label,(q.left+q.right)/2,(q.top+q.bottom)/2+x(6),p);p.setTextAlign(Paint.Align.LEFT);}
    private void arrow(Canvas c,float xx,float yy){p.setColor(RED);path.reset();path.moveTo(x(xx),y(yy));path.lineTo(x(xx+72),y(yy+42));path.lineTo(x(xx),y(yy+84));path.close();c.drawPath(path,p);}
    private void appTile(Canvas c,AppEntry a,float l,float t,float r,float b){
        boolean selected=a.packageName.equals(selectedAppPackage);float scale=Math.min(u,usableH/1440f);int cx=(int)x((l+r)/2);
        RectF card=new RectF(x(l+4),y(t+2),x(r-4),y(b));
        p.setShader(new LinearGradient(card.left,card.top,card.left,card.bottom,selected?0xff343a42:0xff242930,0xff050608,Shader.TileMode.CLAMP));c.drawRoundRect(card,x(18),x(18),p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(selected?2:1));p.setColor(selected?0xffe2e5e9:0xff333941);c.drawRoundRect(card,x(18),x(18),p);p.setStyle(Paint.Style.FILL);
        line(c,l+22,t+9,r-22,t+9,selected?0x99ffffff:0x446f7781,1);
        Drawable icon=a.icon;float iconScale=Math.max(.8f,Math.min(1.2f,prefs.getInt("app_icon_percent",100)/100f));int sz=Math.max(1,Math.round(74*scale*iconScale)),top=(int)y(t+17+(74-74*iconScale)/2f);try{icon.setBounds(cx-sz/2,top,cx+sz/2,top+sz);icon.draw(c);}catch(Throwable ignored){}
        line(c,l+17,t+107,r-17,t+107,selected?0x88aab0b8:0x443b424b,1);
        drawAppLabel(c,a.label,cx,l+10,r-10,b+1,scale);
        if(isFavorite(a)){paint(RED,12,true);p.setTextAlign(Paint.Align.RIGHT);c.drawText("★",x(r-13),y(t+23),p);p.setTextAlign(Paint.Align.LEFT);}
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
    private void dockIcon(Canvas c,int kind,float cx,float cy,int color){paint(color,23,true);p.setTextAlign(Paint.Align.CENTER);String icon=kind==0?"⌂":kind==1?"➤":kind==2?"♫":kind==3?"◴":"▦";c.drawText(icon,x(cx),y(cy),p);p.setTextAlign(Paint.Align.LEFT);}
    private void dock(Canvas c){
        float top=1300,bottom=1440;p.setShader(new LinearGradient(0,y(top),0,H-safeBottom,0xff1a1d20,0xff020304,Shader.TileMode.CLAMP));c.drawRect(0,y(top),W,H-safeBottom,p);p.setShader(null);line(c,0,1301,1080,1301,0xff747b83,1);
        for(int i=0;i<5;i++){
            float l=i*216,r=l+216,cx=(l+r)/2;boolean active=page==i;float inset=active?0:5;
            path.reset();path.moveTo(x(l+inset+(i==0?0:18)),y(top+5));path.lineTo(x(r-inset+(i==4?0:18)),y(top+5));path.lineTo(x(r-inset),y(1432));path.lineTo(x(l+inset),y(1432));path.close();
            p.setShader(new LinearGradient(x(l),y(top),x(r),y(1435),active?0xff2a2417:0xff131619,0xff020304,Shader.TileMode.CLAMP));c.drawPath(path,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(active?2:1));p.setColor(active?RED:0xff343a41);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
            if(active){p.setShader(new RadialGradient(x(cx),y(1360),x(115),((RED&0x00ffffff)|0x55000000),0x00000000,Shader.TileMode.CLAMP));c.drawRect(x(l),y(top),x(r),y(1438),p);p.setShader(null);}
            int color=active?RED:0xffd7d9dc;dockIcon(c,i,cx,1352,color);paint(color,13,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(PAGES[i].toUpperCase(Locale.US),x(cx),y(1398),p);p.setTextAlign(Paint.Align.LEFT);if(active)line(c,l+58,1423,r-58,1423,RED,3);
        }
    }
    private void selectPage(int next,int direction){next=Math.max(0,Math.min(4,next));if(next==page)return;page=next;transitionDirection=direction;transitionAt=SystemClock.uptimeMillis();prefs.edit().putInt("page",page).apply();activity.showLiveMap(page==1);if(page==4)loadApps();invalidate();}
    private void loadApps(){try{apps=activity.installedApps();refreshDisplayedApps();}catch(Throwable ignored){apps=new ArrayList<>();displayApps=new ArrayList<>();appScroll=0;}}
    public void reloadApps(){loadApps();invalidate();}
    public void setAppSearch(String query){appSearch=query==null?"":query.trim();appScroll=0;refreshDisplayedApps();invalidate();}
    private void refreshDisplayedApps(){
        displayApps=new ArrayList<>();String needle=appSearch.toLowerCase(Locale.US);
        for(AppEntry app:apps)if((!favoriteAppsOnly||isFavorite(app))&&matchesCategory(app)&&(needle.isEmpty()||app.label.toLowerCase(Locale.US).contains(needle)))displayApps.add(app);
        float rows=(displayApps.size()+3)/4f,max=Math.max(0,290+rows*170-1260);appScroll=Math.max(0,Math.min(max,appScroll));
        if(selectedApp()!=null){boolean shown=false;for(AppEntry app:displayApps)if(app.packageName.equals(selectedAppPackage)){shown=true;break;}if(!shown){selectedAppPackage="";selectedAppIndex=-1;}}
    }
    private boolean matchesCategory(AppEntry app){
        if(appCategory==0)return true;String value=(app.label+" "+app.packageName).toLowerCase(Locale.US);
        if(appCategory==1)return containsAny(value,"map","waze","car","auto","drive","nav","parking","gas","fuel","obd","torque","weather","uber","lyft");
        if(appCategory==2)return containsAny(value,"music","spotify","youtube","radio","audio","media","pandora","sound","podcast","netflix","hulu","tv");
        return containsAny(value,"setting","calculator","calendar","clock","file","drive","auth","assistant","camera","phone","contact","vpn","mail","gmail","browser","chrome");
    }
    private boolean containsAny(String value,String...tokens){for(String token:tokens)if(value.contains(token))return true;return false;}
    private boolean isFavorite(AppEntry app){return prefs.getStringSet("favorite_apps",java.util.Collections.emptySet()).contains(app.packageName);}
    private List<Character> appLetters(){List<Character> result=new ArrayList<>();for(AppEntry app:displayApps){char letter=Character.toUpperCase(app.label.charAt(0));if(!result.contains(letter))result.add(letter);}return result;}
    private void jumpToLetter(int letterIndex){
        List<Character> letters=appLetters();if(letters.isEmpty())return;letterIndex=Math.max(0,Math.min(letters.size()-1,letterIndex));char target=letters.get(letterIndex);
        for(int i=0;i<displayApps.size();i++)if(Character.toUpperCase(displayApps.get(i).label.charAt(0))==target){float rows=(displayApps.size()+3)/4f,max=Math.max(0,290+rows*170-1260);appScroll=Math.max(0,Math.min(max,(i/4)*170));break;}invalidate();
    }
    private int appIndexAt(float xx,float yy){
        float localX=xx-48,localY=yy-290+appScroll;if(localX<0||localY<0||xx>804)return -1;
        int col=(int)(localX/184),row=(int)(localY/170);if(col<0||col>=4||localX-col*184>160||localY-row*170>154)return -1;
        int index=row*4+col;return index<displayApps.size()?index:-1;
    }
    private int maxAppScroll(){float rows=(displayApps.size()+3)/4f;return Math.max(0,Math.round(290+rows*170-1260));}
    
    private void startAppTracking(MotionEvent e){
        if(appScroller!=null&&!appScroller.isFinished())appScroller.abortAnimation();
        if(appVelocity!=null)appVelocity.recycle();appVelocity=android.view.VelocityTracker.obtain();appVelocity.addMovement(e);
        float xx=e.getX()/Math.max(.01f,u),yy=(e.getY()-safeTop)*1440f/Math.max(1,usableH);
        heldAppIndex=appIndexAt(xx,yy);appLongPressOpened=false;clock.removeCallbacks(openHeldAppOptions);
        if(heldAppIndex>=0)clock.postDelayed(openHeldAppOptions,480);
    }
    private void cancelHeldApp(){clock.removeCallbacks(openHeldAppOptions);heldAppIndex=-1;}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();scrollAtDown=appScroll;mediaScrollAtDown=mediaScroll;touchX=downX;touchY=downY;if(page==4)startAppTracking(e);invalidate();return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE){
            touchX=e.getX();touchY=e.getY();if(page==4&&appVelocity!=null)appVelocity.addMovement(e);float movedX=Math.abs(e.getX()-downX),movedY=Math.abs(e.getY()-downY);if(movedX>x(16)||movedY>x(16))cancelHeldApp();
            if(page==2&&e.getY()>y(960)&&e.getY()<y(1245)){float max=Math.max(0,(mediaApps.size()+1)*200-1000);mediaScroll=Math.max(0,Math.min(max,mediaScrollAtDown+(downX-e.getX())/Math.max(.01f,u)));}
            if(page==4&&e.getY()>y(270)&&e.getX()<x(804)){appScroll=Math.max(0,Math.min(maxAppScroll(),scrollAtDown+(downY-e.getY())/Math.max(.01f,usableH)*1440f));}invalidate();return true;
        }
        if(e.getAction()!=MotionEvent.ACTION_UP&&e.getAction()!=MotionEvent.ACTION_CANCEL)return true;clock.removeCallbacks(openHeldAppOptions);
        float upX=e.getX(),upY=e.getY(),xx=upX/u,yy=(upY-safeTop)*1440f/usableH;float moveX=Math.abs(upX-downX),moveY=Math.abs(upY-downY);touchX=touchY=-1;
        if(appLongPressOpened){cancelHeldApp();if(appVelocity!=null){appVelocity.recycle();appVelocity=null;}return true;}
        if(page==4&&appVelocity!=null){appVelocity.addMovement(e);appVelocity.computeCurrentVelocity(1000);float vy=appVelocity.getYVelocity();if(moveY>x(24)&&Math.abs(vy)>180){int logicalVelocity=Math.round(-vy*1440f/Math.max(1,usableH));appScroller.fling(0,Math.round(appScroll),0,logicalVelocity,0,0,0,maxAppScroll());postInvalidateOnAnimation();}}
        if(appVelocity!=null){appVelocity.recycle();appVelocity=null;}cancelHeldApp();
        if(yy>=1300){selectedAppPackage="";selectPage((int)(xx/216),xx/216>page?1:-1);return true;}
        if(yy>=82&&yy<=152&&xx>=32&&xx<=1048&&moveX<x(18)&&moveY<x(18)){
            if(xx>=900){activity.openSettingsScreen();return true;}
            int tab=Math.max(0,Math.min(3,(int)((xx-32)/217)));
            sections[page]=tab;
            activity.showLiveMap(page==1);
            if(page==4){favoriteAppsOnly=false;appCategory=tab;appScroll=0;refreshDisplayedApps();}
            if(page==1)activity.navigationSection(tab);
            invalidate();return true;
        }
        if(moveX<x(24)&&moveY<x(24)&&touchSection(xx,yy)){invalidate();return true;}
        if(page==0&&xx<750&&yy>164&&yy<1290){selectPage(1,1);return true;}
        if(page==0&&xx>=750&&yy>164&&yy<680){if(!MediaBridge.hasAccess(activity)){activity.requestMediaAccess();return true;}if(yy>580){if(xx<855)MediaBridge.previous(activity);else if(xx<952)MediaBridge.toggle(activity);else MediaBridge.next(activity);}else selectPage(2,1);return true;}
        if(page==0&&xx>750&&yy>680&&yy<1290){selectPage(3,1);return true;}if(page==1&&xx>700&&yy>1080){activity.openNavigation();return true;}
        if(page==2&&handleMediaTouch(xx,yy,moveX,moveY))return true;
        if(page==3&&yy>700&&yy<830){if(xx<220)armRun();else if(xx<430)resetRun();return true;}
        if(page==4){
            AppEntry selected=selectedApp();float actionTop=appActionTop();
            if(selected!=null&&yy>actionTop&&yy<actionTop+88&&xx>110&&xx<805&&moveX<x(18)&&moveY<x(18)){if(xx<342)activity.toggleAppFavoriteFromDashboard(selected);else if(xx<568)activity.openAppInformationFromDashboard(selected);else activity.requestAppUninstallFromDashboard(selected);selectedAppPackage="";selectedAppIndex=-1;invalidate();return true;}
            if(yy>170&&yy<260&&xx<920&&moveX<x(18)&&moveY<x(18)){activity.showAppSearch(appSearch);return true;}
            if(xx>820&&yy>330&&yy<1230&&moveX<x(18)&&moveY<x(18)){int qi=(int)((yy-345)/145);List<AppEntry> quick=quickApps();if(qi>=0&&qi<quick.size())activity.launch(quick.get(qi));return true;}
            if(moveX>x(24)||moveY>x(24)){invalidate();return true;}int index=appIndexAt(xx,yy);if(index>=0){selectedAppPackage="";activity.launch(displayApps.get(index));}else{selectedAppPackage="";invalidate();}
        }
        invalidate();return true;
    }

}
