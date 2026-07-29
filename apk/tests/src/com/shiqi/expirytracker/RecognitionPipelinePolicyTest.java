package com.shiqi.expirytracker;

public final class RecognitionPipelinePolicyTest {
    private int passed;

    public static void main(String[] args) {
        new RecognitionPipelinePolicyTest().run();
    }

    private void run() {
        stillImageUsesCompleteBudget();
        cameraQualityControlsHeavyOcr();
        videoUsesQualityAndFixedBudget();
        completedAndBarcodeOnlySessionsStop();
        System.out.println("Recognition pipeline policy tests: " + passed + " passed, 0 failed.");
    }

    private void stillImageUsesCompleteBudget() {
        RecognitionPipelinePolicy.Decision decision = RecognitionPipelinePolicy.decide(input(
                RecognitionPipelinePolicy.SOURCE_IMAGE,
                true,
                false,
                true,
                true,
                false,
                false,
                false,
                1,
                0,
                0.10d,
                0.05d,
                0.40d
        ));
        assertTrue(decision.runTextDetector, "a selected still image must run detection");
        assertEquals(1280, decision.detectorInputSide);
        assertEquals(64, decision.displayRegionLimit);
        assertEquals(24, decision.recognitionRegionLimit);
        passed++;
    }

    private void cameraQualityControlsHeavyOcr() {
        RecognitionPipelinePolicy.Decision blurry = RecognitionPipelinePolicy.decide(input(
                RecognitionPipelinePolicy.SOURCE_CAMERA,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                3,
                0,
                0.20d,
                0.08d,
                0.18d
        ));
        assertFalse(blurry.runTextDetector, "a blurry camera frame must not spend heavy OCR");
        assertEquals(RecognitionPipelinePolicy.REASON_QUALITY, blurry.reason);

        RecognitionPipelinePolicy.Decision clear = RecognitionPipelinePolicy.decide(input(
                RecognitionPipelinePolicy.SOURCE_CAMERA,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                3,
                0,
                0.66d,
                0.38d,
                0.06d
        ));
        assertTrue(clear.runTextDetector, "a clear camera frame should run heavy OCR");
        assertEquals(960, clear.detectorInputSide);
        assertEquals(48, clear.displayRegionLimit);
        assertEquals(8, clear.recognitionRegionLimit);
        assertEquals(2, RecognitionPipelinePolicy.mlKitTimeoutSeconds(
                RecognitionPipelinePolicy.SOURCE_CAMERA));
        passed++;
    }

    private void videoUsesQualityAndFixedBudget() {
        RecognitionPipelinePolicy.Decision notDetail = RecognitionPipelinePolicy.decide(input(
                RecognitionPipelinePolicy.SOURCE_VIDEO,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                5,
                0,
                0.80d,
                0.50d,
                0.02d
        ));
        assertFalse(notDetail.runTextDetector, "non-detail video frames should stay cheap");
        assertEquals(RecognitionPipelinePolicy.REASON_NOT_DETAIL_FRAME, notDetail.reason);

        RecognitionPipelinePolicy.Decision useful = RecognitionPipelinePolicy.decide(input(
                RecognitionPipelinePolicy.SOURCE_VIDEO,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                6,
                1,
                0.58d,
                0.32d,
                0.08d
        ));
        assertTrue(useful.runTextDetector, "a useful video keyframe should share the OCR pipeline");
        assertEquals(960, useful.detectorInputSide);
        assertEquals(10, useful.recognitionRegionLimit);
        assertEquals(6, RecognitionPipelinePolicy.mlKitTimeoutSeconds(
                RecognitionPipelinePolicy.SOURCE_IMAGE));

        RecognitionPipelinePolicy.Decision exhausted = RecognitionPipelinePolicy.decide(input(
                RecognitionPipelinePolicy.SOURCE_VIDEO,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                20,
                4,
                0.90d,
                0.70d,
                0.01d
        ));
        assertFalse(exhausted.runTextDetector, "video must stop after its detector budget");
        assertEquals(RecognitionPipelinePolicy.REASON_BUDGET_EXHAUSTED, exhausted.reason);
        passed++;
    }

    private void completedAndBarcodeOnlySessionsStop() {
        RecognitionPipelinePolicy.Decision complete = RecognitionPipelinePolicy.decide(input(
                RecognitionPipelinePolicy.SOURCE_CAMERA,
                true,
                false,
                false,
                true,
                false,
                true,
                true,
                9,
                2,
                0.90d,
                0.70d,
                0.01d
        ));
        assertFalse(complete.runTextDetector, "stable dates should freeze heavy date OCR");
        assertEquals(RecognitionPipelinePolicy.REASON_COMPLETE, complete.reason);

        RecognitionPipelinePolicy.Decision barcodeOnly = RecognitionPipelinePolicy.decide(input(
                RecognitionPipelinePolicy.SOURCE_CAMERA,
                true,
                true,
                false,
                true,
                false,
                false,
                false,
                3,
                0,
                0.90d,
                0.70d,
                0.01d
        ));
        assertFalse(barcodeOnly.runTextDetector, "barcode-only lookup must skip text detection");
        assertEquals(RecognitionPipelinePolicy.REASON_BARCODE_ONLY, barcodeOnly.reason);
        passed++;
    }

    private RecognitionPipelinePolicy.Input input(
            int source,
            boolean modelReady,
            boolean barcodeLookupOnly,
            boolean singleFrame,
            boolean detailFrame,
            boolean screenRecording,
            boolean hasCompleteDate,
            boolean dateOnlyMode,
            int analyzedFrames,
            int completedPasses,
            double visualScore,
            double sharpness,
            double glareRatio
    ) {
        return new RecognitionPipelinePolicy.Input(
                source,
                modelReady,
                barcodeLookupOnly,
                singleFrame,
                detailFrame,
                screenRecording,
                hasCompleteDate,
                dateOnlyMode,
                analyzedFrames,
                completedPasses,
                visualScore,
                sharpness,
                glareRatio
        );
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected <" + expected + "> but got <" + actual + ">");
        }
    }
}
