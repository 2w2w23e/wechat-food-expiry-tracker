package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecognitionEvidence {
    static final String FIELD_PRODUCT_NAME = "product_name";
    static final String FIELD_PRODUCTION_DATE = "production_date";
    static final String FIELD_EXPIRY_DATE = "expiry_date";
    static final String FIELD_BARCODE = "barcode";

    private RecognitionEvidence() {}

    static double fusedConfidence(
            double modelConfidence,
            double frameQuality,
            double semanticConfidence,
            int distinctFrameVotes,
            int engineCount
    ) {
        double quality = clamp01(frameQuality);
        double semantic = clamp01(semanticConfidence);
        double voteSupport = clamp01(distinctFrameVotes / 3d);
        double engineSupport = clamp01(engineCount / 2d);
        double result;
        if (Double.isNaN(modelConfidence)) {
            result = clamp01(
                    (quality * 0.34d)
                            + (semantic * 0.36d)
                            + (voteSupport * 0.24d)
                            + (engineSupport * 0.06d)
            );
        } else {
            result = clamp01(
                    (clamp01(modelConfidence) * 0.30d)
                            + (quality * 0.25d)
                            + (semantic * 0.25d)
                            + (voteSupport * 0.15d)
                            + (engineSupport * 0.05d)
            );
        }
        if (distinctFrameVotes <= 1) {
            return Math.min(0.72d, result);
        }
        if (distinctFrameVotes == 2) {
            return Math.min(0.86d, result);
        }
        return result;
    }

    static String confidenceLabel(double confidence) {
        double value = clamp01(confidence);
        if (value >= 0.78d) {
            return "高";
        }
        if (value >= 0.58d) {
            return "中";
        }
        return "低";
    }

    static double clamp01(double value) {
        if (Double.isNaN(value) || value <= 0d) {
            return 0d;
        }
        return Math.min(1d, value);
    }

    static final class NormalizedRect {
        final double left;
        final double top;
        final double right;
        final double bottom;

        NormalizedRect(double left, double top, double right, double bottom) {
            double safeLeft = clamp01(Math.min(left, right));
            double safeTop = clamp01(Math.min(top, bottom));
            double safeRight = clamp01(Math.max(left, right));
            double safeBottom = clamp01(Math.max(top, bottom));
            this.left = safeLeft;
            this.top = safeTop;
            this.right = safeRight;
            this.bottom = safeBottom;
        }

        double area() {
            return Math.max(0d, right - left) * Math.max(0d, bottom - top);
        }

        NormalizedRect union(NormalizedRect other) {
            if (other == null || other.area() <= 0d) {
                return this;
            }
            if (area() <= 0d) {
                return other;
            }
            return new NormalizedRect(
                    Math.min(left, other.left),
                    Math.min(top, other.top),
                    Math.max(right, other.right),
                    Math.max(bottom, other.bottom)
            );
        }
    }

    static final class Region {
        final String field;
        final String value;
        final NormalizedRect rect;
        final String engine;
        final double modelConfidence;
        final double confidence;

        Region(
                String field,
                String value,
                NormalizedRect rect,
                String engine,
                double modelConfidence,
                double confidence
        ) {
            this.field = field == null ? "" : field;
            this.value = FoodItem.cleanText(value);
            this.rect = rect == null ? new NormalizedRect(0d, 0d, 0d, 0d) : rect;
            this.engine = engine == null ? "" : engine;
            this.modelConfidence = modelConfidence;
            this.confidence = clamp01(confidence);
        }
    }

    static final class Frame {
        final long id;
        final long timestampUs;
        final int width;
        final int height;
        final double quality;
        final long contentSignature;
        final List<Region> regions;

        Frame(
                long id,
                long timestampUs,
                int width,
                int height,
                double quality,
                long contentSignature,
                List<Region> regions
        ) {
            this.id = id;
            this.timestampUs = Math.max(0L, timestampUs);
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.quality = clamp01(quality);
            this.contentSignature = contentSignature;
            List<Region> safeRegions = regions == null
                    ? Collections.<Region>emptyList()
                    : new ArrayList<Region>(regions);
            this.regions = Collections.unmodifiableList(safeRegions);
        }
    }
}
