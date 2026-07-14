package com.shiqi.expirytracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FoodStoreMigration {
    private static final int LEGACY_ARRAY_SCHEMA_VERSION = 0;
    private static final String SCHEMA_VERSION_KEY = "schemaVersion";
    private static final String FOODS_KEY = "foods";

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
            return new MigrationResult(readFoods((JSONArray) root).foods, true);
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
        root.put(FOODS_KEY, serializeFoods(foods));
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

        ReadFoodsResult read = readFoods(foods);
        return new MigrationResult(read.foods, schemaVersion != currentSchemaVersion || read.repairedDateSource);
    }

    private static ReadFoodsResult readFoods(JSONArray array) {
        List<FoodItem> foods = new ArrayList<FoodItem>();
        boolean repairedDateSource = false;

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) {
                continue;
            }

            String storedDateSource = object.optString("dateSource", "").trim();
            FoodItem item = FoodItem.fromJson(object);
            if ("calculated".equals(storedDateSource) && "manual".equals(item.dateSource)) {
                repairedDateSource = true;
            }
            if (item.id.length() > 0 && item.name.length() > 0) {
                foods.add(item);
            }
        }

        return new ReadFoodsResult(foods, repairedDateSource);
    }

    private static JSONArray serializeFoods(List<FoodItem> foods) {
        JSONArray array = new JSONArray();
        if (foods == null) {
            return array;
        }

        for (FoodItem item : foods) {
            if (item == null) {
                continue;
            }

            try {
                array.put(item.toJson());
            } catch (JSONException ignored) {
                // Invalid single records are skipped instead of corrupting the whole saved list.
            }
        }

        return array;
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
        final boolean repairedDateSource;

        ReadFoodsResult(List<FoodItem> foods, boolean repairedDateSource) {
            this.foods = foods;
            this.repairedDateSource = repairedDateSource;
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
