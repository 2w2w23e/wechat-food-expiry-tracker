package com.shiqi.expirytracker;

final class CameraOcrFeedback {
    private CameraOcrFeedback() {}

    static String qualityLabel(
            double sharpness,
            double brightness,
            double contrast,
            double glareRatio
    ) {
        if (glareRatio >= 0.18d) {
            return "反光较强，请稍微转动包装";
        }
        if (brightness <= 0.16d) {
            return "画面偏暗，请增加光线";
        }
        if (brightness >= 0.88d) {
            return "画面过亮，请避开直射光";
        }
        if (sharpness < 0.13d) {
            return "画面偏糊，请稍后退并轻点日期";
        }
        if (contrast < 0.20d) {
            return "低对比日期，正在增强笔画";
        }
        if (sharpness < 0.24d) {
            return "清晰度一般，请保持稳定";
        }
        return "画面清晰";
    }

    static boolean shouldRunLowContrastRecovery(
            int analyzedFrames,
            int completedRecoveryPasses,
            double sharpness,
            double brightness,
            double contrast,
            double glareRatio,
            boolean hasCompleteDate
    ) {
        if (hasCompleteDate || analyzedFrames < 2 || completedRecoveryPasses >= 3) {
            return false;
        }
        if (sharpness < 0.12d || brightness < 0.10d || brightness > 0.94d
                || glareRatio > 0.32d) {
            return false;
        }
        return contrast <= 0.28d && analyzedFrames >= 2 + completedRecoveryPasses;
    }

    static boolean shouldPublishFrameStatus(long nowMillis, long lastPublishedMillis) {
        return lastPublishedMillis <= 0L
                || nowMillis < lastPublishedMillis
                || nowMillis - lastPublishedMillis >= 180L;
    }

    static boolean isStrongCenteredDateRead(String text) {
        String digits = dateSignature(text);
        if (digits.length() >= 8 && digits.length() <= 18) {
            return true;
        }
        if (digits.length() != 6) {
            return false;
        }
        int month = Integer.parseInt(digits.substring(2, 4));
        int day = Integer.parseInt(digits.substring(4, 6));
        return month >= 1 && month <= 12 && day >= 1 && day <= 31;
    }

    static String dateSignature(String text) {
        String value = text == null ? "" : text;
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                digits.append(value.charAt(index));
            }
        }
        return digits.toString();
    }
}
