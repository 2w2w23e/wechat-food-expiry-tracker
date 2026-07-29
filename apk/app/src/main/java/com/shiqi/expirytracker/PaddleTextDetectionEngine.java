package com.shiqi.expirytracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

final class PaddleTextDetectionEngine implements AutoCloseable {
    private static final String TAG = "ShiqiPaddleTextDet";
    private static final String MODEL_ASSET = "PP-OCRv6_small_det.onnx";
    private static final int MAX_INPUT_SIDE = 1280;

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

            return DbTextPostProcessor.process(probability, maxRegions);
        } catch (Throwable error) {
            Log.w(TAG, "PP-OCRv6 text detection failed", error);
            return Collections.emptyList();
        } finally {
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
        private final double[] quad;

        TextRegion(double left, double top, double right, double bottom, double confidence) {
            this(
                    left,
                    top,
                    right,
                    bottom,
                    confidence,
                    new double[] {
                            left, top,
                            right, top,
                            right, bottom,
                            left, bottom
                    }
            );
        }

        TextRegion(
                double left,
                double top,
                double right,
                double bottom,
                double confidence,
                double[] quad
        ) {
            this.left = RecognitionEvidence.clamp01(left);
            this.top = RecognitionEvidence.clamp01(top);
            this.right = RecognitionEvidence.clamp01(right);
            this.bottom = RecognitionEvidence.clamp01(bottom);
            this.confidence = RecognitionEvidence.clamp01(confidence);
            this.quad = normalizeQuad(quad);
        }

        boolean hasQuadrilateral() {
            return quad.length == 8;
        }

        double[] quadrilateral() {
            return quad.clone();
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
            double compactLine = Math.max(0d, Math.min(1d, (0.025d - height) / 0.018d));
            double textLineShape = Math.max(
                    0d,
                    1d - (Math.abs(Math.min(aspect, 14d) - 6d) / 9d)
            );
            return confidence
                    + (compactLine * 0.08d)
                    + (textLineShape * 0.14d);
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

        private static double[] normalizeQuad(double[] source) {
            if (source == null || source.length != 8) {
                return new double[0];
            }
            double[] normalized = new double[8];
            for (int index = 0; index < normalized.length; index++) {
                normalized[index] = RecognitionEvidence.clamp01(source[index]);
            }
            return normalized;
        }
    }
}
