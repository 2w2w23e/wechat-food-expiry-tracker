package com.shiqi.expirytracker;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

final class LowContrastTextPreprocessor {
    private static final String TAG = "ShiqiLowContrast";
    private static boolean initializationAttempted;
    private static boolean available;

    private LowContrastTextPreprocessor() {}

    static synchronized boolean isAvailable() {
        if (!initializationAttempted) {
            initializationAttempted = true;
            try {
                available = OpenCVLoader.initLocal();
            } catch (Throwable error) {
                Log.w(TAG, "OpenCV initialization failed; original OCR remains available", error);
                available = false;
            }
        }
        return available;
    }

    static Bitmap enhanceLaserPrintedText(Bitmap source) {
        if (source == null || source.isRecycled() || !isAvailable()) {
            return null;
        }

        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat sharpened = new Mat();
        Mat enhanced = new Mat();
        CLAHE clahe = null;
        try {
            Utils.bitmapToMat(source, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray, blurred, new Size(0, 0), 1.1d);
            Core.addWeighted(gray, 1.65d, blurred, -0.65d, 0d, sharpened);
            clahe = Imgproc.createCLAHE(3.0d, new Size(8, 8));
            clahe.apply(sharpened, enhanced);

            Bitmap bitmap = Bitmap.createBitmap(
                    enhanced.cols(),
                    enhanced.rows(),
                    Bitmap.Config.ARGB_8888
            );
            Utils.matToBitmap(enhanced, bitmap);
            return bitmap;
        } catch (Throwable error) {
            Log.w(TAG, "Low-contrast enhancement failed for one frame", error);
            return null;
        } finally {
            if (clahe != null) {
                clahe.collectGarbage();
                clahe.clear();
            }
            enhanced.release();
            sharpened.release();
            blurred.release();
            gray.release();
            rgba.release();
        }
    }

    static Bitmap enhanceEmbossedText(Bitmap source) {
        if (source == null || source.isRecycled() || !isAvailable()) {
            return null;
        }

        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat background = new Mat();
        Mat localContrast = new Mat();
        Mat normalized = new Mat();
        Mat enhanced = new Mat();
        CLAHE clahe = null;
        try {
            Utils.bitmapToMat(source, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray, background, new Size(0, 0), 12.0d);
            Core.addWeighted(gray, 1.0d, background, -1.0d, 128.0d, localContrast);
            Core.normalize(localContrast, normalized, 0.0d, 255.0d, Core.NORM_MINMAX);
            clahe = Imgproc.createCLAHE(4.0d, new Size(12, 6));
            clahe.apply(normalized, enhanced);

            Bitmap bitmap = Bitmap.createBitmap(
                    enhanced.cols(),
                    enhanced.rows(),
                    Bitmap.Config.ARGB_8888
            );
            Utils.matToBitmap(enhanced, bitmap);
            return bitmap;
        } catch (Throwable error) {
            Log.w(TAG, "Embossed-text enhancement failed for one frame", error);
            return null;
        } finally {
            if (clahe != null) {
                clahe.collectGarbage();
                clahe.clear();
            }
            enhanced.release();
            normalized.release();
            localContrast.release();
            background.release();
            gray.release();
            rgba.release();
        }
    }

