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
    public static volatile String[] queueTitles=new String[0];
    public static volatile String[] queueArtists=new String[0];
    public static volatile Bitmap[] queueArtwork=new Bitmap[0];
    private static volatile long positionMs;
    private static volatile long positionCapturedAt;
    private static final java.util.Map<String,Bitmap> artworkCache=java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<String,Bitmap>(64,.75f,true){
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,Bitmap> eldest){return size()>64;}
        });
    private static final java.util.Set<String> artworkRequests=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.concurrent.ExecutorService artworkExecutor=java.util.concurrent.Executors.newFixedThreadPool(2);

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
            @Override public void onQueueChanged(List<android.media.session.MediaSession.QueueItem> queue) {
                updateQueue(controller);
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
            queueTitles=new String[0];queueArtists=new String[0];queueArtwork=new Bitmap[0];
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
            if(art!=null)artworkCache.put(trackKey(title,artist),art);
            updateQueue(active);
        } catch (Throwable ignored) { }
    }
    private static volatile long[] queueIds=new long[0];
    public static void playQueueItem(Context context,int index){
        long[] ids=queueIds;
        if(controller!=null&&index>=0&&index<ids.length){
            try{controller.getTransportControls().skipToQueueItem(ids[index]);}catch(RuntimeException e){android.widget.Toast.makeText(context,"Player does not support queue selection",0).show();}
        }
    }
    private static void updateQueue(MediaController active){
        queueIds=new long[0];
        if(active==null){queueTitles=new String[0];queueArtists=new String[0];queueArtwork=new Bitmap[0];return;}
        try{
            List<android.media.session.MediaSession.QueueItem> items=active.getQueue();
            if(items==null||items.isEmpty()){queueTitles=new String[0];queueArtists=new String[0];queueArtwork=new Bitmap[0];return;}
            long currentId=active.getPlaybackState()==null?-1:active.getPlaybackState().getActiveQueueItemId();
            java.util.ArrayList<String> titles=new java.util.ArrayList<>(),artists=new java.util.ArrayList<>();
            java.util.ArrayList<Bitmap> images=new java.util.ArrayList<>();
            java.util.ArrayList<Long> ids=new java.util.ArrayList<>();
            for(android.media.session.MediaSession.QueueItem item:items){
                if(item==null||item.getQueueId()==currentId)continue;
                android.media.MediaDescription d=item.getDescription();
                CharSequence t=d==null?null:d.getTitle(),a=d==null?null:d.getSubtitle();
                String track=t==null?"UPCOMING TRACK":t.toString(),performer=a==null?"":a.toString();
                titles.add(track);artists.add(performer);
                ids.add(item.getQueueId());
                Bitmap image=descriptionArtwork(d);
                if(image==null)image=artworkCache.get(trackKey(track,performer));
                images.add(image);if(titles.size()>=3)break;
            }
            queueTitles=titles.toArray(new String[0]);queueArtists=artists.toArray(new String[0]);queueArtwork=images.toArray(new Bitmap[0]);
            long[] result=new long[ids.size()];for(int i=0;i<result.length;i++)result[i]=ids.get(i);queueIds=result;
            for(int i=0;i<queueTitles.length;i++)if(i>=queueArtwork.length||queueArtwork[i]==null)requestArtwork(queueTitles[i],i<queueArtists.length?queueArtists[i]:"");
        }catch(Throwable ignored){queueTitles=new String[0];queueArtists=new String[0];queueArtwork=new Bitmap[0];}
    }

    private static Bitmap descriptionArtwork(android.media.MediaDescription description){
        if(description==null)return null;
        Bitmap result=description.getIconBitmap();if(result!=null)return result;
        result=loadLocalArtwork(description.getIconUri());if(result!=null)return result;
        try{
            android.os.Bundle extras=description.getExtras();
            if(extras!=null)for(String key:extras.keySet()){
                String lower=key==null?"":key.toLowerCase(java.util.Locale.US);
                if(!(lower.contains("art")||lower.contains("album")||lower.contains("icon")||lower.contains("image")||lower.contains("thumb")))continue;
                Object value;try{value=extras.get(key);}catch(Throwable ignored){continue;}
                if(value instanceof Bitmap)return (Bitmap)value;
                if(value instanceof android.net.Uri){result=loadLocalArtwork((android.net.Uri)value);if(result!=null)return result;}
                if(value instanceof String){try{result=loadLocalArtwork(android.net.Uri.parse((String)value));if(result!=null)return result;}catch(Throwable ignored){}}
            }
        }catch(Throwable ignored){}
        return null;
    }

    private static Bitmap loadLocalArtwork(android.net.Uri uri){
        if(uri==null||instance==null)return null;
        String scheme=uri.getScheme();
        if(scheme==null||(!scheme.equals("content")&&!scheme.equals("file")&&!scheme.equals("android.resource")))return null;
        java.io.InputStream stream=null;
        try{stream=instance.getContentResolver().openInputStream(uri);return android.graphics.BitmapFactory.decodeStream(stream);}
        catch(Throwable ignored){return null;}finally{try{if(stream!=null)stream.close();}catch(Throwable ignored){}}
    }

    private static String trackKey(String track,String performer){
        return ((track==null?"":track)+"|"+(performer==null?"":performer)).trim().toLowerCase(java.util.Locale.US);
    }

    public static void clearArtworkCache(){artworkCache.clear();queueArtwork=new Bitmap[queueTitles.length];}

    private static void requestArtwork(final String track,final String performer){
        if(instance!=null&&!instance.getSharedPreferences("launcher",MODE_PRIVATE).getBoolean("online_artwork",true))return;
        final String key=trackKey(track,performer);
        if(key.length()<2||artworkCache.containsKey(key)||!artworkRequests.add(key))return;
        artworkExecutor.execute(()->{
            try{
                Bitmap found=lookupArtwork(track,performer);
                if(found!=null){
                    artworkCache.put(key,found);
                    String[] titles=queueTitles,artists=queueArtists;Bitmap[] current=queueArtwork;
                    Bitmap[] updated=java.util.Arrays.copyOf(current,Math.max(current.length,titles.length));
                    for(int i=0;i<titles.length;i++)if(key.equals(trackKey(titles[i],i<artists.length?artists[i]:"")))updated[i]=found;
                    queueArtwork=updated;
                }
            }catch(Throwable ignored){}finally{artworkRequests.remove(key);}
        });
    }

    private static Bitmap lookupArtwork(String track,String performer){
        java.net.HttpURLConnection connection=null;java.io.InputStream stream=null;
        try{
            String query=(track==null?"":track)+" "+(performer==null?"":performer);
            String encoded=java.net.URLEncoder.encode(query,"UTF-8");
            java.net.URL search=new java.net.URL("https://itunes.apple.com/search?media=music&entity=song&country=US&limit=1&term="+encoded);
            connection=(java.net.HttpURLConnection)search.openConnection();connection.setConnectTimeout(4500);connection.setReadTimeout(5500);connection.setRequestProperty("User-Agent","TRX-Launcher/1.5.2");
            stream=connection.getInputStream();java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
            while((count=stream.read(buffer))!=-1)data.write(buffer,0,count);
            org.json.JSONObject root=new org.json.JSONObject(data.toString("UTF-8"));org.json.JSONArray results=root.optJSONArray("results");
            if(results==null||results.length()==0)return null;String artworkUrl=results.getJSONObject(0).optString("artworkUrl100","");
            if(artworkUrl.isEmpty())return null;artworkUrl=artworkUrl.replace("100x100bb","300x300bb");
            try{stream.close();}catch(Throwable ignored){}stream=null;connection.disconnect();connection=null;
            connection=(java.net.HttpURLConnection)new java.net.URL(artworkUrl).openConnection();connection.setConnectTimeout(4500);connection.setReadTimeout(5500);connection.setRequestProperty("User-Agent","TRX-Launcher/1.5.2");
            stream=connection.getInputStream();return android.graphics.BitmapFactory.decodeStream(stream);
        }catch(Throwable ignored){return null;}
        finally{try{if(stream!=null)stream.close();}catch(Throwable ignored){}if(connection!=null)connection.disconnect();}
    }

    public static long currentPositionMs(){
        long result=positionMs;
        if(playing)result+=Math.max(0,android.os.SystemClock.elapsedRealtime()-positionCapturedAt);
        if(durationMs>0)result=Math.min(result,durationMs);
        return Math.max(0,result);
    }

    public static void seekTo(Context context,long position){try{refresh(context);if(controller!=null)controller.getTransportControls().seekTo(Math.max(0,position));}catch(Throwable ignored){}}

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
