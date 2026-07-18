package com.shiqi.expirytracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

final class PaddleTextDetectionEngine implements AutoCloseable {
    private static final String TAG = "ShiqiPaddleTextDet";
    private static final String MODEL_ASSET = "PP-OCRv6_small_det.onnx";
    private static final int MAX_INPUT_SIDE = 1280;
    private static final float PIXEL_THRESHOLD = 0.20f;
    private static final double BOX_THRESHOLD = 0.42d;

    private final Context applicationContext;
    private OrtEnvironment environment;
    private OrtSession session;
    private String inputName;
    private boolean unavailable;
    private boolean closed;

    PaddleTextDetectionEngine(Context context) {
        applicationContext = context.getApplicationContext();
    }

    synchronized List<TextRegion> detect(Bitmap source, int maxRegions) {
        return detect(source, maxRegions, MAX_INPUT_SIDE);
    }

    synchronized List<TextRegion> detect(Bitmap source, int maxRegions, int requestedMaxInputSide) {
        if (source == null || source.isRecycled() || unavailable || closed
                || maxRegions <= 0 || !LowContrastTextPreprocessor.isAvailable()) {
            return Collections.emptyList();
        }

        Bitmap resized = null;
        Mat probability = new Mat();
        Mat binary = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        try {
            ensureInitialized();
            if (session == null) {
                return Collections.emptyList();
            }

            int[] target = targetSize(
                    source.getWidth(),
                    source.getHeight(),
                    Math.max(640, Math.min(MAX_INPUT_SIDE, requestedMaxInputSide))
            );
            resized = Bitmap.createScaledBitmap(source, target[0], target[1], true);
            FloatBuffer input = prepareInput(resized);
            long[] shape = new long[] {1L, 3L, target[1], target[0]};
            try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input, shape);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
                OnnxValue value = result.get(0);
                Object raw = value == null ? null : value.getValue();
                if (!(raw instanceof float[][][][])) {
                    return Collections.emptyList();
                }
                float[][][][] output = (float[][][][]) raw;
                if (output.length == 0 || output[0].length == 0
                        || output[0][0].length == 0 || output[0][0][0].length == 0) {
                    return Collections.emptyList();
                }
                float[][] map = output[0][0];
                int mapHeight = map.length;
                int mapWidth = map[0].length;
                float[] flattened = new float[mapHeight * mapWidth];
                for (int y = 0; y < mapHeight; y++) {
                    System.arraycopy(map[y], 0, flattened, y * mapWidth, mapWidth);
                }
                probability = new Mat(mapHeight, mapWidth, CvType.CV_32FC1);
                probability.put(0, 0, flattened);
            }

            Imgproc.threshold(probability, binary, PIXEL_THRESHOLD, 255d, Imgproc.THRESH_BINARY);
            binary.convertTo(binary, CvType.CV_8UC1);
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

            List<TextRegion> regions = new ArrayList<TextRegion>();
            for (MatOfPoint contour : contours) {
                Rect rect = Imgproc.boundingRect(contour);
                if (rect.width < 8 || rect.height < 3 || rect.area() < 18d
                        || rect.area() > probability.total() * 0.40d) {
                    continue;
                }
                double score = boxScore(probability, contour, rect);
                if (score < BOX_THRESHOLD) {
                    continue;
                }
                double left = rect.x / (double) probability.cols();
                double top = rect.y / (double) probability.rows();
                double right = (rect.x + rect.width) / (double) probability.cols();
                double bottom = (rect.y + rect.height) / (double) probability.rows();
                TextRegion candidate = new TextRegion(left, top, right, bottom, score);
                if (!candidate.isPlausibleTextLine()) {
                    continue;
                }
                regions.add(candidate);
            }

