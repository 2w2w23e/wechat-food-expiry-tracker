package com.shiqi.expirytracker;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DateOcrScanActivity extends ComponentActivity {
    static final String EXTRA_START_VIDEO_REPLAY = "com.shiqi.expirytracker.START_VIDEO_REPLAY";
    private static final int REQUEST_CAMERA_PERMISSION = 6201;
    private static final int REQUEST_VIDEO_REPLAY = 6202;
    private static final int MAX_RECENT_FRAMES = 6;
    private static final long ANALYZE_INTERVAL_MS = 450L;
    private static final long VIDEO_FRAME_INTERVAL_US = 450000L;
    private static final int VIDEO_MAX_FRAME_SIDE = 1280;

    private PreviewView previewView;
    private ImageView replayFrameView;
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
    private volatile boolean videoReplayActive;
    private boolean cameraBound;
    private Bitmap lastReplayFrame;

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
        showInputChoiceState();
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_START_VIDEO_REPLAY, false)) {
            startVideoReplayPicker();
        }
    }

    @Override
    protected void onDestroy() {
        stopVideoReplay();
        stopCamera();
        clearLastReplayFrame();
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

        replayFrameView = new ImageView(this);
        replayFrameView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        replayFrameView.setBackgroundColor(Color.BLACK);
        replayFrameView.setVisibility(View.GONE);
        root.addView(replayFrameView, new FrameLayout.LayoutParams(
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

        Button videoButton = overlayButton("视频", 62);
        videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startVideoReplayPicker();
            }
        });
        topActions.addView(videoButton, fixed(62, 42));

        Button cameraButton = overlayButton("相机", 62);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ensureCameraPermission();
            }
        });
        topActions.addView(cameraButton, fixed(62, 42));

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

    private void showInputChoiceState() {
        stopCamera();
        stopVideoReplay();
        resetRecognitionState();
        if (previewView != null) {
            previewView.setVisibility(View.VISIBLE);
        }
        if (replayFrameView != null) {
            replayFrameView.setVisibility(View.GONE);
        }
        statusText.setText("可选择视频模拟相机实时识别，或打开相机扫描包装文字");
        candidateText.setText("候选：等待选择输入源");
        rawText.setText("原始 OCR：暂无");
    }

    private void ensureCameraPermission() {
        stopVideoReplay();
        resetRecognitionState();
        if (previewView != null) {
            previewView.setVisibility(View.VISIBLE);
        }
        if (replayFrameView != null) {
            replayFrameView.setVisibility(View.GONE);
        }
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VIDEO_REPLAY) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            statusText.setText("未选择视频，可继续选择视频模拟或打开相机");
            return;
        }
        startVideoReplay(data.getData());
    }

    private void startVideoReplayPicker() {
        stopCamera();
        stopVideoReplay();
        resetRecognitionState();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_VIDEO_REPLAY);
        } catch (ActivityNotFoundException error) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("video/*");
            try {
                startActivityForResult(Intent.createChooser(fallback, "选择包装视频"), REQUEST_VIDEO_REPLAY);
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, "当前设备没有可用的视频选择器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startVideoReplay(final Uri uri) {
        if (uri == null || cameraExecutor == null) {
            statusText.setText("未选择视频，可继续选择视频模拟或打开相机");
            return;
        }

        stopCamera();
        stopVideoReplay();
        resetRecognitionState();
        videoReplayActive = true;
        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
        if (replayFrameView != null) {
            replayFrameView.setVisibility(View.VISIBLE);
        }
        statusText.setText("正在用视频模拟实时相机识别，候选仍需手动确认");

        cameraExecutor.execute(new Runnable() {
            @Override
            public void run() {
                replayVideoFrames(uri);
            }
        });
    }

    private void replayVideoFrames(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int analyzedFrames = 0;
        try {
            retriever.setDataSource(this, uri);
            long durationUs = videoDurationUs(retriever);
            if (durationUs <= 0) {
                durationUs = 30000000L;
            }

            for (long frameUs = 0; videoReplayActive && frameUs <= durationUs; frameUs += VIDEO_FRAME_INTERVAL_US) {
                Bitmap frame = retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame == null) {
                    continue;
                }
                Bitmap scaled = scaleReplayFrame(frame);
                if (scaled != frame) {
                    frame.recycle();
                }

                showReplayFrame(scaled);
                analyzeReplayFrame(scaled);
                analyzedFrames++;
                SystemClock.sleep(Math.max(120L, ANALYZE_INTERVAL_MS / 2L));
            }

            final int finalFrameCount = analyzedFrames;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateVideoReplayCompleteState(finalFrameCount);
                }
            });
        } catch (final Exception error) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusText.setText("视频模拟识别失败，请换一个本地视频或手动输入");
                    Toast.makeText(DateOcrScanActivity.this, "视频读取失败", Toast.LENGTH_SHORT).show();
                }
            });
        } finally {
            videoReplayActive = false;
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void analyzeReplayFrame(Bitmap bitmap) throws Exception {
        if (bitmap == null || recognizer == null) {
            return;
        }
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        com.google.mlkit.vision.text.Text visionText =
                Tasks.await(recognizer.process(image), 8, TimeUnit.SECONDS);
        handleOcrText(visionText == null ? "" : visionText.getText());
    }

    private long videoDurationUs(MediaMetadataRetriever retriever) {
        try {
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Long.parseLong(value) * 1000L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Bitmap scaleReplayFrame(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largest = Math.max(width, height);
        if (largest <= VIDEO_MAX_FRAME_SIDE) {
            return bitmap;
        }
        float scale = VIDEO_MAX_FRAME_SIDE / (float) largest;
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
    }

    private void showReplayFrame(final Bitmap bitmap) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (replayFrameView == null || bitmap == null) {
                    return;
                }
                clearLastReplayFrame();
                lastReplayFrame = bitmap;
                replayFrameView.setImageBitmap(bitmap);
            }
        });
    }

    private void updateVideoReplayCompleteState(int analyzedFrames) {
        DateOcrFrameVoter.VoteResult vote = latestVote;
        if (vote != null && vote.readyForUserConfirmation()) {
            statusText.setText("视频模拟完成，候选已稳定；确认前不会保存到食品列表");
        } else if (analyzedFrames > 0) {
            statusText.setText("视频模拟完成，但候选还不稳定，可换更清晰视频或手动输入");
        } else {
            statusText.setText("视频没有可用画面，请换一个本地视频或手动输入");
        }
    }

    private void stopVideoReplay() {
        videoReplayActive = false;
    }

    private void resetRecognitionState() {
        recentFrames.clear();
        latestVote = null;
        latestRawText = "";
        analysisInFlight = false;
        lastAnalyzeAt = 0L;
        if (useButton != null) {
            useButton.setEnabled(false);
            useButton.setAlpha(0.45f);
        }
    }

    private void clearLastReplayFrame() {
        if (lastReplayFrame != null) {
            lastReplayFrame.recycle();
            lastReplayFrame = null;
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
