package com.shiqi.expirytracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class RoomInventoryMigrationInstrumentedTest {
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
    public void emptyLegacyInitializesVerifiedRoomWithoutWritingPreferences() {
        FoodStore store = new FoodStore(context);

        assertTrue(store.loadFoods().isEmpty());
        assertEquals("ROOM", store.storageModeForTests());
        assertFalse(preferences.contains(FoodStore.STORAGE_KEY));
        assertTrue(context.getDatabasePath(RoomInventoryDatabase.DATABASE_NAME).isFile());
        assertTrue(RoomMigrationBackup.verifiedCatalog(
                context,
                FoodStore.CURRENT_SCHEMA_VERSION
        ).candidates.isEmpty());
    }

    @Test
    public void schemaZeroAndSchemaTwoPayloadsMigrateWithoutLosingBatchFields()
            throws Exception {
        FoodItem legacyArrayItem = food(
                "batch-v0",
                "",
                "",
                "低温鲜牛奶",
                "2026-07-01",
                "2026-07-31"
        );
        legacyArrayItem.quantity = 4.5;
        legacyArrayItem.remainingQuantity = 2.25;
        legacyArrayItem.notes = "schema-0-note";
        JSONArray legacyArray = new JSONArray();
        legacyArray.put(legacyArrayItem.toJson());
        String schemaZeroRaw = "\n " + legacyArray.toString() + "\t ";
        assertTrue(preferences.edit()
                .putString(FoodStore.STORAGE_KEY, schemaZeroRaw)
                .commit());

        List<FoodItem> schemaZeroFoods = new FoodStore(context).loadFoods();
        assertEquals(1, schemaZeroFoods.size());
        assertEquals("batch-v0", schemaZeroFoods.get(0).id);
        assertEquals("低温鲜牛奶", schemaZeroFoods.get(0).name);
        assertEquals(4.5, schemaZeroFoods.get(0).quantity, 0.0001);
        assertEquals(2.25, schemaZeroFoods.get(0).remainingQuantity, 0.0001);
        assertEquals("schema-0-note", schemaZeroFoods.get(0).notes);
        assertEquals(schemaZeroRaw, preferences.getString(FoodStore.STORAGE_KEY, null));
        assertBackupEnvelope(schemaZeroRaw, 0);

        RoomInventoryStore.resetForTests(context);
        assertTrue(preferences.edit().clear().commit());

        FoodItem schemaTwoItem = food(
                "batch-v2",
                "profile-v2",
                "4006381333931",
                "常温酸奶",
                "2026-07-10",
                "2026-08-10"
        );
        schemaTwoItem.storageMethod = "refrigerated";
        schemaTwoItem.location = "fridge";
        schemaTwoItem.openedDate = "2026-07-20";
        schemaTwoItem.afterOpenShelfLifeValue = Integer.valueOf(3);
        schemaTwoItem.afterOpenShelfLifeUnit = "day";
        String schemaTwoRaw = FoodStoreMigration.serialize(
                Arrays.asList(schemaTwoItem),
                2
        );
        assertTrue(preferences.edit()
                .putString(FoodStore.STORAGE_KEY, schemaTwoRaw)
                .commit());

        List<FoodItem> schemaTwoFoods = new FoodStore(context).loadFoods();
        assertEquals(1, schemaTwoFoods.size());
        FoodItem migrated = schemaTwoFoods.get(0);
        assertEquals("batch-v2", migrated.id);
        assertEquals("profile-v2", migrated.productProfileId);
        assertEquals("4006381333931", migrated.barcode);
        assertEquals("2026-07-20", migrated.openedDate);
        assertEquals(Integer.valueOf(3), migrated.afterOpenShelfLifeValue);
        assertEquals("day", migrated.afterOpenShelfLifeUnit);
        assertEquals(schemaTwoRaw, preferences.getString(FoodStore.STORAGE_KEY, null));
        assertBackupEnvelope(schemaTwoRaw, 2);
    }

    @Test
    public void schemaThreeMigrationStaysAuthoritativeIdempotentAndTransactional()
            throws Exception {
        FoodItem first = food(
                "batch-a",
                "profile-a",
                "4006381333931",
                "原味酸奶",
                "2026-07-01",
                "2026-08-01"
        );
        first.quantity = 3;
        first.remainingQuantity = 2;
        first.unit = "盒";
        first.smartReminderOffsets.add(Integer.valueOf(7));
        first.smartReminderOffsets.add(Integer.valueOf(3));
        first.smartReminderFingerprint = "smart-a";
        first.smartReminderPlannedDaysLeft = 31;
        first.smartReminderPlannedOn = "2026-07-01";

        FoodItem second = food(
                "batch-b",
                "profile-b",
                "4006381333931",
                "草莓酸奶",
                "2026-07-05",
                "2026-08-05"
        );
        second.quantity = 5;
        second.remainingQuantity = 0;
        second.isFinished = true;
        second.finishedAt = "2026-07-20T10:00:00+0800";
        second.updatedAt = "2026-07-20T10:00:00+0800";

        String originalRaw = " \n" + FoodStoreMigration.serialize(
                Arrays.asList(first, second),
                FoodStore.CURRENT_SCHEMA_VERSION
        ) + "\n\t";
        assertTrue(preferences.edit()
                .putString(FoodStore.STORAGE_KEY, originalRaw)
                .commit());

        FoodStore firstStore = new FoodStore(context);
        List<FoodItem> migrated = firstStore.loadFoods();
        assertEquals("ROOM", firstStore.storageModeForTests());
        assertEquals(2, migrated.size());
        assertEquals(originalRaw, preferences.getString(FoodStore.STORAGE_KEY, null));
        assertEquals("4006381333931", migrated.get(0).barcode);
        assertEquals("4006381333931", migrated.get(1).barcode);
        assertNotEquals(migrated.get(0).productProfileId, migrated.get(1).productProfileId);
        assertEquals(Arrays.asList(Integer.valueOf(7), Integer.valueOf(3)),
                migrated.get(0).smartReminderOffsets);
        assertTrue(migrated.get(1).isFinished);
        assertBackupEnvelope(originalRaw, FoodStore.CURRENT_SCHEMA_VERSION);

        List<FoodItem> repeated = new FoodStore(context).loadFoods();
        assertEquals(2, repeated.size());
        assertEquals("batch-a", repeated.get(0).id);
        assertEquals("batch-b", repeated.get(1).id);

        FoodItem decoy = food(
                "legacy-decoy",
                "legacy-decoy-profile",
                "",
                "不应重新载入的旧数据",
                "2026-01-01",
                "2026-01-02"
        );
        String externallyChangedLegacy = FoodStoreMigration.serialize(
                Arrays.asList(decoy),
                FoodStore.CURRENT_SCHEMA_VERSION
        );
        assertTrue(preferences.edit()
                .putString(FoodStore.STORAGE_KEY, externallyChangedLegacy)
                .commit());

        FoodStore authoritativeStore = new FoodStore(context);
        List<FoodItem> authoritative = authoritativeStore.loadFoods();
        assertEquals(2, authoritative.size());
        assertEquals("原味酸奶", authoritative.get(0).name);
        assertEquals("草莓酸奶", authoritative.get(1).name);

        authoritative.get(0).remainingQuantity = 1;
        authoritative.get(0).notes = "Room edit";
        assertTrue(authoritativeStore.saveFoods(authoritative));
        assertEquals(
                externallyChangedLegacy,
                preferences.getString(FoodStore.STORAGE_KEY, null)
        );

        List<FoodItem> afterSave = new FoodStore(context).loadFoods();
        assertEquals(1, (int) afterSave.get(0).remainingQuantity);
        assertEquals("Room edit", afterSave.get(0).notes);

        List<FoodItem> failedWrite = copies(afterSave);
        failedWrite.get(0).name = "不应提交";
        assertFalse(authoritativeStore.saveFoodsForQaForcedFailure(failedWrite));

        List<FoodItem> afterRollback = new FoodStore(context).loadFoods();
        assertEquals("原味酸奶", afterRollback.get(0).name);
        assertEquals("Room edit", afterRollback.get(0).notes);
        assertEquals(2, afterRollback.size());
    }

    @Test
    public void corruptedRoomVerificationFallsBackReadOnlyWithoutTouchingLegacy()
            throws Exception {
        FoodItem original = food(
                "fallback-batch",
                "fallback-profile",
                "4006381333931",
                "保留的旧库存",
                "2026-07-01",
                "2026-08-01"
        );
        String legacyRaw = FoodStoreMigration.serialize(
                Arrays.asList(original),
                FoodStore.CURRENT_SCHEMA_VERSION
        );
        assertTrue(preferences.edit()
                .putString(FoodStore.STORAGE_KEY, legacyRaw)
                .commit());

        FoodStore initialized = new FoodStore(context);
        assertEquals(1, initialized.loadFoods().size());
        assertEquals("ROOM", initialized.storageModeForTests());

        RoomInventoryMetadataEntity corrupted = new RoomInventoryMetadataEntity();
        corrupted.metadataKey = "inventory_fingerprint";
        corrupted.metadataValue = "corrupted-for-test";
        RoomInventoryDatabase.get(context).inventoryDao().putMetadata(corrupted);

        FoodStore fallback = new FoodStore(context);
        List<FoodItem> visible = fallback.loadFoods();
        assertEquals("ROOM_FAILED", fallback.storageModeForTests());
        assertFalse(fallback.lastRoomErrorForTests().isEmpty());
        assertEquals(1, visible.size());
        assertEquals("保留的旧库存", visible.get(0).name);

        visible.get(0).name = "不应覆盖";
        assertFalse(fallback.saveFoods(visible));
        assertEquals(legacyRaw, preferences.getString(FoodStore.STORAGE_KEY, null));
    }

    @Test
    public void sameProductKeepsMultipleBarcodesAcrossRoomRoundTrip() throws Exception {
        FoodItem first = food(
                "multi-barcode-a",
                "shared-profile",
                "6926265313430",
                "同一商品",
                "2026-07-01",
                "2026-08-01"
        );
        FoodItem second = food(
                "multi-barcode-b",
                "shared-profile",
                "4006381333931",
                "同一商品",
                "2026-07-05",
                "2026-08-05"
        );
        second.unit = "盒";
        String raw = FoodStoreMigration.serialize(
                Arrays.asList(first, second),
                FoodStore.CURRENT_SCHEMA_VERSION
        );
        assertTrue(preferences.edit()
                .putString(FoodStore.STORAGE_KEY, raw)
                .commit());

        List<FoodItem> migrated = new FoodStore(context).loadFoods();

        assertEquals(2, migrated.size());
        assertEquals("shared-profile", migrated.get(0).productProfileId);
        assertEquals("shared-profile", migrated.get(1).productProfileId);
        assertEquals("6926265313430", migrated.get(0).barcode);
        assertEquals("4006381333931", migrated.get(1).barcode);
        assertEquals("盒", migrated.get(1).unit);
    }

    private FoodItem food(
            String id,
            String profileId,
            String barcode,
            String name,
            String productionDate,
            String expiryDate
    ) {
        FoodItem item = new FoodItem();
        item.id = id;
        item.productProfileId = profileId;
        item.barcode = barcode;
        item.name = name;
        item.category = "dairy";
        item.productionDate = productionDate;
        item.shelfLifeValue = Integer.valueOf(31);
        item.shelfLifeUnit = "day";
        item.expiryDate = expiryDate;
        item.dateSource = "manual";
        item.quantity = 1;
        item.remainingQuantity = 1;
        item.unit = "件";
        item.storageMethod = "refrigerated";
        item.location = "fridge";
        item.notes = "迁移测试";
        item.createdAt = "2026-07-01T08:00:00+0800";
        item.updatedAt = "2026-07-01T08:00:00+0800";
        return item;
    }

    private List<FoodItem> copies(List<FoodItem> source) {
        List<FoodItem> result = new ArrayList<FoodItem>();
        for (FoodItem item : source) {
            result.add(item.copy());
        }
        return result;
    }

    private void assertBackupEnvelope(String expectedPayload, int expectedSchema)
            throws Exception {
        File directory = new File(context.getFilesDir(), "inventory-migration");
        File[] files = directory.listFiles();
        assertTrue(files != null && files.length == 1);
        JSONObject envelope = new JSONObject(readUtf8(files[0]));
        assertEquals("shiqi-room-migration-backup", envelope.getString("format"));
        assertEquals(1, envelope.getInt("formatVersion"));
        assertEquals(expectedSchema, envelope.getInt("sourceSchemaVersion"));
        assertEquals(
                expectedPayload.getBytes(StandardCharsets.UTF_8).length,
                envelope.getInt("payloadLength")
        );
        assertEquals(sha256(expectedPayload), envelope.getString("payloadSha256"));
        assertEquals(expectedPayload, envelope.getString("payload"));
    }

    private static String readUtf8(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte current : hash) {
            result.append(String.format("%02x", current & 0xff));
        }
        return result.toString();
    }
}