            Collections.sort(regions, new Comparator<TextRegion>() {
                @Override
                public int compare(TextRegion left, TextRegion right) {
                    return Double.compare(right.priority(), left.priority());
                }
            });
            List<TextRegion> selected = new ArrayList<TextRegion>();
            for (TextRegion region : regions) {
                if (isNearDuplicate(region, selected)) {
                    continue;
                }
                selected.add(region);
                if (selected.size() >= maxRegions) {
                    break;
                }
            }
            return selected;
        } catch (Throwable error) {
            Log.w(TAG, "PP-OCRv6 text detection failed", error);
            return Collections.emptyList();
        } finally {
            for (MatOfPoint contour : contours) {
                contour.release();
            }
            hierarchy.release();
            binary.release();
            probability.release();
            if (resized != null && resized != source && !resized.isRecycled()) {
                resized.recycle();
            }
        }
    }

    synchronized void warmUp() {
        if (session != null || unavailable || closed) {
            return;
        }
        try {
            ensureInitialized();
        } catch (Throwable error) {
            Log.w(TAG, "OCR model warm-up failed", error);
        }
    }

    private void ensureInitialized() throws Exception {
        if (session != null || unavailable || closed) {
            return;
        }
        try {
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            int inferenceThreads = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
            options.setIntraOpNumThreads(inferenceThreads);
            options.setInterOpNumThreads(1);
            options.setMemoryPatternOptimization(false);
            options.setCPUArenaAllocator(false);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            try {
                session = environment.createSession(
                        OnnxModelAsset.materialize(applicationContext, MODEL_ASSET).getAbsolutePath(),
                        options
                );
            } finally {
                options.close();
            }
            inputName = session.getInputNames().iterator().next();
        } catch (Throwable error) {
            unavailable = true;
            close();
            throw error;
        }
    }

    private int[] targetSize(int width, int height, int maxInputSide) {
        double scale = Math.min(1d, maxInputSide / (double) Math.max(width, height));
        int targetWidth = Math.max(32, (int) Math.ceil((width * scale) / 32d) * 32);
        int targetHeight = Math.max(32, (int) Math.ceil((height * scale) / 32d) * 32);
        return new int[] {targetWidth, targetHeight};
    }

    private FloatBuffer prepareInput(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int imageSize = width * height;
        FloatBuffer buffer = ByteBuffer.allocateDirect(imageSize * 3 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        int[] pixels = new int[imageSize];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                int destination = y * width + x;
                float blue = (pixel & 0xff) / 255f;
                float green = ((pixel >> 8) & 0xff) / 255f;
                float red = ((pixel >> 16) & 0xff) / 255f;
                buffer.put(destination, (blue - 0.485f) / 0.229f);
                buffer.put(imageSize + destination, (green - 0.456f) / 0.224f);
                buffer.put((imageSize * 2) + destination, (red - 0.406f) / 0.225f);
            }
        }
        buffer.position(0);
        return buffer;
    }

    private double boxScore(Mat probability, MatOfPoint contour, Rect rect) {
        Mat probabilityRoi = probability.submat(rect);
        Mat mask = Mat.zeros(rect.height, rect.width, CvType.CV_8UC1);
        Point[] sourcePoints = contour.toArray();
        Point[] shiftedPoints = new Point[sourcePoints.length];
        for (int index = 0; index < sourcePoints.length; index++) {
            shiftedPoints[index] = new Point(
                    sourcePoints[index].x - rect.x,
                    sourcePoints[index].y - rect.y
            );
        }
        MatOfPoint shifted = new MatOfPoint(shiftedPoints);
        try {
            Imgproc.fillPoly(mask, Collections.singletonList(shifted), new Scalar(255d));
            return Core.mean(probabilityRoi, mask).val[0];
        } finally {
            shifted.release();
            mask.release();
            probabilityRoi.release();
        }
    }

    private boolean isNearDuplicate(TextRegion candidate, List<TextRegion> selected) {
        for (TextRegion existing : selected) {
            if (candidate.iou(existing) >= 0.82d) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
            session = null;
        }
    }

    static final class TextRegion {
        final double left;
        final double top;
        final double right;
        final double bottom;
        final double confidence;

        TextRegion(double left, double top, double right, double bottom, double confidence) {
            this.left = RecognitionEvidence.clamp01(left);
            this.top = RecognitionEvidence.clamp01(top);
            this.right = RecognitionEvidence.clamp01(right);
            this.bottom = RecognitionEvidence.clamp01(bottom);
            this.confidence = RecognitionEvidence.clamp01(confidence);
        }

        RecognitionEvidence.NormalizedRect contextRect() {
            double width = Math.max(0.001d, right - left);
            double height = Math.max(0.001d, bottom - top);
            double leftPadding = Math.max(width, height * 5d);
            double rightPadding = Math.max(width * 0.35d, height * 3d);
            double verticalPadding = height * 0.45d;
            return new RecognitionEvidence.NormalizedRect(
                    left - leftPadding,
                    top - verticalPadding,
                    right + rightPadding,
                    bottom + verticalPadding
            );
        }

        RecognitionEvidence.NormalizedRect tightTextRect() {
            double width = Math.max(0.001d, right - left);
            double height = Math.max(0.001d, bottom - top);
            return new RecognitionEvidence.NormalizedRect(
                    left - (width * 0.08d),
                    top - (height * 0.35d),
                    right + (width * 0.08d),
                    bottom + (height * 0.35d)
            );
        }

        double aspectRatio() {
            return (right - left) / Math.max(0.001d, bottom - top);
        }

        double widthRatio() {
            return Math.max(0d, right - left);
        }

        boolean isPlausibleTextLine() {
            double width = right - left;
            double height = bottom - top;
            if (width <= 0d || height <= 0d || width * height > 0.35d) {
                return false;
            }
            double aspect = width / height;
            return aspect >= 1.15d || height >= width;
        }

        double priority() {
            double width = right - left;
            double height = Math.max(0.001d, bottom - top);
            double aspect = width / height;
            double centerY = (top + bottom) / 2d;
            double centerX = (left + right) / 2d;
            double compactLine = Math.max(0d, Math.min(1d, (0.025d - height) / 0.018d));
            double dateLineShape = Math.max(
                    0d,
                    1d - (Math.abs(Math.min(aspect, 18d) - 8d) / 10d)
            );
            return confidence
                    + (centerY * 0.18d)
                    + (centerX * 0.10d)
                    + (compactLine * 0.12d)
                    + (dateLineShape * 0.08d);
        }

        double iou(TextRegion other) {
            double overlapLeft = Math.max(left, other.left);
            double overlapTop = Math.max(top, other.top);
            double overlapRight = Math.min(right, other.right);
            double overlapBottom = Math.min(bottom, other.bottom);
            double overlap = Math.max(0d, overlapRight - overlapLeft)
                    * Math.max(0d, overlapBottom - overlapTop);
            double area = (right - left) * (bottom - top);
            double otherArea = (other.right - other.left) * (other.bottom - other.top);
            double union = area + otherArea - overlap;
            return union <= 0d ? 0d : overlap / union;
        }
    }
}
