package com.shiqi.expirytracker;

import android.content.Context;

import java.util.List;

final class FoodStoreInventoryRepository implements InventoryRepository {
    private final FoodStore store;

    FoodStoreInventoryRepository(Context context) {
        store = new FoodStore(context);
    }

    @Override
    public List<FoodItem> loadFoods() {
        return store.loadFoods();
    }

    @Override
    public boolean saveFoods(List<FoodItem> foods) {
        return store.saveFoods(foods);
    }
}
