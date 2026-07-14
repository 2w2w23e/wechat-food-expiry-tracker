package com.shiqi.expirytracker;

import java.util.Locale;

final class BarcodeCategoryClassifier {
    private BarcodeCategoryClassifier() {}

    static String inferCategory(BarcodeProductInfo info) {
        if (info == null) {
            return "other";
        }

        String name = (info.displayName() + " " + info.brand).toLowerCase(Locale.US);
        String metadata = (
                info.generalName + " " + info.category + " " + info.specification
        ).toLowerCase(Locale.US);

        String categoryFromName = classifyText(name);
        if (!"other".equals(categoryFromName)) {
            return categoryFromName;
        }
        return classifyText(metadata);
    }

    private static String classifyText(String text) {

        if (containsAny(text, "冷冻", "速冻", "冰淇淋", "冰棒", "雪糕")) {
            return "frozen";
        }
        if (containsAny(text, "牛奶", "酸奶", "乳", "奶酪", "芝士", "奶油", "乳饮")) {
            return "dairy";
        }
        if (containsAny(text, "酱油", "醋", "食用油", "调和油", "盐", "糖", "味精", "鸡精", "调味", "辣椒酱", "豆瓣酱", "沙拉酱")) {
            return "condiment";
        }
        if (containsAny(text, "鸡蛋", "鸭蛋", "猪肉", "牛肉", "羊肉", "鸡肉", "鱼", "虾", "蟹", "贝", "肉", "蛋", "水产", "海鲜")) {
            return "meat_egg_seafood";
        }
        if (containsAny(text, "熟食", "剩菜", "便当", "饭团", "即食菜", "预制菜")) {
            return "cooked";
        }
        if (containsAny(text, "蔬菜", "水果", "苹果", "香蕉", "橙", "梨", "番茄", "黄瓜", "青菜", "生菜")) {
            return "produce";
        }
        if (containsAny(text, "大米", "面粉", "挂面", "面条", "面包", "馒头", "饺子皮", "麦片", "燕麦", "主食")) {
            return "staple";
        }
        if (containsAny(text, "饮用水", "矿泉水", "纯净水", "气泡水", "饮料", "果汁", "茶饮", "咖啡", "可乐", "啤酒", "酒")) {
            return "beverage";
        }
        if (containsAny(text, "饼干", "薯片", "巧克力", "糖果", "软糖", "口香糖", "坚果", "瓜子", "糕点", "蛋糕", "零食", "膨化", "uha", "悠哈", "味觉糖")) {
            return "snack";
        }
        return "other";
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
