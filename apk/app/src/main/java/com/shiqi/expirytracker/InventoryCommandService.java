package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class InventoryCommandService {
    interface TimeSource {
        String today();

        String now();
    }

    private final InventoryRepository repository;
    private final TimeSource timeSource;

    InventoryCommandService(InventoryRepository repository, TimeSource timeSource) {
        if (repository == null || timeSource == null) {
            throw new IllegalArgumentException("repository and timeSource are required");
        }
        this.repository = repository;
        this.timeSource = timeSource;
    }

    static TimeSource systemTimeSource() {
        return new TimeSource() {
            @Override
            public String today() {
                return DateRules.todayString();
            }

            @Override
            public String now() {
                return DateRules.nowIsoLike();
            }
        };
    }

    InventoryCommandResult add(FoodItem draft) {
        return add(draft, false);
    }

    InventoryCommandResult addAsSeparateProduct(FoodItem draft) {
        return add(draft, true);
    }

    InventoryCommandResult replaceAllValidated(List<FoodItem> drafts) {
        List<FoodItem> candidates = new ArrayList<FoodItem>();
        List<InventoryCommandResult.FieldError> errors =
                new ArrayList<InventoryCommandResult.FieldError>();
        Set<String> batchIds = new LinkedHashSet<String>();
        int row = 0;
        if (drafts != null) {
            for (FoodItem draft : drafts) {
                row++;
                FoodItem candidate = normalizedDraft(draft, null);
                for (InventoryCommandResult.FieldError error : validate(candidate)) {
                    errors.add(new InventoryCommandResult.FieldError(
                            "row[" + row + "]." + error.field,
                            "第 " + row + " 条：" + error.message
                    ));
                }
                String batchId = FoodItem.cleanText(candidate.id);
                if (batchId.length() == 0 || !batchIds.add(batchId)) {
                    errors.add(new InventoryCommandResult.FieldError(
                            "row[" + row + "].id",
                            "第 " + row + " 条：批次编号缺失或重复。"
                    ));
                }
                candidates.add(candidate);
            }
        }
        if (!errors.isEmpty()) {
            return InventoryCommandResult.validation(deduplicate(errors));
        }
        return save(candidates, null, null);
    }

    private InventoryCommandResult add(FoodItem draft, boolean allowExistingBarcode) {
        List<FoodItem> foods = copyFoods(repository.loadFoods());
        FoodItem candidate = normalizedDraft(draft, null);
        List<InventoryCommandResult.FieldError> errors = validate(candidate);
        if (!errors.isEmpty()) {
            return InventoryCommandResult.validation(errors);
        }

        List<String> barcodeMatches = profilesForBarcode(foods, candidate.barcode);
        if (!allowExistingBarcode && !barcodeMatches.isEmpty()) {
            return InventoryCommandResult.conflict(
                    "barcode",
                    "这个条码已经关联本机商品，请先选择商品再补货。",
                    barcodeMatches
            );
        }

        candidate.id = FoodIdGenerator.nextId(foods, null);
        candidate.productProfileId = newProfileId();
        candidate.createdAt = timeSource.now();
        candidate.updatedAt = candidate.createdAt;
        candidate.remainingQuantity = candidate.quantity;
        foods.add(candidate.copy());
        return save(foods, null, candidate);
    }

    InventoryCommandResult edit(String batchId, FoodItem draft) {
        List<FoodItem> foods = copyFoods(repository.loadFoods());
        int index = indexOfBatch(foods, batchId);
        if (index < 0) {
            return InventoryCommandResult.notFound("这个批次已不存在。" );
        }

        FoodItem before = foods.get(index).copy();
        FoodItem candidate = normalizedDraft(draft, before);
        List<InventoryCommandResult.FieldError> errors = validate(candidate);
        if (!errors.isEmpty()) {
            return InventoryCommandResult.validation(errors);
        }

        candidate.id = before.id;
        candidate.createdAt = before.createdAt;
        candidate.updatedAt = timeSource.now();
        candidate.remainingQuantity = FoodItem.clampedRemainingQuantity(
                candidate.remainingQuantity,
                candidate.quantity
        );

        boolean barcodeChanged = !BarcodeUtils.toGtin14(before.barcode).equals(
                BarcodeUtils.toGtin14(candidate.barcode)
        );
        if (barcodeChanged) {
            candidate.productProfileId = newProfileId();
            foods.set(index, candidate.copy());
        } else {
            candidate.productProfileId = before.productProfileId;
            foods = ProductProfileEditor.applyEdit(foods, before, candidate);
        }
        return save(foods, before, candidate);
    }

    InventoryCommandResult replenish(String productProfileId, FoodItem draft) {
        List<FoodItem> foods = copyFoods(repository.loadFoods());
        FoodItem source = representativeForProfile(foods, productProfileId);
        if (source == null) {
            return InventoryCommandResult.notFound("没有找到可补货的商品档案。" );
        }

        FoodItem candidate = normalizedDraft(draft, null);
        candidate.name = source.name;
        candidate.category = FoodData.normalizeCategoryValue(source.category);
        candidate.unit = fallback(source.unit, "件");
        candidate.storageMethod = fallback(candidate.storageMethod, source.storageMethod);
        candidate.location = FoodData.normalizeLocationValue(
                fallback(candidate.location, source.location)
        );
        candidate.barcode = source.barcode;
        List<InventoryCommandResult.FieldError> errors = validate(candidate);
        if (!errors.isEmpty()) {
            return InventoryCommandResult.validation(errors);
        }

        candidate.id = FoodIdGenerator.nextId(foods, null);
        candidate.productProfileId = source.productProfileId;
        candidate.createdAt = timeSource.now();
        candidate.updatedAt = candidate.createdAt;
        candidate.remainingQuantity = candidate.quantity;
        foods.add(candidate.copy());
        return save(foods, null, candidate);
    }

    InventoryCommandResult decrement(String batchId, double amount) {
        List<FoodItem> foods = copyFoods(repository.loadFoods());
        int index = indexOfBatch(foods, batchId);
        if (index < 0) {
            return InventoryCommandResult.notFound("这个批次已不存在。" );
        }
        if (!Double.isFinite(amount) || amount <= 0d) {
            List<InventoryCommandResult.FieldError> errors =
                    new ArrayList<InventoryCommandResult.FieldError>();
            errors.add(new InventoryCommandResult.FieldError(
                    "quantity",
                    "减少数量必须大于 0。"
            ));
            return InventoryCommandResult.validation(errors);
        }

        FoodItem before = foods.get(index).copy();
        if (InventoryRules.isFinished(before)) {
            return InventoryCommandResult.conflict(
                    "quantity",
                    "当前批次已经用完，不能继续减少。",
                    null
            );
        }
        FoodItem after = before.copy();
        after.remainingQuantity = FoodItem.clampedRemainingQuantity(
                after.remainingQuantity - amount,
                after.quantity
        );
        after.updatedAt = timeSource.now();
        InventoryRules.finishIfDepleted(after, after.updatedAt);
        foods.set(index, after.copy());
        return save(foods, before, after);
    }

    InventoryCommandResult finish(String batchId) {
        List<FoodItem> foods = copyFoods(repository.loadFoods());
        int index = indexOfBatch(foods, batchId);
        if (index < 0) {
            return InventoryCommandResult.notFound("这个批次已不存在。" );
        }
        FoodItem before = foods.get(index).copy();
        FoodItem after = before.copy();
        after.remainingQuantity = 0d;
        after.updatedAt = timeSource.now();
        InventoryRules.finishIfDepleted(after, after.updatedAt);
        foods.set(index, after.copy());
        return save(foods, before, after);
    }

    InventoryCommandResult restore(String batchId) {
        List<FoodItem> foods = copyFoods(repository.loadFoods());
        int index = indexOfBatch(foods, batchId);
        if (index < 0) {
            return InventoryCommandResult.notFound("这个批次已不存在。" );
        }
        FoodItem before = foods.get(index).copy();
        FoodItem after = before.copy();
        InventoryRules.restoreToActive(after);
        after.updatedAt = timeSource.now();
        foods.set(index, after.copy());
        return save(foods, before, after);
    }

    InventoryCommandResult deleteBatch(String batchId) {
        List<FoodItem> foods = copyFoods(repository.loadFoods());
        int index = indexOfBatch(foods, batchId);
        if (index < 0) {
            return InventoryCommandResult.notFound("这个批次已不存在。" );
        }
        FoodItem before = foods.remove(index);
        return save(foods, before, null);
    }

    InventoryCommandResult deleteProduct(String productProfileId) {
        String wanted = FoodItem.cleanText(productProfileId);
        List<FoodItem> foods = copyFoods(repository.loadFoods());
        List<FoodItem> remaining = new ArrayList<FoodItem>();
        FoodItem firstRemoved = null;
        for (FoodItem food : foods) {
            if (wanted.equals(FoodItem.cleanText(food.productProfileId))) {
                if (firstRemoved == null) {
                    firstRemoved = food.copy();
                }
            } else {
                remaining.add(food.copy());
            }
        }
        if (firstRemoved == null) {
            return InventoryCommandResult.notFound("这个商品档案已不存在。" );
        }
        return save(remaining, firstRemoved, null);
    }

    private InventoryCommandResult save(
            List<FoodItem> foods,
            FoodItem before,
            FoodItem after
    ) {
        return repository.saveFoods(copyFoods(foods))
                ? InventoryCommandResult.success(before, after)
                : InventoryCommandResult.storageError();
    }

    private FoodItem normalizedDraft(FoodItem draft, FoodItem original) {
        FoodItem result = draft == null ? new FoodItem() : draft.copy();
        result.name = FoodItem.cleanText(result.name);
        result.barcode = BarcodeUtils.digitsOnly(result.barcode);
        result.category = FoodData.normalizeCategoryValue(result.category);
        result.unit = fallback(result.unit, "件");
        result.storageMethod = fallback(result.storageMethod, "room_temp");
        result.location = FoodData.normalizeLocationValue(result.location);
        result.notes = FoodItem.cleanText(result.notes);
        result.productionDate = FoodItem.cleanText(result.productionDate);
        result.expiryDate = FoodItem.cleanText(result.expiryDate);
        result.openedDate = FoodItem.cleanText(result.openedDate);
        result.dateSource = FoodItem.cleanText(result.dateSource);

        if (result.dateSource.length() == 0) {
            result.dateSource = result.expiryDate.length() > 0 ? "manual" : "unknown";
        }
        if ("calculated".equals(result.dateSource)
                && DateRules.isValidDateString(result.productionDate)
                && result.shelfLifeValue != null
                && result.shelfLifeValue.intValue() > 0
                && DateRules.isShelfLifeUnit(result.shelfLifeUnit)) {
            result.expiryDate = DateRules.addShelfLife(
                    result.productionDate,
                    result.shelfLifeValue,
                    result.shelfLifeUnit
            );
        } else if ("unknown".equals(result.dateSource) || "none".equals(result.dateSource)) {
            result.expiryDate = "";
            if ("none".equals(result.dateSource)) {
                result.shelfLifeValue = null;
                result.shelfLifeUnit = "";
            }
        }

        if (original != null) {
            result.id = original.id;
            result.productProfileId = original.productProfileId;
            result.createdAt = original.createdAt;
        }
        return result;
    }

    private List<InventoryCommandResult.FieldError> validate(FoodItem candidate) {
        List<InventoryCommandResult.FieldError> errors =
                new ArrayList<InventoryCommandResult.FieldError>();
        if (candidate.name.length() == 0) {
            errors.add(new InventoryCommandResult.FieldError(
                    "name",
                    "请填写商品名称。"
            ));
        }
        if (!Double.isFinite(candidate.quantity) || candidate.quantity <= 0d) {
            errors.add(new InventoryCommandResult.FieldError(
                    "quantity",
                    "新批次数量必须大于 0。"
            ));
        }
        if (Double.isFinite(candidate.quantity)
                && candidate.quantity > 0d
                && (!Double.isFinite(candidate.remainingQuantity)
                        || candidate.remainingQuantity < 0d
                        || candidate.remainingQuantity > candidate.quantity)) {
            errors.add(new InventoryCommandResult.FieldError(
                    "remainingQuantity",
                    "剩余数量必须在 0 和入库数量之间。"
            ));
        }
        if (candidate.barcode.length() > 0
                && !BarcodeUtils.isSupportedProductCode(candidate.barcode)) {
            errors.add(new InventoryCommandResult.FieldError(
                    "barcode",
                    "条码校验失败，请重新扫描或留空。"
            ));
        }

        String productionError = FoodValidationRules.productionDateError(
                candidate.productionDate,
                timeSource.today()
        );
        if (productionError.length() > 0) {
            errors.add(new InventoryCommandResult.FieldError("productionDate", productionError));
        }

        if ("calculated".equals(candidate.dateSource)) {
            if (!DateRules.isValidDateString(candidate.productionDate)) {
                errors.add(new InventoryCommandResult.FieldError(
                        "productionDate",
                        "请填写有效的生产日期。"
                ));
            }
            if (candidate.shelfLifeValue == null
                    || candidate.shelfLifeValue.intValue() <= 0
                    || !DateRules.isShelfLifeUnit(candidate.shelfLifeUnit)) {
                errors.add(new InventoryCommandResult.FieldError(
                        "shelfLife",
                        "请填写有效的保质期和单位。"
                ));
            }
        } else if ("manual".equals(candidate.dateSource)) {
            String expiryError = FoodValidationRules.expiryDateError(
                    candidate.productionDate,
                    candidate.expiryDate
            );
            if (expiryError.length() > 0) {
                errors.add(new InventoryCommandResult.FieldError("expiryDate", expiryError));
            }
        } else if (!"unknown".equals(candidate.dateSource)
                && !"none".equals(candidate.dateSource)) {
            errors.add(new InventoryCommandResult.FieldError(
                    "dateSource",
                    "日期状态无效，请重新选择日期填写方式。"
            ));
        }

        if (candidate.openedDate.length() > 0) {
            String openedError = FoodValidationRules.openedDateError(
                    candidate.productionDate,
                    candidate.openedDate,
                    candidate.expiryDate,
                    timeSource.today()
            );
            if (openedError.length() > 0) {
                errors.add(new InventoryCommandResult.FieldError("openedDate", openedError));
            }
        }
        return deduplicate(errors);
    }

    private static List<InventoryCommandResult.FieldError> deduplicate(
            List<InventoryCommandResult.FieldError> errors
    ) {
        List<InventoryCommandResult.FieldError> result =
                new ArrayList<InventoryCommandResult.FieldError>();
        Set<String> seen = new LinkedHashSet<String>();
        for (InventoryCommandResult.FieldError error : errors) {
            String key = error.field + "\n" + error.message;
            if (seen.add(key)) {
                result.add(error);
            }
        }
        return result;
    }

    private static List<String> profilesForBarcode(List<FoodItem> foods, String barcode) {
        String wanted = BarcodeUtils.toGtin14(barcode);
        Set<String> profiles = new LinkedHashSet<String>();
        if (wanted.length() == 0) {
            return new ArrayList<String>();
        }
        for (FoodItem food : foods) {
            if (wanted.equals(BarcodeUtils.toGtin14(food.barcode))) {
                String profileId = FoodItem.cleanText(food.productProfileId);
                if (profileId.length() > 0) {
                    profiles.add(profileId);
                }
            }
        }
        return new ArrayList<String>(profiles);
    }

    private static FoodItem representativeForProfile(
            List<FoodItem> foods,
            String productProfileId
    ) {
        String wanted = FoodItem.cleanText(productProfileId);
        FoodItem selected = null;
        for (FoodItem food : foods) {
            if (!wanted.equals(FoodItem.cleanText(food.productProfileId))) {
                continue;
            }
            if (selected == null
                    || FoodItem.cleanText(food.updatedAt).compareTo(
                    FoodItem.cleanText(selected.updatedAt)
            ) > 0) {
                selected = food;
            }
        }
        return selected == null ? null : selected.copy();
    }

    private static int indexOfBatch(List<FoodItem> foods, String batchId) {
        String wanted = FoodItem.cleanText(batchId);
        for (int index = 0; index < foods.size(); index++) {
            if (wanted.equals(FoodItem.cleanText(foods.get(index).id))) {
                return index;
            }
        }
        return -1;
    }

    private static List<FoodItem> copyFoods(List<FoodItem> source) {
        List<FoodItem> copy = new ArrayList<FoodItem>();
        if (source != null) {
            for (FoodItem food : source) {
                if (food != null) {
                    copy.add(food.copy());
                }
            }
        }
        return copy;
    }

    private static String fallback(String value, String fallback) {
        String cleaned = FoodItem.cleanText(value);
        return cleaned.length() > 0 ? cleaned : fallback;
    }

    private static String newProfileId() {
        return "profile_" + UUID.randomUUID().toString();
    }
}
