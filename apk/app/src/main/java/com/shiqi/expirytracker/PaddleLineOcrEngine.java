package com.shiqi.expirytracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
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
        if (source == null || source.isRecycled() || unavailable || closed) {
            return "";
        }
        try {
            ensureInitialized();
            if (session == null || characters == null) {
                return "";
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
                    Object raw = value == null ? null : value.getValue();
                    if (!(raw instanceof float[][][])) {
                        return "";
                    }
                    return decode((float[][][]) raw);
                }
            } finally {
                if (resized != source) {
                    resized.recycle();
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "PP-OCRv6 line recognition failed", error);
            return "";
        }
    }

    private void ensureInitialized() throws Exception {
        if (session != null || unavailable || closed) {
            return;
        }
        try {
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            try {
                session = environment.createSession(readAsset(MODEL_ASSET), options);
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

    private byte[] readAsset(String name) throws Exception {
        InputStream input = applicationContext.getAssets().open(name);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
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

    private String decode(float[][][] output) {
        if (output.length == 0 || output[0] == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        int previousIndex = -1;
        float scoreTotal = 0f;
        int scoreCount = 0;
        for (float[] timestep : output[0]) {
            if (timestep == null || timestep.length == 0) {
                continue;
            }
            int bestIndex = 0;
            float bestScore = timestep[0];
            for (int index = 1; index < timestep.length; index++) {
                if (timestep[index] > bestScore) {
                    bestScore = timestep[index];
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
        if (scoreCount == 0 || scoreTotal / scoreCount < 0.42f) {
            return "";
        }
        return RecognitionTextCleaner.cleanForPackagingOcr(text.toString());
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
