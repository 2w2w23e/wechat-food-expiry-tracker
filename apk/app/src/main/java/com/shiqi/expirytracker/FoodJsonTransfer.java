package com.shiqi.expirytracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class FoodJsonTransfer {
    static final String MIME_TYPE = "application/json";
    static final String FORMAT = "shiqi-food-batch-transfer";
    static final int SCHEMA_VERSION = 1;
    private static final int MAX_IMPORT_BYTES = 64 * 1024 * 1024;

    private FoodJsonTransfer() {
    }

    static String fileName(String timestamp) {
        String safeTimestamp = FoodItem.cleanText(timestamp).replaceAll("[^0-9]", "");
        if (safeTimestamp.length() < 12) {
            safeTimestamp = new SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(new Date());
        } else {
            safeTimestamp = safeTimestamp.substring(0, 12);
        }
        return "shiqi-batches-" + safeTimestamp + ".json";
    }

    static ExportResult write(
            OutputStream outputStream,
            List<FoodItem> foods,
            ExportFilter filter,
            String exportedAt
    ) throws IOException {
        if (outputStream == null) {
            throw new IOException("导出文件不可写");
        }
        ExportFilter safeFilter = filter == null ? ExportFilter.all() : filter;
        safeFilter.validate();

        JSONArray batches = new JSONArray();
        Set<String> ids = new HashSet<String>();
        List<FoodItem> selected = new ArrayList<FoodItem>();
        if (foods != null) {
            for (FoodItem food : foods) {
                if (food == null || !safeFilter.matches(food)) {
                    continue;
                }
                String id = FoodItem.cleanText(food.id);
                if (id.length() == 0) {
                    throw new IOException("存在没有批次编号的食品，请重新打开应用完成数据修复");
                }
                if (!ids.add(id)) {
                    throw new IOException("存在重复批次编号：" + id);
                }
                try {
                    batches.put(food.toJson());
                    selected.add(food.copy());
                } catch (JSONException error) {
                    throw new IOException("无法序列化批次：" + id, error);
                }
            }
        }

        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("exportedAt", FoodItem.cleanText(exportedAt));
            root.put("batchCount", selected.size());
            root.put("filters", safeFilter.toJson());
            root.put("batches", batches);
            outputStream.write(root.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return new ExportResult(selected);
        } catch (JSONException error) {
            throw new IOException("无法生成 JSON 导出文件", error);
        }
    }

    static ImportPreview read(InputStream inputStream, List<FoodItem> existingFoods) throws IOException {
        String raw = readUtf8(inputStream);
        Object value;
        JSONTokener tokener = new JSONTokener(raw);
        try {
            value = tokener.nextValue();
            if (tokener.nextClean() != 0 || !(value instanceof JSONObject)) {
                throw new IOException("不是有效的食期管家 JSON 文件");
            }
        } catch (JSONException error) {
            throw new IOException("JSON 文件格式错误", error);
        }

        JSONObject root = (JSONObject) value;
        if (!FORMAT.equals(root.optString("format", ""))) {
            throw new IOException("不是食期管家快速迁移文件");
        }
        int version = root.optInt("schemaVersion", 0);
        if (version != SCHEMA_VERSION) {
            throw new IOException(version > SCHEMA_VERSION
                    ? "迁移文件版本高于当前应用，请先更新应用"
                    : "不支持的迁移文件版本");
        }
        JSONArray batches = root.optJSONArray("batches");
        if (batches == null) {
            throw new IOException("迁移文件缺少批次数组");
        }

        List<FoodItem> local = copyFoods(existingFoods);
        Map<String, FoodItem> localById = new LinkedHashMap<String, FoodItem>();
        for (FoodItem food : local) {
            if (food != null && FoodItem.cleanText(food.id).length() > 0) {
                localById.put(food.id, food);
            }
        }

        List<RowResult> rows = new ArrayList<RowResult>();
        Set<String> importedIds = new HashSet<String>();
        Map<String, FoodItem> replacements = new HashMap<String, FoodItem>();
        List<FoodItem> additions = new ArrayList<FoodItem>();
        List<String> errors = new ArrayList<String>();

        for (int index = 0; index < batches.length(); index++) {
            int displayRow = index + 1;
            JSONObject object = batches.optJSONObject(index);
            if (object == null) {
                errors.add("第 " + displayRow + " 条不是对象");
                continue;
            }
            List<String> rowErrors = validateBatch(object);
            FoodItem imported = FoodItem.fromJson(object);
            String id = FoodItem.cleanText(imported.id);
            if (id.length() > 0 && !importedIds.add(id)) {
                rowErrors.add("批次编号在文件内重复");
            }
            if (!rowErrors.isEmpty()) {
                errors.add("第 " + displayRow + " 条：" + join(rowErrors));
                rows.add(RowResult.error(displayRow, id, imported.name, rowErrors));
                continue;
            }

            FoodItem current = localById.get(id);
            if (current == null) {
                additions.add(imported.copy());
                rows.add(RowResult.add(displayRow, imported));
                continue;
            }
            int updatedOrder = compareTimestamps(imported.updatedAt, current.updatedAt);
            if (updatedOrder > 0) {
                replacements.put(id, imported.copy());
                rows.add(RowResult.update(displayRow, imported));
            } else if (sameBatch(imported, current)) {
                rows.add(RowResult.unchanged(displayRow, imported));
            } else {
                String reason = updatedOrder < 0
                        ? "本机批次更新时间更新，未覆盖"
                        : "更新时间相同但内容不同，未自动覆盖";
                rows.add(RowResult.conflict(displayRow, imported, reason));
            }
        }

        List<FoodItem> merged = new ArrayList<FoodItem>();
        for (FoodItem food : local) {
            FoodItem replacement = food == null ? null : replacements.get(food.id);
            merged.add(replacement == null ? food.copy() : replacement.copy());
        }
        for (FoodItem addition : additions) {
            merged.add(addition.copy());
        }
        return new ImportPreview(rows, errors, merged);
    }

    private static List<String> validateBatch(JSONObject object) {
        List<String> errors = new ArrayList<String>();
        String id = optCleanString(object, "id");
        String name = optCleanString(object, "name");
        if (id.length() == 0) {
            errors.add("缺少批次编号");
        }
        if (name.length() == 0) {
            errors.add("食品名称为空");
        }
        validateDate(object, "productionDate", "生产日期", errors);
        validateDate(object, "openedDate", "开封日期", errors);
        validateDate(object, "expiryDate", "最终日期", errors);

        double quantity = object.optDouble("quantity", 1d);
        double remaining = object.optDouble("remainingQuantity", quantity);
        if (!finiteNonNegative(quantity)) {
            errors.add("总数量无效");
        }
        if (!finiteNonNegative(remaining) || remaining > quantity) {
            errors.add("剩余数量无效");
        }
        validateTimestamp(optCleanString(object, "createdAt"), "入库时间", errors);
        validateTimestamp(optCleanString(object, "updatedAt"), "更新时间", errors);
        return errors;
    }

    private static void validateDate(JSONObject object, String key, String label, List<String> errors) {
        String value = optCleanString(object, key);
        if (value.length() > 0 && !DateRules.isValidDateString(value)) {
            errors.add(label + "格式无效");
        }
    }

    private static String optCleanString(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            return "";
        }
        return FoodItem.cleanText(object.optString(key, ""));
    }

    private static void validateTimestamp(String value, String label, List<String> errors) {
        String text = FoodItem.cleanText(value);
        if (text.length() > 0 && parseTimestamp(text) == Long.MIN_VALUE) {
            errors.add(label + "格式无效");
        }
    }

    private static boolean finiteNonNegative(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0d;
    }

    private static int compareTimestamps(String left, String right) {
        long leftValue = parseTimestamp(left);
        long rightValue = parseTimestamp(right);
        if (leftValue == Long.MIN_VALUE && rightValue == Long.MIN_VALUE) {
            return 0;
        }
        if (leftValue == Long.MIN_VALUE) {
            return -1;
        }
        if (rightValue == Long.MIN_VALUE) {
            return 1;
        }
        return Long.compare(leftValue, rightValue);
    }

    private static long parseTimestamp(String value) {
        String text = FoodItem.cleanText(value);
        if (text.length() == 0) {
            return Long.MIN_VALUE;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        format.setLenient(false);
        try {
            return format.parse(text).getTime();
        } catch (ParseException error) {
            return Long.MIN_VALUE;
        }
    }

    private static boolean sameBatch(FoodItem left, FoodItem right) {
        try {
            return left.toJson().toString().equals(right.toJson().toString());
        } catch (JSONException error) {
            return false;
        }
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException("导入文件为空");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_IMPORT_BYTES) {
                throw new IOException("JSON 文件过大，无法导入");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    private static List<FoodItem> copyFoods(List<FoodItem> foods) {
        List<FoodItem> copied = new ArrayList<FoodItem>();
        if (foods != null) {
            for (FoodItem food : foods) {
                if (food != null) {
                    copied.add(food.copy());
                }
            }
        }
        return copied;
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    static final class ExportFilter {
        final String createdFrom;
        final String createdTo;
        final String productionFrom;
        final String productionTo;
        final String expiryFrom;
        final String expiryTo;

        ExportFilter(
                String createdFrom,
                String createdTo,
                String productionFrom,
                String productionTo,
                String expiryFrom,
                String expiryTo
        ) {
            this.createdFrom = FoodItem.cleanText(createdFrom);
            this.createdTo = FoodItem.cleanText(createdTo);
            this.productionFrom = FoodItem.cleanText(productionFrom);
            this.productionTo = FoodItem.cleanText(productionTo);
            this.expiryFrom = FoodItem.cleanText(expiryFrom);
            this.expiryTo = FoodItem.cleanText(expiryTo);
        }

        static ExportFilter all() {
            return new ExportFilter("", "", "", "", "", "");
        }

        void validate() {
            validateRange(createdFrom, createdTo, "入库时间");
            validateRange(productionFrom, productionTo, "生产日期");
            validateRange(expiryFrom, expiryTo, "最终日期");
        }

        boolean matches(FoodItem food) {
            return food != null
                    && inRange(datePart(food.createdAt), createdFrom, createdTo)
                    && inRange(food.productionDate, productionFrom, productionTo)
                    && inRange(food.expiryDate, expiryFrom, expiryTo);
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("createdFrom", nullable(createdFrom));
            json.put("createdTo", nullable(createdTo));
            json.put("productionFrom", nullable(productionFrom));
            json.put("productionTo", nullable(productionTo));
            json.put("expiryFrom", nullable(expiryFrom));
            json.put("expiryTo", nullable(expiryTo));
            return json;
        }

        private static Object nullable(String value) {
            return value.length() == 0 ? JSONObject.NULL : value;
        }

        private static void validateRange(String from, String to, String label) {
            if ((from.length() > 0 && !DateRules.isValidDateString(from))
                    || (to.length() > 0 && !DateRules.isValidDateString(to))) {
                throw new IllegalArgumentException(label + "格式无效");
            }
            if (from.length() > 0 && to.length() > 0 && from.compareTo(to) > 0) {
                throw new IllegalArgumentException(label + "开始日期不能晚于结束日期");
            }
        }

        private static boolean inRange(String value, String from, String to) {
            if (from.length() == 0 && to.length() == 0) {
                return true;
            }
            if (!DateRules.isValidDateString(value)) {
                return false;
            }
            return (from.length() == 0 || value.compareTo(from) >= 0)
                    && (to.length() == 0 || value.compareTo(to) <= 0);
        }

        private static String datePart(String timestamp) {
            String text = FoodItem.cleanText(timestamp);
            if (text.length() >= 10) {
                String date = text.substring(0, 10);
                return DateRules.isValidDateString(date) ? date : "";
            }
            return "";
        }
    }

    static final class ExportResult {
        final List<FoodItem> foods;
        final int count;

        ExportResult(List<FoodItem> foods) {
            this.foods = copyFoods(foods);
            this.count = this.foods.size();
        }
    }

    static final class ImportPreview {
        final List<RowResult> rows;
        final List<String> errors;
        final List<FoodItem> mergedFoods;
        final int total;
        final int additions;
        final int updates;
        final int unchanged;
        final int conflicts;

        ImportPreview(List<RowResult> rows, List<String> errors, List<FoodItem> mergedFoods) {
            this.rows = rows;
            this.errors = errors;
            this.mergedFoods = copyFoods(mergedFoods);
            this.total = rows.size();
            int addCount = 0;
            int updateCount = 0;
            int unchangedCount = 0;
            int conflictCount = 0;
            for (RowResult row : rows) {
                if (RowResult.ADD.equals(row.action)) addCount++;
                if (RowResult.UPDATE.equals(row.action)) updateCount++;
                if (RowResult.UNCHANGED.equals(row.action)) unchangedCount++;
                if (RowResult.CONFLICT.equals(row.action)) conflictCount++;
            }
            additions = addCount;
            updates = updateCount;
            unchanged = unchangedCount;
            conflicts = conflictCount;
        }

        boolean canApply() {
            return errors.isEmpty() && (additions > 0 || updates > 0);
        }
    }

    static final class RowResult {
        static final String ADD = "add";
        static final String UPDATE = "update";
        static final String UNCHANGED = "unchanged";
        static final String CONFLICT = "conflict";
        static final String ERROR = "error";

        final int rowNumber;
        final String batchId;
        final String name;
        final String action;
        final String message;

        private RowResult(int rowNumber, String batchId, String name, String action, String message) {
            this.rowNumber = rowNumber;
            this.batchId = FoodItem.cleanText(batchId);
            this.name = FoodItem.cleanText(name);
            this.action = action;
            this.message = FoodItem.cleanText(message);
        }

        static RowResult add(int rowNumber, FoodItem food) {
            return fromFood(rowNumber, food, ADD, "新增批次");
        }

        static RowResult update(int rowNumber, FoodItem food) {
            return fromFood(rowNumber, food, UPDATE, "按较新更新时间更新数量和状态");
        }

        static RowResult unchanged(int rowNumber, FoodItem food) {
            return fromFood(rowNumber, food, UNCHANGED, "内容相同");
        }

        static RowResult conflict(int rowNumber, FoodItem food, String message) {
            return fromFood(rowNumber, food, CONFLICT, message);
        }

        static RowResult error(int rowNumber, String id, String name, List<String> errors) {
            return new RowResult(rowNumber, id, name, ERROR, join(errors));
        }

        private static RowResult fromFood(int rowNumber, FoodItem food, String action, String message) {
            return new RowResult(rowNumber, food.id, food.name, action, message);
        }
    }
}
