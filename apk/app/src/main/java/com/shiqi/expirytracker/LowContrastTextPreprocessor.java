package com.shiqi.expirytracker;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
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
}
