// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

/** Require a fresh approach and sustained signal, rather than a single spike. */
final class ProximityGate {
    private static final long HOLD_MS = 2000;
    private long nearSince = -1;
    private long lastSample = -1;
    private boolean latched;
    private double filtered;
    private boolean initialized;

    boolean sample(int rssi, int threshold, long elapsed) {
        if (rssi >= 0 || rssi <= -127) return false;
        if (lastSample >= 0 && elapsed - lastSample > 4000) {
            nearSince = -1;
            initialized = false;
        }
        lastSample = elapsed;
        filtered = initialized ? .5 * rssi + .5 * filtered : rssi;
        initialized = true;
        if (filtered < threshold - 6) latched = false;
        if (filtered < threshold) { nearSince = -1; return false; }
        if (nearSince < 0) nearSince = elapsed;
        if (!latched && elapsed - nearSince >= HOLD_MS) {
            latched = true;
            return true;
        }
        return false;
    }

    void reset() { nearSince = lastSample = -1; latched = initialized = false; }
}
