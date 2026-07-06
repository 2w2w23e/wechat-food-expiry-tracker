package com.shiqi.expirytracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID_DAILY_BRIEFING = 8001;
    private static final int NOTIFICATION_ID_DUE_DAY_BASE = 8100;
    private static final int DISPLAY_LIMIT = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ReminderScheduler.scheduleDaily(context);
            return;
        }

        ReminderScheduler.scheduleDaily(context);

        if (!ReminderScheduler.canPostNotifications(context)) {
            return;
        }

        String action = intent == null ? "" : FoodItem.cleanText(intent.getAction());
        ReminderContent content;
        if (ReminderScheduler.ACTION_DUE_DAY_REMINDER.equals(action)) {
            String hour = intent.getStringExtra(ReminderScheduler.EXTRA_DUE_DAY_HOUR);
            content = buildDueDayContent(new FoodStore(context).loadFoods(), hour);
        } else {
            content = buildDailyBriefingContent(new FoodStore(context).loadFoods());
        }

        if (!content.shouldNotify) {
            return;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        ReminderScheduler.ensureNotificationChannel(context);
        manager.notify(content.notificationId, buildNotification(context, content));
    }

    private Notification buildNotification(Context context, ReminderContent content) {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_stat_food_reminder)
                .setContentTitle(content.title)
                .setContentText(content.summary)
                .setStyle(new Notification.BigTextStyle().bigText(content.detail))
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setColor(Color.rgb(63, 111, 83));

        if (Build.VERSION.SDK_INT >= 21) {
            builder.setCategory(Notification.CATEGORY_REMINDER);
        } else {
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        return builder.build();
    }

    private static ReminderContent buildDailyBriefingContent(List<FoodItem> foods) {
        DailyBriefing briefing = ReminderPolicy.dailyBriefing(foods);
        ReminderContent content = new ReminderContent();
        content.shouldNotify = true;
        content.notificationId = NOTIFICATION_ID_DAILY_BRIEFING;
        content.title = "今日食期简报 · " + DailyBriefing.BRIEFING_TIME;

        if (briefing.isEmpty()) {
            content.summary = "今天没有需要特别处理的食品";
            content.detail = content.summary;
            return content;
        }

        List<String> summaryParts = new ArrayList<String>();
        if (!briefing.yesterdayExpired.isEmpty()) {
            summaryParts.add("昨日过期 " + briefing.yesterdayExpired.size() + " 件");
        }
        if (!briefing.todayDue.isEmpty()) {
            summaryParts.add("今日到期 " + briefing.todayDue.size() + " 件");
        }
        if (!briefing.upcoming.isEmpty()) {
            summaryParts.add("临近 " + briefing.upcoming.size() + " 件");
        }

        List<String> detailLines = new ArrayList<String>();
        addSectionLine(detailLines, "昨日过期", briefing.yesterdayExpired);
        addSectionLine(detailLines, "今日到期", briefing.todayDue);
        addSectionLine(detailLines, "临近保质期", briefing.upcoming);

        content.summary = join(summaryParts, " · ");
        content.detail = join(detailLines, "\n");
        return content;
    }

    private static ReminderContent buildDueDayContent(List<FoodItem> foods, String hour) {
        String slot = FoodItem.cleanText(hour);
        ReminderContent content = new ReminderContent();
        content.notificationId = NOTIFICATION_ID_DUE_DAY_BASE + dueDaySlotIndex(slot);
        content.title = "今日到期提醒 · " + slot;

        if (slot.length() == 0 || foods == null) {
            return content;
        }

        List<DailyBriefing.Entry> dueFoods = new ArrayList<DailyBriefing.Entry>();
        for (FoodItem food : foods) {
            if (food == null || food.isFinished || !DateRules.isToday(food.expiryDate)) {
                continue;
            }

            ReminderPlan plan = ReminderPolicy.planFor(food);
            if (plan.enabled && plan.dueDayHours.contains(slot)) {
                dueFoods.add(new DailyBriefing.Entry(food, plan, dueDayLine(food, plan)));
            }
        }

        sortEntries(dueFoods);
        if (dueFoods.isEmpty()) {
            return content;
        }

        content.shouldNotify = true;
        content.summary = limitedEntryText(dueFoods);
        content.detail = limitedEntryText(dueFoods) + "\n请优先查看，需要时及时处理。";
        return content;
    }

    private static void addSectionLine(List<String> lines, String title, List<DailyBriefing.Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        lines.add(title + "：" + limitedEntryText(entries));
    }

    private static String dueDayLine(FoodItem food, ReminderPlan plan) {
        if ("frozen".equals(food.storageMethod)) {
            return food.name + "：建议检查品质并安排食用";
        }
        return food.name + "：" + plan.nextReminderSummary;
    }

    private static String limitedEntryText(List<DailyBriefing.Entry> entries) {
        List<String> values = new ArrayList<String>();
        int limit = Math.min(DISPLAY_LIMIT, entries.size());
        for (int index = 0; index < limit; index++) {
            values.add(entries.get(index).text);
        }

        String text = join(values, "、");
        if (entries.size() > limit) {
            text += " 等 " + entries.size() + " 件";
        }
        return text;
    }

    private static void sortEntries(List<DailyBriefing.Entry> entries) {
        for (int index = 1; index < entries.size(); index++) {
            DailyBriefing.Entry current = entries.get(index);
            int cursor = index - 1;
            while (cursor >= 0 && compareEntries(entries.get(cursor), current) > 0) {
                entries.set(cursor + 1, entries.get(cursor));
                cursor--;
            }
            entries.set(cursor + 1, current);
        }
    }

    private static int compareEntries(DailyBriefing.Entry left, DailyBriefing.Entry right) {
        if (left.plan.priorityScore < right.plan.priorityScore) {
            return 1;
        }
        if (left.plan.priorityScore > right.plan.priorityScore) {
            return -1;
        }
        return left.food.expiryDate.compareTo(right.food.expiryDate);
    }

    private static int dueDaySlotIndex(String slot) {
        String[] slots = ReminderScheduler.dueDaySlots();
        for (int index = 0; index < slots.length; index++) {
            if (slots[index].equals(slot)) {
                return index;
            }
        }
        return 99;
    }

    private static String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(separator);
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private static final class ReminderContent {
        boolean shouldNotify = false;
        int notificationId = NOTIFICATION_ID_DAILY_BRIEFING;
        String title = "";
        String summary = "";
        String detail = "";
    }
}
