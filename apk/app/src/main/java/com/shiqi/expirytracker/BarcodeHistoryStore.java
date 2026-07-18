package com.shiqi.expirytracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class BarcodeHistoryStore {
    private static final String PREFS_NAME = "shiqi_barcode_history_v0";
    static final String STORAGE_KEY = "barcode_history_templates_v0";
    private final SharedPreferences preferences;

    BarcodeHistoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    List<BarcodeHistoryItem> loadHistory() {
        String raw = preferences.getString(STORAGE_KEY, null);
        return BarcodeHistoryItem.parseListOrEmpty(raw);
    }

    BarcodeHistoryItem findByBarcode(String barcode) {
        List<BarcodeHistoryItem> matches = findAllByBarcode(barcode);
        return matches.isEmpty() ? null : matches.get(0);
    }

    List<BarcodeHistoryItem> findAllByBarcode(String barcode) {
        String normalizedBarcode = BarcodeUtils.digitsOnly(barcode);
        List<BarcodeHistoryItem> matches = new ArrayList<BarcodeHistoryItem>();
        if (normalizedBarcode.length() == 0) {
            return matches;
        }

        List<BarcodeHistoryItem> items = loadHistory();
        for (BarcodeHistoryItem item : items) {
            if (normalizedBarcode.equals(item.barcode)) {
                matches.add(item.copy());
            }
        }
        return matches;
    }

    void saveConfirmedDraft(BarcodeHistoryItem draft) {
        // This cache stores reusable barcode draft templates only after user confirmation.
        // It must never create or modify FoodItem records automatically.
        String updatedAt = DateRules.nowIsoLike();
        BarcodeHistoryItem confirmed = draft == null ? null : draft.normalizedForStorage(updatedAt);
        if (confirmed == null || !confirmed.isReusableTemplate()) {
            return;
        }

        List<BarcodeHistoryItem> next = BarcodeHistoryItem.upsertConfirmedTemplate(
                loadHistory(),
                confirmed,
                updatedAt
        );
        preferences.edit()
                .putString(STORAGE_KEY, BarcodeHistoryItem.serializeList(next))
                .apply();
    }

    void saveConfirmedDraft(String barcode, String name, String category, String unit, String notes) {
        saveConfirmedDraft("", barcode, name, category, unit, notes);
    }

    void saveConfirmedDraft(
            String productProfileId,
            String barcode,
            String name,
            String category,
            String unit,
            String notes
    ) {
        BarcodeHistoryItem draft = new BarcodeHistoryItem();
        draft.productProfileId = productProfileId;
        draft.barcode = barcode;
        draft.name = name;
        draft.category = category;
        draft.unit = unit;
        draft.notes = notes;
        saveConfirmedDraft(draft);
    }
}
