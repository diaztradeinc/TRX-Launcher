package com.mdiaz.trxlauncher;
import android.content.Context;
final class ThemeStore{
 static final int[] COLORS={0xffe1192d,0xffffa928,0xff1976d2,0xffb7bcc5};
 static final String[] NAMES={"TRX Red","Baja Amber","Electric Blue","Stealth Silver"};
 static void apply(Context c){int i=c.getSharedPreferences("trx",0).getInt("theme",0);Ui.RED=COLORS[Math.max(0,Math.min(COLORS.length-1,i))];}
 static void set(Context c,int i){c.getSharedPreferences("trx",0).edit().putInt("theme",i).apply();apply(c);}
 private ThemeStore(){}
}
