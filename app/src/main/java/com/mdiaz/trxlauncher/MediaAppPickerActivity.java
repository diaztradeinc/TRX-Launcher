package com.mdiaz.trxlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MediaAppPickerActivity extends Activity {
    private final List<CheckBox> checks=new ArrayList<>();
    private final List<String> packages=new ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff050608);
        getWindow().setNavigationBarColor(0xff050608);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(22),dp(24),dp(24));
        root.setBackgroundColor(0xff050608);

        TextView title=new TextView(this);
        title.setText("CHOOSE MEDIA APPS");
        title.setTextColor(Color.WHITE);title.setTextSize(25);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView help=new TextView(this);
        help.setText("Selected apps appear in the launcher’s swipeable media shelf.");
        help.setTextColor(0xffaeb2ba);help.setTextSize(14);
        help.setPadding(0,dp(5),0,dp(14));root.addView(help);

        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,0,1));

        Set<String> selected=readSelected();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> rows=getPackageManager().queryIntentActivities(query,0);
        Collections.sort(rows,(a,b)->String.valueOf(a.loadLabel(getPackageManager()))
            .compareToIgnoreCase(String.valueOf(b.loadLabel(getPackageManager()))));
        for(ResolveInfo row:rows){
            if(row.activityInfo==null||getPackageName().equals(row.activityInfo.packageName))continue;
            String pkg=row.activityInfo.packageName;
            CheckBox box=new CheckBox(this);
            box.setText(String.valueOf(row.loadLabel(getPackageManager())));
            box.setTextColor(Color.WHITE);box.setTextSize(17);
            box.setGravity(Gravity.CENTER_VERTICAL);box.setButtonTintList(
                new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                    new int[]{0xffff2338,0xff666b73}));
            try{box.setCompoundDrawablesWithIntrinsicBounds(row.loadIcon(getPackageManager()),null,null,null);
                box.setCompoundDrawablePadding(dp(14));}catch(Throwable ignored){}
            box.setChecked(selected.contains(pkg));
            list.addView(box,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(62)));
            checks.add(box);packages.add(pkg);
        }

        Button save=new Button(this);save.setText("SAVE MEDIA APPS");
        save.setTextColor(Color.WHITE);save.setTextSize(16);save.setAllCaps(false);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xff6e0713);bg.setCornerRadius(dp(12));bg.setStroke(dp(2),0xffff2338);
        save.setBackground(bg);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(60));bp.topMargin=dp(14);root.addView(save,bp);
        save.setOnClickListener(v->save());
        setContentView(root);
    }

    private Set<String> readSelected(){
        String raw=getSharedPreferences("launcher",MODE_PRIVATE).getString("media_apps","");
        Set<String> result=new HashSet<>();
        if(!raw.isEmpty())for(String value:raw.split(","))if(!value.trim().isEmpty())result.add(value.trim());
        return result;
    }

    private void save(){
        StringBuilder value=new StringBuilder();
        for(int i=0;i<checks.size();i++)if(checks.get(i).isChecked()){
            if(value.length()>0)value.append(',');
            value.append(packages.get(i));
        }
        getSharedPreferences("launcher",MODE_PRIVATE).edit()
            .putString("media_apps",value.toString()).apply();
        setResult(RESULT_OK);finish();
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
