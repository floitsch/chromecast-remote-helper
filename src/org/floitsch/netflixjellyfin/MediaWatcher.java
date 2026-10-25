// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioPlaybackConfiguration;
import android.os.SystemClock;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.util.Log;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MediaWatcher {
    private final MediaSessionManager manager;
    private final AudioManager audio;
    private final AudioFallback audioFallback = new AudioFallback();
    private final AudioManager.AudioPlaybackCallback audioCallback = new AudioManager.AudioPlaybackCallback() {
        @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations) { evaluate(); }
    };
    private final ComponentName access;
    private final Handler handler;
    private final Runnable changed;
    private final Map<MediaSession.Token, MediaController> controllers = new HashMap<>();
    private final Map<MediaSession.Token, MediaController.Callback> callbacks = new HashMap<>();
    private final Set<MediaSession.Token> played = new HashSet<>();
    private boolean registered;
    private boolean available;
    private String lastSummary = "";
    private NightGate.Playback phase = NightGate.Playback.UNKNOWN;
    private String summary = "Playback access not available";
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = ignored -> refresh();

    MediaWatcher(Context context, Handler handler, Runnable changed) {
        this.manager = context.getSystemService(MediaSessionManager.class);
        this.audio = context.getSystemService(AudioManager.class);
        this.access = new ComponentName(context, MediaAccessService.class);
        this.handler = handler;
        this.changed = changed;
        audio.registerAudioPlaybackCallback(audioCallback, handler);
    }
    void refresh() {
        try {
            if (!registered) {
                manager.addOnActiveSessionsChangedListener(sessions, access, handler);
                registered = true;
            }
            List<MediaController> active = manager.getActiveSessions(access);
            Set<MediaSession.Token> tokens = new HashSet<>();
            for (MediaController controller : active) {
                MediaSession.Token token = controller.getSessionToken();
                tokens.add(token);
                if (!controllers.containsKey(token)) {
                    MediaController.Callback callback = new MediaController.Callback() {
                        @Override public void onPlaybackStateChanged(PlaybackState state) { evaluate(); }
                        @Override public void onMetadataChanged(MediaMetadata metadata) { evaluate(); }
                        @Override public void onSessionDestroyed() { handler.post(() -> refresh()); }
                    };
                    controllers.put(token, controller);
                    callbacks.put(token, callback);
                    controller.registerCallback(callback, handler);
                }
            }
            for (MediaSession.Token token : new HashSet<>(controllers.keySet())) {
                if (!tokens.contains(token)) {
                    controllers.remove(token).unregisterCallback(callbacks.remove(token));
                    played.remove(token);
                }
            }
            available = true;
            evaluate();
        } catch (SecurityException error) {
            available = false;
            evaluate();
        }
    }
    private void evaluate() {
        boolean active = false;
        StringBuilder details = new StringBuilder();
        for (Map.Entry<MediaSession.Token, MediaController> entry : controllers.entrySet()) {
            MediaController controller = entry.getValue();
            PlaybackState state = controller.getPlaybackState();
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            boolean moving = value == PlaybackState.STATE_PLAYING || value == PlaybackState.STATE_BUFFERING
                || value == PlaybackState.STATE_CONNECTING || value == PlaybackState.STATE_FAST_FORWARDING
                || value == PlaybackState.STATE_REWINDING || value == PlaybackState.STATE_SKIPPING_TO_NEXT
                || value == PlaybackState.STATE_SKIPPING_TO_PREVIOUS || value == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;
            if (moving) played.add(entry.getKey());
            MediaMetadata metadata = controller.getMetadata();
            boolean pausedContent = value == PlaybackState.STATE_PAUSED
                && (played.contains(entry.getKey()) || (metadata != null && metadata.size() > 0));
            active |= moving || pausedContent;
            if (details.length() > 0) details.append(", ");
            details.append(controller.getPackageName()).append('=').append(value);
        }
        boolean audioActive = audioPlaying();
        boolean fallbackActive = audioFallback.active(audioActive, SystemClock.elapsedRealtime());
        active |= fallbackActive;
        if (audioActive || audioFallback.paused()) {
            if (details.length() > 0) details.append(", ");
            details.append(audioActive ? "audio=playing" : "audio=paused-by-remote");
        }
        phase = !available ? NightGate.Playback.UNKNOWN : active ? NightGate.Playback.ACTIVE : NightGate.Playback.IDLE;
        summary = !available ? "Playback access unavailable — navigation stays enabled"
            : (active ? "Video playing / paused / buffering" : "No active playback") + (details.length() == 0 ? "" : "\n" + details);
        String log = phase + ": " + details;
        if (!log.equals(lastSummary)) { Log.i(NetflixButtonService.TAG, "Playback " + log); lastSummary = log; }
        changed.run();
    }
    private boolean audioPlaying() {
        if (audio.isMusicActive()) return true;
        // Active configurations also cover direct/passthrough players. Exclude UI sounds.
        for (AudioPlaybackConfiguration configuration : audio.getActivePlaybackConfigurations()) {
            int usage = configuration.getAudioAttributes().getUsage();
            if (usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME) return true;
        }
        return false;
    }
    void remoteKey(int code) {
        long now = SystemClock.elapsedRealtime();
        if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER
            || code == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE || code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            audioFallback.pauseIntent(now, audioPlaying());
        } else if (code == android.view.KeyEvent.KEYCODE_HOME || code == android.view.KeyEvent.KEYCODE_MEDIA_STOP) audioFallback.leave();
        else if (code == android.view.KeyEvent.KEYCODE_BACK) audioFallback.back(now);
    }
    NightGate.Playback phase() { return phase; }
    String summary() { return summary; }
    boolean available() { return available; }
    void close() {
        audio.unregisterAudioPlaybackCallback(audioCallback);
        if (registered) manager.removeOnActiveSessionsChangedListener(sessions);
        for (MediaSession.Token token : controllers.keySet()) controllers.get(token).unregisterCallback(callbacks.get(token));
        controllers.clear(); callbacks.clear(); played.clear();
    }
}
