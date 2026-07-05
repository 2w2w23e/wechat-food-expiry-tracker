package com.shiqi.expirytracker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class FoodData {
    static final String ALL = "all";

    static final List<Option> CATEGORIES = Arrays.asList(
            new Option("dairy", "乳制品", "ruzhipin", "rzp"),
            new Option("staple", "主食", "zhushi", "zs"),
            new Option("frozen", "冷冻食品", "lengdongshipin", "ldsp"),
            new Option("beverage", "饮品", "yinpin", "yp"),
            new Option("snack", "零食", "lingshi", "ls"),
            new Option("condiment", "调味品", "tiaoweipin", "twp"),
            new Option("produce", "蔬果", "shuguo", "sg"),
            new Option("meat_egg_seafood", "肉蛋水产", "roudanshuichan", "rdsc"),
            new Option("cooked", "熟食/剩菜", "shushishengcai", "sssc"),
            new Option("other", "其他", "qita", "qt")
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
            new Option(DateRules.STATUS_NORMAL, "正常"),
            new Option(DateRules.STATUS_UNKNOWN, "暂无到期日"),
            new Option(DateRules.STATUS_FINISHED, "已用完")
    );

    private static boolean pinyinAttempted = false;
    private static Object pinyinTransliterator = null;
    private static Method transliterateMethod = null;

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
        if (DateRules.STATUS_FINISHED.equals(value)) {
            return "已用完";
        }
        return "暂无到期日";
    }

    static List<String> statusFilterValues() {
        List<String> values = new ArrayList<String>();
        for (Option option : STATUS_FILTERS) {
            if (!ALL.equals(option.value)) {
                values.add(option.value);
            }
        }
        return values;
    }

    static List<Option> categoryFilterOptions() {
        return categoryFilterOptions(null);
    }

    static List<Option> categoryFilterOptions(List<FoodItem> foods) {
        List<Option> options = new ArrayList<Option>();
        options.add(new Option(ALL, "全部分类"));
        options.addAll(CATEGORIES);

        if (foods == null) {
            return options;
        }

        Map<String, Option> standardCategories = new LinkedHashMap<String, Option>();
        for (Option option : CATEGORIES) {
            standardCategories.put(option.value, option);
        }

        Map<String, Option> customCategories = new LinkedHashMap<String, Option>();
        for (FoodItem food : foods) {
            String value = normalizeCategoryValue(food.category);
            if (value.length() == 0 || ALL.equals(value) || standardCategories.containsKey(value) || customCategories.containsKey(value)) {
                continue;
            }
            customCategories.put(value, new Option(value, value));
        }

        options.addAll(customCategories.values());
        return options;
    }

    static boolean isKnownStatusFilter(String value) {
        for (String status : statusFilterValues()) {
            if (status.equals(value)) {
                return true;
            }
        }
        return false;
    }

    static boolean isKnownCategoryFilter(String value, List<Option> categoryOptions) {
        if (ALL.equals(value)) {
            return true;
        }

        for (Option option : categoryOptions) {
            if (option.value.equals(value)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesFoodSearch(FoodItem food, String searchText) {
        return foodSearchRank(food, searchText) >= 0;
    }

    static int foodSearchRank(FoodItem food, String searchText) {
        String query = normalizeSearchText(searchText);
        if (query.length() == 0) {
            return 0;
        }

        if (food == null) {
            return -1;
        }

        String name = normalizeSearchText(food.name);
        int nameIndex = name.indexOf(query);
        if (nameIndex >= 0) {
            return nameIndex * 100;
        }

        String pinyinText = pinyinText(food.name);
        int syllableIndex = pinyinSyllablePrefixIndex(pinyinText, query);
        if (syllableIndex >= 0) {
            return syllableIndex * 100 + 10;
        }

        int initialsIndex = initialsPrefixIndex(pinyinText, query);
        if (initialsIndex >= 0) {
            return initialsIndex * 100 + 20;
        }

        return -1;
    }

    static boolean matchesCategorySearch(Option option, String searchText) {
        String query = normalizeSearchText(searchText);
        if (query.length() == 0) {
            return true;
        }

        if (option == null) {
            return false;
        }

        String label = normalizeSearchText(option.label);
        if (label.contains(query)) {
            return true;
        }

        String optionPinyin = normalizeSearchText(option.pinyin);
        String optionInitials = normalizeSearchText(option.initials);
        if (optionPinyin.startsWith(query) || optionInitials.startsWith(query)) {
            return true;
        }

        String pinyinText = pinyinText(option.label);
        return normalizeSearchText(pinyinText).startsWith(query) || initialsFromPinyinText(pinyinText).startsWith(query);
    }

    private static String normalizeSearchText(String value) {
        String text = FoodItem.cleanText(value);
        return text.toLowerCase(Locale.US).replaceAll("\\s+", "");
    }

    private static String pinyinText(String value) {
        String text = FoodItem.cleanText(value);
        if (text.length() == 0) {
            return "";
        }

        String icuText = pinyinTextFromIcu(text);
        if (icuText.length() > 0) {
            return icuText;
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            String syllable = fallbackPinyin(text.charAt(index));
            if (syllable.length() > 0) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(syllable);
            } else {
                builder.append(text.charAt(index));
            }
        }
        return builder.toString();
    }

    private static String pinyinTextFromIcu(String value) {
        try {
            ensurePinyinTransliterator();
            if (pinyinTransliterator == null || transliterateMethod == null) {
                return "";
            }
            Object result = transliterateMethod.invoke(pinyinTransliterator, value);
            return result == null ? "" : result.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void ensurePinyinTransliterator() {
        if (pinyinAttempted) {
            return;
        }

        pinyinAttempted = true;
        try {
            Class<?> transliteratorClass = Class.forName("android.icu.text.Transliterator");
            Method getInstance = transliteratorClass.getMethod("getInstance", String.class);
            try {
                pinyinTransliterator = getInstance.invoke(null, "Han-Latin/Names; Latin-ASCII; Lower()");
            } catch (Exception ignored) {
                pinyinTransliterator = getInstance.invoke(null, "Han-Latin; Latin-ASCII; Lower()");
            }
            transliterateMethod = transliteratorClass.getMethod("transliterate", String.class);
        } catch (Exception ignored) {
            pinyinTransliterator = null;
            transliterateMethod = null;
        }
    }

    private static String initialsFromPinyinText(String value) {
        String text = FoodItem.cleanText(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
        if (text.length() == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        String[] parts = text.split("\\s+");
        for (String part : parts) {
            if (part.length() > 0) {
                builder.append(part.charAt(0));
            }
        }
        return builder.toString();
    }

    private static int pinyinSyllablePrefixIndex(String pinyinText, String query) {
        String text = FoodItem.cleanText(pinyinText).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
        if (text.length() == 0 || query.length() == 0) {
            return -1;
        }

        String[] parts = text.split("\\s+");
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].startsWith(query)) {
                return index;
            }
        }
        return -1;
    }

    private static int initialsPrefixIndex(String pinyinText, String query) {
        String initials = initialsFromPinyinText(pinyinText);
        if (initials.length() == 0 || query.length() == 0) {
            return -1;
        }

        for (int index = 0; index < initials.length(); index++) {
            if (initials.substring(index).startsWith(query)) {
                return index;
            }
        }
        return -1;
    }

    private static String fallbackPinyin(char value) {
        switch (value) {
            case '乳':
                return "ru";
            case '制':
                return "zhi";
            case '品':
                return "pin";
            case '主':
                return "zhu";
            case '食':
                return "shi";
            case '冷':
                return "leng";
            case '冻':
                return "dong";
            case '饮':
                return "yin";
            case '零':
                return "ling";
            case '调':
                return "tiao";
            case '味':
                return "wei";
            case '蔬':
                return "shu";
            case '果':
                return "guo";
            case '肉':
                return "rou";
            case '蛋':
                return "dan";
            case '水':
                return "shui";
            case '产':
                return "chan";
            case '熟':
                return "shu";
            case '剩':
                return "sheng";
            case '菜':
                return "cai";
            case '其':
                return "qi";
            case '他':
                return "ta";
            case '纯':
                return "chun";
            case '牛':
                return "niu";
            case '奶':
                return "nai";
            case '酸':
                return "suan";
            case '全':
                return "quan";
            case '麦':
                return "mai";
            case '面':
                return "mian";
            case '包':
                return "bao";
            case '挂':
                return "gua";
            case '饺':
                return "jiao";
            case '虾':
                return "xia";
            case '仁':
                return "ren";
            case '橙':
                return "cheng";
            case '汁':
                return "zhi";
            case '气':
                return "qi";
            case '泡':
                return "pao";
            case '薯':
                return "shu";
            case '片':
                return "pian";
            case '夹':
                return "jia";
            case '心':
                return "xin";
            case '饼':
                return "bing";
            case '干':
                return "gan";
            case '酱':
                return "jiang";
            case '油':
                return "you";
            case '辣':
                return "la";
            case '椒':
                return "jiao";
            case '苹':
                return "ping";
            case '青':
                return "qing";
            case '鸡':
                return "ji";
            case '猪':
                return "zhu";
            case '昨':
                return "zuo";
            case '晚':
                return "wan";
            case '米':
                return "mi";
            case '饭':
                return "fan";
            case '红':
                return "hong";
            case '烧':
                return "shao";
            case '豆':
                return "dou";
            case '腐':
                return "fu";
            case '蜂':
                return "feng";
            case '蜜':
                return "mi";
            default:
                return "";
        }
    }

}
