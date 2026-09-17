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
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DashboardView extends View {
    private static final int RED=0xffff1d32, WHITE=0xfff4f5f6, MUTED=0xffa8acb2;
    private static final String[] PAGES={"Home","Navigation","Media","Performance","Apps"};
    private final MainActivity activity;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final Handler clock=new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final Bitmap hero;
    private List<AppEntry> apps;
    private int page;
    private float W,H,u;
    private final Runnable ticker=new Runnable(){ public void run(){ invalidate(); clock.postDelayed(this,1000); }};

    public DashboardView(MainActivity context) {
        super(context); activity=context; setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        prefs=context.getSharedPreferences("launcher",Context.MODE_PRIVATE);
        page=prefs.getInt("page",0);
        Drawable heroDrawable=context.getDrawable(R.drawable.trx_hero);
        hero=Bitmap.createBitmap(1080,1440,Bitmap.Config.ARGB_8888);
        Canvas heroCanvas=new Canvas(hero);
        if(heroDrawable!=null){heroDrawable.setBounds(0,0,hero.getWidth(),hero.getHeight());heroDrawable.draw(heroCanvas);}
        apps=activity.installedApps();
        clock.post(ticker);
    }

    @Override protected void onDetachedFromWindow(){ clock.removeCallbacks(ticker); super.onDetachedFromWindow(); }
    @Override protected void onDraw(Canvas c){
        W=getWidth();H=getHeight();u=W/1080f;
        c.drawColor(0xff050607);
        status(c);
        switch(page){case 0:home(c);break;case 1:navigation(c);break;case 2:media(c);break;case 3:performance(c);break;default:apps(c);}
        dock(c);
    }

    private float x(float n){return n*u;} private float y(float n){return n*H/1440f;}
    private void paint(int color,float size,boolean bold){p.setColor(color);p.setTextSize(x(size));p.setTypeface(bold?Typeface.create("sans",Typeface.BOLD):Typeface.create("sans",Typeface.NORMAL));p.setStyle(Paint.Style.FILL);p.setShader(null);}
    private void text(Canvas c,String s,float xx,float yy,float size,int color,boolean bold){paint(color,size,bold);c.drawText(s,x(xx),y(yy),p);}
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){p.setColor(color);p.setStrokeWidth(x(width));p.setStyle(Paint.Style.STROKE);c.drawLine(x(x1),y(y1),x(x2),y(y2),p);p.setStyle(Paint.Style.FILL);}
    private void panel(Canvas c,float l,float t,float r,float b,String title){
        RectF q=new RectF(x(l),y(t),x(r),y(b));p.setStyle(Paint.Style.FILL);p.setColor(0xee090b0e);c.drawRoundRect(q,x(10),x(10),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(2));p.setColor(0xff596068);c.drawRoundRect(q,x(10),x(10),p);p.setStyle(Paint.Style.FILL);
        line(c,l+16,t+48,r-16,t+48,0xff373b40,1);line(c,l+18,b-4,r-18,b-4,RED,3);
        text(c,title,l+18,t+34,20,WHITE,true);
    }
    private void status(Canvas c){
        RectF r=new RectF(0,0,W,y(64));p.setShader(new LinearGradient(0,0,W,0,0xff080a0c,0xff121418,Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);
        text(c,"RAM",34,42,28,WHITE,true);text(c,"TRX LAUNCHER",132,40,18,RED,true);
        SimpleDateFormat tf=new SimpleDateFormat("h:mm a",Locale.US);String now=tf.format(new Date());
        p.setTextAlign(Paint.Align.RIGHT);text(c,now,1042,41,24,WHITE,true);p.setTextAlign(Paint.Align.LEFT);
        line(c,0,62,1080,62,RED,2);
    }
    private void hero(Canvas c,float top,float bottom){
        Rect src=new Rect(0,0,hero.getWidth(),Math.min(hero.getHeight(),(int)(hero.getHeight()*.46f)));
        RectF dst=new RectF(0,y(top),W,y(bottom));c.drawBitmap(hero,src,dst,p);
        p.setShader(new LinearGradient(0,y(top),0,y(bottom),0x00000000,0xe8050608,Shader.TileMode.CLAMP));c.drawRect(dst,p);p.setShader(null);
    }
    private void home(Canvas c){
        hero(c,64,500);text(c,"BUILT TO DOMINATE",610,130,23,WHITE,true);
        metric(c,18,510,258,"BOOST","--","PSI");metric(c,274,510,514,"RPM","--","");metric(c,530,510,770,"COOLANT","--","°F");metric(c,786,510,1062,"TRANS TEMP","--","°F");
        panel(c,18,655,650,1022,"NAVIGATION"); text(c,"Tap to begin navigation",54,745,27,MUTED,false); text(c,"HOME",54,800,46,WHITE,true); text(c,"Route and traffic open in Maps",54,850,20,MUTED,false); arrow(c,550,820);
        panel(c,668,655,1062,1022,"MEDIA");text(c,"NO MEDIA PLAYING",704,752,22,MUTED,true);text(c,"Select Media",704,810,34,WHITE,true);button(c,744,890,986,978,"OPEN MEDIA",false);
        panel(c,18,1040,1062,1290,"PERFORMANCE");text(c,"0–60",60,1128,18,MUTED,true);text(c,"--.- s",60,1192,42,WHITE,true);text(c,"1/4 MILE",300,1128,18,MUTED,true);text(c,"--.- s",300,1192,42,WHITE,true);text(c,"OBD",590,1128,18,MUTED,true);text(c,"DISCONNECTED",590,1192,34,RED,true);
    }
    private void navigation(Canvas c){
        hero(c,64,214);text(c,"NAVIGATION",38,125,38,WHITE,true);text(c,"Plainsboro, NJ",38,170,22,MUTED,false);
        panel(c,18,228,1062,1288,"");p.setColor(0xff121a20);c.drawRect(x(34),y(248),x(1046),y(1268),p);
        for(int i=0;i<9;i++)line(c,40,320+i*105,1040,280+i*110,0xff34424b,5);
        for(int i=0;i<7;i++)line(c,100+i*145,250,70+i*150,1260,0xff2a353d,4);
        path.reset();path.moveTo(x(470),y(1240));path.cubicTo(x(380),y(1050),x(690),y(800),x(590),y(610));path.cubicTo(x(540),y(510),x(700),y(430),x(760),y(300));
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(14));p.setColor(0xff4a0710);c.drawPath(path,p);p.setStrokeWidth(x(6));p.setColor(RED);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        panel(c,52,278,498,480,"NEXT TURN");text(c,"0.8 mi",84,365,46,WHITE,true);text(c,"Turn right onto Scudders Mill Rd",84,420,18,MUTED,false);button(c,748,1120,1005,1210,"OPEN MAPS",true);
    }
    private void media(Canvas c){
        hero(c,64,242);text(c,"MEDIA",38,135,42,WHITE,true);panel(c,18,260,1062,870,"NOW PLAYING");
        RectF art=new RectF(x(48),y(330),x(470),y(752));p.setShader(new LinearGradient(art.left,art.top,art.right,art.bottom,0xffff7a32,0xff32131a,Shader.TileMode.CLAMP));c.drawRoundRect(art,x(10),x(10),p);p.setShader(null);
        text(c,"NO TRACK SELECTED",520,392,21,MUTED,true);text(c,"Choose a media app",520,460,38,WHITE,true);text(c,"Metadata and controls will appear here",520,510,18,MUTED,false);
        button(c,520,630,690,740,"◀",false);button(c,710,610,880,760,"▶",true);button(c,900,630,1030,740,"▶|",false);
        panel(c,18,888,1062,1288,"MEDIA SOURCES");String[] s={"Spotify","YouTube Music","Apple Music","Bluetooth","Local"};for(int i=0;i<5;i++)button(c,42+i*201,960,222+i*201,1095,s[i],i==0);
        text(c,"Playback uses Android MediaSession controls",46,1190,21,MUTED,false);
    }
    private void performance(Canvas c){
        hero(c,64,276);text(c,"PERFORMANCE",38,128,38,WHITE,true);
        metric(c,18,290,258,"BOOST","--","PSI");metric(c,274,290,514,"RPM","--","");metric(c,530,290,770,"COOLANT","--","°F");metric(c,786,290,1062,"TRANS TEMP","--","°F");
        panel(c,18,438,520,824,"0–60 / 1/4 MILE");text(c,"0–60",60,535,20,MUTED,true);text(c,"--.- s",60,610,54,WHITE,true);text(c,"1/4 MILE",280,535,20,MUTED,true);text(c,"--.- s",280,610,54,WHITE,true);button(c,55,680,245,785,"START",true);button(c,270,680,475,785,"RESET",false);
        panel(c,540,438,1062,824,"ACCELERATION");for(int i=0;i<5;i++)line(c,570,520+i*58,1035,520+i*58,0xff33383e,1);for(int i=0;i<6;i++)line(c,590+i*85,500,590+i*85,790,0xff33383e,1);text(c,"Waiting for GPS/OBD data",664,655,23,MUTED,false);
        panel(c,18,842,1062,1130,"LIVE DATA");metric(c,38,900,280,"THROTTLE","--","%");metric(c,294,900,536,"ENGINE LOAD","--","%");metric(c,550,900,792,"INTAKE TEMP","--","°F");metric(c,806,900,1042,"BATTERY","--","V");
        panel(c,18,1148,1062,1288,"SESSION HISTORY");text(c,"No runs recorded",50,1230,22,MUTED,false);
    }
    private void apps(Canvas c){
        hero(c,64,238);text(c,"APPS",38,132,42,WHITE,true);panel(c,18,250,1062,1288,"ALL APPS");
        int max=Math.min(apps.size(),16);for(int i=0;i<max;i++){int col=i%4,row=i/4;float l=46+col*252,t=330+row*220;appTile(c,apps.get(i),l,t,l+218,t+186);}
        if(max==0)text(c,"No launchable apps found",60,390,26,MUTED,false);
    }
    private void metric(Canvas c,float l,float t,float r,String label,String value,String unit){
        RectF q=new RectF(x(l),y(t),x(r),y(t+126));p.setColor(0xee090b0e);c.drawRoundRect(q,x(8),x(8),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(2));p.setColor(0xff4d535a);c.drawRoundRect(q,x(8),x(8),p);p.setStyle(Paint.Style.FILL);line(c,l+12,t+120,r-12,t+120,RED,3);text(c,label,l+18,t+36,15,MUTED,true);text(c,value,l+18,t+88,34,WHITE,true);text(c,unit,r-58,t+88,14,MUTED,true);
    }
    private void button(Canvas c,float l,float t,float r,float b,String label,boolean active){RectF q=new RectF(x(l),y(t),x(r),y(b));p.setColor(active?0xff31070e:0xff111317);c.drawRoundRect(q,x(9),x(9),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(2));p.setColor(active?RED:0xff555b61);c.drawRoundRect(q,x(9),x(9),p);p.setStyle(Paint.Style.FILL);paint(WHITE,16,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(label,(q.left+q.right)/2,(q.top+q.bottom)/2-x(-6),p);p.setTextAlign(Paint.Align.LEFT);}
    private void arrow(Canvas c,float xx,float yy){p.setColor(RED);path.reset();path.moveTo(x(xx),y(yy));path.lineTo(x(xx+74),y(yy+42));path.lineTo(x(xx),y(yy+84));path.close();c.drawPath(path,p);}
    private void appTile(Canvas c,AppEntry a,float l,float t,float r,float b){RectF q=new RectF(x(l),y(t),x(r),y(b));p.setColor(0xff111318);c.drawRoundRect(q,x(8),x(8),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(2));p.setColor(0xff555b61);c.drawRoundRect(q,x(8),x(8),p);p.setStyle(Paint.Style.FILL);Drawable d=a.icon;int cx=(int)x((l+r)/2),top=(int)y(t+24),sz=(int)x(72);d.setBounds(cx-sz/2,top,cx+sz/2,top+sz);d.draw(c);paint(WHITE,16,false);p.setTextAlign(Paint.Align.CENTER);String label=a.label.length()>17?a.label.substring(0,16)+"…":a.label;c.drawText(label,cx,y(b-24),p);p.setTextAlign(Paint.Align.LEFT);}
    private void dock(Canvas c){float top=1302;for(int i=0;i<5;i++){float l=i*216,r=l+216;RectF q=new RectF(x(l+2),y(top),x(r-2),H);p.setColor(page==i?0xff30070e:0xff090b0e);c.drawRect(q,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(x(2));p.setColor(page==i?RED:0xff444a50);c.drawRect(q,p);p.setStyle(Paint.Style.FILL);paint(page==i?WHITE:MUTED,16,true);p.setTextAlign(Paint.Align.CENTER);c.drawText(PAGES[i],x((l+r)/2),y(1384),p);p.setTextAlign(Paint.Align.LEFT);if(page==i)line(c,l+25,1305,r-25,1305,RED,4);}}

    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float xx=e.getX()/u,yy=e.getY()*1440f/H;
        if(yy>=1300){page=Math.max(0,Math.min(4,(int)(xx/216)));prefs.edit().putInt("page",page).apply();if(page==4)apps=activity.installedApps();invalidate();return true;}
        if(page==0 && xx<650 && yy>655 && yy<1022){activity.openNavigation();return true;}
        if(page==1 && xx>700 && yy>1080){activity.openNavigation();return true;}
        if(page==4){int col=(int)((xx-46)/252),row=(int)((yy-330)/220);if(col>=0&&col<4&&row>=0&&row<4){int i=row*4+col;if(i<apps.size())activity.launch(apps.get(i));}}
        return true;
    }
}
