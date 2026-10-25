// Copyright (C) 2026 Toit contributors.
package org.floitsch.netflixjellyfin;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public final class PolicyTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static ZonedDateTime time(String value) {
        return ZonedDateTime.parse(value + "[Europe/Copenhagen]");
    }
    public static void main(String[] args) {
        int[] cutoffs = {0, 0, 0, 0, 90, 90, 0};
        NightSchedule schedule = new NightSchedule(cutoffs, 240, 90, null);
        check(!schedule.window(time("2026-10-23T23:59:00+02:00")).active, "Friday evening allowed");
        check(!schedule.window(time("2026-10-24T00:30:00+02:00")).active, "Friday night grace");
        check(schedule.window(time("2026-10-24T01:30:00+02:00")).active, "Friday night cutoff");
        check(!schedule.window(time("2026-10-24T04:00:00+02:00")).active, "Morning reset");
        check(!schedule.window(time("2026-10-25T01:29:00+02:00")).active, "Saturday night grace");
        check(schedule.window(time("2026-10-25T02:30:00+02:00")).active, "DST first 02:30");
        check(schedule.window(time("2026-10-25T02:30:00+01:00")).active, "DST repeated 02:30");
        check(!schedule.window(time("2026-10-25T04:00:00+01:00")).active, "DST reset");
        check(schedule.window(time("2026-10-26T00:00:00+01:00")).active, "Sunday night midnight cutoff");
        NightSchedule holiday = new NightSchedule(cutoffs, 240, 90, LocalDate.parse("2026-10-25"));
        check(!holiday.window(time("2026-10-26T00:30:00+01:00")).active, "Holiday override tonight");
        check(holiday.window(time("2026-10-26T01:30:00+01:00")).active, "Holiday later cutoff");
        check(holiday.window(time("2026-10-27T00:30:00+01:00")).active, "Holiday expires following night");
        check(NightSchedule.eveningDate(time("2026-10-26T00:30:00+01:00"), 240).equals(LocalDate.parse("2026-10-25")), "Override after midnight uses same evening");
        check(NightSchedule.eveningDate(time("2026-10-26T04:00:00+01:00"), 240).equals(LocalDate.parse("2026-10-26")), "Holiday/date reset occurs at morning boundary");
        check(NightSchedule.eveningDate(time("2026-10-26T07:00:00+01:00"), 240).equals(LocalDate.parse("2026-10-26")), "Holiday chosen in the morning applies to coming night");
        NightSchedule evening = new NightSchedule(new int[]{1380,1380,1380,1380,1380,1380,1380},240,90,null);
        check(evening.window(time("2026-10-25T23:30:00+01:00")).active, "Evening cutoff supported");
        NightGate gate = new NightGate();
        check(!gate.update("day",false,NightGate.Playback.IDLE,0), "Daytime free");
        check(gate.update("night",true,NightGate.Playback.IDLE,1), "Idle at cutoff locks");
        check(!gate.update("night",true,NightGate.Playback.ACTIVE,2), "Video playing allows navigation");
        check(!gate.update("night",true,NightGate.Playback.ACTIVE,10000), "Pause/buffering remains active");
        check(!gate.update("night",true,NightGate.Playback.IDLE,11000), "Brief handoff grace");
        check(gate.update("night",true,NightGate.Playback.IDLE,14000), "End of video locks");
        check(gate.unlock(), "Close remote unlocks");
        check(!gate.update("night",true,NightGate.Playback.IDLE,15000), "Browsing remains unlocked without time limit");
        check(!gate.update("night",true,NightGate.Playback.IDLE,1000000), "Long browsing stays unlocked");
        check(!gate.update("night",true,NightGate.Playback.ACTIVE,1001000), "Next video begins");
        check(!gate.update("night",true,NightGate.Playback.IDLE,1002000), "Next end grace");
        check(gate.update("night",true,NightGate.Playback.IDLE,1005000), "Unlock expires at next video end");
        check(!gate.update("night",true,NightGate.Playback.UNKNOWN,1006000), "Missing media permission fails open");
        check(gate.update("night",true,NightGate.Playback.IDLE,1007000), "Monitoring recovered");
        check(!gate.update("night",false,NightGate.Playback.IDLE,1008000), "Morning unlocks");
        check(gate.update("next-night",true,NightGate.Playback.IDLE,2000000), "No stale next-night unlock");
        ProximityGate proximity = new ProximityGate();
        check(!proximity.sample(-70,-40,0), "Far remote does not unlock");
        check(!proximity.sample(-20,-40,1000), "Single strong spike does not unlock");
        check(!proximity.sample(-70,-40,2000), "Spike rejected");
        check(!proximity.sample(-20,-40,3000), "Approach begins");
        check(!proximity.sample(-20,-40,4000), "Hold continues");
        check(proximity.sample(-20,-40,5000), "Sustained approach unlocks");
        check(!proximity.sample(-20,-40,6000), "Holding close cannot unlock repeatedly");
        proximity.sample(-90,-40,7000);
        proximity.sample(-90,-40,8000);
        proximity.sample(-20,-40,9000);
        proximity.sample(-20,-40,10000);
        proximity.sample(-20,-40,11000);
        check(proximity.sample(-20,-40,12000), "Move away and approach can unlock again");
        proximity.reset();
        check(!proximity.sample(127,-40,0), "Invalid RSSI rejected");
        check(!proximity.sample(-20,-40,1), "Fresh sample begins hold");
        check(!proximity.sample(-20,-40,10000), "Stale gap cannot complete a hold");
        AudioFallback audio = new AudioFallback();
        check(!audio.active(false, 0), "No phantom legacy playback");
        check(audio.active(true, 1), "Legacy movie audio permits navigation");
        audio.pauseIntent(1000, true);
        check(audio.active(false, 1100), "Physical pause preserved without media session");
        check(audio.active(false, 100000), "Paused video remains usable");
        check(audio.active(true, 101000), "Resume clears legacy pause");
        check(!audio.active(false, 102000), "Natural completion does not masquerade as pause");
        audio.pauseIntent(103000, true);
        audio.active(false, 103100);
        audio.leave();
        check(!audio.active(false, 104000), "Home or stop clears pause hold");
        audio.pauseIntent(105000, true);
        audio.active(false, 105100);
        audio.back(106000); audio.back(107000);
        check(!audio.active(false, 107100), "Back twice exits legacy pause");
        System.out.println("PASS: " + checks + " schedule, DST, holiday, playback, grant and proximity checks");
    }
}
