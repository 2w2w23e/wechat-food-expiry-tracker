package com.shiqi.expirytracker;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class FoodExcelExporter {
    private static final String MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] FOOD_HEADERS = new String[] {
            "id",
            "name",
            "category",
            "categoryLabel",
            "productionDate",
            "shelfLifeValue",
            "shelfLifeUnit",
            "shelfLifeUnitLabel",
            "openedDate",
            "afterOpenShelfLifeValue",
            "afterOpenShelfLifeUnit",
            "expiryDate",
            "dateSource",
            "quantity",
            "remainingQuantity",
            "unit",
            "storageMethod",
            "storageMethodLabel",
            "location",
            "locationLabel",
            "notes",
            "isFinished",
            "finishedAt",
            "createdAt",
            "updatedAt",
            "productProfileId",
            "barcode"
    };

    private FoodExcelExporter() {
    }

    static String mimeType() {
        return MIME_TYPE;
    }

    static void writeWorkbook(OutputStream outputStream, List<FoodItem> foods) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        try {
            writeEntry(zip, "[Content_Types].xml", contentTypesXml());
            writeEntry(zip, "_rels/.rels", rootRelationshipsXml());
            writeEntry(zip, "docProps/core.xml", corePropertiesXml());
            writeEntry(zip, "docProps/app.xml", appPropertiesXml());
            writeEntry(zip, "xl/workbook.xml", workbookXml());
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml());
            writeEntry(zip, "xl/styles.xml", stylesXml());
            writeEntry(zip, "xl/worksheets/sheet1.xml", foodSheetXml(foods));
            writeEntry(zip, "xl/worksheets/sheet2.xml", readmeSheetXml());
        } finally {
            zip.finish();
            zip.close();
        }
    }

    private static String foodSheetXml(List<FoodItem> foods) {
        StringBuilder builder = worksheetStart("A1:AA" + Math.max(1, (foods == null ? 0 : foods.size()) + 1));
        appendRow(builder, 1, FOOD_HEADERS);
        if (foods != null) {
            for (int index = 0; index < foods.size(); index++) {
                appendRow(builder, index + 2, foodValues(foods.get(index)));
            }
        }
        return worksheetEnd(builder);
    }

    private static String[] foodValues(FoodItem food) {
        if (food == null) {
            food = new FoodItem();
        }

        return new String[] {
                food.id,
                food.name,
                food.category,
                FoodData.categoryLabel(food.category),
                food.productionDate,
                integerText(food.shelfLifeValue),
                food.shelfLifeUnit,
                FoodData.shelfLifeUnitLabel(food.shelfLifeUnit),
                food.openedDate,
                integerText(food.afterOpenShelfLifeValue),
                food.afterOpenShelfLifeUnit,
                food.expiryDate,
                food.dateSource,
                numberText(food.quantity),
                numberText(food.remainingQuantity),
                food.unit,
                food.storageMethod,
                FoodData.storageLabel(food.storageMethod),
                food.location,
                FoodData.locationLabel(food.location),
                food.notes,
                Boolean.toString(food.isFinished),
                food.finishedAt,
                food.createdAt,
                food.updatedAt,
                food.productProfileId,
                food.barcode
        };
    }

    private static String readmeSheetXml() {
        StringBuilder builder = worksheetStart("A1:B11");
        appendRow(builder, 1, new String[] { "field", "description" });
        appendRow(builder, 2, new String[] { "expiryDate", "Canonical final edible date. Sorting and reminders use this field." });
        appendRow(builder, 3, new String[] { "dateSource", "manual, calculated, none, or unknown." });
        appendRow(builder, 4, new String[] { "productionDate + shelfLifeValue + shelfLifeUnit", "Inputs for calculated expiryDate mode." });
        appendRow(builder, 5, new String[] { "openedDate + afterOpenShelfLifeValue + afterOpenShelfLifeUnit", "Optional after-open reminder inputs; they do not replace expiryDate." });
        appendRow(builder, 6, new String[] { "quantity / remainingQuantity", "Remaining quantity is clamped between 0 and quantity." });
        appendRow(builder, 7, new String[] { "category / storageMethod / location", "Use value columns for future import. Label columns are for human review." });
        appendRow(builder, 8, new String[] { "isFinished / finishedAt", "Finished foods remain in the local archive." });
        appendRow(builder, 9, new String[] { "Import rule", "Future import must preview rows and ask for confirmation before writing local data." });
        appendRow(builder, 10, new String[] { "OCR rule", "OCR or AI results must never be auto-saved." });
        appendRow(builder, 11, new String[] { "Scope", "Local APK only. No cloud sync, account system, or publishing flow." });
        return worksheetEnd(builder);
    }

    private static StringBuilder worksheetStart(String dimension) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        builder.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        builder.append("<dimension ref=\"").append(escapeXml(dimension)).append("\"/>");
        builder.append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>");
        builder.append("<sheetFormatPr defaultRowHeight=\"18\"/>");
        builder.append("<sheetData>");
        return builder;
    }

    private static String worksheetEnd(StringBuilder builder) {
        builder.append("</sheetData></worksheet>");
        return builder.toString();
    }

    private static void appendRow(StringBuilder builder, int rowNumber, String[] values) {
        builder.append("<row r=\"").append(rowNumber).append("\">");
        for (int index = 0; index < values.length; index++) {
            String cellRef = columnName(index + 1) + rowNumber;
            builder.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\"><is><t");
            String value = safeText(values[index]);
            if (needsSpacePreserve(value)) {
                builder.append(" xml:space=\"preserve\"");
            }
            builder.append(">").append(escapeXml(value)).append("</t></is></c>");
        }
        builder.append("</row>");
    }

    private static String columnName(int index) {
        StringBuilder builder = new StringBuilder();
        int value = index;
        while (value > 0) {
            value--;
            builder.insert(0, (char) ('A' + (value % 26)));
            value /= 26;
        }
        return builder.toString();
    }

    private static String contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>";
    }

    private static String rootRelationshipsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";
    }

    private static String workbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>"
                + "<sheet name=\"foods\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "<sheet name=\"README\" sheetId=\"2\" r:id=\"rId2\"/>"
                + "</sheets></workbook>";
    }

    private static String workbookRelationshipsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
                + "</styleSheet>";
    }

    private static String corePropertiesXml() {
        String now = utcTimestamp();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:dcterms=\"http://purl.org/dc/terms/\" "
                + "xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title>Food Expiry Tracker Export</dc:title>"
                + "<dc:creator>Food Expiry Tracker</dc:creator>"
                + "<cp:lastModifiedBy>Food Expiry Tracker</cp:lastModifiedBy>"
                + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:created>"
                + "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:modified>"
                + "</cp:coreProperties>";
    }

    private static String appPropertiesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" "
                + "xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">"
                + "<Application>Food Expiry Tracker</Application>"
                + "</Properties>";
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String integerText(Integer value) {
        return value == null ? "" : Integer.toString(value.intValue());
    }

    private static String numberText(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        return String.format(Locale.US, "%.3f", value).replaceAll("\\.?0+$", "");
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static boolean needsSpacePreserve(String value) {
        return value.length() > 0
                && (Character.isWhitespace(value.charAt(0)) || Character.isWhitespace(value.charAt(value.length() - 1)));
    }

    private static String escapeXml(String value) {
        String text = safeText(value);
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '&') {
                builder.append("&amp;");
            } else if (ch == '<') {
                builder.append("&lt;");
            } else if (ch == '>') {
                builder.append("&gt;");
            } else if (ch == '"') {
                builder.append("&quot;");
            } else if (ch == '\'') {
                builder.append("&apos;");
            } else if (ch >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String utcTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
