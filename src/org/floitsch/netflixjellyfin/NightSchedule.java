// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** Days label the evening: Friday 01:30 means Saturday at 01:30. */
final class NightSchedule {
    final int[] cutoffs;
    final int morning;
    final int holidayCutoff;
    final LocalDate holidayNight;

    NightSchedule(int[] cutoffs, int morning, int holidayCutoff, LocalDate holidayNight) {
        if (cutoffs.length != 7 || morning <= 0 || morning > 12 * 60) {
            throw new IllegalArgumentException("Morning must be after 00:00 and no later than 12:00");
        }
        this.cutoffs = cutoffs.clone();
        this.morning = morning;
        this.holidayCutoff = holidayCutoff;
        this.holidayNight = holidayNight;
        for (int cutoff : cutoffs) validateCutoff(cutoff, morning);
        validateCutoff(holidayCutoff, morning);
    }

    private static void validateCutoff(int cutoff, int morning) {
        if (cutoff < 0 || cutoff >= 24 * 60 || (cutoff > morning && cutoff < 12 * 60)) {
            throw new IllegalArgumentException("Night cutoff must be before the morning reset, or in the evening");
        }
    }

    static LocalDate eveningDate(ZonedDateTime now, int morning) {
        return now.toLocalTime().isBefore(LocalTime.of(morning / 60, morning % 60))
            ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }

    Window window(ZonedDateTime now) {
        LocalDate night = eveningDate(now, morning);
        int cutoff = night.equals(holidayNight) ? holidayCutoff : cutoffs[night.getDayOfWeek().getValue() - 1];
        LocalDate startDate = cutoff < 12 * 60 ? night.plusDays(1) : night;
        ZonedDateTime start = startDate.atTime(cutoff / 60, cutoff % 60).atZone(now.getZone());
        ZonedDateTime end = night.plusDays(1).atTime(morning / 60, morning % 60).atZone(now.getZone());
        return new Window(night.toString(), !now.isBefore(start) && now.isBefore(end), start, end);
    }

    static final class Window {
        final String key;
        final boolean active;
        final ZonedDateTime start;
        final ZonedDateTime end;
        Window(String key, boolean active, ZonedDateTime start, ZonedDateTime end) {
            this.key = key; this.active = active; this.start = start; this.end = end;
        }
    }
}
