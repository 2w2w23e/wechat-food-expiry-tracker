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
    private static final Pattern PACKAGED_DOSAGE_NAME = Pattern.compile(
            "([\\u4e00-\\u9fff]{2,12}(?:口服液|混悬液|喷雾剂|胶囊|颗粒|滴丸|丸剂|片剂|糖浆))"
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
            if (cleaned.length() == 0
                    || (!hasProductNameLabel(cleaned) && isAppUiNoiseLine(cleaned))) {
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
            if (containsAny(compact, new String[] {
                    "药品追溯码", "药品追濟码", "药品标识码", "序列号"
            })) {
                continue;
            }
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
        String packagedDosageName = extractPackagedDosageName(cleaned);
        if (packagedDosageName.length() > 0) {
            best = packagedDosageName;
            bestScore = productNameScore(packagedDosageName) + 220;
        }
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

    private static String extractPackagedDosageName(String rawText) {
        String text = FoodItem.cleanText(rawText);
        Matcher matcher = PACKAGED_DOSAGE_NAME.matcher(text);
        String best = "";
        int bestScore = 0;
        while (matcher.find()) {
            String candidate = cleanProductNameLine(matcher.group(1));
            String compact = productNameKey(candidate);
            if (compact.length() < 4
                    || compact.length() > 14
                    || containsAny(compact, new String[] {
                    "本品", "除去", "包装", "颜色", "色的", "薄衣", "糖衣", "用法",
                    "服用", "口服", "一次", "一日", "每日", "每丸", "每片", "说明"
            })) {
                continue;
            }
            int score = productNameScore(candidate) + candidate.length() * 3;
            if (compact.endsWith("滴丸")
                    || compact.endsWith("胶囊")
                    || compact.endsWith("口服液")) {
                score += 28;
            }
            if (compact.startsWith("复方")) {
                score += 20;
            }
            if (countOccurrences(productNameKey(text), compact) >= 2) {
                score += 36;
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
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
                "识別结果",
                "填入新增表单",
                "填入表单",
                "直接填入",
                "采用所选结果",
                "商品码",
                "商品名",
                "商品名可信度",
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
                "继续识别",
                "正在寻找",
                "候选已可用",
                "可先确认",
                "结果较稳",
                "关键帧",
                "可信度",
                "候选",
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
        text = text.replaceFirst(
                "^(?:(?:产\\s*品|生\\s*产)\\s*)?批\\s*(?:号|次)\\s*[:：]?\\s*",
                ""
        ).trim();
        text = cutAtFirst(text, new String[] {
                "(", "（", "约", "配料", "净含量", "净重", "规格", "营养", "生产日期", "制造日期",
                "包装日期", "保质期", "有效期", "执行标准", "产品标准", "标准号", "厂家", "厂址", "地址",
                "生产批号", "产品批号", "批次", "批号", "食用方法", "冲调方法", "扫码", "二维码",
                "条形码", "产品名称", "产品类型", "产品类别"
        });
        text = text.replaceFirst(
                "(?i)\\s*\\d+(?:\\.\\d+)?\\s*(?:mg|kg|g|ml|l|克|千克|公斤|斤|毫克|毫升|升|袋|包|盒|瓶|罐|支|枚|个)"
                        + "(?:\\s*[x×*]\\s*\\d+)?\\s*$",
                ""
        ).trim();
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
                "柠檬芭乐气泡果汁饮料", "气泡果汁饮料",
                "饮用天然矿泉水", "天然矿泉水", "饮用纯净水", "饮用天然水", "饮用净水",
                "维生素饮料", "去壳清水鹌鹑蛋", "清水鹌鹑蛋", "老北京炸酱面",
                "纯牛奶", "维C水", "果汁饮料", "茶饮料", "炸酱面", "鹌鹑蛋",
                "矿泉水", "纯净水", "天然水", "饮用水", "苏打水", "喝开水",
                "酸奶", "牛奶", "面包", "饼干", "薯条", "腐乳", "酸菜", "果汁"
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
                .replace("酸酸爽區酸菜", "酸菜")
                .replace("酸酸爽区酸菜", "酸菜")
                .replace("酸萊酸菜", "酸菜")
                .replace("酸莱酸菜", "酸菜")
                .replace("酸萊", "酸菜")
                .replace("寶", "宝")
                .replace("純", "纯")
                .replace("飲", "饮")
                .replace("淨", "净")
                .replace("礦", "矿")
                .replace("売", "壳")
                .replace("鹤鹑", "鹌鹑")
                .replace("鸣鹑", "鹌鹑")
                .replace("鳴鹑", "鹌鹑")
                .replace("朝鹑", "鹌鹑")
                .replace("大重老北京", "大董老北京")
                .replace("大疆老北京", "大董老北京")
                .replace("炸著面", "炸酱面")
                .replace("去酒鹌鹑蛋", "去壳清水鹌鹑蛋")
                .replace("粿汁", "果汁")
                .replace("課汁", "果汁")
                .replace("裸汁", "果汁")
                .replace("茶饮E", "茶饮料")
                .replace("票汁", "果汁")
                .replace("系料", "饮料")
                .replace("個开水", "喝开水")
                .replace("倜开水", "喝开水")
                .replace("遇开水", "喝开水")
                .replace("渴开水", "喝开水")
                .replace("著条", "薯条")
                .replace("暑条", "薯条")
                .replace("维Ｃ水", "维C水")
                .replace("维c水", "维C水")
                .replace("维℃水", "维C水")
                .replace("牛如", "牛奶");
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
        if (text.startsWith("名") && !fragments.isEmpty()) {
            int firstFoodIndex = Integer.MAX_VALUE;
            for (String fragment : fragments) {
                int index = text.indexOf(fragment);
                if (index >= 0) {
                    firstFoodIndex = Math.min(firstFoodIndex, index);
                }
            }
            if (firstFoodIndex >= 3 && firstFoodIndex <= 9) {
                text = FoodItem.cleanText(text.substring(1));
                fragments = extractFoodNameFragments(text);
            }
        }
        String bestFragment = "";
        for (String fragment : fragments) {
            if (fragment.length() > bestFragment.length()) {
                bestFragment = fragment;
            }
        }
        if (bestFragment.length() > 0 && isLikelyQualityClaim(text)) {
            return bestFragment;
        }
        String leadingFragment = "";
        int leadingFragmentIndex = Integer.MAX_VALUE;
        for (String fragment : fragments) {
            int index = text.indexOf(fragment);
            if (index >= 2 && index <= 8
                    && (index < leadingFragmentIndex
                    || (index == leadingFragmentIndex && fragment.length() > leadingFragment.length()))) {
                leadingFragment = fragment;
                leadingFragmentIndex = index;
            }
        }
        if (leadingFragment.length() > 0) {
            int fragmentIndex = leadingFragmentIndex;
            if (fragmentIndex >= 2 && fragmentIndex <= 8) {
                String prefix = FoodItem.cleanText(text.substring(0, fragmentIndex));
                String prefixKey = productNameKey(prefix);
                if (prefix.matches(".*[A-Za-z].*")
                        && prefix.matches(".*[\\u4e00-\\u9fff].*")) {
                    return leadingFragment;
                }
                String combined = prefix + leadingFragment;
                if (prefixKey.length() >= 2
                        && prefixKey.length() <= 8
                        && combined.length() <= 18
                        && !containsAny(prefixKey, new String[] {
                        "低糖", "无糖", "零糖", "少糖", "低脂", "无脂", "原味", "新品", "口味",
                        "发酵", "减盐", "低盐", "清净", "工艺", "传统", "经典",
                        "吹用", "文用", "义用"
                })) {
                    return combined;
                }
                if (containsAny(prefixKey, new String[] {
                        "吹用", "文用", "义用", "发酵", "低糖", "无糖", "零糖",
                        "少糖", "低脂", "无脂", "原味", "新品", "传统", "经典"
                })) {
                    return leadingFragment;
                }
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
                && !isAppUiNoiseLine(text)
                && isLikelyFoodProductName(text)
                && !isAttributeOnlyProductName(text)
                && !hasSuspiciousNameArtifacts(text);
    }

    static boolean isLikelyStandaloneBrand(String value) {
        String text = cleanProductNameLine(value);
        String compact = productNameKey(text);
        if (compact.length() < 2 || compact.length() > 10
                || text.contains(":")
                || isAppUiNoiseLine(text)
                || isPackagingMetadata(text)
                || isLikelyMarketingSlogan(text)
                || containsAny(compact, new String[] {
                "经典", "原味", "传统", "风味", "低糖", "无糖", "零糖", "新品", "口味",
                "每日", "营养", "健康", "满足"
        })
                || isAttributeOnlyProductName(text)) {
            return false;
        }
        if (compact.matches("[a-z0-9]+") && compact.length() < 4) {
            return false;
        }
        int readable = 0;
        for (int index = 0; index < compact.length(); index++) {
            char c = compact.charAt(index);
            if (isChinese(c) || (c >= 'a' && c <= 'z')) {
                readable++;
            }
        }
        return readable >= 2;
    }

    private static boolean isAttributeOnlyProductName(String value) {
        String compact = productNameKey(value);
        if (compact.length() == 0) {
            return true;
        }
        String stripped = compact
                .replace("低糖", "")
                .replace("无糖", "")
                .replace("零糖", "")
                .replace("0糖", "")
                .replace("少糖", "")
                .replace("低脂", "")
                .replace("无脂", "")
                .replace("原味", "")
                .replace("新品", "");
        if (stripped.length() == 0) {
            return true;
        }
        return stripped.matches("[a-z0-9]{1,3}")
                && containsAny(compact, new String[] {
                "低糖", "无糖", "零糖", "0糖", "少糖", "低脂", "无脂", "原味", "新品"
        });
    }

    static boolean isHighConfidenceLabeledProductName(String value) {
        String text = intelligentProductNameCandidate(value);
        String compact = productNameKey(text);
        return productNameScore(text) > 0
                && compact.length() >= 2
                && compact.length() <= 18
                && !text.contains(":")
                && !hasSuspiciousNameArtifacts(text)
                && !isLikelyMarketingSlogan(text);
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

    static String preferredRecognizedProductName(String packagingName, String lookupName) {
        String packaging = intelligentProductNameCandidate(packagingName);
        String lookup = intelligentProductNameCandidate(lookupName);
        if (isHighConfidenceFoodProductName(lookup)
                && isLookupMoreSpecificThanPackaging(packaging, lookup)) {
            return lookup;
        }
        if (isHighConfidenceFoodProductName(packaging)) {
            return packaging;
        }
        return lookup;
    }

    private static boolean isLookupMoreSpecificThanPackaging(String packaging, String lookup) {
        String packagingKey = productNameKey(packaging);
        String lookupKey = productNameKey(lookup);
        if (packagingKey.length() < 2
                || packagingKey.length() > 5
                || lookupKey.length() < packagingKey.length() + 2) {
            return false;
        }
        if (lookupKey.contains(packagingKey)) {
            return true;
        }
        String packagingCategory = foodCategoryKey(packaging);
        String lookupCategory = foodCategoryKey(lookup);
        if (packagingCategory.length() > 0 && packagingCategory.equals(lookupCategory)) {
            return true;
        }
        return isWaterCategory(packagingCategory) && isWaterCategory(lookupCategory);
    }

    private static boolean isWaterCategory(String category) {
        return "矿泉水".equals(category)
                || "纯净水".equals(category)
                || "天然水".equals(category)
                || "维c水".equals(category)
                || "苏打水".equals(category)
                || "喝开水".equals(category)
                || "饮用水".equals(category);
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
        String leftCategory = foodCategoryKey(first);
        String rightCategory = foodCategoryKey(second);
        if (leftCategory.length() > 0
                && rightCategory.length() > 0
                && !leftCategory.equals(rightCategory)) {
            return 0d;
        }
        if (left.length() == right.length()
                && left.length() >= 3
                && productNameEditDistance(left, right) == 1) {
            return 0.80d;
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

    static String foodCategoryKey(String value) {
        String compact = productNameKey(normalizeCommonPackagingOcr(value));
        if (compact.contains("矿泉水")) return "矿泉水";
        if (compact.contains("纯净水") || compact.contains("饮用净水")) return "纯净水";
        if (compact.contains("天然水")) return "天然水";
        if (compact.contains("维c水")) return "维c水";
        if (compact.contains("苏打水")) return "苏打水";
        if (compact.contains("喝开水")) return "喝开水";
        if (compact.contains("饮用水")) return "饮用水";
        if (compact.contains("酸奶")) return "酸奶";
        if (compact.contains("牛奶") || compact.endsWith("奶")) return "牛奶";
        if (compact.contains("炸酱面")) return "炸酱面";
        if (compact.contains("鹌鹑蛋")) return "鹌鹑蛋";
        if (compact.contains("果汁")) return "果汁";
        if (compact.contains("酸菜")) return "酸菜";
        if (compact.contains("腐乳")) return "腐乳";
        return "";
    }

    static int productNameEditDistance(String first, String second) {
        String left = productNameKey(first);
        String right = productNameKey(second);
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitution = previous[rightIndex - 1]
                        + (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1);
                current[rightIndex] = Math.min(
                        Math.min(previous[rightIndex] + 1, current[rightIndex - 1] + 1),
                        substitution
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    static String productNameKey(String value) {
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
        if (isAppUiNoiseLine(text)) {
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
        if (PACKAGED_DOSAGE_NAME.matcher(compact).matches()) {
            return true;
        }
        if (containsAny(compact, new String[] {
                "饮料", "牛奶", "酸奶", "乳饮", "果汁", "茶饮", "矿泉水", "苏打水",
                "维c水", "饮用水", "汽水", "咖啡", "可乐", "面包", "饼干", "蛋糕", "糕点",
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

    static boolean isNonProductTraceabilityCode(String rawText, String barcode) {
        String code = BarcodeUtils.digitsOnly(barcode);
        if (!BarcodeUtils.isSupportedProductCode(code)) {
            return false;
        }
        String compact = FoodItem.cleanText(rawText)
                .replaceAll("\\s+", "")
                .replace("-", "")
                .replace("—", "")
                .toLowerCase();
        int codeIndex = compact.indexOf(code);
        if (codeIndex < 0) {
            return false;
        }
        for (String token : new String[] {
                "药品追溯码", "药品追濟码", "药品标识码", "药品標識碼", "序列号", "序列號"
        }) {
            int tokenIndex = compact.indexOf(token);
            if (tokenIndex >= 0 && Math.abs(codeIndex - tokenIndex) <= 120) {
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
        if (compact.length() < 2) {
            return false;
        }
        if (compact.contains("品质") && !hasFoodProductToken(compact)) {
            return true;
        }
        return containsAny(compact, new String[] {
                "配啥都好吃", "酸酸爽爽", "蛋白爽滑", "蛋黄绵密", "料简单",
                "每一口", "尽享美味", "匠心制作", "经典之选", "好吃", "好喝",
                "更香", "真香", "元气满满", "航天品质", "浓纯营"
        });
    }

    private static boolean isLikelyQualityClaim(String value) {
        String compact = productNameKey(value);
        return compact.contains("航天品质")
                || compact.contains("浓纯营")
                || (compact.contains("品质") && !compact.startsWith("品质"));
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
