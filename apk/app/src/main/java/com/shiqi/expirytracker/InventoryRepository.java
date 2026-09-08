package com.shiqi.expirytracker;

import java.util.List;

interface InventoryRepository {
    List<FoodItem> loadFoods();

    boolean saveFoods(List<FoodItem> foods);
}
