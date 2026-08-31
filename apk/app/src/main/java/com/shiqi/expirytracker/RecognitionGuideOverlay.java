package com.shiqi.expirytracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecognitionGuideOverlay extends View {
    static final int FOCUS_NONE = 0;
    static final int FOCUS_RUNNING = 1;
    static final int FOCUS_SUCCESS = 2;
    static final int FOCUS_FAILED = 3;
    static final int FOCUS_UNSUPPORTED = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<RecognitionEvidence.Region> evidenceRegions = Collections.emptyList();
    private int evidenceImageWidth;
    private int evidenceImageHeight;
    private List<PaddleTextDetectionEngine.TextRegion> liveTextRegions = Collections.emptyList();
    private int liveImageWidth;
    private int liveImageHeight;
    private long liveVisibleUntilMs;
    private float focusX;
    private float focusY;
    private int focusState;
    private long focusVisibleUntilMs;

    RecognitionGuideOverlay(Context context) {
        super(context);
    }

    void showEvidence(List<RecognitionEvidence.Region> regions, int imageWidth, int imageHeight) {
        evidenceRegions = regions == null
                ? Collections.<RecognitionEvidence.Region>emptyList()
                : new ArrayList<RecognitionEvidence.Region>(regions);
        evidenceImageWidth = imageWidth;
        evidenceImageHeight = imageHeight;
        invalidate();
    }

    void showLiveRegions(
            List<PaddleTextDetectionEngine.TextRegion> regions,
            int imageWidth,
            int imageHeight
    ) {
        liveTextRegions = regions == null
                ? Collections.<PaddleTextDetectionEngine.TextRegion>emptyList()
                : new ArrayList<PaddleTextDetectionEngine.TextRegion>(regions);
        liveImageWidth = imageWidth;
        liveImageHeight = imageHeight;
        liveVisibleUntilMs = liveTextRegions.isEmpty()
                ? 0L
                : SystemClock.uptimeMillis() + 1200L;
        invalidate();
        if (liveVisibleUntilMs > 0L) {
            postInvalidateDelayed(1250L);
        }
    }

    void showFocusPoint(float x, float y, int state) {
        focusX = x;
        focusY = y;
        focusState = state;
        focusVisibleUntilMs = SystemClock.uptimeMillis()
                + (state == FOCUS_RUNNING ? 2600L : 1400L);
        invalidate();
        postInvalidateDelayed(state == FOCUS_RUNNING ? 2700L : 1500L);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (!evidenceRegions.isEmpty() && evidenceImageWidth > 0 && evidenceImageHeight > 0) {
            drawEvidence(canvas, width, height);
            return;
        }
        RectF frame = new RectF(
                width * 0.07f,
                height * 0.15f,
                width * 0.93f,
                height * 0.58f
        );

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(86, 0, 0, 0));
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.bottom, width, height, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom, paint);
        canvas.drawRect(frame.right, frame.top, width, frame.bottom, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(178, 255, 255, 255));
        canvas.drawRoundRect(frame, dp(10), dp(10), paint);

        paint.setStrokeWidth(dp(4));
        paint.setColor(Color.rgb(126, 231, 164));
        float corner = dp(30);
        drawCorner(canvas, frame.left, frame.top, corner, true, true);
        drawCorner(canvas, frame.right, frame.top, corner, false, true);
        drawCorner(canvas, frame.left, frame.bottom, corner, true, false);
        drawCorner(canvas, frame.right, frame.bottom, corner, false, false);

        drawLiveTextRegions(canvas, width, height);
        drawFocusPoint(canvas);
    }

    private void drawLiveTextRegions(Canvas canvas, int width, int height) {
        if (liveTextRegions.isEmpty()
                || liveImageWidth <= 0
                || liveImageHeight <= 0
                || SystemClock.uptimeMillis() > liveVisibleUntilMs) {
            if (SystemClock.uptimeMillis() > liveVisibleUntilMs) {
                liveTextRegions = Collections.emptyList();
            }
            return;
        }
        float scale = Math.max(
                width / (float) liveImageWidth,
                height / (float) liveImageHeight
        );
        float displayedWidth = liveImageWidth * scale;
        float displayedHeight = liveImageHeight * scale;
        float offsetX = (width - displayedWidth) / 2f;
        float offsetY = (height - displayedHeight) / 2f;
        RectF firstRect = null;
        for (PaddleTextDetectionEngine.TextRegion region : liveTextRegions) {
            RectF rect = new RectF(
                    offsetX + (float) (region.left * liveImageWidth * scale),
                    offsetY + (float) (region.top * liveImageHeight * scale),
                    offsetX + (float) (region.right * liveImageWidth * scale),
                    offsetY + (float) (region.bottom * liveImageHeight * scale)
            );
            if (firstRect == null) {
                firstRect = rect;
            }
            int color = region.confidence >= 0.62d
                    ? Color.rgb(91, 232, 145)
                    : Color.rgb(255, 204, 82);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(color);
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
        }
        if (firstRect == null) {
            return;
        }
        String label = "可能文字区 " + liveTextRegions.size();
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(sp(13));
        float labelWidth = paint.measureText(label) + dp(14);
        float labelLeft = Math.max(
                dp(8),
                Math.min(firstRect.left, width - labelWidth - dp(8))
        );
        float labelTop = Math.max(dp(112), firstRect.top - dp(28));
        RectF labelRect = new RectF(
                labelLeft,
                labelTop,
                labelLeft + labelWidth,
                labelTop + dp(27)
        );
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(224, 20, 28, 24));
        canvas.drawRoundRect(labelRect, dp(4), dp(4), paint);
        paint.setColor(Color.WHITE);
        canvas.drawText(label, labelRect.left + dp(7), labelRect.bottom - dp(7), paint);
    }

    private void drawFocusPoint(Canvas canvas) {
        if (focusState == FOCUS_NONE || SystemClock.uptimeMillis() > focusVisibleUntilMs) {
            focusState = FOCUS_NONE;
            return;
        }
        int color = focusState == FOCUS_SUCCESS
                ? Color.rgb(91, 232, 145)
                : focusState == FOCUS_FAILED
                ? Color.rgb(255, 118, 104)
                : focusState == FOCUS_UNSUPPORTED
                ? Color.rgb(190, 198, 193)
                : Color.rgb(255, 204, 82);
        float half = dp(28);
        RectF focusRect = new RectF(
                Math.max(dp(8), focusX - half),
                Math.max(dp(68), focusY - half),
                Math.min(getWidth() - dp(8), focusX + half),
                Math.min(getHeight() - dp(8), focusY + half)
        );
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(color);
        canvas.drawRoundRect(focusRect, dp(6), dp(6), paint);
        canvas.drawCircle(focusRect.centerX(), focusRect.centerY(), dp(3), paint);
    }

    private void drawEvidence(Canvas canvas, int width, int height) {
        float scale = Math.max(
                width / (float) evidenceImageWidth,
                height / (float) evidenceImageHeight
        );
        float displayedWidth = evidenceImageWidth * scale;
        float displayedHeight = evidenceImageHeight * scale;
        float offsetX = (width - displayedWidth) / 2f;
        float offsetY = (height - displayedHeight) / 2f;
        for (RecognitionEvidence.Region region : evidenceRegions) {
            RectF rect = new RectF(
                    offsetX + (float) (region.rect.left * evidenceImageWidth * scale),
                    offsetY + (float) (region.rect.top * evidenceImageHeight * scale),
                    offsetX + (float) (region.rect.right * evidenceImageWidth * scale),
                    offsetY + (float) (region.rect.bottom * evidenceImageHeight * scale)
            );
            int color = evidenceColor(region.field);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(color);
            canvas.drawRoundRect(rect, dp(5), dp(5), paint);

            String label = fieldLabel(region.field) + " · "
                    + RecognitionEvidence.confidenceLabel(region.confidence);
            paint.setTextSize(sp(14));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            float labelWidth = paint.measureText(label) + dp(12);
            float visibleLabelWidth = Math.min(labelWidth, width - dp(12));
            float labelLeft = Math.max(
                    dp(6),
                    Math.min(rect.left, width - dp(6) - visibleLabelWidth)
            );
            float labelTop = Math.max(dp(58), rect.top - dp(25));
            RectF labelRect = new RectF(
                    labelLeft,
                    labelTop,
                    labelLeft + visibleLabelWidth,
                    labelTop + dp(30)
            );
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(230, 20, 28, 24));
            canvas.drawRoundRect(labelRect, dp(4), dp(4), paint);
            paint.setColor(Color.WHITE);
            canvas.drawText(label, labelRect.left + dp(6), labelRect.bottom - dp(8), paint);
        }
    }

    private int evidenceColor(String field) {
        if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(field)) {
            return Color.rgb(91, 232, 145);
        }
        if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(field)) {
            return Color.rgb(255, 204, 82);
        }
        if (RecognitionEvidence.FIELD_EXPIRY_DATE.equals(field)) {
            return Color.rgb(103, 183, 255);
        }
        if (RecognitionEvidence.FIELD_TEXT_REGION.equals(field)) {
            return Color.rgb(99, 148, 255);
        }
        return Color.rgb(96, 221, 224);
    }

    private String fieldLabel(String field) {
        if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(field)) {
            return "商品名";
        }
        if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(field)) {
            return "生产日期";
        }
        if (RecognitionEvidence.FIELD_EXPIRY_DATE.equals(field)) {
            return "有效期";
        }
        if (RecognitionEvidence.FIELD_BARCODE.equals(field)) {
            return "条码";
        }
        return "包装文字";
    }

    private void drawCorner(
            Canvas canvas,
            float x,
            float y,
            float length,
            boolean left,
            boolean top
    ) {
        float horizontalEnd = left ? x + length : x - length;
        float verticalEnd = top ? y + length : y - length;
        canvas.drawLine(x, y, horizontalEnd, y, paint);
        canvas.drawLine(x, y, x, verticalEnd, paint);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
