// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

/** Policy independent of Android, wall-clock changes, and Bluetooth callbacks. */
final class NightGate {
    enum Playback { ACTIVE, IDLE, UNKNOWN }
    private static final long END_GRACE_MS = 3000;
    private String window;
    private boolean unlocked;
    private boolean playedSinceUnlock;
    private boolean previouslyActive;
    private long idleSince = -1;
    private boolean blocked;

    boolean update(String windowKey, boolean night, Playback playback, long elapsed) {
        if (!night || !windowKey.equals(window)) {
            window = windowKey;
            unlocked = playedSinceUnlock = previouslyActive = blocked = false;
            idleSince = -1;
        }
        if (!night) return false;
        // Unavailable monitoring must never silently block a playing video.
        if (playback == Playback.UNKNOWN) { blocked = false; return false; }
        if (playback == Playback.ACTIVE) {
            previouslyActive = true;
            idleSince = -1;
            if (unlocked) playedSinceUnlock = true;
            blocked = false;
            return false;
        }
        if (previouslyActive) {
            if (idleSince < 0) idleSince = elapsed;
            if (elapsed - idleSince < END_GRACE_MS) { blocked = false; return false; }
            previouslyActive = false;
            if (playedSinceUnlock) unlocked = false;
            playedSinceUnlock = false;
        }
        blocked = !unlocked;
        return blocked;
    }

    boolean unlock() {
        if (!blocked) return false;
        unlocked = true;
        playedSinceUnlock = false;
        blocked = false;
        return true;
    }

    boolean isUnlocked() { return unlocked; }
}
