package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;

public final class InventoryCommandServiceTest {
    private int passed;

    public static void main(String[] args) {
        new InventoryCommandServiceTest().run();
    }

    private void run() {
        validatesAllFieldsAtOnce();
        savesUnknownDateWithoutInventingNoExpiry();
        calculatesCanonicalExpiry();
        resolvesExistingBarcodeBeforeAdd();
        replenishesAsIndependentBatch();
        editsSharedIdentityWithoutChangingSiblingBatchData();
        detachesOneBatchWhenBarcodeChanges();
        decrementsAndFinishesAtomically();
        leavesRepositoryUnchangedOnSaveFailure();
        deletesEveryBatchOnlyAfterProductCommand();
        validatesImportReplacementAtomically();
        System.out.println("InventoryCommandServiceTest: " + passed + " passed");
    }

    private void validatesAllFieldsAtOnce() {
        FakeRepository repository = new FakeRepository();
        FoodItem draft = new FoodItem();
        draft.quantity = 0d;
        draft.barcode = "123";
        draft.productionDate = "2026-09-01";
        draft.dateSource = "calculated";
        draft.shelfLifeValue = null;
        draft.shelfLifeUnit = "";

        InventoryCommandResult result = service(repository).add(draft);

        expect(InventoryCommandResult.Status.VALIDATION_ERROR, result.status, "validation status");
        expect(5, result.errors.size(), "all independent errors are returned");
        expect("name", result.errors.get(0).field, "first error is stable");
        expect("quantity", result.errors.get(1).field, "quantity error is not short circuited");
        expect("barcode", result.errors.get(2).field, "barcode error is returned");
        expect("productionDate", result.errors.get(3).field, "future production is returned");
        expect("shelfLife", result.errors.get(4).field, "shelf life error is returned");
        expect(0, repository.foods.size(), "invalid command does not write");
    }

    private void savesUnknownDateWithoutInventingNoExpiry() {
        FakeRepository repository = new FakeRepository();
        FoodItem draft = draft("待补日期食品", 2d);
        draft.dateSource = "unknown";

        InventoryCommandResult result = service(repository).add(draft);

        expect(true, result.isSuccess(), "unknown date add succeeds");
        expect(1, repository.foods.size(), "one batch saved");
        expect("unknown", repository.foods.get(0).dateSource, "date remains pending");
        expect("", repository.foods.get(0).expiryDate, "no expiry is invented");
        expect(true, repository.foods.get(0).id.startsWith("food_"), "batch UUID generated");
        expect(true, repository.foods.get(0).productProfileId.startsWith("profile_"), "profile UUID generated");
    }

    private void calculatesCanonicalExpiry() {
        FakeRepository repository = new FakeRepository();
        FoodItem draft = draft("月末食品", 1d);
        draft.productionDate = "2026-01-31";
        draft.shelfLifeValue = Integer.valueOf(1);
        draft.shelfLifeUnit = "month";
        draft.dateSource = "calculated";
        draft.expiryDate = "2099-12-31";

        InventoryCommandResult result = service(repository).add(draft);

        expect(true, result.isSuccess(), "calculated add succeeds");
        expect("2026-02-28", repository.foods.get(0).expiryDate, "service owns canonical expiry");
    }

    private void resolvesExistingBarcodeBeforeAdd() {
        FakeRepository repository = new FakeRepository();
        FoodItem existing = batch("batch-a", "profile-a", "原商品", "2026-09-30", 1d);
        existing.barcode = "6917246211883";
        repository.foods.add(existing);
        FoodItem draft = draft("另一商品", 1d);
        draft.barcode = existing.barcode;

        InventoryCommandResult conflict = service(repository).add(draft);
        expect(InventoryCommandResult.Status.CONFLICT, conflict.status, "known barcode requires resolution");
        expect(1, conflict.candidateProductProfileIds.size(), "matching profile is exposed");
        expect("profile-a", conflict.candidateProductProfileIds.get(0), "matching profile id");
        expect(1, repository.foods.size(), "conflict does not write");

        InventoryCommandResult separate = service(repository).addAsSeparateProduct(draft);
        expect(true, separate.isSuccess(), "explicit separate product is allowed");
        expect(2, repository.foods.size(), "same barcode can map to two profiles");
        expect(false, "profile-a".equals(repository.foods.get(1).productProfileId), "separate profile created");
    }

