package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class UnifiedRecognitionStabilizer {
    private static final int DEFAULT_MAX_FRAMES = 10;
    private static final int DEFAULT_DATE_MIN_VOTES = 3;
    private static final int BARCODE_LOCK_VOTES = 2;
    private static final int PACKAGING_NAME_LOCK_VOTES = 1;

    private final int maxFrames;
    private final int dateMinVotes;
    private final List<Frame> frames = new ArrayList<Frame>();
    private String lockedBarcode = "";
    private String lockedPackagingName = "";
    private DateOcrFrameVoter.VoteResult stableDateVote;
    private DateOcrFrameVoter.VoteResult latestDateVote;
    private String latestRawText = "";

    UnifiedRecognitionStabilizer() {
        this(DEFAULT_MAX_FRAMES, DEFAULT_DATE_MIN_VOTES);
    }

    UnifiedRecognitionStabilizer(int maxFrames, int dateMinVotes) {
        this.maxFrames = Math.max(2, maxFrames);
        this.dateMinVotes = Math.max(1, dateMinVotes);
    }

    Snapshot addFrame(
            String rawBarcode,
            DateOcrParser.Result ocrResult,
            String rawText,
            boolean singleFrameConfirmation
    ) {
        String barcode = BarcodeUtils.extractProductCode(rawBarcode);
        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
            barcode = "";
        }

        String cleanedRawText = FoodItem.cleanText(rawText);
        String packagingName = RecognitionTextCleaner.extractProductNameFromOcr(cleanedRawText);
        Frame frame = new Frame(barcode, packagingName, ocrResult, cleanedRawText);
        frames.add(frame);
        while (frames.size() > maxFrames) {
            frames.remove(0);
        }

        latestRawText = frame.rawText;
        updateBarcodeLock(barcode, singleFrameConfirmation);
        updatePackagingNameLock(packagingName, singleFrameConfirmation);
        updateDateVote();
        return snapshot();
    }

    Snapshot snapshot() {
        return new Snapshot(
                frames.size(),
                lockedBarcode,
                barcodeVotes(lockedBarcode),
                lockedPackagingName,
                packagingNameVotes(lockedPackagingName),
                stableDateVote,
                latestDateVote,
                latestRawText
        );
    }

    void reset() {
        frames.clear();
        lockedBarcode = "";
        lockedPackagingName = "";
        stableDateVote = null;
        latestDateVote = null;
        latestRawText = "";
    }

    private void updateBarcodeLock(String barcode, boolean singleFrameConfirmation) {
        if (lockedBarcode.length() > 0) {
            return;
        }
        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
            return;
        }
        if (singleFrameConfirmation || barcodeVotes(barcode) >= BARCODE_LOCK_VOTES) {
            lockedBarcode = barcode;
        }
    }

    private void updateDateVote() {
        List<DateOcrParser.Result> ocrFrames = new ArrayList<DateOcrParser.Result>();
        for (Frame frame : frames) {
            if (frame.ocrResult != null && frame.ocrResult.hasAnyCandidate()) {
                ocrFrames.add(frame.ocrResult);
            }
        }
        latestDateVote = DateOcrFrameVoter.vote(ocrFrames, dateMinVotes);
        if (latestDateVote.readyForUserConfirmation()) {
            stableDateVote = latestDateVote;
        }
    }

    private void updatePackagingNameLock(String packagingName, boolean singleFrameConfirmation) {
        if (lockedPackagingName.length() > 0) {
            return;
        }
        String name = FoodItem.cleanText(packagingName);
        if (name.length() == 0) {
            return;
        }
        if (singleFrameConfirmation || packagingNameVotes(name) >= PACKAGING_NAME_LOCK_VOTES) {
            lockedPackagingName = name;
        }
    }

    private int barcodeVotes(String barcode) {
        String code = BarcodeUtils.digitsOnly(barcode);
        if (code.length() == 0) {
            return 0;
        }
        int votes = 0;
        for (Frame frame : frames) {
            if (code.equals(frame.barcode)) {
                votes++;
            }
        }
        return votes;
    }

    private int packagingNameVotes(String packagingName) {
        String name = FoodItem.cleanText(packagingName);
        if (name.length() == 0) {
            return 0;
        }
        int votes = 0;
        for (Frame frame : frames) {
            if (RecognitionTextCleaner.productNamesSimilar(name, frame.packagingName)) {
                votes++;
            }
        }
        return votes;
    }

    private static final class Frame {
        final String barcode;
        final String packagingName;
        final DateOcrParser.Result ocrResult;
        final String rawText;

        Frame(String barcode, String packagingName, DateOcrParser.Result ocrResult, String rawText) {
            this.barcode = barcode == null ? "" : barcode;
            this.packagingName = FoodItem.cleanText(packagingName);
            this.ocrResult = ocrResult;
            this.rawText = rawText == null ? "" : rawText;
        }
    }

    static final class Snapshot {
        final int frameCount;
        final String stableBarcode;
        final int barcodeVotes;
        final String stablePackagingName;
        final int packagingNameVotes;
        final DateOcrFrameVoter.VoteResult stableDateVote;
        final DateOcrFrameVoter.VoteResult latestDateVote;
        final String latestRawText;

        Snapshot(
                int frameCount,
                String stableBarcode,
                int barcodeVotes,
                String stablePackagingName,
                int packagingNameVotes,
                DateOcrFrameVoter.VoteResult stableDateVote,
                DateOcrFrameVoter.VoteResult latestDateVote,
                String latestRawText
        ) {
            this.frameCount = frameCount;
            this.stableBarcode = stableBarcode == null ? "" : stableBarcode;
            this.barcodeVotes = barcodeVotes;
            this.stablePackagingName = FoodItem.cleanText(stablePackagingName);
            this.packagingNameVotes = Math.max(0, packagingNameVotes);
            this.stableDateVote = stableDateVote;
            this.latestDateVote = latestDateVote;
            this.latestRawText = latestRawText == null ? "" : latestRawText;
        }

        boolean hasStableBarcode() {
            return BarcodeUtils.isSupportedProductCode(stableBarcode);
        }

        boolean hasStableDateCandidate() {
            return stableDateVote != null && stableDateVote.readyForUserConfirmation();
        }

        boolean hasStablePackagingName() {
            return stablePackagingName.length() > 0;
        }

        boolean hasFillableCandidate() {
            if (hasStableBarcode()) {
                return true;
            }
            if (hasStablePackagingName()) {
                return true;
            }
            if (!hasStableDateCandidate()) {
                return false;
            }
            return DateOcrResultPayload.hasUsableDraft(DateOcrResultPayload.toDraft(stableDateVote));
        }

        boolean hasAnySeenCandidate() {
            return hasFillableCandidate()
                    || (latestDateVote != null
                    && (latestDateVote.hasStableCandidate() || latestDateVote.framesWithCandidates > 0));
        }

        List<String> statusHints() {
            List<String> hints = new ArrayList<String>();
            if (hasStableBarcode()) {
                hints.add("条码已锁定");
            }
            if (hasStableDateCandidate()) {
                hints.add("日期候选已稳定");
            } else if (latestDateVote != null && latestDateVote.hasConflict) {
                hints.add("日期候选冲突");
            }
            if (latestRawText.length() > 0) {
                hints.add("已看到包装文字");
            }
            return Collections.unmodifiableList(hints);
        }
    }
}
