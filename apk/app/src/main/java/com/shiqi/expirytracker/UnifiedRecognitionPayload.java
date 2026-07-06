package com.shiqi.expirytracker;

final class UnifiedRecognitionPayload {
    static final String EXTRA_BARCODE = "UNIFIED_RECOGNITION_BARCODE";
    static final String EXTRA_PRODUCT_FOUND = "UNIFIED_RECOGNITION_PRODUCT_FOUND";
    static final String EXTRA_PRODUCT_NAME = "UNIFIED_RECOGNITION_PRODUCT_NAME";
    static final String EXTRA_PRODUCT_CATEGORY = "UNIFIED_RECOGNITION_PRODUCT_CATEGORY";
    static final String EXTRA_PRODUCT_NOTES = "UNIFIED_RECOGNITION_PRODUCT_NOTES";
    static final String EXTRA_SUMMARY = "UNIFIED_RECOGNITION_SUMMARY";

    private UnifiedRecognitionPayload() {}

    static FoodItem toDraft(
            String barcode,
            String productName,
            String productCategory,
            String productNotes,
            String productionDate,
            String expiryDate,
            boolean expiryCalculated,
            Integer shelfLifeValue,
            String shelfLifeUnit
    ) {
        FoodItem draft = DateOcrResultPayload.toDraft(
                productionDate,
                expiryDate,
                expiryCalculated,
                shelfLifeValue,
                shelfLifeUnit
        );

        String cleanBarcode = BarcodeUtils.extractProductCode(barcode);
        String cleanName = FoodItem.cleanText(productName);
        String cleanCategory = FoodItem.cleanText(productCategory);
        String cleanNotes = FoodItem.cleanText(productNotes);

        if (cleanName.length() > 0) {
            draft.name = cleanName;
        } else if (BarcodeUtils.isSupportedProductCode(cleanBarcode)) {
            draft.name = "条码商品 " + cleanBarcode;
        }
        if (cleanCategory.length() > 0) {
            draft.category = cleanCategory;
        }
        if (cleanName.length() > 0 || cleanBarcode.length() > 0) {
            draft.unit = "件";
        }
        if ((BarcodeUtils.isSupportedProductCode(cleanBarcode) || cleanName.length() > 0)
                && !DateOcrResultPayload.hasUsableDraft(draft)) {
            draft.dateSource = "none";
        }

        StringBuilder notes = new StringBuilder();
        if (cleanNotes.length() > 0) {
            notes.append(cleanNotes);
        }
        if (cleanBarcode.length() > 0 && notes.indexOf(cleanBarcode) < 0) {
            if (notes.length() > 0) {
                notes.append('\n');
            }
            notes.append("条码：").append(cleanBarcode);
        }
        draft.notes = notes.toString();
        return draft;
    }

    static boolean hasUsableDraft(FoodItem draft) {
        if (draft == null) {
            return false;
        }
        return FoodItem.cleanText(draft.name).length() > 0
                || FoodItem.cleanText(draft.notes).length() > 0
                || DateOcrResultPayload.hasUsableDraft(draft);
    }

    static String summary(
            String barcode,
            String productName,
            DateOcrFrameVoter.VoteResult dateVote,
            boolean productFound
    ) {
        StringBuilder builder = new StringBuilder();
        String cleanBarcode = BarcodeUtils.extractProductCode(barcode);
        String cleanProductName = FoodItem.cleanText(productName);
        if (cleanBarcode.length() > 0) {
            appendLine(builder, "商品码：" + cleanBarcode);
        }
        if (cleanProductName.length() > 0) {
            appendLine(builder, "商品名：" + cleanProductName + (productFound ? "（条码查询）" : "（待补全）"));
        }
        String dateSummary = DateOcrResultPayload.summary(dateVote);
        if (!"还没有稳定候选".equals(dateSummary)) {
            appendLine(builder, dateSummary);
        }
        if (builder.length() == 0) {
            return "暂无可填入字段";
        }
        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, String line) {
        String text = FoodItem.cleanText(line);
        if (text.length() == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(text);
    }
}
