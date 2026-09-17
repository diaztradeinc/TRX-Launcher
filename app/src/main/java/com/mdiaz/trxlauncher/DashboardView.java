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
    private List<AppEntry> apps=new ArrayList<>();
    private List<AppEntry> mediaApps=new ArrayList<>();
    private int page, appPage, safeTop, safeBottom;
    private float W,H,u,usableH,downX,downY,touchX=-1,touchY=-1,appScroll,scrollAtDown,mediaScroll,mediaScrollAtDown;
    private long launchAt=SystemClock.uptimeMillis(), transitionAt, lastMediaRefresh, runStarted;
    private float speedMph, zeroToSixty;
    private boolean runArmed, runActive;
    private int transitionDirection;
    private final Runnable ticker=new Runnable(){public void run(){invalidate();clock.postDelayed(this,33);}};

    public DashboardView(MainActivity context){
        super(context);activity=context;setFocusable(true);
        prefs=context.getSharedPreferences("launcher",Context.MODE_PRIVATE);
        page=Math.max(0,Math.min(4,prefs.getInt("page",0)));
        hero=BitmapFactory.decodeResource(getResources(),R.drawable.trx_hero_banner);
        reloadMediaApps();clock.post(ticker);
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());safeTop=bars.top;safeBottom=bars.bottom;}
        else{safeTop=insets.getSystemWindowInsetTop();safeBottom=insets.getSystemWindowInsetBottom();}
        invalidate();return insets;
    }
    @Override protected void onDetachedFromWindow(){clock.removeCallbacks(ticker);super.onDetachedFromWindow();}
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
    private void panel(Canvas c,float l,float t,float r,float b,String title){RectF q=new RectF(x(l),y(t),x(r),y(b));p.setColor(PANEL);c.drawRoundRect(q,x(12),x(12),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(1.5f));p.setColor(LINE);c.drawRoundRect(q,x(12),x(12),p);p.setStyle(Paint.Style.FILL);if(!title.isEmpty()){line(c,l+16,t+45,r-16,t+45,0xff30343a,1);text(c,title,l+18,t+32,18,WHITE,true);}line(c,l+18,b-4,r-18,b-4,RED,2.5f);}
    private float introGauge(){return Math.max(0,Math.min(1f,(SystemClock.uptimeMillis()-launchAt-250)/1000f));}
    private void gauge(Canvas c,float l,float t,float r,String label,String value,String unit,float level){RectF q=new RectF(x(l),y(t),x(r),y(t+138));p.setColor(PANEL);c.drawRoundRect(q,x(10),x(10),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(1.5f));p.setColor(LINE);c.drawRoundRect(q,x(10),x(10),p);RectF arc=new RectF(x(l+24),y(t+46),x(r-24),y(t+154));p.setStrokeWidth(x(7));p.setColor(0xff333840);c.drawArc(arc,195,150,false,p);p.setColor(RED);c.drawArc(arc,195,Math.max(8,150*level*introGauge()),false,p);p.setStyle(Paint.Style.FILL);text(c,label,l+18,t+32,14,MUTED,true);text(c,value,l+18,t+92,31,WHITE,true);text(c,unit,r-58,t+92,13,MUTED,true);}

    private void home(Canvas c,float intro){
        hero(c,63,492,intro);text(c,"BUILT TO",735,116,18,WHITE,true);text(c,"DOMINATE",868,116,18,RED,true);
        gauge(c,18,500,258,"BOOST","0","PSI",.08f);gauge(c,274,500,514,"RPM","700","RPM",.13f);gauge(c,530,500,770,"COOLANT","194","°F",.62f);gauge(c,786,500,1062,"TRANS TEMP","178","°F",.55f);
        panel(c,18,652,650,1025,"NAVIGATION");text(c,"Tap to begin navigation",52,744,25,MUTED,false);text(c,"HOME",52,801,43,WHITE,true);text(c,"Route and traffic open in Maps",52,850,18,MUTED,false);arrow(c,548,818);
        panel(c,668,652,1062,1025,"MEDIA");marquee(c,MediaBridge.artist,700,750,1030,18,MUTED,true);marquee(c,MediaBridge.title,700,807,1030,29,WHITE,true);button(c,726,885,1006,976,MediaBridge.playing?"Ⅱ   PAUSE":"▶   OPEN MEDIA",false);
        panel(c,18,1042,1062,1292,"PERFORMANCE");text(c,"0–60",58,1129,17,MUTED,true);text(c,runTime(),58,1192,39,WHITE,true);text(c,"GPS SPEED",295,1129,17,MUTED,true);text(c,Math.round(speedMph)+" MPH",295,1192,39,WHITE,true);text(c,"OBD",585,1129,17,MUTED,true);float pulse=.55f+.45f*(float)Math.sin(SystemClock.uptimeMillis()/330.0);paint(RED,31,true);p.setAlpha((int)(150+105*pulse));c.drawText("DISCONNECTED",x(585),y(1192),p);p.setAlpha(255);
    }
    private void navigation(Canvas c){hero(c,63,236,1);text(c,"NAVIGATION",38,125,36,WHITE,true);text(c,"Plainsboro, NJ  •  "+activity.weatherTemp()+"  •  "+activity.weatherCondition(),38,170,20,MUTED,false);panel(c,18,248,1062,1290,"");p.setColor(0xff111820);c.drawRect(x(34),y(266),x(1046),y(1270),p);for(int i=0;i<9;i++)line(c,40,330+i*104,1040,286+i*110,0xff303d47,4);for(int i=0;i<7;i++)line(c,95+i*148,270,65+i*151,1260,0xff27323a,3);path.reset();path.moveTo(x(470),y(1240));path.cubicTo(x(380),y(1050),x(690),y(800),x(590),y(610));path.cubicTo(x(540),y(510),x(700),y(430),x(760),y(300));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(12));p.setColor(DEEP_RED);c.drawPath(path,p);p.setStrokeWidth(x(5));p.setColor(RED);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);panel(c,50,290,505,490,"NEXT TURN");text(c,"0.8 mi",80,380,43,WHITE,true);text(c,"Turn right onto Scudders Mill Rd",80,432,16,MUTED,false);button(c,744,1125,1007,1218,"OPEN MAPS",true);}
    private void media(Canvas c){hero(c,63,250,1);text(c,"MEDIA",38,137,39,WHITE,true);panel(c,18,270,1062,875,"NOW PLAYING");RectF art=new RectF(x(48),y(334),x(470),y(756));p.setShader(new LinearGradient(art.left,art.top,art.right,art.bottom,0xff5a0710,0xff111318,Shader.TileMode.CLAMP));c.drawRoundRect(art,x(14),x(14),p);p.setShader(null);if(MediaBridge.artwork!=null)c.drawBitmap(MediaBridge.artwork,null,art,p);else text(c,"TRX",152,560,70,0x44ffffff,true);marquee(c,MediaBridge.artist,520,395,1030,18,MUTED,true);marquee(c,MediaBridge.title,520,460,1030,32,WHITE,true);text(c,MediaBridge.hasAccess(activity)?"Android MediaSession connected":"Tap play to enable media access",520,508,17,MUTED,false);button(c,520,630,690,742,"◀",false);button(c,710,610,880,762,MediaBridge.playing?"Ⅱ":"▶",true);button(c,900,630,1030,742,"▶|",false);panel(c,18,895,1062,1290,"MEDIA SOURCES");mediaShelf(c);text(c,"Swipe left or right • Add any installed audio app",46,1218,17,MUTED,false);}
    private void performance(Canvas c){hero(c,63,270,1);text(c,"PERFORMANCE",38,130,36,WHITE,true);gauge(c,18,285,258,"GPS SPEED",String.valueOf(Math.round(speedMph)),"MPH",Math.min(1,speedMph/120f));gauge(c,274,285,514,"RPM","--","RPM",.03f);gauge(c,530,285,770,"COOLANT","--","°F",.03f);gauge(c,786,285,1062,"TRANS TEMP","--","°F",.03f);panel(c,18,442,520,825,"0–60 GPS TIMER");text(c,"0–60",60,538,18,MUTED,true);text(c,runTime(),60,610,49,WHITE,true);text(c,"STATUS",280,538,18,MUTED,true);text(c,runActive?"RUNNING":(runArmed?"ARMED":"READY"),280,610,31,runActive?RED:WHITE,true);button(c,55,685,245,785,"START",true);button(c,270,685,475,785,"RESET",false);panel(c,540,442,1062,825,"ACCELERATION");for(int i=0;i<5;i++)line(c,570,520+i*58,1035,520+i*58,0xff30343a,1);for(int i=0;i<6;i++)line(c,590+i*85,500,590+i*85,790,0xff30343a,1);text(c,"Timer begins automatically above 1 MPH",615,655,18,MUTED,false);panel(c,18,845,1062,1135,"LIVE DATA");gauge(c,38,900,280,"GPS SPEED",String.valueOf(Math.round(speedMph)),"MPH",Math.min(1,speedMph/120f));gauge(c,294,900,536,"ENGINE LOAD","--","%",.03f);gauge(c,550,900,792,"INTAKE TEMP","--","°F",.03f);gauge(c,806,900,1042,"BATTERY","--","V",.03f);panel(c,18,1150,1062,1290,"SESSION HISTORY");text(c,zeroToSixty>0?"Last 0–60: "+String.format(Locale.US,"%.1f seconds",zeroToSixty):"No completed runs",50,1235,20,MUTED,false);}
    private void apps(Canvas c){hero(c,63,240,1);text(c,"APPS",38,132,39,WHITE,true);text(c,"Swipe vertically to browse",38,177,17,MUTED,false);panel(c,18,255,1062,1290,"APP DRAWER");button(c,835,267,1035,318,"⚙ SETTINGS",true);c.save();c.clipRect(x(30),y(325),x(1050),y(1265));for(int i=0;i<apps.size();i++){int col=i%4,row=i/4;float l=46+col*252,t=338+row*190-appScroll;if(t>-5&&t<1260)appTile(c,apps.get(i),l,t,l+218,t+158);}c.restore();if(apps.isEmpty())text(c,"No launchable applications found",60,390,23,MUTED,false);}

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

    private void button(Canvas c,float l,float t,float r,float b,String label,boolean active){RectF q=new RectF(x(l),y(t),x(r),y(b));boolean hit=touchX>=q.left&&touchX<=q.right&&touchY>=q.top&&touchY<=q.bottom;p.setColor(hit?0xff510b16:(active?0xff33070d:0xff121419));c.drawRoundRect(q,x(10),x(10),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(hit?3:1.5f));p.setColor(active||hit?RED:LINE);c.drawRoundRect(q,x(10),x(10),p);p.setStyle(Paint.Style.FILL);paint(WHITE,15,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(label,(q.left+q.right)/2,(q.top+q.bottom)/2+x(6),p);p.setTextAlign(Paint.Align.LEFT);}
    private void arrow(Canvas c,float xx,float yy){p.setColor(RED);path.reset();path.moveTo(x(xx),y(yy));path.lineTo(x(xx+72),y(yy+42));path.lineTo(x(xx),y(yy+84));path.close();c.drawPath(path,p);}
    private void appTile(Canvas c,AppEntry a,float l,float t,float r,float b){RectF q=new RectF(x(l),y(t),x(r),y(b));p.setColor(0xf5111418);c.drawRoundRect(q,x(10),x(10),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(1.5f));p.setColor(LINE);c.drawRoundRect(q,x(10),x(10),p);p.setStyle(Paint.Style.FILL);Drawable d=a.icon;int cx=(int)x((l+r)/2),top=(int)y(t+24),sz=(int)x(72);d.setBounds(cx-sz/2,top,cx+sz/2,top+sz);d.draw(c);paint(WHITE,15,false);p.setTextAlign(Paint.Align.CENTER);String label=a.label.length()>17?a.label.substring(0,16)+"…":a.label;c.drawText(label,cx,y(b-24),p);p.setTextAlign(Paint.Align.LEFT);}
    private void dockIcon(Canvas c,int kind,float cx,float cy,int color){paint(color,17,true);p.setTextAlign(Paint.Align.CENTER);String icon=kind==0?"◆":kind==1?"➤":kind==2?"♫":kind==3?"⌁":"▦";c.drawText(icon,x(cx),y(cy),p);p.setTextAlign(Paint.Align.LEFT);}
    private void dock(Canvas c){float top=1302;for(int i=0;i<5;i++){float l=i*216,r=l+216,cx=(l+r)/2,lift=page==i?0:7;RectF q=new RectF(x(l+7),y(top+5+lift),x(r-7),H-safeBottom-x(5));p.setShader(null);p.setColor(page==i?0xff0b0d11:0xff050608);c.drawRoundRect(q,x(18),x(18),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(page==i?2.5f:1));p.setColor(page==i?RED:0xff54101a);c.drawRoundRect(q,x(18),x(18),p);p.setStyle(Paint.Style.FILL);int red=page==i?RED:0xffa81324;dockIcon(c,i,cx,1345+lift,red);paint(red,14,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(PAGES[i],x(cx),y(1392+lift),p);p.setTextAlign(Paint.Align.LEFT);if(page==i)line(c,l+54,1307,r-54,1307,RED,4);}}
    private void selectPage(int next,int direction){next=Math.max(0,Math.min(4,next));if(next==page)return;page=next;transitionDirection=direction;transitionAt=SystemClock.uptimeMillis();prefs.edit().putInt("page",page).apply();activity.showLiveMap(page==1);if(page==4)loadApps();invalidate();}
    private void loadApps(){try{apps=activity.installedApps();int pages=Math.max(1,(apps.size()+15)/16);appPage=Math.max(0,Math.min(appPage,pages-1));}catch(Throwable ignored){apps=new ArrayList<>();appPage=0;}}
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();scrollAtDown=appScroll;mediaScrollAtDown=mediaScroll;touchX=downX;touchY=downY;invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){touchX=e.getX();touchY=e.getY();if(page==2&&e.getY()>y(930)&&e.getY()<y(1200)){float max=Math.max(0,(mediaApps.size()+1)*198-1000);mediaScroll=Math.max(0,Math.min(max,mediaScrollAtDown+(downX-e.getX())/Math.max(.01f,u)));}if(page==4){float rows=(apps.size()+3)/4f,max=Math.max(0,338+rows*190-1250);appScroll=Math.max(0,Math.min(max,scrollAtDown+(downY-e.getY())/Math.max(.01f,usableH)*1440f));}invalidate();return true;}if(e.getAction()!=MotionEvent.ACTION_UP&&e.getAction()!=MotionEvent.ACTION_CANCEL)return true;float upX=e.getX(),upY=e.getY(),dx=upX-downX;touchX=touchY=-1;if(Math.abs(dx)>x(120)&&Math.abs(dx)>Math.abs(upY-downY)&&page!=4){selectPage(page+(dx<0?1:-1),dx<0?1:-1);return true;}float xx=upX/u,yy=(upY-safeTop)*1440f/usableH;if(yy>=1300){selectPage((int)(xx/216),xx/216>page?1:-1);return true;}if(page==0&&xx<650&&yy>652&&yy<1025){activity.openNavigation();return true;}if(page==0&&xx>668&&yy>652&&yy<1025){if(MediaBridge.hasAccess(activity))MediaBridge.toggle(activity);else activity.openMedia();return true;}if(page==1&&xx>700&&yy>1080){activity.openNavigation();return true;}if(page==2&&yy>600&&yy<790){if(xx<700)MediaBridge.previous(activity);else if(xx<895)MediaBridge.toggle(activity);else MediaBridge.next(activity);return true;}if(page==2&&yy>930&&yy<1190){if(Math.abs(upX-downX)>x(24)){invalidate();return true;}int item=(int)((xx-42+mediaScroll)/198);if(item>=0&&item<mediaApps.size())activity.launch(mediaApps.get(item));else if(item==mediaApps.size())activity.openMediaAppPicker();return true;}if(page==3&&yy>665&&yy<810){if(xx<258)armRun();else if(xx<520)resetRun();return true;}if(page==4){if(yy>250&&yy<330&&xx>800){activity.openSettingsScreen();return true;}if(Math.abs(upY-downY)>x(24)){invalidate();return true;}int col=(int)((xx-46)/252),row=(int)((yy-338+appScroll)/190);if(col>=0&&col<4&&row>=0){int i=row*4+col;if(i<apps.size())activity.launch(apps.get(i));}}invalidate();return true;}
}
