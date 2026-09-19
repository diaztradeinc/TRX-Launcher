package com.mdiaz.trxlauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

final class Ui {
    static final int BG=0xff050506, CARD=0xff111214, CARD2=0xff17191c, RED=0xffe1192d;
    static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable bg(int color,int stroke,int radius,Context c){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));
        if(stroke!=0)g.setStroke(dp(c,1),stroke);return g;
    }
    static TextView text(Context c,String s,float size,int color,boolean bold){
        TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    static Button button(Context c,String s,boolean selected){
        Button b=new Button(c);b.setText(s);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(bg(selected?RED:CARD2,selected?0:0xff3d4148,14,c));return b;
    }
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static LinearLayout col(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout tabs(Context c,String[] names,int active,java.util.function.IntConsumer onSelect){
        LinearLayout bar=row(c);bar.setPadding(dp(c,6),dp(c,6),dp(c,6),dp(c,6));bar.setBackground(bg(CARD,0xff383b40,18,c));
        for(int i=0;i<names.length;i++){final int index=i;Button b=button(c,names[i],i==active);bar.addView(b,new LinearLayout.LayoutParams(0,dp(c,58),1));b.setOnClickListener(v->onSelect.accept(index));}
        return bar;
    }
    static LinearLayout metric(Context c,String label,String value,String unit){
        LinearLayout box=col(c);box.setPadding(dp(c,18),dp(c,14),dp(c,18),dp(c,14));box.setBackground(bg(CARD,0xff383b40,18,c));
        box.addView(text(c,label,11,0xff9ea2a9,true));LinearLayout line=row(c);line.addView(text(c,value,30,Color.WHITE,false));line.addView(text(c,"  "+unit,12,0xffaaaeb5,true));box.addView(line);return box;
    }
    static void margins(View v,int l,int t,int r,int b){if(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams m)m.setMargins(dp(v.getContext(),l),dp(v.getContext(),t),dp(v.getContext(),r),dp(v.getContext(),b));}
    private Ui(){}
}
