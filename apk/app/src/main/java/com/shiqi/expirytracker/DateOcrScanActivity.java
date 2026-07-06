package com.shiqi.expirytracker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DateOcrScanActivity extends ComponentActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 6201;
    private static final int MAX_RECENT_FRAMES = 6;
    private static final long ANALYZE_INTERVAL_MS = 450L;

    private PreviewView previewView;
    private TextView statusText;
    private TextView candidateText;
    private TextView rawText;
    private Button useButton;
    private ExecutorService cameraExecutor;
    private TextRecognizer recognizer;
    private ProcessCameraProvider cameraProvider;
    private final List<DateOcrParser.Result> recentFrames = new ArrayList<DateOcrParser.Result>();
    private DateOcrFrameVoter.VoteResult latestVote;
    private String latestRawText = "";
    private long lastAnalyzeAt;
    private volatile boolean analysisInFlight;
    private boolean cameraBound;

    private final Executor mainExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            runOnUiThread(command);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraExecutor = Executors.newSingleThreadExecutor();
        recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());

        buildScreen();
        ensureCameraPermission();
    }

    @Override
    protected void onDestroy() {
        stopCamera();
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
        super.onDestroy();
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(new DateGuideOverlay(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(dp(18), dp(14), dp(18), dp(12));
        topPanel.setBackgroundColor(Color.argb(170, 0, 0, 0));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        topParams.gravity = Gravity.TOP;
        root.addView(topPanel, topParams);

        LinearLayout topActions = new LinearLayout(this);
        topActions.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.addView(topActions, matchWrap());

        Button closeButton = overlayButton("返回", 76);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        topActions.addView(closeButton, fixed(76, 42));

        TextView title = new TextView(this);
        title.setText("\u8bc6\u522b\u5305\u88c5\u6587\u5b57");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        topActions.addView(title, weightWrap(1));

        TextView spacer = new TextView(this);
        topActions.addView(spacer, fixed(76, 42));

        statusText = new TextView(this);
        statusText.setText("把生产日期、喷码或保质期放入框内，缓慢移动包装");
        statusText.setTextColor(Color.rgb(223, 240, 228));
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(10), 0, 0);
        topPanel.addView(statusText, matchWrap());

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(dp(18), dp(12), dp(18), dp(22));
        bottomPanel.setBackgroundColor(Color.argb(178, 0, 0, 0));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bottomParams.gravity = Gravity.BOTTOM;
        root.addView(bottomPanel, bottomParams);

        candidateText = new TextView(this);
        candidateText.setText("候选：等待稳定识别");
        candidateText.setTextColor(Color.WHITE);
        candidateText.setTextSize(14);
        candidateText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        candidateText.setMaxLines(5);
        bottomPanel.addView(candidateText, matchWrap());

        rawText = new TextView(this);
        rawText.setText("原始 OCR：暂无");
        rawText.setTextColor(Color.rgb(218, 226, 218));
        rawText.setTextSize(12);
        rawText.setMaxLines(3);
        rawText.setPadding(0, dp(6), 0, dp(10));
        bottomPanel.addView(rawText, matchWrap());

        LinearLayout bottomActions = new LinearLayout(this);
        bottomActions.setGravity(Gravity.CENTER_VERTICAL);
        bottomPanel.addView(bottomActions, fixedHeight(52));

        Button manualButton = overlayButton("手动输入", 104);
        manualButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        bottomActions.addView(manualButton, fixed(104, 48));

        TextView buttonSpacer = new TextView(this);
        bottomActions.addView(buttonSpacer, new LinearLayout.LayoutParams(0, dp(1), 1));

        useButton = overlayButton("使用候选", 112);
        useButton.setEnabled(false);
        useButton.setAlpha(0.45f);
        useButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishWithCandidate();
            }
        });
        bottomActions.addView(useButton, fixed(112, 48));

        setContentView(root);
    }

    private void ensureCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            return;
        }
        requestPermissions(new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA_PERMISSION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            statusText.setText("未获得相机权限，可以返回后手动新增食品");
            candidateText.setText("候选：相机权限未开启");
            useButton.setEnabled(false);
            useButton.setAlpha(0.45f);
        }
    }

    private void startCamera() {
        if (previewView == null || cameraExecutor == null) {
            return;
        }

        final ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = providerFuture.get();
                    if (!hasBackCamera(cameraProvider)) {
                        showCameraUnavailable("\u672a\u627e\u5230\u53ef\u7528\u540e\u7f6e\u76f8\u673a\uff0c\u53ef\u4ee5\u8fd4\u56de\u540e\u624b\u52a8\u65b0\u589e\u98df\u54c1");
                        return;
                    }
                    bindCameraUseCases(cameraProvider);
                    cameraBound = true;
                    statusText.setText("正在识别，尽量让文字占满框内并保持清晰");
                } catch (Exception error) {
                    showCameraUnavailable("\u76f8\u673a\u542f\u52a8\u5931\u8d25\uff0c\u53ef\u4ee5\u8fd4\u56de\u540e\u624b\u52a8\u65b0\u589e\u98df\u54c1");
                    Toast.makeText(DateOcrScanActivity.this, "相机启动失败", Toast.LENGTH_SHORT).show();
                }
            }
        }, mainExecutor);
    }

    private boolean hasBackCamera(ProcessCameraProvider provider) {
        try {
            return provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
        } catch (Exception error) {
            return false;
        }
    }

    private void showCameraUnavailable(String message) {
        cameraBound = false;
        statusText.setText(message);
        candidateText.setText("\u5019\u9009\uff1a\u76f8\u673a\u4e0d\u53ef\u7528\uff0c\u8bf7\u624b\u52a8\u8f93\u5165");
        rawText.setText("\u539f\u59cb OCR\uff1a\u6682\u65e0");
        useButton.setEnabled(false);
        useButton.setAlpha(0.45f);
        stopCamera();
    }

    private void bindCameraUseCases(ProcessCameraProvider provider) {
        provider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(ImageProxy imageProxy) {
                analyzeImage(imageProxy);
            }
        });

        provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
        );
    }

    private void stopCamera() {
        cameraBound = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        analysisInFlight = false;
    }

    private void analyzeImage(final ImageProxy imageProxy) {
        long now = SystemClock.uptimeMillis();
        if (!cameraBound || analysisInFlight || now - lastAnalyzeAt < ANALYZE_INTERVAL_MS) {
            imageProxy.close();
            return;
        }
        lastAnalyzeAt = now;
        analysisInFlight = true;

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null || recognizer == null) {
            analysisInFlight = false;
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );
        recognizer.process(inputImage)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<com.google.mlkit.vision.text.Text>() {
                    @Override
                    public void onSuccess(com.google.mlkit.vision.text.Text visionText) {
                        handleOcrText(visionText == null ? "" : visionText.getText());
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText("本帧识别失败，继续对准包装文字");
                            }
                        });
                    }
                })
                .addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<com.google.mlkit.vision.text.Text>() {
                    @Override
                    public void onComplete(com.google.android.gms.tasks.Task<com.google.mlkit.vision.text.Text> task) {
                        analysisInFlight = false;
                        imageProxy.close();
                    }
                });
    }

    private void handleOcrText(String text) {
        final String cleanedText = FoodItem.cleanText(text);
        DateOcrParser.Result parsed = DateOcrParser.parse(cleanedText);
        if (cleanedText.length() > 0 || parsed.hasAnyCandidate()) {
            recentFrames.add(parsed);
            while (recentFrames.size() > MAX_RECENT_FRAMES) {
                recentFrames.remove(0);
            }
        }

        latestVote = DateOcrFrameVoter.vote(recentFrames);
        latestRawText = cleanedText;
        final DateOcrFrameVoter.VoteResult vote = latestVote;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateCandidateUi(vote, cleanedText);
            }
        });
    }

    private void updateCandidateUi(DateOcrFrameVoter.VoteResult vote, String raw) {
        candidateText.setText("候选：\n" + DateOcrResultPayload.summary(vote));
        rawText.setText("原始 OCR：" + snippet(raw, 120));

        boolean ready = vote != null && vote.readyForUserConfirmation();
        useButton.setEnabled(ready);
        useButton.setAlpha(ready ? 1f : 0.45f);
        if (ready) {
            statusText.setText("候选已稳定，确认前不会保存到食品列表");
        } else if (vote != null && vote.hasConflict) {
            statusText.setText("候选冲突，请继续对准日期区域或手动输入");
        } else if (raw != null && raw.length() > 0) {
            statusText.setText("已看到文字，继续保持清晰直到候选稳定");
        } else {
            statusText.setText("正在识别，尽量让日期文字占满框内");
        }
    }

    private void finishWithCandidate() {
        DateOcrFrameVoter.VoteResult vote = latestVote;
        if (vote == null || !vote.readyForUserConfirmation()) {
            Toast.makeText(this, "还没有稳定候选", Toast.LENGTH_SHORT).show();
            return;
        }

        FoodItem draft = DateOcrResultPayload.toDraft(vote);
        if (!DateOcrResultPayload.hasUsableDraft(draft)) {
            Toast.makeText(this, "候选内容不足，请继续扫描或手动输入", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent result = new Intent();
        result.putExtra(DateOcrResultPayload.EXTRA_PRODUCTION_DATE, draft.productionDate);
        result.putExtra(DateOcrResultPayload.EXTRA_EXPIRY_DATE, draft.expiryDate);
        result.putExtra(DateOcrResultPayload.EXTRA_EXPIRY_CALCULATED, "calculated".equals(draft.dateSource));
        if (draft.shelfLifeValue != null) {
            result.putExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_VALUE, draft.shelfLifeValue.intValue());
        }
        result.putExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_UNIT, draft.shelfLifeUnit);
        result.putExtra(DateOcrResultPayload.EXTRA_RAW_TEXT, latestRawText);
        result.putExtra(DateOcrResultPayload.EXTRA_SUMMARY, DateOcrResultPayload.summary(vote));
        setResult(RESULT_OK, result);
        finish();
    }

    private String snippet(String value, int maxLength) {
        String text = FoodItem.cleanText(value).replace('\n', ' ');
        if (text.length() == 0) {
            return "暂无";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private Button overlayButton(String text, int widthDp) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(widthDp <= 80 ? 13 : 14);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rounded(Color.argb(218, 63, 111, 83), dp(8), Color.argb(120, 255, 255, 255)));
        return button;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private LinearLayout.LayoutParams fixed(int widthDp, int heightDp) {
        return new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp));
    }

    private LinearLayout.LayoutParams fixedHeight(int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class DateGuideOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DateGuideOverlay(ComponentActivity context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            RectF frame = new RectF(
                    width * 0.08f,
                    height * 0.32f,
                    width * 0.92f,
                    height * 0.62f
            );

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(106, 0, 0, 0));
            canvas.drawRect(0, 0, width, frame.top, paint);
            canvas.drawRect(0, frame.bottom, width, height, paint);
            canvas.drawRect(0, frame.top, frame.left, frame.bottom, paint);
            canvas.drawRect(frame.right, frame.top, width, frame.bottom, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(176, 255, 255, 255));
            canvas.drawRoundRect(frame, dp(12), dp(12), paint);

            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.rgb(126, 231, 164));
            float corner = dp(32);
            drawCorner(canvas, frame.left, frame.top, corner, true, true);
            drawCorner(canvas, frame.right, frame.top, corner, false, true);
            drawCorner(canvas, frame.left, frame.bottom, corner, true, false);
            drawCorner(canvas, frame.right, frame.bottom, corner, false, false);
        }

        private void drawCorner(Canvas canvas, float x, float y, float length, boolean left, boolean top) {
            float horizontalEnd = left ? x + length : x - length;
            float verticalEnd = top ? y + length : y - length;
            canvas.drawLine(x, y, horizontalEnd, y, paint);
            canvas.drawLine(x, y, x, verticalEnd, paint);
        }
    }
}
