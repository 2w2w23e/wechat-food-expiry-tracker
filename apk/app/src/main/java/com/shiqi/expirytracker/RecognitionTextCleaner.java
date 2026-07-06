package com.shiqi.expirytracker;

final class RecognitionTextCleaner {
    private RecognitionTextCleaner() {}

    static String cleanForPackagingOcr(String rawText) {
        String raw = FoodItem.cleanText(rawText);
        if (raw.length() == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder(raw.length());
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = FoodItem.cleanText(line);
            if (cleaned.length() == 0 || isAppUiNoiseLine(cleaned)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(cleaned);
        }
        return builder.toString();
    }

    static String extractProductCodeFromOcr(String rawText) {
        String direct = BarcodeUtils.extractProductCode(rawText);
        if (BarcodeUtils.isSupportedProductCode(direct)) {
            return direct;
        }
        return BarcodeUtils.extractProductCode(cleanForPackagingOcr(rawText));
    }

    static String extractProductNameFromOcr(String rawText) {
        String cleaned = cleanForPackagingOcr(rawText);
        if (cleaned.length() == 0) {
            return "";
        }

        String best = "";
        int bestScore = 0;
        String previousGood = "";
        String[] lines = cleaned.split("\\r?\\n");
        for (String line : lines) {
            String candidate = cleanProductNameLine(line);
            int score = productNameScore(candidate);
            if (score <= 0) {
                continue;
            }
            String combined = combineProductNameLines(previousGood, candidate);
            int combinedScore = productNameScore(combined);
            if (combinedScore > score && combinedScore > bestScore) {
                best = combined;
                bestScore = combinedScore;
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
            previousGood = candidate;
        }
        return best;
    }

    private static boolean isAppUiNoiseLine(String line) {
        if (hasCandidateData(line)) {
            return false;
        }

        String compact = line
                .replace(" ", "")
                .replace("/", "")
                .replace("\\", "")
                .replace("：", ":")
                .toLowerCase();
        if (compact.length() == 0 || compact.matches("[0-9:.\\-]+")) {
            return true;
        }

        String[] noiseTokens = new String[] {
                "识别结果",
                "填入新增表单",
                "商品码",
                "商品名",
                "生产日期",
                "保质期",
                "最终日期",
                "未发现稳定条码",
                "等待条码",
                "待确认",
                "原文",
                "相机",
                "视频",
                "图片",
                "手动",
                "返回",
                "识别中",
                "已稳定",
                "条码已锁",
                "日期已稳",
                "已看到包装文字",
                "继续保持稳定",
                "候选锁定",
                "本地安卓版",
                "今日简报",
                "手机提醒",
                "新增食品",
                "导入excel",
                "导出excel",
                "筛选食品"
        };
        for (String token : noiseTokens) {
            if (compact.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCandidateData(String line) {
        if (BarcodeUtils.isSupportedProductCode(BarcodeUtils.extractProductCode(line))) {
            return true;
        }
        return DateOcrParser.parse(line).hasAnyCandidate();
    }

    private static String cleanProductNameLine(String line) {
        String text = FoodItem.cleanText(line);
        if (text.length() == 0) {
            return "";
        }
        text = text
                .replace("（", "(")
                .replace("）", ")")
                .replace("：", ":")
                .replace("，", " ")
                .replace(",", " ")
                .replace("。", " ")
                .replace("、", " ")
                .replace("|", " ")
                .replace("/", " ")
                .replace("\\", " ");
        text = text.replaceAll("\\s+", " ").trim();
        text = cutAtFirst(text, new String[] {
                "(", "（", "约", "配料", "净含量", "规格", "营养", "生产", "保质期"
        });
        text = text.replaceAll("^[0-9A-Za-z._:;\\- ]+", "").trim();
        text = text.replaceAll("[0-9A-Za-z._:;\\- ]+$", "").trim();
        if (text.length() > 28) {
            text = text.substring(0, 28).trim();
        }
        return text;
    }

    static boolean productNamesSimilar(String first, String second) {
        String left = productNameKey(first);
        String right = productNameKey(second);
        if (left.length() == 0 || right.length() == 0) {
            return false;
        }
        if (left.equals(right) || left.contains(right) || right.contains(left)) {
            return true;
        }
        int prefix = 0;
        int max = Math.min(left.length(), right.length());
        while (prefix < max && left.charAt(prefix) == right.charAt(prefix)) {
            prefix++;
        }
        return prefix >= 4;
    }

    private static String productNameKey(String value) {
        String text = FoodItem.cleanText(value).replace(" ", "").toLowerCase();
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c) || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String cutAtFirst(String text, String[] markers) {
        String value = FoodItem.cleanText(text);
        int cut = value.length();
        for (String marker : markers) {
            int index = value.indexOf(marker);
            if (index >= 0 && index < cut) {
                cut = index;
            }
        }
        return value.substring(0, cut).trim();
    }

    private static String combineProductNameLines(String first, String second) {
        String left = FoodItem.cleanText(first);
        String right = FoodItem.cleanText(second);
        if (left.length() == 0) {
            return right;
        }
        if (right.length() == 0 || left.contains(right)) {
            return left;
        }
        if (right.contains(left)) {
            return right;
        }
        String combined = left + " " + right;
        return combined.length() <= 24 ? combined : left;
    }

    private static int productNameScore(String candidate) {
        String text = FoodItem.cleanText(candidate);
        if (text.length() < 2) {
            return 0;
        }
        String compact = text.replace(" ", "").toLowerCase();
        if (compact.length() < 2) {
            return 0;
        }
        if (BarcodeUtils.isSupportedProductCode(BarcodeUtils.extractProductCode(compact))) {
            return 0;
        }
        if (containsJapaneseKana(compact)) {
            return 0;
        }
        if (DateOcrParser.parse(text).hasAnyCandidate()) {
            return 0;
        }
        String[] blockTokens = new String[] {
                "营养成分", "配料", "食品添加剂", "执行标准", "生产许可证", "产品标准",
                "贮存", "储存", "保存", "地址", "电话", "网址", "委托", "制造商",
                "保质期", "生产日期", "净含量", "规格", "食用方法", "请勿", "过敏",
                "二维码", "条形码", "扫码", "合格", "检验", "温馨提示"
        };
        for (String token : blockTokens) {
            if (compact.contains(token)) {
                return 0;
            }
        }

        int chinese = 0;
        int letters = 0;
        int digits = 0;
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (isChinese(c)) {
                chinese++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                letters++;
            } else if (c >= '0' && c <= '9') {
                digits++;
            }
        }
        if (chinese + letters < 2 || digits > chinese + letters) {
            return 0;
        }

        int score = chinese * 4 + letters;
        if (compact.length() >= 3 && compact.length() <= 14) {
            score += 12;
        }
        String[] foodTokens = new String[] {
                "面", "饭", "粉", "蛋", "丸", "水", "茶", "饮", "奶", "酱",
                "汁", "糕", "饼", "糖", "豆", "肉", "鱼", "虾", "食品", "饮料"
        };
        for (String token : foodTokens) {
            if (compact.contains(token)) {
                score += 10;
                break;
            }
        }
        return score;
    }

    private static boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    private static boolean containsJapaneseKana(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u3040' && c <= '\u30ff') || (c >= '\uff66' && c <= '\uff9f')) {
                return true;
            }
        }
        return false;
    }
}
