package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;

final class ReminderPlan {
    boolean enabled = false;
    String disabledReason = "";
    String riskLevel = "B";
    String riskLabel = "中风险";
    String riskReason = "";
    int totalShelfLifeDays = 1;
    int daysLeft = Integer.MIN_VALUE;
    double priorityScore = 0;
    String priorityBand = "普通";
    String nextReminderSummary = "";
    String cardHint = "";
    String detailAdvice = "";
    final List<Integer> offsets = new ArrayList<Integer>();
    final List<ReminderEvent> events = new ArrayList<ReminderEvent>();
    final List<String> dueDayHours = new ArrayList<String>();

    boolean hasReminderInNextDays(int days) {
        String today = DateRules.todayString();
        for (ReminderEvent event : events) {
            if (event.postExpiry) {
                continue;
            }
            int daysToReminder = DateRules.daysBetween(today, event.reminderDate);
            if (daysToReminder >= 0 && daysToReminder <= days) {
                return true;
            }
        }
        return false;
    }

    ReminderEvent nextFutureEvent() {
        String today = DateRules.todayString();
        ReminderEvent next = null;
        for (ReminderEvent event : events) {
            if (event.postExpiry) {
                continue;
            }
            int daysToReminder = DateRules.daysBetween(today, event.reminderDate);
            if (daysToReminder < 0) {
                continue;
            }
            if (next == null || event.reminderDate.compareTo(next.reminderDate) < 0) {
                next = event;
            }
        }
        return next;
    }
}
