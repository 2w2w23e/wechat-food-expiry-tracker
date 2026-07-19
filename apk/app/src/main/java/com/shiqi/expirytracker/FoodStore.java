package com.shiqi.expirytracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FoodStore {
    private static final String PREFS_NAME = "shiqi_android_v0";
    static final int CURRENT_SCHEMA_VERSION = 3;
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

    ProductProfileIndex loadProductProfileIndex() {
        return ProductProfileIndex.fromFoods(loadFoods());
    }

    boolean saveFoods(List<FoodItem> foods) {
        return saveFoodsInternal(foods, false);
    }

    boolean saveFoodsForImport(List<FoodItem> foods) {
        return saveFoodsInternal(foods, false);
    }

    boolean saveFoodsForQaForcedFailure(List<FoodItem> foods) {
        return saveFoodsInternal(foods, true);
    }

    private boolean saveFoodsInternal(List<FoodItem> foods, boolean forceFailureAfterCommit) {
        String currentRaw = preferences.getString(STORAGE_KEY, null);
        if (!canOverwriteCurrentStorage(currentRaw)) {
            return false;
        }

        try {
            String nextRaw = FoodStoreMigration.serialize(foods, CURRENT_SCHEMA_VERSION);
            PreferenceSnapshot snapshot = captureStorageSnapshot();
            SharedPreferences.Editor editor = preferences.edit();
            if (hasStoredPayload(currentRaw)) {
                rotateRecentBackups(editor, currentRaw);
            }
            if (FoodStoreMigration.needsMigration(currentRaw, CURRENT_SCHEMA_VERSION)) {
                editor.putString(MIGRATION_BACKUP_KEY, currentRaw);
            }
            editor.putString(STORAGE_KEY, nextRaw);
            boolean committed;
            try {
                committed = editor.commit();
            } catch (RuntimeException ignored) {
                restoreStorageSnapshot(snapshot);
                return false;
            }
            if (!committed || forceFailureAfterCommit) {
                // commit() may update SharedPreferences' process cache even when disk persistence fails.
                restoreStorageSnapshot(snapshot);
                return false;
            }
            lastLoadFailed = false;
            return true;
        } catch (JSONException ignored) {
            // Keep the previous SharedPreferences value if the new payload cannot be serialized.
            return false;
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

    private PreferenceSnapshot captureStorageSnapshot() {
        Map<String, String> values = new HashMap<String, String>();
        Set<String> presentKeys = new HashSet<String>();
        captureStringPreference(STORAGE_KEY, values, presentKeys);
        captureStringPreference(MIGRATION_BACKUP_KEY, values, presentKeys);
        for (String key : RECENT_BACKUP_KEYS) {
            captureStringPreference(key, values, presentKeys);
        }
        return new PreferenceSnapshot(values, presentKeys);
    }

    private void captureStringPreference(String key, Map<String, String> values, Set<String> presentKeys) {
        if (preferences.contains(key)) {
            presentKeys.add(key);
            values.put(key, preferences.getString(key, null));
        }
    }

    private void restoreStorageSnapshot(PreferenceSnapshot snapshot) {
        SharedPreferences.Editor restore = preferences.edit();
        for (String key : snapshot.allKeys()) {
            if (snapshot.presentKeys.contains(key)) {
                restore.putString(key, snapshot.values.get(key));
            } else {
                restore.remove(key);
            }
        }
        try {
            // Even if this disk write also fails, commit updates the process cache back to the old values.
            restore.commit();
        } catch (RuntimeException ignored) {
            // The original save still reports failure; the existing on-disk payload remains authoritative.
        }
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

    private static final class PreferenceSnapshot {
        final Map<String, String> values;
        final Set<String> presentKeys;

        PreferenceSnapshot(Map<String, String> values, Set<String> presentKeys) {
            this.values = values;
            this.presentKeys = presentKeys;
        }

        Set<String> allKeys() {
            Set<String> keys = new HashSet<String>();
            keys.add(STORAGE_KEY);
            keys.add(MIGRATION_BACKUP_KEY);
            for (String key : RECENT_BACKUP_KEYS) {
                keys.add(key);
            }
            return keys;
        }
    }
}
