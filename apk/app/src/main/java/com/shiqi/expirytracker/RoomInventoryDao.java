package com.shiqi.expirytracker;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
interface RoomInventoryDao {
    @Query("SELECT * FROM products ORDER BY sortOrder ASC, productId ASC")
    List<RoomProductEntity> loadProducts();

    @Query("SELECT * FROM batches ORDER BY sortOrder ASC, batchId ASC")
    List<RoomBatchEntity> loadBatches();

    @Query("SELECT * FROM barcodes ORDER BY barcode ASC")
    List<RoomBarcodeEntity> loadBarcodes();

    @Query("SELECT * FROM product_barcodes ORDER BY productId ASC, barcode ASC")
    List<RoomProductBarcodeEntity> loadProductBarcodes();

    @Query("SELECT * FROM batch_reminder_plans ORDER BY batchId ASC")
    List<RoomReminderPlanEntity> loadReminderPlans();

    @Query("SELECT metadataValue FROM inventory_metadata WHERE metadataKey = :key LIMIT 1")
    String metadata(String key);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertProducts(List<RoomProductEntity> products);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertBatches(List<RoomBatchEntity> batches);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertBarcodes(List<RoomBarcodeEntity> barcodes);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertProductBarcodes(List<RoomProductBarcodeEntity> relations);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertReminderPlans(List<RoomReminderPlanEntity> plans);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putMetadata(RoomInventoryMetadataEntity metadata);

    @Query("DELETE FROM batch_reminder_plans")
    void deleteReminderPlans();

    @Query("DELETE FROM product_barcodes")
    void deleteProductBarcodes();

    @Query("DELETE FROM barcodes")
    void deleteBarcodes();

    @Query("DELETE FROM batches")
    void deleteBatches();

    @Query("DELETE FROM products")
    void deleteProducts();
}
