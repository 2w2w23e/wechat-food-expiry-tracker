package com.shiqi.expirytracker;

/**
 * Source-neutral budget decisions for expensive text detection.
 *
 * Camera, image, and video keep their own acquisition mechanics, but all three
 * sources use the same quality gates and completion rules before invoking the
 * detector. This class intentionally has no Android dependency so its behavior
 * is covered by the local JVM test suite.
 */
final class RecognitionPipelinePolicy {
    static final int SOURCE_CAMERA = 1;
    static final int SOURCE_IMAGE = 2;
    static final int SOURCE_VIDEO = 3;

    static final int REASON_RUN = 1;
    static final int REASON_MODEL_UNAVAILABLE = 2;
    static final int REASON_BARCODE_ONLY = 3;
    static final int REASON_COMPLETE = 4;
    static final int REASON_NOT_DETAIL_FRAME = 5;
    static final int REASON_BUDGET_EXHAUSTED = 6;
    static final int REASON_QUALITY = 7;

    static Decision decide(Input input) {
        if (input == null || !input.modelReady) {
            return Decision.skip(REASON_MODEL_UNAVAILABLE);
        }
        if (input.barcodeLookupOnly) {
            return Decision.skip(REASON_BARCODE_ONLY);
        }
        if (input.singleFrame || input.source == SOURCE_IMAGE) {
            return Decision.run(1280, 64, 24);
        }
        if (input.hasCompleteDateCandidate) {
            return Decision.skip(REASON_COMPLETE);
        }

        if (input.source == SOURCE_CAMERA) {
            int maxPasses = input.dateOnlyMode ? 7 : 5;
            if (input.completedDetectorPasses >= maxPasses) {
                return Decision.skip(REASON_BUDGET_EXHAUSTED);
            }
            boolean run = RecognitionFrameSelector.shouldRunHeavyCameraOcr(
                    input.analyzedFrames,
                    input.completedDetectorPasses,
                    input.visualScore,
                    input.sharpness,
                    input.glareRatio,
                    false,
                    input.dateOnlyMode
            );
            if (!run && input.screenRecording
                    && input.analyzedFrames >= 2 + input.completedDetectorPasses
                    && input.visualScore >= 0.30d
                    && input.sharpness >= 0.12d
                    && input.glareRatio <= 0.32d) {
                run = true;
            }
            if (!run) {
                return Decision.skip(REASON_QUALITY);
            }
            return Decision.run(
                    960,
                    liveDisplayRegionLimit(input.dateOnlyMode),
                    input.dateOnlyMode ? 12 : 8
            );
        }

        if (input.source == SOURCE_VIDEO) {
            if (!input.detailFrame) {
                return Decision.skip(REASON_NOT_DETAIL_FRAME);
            }
            int maxPasses = input.dateOnlyMode ? 5 : 4;
            if (input.completedDetectorPasses >= maxPasses) {
                return Decision.skip(REASON_BUDGET_EXHAUSTED);
            }
            boolean acceptable = input.visualScore >= 0.34d
                    && input.sharpness >= 0.12d
                    && input.glareRatio <= 0.32d;
            boolean firstUsefulFrame = input.completedDetectorPasses == 0
                    && input.visualScore >= 0.27d
                    && input.glareRatio <= 0.38d;
            if (!acceptable && !firstUsefulFrame) {
                return Decision.skip(REASON_QUALITY);
            }
            return Decision.run(960, 64, input.dateOnlyMode ? 14 : 10);
        }

        return Decision.skip(REASON_QUALITY);
    }

    static int mlKitTimeoutSeconds(int source) {
        return source == SOURCE_IMAGE ? 6 : 2;
    }

    static int liveDisplayRegionLimit(boolean dateOnlyMode) {
        return dateOnlyMode ? 40 : 48;
    }

    static final class Input {
        final int source;
        final boolean modelReady;
        final boolean barcodeLookupOnly;
        final boolean singleFrame;
        final boolean detailFrame;
        final boolean screenRecording;
        final boolean hasCompleteDateCandidate;
        final boolean dateOnlyMode;
        final int analyzedFrames;
        final int completedDetectorPasses;
        final double visualScore;
        final double sharpness;
        final double glareRatio;

        Input(
                int source,
                boolean modelReady,
                boolean barcodeLookupOnly,
                boolean singleFrame,
                boolean detailFrame,
                boolean screenRecording,
                boolean hasCompleteDateCandidate,
                boolean dateOnlyMode,
                int analyzedFrames,
                int completedDetectorPasses,
                double visualScore,
                double sharpness,
                double glareRatio
        ) {
            this.source = source;
            this.modelReady = modelReady;
            this.barcodeLookupOnly = barcodeLookupOnly;
            this.singleFrame = singleFrame;
            this.detailFrame = detailFrame;
            this.screenRecording = screenRecording;
            this.hasCompleteDateCandidate = hasCompleteDateCandidate;
            this.dateOnlyMode = dateOnlyMode;
            this.analyzedFrames = Math.max(0, analyzedFrames);
            this.completedDetectorPasses = Math.max(0, completedDetectorPasses);
            this.visualScore = clamp01(visualScore);
            this.sharpness = Math.max(0d, sharpness);
            this.glareRatio = clamp01(glareRatio);
        }
    }

    static final class Decision {
        final boolean runTextDetector;
        final int reason;
        final int detectorInputSide;
        final int displayRegionLimit;
        final int recognitionRegionLimit;

        private Decision(
                boolean runTextDetector,
                int reason,
                int detectorInputSide,
                int displayRegionLimit,
                int recognitionRegionLimit
        ) {
            this.runTextDetector = runTextDetector;
            this.reason = reason;
            this.detectorInputSide = detectorInputSide;
            this.displayRegionLimit = displayRegionLimit;
            this.recognitionRegionLimit = recognitionRegionLimit;
        }

        static Decision run(int inputSide, int displayLimit, int recognitionLimit) {
            return new Decision(true, REASON_RUN, inputSide, displayLimit, recognitionLimit);
        }

        static Decision skip(int reason) {
            return new Decision(false, reason, 0, 0, 0);
        }
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private RecognitionPipelinePolicy() {
    }
}
