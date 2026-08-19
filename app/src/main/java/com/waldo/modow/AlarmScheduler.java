package com.waldo.modow;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class AlarmScheduler {
    private AlarmScheduler() {}

    public static void scheduleAll(Context context) {
        AppDb db = new AppDb(context.getApplicationContext());
        for (AppDb.Habit h : db.habits(false)) {
            if (h.active() && h.notifyEnabled()) scheduleHabit(context, h);
            else cancelHabit(context, h.id());
        }
        db.close();
    }

    public static void scheduleHabit(Context context, AppDb.Habit h) {
        cancelHabit(context, h.id());
        if (!h.active() || !h.notifyEnabled()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = null;

        for (int offset = 0; offset <= 8; offset++) {
            LocalDate candidateDay = now.toLocalDate().plusDays(offset);
            if (!h.dueOn(candidateDay)) continue;
            LocalDateTime candidate = candidateDay.atTime(h.notifyHour(), h.notifyMinute());
            if (candidate.isAfter(now)) {
                target = candidate;
                break;
            }
        }

        if (target == null) return;

        long triggerAt = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pendingIntent(context, h.id());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    public static void cancelHabit(Context context, long habitId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent(context, habitId));
    }

    private static PendingIntent pendingIntent(Context context, long habitId) {
        Intent i = new Intent(context, NotificationReceiver.class);
        i.setAction("com.waldo.modow.NOTIFY_" + habitId);
        i.putExtra("habit_id", habitId);
        int requestCode = (int) (habitId & 0x7fffffff);
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
