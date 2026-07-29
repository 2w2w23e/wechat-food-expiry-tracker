package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InventoryPresentation {
    private InventoryPresentation() {
    }

    static final class ProductGroup {
        final String profileId;
        final String name;
        final String category;
        final String barcode;
        final String unit;
        final List<FoodItem> batches;
        final int activeBatchCount;
        final int finishedBatchCount;
        final double totalQuantity;
        final double totalRemainingQuantity;
        final String nearestExpiryDate;
        final boolean allActiveNoExpiry;
        final String status;

        private ProductGroup(String profileId, List<FoodItem> sourceBatches) {
            this.profileId = profileId;
            batches = copyAndSortBatches(sourceBatches);

            FoodItem representative = newestBatch(batches);
            name = representative == null ? "未命名商品" : fallback(representative.name, "未命名商品");
            category = representative == null ? "other" : representative.category;
            barcode = representative == null ? "" : FoodItem.cleanText(representative.barcode);
            unit = representative == null ? "件" : fallback(representative.unit, "件");

            int activeCount = 0;
            int finishedCount = 0;
            double quantity = 0;
            double remaining = 0;
            String nearestExpiry = "";
            int explicitNoExpiryCount = 0;
            for (FoodItem batch : batches) {
                if (batch.isFinished) {
                    finishedCount++;
                    continue;
                }
                activeCount++;
                if ("none".equals(batch.dateSource)) {
                    explicitNoExpiryCount++;
                }
                quantity += FoodItem.normalizedQuantity(batch.quantity);
                remaining += FoodItem.normalizedQuantity(batch.remainingQuantity);
                if (DateRules.isValidDateString(batch.expiryDate)
                        && (nearestExpiry.length() == 0 || batch.expiryDate.compareTo(nearestExpiry) < 0)) {
                    nearestExpiry = batch.expiryDate;
                }
            }
            activeBatchCount = activeCount;
            finishedBatchCount = finishedCount;
            totalQuantity = quantity;
            totalRemainingQuantity = remaining;
            nearestExpiryDate = nearestExpiry;
            allActiveNoExpiry = activeCount > 0 && explicitNoExpiryCount == activeCount;
            status = activeCount == 0
                    ? DateRules.STATUS_FINISHED
                    : DateRules.getExpiryStatus(nearestExpiry);
        }

        FoodItem decrementTarget() {
            for (FoodItem batch : batches) {
                if (!batch.isFinished && batch.remainingQuantity > 0) {
                    return batch.copy();
                }
            }
            return null;
        }

        FoodItem representativeBatch() {
            FoodItem representative = newestBatch(batches);
            return representative == null ? null : representative.copy();
        }
    }

    static List<ProductGroup> group(List<FoodItem> foods) {
        Map<String, List<FoodItem>> grouped = new LinkedHashMap<String, List<FoodItem>>();
        if (foods != null) {
            for (int index = 0; index < foods.size(); index++) {
                FoodItem item = foods.get(index);
                if (item == null) {
                    continue;
                }
                String key = profileKey(item);
                if (key.length() == 0) {
                    key = "row:" + index;
                }
                List<FoodItem> batches = grouped.get(key);
                if (batches == null) {
                    batches = new ArrayList<FoodItem>();
                    grouped.put(key, batches);
                }
                batches.add(item.copy());
            }
        }

        List<ProductGroup> result = new ArrayList<ProductGroup>();
        for (Map.Entry<String, List<FoodItem>> entry : grouped.entrySet()) {
            result.add(new ProductGroup(entry.getKey(), entry.getValue()));
        }
        Collections.sort(result, PRODUCT_COMPARATOR);
        return result;
    }

    static ProductGroup find(List<FoodItem> foods, String profileId) {
        String wanted = FoodItem.cleanText(profileId);
        if (wanted.length() == 0) {
            return null;
        }
        for (ProductGroup group : group(foods)) {
            if (wanted.equals(group.profileId)) {
                return group;
            }
        }
        return null;
    }

    static String profileKey(FoodItem item) {
        if (item == null) {
            return "";
        }
        String profileId = FoodItem.cleanText(item.productProfileId);
        if (profileId.length() > 0) {
            return profileId;
        }
        String batchId = FoodItem.cleanText(item.id);
        if (batchId.length() > 0) {
            return "batch:" + batchId;
        }
        return "";
    }

    static String displayBatchId(String value) {
        String batchId = FoodItem.cleanText(value);
        if (batchId.length() == 0) {
            return "未编号";
        }
        if (batchId.length() <= 10) {
            return batchId;
        }
        return "…" + batchId.substring(batchId.length() - 8);
    }

    private static List<FoodItem> copyAndSortBatches(List<FoodItem> source) {
        List<FoodItem> result = new ArrayList<FoodItem>();
        if (source != null) {
            for (FoodItem item : source) {
                if (item != null) {
                    result.add(item.copy());
                }
            }
        }
        Collections.sort(result, BATCH_COMPARATOR);
        return result;
    }

    private static FoodItem newestBatch(List<FoodItem> batches) {
        FoodItem newest = null;
        String newestTimestamp = "";
        if (batches == null) {
            return null;
        }
        for (FoodItem batch : batches) {
            String timestamp = effectiveTimestamp(batch);
            if (newest == null || timestamp.compareTo(newestTimestamp) > 0) {
                newest = batch;
                newestTimestamp = timestamp;
            }
        }
        return newest;
    }

    private static String effectiveTimestamp(FoodItem item) {
        String updatedAt = item == null ? "" : FoodItem.cleanText(item.updatedAt);
        if (updatedAt.length() > 0) {
            return updatedAt;
        }
        return item == null ? "" : FoodItem.cleanText(item.createdAt);
    }

    private static String fallback(String value, String fallback) {
        String cleaned = FoodItem.cleanText(value);
        return cleaned.length() > 0 ? cleaned : fallback;
    }

    private static int statusRank(String status) {
        if (DateRules.STATUS_EXPIRED.equals(status)) {
            return 0;
        }
        if (DateRules.STATUS_TODAY.equals(status)) {
            return 1;
        }
        if (DateRules.STATUS_SOON.equals(status)) {
            return 2;
        }
        if (DateRules.STATUS_NORMAL.equals(status)) {
            return 3;
        }
        if (DateRules.STATUS_UNKNOWN.equals(status)) {
            return 4;
        }
        return 5;
    }

    private static final Comparator<FoodItem> BATCH_COMPARATOR = new Comparator<FoodItem>() {
        @Override
        public int compare(FoodItem left, FoodItem right) {
            if (left.isFinished != right.isFinished) {
                return left.isFinished ? 1 : -1;
            }
            if (left.isFinished) {
                int finishedCompare = effectiveTimestamp(right).compareTo(effectiveTimestamp(left));
                if (finishedCompare != 0) {
                    return finishedCompare;
                }
                return FoodItem.cleanText(left.id).compareTo(FoodItem.cleanText(right.id));
            }

            boolean leftValid = DateRules.isValidDateString(left.expiryDate);
            boolean rightValid = DateRules.isValidDateString(right.expiryDate);
            if (leftValid && rightValid) {
                int expiryCompare = left.expiryDate.compareTo(right.expiryDate);
                if (expiryCompare != 0) {
                    return expiryCompare;
                }
            } else if (leftValid != rightValid) {
                return leftValid ? -1 : 1;
            }

            int createdCompare = effectiveTimestamp(left).compareTo(effectiveTimestamp(right));
            if (createdCompare != 0) {
                return createdCompare;
            }
            return FoodItem.cleanText(left.id).compareTo(FoodItem.cleanText(right.id));
        }
    };

    private static final Comparator<ProductGroup> PRODUCT_COMPARATOR =
            new Comparator<ProductGroup>() {
                @Override
                public int compare(ProductGroup left, ProductGroup right) {
                    int statusCompare = statusRank(left.status) - statusRank(right.status);
                    if (statusCompare != 0) {
                        return statusCompare;
                    }
                    boolean leftValid = DateRules.isValidDateString(left.nearestExpiryDate);
                    boolean rightValid = DateRules.isValidDateString(right.nearestExpiryDate);
                    if (leftValid && rightValid) {
                        int expiryCompare = left.nearestExpiryDate.compareTo(right.nearestExpiryDate);
                        if (expiryCompare != 0) {
                            return expiryCompare;
                        }
                    } else if (leftValid != rightValid) {
                        return leftValid ? -1 : 1;
                    }
                    int nameCompare = left.name.compareTo(right.name);
                    if (nameCompare != 0) {
                        return nameCompare;
                    }
                    return left.profileId.compareTo(right.profileId);
                }
            };
}
