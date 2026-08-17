package com.waldo.modow;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.time.LocalDate;

public class NotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long habitId = intent.getLongExtra("habit_id", -1);
        if (habitId < 0) return;

        AppDb db = new AppDb(context.getApplicationContext());
        AppDb.Habit h = db.habit(habitId);
        if (h == null || !h.active() || !h.notifyEnabled()) {
            AlarmScheduler.cancelHabit(context, habitId);
            db.close();
            return;
        }

        if (!h.dueOn(LocalDate.now())) {
            AlarmScheduler.scheduleHabit(context, h);
            db.close();
            return;
        }

        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            showNotification(context, h);
        }

        AlarmScheduler.scheduleHabit(context, h);
        db.close();
    }

    private void showNotification(Context context, AppDb.Habit h) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri sound = h.soundUri() == null || h.soundUri().isBlank()
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                : Uri.parse(h.soundUri());

        String channelId = "habit_" + h.id() + "_" + Integer.toHexString(String.valueOf(sound).hashCode());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Modo W · " + h.name(), NotificationManager.IMPORTANCE_HIGH);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(sound, attrs);
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channelId)
                : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.notification_icon)
                .setContentTitle("Modo W")
                .setContentText("Es hora de: " + h.name())
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) b.setSound(sound);

        nm.notify((int) (h.id() & 0x7fffffff), b.build());
    }
}
