package com.mdiaz.trxlauncher;

import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.util.*;

final class AppsScreen extends ScrollView {
    private final MainActivity a;
    private final android.content.SharedPreferences prefs;

    AppsScreen(MainActivity c) {
        super(c);
        a=c;
        prefs=a.getSharedPreferences("trx",0);
        setFillViewport(true);
        show(0);
    }

    private void show(int tab) {
        removeAllViews();
        LinearLayout root=Ui.col(a);
        root.setPadding(24,18,24,22);
        addView(root);
        root.addView(Ui.text(a,"TRX LAUNCHER APPS",11,Ui.RED,true));
        root.addView(Ui.text(a,"Every app, without the clutter.",32,Color.WHITE,true));
        root.addView(Ui.tabs(a,new String[]{"All","Driving","Media","Tools"},tab,this::show),new LinearLayout.LayoutParams(-1,76));

        LinearLayout quick=Ui.row(a);
        String saved=prefs.getString("quick_launch","");
        for(String pkg:saved.split("\n")) if(!pkg.isBlank()) {
            Intent launch=a.getPackageManager().getLaunchIntentForPackage(pkg);
            try {
                ApplicationInfo ai=a.getPackageManager().getApplicationInfo(pkg,0);
                Button q=Ui.button(a,ai.loadLabel(a.getPackageManager()).toString(),true);
                q.setOnClickListener(v->{if(launch!=null)a.startActivity(launch);});
                quick.addView(q,new LinearLayout.LayoutParams(0,58,1));
            } catch(Exception ignored) {}
        }
        if(quick.getChildCount()>0) root.addView(quick,new LinearLayout.LayoutParams(-1,58));

        EditText search=new EditText(a);
        search.setHint("Search all apps");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xff999da4);
        search.setBackground(Ui.bg(Ui.CARD,0xff3b3e44,18,a));
        search.setPadding(24,0,24,0);
        root.addView(search,new LinearLayout.LayoutParams(-1,64));

        GridLayout grid=new GridLayout(a);
        grid.setColumnCount(4);
        List<ResolveInfo> apps=a.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        apps.sort(Comparator.comparing(x->x.loadLabel(a.getPackageManager()).toString().toLowerCase(Locale.US)));
        for(ResolveInfo info:apps) {
            String label=info.loadLabel(a.getPackageManager()).toString();
            String pkg=info.activityInfo.packageName;
            if(!inCategory(tab,label,pkg)) continue;
            Button b=Ui.button(a,label,false);
            b.setOnClickListener(v->{Intent i=a.getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null)a.startActivity(i);});
            b.setOnLongClickListener(v->{toggleQuick(pkg);return true;});
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
            lp.width=0;lp.height=Ui.dp(a,110);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(6,6,6,6);
            grid.addView(b,lp);
        }
        root.addView(grid);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){for(int i=0;i<grid.getChildCount();i++){Button v=(Button)grid.getChildAt(i);v.setVisibility(v.getText().toString().toLowerCase(Locale.US).contains(s.toString().toLowerCase(Locale.US))?View.VISIBLE:View.GONE);}}
            public void afterTextChanged(android.text.Editable e){}
        });
    }

    private boolean inCategory(int tab,String label,String pkg) {
        if(tab==0)return true;
        String s=(label+" "+pkg).toLowerCase(Locale.US);
        if(tab==1)return contains(s,"map","waze","fuel","parking","radar","drive","vehicle","car");
        if(tab==2)return contains(s,"music","spotify","youtube","media","radio","podcast","audio");
        return !inCategory(1,label,pkg)&&!inCategory(2,label,pkg);
    }
    private boolean contains(String text,String... words){for(String w:words)if(text.contains(w))return true;return false;}
    private void toggleQuick(String pkg) {
        LinkedHashSet<String> set=new LinkedHashSet<>(Arrays.asList(prefs.getString("quick_launch","").split("\n")));
        set.remove("");
        boolean added;
        if(set.contains(pkg)){set.remove(pkg);added=false;}else{if(set.size()>=4)set.remove(set.iterator().next());set.add(pkg);added=true;}
        prefs.edit().putString("quick_launch",String.join("\n",set)).apply();
        Toast.makeText(a,added?"Added to Quick Launch":"Removed from Quick Launch",Toast.LENGTH_SHORT).show();
        show(0);
    }
}
