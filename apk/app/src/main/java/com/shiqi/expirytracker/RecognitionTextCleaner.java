package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RecognitionTextCleaner {
    private static final Pattern NUMBERED_SENTENCE_PREFIX = Pattern.compile(
            "^\\s*(?:[（(]?\\d{1,3}[)）]|[（(]?\\d{1,3}[.．、:：])\\s*[\\u4e00-\\u9fffA-Za-z]"
    );
    private static final Pattern PRODUCT_NAME_LABEL = Pattern.compile(
            "(?:(?:产\\s*品|商\\s*品|食\\s*品)\\s*(?:名\\s*称|名)|品\\s*名)"
                    + "\\s*[:：]?\\s*(.{2,28})",
            Pattern.CASE_INSENSITIVE
    );

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
        String cleaned = cleanForPackagingOcr(rawText);
        for (String line : cleaned.split("\\r?\\n")) {
            String value = FoodItem.cleanText(line);
            String compact = value.replace(" ", "").toLowerCase();
            if (DateOcrParser.parse(value).hasAnyCandidate()) {
                continue;
            }
            if (containsAny(compact, new String[] {
                    "生产日期", "制造日期", "包装日期", "有效期", "保质期", "最终日期", "最终可食用"
            })) {
                continue;
            }
            String candidate = BarcodeUtils.extractProductCode(value);
            if (!BarcodeUtils.isSupportedProductCode(candidate)) {
                continue;
            }
            boolean hasBarcodeContext = containsAny(compact, new String[] {
                    "条码", "商品码", "barcode", "gtin", "ean", "upc"
            });
            if (!hasBarcodeContext && candidate.startsWith("0")) {
                continue;
            }
            if (candidate.length() == 8 && !hasBarcodeContext) {
                continue;
            }
            return candidate;
        }
        return "";
    }

    private static boolean containsAny(String text, String[] tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
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
            String labeled = intelligentProductNameCandidate(extractLabeledProductName(line));
            int labeledScore = productNameScore(labeled);
            if (labeledScore > 0 && labeledScore + 180 > bestScore) {
                best = labeled;
                bestScore = labeledScore + 180;
            }
            String candidate = intelligentProductNameCandidate(line);
            if (!isHighConfidenceFoodProductName(candidate)) {
                if (isLikelyLatinBrand(candidate)) {
                    previousGood = candidate;
                }
                continue;
            }
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

    private static boolean isLikelyLatinBrand(String value) {
        String text = FoodItem.cleanText(value);
        String compact = productNameKey(text);
        return text.length() <= 12
                && compact.matches("[a-z0-9]{2,12}")
                && productNameScore(text) > 0;
    }

    static String extractLabeledProductName(String line) {
        String text = FoodItem.cleanText(line);
        Matcher matcher = PRODUCT_NAME_LABEL.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return cleanProductNameLine(matcher.group(1));
    }

    static boolean hasProductNameLabel(String line) {
        return PRODUCT_NAME_LABEL.matcher(FoodItem.cleanText(line)).find();
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

    static String cleanProductNameLine(String line) {
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
        text = normalizeCommonPackagingOcr(text);
        text = text.replaceAll("\\s+", " ").trim();
        text = cutAtFirst(text, new String[] {
                "(", "（", "约", "配料", "净含量", "净重", "规格", "营养", "生产日期", "制造日期",
                "包装日期", "保质期", "有效期", "执行标准", "产品标准", "标准号", "厂家", "厂址", "地址",
                "食用方法", "冲调方法", "扫码", "二维码", "条形码", "产品名称", "产品类型", "产品类别"
        });
        text = text.replaceAll("(?i)(?:^|\\s)[o0](?=\\s|$)", " ");
        text = text.replaceAll("^[._:;\\- ]+", "").trim();
        text = text.replaceAll("[._:;\\- ]+$", "").trim();
        if (text.length() > 28) {
            text = text.substring(0, 28).trim();
        }
        return text;
    }

    static List<String> extractFoodNameFragments(String value) {
        String text = normalizeCommonPackagingOcr(FoodItem.cleanText(value));
        List<String> fragments = new ArrayList<String>();
        String[] productPhrases = new String[] {
                "果汁饮料", "茶饮料", "老北京炸酱面", "炸酱面", "去壳清水鹌鹑蛋", "清水鹌鹑蛋",
                "鹌鹑蛋", "矿泉水", "苏打水", "喝开水", "酸奶", "牛奶", "面包", "饼干", "腐乳", "酸菜", "果汁"
        };
        for (String phrase : productPhrases) {
            if (text.contains(phrase) && !fragments.contains(phrase)) {
                fragments.add(phrase);
            }
        }
        if (text.contains("清水") && text.contains("鹌鹑蛋") && !fragments.contains("清水鹌鹑蛋")) {
            fragments.add(0, "清水鹌鹑蛋");
        }
        return fragments;
    }

    static boolean isCanonicalFoodName(String value) {
        String text = FoodItem.cleanText(value);
        for (String fragment : extractFoodNameFragments(text)) {
            if (text.equalsIgnoreCase(fragment)) {
                return true;
            }
            String compactText = productNameKey(text);
            String compactFragment = productNameKey(fragment);
            if (compactText.endsWith(compactFragment)
                    && compactText.length() <= compactFragment.length() + 4
                    && countOccurrences(compactText, compactFragment) == 1) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeCommonPackagingOcr(String value) {
        String text = FoodItem.cleanText(value)
                .replace("鹤鹑", "鹌鹑")
                .replace("鸣鹑", "鹌鹑")
                .replace("鳴鹑", "鹌鹑")
                .replace("朝鹑", "鹌鹑")
                .replace("票汁", "果汁")
                .replace("系料", "饮料")
                .replace("個开水", "喝开水")
                .replace("倜开水", "喝开水")
                .replace("遇开水", "喝开水")
                .replace("渴开水", "喝开水");
        text = text
                .replace("发醇", "发酵")
                .replace("發醇", "发酵")
                .replace("發酵", "发酵");
        text = text.replaceAll("(?i)^[\\u4e00-\\u9fff](?=blue\\b)", "");
        return text.replaceAll("(?i)blue", "BLUE");
    }

    static String intelligentProductNameCandidate(String value) {
        String text = cleanProductNameLine(value);
        if (text.length() == 0) {
            return "";
        }

        List<String> fragments = extractFoodNameFragments(text);
        String bestFragment = "";
        for (String fragment : fragments) {
            if (fragment.length() > bestFragment.length()) {
                bestFragment = fragment;
            }
        }
        String compact = productNameKey(text);
        boolean repeatsFoodPhrase = false;
        for (String fragment : fragments) {
            String key = productNameKey(fragment);
            if (key.length() > 0 && countOccurrences(compact, key) > 1) {
                repeatsFoodPhrase = true;
                break;
            }
        }
        if ((repeatsFoodPhrase || hasSuspiciousNameArtifacts(text)) && bestFragment.length() > 0) {
            return bestFragment;
        }
        if (hasSuspiciousNameArtifacts(text)) {
            return "";
        }
        return text;
    }

    static boolean isHighConfidenceFoodProductName(String value) {
        String text = intelligentProductNameCandidate(value);
        return text.length() > 0
                && isLikelyFoodProductName(text)
                && !hasSuspiciousNameArtifacts(text);
    }

    private static boolean hasSuspiciousNameArtifacts(String value) {
        String text = FoodItem.cleanText(value);
        String compact = productNameKey(text);
        if (compact.length() == 0) {
            return true;
        }
        if (containsAny(compact, new String[] {
                "品类型", "产品类", "品类别", "配料简单", "蛋白爽滑", "蛋黄绵密",
                "净含", "过敏物质", "贮存条件", "保存条件"
        })) {
            return true;
        }

        int chinese = 0;
        int latin = 0;
        int longestLatinRun = 0;
        int latinRun = 0;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (isChinese(c)) {
                chinese++;
                latinRun = 0;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin++;
                latinRun++;
                longestLatinRun = Math.max(longestLatinRun, latinRun);
            } else {
                latinRun = 0;
            }
        }
        return chinese >= 2 && latin > 0 && longestLatinRun > 6;
    }

    private static int countOccurrences(String text, String token) {
        if (text.length() == 0 || token.length() == 0) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    static boolean productNamesSimilar(String first, String second) {
        return productNameSimilarity(first, second) >= 0.72d;
    }

    static double productNameSimilarity(String first, String second) {
        String left = productNameKey(first);
        String right = productNameKey(second);
        if (left.length() == 0 || right.length() == 0) {
            return 0d;
        }
        if (left.equals(right)) {
            return 1d;
        }
        int shorterLength = Math.min(left.length(), right.length());
        if (shorterLength >= 2 && (left.contains(right) || right.contains(left))) {
            return Math.max(0.82d, (double) shorterLength / Math.max(left.length(), right.length()));
        }

        int lcsLength = longestCommonSubsequenceLength(left, right);
        double lcsRatio = (double) lcsLength / shorterLength;
        double bigramRatio = bigramDiceCoefficient(left, right);
        if (lcsLength >= 3 && lcsRatio >= 0.72d) {
            return Math.max(lcsRatio, bigramRatio);
        }
        if (shorterLength >= 4 && bigramRatio >= 0.58d) {
            return Math.max(lcsRatio, bigramRatio);
        }
        return Math.max(lcsRatio * 0.8d, bigramRatio);
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

    static int productNameScore(String candidate) {
        String text = FoodItem.cleanText(candidate);
        if (text.length() < 2) {
            return 0;
        }
        if (isLikelyExplanatorySentence(text)) {
            return 0;
        }
        String compact = text.replace(" ", "").toLowerCase();
        if (compact.length() < 2) {
            return 0;
        }
        if ("tm".equals(compact) || "nrv".equals(compact)) {
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
        if (isPackagingMetadata(text)) {
            return 0;
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
        if (chinese == 0 && letters >= 4) {
            score += 16;
        }
        if (compact.length() >= 3 && compact.length() <= 14) {
            score += 12;
        }
        if (hasFoodProductToken(compact)) {
            score += 32;
        }
        if (isLikelyMarketingSlogan(compact)) {
            score -= 24;
        }
        return Math.max(1, score);
    }

    static boolean isLikelyFoodProductName(String value) {
        String text = cleanProductNameLine(value);
        if (productNameScore(text) <= 0) {
            return false;
        }
        String compact = productNameKey(text);
        if (compact.length() < 2 || compact.length() > 18) {
            return false;
        }
        if (isCanonicalFoodName(text)) {
            return true;
        }
        if (containsAny(compact, new String[] {
                "饮料", "牛奶", "酸奶", "乳饮", "果汁", "茶饮", "矿泉水", "苏打水",
                "饮用水", "汽水", "咖啡", "可乐", "面包", "饼干", "蛋糕", "糕点",
                "糖果", "巧克力", "酸菜", "榨菜", "罐头", "腐乳", "酱油", "食用油",
                "米饭", "米粉", "面条", "方便面", "炸酱面", "鹌鹑蛋", "鸡蛋", "肉丸",
                "火腿", "香肠", "零食", "坚果", "麦片", "果冻", "薯片", "豆制品"
        })) {
            return true;
        }
        if (compact.length() > 12) {
            return false;
        }
        for (String suffix : new String[] {
                "面", "饭", "粉", "蛋", "奶", "茶", "酱", "汁", "糕", "饼", "糖",
                "豆", "肉", "鱼", "虾", "菜", "油", "醋", "酒"
        }) {
            if (compact.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLikelyExplanatorySentence(String value) {
        String text = FoodItem.cleanText(value);
        if (NUMBERED_SENTENCE_PREFIX.matcher(text).find()) {
            return true;
        }

        String compact = productNameKey(text);
        if (compact.length() < 10) {
            return false;
        }
        if (containsAny(compact, new String[] {
                "用于", "适用于", "可用于", "有助于", "适应症", "用法用量",
                "本品为", "除去包装", "除去包", "性状"
        })) {
            return true;
        }

        int medicalTokenCount = 0;
        for (String token : new String[] {
                "症状", "病变", "疾病", "患者", "治疗", "诊断", "综合征",
                "视网膜", "糖尿病", "高血压", "血瘀证", "视物", "眼底",
                "出血", "舌质", "面色", "不良反应", "临床试验", "监测",
                "胃肠系统", "药品说明", "说明书"
        }) {
            if (compact.contains(token)) {
                medicalTokenCount++;
            }
        }
        return medicalTokenCount >= 2;
    }

    static boolean isLikelyMarketingSlogan(String value) {
        String compact = productNameKey(value);
        if (compact.length() < 5) {
            return false;
        }
        return containsAny(compact, new String[] {
                "配啥都好吃", "酸酸爽爽", "蛋白爽滑", "蛋黄绵密", "料简单",
                "每一口", "尽享美味", "匠心制作", "经典之选", "好吃", "好喝",
                "更香", "真香", "元气满满"
        });
    }

    private static boolean hasFoodProductToken(String compact) {
        return containsAny(compact, new String[] {
                "面", "饭", "粉", "蛋", "丸", "水", "茶", "饮", "奶", "乳", "酱",
                "汁", "糕", "饼", "糖", "豆", "肉", "鱼", "虾", "菜", "果", "油",
                "醋", "酒", "腐乳", "酸菜", "食品", "饮料", "罐头", "零食"
        });
    }

    static boolean isPackagingMetadata(String value) {
        String compact = productNameKey(value);
        if (compact.length() == 0) {
            return true;
        }
        String[] blockTokens = new String[] {
                "营养成分表", "营养成分", "营养表", "营养素参考值", "养分", "nrv", "能量", "蛋白质", "脂肪",
                "碳水化合物", "水化合物", "钠", "配料表", "配料", "食品添加剂", "过敏原", "致敏物",
                "执行标准", "产品标准", "标准号", "生产许可证", "食品生产许可证", "sc编号",
                "生产日期", "制造日期", "包装日期", "生产批号", "批次", "保质期", "有效期",
                "净含量", "净重", "规格", "贮存", "储存", "保存方法", "保存条件",
                "厂家地址", "厂址", "地址", "电话", "客服", "网址", "委托方", "委托商",
                "生产商", "制造商", "经销商", "委托生产", "受委托生产", "产地", "建议", "公司服务",
                "食用方法", "冲调方法", "使用方法", "请勿",
                "二维码", "条形码", "扫码", "公众号", "合格", "检验", "温馨提示"
        };
        for (String token : blockTokens) {
            if (compact.contains(token)) {
                return true;
            }
        }
        if (compact.matches(".*(?:gb|gbt|qb|q)[0-9]{3,}.*")) {
            return true;
        }
        return DateOcrParser.parse(value).hasAnyCandidate();
    }

    private static int longestCommonSubsequenceLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                if (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1)) {
                    current[rightIndex] = previous[rightIndex - 1] + 1;
                } else {
                    current[rightIndex] = Math.max(previous[rightIndex], current[rightIndex - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            java.util.Arrays.fill(current, 0);
        }
        return previous[right.length()];
    }

    private static double bigramDiceCoefficient(String left, String right) {
        if (left.length() < 2 || right.length() < 2) {
            return 0d;
        }
        java.util.List<String> rightBigrams = new java.util.ArrayList<String>();
        for (int index = 0; index < right.length() - 1; index++) {
            rightBigrams.add(right.substring(index, index + 2));
        }
        int matches = 0;
        for (int index = 0; index < left.length() - 1; index++) {
            String bigram = left.substring(index, index + 2);
            int matchIndex = rightBigrams.indexOf(bigram);
            if (matchIndex >= 0) {
                matches++;
                rightBigrams.remove(matchIndex);
            }
        }
        return (2d * matches) / ((left.length() - 1) + (right.length() - 1));
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
