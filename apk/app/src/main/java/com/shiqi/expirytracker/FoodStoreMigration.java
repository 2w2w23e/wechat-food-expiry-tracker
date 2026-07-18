package com.shiqi.expirytracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FoodStoreMigration {
    private static final int LEGACY_ARRAY_SCHEMA_VERSION = 0;
    private static final int PRODUCT_PROFILE_SCHEMA_VERSION = 2;
    private static final String SCHEMA_VERSION_KEY = "schemaVersion";
    private static final String FOODS_KEY = "foods";
    private static final Pattern EXPLICIT_BARCODE_NOTE = Pattern.compile(
            "(?m)^[\\t ]*条码[\\t ]*：[\\t ]*([0-9]{8,14})[\\t ]*$"
    );

    private FoodStoreMigration() {
    }

    static MigrationResult migrate(String raw, int currentSchemaVersion) throws JSONException {
        String text = raw == null ? "" : raw.trim();
        if (text.length() == 0) {
            return new MigrationResult(new ArrayList<FoodItem>(), false);
        }

        JSONTokener tokener = new JSONTokener(text);
        Object root = tokener.nextValue();
        if (tokener.nextClean() != 0) {
            throw new JSONException("Food store JSON has trailing data.");
        }

        if (root instanceof JSONArray) {
            return new MigrationResult(readFoods((JSONArray) root, currentSchemaVersion).foods, true);
        }

        if (root instanceof JSONObject) {
            return migrateObject((JSONObject) root, currentSchemaVersion);
        }

        throw new JSONException("Unsupported food store JSON root.");
    }

    static StorageLoadResult loadWithBackups(
            String primaryRaw,
            List<String> backupRaws,
            int currentSchemaVersion
    ) {
        String primaryText = cleanRaw(primaryRaw);
        if (primaryText.length() == 0) {
            return StorageLoadResult.loaded(new ArrayList<FoodItem>(), false, false, false);
        }

        try {
            MigrationResult primary = migrate(primaryText, currentSchemaVersion);
            return StorageLoadResult.loaded(primary.foods, primary.needsWriteBack, false, false);
        } catch (JSONException ignored) {
            if (hasFutureSchema(primaryText, currentSchemaVersion)) {
                return StorageLoadResult.failed(true);
            }

            List<String> backups = backupRaws == null ? Collections.<String>emptyList() : backupRaws;
            for (String backupRaw : backups) {
                String backupText = cleanRaw(backupRaw);
                if (backupText.length() == 0) {
                    continue;
                }

                try {
                    MigrationResult backup = migrate(backupText, currentSchemaVersion);
                    return StorageLoadResult.loaded(backup.foods, true, true, false);
                } catch (JSONException ignoredBackup) {
                    // Try the next backup slot.
                }
            }

            return StorageLoadResult.failed(false);
        }
    }

    static String serialize(List<FoodItem> foods, int currentSchemaVersion) throws JSONException {
        JSONObject root = new JSONObject();
        root.put(SCHEMA_VERSION_KEY, currentSchemaVersion);
        root.put(FOODS_KEY, serializeFoods(foods, currentSchemaVersion));
        return root.toString();
    }

    static boolean needsMigration(String raw, int currentSchemaVersion) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() == 0) {
            return false;
        }

        try {
            return migrate(text, currentSchemaVersion).needsWriteBack;
        } catch (JSONException ignored) {
            return false;
        }
    }

    private static String cleanRaw(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static boolean hasFutureSchema(String raw, int currentSchemaVersion) {
        String text = cleanRaw(raw);
        if (text.length() == 0) {
            return false;
        }

        try {
            JSONTokener tokener = new JSONTokener(text);
            Object root = tokener.nextValue();
            if (tokener.nextClean() != 0 || !(root instanceof JSONObject)) {
                return false;
            }

            JSONObject object = (JSONObject) root;
            if (!object.has(SCHEMA_VERSION_KEY)) {
                return false;
            }

            return object.optInt(SCHEMA_VERSION_KEY, LEGACY_ARRAY_SCHEMA_VERSION) > currentSchemaVersion;
        } catch (JSONException ignored) {
            return false;
        }
    }

    private static MigrationResult migrateObject(JSONObject root, int currentSchemaVersion) throws JSONException {
        int schemaVersion = root.has(SCHEMA_VERSION_KEY)
                ? root.optInt(SCHEMA_VERSION_KEY, LEGACY_ARRAY_SCHEMA_VERSION)
                : LEGACY_ARRAY_SCHEMA_VERSION;

        if (schemaVersion > currentSchemaVersion) {
            throw new JSONException("Food store schema is newer than this app version.");
        }

        JSONArray foods = root.optJSONArray(FOODS_KEY);
        if (foods == null) {
            throw new JSONException("Food store object is missing foods array.");
        }

        ReadFoodsResult read = readFoods(foods, currentSchemaVersion);
        return new MigrationResult(read.foods, schemaVersion != currentSchemaVersion || read.repairedRecords);
    }

    private static ReadFoodsResult readFoods(JSONArray array, int currentSchemaVersion) {
        List<FoodItem> foods = new ArrayList<FoodItem>();
        boolean repairedRecords = false;

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) {
                continue;
            }

            String storedDateSource = object.optString("dateSource", "").trim();
            FoodItem item = FoodItem.fromJson(object);
            if ("calculated".equals(storedDateSource) && "manual".equals(item.dateSource)) {
                repairedRecords = true;
            }
            if (currentSchemaVersion >= PRODUCT_PROFILE_SCHEMA_VERSION
                    && ensureStructuredFields(item, index)) {
                repairedRecords = true;
            }
            if (item.id.length() > 0 && item.name.length() > 0) {
                foods.add(item);
            }
        }

        return new ReadFoodsResult(foods, repairedRecords);
    }

    private static JSONArray serializeFoods(List<FoodItem> foods, int currentSchemaVersion) {
        JSONArray array = new JSONArray();
        if (foods == null) {
            return array;
        }

        for (int index = 0; index < foods.size(); index++) {
            FoodItem item = foods.get(index);
            if (item == null) {
                continue;
            }

            try {
                if (currentSchemaVersion >= PRODUCT_PROFILE_SCHEMA_VERSION) {
                    ensureStructuredFields(item, index);
                }
                array.put(item.toJson());
            } catch (JSONException ignored) {
                // Invalid single records are skipped instead of corrupting the whole saved list.
            }
        }

        return array;
    }

    private static boolean ensureStructuredFields(FoodItem item, int recordIndex) {
        boolean changed = false;
        if (FoodItem.cleanText(item.productProfileId).length() == 0) {
            item.productProfileId = stableProfileId(item.id, recordIndex);
            changed = true;
        }
        if (FoodItem.cleanText(item.barcode).length() == 0) {
            String migratedBarcode = barcodeFromExplicitNote(item.notes);
            if (migratedBarcode.length() > 0) {
                item.barcode = migratedBarcode;
                changed = true;
            }
        }
        return changed;
    }

    private static String stableProfileId(String batchId, int recordIndex) {
        String cleanBatchId = FoodItem.cleanText(batchId);
        String seed = "food-profile-v2:"
                + cleanBatchId.length()
                + ":"
                + cleanBatchId
                + ":"
                + recordIndex;
        return "profile_" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String barcodeFromExplicitNote(String notes) {
        Matcher matcher = EXPLICIT_BARCODE_NOTE.matcher(FoodItem.cleanText(notes));
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (BarcodeUtils.isSupportedProductCode(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    static final class MigrationResult {
        final List<FoodItem> foods;
        final boolean needsWriteBack;

        MigrationResult(List<FoodItem> foods, boolean needsWriteBack) {
            this.foods = foods;
            this.needsWriteBack = needsWriteBack;
        }
    }

    private static final class ReadFoodsResult {
        final List<FoodItem> foods;
        final boolean repairedRecords;

        ReadFoodsResult(List<FoodItem> foods, boolean repairedRecords) {
            this.foods = foods;
            this.repairedRecords = repairedRecords;
        }
    }

    static final class StorageLoadResult {
        final List<FoodItem> foods;
        final boolean loaded;
        final boolean needsPrimaryWriteBack;
        final boolean restoredFromBackup;
        final boolean primaryHasFutureSchema;

        private StorageLoadResult(
                List<FoodItem> foods,
                boolean loaded,
                boolean needsPrimaryWriteBack,
                boolean restoredFromBackup,
                boolean primaryHasFutureSchema
        ) {
            this.foods = foods;
            this.loaded = loaded;
            this.needsPrimaryWriteBack = needsPrimaryWriteBack;
            this.restoredFromBackup = restoredFromBackup;
            this.primaryHasFutureSchema = primaryHasFutureSchema;
        }

        static StorageLoadResult loaded(
                List<FoodItem> foods,
                boolean needsPrimaryWriteBack,
                boolean restoredFromBackup,
                boolean primaryHasFutureSchema
        ) {
            List<FoodItem> safeFoods = foods == null ? new ArrayList<FoodItem>() : foods;
            return new StorageLoadResult(safeFoods, true, needsPrimaryWriteBack, restoredFromBackup, primaryHasFutureSchema);
        }

        static StorageLoadResult failed(boolean primaryHasFutureSchema) {
            return new StorageLoadResult(
                    new ArrayList<FoodItem>(),
                    false,
                    false,
                    false,
                    primaryHasFutureSchema
            );
        }
    }
}
