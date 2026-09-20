package com.mdiaz.trxlauncher;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only OBD-II bridge for a paired OBDLink MX+.
 * It only sends adapter setup commands and SAE Mode 01 live-data queries.
 */
public final class ObdBridge {
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static volatile boolean running;
    private static volatile Thread worker;
    private static volatile BluetoothSocket socket;
    private static volatile InputStream input;
    private static volatile OutputStream output;

    public static volatile boolean connected;
    public static volatile String status="PAIR OBDLINK MX+";
    public static volatile String deviceName="OBDLink MX+";
    public static volatile long lastUpdate;

    public static volatile float rpm=Float.NaN;
    public static volatile float coolantF=Float.NaN;
    public static volatile float intakeF=Float.NaN;
    public static volatile float engineLoad=Float.NaN;
    public static volatile float batteryV=Float.NaN;
    public static volatile float obdSpeedMph=Float.NaN;
    public static volatile float boostPsi=Float.NaN;
    public static volatile float transmissionF=Float.NaN;

    private ObdBridge(){}

    public static void reconnect(Context context){closeSocket();start(context);}

    public static synchronized void start(Context context){
        if(running)return;
        final Context app=context.getApplicationContext();
        running=true;
        worker=new Thread(()->runLoop(app),"trx-obdlink");
        worker.start();
    }

    public static synchronized void stop(){
        running=false;
        closeSocket();
        Thread active=worker;
        if(active!=null)active.interrupt();
        worker=null;
        connected=false;
        status="OBD DISCONNECTED";
    }

    private static void runLoop(Context context){
        while(running){
            try{
                if(!context.getSharedPreferences("launcher",Context.MODE_PRIVATE).getBoolean("obd_enabled",true)){status="OBD DISCONNECTED";sleep(1000);continue;}
                if(Build.VERSION.SDK_INT>=31&&context.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
                    connected=false;status="BLUETOOTH PERMISSION REQUIRED";sleep(4000);continue;
                }
                BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
                if(adapter==null){status="BLUETOOTH UNAVAILABLE";sleep(5000);continue;}
                if(!adapter.isEnabled()){status="TURN ON BLUETOOTH";sleep(3500);continue;}

                BluetoothDevice target=null;
                String selected=context.getSharedPreferences("launcher",Context.MODE_PRIVATE).getString("obd_address","");
                if(selected.isEmpty())target=findMx(adapter.getBondedDevices());
                else for(BluetoothDevice device:adapter.getBondedDevices())if(selected.equals(device.getAddress())){target=device;break;}
                if(target==null){status="PAIR OBDLINK MX+";sleep(4000);continue;}
                deviceName=safeName(target);
                status="CONNECTING "+deviceName.toUpperCase(Locale.US)+"…";
                // Only connect to a paired adapter; do not require scan permission.
                BluetoothSocket next=target.createRfcommSocketToServiceRecord(SPP);
                socket=next;
                next.connect();
                input=next.getInputStream();
                output=next.getOutputStream();

                initializeAdapter();
                connected=true;
                status="OBD LIVE • "+deviceName.toUpperCase(Locale.US);
                while(running&&next.isConnected()){
                    pollStandardPids();
                    lastUpdate=SystemClock.elapsedRealtime();
                    sleep(90);
                }
            }catch(SecurityException denied){
                status="BLUETOOTH PERMISSION REQUIRED";
            }catch(Throwable error){
                status="OBD RECONNECTING…";
            }finally{
                connected=false;
                rpm=coolantF=intakeF=engineLoad=batteryV=obdSpeedMph=boostPsi=transmissionF=Float.NaN;
                closeSocket();
            }
            if(running)sleep(3000);
        }
    }

    private static BluetoothDevice findMx(Set<BluetoothDevice> bonded){
        if(bonded==null)return null;
        BluetoothDevice fallback=null;
        for(BluetoothDevice device:bonded){
            String name=safeName(device).toUpperCase(Locale.US);
            if(name.contains("OBDLINK")&&(name.contains("MX")||name.contains("STN")))return device;
            if(name.contains("MX+"))return device;
        }
        return null;
    }

