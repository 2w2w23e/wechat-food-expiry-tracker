package com.shiqi.expirytracker;

final class VerifiedBarcodeCatalog {
    static final class Entry {
        final String barcode;
        final String name;
        final String brand;
        final String generalName;
        final String category;
        final String specification;

        Entry(
                String barcode,
                String name,
                String brand,
                String generalName,
                String category,
                String specification
        ) {
            this.barcode = barcode;
            this.name = name;
            this.brand = brand;
            this.generalName = generalName;
            this.category = category;
            this.specification = specification;
        }
    }

    private static final Entry[] ENTRIES = new Entry[] {
            new Entry(
                    "6971416494254",
                    "大董老北京炸酱面",
                    "大董",
                    "炸酱面",
                    "方便食品",
                    "净含量 230克"
            ),
            new Entry(
                    "6930628889364",
                    "\u7a96\u85cf\u7ea2\u8150\u4e73",
                    "",
                    "\u7ea2\u8150\u4e73",
                    "\u8c03\u5473\u54c1",
                    ""
            ),
            new Entry(
                    "6926265313430",
                    "上好佳薯条",
                    "上好佳",
                    "薯条",
                    "膨化食品",
                    "原味 80克"
            ),
            new Entry(
                    "6920459940310",
                    "康师傅喝开水",
                    "康师傅",
                    "熟水饮用水",
                    "饮用水",
                    "550毫升"
            )
    };

    private VerifiedBarcodeCatalog() {}

    static Entry find(String barcode) {
        String code = BarcodeUtils.digitsOnly(barcode);
        for (Entry entry : ENTRIES) {
            if (entry.barcode.equals(code)) {
                return entry;
            }
        }
        return null;
    }

    static String recoverTruncatedOcrBarcode(String barcode, String rawText) {
        String code = BarcodeUtils.extractProductCode(barcode);
        if (code.length() != 12) {
            return code;
        }
        String text = FoodItem.cleanText(rawText);
        for (Entry entry : ENTRIES) {
            if (entry.barcode.length() == 13
                    && entry.barcode.startsWith(code)
                    && hasProductContext(entry, text)) {
                return entry.barcode;
            }
        }
        return code;
    }

    private static boolean hasProductContext(Entry entry, String rawText) {
        if (entry == null || rawText.length() == 0) {
            return false;
        }
        if (entry.brand.length() >= 2 && rawText.contains(entry.brand)) {
            return true;
        }
        for (String fragment : RecognitionTextCleaner.extractFoodNameFragments(rawText)) {
            String value = FoodItem.cleanText(fragment);
            if (value.length() >= 2
                    && (entry.name.contains(value)
                    || entry.generalName.contains(value))) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalCatalogBarcode(String barcode) {
        String code = BarcodeUtils.extractProductCode(barcode);
        Entry entry = find(code);
        return entry == null ? code : entry.barcode;
    }

    static String preferCatalogBackedBarcode(String scannedBarcode, String ocrBarcode) {
        String scanned = canonicalCatalogBarcode(scannedBarcode);
        String printed = canonicalCatalogBarcode(ocrBarcode);
        if (!BarcodeUtils.isSupportedProductCode(scanned)) {
            return BarcodeUtils.isSupportedProductCode(printed) ? printed : "";
        }
        if (!BarcodeUtils.isSupportedProductCode(printed) || scanned.equals(printed)) {
            return scanned;
        }
        return find(printed) != null && find(scanned) == null ? printed : scanned;
    }
}
