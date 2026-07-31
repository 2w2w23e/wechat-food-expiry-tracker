package com.shiqi.expirytracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "products")
final class RoomProductEntity {
    @PrimaryKey
    @NonNull
    public String productId = "";
    @NonNull
    public String name = "";
    @NonNull
    public String category = "other";
    @NonNull
    public String defaultUnit = "件";
    @NonNull
    public String defaultStorageMethod = "room_temp";
    @NonNull
    public String defaultLocation = FoodData.LOCATION_UNSPECIFIED;
    public Integer defaultShelfLifeValue;
    @NonNull
    public String defaultShelfLifeUnit = "";
    @NonNull
    public String notes = "";
    @NonNull
    public String createdAt = "";
    @NonNull
    public String updatedAt = "";
    public int sortOrder;
}

@Entity(
        tableName = "batches",
        foreignKeys = @ForeignKey(
                entity = RoomProductEntity.class,
                parentColumns = "productId",
                childColumns = "productId",
                onDelete = ForeignKey.CASCADE,
                deferred = true
        ),
        indices = @Index("productId")
)
final class RoomBatchEntity {
    @PrimaryKey
    @NonNull
    public String batchId = "";
    @NonNull
    public String productId = "";
    @NonNull
    public String sourceBarcode = "";
    @NonNull
    public String productionDate = "";
    public Integer shelfLifeValue;
    @NonNull
    public String shelfLifeUnit = "";
    @NonNull
    public String openedDate = "";
    public Integer afterOpenShelfLifeValue;
    @NonNull
    public String afterOpenShelfLifeUnit = "";
    @NonNull
    public String expiryDate = "";
    @NonNull
    public String dateSource = "unknown";
    public double quantity;
    public double remainingQuantity;
    @NonNull
    public String unit = "件";
    @NonNull
    public String storageMethod = "room_temp";
    @NonNull
    public String location = FoodData.LOCATION_UNSPECIFIED;
    @NonNull
    public String notes = "";
    @NonNull
    public String createdAt = "";
    @NonNull
    public String updatedAt = "";
    @NonNull
    public String status = "active";
    @NonNull
    public String finishedAt = "";
    public int sortOrder;
}

@Entity(tableName = "barcodes")
final class RoomBarcodeEntity {
    @PrimaryKey
    @NonNull
    public String barcode = "";
    @NonNull
    public String format = "";
    @NonNull
    public String createdAt = "";
    @NonNull
    public String lastConfirmedAt = "";
}

@Entity(
        tableName = "product_barcodes",
        primaryKeys = {"productId", "barcode"},
        foreignKeys = {
                @ForeignKey(
                        entity = RoomProductEntity.class,
                        parentColumns = "productId",
                        childColumns = "productId",
                        onDelete = ForeignKey.CASCADE,
                        deferred = true
                ),
                @ForeignKey(
                        entity = RoomBarcodeEntity.class,
                        parentColumns = "barcode",
                        childColumns = "barcode",
                        onDelete = ForeignKey.CASCADE,
                        deferred = true
                )
        },
        indices = {@Index("productId"), @Index("barcode")}
)
final class RoomProductBarcodeEntity {
    @NonNull
    public String productId = "";
    @NonNull
    public String barcode = "";
}

@Entity(
        tableName = "batch_reminder_plans",
        foreignKeys = @ForeignKey(
                entity = RoomBatchEntity.class,
                parentColumns = "batchId",
                childColumns = "batchId",
                onDelete = ForeignKey.CASCADE,
                deferred = true
        ),
        indices = @Index("batchId")
)
final class RoomReminderPlanEntity {
    @PrimaryKey
    @NonNull
    public String batchId = "";
    @NonNull
    public String mode = "smart";
    @NonNull
    public String offsetsJson = "[]";
    @NonNull
    public String fingerprint = "";
    public int plannedDaysLeft = Integer.MIN_VALUE;
    @NonNull
    public String plannedOn = "";
}

@Entity(tableName = "inventory_metadata")
final class RoomInventoryMetadataEntity {
    @PrimaryKey
    @NonNull
    public String metadataKey = "";
    @NonNull
    public String metadataValue = "";
}
