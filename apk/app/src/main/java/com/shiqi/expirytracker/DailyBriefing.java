package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;

final class DailyBriefing {
    static final String BRIEFING_TIME = "08:30";

    final List<Entry> yesterdayExpired = new ArrayList<Entry>();
    final List<Entry> todayDue = new ArrayList<Entry>();
    final List<Entry> upcoming = new ArrayList<Entry>();

    boolean isEmpty() {
        return yesterdayExpired.isEmpty() && todayDue.isEmpty() && upcoming.isEmpty();
    }

    static final class Entry {
        final FoodItem food;
        final ReminderPlan plan;
        final String text;

        Entry(FoodItem food, ReminderPlan plan, String text) {
            this.food = food;
            this.plan = plan;
            this.text = text;
        }
    }
}
