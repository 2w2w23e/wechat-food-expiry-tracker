package com.shiqi.expirytracker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class InventorySnapshotMapper {
    private InventorySnapshotMapper() {
    }

    static InventorySnapshot fromFoods(List<FoodItem> source)
            throws InventorySnapshot.SnapshotException {
        List<PreparedItem> prepared = prepareItems(source);
        Map<String, TreeSet<String>> signaturesByProfile = collectProfileSignatures(prepared);
        Map<String, String> assignedProfileSignatures = new HashMap<String, String>();
        Map<String, InventorySnapshot.ProductRecord> products =
                new LinkedHashMap<String, InventorySnapshot.ProductRecord>();
        Map<String, InventorySnapshot.BarcodeRecord> barcodes =
                new LinkedHashMap<String, InventorySnapshot.BarcodeRecord>();
        Set<String> relations = new HashSet<String>();
        InventorySnapshot snapshot = new InventorySnapshot();

        for (PreparedItem preparedItem : prepared) {
            FoodItem item = preparedItem.item;
            String productId = resolveProductId(
                    preparedItem,
                    signaturesByProfile,
                    assignedProfileSignatures
            );
            InventorySnapshot.ProductRecord product = products.get(productId);
            if (product == null) {
                product = productRecord(item, productId, products.size());
                products.put(productId, product);
            } else {
                mergeProductTimestamps(product, item);
            }

            snapshot.batches.add(batchRecord(item, productId, preparedItem.sortOrder));
            snapshot.reminderPlans.add(reminderRecord(item));
            addBarcodeRecords(item, productId, barcodes, relations, snapshot.productBarcodes);
        }

        snapshot.products.addAll(products.values());
        snapshot.barcodes.addAll(barcodes.values());
        return snapshot;
    }

    static List<FoodItem> toFoods(InventorySnapshot snapshot)
            throws InventorySnapshot.SnapshotException {
        if (snapshot == null) {
            throw new InventorySnapshot.SnapshotException("Inventory snapshot is missing.");
        }

        Map<String, InventorySnapshot.ProductRecord> products =
                new HashMap<String, InventorySnapshot.ProductRecord>();
        for (InventorySnapshot.ProductRecord product : snapshot.products) {
            String productId = clean(product.productId);
            if (productId.length() == 0 || products.put(productId, product) != null) {
                throw new InventorySnapshot.SnapshotException("Product ids must be non-empty and unique.");
            }
        }

        Map<String, InventorySnapshot.ReminderPlanRecord> reminders =
                new HashMap<String, InventorySnapshot.ReminderPlanRecord>();
        for (InventorySnapshot.ReminderPlanRecord reminder : snapshot.reminderPlans) {
            String batchId = clean(reminder.batchId);
            if (batchId.length() == 0 || reminders.put(batchId, reminder) != null) {
                throw new InventorySnapshot.SnapshotException("Reminder batch ids must be unique.");
            }
        }

        Map<String, String> fallbackBarcodes = firstBarcodeByProduct(snapshot.productBarcodes);
        List<InventorySnapshot.BatchRecord> ordered =
                new ArrayList<InventorySnapshot.BatchRecord>(snapshot.batches);
        Collections.sort(ordered, new Comparator<InventorySnapshot.BatchRecord>() {
            @Override
            public int compare(
                    InventorySnapshot.BatchRecord left,
                    InventorySnapshot.BatchRecord right
            ) {
                int order = Integer.compare(left.sortOrder, right.sortOrder);
                return order != 0 ? order : clean(left.batchId).compareTo(clean(right.batchId));
            }
        });

        Set<String> batchIds = new HashSet<String>();
        List<FoodItem> foods = new ArrayList<FoodItem>();
        for (InventorySnapshot.BatchRecord batch : ordered) {
            String batchId = clean(batch.batchId);
            if (batchId.length() == 0 || !batchIds.add(batchId)) {
                throw new InventorySnapshot.SnapshotException("Batch ids must be non-empty and unique.");
            }
            InventorySnapshot.ProductRecord product = products.get(clean(batch.productId));
            if (product == null) {
                throw new InventorySnapshot.SnapshotException(
                        "Batch references a missing product: " + batchId
                );
            }

            FoodItem item = new FoodItem();
            item.id = batchId;
            item.productProfileId = clean(product.productId);
            item.barcode = clean(batch.sourceBarcode);
            if (item.barcode.length() == 0) {
                item.barcode = clean(fallbackBarcodes.get(item.productProfileId));
            }
            item.name = clean(product.name);
            item.category = FoodData.normalizeCategoryValue(product.category);
            item.productionDate = clean(batch.productionDate);
            item.shelfLifeValue = batch.shelfLifeValue;
            item.shelfLifeUnit = clean(batch.shelfLifeUnit);
            item.openedDate = clean(batch.openedDate);
            item.afterOpenShelfLifeValue = batch.afterOpenShelfLifeValue;
            item.afterOpenShelfLifeUnit = clean(batch.afterOpenShelfLifeUnit);
            item.expiryDate = clean(batch.expiryDate);
            item.dateSource = defaultText(batch.dateSource, "unknown");
            item.quantity = batch.quantity;
            item.remainingQuantity = batch.remainingQuantity;
            item.unit = defaultText(batch.unit, "件");
            item.storageMethod = defaultText(batch.storageMethod, "room_temp");
            item.location = FoodData.normalizeLocationValue(batch.location);
            item.notes = clean(batch.notes);
            item.createdAt = clean(batch.createdAt);
            item.updatedAt = clean(batch.updatedAt);
            item.isFinished = "finished".equals(batch.status) || "archived".equals(batch.status);
            item.finishedAt = clean(batch.finishedAt);
            item.normalizeQuantityBounds();

            InventorySnapshot.ReminderPlanRecord reminder = reminders.get(batchId);
            if (reminder != null) {
                item.smartReminderOffsets.addAll(decodeOffsets(reminder.offsetsJson));
                item.smartReminderFingerprint = clean(reminder.fingerprint);
                item.smartReminderPlannedDaysLeft = reminder.plannedDaysLeft;
                item.smartReminderPlannedOn = clean(reminder.plannedOn);
            }
            foods.add(item);
        }
        return foods;
    }

    static String fingerprint(InventorySnapshot snapshot)
            throws InventorySnapshot.SnapshotException {
        if (snapshot == null) {
            throw new InventorySnapshot.SnapshotException("Inventory snapshot is missing.");
        }
        List<String> rows = new ArrayList<String>();
        for (InventorySnapshot.ProductRecord product : snapshot.products) {
            rows.add("P|" + encoded(
                    product.productId,
                    product.name,
                    product.category,
                    product.defaultUnit,
                    product.defaultStorageMethod,
                    product.defaultLocation,
                    nullableInteger(product.defaultShelfLifeValue),
                    product.defaultShelfLifeUnit,
                    product.notes,
                    product.createdAt,
                    product.updatedAt,
                    String.valueOf(product.sortOrder)
            ));
        }
        for (InventorySnapshot.BatchRecord batch : snapshot.batches) {
            rows.add("B|" + encoded(
                    batch.batchId,
                    batch.productId,
                    batch.sourceBarcode,
                    batch.productionDate,
                    nullableInteger(batch.shelfLifeValue),
                    batch.shelfLifeUnit,
                    batch.openedDate,
                    nullableInteger(batch.afterOpenShelfLifeValue),
                    batch.afterOpenShelfLifeUnit,
                    batch.expiryDate,
                    batch.dateSource,
                    Double.toString(batch.quantity),
                    Double.toString(batch.remainingQuantity),
                    batch.unit,
                    batch.storageMethod,
                    batch.location,
                    batch.notes,
                    batch.createdAt,
                    batch.updatedAt,
                    batch.status,
                    batch.finishedAt,
                    String.valueOf(batch.sortOrder)
            ));
        }
        for (InventorySnapshot.BarcodeRecord barcode : snapshot.barcodes) {
            rows.add("C|" + encoded(
                    barcode.barcode,
                    barcode.format,
                    barcode.createdAt,
                    barcode.lastConfirmedAt
            ));
        }
        for (InventorySnapshot.ProductBarcodeRecord relation : snapshot.productBarcodes) {
            rows.add("X|" + encoded(relation.productId, relation.barcode));
        }
        for (InventorySnapshot.ReminderPlanRecord reminder : snapshot.reminderPlans) {
            rows.add("R|" + encoded(
                    reminder.batchId,
                    reminder.mode,
                    reminder.offsetsJson,
                    reminder.fingerprint,
                    String.valueOf(reminder.plannedDaysLeft),
                    reminder.plannedOn
            ));
        }
        Collections.sort(rows);
        return sha256(joinRows(rows));
    }

    static VerificationSummary summarize(InventorySnapshot snapshot)
            throws InventorySnapshot.SnapshotException {
        int finished = 0;
        double quantity = 0;
        double remaining = 0;
        for (InventorySnapshot.BatchRecord batch : snapshot.batches) {
            if ("finished".equals(batch.status) || "archived".equals(batch.status)) {
                finished++;
            }
            quantity += batch.quantity;
            remaining += batch.remainingQuantity;
        }
        return new VerificationSummary(
                snapshot.products.size(),
                snapshot.batches.size(),
                snapshot.barcodes.size(),
                snapshot.productBarcodes.size(),
                finished,
                quantity,
                remaining,
                fingerprint(snapshot)
        );
    }

    private static List<PreparedItem> prepareItems(List<FoodItem> source)
            throws InventorySnapshot.SnapshotException {
        List<PreparedItem> prepared = new ArrayList<PreparedItem>();
        Set<String> batchIds = new HashSet<String>();
        if (source == null) {
            return prepared;
        }
        for (int index = 0; index < source.size(); index++) {
            FoodItem original = source.get(index);
            if (original == null) {
                throw new InventorySnapshot.SnapshotException("Inventory contains a null batch.");
            }
            FoodItem item = original.copy();
            item.id = clean(item.id);
            if (item.id.length() == 0 || !batchIds.add(item.id)) {
                throw new InventorySnapshot.SnapshotException(
                        "Batch ids must be non-empty and unique."
                );
            }
            if (!Double.isFinite(item.quantity) || !Double.isFinite(item.remainingQuantity)) {
                throw new InventorySnapshot.SnapshotException(
                        "Batch quantity is not finite: " + item.id
                );
            }
            item.normalizeQuantityBounds();
            String baseProfileId = clean(item.productProfileId);
            String signature = identitySignature(item);
            prepared.add(new PreparedItem(item, baseProfileId, signature, index));
        }
        return prepared;
    }

    private static Map<String, TreeSet<String>> collectProfileSignatures(
            List<PreparedItem> prepared
    ) {
        Map<String, TreeSet<String>> result = new HashMap<String, TreeSet<String>>();
        for (PreparedItem item : prepared) {
            if (item.baseProfileId.length() == 0) {
                continue;
            }
            TreeSet<String> signatures = result.get(item.baseProfileId);
            if (signatures == null) {
                signatures = new TreeSet<String>();
                result.put(item.baseProfileId, signatures);
            }
            signatures.add(item.identitySignature);
        }
        return result;
    }

    private static String resolveProductId(
            PreparedItem item,
            Map<String, TreeSet<String>> signaturesByProfile,
            Map<String, String> assignedProfileSignatures
    ) throws InventorySnapshot.SnapshotException {
        String desired;
        if (item.baseProfileId.length() == 0) {
            desired = "profile_" + shortHash(
                    "missing|" + item.item.id + "|" + item.identitySignature
            );
        } else {
            TreeSet<String> signatures = signaturesByProfile.get(item.baseProfileId);
            String canonical = signatures == null || signatures.isEmpty()
                    ? item.identitySignature
                    : signatures.first();
            desired = canonical.equals(item.identitySignature)
                    ? item.baseProfileId
                    : item.baseProfileId + "_split_" + shortHash(item.identitySignature);
        }

        String existing = assignedProfileSignatures.get(desired);
        if (existing == null || existing.equals(item.identitySignature)) {
            assignedProfileSignatures.put(desired, item.identitySignature);
            return desired;
        }

        String collisionSafe = desired + "_" + shortHash(
                desired + "|" + item.identitySignature
        );
        existing = assignedProfileSignatures.get(collisionSafe);
        if (existing != null && !existing.equals(item.identitySignature)) {
            throw new InventorySnapshot.SnapshotException(
                    "Could not allocate a deterministic product id."
            );
        }
        assignedProfileSignatures.put(collisionSafe, item.identitySignature);
        return collisionSafe;
    }

    private static InventorySnapshot.ProductRecord productRecord(
            FoodItem item,
            String productId,
            int sortOrder
    ) {
        InventorySnapshot.ProductRecord record = new InventorySnapshot.ProductRecord();
        record.productId = productId;
        record.name = clean(item.name);
        record.category = FoodData.normalizeCategoryValue(item.category);
        record.defaultUnit = defaultText(item.unit, "件");
        record.defaultStorageMethod = defaultText(item.storageMethod, "room_temp");
        record.defaultLocation = FoodData.normalizeLocationValue(item.location);
        record.defaultShelfLifeValue = item.shelfLifeValue;
        record.defaultShelfLifeUnit = clean(item.shelfLifeUnit);
        record.notes = clean(item.notes);
        record.createdAt = clean(item.createdAt);
        record.updatedAt = clean(item.updatedAt);
        record.sortOrder = sortOrder;
        return record;
    }

    private static void mergeProductTimestamps(
            InventorySnapshot.ProductRecord product,
            FoodItem item
    ) {
        product.createdAt = earlierNonEmpty(product.createdAt, item.createdAt);
        product.updatedAt = laterNonEmpty(product.updatedAt, item.updatedAt);
    }

    private static InventorySnapshot.BatchRecord batchRecord(
            FoodItem item,
            String productId,
            int sortOrder
    ) {
        InventorySnapshot.BatchRecord record = new InventorySnapshot.BatchRecord();
        record.batchId = clean(item.id);
        record.productId = productId;
        record.sourceBarcode = BarcodeUtils.digitsOnly(item.barcode);
        record.productionDate = clean(item.productionDate);
        record.shelfLifeValue = item.shelfLifeValue;
        record.shelfLifeUnit = clean(item.shelfLifeUnit);
        record.openedDate = clean(item.openedDate);
        record.afterOpenShelfLifeValue = item.afterOpenShelfLifeValue;
        record.afterOpenShelfLifeUnit = clean(item.afterOpenShelfLifeUnit);
        record.expiryDate = clean(item.expiryDate);
        record.dateSource = defaultText(item.dateSource, "unknown");
        record.quantity = item.quantity;
        record.remainingQuantity = item.remainingQuantity;
        record.unit = defaultText(item.unit, "件");
        record.storageMethod = defaultText(item.storageMethod, "room_temp");
        record.location = FoodData.normalizeLocationValue(item.location);
        record.notes = clean(item.notes);
        record.createdAt = clean(item.createdAt);
        record.updatedAt = clean(item.updatedAt);
        record.status = item.isFinished ? "finished" : "active";
        record.finishedAt = clean(item.finishedAt);
        record.sortOrder = sortOrder;
        return record;
    }

    private static InventorySnapshot.ReminderPlanRecord reminderRecord(FoodItem item) {
        InventorySnapshot.ReminderPlanRecord record =
                new InventorySnapshot.ReminderPlanRecord();
        record.batchId = clean(item.id);
        record.mode = "smart";
        record.offsetsJson = encodeOffsets(item.smartReminderOffsets);
        record.fingerprint = clean(item.smartReminderFingerprint);
        record.plannedDaysLeft = item.smartReminderPlannedDaysLeft;
        record.plannedOn = clean(item.smartReminderPlannedOn);
        return record;
    }

    private static void addBarcodeRecords(
            FoodItem item,
            String productId,
            Map<String, InventorySnapshot.BarcodeRecord> barcodes,
            Set<String> relations,
            List<InventorySnapshot.ProductBarcodeRecord> outputRelations
    ) {
        String normalized = BarcodeUtils.toGtin14(item.barcode);
        if (!BarcodeUtils.isSupportedProductCode(normalized)) {
            return;
        }
        InventorySnapshot.BarcodeRecord barcode = barcodes.get(normalized);
        if (barcode == null) {
            barcode = new InventorySnapshot.BarcodeRecord();
            barcode.barcode = normalized;
            barcode.format = barcodeFormat(BarcodeUtils.digitsOnly(item.barcode));
            barcode.createdAt = clean(item.createdAt);
            barcode.lastConfirmedAt = laterNonEmpty(item.updatedAt, item.createdAt);
            barcodes.put(normalized, barcode);
        } else {
            barcode.createdAt = earlierNonEmpty(barcode.createdAt, item.createdAt);
            barcode.lastConfirmedAt = laterNonEmpty(barcode.lastConfirmedAt, item.updatedAt);
        }

        String relationKey = productId + "\u0000" + normalized;
        if (relations.add(relationKey)) {
            InventorySnapshot.ProductBarcodeRecord relation =
                    new InventorySnapshot.ProductBarcodeRecord();
            relation.productId = productId;
            relation.barcode = normalized;
            outputRelations.add(relation);
        }
    }

    private static Map<String, String> firstBarcodeByProduct(
            List<InventorySnapshot.ProductBarcodeRecord> relations
    ) {
        Map<String, String> result = new HashMap<String, String>();
        for (InventorySnapshot.ProductBarcodeRecord relation : relations) {
            String productId = clean(relation.productId);
            String barcode = clean(relation.barcode);
            if (productId.length() > 0 && barcode.length() > 0 && !result.containsKey(productId)) {
                result.put(productId, barcode);
            }
        }
        return result;
    }

    private static String identitySignature(FoodItem item) {
        return encoded(
                clean(item.name),
                FoodData.normalizeCategoryValue(item.category)
        );
    }

    private static String barcodeFormat(String rawDigits) {
        int length = clean(rawDigits).length();
        if (length == 8) {
            return "EAN-8";
        }
        if (length == 12) {
            return "UPC-A";
        }
        if (length == 13) {
            return "EAN-13";
        }
        return length == 14 ? "GTIN-14" : "UNKNOWN";
    }

    private static String encodeOffsets(List<Integer> offsets) {
        StringBuilder builder = new StringBuilder("[");
        if (offsets != null) {
            for (Integer offset : offsets) {
                if (offset == null) {
                    continue;
                }
                if (builder.length() > 1) {
                    builder.append(',');
                }
                builder.append(offset.intValue());
            }
        }
        return builder.append(']').toString();
    }

    private static List<Integer> decodeOffsets(String raw)
            throws InventorySnapshot.SnapshotException {
        String text = clean(raw).trim();
        List<Integer> values = new ArrayList<Integer>();
        if (text.length() == 0 || "[]".equals(text)) {
            return values;
        }
        if (!text.startsWith("[") || !text.endsWith("]")) {
            throw new InventorySnapshot.SnapshotException("Reminder offsets are malformed.");
        }
        String body = text.substring(1, text.length() - 1).trim();
        if (body.length() == 0) {
            return values;
        }
        for (String token : body.split(",")) {
            try {
                values.add(Integer.valueOf(token.trim()));
            } catch (NumberFormatException exception) {
                throw new InventorySnapshot.SnapshotException(
                        "Reminder offsets are malformed.",
                        exception
                );
            }
        }
        return values;
    }

    private static String earlierNonEmpty(String left, String right) {
        String cleanLeft = clean(left);
        String cleanRight = clean(right);
        if (cleanLeft.length() == 0) {
            return cleanRight;
        }
        if (cleanRight.length() == 0) {
            return cleanLeft;
        }
        return cleanLeft.compareTo(cleanRight) <= 0 ? cleanLeft : cleanRight;
    }

    private static String laterNonEmpty(String left, String right) {
        String cleanLeft = clean(left);
        String cleanRight = clean(right);
        if (cleanLeft.length() == 0) {
            return cleanRight;
        }
        if (cleanRight.length() == 0) {
            return cleanLeft;
        }
        return cleanLeft.compareTo(cleanRight) >= 0 ? cleanLeft : cleanRight;
    }

    private static String nullableInteger(Integer value) {
        return value == null ? "<null>" : String.valueOf(value.intValue());
    }

    private static String encoded(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String cleanValue = clean(value);
            builder.append(cleanValue.length()).append(':').append(cleanValue).append('|');
        }
        return builder.toString();
    }

    private static String joinRows(List<String> rows) {
        StringBuilder builder = new StringBuilder();
        for (String row : rows) {
            builder.append(row).append('\n');
        }
        return builder.toString();
    }

    private static String shortHash(String value)
            throws InventorySnapshot.SnapshotException {
        return sha256(value).substring(0, 12);
    }

    private static String sha256(String value)
            throws InventorySnapshot.SnapshotException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(clean(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                hex.append(String.format("%02x", current & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new InventorySnapshot.SnapshotException("SHA-256 is unavailable.", exception);
        }
    }

    private static String defaultText(String value, String fallback) {
        String normalized = clean(value);
        return normalized.length() == 0 ? fallback : normalized;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static final class VerificationSummary {
        final int productCount;
        final int batchCount;
        final int barcodeCount;
        final int relationCount;
        final int finishedCount;
        final double totalQuantity;
        final double totalRemaining;
        final String fingerprint;

        VerificationSummary(
                int productCount,
                int batchCount,
                int barcodeCount,
                int relationCount,
                int finishedCount,
                double totalQuantity,
                double totalRemaining,
                String fingerprint
        ) {
            this.productCount = productCount;
            this.batchCount = batchCount;
            this.barcodeCount = barcodeCount;
            this.relationCount = relationCount;
            this.finishedCount = finishedCount;
            this.totalQuantity = totalQuantity;
            this.totalRemaining = totalRemaining;
            this.fingerprint = fingerprint;
        }

        boolean equivalentTo(VerificationSummary other) {
            return other != null
                    && productCount == other.productCount
                    && batchCount == other.batchCount
                    && barcodeCount == other.barcodeCount
                    && relationCount == other.relationCount
                    && finishedCount == other.finishedCount
                    && Double.compare(totalQuantity, other.totalQuantity) == 0
                    && Double.compare(totalRemaining, other.totalRemaining) == 0
                    && clean(fingerprint).equals(clean(other.fingerprint));
        }
    }

    private static final class PreparedItem {
        final FoodItem item;
        final String baseProfileId;
        final String identitySignature;
        final int sortOrder;

        PreparedItem(
                FoodItem item,
                String baseProfileId,
                String identitySignature,
                int sortOrder
        ) {
            this.item = item;
            this.baseProfileId = baseProfileId;
            this.identitySignature = identitySignature;
            this.sortOrder = sortOrder;
        }
    }
}