    static Bitmap binarizeText(Bitmap source, boolean invert) {
        if (source == null || source.isRecycled() || !isAvailable()) {
            return null;
        }

        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat normalized = new Mat();
        Mat binary = new Mat();
        CLAHE clahe = null;
        try {
            Utils.bitmapToMat(source, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            clahe = Imgproc.createCLAHE(4.0d, new Size(8, 4));
            clahe.apply(gray, normalized);
            Imgproc.threshold(
                    normalized,
                    binary,
                    0d,
                    255d,
                    Imgproc.THRESH_OTSU | (invert ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY)
            );

            Bitmap bitmap = Bitmap.createBitmap(
                    binary.cols(),
                    binary.rows(),
                    Bitmap.Config.ARGB_8888
            );
            Utils.matToBitmap(binary, bitmap);
            return bitmap;
        } catch (Throwable error) {
            Log.w(TAG, "Text binarization failed for one frame", error);
            return null;
        } finally {
            if (clahe != null) {
                clahe.collectGarbage();
                clahe.clear();
            }
            binary.release();
            normalized.release();
            gray.release();
            rgba.release();
        }
    }

    static Bitmap isolateBrightText(Bitmap source) {
        if (source == null || source.isRecycled() || !isAvailable()) {
            return null;
        }

        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat background = new Mat();
        Mat localContrast = new Mat();
        Mat normalized = new Mat();
        Mat binary = new Mat();
        try {
            Utils.bitmapToMat(source, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            double sigma = Math.max(4d, Math.min(24d, Math.min(gray.cols(), gray.rows()) / 12d));
            Imgproc.GaussianBlur(gray, background, new Size(0, 0), sigma);
            Core.subtract(gray, background, localContrast);
            Core.normalize(localContrast, normalized, 0d, 255d, Core.NORM_MINMAX);
            Imgproc.threshold(normalized, binary, 0d, 255d, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
            Core.bitwise_not(binary, binary);

            Bitmap bitmap = Bitmap.createBitmap(
                    binary.cols(),
                    binary.rows(),
                    Bitmap.Config.ARGB_8888
            );
            Utils.matToBitmap(binary, bitmap);
            return bitmap;
        } catch (Throwable error) {
            Log.w(TAG, "Bright-text isolation failed for one frame", error);
            return null;
        } finally {
            binary.release();
            normalized.release();
            localContrast.release();
            background.release();
            gray.release();
            rgba.release();
        }
    }

    static FrameMetrics measureFrame(Bitmap source) {
        if (source == null || source.isRecycled() || !isAvailable()) {
            return FrameMetrics.unknown();
        }
        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat laplacian = new Mat();
        Mat highlights = new Mat();
        Mat tiny = new Mat();
        MatOfDouble grayMean = new MatOfDouble();
        MatOfDouble grayDeviation = new MatOfDouble();
        MatOfDouble edgeMean = new MatOfDouble();
        MatOfDouble edgeDeviation = new MatOfDouble();
        try {
            Utils.bitmapToMat(source, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            Core.meanStdDev(gray, grayMean, grayDeviation);
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
            Core.meanStdDev(laplacian, edgeMean, edgeDeviation);
            Imgproc.threshold(gray, highlights, 244d, 255d, Imgproc.THRESH_BINARY);
            Imgproc.resize(gray, tiny, new Size(8, 8), 0d, 0d, Imgproc.INTER_AREA);

            double brightness = first(grayMean) / 255d;
            double contrast = Math.min(1d, first(grayDeviation) / 64d);
            double edgeDeviationValue = first(edgeDeviation);
            double sharpnessVariance = edgeDeviationValue * edgeDeviationValue;
            double sharpness = sharpnessVariance / (sharpnessVariance + 180d);
            double glare = gray.total() <= 0d ? 0d : Core.countNonZero(highlights) / gray.total();
            long signature = averageHash(tiny);
            double exposure = Math.max(0d, 1d - (Math.abs(brightness - 0.53d) / 0.53d));
            double visualScore = clamp01(
                    (sharpness * 0.48d)
                            + (contrast * 0.20d)
                            + (exposure * 0.20d)
                            + (Math.max(0d, 1d - (glare * 7d)) * 0.12d)
            );
            return new FrameMetrics(
                    sharpness,
                    brightness,
                    contrast,
                    glare,
                    visualScore,
                    signature
            );
        } catch (Throwable error) {
            Log.w(TAG, "Frame quality measurement failed for one frame", error);
            return FrameMetrics.unknown();
        } finally {
            edgeDeviation.release();
            edgeMean.release();
            grayDeviation.release();
            grayMean.release();
            tiny.release();
            highlights.release();
            laplacian.release();
            gray.release();
            rgba.release();
        }
    }

    private static double first(MatOfDouble values) {
        double[] data = values.toArray();
        return data.length == 0 ? 0d : data[0];
    }

    private static long averageHash(Mat tiny) {
        byte[] values = new byte[(int) tiny.total()];
        tiny.get(0, 0, values);
        double total = 0d;
        for (byte value : values) {
            total += value & 0xff;
        }
        double average = values.length == 0 ? 0d : total / values.length;
        long signature = 0L;
        int count = Math.min(64, values.length);
        for (int index = 0; index < count; index++) {
            if ((values[index] & 0xff) >= average) {
                signature |= 1L << index;
            }
        }
        return signature;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || value <= 0d) {
            return 0d;
        }
        return Math.min(1d, value);
    }

    static final class FrameMetrics {
        final double sharpness;
        final double brightness;
        final double contrast;
        final double glareRatio;
        final double visualScore;
        final long signature;

        FrameMetrics(
                double sharpness,
                double brightness,
                double contrast,
                double glareRatio,
                double visualScore,
                long signature
        ) {
            this.sharpness = clamp01(sharpness);
            this.brightness = clamp01(brightness);
            this.contrast = clamp01(contrast);
            this.glareRatio = clamp01(glareRatio);
            this.visualScore = clamp01(visualScore);
            this.signature = signature;
        }

        static FrameMetrics unknown() {
            return new FrameMetrics(0.45d, 0.5d, 0.45d, 0d, 0.45d, 0L);
        }
    }
}
