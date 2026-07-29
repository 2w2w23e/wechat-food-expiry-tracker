package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts OCR observations into reviewable fields.
 *
 * <p>This is intentionally separate from camera and model orchestration so a
 * detector hit, recognized text, semantic candidate, and promoted field can be
 * tested and diagnosed independently.</p>
 */
final class RecognitionFieldPromoter {
    private RecognitionFieldPromoter() {}

    static Promotion promote(
            UnifiedRecognitionStabilizer stabilizer,
            UnifiedRecognitionStabilizer.Snapshot previous,
            String rawBarcode,
            DateOcrParser.Result parsed,
            String rawText,
            List<PackagingTextAnalyzer.Candidate> packagingCandidates,
            boolean singleFrameConfirmation
    ) {
        if (stabilizer == null) {
            throw new IllegalArgumentException("stabilizer is required");
        }
        String cleanedText = FoodItem.cleanText(rawText);
        String printedBarcode = RecognitionTextCleaner.extractProductCodeFromOcr(cleanedText);
        String barcode = VerifiedBarcodeCatalog.preferCatalogBackedBarcode(
                rawBarcode,
                printedBarcode
        );
        if (RecognitionTextCleaner.isNonProductTraceabilityCode(cleanedText, barcode)) {
            barcode = "";
        }

        List<PackagingTextAnalyzer.Candidate> safeCandidates =
                packagingCandidates == null
                        ? Collections.<PackagingTextAnalyzer.Candidate>emptyList()
                        : packagingCandidates;
        UnifiedRecognitionStabilizer.Snapshot current = stabilizer.addFrame(
                barcode,
                parsed,
                cleanedText,
                safeCandidates,
                singleFrameConfirmation
        );
        UnifiedRecognitionStabilizer.Snapshot snapshot =
                UnifiedRecognitionStabilizer.retainStableEvidence(previous, current);
        return new Promotion(
                snapshot,
                cleanedText,
                safeCandidates.size(),
                hasNewBarcode(previous, snapshot),
                hasNewPackagingName(previous, snapshot),
                hasNewDate(previous, snapshot)
        );
    }

    private static boolean hasNewBarcode(
            UnifiedRecognitionStabilizer.Snapshot previous,
            UnifiedRecognitionStabilizer.Snapshot current
    ) {
        return current != null
                && current.hasStableBarcode()
                && (previous == null
                || !current.stableBarcode.equals(previous.stableBarcode));
    }

    private static boolean hasNewPackagingName(
            UnifiedRecognitionStabilizer.Snapshot previous,
            UnifiedRecognitionStabilizer.Snapshot current
    ) {
        return current != null
                && current.hasStablePackagingName()
                && (previous == null
                || !current.stablePackagingName.equals(previous.stablePackagingName));
    }

    private static boolean hasNewDate(
            UnifiedRecognitionStabilizer.Snapshot previous,
            UnifiedRecognitionStabilizer.Snapshot current
    ) {
        if (current == null || !current.hasStableDateCandidate()) {
            return false;
        }
        if (previous == null || !previous.hasStableDateCandidate()) {
            return true;
        }
        return !dateSignature(current.stableDateVote).equals(
                dateSignature(previous.stableDateVote)
        );
    }

    private static String dateSignature(DateOcrFrameVoter.VoteResult value) {
        if (value == null) {
            return "";
        }
        return dateValue(value.productionDate)
                + "|" + dateValue(value.expiryDate)
                + "|" + shelfLifeValue(value.shelfLife)
                + "|" + dateValue(value.calculatedExpiryDate);
    }

    private static String dateValue(DateOcrFrameVoter.StableDate value) {
        return value == null ? "" : FoodItem.cleanText(value.value);
    }

    private static String shelfLifeValue(DateOcrFrameVoter.StableShelfLife value) {
        return value == null
                ? ""
                : value.value + ":" + FoodItem.cleanText(value.unit);
    }

    static List<PackagingTextAnalyzer.Candidate> candidatesFromTranscript(String rawText) {
        String cleaned = FoodItem.cleanText(rawText);
        if (cleaned.length() == 0) {
            return Collections.emptyList();
        }
        List<PackagingTextAnalyzer.Candidate> candidates =
                new ArrayList<PackagingTextAnalyzer.Candidate>(
                        PackagingTextAnalyzer.analyze(observationsFromRawText(cleaned), true)
                );
        String recoveredName = PackagingNamePolicy.recoverStrongCandidate(cleaned);
        String extractedName = recoveredName.length() > 0
                ? recoveredName
                : RecognitionTextCleaner.extractProductNameFromOcr(cleaned);
        boolean strongExtractedName = PackagingNamePolicy.isStrong(extractedName);
        if (strongExtractedName) {
            addOrReplaceCandidate(candidates, new PackagingTextAnalyzer.Candidate(
                    extractedName,
                    Math.max(
                            92d,
                            RecognitionTextCleaner.productNameScore(extractedName) + 44d
                    ),
                    2,
                    Collections.singletonList(extractedName)
            ));
        }
        return Collections.unmodifiableList(candidates);
    }

    private static List<PackagingTextAnalyzer.Observation> observationsFromRawText(
            String rawText
    ) {
        List<PackagingTextAnalyzer.Observation> observations =
                new ArrayList<PackagingTextAnalyzer.Observation>();
        String cleaned = RecognitionTextCleaner.cleanForPackagingOcr(rawText);
        for (String line : cleaned.split("\\r?\\n")) {
            String value = FoodItem.cleanText(line);
            if (value.length() == 0) {
                continue;
            }
            observations.add(new PackagingTextAnalyzer.Observation(
                    value,
                    0.035d,
                    Math.min(0.9d, 0.12d + value.length() * 0.035d),
                    0.5d,
                    0.5d,
                    0.55d
            ));
        }
        return observations;
    }

    private static void addOrReplaceCandidate(
            List<PackagingTextAnalyzer.Candidate> candidates,
            PackagingTextAnalyzer.Candidate incoming
    ) {
        for (int index = 0; index < candidates.size(); index++) {
            PackagingTextAnalyzer.Candidate current = candidates.get(index);
            if (!RecognitionTextCleaner.productNamesSimilar(current.text, incoming.text)) {
                continue;
            }
            if (incoming.score > current.score
                    || (incoming.score == current.score
                    && incoming.text.length() > current.text.length())) {
                candidates.set(index, incoming);
            }
            return;
        }
        candidates.add(incoming);
    }

    static final class Promotion {
        final UnifiedRecognitionStabilizer.Snapshot snapshot;
        final String cleanedText;
        final int candidateCount;
        final boolean barcodePromoted;
        final boolean namePromoted;
        final boolean datePromoted;

        Promotion(
                UnifiedRecognitionStabilizer.Snapshot snapshot,
                String cleanedText,
                int candidateCount,
                boolean barcodePromoted,
                boolean namePromoted,
                boolean datePromoted
        ) {
            this.snapshot = snapshot;
            this.cleanedText = cleanedText == null ? "" : cleanedText;
            this.candidateCount = Math.max(0, candidateCount);
            this.barcodePromoted = barcodePromoted;
            this.namePromoted = namePromoted;
            this.datePromoted = datePromoted;
        }

        boolean hasText() {
            return cleanedText.length() > 0;
        }
    }
}
