// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

final class RemoteProximity {
    interface Listener {
        void sample(int rssi, long elapsed);
        boolean fastPolling();
    }
    private final Context context;
    private final Handler handler;
    private final Listener listener;
    private BluetoothGatt connection;
    private boolean connected;
    private boolean closed;
    private boolean pending;
    private long requestedAt;
    private long lastSample;
    private int lastRssi;
    private String status = "Remote not connected";
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (connection == null) connect();
            if (connection != null && connected && (!pending || SystemClock.elapsedRealtime() - requestedAt > 5000)) {
                try {
                    requestedAt = SystemClock.elapsedRealtime();
                    pending = connection.readRemoteRssi();
                    if (!pending) status = "RSSI request unavailable";
                } catch (SecurityException error) { status = "Nearby devices permission required"; }
            }
            handler.postDelayed(this, listener.fastPolling() ? 500 : 10000);
        }
    };
    RemoteProximity(Context context, Handler handler, Listener listener) {
        this.context = context; this.handler = handler; this.listener = listener;
    }
    void start() { closed = false; handler.post(poll); }
    private void connect() {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status = "Nearby devices permission required"; return;
        }
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        if (manager == null || manager.getAdapter() == null) { status = "Bluetooth unavailable"; return; }
        BluetoothDevice remote = null;
        for (BluetoothDevice device : manager.getAdapter().getBondedDevices()) {
            if ("Chromecast Remote".equals(device.getName())) {
                if (remote != null) { status = "Multiple paired Chromecast remotes; cannot choose safely"; return; }
                remote = device;
            }
        }
        if (remote == null) { status = "No paired Chromecast Remote"; return; }
        status = "Waiting for remote connection";
        connection = remote.connectGatt(context, true, new BluetoothGattCallback() {
            @Override public void onConnectionStateChange(BluetoothGatt gatt, int result, int state) {
                handler.post(() -> {
                    if (closed || gatt != connection) return;
                    connected = result == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED;
                    pending = false;
                    status = connected ? "Remote connected; waiting for signal" : "Waiting for remote to reconnect";
                    Log.i(NetflixButtonService.TAG, "Remote GATT status=" + result + " connected=" + connected);
                    if (result != BluetoothGatt.GATT_SUCCESS) { connection.close(); connection = null; }
                    handler.removeCallbacks(poll); handler.post(poll);
                });
            }
            @Override public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int result) {
                handler.post(() -> {
                    if (closed || gatt != connection) return;
                    pending = false;
                    if (result != BluetoothGatt.GATT_SUCCESS || rssi >= 0 || rssi <= -127) {
                        status = "RSSI unavailable (status " + result + ")"; return;
                    }
                    lastRssi = rssi; lastSample = SystemClock.elapsedRealtime();
                    status = "Remote signal " + rssi + " dBm";
                    listener.sample(rssi, lastSample);
                });
            }
        }, BluetoothDevice.TRANSPORT_LE);
    }
    void requestSample() { handler.removeCallbacks(poll); handler.post(poll); }
    String status() {
        if (lastSample != 0 && SystemClock.elapsedRealtime() - lastSample > 15000) return status + " (stale)";
        return status;
    }
    void close() {
        closed = true; handler.removeCallbacks(poll);
        if (connection != null) { connection.close(); connection = null; }
    }
}
