package com.mdiaz.trxlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;

public class MediaBridge extends NotificationListenerService {
    private static volatile MediaBridge instance;
    private static volatile MediaController controller;
    private static volatile MediaController.Callback callback;
    public static volatile String title = "NO TRACK SELECTED";
    public static volatile String artist = "Choose a media app";
    public static volatile String source = "";
    public static volatile Bitmap artwork;
    public static volatile boolean playing;
    public static volatile long durationMs;
    private static volatile long positionMs;
    private static volatile long positionCapturedAt;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        refresh(this);
    }

    @Override public void onListenerDisconnected() {
        instance = null;
        detachController();
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(this); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(this); }

    public static boolean hasAccess(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                context.getContentResolver(),"enabled_notification_listeners");
            return enabled != null &&
                enabled.toLowerCase().contains(context.getPackageName().toLowerCase());
        } catch (Throwable ignored) { return false; }
    }

    public static void ensureConnected(Context context) {
        if (!hasAccess(context)) return;
        try {
            NotificationListenerService.requestRebind(
                new ComponentName(context,MediaBridge.class));
        } catch (Throwable ignored) { }
        refresh(context);
    }

    public static void requestAccess(Context context) {
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Throwable ignored) {
            try {
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Throwable ignoredAgain) { }
        }
    }

    public static void refresh(Context context) {
        if (!hasAccess(context)) {
            title = "MEDIA ACCESS REQUIRED";
            artist = "Tap play and enable TRX Media Controls";
            artwork = null;
            playing = false;
            durationMs = 0;
            positionMs = 0;
            return;
        }
        try {
            MediaSessionManager manager =
                (MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = manager.getActiveSessions(
                new ComponentName(context,MediaBridge.class));
            MediaController best = null;
            for (MediaController item : controllers) {
                PlaybackState state = item.getPlaybackState();
                if (best == null) best = item;
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    best = item;
                    break;
                }
            }
            attachController(best);
            updateMetadata(best);
        } catch (SecurityException error) {
            title = "MEDIA ACCESS REQUIRED";
            artist = "Enable TRX Media Controls";
            artwork = null;
        } catch (Throwable ignored) { }
    }

    private static void attachController(MediaController next) {
        if (controller == next) return;
        detachController();
        controller = next;
        if (next == null) return;
        callback = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata metadata) {
                updateMetadata(controller);
            }
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                updateMetadata(controller);
            }
            @Override public void onSessionDestroyed() {
                controller = null;
                title = "NO TRACK SELECTED";
                artist = "Choose a media app";
                artwork = null;
                playing = false;
            }
        };
        try { next.registerCallback(callback); } catch (Throwable ignored) { }
    }

    private static void detachController() {
        try { if (controller != null && callback != null) controller.unregisterCallback(callback); }
        catch (Throwable ignored) { }
        controller = null;
        callback = null;
    }

    private static void updateMetadata(MediaController active) {
        if (active == null) {
            title = "NO ACTIVE MEDIA SESSION";
            artist = "Start music, then return here";
            source = "";
            artwork = null;
            playing = false;
            return;
        }
        try {
            source = active.getPackageName();
            PlaybackState state = active.getPlaybackState();
            playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            positionMs = state == null ? 0 : Math.max(0,state.getPosition());
            positionCapturedAt = android.os.SystemClock.elapsedRealtime();
            MediaMetadata metadata = active.getMetadata();
            if (metadata == null) {
                title = "WAITING FOR TRACK INFO";
                artist = source;
                artwork = null;
                return;
            }
            durationMs = Math.max(0,metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
            String nextTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (nextTitle == null || nextTitle.trim().isEmpty())
                nextTitle = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            String nextArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (nextArtist == null || nextArtist.trim().isEmpty())
                nextArtist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (nextArtist == null || nextArtist.trim().isEmpty())
                nextArtist = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            title = nextTitle == null || nextTitle.trim().isEmpty() ? "UNKNOWN TRACK" : nextTitle;
            artist = nextArtist == null || nextArtist.trim().isEmpty() ? source : nextArtist;
            Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            artwork = art;
        } catch (Throwable ignored) { }
    }

    public static long currentPositionMs(){
        long result=positionMs;
        if(playing)result+=Math.max(0,android.os.SystemClock.elapsedRealtime()-positionCapturedAt);
        if(durationMs>0)result=Math.min(result,durationMs);
        return Math.max(0,result);
    }

    public static void previous(Context context) {
        try { refresh(context); if (controller != null) controller.getTransportControls().skipToPrevious(); }
        catch (Throwable ignored) { }
    }

    public static void next(Context context) {
        try { refresh(context); if (controller != null) controller.getTransportControls().skipToNext(); }
        catch (Throwable ignored) { }
    }

    public static void toggle(Context context) {
        if (!hasAccess(context)) {
            requestAccess(context);
            return;
        }
        try {
            refresh(context);
            if (controller == null) return;
            if (playing) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        } catch (Throwable ignored) { }
    }
}
