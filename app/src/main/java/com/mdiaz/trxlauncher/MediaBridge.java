package com.mdiaz.trxlauncher;

import android.app.Notification;
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
    public static volatile String title = "NO TRACK SELECTED";
    public static volatile String artist = "Choose a media app";
    public static volatile Bitmap artwork;
    public static volatile boolean playing;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        refresh();
    }

    @Override public void onListenerDisconnected() {
        instance = null;
        controller = null;
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }

    public static boolean hasAccess(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
            return enabled != null && enabled.contains(context.getPackageName());
        } catch (Throwable ignored) { return false; }
    }

    public static void requestAccess(Context context) {
        try {
            Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
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

    public static void refresh() {
        MediaBridge service = instance;
        if (service == null) return;
        try {
            MediaSessionManager manager =
                (MediaSessionManager)service.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> controllers =
                manager.getActiveSessions(new ComponentName(service, MediaBridge.class));
            MediaController best = null;
            for (MediaController item : controllers) {
                PlaybackState state = item.getPlaybackState();
                if (best == null) best = item;
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    best = item;
                    break;
                }
            }
            controller = best;
            updateMetadata(best);
        } catch (Throwable ignored) { }
    }

    private static void updateMetadata(MediaController active) {
        if (active == null) {
            title = "NO TRACK SELECTED";
            artist = "Choose a media app";
            artwork = null;
            playing = false;
            return;
        }
        try {
            PlaybackState state = active.getPlaybackState();
            playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            MediaMetadata metadata = active.getMetadata();
            if (metadata != null) {
                String nextTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                String nextArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (nextArtist == null || nextArtist.trim().isEmpty())
                    nextArtist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                title = nextTitle == null || nextTitle.trim().isEmpty() ? "UNKNOWN TRACK" : nextTitle;
                artist = nextArtist == null || nextArtist.trim().isEmpty() ?
                    active.getPackageName() : nextArtist;
                Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                artwork = art;
            }
        } catch (Throwable ignored) { }
    }

    public static void previous() {
        try { if (controller != null) controller.getTransportControls().skipToPrevious(); }
        catch (Throwable ignored) { }
    }

    public static void next() {
        try { if (controller != null) controller.getTransportControls().skipToNext(); }
        catch (Throwable ignored) { }
    }

    public static void toggle(Context context) {
        if (!hasAccess(context)) {
            requestAccess(context);
            return;
        }
        try {
            refresh();
            if (controller == null) return;
            if (playing) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
            playing = !playing;
        } catch (Throwable ignored) { }
    }
}