    private void replenishesAsIndependentBatch() {
        FakeRepository repository = new FakeRepository();
        FoodItem existing = batch("batch-a", "profile-a", "鲜牛奶", "2026-09-02", 1d);
        existing.barcode = "6917246211883";
        existing.storageMethod = "refrigerated";
        repository.foods.add(existing);
        FoodItem draft = draft("错误名称不会覆盖档案", 3d);
        draft.productionDate = "2026-08-31";
        draft.expiryDate = "2026-09-07";
        draft.dateSource = "manual";
        draft.storageMethod = "refrigerated";

        InventoryCommandResult result = service(repository).replenish("profile-a", draft);

        expect(true, result.isSuccess(), "replenishment succeeds");
        expect(2, repository.foods.size(), "new batch added");
        FoodItem added = repository.foods.get(1);
        expect("profile-a", added.productProfileId, "profile reused");
        expect("鲜牛奶", added.name, "identity reused");
        expect("2026-09-07", added.expiryDate, "new batch date kept");
        expect(3d, added.remainingQuantity, "new quantity independent");
        expect(false, added.id.equals(existing.id), "new batch id generated");
    }

    private void editsSharedIdentityWithoutChangingSiblingBatchData() {
        FakeRepository repository = new FakeRepository();
        FoodItem first = batch("batch-a", "profile-a", "旧名称", "2026-09-02", 1d);
        FoodItem second = batch("batch-b", "profile-a", "旧名称", "2026-10-02", 2d);
        repository.foods.add(first);
        repository.foods.add(second);
        FoodItem edited = first.copy();
        edited.name = "新名称";
        edited.category = "beverage";

        InventoryCommandResult result = service(repository).edit(first.id, edited);

        expect(true, result.isSuccess(), "edit succeeds");
        expect("新名称", repository.foods.get(0).name, "edited batch name updated");
        expect("新名称", repository.foods.get(1).name, "shared profile name updated");
        expect("2026-10-02", repository.foods.get(1).expiryDate, "sibling batch date preserved");
        expect(2d, repository.foods.get(1).quantity, "sibling quantity preserved");
    }

    private void detachesOneBatchWhenBarcodeChanges() {
        FakeRepository repository = new FakeRepository();
        FoodItem first = batch("batch-a", "profile-a", "商品", "2026-09-02", 1d);
        first.barcode = "6917246211883";
        FoodItem second = batch("batch-b", "profile-a", "商品", "2026-10-02", 1d);
        second.barcode = first.barcode;
        repository.foods.add(first);
        repository.foods.add(second);
        FoodItem edited = first.copy();
        edited.barcode = "6901234567892";

        InventoryCommandResult result = service(repository).edit(first.id, edited);

        expect(true, result.isSuccess(), "barcode edit succeeds");
        expect(false, "profile-a".equals(repository.foods.get(0).productProfileId), "edited batch detaches");
        expect("profile-a", repository.foods.get(1).productProfileId, "sibling profile stays");
        expect("6917246211883", repository.foods.get(1).barcode, "sibling barcode stays");
    }

    private void decrementsAndFinishesAtomically() {
        FakeRepository repository = new FakeRepository();
        repository.foods.add(batch("batch-a", "profile-a", "商品", "2026-09-02", 0.5d));

        InventoryCommandResult result = service(repository).decrement("batch-a", 1d);

        expect(true, result.isSuccess(), "decrement succeeds");
        expect(0d, repository.foods.get(0).remainingQuantity, "decrement clamps at zero");
        expect(true, repository.foods.get(0).isFinished, "zero batch becomes finished");
        expect(0.5d, result.before.remainingQuantity, "result keeps undo snapshot");

        InventoryCommandResult restored = service(repository).restore("batch-a");
        expect(true, restored.isSuccess(), "finished batch restores");
        expect(false, repository.foods.get(0).isFinished, "restored batch is active");
        expect(0.5d, repository.foods.get(0).remainingQuantity, "restore respects batch quantity");
    }

