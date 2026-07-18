package com.shiqi.expirytracker;

final class ReplenishmentDraftFactory {
    private ReplenishmentDraftFactory() {
    }

    static FoodItem createDraft(FoodItem source) {
        FoodItem draft = new FoodItem();
        draft.quantity = 0;
        draft.remainingQuantity = 0;
        if (source == null) {
            return draft;
        }

        draft.productProfileId = FoodItem.cleanText(source.productProfileId);
        draft.barcode = FoodItem.cleanText(source.barcode);
        draft.name = FoodItem.cleanText(source.name);
        draft.category = FoodData.normalizeCategoryValue(source.category);
        draft.unit = cleanOrDefault(source.unit, "件");
        draft.storageMethod = reasonableStorageMethod(source.storageMethod);
        draft.location = FoodData.normalizeLocationValue(source.location);
        return draft;
    }

    static boolean hasReusableIdentityChanged(FoodItem original, FoodItem updated) {
        if (original == null || updated == null) {
            return true;
        }
        return hasReusableIdentityFieldsChanged(original, updated)
                || !sameText(
                        BarcodeUtils.toGtin14(original.barcode),
                        BarcodeUtils.toGtin14(updated.barcode)
                );
    }

    static boolean hasReusableIdentityFieldsChanged(FoodItem original, FoodItem updated) {
        if (original == null || updated == null) {
            return true;
        }
        return !sameText(original.name, updated.name)
                || !sameText(original.category, updated.category)
                || !sameText(original.unit, updated.unit);
    }

    static String resolveProductProfileId(FoodItem source, FoodItem saved, String generatedProfileId) {
        String sourceProfileId = source == null ? "" : FoodItem.cleanText(source.productProfileId);
        if (sourceProfileId.length() > 0 && !hasReusableIdentityChanged(source, saved)) {
            return sourceProfileId;
        }
        return FoodItem.cleanText(generatedProfileId);
    }

    private static boolean sameText(String left, String right) {
        return FoodItem.cleanText(left).equals(FoodItem.cleanText(right));
    }

    private static String reasonableStorageMethod(String value) {
        String normalized = FoodItem.cleanText(value);
        for (Option option : FoodData.STORAGE_METHODS) {
            if (option.value.equals(normalized)) {
                return normalized;
            }
        }
        return "room_temp";
    }

    private static String cleanOrDefault(String value, String fallback) {
        String cleaned = FoodItem.cleanText(value);
        return cleaned.length() > 0 ? cleaned : fallback;
    }
}
