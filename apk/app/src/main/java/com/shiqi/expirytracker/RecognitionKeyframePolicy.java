package com.shiqi.expirytracker;

import java.util.List;

final class RecognitionKeyframePolicy {
    private RecognitionKeyframePolicy() {}

    static RecognitionEvidence.Region primaryRegion(RecognitionEvidence.Frame frame) {
        return primaryRegion(frame.regions);
    }

    // Names take precedence; equal-confidence regions retain their input order.
    static RecognitionEvidence.Region primaryRegion(List<RecognitionEvidence.Region> regions) {
        RecognitionEvidence.Region strongest = null;
        if (regions == null) {
            return null;
        }
        for (RecognitionEvidence.Region region : regions) {
            if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(region.field)) {
                return region;
            }
            if (strongest == null || region.confidence > strongest.confidence) {
                strongest = region;
            }
        }
        return strongest;
    }

    static double score(RecognitionEvidence.Frame frame) {
        double strongest = 0d;
        for (RecognitionEvidence.Region region : frame.regions) {
            strongest = Math.max(strongest, region.confidence);
        }
        return (frame.quality * 0.55d)
                + (strongest * 0.38d)
                + (Math.min(3, frame.regions.size()) * 0.025d);
    }

    static int evidenceMask(List<RecognitionEvidence.Region> regions) {
        int mask = RecognitionFrameSelector.EVIDENCE_NONE;
        if (regions != null) {
            for (RecognitionEvidence.Region region : regions) {
                mask |= evidenceForField(region.field);
            }
        }
        return mask;
    }

    static int primaryEvidence(List<RecognitionEvidence.Region> regions) {
        RecognitionEvidence.Region primary = primaryRegion(regions);
        return evidenceForField(primary == null ? null : primary.field);
    }

    // Empty frames, barcodes and unknown fields share the existing fallback order.
    static int keyframeFieldOrder(String field) {
        if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(field)) {
            return 0;
        }
        if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(field)) {
            return 1;
        }
        if (RecognitionEvidence.FIELD_EXPIRY_DATE.equals(field)) {
            return 2;
        }
        if (RecognitionEvidence.FIELD_TEXT_REGION.equals(field)) {
            return 3;
        }
        return 4;
    }

    static int compare(RecognitionEvidence.Frame left, RecognitionEvidence.Frame right) {
        RecognitionEvidence.Region leftPrimary = primaryRegion(left);
        RecognitionEvidence.Region rightPrimary = primaryRegion(right);
        int fieldOrder = Integer.compare(
                keyframeFieldOrder(leftPrimary == null ? null : leftPrimary.field),
                keyframeFieldOrder(rightPrimary == null ? null : rightPrimary.field)
        );
        return fieldOrder != 0 ? fieldOrder : Double.compare(score(right), score(left));
    }

    private static int evidenceForField(String field) {
        if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(field)) {
            return RecognitionFrameSelector.EVIDENCE_PRODUCT_NAME;
        }
        if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(field)) {
            return RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE;
        }
        if (RecognitionEvidence.FIELD_EXPIRY_DATE.equals(field)) {
            return RecognitionFrameSelector.EVIDENCE_EXPIRY_DATE;
        }
        if (RecognitionEvidence.FIELD_BARCODE.equals(field)) {
            return RecognitionFrameSelector.EVIDENCE_BARCODE;
        }
        return RecognitionFrameSelector.EVIDENCE_NONE;
    }
}
