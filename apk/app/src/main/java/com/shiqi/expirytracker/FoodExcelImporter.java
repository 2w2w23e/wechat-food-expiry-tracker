package com.shiqi.expirytracker;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

final class FoodExcelImporter {
    private FoodExcelImporter() {
    }

    static ImportPreview readWorkbook(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException("Excel 文件为空");
        }

        Map<String, byte[]> entries = readZipEntries(inputStream);
        if (!entries.containsKey("xl/workbook.xml")) {
            throw new IOException("不是有效的 .xlsx 文件");
        }

        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        byte[] sheet = entries.get(findFoodsSheetPath(entries));
        if (sheet == null) {
            throw new IOException("未找到 foods 工作表");
        }

        return parseFoodSheet(sheet, sharedStrings);
    }

    private static ImportPreview parseFoodSheet(byte[] sheetXml, List<String> sharedStrings) throws IOException {
        Document document = parseXml(sheetXml);
        NodeList rows = document.getElementsByTagNameNS("*", "row");
        if (rows.getLength() == 0) {
            throw new IOException("foods 工作表没有表头");
        }

        Map<Integer, String> headers = new LinkedHashMap<Integer, String>();
        List<RowResult> results = new ArrayList<RowResult>();

        for (int rowIndex = 0; rowIndex < rows.getLength(); rowIndex++) {
            Element row = (Element) rows.item(rowIndex);
            int rowNumber = parseInt(row.getAttribute("r"), rowIndex + 1);
            Map<Integer, String> cells = readRowCells(row, sharedStrings);

            if (headers.isEmpty()) {
                for (Map.Entry<Integer, String> cell : cells.entrySet()) {
                    String header = normalizeHeader(cell.getValue());
                    if (header.length() > 0) {
                        headers.put(cell.getKey(), header);
                    }
                }
                if (!headers.containsValue("name")) {
                    throw new IOException("foods 工作表缺少 name 表头");
                }
                continue;
            }

            Map<String, String> values = new LinkedHashMap<String, String>();
            boolean hasAnyValue = false;
            for (Map.Entry<Integer, String> header : headers.entrySet()) {
                String value = clean(cells.get(header.getKey()));
                if (value.length() > 0) {
                    hasAnyValue = true;
                }
                values.put(header.getValue(), value);
            }
            if (!hasAnyValue) {
                continue;
            }

            results.add(parseFoodRow(rowNumber, values));
        }

        return new ImportPreview(results);
    }

    private static RowResult parseFoodRow(int rowNumber, Map<String, String> values) {
        List<String> errors = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        FoodItem item = new FoodItem();

        item.id = clean(value(values, "id"));
        item.name = clean(value(values, "name"));
        if (item.name.length() == 0) {
            errors.add("食品名称不能为空");
        }

        item.category = FoodData.normalizeCategoryValue(firstNonEmpty(
                value(values, "category"),
                value(values, "categorylabel")
        ));

        String productionDate = clean(value(values, "productiondate"));
        if (productionDate.length() > 0 && !DateRules.isValidDateString(productionDate)) {
            errors.add("生产日期格式必须为 yyyy-MM-dd");
        }
        item.productionDate = DateRules.isValidDateString(productionDate) ? productionDate : "";

        Integer shelfLifeValue = parsePositiveInteger(value(values, "shelflifevalue"), "保质期数值", errors);
        String shelfLifeUnit = normalizeShelfLifeUnit(value(values, "shelflifeunit"), value(values, "shelflifeunitlabel"));
        if (shelfLifeValue != null && shelfLifeUnit.length() == 0) {
            errors.add("保质期单位必须为 day / month / year");
        }
        item.shelfLifeValue = shelfLifeValue;
        item.shelfLifeUnit = shelfLifeValue == null ? "" : shelfLifeUnit;

        item.openedDate = parseOptionalDate(value(values, "openeddate"), "开封日期", errors);
        item.afterOpenShelfLifeValue = parsePositiveInteger(value(values, "afteropenshelflifevalue"), "开封后保质期", errors);
        item.afterOpenShelfLifeUnit = normalizeShelfLifeUnit(value(values, "afteropenshelflifeunit"), "");
        if ((item.openedDate.length() > 0 || item.afterOpenShelfLifeValue != null || item.afterOpenShelfLifeUnit.length() > 0)
                && (item.openedDate.length() == 0 || item.afterOpenShelfLifeValue == null || item.afterOpenShelfLifeUnit.length() == 0)) {
            errors.add("开封信息必须同时包含开封日期、开封后保质期数值和单位");
        }

        item.expiryDate = "";
        item.dateSource = normalizeDateSource(value(values, "datesource"));
        String manualExpiryDate = clean(value(values, "expirydate"));
        if ("none".equals(item.dateSource)) {
            if (!DateRules.isValidDateString(item.productionDate)) {
                errors.add("无过期时间食品必须填写生产日期");
            }
        } else if (manualExpiryDate.length() > 0) {
            if (DateRules.isValidDateString(manualExpiryDate)) {
                item.expiryDate = manualExpiryDate;
                if (item.dateSource.length() == 0 || "unknown".equals(item.dateSource)) {
                    item.dateSource = "manual";
                }
            } else {
                errors.add("最终可食用日期格式必须为 yyyy-MM-dd");
            }
        } else if (item.productionDate.length() > 0 && item.shelfLifeValue != null && DateRules.isShelfLifeUnit(item.shelfLifeUnit)) {
            item.expiryDate = DateRules.addShelfLife(item.productionDate, item.shelfLifeValue, item.shelfLifeUnit);
            item.dateSource = "calculated";
        } else {
            errors.add("必须填写最终可食用日期，或填写生产日期 + 保质期");
        }

        if (item.dateSource.length() == 0) {
            item.dateSource = item.expiryDate.length() > 0 ? "manual" : "unknown";
        }

        item.quantity = parseNumber(value(values, "quantity"), 1.0, "数量", errors);
        item.remainingQuantity = parseNumber(value(values, "remainingquantity"), item.quantity, "剩余数量", errors);
        if (item.quantity < 0) {
            errors.add("数量不能小于 0");
        }
        if (item.remainingQuantity < 0) {
            errors.add("剩余数量不能小于 0");
        }
        if (item.remainingQuantity > item.quantity) {
            errors.add("剩余数量不能大于总数量");
        }
        item.normalizeQuantityBounds();

        item.unit = firstNonEmpty(value(values, "unit"), "件");
        item.storageMethod = normalizeStorage(value(values, "storagemethod"), value(values, "storagemethodlabel"), warnings);
        item.location = FoodData.normalizeLocationValue(firstNonEmpty(value(values, "location"), value(values, "locationlabel")));
        item.notes = clean(value(values, "notes"));
        item.isFinished = parseBoolean(value(values, "isfinished"));
        item.finishedAt = clean(value(values, "finishedat"));
        item.createdAt = clean(value(values, "createdat"));
        item.updatedAt = clean(value(values, "updatedat"));

        return new RowResult(rowNumber, values, item, errors, warnings);
    }

    private static Map<Integer, String> readRowCells(Element row, List<String> sharedStrings) {
        Map<Integer, String> cells = new LinkedHashMap<Integer, String>();
        NodeList cellNodes = row.getElementsByTagNameNS("*", "c");
        int nextColumn = 1;
        for (int index = 0; index < cellNodes.getLength(); index++) {
            Element cell = (Element) cellNodes.item(index);
            int column = columnIndex(cell.getAttribute("r"));
            if (column <= 0) {
                column = nextColumn;
            }
            cells.put(Integer.valueOf(column), cellText(cell, sharedStrings));
            nextColumn = column + 1;
        }
        return cells;
    }

    private static String cellText(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList inline = cell.getElementsByTagNameNS("*", "is");
            return inline.getLength() == 0 ? "" : clean(inline.item(0).getTextContent());
        }

        String raw = firstChildText(cell, "v");
        if ("s".equals(type)) {
            int sharedIndex = parseInt(raw, -1);
            return sharedIndex >= 0 && sharedIndex < sharedStrings.size() ? sharedStrings.get(sharedIndex) : "";
        }
        return clean(raw);
    }

    private static String firstChildText(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) {
            return "";
        }
        return clean(nodes.item(0).getTextContent());
    }

    private static List<String> parseSharedStrings(byte[] sharedStringsXml) throws IOException {
        List<String> values = new ArrayList<String>();
        if (sharedStringsXml == null) {
            return values;
        }

        Document document = parseXml(sharedStringsXml);
        NodeList items = document.getElementsByTagNameNS("*", "si");
        for (int index = 0; index < items.getLength(); index++) {
            values.add(clean(items.item(index).getTextContent()));
        }
        return values;
    }

    private static String findFoodsSheetPath(Map<String, byte[]> entries) throws IOException {
        String workbookXml = utf8(entries.get("xl/workbook.xml"));
        String workbookRelsXml = utf8(entries.get("xl/_rels/workbook.xml.rels"));
        String foodsRelationshipId = findFoodsRelationshipId(workbookXml);
        Map<String, String> relationships = parseWorkbookRelationships(workbookRelsXml);
        String target = relationships.get(foodsRelationshipId);
        if (target == null || target.length() == 0) {
            return "xl/worksheets/sheet1.xml";
        }
        if (target.startsWith("/")) {
            return target.substring(1);
        }
        return "xl/" + target;
    }

    private static String findFoodsRelationshipId(String workbookXml) throws IOException {
        Document document = parseXml(workbookXml.getBytes(StandardCharsets.UTF_8));
        NodeList sheets = document.getElementsByTagNameNS("*", "sheet");
        String firstRelationshipId = "";
        for (int index = 0; index < sheets.getLength(); index++) {
            Element sheet = (Element) sheets.item(index);
            String relationshipId = sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            if (relationshipId.length() == 0) {
                relationshipId = sheet.getAttribute("r:id");
            }
            if (firstRelationshipId.length() == 0) {
                firstRelationshipId = relationshipId;
            }
            if ("foods".equalsIgnoreCase(sheet.getAttribute("name"))) {
                return relationshipId;
            }
        }
        return firstRelationshipId;
    }

    private static Map<String, String> parseWorkbookRelationships(String workbookRelsXml) throws IOException {
        Map<String, String> relationships = new HashMap<String, String>();
        if (workbookRelsXml.length() == 0) {
            return relationships;
        }

        Document document = parseXml(workbookRelsXml.getBytes(StandardCharsets.UTF_8));
        NodeList rels = document.getElementsByTagNameNS("*", "Relationship");
        for (int index = 0; index < rels.getLength(); index++) {
            Element relationship = (Element) rels.item(index);
            relationships.put(relationship.getAttribute("Id"), relationship.getAttribute("Target"));
        }
        return relationships;
    }

    private static Document parseXml(byte[] bytes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception error) {
            throw new IOException("无法解析 Excel XML：" + FoodItem.cleanText(error.getMessage()), error);
        }
    }

    private static Map<String, byte[]> readZipEntries(InputStream inputStream) throws IOException {
        Map<String, byte[]> entries = new HashMap<String, byte[]>();
        ZipInputStream zip = new ZipInputStream(inputStream);
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                entries.put(entry.getName(), output.toByteArray());
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return entries;
    }

    private static String normalizeHeader(String value) {
        return clean(value).replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.US);
    }

    private static String value(Map<String, String> values, String key) {
        String value = values.get(normalizeHeader(key));
        return value == null ? "" : value;
    }

    private static String parseOptionalDate(String value, String label, List<String> errors) {
        String text = clean(value);
        if (text.length() == 0) {
            return "";
        }
        if (!DateRules.isValidDateString(text)) {
            errors.add(label + "格式必须为 yyyy-MM-dd");
            return "";
        }
        return text;
    }

    private static Integer parsePositiveInteger(String value, String label, List<String> errors) {
        String text = clean(value);
        if (text.length() == 0) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(text.replaceAll("\\.0+$", ""));
            if (parsed <= 0) {
                errors.add(label + "必须是正整数");
                return null;
            }
            return Integer.valueOf(parsed);
        } catch (NumberFormatException error) {
            errors.add(label + "必须是正整数");
            return null;
        }
    }

    private static double parseNumber(String value, double fallback, String label, List<String> errors) {
        String text = clean(value);
        if (text.length() == 0) {
            return fallback;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException error) {
            errors.add(label + "必须是数字");
            return fallback;
        }
    }

    private static String normalizeShelfLifeUnit(String value, String label) {
        String text = firstNonEmpty(value, label);
        for (Option option : FoodData.SHELF_LIFE_UNITS) {
            if (option.value.equals(text) || option.label.equals(text)) {
                return option.value;
            }
        }
        if ("days".equalsIgnoreCase(text)) {
            return "day";
        }
        if ("months".equalsIgnoreCase(text)) {
            return "month";
        }
        if ("years".equalsIgnoreCase(text)) {
            return "year";
        }
        return "";
    }

    private static String normalizeStorage(String value, String label, List<String> warnings) {
        String text = firstNonEmpty(value, label);
        if (text.length() == 0) {
            return "room_temp";
        }
        for (Option option : FoodData.STORAGE_METHODS) {
            if (option.value.equals(text) || option.label.equals(text)) {
                return option.value;
            }
        }
        warnings.add("保存方式未识别，已按常温导入");
        return "room_temp";
    }

    private static String normalizeDateSource(String value) {
        String text = clean(value);
        if ("manual".equals(text) || "calculated".equals(text) || "none".equals(text) || "unknown".equals(text)) {
            return text;
        }
        return "";
    }

    private static boolean parseBoolean(String value) {
        String text = clean(value).toLowerCase(Locale.US);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text) || "y".equals(text);
    }

    private static int columnIndex(String cellRef) {
        if (cellRef == null) {
            return -1;
        }
        int index = 0;
        int position = 0;
        while (position < cellRef.length()) {
            char ch = cellRef.charAt(position);
            if (ch < 'A' || ch > 'Z') {
                break;
            }
            index = index * 26 + (ch - 'A' + 1);
            position++;
        }
        return index;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(clean(value));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static String firstNonEmpty(String first, String second) {
        String firstText = clean(first);
        if (firstText.length() > 0) {
            return firstText;
        }
        return clean(second);
    }

    private static String clean(String value) {
        return FoodItem.cleanText(value);
    }

    private static String utf8(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    static final class ImportPreview {
        final List<RowResult> rows;
        final int totalRows;
        final int importableRows;
        final int errorRows;
        final int warningRows;

        ImportPreview(List<RowResult> rows) {
            this.rows = rows;
            this.totalRows = rows.size();
            int importable = 0;
            int errors = 0;
            int warnings = 0;
            for (RowResult row : rows) {
                if (row.canImport()) {
                    importable++;
                } else {
                    errors++;
                }
                if (!row.warnings.isEmpty()) {
                    warnings++;
                }
            }
            this.importableRows = importable;
            this.errorRows = errors;
            this.warningRows = warnings;
        }

        List<FoodItem> importableFoods() {
            List<FoodItem> foods = new ArrayList<FoodItem>();
            for (RowResult row : rows) {
                if (row.canImport()) {
                    foods.add(row.food.copy());
                }
            }
            return foods;
        }
    }

    static final class RowResult {
        final int rowNumber;
        final Map<String, String> values;
        final FoodItem food;
        final List<String> errors;
        final List<String> warnings;

        RowResult(int rowNumber, Map<String, String> values, FoodItem food, List<String> errors, List<String> warnings) {
            this.rowNumber = rowNumber;
            this.values = values;
            this.food = food;
            this.errors = errors;
            this.warnings = warnings;
        }

        boolean canImport() {
            return errors.isEmpty();
        }
    }
}
