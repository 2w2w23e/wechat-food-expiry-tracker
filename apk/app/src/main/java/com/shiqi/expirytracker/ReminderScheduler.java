package com.shiqi.expirytracker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

final class ReminderScheduler {
    static final String ACTION_DAILY_BRIEFING = "com.shiqi.expirytracker.action.DAILY_BRIEFING";
    static final String ACTION_DUE_DAY_REMINDER = "com.shiqi.expirytracker.action.DUE_DAY_REMINDER";
    static final String EXTRA_DUE_DAY_HOUR = "due_day_hour";
    static final String CHANNEL_ID = "food_expiry_reminders";

    private static final String ACTION_LEGACY_DAILY_REMINDER = "com.shiqi.expirytracker.action.DAILY_REMINDER";
    private static final String PREFS_NAME = "shiqi_android_v0";
    private static final String PREF_REMINDER_ENABLED = "reminder_enabled_v0";
    private static final String PREF_REMINDER_MODE = "reminder_mode_v1";
    private static final String PREF_REMINDER_ADVANCE_DAYS = "reminder_advance_days_v0";
    private static final String PREF_REMINDER_TODAY_SLOTS = "reminder_today_slots_v0";
    private static final int REQUEST_CODE_DAILY_BRIEFING = 7001;
    private static final int REQUEST_CODE_DUE_DAY_BASE = 7100;
    private static final int MAX_DUE_DAY_SLOT_REQUESTS = 12;
    private static final int BRIEFING_HOUR = 8;
    private static final int BRIEFING_MINUTE = 30;
    private static final String[] DEFAULT_DUE_DAY_SLOTS = new String[] { "08:30", "09:00", "12:30", "18:00", "18:30" };
    private static ReminderSettings activeSettings = ReminderSettings.defaults();

    private ReminderScheduler() {}

    static void scheduleDaily(Context context) {
        scheduleDaily(context, loadSettings(context));
    }

    static void scheduleDaily(Context context, ReminderSettings settings) {
        Context appContext = context.getApplicationContext();
        ReminderSettings effectiveSettings = ReminderSettings.validOrDefault(settings);
        activeSettings = effectiveSettings;
        ReminderPolicy.useSettings(effectiveSettings);

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        cancelLegacyDaily(appContext, alarmManager);
        cancelCurrentSchedules(appContext, alarmManager);
        if (!effectiveSettings.enabled) {
            return;
        }

        ensureNotificationChannel(appContext);

        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextTimeMillis(BRIEFING_HOUR, BRIEFING_MINUTE),
                AlarmManager.INTERVAL_DAY,
                briefingPendingIntent(appContext)
        );

        String[] slots = dueDaySlots(effectiveSettings);
        int count = Math.min(slots.length, MAX_DUE_DAY_SLOT_REQUESTS);
        for (int index = 0; index < count; index++) {
            String slot = slots[index];
            int[] parts = timeParts(slot);
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    nextTimeMillis(parts[0], parts[1]),
                    AlarmManager.INTERVAL_DAY,
                    dueDayPendingIntent(appContext, slot, index)
            );
        }
    }

    static ReminderSettings loadSettings(Context context) {
        if (context == null) {
            return ReminderSettings.defaults();
        }

        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = preferences.getBoolean(PREF_REMINDER_ENABLED, ReminderSettings.DEFAULT_ENABLED);
        String advanceDays = preferences.getString(PREF_REMINDER_ADVANCE_DAYS, ReminderSettings.DEFAULT_ADVANCE_DAYS_TEXT);
        String todaySlots = preferences.getString(PREF_REMINDER_TODAY_SLOTS, ReminderSettings.DEFAULT_TODAY_SLOTS_TEXT);
        if (!preferences.contains(PREF_REMINDER_MODE)) {
            return ReminderSettings.fromStoredValues(enabled, advanceDays, todaySlots);
        }
        return ReminderSettings.fromStoredValues(
                enabled,
                preferences.getString(PREF_REMINDER_MODE, ReminderSettings.DEFAULT_MODE),
                advanceDays,
                todaySlots
        );
    }

    static void saveSettings(Context context, ReminderSettings settings) {
        ReminderSettings effectiveSettings = ReminderSettings.validOrDefault(settings);
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit()
                .putBoolean(PREF_REMINDER_ENABLED, effectiveSettings.enabled)
                .putString(PREF_REMINDER_MODE, effectiveSettings.mode)
                .putString(PREF_REMINDER_ADVANCE_DAYS, effectiveSettings.advanceDaysText())
                .putString(PREF_REMINDER_TODAY_SLOTS, effectiveSettings.todaySlotsText())
                .apply();
        scheduleDaily(context, effectiveSettings);
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
        return reminderTimeLabel(activeSettings);
    }

    static String reminderTimeLabel(ReminderSettings settings) {
        ReminderSettings effectiveSettings = ReminderSettings.validOrDefault(settings);
        if (!effectiveSettings.enabled) {
            return "全局提醒已关闭";
        }

        String advanceText = effectiveSettings.isSmartMode()
                ? "智能安排提醒日"
                : "固定提前 " + effectiveSettings.advanceDaysText() + " 天";
        String todayText = effectiveSettings.usesDefaultTodaySlots()
                ? "今日到期按风险时段 " + joinArray(DEFAULT_DUE_DAY_SLOTS)
                : "今日到期按 " + effectiveSettings.todaySlotsText();
        return "每天 08:30 简报；" + todayText + " 提醒；" + advanceText;
    }

    static String[] dueDaySlots() {
        return dueDaySlots(activeSettings);
    }

    static String[] dueDaySlots(ReminderSettings settings) {
        ReminderSettings effectiveSettings = ReminderSettings.validOrDefault(settings);
        if (effectiveSettings.usesDefaultTodaySlots()) {
            return DEFAULT_DUE_DAY_SLOTS.clone();
        }

        List<String> slots = effectiveSettings.todayReminderSlots();
        return slots.toArray(new String[slots.size()]);
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

    private static void cancelCurrentSchedules(Context context, AlarmManager alarmManager) {
        PendingIntent briefing = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_DAILY_BRIEFING,
                new Intent(context, ReminderReceiver.class).setAction(ACTION_DAILY_BRIEFING),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (briefing != null) {
            alarmManager.cancel(briefing);
            briefing.cancel();
        }

        for (int index = 0; index < MAX_DUE_DAY_SLOT_REQUESTS; index++) {
            PendingIntent dueDay = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_DUE_DAY_BASE + index,
                    new Intent(context, ReminderReceiver.class).setAction(ACTION_DUE_DAY_REMINDER),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (dueDay != null) {
                alarmManager.cancel(dueDay);
                dueDay.cancel();
            }
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

    private static String joinArray(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append('、');
            }
            builder.append(values[index]);
        }
        return builder.toString();
    }
}
