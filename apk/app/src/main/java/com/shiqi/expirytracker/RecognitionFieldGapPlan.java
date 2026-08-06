package com.shiqi.expirytracker;

/**
 * Describes the smallest recognition pass that can close the fields still missing
 * from the current review session. The planner is deliberately independent from
 * Android and model implementations so its decisions can be regression tested.
 */
final class RecognitionFieldGapPlan {
    static final int FIELD_PRODUCT_NAME = 1;
    static final int FIELD_BARCODE = 1 << 1;
    static final int FIELD_DATES = 1 << 2;

    enum Target {
        AUTOMATIC,
        PRODUCT_NAME,
        BARCODE,
        DATES
    }

    final Target target;
    final int missingFields;
    final boolean scanProductName;
    final boolean scanBarcode;
    final boolean scanDates;
    final boolean runHeavyDetector;
    final int maxHeavyPasses;
    final int maxDateBandPasses;
    final String label;
    final String guidance;

    private RecognitionFieldGapPlan(
            Target target,
            int missingFields,
            boolean scanProductName,
            boolean scanBarcode,
            boolean scanDates,
            boolean runHeavyDetector,
            int maxHeavyPasses,
            int maxDateBandPasses,
            String label,
            String guidance
    ) {
        this.target = target == null ? Target.AUTOMATIC : target;
        this.missingFields = missingFields;
        this.scanProductName = scanProductName;
        this.scanBarcode = scanBarcode;
        this.scanDates = scanDates;
        this.runHeavyDetector = runHeavyDetector;
        this.maxHeavyPasses = Math.max(0, maxHeavyPasses);
        this.maxDateBandPasses = Math.max(0, maxDateBandPasses);
        this.label = label == null ? "自动识别" : label;
        this.guidance = guidance == null ? "把包装信息放入框内并保持稳定" : guidance;
    }

    static RecognitionFieldGapPlan evaluate(
            Target requestedTarget,
            boolean dateOnlyMode,
            boolean stableProductName,
            boolean stableBarcode,
            boolean stableDates,
            boolean dateConflict,
            int analyzedFrames
    ) {
        Target target = dateOnlyMode ? Target.DATES
                : requestedTarget == null ? Target.AUTOMATIC : requestedTarget;
        int missing = 0;
        if (!stableProductName) {
            missing |= FIELD_PRODUCT_NAME;
        }
        if (!stableBarcode) {
            missing |= FIELD_BARCODE;
        }
        if (!stableDates || dateConflict) {
            missing |= FIELD_DATES;
        }

        boolean scanName;
        boolean scanBarcode;
        boolean scanDates;
        int heavyPasses;
        int dateBandPasses;
        String label;
        String guidance;
        switch (target) {
            case PRODUCT_NAME:
                scanName = true;
                scanBarcode = false;
                scanDates = false;
                heavyPasses = 2;
                dateBandPasses = 0;
                label = "补扫商品名";
                guidance = "让包装正面较大的中文商品名占满框，保持稳定";
                break;
            case BARCODE:
                scanName = false;
                scanBarcode = true;
                scanDates = false;
                heavyPasses = 0;
                dateBandPasses = 0;
                label = "补扫条码";
                guidance = "让条码和下方数字完整进入框内，避免反光";
                break;
            case DATES:
                scanName = false;
                scanBarcode = false;
                scanDates = true;
                heavyPasses = 6;
                dateBandPasses = 6;
                label = "补扫日期";
                guidance = "让生产日期、保质期或有效期小字占满框，轻点后保持稳定";
                break;
            case AUTOMATIC:
            default:
                scanName = !stableProductName;
                scanBarcode = !stableBarcode;
                scanDates = !stableDates || dateConflict;
                // Automatic mode gets a short fast pass. Date close-up can opt into
                // the larger budget without making every camera frame expensive.
                heavyPasses = scanDates ? 3 : 0;
                dateBandPasses = scanDates ? 3 : 0;
                label = "自动识别";
                guidance = scanDates
                        ? "先保持包装稳定，系统正在找生产日期和有效期"
                        : "把包装正面放入框内，系统会合并中文、条码和日期";
                break;
        }

        boolean frameBudgetAllowsHeavy = analyzedFrames <= 0
                || analyzedFrames <= (target == Target.DATES ? 12 : 8);
        return new RecognitionFieldGapPlan(
                target,
                missing,
                scanName,
                scanBarcode,
                scanDates,
                heavyPasses > 0 && frameBudgetAllowsHeavy,
                heavyPasses,
                dateBandPasses,
                label,
                guidance
        );
    }

    boolean hasMissing(int field) {
        return (missingFields & field) != 0;
    }

    boolean allowsHeavyPass(int completedPasses) {
        return runHeavyDetector && completedPasses < maxHeavyPasses;
    }

    boolean allowsDateBandPass(int completedPasses) {
        return scanDates && completedPasses < maxDateBandPasses;
    }

    boolean isCompleteForTarget(
            boolean stableProductName,
            boolean stableBarcode,
            boolean stableDates
    ) {
        switch (target) {
            case PRODUCT_NAME:
                return stableProductName;
            case BARCODE:
                return stableBarcode;
            case DATES:
                return stableDates;
            case AUTOMATIC:
            default:
                return stableDates && (stableProductName || stableBarcode);
        }
    }
}
