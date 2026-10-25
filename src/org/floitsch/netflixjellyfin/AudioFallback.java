// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

/** Fallback for legacy players which do not publish a useful media session. */
final class AudioFallback {
    private static final long CONTROL_WINDOW_MS = 2500;
    private long pauseIntentUntil = -1;
    private long lastBack = -10000;
    private boolean wasAudio;
    private boolean pausedByRemote;

    void pauseIntent(long now, boolean currentlyPlaying) {
        if (currentlyPlaying) { pauseIntentUntil = now + CONTROL_WINDOW_MS; wasAudio = true; }
    }
    void leave() { pausedByRemote = false; pauseIntentUntil = -1; }
    void back(long now) { if (now - lastBack < 2500) leave(); lastBack = now; }
    boolean active(boolean audio, long now) {
        if (audio) pausedByRemote = false;
        else if (wasAudio && now <= pauseIntentUntil) pausedByRemote = true;
        wasAudio = audio;
        return audio || pausedByRemote;
    }
    boolean paused() { return pausedByRemote; }
}
