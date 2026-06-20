package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class FoodData {
    static final String ALL = "all";

    static final List<Option> CATEGORIES = Arrays.asList(
            new Option("dairy", "乳制品"),
            new Option("staple", "主食"),
            new Option("frozen", "冷冻食品"),
            new Option("beverage", "饮品"),
            new Option("snack", "零食"),
            new Option("condiment", "调味品"),
            new Option("produce", "蔬果"),
            new Option("meat_egg_seafood", "肉蛋水产"),
            new Option("cooked", "熟食/剩菜"),
            new Option("other", "其他")
    );

    static final List<Option> STORAGE_METHODS = Arrays.asList(
            new Option("refrigerated", "冷藏"),
            new Option("room_temp", "常温"),
            new Option("frozen", "冷冻"),
            new Option("avoid_light", "避光"),
            new Option("cool_dry", "阴凉干燥")
    );

    static final List<Option> SHELF_LIFE_UNITS = Arrays.asList(
            new Option("day", "日"),
            new Option("month", "月"),
            new Option("year", "年")
    );

    static final List<Option> STATUS_FILTERS = Arrays.asList(
            new Option(ALL, "全部状态"),
            new Option(DateRules.STATUS_EXPIRED, "已过期"),
            new Option(DateRules.STATUS_TODAY, "今日到期"),
            new Option(DateRules.STATUS_SOON, "即将到期"),
            new Option(DateRules.STATUS_NORMAL, "正常")
    );

    private FoodData() {}

    static String normalizeCategoryValue(String value) {
        String text = FoodItem.cleanText(value);
        if (text.length() == 0) {
            return "other";
        }

        for (Option option : CATEGORIES) {
            if (option.value.equals(text)) {
                return option.value;
            }
            if (option.label.equals(text)) {
                return option.value;
            }
        }

        return text;
    }

    static String labelFor(List<Option> options, String value, String fallback) {
        for (Option option : options) {
            if (option.value.equals(value)) {
                return option.label;
            }
        }
        return fallback;
    }

    static String categoryLabel(String value) {
        String normalized = normalizeCategoryValue(value);
        return labelFor(CATEGORIES, normalized, normalized.length() > 0 ? normalized : "其他");
    }

    static String storageLabel(String value) {
        return labelFor(STORAGE_METHODS, value, value == null || value.length() == 0 ? "常温" : value);
    }

    static String shelfLifeUnitLabel(String value) {
        return labelFor(SHELF_LIFE_UNITS, value, "");
    }

    static String statusLabel(String value) {
        if (DateRules.STATUS_EXPIRED.equals(value)) {
            return "已过期";
        }
        if (DateRules.STATUS_TODAY.equals(value)) {
            return "今日到期";
        }
        if (DateRules.STATUS_SOON.equals(value)) {
            return "即将到期";
        }
        if (DateRules.STATUS_NORMAL.equals(value)) {
            return "正常";
        }
        return "暂无到期日";
    }

    static List<Option> categoryFilterOptions() {
        List<Option> options = new ArrayList<Option>();
        options.add(new Option(ALL, "全部分类"));
        options.addAll(CATEGORIES);
        return options;
    }

    static List<FoodItem> sampleFoods() {
        List<FoodItem> foods = new ArrayList<FoodItem>();
        foods.add(food("food_001", "纯牛奶", "dairy", "2026-06-01", 30, "day", "2026-07-01", "calculated", 6, 4, "盒", "refrigerated", "正常未来到期示例", "2026-06-01T09:00:00+0800"));
        foods.add(food("food_002", "酸奶", "dairy", "2026-05-25", 15, "day", "2026-06-09", "calculated", 3, 1, "瓶", "refrigerated", "今日到期示例", "2026-05-25T08:30:00+0800"));
        foods.add(food("food_003", "全麦面包", "staple", "2026-06-05", 7, "day", "2026-06-12", "calculated", 1, 1, "袋", "room_temp", "早餐面包，临期示例", "2026-06-05T10:00:00+0800"));
        foods.add(food("food_004", "挂面", "staple", "2025-12-15", 18, "month", "2027-06-15", "calculated", 2, 2, "包", "cool_dry", "放在橱柜干燥处", "2026-05-28T11:30:00+0800"));
        foods.add(food("food_005", "冷冻水饺", "frozen", "2025-12-01", 6, "month", "2026-06-01", "calculated", 1000, 250, "克", "frozen", "已过期示例", "2026-01-10T18:00:00+0800"));
        foods.add(food("food_006", "冷冻虾仁", "frozen", "2026-01-10", 12, "month", "2027-01-10", "calculated", 500, 300, "克", "frozen", "密封冷冻保存", "2026-05-21T17:00:00+0800"));
        foods.add(food("food_007", "橙汁", "beverage", "", null, "", "2026-06-20", "manual", 1, 1, "瓶", "avoid_light", "手动填写最终可食用日期", "2026-06-07T14:00:00+0800"));
        foods.add(food("food_008", "气泡水", "beverage", "2026-04-01", 6, "month", "2026-10-01", "calculated", 6, 5, "罐", "avoid_light", "常温避光保存", "2026-06-03T16:10:00+0800"));
        foods.add(food("food_009", "薯片", "snack", "2026-03-01", 9, "month", "2026-12-01", "calculated", 3, 2, "袋", "room_temp", "零食柜上层", "2026-05-18T13:20:00+0800"));
        foods.add(food("food_010", "夹心饼干", "snack", "", null, "", "2026-06-07", "manual", 2, 1, "盒", "room_temp", "手动日期，已过期示例", "2026-06-01T15:00:00+0800"));
        foods.add(food("food_011", "酱油", "condiment", "2025-06-01", 1, "year", "2026-06-01", "calculated", 1, 0.5, "瓶", "cool_dry", "年份保质期示例", "2026-05-01T12:00:00+0800"));
        foods.add(food("food_012", "辣椒酱", "condiment", "", null, "", "2026-06-13", "manual", 1, 1, "瓶", "refrigerated", "开封后冷藏，临期示例", "2026-06-04T12:00:00+0800"));
        foods.add(food("food_013", "苹果", "produce", "", null, "", "2026-06-15", "manual", 6, 4, "个", "refrigerated", "冷藏抽屉里", "2026-06-06T08:00:00+0800"));
        foods.add(food("food_014", "青菜", "produce", "", null, "", "2026-06-08", "manual", 1, 1, "把", "refrigerated", "手动日期，已过期示例", "2026-06-06T18:00:00+0800"));
        foods.add(food("food_015", "鸡蛋", "meat_egg_seafood", "2026-06-01", 21, "day", "2026-06-22", "calculated", 12, 8, "个", "refrigerated", "冰箱门架", "2026-06-01T07:50:00+0800"));
        foods.add(food("food_016", "猪肉片", "meat_egg_seafood", "2026-06-06", 3, "day", "2026-06-09", "calculated", 500, 200, "克", "refrigerated", "今日到期示例", "2026-06-06T18:30:00+0800"));
        foods.add(food("food_017", "昨晚米饭", "cooked", "", null, "", "2026-06-10", "manual", 1, 1, "盒", "refrigerated", "剩饭，手动填写最终可食用日期", "2026-06-08T20:30:00+0800"));
        foods.add(food("food_018", "红烧肉剩菜", "cooked", "", null, "", "2026-06-09", "manual", 1, 0.5, "盒", "refrigerated", "今日到期示例", "2026-06-08T13:00:00+0800"));
        foods.add(food("food_019", "豆腐", "other", "", null, "", "2026-06-11", "manual", 1, 1, "盒", "refrigerated", "无法确定细分类时归入其他", "2026-06-08T10:20:00+0800"));
        foods.add(food("food_020", "蜂蜜", "other", "2025-05-01", 2, "year", "2027-05-01", "calculated", 1, 1, "瓶", "cool_dry", "阴凉干燥处保存", "2026-05-03T09:10:00+0800"));
        return foods;
    }

    private static FoodItem food(
            String id,
            String name,
            String category,
            String productionDate,
            Integer shelfLifeValue,
            String shelfLifeUnit,
            String expiryDate,
            String dateSource,
            double quantity,
            double remainingQuantity,
            String unit,
            String storageMethod,
            String notes,
            String timestamp
    ) {
        FoodItem item = new FoodItem();
        item.id = id;
        item.name = name;
        item.category = category;
        item.productionDate = productionDate;
        item.shelfLifeValue = shelfLifeValue;
        item.shelfLifeUnit = shelfLifeUnit;
        item.expiryDate = expiryDate;
        item.dateSource = dateSource;
        item.quantity = quantity;
        item.remainingQuantity = remainingQuantity;
        item.unit = unit;
        item.storageMethod = storageMethod;
        item.notes = notes;
        item.createdAt = timestamp;
        item.updatedAt = timestamp;
        return item;
    }
}
