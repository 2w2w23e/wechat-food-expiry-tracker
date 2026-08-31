package com.shiqi.expirytracker;

import java.util.List;

final class InventoryRules {
    private InventoryRules() {
    }

    static boolean isFinished(FoodItem food) {
        return food != null && (food.isFinished || food.remainingQuantity <= 0d);
    }

    static boolean finishIfDepleted(FoodItem food, String now) {
        if (food == null) {
            return false;
        }
        food.normalizeQuantityBounds();
        if (food.remainingQuantity > 0d || food.isFinished) {
            return false;
        }
        String timestamp = FoodItem.cleanText(now);
        food.isFinished = true;
        food.finishedAt = timestamp;
        food.updatedAt = timestamp.length() == 0 ? food.updatedAt : timestamp;
        food.smartReminderOffsets.clear();
        food.smartReminderFingerprint = "";
        food.smartReminderPlannedDaysLeft = Integer.MIN_VALUE;
        food.smartReminderPlannedOn = "";
        return true;
    }

    static boolean finishDepletedBatches(List<FoodItem> foods, String now) {
        boolean changed = false;
        if (foods == null) {
            return false;
        }
        for (FoodItem food : foods) {
            changed = finishIfDepleted(food, now) || changed;
        }
        return changed;
    }

    static void restoreToActive(FoodItem food) {
        if (food == null) {
            return;
        }
        food.isFinished = false;
        food.finishedAt = "";
        if (food.quantity <= 0d) {
            food.quantity = 1d;
        }
        if (food.remainingQuantity <= 0d) {
            food.remainingQuantity = Math.min(1d, food.quantity);
        }
        food.normalizeQuantityBounds();
    }
}
