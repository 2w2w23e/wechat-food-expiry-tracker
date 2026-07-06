package com.shiqi.expirytracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class FoodStore {
    private static final String PREFS_NAME = "shiqi_android_v0";
    static final int CURRENT_SCHEMA_VERSION = 1;
    static final String STORAGE_KEY = "food_expiry_tracker_foods_v0";
    static final String MIGRATION_BACKUP_KEY = STORAGE_KEY + "_pre_schema_migration_backup";
    static final String RECENT_BACKUP_KEY_0 = STORAGE_KEY + "_recent_backup_0";
    static final String RECENT_BACKUP_KEY_1 = STORAGE_KEY + "_recent_backup_1";
    static final String RECENT_BACKUP_KEY_2 = STORAGE_KEY + "_recent_backup_2";
    private static final String[] RECENT_BACKUP_KEYS = new String[] {
            RECENT_BACKUP_KEY_0,
            RECENT_BACKUP_KEY_1,
            RECENT_BACKUP_KEY_2
    };

    private final SharedPreferences preferences;
    private boolean lastLoadFailed = false;

    FoodStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    List<FoodItem> loadFoods() {
        String raw = preferences.getString(STORAGE_KEY, null);
        FoodStoreMigration.StorageLoadResult result = FoodStoreMigration.loadWithBackups(
                raw,
                loadBackupRaws(),
                CURRENT_SCHEMA_VERSION
        );

        if (!result.loaded) {
            lastLoadFailed = true;
            return new ArrayList<FoodItem>();
        }

        lastLoadFailed = false;
        if (result.needsPrimaryWriteBack) {
            if (result.restoredFromBackup) {
                writeRecoveredStorage(result.foods);
            } else {
                writeMigratedStorage(raw, result.foods);
            }
        }
        return result.foods;
    }

    void saveFoods(List<FoodItem> foods) {
        String currentRaw = preferences.getString(STORAGE_KEY, null);
        if (!canOverwriteCurrentStorage(currentRaw)) {
            return;
        }

        try {
            String nextRaw = FoodStoreMigration.serialize(foods, CURRENT_SCHEMA_VERSION);
            SharedPreferences.Editor editor = preferences.edit();
            if (hasStoredPayload(currentRaw)) {
                rotateRecentBackups(editor, currentRaw);
            }
            if (FoodStoreMigration.needsMigration(currentRaw, CURRENT_SCHEMA_VERSION)) {
                editor.putString(MIGRATION_BACKUP_KEY, currentRaw);
            }
            editor.putString(STORAGE_KEY, nextRaw).apply();
            lastLoadFailed = false;
        } catch (JSONException ignored) {
            // Keep the previous SharedPreferences value if the new payload cannot be serialized.
        }
    }

    private void writeMigratedStorage(String previousRaw, List<FoodItem> foods) {
        try {
            String nextRaw = FoodStoreMigration.serialize(foods, CURRENT_SCHEMA_VERSION);
            SharedPreferences.Editor editor = preferences.edit();
            if (hasStoredPayload(previousRaw)) {
                rotateRecentBackups(editor, previousRaw);
                editor.putString(MIGRATION_BACKUP_KEY, previousRaw);
            }
            editor.putString(STORAGE_KEY, nextRaw).apply();
        } catch (JSONException ignored) {
            // Migration is already available in memory; failed write-back must not touch storage.
        }
    }

    private void writeRecoveredStorage(List<FoodItem> foods) {
        try {
            String nextRaw = FoodStoreMigration.serialize(foods, CURRENT_SCHEMA_VERSION);
            preferences.edit().putString(STORAGE_KEY, nextRaw).apply();
        } catch (JSONException ignored) {
            // Recovery is already available in memory; failed write-back must not touch storage.
        }
    }

    private List<String> loadBackupRaws() {
        List<String> backups = new ArrayList<String>();
        for (String key : RECENT_BACKUP_KEYS) {
            backups.add(preferences.getString(key, null));
        }
        backups.add(preferences.getString(MIGRATION_BACKUP_KEY, null));
        return backups;
    }

    private void rotateRecentBackups(SharedPreferences.Editor editor, String currentRaw) {
        if (!hasStoredPayload(currentRaw)) {
            return;
        }

        for (int index = RECENT_BACKUP_KEYS.length - 1; index > 0; index--) {
            String previous = preferences.getString(RECENT_BACKUP_KEYS[index - 1], null);
            if (hasStoredPayload(previous)) {
                editor.putString(RECENT_BACKUP_KEYS[index], previous);
            } else {
                editor.remove(RECENT_BACKUP_KEYS[index]);
            }
        }
        editor.putString(RECENT_BACKUP_KEYS[0], currentRaw);
    }

    private boolean canOverwriteCurrentStorage(String currentRaw) {
        if (!hasStoredPayload(currentRaw)) {
            return true;
        }

        if (lastLoadFailed) {
            return false;
        }

        try {
            FoodStoreMigration.migrate(currentRaw, CURRENT_SCHEMA_VERSION);
            return true;
        } catch (JSONException ignored) {
            return false;
        }
    }

    private static boolean hasStoredPayload(String raw) {
        return raw != null && raw.trim().length() > 0;
    }
}
