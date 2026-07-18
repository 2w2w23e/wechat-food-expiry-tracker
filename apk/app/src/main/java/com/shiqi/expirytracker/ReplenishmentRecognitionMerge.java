package com.shiqi.expirytracker;

final class ReplenishmentRecognitionMerge {
    private ReplenishmentRecognitionMerge() {
    }

    static FoodItem mergeDates(FoodItem replenishmentDraft, FoodItem recognizedDraft) {
        FoodItem merged = replenishmentDraft == null
                ? new FoodItem()
                : replenishmentDraft.copy();
        if (recognizedDraft == null) {
            return merged;
        }

        if (DateRules.isValidDateString(recognizedDraft.productionDate)) {
            merged.productionDate = recognizedDraft.productionDate;
        }
        if (recognizedDraft.shelfLifeValue != null
                && recognizedDraft.shelfLifeValue.intValue() > 0) {
            merged.shelfLifeValue = recognizedDraft.shelfLifeValue;
            merged.shelfLifeUnit = FoodItem.cleanText(recognizedDraft.shelfLifeUnit);
        }
        if (DateRules.isValidDateString(recognizedDraft.expiryDate)) {
            merged.expiryDate = recognizedDraft.expiryDate;
        }
        if ("calculated".equals(recognizedDraft.dateSource)
                || "manual".equals(recognizedDraft.dateSource)) {
            merged.dateSource = recognizedDraft.dateSource;
        }
        return merged;
    }

    static boolean hasRecognizedDate(FoodItem recognizedDraft) {
        return recognizedDraft != null
                && (DateRules.isValidDateString(recognizedDraft.productionDate)
                || DateRules.isValidDateString(recognizedDraft.expiryDate)
                || (recognizedDraft.shelfLifeValue != null
                && recognizedDraft.shelfLifeValue.intValue() > 0));
    }
}