    private void leavesRepositoryUnchangedOnSaveFailure() {
        FakeRepository repository = new FakeRepository();
        repository.foods.add(batch("batch-a", "profile-a", "商品", "2026-09-02", 2d));
        repository.failNextSave = true;

        InventoryCommandResult result = service(repository).decrement("batch-a", 1d);

        expect(InventoryCommandResult.Status.STORAGE_ERROR, result.status, "save failure is structured");
        expect(2d, repository.foods.get(0).remainingQuantity, "failed save leaves old snapshot");
    }

    private void deletesEveryBatchOnlyAfterProductCommand() {
        FakeRepository repository = new FakeRepository();
        repository.foods.add(batch("batch-a", "profile-a", "商品 A", "2026-09-02", 1d));
        repository.foods.add(batch("batch-b", "profile-a", "商品 A", "2026-10-02", 1d));
        repository.foods.add(batch("batch-c", "profile-b", "商品 B", "2026-11-02", 1d));

        InventoryCommandResult result = service(repository).deleteProduct("profile-a");

        expect(true, result.isSuccess(), "delete product succeeds");
        expect(1, repository.foods.size(), "all selected profile batches removed");
        expect("profile-b", repository.foods.get(0).productProfileId, "other profile remains");
    }

    private void validatesImportReplacementAtomically() {
        FakeRepository repository = new FakeRepository();
        repository.foods.add(batch("old", "profile-old", "旧商品", "2026-09-02", 1d));
        FoodItem first = batch("import-a", "profile-a", "导入 A", "2026-10-02", 2d);
        FoodItem duplicate = batch("import-a", "profile-b", "导入 B", "2026-11-02", 1d);

        InventoryCommandResult rejected = service(repository).replaceAllValidated(
                java.util.Arrays.asList(first, duplicate)
        );

        expect(InventoryCommandResult.Status.VALIDATION_ERROR, rejected.status, "duplicate import is rejected");
        expect(1, repository.foods.size(), "rejected import keeps old snapshot");
        expect("old", repository.foods.get(0).id, "old batch remains after rejection");

        duplicate.id = "import-b";
        InventoryCommandResult applied = service(repository).replaceAllValidated(
                java.util.Arrays.asList(first, duplicate)
        );
        expect(true, applied.isSuccess(), "validated import replacement succeeds");
        expect(2, repository.foods.size(), "replacement writes every validated row");
        expect("import-a", repository.foods.get(0).id, "first imported id is preserved");
        expect("import-b", repository.foods.get(1).id, "second imported id is preserved");
    }

    private InventoryCommandService service(FakeRepository repository) {
        return new InventoryCommandService(
                repository,
                new InventoryCommandService.TimeSource() {
                    @Override
                    public String today() {
                        return "2026-08-31";
                    }

                    @Override
                    public String now() {
                        return "2026-08-31T12:00:00";
                    }
                }
        );
    }

    private static FoodItem draft(String name, double quantity) {
        FoodItem item = new FoodItem();
        item.name = name;
        item.quantity = quantity;
        item.remainingQuantity = quantity;
        item.dateSource = "unknown";
        return item;
    }

    private static FoodItem batch(
            String id,
            String profileId,
            String name,
            String expiry,
            double quantity
    ) {
        FoodItem item = draft(name, quantity);
        item.id = id;
        item.productProfileId = profileId;
        item.expiryDate = expiry;
        item.dateSource = "manual";
        item.createdAt = "2026-08-01T00:00:00";
        item.updatedAt = item.createdAt;
        return item;
    }

    private void expect(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + " expected=<" + expected + "> actual=<" + actual + ">"
            );
        }
        passed++;
    }

    private static final class FakeRepository implements InventoryRepository {
        final List<FoodItem> foods = new ArrayList<FoodItem>();
        boolean failNextSave;

        @Override
        public List<FoodItem> loadFoods() {
            return copy(foods);
        }

        @Override
        public boolean saveFoods(List<FoodItem> nextFoods) {
            if (failNextSave) {
                failNextSave = false;
                return false;
            }
            foods.clear();
            foods.addAll(copy(nextFoods));
            return true;
        }

        private static List<FoodItem> copy(List<FoodItem> values) {
            List<FoodItem> result = new ArrayList<FoodItem>();
            for (FoodItem item : values) {
                result.add(item.copy());
            }
            return result;
        }
    }
}
