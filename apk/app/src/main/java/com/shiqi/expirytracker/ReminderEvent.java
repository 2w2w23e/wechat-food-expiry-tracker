package com.shiqi.expirytracker;

final class ReminderEvent {
    final int offsetDays;
    final String reminderDate;
    final String expiryDate;
    final boolean postExpiry;
    final String label;

    ReminderEvent(int offsetDays, String reminderDate, String expiryDate, boolean postExpiry, String label) {
        this.offsetDays = offsetDays;
        this.reminderDate = reminderDate;
        this.expiryDate = expiryDate;
        this.postExpiry = postExpiry;
        this.label = label;
    }
}
