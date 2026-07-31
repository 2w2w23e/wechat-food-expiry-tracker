package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InventorySnapshotMapperTest {
    private int passed;

    public static void main(String[] args) throws Exception {
        new InventorySnapshotMapperTest().run();
    }

    private void run() throws Exception {
        roundTripsEveryBatchField();
        preservesBarcodeMultiplicity();
        preservesMultipleBarcodesForOneProduct();
        splitsConflictingProfilesDeterministically();
        rejectsDuplicateBatchIds();
        rejectsNonFiniteQuantity();
        preservesEmptyInventory();
        System.out.println("InventorySnapshotMapperTest: " + passed + " passed");
    }

    private void roundTripsEveryBatchField() throws Exception {
        FoodItem first = food(
                "batch-a",
                "profile-a",
                "6926265313430",
                "上好佳薯条",
                "snack",
                "2026-01-20",
                "2026-10-20",
                2,
                1
        );
        first.shelfLifeValue = Integer.valueOf(9);
        first.shelfLifeUnit = "month";
        first.dateSource = "calculated";
        first.openedDate = "2026-02-01";
        first.afterOpenShelfLifeValue = Integer.valueOf(7);
        first.afterOpenShelfLifeUnit = "day";
        first.storageMethod = "refrigerated";
        first.location = "fridge";
        first.notes = "第一批";
        first.smartReminderOffsets.addAll(Arrays.asList(7, 3, 1, 0));
        first.smartReminderFingerprint = "smart-v1";
        first.smartReminderPlannedDaysLeft = 273;
        first.smartReminderPlannedOn = "2026-01-21";

        FoodItem second = first.copy();
        second.id = "batch-b";
        second.productionDate = "2026-02-20";
        second.expiryDate = "2026-11-20";
        second.quantity = 3;
        second.remainingQuantity = 3;
        second.notes = "第二批";
        second.createdAt = "2026-02-20T10:00:00+08:00";
        second.updatedAt = second.createdAt;

        InventorySnapshot snapshot = InventorySnapshotMapper.fromFoods(
                Arrays.asList(first, second)
        );
        List<FoodItem> restored = InventorySnapshotMapper.toFoods(snapshot);
        InventorySnapshot roundTrip = InventorySnapshotMapper.fromFoods(restored);

        expect(1, snapshot.products.size(), "one product");
        expect(2, snapshot.batches.size(), "two independent batches");
        expect(
                InventorySnapshotMapper.fingerprint(snapshot),
                InventorySnapshotMapper.fingerprint(roundTrip),
                "stable round-trip fingerprint"
        );
        FoodItem restoredFirst = find(restored, "batch-a");
        expect("2026-10-20", restoredFirst.expiryDate, "canonical expiryDate");
        expect("2026-02-01", restoredFirst.openedDate, "opened date");
        expect(Integer.valueOf(7), restoredFirst.afterOpenShelfLifeValue, "after-open shelf life");
        expect("第一批", restoredFirst.notes, "batch notes");
        expect(4, restoredFirst.smartReminderOffsets.size(), "smart reminder offsets");
        expect("smart-v1", restoredFirst.smartReminderFingerprint, "reminder fingerprint");
        expect(273, restoredFirst.smartReminderPlannedDaysLeft, "planned days");
    }

    private void preservesBarcodeMultiplicity() throws Exception {
        FoodItem first = food(
                "same-code-a",
                "profile-one",
                "6926265313430",
                "上好佳薯条",
                "snack",
                "2026-01-20",
                "2026-10-20",
                1,
                1
        );
        FoodItem second = food(
                "same-code-b",
                "profile-two",
                "6926265313430",
                "同码不同商品",
                "other",
                "2026-03-01",
                "2026-09-01",
                4,
                2
        );

        InventorySnapshot snapshot = InventorySnapshotMapper.fromFoods(
                Arrays.asList(first, second)
        );
        expect(2, snapshot.products.size(), "same barcode keeps two products");
        expect(1, snapshot.barcodes.size(), "normalized barcode stored once");
        expect(2, snapshot.productBarcodes.size(), "two product-barcode relations");
        expect(2, InventorySnapshotMapper.toFoods(snapshot).size(), "both batches restore");
    }

    private void preservesMultipleBarcodesForOneProduct() throws Exception {
        FoodItem first = food(
                "multi-code-a",
                "shared-product",
                "6926265313430",
                "同一商品",
                "snack",
                "2026-01-20",
                "2026-10-20",
                1,
                1
        );
        FoodItem second = food(
                "multi-code-b",
                "shared-product",
                "4006381333931",
                "同一商品",
                "snack",
                "2026-02-20",
                "2026-11-20",
                2,
                2
        );
        second.unit = "盒";

        InventorySnapshot snapshot = InventorySnapshotMapper.fromFoods(
                Arrays.asList(first, second)
        );
        List<FoodItem> restored = InventorySnapshotMapper.toFoods(snapshot);

        expect(1, snapshot.products.size(), "one product can retain multiple barcodes");
        expect(2, snapshot.barcodes.size(), "both barcodes are retained");
        expect(2, snapshot.productBarcodes.size(), "both product-barcode relations are retained");
        expect(
                "shared-product",
                find(restored, "multi-code-a").productProfileId,
                "first batch keeps the shared profile"
        );
        expect(
                "shared-product",
                find(restored, "multi-code-b").productProfileId,
                "second batch keeps the shared profile"
        );
        expect(
                "4006381333931",
                find(restored, "multi-code-b").barcode,
                "batch source barcode remains exact"
        );
        expect(
                "盒",
                find(restored, "multi-code-b").unit,
                "batch unit does not split product identity"
        );
    }

    private void splitsConflictingProfilesDeterministically() throws Exception {
        FoodItem first = food(
                "conflict-a",
                "shared-profile",
                "",
                "乙商品",
                "other",
                "",
                "",
                1,
                1
        );
        FoodItem second = food(
                "conflict-b",
                "shared-profile",
                "",
                "甲商品",
                "other",
                "",
                "",
                1,
                1
        );

        Map<String, String> firstMapping = profileByBatch(
                InventorySnapshotMapper.toFoods(
                        InventorySnapshotMapper.fromFoods(Arrays.asList(first, second))
                )
        );
        Map<String, String> secondMapping = profileByBatch(
                InventorySnapshotMapper.toFoods(
                        InventorySnapshotMapper.fromFoods(Arrays.asList(second, first))
                )
        );
        expect(firstMapping, secondMapping, "conflict split is independent of input order");
        expect(
                false,
                firstMapping.get("conflict-a").equals(firstMapping.get("conflict-b")),
                "conflicting identities never overwrite"
        );
        expect(
                true,
                firstMapping.get("conflict-a").startsWith("shared-profile"),
                "split id preserves profile lineage"
        );
    }

    private void rejectsDuplicateBatchIds() throws Exception {
        FoodItem first = food("duplicate", "one", "", "一", "other", "", "", 1, 1);
        FoodItem second = food("duplicate", "two", "", "二", "other", "", "", 1, 1);
        expectThrows(
                Arrays.asList(first, second),
                "duplicate batch ids are rejected"
        );
    }

    private void rejectsNonFiniteQuantity() throws Exception {
        FoodItem item = food("bad-number", "one", "", "一", "other", "", "", 1, 1);
        item.quantity = Double.NaN;
        expectThrows(Arrays.asList(item), "non-finite quantity is rejected");
    }

    private void preservesEmptyInventory() throws Exception {
        InventorySnapshot snapshot = InventorySnapshotMapper.fromFoods(
                new ArrayList<FoodItem>()
        );
        expect(0, snapshot.products.size(), "empty products");
        expect(0, InventorySnapshotMapper.toFoods(snapshot).size(), "empty round-trip");
        expect(
                InventorySnapshotMapper.fingerprint(snapshot),
                InventorySnapshotMapper.fingerprint(
                        InventorySnapshotMapper.fromFoods(new ArrayList<FoodItem>())
                ),
                "empty fingerprint is stable"
        );
    }

    private void expectThrows(List<FoodItem> foods, String message) throws Exception {
        try {
            InventorySnapshotMapper.fromFoods(foods);
            throw new AssertionError(message + " expected SnapshotException");
        } catch (InventorySnapshot.SnapshotException expected) {
            passed++;
        }
    }

    private static Map<String, String> profileByBatch(List<FoodItem> foods) {
        Map<String, String> result = new HashMap<String, String>();
        for (FoodItem food : foods) {
            result.put(food.id, food.productProfileId);
        }
        return result;
    }

    private static FoodItem find(List<FoodItem> foods, String id) {
        for (FoodItem food : foods) {
            if (id.equals(food.id)) {
                return food;
            }
        }
        throw new AssertionError("Missing batch " + id);
    }

    private static FoodItem food(
            String id,
            String profileId,
            String barcode,
            String name,
            String category,
            String productionDate,
            String expiryDate,
            double quantity,
            double remaining
    ) {
        FoodItem food = new FoodItem();
        food.id = id;
        food.productProfileId = profileId;
        food.barcode = barcode;
        food.name = name;
        food.category = category;
        food.productionDate = productionDate;
        food.expiryDate = expiryDate;
        food.quantity = quantity;
        food.remainingQuantity = remaining;
        food.createdAt = "2026-01-20T10:00:00+08:00";
        food.updatedAt = food.createdAt;
        return food;
    }

    private void expect(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + " expected=<" + expected + "> actual=<" + actual + ">"
            );
        }
        passed++;
    }
}
