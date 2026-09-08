package com.shiqi.expirytracker;

final class FoodValidationRules {
    private FoodValidationRules() {
    }

    static String productionDateError(String productionDate, String today) {
        String value = FoodItem.cleanText(productionDate);
        if (value.length() == 0) {
            return "";
        }
        if (!DateRules.isValidDateString(value)) {
            return "\u751f\u4ea7\u65e5\u671f\u683c\u5f0f\u4e0d\u6b63\u786e";
        }
        if (DateRules.compareDates(value, today) > 0) {
            return "\u751f\u4ea7\u65e5\u671f\u4e0d\u80fd\u665a\u4e8e\u4eca\u5929";
        }
        return "";
    }

    static String expiryDateError(String productionDate, String expiryDate) {
        String expiry = FoodItem.cleanText(expiryDate);
        if (!DateRules.isValidDateString(expiry)) {
            return "\u6700\u7ec8\u53ef\u98df\u7528\u65e5\u671f\u683c\u5f0f\u4e0d\u6b63\u786e";
        }
        String production = FoodItem.cleanText(productionDate);
        if (DateRules.isValidDateString(production)
                && DateRules.compareDates(expiry, production) < 0) {
            return "\u6700\u7ec8\u53ef\u98df\u7528\u65e5\u671f\u4e0d\u80fd\u65e9\u4e8e\u751f\u4ea7\u65e5\u671f";
        }
        return "";
    }

    static String openedDateError(
            String productionDate,
            String openedDate,
            String expiryDate,
            String today
    ) {
        String opened = FoodItem.cleanText(openedDate);
        if (!DateRules.isValidDateString(opened)) {
            return "\u5f00\u5c01\u65e5\u671f\u683c\u5f0f\u4e0d\u6b63\u786e";
        }
        String production = FoodItem.cleanText(productionDate);
        if (DateRules.isValidDateString(production)
                && DateRules.compareDates(opened, production) < 0) {
            return "\u5f00\u5c01\u65e5\u671f\u4e0d\u80fd\u65e9\u4e8e\u751f\u4ea7\u65e5\u671f";
        }
        if (DateRules.compareDates(opened, today) > 0) {
            return "\u5f00\u5c01\u65e5\u671f\u4e0d\u80fd\u665a\u4e8e\u4eca\u5929";
        }
        String expiry = FoodItem.cleanText(expiryDate);
        if (DateRules.isValidDateString(expiry)
                && DateRules.compareDates(opened, expiry) > 0) {
            return "\u5f00\u5c01\u65e5\u671f\u4e0d\u80fd\u665a\u4e8e\u6700\u7ec8\u53ef\u98df\u7528\u65e5\u671f";
        }
        return "";
    }
}
