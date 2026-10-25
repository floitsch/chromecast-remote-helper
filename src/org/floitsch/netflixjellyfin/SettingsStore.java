// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

final class SettingsStore {
    final SharedPreferences values;
    SettingsStore(Context context) { values = context.getSharedPreferences("night", Context.MODE_PRIVATE); }
    NightSchedule schedule() {
        int[] defaults = {0, 0, 0, 0, 90, 90, 0};
        int[] cutoffs = new int[7];
        for (int i = 0; i < 7; i++) cutoffs[i] = values.getInt("cutoff_" + i, defaults[i]);
        String holiday = values.getString("holiday_night", "");
        return new NightSchedule(cutoffs, values.getInt("morning", 240),
            values.getInt("holiday_cutoff", 90), holiday.isEmpty() ? null : LocalDate.parse(holiday));
    }
    boolean enabled() { return values.getBoolean("enabled", false); }
    boolean calibrated() { return values.getBoolean("calibrated", false); }
    int threshold() { return values.getInt("threshold", -40); }
    void holiday(boolean enabled) {
        values.edit().putString("holiday_night", enabled ? NightSchedule.eveningDate(ZonedDateTime.now(), values.getInt("morning", 240)).toString() : "").apply();
    }
    boolean holiday() { return NightSchedule.eveningDate(ZonedDateTime.now(), values.getInt("morning", 240)).toString().equals(values.getString("holiday_night", "")); }
    static String time(int minutes) { return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60); }
    static int parseTime(String text) {
        if (!text.matches("[0-2]?[0-9]:[0-5][0-9]")) throw new IllegalArgumentException("Use HH:mm, for example 01:30");
        String[] parts = text.split(":");
        int hour = Integer.parseInt(parts[0]);
        if (hour > 23) throw new IllegalArgumentException("Hour must be 00–23");
        return hour * 60 + Integer.parseInt(parts[1]);
    }
}
