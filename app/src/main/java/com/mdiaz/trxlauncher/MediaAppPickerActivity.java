package com.mdiaz.trxlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MediaAppPickerActivity extends Activity {
    private static final int RED=0xffff2338, WHITE=0xfff5f5f7, MUTED=0xffaeb2ba;
    private final List<String> packages=new ArrayList<>();
    private final List<Boolean> choices=new ArrayList<>();
    private final List<LinearLayout> cards=new ArrayList<>();
    private final List<TextView> badges=new ArrayList<>();
    private TextView selectedLabel;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff050608);getWindow().setNavigationBarColor(0xff050608);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(14),dp(18),dp(16));root.setBackgroundColor(0xff050608);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headings=new LinearLayout(this);headings.setOrientation(LinearLayout.VERTICAL);
        TextView title=label("MEDIA SOURCES",26,WHITE,true);headings.addView(title);
        TextView help=label("Choose the apps that appear on your Command Center media shelf.",13,MUTED,false);
        headings.addView(help);
        header.addView(headings,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        selectedLabel=label("",12,RED,true);selectedLabel.setGravity(Gravity.CENTER);
        selectedLabel.setBackground(shape(0xff0b0e12,0xff6e1721,18));
        header.addView(selectedLabel,new LinearLayout.LayoutParams(dp(125),dp(42)));
        root.addView(header);

        TextView hint=label("TRX MEDIA GRID  •  TAP TO SELECT",11,MUTED,true);
        hint.setPadding(dp(2),dp(14),0,dp(8));root.addView(hint);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        GridLayout grid=new GridLayout(this);int columns=getResources().getConfiguration().screenWidthDp>=800?5:4;
        grid.setColumnCount(columns);grid.setPadding(0,0,0,dp(12));scroll.addView(grid);
        root.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));

        Set<String> selected=readSelected();PackageManager pm=getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> rows=pm.queryIntentActivities(query,0);
        Collections.sort(rows,(a,b)->String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        int screenDp=getResources().getConfiguration().screenWidthDp-36;
        int cardWidth=Math.max(118,(screenDp-(columns-1)*10)/columns);
        for(ResolveInfo row:rows){
            if(row.activityInfo==null||getPackageName().equals(row.activityInfo.packageName))continue;
            final int index=packages.size();String pkg=row.activityInfo.packageName;
            packages.add(pkg);choices.add(selected.contains(pkg));

            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);card.setPadding(dp(8),dp(10),dp(8),dp(8));card.setClickable(true);
            ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try{icon.setImageDrawable(row.loadIcon(pm));}catch(Throwable ignored){}
            card.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));

            TextView name=label(String.valueOf(row.loadLabel(pm)),12,WHITE,true);name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(40));np.topMargin=dp(6);card.addView(name,np);

            TextView badge=label("",10,RED,true);badge.setGravity(Gravity.CENTER);
            card.addView(badge,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(22)));
            cards.add(card);badges.add(badge);paintCard(index);
            card.setOnClickListener(v->{choices.set(index,!choices.get(index));paintCard(index);updateCount();});

            GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=dp(cardWidth);gp.height=dp(150);
            gp.setMargins(dp(4),dp(4),dp(4),dp(4));grid.addView(card,gp);
        }
        updateCount();

        TextView save=label("SAVE MEDIA SOURCES",16,WHITE,true);save.setGravity(Gravity.CENTER);
        save.setBackground(shape(0xff3b0d14,RED,14));save.setClickable(true);save.setOnClickListener(v->save());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(64));sp.topMargin=dp(10);root.addView(save,sp);
        setContentView(root);
    }

    private void paintCard(int index){
        boolean selected=choices.get(index);cards.get(index).setBackground(shape(selected?0xff171a1f:0xff0b0e12,selected?RED:0xff454b54,14));
        badges.get(index).setText(selected?"✓  ON MEDIA SHELF":"TAP TO ADD");
        badges.get(index).setTextColor(selected?RED:MUTED);
    }
    private void updateCount(){int count=0;for(boolean value:choices)if(value)count++;selectedLabel.setText(count+" SELECTED");}
    private TextView label(String value,float size,int color,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return view;}
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(fill);bg.setCornerRadius(dp(radius));bg.setStroke(dp(1),stroke);return bg;}
    private Set<String> readSelected(){String raw=getSharedPreferences("launcher",MODE_PRIVATE).getString("media_apps","");Set<String> result=new HashSet<>();if(!raw.isEmpty())for(String value:raw.split(","))if(!value.trim().isEmpty())result.add(value.trim());return result;}
    private void save(){StringBuilder value=new StringBuilder();for(int i=0;i<choices.size();i++)if(choices.get(i)){if(value.length()>0)value.append(',');value.append(packages.get(i));}getSharedPreferences("launcher",MODE_PRIVATE).edit().putString("media_apps",value.toString()).apply();setResult(RESULT_OK);finish();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}