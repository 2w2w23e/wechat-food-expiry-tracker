package com.shiqi.expirytracker;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DateOcrScanActivity extends ComponentActivity {
    static final String EXTRA_START_VIDEO_REPLAY = "com.shiqi.expirytracker.START_VIDEO_REPLAY";
    static final String EXTRA_QA_VIDEO_PATH = "com.shiqi.expirytracker.QA_VIDEO_PATH";
    static final String EXTRA_QA_IMAGE_PATH = "com.shiqi.expirytracker.QA_IMAGE_PATH";
    static final String EXTRA_DATE_ONLY_MODE = "com.shiqi.expirytracker.DATE_ONLY_MODE";
    static final String EXTRA_BARCODE_LOOKUP_ONLY =
            "com.shiqi.expirytracker.BARCODE_LOOKUP_ONLY";

    private static final int REQUEST_CAMERA_PERMISSION = 6201;
    private static final int REQUEST_VIDEO_REPLAY = 6202;
    private static final int REQUEST_IMAGE_RECOGNITION = 6203;
    private static final long CAMERA_CAPTURE_INTERVAL_MS = 66L;
    private static final int VIDEO_MAX_FRAME_SIDE = 1280;
    private static final int STILL_IMAGE_MAX_SIDE = 2048;
    private static final int STILL_IMAGE_MIN_SIDE = 1400;
    private PreviewView previewView;
    private ImageView replayFrameView;
    private TextView statusBadge;
    private TextView statusText;
    private TextView barcodeValue;
    private TextView productValue;
    private TextView productionDateValue;
    private TextView rawPreviewText;
    private LinearLayout productCandidateContainer;
    private HorizontalScrollView productCandidateScroll;
    private TextView keyframeTitle;
    private HorizontalScrollView keyframeScroll;
    private LinearLayout keyframeContainer;
    private LinearLayout resultSheet;
    private RecognitionGuideOverlay recognitionOverlay;
    private Button fillButton;
    private Button cameraSourceButton;
    private Button videoSourceButton;
    private Button imageSourceButton;
    private Button restartRecognitionButton;

    private ExecutorService cameraAnalyzerExecutor;
    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private TextRecognizer latinTextRecognizer;
    private BarcodeScanner barcodeScanner;
    private PaddleLineOcrEngine paddleLineOcrEngine;
    private PaddleTextDetectionEngine paddleTextDetectionEngine;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private final UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer();
    private final RecognitionFrameSelector frameSelector = new RecognitionFrameSelector(1600L);
    private UnifiedRecognitionStabilizer.Snapshot latestSnapshot = stabilizer.snapshot();
    private UnifiedRecognitionStabilizer.Snapshot bestSessionSnapshot;
    private BarcodeProductInfo latestProductInfo;
    private String latestProductBarcode = "";
    private String productLookupInFlightBarcode = "";
    private String productLookupError = "";
    private String latestRawText = "";
    private final StringBuilder videoTranscriptText = new StringBuilder();
    private volatile DateOcrParser.Result bestVideoDatePair;
    private volatile double bestVideoDatePairScore;
    private volatile double currentVideoFrameRatio;
    private String selectedPackagingName = "";
    private final List<SessionKeyframe> sessionKeyframes = new ArrayList<SessionKeyframe>();
    private Bitmap incompleteDateReviewFrame;
    private RecognitionEvidence.NormalizedRect incompleteDateReviewRect;
    private long incompleteDateReviewTimestampUs;
    private double incompleteDateReviewScore;
    private int focusedDateTailAttempts;
    private volatile long currentFrameTimestampUs;
    private volatile boolean appScreenRecordingDetected;
    private long nextFrameId;
    private boolean showingEvidenceFrame;
    private volatile int recognitionGeneration;
    private long lastAnalyzeAt;
    private volatile boolean analysisInFlight;
    private volatile boolean videoReplayActive;
    private volatile boolean barcodeLookupOnly;
    private volatile boolean dateOnlyMode;
    private volatile boolean cameraSimulationActive;
    private volatile boolean cameraSimulationScreenRecording;
    private volatile boolean longVideoProfile;
    private volatile boolean videoDetailFrame;
    private int analyzedFrameSequence;
    private volatile long recognitionFirstFrameAtMs;
    private int paddleVideoFramesUsed;
    private int paddleDetectionVideoFramesUsed;
    private int paddleFocusedLabelVideoFramesUsed;
    private int paddleCameraBandFramesUsed;
    private int paddleDetectionCameraFramesUsed;
    private int paddleDetectedLineCameraFramesUsed;
    private int videoAnalyzedFrames;
    private int videoExpectedFrames;
    private volatile boolean cameraBound;
    private volatile boolean liveCameraResultFrozen;
    private Bitmap lastReplayFrame;
    private final Object pendingCameraFrameLock = new Object();
    private Bitmap pendingCameraFrame;
    private long pendingCameraFrameTimestampUs;
    private int pendingCameraFrameGeneration;
    private boolean cameraRecognitionScheduled;

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
        window.setStatusBarColor(Color.rgb(17, 23, 19));
        window.setNavigationBarColor(Color.rgb(17, 23, 19));
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int systemUi = window.getDecorView().getSystemUiVisibility()
                & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUi &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(systemUi);

        Intent intent = getIntent();
        barcodeLookupOnly = intent != null
                && intent.getBooleanExtra(EXTRA_BARCODE_LOOKUP_ONLY, false);
        dateOnlyMode = intent != null
                && intent.getBooleanExtra(EXTRA_DATE_ONLY_MODE, false);

        cameraAnalyzerExecutor = Executors.newSingleThreadExecutor();
        cameraExecutor = Executors.newSingleThreadExecutor();
        textRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        latinTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_DATA_MATRIX
                )
                .enableAllPotentialBarcodes()
                .build());
        paddleLineOcrEngine = new PaddleLineOcrEngine(getApplicationContext());
        paddleTextDetectionEngine = new PaddleTextDetectionEngine(getApplicationContext());
        if (!barcodeLookupOnly) {
            cameraExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    paddleTextDetectionEngine.warmUp();
                    paddleLineOcrEngine.warmUp();
                }
            });
        }

        buildScreen();
        showInputChoiceState();

        String qaVideoPath = intent == null ? "" : FoodItem.cleanText(intent.getStringExtra(EXTRA_QA_VIDEO_PATH));
        String qaImagePath = intent == null ? "" : FoodItem.cleanText(intent.getStringExtra(EXTRA_QA_IMAGE_PATH));
        if (isDebuggable() && qaVideoPath.length() > 0) {
            startVideoReplayPath(qaVideoPath);
        } else if (isDebuggable() && qaImagePath.length() > 0) {
            startImageRecognitionPath(qaImagePath);
        } else if (isDebuggable()
                && intent != null
                && intent.getBooleanExtra(EXTRA_START_VIDEO_REPLAY, false)) {
            startVideoReplayPicker();
        } else {
            ensureCameraPermission();
        }
    }

    @Override
    protected void onDestroy() {
        recognitionGeneration++;
        stopVideoReplay();
        stopCamera();
        clearLastReplayFrame();
        clearSessionKeyframes();
        ExecutorService recognitionExecutor = cameraExecutor;
        cameraExecutor = null;
        ExecutorService analyzerExecutor = cameraAnalyzerExecutor;
        cameraAnalyzerExecutor = null;
        if (analyzerExecutor != null) {
            analyzerExecutor.shutdownNow();
        }
        clearPendingCameraFrame();
        if (recognitionExecutor != null) {
            recognitionExecutor.shutdownNow();
        }
        if (textRecognizer != null) {
            textRecognizer.close();
            textRecognizer = null;
        }
        if (latinTextRecognizer != null) {
            latinTextRecognizer.close();
            latinTextRecognizer = null;
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
            barcodeScanner = null;
        }
        final PaddleLineOcrEngine engineToClose = paddleLineOcrEngine;
        final PaddleTextDetectionEngine detectorToClose = paddleTextDetectionEngine;
        paddleLineOcrEngine = null;
        paddleTextDetectionEngine = null;
        if (engineToClose != null || detectorToClose != null) {
            Thread releaseThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (engineToClose != null) {
                        engineToClose.close();
                    }
                    if (detectorToClose != null) {
                        detectorToClose.close();
                    }
                }
            }, "shiqi-ocr-release");
            releaseThread.setDaemon(true);
            releaseThread.start();
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
        replayFrameView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        replayFrameView.setBackgroundColor(Color.BLACK);
        replayFrameView.setVisibility(View.GONE);
        root.addView(replayFrameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        recognitionOverlay = new RecognitionGuideOverlay(this);
        root.addView(recognitionOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.HORIZONTAL);
        topPanel.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        topPanel.setBackgroundColor(Color.argb(210, 20, 24, 22));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        topParams.gravity = Gravity.TOP;
        root.addView(topPanel, topParams);

        Button closeButton = overlayButton("返回", Color.rgb(52, 58, 55));
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        topPanel.addView(closeButton, fixed(64, 48));

        TextView title = new TextView(this);
        title.setText(dateOnlyMode ? "识别本批次日期" : "智能识别");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        topPanel.addView(title, weightWrap(1));

        statusBadge = new TextView(this);
        statusBadge.setText("待识别");
        statusBadge.setTextColor(Color.rgb(226, 246, 232));
        statusBadge.setTextSize(14);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusBadge.setBackground(rounded(Color.rgb(46, 91, 66), dp(8), 0));
        topPanel.addView(statusBadge, fixed(88, 36));

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(82, 91, 86));
        statusText.setTextSize(16);
        statusText.setMaxLines(3);
        statusText.setPadding(0, dp(1), 0, dp(2));

        LinearLayout bottomShell = new LinearLayout(this);
        bottomShell.setOrientation(LinearLayout.VERTICAL);
        bottomShell.setBackground(rounded(Color.rgb(249, 250, 250), dp(8), 0));
        resultSheet = bottomShell;

        ScrollView resultScroll = new ScrollView(this);
        resultScroll.setFillViewport(false);
        resultScroll.setVerticalScrollBarEnabled(true);

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(dp(14), dp(10), dp(14), dp(6));
        resultScroll.addView(bottomPanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        bottomShell.addView(resultScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        int desiredPanelDp = 440 + Math.round(Math.min(0.4f, fontScale - 1f) * 160f);
        int maxPanelHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.72f);
        int panelHeight = Math.min(dp(desiredPanelDp), maxPanelHeight);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                panelHeight
        );
        bottomParams.gravity = Gravity.BOTTOM;
        root.addView(bottomShell, bottomParams);

        LinearLayout resultHeader = new LinearLayout(this);
        resultHeader.setGravity(Gravity.CENTER_VERTICAL);
        bottomPanel.addView(resultHeader, matchWrap());

        TextView resultTitle = new TextView(this);
        resultTitle.setText("识别结果");
        resultTitle.setTextColor(Color.rgb(30, 35, 33));
        resultTitle.setTextSize(16);
        resultTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        resultHeader.addView(resultTitle, weightWrap(1));

        Button rawButton = plainButton("原文");
        rawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showRawTextDialog();
            }
        });
        resultHeader.addView(rawButton, fixed(64, 48));

        productValue = new TextView(this);
        productValue.setText(dateOnlyMode ? "正在寻找日期文字" : "正在寻找商品名");
        productValue.setTextColor(Color.rgb(24, 30, 27));
        productValue.setTextSize(18);
        productValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        productValue.setMaxLines(3);
        productValue.setPadding(0, dp(3), 0, dp(1));
        bottomPanel.addView(productValue, matchWrap());
        bottomPanel.addView(statusText, matchWrap());

        productCandidateScroll = new HorizontalScrollView(this);
        productCandidateScroll.setHorizontalScrollBarEnabled(false);
        productCandidateScroll.setFillViewport(false);
        productCandidateScroll.setVisibility(View.GONE);
        productCandidateContainer = new LinearLayout(this);
        productCandidateContainer.setOrientation(LinearLayout.HORIZONTAL);
        productCandidateScroll.addView(productCandidateContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        bottomPanel.addView(productCandidateScroll, fixedHeight(48));

        keyframeTitle = new TextView(this);
        keyframeTitle.setText("识别依据");
        keyframeTitle.setTextColor(Color.rgb(70, 82, 73));
        keyframeTitle.setTextSize(14);
        keyframeTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        keyframeTitle.setGravity(Gravity.CENTER_VERTICAL);
        keyframeTitle.setPadding(0, dp(5), 0, 0);
        bottomPanel.addView(keyframeTitle, fixedHeight(30));

        keyframeScroll = new HorizontalScrollView(this);
        keyframeScroll.setHorizontalScrollBarEnabled(false);
        keyframeScroll.setFillViewport(false);
        keyframeTitle.setVisibility(View.GONE);
        keyframeScroll.setVisibility(View.GONE);
        keyframeContainer = new LinearLayout(this);
        keyframeContainer.setOrientation(LinearLayout.HORIZONTAL);
        keyframeScroll.addView(keyframeContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        bottomPanel.addView(keyframeScroll, fixedHeight(94));

        barcodeValue = compactResultText();
        bottomPanel.addView(barcodeValue, matchWrap());

        productionDateValue = compactResultText();
        bottomPanel.addView(productionDateValue, matchWrap());

        rawPreviewText = new TextView(this);
        rawPreviewText.setTextColor(Color.rgb(86, 99, 88));
        rawPreviewText.setTextSize(14);
        rawPreviewText.setMaxLines(2);
        rawPreviewText.setPadding(0, dp(3), 0, dp(5));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(14), dp(6), dp(14), dp(12));

        cameraSourceButton = sourceButton("相机");
        cameraSourceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ensureCameraPermission();
            }
        });
        actionRow.addView(cameraSourceButton, withMargins(weightWrap(1), 0, 0, dp(6), 0));

        videoSourceButton = sourceButton("视频");
        videoSourceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startVideoReplayPicker();
            }
        });
        videoSourceButton.setVisibility(View.GONE);

        imageSourceButton = sourceButton("图片");
        imageSourceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startImagePicker();
            }
        });
        imageSourceButton.setVisibility(View.GONE);

        restartRecognitionButton = sourceButton("重新识别");
        restartRecognitionButton.setVisibility(View.GONE);
        restartRecognitionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showRecognitionSourceDialog();
            }
        });
        actionRow.addView(restartRecognitionButton, withMargins(weightWrap(1), 0, 0, dp(6), 0));

        fillButton = overlayButton("核对并填入", Color.rgb(38, 104, 76));
        fillButton.setEnabled(false);
        fillButton.setAlpha(0.45f);
        fillButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishWithCandidate();
            }
        });
        actionRow.addView(fillButton, weightWrap(2));
        bottomShell.addView(actionRow, fixedHeight(68));

        setContentView(root);
    }

    private TextView compactResultText() {
        TextView value = new TextView(this);
        value.setTextColor(Color.rgb(70, 82, 73));
        value.setTextSize(16);
        value.setMaxLines(5);
        value.setPadding(0, dp(2), 0, 0);
        return value;
    }

    private void showRecognitionSourceDialog() {
        ensureCameraPermission();
    }

    private void setRecognitionComplete(boolean complete) {
        if (cameraSourceButton == null || videoSourceButton == null
                || imageSourceButton == null || restartRecognitionButton == null) {
            return;
        }
        int sourceVisibility = complete ? View.GONE : View.VISIBLE;
        cameraSourceButton.setVisibility(sourceVisibility);
        videoSourceButton.setVisibility(View.GONE);
        imageSourceButton.setVisibility(View.GONE);
        restartRecognitionButton.setVisibility(complete ? View.VISIBLE : View.GONE);
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
            replayFrameView.setImageBitmap(null);
        }
        statusBadge.setText("待识别");
        statusText.setText(barcodeLookupOnly
                ? "正在准备相机，请让条码尽量占满识别框。"
                : dateOnlyMode
                ? "保持约 15–25 厘米，让日期小字清晰占满识别框，再轻点文字对焦。"
                : "正在准备相机，请让包装文字和喷码尽量占满识别框。");
        updateResultUi(stabilizer.snapshot());
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
            statusBadge.setText("无相机");
            statusText.setText("未获得相机权限，请在系统设置中允许相机后重试。");
            updateResultUi(stabilizer.snapshot());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            statusText.setText("未选择文件，可继续选择视频、图片或打开相机。");
            return;
        }
        if (requestCode == REQUEST_VIDEO_REPLAY) {
            startVideoReplay(data.getData());
        } else if (requestCode == REQUEST_IMAGE_RECOGNITION) {
            startImageRecognition(data.getData());
        }
    }

    private void startVideoReplayPicker() {
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

    private void startImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_IMAGE_RECOGNITION);
        } catch (ActivityNotFoundException error) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("image/*");
            try {
                startActivityForResult(Intent.createChooser(fallback, "选择包装图片"), REQUEST_IMAGE_RECOGNITION);
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, "当前设备没有可用的图片选择器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startVideoReplay(final Uri uri) {
        if (uri == null || cameraExecutor == null) {
            statusText.setText("未选择视频，可继续选择视频、图片或打开相机。");
            return;
        }
        cameraSimulationActive = false;
        cameraSimulationScreenRecording = false;
        prepareReplaySurface("视频样本");
        cameraExecutor.execute(new Runnable() {
            @Override
            public void run() {
                replayVideoFrames(uri, "");
            }
        });
    }

    private void startVideoReplayPath(final String path) {
        if (path.length() == 0 || cameraExecutor == null) {
            return;
        }
        prepareReplaySurface("相机模拟");
        cameraSimulationActive = true;
        cameraExecutor.execute(new Runnable() {
            @Override
            public void run() {
                replayVideoFrames(null, path);
            }
        });
    }

    private void prepareReplaySurface(String badge) {
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
        statusBadge.setText(badge);
        statusText.setText("正在分析中英文包装，条码、商品名和日期会自动合并。");
    }

    private void replayVideoFrames(Uri uri, String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int analyzedFrames = 0;
        int scannedFrames = 0;
        Bitmap selectedFrame = null;
        long selectedFrameUs = 0L;
        double selectedFrameRatio = 0d;
        double selectedFrameScore = -1d;
        boolean selectedDetailFrame = false;
        final int replayGeneration = recognitionGeneration;
        try {
            if (path != null && path.length() > 0) {
                retriever.setDataSource(path);
            } else {
                retriever.setDataSource(this, uri);
            }
            long durationUs = videoDurationUs(retriever);
            if (durationUs <= 0) {
                durationUs = 30000000L;
            }

            longVideoProfile = durationUs > 20000000L;
            List<Long> frameTimesUs = videoFrameTimes(durationUs, longVideoProfile);
            int selectionWindow = RecognitionFrameSelector.highRateVideoSelectionWindow(
                    frameTimesUs.size(),
                    longVideoProfile
            );
            final int expectedFrames = Math.max(
                    1,
                    (frameTimesUs.size() + selectionWindow - 1) / selectionWindow
            );
            for (int frameIndex = 0; videoReplayActive && frameIndex < frameTimesUs.size(); frameIndex++) {
                long frameUs = frameTimesUs.get(frameIndex).longValue();
                double frameRatio = durationUs <= 0L
                        ? 0d
                        : Math.max(0d, Math.min(1d, frameUs / (double) durationUs));
                currentVideoFrameRatio = frameRatio;
                currentFrameTimestampUs = frameUs;
                videoDetailFrame = longVideoProfile || frameUs >= Math.round(durationUs * 0.58d);
                Bitmap frame = retrieveReplayFrame(retriever, frameUs);
                scannedFrames++;
                if (frame == null) {
                    updateHighFrameVideoReplayProgress(
                            scannedFrames,
                            frameTimesUs.size(),
                            analyzedFrames,
                            expectedFrames
                    );
                    continue;
                }
                if (frameIndex == 0 && isLikelyPhoneScreenRecording(frame, durationUs)) {
                    cameraSimulationScreenRecording = cameraSimulationActive;
                    appScreenRecordingDetected = !cameraSimulationActive;
                    Log.i("ShiqiRecognition", "Phone screen recording detected from video geometry");
                }
                if (cameraSimulationActive && appScreenRecordingDetected) {
                    cameraSimulationScreenRecording = true;
                    appScreenRecordingDetected = false;
                    Log.i("ShiqiRecognition", "Camera simulation switched to recorded preview content");
                }
                Bitmap cameraContent = cameraSimulationScreenRecording
                        ? cropRecordedCameraContent(frame)
                        : frame;
                Bitmap scaled = scaleReplayFrame(cameraContent);
                if (scaled != cameraContent) {
                    cameraContent.recycle();
                }
                if (cameraContent != frame && !frame.isRecycled()) {
                    frame.recycle();
                }
                if (appScreenRecordingDetected
                        && longVideoProfile
                        && (frameRatio < 0.06d || frameRatio > 0.89d)) {
                    scaled.recycle();
                    updateHighFrameVideoReplayProgress(
                            scannedFrames,
                            frameTimesUs.size(),
                            analyzedFrames,
                            expectedFrames
                    );
                    continue;
                }

                LowContrastTextPreprocessor.FrameMetrics metrics =
                        LowContrastTextPreprocessor.measureFrame(scaled);
                double candidateScore = RecognitionFrameSelector.highRateVideoFrameScore(
                        metrics.visualScore,
                        metrics.sharpness,
                        metrics.glareRatio
                );
                if (selectedFrame == null || candidateScore > selectedFrameScore) {
                    recycleBitmap(selectedFrame);
                    selectedFrame = scaled;
                    selectedFrameUs = frameUs;
                    selectedFrameRatio = frameRatio;
                    selectedFrameScore = candidateScore;
                    selectedDetailFrame = videoDetailFrame;
                } else {
                    scaled.recycle();
                }

                boolean windowComplete = scannedFrames % selectionWindow == 0
                        || frameIndex == frameTimesUs.size() - 1;
                if (!windowComplete) {
                    updateHighFrameVideoReplayProgress(
                            scannedFrames,
                            frameTimesUs.size(),
                            analyzedFrames,
                            expectedFrames
                    );
                    continue;
                }
                if (selectedFrame == null) {
                    continue;
                }

                currentFrameTimestampUs = selectedFrameUs;
                currentVideoFrameRatio = selectedFrameRatio;
                videoDetailFrame = selectedDetailFrame;
                Bitmap frameForRecognition = selectedFrame;
                selectedFrame = null;
                selectedFrameScore = -1d;
                showReplayFrame(frameForRecognition);
                if (cameraSimulationActive) {
                    analyzeBitmapFrame(frameForRecognition, false, true, replayGeneration);
                } else {
                    analyzeBitmapFrame(frameForRecognition, false);
                }
                analyzedFrames++;
                updateHighFrameVideoReplayProgress(
                        scannedFrames,
                        frameTimesUs.size(),
                        analyzedFrames,
                        expectedFrames
                );
                if (cameraSimulationActive
                        && RecognitionFrameSelector.shouldFinishCameraSimulation(
                        analyzedFrames,
                        hasCompleteVideoLabelCandidate()
                )) {
                    break;
                }
                if (selectedFrameRatio >= 0.90d && hasCompleteVideoLabelCandidate()) {
                    break;
                }
            }

            refineIncompleteVideoDateFromKeyframe(retriever, durationUs);
            final int finalFrameCount = analyzedFrames;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (replayGeneration != recognitionGeneration) {
                        return;
                    }
                    updateVideoReplayCompleteState(finalFrameCount);
                }
            });
        } catch (final Exception error) {
            final int partialFrameCount = analyzedFrames;
            Log.w("ShiqiRecognition", "Video replay stopped after readable frames", error);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (replayGeneration != recognitionGeneration) {
                        return;
                    }
                    if (partialFrameCount > 0) {
                        statusBadge.setText("部分完成");
                        updateVideoReplayCompleteState(partialFrameCount);
                        Toast.makeText(
                                DateOcrScanActivity.this,
                                "视频后段读取中断，已保留前面识别候选",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        statusBadge.setText("读取失败");
                        statusText.setText("视频读取失败，请换一个更清晰的本地视频或图片。");
                        Toast.makeText(DateOcrScanActivity.this, "视频读取失败", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } finally {
            recycleBitmap(selectedFrame);
            videoReplayActive = false;
            cameraSimulationActive = false;
            cameraSimulationScreenRecording = false;
            longVideoProfile = false;
            videoDetailFrame = false;
            currentVideoFrameRatio = 0d;
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void updateHighFrameVideoReplayProgress(
            final int scannedFrames,
            final int candidateFrames,
            final int analyzedFrames,
            final int expectedAnalyzedFrames
    ) {
        boolean recognitionAdvanced = analyzedFrames > videoAnalyzedFrames;
        int progressStep = Math.max(1, candidateFrames / 20);
        if (!recognitionAdvanced
                && scannedFrames < candidateFrames
                && scannedFrames % progressStep != 0) {
            return;
        }
        videoAnalyzedFrames = analyzedFrames;
        videoExpectedFrames = expectedAnalyzedFrames;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusBadge.setText("高帧扫描");
                statusText.setText(
                        "已扫描 " + Math.min(scannedFrames, candidateFrames)
                                + "/" + candidateFrames
                                + " 帧，精选 " + analyzedFrames
                                + "/" + expectedAnalyzedFrames
                                + " 帧识别中文包装和日期。"
                );
            }
        });
    }

    private void startImageRecognition(Uri uri) {
        if (uri == null || cameraExecutor == null) {
            return;
        }
        stopCamera();
        stopVideoReplay();
        resetRecognitionState();
        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
        if (replayFrameView != null) {
            replayFrameView.setVisibility(View.VISIBLE);
        }
        statusBadge.setText("图片");
        statusText.setText("正在识别所选图片中的条码和包装文字。");

        cameraExecutor.execute(new ImageRecognitionTask(uri));
    }

    private void startImageRecognitionPath(final String path) {
        if (path.length() == 0 || cameraExecutor == null) {
            return;
        }
        prepareImageSurface();
        cameraExecutor.execute(new Runnable() {
            @Override
            public void run() {
                analyzeDecodedImage(decodeOrientedFile(path));
            }
        });
    }

    private void prepareImageSurface() {
        stopCamera();
        stopVideoReplay();
        resetRecognitionState();
        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
        if (replayFrameView != null) {
            replayFrameView.setVisibility(View.VISIBLE);
        }
        statusBadge.setText("图片");
        statusText.setText("正在识别所选图片中的条码和包装文字。");
    }

    private final class ImageRecognitionTask implements Runnable {
        private final Uri uri;

        ImageRecognitionTask(Uri uri) {
            this.uri = uri;
        }

        @Override
        public void run() {
            try {
                analyzeDecodedImage(decodeOrientedUri(uri));
            } catch (final Exception error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusBadge.setText("读取失败");
                        statusText.setText("图片读取失败，请换一张更清晰的图片。");
                    }
                });
            }
        }
    }

    private Bitmap decodeOrientedFile(String path) {
        Bitmap decoded = BitmapFactory.decodeFile(path);
        if (decoded == null) {
            return null;
        }
        try {
            ExifInterface exif = new ExifInterface(path);
            return applyExifOrientation(decoded, exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            ));
        } catch (Exception ignored) {
            return decoded;
        }
    }

    private Bitmap decodeOrientedUri(Uri uri) throws Exception {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        InputStream exifStream = getContentResolver().openInputStream(uri);
        if (exifStream != null) {
            try {
                ExifInterface exif = new ExifInterface(exifStream);
                orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                );
            } finally {
                exifStream.close();
            }
        }

        InputStream bitmapStream = getContentResolver().openInputStream(uri);
        if (bitmapStream == null) {
            return null;
        }
        try {
            return applyExifOrientation(BitmapFactory.decodeStream(bitmapStream), orientation);
        } finally {
            bitmapStream.close();
        }
    }

    private Bitmap applyExifOrientation(Bitmap source, int orientation) {
        if (source == null || orientation == ExifInterface.ORIENTATION_NORMAL
                || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return source;
        }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return source;
        }
        Bitmap oriented = Bitmap.createBitmap(
                source,
                0,
                0,
                source.getWidth(),
                source.getHeight(),
                matrix,
                true
        );
        if (oriented != source) {
            source.recycle();
        }
        return oriented;
    }

    private void analyzeDecodedImage(Bitmap bitmap) {
        try {
            if (bitmap == null) {
                throw new IllegalArgumentException("bad image");
            }
            Bitmap scaled = prepareStillImage(bitmap);
            currentFrameTimestampUs = 0L;
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            showReplayFrame(scaled);
            analyzeBitmapFrame(scaled, true);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UnifiedRecognitionStabilizer.Snapshot snapshot = stabilizer.snapshot();
                    statusText.setText(snapshot.hasFillableCandidate()
                            ? "图片识别完成，可先确认再填入表单。"
                            : "图片识别完成，但没有稳定候选。可换一张更清晰的图片。");
                    setRecognitionComplete(true);
                }
            });
        } catch (final Exception error) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusBadge.setText("读取失败");
                    statusText.setText("图片读取失败，请换一张更清晰的图片。");
                }
            });
        }
    }

    private void analyzeBitmapFrame(Bitmap bitmap, boolean singleFrameConfirmation) throws Exception {
        analyzeBitmapFrame(bitmap, singleFrameConfirmation, false, recognitionGeneration);
    }

    private void analyzeBitmapFrame(
            Bitmap bitmap,
            boolean singleFrameConfirmation,
            boolean cameraLive,
            int expectedGeneration
    ) throws Exception {
        if (bitmap == null || textRecognizer == null || barcodeScanner == null) {
            return;
        }
        if (recognitionFirstFrameAtMs == 0L) {
            recognitionFirstFrameAtMs = SystemClock.elapsedRealtime();
        }
        String barcode = "";
        StringBuilder rawText = new StringBuilder();
        StringBuilder focusedDateText = new StringBuilder();
        StringBuilder supplementaryDateText = new StringBuilder();
        String preferredDateText = "";
        String independentPaddleDateText = "";
        double independentPaddleDateConfidence = 0d;
        boolean zxingAttempted = false;
        boolean trustedBarcodeEvidence = false;
        long frameId = ++nextFrameId;
        long frameTimestampUs = currentFrameTimestampUs;
        LowContrastTextPreprocessor.FrameMetrics frameMetrics =
                LowContrastTextPreprocessor.measureFrame(bitmap);
        analyzedFrameSequence++;
        boolean needsBarcode = latestSnapshot == null || !latestSnapshot.hasStableBarcode();
        if (cameraLive
                && needsBarcode
                && (analyzedFrameSequence <= 12 || analyzedFrameSequence % 2 == 0)) {
            boolean thoroughBarcodePass = analyzedFrameSequence % 4 == 0
                    || (videoReplayActive
                    && ((currentVideoFrameRatio >= 0.35d && currentVideoFrameRatio <= 0.48d)
                    || currentVideoFrameRatio >= 0.80d));
            barcode = thoroughBarcodePass
                    ? BarcodeImageDecoder.decodeProductBarcode(bitmap)
                    : BarcodeImageDecoder.decodeProductBarcodeFast(bitmap);
            trustedBarcodeEvidence = BarcodeUtils.isSupportedProductCode(barcode);
            zxingAttempted = true;
        }
        boolean runFullPaddleTextDetection = shouldRunPaddleTextDetection(
                singleFrameConfirmation,
                cameraLive,
                frameMetrics
        );
        List<PackagingTextAnalyzer.Observation> textObservations =
                new ArrayList<PackagingTextAnalyzer.Observation>();
        boolean paddlePrimaryPass = runFullPaddleTextDetection
                && (singleFrameConfirmation || cameraLive || videoReplayActive);
        List<FrameVariant> variants = buildFrameVariants(
                bitmap,
                cameraLive,
                singleFrameConfirmation,
                runFullPaddleTextDetection
        );
        try {
            for (FrameVariant variant : variants) {
                if (expectedGeneration != recognitionGeneration) {
                    return;
                }
                InputImage image = InputImage.fromBitmap(variant.bitmap, variant.rotationDegrees);
                List<Task<?>> variantTasks = new ArrayList<Task<?>>();
                Task<List<Barcode>> barcodeTask = null;
                Task<Text> textTask = null;
                Task<Text> latinTextTask = null;
                if (variant.scanBarcode && barcode.length() == 0) {
                    barcodeTask = barcodeScanner.process(image);
                    variantTasks.add(barcodeTask);
                }
                if (variant.scanText && (!paddlePrimaryPass || variant.dateFocused)) {
                    textTask = textRecognizer.process(image);
                    variantTasks.add(textTask);
                }
                if (variant.scanLatinText
                        && latinTextRecognizer != null
                        && textTask == null
                        && !paddlePrimaryPass) {
                    latinTextTask = latinTextRecognizer.process(image);
                    variantTasks.add(latinTextTask);
                }
                if (!variantTasks.isEmpty()) {
                    awaitRecognitionTask(
                            Tasks.whenAllComplete(variantTasks),
                            "recognition-variant",
                            6L
                    );
                }
                if (barcodeTask != null && barcodeTask.isSuccessful()) {
                    List<Barcode> barcodeResults = barcodeTask.getResult();
                    barcode = extractProductBarcode(barcodeResults);
                    if (barcode.length() == 0 && variant.rotationDegrees == 0) {
                        barcode = decodePotentialBarcodeCrops(variant.bitmap, barcodeResults);
                    }
                    trustedBarcodeEvidence = BarcodeUtils.isSupportedProductCode(barcode);
                }
                boolean shouldTryZxing = cameraLive
                        && barcode.length() == 0
                        && !zxingAttempted
                        && variant.scanBarcode
                        && ((appScreenRecordingDetected && !variant.scanText)
                        || (!appScreenRecordingDetected && analyzedFrameSequence % 3 == 0));
                if (shouldTryZxing) {
                    zxingAttempted = true;
                    barcode = BarcodeImageDecoder.decodeProductBarcodeFast(variant.bitmap);
                    trustedBarcodeEvidence = BarcodeUtils.isSupportedProductCode(barcode);
                }
                if (textTask != null && textTask.isSuccessful() && textTask.getResult() != null) {
                    Text recognizedText = textTask.getResult();
                    detectAppScreenRecording(recognizedText);
                    appendRecognizedText(rawText, recognizedText, variant);
                    if (variant.dateFocused) {
                        appendRecognizedText(focusedDateText, recognizedText, variant);
                    }
                    if (variant.datePairSupplement) {
                        appendRecognizedText(supplementaryDateText, recognizedText, variant);
                    }
                    appendTextObservations(
                            textObservations,
                            recognizedText,
                            variant,
                            "ML Kit 中文"
                    );
                }
                if (latinTextTask != null && latinTextTask.isSuccessful() && latinTextTask.getResult() != null) {
                    Text latinText = latinTextTask.getResult();
                    appendRecognizedText(rawText, latinText, variant);
                    if (variant.dateFocused) {
                        appendRecognizedText(focusedDateText, latinText, variant);
                    }
                    if (variant.datePairSupplement) {
                        appendRecognizedText(supplementaryDateText, latinText, variant);
                    }
                    appendTextObservations(
                            textObservations,
                            latinText,
                            variant.withSourceQuality(Math.min(1d, variant.sourceQuality + 0.05d)),
                            "ML Kit 拉丁"
                    );
                }
            }
            boolean targetedCameraDatePass = cameraLive
                    && paddleDetectedLineCameraFramesUsed < (dateOnlyMode ? 5 : 3)
                    && (!hasCompleteCameraDateCandidate()
                    || hasDatePairObservation(textObservations))
                    && hasDateLineObservation(textObservations);
            if (targetedCameraDatePass) {
                paddleDetectedLineCameraFramesUsed++;
                if (isDebuggable()) {
                    Log.d(
                            "ShiqiRecognition",
                            "Paddle targeted date pass frame=" + analyzedFrameSequence
                                    + " ratio="
                                    + String.format(Locale.US, "%.2f", currentVideoFrameRatio)
                    );
                }
            }
            PaddleDateEvidence detectedLineEvidence = PaddleDateEvidence.empty();
            if (singleFrameConfirmation || targetedCameraDatePass || (videoReplayActive
                    && longVideoProfile
                    && videoDetailFrame
                    && !runFullPaddleTextDetection
                    && analyzedFrameSequence % 2 == 0)) {
                detectedLineEvidence = recognizePaddleDetectedDateLines(
                        bitmap,
                        textObservations
                );
                if (detectedLineEvidence.text.length() > 0) {
                    appendRecognizedText(rawText, detectedLineEvidence.text);
                    appendRecognizedText(focusedDateText, detectedLineEvidence.text);
                    appendRecognizedText(supplementaryDateText, detectedLineEvidence.text);
                    preferredDateText = detectedLineEvidence.originalText;
                    if (detectedLineEvidence.confidence > independentPaddleDateConfidence) {
                        independentPaddleDateText = detectedLineEvidence.originalText;
                        independentPaddleDateConfidence = detectedLineEvidence.confidence;
                    }
                    textObservations.add(detectedLineEvidence.toObservation(frameMetrics.visualScore));
                }
            }
            PaddleDateEvidence detectedRegionEvidence = PaddleDateEvidence.empty();
            if (runFullPaddleTextDetection) {
                if (videoReplayActive) {
                    paddleDetectionVideoFramesUsed++;
                }
                detectedRegionEvidence = recognizePaddleTextRegionsForFrame(bitmap, cameraLive);
                if (cameraLive) {
                    paddleDetectionCameraFramesUsed++;
                }
                if (detectedRegionEvidence.packagingText.length() > 0) {
                    appendRecognizedText(rawText, detectedRegionEvidence.packagingText);
                    textObservations.addAll(
                            detectedRegionEvidence.toPackagingObservations(frameMetrics.visualScore)
                    );
                }
                if (detectedRegionEvidence.text.length() > 0) {
                    appendRecognizedText(rawText, detectedRegionEvidence.text);
                    appendRecognizedText(focusedDateText, detectedRegionEvidence.text);
                    appendRecognizedText(supplementaryDateText, detectedRegionEvidence.text);
                    preferredDateText = FoodItem.cleanText(preferredDateText).length() == 0
                            ? detectedRegionEvidence.originalText
                            : preferredDateText + "\n" + detectedRegionEvidence.originalText;
                    textObservations.add(detectedRegionEvidence.toObservation(frameMetrics.visualScore));
                    String paddleBarcode = RecognitionTextCleaner.extractProductCodeFromOcr(
                            detectedRegionEvidence.text
                    );
                    if (cameraLive
                            && barcode.length() == 0
                            && BarcodeUtils.isSupportedProductCode(paddleBarcode)) {
                        barcode = paddleBarcode;
                        trustedBarcodeEvidence = true;
                    }
                }
            }
            if (videoReplayActive
                    && !longVideoProfile
                    && videoDetailFrame
                    && paddleFocusedLabelVideoFramesUsed < 2
                    && currentVideoFrameRatio >= 0.68d
                    && currentVideoFrameRatio <= 0.82d
                    && !hasCompleteCameraDateCandidate()) {
                paddleFocusedLabelVideoFramesUsed++;
                PaddleDateEvidence focusedLabelEvidence = recognizePaddleFocusedLabelRegion(bitmap);
                if (focusedLabelEvidence.packagingText.length() > 0) {
                    appendRecognizedText(rawText, focusedLabelEvidence.packagingText);
                    appendRecognizedText(focusedDateText, focusedLabelEvidence.packagingText);
                    appendRecognizedText(supplementaryDateText, focusedLabelEvidence.packagingText);
                    textObservations.addAll(
                            focusedLabelEvidence.toPackagingObservations(frameMetrics.visualScore)
                    );
                    preferredDateText = FoodItem.cleanText(preferredDateText).length() == 0
                            ? focusedLabelEvidence.packagingText
                            : preferredDateText + "\n" + focusedLabelEvidence.packagingText;
                }
                if (focusedLabelEvidence.text.length() > 0) {
                    appendRecognizedText(rawText, focusedLabelEvidence.text);
                    appendRecognizedText(focusedDateText, focusedLabelEvidence.text);
                    appendRecognizedText(supplementaryDateText, focusedLabelEvidence.text);
                }
            }
            DateOcrParser.Result visibleDates = DateOcrParser.parse(rawText.toString());
            boolean visibleDatePair = !visibleDates.productionDates.isEmpty()
                    && !visibleDates.expiryDates.isEmpty();
            if (visibleDatePair) {
                appendRecognizedText(supplementaryDateText, rawText.toString());
            }
            DateOcrParser.Result detectorDates = DateOcrParser.parse(detectedRegionEvidence.text);
            boolean detectorFoundCompleteDate = hasCompleteDateEvidence(detectorDates);
            if (!detectorFoundCompleteDate && shouldRunPaddleFallback(
                    visibleDates,
                    singleFrameConfirmation,
                    cameraLive,
                    frameMetrics.visualScore
            )) {
                if (cameraLive) {
                    paddleCameraBandFramesUsed++;
                } else if (videoReplayActive) {
                    paddleVideoFramesUsed++;
                }
                PaddleDateEvidence paddleEvidence = recognizePaddleDateBands(
                        bitmap,
                        singleFrameConfirmation,
                        cameraLive,
                        false
                );
                if (paddleEvidence.text.length() > 0) {
                    appendRecognizedText(rawText, paddleEvidence.text);
                    appendRecognizedText(focusedDateText, paddleEvidence.text);
                    appendRecognizedText(supplementaryDateText, paddleEvidence.text);
                    preferredDateText = FoodItem.cleanText(preferredDateText).length() == 0
                             ? paddleEvidence.originalText
                             : preferredDateText + "\n" + paddleEvidence.originalText;
                    if (paddleEvidence.confidence > independentPaddleDateConfidence) {
                        independentPaddleDateText = paddleEvidence.originalText;
                        independentPaddleDateConfidence = paddleEvidence.confidence;
                    }
                    textObservations.add(paddleEvidence.toObservation(frameMetrics.visualScore));
                }
            }
        } finally {
            recycleVariants(variants);
        }

        if (expectedGeneration != recognitionGeneration) {
            return;
        }
        List<PackagingTextAnalyzer.Candidate> packagingCandidates =
                PackagingTextAnalyzer.analyze(textObservations, singleFrameConfirmation);
        String selectedDateText = videoReplayActive || cameraLive
                ? focusedDateText.toString()
                : rawText.toString();
        DateOcrParser.Result parsedFrame = DateOcrParser.parseFocusedWithDateOnlySupplement(
                FoodItem.cleanText(selectedDateText),
                FoodItem.cleanText(supplementaryDateText.toString())
        );
        parsedFrame = DateEvidencePolicy.apply(
                parsedFrame,
                preferredDateText,
                singleFrameConfirmation
        );
        parsedFrame = DateEvidencePolicy.reconcileIndependentExpiryEvidence(
                parsedFrame,
                independentPaddleDateText,
                independentPaddleDateConfidence
        );
        if (!parsedFrame.productionDates.isEmpty()
                && parsedFrame.expiryDates.isEmpty()
                && focusedDateTailAttempts < 2) {
            RecognitionEvidence.NormalizedRect dateTailRect = bestIncompleteDateTailRect(
                    textObservations
            );
            if (dateTailRect != null) {
                focusedDateTailAttempts++;
                PaddleDateEvidence tailEvidence = recognizeObservedDateTail(bitmap, dateTailRect);
                if (tailEvidence.text.length() > 0) {
                    appendRecognizedText(supplementaryDateText, tailEvidence.text);
                    parsedFrame = DateOcrParser.parseFocusedWithDateOnlySupplement(
                            FoodItem.cleanText(selectedDateText),
                            FoodItem.cleanText(supplementaryDateText.toString())
                    );
                    parsedFrame = DateEvidencePolicy.apply(
                            parsedFrame,
                            tailEvidence.originalText,
                            singleFrameConfirmation
                    );
                    parsedFrame = DateEvidencePolicy.reconcileIndependentExpiryEvidence(
                            parsedFrame,
                            tailEvidence.originalText,
                            tailEvidence.confidence
                    );
                }
            }
        }
        rememberIncompleteDateReviewFrame(
                bitmap,
                frameTimestampUs,
                selectedDateText + "\n" + supplementaryDateText,
                parsedFrame,
                frameMetrics.visualScore,
                textObservations
        );
        if (trustedBarcodeEvidence && expectedGeneration == recognitionGeneration) {
            stabilizer.promoteChecksumConstrainedBarcode(barcode);
        }
        handleRecognitionResult(
                barcode,
                rawText.toString(),
                packagingCandidates,
                singleFrameConfirmation,
                selectedDateText,
                supplementaryDateText.toString(),
                parsedFrame,
                cameraLive,
                expectedGeneration
        );
        rememberEvidenceFrame(
                bitmap,
                frameId,
                frameTimestampUs,
                frameMetrics,
                textObservations,
                packagingCandidates,
                parsedFrame,
                barcode
        );
        maybeCompleteLiveCamera(cameraLive, expectedGeneration);
    }

    private boolean awaitRecognitionTask(Task<?> task, String label, long timeoutSeconds) {
        try {
            Tasks.await(task, timeoutSeconds, TimeUnit.SECONDS);
            return task.isSuccessful();
        } catch (Exception error) {
            Log.w("ShiqiRecognition", label + " failed for one variant", error);
            return false;
        }
    }

    private String decodePotentialBarcodeCrops(Bitmap source, List<Barcode> barcodes) {
        if (source == null || source.isRecycled() || barcodes == null || barcodes.isEmpty()) {
            return "";
        }
        int attempts = 0;
        for (Barcode candidate : barcodes) {
            Rect box = candidate == null ? null : candidate.getBoundingBox();
            if (box == null || box.width() < 8 || box.height() < 8) {
                continue;
            }
            int expandX = Math.max(12, Math.round(box.width() * 0.55f));
            int expandY = Math.max(12, Math.round(box.height() * 0.85f));
            int left = clamp(box.left - expandX, 0, source.getWidth() - 1);
            int top = clamp(box.top - expandY, 0, source.getHeight() - 1);
            int right = clamp(box.right + expandX, left + 1, source.getWidth());
            int bottom = clamp(box.bottom + expandY, top + 1, source.getHeight());
            Bitmap crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top);
            Bitmap prepared = upscaleIfNeeded(crop, 1400);
            if (prepared != crop) {
                crop.recycle();
            }
            try {
                Task<List<Barcode>> retry = barcodeScanner.process(InputImage.fromBitmap(prepared, 0));
                awaitRecognitionTask(retry, "potential-barcode-crop", 4L);
                String decoded = retry.isSuccessful()
                        ? extractProductBarcode(retry.getResult())
                        : "";
                if (decoded.length() == 0) {
                    decoded = BarcodeImageDecoder.decodeProductBarcode(prepared);
                }
                if (BarcodeUtils.isSupportedProductCode(decoded)) {
                    Log.i("ShiqiRecognition", "Potential barcode crop decoded=" + decoded);
                    return decoded;
                }
            } finally {
                prepared.recycle();
            }
            attempts++;
            if (attempts >= 2) {
                break;
            }
        }
        return "";
    }

    private void updateVideoReplayProgress(final int analyzedFrames, final int expectedFrames) {
        videoAnalyzedFrames = analyzedFrames;
        videoExpectedFrames = expectedFrames;
        if (analyzedFrames > 1 && analyzedFrames % 2 != 0 && analyzedFrames < expectedFrames) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusBadge.setText("分析中");
                statusText.setText("正在分析中英文包装 " + Math.min(analyzedFrames, expectedFrames)
                        + "/" + expectedFrames + " 帧，候选稳定后可选择。");
            }
        });
    }

    private List<FrameVariant> buildFrameVariants(
            Bitmap bitmap,
            boolean cameraLive,
            boolean singleFrameConfirmation,
            boolean runningHeavyDatePass
    ) {
        List<FrameVariant> variants = new ArrayList<FrameVariant>();
        if (singleFrameConfirmation) {
            variants.add(new FrameVariant(bitmap, 0, true, true, true, false, 0.90d));
            variants.add(new FrameVariant(bitmap, 90, false, true, true, false, 0.80d, true, true));
            variants.add(new FrameVariant(bitmap, 270, false, true, true, false, 0.80d, true, true));
            if (isLikelyDateStrip(bitmap)) {
                Bitmap laser = LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap);
                if (laser != null) {
                    variants.add(new FrameVariant(laser, 0, false, true, true, true, 0.96d, true, true));
                    variants.add(new FrameVariant(laser, 90, false, true, true, false, 0.88d, true, true));
                    variants.add(new FrameVariant(laser, 270, false, true, true, false, 0.88d, true, true));
                }
                Bitmap embossed = LowContrastTextPreprocessor.enhanceEmbossedText(bitmap);
                if (embossed != null) {
                    variants.add(new FrameVariant(embossed, 0, false, true, true, true, 0.96d, true, true));
                }
                Bitmap binary = LowContrastTextPreprocessor.binarizeText(bitmap, false);
                if (binary != null) {
                    variants.add(new FrameVariant(binary, 0, false, true, true, true, 0.94d, true, true));
                }
                Bitmap inverted = LowContrastTextPreprocessor.binarizeText(bitmap, true);
                if (inverted != null) {
                    variants.add(new FrameVariant(inverted, 0, false, true, true, true, 0.94d, true, true));
                }
                Bitmap brightText = LowContrastTextPreprocessor.isolateBrightText(bitmap);
                if (brightText != null) {
                    variants.add(new FrameVariant(brightText, 0, false, true, true, true, 0.97d, true, true));
                }
                return variants;
            }

            float[] lefts = new float[] {0.00f, 0.40f};
            float[] tops = new float[] {0.00f, 0.29f, 0.58f};
            for (float top : tops) {
                for (float left : lefts) {
                    addFrameCrop(
                            variants,
                            bitmap,
                            left,
                            top,
                            0.60f,
                            0.42f,
                            STILL_IMAGE_MIN_SIDE,
                            0,
                            false,
                            true,
                            true,
                            true
                    );
                }
            }
            Bitmap enhanced = LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap);
            if (enhanced != null) {
                variants.add(new FrameVariant(enhanced, 0, false, true, true, true, 0.92d, true, false));
                variants.add(new FrameVariant(enhanced, 90, false, true, true, false, 0.86d, true, true));
                variants.add(new FrameVariant(enhanced, 270, false, true, true, false, 0.86d, true, true));
            }
            return variants;
        }
        if (videoReplayActive && appScreenRecordingDetected) {
            variants.add(new FrameVariant(bitmap, 0, true, false, false, false, 0.74d));
            addFrameCrop(
                    variants,
                    bitmap,
                    0.04f,
                    0.075f,
                    0.92f,
                    0.565f,
                    1280,
                    0,
                    true,
                    true,
                    analyzedFrameSequence % 3 == 0,
                    true
            );
            UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
            if ((snapshot == null || !snapshot.hasStableBarcode())
                    && analyzedFrameSequence >= 5
                    && analyzedFrameSequence % 2 == 1) {
                addFrameCrop(
                        variants,
                        bitmap,
                        0.18f,
                        0.30f,
                        0.76f,
                        0.30f,
                        1800,
                        0,
                        true,
                        false,
                        false,
                        false
                );
            }
            return variants;
        }
        if (cameraLive) {
            UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
            boolean stillNeedsName = snapshot == null || !snapshot.hasStablePackagingName();
            boolean scanPackagingText = !barcodeLookupOnly
                    && (analyzedFrameSequence <= 5
                    || (stillNeedsName
                    && analyzedFrameSequence >= 7
                    && analyzedFrameSequence % 3 == 1));
            variants.add(new FrameVariant(
                    bitmap,
                    0,
                    true,
                    scanPackagingText,
                    !barcodeLookupOnly && analyzedFrameSequence == 1,
                    false,
                    0.84d
            ));
            boolean stillNeedsIdentity = snapshot == null
                    || (!snapshot.hasStableBarcode() && !snapshot.hasStablePackagingName());
            if (!barcodeLookupOnly
                    && !runningHeavyDatePass
                    && stillNeedsIdentity
                    && analyzedFrameSequence <= 2
                    && analyzedFrameSequence % 2 == 0) {
                addFrameCrop(
                        variants,
                        bitmap,
                        0.07f,
                        0.13f,
                        0.86f,
                        0.52f,
                        1280,
                        0,
                        true,
                        true,
                        false,
                        false
                );
            }
            if (!barcodeLookupOnly && runningHeavyDatePass) {
                Bitmap enhanced = paddleDetectionCameraFramesUsed % 2 == 0
                        ? LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap)
                        : LowContrastTextPreprocessor.enhanceEmbossedText(bitmap);
                if (enhanced != null) {
                    variants.add(new FrameVariant(
                            enhanced,
                            0,
                            false,
                            true,
                            true,
                            true,
                            0.94d,
                            true,
                            true
                    ));
                }
            }
            return variants;
        }
        if (videoReplayActive && !longVideoProfile && videoDetailFrame) {
            variants.add(new FrameVariant(
                    bitmap,
                    0,
                    true,
                    true,
                    analyzedFrameSequence % 3 == 0,
                    true,
                    0.76d
            ));
            if (analyzedFrameSequence % 2 == 0) {
                addFrameCrop(
                        variants,
                        bitmap,
                        0.44f,
                        0.54f,
                        0.56f,
                        0.38f,
                        1800,
                        0,
                        false,
                        true,
                        true,
                        true
                );
            }
            return variants;
        }
        if (videoReplayActive && !videoDetailFrame) {
            variants.add(new FrameVariant(
                    bitmap,
                    0,
                    true,
                    true,
                    analyzedFrameSequence % 2 == 1,
                    false,
                    0.72d
            ));
            return variants;
        }
        variants.add(new FrameVariant(bitmap, 0, true, true, false, false, 0.68d));
        addFrameCrop(variants, bitmap, 0.04f, 0.13f, 0.92f, 0.50f, 1100, 0, false, true, false);
        addFrameCrop(variants, bitmap, 0.14f, 0.20f, 0.72f, 0.40f, 1800, 0, false, true, true, true);
        addFrameCrop(variants, bitmap, 0.38f, 0.48f, 0.60f, 0.44f, 1400, 0, false, true, false, true);
        addFrameCrop(variants, bitmap, 0.54f, 0.60f, 0.44f, 0.22f, 1500, 0, false, true, true, true);
        if (analyzedFrameSequence % 2 == 1) {
            Bitmap enhanced = LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap);
            if (enhanced != null) {
                variants.add(new FrameVariant(enhanced, 0, false, true, false, true, 0.88d, false, true));
            }
            addEmbossedFrameCrop(variants, bitmap, 0.14f, 0.20f, 0.72f, 0.40f, 1800);
        } else {
            addEnhancedFrameCrop(variants, bitmap, 0.54f, 0.60f, 0.44f, 0.22f, 1500);
        }
        return variants;
    }

    private long recognitionElapsedMs() {
        long startedAt = recognitionFirstFrameAtMs;
        return startedAt <= 0L ? 0L : Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
    }

    private void maybeCompleteLiveCamera(boolean liveCamera, final int expectedGeneration) {
        if (!liveCamera || videoReplayActive || !cameraBound
                || expectedGeneration != recognitionGeneration) {
            return;
        }
        final UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
        if (barcodeLookupOnly) {
            if (analyzedFrameSequence < 2 || snapshot == null || !snapshot.hasStableBarcode()) {
                return;
            }
            cameraBound = false;
            liveCameraResultFrozen = true;
            final String barcode = snapshot.stableBarcode;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (expectedGeneration != recognitionGeneration) {
                        return;
                    }
                    stopCamera();
                    Intent result = new Intent();
                    result.putExtra("SCAN_RESULT", barcode);
                    result.putExtra(UnifiedRecognitionPayload.EXTRA_BARCODE, barcode);
                    setResult(RESULT_OK, result);
                    Log.i(
                            "ShiqiRecognition",
                            "Barcode lookup complete frames=" + analyzedFrameSequence
                                    + " elapsedMs=" + recognitionElapsedMs()
                    );
                    finish();
                }
            });
            return;
        }
        if (analyzedFrameSequence < 3 || !hasCompleteVideoLabelCandidate()) {
            return;
        }
        cameraBound = false;
        liveCameraResultFrozen = true;
        final long elapsedMs = recognitionElapsedMs();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (expectedGeneration != recognitionGeneration) {
                    return;
                }
                stopCamera();
                updateResultUi(snapshot);
                statusBadge.setText("识别完成");
                statusText.setText("已自动选出稳定结果，请核对后填入。");
                setRecognitionComplete(true);
                List<SessionKeyframe> completedKeyframes = keyframeSnapshot();
                if (!completedKeyframes.isEmpty()) {
                    showEvidenceFrame(completedKeyframes.get(0), false);
                }
                Log.i(
                        "ShiqiRecognition",
                        "Camera complete frames=" + analyzedFrameSequence
                                + " elapsedMs=" + elapsedMs
                                + " barcode=" + snapshot.hasStableBarcode()
                                + " name=" + snapshot.hasStablePackagingName()
                                + " dates=" + snapshot.hasStableDateCandidate()
                );
            }
        });
    }

    private boolean hasCompleteVideoLabelCandidate() {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
        if (snapshot == null || snapshot.stableDateVote == null) {
            return false;
        }
        DateOcrFrameVoter.VoteResult vote = snapshot.stableDateVote;
        boolean directDatePair = vote.productionDate != null && vote.expiryDate != null;
        boolean calculatedDatePair = vote.productionDate != null
                && vote.shelfLife != null
                && vote.calculatedExpiryDate != null;
        boolean completeDate = directDatePair || calculatedDatePair;
        if (dateOnlyMode) {
            return completeDate;
        }
        return completeDate
                && (snapshot.hasStableBarcode()
                || snapshot.hasStablePackagingName());
    }

    private void addEnhancedFrameCrop(
            List<FrameVariant> variants,
            Bitmap source,
            float leftRatio,
            float topRatio,
            float widthRatio,
            float heightRatio,
            int minLargestSide
    ) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int left = clamp(Math.round(sourceWidth * leftRatio), 0, sourceWidth - 1);
        int top = clamp(Math.round(sourceHeight * topRatio), 0, sourceHeight - 1);
        int width = clamp(Math.round(sourceWidth * widthRatio), 1, sourceWidth - left);
        int height = clamp(Math.round(sourceHeight * heightRatio), 1, sourceHeight - top);
        Bitmap crop = Bitmap.createBitmap(source, left, top, width, height);
        Bitmap prepared = upscaleIfNeeded(crop, minLargestSide);
        if (prepared != crop) {
            crop.recycle();
        }
        Bitmap enhanced = LowContrastTextPreprocessor.enhanceLaserPrintedText(prepared);
        prepared.recycle();
        if (enhanced != null) {
            variants.add(new FrameVariant(
                    enhanced,
                    0,
                    false,
                    true,
                    true,
                    true,
                    0.94d,
                    true,
                    false,
                    leftRatio,
                    topRatio,
                    widthRatio,
                    heightRatio
            ));
        }
    }

    private void addFrameCrop(
            List<FrameVariant> variants,
            Bitmap source,
            float leftRatio,
            float topRatio,
            float widthRatio,
            float heightRatio,
            int minLargestSide,
            int rotationDegrees,
            boolean scanBarcode,
            boolean scanText,
            boolean scanLatinText
    ) {
        addFrameCrop(
                variants,
                source,
                leftRatio,
                topRatio,
                widthRatio,
                heightRatio,
                minLargestSide,
                rotationDegrees,
                scanBarcode,
                scanText,
                scanLatinText,
                false
        );
    }

    private PaddleDateEvidence recognizePaddleDetectedDateLines(
            Bitmap bitmap,
            List<PackagingTextAnalyzer.Observation> observations
    ) {
        PaddleLineOcrEngine engine = paddleLineOcrEngine;
        if (engine == null || bitmap == null || bitmap.isRecycled()
                || observations == null || observations.isEmpty()) {
            return PaddleDateEvidence.empty();
        }

        List<PackagingTextAnalyzer.Observation> candidates =
                new ArrayList<PackagingTextAnalyzer.Observation>();
        for (PackagingTextAnalyzer.Observation observation : observations) {
            if (looksLikeDateLine(observation.text)) {
                candidates.add(observation);
            }
        }
        Collections.sort(candidates, new Comparator<PackagingTextAnalyzer.Observation>() {
            @Override
            public int compare(
                    PackagingTextAnalyzer.Observation left,
                    PackagingTextAnalyzer.Observation right
            ) {
                return Double.compare(dateLinePriority(right), dateLinePriority(left));
            }
        });

        StringBuilder recognized = new StringBuilder();
        StringBuilder original = new StringBuilder();
        RecognitionEvidence.NormalizedRect evidenceRect = null;
        List<RecognitionEvidence.NormalizedRect> attempted =
                new ArrayList<RecognitionEvidence.NormalizedRect>();
        double bestConfidence = 0d;
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int maxAttempts = videoReplayActive ? 2 : 8;
        for (PackagingTextAnalyzer.Observation observation : candidates) {
            RecognitionEvidence.NormalizedRect rect = expandedDateLineRect(observation);
            if (isNearDuplicateRect(rect, attempted)) {
                continue;
            }
            attempted.add(rect);
            if (attempted.size() > maxAttempts) {
                break;
            }

            int left = clamp((int) Math.floor(rect.left * imageWidth), 0, imageWidth - 1);
            int top = clamp((int) Math.floor(rect.top * imageHeight), 0, imageHeight - 1);
            int right = clamp((int) Math.ceil(rect.right * imageWidth), left + 1, imageWidth);
            int bottom = clamp((int) Math.ceil(rect.bottom * imageHeight), top + 1, imageHeight);
            Bitmap crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
            Bitmap prepared = upscaleIfNeeded(crop, 1200);
            if (prepared != crop) {
                crop.recycle();
            }
            List<PaddleLineOcrEngine.LineResult> lineResults =
                    new ArrayList<PaddleLineOcrEngine.LineResult>();
            try {
                lineResults.add(engine.recognizeResult(prepared, 0.25f));
                Bitmap laser = LowContrastTextPreprocessor.enhanceLaserPrintedText(prepared);
                if (laser != null) {
                    try {
                        lineResults.add(engine.recognizeResult(laser, 0.25f));
                    } finally {
                        laser.recycle();
                    }
                }
                addDeskewedDateLineResults(engine, prepared, lineResults);
                if (!videoReplayActive) {
                    Bitmap brightText = LowContrastTextPreprocessor.isolateBrightText(prepared);
                    if (brightText != null) {
                        try {
                            lineResults.add(engine.recognizeResult(brightText, 0.25f));
                        } finally {
                            brightText.recycle();
                        }
                    }
                }
            } finally {
                prepared.recycle();
            }

            boolean acceptedCrop = false;
            for (PaddleLineOcrEngine.LineResult result : lineResults) {
                if (isDebuggable() && result != null && result.text.length() > 0) {
                    Log.d(
                            "ShiqiRecognition",
                            "Paddle targeted date text=" + result.text
                                    + " confidence="
                                    + String.format(Locale.US, "%.2f", result.confidence)
                    );
                }
                String decodedBarcode = result == null
                        ? ""
                        : BarcodeUtils.extractProductCode(result.text);
                if (!BarcodeUtils.isSupportedProductCode(decodedBarcode) && result != null) {
                    decodedBarcode = BarcodeUtils.recoverEan13FromPrintedDigits(result.text);
                }
                if (result == null
                        || (!DateOcrParser.parse(result.text).hasAnyCandidate()
                        && !BarcodeUtils.isSupportedProductCode(decodedBarcode))) {
                    continue;
                }
                appendRecognizedText(recognized, decodedBarcode.length() > 0
                        ? decodedBarcode
                        : result.text);
                appendRecognizedText(original, result.text);
                bestConfidence = Math.max(bestConfidence, result.confidence);
                acceptedCrop = true;
            }
            if (acceptedCrop) {
                evidenceRect = evidenceRect == null ? rect : evidenceRect.union(rect);
            }
        }

        return new PaddleDateEvidence(
                recognized.toString(),
                original.toString(),
                bestConfidence,
                evidenceRect
        );
    }

    private boolean shouldRunPaddleTextDetection(
            boolean singleFrameConfirmation,
            boolean cameraLive,
            LowContrastTextPreprocessor.FrameMetrics frameMetrics
    ) {
        if (paddleTextDetectionEngine == null) {
            return false;
        }
        if (singleFrameConfirmation) {
            return true;
        }
        if (cameraLive && barcodeLookupOnly) {
            return false;
        }
        if (cameraLive) {
            return RecognitionFrameSelector.shouldRunHeavyCameraOcr(
                    analyzedFrameSequence,
                    paddleDetectionCameraFramesUsed,
                    frameMetrics.visualScore,
                    frameMetrics.sharpness,
                    frameMetrics.glareRatio,
                    hasCompleteCameraDateCandidate(),
                    dateOnlyMode
            );
        }
        if (!videoReplayActive || !videoDetailFrame) {
            return false;
        }
        if (longVideoProfile) {
            return paddleDetectionVideoFramesUsed < 3
                    && currentVideoFrameRatio >= 0.70d
                    && currentVideoFrameRatio <= 0.88d;
        }
        return paddleDetectionVideoFramesUsed < 2
                && currentVideoFrameRatio >= 0.84d
                && currentVideoFrameRatio <= 0.91d;
    }

    private boolean hasCompleteCameraDateCandidate() {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
        if (snapshot == null || snapshot.stableDateVote == null) {
            return false;
        }
        DateOcrFrameVoter.VoteResult vote = snapshot.stableDateVote;
        return vote.productionDate != null
                && ((vote.expiryDate != null)
                || (vote.shelfLife != null && vote.calculatedExpiryDate != null));
    }

    private PaddleDateEvidence recognizePaddleTextRegions(Bitmap bitmap, boolean cameraLive) {
        return recognizePaddleTextRegions(bitmap, cameraLive, 0);
    }

    private PaddleDateEvidence recognizePaddleTextRegions(
            Bitmap bitmap,
            boolean cameraLive,
            int requestedMaxRegions
    ) {
        PaddleTextDetectionEngine detector = paddleTextDetectionEngine;
        PaddleLineOcrEngine recognizer = paddleLineOcrEngine;
        if (detector == null || recognizer == null || bitmap == null || bitmap.isRecycled()) {
            return PaddleDateEvidence.empty();
        }

        int cameraPass = paddleDetectionCameraFramesUsed;
        int cameraInputSide = cameraPass == 0 ? 960 : 1280;
        Bitmap detectorInput = bitmap;
        if (cameraLive && cameraPass >= 2) {
            Bitmap enhancedDetectorInput = cameraPass % 2 == 1
                    ? LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap)
                    : LowContrastTextPreprocessor.enhanceEmbossedText(bitmap);
            if (enhancedDetectorInput != null) {
                detectorInput = enhancedDetectorInput;
            }
        }
        long detectorStartedAt = SystemClock.elapsedRealtime();
        List<PaddleTextDetectionEngine.TextRegion> detectedRegions;
        try {
            int maxRegions = requestedMaxRegions > 0
                    ? requestedMaxRegions
                    : cameraLive ? (dateOnlyMode ? 28 : 22) : videoReplayActive ? 10 : 24;
            detectedRegions = detector.detect(
                    detectorInput,
                    maxRegions,
                    cameraLive ? cameraInputSide : 1280
            );
        } finally {
            if (detectorInput != bitmap && !detectorInput.isRecycled()) {
                detectorInput.recycle();
            }
        }
        List<PaddleTextDetectionEngine.TextRegion> regions =
                new ArrayList<PaddleTextDetectionEngine.TextRegion>();
        List<RecognitionEvidence.NormalizedRect> cropRects =
                new ArrayList<RecognitionEvidence.NormalizedRect>();
        int dateRegionCount = cameraLive
                ? Math.min(dateOnlyMode ? 12 : 8, detectedRegions.size())
                : detectedRegions.size();
        for (int index = 0; index < dateRegionCount; index++) {
            PaddleTextDetectionEngine.TextRegion region = detectedRegions.get(index);
            regions.add(region);
            cropRects.add(region.contextRect());
        }
        if (cameraLive) {
            for (PaddleTextDetectionEngine.TextRegion region : detectedRegions) {
                if (region.aspectRatio() < 4d || region.widthRatio() < 0.06d) {
                    continue;
                }
                regions.add(region);
                cropRects.add(region.tightTextRect());
                if (regions.size() >= dateRegionCount + (dateOnlyMode ? 8 : 5)) {
                    break;
                }
            }
        }
        Log.i(
                "ShiqiRecognition",
                "Text detector pass=" + (cameraPass + 1)
                        + " input=" + cameraInputSide
                        + " regions=" + regions.size()
                        + " elapsedMs=" + (SystemClock.elapsedRealtime() - detectorStartedAt)
        );
        StringBuilder recognized = new StringBuilder();
        StringBuilder original = new StringBuilder();
        List<String> packagingLines = new ArrayList<String>();
        RecognitionEvidence.NormalizedRect evidenceRect = null;
        double bestConfidence = 0d;
        double bestPackagingConfidence = 0d;
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        List<Bitmap> crops = new ArrayList<Bitmap>();
        for (int index = 0; index < regions.size(); index++) {
            RecognitionEvidence.NormalizedRect rect = cropRects.get(index);
            int left = clamp((int) Math.floor(rect.left * imageWidth), 0, imageWidth - 1);
            int top = clamp((int) Math.floor(rect.top * imageHeight), 0, imageHeight - 1);
            int right = clamp((int) Math.ceil(rect.right * imageWidth), left + 1, imageWidth);
            int bottom = clamp((int) Math.ceil(rect.bottom * imageHeight), top + 1, imageHeight);
            crops.add(Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top));
        }

        List<Bitmap> recognitionCrops = crops;
        List<Bitmap> generatedRecognitionCrops = new ArrayList<Bitmap>();
        List<Integer> recognitionRegionIndexes = new ArrayList<Integer>();
        for (int index = 0; index < crops.size(); index++) {
            recognitionRegionIndexes.add(Integer.valueOf(index));
        }
        if (cameraLive && !crops.isEmpty()) {
            recognitionCrops = new ArrayList<Bitmap>(crops);
            for (int cropIndex = 0; cropIndex < crops.size(); cropIndex++) {
                Bitmap crop = crops.get(cropIndex);
                if (cropIndex >= (dateOnlyMode ? 8 : 5)) {
                    continue;
                }
                Bitmap laser = LowContrastTextPreprocessor.enhanceLaserPrintedText(crop);
                if (laser != null) {
                    recognitionCrops.add(laser);
                    generatedRecognitionCrops.add(laser);
                    recognitionRegionIndexes.add(Integer.valueOf(cropIndex));
                }
                Bitmap embossed = LowContrastTextPreprocessor.enhanceEmbossedText(crop);
                if (embossed != null) {
                    recognitionCrops.add(embossed);
                    generatedRecognitionCrops.add(embossed);
                    recognitionRegionIndexes.add(Integer.valueOf(cropIndex));
                }
                if (videoDetailFrame || cameraPass >= 2) {
                    Bitmap binary = LowContrastTextPreprocessor.binarizeText(crop, false);
                    if (binary != null) {
                        recognitionCrops.add(binary);
                        generatedRecognitionCrops.add(binary);
                        recognitionRegionIndexes.add(Integer.valueOf(cropIndex));
                    }
                    Bitmap inverted = LowContrastTextPreprocessor.binarizeText(crop, true);
                    if (inverted != null) {
                        recognitionCrops.add(inverted);
                        generatedRecognitionCrops.add(inverted);
                        recognitionRegionIndexes.add(Integer.valueOf(cropIndex));
                    }
                }
            }
        }
        long recognizerStartedAt = SystemClock.elapsedRealtime();
        try {
            List<PaddleLineOcrEngine.LineResult> results =
                    recognizePaddleCropsInSmallBatches(recognizer, recognitionCrops);
            Log.i(
                    "ShiqiRecognition",
                    "Text recognizer pass=" + (cameraPass + 1)
                            + " crops=" + recognitionCrops.size()
                            + " elapsedMs=" + (SystemClock.elapsedRealtime() - recognizerStartedAt)
            );
            boolean acceptedAny = false;
            for (int index = 0; index < results.size(); index++) {
                int regionIndex = recognitionRegionIndexes.get(index).intValue();
                PaddleLineOcrEngine.LineResult result = results.get(index);
                if (isDebuggable() && result != null && result.text.length() > 0) {
                    Log.d(
                            "ShiqiRecognition",
                            "Paddle crop pass=" + (cameraPass + 1)
                                    + " index=" + index
                                    + " confidence=" + String.format(Locale.US, "%.2f", result.confidence)
                                    + " text=" + result.text
                    );
                }
                if (result != null
                        && result.text.length() > 0
                        && result.confidence >= 0.55d
                        && !packagingLines.contains(result.text)) {
                    packagingLines.add(result.text);
                    bestPackagingConfidence = Math.max(
                            bestPackagingConfidence,
                            Math.min(result.confidence, regions.get(regionIndex).confidence)
                    );
                    RecognitionEvidence.NormalizedRect packagingRect = cropRects.get(regionIndex);
                    evidenceRect = evidenceRect == null
                            ? packagingRect
                            : evidenceRect.union(packagingRect);
                }
                String decodedBarcode = result == null
                        ? ""
                        : BarcodeUtils.extractProductCode(result.text);
                if (!BarcodeUtils.isSupportedProductCode(decodedBarcode) && result != null) {
                    decodedBarcode = BarcodeUtils.recoverEan13FromPrintedDigits(result.text);
                }
                if (result == null
                        || (!DateOcrParser.parse(result.text).hasAnyCandidate()
                        && !BarcodeUtils.isSupportedProductCode(decodedBarcode))) {
                    continue;
                }
                appendRecognizedText(recognized, decodedBarcode.length() > 0
                        ? decodedBarcode
                        : result.text);
                appendRecognizedText(original, result.text);
                bestConfidence = Math.max(
                        bestConfidence,
                        Math.min(result.confidence, regions.get(regionIndex).confidence)
                );
                RecognitionEvidence.NormalizedRect rect = cropRects.get(regionIndex);
                evidenceRect = evidenceRect == null ? rect : evidenceRect.union(rect);
                acceptedAny = true;
            }

            if (!acceptedAny && !videoReplayActive && !cameraLive && !crops.isEmpty()) {
                List<Bitmap> enhancedCrops = new ArrayList<Bitmap>();
                try {
                    for (Bitmap crop : crops) {
                        Bitmap enhanced = LowContrastTextPreprocessor.enhanceLaserPrintedText(crop);
                        enhancedCrops.add(enhanced == null ? crop : enhanced);
                    }
                    List<PaddleLineOcrEngine.LineResult> enhancedResults =
                            recognizePaddleCropsInSmallBatches(recognizer, enhancedCrops);
                    for (int index = 0;
                         index < enhancedResults.size() && index < regions.size();
                         index++) {
                        PaddleLineOcrEngine.LineResult result = enhancedResults.get(index);
                        String decodedBarcode = result == null
                                ? ""
                                : BarcodeUtils.extractProductCode(result.text);
                        if (!BarcodeUtils.isSupportedProductCode(decodedBarcode) && result != null) {
                            decodedBarcode = BarcodeUtils.recoverEan13FromPrintedDigits(result.text);
                        }
                        if (result == null
                                || (!DateOcrParser.parse(result.text).hasAnyCandidate()
                                && !BarcodeUtils.isSupportedProductCode(decodedBarcode))) {
                            continue;
                        }
                        appendRecognizedText(recognized, decodedBarcode.length() > 0
                                ? decodedBarcode
                                : result.text);
                        appendRecognizedText(original, result.text);
                        bestConfidence = Math.max(
                                bestConfidence,
                                Math.min(result.confidence, regions.get(index).confidence)
                        );
                        RecognitionEvidence.NormalizedRect rect = cropRects.get(index);
                        evidenceRect = evidenceRect == null ? rect : evidenceRect.union(rect);
                    }
                } finally {
                    for (int index = 0; index < enhancedCrops.size(); index++) {
                        Bitmap enhanced = enhancedCrops.get(index);
                        if (enhanced != crops.get(index) && !enhanced.isRecycled()) {
                            enhanced.recycle();
                        }
                    }
                }
            }
        } finally {
            for (Bitmap generated : generatedRecognitionCrops) {
                if (generated != null && !generated.isRecycled()) {
                    generated.recycle();
                }
            }
            for (Bitmap crop : crops) {
                if (!crop.isRecycled()) {
                    crop.recycle();
                }
            }
        }
        return new PaddleDateEvidence(
                recognized.toString(),
                original.toString(),
                joinLines(packagingLines),
                bestConfidence,
                bestPackagingConfidence,
                evidenceRect
        );
    }

    private PaddleDateEvidence recognizePaddleTextRegionsForFrame(Bitmap bitmap, boolean cameraLive) {
        if (!appScreenRecordingDetected || bitmap == null || bitmap.isRecycled()) {
            return recognizePaddleTextRegions(bitmap, cameraLive);
        }
        double leftRatio = 0.04d;
        double topRatio = 0.075d;
        double widthRatio = 0.92d;
        double heightRatio = 0.565d;
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int left = clamp((int) Math.round(imageWidth * leftRatio), 0, imageWidth - 1);
        int top = clamp((int) Math.round(imageHeight * topRatio), 0, imageHeight - 1);
        int width = clamp((int) Math.round(imageWidth * widthRatio), 1, imageWidth - left);
        int height = clamp((int) Math.round(imageHeight * heightRatio), 1, imageHeight - top);
        Bitmap cameraContent = Bitmap.createBitmap(bitmap, left, top, width, height);
        try {
            PaddleDateEvidence local = recognizePaddleTextRegions(cameraContent, cameraLive);
            if (local.text.length() == 0 && local.packagingText.length() == 0) {
                return local;
            }
            RecognitionEvidence.NormalizedRect localRect = local.rect;
            RecognitionEvidence.NormalizedRect mappedRect =
                    new RecognitionEvidence.NormalizedRect(
                            leftRatio + (localRect.left * widthRatio),
                            topRatio + (localRect.top * heightRatio),
                            leftRatio + (localRect.right * widthRatio),
                            topRatio + (localRect.bottom * heightRatio)
                    );
            return new PaddleDateEvidence(
                    local.text,
                    local.originalText,
                    local.packagingText,
                    local.confidence,
                    local.packagingConfidence,
                    mappedRect
            );
        } finally {
            cameraContent.recycle();
        }
    }

    private PaddleDateEvidence recognizePaddleFocusedLabelRegion(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return PaddleDateEvidence.empty();
        }
        double leftRatio = 0.44d;
        double topRatio = 0.54d;
        double widthRatio = 0.56d;
        double heightRatio = 0.38d;
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int left = clamp((int) Math.floor(imageWidth * leftRatio), 0, imageWidth - 1);
        int top = clamp((int) Math.floor(imageHeight * topRatio), 0, imageHeight - 1);
        int width = clamp((int) Math.ceil(imageWidth * widthRatio), 1, imageWidth - left);
        int height = clamp((int) Math.ceil(imageHeight * heightRatio), 1, imageHeight - top);
        Bitmap crop = Bitmap.createBitmap(bitmap, left, top, width, height);
        Bitmap prepared = upscaleIfNeeded(crop, 1500);
        if (prepared != crop) {
            crop.recycle();
        }
        try {
            PaddleDateEvidence local = recognizePaddleTextRegions(prepared, false, 24);
            if (local.text.length() == 0 && local.packagingText.length() == 0) {
                return local;
            }
            RecognitionEvidence.NormalizedRect localRect = local.rect;
            RecognitionEvidence.NormalizedRect mappedRect =
                    new RecognitionEvidence.NormalizedRect(
                            leftRatio + (localRect.left * widthRatio),
                            topRatio + (localRect.top * heightRatio),
                            leftRatio + (localRect.right * widthRatio),
                            topRatio + (localRect.bottom * heightRatio)
                    );
            return new PaddleDateEvidence(
                    local.text,
                    local.originalText,
                    local.packagingText,
                    local.confidence,
                    local.packagingConfidence,
                    mappedRect
            );
        } finally {
            prepared.recycle();
        }
    }

    private List<PaddleLineOcrEngine.LineResult> recognizePaddleCropsInSmallBatches(
            PaddleLineOcrEngine recognizer,
            List<Bitmap> crops
    ) {
        List<PaddleLineOcrEngine.LineResult> results =
                new ArrayList<PaddleLineOcrEngine.LineResult>();
        for (int start = 0; start < crops.size(); start++) {
            int end = start + 1;
            results.addAll(recognizer.recognizeResults(crops.subList(start, end), 0.25f));
        }
        return results;
    }

    private boolean looksLikeDateLine(String text) {
        String cleaned = FoodItem.cleanText(text);
        StringBuilder digitText = new StringBuilder();
        for (int index = 0; index < cleaned.length(); index++) {
            if (Character.isDigit(cleaned.charAt(index))) {
                digitText.append(cleaned.charAt(index));
            }
        }
        int digits = digitText.length();
        boolean shelfLifeLine = cleaned.contains("保质期")
                || cleaned.contains("质期")
                || cleaned.toLowerCase(Locale.US).contains("shelf life");
        if (shelfLifeLine && digits >= 1 && digits <= 3) {
            return true;
        }
        if (digits < 4 || digits > 18) {
            return false;
        }
        boolean hasSeparator = cleaned.indexOf('/') >= 0
                || cleaned.indexOf('.') >= 0
                || cleaned.indexOf('-') >= 0;
        boolean hasDateHint = cleaned.toLowerCase(Locale.US).contains("date")
                || cleaned.toLowerCase(Locale.US).contains("exp")
                || cleaned.contains("日期")
                || cleaned.contains("保质期")
                || cleaned.contains("有效期");
        int secondYear = digitText.indexOf("20", 7);
        boolean compactDatePair = digits >= 15
                && digits <= 16
                && digitText.indexOf("20") == 0
                && secondYear >= 7;
        if (digits > 10 && !hasSeparator && !hasDateHint && !compactDatePair) {
            return false;
        }
        return digits >= 6 || (hasDateHint && digits >= 1);
    }

    private boolean hasDateLineObservation(
            List<PackagingTextAnalyzer.Observation> observations
    ) {
        if (observations == null) {
            return false;
        }
        for (PackagingTextAnalyzer.Observation observation : observations) {
            if (looksLikeDateLine(observation.text)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDatePairObservation(
            List<PackagingTextAnalyzer.Observation> observations
    ) {
        if (observations == null) {
            return false;
        }
        for (PackagingTextAnalyzer.Observation observation : observations) {
            String text = FoodItem.cleanText(observation.text);
            StringBuilder digits = new StringBuilder();
            for (int index = 0; index < text.length(); index++) {
                if (Character.isDigit(text.charAt(index))) {
                    digits.append(text.charAt(index));
                }
            }
            if (digits.length() >= 14 && digits.length() <= 18) {
                return true;
            }
        }
        return false;
    }

    private double dateLinePriority(PackagingTextAnalyzer.Observation observation) {
        int digits = 0;
        for (int index = 0; index < observation.text.length(); index++) {
            if (Character.isDigit(observation.text.charAt(index))) {
                digits++;
            }
        }
        double model = Double.isNaN(observation.modelConfidence)
                ? 0.45d
                : RecognitionEvidence.clamp01(observation.modelConfidence);
        return Math.min(8, digits) + observation.sourceQuality + model;
    }

    private RecognitionEvidence.NormalizedRect expandedDateLineRect(
            PackagingTextAnalyzer.Observation observation
    ) {
        double height = Math.max(0.01d, observation.bottom - observation.top);
        double width = Math.max(0.01d, observation.right - observation.left);
        double verticalPadding = Math.max(0.015d, height * 0.80d);
        double horizontalPadding = Math.max(0.018d, width * 0.06d);
        return new RecognitionEvidence.NormalizedRect(
                observation.left - horizontalPadding,
                observation.top - verticalPadding,
                observation.right + horizontalPadding,
                observation.bottom + verticalPadding
        );
    }

    private void addDeskewedDateLineResults(
            PaddleLineOcrEngine engine,
            Bitmap source,
            List<PaddleLineOcrEngine.LineResult> target
    ) {
        for (float degrees : new float[] {15f, -15f}) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(
                    source,
                    0,
                    0,
                    source.getWidth(),
                    source.getHeight(),
                    matrix,
                    true
            );
            try {
                target.add(engine.recognizeResult(rotated, 0.25f));
                Bitmap laser = LowContrastTextPreprocessor.enhanceLaserPrintedText(rotated);
                if (laser != null) {
                    try {
                        target.add(engine.recognizeResult(laser, 0.25f));
                    } finally {
                        laser.recycle();
                    }
                }
            } finally {
                if (rotated != source && !rotated.isRecycled()) {
                    rotated.recycle();
                }
            }
        }
    }

    private boolean isNearDuplicateRect(
            RecognitionEvidence.NormalizedRect candidate,
            List<RecognitionEvidence.NormalizedRect> existing
    ) {
        double centerX = (candidate.left + candidate.right) / 2d;
        double centerY = (candidate.top + candidate.bottom) / 2d;
        for (RecognitionEvidence.NormalizedRect rect : existing) {
            double otherCenterX = (rect.left + rect.right) / 2d;
            double otherCenterY = (rect.top + rect.bottom) / 2d;
            if (Math.abs(centerX - otherCenterX) <= 0.060d
                    && Math.abs(centerY - otherCenterY) <= 0.010d) {
                return true;
            }
        }
        return false;
    }

    private PaddleDateEvidence recognizePaddleDateBands(
            Bitmap bitmap,
            boolean singleFrameConfirmation,
            boolean cameraLive,
            boolean staticKeyframeReview
    ) {
        PaddleLineOcrEngine engine = paddleLineOcrEngine;
        if (engine == null || bitmap == null || bitmap.isRecycled()) {
            return PaddleDateEvidence.empty();
        }
        if (isDebuggable() && staticKeyframeReview) {
            Log.d("ShiqiRecognition", "Paddle static keyframe review enabled");
        }
        if (singleFrameConfirmation && isLikelyDateStrip(bitmap)) {
            return recognizePaddleDateStrip(engine, bitmap);
        }
        boolean recordedCameraContent = !staticKeyframeReview
                && (appScreenRecordingDetected || cameraSimulationScreenRecording);
        float[] bandStarts = staticKeyframeReview
                ? new float[] {
                0.285f, 0.325f, 0.20f, 0.24f, 0.37f,
                0.44f, 0.51f, 0.58f, 0.61f, 0.65f, 0.72f
        }
                : cameraSimulationScreenRecording
                ? new float[] {
                0.05f, 0.105f, 0.16f, 0.215f, 0.27f, 0.325f, 0.38f,
                0.435f, 0.49f, 0.545f, 0.60f, 0.655f, 0.71f
        }
                : appScreenRecordingDetected
                ? new float[] {0.18f, 0.21f, 0.24f, 0.27f, 0.30f, 0.33f, 0.36f}
                : videoReplayActive
                ? new float[] {
                0.64f, 0.645f, 0.67f, 0.75f, 0.80f, 0.85f,
                0.90f, 0.55f, 0.45f, 0.35f, 0.25f, 0.15f
        }
                : new float[] {
                 0.285f, 0.325f, 0.20f, 0.24f, 0.37f,
                0.44f, 0.51f, 0.58f, 0.61f, 0.65f, 0.72f
        };
        float[] horizontalStarts = staticKeyframeReview
                ? new float[] {0.42f, 0.02f, 0.56f}
                : recordedCameraContent
                ? new float[] {cameraSimulationScreenRecording ? 0.02f : 0.08f}
                : new float[] {0.42f, 0.02f, 0.56f};
        int firstIndex;
        int bandCount;
        if (singleFrameConfirmation) {
            firstIndex = 0;
            bandCount = bandStarts.length;
        } else if (cameraLive) {
            firstIndex = recordedCameraContent
                    ? 0
                    : analyzedFrameSequence % bandStarts.length;
            bandCount = recordedCameraContent
                    ? bandStarts.length
                    : 1;
        } else if (videoReplayActive && videoDetailFrame) {
            if (appScreenRecordingDetected && currentVideoFrameRatio >= 0.62d) {
                firstIndex = 0;
                bandCount = bandStarts.length;
            } else {
                firstIndex = (analyzedFrameSequence * 3) % bandStarts.length;
                bandCount = Math.min(3, bandStarts.length);
            }
        } else {
            return PaddleDateEvidence.empty();
        }

        StringBuilder recognized = new StringBuilder();
        StringBuilder originalRecognized = new StringBuilder();
        RecognitionEvidence.NormalizedRect evidenceRect = null;
        double bestConfidence = 0d;
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int bandHeight = Math.max(1, Math.round(
                imageHeight * (staticKeyframeReview
                        ? 0.08f
                        : cameraSimulationScreenRecording
                        ? 0.06f
                        : appScreenRecordingDetected
                        ? 0.065f
                        : videoReplayActive ? 0.032f : 0.08f)
        ));
        for (int index = 0; index < bandCount; index++) {
            int bandIndex = (firstIndex + index) % bandStarts.length;
            int top = Math.max(0, Math.min(
                    imageHeight - bandHeight,
                    Math.round(imageHeight * bandStarts[bandIndex])
            ));
            int horizontalStartIndex = singleFrameConfirmation
                    ? 0
                    : (analyzedFrameSequence + index) % horizontalStarts.length;
            int horizontalCount = singleFrameConfirmation
                    ? horizontalStarts.length
                    : (videoReplayActive && !recordedCameraContent
                    ? Math.min(2, horizontalStarts.length)
                    : 1);
            for (int horizontalIndex = 0; horizontalIndex < horizontalCount; horizontalIndex++) {
                float leftRatio = horizontalStarts[
                        (horizontalStartIndex + horizontalIndex) % horizontalStarts.length
                ];
                int left = Math.max(0, Math.min(
                        imageWidth - 1,
                        Math.round(imageWidth * leftRatio)
                ));
                float widthRatio = staticKeyframeReview
                        ? 0.38f
                        : recordedCameraContent
                        ? (cameraSimulationScreenRecording ? 0.96f : 0.84f)
                        : videoReplayActive ? 0.52f : 0.38f;
                int width = Math.max(1, Math.min(
                        imageWidth - left,
                        Math.round(imageWidth * widthRatio)
                ));
                Bitmap crop = Bitmap.createBitmap(bitmap, left, top, width, bandHeight);
                Bitmap preparedCrop = crop;
                try {
                    PaddleLineOcrEngine.LineResult original = engine.recognizeResult(preparedCrop);
                    if (isDebuggable() && original.text.length() > 0) {
                        Log.d(
                                "ShiqiRecognition",
                                "Paddle band top=" + String.format(Locale.US, "%.3f", bandStarts[bandIndex])
                                        + " text=" + original.text
                                        + " confidence=" + String.format(Locale.US, "%.2f", original.confidence)
                        );
                    }
                    appendRecognizedText(recognized, original.text);
                    appendRecognizedText(originalRecognized, original.text);
                    if (original.text.length() > 0) {
                        bestConfidence = Math.max(bestConfidence, original.confidence);
                        RecognitionEvidence.NormalizedRect currentRect = new RecognitionEvidence.NormalizedRect(
                                left / (double) imageWidth,
                                top / (double) imageHeight,
                                (left + width) / (double) imageWidth,
                                (top + bandHeight) / (double) imageHeight
                        );
                        evidenceRect = evidenceRect == null ? currentRect : evidenceRect.union(currentRect);
                    }
                    if ((singleFrameConfirmation || videoReplayActive || cameraLive)
                            && horizontalIndex == 0) {
                        Bitmap enhanced = LowContrastTextPreprocessor.enhanceEmbossedText(preparedCrop);
                        if (enhanced != null) {
                            try {
                                PaddleLineOcrEngine.LineResult enhancedResult = engine.recognizeResult(enhanced);
                                if (isDebuggable() && enhancedResult.text.length() > 0) {
                                    Log.d(
                                            "ShiqiRecognition",
                                            "Paddle embossed band top="
                                                    + String.format(Locale.US, "%.3f", bandStarts[bandIndex])
                                                    + " text=" + enhancedResult.text
                                                    + " confidence=" + String.format(
                                                    Locale.US,
                                                    "%.2f",
                                                    enhancedResult.confidence
                                            )
                                    );
                                }
                                appendRecognizedText(recognized, enhancedResult.text);
                                if (enhancedResult.text.length() > 0) {
                                    bestConfidence = Math.max(bestConfidence, enhancedResult.confidence);
                                    RecognitionEvidence.NormalizedRect currentRect =
                                            new RecognitionEvidence.NormalizedRect(
                                                    left / (double) imageWidth,
                                                    top / (double) imageHeight,
                                                    (left + width) / (double) imageWidth,
                                                    (top + bandHeight) / (double) imageHeight
                                            );
                                    evidenceRect = evidenceRect == null
                                            ? currentRect
                                            : evidenceRect.union(currentRect);
                                }
                            } finally {
                                enhanced.recycle();
                            }
                        }
                    }
                } finally {
                    crop.recycle();
                }
                DateOcrParser.Result parsed = DateOcrParser.parse(recognized.toString());
                if (!parsed.productionDates.isEmpty() && !parsed.expiryDates.isEmpty()) {
                    return new PaddleDateEvidence(
                            recognized.toString(),
                            originalRecognized.toString(),
                            bestConfidence,
                            evidenceRect
                    );
                }
            }
        }
        return new PaddleDateEvidence(
                recognized.toString(),
                originalRecognized.toString(),
                bestConfidence,
                evidenceRect
        );
    }

    private boolean shouldRunPaddleFallback(
            DateOcrParser.Result visibleDates,
            boolean singleFrameConfirmation,
            boolean cameraLive,
            double visualQuality
    ) {
        if (singleFrameConfirmation) {
            return true;
        }
        boolean hasDateSignal = visibleDates != null && visibleDates.hasAnyCandidate();
        boolean hasCalendarDateSignal = visibleDates != null
                && (!visibleDates.productionDates.isEmpty()
                || !visibleDates.expiryDates.isEmpty()
                || !visibleDates.calculatedExpiryDates.isEmpty());
        if (cameraLive) {
            if (cameraSimulationScreenRecording && currentVideoFrameRatio < 0.52d) {
                return false;
            }
            int attemptLimit = cameraSimulationScreenRecording ? 4 : 6;
            return paddleCameraBandFramesUsed < attemptLimit
                    && visualQuality >= 0.40d
                    && (hasCalendarDateSignal || paddleDetectionCameraFramesUsed >= 3);
        }
        if (!videoReplayActive || !videoDetailFrame || paddleVideoFramesUsed >= 5) {
            return false;
        }
        if (hasDateSignal) {
            return true;
        }
        if (appScreenRecordingDetected && currentVideoFrameRatio < 0.52d) {
            return false;
        }
        return visualQuality >= 0.42d
                && (currentVideoFrameRatio >= 0.52d || analyzedFrameSequence % 5 == 0);
    }

    private boolean hasCompleteDateEvidence(DateOcrParser.Result result) {
        return result != null
                && !result.productionDates.isEmpty()
                && (!result.expiryDates.isEmpty()
                || (!result.shelfLives.isEmpty() && !result.calculatedExpiryDates.isEmpty()));
    }

    private void addFrameCrop(
            List<FrameVariant> variants,
            Bitmap source,
            float leftRatio,
            float topRatio,
            float widthRatio,
            float heightRatio,
            int minLargestSide,
            int rotationDegrees,
            boolean scanBarcode,
            boolean scanText,
            boolean scanLatinText,
            boolean dateFocused
    ) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int left = clamp(Math.round(sourceWidth * leftRatio), 0, sourceWidth - 1);
        int top = clamp(Math.round(sourceHeight * topRatio), 0, sourceHeight - 1);
        int width = clamp(Math.round(sourceWidth * widthRatio), 1, sourceWidth - left);
        int height = clamp(Math.round(sourceHeight * heightRatio), 1, sourceHeight - top);
        Bitmap crop = Bitmap.createBitmap(source, left, top, width, height);
        Bitmap prepared = upscaleIfNeeded(crop, minLargestSide);
        if (prepared != crop) {
            crop.recycle();
        }
        double sourceQuality = Math.min(1d, 0.72d + (widthRatio * heightRatio * 0.45d));
        variants.add(new FrameVariant(
                prepared,
                rotationDegrees,
                scanBarcode,
                scanText,
                scanLatinText,
                true,
                sourceQuality,
                dateFocused,
                false,
                leftRatio,
                topRatio,
                widthRatio,
                heightRatio
        ));
    }

    private Bitmap upscaleIfNeeded(Bitmap bitmap, int minLargestSide) {
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest >= minLargestSide) {
            return bitmap;
        }
        float scale = Math.min(4.0f, minLargestSide / (float) largest);
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)),
                true
        );
    }

    private void appendRecognizedText(StringBuilder builder, String rawText) {
        String cleaned = RecognitionTextCleaner.cleanForPackagingOcr(rawText);
        if (cleaned.length() == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(cleaned);
    }

    private static String joinLines(List<String> lines) {
        StringBuilder joined = new StringBuilder();
        if (lines == null) {
            return "";
        }
        for (String line : lines) {
            String cleaned = FoodItem.cleanText(line);
            if (cleaned.length() == 0) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(cleaned);
        }
        return joined.toString();
    }

    private void appendRecognizedText(StringBuilder builder, Text text, FrameVariant variant) {
        if (builder == null || text == null || variant == null) {
            return;
        }
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box == null || !isAllowedEvidenceRegion(variant, box)) {
                    continue;
                }
                appendRecognizedText(builder, line.getText());
            }
        }
    }

    private boolean isLikelyPhoneScreenRecording(Bitmap bitmap, long durationUs) {
        if (bitmap == null || bitmap.isRecycled()
                || (!cameraSimulationActive && durationUs < 20000000L)) {
            return false;
        }
        int shortSide = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int longSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (shortSide <= 0) {
            return false;
        }
        double aspect = longSide / (double) shortSide;
        return aspect >= 2.02d && aspect <= 2.35d;
    }

    private void detectAppScreenRecording(Text text) {
        if (!videoReplayActive
                || cameraSimulationScreenRecording
                || appScreenRecordingDetected
                || text == null) {
            return;
        }
        String compact = FoodItem.cleanText(text.getText())
                .replace(" ", "")
                .replace("識", "识")
                .replace("別", "别")
                .replace("碼", "码")
                .replace("導", "导");
        for (String strongToken : new String[] {
                "智能识别", "识别结果", "填入表单", "填入新增表单", "查看原文", "视频样本",
                "采用所选结果", "今日简报", "手动新增", "新增食品", "提醒设置", "筛选食品"
        }) {
            if (compact.contains(strongToken)) {
                appScreenRecordingDetected = true;
                discardPreScreenRecordingEvidence();
                return;
            }
        }
        int matches = 0;
        for (String token : new String[] {
                "商品码", "已识别", "最终可食用", "继续识别", "候选", "导入", "导出"
        }) {
            if (compact.contains(token)) {
                matches++;
            }
        }
        appScreenRecordingDetected = matches >= 2
                || (compact.contains("识别结果") && compact.contains("商品码"));
        if (appScreenRecordingDetected) {
            discardPreScreenRecordingEvidence();
        }
    }

    private void discardPreScreenRecordingEvidence() {
        stabilizer.reset();
        latestSnapshot = stabilizer.snapshot();
        bestSessionSnapshot = null;
        latestRawText = "";
        videoTranscriptText.setLength(0);
        bestVideoDatePair = null;
        bestVideoDatePairScore = 0d;
        selectedPackagingName = "";
        paddleVideoFramesUsed = 0;
        paddleDetectionVideoFramesUsed = 0;
        paddleFocusedLabelVideoFramesUsed = 0;
        paddleCameraBandFramesUsed = 0;
        paddleDetectionCameraFramesUsed = 0;
        paddleDetectedLineCameraFramesUsed = 0;
        latestProductInfo = null;
        latestProductBarcode = "";
        productLookupInFlightBarcode = "";
        productLookupError = "";
        clearSessionKeyframes();
        final UnifiedRecognitionStabilizer.Snapshot emptySnapshot = latestSnapshot;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateResultUi(emptySnapshot);
            }
        });
    }

    private boolean isAllowedEvidenceRegion(FrameVariant variant, Rect box) {
        if (!appScreenRecordingDetected) {
            return true;
        }
        double localCenterY = box.exactCenterY() / Math.max(1d, variant.bitmap.getHeight());
        double sourceCenterY = variant.sourceTop + (localCenterY * variant.sourceHeight);
        return sourceCenterY >= 0.075d && sourceCenterY <= 0.64d;
    }

    private void appendTextObservations(
            List<PackagingTextAnalyzer.Observation> target,
            Text text,
            FrameVariant variant,
            String engine
    ) {
        int imageWidth = variant == null ? 0 : variant.bitmap.getWidth();
        int imageHeight = variant == null ? 0 : variant.bitmap.getHeight();
        if (target == null || text == null || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        int added = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                String value = FoodItem.cleanText(line.getText());
                if (box == null || value.length() == 0 || !isAllowedEvidenceRegion(variant, box)) {
                    continue;
                }
                double localLeft = box.left / (double) imageWidth;
                double localTop = box.top / (double) imageHeight;
                double localRight = box.right / (double) imageWidth;
                double localBottom = box.bottom / (double) imageHeight;
                double left = variant.sourceLeft + (localLeft * variant.sourceWidth);
                double top = variant.sourceTop + (localTop * variant.sourceHeight);
                double right = variant.sourceLeft + (localRight * variant.sourceWidth);
                double bottom = variant.sourceTop + (localBottom * variant.sourceHeight);
                double normalizedHeight = Math.max(0d, bottom - top);
                double normalizedWidth = Math.max(0d, right - left);
                double centerX = (left + right) / 2d;
                double centerY = (top + bottom) / 2d;
                double confidence = line.getConfidence();
                if (confidence <= 0d) {
                    confidence = Double.NaN;
                }
                target.add(new PackagingTextAnalyzer.Observation(
                        value,
                        normalizedHeight,
                        normalizedWidth,
                        centerX,
                        centerY,
                        variant.sourceQuality,
                        left,
                        top,
                        right,
                        bottom,
                        confidence,
                        engine
                ));
                added++;
            }
        }
        if (added == 0 && FoodItem.cleanText(text.getText()).length() > 0) {
            target.add(new PackagingTextAnalyzer.Observation(
                    text.getText(),
                    0.03d,
                    0.70d,
                    0.50d,
                    0.50d,
                    variant.sourceQuality * 0.65d,
                    variant.sourceLeft,
                    variant.sourceTop,
                    variant.sourceLeft + variant.sourceWidth,
                    variant.sourceTop + variant.sourceHeight,
                    Double.NaN,
                    engine
            ));
        }
    }

    private void recycleVariants(List<FrameVariant> variants) {
        for (FrameVariant variant : variants) {
            if (variant.recycleBitmap) {
                variant.bitmap.recycle();
            }
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Bitmap retrieveReplayFrame(MediaMetadataRetriever retriever, long frameUs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            int sourceWidth = videoMetadataInt(
                    retriever,
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            );
            int sourceHeight = videoMetadataInt(
                    retriever,
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            );
            int largestSide = Math.max(sourceWidth, sourceHeight);
            if (sourceWidth > 0 && sourceHeight > 0 && largestSide > 0) {
                double scale = Math.min(1d, VIDEO_MAX_FRAME_SIDE / (double) largestSide);
                int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
                int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
                try {
                    return retriever.getScaledFrameAtTime(
                            frameUs,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                            targetWidth,
                            targetHeight
                    );
                } catch (RuntimeException error) {
                    Log.d("ShiqiRecognition", "Scaled video frame unavailable; using full frame", error);
                }
            }
        }
        return retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST);
    }

    private int videoMetadataInt(MediaMetadataRetriever retriever, int key) {
        try {
            String value = retriever.extractMetadata(key);
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long videoDurationUs(MediaMetadataRetriever retriever) {
        try {
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Long.parseLong(value) * 1000L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void addEmbossedFrameCrop(
            List<FrameVariant> variants,
            Bitmap source,
            float leftRatio,
            float topRatio,
            float widthRatio,
            float heightRatio,
            int minLargestSide
    ) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int left = clamp(Math.round(sourceWidth * leftRatio), 0, sourceWidth - 1);
        int top = clamp(Math.round(sourceHeight * topRatio), 0, sourceHeight - 1);
        int width = clamp(Math.round(sourceWidth * widthRatio), 1, sourceWidth - left);
        int height = clamp(Math.round(sourceHeight * heightRatio), 1, sourceHeight - top);
        Bitmap crop = Bitmap.createBitmap(source, left, top, width, height);
        Bitmap prepared = upscaleIfNeeded(crop, minLargestSide);
        if (prepared != crop) {
            crop.recycle();
        }
        Bitmap enhanced = LowContrastTextPreprocessor.enhanceEmbossedText(prepared);
        prepared.recycle();
        if (enhanced != null) {
            variants.add(new FrameVariant(
                    enhanced,
                    0,
                    false,
                    true,
                    true,
                    true,
                    0.96d,
                    true,
                    true,
                    leftRatio,
                    topRatio,
                    widthRatio,
                    heightRatio
            ));
        }
    }

    private List<Long> videoFrameTimes(long durationUs, boolean longVideo) {
        return RecognitionFrameSelector.highRateVideoFrameTimes(durationUs, longVideo);
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
                showingEvidenceFrame = false;
                if (recognitionOverlay != null) {
                    recognitionOverlay.showEvidence(null, 0, 0);
                }
                clearLastReplayFrame();
                lastReplayFrame = bitmap;
                replayFrameView.setImageBitmap(bitmap);
            }
        });
    }

    private void updateVideoReplayCompleteState(int analyzedFrames) {
        String completionTranscript = videoTranscriptText.length() > 0
                ? videoTranscriptText.toString()
                : latestRawText;
        if (FoodItem.cleanText(completionTranscript).length() > 0) {
            latestRawText = completionTranscript;
            Log.i(
                    "ShiqiRecognition",
                    "Replay transcript=" + snippet(completionTranscript, 1200).replace('\n', '|')
            );
        }
        DateOcrParser.Result finalTranscriptDates = DateEvidencePolicy.chooseVideoCompletionEvidence(
                completionTranscript,
                bestVideoDatePair
        );
        DateOcrParser.Result evidenceDates = datePairFromSessionEvidence(latestSnapshot);
        if ((finalTranscriptDates.productionDates.isEmpty()
                || finalTranscriptDates.expiryDates.isEmpty())
                && !evidenceDates.productionDates.isEmpty()
                && !evidenceDates.expiryDates.isEmpty()) {
            finalTranscriptDates = evidenceDates;
        }
        promoteCompletionTranscriptEvidence(completionTranscript, finalTranscriptDates);
        String sessionProductName = bestProductNameFromSessionEvidence();
        if (sessionProductName.length() > 0) {
            stabilizer.promotePackagingCandidateForConfirmation(sessionProductName);
        }
        UnifiedRecognitionStabilizer.Snapshot snapshot;
        if (!finalTranscriptDates.productionDates.isEmpty()
                && !finalTranscriptDates.expiryDates.isEmpty()) {
            snapshot = stabilizer.promoteDirectDatePairForConfirmation(finalTranscriptDates);
        } else if (!finalTranscriptDates.productionDates.isEmpty()
                && !finalTranscriptDates.shelfLives.isEmpty()
                && !finalTranscriptDates.calculatedExpiryDates.isEmpty()) {
            snapshot = stabilizer.promoteCalculatedDateForConfirmation(finalTranscriptDates);
        } else {
            snapshot = stabilizer.snapshot();
        }
        snapshot = stabilizer.promoteStrongPackagingCandidateForConfirmation();
        snapshot = UnifiedRecognitionStabilizer.retainStableEvidence(
                UnifiedRecognitionStabilizer.retainStableEvidence(bestSessionSnapshot, latestSnapshot),
                snapshot
        );
        if (RecognitionTextCleaner.isNonProductTraceabilityCode(
                completionTranscript,
                snapshot.stableBarcode
        )) {
            snapshot = UnifiedRecognitionStabilizer.withoutBarcode(snapshot);
            latestSnapshot = UnifiedRecognitionStabilizer.withoutBarcode(latestSnapshot);
            bestSessionSnapshot = UnifiedRecognitionStabilizer.withoutBarcode(bestSessionSnapshot);
        }
        latestSnapshot = snapshot;
        rememberSessionSnapshot(snapshot);
        boolean partialCompletion = "部分完成".equals(statusBadge.getText().toString());
        updateResultUi(snapshot);
        startProductLookupIfNeeded(snapshot);
        if (partialCompletion) {
            statusBadge.setText("部分完成");
        } else {
            statusBadge.setText("分析完成");
        }
        if (snapshot.hasFillableCandidate()) {
            statusText.setText("识别完成，请核对下方结果。");
        } else if (!snapshot.rankedPackagingCandidates.isEmpty()) {
            statusText.setText("识别完成，请选择最符合包装的商品名。");
        } else if (analyzedFrames > 0) {
            statusText.setText("未找到可靠结果，请重新对准包装后再试。");
        } else {
            statusText.setText("没有可用画面，请重新对准包装后再试。");
        }
        List<SessionKeyframe> completedKeyframes = keyframeSnapshot();
        if (!completedKeyframes.isEmpty()) {
            showEvidenceFrame(completedKeyframes.get(0), false);
        }
        setRecognitionComplete(true);
        Log.i(
                "ShiqiRecognition",
                "Replay complete frames=" + analyzedFrames
                        + " fillable=" + snapshot.hasFillableCandidate()
                        + " barcode=" + snapshot.hasStableBarcode()
                        + " name=" + snapshot.hasStablePackagingName()
                        + " dates=" + (snapshot.stableDateVote != null)
                        + " completionProduction=" + (finalTranscriptDates.productionDates.isEmpty()
                        ? "" : finalTranscriptDates.productionDates.get(0).normalized)
                        + " completionExpiry=" + completionExpiry(finalTranscriptDates)
                        + " sessionName=" + sessionProductName
                        + " elapsedMs=" + recognitionElapsedMs()
        );
    }

    private String completionExpiry(DateOcrParser.Result result) {
        if (result == null) {
            return "";
        }
        if (!result.expiryDates.isEmpty()) {
            return result.expiryDates.get(0).normalized;
        }
        return result.calculatedExpiryDates.isEmpty()
                ? ""
                : result.calculatedExpiryDates.get(0).normalized;
    }

    private void refineIncompleteVideoDateFromKeyframe(
            MediaMetadataRetriever retriever,
            long durationUs
    ) {
        String transcript = videoTranscriptText.length() > 0
                ? videoTranscriptText.toString()
                : latestRawText;
        DateOcrParser.Result current = DateEvidencePolicy.apply(
                DateOcrParser.parse(transcript),
                "",
                false
        );
        if (!RecognitionFrameSelector.needsIncompleteDateRefinement(
                !current.productionDates.isEmpty(),
                !current.expiryDates.isEmpty(),
                !current.calculatedExpiryDates.isEmpty()
        )) {
            return;
        }

        SessionKeyframe keyframe = bestDateReviewKeyframe();
        if (keyframe == null || retriever == null) {
            return;
        }
        Log.i(
                "ShiqiRecognition",
                "Reviewing date keyframe timestampUs=" + keyframe.evidence.timestampUs
        );
        Bitmap focusedReviewFrame;
        RecognitionEvidence.NormalizedRect focusedReviewRect;
        long focusedReviewTimestampUs;
        synchronized (sessionKeyframes) {
            focusedReviewFrame = incompleteDateReviewFrame;
            focusedReviewRect = incompleteDateReviewRect;
            focusedReviewTimestampUs = incompleteDateReviewTimestampUs;
        }
        if (focusedReviewFrame != null && !focusedReviewFrame.isRecycled()) {
            Bitmap focusedPrepared = prepareStillImage(focusedReviewFrame);
            try {
                if (refineDateFromPreparedKeyframe(
                        focusedPrepared,
                        transcript,
                        focusedReviewTimestampUs,
                        focusedReviewRect
                )) {
                    return;
                }
            } finally {
                if (focusedPrepared != focusedReviewFrame && !focusedPrepared.isRecycled()) {
                    focusedPrepared.recycle();
                }
            }
        }
        Bitmap retainedPrepared = prepareStillImage(keyframe.bitmap);
        try {
            if (refineDateFromPreparedKeyframe(
                    retainedPrepared,
                    transcript,
                    keyframe.evidence.timestampUs,
                    null
            )) {
                return;
            }
        } finally {
            if (retainedPrepared != keyframe.bitmap && !retainedPrepared.isRecycled()) {
                retainedPrepared.recycle();
            }
        }
        long[] offsetsUs = new long[] {
                0L, -180000L, 180000L
        };
        for (long offsetUs : offsetsUs) {
            long frameTimeUs = Math.max(
                    0L,
                    Math.min(durationUs, keyframe.evidence.timestampUs + offsetUs)
            );
            Bitmap frame = retriever.getFrameAtTime(
                    frameTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
            );
            if (frame == null) {
                continue;
            }
            Bitmap cameraContent = cameraSimulationScreenRecording
                    ? cropRecordedCameraContent(frame)
                    : frame;
            if (cameraContent != frame && !frame.isRecycled()) {
                frame.recycle();
            }
            Bitmap prepared = prepareStillImage(cameraContent);
            if (prepared != cameraContent && !cameraContent.isRecycled()) {
                cameraContent.recycle();
            }
            try {
                if (refineDateFromPreparedKeyframe(prepared, transcript, frameTimeUs, null)) {
                    return;
                }
            } finally {
                if (!prepared.isRecycled()) {
                    prepared.recycle();
                }
            }
        }
    }

    private boolean refineDateFromPreparedKeyframe(
            Bitmap prepared,
            String transcript,
            long frameTimeUs,
            RecognitionEvidence.NormalizedRect dateTailRect
    ) {
        PaddleDateEvidence evidence = dateTailRect == null
                ? recognizeFocusedLaserExpiry(prepared)
                : recognizeObservedDateTail(prepared, dateTailRect);
        if (evidence.text.length() == 0) {
            return false;
        }
        DateOcrParser.Result refined = DateOcrParser.parseFocusedWithDateOnlySupplement(
                transcript,
                evidence.text
        );
        refined = DateEvidencePolicy.apply(refined, evidence.originalText, true);
        refined = DateEvidencePolicy.reconcileIndependentExpiryEvidence(
                refined,
                evidence.originalText,
                evidence.confidence
        );
        DateOcrFrameVoter.VoteResult vote = DateOcrFrameVoter.vote(
                Collections.singletonList(refined),
                1
        );
        if (vote.productionDate == null
                || vote.expiryDate == null
                || !isPlausibleFoodDateSpan(
                vote.productionDate.value,
                vote.expiryDate.value
        )) {
            return false;
        }
        appendRecognizedText(videoTranscriptText, evidence.text);
        bestVideoDatePair = refined;
        bestVideoDatePairScore = Math.max(bestVideoDatePairScore, 3d);
        latestSnapshot = UnifiedRecognitionStabilizer.retainStableEvidence(
                latestSnapshot,
                stabilizer.promoteDirectDatePairForConfirmation(refined)
        );
        Log.i(
                "ShiqiRecognition",
                "Static keyframe date review timestampUs=" + frameTimeUs
                        + " production=" + vote.productionDate.value
                        + " expiry=" + vote.expiryDate.value
        );
        return true;
    }

    private PaddleDateEvidence recognizeFocusedLaserExpiry(Bitmap bitmap) {
        PaddleLineOcrEngine engine = paddleLineOcrEngine;
        if (engine == null || bitmap == null || bitmap.isRecycled()) {
            return PaddleDateEvidence.empty();
        }
        // Try the exact, unscaled strips first. Upscaling and wide crops can erase faint
        // dot-matrix strokes; these narrow strips match the successful still-image path.
        float[][] priorityBands = new float[][] {
                {0.42f, 0.72f, 0.38f, 0.08f},
                {0.56f, 0.72f, 0.38f, 0.08f},
                {0.42f, 0.65f, 0.38f, 0.08f},
                {0.56f, 0.65f, 0.38f, 0.08f},
                {0.42f, 0.58f, 0.38f, 0.08f},
                {0.02f, 0.72f, 0.38f, 0.08f}
        };
        for (float[] band : priorityBands) {
            PaddleDateEvidence evidence = recognizeExactDateBand(
                    engine,
                    bitmap,
                    band[0],
                    band[1],
                    band[2],
                    band[3]
            );
            if (evidence.text.length() > 0) {
                return evidence;
            }
        }

        // Keep a bounded fallback for labels whose date strip is elsewhere on the package.
        return recognizePaddleDateBands(bitmap, true, false, true);
    }

    private PaddleDateEvidence recognizeExactDateBand(
            PaddleLineOcrEngine engine,
            Bitmap bitmap,
            float leftRatio,
            float topRatio,
            float widthRatio,
            float heightRatio
    ) {
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int left = clamp(Math.round(imageWidth * leftRatio), 0, imageWidth - 1);
        int top = clamp(Math.round(imageHeight * topRatio), 0, imageHeight - 1);
        int width = clamp(Math.round(imageWidth * widthRatio), 1, imageWidth - left);
        int height = clamp(Math.round(imageHeight * heightRatio), 1, imageHeight - top);
        Bitmap crop = Bitmap.createBitmap(bitmap, left, top, width, height);
        try {
            PaddleLineOcrEngine.LineResult result = engine.recognizeResult(crop);
            if (result == null || result.confidence < 0.55d) {
                return PaddleDateEvidence.empty();
            }
            DateOcrParser.Result parsed = DateOcrParser.parse(result.text);
            if (parsed.expiryDates.isEmpty()) {
                return PaddleDateEvidence.empty();
            }
            Log.i(
                    "ShiqiRecognition",
                    "Focused laser expiry text=" + result.text
                            + " confidence="
                            + String.format(Locale.US, "%.2f", result.confidence)
                            + " left=" + String.format(Locale.US, "%.2f", leftRatio)
                            + " top=" + String.format(Locale.US, "%.2f", topRatio)
            );
            return new PaddleDateEvidence(
                    result.text,
                    result.text,
                    result.confidence,
                    new RecognitionEvidence.NormalizedRect(
                            left / (double) imageWidth,
                            top / (double) imageHeight,
                            (left + width) / (double) imageWidth,
                            (top + height) / (double) imageHeight
                    )
            );
        } finally {
            if (!crop.isRecycled()) {
                crop.recycle();
            }
        }
    }

    private SessionKeyframe bestDateReviewKeyframe() {
        SessionKeyframe best = null;
        for (SessionKeyframe keyframe : keyframeSnapshot()) {
            boolean hasProduction = false;
            for (RecognitionEvidence.Region region : keyframe.evidence.regions) {
                if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(region.field)) {
                    hasProduction = true;
                }
            }
            if (!hasProduction) {
                continue;
            }
            if (best == null
                    || keyframe.evidence.timestampUs < best.evidence.timestampUs) {
                best = keyframe;
            }
        }
        return best;
    }

    private Bitmap cropRecordedCameraContent(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return bitmap;
        }
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int left = clamp(Math.round(imageWidth * 0.04f), 0, imageWidth - 1);
        int top = clamp(Math.round(imageHeight * 0.075f), 0, imageHeight - 1);
        int width = clamp(Math.round(imageWidth * 0.92f), 1, imageWidth - left);
        int height = clamp(Math.round(imageHeight * 0.565f), 1, imageHeight - top);
        return Bitmap.createBitmap(bitmap, left, top, width, height);
    }

    private void stopVideoReplay() {
        videoReplayActive = false;
        cameraSimulationActive = false;
        cameraSimulationScreenRecording = false;
        longVideoProfile = false;
        videoDetailFrame = false;
    }

    private void resetRecognitionState() {
        Log.i(
                "ShiqiRecognition",
                "Reset recognition generation=" + (recognitionGeneration + 1)
                        + " videoActive=" + videoReplayActive
        );
        recognitionGeneration++;
        clearPendingCameraFrame();
        stabilizer.reset();
        latestSnapshot = stabilizer.snapshot();
        latestRawText = "";
        videoTranscriptText.setLength(0);
        bestVideoDatePair = null;
        bestVideoDatePairScore = 0d;
        currentVideoFrameRatio = 0d;
        selectedPackagingName = "";
        clearSessionKeyframes();
        currentFrameTimestampUs = 0L;
        appScreenRecordingDetected = false;
        nextFrameId = 0L;
        showingEvidenceFrame = false;
        analyzedFrameSequence = 0;
        recognitionFirstFrameAtMs = 0L;
        paddleVideoFramesUsed = 0;
        paddleDetectionVideoFramesUsed = 0;
        paddleFocusedLabelVideoFramesUsed = 0;
        paddleCameraBandFramesUsed = 0;
        paddleDetectionCameraFramesUsed = 0;
        paddleDetectedLineCameraFramesUsed = 0;
        videoAnalyzedFrames = 0;
        videoExpectedFrames = 0;
        analysisInFlight = false;
        liveCameraResultFrozen = false;
        lastAnalyzeAt = 0L;
        latestProductInfo = null;
        latestProductBarcode = "";
        productLookupInFlightBarcode = "";
        productLookupError = "";
        if (fillButton != null) {
            fillButton.setEnabled(false);
            fillButton.setAlpha(0.45f);
        }
        setRecognitionComplete(false);
        updateResultUi(latestSnapshot);
    }

    private Bitmap prepareStillImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largest = Math.max(width, height);
        if (largest <= 0) {
            return bitmap;
        }
        float scale = 1f;
        if (largest > STILL_IMAGE_MAX_SIDE) {
            scale = STILL_IMAGE_MAX_SIDE / (float) largest;
        } else if (largest < STILL_IMAGE_MIN_SIDE) {
            scale = Math.min(8f, STILL_IMAGE_MIN_SIDE / (float) largest);
        }
        if (Math.abs(scale - 1f) < 0.001f) {
            return bitmap;
        }
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
    }

    private boolean isLikelyDateStrip(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int shortSide = Math.min(width, height);
        int longSide = Math.max(width, height);
        return shortSide > 0 && longSide / (double) shortSide >= 2.2d;
    }

    private PaddleDateEvidence recognizePaddleDateStrip(
            PaddleLineOcrEngine engine,
            Bitmap bitmap
    ) {
        final float lowConfidenceDateThreshold = 0.25f;
        List<PaddleLineOcrEngine.LineResult> results =
                new ArrayList<PaddleLineOcrEngine.LineResult>();
        StringBuilder recognized = new StringBuilder();
        StringBuilder original = new StringBuilder();
        double bestConfidence = 0d;

        results.add(engine.recognizeResult(bitmap, lowConfidenceDateThreshold));

        Bitmap laser = LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap);
        if (laser != null) {
            try {
                results.add(engine.recognizeResult(laser, lowConfidenceDateThreshold));
            } finally {
                laser.recycle();
            }
        }

        Bitmap embossed = LowContrastTextPreprocessor.enhanceEmbossedText(bitmap);
        if (embossed != null) {
            try {
                results.add(engine.recognizeResult(embossed, lowConfidenceDateThreshold));
            } finally {
                embossed.recycle();
            }
        }

        Bitmap binary = LowContrastTextPreprocessor.binarizeText(bitmap, false);
        if (binary != null) {
            try {
                results.add(engine.recognizeResult(binary, lowConfidenceDateThreshold));
            } finally {
                binary.recycle();
            }
        }

        Bitmap inverted = LowContrastTextPreprocessor.binarizeText(bitmap, true);
        if (inverted != null) {
            try {
                results.add(engine.recognizeResult(inverted, lowConfidenceDateThreshold));
            } finally {
                inverted.recycle();
            }
        }

        Bitmap brightText = LowContrastTextPreprocessor.isolateBrightText(bitmap);
        if (brightText != null) {
            try {
                results.add(engine.recognizeResult(brightText, lowConfidenceDateThreshold));
            } finally {
                brightText.recycle();
            }
        }

        for (PaddleLineOcrEngine.LineResult result : results) {
            if (result == null || !DateOcrParser.parse(result.text).hasAnyCandidate()) {
                continue;
            }
            appendRecognizedText(recognized, result.text);
            if (original.length() == 0) {
                appendRecognizedText(original, result.text);
            }
            bestConfidence = Math.max(bestConfidence, result.confidence);
        }

        RecognitionEvidence.NormalizedRect fullFrame = recognized.length() == 0
                ? null
                : new RecognitionEvidence.NormalizedRect(0d, 0d, 1d, 1d);
        return new PaddleDateEvidence(
                recognized.toString(),
                original.toString(),
                bestConfidence,
                fullFrame
        );
    }

    private void clearSessionKeyframes() {
        synchronized (sessionKeyframes) {
            for (SessionKeyframe keyframe : sessionKeyframes) {
                if (keyframe.bitmap != null && !keyframe.bitmap.isRecycled()) {
                    keyframe.bitmap.recycle();
                }
            }
            sessionKeyframes.clear();
            frameSelector.clear();
            if (incompleteDateReviewFrame != null && !incompleteDateReviewFrame.isRecycled()) {
                incompleteDateReviewFrame.recycle();
            }
            incompleteDateReviewFrame = null;
            incompleteDateReviewRect = null;
            incompleteDateReviewTimestampUs = 0L;
            incompleteDateReviewScore = 0d;
            focusedDateTailAttempts = 0;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (keyframeContainer != null) {
                    keyframeContainer.removeAllViews();
                }
                if (recognitionOverlay != null) {
                    recognitionOverlay.showEvidence(null, 0, 0);
                }
            }
        });
    }

    private void clearLastReplayFrame() {
        if (lastReplayFrame != null) {
            lastReplayFrame.recycle();
            lastReplayFrame = null;
        }
    }

    private void startCamera() {
        if (previewView == null || cameraExecutor == null || cameraAnalyzerExecutor == null) {
            return;
        }

        final int cameraGeneration = recognitionGeneration;
        final ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    if (cameraGeneration != recognitionGeneration) {
                        return;
                    }
                    cameraProvider = providerFuture.get();
                    if (!hasBackCamera(cameraProvider)) {
                        showCameraUnavailable("未找到可用后置相机，请检查设备相机状态。");
                        return;
                    }
                    bindCameraUseCases(cameraProvider);
                    cameraBound = true;
                    statusBadge.setText("相机");
                    statusText.setText(barcodeLookupOnly
                            ? "请对准商品条码，连续两帧一致后会自动查找。"
                            : dateOnlyMode
                            ? "高帧扫描已启动。保持约 15–25 厘米，让日期小字清晰占满识别框。"
                            : "高帧扫描已启动，正在优先识别中文商品名、条码和包装日期。");
                } catch (Exception error) {
                    showCameraUnavailable("相机启动失败，请检查相机权限或重新打开应用。");
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
        statusBadge.setText("无相机");
        statusText.setText(message);
        updateResultUi(stabilizer.snapshot());
        stopCamera();
    }

    private void bindCameraUseCases(ProcessCameraProvider provider) {
        provider.unbindAll();

        int targetRotation = previewView.getDisplay() == null
                ? Surface.ROTATION_0
                : previewView.getDisplay().getRotation();
        Preview preview = new Preview.Builder()
                .setTargetRotation(targetRotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(
                        new Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                ))
                .build();
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraAnalyzerExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(ImageProxy imageProxy) {
                analyzeImage(imageProxy);
            }
        });

        camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
        );
        try {
            camera.getCameraControl().setLinearZoom(dateOnlyMode ? 0.12f : 0.06f);
        } catch (RuntimeException ignored) {
            // Some fixed-focus and virtual cameras expose no usable zoom range.
        }
        configureCameraFocus(camera);
    }

    private void configureCameraFocus(final Camera boundCamera) {
        if (previewView == null || boundCamera == null) {
            return;
        }
        previewView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return true;
                }
                startFocusAndMetering(boundCamera, event.getX(), event.getY(), true);
                return true;
            }
        });
        previewView.post(new Runnable() {
            @Override
            public void run() {
                startFocusAndMetering(
                        boundCamera,
                        previewView.getWidth() / 2f,
                        previewView.getHeight() * 0.48f,
                        false
                );
            }
        });
    }

    private void startFocusAndMetering(final Camera boundCamera, float x, float y, final boolean announceResult) {
        if (previewView == null || boundCamera == null || previewView.getWidth() <= 0 || previewView.getHeight() <= 0) {
            return;
        }
        MeteringPoint point = previewView.getMeteringPointFactory().createPoint(x, y, 0.18f);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB
        ).setAutoCancelDuration(8, TimeUnit.SECONDS).build();
        if (!boundCamera.getCameraInfo().isFocusMeteringSupported(action)) {
            if (announceResult) {
                statusText.setText("此设备不支持点按对焦，请保持包装稳定并靠近小字。");
            }
            return;
        }
        if (announceResult) {
            statusText.setText("正在对焦包装小字…");
        }
        final ListenableFuture<FocusMeteringResult> focusFuture =
                boundCamera.getCameraControl().startFocusAndMetering(action);
        focusFuture.addListener(new Runnable() {
            @Override
            public void run() {
                if (!announceResult || boundCamera != camera) {
                    return;
                }
                try {
                    statusText.setText(focusFuture.get().isFocusSuccessful()
                            ? "对焦完成，请保持包装稳定并让小字占满识别框。"
                            : "对焦未锁定，请靠近包装小字后再轻点一次。");
                } catch (Exception error) {
                    statusText.setText("对焦失败，请保持镜头稳定后再轻点包装小字。");
                }
            }
        }, mainExecutor);
    }

    private void stopCamera() {
        cameraBound = false;
        if (previewView != null) {
            previewView.setOnTouchListener(null);
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        camera = null;
        clearPendingCameraFrame();
    }

    private void analyzeImage(final ImageProxy imageProxy) {
        long now = SystemClock.uptimeMillis();
        if (!RecognitionFrameSelector.shouldCaptureLatestCameraFrame(
                cameraBound,
                now,
                lastAnalyzeAt,
                CAMERA_CAPTURE_INTERVAL_MS,
                analysisInFlight
        )) {
            imageProxy.close();
            return;
        }
        lastAnalyzeAt = now;
        final int frameGeneration = recognitionGeneration;
        final long frameTimestampUs = SystemClock.elapsedRealtime() * 1000L;

        Bitmap oriented = null;
        try {
            oriented = copyUprightBitmap(imageProxy);
            offerLatestCameraFrame(oriented, frameTimestampUs, frameGeneration);
            oriented = null;
        } catch (Exception error) {
            Log.w("ShiqiRecognition", "Camera frame capture failed", error);
        } finally {
            if (oriented != null && !oriented.isRecycled()) {
                oriented.recycle();
            }
        }
    }

    private void offerLatestCameraFrame(Bitmap bitmap, long timestampUs, int generation) {
        ExecutorService recognitionExecutor = cameraExecutor;
        if (bitmap == null || bitmap.isRecycled() || recognitionExecutor == null) {
            recycleBitmap(bitmap);
            return;
        }
        boolean scheduleWorker = false;
        synchronized (pendingCameraFrameLock) {
            if (!cameraBound || generation != recognitionGeneration) {
                recycleBitmap(bitmap);
                return;
            }
            recycleBitmap(pendingCameraFrame);
            pendingCameraFrame = bitmap;
            pendingCameraFrameTimestampUs = timestampUs;
            pendingCameraFrameGeneration = generation;
            if (!cameraRecognitionScheduled) {
                cameraRecognitionScheduled = true;
                scheduleWorker = true;
            }
        }
        if (!scheduleWorker) {
            return;
        }
        try {
            recognitionExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    drainLatestCameraFrames();
                }
            });
        } catch (RuntimeException error) {
            synchronized (pendingCameraFrameLock) {
                cameraRecognitionScheduled = false;
            }
            clearPendingCameraFrame();
        }
    }

    private void drainLatestCameraFrames() {
        while (true) {
            Bitmap frame;
            long timestampUs;
            int generation;
            synchronized (pendingCameraFrameLock) {
                frame = pendingCameraFrame;
                timestampUs = pendingCameraFrameTimestampUs;
                generation = pendingCameraFrameGeneration;
                pendingCameraFrame = null;
                if (frame == null) {
                    cameraRecognitionScheduled = false;
                    analysisInFlight = false;
                    return;
                }
                analysisInFlight = true;
            }
            try {
                if (cameraBound && generation == recognitionGeneration) {
                    currentFrameTimestampUs = timestampUs;
                    analyzeBitmapFrame(frame, false, true, generation);
                }
            } catch (Exception error) {
                Log.w("ShiqiRecognition", "Camera frame recognition failed", error);
            } finally {
                recycleBitmap(frame);
            }
        }
    }

    private void clearPendingCameraFrame() {
        synchronized (pendingCameraFrameLock) {
            recycleBitmap(pendingCameraFrame);
            pendingCameraFrame = null;
            pendingCameraFrameTimestampUs = 0L;
            pendingCameraFrameGeneration = 0;
        }
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private Bitmap copyUprightBitmap(ImageProxy imageProxy) {
        Bitmap source;
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        try {
            source = imageProxy.toBitmap();
        } finally {
            imageProxy.close();
        }
        int normalizedRotation = ((rotationDegrees % 360) + 360) % 360;
        if (normalizedRotation == 0) {
            return source;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(normalizedRotation);
        try {
            Bitmap upright = Bitmap.createBitmap(
                    source,
                    0,
                    0,
                    source.getWidth(),
                    source.getHeight(),
                    matrix,
                    true
            );
            if (upright != source) {
                source.recycle();
            }
            return upright;
        } catch (RuntimeException error) {
            source.recycle();
            throw error;
        }
    }

    private String extractProductBarcode(List<Barcode> barcodes) {
        if (barcodes == null) {
            return "";
        }
        for (Barcode barcode : barcodes) {
            if (barcode == null) {
                continue;
            }
            boolean retailSymbology = barcode.getFormat() == Barcode.FORMAT_EAN_13
                    || barcode.getFormat() == Barcode.FORMAT_EAN_8
                    || barcode.getFormat() == Barcode.FORMAT_UPC_A
                    || barcode.getFormat() == Barcode.FORMAT_UPC_E;
            String productCode = BarcodeUtils.productCodeFromScannerValue(
                    barcode.getRawValue(),
                    retailSymbology
            );
            if (BarcodeUtils.isSupportedProductCode(productCode)) {
                return productCode;
            }
            productCode = BarcodeUtils.productCodeFromScannerValue(
                    barcode.getDisplayValue(),
                    retailSymbology
            );
            if (BarcodeUtils.isSupportedProductCode(productCode)) {
                return productCode;
            }
        }
        return "";
    }

    private void handleRecognitionResult(String barcode, String rawText, boolean singleFrameConfirmation) {
        handleRecognitionResult(
                barcode,
                rawText,
                PackagingTextAnalyzer.analyze(observationsFromRawText(rawText)),
                singleFrameConfirmation
        );
    }

    private void handleRecognitionResult(
            String barcode,
            String rawText,
            List<PackagingTextAnalyzer.Candidate> packagingCandidates,
            boolean singleFrameConfirmation
    ) {
        handleRecognitionResult(barcode, rawText, packagingCandidates, singleFrameConfirmation, rawText);
    }

    private void handleRecognitionResult(
            String barcode,
            String rawText,
            List<PackagingTextAnalyzer.Candidate> packagingCandidates,
            boolean singleFrameConfirmation,
            String dateRawText
    ) {
        handleRecognitionResult(
                barcode,
                rawText,
                packagingCandidates,
                singleFrameConfirmation,
                dateRawText,
                ""
        );
    }

    private void handleRecognitionResult(
            String barcode,
            String rawText,
            List<PackagingTextAnalyzer.Candidate> packagingCandidates,
            boolean singleFrameConfirmation,
            String dateRawText,
            String supplementaryDateText
    ) {
        handleRecognitionResult(
                barcode,
                rawText,
                packagingCandidates,
                singleFrameConfirmation,
                dateRawText,
                supplementaryDateText,
                null,
                false,
                recognitionGeneration
        );
    }

    private void handleRecognitionResult(
            String barcode,
            String rawText,
            List<PackagingTextAnalyzer.Candidate> packagingCandidates,
            boolean singleFrameConfirmation,
            String dateRawText,
            String supplementaryDateText,
            DateOcrParser.Result parsedOverride,
            boolean liveCamera,
            int expectedGeneration
    ) {
        if (expectedGeneration != recognitionGeneration) {
            return;
        }
        String cleanedText = FoodItem.cleanText(rawText);
        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
            barcode = RecognitionTextCleaner.extractProductCodeFromOcr(cleanedText);
        }
        DateOcrParser.Result parsed = parsedOverride == null
                ? DateOcrParser.parseFocusedWithDateOnlySupplement(
                FoodItem.cleanText(dateRawText),
                FoodItem.cleanText(supplementaryDateText)
        )
                : parsedOverride;
        rememberBestVideoDatePair(parsed);
        if (expectedGeneration != recognitionGeneration) {
            return;
        }
        latestSnapshot = UnifiedRecognitionStabilizer.retainStableEvidence(
                latestSnapshot,
                stabilizer.addFrame(
                barcode,
                parsed,
                cleanedText,
                packagingCandidates,
                singleFrameConfirmation
                )
        );
        rememberSessionSnapshot(latestSnapshot);

        if (cleanedText.length() > 0) {
            latestRawText = cleanedText;
            if (videoReplayActive && videoTranscriptText.length() < 32000) {
                appendRecognizedText(videoTranscriptText, cleanedText);
            }
        }
        final UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
        final int uiGeneration = expectedGeneration;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (uiGeneration != recognitionGeneration) {
                    return;
                }
                updateResultUi(snapshot);
                startProductLookupIfNeeded(snapshot);
            }
        });
    }

    private void rememberBestVideoDatePair(DateOcrParser.Result parsed) {
        if (!videoReplayActive || parsed == null) {
            return;
        }
        DateOcrFrameVoter.VoteResult direct = DateOcrFrameVoter.vote(
                Collections.singletonList(parsed),
                1
        );
        if (!UnifiedRecognitionStabilizer.isReliableDirectDatePair(direct)
                || !isPlausibleFoodDateSpan(
                direct.productionDate.value,
                direct.expiryDate.value
        )) {
            return;
        }
        double score = direct.productionDate.confidence
                + direct.expiryDate.confidence
                + (Math.min(4, direct.productionDate.votes) * 0.12d)
                + (Math.min(4, direct.expiryDate.votes) * 0.12d);
        if (bestVideoDatePair == null || score > bestVideoDatePairScore) {
            bestVideoDatePair = parsed;
            bestVideoDatePairScore = score;
        }
    }

    private boolean isPlausibleFoodDateSpan(String productionDate, String expiryDate) {
        int days = DateRules.daysBetween(productionDate, expiryDate);
        return days >= 0 && days <= (366 * 5);
    }

    private List<PackagingTextAnalyzer.Observation> observationsFromRawText(String rawText) {
        List<PackagingTextAnalyzer.Observation> observations =
                new ArrayList<PackagingTextAnalyzer.Observation>();
        String cleaned = RecognitionTextCleaner.cleanForPackagingOcr(rawText);
        for (String line : cleaned.split("\\r?\\n")) {
            String value = FoodItem.cleanText(line);
            if (value.length() > 0) {
                observations.add(new PackagingTextAnalyzer.Observation(
                        value,
                        0.035d,
                        Math.min(0.9d, 0.12d + value.length() * 0.035d),
                        0.5d,
                        0.5d,
                        0.55d
                ));
            }
        }
        return observations;
    }

    private void rememberEvidenceFrame(
            Bitmap bitmap,
            long frameId,
            long timestampUs,
            LowContrastTextPreprocessor.FrameMetrics metrics,
            List<PackagingTextAnalyzer.Observation> observations,
            List<PackagingTextAnalyzer.Candidate> packagingCandidates,
            DateOcrParser.Result parsed,
            String barcode
    ) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        double textCoverage = 0d;
        double confidenceTotal = 0d;
        int confidenceCount = 0;
        for (PackagingTextAnalyzer.Observation observation : observations) {
            textCoverage += Math.max(0d, observation.right - observation.left)
                    * Math.max(0d, observation.bottom - observation.top);
            if (!Double.isNaN(observation.modelConfidence)) {
                confidenceTotal += observation.modelConfidence;
                confidenceCount++;
            }
        }
        double averageModelConfidence = confidenceCount == 0
                ? Double.NaN
                : confidenceTotal / confidenceCount;
        double coverageScore = Math.min(1d, textCoverage / 0.18d);
        double selectorQuality = RecognitionFrameSelector.qualityScore(
                metrics.sharpness,
                metrics.brightness,
                metrics.glareRatio,
                coverageScore,
                Double.isNaN(averageModelConfidence) ? 0.5d : averageModelConfidence,
                videoReplayActive ? currentVideoFrameRatio : 0.5d
        );
        double frameQuality = RecognitionEvidence.clamp01(
                (selectorQuality * 0.82d)
                        + (metrics.contrast * 0.18d)
        );

        List<RecognitionEvidence.Region> regions = new ArrayList<RecognitionEvidence.Region>();
        if (packagingCandidates != null && !packagingCandidates.isEmpty()) {
            RecognitionEvidence.Region productRegion = productRegion(
                    packagingCandidates.get(0),
                    observations,
                    frameQuality
            );
            if (productRegion != null) {
                regions.add(productRegion);
            }
        }
        addDateRegion(regions, RecognitionEvidence.FIELD_PRODUCTION_DATE, parsed.productionDates, observations, frameQuality);
        addDateRegion(regions, RecognitionEvidence.FIELD_EXPIRY_DATE, parsed.expiryDates, observations, frameQuality);
        if (BarcodeUtils.isSupportedProductCode(barcode)) {
            RecognitionEvidence.NormalizedRect barcodeRect = findBarcodeRect(barcode, observations);
            if (barcodeRect != null) {
                regions.add(new RecognitionEvidence.Region(
                        RecognitionEvidence.FIELD_BARCODE,
                        barcode,
                        barcodeRect,
                        "ML Kit 条码",
                        Double.NaN,
                        RecognitionEvidence.fusedConfidence(Double.NaN, frameQuality, 0.96d, 1, 1)
                ));
            }
        }
        if (regions.isEmpty() || frameQuality < 0.30d) {
            return;
        }

        int evidenceMask = evidenceMask(regions);
        RecognitionFrameSelector.FrameCandidate frameCandidate =
                new RecognitionFrameSelector.FrameCandidate(
                        Long.toString(frameId),
                        timestampUs / 1000L,
                        Long.toHexString(metrics.signature),
                        metrics.sharpness,
                        metrics.brightness,
                        metrics.glareRatio,
                        coverageScore,
                        Double.isNaN(averageModelConfidence) ? 0.5d : averageModelConfidence,
                        videoReplayActive ? currentVideoFrameRatio : 0.5d,
                        evidenceMask,
                        primaryEvidence(regions)
                );

        RecognitionEvidence.Frame evidenceFrame = new RecognitionEvidence.Frame(
                frameId,
                timestampUs,
                bitmap.getWidth(),
                bitmap.getHeight(),
                frameQuality,
                metrics.signature,
                regions
        );
        Bitmap retained = retainEvidenceBitmap(bitmap);
        if (retained == null) {
            return;
        }
        offerSessionKeyframe(new SessionKeyframe(evidenceFrame, retained, frameCandidate));
    }

    private RecognitionEvidence.NormalizedRect bestIncompleteDateTailRect(
            List<PackagingTextAnalyzer.Observation> observations
    ) {
        if (observations == null || observations.isEmpty()) {
            return null;
        }
        PackagingTextAnalyzer.Observation best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PackagingTextAnalyzer.Observation observation : observations) {
            String text = FoodItem.cleanText(observation.text);
            if (!looksLikeDateLine(text)
                    || !looksLikeIncompleteDatePairText(text)) {
                continue;
            }
            double score = dateLinePriority(observation);
            if (best == null || score > bestScore) {
                best = observation;
                bestScore = score;
            }
        }
        if (best == null) {
            return null;
        }
        RecognitionEvidence.NormalizedRect line = observationRect(best);
        double width = Math.max(0.02d, line.right - line.left);
        double height = Math.max(0.01d, line.bottom - line.top);
        double verticalPadding = Math.max(0.010d, Math.min(0.018d, height * 0.12d));
        double focusedHeight = Math.max(0.055d, Math.min(0.20d, height * 1.05d));
        RecognitionEvidence.NormalizedRect tail = new RecognitionEvidence.NormalizedRect(
                line.left - Math.max(0.025d, width * 0.08d),
                line.top - verticalPadding,
                line.right + Math.max(0.060d, width * 0.50d),
                line.top + focusedHeight + verticalPadding
        );
        if (isDebuggable()) {
            Log.d(
                    "ShiqiRecognition",
                    "Date tail rect text=" + best.text
                            + " left=" + String.format(Locale.US, "%.3f", tail.left)
                            + " top=" + String.format(Locale.US, "%.3f", tail.top)
                            + " right=" + String.format(Locale.US, "%.3f", tail.right)
                            + " bottom=" + String.format(Locale.US, "%.3f", tail.bottom)
            );
        }
        return tail;
    }

    private PaddleDateEvidence recognizeObservedDateTail(
            Bitmap bitmap,
            RecognitionEvidence.NormalizedRect rect
    ) {
        PaddleLineOcrEngine engine = paddleLineOcrEngine;
        if (engine == null || bitmap == null || bitmap.isRecycled() || rect == null) {
            return PaddleDateEvidence.empty();
        }
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int left = clamp((int) Math.floor(rect.left * imageWidth), 0, imageWidth - 1);
        int top = clamp((int) Math.floor(rect.top * imageHeight), 0, imageHeight - 1);
        int right = clamp((int) Math.ceil(rect.right * imageWidth), left + 1, imageWidth);
        int bottom = clamp((int) Math.ceil(rect.bottom * imageHeight), top + 1, imageHeight);
        Bitmap crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        Bitmap prepared = upscaleIfNeeded(crop, 560);
        if (prepared != crop) {
            crop.recycle();
        }
        List<PaddleLineOcrEngine.LineResult> results =
                new ArrayList<PaddleLineOcrEngine.LineResult>();
        Bitmap laserPrepared = LowContrastTextPreprocessor.enhanceLaserPrintedText(prepared);
        Bitmap brightPrepared = LowContrastTextPreprocessor.isolateBrightText(prepared);
        try {
            results.add(engine.recognizeResult(prepared, 0.25f));
            for (float degrees : new float[] {-30f, -25f, -20f, -15f}) {
                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(
                        prepared,
                        0,
                        0,
                        prepared.getWidth(),
                        prepared.getHeight(),
                        matrix,
                        true
                );
                try {
                    results.add(engine.recognizeResult(rotated, 0.25f));
                    appendMlKitDateResult(results, textRecognizer, rotated);
                    appendMlKitDateResult(results, latinTextRecognizer, rotated);
                    if (laserPrepared != null) {
                        Bitmap laser = Bitmap.createBitmap(
                                laserPrepared,
                                0,
                                0,
                                laserPrepared.getWidth(),
                                laserPrepared.getHeight(),
                                matrix,
                                true
                        );
                        try {
                            results.add(engine.recognizeResult(laser, 0.25f));
                        } finally {
                            laser.recycle();
                        }
                    }
                    if (brightPrepared != null) {
                        Bitmap bright = Bitmap.createBitmap(
                                brightPrepared,
                                0,
                                0,
                                brightPrepared.getWidth(),
                                brightPrepared.getHeight(),
                                matrix,
                                true
                        );
                        try {
                            results.add(engine.recognizeResult(bright, 0.25f));
                            appendMlKitDateResult(results, latinTextRecognizer, bright);
                        } finally {
                            bright.recycle();
                        }
                    }
                } finally {
                    if (!rotated.isRecycled()) {
                        rotated.recycle();
                    }
                }
            }
        } finally {
            if (laserPrepared != null && !laserPrepared.isRecycled()) {
                laserPrepared.recycle();
            }
            if (brightPrepared != null && !brightPrepared.isRecycled()) {
                brightPrepared.recycle();
            }
            if (!prepared.isRecycled()) {
                prepared.recycle();
            }
        }

        List<String> expiryValues = new ArrayList<String>();
        List<String> representativeTexts = new ArrayList<String>();
        List<Integer> voteCounts = new ArrayList<Integer>();
        List<Double> bestConfidences = new ArrayList<Double>();
        for (PaddleLineOcrEngine.LineResult result : results) {
            if (result == null || result.text.length() == 0) {
                continue;
            }
            if (isDebuggable()) {
                Log.d(
                        "ShiqiRecognition",
                        "Focused date tail text=" + result.text
                                + " confidence="
                                + String.format(Locale.US, "%.2f", result.confidence)
                );
            }
            DateOcrParser.Result parsed = DateOcrParser.parse(result.text);
            if (parsed.expiryDates.isEmpty()
                    || result.confidence < 0.55d) {
                continue;
            }
            DateOcrParser.DateCandidate expiry = parsed.expiryDates.get(0);
            if (!hasDayPrecisionDateOcr(expiry.raw)) {
                continue;
            }
            int existing = expiryValues.indexOf(expiry.normalized);
            if (existing < 0) {
                expiryValues.add(expiry.normalized);
                representativeTexts.add(result.text);
                voteCounts.add(Integer.valueOf(1));
                bestConfidences.add(Double.valueOf(result.confidence));
            } else {
                voteCounts.set(existing, Integer.valueOf(voteCounts.get(existing).intValue() + 1));
                if (result.confidence > bestConfidences.get(existing).doubleValue()) {
                    bestConfidences.set(existing, Double.valueOf(result.confidence));
                    representativeTexts.set(existing, result.text);
                }
            }
        }
        int bestIndex = -1;
        for (int index = 0; index < expiryValues.size(); index++) {
            if (bestIndex < 0
                    || voteCounts.get(index).intValue() > voteCounts.get(bestIndex).intValue()
                    || (voteCounts.get(index).intValue() == voteCounts.get(bestIndex).intValue()
                    && bestConfidences.get(index).doubleValue()
                    > bestConfidences.get(bestIndex).doubleValue())) {
                bestIndex = index;
            }
        }
        if (bestIndex < 0) {
            return PaddleDateEvidence.empty();
        }
        int winningVotes = voteCounts.get(bestIndex).intValue();
        double winningConfidence = bestConfidences.get(bestIndex).doubleValue();
        if (winningVotes < 2 && winningConfidence < 0.82d) {
            return PaddleDateEvidence.empty();
        }
        String winningText = representativeTexts.get(bestIndex);
        double fusedConfidence = Math.min(
                0.98d,
                winningConfidence + Math.min(0.18d, (winningVotes - 1) * 0.04d)
        );
        Log.i(
                "ShiqiRecognition",
                "Focused date tail accepted text=" + winningText
                        + " expiry=" + expiryValues.get(bestIndex)
                        + " votes=" + winningVotes
                        + " confidence=" + String.format(Locale.US, "%.2f", fusedConfidence)
        );
        return new PaddleDateEvidence(winningText, winningText, fusedConfidence, rect);
    }

    private boolean hasDayPrecisionDateOcr(String text) {
        StringBuilder digits = new StringBuilder();
        String cleaned = FoodItem.cleanText(text);
        for (int index = 0; index < cleaned.length(); index++) {
            char value = cleaned.charAt(index);
            if (Character.isDigit(value)) {
                digits.append(value);
            }
        }
        if (digits.length() >= 8) {
            return true;
        }
        if (digits.length() != 6) {
            return false;
        }
        String compact = digits.toString();
        return !compact.startsWith("19") && !compact.startsWith("20");
    }

    private boolean looksLikeIncompleteDatePairText(String text) {
        String cleaned = FoodItem.cleanText(text);
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < cleaned.length(); index++) {
            char value = cleaned.charAt(index);
            if (Character.isDigit(value)) {
                digits.append(value);
            }
        }
        String compact = digits.toString();
        for (int first = 0; first + 15 <= compact.length(); first++) {
            if (!compact.startsWith("20", first)) {
                continue;
            }
            int secondStartMin = first + 8;
            int secondStartMax = Math.min(first + 10, compact.length() - 7);
            for (int second = secondStartMin; second <= secondStartMax; second++) {
                if (compact.startsWith("20", second)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void appendMlKitDateResult(
            List<PaddleLineOcrEngine.LineResult> target,
            TextRecognizer recognizer,
            Bitmap bitmap
    ) {
        if (recognizer == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        try {
            Text text = Tasks.await(
                    recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                    6,
                    TimeUnit.SECONDS
            );
            if (text != null && FoodItem.cleanText(text.getText()).length() > 0) {
                target.add(new PaddleLineOcrEngine.LineResult(text.getText(), 0.70d));
            }
        } catch (Exception error) {
            Log.d("ShiqiRecognition", "Focused ML Kit date tail pass skipped", error);
        }
    }

    private void rememberIncompleteDateReviewFrame(
            Bitmap bitmap,
            long timestampUs,
            String observedText,
            DateOcrParser.Result parsed,
            double visualScore,
            List<PackagingTextAnalyzer.Observation> observations
    ) {
        if (!videoReplayActive
                || bitmap == null
                || bitmap.isRecycled()
                || parsed == null
                || parsed.productionDates.isEmpty()
                || !parsed.expiryDates.isEmpty()) {
            return;
        }
        String text = FoodItem.cleanText(observedText);
        if (!looksLikeIncompleteDatePairText(text)) {
            return;
        }
        RecognitionEvidence.NormalizedRect dateTailRect = bestIncompleteDateTailRect(observations);
        if (dateTailRect == null) {
            return;
        }
        double score = 2d
                + RecognitionEvidence.clamp01(visualScore)
                + Math.max(0d, Math.min(1d, currentVideoFrameRatio)) * 0.10d;
        synchronized (sessionKeyframes) {
            if (incompleteDateReviewFrame != null && score <= incompleteDateReviewScore) {
                return;
            }
            Bitmap retained = retainEvidenceBitmap(bitmap);
            if (retained == null) {
                return;
            }
            if (incompleteDateReviewFrame != null && !incompleteDateReviewFrame.isRecycled()) {
                incompleteDateReviewFrame.recycle();
            }
            incompleteDateReviewFrame = retained;
            incompleteDateReviewRect = dateTailRect;
            incompleteDateReviewTimestampUs = timestampUs;
            incompleteDateReviewScore = score;
        }
        Log.i(
                "ShiqiRecognition",
                "Retained incomplete date frame timestampUs=" + timestampUs
                        + " score=" + String.format(Locale.US, "%.2f", score)
        );
    }

    private RecognitionEvidence.Region productRegion(
            PackagingTextAnalyzer.Candidate candidate,
            List<PackagingTextAnalyzer.Observation> observations,
            double frameQuality
    ) {
        RecognitionEvidence.NormalizedRect rect = null;
        double modelTotal = 0d;
        int modelCount = 0;
        List<String> engines = new ArrayList<String>();
        for (PackagingTextAnalyzer.Observation observation : observations) {
            String observedName = RecognitionTextCleaner.intelligentProductNameCandidate(observation.text);
            double similarity = RecognitionTextCleaner.productNameSimilarity(candidate.text, observedName);
            String candidateKey = RecognitionTextCleaner.productNameKey(candidate.text);
            String observedKey = RecognitionTextCleaner.productNameKey(observedName);
            boolean componentMatch = observedKey.length() >= 2
                    && candidateKey.contains(observedKey);
            if (similarity < 0.72d && !componentMatch) {
                continue;
            }
            RecognitionEvidence.NormalizedRect current = observationRect(observation);
            rect = rect == null ? current : rect.union(current);
            if (!Double.isNaN(observation.modelConfidence)) {
                modelTotal += observation.modelConfidence;
                modelCount++;
            }
            if (observation.engine.length() > 0 && !engines.contains(observation.engine)) {
                engines.add(observation.engine);
            }
        }
        if (rect == null) {
            return null;
        }
        int votes = candidate.votes;
        if (latestSnapshot != null) {
            for (PackagingTextAnalyzer.Candidate ranked : latestSnapshot.rankedPackagingCandidates) {
                if (RecognitionTextCleaner.productNamesSimilar(ranked.text, candidate.text)) {
                    votes = Math.max(votes, ranked.votes);
                    break;
                }
            }
        }
        double modelConfidence = modelCount == 0 ? Double.NaN : modelTotal / modelCount;
        double semanticConfidence = Math.min(1d, RecognitionTextCleaner.productNameScore(candidate.text) / 92d);
        return new RecognitionEvidence.Region(
                RecognitionEvidence.FIELD_PRODUCT_NAME,
                candidate.text,
                rect,
                joinEngines(engines),
                modelConfidence,
                RecognitionEvidence.fusedConfidence(
                        modelConfidence,
                        frameQuality,
                        semanticConfidence,
                        votes,
                        Math.max(1, engines.size())
                )
        );
    }

    private void addDateRegion(
            List<RecognitionEvidence.Region> target,
            String field,
            List<DateOcrParser.DateCandidate> candidates,
            List<PackagingTextAnalyzer.Observation> observations,
            double frameQuality
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        DateOcrParser.DateCandidate selected = candidates.get(0);
        RecognitionEvidence.NormalizedRect rect = null;
        double modelTotal = 0d;
        int modelCount = 0;
        List<String> engines = new ArrayList<String>();
        for (PackagingTextAnalyzer.Observation observation : observations) {
            DateOcrParser.Result observationDates = DateOcrParser.parse(observation.text);
            List<DateOcrParser.DateCandidate> matching = RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(field)
                    ? observationDates.productionDates
                    : observationDates.expiryDates;
            if (!containsDateValue(matching, selected.normalized)) {
                continue;
            }
            RecognitionEvidence.NormalizedRect current = observationRect(observation);
            rect = rect == null ? current : rect.union(current);
            if (!Double.isNaN(observation.modelConfidence)) {
                modelTotal += observation.modelConfidence;
                modelCount++;
            }
            if (observation.engine.length() > 0 && !engines.contains(observation.engine)) {
                engines.add(observation.engine);
            }
        }
        if (rect == null) {
            return;
        }
        int votes = latestSnapshot != null && latestSnapshot.stableDateVote != null
                ? Math.max(1, latestSnapshot.stableDateVote.framesWithCandidates)
                : 1;
        double modelConfidence = modelCount == 0 ? Double.NaN : modelTotal / modelCount;
        target.add(new RecognitionEvidence.Region(
                field,
                selected.normalized,
                rect,
                joinEngines(engines),
                modelConfidence,
                RecognitionEvidence.fusedConfidence(
                        modelConfidence,
                        frameQuality,
                        selected.confidence,
                        votes,
                        Math.max(1, engines.size())
                )
        ));
    }

    private boolean containsDateValue(List<DateOcrParser.DateCandidate> candidates, String value) {
        for (DateOcrParser.DateCandidate candidate : candidates) {
            if (value.equals(candidate.normalized)) {
                return true;
            }
        }
        return false;
    }

    private RecognitionEvidence.NormalizedRect findBarcodeRect(
            String barcode,
            List<PackagingTextAnalyzer.Observation> observations
    ) {
        for (PackagingTextAnalyzer.Observation observation : observations) {
            if (barcode.equals(BarcodeUtils.extractProductCode(observation.text))) {
                return observationRect(observation);
            }
        }
        return null;
    }

    private RecognitionEvidence.NormalizedRect observationRect(PackagingTextAnalyzer.Observation observation) {
        return new RecognitionEvidence.NormalizedRect(
                observation.left,
                observation.top,
                observation.right,
                observation.bottom
        );
    }

    private String joinEngines(List<String> engines) {
        StringBuilder builder = new StringBuilder();
        for (String engine : engines) {
            if (builder.length() > 0) {
                builder.append(" + ");
            }
            builder.append(engine);
        }
        return builder.toString();
    }

    private Bitmap retainEvidenceBitmap(Bitmap bitmap) {
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        Bitmap source = bitmap;
        if (largest > 960) {
            float scale = 960f / largest;
            source = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)),
                    true
            );
        }
        Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
        if (source != bitmap) {
            source.recycle();
        }
        return copy;
    }

    private void offerSessionKeyframe(SessionKeyframe incoming) {
        synchronized (sessionKeyframes) {
            if (!frameSelector.offer(incoming.selectorCandidate)) {
                incoming.bitmap.recycle();
                return;
            }
            sessionKeyframes.add(incoming);
            final List<RecognitionFrameSelector.FrameCandidate> selected = frameSelector.selectedFrames();
            for (int index = sessionKeyframes.size() - 1; index >= 0; index--) {
                SessionKeyframe existing = sessionKeyframes.get(index);
                if (!containsSelectedFrame(selected, existing.selectorCandidate.frameId)) {
                    sessionKeyframes.remove(index);
                    existing.bitmap.recycle();
                }
            }
            Collections.sort(sessionKeyframes, new Comparator<SessionKeyframe>() {
                @Override
                public int compare(SessionKeyframe left, SessionKeyframe right) {
                    int fieldOrder = Integer.compare(
                            keyframeFieldOrder(left.primaryRegion().field),
                            keyframeFieldOrder(right.primaryRegion().field)
                    );
                    if (fieldOrder != 0) {
                        return fieldOrder;
                    }
                    return Double.compare(right.score(), left.score());
                }
            });
        }
        renderKeyframesAsync();
    }

    private List<SessionKeyframe> keyframeSnapshot() {
        synchronized (sessionKeyframes) {
            return new ArrayList<SessionKeyframe>(sessionKeyframes);
        }
    }

    private boolean containsSelectedFrame(
            List<RecognitionFrameSelector.FrameCandidate> selected,
            String frameId
    ) {
        return selectedFrameIndex(selected, frameId) >= 0;
    }

    private int selectedFrameIndex(
            List<RecognitionFrameSelector.FrameCandidate> selected,
            String frameId
    ) {
        for (int index = 0; index < selected.size(); index++) {
            if (selected.get(index).frameId.equals(frameId)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private int evidenceMask(List<RecognitionEvidence.Region> regions) {
        int mask = RecognitionFrameSelector.EVIDENCE_NONE;
        for (RecognitionEvidence.Region region : regions) {
            if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(region.field)) {
                mask |= RecognitionFrameSelector.EVIDENCE_PRODUCT_NAME;
            } else if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(region.field)) {
                mask |= RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE;
            } else if (RecognitionEvidence.FIELD_EXPIRY_DATE.equals(region.field)) {
                mask |= RecognitionFrameSelector.EVIDENCE_EXPIRY_DATE;
            } else if (RecognitionEvidence.FIELD_BARCODE.equals(region.field)) {
                mask |= RecognitionFrameSelector.EVIDENCE_BARCODE;
            }
        }
        return mask;
    }

    private int primaryEvidence(List<RecognitionEvidence.Region> regions) {
        RecognitionEvidence.Region strongest = null;
        for (RecognitionEvidence.Region region : regions) {
            if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(region.field)) {
                return RecognitionFrameSelector.EVIDENCE_PRODUCT_NAME;
            }
            if (strongest == null || region.confidence > strongest.confidence) {
                strongest = region;
            }
        }
        if (strongest == null) {
            return RecognitionFrameSelector.EVIDENCE_NONE;
        }
        if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(strongest.field)) {
            return RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE;
        }
        if (RecognitionEvidence.FIELD_EXPIRY_DATE.equals(strongest.field)) {
            return RecognitionFrameSelector.EVIDENCE_EXPIRY_DATE;
        }
        return RecognitionFrameSelector.EVIDENCE_BARCODE;
    }

    private int keyframeFieldOrder(String field) {
        if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(field)) {
            return 0;
        }
        if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(field)) {
            return 1;
        }
        if (RecognitionEvidence.FIELD_EXPIRY_DATE.equals(field)) {
            return 2;
        }
        return 3;
    }

    private void renderKeyframesAsync() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderKeyframes();
                renderProductCandidates(latestSnapshot);
                if (productionDateValue != null) {
                    productionDateValue.setText(compactDateSummary(
                            DateOcrResultPayload.toDraft(latestSnapshot.stableDateVote)
                    ));
                }
            }
        });
    }

    private void startProductLookupIfNeeded(final UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot == null || !snapshot.hasStableBarcode() || liveCameraResultFrozen) {
            return;
        }
        final String barcode = snapshot.stableBarcode;
        if (barcode.equals(latestProductBarcode) || barcode.equals(productLookupInFlightBarcode)) {
            return;
        }

        latestProductInfo = null;
        latestProductBarcode = "";
        productLookupError = "";
        productLookupInFlightBarcode = barcode;
        final int lookupGeneration = recognitionGeneration;
        updateResultUi(snapshot);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BarcodeProductInfo info = BarcodeLookupClient.query(barcode);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (lookupGeneration != recognitionGeneration
                                    || liveCameraResultFrozen
                                    || !barcode.equals(stabilizer.snapshot().stableBarcode)) {
                                return;
                            }
                            productLookupInFlightBarcode = "";
                            latestProductBarcode = barcode;
                            latestProductInfo = info;
                            if (latestProductInfo != null && latestProductInfo.barcode.length() == 0) {
                                latestProductInfo.barcode = barcode;
                            }
                            updateResultUi(stabilizer.snapshot());
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (lookupGeneration != recognitionGeneration
                                    || liveCameraResultFrozen
                                    || !barcode.equals(stabilizer.snapshot().stableBarcode)) {
                                return;
                            }
                            productLookupInFlightBarcode = "";
                            latestProductBarcode = barcode;
                            productLookupError = FoodItem.cleanText(error.getMessage());
                            if (productLookupError.length() == 0) {
                                productLookupError = "商品查询暂不可用";
                            }
                            updateResultUi(stabilizer.snapshot());
                        }
                    });
                }
            }
        }).start();
    }

    private void updateResultUi(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot == null) {
            snapshot = stabilizer.snapshot();
        }
        snapshot = UnifiedRecognitionStabilizer.retainStableEvidence(
                UnifiedRecognitionStabilizer.retainStableEvidence(bestSessionSnapshot, latestSnapshot),
                snapshot
        );
        latestSnapshot = snapshot;
        rememberSessionSnapshot(snapshot);

        if (barcodeValue != null) {
            barcodeValue.setText(snapshot.hasStableBarcode()
                    ? "商品码  " + snapshot.stableBarcode + " · 已识别"
                    : "商品码  暂未识别");
        }

        if (productValue != null) {
            productValue.setText(productDisplayText(snapshot));
        }
        renderProductCandidates(snapshot);

        FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
        if (productionDateValue != null) {
            productionDateValue.setText(compactDateSummary(dateDraft));
        }

        int recognizedChars = FoodItem.cleanText(latestRawText).replace(" ", "").length();
        rawPreviewText.setText(recognizedChars > 0
                ? "已读取 " + recognizedChars + " 个字符 · 原文可查看"
                : "尚未读取到清晰文字");

        boolean fillable = snapshot.hasFillableCandidate() || selectedPackagingName.length() > 0;
        fillButton.setEnabled(fillable);
        fillButton.setAlpha(fillable ? 1f : 0.45f);

        updateStatus(snapshot);
        updateResultSheetHeight(snapshot);
    }

    private void updateResultSheetHeight(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (resultSheet == null) {
            return;
        }
        int desiredDp = 260;
        if (productCandidateScroll != null
                && productCandidateScroll.getVisibility() == View.VISIBLE) {
            desiredDp += 40;
        }
        if (keyframeScroll != null && keyframeScroll.getVisibility() == View.VISIBLE) {
            desiredDp += 100;
        }
        if (snapshot != null && snapshot.hasStableDateCandidate()) {
            desiredDp += 60;
        }
        if (snapshot != null
                && snapshot.hasStablePackagingName()
                && (keyframeScroll == null || keyframeScroll.getVisibility() != View.VISIBLE)) {
            desiredDp += 20;
        }
        desiredDp = Math.max(260, Math.min(440, desiredDp));
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        desiredDp += Math.round(Math.min(0.4f, fontScale - 1f) * 120f);
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.72f);
        ViewGroup.LayoutParams params = resultSheet.getLayoutParams();
        if (params != null) {
            params.height = Math.min(dp(desiredDp), maxHeight);
            resultSheet.setLayoutParams(params);
        }
    }

    private void promoteCompletionTranscriptEvidence(
            String transcript,
            DateOcrParser.Result dateEvidence
    ) {
        String cleaned = FoodItem.cleanText(transcript);
        if (cleaned.length() == 0) {
            return;
        }
        List<PackagingTextAnalyzer.Candidate> candidates =
                PackagingTextAnalyzer.analyze(observationsFromRawText(cleaned), true);
        String extractedName = RecognitionTextCleaner.extractProductNameFromOcr(cleaned);
        if (RecognitionTextCleaner.productNameScore(extractedName) > 0) {
            List<PackagingTextAnalyzer.Candidate> merged =
                    new ArrayList<PackagingTextAnalyzer.Candidate>(candidates);
            merged.add(new PackagingTextAnalyzer.Candidate(
                    extractedName,
                    Math.max(92d, RecognitionTextCleaner.productNameScore(extractedName)),
                    2,
                    java.util.Collections.singletonList(extractedName)
            ));
            candidates = merged;
        }
        String barcode = RecognitionTextCleaner.extractProductCodeFromOcr(cleaned);
        latestSnapshot = UnifiedRecognitionStabilizer.retainStableEvidence(
                latestSnapshot,
                stabilizer.addFrame(
                        barcode,
                        dateEvidence,
                        cleaned,
                        candidates,
                        true
                )
        );
        rememberSessionSnapshot(latestSnapshot);
    }

    private DateOcrParser.Result datePairFromSessionEvidence(
            UnifiedRecognitionStabilizer.Snapshot snapshot
    ) {
        String production = "";
        String expiry = "";
        double productionConfidence = 0d;
        double expiryConfidence = 0d;
        double bestPairScore = 0d;
        if (snapshot != null
                && UnifiedRecognitionStabilizer.isReliableDirectDatePair(snapshot.stableDateVote)) {
            production = snapshot.stableDateVote.productionDate.value;
            expiry = snapshot.stableDateVote.expiryDate.value;
            productionConfidence = snapshot.stableDateVote.productionDate.confidence;
            expiryConfidence = snapshot.stableDateVote.expiryDate.confidence;
            bestPairScore = productionConfidence + expiryConfidence;
        }
        for (SessionKeyframe keyframe : keyframeSnapshot()) {
            RecognitionEvidence.Region frameProduction = null;
            RecognitionEvidence.Region frameExpiry = null;
            for (RecognitionEvidence.Region region : keyframe.evidence.regions) {
                if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(region.field)
                        && DateRules.isValidDateString(region.value)
                        && region.value.compareTo(DateRules.getTodayString()) <= 0
                        && (frameProduction == null
                        || region.confidence > frameProduction.confidence)) {
                    frameProduction = region;
                } else if (RecognitionEvidence.FIELD_EXPIRY_DATE.equals(region.field)
                        && DateRules.isValidDateString(region.value)
                        && (frameExpiry == null || region.confidence > frameExpiry.confidence)) {
                    frameExpiry = region;
                }
            }
            if (frameProduction == null || frameExpiry == null) {
                continue;
            }
            int spanDays = DateRules.daysBetween(frameProduction.value, frameExpiry.value);
            double pairScore = frameProduction.confidence + frameExpiry.confidence;
            if (spanDays >= 0 && spanDays <= (366 * 5) && pairScore > bestPairScore) {
                production = frameProduction.value;
                expiry = frameExpiry.value;
                productionConfidence = frameProduction.confidence;
                expiryConfidence = frameExpiry.confidence;
                bestPairScore = pairScore;
            }
        }
        if (!DateRules.isValidDateString(production)
                || !DateRules.isValidDateString(expiry)
                || DateRules.daysBetween(production, expiry) < 0
                || DateRules.daysBetween(production, expiry) > (366 * 5)) {
            return DateOcrParser.parse("");
        }
        List<DateOcrParser.DateCandidate> productionDates =
                new ArrayList<DateOcrParser.DateCandidate>();
        productionDates.add(new DateOcrParser.DateCandidate(
                "productionDate",
                production,
                production,
                "session evidence",
                Math.max(0.70d, productionConfidence),
                false,
                false
        ));
        List<DateOcrParser.DateCandidate> expiryDates =
                new ArrayList<DateOcrParser.DateCandidate>();
        expiryDates.add(new DateOcrParser.DateCandidate(
                "expiryDate",
                expiry,
                expiry,
                "session evidence",
                Math.max(0.70d, expiryConfidence),
                false,
                false
        ));
        return new DateOcrParser.Result(
                production + "\n" + expiry,
                production + "\n" + expiry,
                productionDates,
                expiryDates,
                new ArrayList<DateOcrParser.ShelfLifeCandidate>(),
                new ArrayList<DateOcrParser.DateCandidate>()
        );
    }

    private String bestProductNameFromSessionEvidence() {
        String bestName = "";
        double bestScore = 0d;
        if (bestSessionSnapshot != null) {
            if (bestSessionSnapshot.hasStablePackagingName()) {
                bestName = bestSessionSnapshot.stablePackagingName;
                bestScore = 200d + bestSessionSnapshot.packagingNameVotes;
            }
            for (PackagingTextAnalyzer.Candidate candidate
                    : bestSessionSnapshot.rankedPackagingCandidates) {
                if (!RecognitionTextCleaner.isHighConfidenceFoodProductName(candidate.text)) {
                    continue;
                }
                double score = candidate.score + (Math.min(4, candidate.votes) * 12d);
                if (score > bestScore) {
                    bestName = candidate.text;
                    bestScore = score;
                }
            }
        }
        for (SessionKeyframe keyframe : keyframeSnapshot()) {
            for (RecognitionEvidence.Region region : keyframe.evidence.regions) {
                if (!RecognitionEvidence.FIELD_PRODUCT_NAME.equals(region.field)
                        || !RecognitionTextCleaner.isHighConfidenceFoodProductName(region.value)) {
                    continue;
                }
                double score = (region.confidence * 100d)
                        + RecognitionTextCleaner.productNameScore(region.value);
                if (score > bestScore) {
                    bestName = region.value;
                    bestScore = score;
                }
            }
        }
        String normalized = RecognitionTextCleaner.intelligentProductNameCandidate(bestName);
        return normalized.length() > 0 ? normalized : bestName;
    }

    private void rememberSessionSnapshot(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot == null || !snapshot.hasAnySeenCandidate()) {
            return;
        }
        bestSessionSnapshot = UnifiedRecognitionStabilizer.retainStableEvidence(
                bestSessionSnapshot,
                snapshot
        );
    }

    private String productDisplayText(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (selectedPackagingName.length() > 0) {
            return selectedPackagingName;
        }
        String packagingName = snapshot == null ? "" : snapshot.bestPackagingNameForConfirmation();
        if (latestProductInfo != null && latestProductInfo.found) {
            String name = RecognitionTextCleaner.preferredRecognizedProductName(
                    packagingName,
                    latestProductInfo.displayName()
            );
            return name.length() > 0 ? name : "已查询到商品";
        }
        if (packagingName.length() > 0) {
            return packagingName;
        }
        if (snapshot != null && snapshot.hasStableBarcode() && productLookupInFlightBarcode.length() > 0) {
            return "正在查询条码商品信息";
        }
        if (snapshot != null && snapshot.hasStablePackagingName()) {
            return snapshot.stablePackagingName;
        }
        if (snapshot != null && !snapshot.rankedPackagingCandidates.isEmpty()) {
            return snapshot.rankedPackagingCandidates.get(0).text;
        }
        if (snapshot.hasStableBarcode() && productLookupError.length() > 0) {
            return "商品名称未识别";
        }
        if (snapshot.hasStableBarcode() && latestProductBarcode.length() > 0) {
            return "商品名称未识别";
        }
        return "正在寻找中文商品名";
    }

    private void renderProductCandidates(final UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (productCandidateContainer == null || productCandidateScroll == null) {
            return;
        }
        productCandidateContainer.removeAllViews();
        productCandidateScroll.setVisibility(View.GONE);
        boolean barcodeLookupOwnsName = snapshot != null
                && snapshot.hasStableBarcode()
                && (productLookupInFlightBarcode.length() > 0
                || (latestProductInfo != null && latestProductInfo.found));
        if (barcodeLookupOwnsName) {
            productCandidateScroll.setVisibility(View.VISIBLE);
            TextView hint = compactResultText();
            hint.setText(productLookupInFlightBarcode.length() > 0
                    ? "正在优先查询条码商品名"
                    : "已采用条码商品名");
            productCandidateContainer.addView(hint, fixed(220, 48));
            return;
        }
        if (snapshot == null || snapshot.rankedPackagingCandidates.isEmpty()) {
            return;
        }

        productCandidateScroll.setVisibility(View.VISIBLE);
        final PackagingTextAnalyzer.Candidate recommended = snapshot.rankedPackagingCandidates.get(0);
        TextView confidence = compactResultText();
        double fusedConfidence = productCandidateConfidence(recommended);
        confidence.setText("商品名可信度 " + RecognitionEvidence.confidenceLabel(fusedConfidence)
                + " · " + Math.max(1, recommended.votes) + "帧");
        productCandidateContainer.addView(confidence, fixed(220, 48));

        if (snapshot.rankedPackagingCandidates.size() > 1) {
            Button alternatives = plainButton("其他结果");
            alternatives.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showProductAlternatives(snapshot.rankedPackagingCandidates);
                }
            });
            productCandidateContainer.addView(alternatives, fixed(96, 48));
        }
    }

    private double productCandidateConfidence(PackagingTextAnalyzer.Candidate candidate) {
        double strongest = 0d;
        for (SessionKeyframe keyframe : keyframeSnapshot()) {
            for (RecognitionEvidence.Region region : keyframe.evidence.regions) {
                if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(region.field)
                        && RecognitionTextCleaner.productNamesSimilar(region.value, candidate.text)) {
                    strongest = Math.max(strongest, region.confidence);
                }
            }
        }
        if (strongest > 0d) {
            return strongest;
        }
        return RecognitionEvidence.fusedConfidence(
                Double.NaN,
                0.62d,
                Math.min(1d, RecognitionTextCleaner.productNameScore(candidate.text) / 92d),
                candidate.votes,
                1
        );
    }

    private void showProductAlternatives(final List<PackagingTextAnalyzer.Candidate> candidates) {
        int count = Math.min(3, candidates.size());
        final String[] labels = new String[count];
        for (int index = 0; index < count; index++) {
            PackagingTextAnalyzer.Candidate candidate = candidates.get(index);
            labels[index] = candidate.text + "  ·  可信度 "
                    + RecognitionEvidence.confidenceLabel(productCandidateConfidence(candidate));
        }
        new AlertDialog.Builder(this)
                .setTitle("选择商品名")
                .setSingleChoiceItems(labels, selectedProductCandidateIndex(candidates, count), null)
                .setPositiveButton("采用", (dialog, which) -> {
                    AlertDialog alert = (AlertDialog) dialog;
                    int checked = alert.getListView().getCheckedItemPosition();
                    if (checked >= 0 && checked < count) {
                        selectedPackagingName = candidates.get(checked).text;
                        updateResultUi(latestSnapshot);
                        showBestEvidenceForValue(selectedPackagingName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int selectedProductCandidateIndex(List<PackagingTextAnalyzer.Candidate> candidates, int count) {
        for (int index = 0; index < count; index++) {
            if (RecognitionTextCleaner.productNamesSimilar(selectedPackagingName, candidates.get(index).text)) {
                return index;
            }
        }
        return 0;
    }

    private void renderKeyframes() {
        if (keyframeContainer == null || keyframeTitle == null || keyframeScroll == null) {
            return;
        }
        keyframeContainer.removeAllViews();
        List<SessionKeyframe> keyframes = keyframeSnapshot();
        if (keyframes.isEmpty()) {
            keyframeTitle.setVisibility(View.GONE);
            keyframeScroll.setVisibility(View.GONE);
            updateResultSheetHeight(latestSnapshot);
            return;
        }
        List<SessionKeyframe> displayKeyframes = new ArrayList<SessionKeyframe>();
        List<String> displayedEvidence = new ArrayList<String>();
        for (SessionKeyframe keyframe : keyframes) {
            RecognitionEvidence.Region primary = keyframe.primaryRegion();
            if (primary != null
                    && RecognitionEvidence.FIELD_BARCODE.equals(primary.field)
                    && (latestSnapshot == null || !latestSnapshot.hasStableBarcode())) {
                continue;
            }
            String signature = primary == null
                    ? "frame:" + displayKeyframes.size()
                    : primary.field + ":" + FoodItem.cleanText(primary.value);
            if (displayedEvidence.contains(signature)) {
                continue;
            }
            displayedEvidence.add(signature);
            displayKeyframes.add(keyframe);
            if (displayKeyframes.size() >= RecognitionFrameSelector.MAX_KEYFRAMES) {
                break;
            }
        }
        if (displayKeyframes.isEmpty()) {
            keyframeTitle.setVisibility(View.GONE);
            keyframeScroll.setVisibility(View.GONE);
            updateResultSheetHeight(latestSnapshot);
            return;
        }
        keyframeTitle.setVisibility(View.VISIBLE);
        keyframeScroll.setVisibility(View.VISIBLE);
        updateResultSheetHeight(latestSnapshot);
        int keyframeCount = displayKeyframes.size();
        for (int keyframeIndex = 0; keyframeIndex < keyframeCount; keyframeIndex++) {
            final SessionKeyframe keyframe = displayKeyframes.get(keyframeIndex);
            FrameLayout item = new FrameLayout(this);
            item.setBackground(rounded(Color.rgb(233, 237, 235), dp(6), Color.rgb(204, 214, 208)));

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(keyframe.bitmap);
            item.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            RecognitionEvidence.Region primary = keyframe.primaryRegion();
            TextView label = new TextView(this);
            label.setText(fieldLabel(primary.field) + " · "
                    + RecognitionEvidence.confidenceLabel(primary.confidence));
            label.setTextColor(Color.WHITE);
            label.setTextSize(14);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setBackgroundColor(Color.argb(205, 23, 32, 27));
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(40)
            );
            labelParams.gravity = Gravity.BOTTOM;
            item.addView(label, labelParams);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showEvidenceFrame(keyframe);
                }
            });

            LinearLayout.LayoutParams params = fixed(112, 84);
            params.setMargins(0, 0, dp(8), 0);
            keyframeContainer.addView(item, params);
        }
    }

    private void showBestEvidenceForValue(String value) {
        SessionKeyframe best = null;
        double bestConfidence = 0d;
        for (SessionKeyframe keyframe : keyframeSnapshot()) {
            for (RecognitionEvidence.Region region : keyframe.evidence.regions) {
                if (RecognitionTextCleaner.productNamesSimilar(value, region.value)
                        && region.confidence > bestConfidence) {
                    best = keyframe;
                    bestConfidence = region.confidence;
                }
            }
        }
        if (best != null) {
            showEvidenceFrame(best);
        }
    }

    private void showEvidenceFrame(SessionKeyframe keyframe) {
        showEvidenceFrame(keyframe, true);
    }

    private void showEvidenceFrame(SessionKeyframe keyframe, boolean updateStatusText) {
        if (keyframe == null || keyframe.bitmap == null || keyframe.bitmap.isRecycled()) {
            return;
        }
        showingEvidenceFrame = true;
        clearLastReplayFrame();
        previewView.setVisibility(View.GONE);
        replayFrameView.setVisibility(View.VISIBLE);
        replayFrameView.setImageBitmap(keyframe.bitmap);
        recognitionOverlay.showEvidence(
                keyframe.evidence.regions,
                keyframe.evidence.width,
                keyframe.evidence.height
        );
        if (updateStatusText) {
            RecognitionEvidence.Region primary = keyframe.primaryRegion();
            statusBadge.setText("证据帧");
            statusText.setText(fieldLabel(primary.field) + " · 可信度 "
                    + RecognitionEvidence.confidenceLabel(primary.confidence));
        }
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
        return "条码";
    }

    private String compactDateSummary(FoodItem draft) {
        List<String> values = new ArrayList<String>();
        if (draft.productionDate.length() > 0) {
            values.add("生产日期  " + draft.productionDate);
        }
        if (draft.shelfLifeValue != null) {
            values.add("保质期  " + draft.shelfLifeValue + shelfLifeSummaryUnit(draft.shelfLifeUnit));
        }
        if (draft.expiryDate.length() > 0) {
            DateOcrFrameVoter.StableDate explicitExpiry = latestSnapshot == null
                    || latestSnapshot.stableDateVote == null
                    ? null
                    : latestSnapshot.stableDateVote.expiryDate;
            String expiryLabel = "calculated".equals(draft.dateSource)
                    ? "系统计算到期日"
                    : "有效期";
            if (explicitExpiry != null && DateOcrParser.isMonthOnlyExpiryRaw(explicitExpiry.raw)) {
                values.add(expiryLabel + "  " + draft.expiryDate + "（包装标到月，按月末）");
            } else {
                values.add(expiryLabel + "  " + draft.expiryDate);
            }
        }
        if (values.isEmpty()) {
            return "日期暂未识别，请重新对准日期区域。";
        }
        StringBuilder builder = new StringBuilder();
        double confidence = strongestDateEvidenceConfidence();
        if (confidence > 0d) {
            builder.append("日期可信度  ")
                    .append(RecognitionEvidence.confidenceLabel(confidence))
                    .append('\n');
        }
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private String shelfLifeSummaryUnit(String unit) {
        if ("day".equals(unit)) {
            return "天";
        }
        if ("month".equals(unit)) {
            return "个月";
        }
        if ("year".equals(unit)) {
            return "年";
        }
        return FoodData.shelfLifeUnitLabel(unit);
    }

    private double strongestDateEvidenceConfidence() {
        double confidence = 0d;
        for (SessionKeyframe keyframe : keyframeSnapshot()) {
            for (RecognitionEvidence.Region region : keyframe.evidence.regions) {
                if (RecognitionEvidence.FIELD_PRODUCTION_DATE.equals(region.field)
                        || RecognitionEvidence.FIELD_EXPIRY_DATE.equals(region.field)) {
                    confidence = Math.max(confidence, region.confidence);
                }
            }
        }
        return confidence;
    }

    private void updateStatus(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (videoReplayActive && videoExpectedFrames > 0 && videoAnalyzedFrames < videoExpectedFrames) {
            statusBadge.setText("分析中");
            statusText.setText("已分析 " + videoAnalyzedFrames + "/" + videoExpectedFrames
                    + " 帧，正在挑选清晰证据。");
            return;
        }
        if (selectedPackagingName.length() > 0) {
            statusBadge.setText("已选择");
            statusText.setText("已选择商品名候选，可继续识别日期或直接填入表单。");
            return;
        }
        if (snapshot != null
                && snapshot.hasStablePackagingName()
                && !snapshot.hasStableBarcode()
                && !snapshot.hasStableDateCandidate()) {
            statusBadge.setText("名称较稳");
            statusText.setText("中文商品名已多帧重复出现，请核对后使用。");
            return;
        }
        if (snapshot.hasStableBarcode() && snapshot.hasStableDateCandidate()) {
            statusBadge.setText("结果较稳");
            statusText.setText("条码和日期已多帧重复出现，请核对后使用。");
        } else if (snapshot.hasStableBarcode()) {
            statusBadge.setText("条码已识别");
            statusText.setText("已识别商品码，可继续寻找中文商品名和日期。");
        } else if (snapshot.hasStableDateCandidate()) {
            statusBadge.setText("日期较稳");
            statusText.setText("日期已多帧重复出现，请核对后使用。");
        } else if (!snapshot.rankedPackagingCandidates.isEmpty()) {
            statusBadge.setText("请核对");
            statusText.setText(snapshot.rankedPackagingCandidates.size() == 1
                    ? "找到一个高可信商品名，请核对后使用。"
                    : "找到两个高可信商品名，请选择更符合包装的一项。");
        } else if (snapshot.latestDateVote != null && snapshot.latestDateVote.hasConflict) {
            statusBadge.setText("需确认");
            statusText.setText("出现多个日期候选，可继续保持清晰后再核对。");
        } else if (latestRawText.length() > 0) {
            statusBadge.setText("识别中");
            statusText.setText("已看到包装文字，继续保持稳定以提高候选可靠度。");
        } else {
            statusBadge.setText("识别中");
            statusText.setText("把条码、生产日期或保质期放入识别框，系统会自动合并结果。");
        }
    }

    private void showRawTextDialog() {
        String raw = FoodItem.cleanText(latestRawText);
        if (raw.length() == 0) {
            raw = "暂无原始 OCR 文本。";
        }
        new AlertDialog.Builder(this)
                .setTitle("原始识别文本")
                .setMessage(raw)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void finishWithCandidate() {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot == null
                ? stabilizer.snapshot()
                : latestSnapshot;
        if (!snapshot.hasFillableCandidate() && selectedPackagingName.length() == 0) {
            Toast.makeText(this, "还没有可填入字段", Toast.LENGTH_SHORT).show();
            return;
        }

        FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
        BarcodeProductInfo info = latestProductBarcode.equals(snapshot.stableBarcode) ? latestProductInfo : null;
        String packagingName = selectedPackagingName.length() > 0
                ? selectedPackagingName
                : snapshot.bestPackagingNameForConfirmation();
        String productName = RecognitionTextCleaner.preferredRecognizedProductName(
                packagingName,
                info != null && info.found ? info.displayName() : ""
        );
        String productCategory = info != null && info.found ? BarcodeCategoryClassifier.inferCategory(info) : "";
        String productNotes = info != null && info.found ? info.notes() : "";

        Intent result = new Intent();
        result.putExtra("SCAN_RESULT", snapshot.stableBarcode);
        result.putExtra(UnifiedRecognitionPayload.EXTRA_BARCODE, snapshot.stableBarcode);
        result.putExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_FOUND, info != null && info.found);
        result.putExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_NAME, productName);
        result.putExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_CATEGORY, productCategory);
        result.putExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_NOTES, productNotes);
        result.putExtra(DateOcrResultPayload.EXTRA_PRODUCTION_DATE, dateDraft.productionDate);
        result.putExtra(DateOcrResultPayload.EXTRA_EXPIRY_DATE, dateDraft.expiryDate);
        result.putExtra(DateOcrResultPayload.EXTRA_EXPIRY_CALCULATED, "calculated".equals(dateDraft.dateSource));
        if (dateDraft.shelfLifeValue != null) {
            result.putExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_VALUE, dateDraft.shelfLifeValue.intValue());
        }
        result.putExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_UNIT, dateDraft.shelfLifeUnit);
        result.putExtra(DateOcrResultPayload.EXTRA_RAW_TEXT, snippet(latestRawText, 260));
        result.putExtra(UnifiedRecognitionPayload.EXTRA_SUMMARY, UnifiedRecognitionPayload.summary(
                snapshot.stableBarcode,
                productName,
                snapshot.stableDateVote,
                info != null && info.found
        ));
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

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private Button overlayButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rounded(color, dp(8), Color.argb(90, 255, 255, 255)));
        return button;
    }

    private Button sourceButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(48, 58, 53));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rounded(Color.rgb(238, 241, 240), dp(8), Color.rgb(216, 222, 219)));
        return button;
    }

    private Button plainButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(38, 104, 76));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rounded(Color.TRANSPARENT, dp(8), Color.rgb(216, 222, 219)));
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

    private LinearLayout.LayoutParams withMargins(LinearLayout.LayoutParams params, int left, int top, int right, int bottom) {
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class FrameVariant {
        final Bitmap bitmap;
        final int rotationDegrees;
        final boolean scanBarcode;
        final boolean scanText;
        final boolean scanLatinText;
        final boolean recycleBitmap;
        final double sourceQuality;
        final boolean dateFocused;
        final boolean datePairSupplement;
        final double sourceLeft;
        final double sourceTop;
        final double sourceWidth;
        final double sourceHeight;

        FrameVariant(
                Bitmap bitmap,
                int rotationDegrees,
                boolean scanBarcode,
                boolean scanText,
                boolean scanLatinText,
                boolean recycleBitmap,
                double sourceQuality
        ) {
            this(
                    bitmap,
                    rotationDegrees,
                    scanBarcode,
                    scanText,
                    scanLatinText,
                    recycleBitmap,
                    sourceQuality,
                    false,
                    false,
                    0d,
                    0d,
                    1d,
                    1d
            );
        }

        FrameVariant(
                Bitmap bitmap,
                int rotationDegrees,
                boolean scanBarcode,
                boolean scanText,
                boolean scanLatinText,
                boolean recycleBitmap,
                double sourceQuality,
                boolean dateFocused
        ) {
            this(
                    bitmap,
                    rotationDegrees,
                    scanBarcode,
                    scanText,
                    scanLatinText,
                    recycleBitmap,
                    sourceQuality,
                    dateFocused,
                    false,
                    0d,
                    0d,
                    1d,
                    1d
            );
        }

        FrameVariant(
                Bitmap bitmap,
                int rotationDegrees,
                boolean scanBarcode,
                boolean scanText,
                boolean scanLatinText,
                boolean recycleBitmap,
                double sourceQuality,
                boolean dateFocused,
                boolean datePairSupplement
        ) {
            this(
                    bitmap,
                    rotationDegrees,
                    scanBarcode,
                    scanText,
                    scanLatinText,
                    recycleBitmap,
                    sourceQuality,
                    dateFocused,
                    datePairSupplement,
                    0d,
                    0d,
                    1d,
                    1d
            );
        }

        FrameVariant(
                Bitmap bitmap,
                int rotationDegrees,
                boolean scanBarcode,
                boolean scanText,
                boolean scanLatinText,
                boolean recycleBitmap,
                double sourceQuality,
                boolean dateFocused,
                boolean datePairSupplement,
                double sourceLeft,
                double sourceTop,
                double sourceWidth,
                double sourceHeight
        ) {
            this.bitmap = bitmap;
            this.rotationDegrees = rotationDegrees;
            this.scanBarcode = scanBarcode;
            this.scanText = scanText;
            this.scanLatinText = scanLatinText;
            this.recycleBitmap = recycleBitmap;
            this.sourceQuality = sourceQuality;
            this.dateFocused = dateFocused;
            this.datePairSupplement = datePairSupplement;
            this.sourceLeft = sourceLeft;
            this.sourceTop = sourceTop;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
        }

        FrameVariant withSourceQuality(double quality) {
            return new FrameVariant(
                    bitmap,
                    rotationDegrees,
                    scanBarcode,
                    scanText,
                    scanLatinText,
                    recycleBitmap,
                    quality,
                    dateFocused,
                    datePairSupplement,
                    sourceLeft,
                    sourceTop,
                    sourceWidth,
                    sourceHeight
            );
        }
    }

    private static final class PaddleDateEvidence {
        final String text;
        final String originalText;
        final String packagingText;
        final double confidence;
        final double packagingConfidence;
        final RecognitionEvidence.NormalizedRect rect;

        PaddleDateEvidence(
                String text,
                String originalText,
                double confidence,
                RecognitionEvidence.NormalizedRect rect
        ) {
            this(text, originalText, originalText, confidence, confidence, rect);
        }

        PaddleDateEvidence(
                String text,
                String originalText,
                String packagingText,
                double confidence,
                double packagingConfidence,
                RecognitionEvidence.NormalizedRect rect
        ) {
            this.text = FoodItem.cleanText(text);
            this.originalText = FoodItem.cleanText(originalText);
            this.packagingText = FoodItem.cleanText(packagingText);
            this.confidence = RecognitionEvidence.clamp01(confidence);
            this.packagingConfidence = RecognitionEvidence.clamp01(packagingConfidence);
            this.rect = rect == null
                    ? new RecognitionEvidence.NormalizedRect(0d, 0d, 0d, 0d)
                    : rect;
        }

        PackagingTextAnalyzer.Observation toObservation(double sourceQuality) {
            double width = Math.max(0d, rect.right - rect.left);
            double height = Math.max(0d, rect.bottom - rect.top);
            return new PackagingTextAnalyzer.Observation(
                    text,
                    height,
                    width,
                    (rect.left + rect.right) / 2d,
                    (rect.top + rect.bottom) / 2d,
                    sourceQuality,
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    confidence <= 0d ? Double.NaN : confidence,
                    "PP-OCRv6"
            );
        }

        List<PackagingTextAnalyzer.Observation> toPackagingObservations(double sourceQuality) {
            List<PackagingTextAnalyzer.Observation> observations =
                    new ArrayList<PackagingTextAnalyzer.Observation>();
            if (packagingText.length() == 0) {
                return observations;
            }
            String[] lines = packagingText.split("\\n");
            double width = Math.max(0.02d, rect.right - rect.left);
            double totalHeight = Math.max(0.02d, rect.bottom - rect.top);
            double lineHeight = Math.max(0.02d, totalHeight / Math.max(1, lines.length));
            for (int index = 0; index < lines.length; index++) {
                String line = FoodItem.cleanText(lines[index]);
                if (line.length() == 0) {
                    continue;
                }
                double top = Math.min(rect.bottom, rect.top + (lineHeight * index));
                double bottom = Math.min(rect.bottom, Math.max(top + 0.01d, top + lineHeight));
                observations.add(new PackagingTextAnalyzer.Observation(
                        line,
                        lineHeight,
                        width,
                        (rect.left + rect.right) / 2d,
                        (top + bottom) / 2d,
                        sourceQuality,
                        rect.left,
                        top,
                        rect.right,
                        bottom,
                        packagingConfidence <= 0d ? Double.NaN : packagingConfidence,
                        "PP-OCRv6 检测"
                ));
            }
            return observations;
        }

        static PaddleDateEvidence empty() {
            return new PaddleDateEvidence("", "", 0d, null);
        }
    }

    private static final class SessionKeyframe {
        final RecognitionEvidence.Frame evidence;
        final Bitmap bitmap;
        final RecognitionFrameSelector.FrameCandidate selectorCandidate;

        SessionKeyframe(
                RecognitionEvidence.Frame evidence,
                Bitmap bitmap,
                RecognitionFrameSelector.FrameCandidate selectorCandidate
        ) {
            this.evidence = evidence;
            this.bitmap = bitmap;
            this.selectorCandidate = selectorCandidate;
        }

        RecognitionEvidence.Region primaryRegion() {
            for (RecognitionEvidence.Region region : evidence.regions) {
                if (RecognitionEvidence.FIELD_PRODUCT_NAME.equals(region.field)) {
                    return region;
                }
            }
            RecognitionEvidence.Region strongest = null;
            for (RecognitionEvidence.Region region : evidence.regions) {
                if (strongest == null || region.confidence > strongest.confidence) {
                    strongest = region;
                }
            }
            return strongest == null ? evidence.regions.get(0) : strongest;
        }

        double score() {
            double strongest = 0d;
            for (RecognitionEvidence.Region region : evidence.regions) {
                strongest = Math.max(strongest, region.confidence);
            }
            return (evidence.quality * 0.55d)
                    + (strongest * 0.38d)
                    + (Math.min(3, evidence.regions.size()) * 0.025d);
        }
    }

    private final class RecognitionGuideOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<RecognitionEvidence.Region> evidenceRegions = Collections.emptyList();
        private int evidenceImageWidth;
        private int evidenceImageHeight;

        RecognitionGuideOverlay(ComponentActivity context) {
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
            return Color.rgb(96, 221, 224);
        }

        private void drawCorner(Canvas canvas, float x, float y, float length, boolean left, boolean top) {
            float horizontalEnd = left ? x + length : x - length;
            float verticalEnd = top ? y + length : y - length;
            canvas.drawLine(x, y, horizontalEnd, y, paint);
            canvas.drawLine(x, y, x, verticalEnd, paint);
        }
    }
}
