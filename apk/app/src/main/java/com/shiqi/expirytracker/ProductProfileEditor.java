package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;

final class ProductProfileEditor {
    private ProductProfileEditor() {
    }

    static List<FoodItem> applyEdit(
            List<FoodItem> foods,
            FoodItem original,
            FoodItem saved
    ) {
        List<FoodItem> result = copyFoods(foods);
        String profileId = original == null
                ? ""
                : FoodItem.cleanText(original.productProfileId);
        if (saved == null) {
            return result;
        }
        if (profileId.length() > 0) {
            saved.productProfileId = profileId;
        }
        boolean sharedIdentityChanged = original != null
                && ReplenishmentDraftFactory.hasReusableIdentityFieldsChanged(original, saved);
        for (int index = 0; index < result.size(); index++) {
            FoodItem current = result.get(index);
            if (current.id.equals(saved.id)) {
                result.set(index, saved.copy());
            } else if (sharedIdentityChanged
                    && profileId.equals(FoodItem.cleanText(current.productProfileId))) {
                applySharedIdentity(current, saved);
            }
        }
        return result;
    }

    private static void applySharedIdentity(FoodItem target, FoodItem source) {
        target.name = FoodItem.cleanText(source.name);
        target.category = FoodData.normalizeCategoryValue(source.category);
        target.unit = FoodItem.cleanText(source.unit).length() == 0 ? "\u4ef6" : source.unit;
        target.updatedAt = source.updatedAt;
    }

    private static List<FoodItem> copyFoods(List<FoodItem> source) {
        List<FoodItem> result = new ArrayList<FoodItem>();
        if (source != null) {
            for (FoodItem item : source) {
                if (item != null) {
                    result.add(item.copy());
                }
            }
        }
        return result;
    }
}
