package com.shiqi.expirytracker;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

final class BarcodeImageDecoder {
    private static final float FRAME_LEFT = 0.08f;
    private static final float FRAME_TOP = 0.28f;
    private static final float FRAME_WIDTH = 0.84f;
    private static final float FRAME_HEIGHT = 0.38f;

    private static final Map<DecodeHintType, Object> FAST_HINTS = hints(false);
    private static final Map<DecodeHintType, Object> HARD_HINTS = hints(true);

    private BarcodeImageDecoder() {}

    static String decodeProductBarcode(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return "";
        }

        String decoded = decodeBitmap(bitmap);
        if (decoded.length() > 0) {
            return decoded;
        }

        Bitmap rotatedRight = rotateRight(bitmap);
        if (rotatedRight != bitmap) {
            try {
                decoded = decodeBitmap(rotatedRight);
                if (decoded.length() > 0) {
                    return decoded;
                }
            } finally {
                rotatedRight.recycle();
            }
        }

        Bitmap rotatedLeft = rotateLeft(bitmap);
        if (rotatedLeft != bitmap) {
            try {
                decoded = decodeBitmap(rotatedLeft);
                if (decoded.length() > 0) {
                    return decoded;
                }
            } finally {
                rotatedLeft.recycle();
            }
        }

        return "";
    }

    static String decodePreviewFrame(byte[] data, int width, int height) {
        if (data == null || width <= 0 || height <= 0) {
            return "";
        }

        byte[] rotated = rotateLuminanceClockwise(data, width, height);
        LuminanceSource portraitSource = new PlanarYUVLuminanceSource(
                rotated,
                height,
                width,
                0,
                0,
                height,
                width,
                false
        );

        String decoded = decodeSource(portraitSource, true);
        if (decoded.length() > 0) {
            return decoded;
        }

        LuminanceSource rawSource = new PlanarYUVLuminanceSource(
                data,
                width,
                height,
                0,
                0,
                width,
                height,
                false
        );
        return decodeSource(rawSource, false);
    }

    private static String decodeBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        return decodeSource(source, false);
    }

    private static String decodeSource(LuminanceSource source, boolean preferScanFrame) {
        if (preferScanFrame && source.isCropSupported()) {
            LuminanceSource cropped = cropScanFrame(source);
            String decoded = decodeLuminanceSource(cropped, FAST_HINTS);
            if (decoded.length() > 0) {
                return decoded;
            }

            decoded = decodeLuminanceSource(cropped, HARD_HINTS);
            if (decoded.length() > 0) {
                return decoded;
            }
        }

        String decoded = decodeLuminanceSource(source, FAST_HINTS);
        if (decoded.length() > 0) {
            return decoded;
        }
        return decodeLuminanceSource(source, HARD_HINTS);
    }

    private static LuminanceSource cropScanFrame(LuminanceSource source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int left = Math.max(0, Math.round(width * FRAME_LEFT));
        int top = Math.max(0, Math.round(height * FRAME_TOP));
        int cropWidth = Math.min(width - left, Math.round(width * FRAME_WIDTH));
        int cropHeight = Math.min(height - top, Math.round(height * FRAME_HEIGHT));
        return source.crop(left, top, cropWidth, cropHeight);
    }

    private static String decodeLuminanceSource(LuminanceSource source, Map<DecodeHintType, Object> hints) {
        String decoded = decodeBinaryBitmap(new BinaryBitmap(new GlobalHistogramBinarizer(source)), hints);
        if (decoded.length() > 0) {
            return decoded;
        }
        return decodeBinaryBitmap(new BinaryBitmap(new HybridBinarizer(source)), hints);
    }

    private static String decodeBinaryBitmap(BinaryBitmap binaryBitmap, Map<DecodeHintType, Object> hints) {
        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);
        try {
            Result result = reader.decodeWithState(binaryBitmap);
            return BarcodeUtils.extractProductCode(result == null ? "" : result.getText());
        } catch (ReaderException ignored) {
            return "";
        } finally {
            reader.reset();
        }
    }

    private static Map<DecodeHintType, Object> hints(boolean hard) {
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.ITF,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128,
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX
        ));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.ASSUME_GS1, Boolean.TRUE);
        if (hard) {
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
        }
        return hints;
    }

    private static byte[] rotateLuminanceClockwise(byte[] source, int width, int height) {
        byte[] rotated = new byte[width * height];
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                rotated[index++] = source[y * width + x];
            }
        }
        return rotated;
    }

    private static Bitmap rotateRight(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return bitmap;
        }

        Bitmap rotated = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rotated.setPixel(height - 1 - y, x, bitmap.getPixel(x, y));
            }
        }
        return rotated;
    }

    private static Bitmap rotateLeft(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return bitmap;
        }

        Bitmap rotated = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rotated.setPixel(y, width - 1 - x, bitmap.getPixel(x, y));
            }
        }
        return rotated;
    }
}
