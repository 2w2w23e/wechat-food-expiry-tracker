package com.shiqi.expirytracker;

import android.graphics.Bitmap;

final class RecognitionFrameVariant {
    final Bitmap bitmap;
    final int rotationDegrees;
    final boolean scanBarcode;
    final boolean scanText;
    final boolean scanLatinText;
    final boolean recycleBitmap;
    final double sourceQuality;
    final boolean dateFocused;
    final boolean datePairSupplement;
    final double sourceLeft;
    final double sourceTop;
    final double sourceWidth;
    final double sourceHeight;

    RecognitionFrameVariant(
            Bitmap bitmap,
            int rotationDegrees,
            boolean scanBarcode,
            boolean scanText,
            boolean scanLatinText,
            boolean recycleBitmap,
            double sourceQuality
    ) {
        this(
                bitmap,
                rotationDegrees,
                scanBarcode,
                scanText,
                scanLatinText,
                recycleBitmap,
                sourceQuality,
                false,
                false,
                0d,
                0d,
                1d,
                1d
        );
    }

    RecognitionFrameVariant(
            Bitmap bitmap,
            int rotationDegrees,
            boolean scanBarcode,
            boolean scanText,
            boolean scanLatinText,
            boolean recycleBitmap,
            double sourceQuality,
            boolean dateFocused
    ) {
        this(
                bitmap,
                rotationDegrees,
                scanBarcode,
                scanText,
                scanLatinText,
                recycleBitmap,
                sourceQuality,
                dateFocused,
                false,
                0d,
                0d,
                1d,
                1d
        );
    }

    RecognitionFrameVariant(
            Bitmap bitmap,
            int rotationDegrees,
            boolean scanBarcode,
            boolean scanText,
            boolean scanLatinText,
            boolean recycleBitmap,
            double sourceQuality,
            boolean dateFocused,
            boolean datePairSupplement
    ) {
        this(
                bitmap,
                rotationDegrees,
                scanBarcode,
                scanText,
                scanLatinText,
                recycleBitmap,
                sourceQuality,
                dateFocused,
                datePairSupplement,
                0d,
                0d,
                1d,
                1d
        );
    }

    RecognitionFrameVariant(
            Bitmap bitmap,
            int rotationDegrees,
            boolean scanBarcode,
            boolean scanText,
            boolean scanLatinText,
            boolean recycleBitmap,
            double sourceQuality,
            boolean dateFocused,
            boolean datePairSupplement,
            double sourceLeft,
            double sourceTop,
            double sourceWidth,
            double sourceHeight
    ) {
        this.bitmap = bitmap;
        this.rotationDegrees = rotationDegrees;
        this.scanBarcode = scanBarcode;
        this.scanText = scanText;
        this.scanLatinText = scanLatinText;
        this.recycleBitmap = recycleBitmap;
        this.sourceQuality = sourceQuality;
        this.dateFocused = dateFocused;
        this.datePairSupplement = datePairSupplement;
        this.sourceLeft = sourceLeft;
        this.sourceTop = sourceTop;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
    }

    RecognitionFrameVariant withSourceQuality(double quality) {
        return new RecognitionFrameVariant(
                bitmap,
                rotationDegrees,
                scanBarcode,
                scanText,
                scanLatinText,
                recycleBitmap,
                quality,
                dateFocused,
                datePairSupplement,
                sourceLeft,
                sourceTop,
                sourceWidth,
                sourceHeight
        );
    }
}
