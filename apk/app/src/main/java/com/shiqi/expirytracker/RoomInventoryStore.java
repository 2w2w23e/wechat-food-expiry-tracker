package com.shiqi.expirytracker;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

final class RoomInventoryStore {
    private static final String META_INITIALIZED = "inventory_initialized";
    private static final String META_FINGERPRINT = "inventory_fingerprint";
    private static final String META_PRODUCT_COUNT = "product_count";
    private static final String META_BATCH_COUNT = "batch_count";
    private static final String META_BARCODE_COUNT = "barcode_count";
    private static final String META_RELATION_COUNT = "relation_count";
    private static final String META_FINISHED_COUNT = "finished_count";
    private static final String META_TOTAL_QUANTITY = "total_quantity";
    private static final String META_TOTAL_REMAINING = "total_remaining";
    private static final String META_UPDATED_AT = "updated_at";
    private static final String META_LEGACY_SHA256 = "legacy_payload_sha256";
    private static final String META_LEGACY_SCHEMA = "legacy_schema_version";
    private static final String META_BACKUP_FILE = "legacy_backup_file";

    private static final ExecutorService DATABASE_EXECUTOR =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "shiqi-room-inventory");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private final Context context;
    private final RoomInventoryDatabase database;

    RoomInventoryStore(Context context) {
        this.context = context.getApplicationContext();
        this.database = RoomInventoryDatabase.get(this.context);
    }

    LoadResult loadIfInitialized() {
        try {
            return execute(new Callable<LoadResult>() {
                @Override
                public LoadResult call() throws Exception {
                    RoomInventoryDao dao = database.inventoryDao();
                    if (!"1".equals(dao.metadata(META_INITIALIZED))) {
                        return LoadResult.notInitialized();
                    }
                    return verifiedLoad(dao);
                }
            });
        } catch (Exception exception) {
            boolean databaseExists = context.getDatabasePath(
                    RoomInventoryDatabase.DATABASE_NAME
            ).exists();
            return LoadResult.failure(databaseExists, describe(exception));
        }
    }

    LoadResult initializeFromLegacy(
            final List<FoodItem> foods,
            final String sourceRaw,
            final int sourceSchemaVersion
    ) {
        try {
            return execute(new Callable<LoadResult>() {
                @Override
                public LoadResult call() throws Exception {
                    RoomInventoryDao dao = database.inventoryDao();
                    if ("1".equals(dao.metadata(META_INITIALIZED))) {
                        return verifiedLoad(dao);
                    }

                    final InventorySnapshot snapshot =
                            InventorySnapshotMapper.fromFoods(foods);
                    final InventorySnapshotMapper.VerificationSummary expected =
                            InventorySnapshotMapper.summarize(snapshot);
                    final List<FoodItem> normalizedFoods =
                            InventorySnapshotMapper.toFoods(snapshot);
                    final RoomMigrationBackup.BackupDescriptor backup =
                            RoomMigrationBackup.ensureBackup(
                                    context,
                                    sourceRaw,
                                    sourceSchemaVersion
                            );
                    replaceInTransaction(snapshot, expected, false, backup);
                    return LoadResult.success(normalizedFoods);
                }
            });
        } catch (Exception exception) {
            return LoadResult.failure(false, describe(exception));
        }
    }

    boolean saveFoods(final List<FoodItem> foods, final boolean forceFailure) {
        try {
            return execute(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    RoomInventoryDao dao = database.inventoryDao();
                    if (!"1".equals(dao.metadata(META_INITIALIZED))) {
                        return Boolean.FALSE;
                    }
                    InventorySnapshot snapshot = InventorySnapshotMapper.fromFoods(foods);
                    InventorySnapshotMapper.VerificationSummary expected =
                            InventorySnapshotMapper.summarize(snapshot);
                    replaceInTransaction(snapshot, expected, forceFailure, null);
                    return Boolean.TRUE;
                }
            }).booleanValue();
        } catch (Exception ignored) {
            return false;
        }
    }

    static void resetForTests(Context context) {
        RoomInventoryDatabase.resetForTests(context);
        RoomMigrationBackup.deleteBackupsForTests(context);
    }

    private void replaceInTransaction(
            final InventorySnapshot snapshot,
            final InventorySnapshotMapper.VerificationSummary expected,
            final boolean forceFailure,
            final RoomMigrationBackup.BackupDescriptor backup
    ) {
        database.runInTransaction(new Runnable() {
            @Override
            public void run() {
                try {
                    RoomInventoryDao dao = database.inventoryDao();
                    clearInventory(dao);
                    insertSnapshot(dao, snapshot);
                    if (forceFailure) {
                        throw new ForcedTransactionFailure();
                    }
                    InventorySnapshot stored = readSnapshot(dao);
                    InventorySnapshotMapper.VerificationSummary actual =
                            InventorySnapshotMapper.summarize(stored);
                    if (!expected.equivalentTo(actual)) {
                        throw new IllegalStateException(
                                "Room transaction read-back did not match the requested inventory."
                        );
                    }
                    writeSummaryMetadata(dao, actual);
                    if (backup != null) {
                        putMetadata(dao, META_LEGACY_SHA256, backup.payloadSha256);
                        putMetadata(
                                dao,
                                META_LEGACY_SCHEMA,
                                String.valueOf(backup.sourceSchemaVersion)
                        );
                        putMetadata(dao, META_BACKUP_FILE, backup.file.getName());
                    }
                    putMetadata(dao, META_INITIALIZED, "1");
                } catch (InventorySnapshot.SnapshotException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        });
    }

    private static void clearInventory(RoomInventoryDao dao) {
        dao.deleteReminderPlans();
        dao.deleteProductBarcodes();
        dao.deleteBarcodes();
        dao.deleteBatches();
        dao.deleteProducts();
    }

    private static void insertSnapshot(RoomInventoryDao dao, InventorySnapshot snapshot) {
        List<RoomProductEntity> products = productEntities(snapshot.products);
        List<RoomBatchEntity> batches = batchEntities(snapshot.batches);
        List<RoomBarcodeEntity> barcodes = barcodeEntities(snapshot.barcodes);
        List<RoomProductBarcodeEntity> relations =
                productBarcodeEntities(snapshot.productBarcodes);
        List<RoomReminderPlanEntity> reminders =
                reminderEntities(snapshot.reminderPlans);
        if (!products.isEmpty()) {
            dao.insertProducts(products);
        }
        if (!batches.isEmpty()) {
            dao.insertBatches(batches);
        }
        if (!barcodes.isEmpty()) {
            dao.insertBarcodes(barcodes);
        }
        if (!relations.isEmpty()) {
            dao.insertProductBarcodes(relations);
        }
        if (!reminders.isEmpty()) {
            dao.insertReminderPlans(reminders);
        }
    }

    private static InventorySnapshot readSnapshot(RoomInventoryDao dao) {
        InventorySnapshot snapshot = new InventorySnapshot();
        for (RoomProductEntity entity : dao.loadProducts()) {
            InventorySnapshot.ProductRecord record = new InventorySnapshot.ProductRecord();
            record.productId = entity.productId;
            record.name = entity.name;
            record.category = entity.category;
            record.defaultUnit = entity.defaultUnit;
            record.defaultStorageMethod = entity.defaultStorageMethod;
            record.defaultLocation = entity.defaultLocation;
            record.defaultShelfLifeValue = entity.defaultShelfLifeValue;
            record.defaultShelfLifeUnit = entity.defaultShelfLifeUnit;
            record.notes = entity.notes;
            record.createdAt = entity.createdAt;
            record.updatedAt = entity.updatedAt;
            record.sortOrder = entity.sortOrder;
            snapshot.products.add(record);
        }
        for (RoomBatchEntity entity : dao.loadBatches()) {
            InventorySnapshot.BatchRecord record = new InventorySnapshot.BatchRecord();
            record.batchId = entity.batchId;
            record.productId = entity.productId;
            record.sourceBarcode = entity.sourceBarcode;
            record.productionDate = entity.productionDate;
            record.shelfLifeValue = entity.shelfLifeValue;
            record.shelfLifeUnit = entity.shelfLifeUnit;
            record.openedDate = entity.openedDate;
            record.afterOpenShelfLifeValue = entity.afterOpenShelfLifeValue;
            record.afterOpenShelfLifeUnit = entity.afterOpenShelfLifeUnit;
            record.expiryDate = entity.expiryDate;
            record.dateSource = entity.dateSource;
            record.quantity = entity.quantity;
            record.remainingQuantity = entity.remainingQuantity;
            record.unit = entity.unit;
            record.storageMethod = entity.storageMethod;
            record.location = entity.location;
            record.notes = entity.notes;
            record.createdAt = entity.createdAt;
            record.updatedAt = entity.updatedAt;
            record.status = entity.status;
            record.finishedAt = entity.finishedAt;
            record.sortOrder = entity.sortOrder;
            snapshot.batches.add(record);
        }
        for (RoomBarcodeEntity entity : dao.loadBarcodes()) {
            InventorySnapshot.BarcodeRecord record = new InventorySnapshot.BarcodeRecord();
            record.barcode = entity.barcode;
            record.format = entity.format;
            record.createdAt = entity.createdAt;
            record.lastConfirmedAt = entity.lastConfirmedAt;
            snapshot.barcodes.add(record);
        }
        for (RoomProductBarcodeEntity entity : dao.loadProductBarcodes()) {
            InventorySnapshot.ProductBarcodeRecord record =
                    new InventorySnapshot.ProductBarcodeRecord();
            record.productId = entity.productId;
            record.barcode = entity.barcode;
            snapshot.productBarcodes.add(record);
        }
        for (RoomReminderPlanEntity entity : dao.loadReminderPlans()) {
            InventorySnapshot.ReminderPlanRecord record =
                    new InventorySnapshot.ReminderPlanRecord();
            record.batchId = entity.batchId;
            record.mode = entity.mode;
            record.offsetsJson = entity.offsetsJson;
            record.fingerprint = entity.fingerprint;
            record.plannedDaysLeft = entity.plannedDaysLeft;
            record.plannedOn = entity.plannedOn;
            snapshot.reminderPlans.add(record);
        }
        return snapshot;
    }

    private static List<RoomProductEntity> productEntities(
            List<InventorySnapshot.ProductRecord> records
    ) {
        List<RoomProductEntity> entities = new ArrayList<RoomProductEntity>();
        for (InventorySnapshot.ProductRecord record : records) {
            RoomProductEntity entity = new RoomProductEntity();
            entity.productId = record.productId;
            entity.name = record.name;
            entity.category = record.category;
            entity.defaultUnit = record.defaultUnit;
            entity.defaultStorageMethod = record.defaultStorageMethod;
            entity.defaultLocation = record.defaultLocation;
            entity.defaultShelfLifeValue = record.defaultShelfLifeValue;
            entity.defaultShelfLifeUnit = record.defaultShelfLifeUnit;
            entity.notes = record.notes;
            entity.createdAt = record.createdAt;
            entity.updatedAt = record.updatedAt;
            entity.sortOrder = record.sortOrder;
            entities.add(entity);
        }
        return entities;
    }

    private static List<RoomBatchEntity> batchEntities(
            List<InventorySnapshot.BatchRecord> records
    ) {
        List<RoomBatchEntity> entities = new ArrayList<RoomBatchEntity>();
        for (InventorySnapshot.BatchRecord record : records) {
            RoomBatchEntity entity = new RoomBatchEntity();
            entity.batchId = record.batchId;
            entity.productId = record.productId;
            entity.sourceBarcode = record.sourceBarcode;
            entity.productionDate = record.productionDate;
            entity.shelfLifeValue = record.shelfLifeValue;
            entity.shelfLifeUnit = record.shelfLifeUnit;
            entity.openedDate = record.openedDate;
            entity.afterOpenShelfLifeValue = record.afterOpenShelfLifeValue;
            entity.afterOpenShelfLifeUnit = record.afterOpenShelfLifeUnit;
            entity.expiryDate = record.expiryDate;
            entity.dateSource = record.dateSource;
            entity.quantity = record.quantity;
            entity.remainingQuantity = record.remainingQuantity;
            entity.unit = record.unit;
            entity.storageMethod = record.storageMethod;
            entity.location = record.location;
            entity.notes = record.notes;
            entity.createdAt = record.createdAt;
            entity.updatedAt = record.updatedAt;
            entity.status = record.status;
            entity.finishedAt = record.finishedAt;
            entity.sortOrder = record.sortOrder;
            entities.add(entity);
        }
        return entities;
    }

    private static List<RoomBarcodeEntity> barcodeEntities(
            List<InventorySnapshot.BarcodeRecord> records
    ) {
        List<RoomBarcodeEntity> entities = new ArrayList<RoomBarcodeEntity>();
        for (InventorySnapshot.BarcodeRecord record : records) {
            RoomBarcodeEntity entity = new RoomBarcodeEntity();
            entity.barcode = record.barcode;
            entity.format = record.format;
            entity.createdAt = record.createdAt;
            entity.lastConfirmedAt = record.lastConfirmedAt;
            entities.add(entity);
        }
        return entities;
    }

    private static List<RoomProductBarcodeEntity> productBarcodeEntities(
            List<InventorySnapshot.ProductBarcodeRecord> records
    ) {
        List<RoomProductBarcodeEntity> entities =
                new ArrayList<RoomProductBarcodeEntity>();
        for (InventorySnapshot.ProductBarcodeRecord record : records) {
            RoomProductBarcodeEntity entity = new RoomProductBarcodeEntity();
            entity.productId = record.productId;
            entity.barcode = record.barcode;
            entities.add(entity);
        }
        return entities;
    }

    private static List<RoomReminderPlanEntity> reminderEntities(
            List<InventorySnapshot.ReminderPlanRecord> records
    ) {
        List<RoomReminderPlanEntity> entities =
                new ArrayList<RoomReminderPlanEntity>();
        for (InventorySnapshot.ReminderPlanRecord record : records) {
            RoomReminderPlanEntity entity = new RoomReminderPlanEntity();
            entity.batchId = record.batchId;
            entity.mode = record.mode;
            entity.offsetsJson = record.offsetsJson;
            entity.fingerprint = record.fingerprint;
            entity.plannedDaysLeft = record.plannedDaysLeft;
            entity.plannedOn = record.plannedOn;
            entities.add(entity);
        }
        return entities;
    }

    private static LoadResult verifiedLoad(RoomInventoryDao dao)
            throws InventorySnapshot.SnapshotException {
        InventorySnapshot snapshot = readSnapshot(dao);
        InventorySnapshotMapper.VerificationSummary summary =
                InventorySnapshotMapper.summarize(snapshot);
        String expectedFingerprint = clean(dao.metadata(META_FINGERPRINT));
        if (expectedFingerprint.length() == 0
                || !expectedFingerprint.equals(summary.fingerprint)
                || !metadataMatches(dao, summary)) {
            return LoadResult.failure(true, "Room inventory verification failed.");
        }
        return LoadResult.success(InventorySnapshotMapper.toFoods(snapshot));
    }

    private static boolean metadataMatches(
            RoomInventoryDao dao,
            InventorySnapshotMapper.VerificationSummary summary
    ) {
        return String.valueOf(summary.productCount).equals(dao.metadata(META_PRODUCT_COUNT))
                && String.valueOf(summary.batchCount).equals(dao.metadata(META_BATCH_COUNT))
                && String.valueOf(summary.barcodeCount).equals(dao.metadata(META_BARCODE_COUNT))
                && String.valueOf(summary.relationCount).equals(dao.metadata(META_RELATION_COUNT))
                && String.valueOf(summary.finishedCount).equals(dao.metadata(META_FINISHED_COUNT))
                && Double.toString(summary.totalQuantity).equals(
                        dao.metadata(META_TOTAL_QUANTITY)
                )
                && Double.toString(summary.totalRemaining).equals(
                        dao.metadata(META_TOTAL_REMAINING)
                );
    }

    private static void writeSummaryMetadata(
            RoomInventoryDao dao,
            InventorySnapshotMapper.VerificationSummary summary
    ) {
        putMetadata(dao, META_FINGERPRINT, summary.fingerprint);
        putMetadata(dao, META_PRODUCT_COUNT, String.valueOf(summary.productCount));
        putMetadata(dao, META_BATCH_COUNT, String.valueOf(summary.batchCount));
        putMetadata(dao, META_BARCODE_COUNT, String.valueOf(summary.barcodeCount));
        putMetadata(dao, META_RELATION_COUNT, String.valueOf(summary.relationCount));
        putMetadata(dao, META_FINISHED_COUNT, String.valueOf(summary.finishedCount));
        putMetadata(dao, META_TOTAL_QUANTITY, Double.toString(summary.totalQuantity));
        putMetadata(dao, META_TOTAL_REMAINING, Double.toString(summary.totalRemaining));
        putMetadata(dao, META_UPDATED_AT, DateRules.nowIsoLike());
    }

    private static void putMetadata(RoomInventoryDao dao, String key, String value) {
        RoomInventoryMetadataEntity metadata = new RoomInventoryMetadataEntity();
        metadata.metadataKey = key;
        metadata.metadataValue = clean(value);
        dao.putMetadata(metadata);
    }

    private static <T> T execute(Callable<T> callable) throws Exception {
        Future<T> future = DATABASE_EXECUTOR.submit(callable);
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static String describe(Exception exception) {
        String message = exception == null ? "" : clean(exception.getMessage());
        return message.length() > 0
                ? exception.getClass().getSimpleName() + ": " + message
                : exception == null ? "Unknown Room inventory error." : exception.getClass().getSimpleName();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static final class LoadResult {
        final boolean initialized;
        final boolean success;
        final List<FoodItem> foods;
        final String error;

        private LoadResult(
                boolean initialized,
                boolean success,
                List<FoodItem> foods,
                String error
        ) {
            this.initialized = initialized;
            this.success = success;
            this.foods = foods == null ? new ArrayList<FoodItem>() : foods;
            this.error = clean(error);
        }

        static LoadResult notInitialized() {
            return new LoadResult(false, true, new ArrayList<FoodItem>(), "");
        }

        static LoadResult success(List<FoodItem> foods) {
            return new LoadResult(true, true, foods, "");
        }

        static LoadResult failure(boolean initialized, String error) {
            return new LoadResult(
                    initialized,
                    false,
                    new ArrayList<FoodItem>(),
                    error
            );
        }
    }

    private static final class ForcedTransactionFailure extends RuntimeException {
        ForcedTransactionFailure() {
            super("Forced Room transaction failure.");
        }
    }
}
