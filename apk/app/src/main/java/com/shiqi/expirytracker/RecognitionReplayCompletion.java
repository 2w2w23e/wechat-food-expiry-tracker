package com.shiqi.expirytracker;

import java.util.Collections;

/**
 * Decides whether accumulated replay evidence is already sufficient for review.
 *
 * <p>The video decoder may inspect many frames, but expensive OCR should stop once
 * the cross-frame transcript contains a reliable date result and, outside
 * replenishment mode, a usable product identity.</p>
 */
final class RecognitionReplayCompletion {
    private RecognitionReplayCompletion() {}

    static boolean shouldFinish(
            int analyzedFrames,
            boolean dateOnlyMode,
            boolean hasProductIdentity,
            String transcript,
            DateOcrParser.Result earlierFallback
    ) {
        if (analyzedFrames < 3 || FoodItem.cleanText(transcript).length() == 0) {
            return false;
        }
        DateOcrParser.Result selected = DateEvidencePolicy.chooseVideoCompletionEvidence(
                transcript,
                earlierFallback
        );
        DateOcrFrameVoter.VoteResult vote = DateOcrFrameVoter.vote(
                Collections.singletonList(selected),
                1
        );
        boolean completeDate = UnifiedRecognitionStabilizer.isReliableDirectDatePair(vote)
                || DateEvidencePolicy.isReliableCalculatedDate(vote);
        return completeDate && (dateOnlyMode || hasProductIdentity);
    }

    static boolean isRichTextIdentity(String value) {
        String text = FoodItem.cleanText(value).replaceAll("\\s+", "");
        return text.length() >= 4;
    }

    static boolean shouldFinishIdentityOnly(
            int analyzedFrames,
            double videoRatio,
            boolean cameraScreenRecording,
            int fullTextPasses,
            boolean hasStrongTextIdentity,
            boolean hasCompleteDate
    ) {
        return shouldFinishIdentityOnly(
                analyzedFrames,
                videoRatio,
                cameraScreenRecording,
                fullTextPasses,
                hasStrongTextIdentity,
                hasCompleteDate,
                false
        );
    }

    static boolean shouldFinishIdentityOnly(
            int analyzedFrames,
            double videoRatio,
            boolean cameraScreenRecording,
            int fullTextPasses,
            boolean hasFinishableIdentity,
            boolean hasCompleteDate,
            boolean cameraSimulation
    ) {
        return (cameraScreenRecording || cameraSimulation)
                && analyzedFrames >= 5
                && videoRatio >= 0.70d
                && fullTextPasses >= 2
                && hasFinishableIdentity
                && !hasCompleteDate;
    }
}
