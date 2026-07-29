package com.shiqi.expirytracker;

/**
 * Bounds the amount of expensive line recognition performed for one frame.
 *
 * <p>Detection may expose every useful text box to the overlay, while this plan
 * keeps recognition work finite. Enhancement polarity rotates across temporal
 * passes instead of multiplying every ROI inside one pass.</p>
 */
final class RecognitionRoiBudget {
    static final int REALTIME_BATCH_SIZE = 1;
    static final int STILL_BATCH_SIZE = 2;

    enum Enhancement {
        LASER,
        EMBOSSED,
        BIDIRECTIONAL,
        BINARY,
        INVERTED
    }

    private RecognitionRoiBudget() {}

    static Plan forCameraPass(
            int passIndex,
            boolean dateOnlyMode,
            boolean lowContrastRecovery,
            int detectedRegionCount
    ) {
        int pass = Math.max(0, passIndex);
        int available = Math.max(0, detectedRegionCount);
        int baseLimit = Math.min(dateOnlyMode ? 12 : 8, available);
        int extraTightLimit = pass <= 0
                ? 0
                : Math.min(dateOnlyMode ? 4 : 3, Math.max(0, available - baseLimit));
        int enhancementLimit = Math.min(
                dateOnlyMode ? 4 : 3,
                baseLimit
        );
        Enhancement enhancement = lowContrastRecovery
                ? Enhancement.BIDIRECTIONAL
                : enhancementForPass(pass);
        int maxRecognitionCrops = dateOnlyMode ? 20 : 14;
        return new Plan(
                baseLimit,
                extraTightLimit,
                enhancementLimit,
                maxRecognitionCrops,
                REALTIME_BATCH_SIZE,
                enhancement
        );
    }

    static Plan forStillPass(int detectedRegionCount) {
        int baseLimit = Math.min(24, Math.max(0, detectedRegionCount));
        return new Plan(
                baseLimit,
                0,
                0,
                baseLimit,
                STILL_BATCH_SIZE,
                Enhancement.LASER
        );
    }

    static boolean shouldReuseDetectorProbe(
            boolean cameraLive,
            int cameraPass,
            int detectedRegionCount
    ) {
        return detectedRegionCount > 0 && (!cameraLive || cameraPass <= 0);
    }

    static boolean shouldStopStillDateVariants(
            boolean dedicatedDateStrip,
            boolean hasProductionDate,
            boolean hasDirectExpiryDate,
            boolean hasCalculatedExpiryDate
    ) {
        return dedicatedDateStrip
                && hasProductionDate
                && (hasDirectExpiryDate || hasCalculatedExpiryDate);
    }

    static int batchCount(int cropCount, int batchSize) {
        int safeCrops = Math.max(0, cropCount);
        int safeBatch = Math.max(1, batchSize);
        return (safeCrops + safeBatch - 1) / safeBatch;
    }

    private static Enhancement enhancementForPass(int pass) {
        switch (pass % 4) {
            case 1:
                return Enhancement.EMBOSSED;
            case 2:
                return Enhancement.BINARY;
            case 3:
                return Enhancement.INVERTED;
            default:
                return Enhancement.LASER;
        }
    }

    static final class Plan {
        final int baseRegionLimit;
        final int extraTightRegionLimit;
        final int enhancementLimit;
        final int maxRecognitionCrops;
        final int batchSize;
        final Enhancement enhancement;

        Plan(
                int baseRegionLimit,
                int extraTightRegionLimit,
                int enhancementLimit,
                int maxRecognitionCrops,
                int batchSize,
                Enhancement enhancement
        ) {
            this.baseRegionLimit = Math.max(0, baseRegionLimit);
            this.extraTightRegionLimit = Math.max(0, extraTightRegionLimit);
            this.enhancementLimit = Math.max(0, enhancementLimit);
            this.maxRecognitionCrops = Math.max(0, maxRecognitionCrops);
            this.batchSize = Math.max(1, batchSize);
            this.enhancement = enhancement == null ? Enhancement.LASER : enhancement;
        }

        int plannedCropCount() {
            return Math.min(
                    maxRecognitionCrops,
                    baseRegionLimit + extraTightRegionLimit + enhancementLimit
            );
        }

        int plannedBatchCount() {
            return batchCount(plannedCropCount(), batchSize);
        }
    }
}
