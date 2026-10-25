// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Local ADB diagnostics, guarded by the system DUMP permission. */
public final class DiagnosticReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NetflixButtonService service = NetflixButtonService.instance;
        String command = intent.getStringExtra("command");
        if (command == null) command = "status";
        SettingsStore settings = new SettingsStore(context);
        switch (command) {
        case "enable": settings.values.edit().putBoolean("enabled",intent.getBooleanExtra("enabled",true)).apply(); break;
        case "holiday": settings.holiday(intent.getBooleanExtra("enabled",true)); break;
        case "far": case "near": if (service != null) service.calibrate(command); break;
        case "test": if (service != null) service.testNight(); break;
        case "cancel-test": if (service != null) service.cancelTest(); break;
        case "status": break;
        default: setResultData("Unknown command"); setResultCode(1); return;
        }
        setResultData(service == null ? "Accessibility service is not running" : service.status());
        setResultCode(service == null ? 1 : 0);
    }
}
