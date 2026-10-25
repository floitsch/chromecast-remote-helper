// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import android.service.notification.NotificationListenerService;

/** Access token for Android media sessions; notification contents are not read. */
public final class MediaAccessService extends NotificationListenerService {
    @Override public void onListenerConnected() {
        NetflixButtonService service = NetflixButtonService.instance;
        if (service != null) service.refreshPlayback();
    }
    @Override public void onListenerDisconnected() {
        NetflixButtonService service = NetflixButtonService.instance;
        if (service != null) service.refreshPlayback();
    }
}
