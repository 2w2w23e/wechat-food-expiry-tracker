package com.shiqi.expirytracker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * Produces a horizontal text-line bitmap from a detector quadrilateral.
 */
final class PerspectiveTextCropper {
    private static final int MIN_SIDE = 8;
    private static final int MAX_LONG_SIDE = 1600;

    static Bitmap crop(
            Bitmap source,
            PaddleTextDetectionEngine.TextRegion region
    ) {
        if (source == null || source.isRecycled() || region == null || !region.hasQuadrilateral()) {
            return null;
        }
        double[] normalized = region.quadrilateral();
        float[] sourcePoints = new float[8];
        for (int index = 0; index < 4; index++) {
            sourcePoints[index * 2] =
                    (float) (normalized[index * 2] * source.getWidth());
            sourcePoints[(index * 2) + 1] =
                    (float) (normalized[(index * 2) + 1] * source.getHeight());
        }

        double topWidth = distance(sourcePoints, 0, 2);
        double bottomWidth = distance(sourcePoints, 6, 4);
        double leftHeight = distance(sourcePoints, 0, 6);
        double rightHeight = distance(sourcePoints, 2, 4);
        int targetWidth = Math.max(MIN_SIDE, (int) Math.round(Math.max(topWidth, bottomWidth)));
        int targetHeight = Math.max(MIN_SIDE, (int) Math.round(Math.max(leftHeight, rightHeight)));
        if (targetWidth <= MIN_SIDE || targetHeight <= MIN_SIDE) {
            return null;
        }

        double scale = Math.min(
                1d,
                MAX_LONG_SIDE / (double) Math.max(targetWidth, targetHeight)
        );
        targetWidth = Math.max(MIN_SIDE, (int) Math.round(targetWidth * scale));
        targetHeight = Math.max(MIN_SIDE, (int) Math.round(targetHeight * scale));
        float[] destinationPoints = new float[] {
                0f, 0f,
                targetWidth - 1f, 0f,
                targetWidth - 1f, targetHeight - 1f,
                0f, targetHeight - 1f
        };

        Bitmap warped = null;
        try {
            Matrix transform = new Matrix();
            if (!transform.setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4)) {
                return null;
            }
            warped = Bitmap.createBitmap(
                    targetWidth,
                    targetHeight,
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(warped);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(source, transform, paint);
            if (targetHeight <= targetWidth * 1.20d) {
                return warped;
            }
            Matrix rotation = new Matrix();
            rotation.postRotate(90f);
            Bitmap horizontal = Bitmap.createBitmap(
                    warped,
                    0,
                    0,
                    warped.getWidth(),
                    warped.getHeight(),
                    rotation,
                    true
            );
            warped.recycle();
            return horizontal;
        } catch (RuntimeException error) {
            if (warped != null && !warped.isRecycled()) {
                warped.recycle();
            }
            return null;
        }
    }

    private static double distance(float[] points, int first, int second) {
        double dx = points[first] - points[second];
        double dy = points[first + 1] - points[second + 1];
        return Math.sqrt((dx * dx) + (dy * dy));
    }

    private PerspectiveTextCropper() {
    }
}
