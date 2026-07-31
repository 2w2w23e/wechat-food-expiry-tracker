package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;

final class InventorySnapshot {
    final List<ProductRecord> products = new ArrayList<ProductRecord>();
    final List<BatchRecord> batches = new ArrayList<BatchRecord>();
    final List<BarcodeRecord> barcodes = new ArrayList<BarcodeRecord>();
    final List<ProductBarcodeRecord> productBarcodes = new ArrayList<ProductBarcodeRecord>();
    final List<ReminderPlanRecord> reminderPlans = new ArrayList<ReminderPlanRecord>();

    static final class ProductRecord {
        String productId = "";
        String name = "";
        String category = "other";
        String defaultUnit = "件";
        String defaultStorageMethod = "room_temp";
        String defaultLocation = FoodData.LOCATION_UNSPECIFIED;
        Integer defaultShelfLifeValue;
        String defaultShelfLifeUnit = "";
        String notes = "";
        String createdAt = "";
        String updatedAt = "";
        int sortOrder;
    }

    static final class BatchRecord {
        String batchId = "";
        String productId = "";
        String sourceBarcode = "";
        String productionDate = "";
        Integer shelfLifeValue;
        String shelfLifeUnit = "";
        String openedDate = "";
        Integer afterOpenShelfLifeValue;
        String afterOpenShelfLifeUnit = "";
        String expiryDate = "";
        String dateSource = "unknown";
        double quantity;
        double remainingQuantity;
        String unit = "件";
        String storageMethod = "room_temp";
        String location = FoodData.LOCATION_UNSPECIFIED;
        String notes = "";
        String createdAt = "";
        String updatedAt = "";
        String status = "active";
        String finishedAt = "";
        int sortOrder;
    }

    static final class BarcodeRecord {
        String barcode = "";
        String format = "";
        String createdAt = "";
        String lastConfirmedAt = "";
    }

    static final class ProductBarcodeRecord {
        String productId = "";
        String barcode = "";
    }

    static final class ReminderPlanRecord {
        String batchId = "";
        String mode = "smart";
        String offsetsJson = "[]";
        String fingerprint = "";
        int plannedDaysLeft = Integer.MIN_VALUE;
        String plannedOn = "";
    }

    static final class SnapshotException extends Exception {
        SnapshotException(String message) {
            super(message);
        }

        SnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
