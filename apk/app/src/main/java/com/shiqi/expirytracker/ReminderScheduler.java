package com.shiqi.expirytracker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;

final class ReminderScheduler {
    static final String ACTION_DAILY_BRIEFING = "com.shiqi.expirytracker.action.DAILY_BRIEFING";
    static final String ACTION_DUE_DAY_REMINDER = "com.shiqi.expirytracker.action.DUE_DAY_REMINDER";
    static final String EXTRA_DUE_DAY_HOUR = "due_day_hour";
    static final String CHANNEL_ID = "food_expiry_reminders";

    private static final String ACTION_LEGACY_DAILY_REMINDER = "com.shiqi.expirytracker.action.DAILY_REMINDER";
    private static final int REQUEST_CODE_DAILY_BRIEFING = 7001;
    private static final int REQUEST_CODE_DUE_DAY_BASE = 7100;
    private static final int BRIEFING_HOUR = 8;
    private static final int BRIEFING_MINUTE = 30;
    private static final String[] DUE_DAY_SLOTS = new String[] { "08:30", "09:00", "12:30", "18:00", "18:30" };

    private ReminderScheduler() {}

    static void scheduleDaily(Context context) {
        Context appContext = context.getApplicationContext();
        ensureNotificationChannel(appContext);

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        cancelLegacyDaily(appContext, alarmManager);

        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextTimeMillis(BRIEFING_HOUR, BRIEFING_MINUTE),
                AlarmManager.INTERVAL_DAY,
                briefingPendingIntent(appContext)
        );

        for (int index = 0; index < DUE_DAY_SLOTS.length; index++) {
            String slot = DUE_DAY_SLOTS[index];
            int[] parts = timeParts(slot);
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    nextTimeMillis(parts[0], parts[1]),
                    AlarmManager.INTERVAL_DAY,
                    dueDayPendingIntent(appContext, slot, index)
            );
        }
    }

    static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "食品到期提醒",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("每天发送食期简报，并按今日到期计划提醒查看食品");
        manager.createNotificationChannel(channel);
    }

    static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= 24) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager == null || manager.areNotificationsEnabled();
        }

        return true;
    }

    static String reminderTimeLabel() {
        return "每天 08:30 简报；今日到期按 08:30、09:00、12:30、18:00、18:30 提醒";
    }

    static String[] dueDaySlots() {
        return DUE_DAY_SLOTS.clone();
    }

    private static PendingIntent briefingPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_DAILY_BRIEFING);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_DAILY_BRIEFING,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent dueDayPendingIntent(Context context, String hour, int index) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_DUE_DAY_REMINDER);
        intent.putExtra(EXTRA_DUE_DAY_HOUR, hour);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_DUE_DAY_BASE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void cancelLegacyDaily(Context context, AlarmManager alarmManager) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_LEGACY_DAILY_REMINDER);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_DAILY_BRIEFING,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private static long nextTimeMillis(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        return calendar.getTimeInMillis();
    }

    private static int[] timeParts(String value) {
        return new int[] {
                Integer.parseInt(value.substring(0, 2)),
                Integer.parseInt(value.substring(3, 5))
        };
    }
}
