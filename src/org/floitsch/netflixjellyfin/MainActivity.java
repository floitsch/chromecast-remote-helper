// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    static boolean visible;
    private final Handler handler = new Handler();
    private final EditText[] times = new EditText[7];
    private SettingsStore settings;
    private LinearLayout layout;
    private TextView status;
    private CheckBox enabled;
    private CheckBox holiday;
    private EditText morning;
    private EditText holidayCutoff;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            NetflixButtonService service = NetflixButtonService.instance;
            status.setText(service == null ? "Enable Remote Helper under Accessibility" : service.status());
            handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new SettingsStore(this);
        NightSchedule schedule = settings.schedule();
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(48,32,48,32);
        scroll.addView(layout); setContentView(scroll);
        text("Remote Helper", 28);
        text("Netflix opens Jellyfin. Night restriction blocks only this remote’s arrows.\nPlayback, pause and buffering keep controls usable. Back, Home, volume and settings remain available.", 18);
        status = text("Starting", 18);
        enabled = new CheckBox(this); enabled.setText("Enable night restriction"); enabled.setChecked(settings.enabled()); layout.addView(enabled);
        enabled.setOnCheckedChangeListener((button, checked) -> settings.values.edit().putBoolean("enabled", checked).apply());
        String[] days = {"Monday night", "Tuesday night", "Wednesday night", "Thursday night", "Friday night → Saturday", "Saturday night → Sunday", "Sunday night"};
        text("Night cutoffs (HH:mm). Times after midnight belong to the preceding evening.", 18);
        for (int i=0;i<7;i++) times[i] = timeField(days[i], schedule.cutoffs[i]);
        morning = timeField("Morning reset", schedule.morning);
        holidayCutoff = timeField("Holiday cutoff", schedule.holidayCutoff);
        holiday = new CheckBox(this); holiday.setText("Tomorrow is a holiday — use the holiday cutoff tonight");
        holiday.setChecked(settings.holiday()); layout.addView(holiday);
        holiday.setOnCheckedChangeListener((button, checked) -> settings.holiday(checked));
        button("Save schedule", view -> saveSchedule());
        text("Proximity setup: measure with the remote at your normal seat, then measure with it 10–20 cm from the Chromecast. Hold each position for ten seconds. The restriction stays inactive until both measurements have enough separation.", 18);
        button("Measure at my seat (10 seconds)", view -> calibrate("far"));
        button("Measure beside Chromecast (10 seconds)", view -> calibrate("near"));
        button("Test night restriction until stopped", view -> {
            NetflixButtonService service = NetflixButtonService.instance;
            if (service != null) { service.testNight(); Toast.makeText(this, "Press Home to try arrows. Return here and choose Stop test when finished", Toast.LENGTH_LONG).show(); }
        });
        button("Stop test", view -> { if (NetflixButtonService.instance != null) NetflixButtonService.instance.cancelTest(); });
        button("Nearby devices permission", view -> requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1));
        button("Playback access settings", view -> { enabled.setChecked(false); startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); });
        button("Accessibility settings / disable helper", view -> { enabled.setChecked(false); startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); });
        handler.post(refresh);
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setPadding(0,8,0,8); layout.addView(view); return view;
    }
    private EditText timeField(String label, int value) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this); name.setText(label); name.setTextSize(18);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        EditText edit = new EditText(this); edit.setSingleLine(); edit.setText(SettingsStore.time(value));
        edit.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        edit.setSelectAllOnFocus(true); edit.setMinWidth(180); row.addView(edit); layout.addView(row); return edit;
    }
    private void button(String label, View.OnClickListener action) {
        Button button = new Button(this); button.setText(label); button.setOnClickListener(action); layout.addView(button);
    }
    private void saveSchedule() {
        try {
            int[] cutoffs = new int[7];
            for(int i=0;i<7;i++) cutoffs[i]=SettingsStore.parseTime(times[i].getText().toString());
            int reset=SettingsStore.parseTime(morning.getText().toString());
            int holidayTime=SettingsStore.parseTime(holidayCutoff.getText().toString());
            new NightSchedule(cutoffs, reset, holidayTime, null);
            SharedPreferences.Editor editor=settings.values.edit().putInt("morning",reset).putInt("holiday_cutoff",holidayTime);
            for(int i=0;i<7;i++) editor.putInt("cutoff_"+i,cutoffs[i]);
            editor.apply(); Toast.makeText(this,"Schedule saved",Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException error) { Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void calibrate(String label) {
        if (NetflixButtonService.instance != null) NetflixButtonService.instance.calibrate(label);
    }
    @Override public void onResume() { super.onResume(); visible=true; }
    @Override public void onPause() { visible=false; super.onPause(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
