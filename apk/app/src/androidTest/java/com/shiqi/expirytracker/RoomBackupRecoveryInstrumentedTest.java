package com.shiqi.expirytracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class RoomBackupRecoveryInstrumentedTest {
    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        RoomInventoryStore.resetForTests(context);
        preferences = context.getSharedPreferences(
                FoodStore.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        assertTrue(preferences.edit().clear().commit());
    }

    @After
    public void tearDown() {
        RoomInventoryStore.resetForTests(context);
        preferences.edit().clear().commit();
    }

    @Test
    public void catalogPreviewsValidBackupAndRejectsSchemaMismatch() throws Exception {
        FoodItem milk = food("backup-milk", "profile-milk", "低温鲜牛奶");
        String raw = FoodStoreMigration.serialize(
                Arrays.asList(milk),
                FoodStore.CURRENT_SCHEMA_VERSION
        );
        RoomMigrationBackup.BackupDescriptor valid = RoomMigrationBackup.ensureBackup(
                context,
                raw,
                FoodStore.CURRENT_SCHEMA_VERSION
        );

        File directory = valid.file.getParentFile();
        File mismatch = new File(directory, "schema-mismatch.json");
        writeUtf8(
                mismatch,
                MigrationBackupEnvelope.create(
                        raw,
                        FoodStore.CURRENT_SCHEMA_VERSION - 1,
                        "2026-07-31T12:00:00+0800"
                )
        );

        RoomMigrationBackup.BackupCatalog catalog =
                RoomMigrationBackup.verifiedCatalog(
                        context,
                        FoodStore.CURRENT_SCHEMA_VERSION
                );

        assertEquals(1, catalog.candidates.size());
        assertEquals(1, catalog.rejectedFiles);
        RoomMigrationBackup.BackupCandidate candidate = catalog.candidates.get(0);
        assertEquals(valid.payloadSha256, candidate.payloadSha256);
        assertEquals(1, candidate.productCount);
        assertEquals(1, candidate.batchCount);
        assertEquals("低温鲜牛奶", candidate.foods().get(0).name);
    }

    @Test
    public void restoreCreatesSafetyBackupAndForcedFailureKeepsCurrentRoomRows()
            throws Exception {
        FoodStore store = new FoodStore(context);
        assertTrue(store.loadFoods().isEmpty());

        FoodItem current = food("current-batch", "current-profile", "当前酸奶");
        assertTrue(store.saveFoods(Arrays.asList(current)));

        FoodItem target = food("target-batch", "target-profile", "备份牛奶");
        target.quantity = 4;
        target.remainingQuantity = 3;
        String targetRaw = FoodStoreMigration.serialize(
                Arrays.asList(target),
                FoodStore.CURRENT_SCHEMA_VERSION
        );
        RoomMigrationBackup.BackupDescriptor targetBackup =
                RoomMigrationBackup.ensureBackup(
                        context,
                        targetRaw,
                        FoodStore.CURRENT_SCHEMA_VERSION
                );

        RoomMigrationBackup.RestoreResult forcedFailure =
                RoomMigrationBackup.restore(
                        context,
                        store,
                        Arrays.asList(current),
                        targetBackup.file,
                        true
                );
        assertFalse(forcedFailure.success);
        List<FoodItem> afterFailure = new FoodStore(context).loadFoods();
        assertEquals(1, afterFailure.size());
        assertEquals("当前酸奶", afterFailure.get(0).name);

        RoomMigrationBackup.BackupCatalog afterSafety =
                RoomMigrationBackup.verifiedCatalog(
                        context,
                        FoodStore.CURRENT_SCHEMA_VERSION
                );
        assertTrue(afterSafety.candidates.size() >= 2);
        String currentRaw = FoodStoreMigration.serialize(
                Arrays.asList(current),
                FoodStore.CURRENT_SCHEMA_VERSION
        );
        String currentHash = MigrationBackupEnvelope.sha256(currentRaw);
        boolean safetyFound = false;
        for (RoomMigrationBackup.BackupCandidate candidate : afterSafety.candidates) {
            if (currentHash.equals(candidate.payloadSha256)) {
                safetyFound = true;
                break;
            }
        }
        assertTrue(safetyFound);

        RoomMigrationBackup.RestoreResult restored =
                RoomMigrationBackup.restore(
                        context,
                        store,
                        afterFailure,
                        targetBackup.file,
                        false
                );
        assertTrue(restored.success);
        List<FoodItem> afterRestore = new FoodStore(context).loadFoods();
        assertEquals(1, afterRestore.size());
        assertEquals("备份牛奶", afterRestore.get(0).name);
        assertEquals(3.0d, afterRestore.get(0).remainingQuantity, 0.0001d);
    }

    @Test
    public void malformedBackupCannotReplaceCurrentInventory() throws Exception {
        FoodStore store = new FoodStore(context);
        assertTrue(store.loadFoods().isEmpty());
        FoodItem current = food("safe-batch", "safe-profile", "保留库存");
        assertTrue(store.saveFoods(Arrays.asList(current)));

        File directory = new File(context.getFilesDir(), "inventory-migration");
        assertTrue(directory.exists() || directory.mkdirs());
        File malformed = new File(directory, "malformed.json");
        writeUtf8(malformed, "{bad backup");

        RoomMigrationBackup.RestoreResult result = RoomMigrationBackup.restore(
                context,
                store,
                Arrays.asList(current),
                malformed,
                false
        );

        assertFalse(result.success);
        List<FoodItem> after = new FoodStore(context).loadFoods();
        assertEquals(1, after.size());
        assertEquals("保留库存", after.get(0).name);
    }

    private FoodItem food(String id, String profileId, String name) {
        FoodItem item = new FoodItem();
        item.id = id;
        item.productProfileId = profileId;
        item.barcode = "4006381333931";
        item.name = name;
        item.category = "dairy";
        item.productionDate = "2026-07-01";
        item.shelfLifeValue = Integer.valueOf(31);
        item.shelfLifeUnit = "day";
        item.expiryDate = "2026-08-01";
        item.dateSource = "manual";
        item.quantity = 1;
        item.remainingQuantity = 1;
        item.unit = "盒";
        item.storageMethod = "refrigerated";
        item.location = "fridge";
        item.createdAt = "2026-07-01T08:00:00+0800";
        item.updatedAt = "2026-07-01T08:00:00+0800";
        return item;
    }

    private void writeUtf8(File file, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        } finally {
            output.close();
        }
    }
}