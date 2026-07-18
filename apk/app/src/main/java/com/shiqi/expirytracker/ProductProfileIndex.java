package com.shiqi.expirytracker;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ProductProfileIndex {
    private final Map<String, List<FoodItem>> profilesByBarcode;

    private ProductProfileIndex(Map<String, List<FoodItem>> profilesByBarcode) {
        this.profilesByBarcode = profilesByBarcode;
    }

    static ProductProfileIndex fromFoods(List<FoodItem> foods) {
        Map<String, LinkedHashMap<String, FoodItem>> grouped =
                new LinkedHashMap<String, LinkedHashMap<String, FoodItem>>();
        if (foods != null) {
            for (int index = 0; index < foods.size(); index++) {
                FoodItem item = foods.get(index);
                if (item == null) {
                    continue;
                }

                String barcode = BarcodeUtils.toGtin14(item.barcode);
                if (!BarcodeUtils.isSupportedProductCode(barcode)) {
                    continue;
                }

                LinkedHashMap<String, FoodItem> profiles = grouped.get(barcode);
                if (profiles == null) {
                    profiles = new LinkedHashMap<String, FoodItem>();
                    grouped.put(barcode, profiles);
                }
                String profileKey = FoodItem.cleanText(item.productProfileId);
                if (profileKey.length() == 0) {
                    profileKey = "batch:" + FoodItem.cleanText(item.id) + ":" + index;
                }
                String equivalentKey = findEquivalentProfileKey(profiles, item);
                if (equivalentKey.length() > 0) {
                    profileKey = equivalentKey;
                }
                FoodItem representative = profiles.get(profileKey);
                if (representative == null || isNewerBatch(item, representative)) {
                    profiles.put(profileKey, item.copy());
                }
            }
        }

        Map<String, List<FoodItem>> index = new LinkedHashMap<String, List<FoodItem>>();
        for (Map.Entry<String, LinkedHashMap<String, FoodItem>> entry : grouped.entrySet()) {
            index.put(entry.getKey(), new ArrayList<FoodItem>(entry.getValue().values()));
        }
        return new ProductProfileIndex(index);
    }

    private static String findEquivalentProfileKey(
            LinkedHashMap<String, FoodItem> profiles,
            FoodItem candidate
    ) {
        for (Map.Entry<String, FoodItem> entry : profiles.entrySet()) {
            if (!ReplenishmentDraftFactory.hasReusableIdentityFieldsChanged(
                    entry.getValue(),
                    candidate
            )) {
                return entry.getKey();
            }
        }
        return "";
    }

    List<FoodItem> findByBarcode(String barcode) {
        String normalized = BarcodeUtils.toGtin14(barcode);
        if (!BarcodeUtils.isSupportedProductCode(normalized)) {
            return new ArrayList<FoodItem>();
        }

        List<FoodItem> stored = profilesByBarcode.get(normalized);
        List<FoodItem> result = new ArrayList<FoodItem>();
        if (stored != null) {
            for (FoodItem item : stored) {
                result.add(item.copy());
            }
        }
        return result;
    }

    private static boolean isNewerBatch(FoodItem candidate, FoodItem current) {
        String candidateTimestamp = effectiveTimestamp(candidate);
        String currentTimestamp = effectiveTimestamp(current);
        Long candidateMillis = parseTimestamp(candidateTimestamp);
        Long currentMillis = parseTimestamp(currentTimestamp);
        if (candidateMillis != null && currentMillis != null) {
            return candidateMillis.longValue() > currentMillis.longValue();
        }
        if (candidateMillis != null) {
            return true;
        }
        if (currentMillis != null) {
            return false;
        }
        return candidateTimestamp.compareTo(currentTimestamp) > 0;
    }

    private static String effectiveTimestamp(FoodItem item) {
        String updatedAt = item == null ? "" : FoodItem.cleanText(item.updatedAt);
        if (updatedAt.length() > 0) {
            return updatedAt;
        }
        return item == null ? "" : FoodItem.cleanText(item.createdAt);
    }

    private static Long parseTimestamp(String value) {
        String normalized = normalizeOffset(value);
        String[] patterns = new String[] {
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(normalized, position);
            if (parsed != null && position.getIndex() == normalized.length()) {
                return Long.valueOf(parsed.getTime());
            }
        }
        return null;
    }

    private static String normalizeOffset(String value) {
        String normalized = FoodItem.cleanText(value);
        int length = normalized.length();
        if (length >= 6 && normalized.charAt(length - 3) == ':'
                && (normalized.charAt(length - 6) == '+' || normalized.charAt(length - 6) == '-')) {
            return normalized.substring(0, length - 3) + normalized.substring(length - 2);
        }
        return normalized;
    }

}
