package com.mdiaz.trxlauncher;

/** Immutable read-only values. A Bluetooth OBD service will publish these later. */
public final class TelemetrySnapshot {
    public final float boostPsi, rpm, coolantF, transF, throttle, engineLoad, intakeF, batteryV;
    public final boolean connected;
    public TelemetrySnapshot(float boostPsi,float rpm,float coolantF,float transF,float throttle,float engineLoad,float intakeF,float batteryV,boolean connected) {
        this.boostPsi=boostPsi; this.rpm=rpm; this.coolantF=coolantF; this.transF=transF;
        this.throttle=throttle; this.engineLoad=engineLoad; this.intakeF=intakeF; this.batteryV=batteryV; this.connected=connected;
    }
    public static TelemetrySnapshot disconnected() { return new TelemetrySnapshot(0,0,0,0,0,0,0,0,false); }
}
