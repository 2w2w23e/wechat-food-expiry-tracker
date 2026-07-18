package com.shiqi.expirytracker;

import java.util.Locale;

final class DateOcrResultPayload {
    static final String EXTRA_PRODUCTION_DATE = "DATE_OCR_PRODUCTION_DATE";
    static final String EXTRA_EXPIRY_DATE = "DATE_OCR_EXPIRY_DATE";
    static final String EXTRA_EXPIRY_CALCULATED = "DATE_OCR_EXPIRY_CALCULATED";
    static final String EXTRA_SHELF_LIFE_VALUE = "DATE_OCR_SHELF_LIFE_VALUE";
    static final String EXTRA_SHELF_LIFE_UNIT = "DATE_OCR_SHELF_LIFE_UNIT";
    static final String EXTRA_RAW_TEXT = "DATE_OCR_RAW_TEXT";
    static final String EXTRA_SUMMARY = "DATE_OCR_SUMMARY";

    private DateOcrResultPayload() {}

    static FoodItem toDraft(
            String productionDate,
            String expiryDate,
            boolean expiryCalculated,
            Integer shelfLifeValue,
            String shelfLifeUnit
    ) {
        FoodItem draft = new FoodItem();
        String cleanedProductionDate = FoodItem.cleanText(productionDate);
        draft.productionDate = DateRules.isValidDateString(cleanedProductionDate)
                && cleanedProductionDate.compareTo(DateRules.getTodayString()) <= 0
                ? cleanedProductionDate
                : "";
        draft.expiryDate = DateRules.isValidDateString(FoodItem.cleanText(expiryDate))
                ? FoodItem.cleanText(expiryDate)
                : "";
        if (expiryCalculated && draft.productionDate.length() == 0) {
            draft.expiryDate = "";
        }
        if (draft.productionDate.length() > 0
                && draft.expiryDate.length() > 0
                && draft.expiryDate.compareTo(draft.productionDate) < 0) {
            draft.expiryDate = "";
        }
        draft.shelfLifeValue = shelfLifeValue != null && shelfLifeValue.intValue() > 0 ? shelfLifeValue : null;
        draft.shelfLifeUnit = draft.shelfLifeValue != null && DateRules.isShelfLifeUnit(FoodItem.cleanText(shelfLifeUnit))
                ? FoodItem.cleanText(shelfLifeUnit)
                : "";
        if (draft.expiryDate.length() > 0) {
            draft.dateSource = expiryCalculated ? "calculated" : "manual";
        }
        return draft;
    }

    static FoodItem toDraft(DateOcrFrameVoter.VoteResult vote) {
        if (vote == null) {
            return new FoodItem();
        }

        DateOcrFrameVoter.StableDate production = vote.productionDate;
        DateOcrFrameVoter.StableDate expiry = bestExpiryDate(vote);
        if (production != null && expiry != null
                && expiry.value.compareTo(production.value) < 0) {
            if (production.weakHint && !expiry.weakHint) {
                production = null;
            } else if (!production.weakHint && expiry.weakHint) {
                expiry = null;
            } else {
                production = null;
                expiry = null;
            }
        }
        String productionDate = production == null ? "" : production.value;
        String expiryDate = expiry == null ? "" : expiry.value;
        boolean expiryCalculated = expiry != null && expiry.calculated;
        Integer shelfLifeValue = vote.shelfLife == null ? null : Integer.valueOf(vote.shelfLife.value);
        String shelfLifeUnit = vote.shelfLife == null ? "" : vote.shelfLife.unit;
        return toDraft(productionDate, expiryDate, expiryCalculated, shelfLifeValue, shelfLifeUnit);
    }

    static boolean hasUsableDraft(FoodItem draft) {
        if (draft == null) {
            return false;
        }
        return DateRules.isValidDateString(draft.productionDate)
                || DateRules.isValidDateString(draft.expiryDate)
                || draft.shelfLifeValue != null;
    }

    static DateOcrFrameVoter.StableDate bestExpiryDate(DateOcrFrameVoter.VoteResult vote) {
        if (vote == null) {
            return null;
        }
        if (vote.expiryDate != null) {
            return vote.expiryDate;
        }
        if (vote.calculatedExpiryDate == null
                || vote.productionDate == null
                || vote.productionDate.value.compareTo(DateRules.getTodayString()) > 0
                || vote.calculatedExpiryDate.value.compareTo(vote.productionDate.value) < 0) {
            return null;
        }
        return vote.calculatedExpiryDate;
    }

    static String summary(DateOcrFrameVoter.VoteResult vote) {
        if (vote == null || !vote.hasStableCandidate()) {
            return "还没有稳定候选";
        }

        StringBuilder builder = new StringBuilder();
        appendDate(builder, "生产日期", vote.productionDate);
        appendShelfLife(builder, vote.shelfLife);
        appendDate(builder, "最终日期", bestExpiryDate(vote));
        if (vote.hasConflict) {
            appendLine(builder, "候选冲突：需要继续扫描或手动确认");
        }
        appendLine(builder, String.format(Locale.US, "候选帧：%d/%d，确认阈值：%d",
                vote.framesWithCandidates,
                vote.frameCount,
                vote.minVotes));
        return builder.toString().trim();
    }

    private static void appendDate(StringBuilder builder, String label, DateOcrFrameVoter.StableDate date) {
        if (date == null) {
            return;
        }
        appendLine(builder, String.format(Locale.US, "%s：%s（%d 帧）", label, date.value, date.votes));
    }

    private static void appendShelfLife(StringBuilder builder, DateOcrFrameVoter.StableShelfLife shelfLife) {
        if (shelfLife == null) {
            return;
        }
        appendLine(builder, String.format(Locale.US, "保质期：%d%s（%d 帧）",
                shelfLife.value,
                FoodData.shelfLifeUnitLabel(shelfLife.unit),
                shelfLife.votes));
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }
}