    private static String safeName(BluetoothDevice device){
        try{
            String name=device.getName();
            return name==null||name.trim().isEmpty()?"OBDLink MX+":name.trim();
        }catch(SecurityException denied){return "OBDLink MX+";}
    }

    private static void initializeAdapter() throws Exception{
        command("ATZ",2600);
        command("ATE0",1000);
        command("ATL0",1000);
        command("ATS0",1000);
        command("ATH0",1000);
        command("ATAT1",1000);
        command("ATSP0",2200);
        command("0100",2200);
    }

    private static void pollStandardPids() throws Exception{
        float[] data;

        data=pid("0C",2);
        rpm=data==null?Float.NaN:(data[0]*256f+data[1])/4f;

        data=pid("05",1);
        coolantF=data==null?Float.NaN:toF(data[0]-40f);

        data=pid("04",1);
        engineLoad=data==null?Float.NaN:data[0]*100f/255f;

        data=pid("0F",1);
        intakeF=data==null?Float.NaN:toF(data[0]-40f);

        data=pid("0D",1);
        obdSpeedMph=data==null?Float.NaN:data[0]*0.621371f;

        data=pid("42",2);
        if(data!=null)batteryV=(data[0]*256f+data[1])/1000f;
        else{
            batteryV=Float.NaN;
            String voltage=command("ATRV",1000).replaceAll("[^0-9.]","");
            try{if(!voltage.isEmpty())batteryV=Float.parseFloat(voltage);}catch(Throwable ignored){}
        }

        float map=Float.NaN,baro=Float.NaN;
        data=pid("0B",1);if(data!=null)map=data[0];
        data=pid("33",1);if(data!=null)baro=data[0];
        boostPsi=Float.NaN;
        if(!Float.isNaN(map)&&!Float.isNaN(baro)&&baro>0){
            boostPsi=Math.max(0,(map-baro)*0.1450377f);
        }
        // Transmission temperature is manufacturer-specific on this vehicle.
        // Leave it unsupported until a verified read-only RAM PID is available.
        transmissionF=Float.NaN;
    }

    private static float[] pid(String code,int count) throws Exception{
        String response=command("01"+code,1500).toUpperCase(Locale.US);
        if(response.contains("NO DATA")||response.contains("UNABLE TO CONNECT"))return null;
        String compact=response.replaceAll("[^0-9A-F]","");
        String marker="41"+code;
        int at=compact.indexOf(marker);
        if(at<0||compact.length()<at+marker.length()+count*2)return null;
        float[] values=new float[count];
        int start=at+marker.length();
        try{
            for(int i=0;i<count;i++)values[i]=Integer.parseInt(compact.substring(start+i*2,start+i*2+2),16);
            return values;
        }catch(Throwable invalid){return null;}
    }

    private static String command(String value,long timeout) throws Exception{
        InputStream in=input;OutputStream out=output;
        if(in==null||out==null)throw new IllegalStateException("OBD socket closed");
        while(in.available()>0)in.read();
        out.write((value+"\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        StringBuilder result=new StringBuilder();
        long deadline=SystemClock.elapsedRealtime()+timeout;
        byte[] buffer=new byte[256];
        while(running&&SystemClock.elapsedRealtime()<deadline){
            int available=in.available();
            if(available>0){
                int read=in.read(buffer,0,Math.min(buffer.length,available));
                if(read>0){
                    result.append(new String(buffer,0,read,StandardCharsets.US_ASCII));
                    if(result.indexOf(">")>=0)break;
                }
            }else sleep(12);
        }
        if(result.length()==0)throw new java.io.IOException("OBD timeout");
        return result.toString();
    }

    private static float toF(float celsius){return celsius*9f/5f+32f;}

    private static void closeSocket(){
        try{if(input!=null)input.close();}catch(Throwable ignored){}
        try{if(output!=null)output.close();}catch(Throwable ignored){}
        try{if(socket!=null)socket.close();}catch(Throwable ignored){}
        input=null;output=null;socket=null;
    }

    private static void sleep(long millis){
        try{Thread.sleep(millis);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
    }
}
