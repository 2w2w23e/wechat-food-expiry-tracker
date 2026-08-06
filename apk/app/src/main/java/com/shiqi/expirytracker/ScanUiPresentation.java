package com.shiqi.expirytracker;

final class ScanUiPresentation {
    enum FieldState {
        SEARCHING,
        LOCATED,
        READY,
        ATTENTION
    }

    static final class State {
        final FieldState barcode;
        final FieldState productName;
        final FieldState date;
        final int recognizedCount;
        final int requiredCount;
        final boolean ready;
        final boolean hasReviewableResult;
        final String confirmationSubtitle;
        final String reviewAction;
        final String primaryScanAction;

        State(
                FieldState barcode,
                FieldState productName,
                FieldState date,
                int recognizedCount,
                int requiredCount,
                boolean ready,
                boolean hasReviewableResult,
                String confirmationSubtitle,
                String reviewAction,
                String primaryScanAction
        ) {
            this.barcode = barcode;
            this.productName = productName;
            this.date = date;
            this.recognizedCount = recognizedCount;
            this.requiredCount = requiredCount;
            this.ready = ready;
            this.hasReviewableResult = hasReviewableResult;
            this.confirmationSubtitle = confirmationSubtitle;
            this.reviewAction = reviewAction;
            this.primaryScanAction = primaryScanAction;
        }
    }

    private ScanUiPresentation() {
    }

    static State from(
            boolean dateOnlyMode,
            boolean stableBarcode,
            boolean stableProductName,
            boolean stableDate,
            boolean sawText,
            boolean sawDateCandidate,
            boolean dateConflict
    ) {
        FieldState barcode = stableBarcode ? FieldState.READY : FieldState.SEARCHING;
        FieldState productName = stableProductName
                ? FieldState.READY
                : sawText ? FieldState.LOCATED : FieldState.SEARCHING;
        FieldState date = stableDate
                ? dateConflict ? FieldState.ATTENTION : FieldState.READY
                : dateConflict ? FieldState.ATTENTION
                : sawDateCandidate || sawText ? FieldState.LOCATED
                : FieldState.SEARCHING;

        int recognizedCount = 0;
        if (stableBarcode) {
            recognizedCount++;
        }
        if (stableProductName) {
            recognizedCount++;
        }
        if (stableDate) {
            recognizedCount++;
        }

        int requiredCount = dateOnlyMode ? 1 : 2;
        boolean ready = dateOnlyMode
                ? stableDate
                : stableDate && (stableBarcode || stableProductName);
        boolean hasReviewableResult = recognizedCount > 0 || sawDateCandidate || sawText;

        String subtitle;
        if (ready) {
            subtitle = dateOnlyMode
                    ? "本批次日期已找到，请核对后使用"
                    : "必要信息已找到，请核对后填入";
        } else if (recognizedCount > 0) {
            subtitle = "已识别 " + recognizedCount + " 项，仍可继续补充";
        } else if (sawText || sawDateCandidate) {
            subtitle = "已看到文字，但还没有稳定字段";
        } else {
            subtitle = dateOnlyMode ? "请对准包装上的日期" : "请对准商品正面或日期";
        }

        String reviewAction = recognizedCount > 0
                ? "核对已识别的 " + recognizedCount + " 项"
                : "查看当前识别结果";
        String primaryScanAction = ready
                ? "核对识别结果"
                : stableDate
                ? "继续识别商品信息"
                : "优先识别日期";

        return new State(
                barcode,
                productName,
                date,
                recognizedCount,
                requiredCount,
                ready,
                hasReviewableResult,
                subtitle,
                reviewAction,
                primaryScanAction
        );
    }

    static String fieldStatus(FieldState state) {
        if (state == FieldState.READY) {
            return "已识别";
        }
        if (state == FieldState.ATTENTION) {
            return "请核对";
        }
        if (state == FieldState.LOCATED) {
            return "已看到文字";
        }
        return "查找中";
    }

    static String replayHeadline(boolean hasAnalyzedFrame) {
        return hasAnalyzedFrame ? "正在核对" : "正在找文字";
    }

    static String replayMessage(boolean hasAnalyzedFrame) {
        return hasAnalyzedFrame
                ? "已找到有用画面，正在核对中文商品名、条码和日期。"
                : "请保持包装文字在取景框内，系统会自动选择更清晰的画面。";
    }

    static String cameraMessage(String focusState, String frameState) {
        String focus = clean(focusState);
        String frame = clean(frameState);
        if (containsAny(frame, "太暗", "增加环境光")) {
            return "画面太暗，请增加环境光";
        }
        if (containsAny(frame, "过亮", "直射光")) {
            return "画面过亮，请避开直射光";
        }
        if (containsAny(frame, "反光")) {
            return "包装反光，请稍微倾斜手机";
        }
        if (containsAny(frame, "发糊", "连续模糊")) {
            return "文字发糊，正在重新对焦";
        }
        if (containsAny(frame, "低对比度")) {
            return "正在增强低对比度日期";
        }
        if (containsAny(frame, "日期候选已稳定")) {
            return "已找到日期，请核对结果";
        }
        if (containsAny(frame, "中文文字", "包装文字")) {
            return "已看到文字，正在读取";
        }
        if (containsAny(frame, "发现", "文字区", "更清晰画面", "有效画面")) {
            return "已找到有用文字，正在读取";
        }
        if (containsAny(frame, "暂未发现")) {
            return "缓慢移动，让文字进入框内";
        }
        if (containsAny(focus, "失败", "未锁定")) {
            return "焦点未稳定，请轻点文字";
        }
        if (containsAny(focus, "对焦中", "准备中")) {
            return "正在对焦，请保持稳定";
        }
        if (containsAny(frame, "等待第一帧", "等待相机画面")) {
            return "等待相机画面";
        }
        if (containsAny(frame, "实时画面", "检查清晰度", "比较画面")) {
            return "已看到画面，正在寻找清晰文字";
        }
        return frame.length() > 0 ? stripFrameCounters(frame) : "对准包装文字并保持稳定";
    }

    private static String stripFrameCounters(String value) {
        String result = value
                .replaceAll("\\s*[·|]\\s*已分析\\s*\\d+\\s*帧", "")
                .replaceAll("\\s*\\d+\\s*帧$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return result.length() == 0 ? "正在寻找清晰文字" : result;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
