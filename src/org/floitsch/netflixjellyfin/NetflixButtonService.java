// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NetflixButtonService extends AccessibilityService implements RemoteProximity.Listener {
    static final String TAG = "NetflixToJellyfin";
    static NetflixButtonService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NightGate gate = new NightGate();
    private final ProximityGate proximityGate = new ProximityGate();
    private final Set<Integer> consumed = new HashSet<>();
    private final List<Integer> calibration = new ArrayList<>();
    private SettingsStore settings;
    private RemoteProximity proximity;
    private MediaWatcher media;
    private boolean blocked;
    private String gateStatus = "Starting";
    private String calibrationLabel;
    private long calibrationEnd;
    private long testEnd;
    private long lastHint;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (media != null) media.refresh();
            if (calibrationLabel != null && SystemClock.elapsedRealtime() >= calibrationEnd) finishCalibration();
            updateGate();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onServiceConnected() {
        instance = this;
        settings = new SettingsStore(this);
        media = new MediaWatcher(this, handler, () -> updateGate());
        proximity = new RemoteProximity(this, handler, this);
        proximity.start();
        handler.post(tick);
        Log.i(TAG, "Remote helper connected; screen-content access disabled");
    }
    void refreshPlayback() { if (media != null) handler.post(() -> media.refresh()); }
    private void updateGate() {
        if (settings == null || media == null) return;
        NightSchedule.Window window = settings.schedule().window(ZonedDateTime.now());
        boolean testing = SystemClock.elapsedRealtime() < testEnd;
        boolean night = settings.enabled() && settings.calibrated() && (window.active || testing);
        boolean next = gate.update(testing ? "test" : window.key, night, media.phase(), SystemClock.elapsedRealtime());
        gateStatus = !settings.enabled() ? "Night restriction disabled"
            : !settings.calibrated() ? "Calibrate both positions before enabling the restriction"
            : !media.available() ? "Playback access unavailable — navigation stays enabled"
            : !night ? "Daytime — all buttons work"
            : next ? "Arrows locked — bring remote close to the Chromecast"
            : gate.isUnlocked() ? "Unlocked until the next video finishes"
            : "Video playing / paused / buffering — all buttons work";
        if (next != blocked) {
            blocked = next;
            Log.i(TAG, "Navigation " + (blocked ? "LOCKED" : "ALLOWED") + "; " + gateStatus);
            if (blocked && proximity != null) proximity.requestSample();
        }
    }
    @Override public void sample(int rssi, long elapsed) {
        if (calibrationLabel != null) {
            calibration.add(rssi);
            Log.i(TAG, "Calibration " + calibrationLabel + " sample=" + rssi);
        }
        if (settings.calibrated() && proximityGate.sample(rssi, settings.threshold(), elapsed) && gate.unlock()) {
            Log.i(TAG, "Proximity unlock; signal=" + rssi + " threshold=" + settings.threshold());
            Toast.makeText(this, "Navigation unlocked until the next video finishes", Toast.LENGTH_LONG).show();
            updateGate();
        }
    }
    @Override public boolean fastPolling() { return blocked || calibrationLabel != null || SystemClock.elapsedRealtime() < testEnd; }
    void calibrate(String label) {
        if (calibrationLabel != null) return;
        calibration.clear(); calibrationLabel = label;
        calibrationEnd = SystemClock.elapsedRealtime() + 10000;
        proximity.requestSample();
        Log.i(TAG, "Calibration " + label + " started for 10 seconds");
    }
    private void finishCalibration() {
        String label = calibrationLabel;
        calibrationLabel = null;
        if (calibration.size() < 8) {
            Toast.makeText(this, "Not enough signal samples; press a remote button and retry", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Calibration " + label + " failed: samples=" + calibration.size());
            return;
        }
        Collections.sort(calibration);
        int median = calibration.get(calibration.size() / 2);
        settings.values.edit().putInt(label + "_rssi", median).apply();
        if (settings.values.contains("far_rssi") && settings.values.contains("near_rssi")) {
            int far = settings.values.getInt("far_rssi", -50);
            int near = settings.values.getInt("near_rssi", -50);
            boolean valid = near >= far + 12;
            settings.values.edit().putBoolean("calibrated", valid).putInt("threshold", (near + far) / 2).apply();
            proximityGate.reset();
            Log.i(TAG, "Calibration far=" + far + " near=" + near + " threshold=" + settings.threshold() + " usable=" + valid);
            if (!valid) Toast.makeText(this, "Signals overlap too much. Move the remote closer and recalibrate", Toast.LENGTH_LONG).show();
        }
        Log.i(TAG, "Calibration " + label + " median=" + median);
        updateGate();
    }
    void testNight() { testEnd = Long.MAX_VALUE; updateGate(); proximity.requestSample(); }
    void cancelTest() { testEnd = 0; updateGate(); }
    String status() {
        if (settings == null) return "Starting";
        NightSchedule.Window window = settings.schedule().window(ZonedDateTime.now());
        String signal = proximity == null ? "Waiting for Bluetooth" : proximity.status();
        if (calibrationLabel != null) signal += "\nMeasuring " + calibrationLabel + ": "
            + Math.max(0, (calibrationEnd - SystemClock.elapsedRealtime() + 999) / 1000) + " s remaining";
        return gateStatus + "\n" + (media == null ? "" : media.summary()) + "\n" + signal
            + "\nTonight: " + window.start.toLocalTime() + "–" + window.end.toLocalTime()
            + (settings.calibrated() ? "\nNear threshold: " + settings.threshold() + " dBm" : "\nProximity not calibrated")
            + (settings.values.contains("far_rssi") ? "\nSeat baseline: " + settings.values.getInt("far_rssi", 0) + " dBm" : "")
            + (settings.values.contains("near_rssi") ? "; close baseline: " + settings.values.getInt("near_rssi", 0) + " dBm" : "")
            + (SystemClock.elapsedRealtime() < testEnd ? "\nTEST MODE — stop it in Remote Helper settings" : "");
    }
    private static boolean arrow(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
            || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
            || code == KeyEvent.KEYCODE_DPAD_UP_LEFT || code == KeyEvent.KEYCODE_DPAD_UP_RIGHT
            || code == KeyEvent.KEYCODE_DPAD_DOWN_LEFT || code == KeyEvent.KEYCODE_DPAD_DOWN_RIGHT;
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP && consumed.remove(code)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN && consumed.contains(code)) return true;
        boolean remote = event.getDevice() != null && "Chromecast Remote".equals(event.getDevice().getName());
        if (!remote) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && media != null) media.remoteKey(code);
        int netflix = getSharedPreferences("mapping", MODE_PRIVATE).getInt("keycode", KeyEvent.KEYCODE_BUTTON_4);
        if (code == netflix) {
            if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return true;
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage("org.jellyfin.androidtv");
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage("org.jellyfin.androidtv");
            if (launch == null) return false;
            try {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launch); consumed.add(code);
                Log.i(TAG, "Netflix intercepted; opened stock Jellyfin");
                return true;
            } catch (ActivityNotFoundException | SecurityException error) { Log.e(TAG, "Cannot open Jellyfin", error); return false; }
        }
        if (!arrow(code) || MainActivity.visible) return false;
        updateGate();
        if (!blocked || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        consumed.add(code);
        if (SystemClock.elapsedRealtime() - lastHint > 5000) {
            lastHint = SystemClock.elapsedRealtime();
            Toast.makeText(this, "Night restriction: bring the remote close to the Chromecast", Toast.LENGTH_LONG).show();
        }
        Log.i(TAG, "Blocked arrow " + code);
        proximity.requestSample();
        return true;
    }
    @Override public void onDestroy() {
        instance = null; handler.removeCallbacksAndMessages(null);
        if (media != null) media.close();
        if (proximity != null) proximity.close();
        super.onDestroy();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
