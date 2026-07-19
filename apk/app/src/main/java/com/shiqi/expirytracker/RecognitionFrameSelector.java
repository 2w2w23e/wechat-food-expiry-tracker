package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class RecognitionFrameSelector {
    static final int MAX_KEYFRAMES = 3;
    static final long DEFAULT_MIN_TIME_GAP_MILLIS = 500L;

    static final int EVIDENCE_NONE = 0;
    static final int EVIDENCE_PRODUCT_NAME = 1;
    static final int EVIDENCE_PRODUCTION_DATE = 1 << 1;
    static final int EVIDENCE_EXPIRY_DATE = 1 << 2;
    static final int EVIDENCE_BARCODE = 1 << 3;

    private static final double CLARITY_WEIGHT = 0.30d;
    private static final double EXPOSURE_WEIGHT = 0.15d;
    private static final double GLARE_WEIGHT = 0.15d;
    private static final double TEXT_COVERAGE_WEIGHT = 0.15d;
    private static final double OCR_CONFIDENCE_WEIGHT = 0.20d;
    private static final double TEMPORAL_POSITION_WEIGHT = 0.05d;

    private static final double FIELD_QUALITY_WEIGHT = 0.40d;
    private static final double FIELD_VOTE_WEIGHT = 0.35d;
    private static final double FIELD_OCR_WEIGHT = 0.25d;

    private static final Comparator<FrameCandidate> QUALITY_ORDER =
            new Comparator<FrameCandidate>() {
                @Override
                public int compare(FrameCandidate left, FrameCandidate right) {
                    int quality = Double.compare(right.qualityScore, left.qualityScore);
                    if (quality != 0) {
                        return quality;
                    }
                    int timestamp = Long.compare(left.timestampMillis, right.timestampMillis);
                    if (timestamp != 0) {
                        return timestamp;
                    }
                    int frameId = left.frameId.compareTo(right.frameId);
                    return frameId != 0
                            ? frameId
                            : left.contentSignature.compareTo(right.contentSignature);
                }
            };

    private final long minTimeGapMillis;
    private final List<FrameCandidate> selectedFrames = new ArrayList<FrameCandidate>();

    RecognitionFrameSelector() {
        this(DEFAULT_MIN_TIME_GAP_MILLIS);
    }

    RecognitionFrameSelector(long minTimeGapMillis) {
        this.minTimeGapMillis = Math.max(0L, minTimeGapMillis);
    }

    /**
     * Inputs are normalized to 0..1. Exposure and temporal position are raw normalized
     * positions, so values near 0.5 are preferred over either extreme.
     */
    static double qualityScore(
            double clarity,
            double exposure,
            double glareRatio,
            double textCoverage,
            double ocrConfidence,
            double temporalPosition
    ) {
        double exposureBalance = centeredQuality(exposure);
        double glareQuality = 1.0d - normalizeGlareRatio(glareRatio);
        double temporalBalance = centeredQuality(temporalPosition);
        return clamp01(
                CLARITY_WEIGHT * clamp01(clarity)
                        + EXPOSURE_WEIGHT * exposureBalance
                        + GLARE_WEIGHT * glareQuality
                        + TEXT_COVERAGE_WEIGHT * clamp01(textCoverage)
                        + OCR_CONFIDENCE_WEIGHT * clamp01(ocrConfidence)
                        + TEMPORAL_POSITION_WEIGHT * temporalBalance
        );
    }

    static boolean shouldRunHeavyCameraOcr(
            int analyzedFrames,
            int completedHeavyPasses,
            double visualScore,
            double sharpness,
            double glareRatio,
            boolean hasCompleteDateCandidate
    ) {
        return shouldRunHeavyCameraOcr(
                analyzedFrames,
                completedHeavyPasses,
                visualScore,
                sharpness,
                glareRatio,
                hasCompleteDateCandidate,
                false
        );
    }

    static boolean shouldRunHeavyCameraOcr(
            int analyzedFrames,
            int completedHeavyPasses,
            double visualScore,
            double sharpness,
            double glareRatio,
            boolean hasCompleteDateCandidate,
            boolean dateOnlyMode
    ) {
        int maxPasses = dateOnlyMode ? 7 : 5;
        if (hasCompleteDateCandidate || completedHeavyPasses >= maxPasses || analyzedFrames < 2) {
            return false;
        }
        int earliestFrame = dateOnlyMode
                ? 2 + completedHeavyPasses
                : 2 + (completedHeavyPasses * 2);
        if (analyzedFrames < earliestFrame) {
            return false;
        }
        boolean normalQuality = visualScore >= 0.42d
                && sharpness >= 0.20d
                && glareRatio <= 0.20d;
        if (normalQuality) {
            return true;
        }
        return analyzedFrames >= earliestFrame + 1
                && visualScore >= 0.32d
                && sharpness >= 0.14d
                && glareRatio <= 0.30d;
    }

    /**
     * Keep acquiring while recognition is busy. The single recognition worker
     * replaces its one pending bitmap, so this raises freshness without growing a queue.
     */
    static boolean shouldCaptureLatestCameraFrame(
            boolean cameraBound,
            long nowMillis,
            long lastCaptureMillis,
            long minimumIntervalMillis,
            boolean recognitionBusy
    ) {
        if (!cameraBound) {
            return false;
        }
        long safeInterval = Math.max(0L, minimumIntervalMillis);
        return nowMillis >= lastCaptureMillis
                && nowMillis - lastCaptureMillis >= safeInterval;
    }

    static List<Long> highRateVideoFrameTimes(long durationUs, boolean longVideo) {
        long safeDurationUs = Math.max(0L, durationUs);
        int maxCandidates = longVideo ? 180 : 120;
        long targetIntervalUs = 66667L;
        int idealCount = safeDurationUs == 0L
                ? 1
                : (int) Math.min(
                Integer.MAX_VALUE,
                (safeDurationUs + targetIntervalUs - 1L) / targetIntervalUs + 1L
        );
        int count = Math.max(1, Math.min(maxCandidates, idealCount));
        List<Long> times = new ArrayList<Long>(count);
        if (count == 1) {
            times.add(Long.valueOf(0L));
            return times;
        }
        for (int index = 0; index < count; index++) {
            long frameUs = Math.round(safeDurationUs * (index / (double) (count - 1)));
            if (times.isEmpty() || times.get(times.size() - 1).longValue() != frameUs) {
                times.add(Long.valueOf(frameUs));
            }
        }
        return times;
    }

    static int highRateVideoSelectionWindow(int candidateCount, boolean longVideo) {
        int safeCount = Math.max(1, candidateCount);
        int maxOcrFrames = longVideo ? 18 : 16;
        return Math.max(1, (safeCount + maxOcrFrames - 1) / maxOcrFrames);
    }

    static boolean shouldFinishCameraSimulation(
            int analyzedFrames,
            boolean hasCompleteStableCandidate
    ) {
        return hasCompleteStableCandidate && analyzedFrames >= 3;
    }

    static boolean needsIncompleteDateRefinement(
            boolean hasProductionDate,
            boolean hasDirectExpiryDate,
            boolean hasCalculatedExpiryDate
    ) {
        return hasProductionDate
                && !hasDirectExpiryDate
                && !hasCalculatedExpiryDate;
    }

    static double highRateVideoFrameScore(
            double visualScore,
            double sharpness,
            double glareRatio
    ) {
        return clamp01(
                0.50d * clamp01(visualScore)
                        + 0.40d * clamp01(sharpness)
                        + 0.10d * (1.0d - clamp01(glareRatio))
        );
    }

    /**
     * Returns true only when the retained keyframe set changes.
     */
    boolean offer(FrameCandidate candidate) {
        if (candidate == null) {
            return false;
        }

        List<FrameCandidate> nearDuplicates = new ArrayList<FrameCandidate>();
        FrameCandidate strongestDuplicate = null;
        for (FrameCandidate selected : selectedFrames) {
            if (!isNearDuplicate(selected, candidate)) {
                continue;
            }
            nearDuplicates.add(selected);
            if (strongestDuplicate == null
                    || selected.qualityScore > strongestDuplicate.qualityScore) {
                strongestDuplicate = selected;
            }
        }

        if (strongestDuplicate != null) {
            if (singleFrameUtility(candidate) <= singleFrameUtility(strongestDuplicate)) {
                return false;
            }
            selectedFrames.removeAll(nearDuplicates);
            selectedFrames.add(candidate);
            sortSelectedFrames();
            return true;
        }

        if (selectedFrames.size() < MAX_KEYFRAMES) {
            selectedFrames.add(candidate);
            sortSelectedFrames();
            return true;
        }

        List<FrameCandidate> contenders = new ArrayList<FrameCandidate>(selectedFrames);
        contenders.add(candidate);
        List<FrameCandidate> best = bestThree(contenders);
        if (!containsFrame(best, candidate.frameId)) {
            return false;
        }
        selectedFrames.clear();
        selectedFrames.addAll(best);
        sortSelectedFrames();
        return true;
    }

    List<FrameCandidate> selectedFrames() {
        return Collections.unmodifiableList(new ArrayList<FrameCandidate>(selectedFrames));
    }

    int size() {
        return selectedFrames.size();
    }

    void clear() {
        selectedFrames.clear();
    }

    /**
     * Blends separate evidence channels for one normalized field value. The result is
     * a transparent heuristic score, not a calibrated probability or one model's confidence.
     */
    static FieldFusionConfidence fuseFieldConfidence(
            double supportingFrameQuality,
            int agreeingFrames,
            int observedFrames,
            double meanOcrConfidence
    ) {
        int safeObservedFrames = Math.max(0, observedFrames);
        int safeAgreeingFrames = Math.max(0, Math.min(agreeingFrames, safeObservedFrames));
        double qualityEvidence = clamp01(supportingFrameQuality);
        double voteAgreement = safeObservedFrames == 0
                ? 0.0d
                : safeAgreeingFrames / (double) safeObservedFrames;
        double multiFrameSupport = clamp01(safeAgreeingFrames / (double) MAX_KEYFRAMES);
        double voteEvidence = clamp01(voteAgreement * multiFrameSupport);
        double ocrEvidence = clamp01(meanOcrConfidence);
        double combinedScore = clamp01(
                FIELD_QUALITY_WEIGHT * qualityEvidence
                        + FIELD_VOTE_WEIGHT * voteEvidence
                        + FIELD_OCR_WEIGHT * ocrEvidence
        );
        return new FieldFusionConfidence(
                safeAgreeingFrames,
                safeObservedFrames,
                qualityEvidence,
                voteAgreement,
                multiFrameSupport,
                voteEvidence,
                ocrEvidence,
                combinedScore
        );
    }

    private boolean isNearDuplicate(FrameCandidate first, FrameCandidate second) {
        boolean signaturesDiffer = first.contentSignature.length() > 0
                && second.contentSignature.length() > 0
                && !first.contentSignature.equals(second.contentSignature);
        if (signaturesDiffer) {
            return false;
        }
        return timestampDistance(first.timestampMillis, second.timestampMillis) < minTimeGapMillis;
    }

    private void sortSelectedFrames() {
        Collections.sort(selectedFrames, QUALITY_ORDER);
    }

    private List<FrameCandidate> bestThree(List<FrameCandidate> contenders) {
        List<FrameCandidate> best = new ArrayList<FrameCandidate>(selectedFrames);
        double bestUtility = selectionUtility(best);

        for (int removed = 0; removed < contenders.size(); removed++) {
            List<FrameCandidate> subset = new ArrayList<FrameCandidate>(MAX_KEYFRAMES);
            for (int index = 0; index < contenders.size(); index++) {
                if (index != removed) {
                    subset.add(contenders.get(index));
                }
            }
            double utility = selectionUtility(subset);
            if (utility > bestUtility + 0.000001d) {
                best = subset;
                bestUtility = utility;
            }
        }
        return best;
    }

    private static double selectionUtility(List<FrameCandidate> frames) {
        double utility = 0d;
        int coveredFields = EVIDENCE_NONE;
        int primaryFields = EVIDENCE_NONE;
        for (FrameCandidate frame : frames) {
            utility += frame.qualityScore;
            coveredFields |= frame.evidenceMask;
            primaryFields |= frame.primaryEvidence;
        }

        utility += fieldCoverageBonus(coveredFields);
        utility += 0.65d * fieldCoverageBonus(primaryFields);
        return utility;
    }

    private static double singleFrameUtility(FrameCandidate frame) {
        return frame.qualityScore
                + fieldCoverageBonus(frame.evidenceMask)
                + (0.65d * fieldCoverageBonus(frame.primaryEvidence));
    }

    private static double fieldCoverageBonus(int mask) {
        double bonus = 0d;
        if ((mask & EVIDENCE_PRODUCT_NAME) != 0) {
            bonus += 1.80d;
        }
        if ((mask & EVIDENCE_PRODUCTION_DATE) != 0) {
            bonus += 1.45d;
        }
        if ((mask & EVIDENCE_EXPIRY_DATE) != 0) {
            bonus += 1.45d;
        }
        if ((mask & EVIDENCE_BARCODE) != 0) {
            bonus += 0.45d;
        }
        return bonus;
    }

    private static boolean containsFrame(List<FrameCandidate> frames, String frameId) {
        for (FrameCandidate frame : frames) {
            if (frame.frameId.equals(frameId)) {
                return true;
            }
        }
        return false;
    }

    private static long timestampDistance(long first, long second) {
        return first >= second ? first - second : second - first;
    }

    private static double centeredQuality(double value) {
        double normalized = clamp01(value);
        return clamp01(1.0d - Math.abs(normalized - 0.5d) * 2.0d);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || value <= 0.0d) {
            return 0.0d;
        }
        if (value >= 1.0d) {
            return 1.0d;
        }
        return value;
    }

    private static double normalizeGlareRatio(double value) {
        return Double.isNaN(value) ? 1.0d : clamp01(value);
    }

    static final class FrameCandidate {
        final String frameId;
        final long timestampMillis;
        final String contentSignature;
        final double clarity;
        final double exposure;
        final double glareRatio;
        final double textCoverage;
        final double ocrConfidence;
        final double temporalPosition;
        final double qualityScore;
        final int evidenceMask;
        final int primaryEvidence;

        FrameCandidate(
                String frameId,
                long timestampMillis,
                String contentSignature,
                double clarity,
                double exposure,
                double glareRatio,
                double textCoverage,
                double ocrConfidence,
                double temporalPosition
        ) {
            this(
                    frameId,
                    timestampMillis,
                    contentSignature,
                    clarity,
                    exposure,
                    glareRatio,
                    textCoverage,
                    ocrConfidence,
                    temporalPosition,
                    EVIDENCE_NONE,
                    EVIDENCE_NONE
            );
        }

        FrameCandidate(
                String frameId,
                long timestampMillis,
                String contentSignature,
                double clarity,
                double exposure,
                double glareRatio,
                double textCoverage,
                double ocrConfidence,
                double temporalPosition,
                int evidenceMask,
                int primaryEvidence
        ) {
            this.frameId = clean(frameId);
            this.timestampMillis = Math.max(0L, timestampMillis);
            this.contentSignature = clean(contentSignature);
            this.clarity = clamp01(clarity);
            this.exposure = clamp01(exposure);
            this.glareRatio = normalizeGlareRatio(glareRatio);
            this.textCoverage = clamp01(textCoverage);
            this.ocrConfidence = clamp01(ocrConfidence);
            this.temporalPosition = clamp01(temporalPosition);
            this.evidenceMask = evidenceMask & (
                    EVIDENCE_PRODUCT_NAME
                            | EVIDENCE_PRODUCTION_DATE
                            | EVIDENCE_EXPIRY_DATE
                            | EVIDENCE_BARCODE
            );
            this.primaryEvidence = primaryEvidence & this.evidenceMask;
            this.qualityScore = RecognitionFrameSelector.qualityScore(
                    this.clarity,
                    this.exposure,
                    this.glareRatio,
                    this.textCoverage,
                    this.ocrConfidence,
                    this.temporalPosition
            );
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }

    static final class FieldFusionConfidence {
        final int agreeingFrames;
        final int observedFrames;
        final double qualityEvidence;
        final double voteAgreement;
        final double multiFrameSupport;
        final double voteEvidence;
        final double ocrEvidence;
        final double combinedScore;

        FieldFusionConfidence(
                int agreeingFrames,
                int observedFrames,
                double qualityEvidence,
                double voteAgreement,
                double multiFrameSupport,
                double voteEvidence,
                double ocrEvidence,
                double combinedScore
        ) {
            this.agreeingFrames = agreeingFrames;
            this.observedFrames = observedFrames;
            this.qualityEvidence = qualityEvidence;
            this.voteAgreement = voteAgreement;
            this.multiFrameSupport = multiFrameSupport;
            this.voteEvidence = voteEvidence;
            this.ocrEvidence = ocrEvidence;
            this.combinedScore = combinedScore;
        }

        boolean hasMultiFrameSupport() {
            return agreeingFrames >= 2;
        }
    }
}
