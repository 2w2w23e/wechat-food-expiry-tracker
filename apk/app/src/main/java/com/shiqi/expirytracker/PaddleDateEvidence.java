package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;

final class PaddleDateEvidence {
    final String text;
    final String originalText;
    final String packagingText;
    final double confidence;
    final double packagingConfidence;
    final RecognitionEvidence.NormalizedRect rect;

    PaddleDateEvidence(
            String text,
            String originalText,
            double confidence,
            RecognitionEvidence.NormalizedRect rect
    ) {
        this(text, originalText, originalText, confidence, confidence, rect);
    }

    PaddleDateEvidence(
            String text,
            String originalText,
            String packagingText,
            double confidence,
            double packagingConfidence,
            RecognitionEvidence.NormalizedRect rect
    ) {
        this.text = FoodItem.cleanText(text);
        this.originalText = FoodItem.cleanText(originalText);
        this.packagingText = FoodItem.cleanText(packagingText);
        this.confidence = RecognitionEvidence.clamp01(confidence);
        this.packagingConfidence = RecognitionEvidence.clamp01(packagingConfidence);
        this.rect = rect == null
                ? new RecognitionEvidence.NormalizedRect(0d, 0d, 0d, 0d)
                : rect;
    }

    PackagingTextAnalyzer.Observation toObservation(double sourceQuality) {
        double width = Math.max(0d, rect.right - rect.left);
        double height = Math.max(0d, rect.bottom - rect.top);
        return new PackagingTextAnalyzer.Observation(
                text,
                height,
                width,
                (rect.left + rect.right) / 2d,
                (rect.top + rect.bottom) / 2d,
                sourceQuality,
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                confidence <= 0d ? Double.NaN : confidence,
                "PP-OCRv6"
        );
    }

    List<PackagingTextAnalyzer.Observation> toPackagingObservations(double sourceQuality) {
        List<PackagingTextAnalyzer.Observation> observations =
                new ArrayList<PackagingTextAnalyzer.Observation>();
        if (packagingText.length() == 0) {
            return observations;
        }
        String[] lines = packagingText.split("\\n");
        double width = Math.max(0.02d, rect.right - rect.left);
        double totalHeight = Math.max(0.02d, rect.bottom - rect.top);
        double lineHeight = Math.max(0.02d, totalHeight / Math.max(1, lines.length));
        for (int index = 0; index < lines.length; index++) {
            String line = FoodItem.cleanText(lines[index]);
            if (line.length() == 0) {
                continue;
            }
            double top = Math.min(rect.bottom, rect.top + (lineHeight * index));
            double bottom = Math.min(rect.bottom, Math.max(top + 0.01d, top + lineHeight));
            observations.add(new PackagingTextAnalyzer.Observation(
                    line,
                    lineHeight,
                    width,
                    (rect.left + rect.right) / 2d,
                    (top + bottom) / 2d,
                    sourceQuality,
                    rect.left,
                    top,
                    rect.right,
                    bottom,
                    packagingConfidence <= 0d ? Double.NaN : packagingConfidence,
                    "PP-OCRv6 检测"
            ));
        }
        return observations;
    }

    static PaddleDateEvidence empty() {
        return new PaddleDateEvidence("", "", 0d, null);
    }
}
