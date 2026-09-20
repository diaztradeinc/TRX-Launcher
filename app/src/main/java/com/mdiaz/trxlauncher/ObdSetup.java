package com.mdiaz.trxlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import java.util.ArrayList;

/** Selection uses already paired devices; pairing remains in Android Bluetooth settings. */
final class ObdSetup {
    static final int PERMISSION_REQUEST=82;
    static void show(Activity activity){
        if(Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},PERMISSION_REQUEST);return;
        }
        android.content.SharedPreferences prefs=activity.getSharedPreferences("launcher",Activity.MODE_PRIVATE);
        ArrayList<String> names=new ArrayList<>(),addresses=new ArrayList<>();
        names.add("Automatic • OBDLink MX+");addresses.add("");
        int selected=0;
        try{
            BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
            if(adapter!=null&&adapter.isEnabled())for(BluetoothDevice device:adapter.getBondedDevices()){
                String name=device.getName();names.add((name==null?"Paired adapter":name)+" • "+device.getAddress());addresses.add(device.getAddress());
                if(device.getAddress().equals(prefs.getString("obd_address","")))selected=addresses.size()-1;
            }
        }catch(SecurityException denied){return;}
        new AlertDialog.Builder(activity).setTitle("OBDLink MX+ setup")
            .setSingleChoiceItems(names.toArray(new String[0]),selected,(dialog,index)->{
                prefs.edit().putString("obd_address",addresses.get(index)).putBoolean("obd_enabled",true).apply();
                ObdBridge.reconnect(activity);dialog.dismiss();showStatus(activity);
            })
            .setPositiveButton("Pair in Bluetooth",(dialog,index)->activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
            .setNeutralButton("Connection status",(dialog,index)->showStatus(activity))
            .setNegativeButton("Close",null).show();
    }
    static void showStatus(Activity activity){
        android.content.SharedPreferences prefs=activity.getSharedPreferences("launcher",Activity.MODE_PRIVATE);
        new AlertDialog.Builder(activity).setTitle("OBD connection")
            .setMessage(ObdBridge.status+"\n\nPlug the MX+ into the truck, switch the ignition on, and pair it with this Android device. Close other OBD apps before connecting.\n\nRPM, coolant, intake, load, voltage, speed and calculated boost use supported live PIDs. Transmission temperature requires a verified RAM-specific PID.")
            .setPositiveButton("Reconnect",(d,i)->{prefs.edit().putBoolean("obd_enabled",true).apply();ObdBridge.reconnect(activity);})
            .setNeutralButton("Disconnect",(d,i)->{prefs.edit().putBoolean("obd_enabled",false).apply();ObdBridge.reconnect(activity);})
            .setNegativeButton("Close",null).show();
    }
}
