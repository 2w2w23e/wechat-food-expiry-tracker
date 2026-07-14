package com.shiqi.expirytracker;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class FoodIdGenerator {
    private FoodIdGenerator() {
    }

    static String nextId(List<FoodItem> existingFoods, Set<String> reservedIds) {
        String candidate;
        do {
            candidate = "food_" + UUID.randomUUID().toString();
        } while (contains(existingFoods, candidate)
                || (reservedIds != null && reservedIds.contains(candidate)));
        if (reservedIds != null) {
            reservedIds.add(candidate);
        }
        return candidate;
    }

    private static boolean contains(List<FoodItem> foods, String id) {
        if (foods == null) {
            return false;
        }
        for (FoodItem food : foods) {
            if (food != null && id.equals(food.id)) {
                return true;
            }
        }
        return false;
    }
}
