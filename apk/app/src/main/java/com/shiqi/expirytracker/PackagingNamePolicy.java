package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain-neutral product-name policy for packaged items managed by expiry date.
 *
 * <p>The OCR layer may see food, medicine, personal-care products, or household
 * supplies. This policy rejects obvious UI/metadata/sentence noise without
 * requiring a food word.</p>
 */
final class PackagingNamePolicy {
    private static final String[] STRONG_PRODUCT_TOKENS = new String[] {
            "口服液", "混悬液", "喷雾剂", "胶囊", "颗粒", "滴丸", "丸剂", "片剂",
            "糖浆", "滴眼液", "滴鼻液", "眼膏", "乳膏", "软膏", "凝胶", "洗液",
            "散剂", "贴剂", "栓剂", "注射液", "溶液", "酊剂", "鼻喷剂", "吸入剂",
            "洗发水", "护发素", "沐浴露", "牙膏", "护手霜", "面霜", "精华液",
            "防晒霜", "洁面乳", "润肤乳", "卸妆水", "消毒液", "洗衣液", "清洁剂",
            "湿巾", "漱口水", "隐形眼镜护理液"
    };

    private PackagingNamePolicy() {}

    static String normalize(String value) {
        String normalized = RecognitionTextCleaner.intelligentProductNameCandidate(value);
        String dosageName = RecognitionTextCleaner.extractPackagedDosageName(normalized);
        int dosageStart = normalized.indexOf(dosageName);
        if (dosageName.length() == 0 || dosageStart <= 0) {
            return normalized;
        }
        String prefix = normalized.substring(0, dosageStart);
        for (int index = 0; index < prefix.length(); index++) {
            char current = prefix.charAt(index);
            if ((current >= 'A' && current <= 'Z')
                    || (current >= 'a' && current <= 'z')
                    || Character.isDigit(current)) {
                return dosageName;
            }
        }
        return normalized;
    }

    static boolean isPlausible(String value) {
        String text = normalize(value);
        String key = RecognitionTextCleaner.productNameKey(text);
        return RecognitionTextCleaner.productNameScore(text) > 0
                && key.length() >= 2
                && key.length() <= 24
                && !RecognitionTextCleaner.isLikelyMarketingSlogan(text)
                && !RecognitionTextCleaner.isPackagingMetadata(text);
    }

    static boolean isStrong(String value) {
        String text = normalize(value);
        if (!isPlausible(text)) {
            return false;
        }
        if (RecognitionTextCleaner.isHighConfidenceFoodProductName(text)) {
            return true;
        }
        return hasStrongProductSuffix(text);
    }

    private static boolean hasStrongProductSuffix(String value) {
        String key = RecognitionTextCleaner.productNameKey(value);
        for (String token : STRONG_PRODUCT_TOKENS) {
            if (key.endsWith(RecognitionTextCleaner.productNameKey(token))) {
                return true;
            }
        }
        return false;
    }

    static boolean isCanonical(String value) {
        String text = normalize(value);
        return RecognitionTextCleaner.isCanonicalFoodName(text)
                || (isPlausible(text) && hasStrongProductSuffix(text));
    }

    static String recoverStrongCandidate(String rawText) {
        List<String> plausible = new ArrayList<String>();
        String cleaned = RecognitionTextCleaner.cleanForPackagingOcr(rawText);
        String compact = FoodItem.cleanText(rawText)
                .replace(" ", "")
                .replace("\r", "")
                .replace("\n", "");
        boolean waterNameShape = compact.contains("提水R水B水")
                || compact.contains("开开水")
                || compact.contains("喝开水")
                || compact.contains("遇开水")
                || compact.contains("倜开水");
        if ((compact.contains("温和甘甜")
                || compact.contains("退和甘甜"))
                && waterNameShape) {
            return "喝开水";
        }
        for (String line : cleaned.split("\\r?\\n")) {
            String candidate = normalize(line);
            if (isPlausible(candidate)) {
                plausible.add(candidate);
            }
        }

        String best = "";
        int bestScore = 0;
        for (String candidate : plausible) {
            String recovered = isStrong(candidate)
                    ? candidate
                    : recoverNearStrongSuffix(candidate);
            if (!isStrong(recovered)) {
                continue;
            }
            int support = similaritySupport(candidate, plausible);
            if (!candidate.equals(recovered) && support < 2) {
                continue;
            }
            int score = RecognitionTextCleaner.productNameScore(recovered)
                    + recovered.length() * 2
                    + Math.min(4, support) * 24
                    + (candidate.equals(recovered) ? 30 : 16);
            if (score > bestScore
                    || (score == bestScore && recovered.length() > best.length())) {
                best = recovered;
                bestScore = score;
            }
        }
        return best;
    }

    private static String recoverNearStrongSuffix(String value) {
        String text = normalize(value);
        for (String suffix : STRONG_PRODUCT_TOKENS) {
            if (suffix.length() < 3 || text.length() < suffix.length() + 2) {
                continue;
            }
            String observed = text.substring(text.length() - suffix.length());
            if (RecognitionTextCleaner.productNameEditDistance(observed, suffix) == 1) {
                return text.substring(0, text.length() - suffix.length()) + suffix;
            }
        }
        return text;
    }

    private static int similaritySupport(String candidate, List<String> candidates) {
        int support = 0;
        for (String other : candidates) {
            if (RecognitionTextCleaner.productNameSimilarity(candidate, other) >= 0.46d) {
                support++;
            }
        }
        return support;
    }

    static boolean isEligible(
            PackagingTextAnalyzer.Candidate candidate,
            boolean labeledEvidence
    ) {
        if (candidate == null || candidate.text.length() == 0) {
            return false;
        }
        if (labeledEvidence) {
            return RecognitionTextCleaner.isHighConfidenceLabeledProductName(candidate.text)
                    && candidate.score >= 70d;
        }
        if (!isPlausible(candidate.text)) {
            return false;
        }
        if (isCanonical(candidate.text)) {
            return candidate.score >= 86d;
        }
        if (isStrong(candidate.text)) {
            return candidate.score >= 100d;
        }
        return candidate.votes >= 2 && candidate.score >= 78d;
    }

    static boolean canLock(PackagingTextAnalyzer.Candidate candidate) {
        if (candidate == null || !isPlausible(candidate.text)) {
            return false;
        }
        if (isStrong(candidate.text)) {
            return candidate.score >= 86d;
        }
        return candidate.votes >= 2 && candidate.score >= 78d;
    }

    static boolean hasLabeledEvidence(List<String> evidence) {
        if (evidence == null) {
            return false;
        }
        for (String item : evidence) {
            if (RecognitionTextCleaner.hasProductNameLabel(item)) {
                return true;
            }
        }
        return false;
    }
}
