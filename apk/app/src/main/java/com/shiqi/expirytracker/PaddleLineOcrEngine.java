package com.shiqi.expirytracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

final class PaddleLineOcrEngine implements AutoCloseable {
    private static final String TAG = "ShiqiPaddleLineOcr";
    private static final String MODEL_ASSET = "PP-OCRv6_rec_small.onnx";
    private static final int MODEL_HEIGHT = 48;
    private static final int MIN_MODEL_WIDTH = 320;
    private static final int MAX_MODEL_WIDTH = 1600;
    private static final int MAX_BATCH_MODEL_WIDTH = 800;

    private final Context applicationContext;
    private OrtEnvironment environment;
    private OrtSession session;
    private String inputName;
    private String[] characters;
    private boolean unavailable;
    private boolean closed;

    PaddleLineOcrEngine(Context context) {
        applicationContext = context.getApplicationContext();
    }

    synchronized String recognize(Bitmap source) {
        return recognizeResult(source).text;
    }

    synchronized LineResult recognizeResult(Bitmap source) {
        return recognizeResult(source, 0.42f);
    }

    synchronized LineResult recognizeResult(Bitmap source, float minimumConfidence) {
        if (source == null || source.isRecycled() || unavailable || closed) {
            return LineResult.empty();
        }
        try {
            ensureInitialized();
            if (session == null || characters == null) {
                return LineResult.empty();
            }
            int resizedWidth = Math.max(1, Math.round(
                    source.getWidth() * (MODEL_HEIGHT / (float) source.getHeight())
            ));
            int modelWidth = Math.max(MIN_MODEL_WIDTH, Math.min(MAX_MODEL_WIDTH, resizedWidth));
            resizedWidth = Math.min(resizedWidth, modelWidth);
            Bitmap resized = Bitmap.createScaledBitmap(source, resizedWidth, MODEL_HEIGHT, true);
            try {
                FloatBuffer input = prepareInput(resized, modelWidth);
                long[] shape = new long[] {1L, 3L, MODEL_HEIGHT, modelWidth};
                try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input, shape);
                     OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
                    OnnxValue value = result.get(0);
                    if (!(value instanceof OnnxTensor)) {
                        return LineResult.empty();
                    }
                    return decodeTensor(
                            (OnnxTensor) value,
                            0,
                            Math.max(0f, Math.min(1f, minimumConfidence))
                    );
                }
            } finally {
                if (resized != source) {
                    resized.recycle();
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "PP-OCRv6 line recognition failed", error);
            return LineResult.empty();
        }
    }

    synchronized List<LineResult> recognizeResults(
            List<Bitmap> sources,
            float minimumConfidence
    ) {
        if (sources == null || sources.isEmpty() || unavailable || closed) {
            return Collections.emptyList();
        }
        List<LineResult> emptyResults = new ArrayList<LineResult>();
        for (int index = 0; index < sources.size(); index++) {
            emptyResults.add(LineResult.empty());
        }
        List<Bitmap> resized = new ArrayList<Bitmap>();
        try {
            ensureInitialized();
            if (session == null || characters == null) {
                return emptyResults;
            }

            int modelWidth = MIN_MODEL_WIDTH;
            for (Bitmap source : sources) {
                if (source == null || source.isRecycled()) {
                    resized.add(null);
                    continue;
                }
                int resizedWidth = Math.max(1, Math.round(
                        source.getWidth() * (MODEL_HEIGHT / (float) source.getHeight())
                ));
                resizedWidth = Math.min(MAX_BATCH_MODEL_WIDTH, resizedWidth);
                modelWidth = Math.max(modelWidth, resizedWidth);
                resized.add(Bitmap.createScaledBitmap(source, resizedWidth, MODEL_HEIGHT, true));
            }

            FloatBuffer input = prepareBatchInput(resized, modelWidth);
            long[] shape = new long[] {sources.size(), 3L, MODEL_HEIGHT, modelWidth};
            try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input, shape);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
                OnnxValue value = result.get(0);
                if (!(value instanceof OnnxTensor)) {
                    return emptyResults;
                }
                OnnxTensor output = (OnnxTensor) value;
                List<LineResult> decoded = new ArrayList<LineResult>();
                float threshold = Math.max(0f, Math.min(1f, minimumConfidence));
                for (int index = 0; index < sources.size(); index++) {
                    decoded.add(decodeTensor(output, index, threshold));
                }
                return decoded;
            }
        } catch (Throwable error) {
            Log.w(TAG, "PP-OCRv6 batched line recognition failed", error);
            return emptyResults;
        } finally {
            for (Bitmap bitmap : resized) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
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
            String characterMetadata = session.getMetadata()
                    .getCustomMetadataValue("character")
                    .orElse("");
            String[] baseCharacters = characterMetadata.split("\\r?\\n");
            characters = new String[baseCharacters.length + 2];
            characters[0] = "";
            System.arraycopy(baseCharacters, 0, characters, 1, baseCharacters.length);
            characters[characters.length - 1] = " ";
        } catch (Throwable error) {
            unavailable = true;
            close();
            throw error;
        }
    }

    private FloatBuffer prepareInput(Bitmap bitmap, int modelWidth) {
        int imageSize = MODEL_HEIGHT * modelWidth;
        FloatBuffer buffer = ByteBuffer.allocateDirect(imageSize * 3 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        for (int index = 0; index < imageSize * 3; index++) {
            buffer.put(0f);
        }
        int width = bitmap.getWidth();
        int[] pixels = new int[width * MODEL_HEIGHT];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, MODEL_HEIGHT);
        for (int y = 0; y < MODEL_HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                int destination = y * modelWidth + x;
                buffer.put(destination, (((pixel) & 0xff) / 127.5f) - 1f);
                buffer.put(imageSize + destination, (((pixel >> 8) & 0xff) / 127.5f) - 1f);
                buffer.put((imageSize * 2) + destination, (((pixel >> 16) & 0xff) / 127.5f) - 1f);
            }
        }
        buffer.position(0);
        return buffer;
    }

    private FloatBuffer prepareBatchInput(List<Bitmap> bitmaps, int modelWidth) {
        int imageSize = MODEL_HEIGHT * modelWidth;
        int batchSize = bitmaps.size();
        FloatBuffer buffer = ByteBuffer.allocateDirect(batchSize * imageSize * 3 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        for (int index = 0; index < batchSize * imageSize * 3; index++) {
            buffer.put(0f);
        }
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            Bitmap bitmap = bitmaps.get(batchIndex);
            if (bitmap == null || bitmap.isRecycled()) {
                continue;
            }
            int width = bitmap.getWidth();
            int[] pixels = new int[width * MODEL_HEIGHT];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, MODEL_HEIGHT);
            int batchOffset = batchIndex * imageSize * 3;
            for (int y = 0; y < MODEL_HEIGHT; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    int destination = batchOffset + (y * modelWidth) + x;
                    buffer.put(destination, ((pixel & 0xff) / 127.5f) - 1f);
                    buffer.put(destination + imageSize, (((pixel >> 8) & 0xff) / 127.5f) - 1f);
                    buffer.put(destination + (imageSize * 2), (((pixel >> 16) & 0xff) / 127.5f) - 1f);
                }
            }
        }
        buffer.position(0);
        return buffer;
    }

    private LineResult decodeTensor(OnnxTensor tensor, int batchIndex, float minimumConfidence) {
        long[] shape = tensor.getInfo().getShape();
        if (shape.length != 3 || batchIndex < 0 || batchIndex >= shape[0]
                || shape[1] <= 0L || shape[2] <= 0L
                || shape[1] > Integer.MAX_VALUE || shape[2] > Integer.MAX_VALUE) {
            return LineResult.empty();
        }
        int timeSteps = (int) shape[1];
        int classCount = (int) shape[2];
        FloatBuffer output = tensor.getFloatBuffer();
        int batchOffset = batchIndex * timeSteps * classCount;
        StringBuilder text = new StringBuilder();
        int previousIndex = -1;
        float scoreTotal = 0f;
        int scoreCount = 0;
        for (int step = 0; step < timeSteps; step++) {
            int offset = batchOffset + (step * classCount);
            int bestIndex = 0;
            float bestScore = output.get(offset);
            for (int index = 1; index < classCount; index++) {
                float score = output.get(offset + index);
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = index;
                }
            }
            if (bestIndex != 0 && bestIndex != previousIndex && bestIndex < characters.length) {
                text.append(characters[bestIndex]);
                scoreTotal += bestScore;
                scoreCount++;
            }
            previousIndex = bestIndex;
        }
        if (scoreCount == 0 || scoreTotal / scoreCount < minimumConfidence) {
            return LineResult.empty();
        }
        String cleaned = RecognitionTextCleaner.cleanForPackagingOcr(text.toString());
        return cleaned.length() == 0
                ? LineResult.empty()
                : new LineResult(cleaned, scoreTotal / scoreCount);
    }

    static final class LineResult {
        final String text;
        final double confidence;

        LineResult(String text, double confidence) {
            this.text = FoodItem.cleanText(text);
            this.confidence = Math.max(0d, Math.min(1d, confidence));
        }

        static LineResult empty() {
            return new LineResult("", 0d);
        }
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
}
