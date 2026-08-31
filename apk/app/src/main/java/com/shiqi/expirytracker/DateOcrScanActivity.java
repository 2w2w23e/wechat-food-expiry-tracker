package com.shiqi.expirytracker;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.FileProvider;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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

import java.io.File;
import java.io.IOException;
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
    private TextView cameraHud;
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
    private View scannerScreen;
    private View confirmationScreen;
    private TextView confirmationSubtitle;
    private TextView confirmationFieldCount;
    private TextView confirmationProductValue;
    private TextView confirmationProductNote;
    private TextView confirmationBarcodeValue;
    private TextView confirmationBarcodeNote;
    private TextView confirmationDateValue;
    private TextView confirmationDateNote;
    private TextView confirmationFieldsTitle;
    private View confirmationDateDivider;
    private View confirmationProductRow;
    private View confirmationProductDivider;
    private View confirmationBarcodeRow;
    private TextView knownProductBanner;
    private TextView evidenceQualityLabel;
    private ScanFieldView barcodeProgressView;
    private ScanFieldView productProgressView;
    private ScanFieldView dateProgressView;
    private Button automaticModeButton;
    private Button dateModeButton;
    private Button reviewResultButton;
    private Button prioritizeDateButton;
    private Button continueScanButton;
    private ImageButton torchButton;
    private Button fillButton;
    private Button cameraSourceButton;
    private Button videoSourceButton;
    private Button imageSourceButton;
    private Button restartRecognitionButton;

    private ExecutorService cameraAnalyzerExecutor;
    private ExecutorService cameraExecutor;
    private ExecutorService modelWarmupExecutor;
    private CameraXFrameSource cameraFrameSource;
    private LatestFrameProcessor<Bitmap> latestCameraFrames;
    private TextRecognizer textRecognizer;
    private TextRecognizer latinTextRecognizer;
    private BarcodeScanner barcodeScanner;
    private PaddleLineOcrEngine paddleLineOcrEngine;
    private PaddleTextDetectionEngine paddleTextDetectionEngine;
    private final UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer();
    private final RecognitionFrameSelector frameSelector = new RecognitionFrameSelector(1600L);
    private final RecognitionDiagnostics diagnostics = new RecognitionDiagnostics(600);
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
    private AlertDialog evidenceDialog;
    private AlertDialog diagnosticsDialog;
    private AlertDialog productNameEditorDialog;
    private TextView diagnosticsSummaryView;
    private final RecognitionWorkSession recognitionWorkSession =
            new RecognitionWorkSession();
    private volatile int recognitionGeneration;
    private volatile long lastAnalyzeAt;
    private volatile boolean videoReplayActive;
    private volatile boolean barcodeLookupOnly;
    private volatile boolean dateOnlyMode;
    private boolean dateOnlyEntryMode;
    private volatile RecognitionFieldGapPlan.Target directedScanTarget =
            RecognitionFieldGapPlan.Target.AUTOMATIC;
    private volatile boolean cameraSimulationActive;
    private volatile boolean cameraSimulationScreenRecording;
    private volatile boolean longVideoProfile;
    private volatile boolean videoDetailFrame;
    private int analyzedFrameSequence;
    private volatile long recognitionFirstFrameAtMs;
    private long firstProductFieldElapsedMs = -1L;
    private long firstBarcodeFieldElapsedMs = -1L;
    private long firstDateFieldElapsedMs = -1L;
    private int paddleVideoFramesUsed;
    private int paddleDetectionVideoFramesUsed;
    private int paddleFocusedLabelVideoFramesUsed;
    private int paddleCameraBandFramesUsed;
    private int paddleDetectionCameraFramesUsed;
    private int paddleDetectedLineCameraFramesUsed;
    private int lowContrastCameraPassesUsed;
    private int videoAnalyzedFrames;
    private int videoExpectedFrames;
    private volatile boolean cameraBound;
    private volatile boolean liveCameraResultFrozen;
    private Bitmap lastReplayFrame;
    private volatile boolean paddleModelsReady;
    private volatile int cameraFramesCaptured;
    private volatile long cameraLastStatusAtMs;
    private volatile String cameraFocusState = "自动对焦准备中";
    private volatile String cameraFrameState = "等待相机画面";
    private final CameraFocusRecoveryPolicy focusRecoveryPolicy =
            new CameraFocusRecoveryPolicy(2, 1800L);
    private double bestCameraVisualScore = -1d;
    private double bestCameraAcceptedScore = -1d;
    private long lastCameraProbeAtMs;
    private long lastCameraAcceptedAtMs;
    private long lastCameraAcceptedSignature;
    private long lastValuableFramePromptAtMs;
    private long lastDiagnosticsCheckpointAtMs;
    private int cameraProbeCount;
    private volatile double latestTextFocusXRatio = 0.5d;
    private volatile double latestTextFocusYRatio = 0.48d;
    private boolean confirmationVisible;
    private boolean torchEnabled;
    private boolean compactScannerLayout;
    private String cachedLocalMatchBarcode = "";
    private int cachedLocalMatchCount;

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
        dateOnlyEntryMode = dateOnlyMode;
        diagnostics.reset(SystemClock.elapsedRealtime(), "launch");
        diagnostics.value("debug_capture", Boolean.toString(
                RecognitionDebugStore.isEnabled(this)
        ));

        cameraAnalyzerExecutor = Executors.newSingleThreadExecutor();
        cameraExecutor = Executors.newSingleThreadExecutor();
        modelWarmupExecutor = Executors.newSingleThreadExecutor();
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
            final PaddleTextDetectionEngine detectorToWarm = paddleTextDetectionEngine;
            final PaddleLineOcrEngine recognizerToWarm = paddleLineOcrEngine;
            modelWarmupExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    detectorToWarm.warmUp();
                    recognizerToWarm.warmUp();
                    paddleModelsReady = true;
                }
            });
        }

        buildScreen();
        initializeCameraPipeline();
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
            ensureCameraPermission(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (confirmationVisible) {
            showScannerScreen();
            return;
        }
        if (!currentUiPresentation().hasReviewableResult) {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
            return;
        }
        requestExitRecognition();
    }
    @Override
    protected void onPause() {
        persistDiagnosticsSnapshot("activity.pause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        persistDiagnosticsSnapshot("activity.destroy");
        recognitionGeneration = recognitionWorkSession.close().generation;
        stopVideoReplay();
        if (evidenceDialog != null) {
            evidenceDialog.dismiss();
            evidenceDialog = null;
        }
        if (diagnosticsDialog != null) {
            diagnosticsDialog.dismiss();
            diagnosticsDialog = null;
            diagnosticsSummaryView = null;
        }
        if (productNameEditorDialog != null) {
            productNameEditorDialog.dismiss();
            productNameEditorDialog = null;
        }
        stopCamera();
        clearLastReplayFrame();
        clearSessionKeyframes();
        ExecutorService recognitionExecutor = cameraExecutor;
        cameraExecutor = null;
        ExecutorService analyzerExecutor = cameraAnalyzerExecutor;
        cameraAnalyzerExecutor = null;
        ExecutorService warmupExecutor = modelWarmupExecutor;
        modelWarmupExecutor = null;
        if (analyzerExecutor != null) {
            analyzerExecutor.shutdownNow();
        }
        if (recognitionExecutor != null) {
            recognitionExecutor.shutdownNow();
        }
        if (warmupExecutor != null) {
            warmupExecutor.shutdownNow();
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
        compactScannerLayout = getResources().getConfiguration().screenHeightDp < 700;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        FrameLayout scanner = new FrameLayout(this);
        scanner.setBackgroundColor(Color.BLACK);
        scannerScreen = scanner;
        root.addView(scanner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        scanner.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        replayFrameView = new ImageView(this);
        replayFrameView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        replayFrameView.setBackgroundColor(Color.BLACK);
        replayFrameView.setVisibility(View.GONE);
        scanner.addView(replayFrameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        recognitionOverlay = new RecognitionGuideOverlay(this);
        scanner.addView(recognitionOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.HORIZONTAL);
        topPanel.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        topPanel.setBackgroundColor(Color.argb(205, 15, 23, 29));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72)
        );
        topParams.gravity = Gravity.TOP;
        scanner.addView(topPanel, topParams);

        ImageButton closeButton = iconButton(
                R.drawable.ic_v2_back,
                Color.WHITE,
                Color.argb(56, 255, 255, 255),
                "返回"
        );
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestExitRecognition();
            }
        });
        topPanel.addView(closeButton, fixedLinear(48, 48));

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setGravity(Gravity.CENTER);
        titleStack.setPadding(dp(6), 0, dp(6), 0);
        topPanel.addView(titleStack, weightWrap(1));

        statusBadge = new TextView(this);
        statusBadge.setText("自动识别");
        statusBadge.setTextColor(Color.WHITE);
        statusBadge.setTextSize(17);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusBadge.setMaxLines(1);
        titleStack.addView(statusBadge, matchWrap());

        statusText = new TextView(this);
        statusText.setText("对准商品正面或日期");
        statusText.setTextColor(Color.rgb(222, 229, 233));
        statusText.setTextSize(12);
        statusText.setGravity(Gravity.CENTER);
        statusText.setMaxLines(2);
        statusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleStack.addView(statusText, matchWrap());

        torchButton = iconButton(
                R.drawable.ic_v2_flashlight,
                Color.WHITE,
                Color.argb(56, 255, 255, 255),
                "打开手电筒"
        );
        torchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleTorch();
            }
        });
        topPanel.addView(torchButton, fixedLinear(48, 48));

        ImageButton helpButton = iconButton(
                R.drawable.ic_v2_help,
                Color.WHITE,
                Color.argb(56, 255, 255, 255),
                "扫描帮助"
        );
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showScanHelp();
            }
        });
        topPanel.addView(helpButton, fixedLinear(48, 48));

        cameraHud = new TextView(this);
        cameraHud.setText("正在对焦，请保持稳定");
        cameraHud.setTextColor(Color.WHITE);
        cameraHud.setTextSize(13);
        cameraHud.setGravity(Gravity.CENTER);
        cameraHud.setMaxLines(1);
        cameraHud.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cameraHud.setPadding(dp(12), dp(6), dp(12), dp(6));
        cameraHud.setBackground(rounded(Color.argb(220, 20, 30, 36), dp(8), 0));
        cameraHud.setContentDescription("相机画面状态");
        FrameLayout.LayoutParams hudParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
        );
        hudParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hudParams.topMargin = dp(82);
        hudParams.leftMargin = dp(16);
        hudParams.rightMargin = dp(16);
        scanner.addView(cameraHud, hudParams);

        LinearLayout bottomShell = new LinearLayout(this);
        bottomShell.setOrientation(LinearLayout.VERTICAL);
        bottomShell.setPadding(dp(12), dp(10), dp(12), dp(10));
        bottomShell.setBackground(rounded(Color.rgb(249, 250, 252), dp(8), 0));
        resultSheet = bottomShell;

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(dp(3), dp(3), dp(3), dp(3));
        modeRow.setBackground(rounded(Color.rgb(235, 239, 244), dp(8), 0));
        automaticModeButton = segmentButton("自动");
        dateModeButton = segmentButton("日期特写");
        modeRow.addView(automaticModeButton, weightMatch(1));
        modeRow.addView(dateModeButton, weightMatch(1));
        bottomShell.addView(modeRow, fixedHeight(compactScannerLayout ? 42 : 48));
        modeRow.setVisibility(dateOnlyEntryMode ? View.GONE : View.VISIBLE);

        automaticModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dateOnlyMode = false;
                directedScanTarget = RecognitionFieldGapPlan.Target.AUTOMATIC;
                updateModeButtons();
                updateResultUi(latestSnapshot);
            }
        });
        dateModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dateOnlyMode = true;
                directedScanTarget = RecognitionFieldGapPlan.Target.DATES;
                updateModeButtons();
                updateResultUi(latestSnapshot);
                requestDateFocus();
            }
        });

        Space modeGap = new Space(this);
        bottomShell.addView(modeGap, fixedHeight(compactScannerLayout ? 4 : 6));

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER);
        barcodeProgressView = buildScanField(R.drawable.ic_v2_barcode, "条码");
        productProgressView = buildScanField(R.drawable.ic_v2_type, "名称");
        dateProgressView = buildScanField(R.drawable.ic_v2_calendar, "日期");
        if (dateOnlyEntryMode) {
            progressRow.addView(dateProgressView.root, weightMatch(1));
        } else {
            progressRow.addView(barcodeProgressView.root, weightMatch(1));
            progressRow.addView(verticalDivider(), fixedLinear(1, 58));
            progressRow.addView(productProgressView.root, weightMatch(1));
            progressRow.addView(verticalDivider(), fixedLinear(1, 58));
            progressRow.addView(dateProgressView.root, weightMatch(1));
        }
        bottomShell.addView(progressRow, fixedHeight(compactScannerLayout ? 60 : 72));

        reviewResultButton = outlinedActionButton("查看当前识别结果");
        reviewResultButton.setEnabled(true);
        reviewResultButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConfirmationScreen();
            }
        });
        bottomShell.addView(reviewResultButton, fixedHeight(48));
        reviewResultButton.setVisibility(
                compactScannerLayout || dateOnlyEntryMode ? View.GONE : View.VISIBLE
        );

        Space actionGap = new Space(this);
        bottomShell.addView(actionGap, fixedHeight(compactScannerLayout ? 4 : 6));

        prioritizeDateButton = primaryActionButton("优先识别日期");
        prioritizeDateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dateOnlyEntryMode) {
                    UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot == null
                            ? stabilizer.snapshot()
                            : latestSnapshot;
                    if (snapshot.hasStableDateCandidate()) {
                        showConfirmationScreen();
                    } else {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                    return;
                }
                ScanUiPresentation.State state = currentUiPresentation();
                if (state.ready || compactScannerLayout) {
                    showConfirmationScreen();
                    return;
                }
                dateOnlyMode = true;
                directedScanTarget = RecognitionFieldGapPlan.Target.DATES;
                updateModeButtons();
                updateResultUi(latestSnapshot);
                requestDateFocus();
            }
        });
        bottomShell.addView(prioritizeDateButton, fixedHeight(compactScannerLayout ? 44 : 48));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(dateOnlyEntryMode
                        ? (compactScannerLayout ? 136 : 154)
                        : (compactScannerLayout ? 174 : 248))
        );
        bottomParams.gravity = Gravity.BOTTOM;
        scanner.addView(bottomShell, bottomParams);

        productCandidateScroll = new HorizontalScrollView(this);
        productCandidateScroll.setVisibility(View.GONE);
        productCandidateContainer = new LinearLayout(this);
        productCandidateContainer.setOrientation(LinearLayout.HORIZONTAL);
        productCandidateScroll.addView(productCandidateContainer);

        rawPreviewText = new TextView(this);
        rawPreviewText.setVisibility(View.GONE);

        confirmationScreen = buildConfirmationScreen();
        confirmationScreen.setVisibility(View.GONE);
        root.addView(confirmationScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
        updateModeButtons();
    }

    private View buildConfirmationScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(247, 249, 251));

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        appBar.setBackgroundColor(Color.WHITE);

        ImageButton backButton = iconButton(
                R.drawable.ic_v2_back,
                Color.rgb(23, 33, 43),
                Color.WHITE,
                "返回扫描"
        );
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showScannerScreen();
            }
        });
        appBar.addView(backButton, fixedLinear(48, 48));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.CENTER);
        appBar.addView(heading, weightWrap(1));

        TextView title = new TextView(this);
        title.setText("核对结果");
        title.setTextColor(Color.rgb(23, 33, 43));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        heading.addView(title, matchWrap());

        confirmationSubtitle = new TextView(this);
        confirmationSubtitle.setText("请核对识别字段后再使用");
        confirmationSubtitle.setTextColor(Color.rgb(100, 116, 139));
        confirmationSubtitle.setTextSize(12);
        confirmationSubtitle.setGravity(Gravity.CENTER);
        confirmationSubtitle.setMaxLines(1);
        confirmationSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(confirmationSubtitle, matchWrap());

        ImageButton moreButton = iconButton(
                R.drawable.ic_v2_more,
                Color.rgb(23, 33, 43),
                Color.WHITE,
                "更多识别信息"
        );
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConfirmationMenu();
            }
        });
        appBar.addView(moreButton, fixedLinear(48, 48));
        screen.addView(appBar, fixedHeight(72));
        screen.addView(horizontalDivider(), fixedHeight(1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        screen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        knownProductBanner = new TextView(this);
        knownProductBanner.setTextColor(Color.rgb(38, 82, 177));
        knownProductBanner.setTextSize(14);
        knownProductBanner.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        knownProductBanner.setGravity(Gravity.CENTER_VERTICAL);
        knownProductBanner.setPadding(dp(12), dp(10), dp(12), dp(10));
        knownProductBanner.setBackground(rounded(
                Color.rgb(235, 241, 255),
                dp(8),
                Color.rgb(192, 209, 247)
        ));
        knownProductBanner.setVisibility(View.GONE);
        content.addView(knownProductBanner, matchWrap());

        LinearLayout evidenceHeading = new LinearLayout(this);
        evidenceHeading.setGravity(Gravity.CENTER_VERTICAL);
        evidenceHeading.setPadding(0, dp(14), 0, dp(6));
        keyframeTitle = sectionTitle("识别依据");
        evidenceHeading.addView(keyframeTitle, weightWrap(1));
        evidenceQualityLabel = new TextView(this);
        evidenceQualityLabel.setText("等待清晰画面");
        evidenceQualityLabel.setTextColor(Color.rgb(23, 132, 93));
        evidenceQualityLabel.setTextSize(12);
        evidenceQualityLabel.setPadding(dp(8), dp(4), dp(8), dp(4));
        evidenceQualityLabel.setBackground(rounded(Color.rgb(232, 247, 240), dp(8), 0));
        evidenceHeading.addView(evidenceQualityLabel, wrapWrap());
        content.addView(evidenceHeading, matchWrap());

        keyframeScroll = new HorizontalScrollView(this);
        keyframeScroll.setHorizontalScrollBarEnabled(false);
        keyframeScroll.setFillViewport(false);
        keyframeContainer = new LinearLayout(this);
        keyframeContainer.setOrientation(LinearLayout.HORIZONTAL);
        keyframeScroll.addView(keyframeContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        content.addView(keyframeScroll, fixedHeight(96));

        LinearLayout fieldHeading = new LinearLayout(this);
        fieldHeading.setGravity(Gravity.CENTER_VERTICAL);
        fieldHeading.setPadding(0, dp(18), 0, dp(6));
        confirmationFieldsTitle = sectionTitle(
                dateOnlyEntryMode ? "日期信息" : "商品与批次"
        );
        fieldHeading.addView(confirmationFieldsTitle, weightWrap(1));
        confirmationFieldCount = new TextView(this);
        confirmationFieldCount.setText(dateOnlyEntryMode ? "0 / 1" : "0 / 3");
        confirmationFieldCount.setTextColor(Color.rgb(100, 116, 139));
        confirmationFieldCount.setTextSize(13);
        fieldHeading.addView(confirmationFieldCount, wrapWrap());
        content.addView(fieldHeading, matchWrap());

        confirmationDateValue = reviewValue("尚未找到清楚日期");
        confirmationDateValue.setMaxLines(4);
        confirmationDateValue.setEllipsize(null);
        confirmationDateNote = reviewNote("继续对准喷码、拓印或激光日期");
        View dateRow = buildReviewRow(
                R.drawable.ic_v2_calendar,
                "生产日期 / 保质期 / 有效期",
                confirmationDateValue,
                confirmationDateNote,
                true
        );
        dateRow.setContentDescription("核对日期或返回补扫日期");
        dateRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                beginDirectedRescan(RecognitionFieldGapPlan.Target.DATES);
            }
        });
        content.addView(dateRow, matchWrap());
        confirmationDateDivider = horizontalDivider();
        content.addView(confirmationDateDivider, fixedHeight(1));

        confirmationProductValue = reviewValue("尚未识别商品名称");
        confirmationProductNote = reviewNote("点击可修改错字、选择建议或补扫");
        confirmationProductRow = buildReviewRow(
                R.drawable.ic_v2_type,
                "商品名称",
                confirmationProductValue,
                confirmationProductNote,
                false
        );
        confirmationProductRow.setContentDescription("核对商品名称");
        confirmationProductRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showProductReview();
            }
        });
        content.addView(confirmationProductRow, matchWrap());
        confirmationProductDivider = horizontalDivider();
        content.addView(confirmationProductDivider, fixedHeight(1));

        confirmationBarcodeValue = reviewValue("尚未识别商品码");
        confirmationBarcodeNote = reviewNote("点击此行可补扫条码或查看证据");
        confirmationBarcodeRow = buildReviewRow(
                R.drawable.ic_v2_barcode,
                "条码",
                confirmationBarcodeValue,
                confirmationBarcodeNote,
                false
        );
        confirmationBarcodeRow.setContentDescription("查看条码识别依据");
        confirmationBarcodeRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!showEvidenceForField(RecognitionEvidence.FIELD_BARCODE)) {
                    beginDirectedRescan(RecognitionFieldGapPlan.Target.BARCODE);
                }
            }
        });
        content.addView(confirmationBarcodeRow, matchWrap());

        if (dateOnlyEntryMode) {
            confirmationDateDivider.setVisibility(View.GONE);
            confirmationProductRow.setVisibility(View.GONE);
            confirmationProductDivider.setVisibility(View.GONE);
            confirmationBarcodeRow.setVisibility(View.GONE);
        }

        productValue = confirmationProductValue;
        barcodeValue = confirmationBarcodeValue;
        productionDateValue = confirmationDateValue;

        screen.addView(horizontalDivider(), fixedHeight(1));
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(12), dp(10), dp(12), dp(10));
        footer.setBackgroundColor(Color.WHITE);

        continueScanButton = outlinedActionButton("继续扫描");
        continueScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showScannerScreen();
                showRecognitionSourceDialog();
            }
        });
        footer.addView(continueScanButton, withMargins(weightMatch(1), 0, 0, dp(8), 0));

        fillButton = primaryActionButton("使用这些结果");
        fillButton.setEnabled(false);
        fillButton.setAlpha(0.45f);
        fillButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishWithCandidate();
            }
        });
        footer.addView(fillButton, weightMatch(2));
        screen.addView(footer, fixedHeight(72));

        return screen;
    }

    private ScanFieldView buildScanField(int iconResource, String labelText) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(2), dp(3), dp(2), dp(3));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(Color.rgb(100, 116, 139));
        icon.setContentDescription(labelText);
        root.addView(icon, fixedLinear(24, 24));

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.rgb(71, 85, 105));
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        root.addView(label, matchWrap());

        TextView state = new TextView(this);
        state.setText("查找中");
        state.setTextColor(Color.rgb(100, 116, 139));
        state.setTextSize(12);
        state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        state.setGravity(Gravity.CENTER);
        state.setMaxLines(1);
        root.addView(state, matchWrap());
        return new ScanFieldView(root, icon, state);
    }

    private View buildReviewRow(
            int iconResource,
            String labelText,
            TextView value,
            TextView note,
            boolean cameraAction
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(82));
        row.setPadding(dp(4), dp(9), dp(4), dp(9));
        row.setBackgroundColor(Color.WHITE);
        row.setClickable(true);
        row.setFocusable(true);

        FrameLayout iconShell = new FrameLayout(this);
        iconShell.setBackground(rounded(
                cameraAction ? Color.rgb(255, 246, 224) : Color.rgb(232, 247, 240),
                dp(8),
                0
        ));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(cameraAction
                ? Color.rgb(180, 120, 18)
                : Color.rgb(23, 132, 93));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        iconShell.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        row.addView(iconShell, fixedLinear(44, 44));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(8), 0);
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.rgb(100, 116, 139));
        label.setTextSize(12);
        copy.addView(label, matchWrap());
        copy.addView(value, matchWrap());
        copy.addView(note, matchWrap());
        row.addView(copy, weightWrap(1));

        ImageView action = new ImageView(this);
        action.setImageResource(cameraAction ? R.drawable.ic_v2_camera : R.drawable.ic_v2_more);
        action.setColorFilter(cameraAction
                ? Color.rgb(47, 102, 237)
                : Color.rgb(100, 116, 139));
        action.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.addView(action, fixedLinear(48, 48));
        return row;
    }

    private TextView reviewValue(String text) {
        TextView value = new TextView(this);
        value.setText(text);
        value.setTextColor(Color.rgb(23, 33, 43));
        value.setTextSize(16);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setMaxLines(2);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return value;
    }

    private TextView reviewNote(String text) {
        TextView note = new TextView(this);
        note.setText(text);
        note.setTextColor(Color.rgb(100, 116, 139));
        note.setTextSize(12);
        note.setMaxLines(2);
        note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return note;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(Color.rgb(23, 33, 43));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return title;
    }

    private Button segmentButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private Button primaryActionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(Color.rgb(47, 102, 237), dp(8), 0));
        return button;
    }

    private Button outlinedActionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(47, 102, 237));
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(Color.WHITE, dp(8), Color.rgb(180, 199, 246)));
        return button;
    }

    private ImageButton iconButton(
            int drawableResource,
            int tintColor,
            int backgroundColor,
            String description
    ) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableResource);
        button.setColorFilter(tintColor);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(rounded(backgroundColor, dp(8), 0));
        button.setContentDescription(description);
        button.setMinimumWidth(dp(48));
        button.setMinimumHeight(dp(48));
        return button;
    }

    private View horizontalDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(221, 227, 234));
        return divider;
    }

    private View verticalDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(225, 230, 236));
        return divider;
    }

    private LinearLayout.LayoutParams fixedLinear(int widthDp, int heightDp) {
        return new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp));
    }

    private LinearLayout.LayoutParams weightMatch(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }
    private TextView compactResultText() {
        TextView value = new TextView(this);
        value.setTextColor(Color.rgb(70, 82, 73));
        value.setTextSize(16);
        value.setMaxLines(5);
        value.setPadding(0, dp(2), 0, 0);
        return value;
    }

    private RecognitionFieldGapPlan currentFieldGapPlan() {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot == null
                ? stabilizer.snapshot()
                : latestSnapshot;
        boolean stableProduct = selectedPackagingName.length() > 0
                || (snapshot != null && snapshot.hasStablePackagingName())
                || (snapshot != null
                && latestProductBarcode.equals(snapshot.stableBarcode)
                && latestProductInfo != null
                && latestProductInfo.found);
        boolean stableBarcode = snapshot != null && snapshot.hasStableBarcode();
        boolean stableDates = snapshot != null && snapshot.hasStableDateCandidate();
        boolean dateConflict = snapshot != null
                && ((snapshot.latestDateVote != null && snapshot.latestDateVote.hasConflict)
                || (snapshot.stableDateVote != null && snapshot.stableDateVote.hasConflict));
        return RecognitionFieldGapPlan.evaluate(
                directedScanTarget,
                dateOnlyMode,
                stableProduct,
                stableBarcode,
                stableDates,
                dateConflict,
                analyzedFrameSequence
        );
    }
    private ScanUiPresentation.State currentUiPresentation() {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot == null
                ? stabilizer.snapshot()
                : latestSnapshot;
        boolean stableProduct = selectedPackagingName.length() > 0
                || (snapshot != null && snapshot.hasStablePackagingName())
                || (snapshot != null
                && latestProductBarcode.equals(snapshot.stableBarcode)
                && latestProductInfo != null
                && latestProductInfo.found);
        boolean sawText = latestRawText.length() > 0
                || (snapshot != null && !snapshot.rankedPackagingCandidates.isEmpty());
        boolean sawDateCandidate = snapshot != null && snapshot.latestDateVote != null;
        boolean conflict = snapshot != null
                && ((snapshot.latestDateVote != null && snapshot.latestDateVote.hasConflict)
                || (snapshot.stableDateVote != null && snapshot.stableDateVote.hasConflict));
        return ScanUiPresentation.from(
                dateOnlyMode,
                snapshot != null && snapshot.hasStableBarcode(),
                stableProduct,
                snapshot != null && snapshot.hasStableDateCandidate(),
                sawText,
                sawDateCandidate,
                conflict
        );
    }

    private String productNameForResult(String packagingName, BarcodeProductInfo info) {
        String packaging = RecognitionTextCleaner.intelligentProductNameCandidate(packagingName);
        String lookupName = info != null && info.found ? info.displayName() : "";
        String lookup = RecognitionTextCleaner.intelligentProductNameCandidate(lookupName);
        if (lookup.length() > 0 && isAuthoritativeBarcodeInfo(info)) {
            return RecognitionTextCleaner.preferredAuthoritativeBarcodeName(packaging, lookup);
        }
        return RecognitionTextCleaner.preferredRecognizedProductName(
                packaging,
                lookup
        );
    }
    private boolean isAuthoritativeBarcodeInfo(BarcodeProductInfo info) {
        if (info == null || !info.found) {
            return false;
        }
        String source = FoodItem.cleanText(info.source);
        return info.registered
                || source.contains("核验")
                || source.contains("GS1")
                || source.contains("中国商品信息服务平台");
    }

    private String recommendedProductName(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (selectedPackagingName.length() > 0) {
            return selectedPackagingName;
        }
        String packagingName = snapshot == null
                ? ""
                : FoodItem.cleanText(snapshot.bestPackagingNameForConfirmation());
        BarcodeProductInfo info = snapshot != null
                && latestProductBarcode.equals(snapshot.stableBarcode)
                ? latestProductInfo
                : null;
        String name = productNameForResult(packagingName, info);
        if (name.length() > 0) {
            return name;
        }
        if (snapshot != null && snapshot.hasStablePackagingName()) {
            return FoodItem.cleanText(snapshot.stablePackagingName);
        }
        return "";
    }

    private void updatePresentationUi(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot == null) {
            snapshot = stabilizer.snapshot();
        }
        ScanUiPresentation.State state = currentUiPresentation();
        updateScanField(barcodeProgressView, state.barcode);
        updateScanField(productProgressView, state.productName);
        updateScanField(dateProgressView, state.date);

        if (reviewResultButton != null) {
            reviewResultButton.setText(state.hasReviewableResult
                    ? state.reviewAction
                    : "跳过识别，手动填写");
            reviewResultButton.setEnabled(true);
            reviewResultButton.setAlpha(1f);
        }
        if (prioritizeDateButton != null) {
            if (dateOnlyEntryMode) {
                boolean hasDate = snapshot.hasStableDateCandidate();
                prioritizeDateButton.setText(hasDate
                        ? "核对识别日期"
                        : "返回手动填写");
                prioritizeDateButton.setEnabled(true);
                prioritizeDateButton.setAlpha(1f);
            } else {
                String primaryAction = compactScannerLayout
                        ? (state.hasReviewableResult
                        ? state.reviewAction
                        : "跳过识别，手动填写")
                        : state.primaryScanAction;
                prioritizeDateButton.setText(barcodeLookupOnly
                        ? "对准条码继续识别"
                        : primaryAction);
            }
        }
        if (confirmationSubtitle != null) {
            confirmationSubtitle.setText(state.confirmationSubtitle);
        }
        if (confirmationFieldCount != null) {
            confirmationFieldCount.setText(dateOnlyEntryMode
                    ? (snapshot.hasStableDateCandidate() ? "1 / 1" : "0 / 1")
                    : state.recognizedCount + " / 3");
        }

        String productName = recommendedProductName(snapshot);
        if (confirmationProductValue != null) {
            confirmationProductValue.setText(productName.length() > 0
                    ? productName
                    : state.productName == ScanUiPresentation.FieldState.LOCATED
                    ? "已看到文字，尚未形成可靠名称"
                    : "尚未识别商品名称");
        }
        if (confirmationProductNote != null) {
            confirmationProductNote.setText(productName.length() > 0
                    ? "点击可直接修改错字；识别建议仅供核对"
                    : "未识别也可继续填写，或点击补扫商品名");
        }

        if (confirmationBarcodeValue != null) {
            confirmationBarcodeValue.setText(snapshot.hasStableBarcode()
                    ? snapshot.stableBarcode
                    : "尚未识别商品码");
        }
        if (confirmationBarcodeNote != null) {
            confirmationBarcodeNote.setText(snapshot.hasStableBarcode()
                    ? "跨帧稳定；条码可关联多个商品档案"
                    : "条码不是保存主键，可稍后补充");
        }

        FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
        if (confirmationDateValue != null) {
            confirmationDateValue.setText(snapshot.hasStableDateCandidate()
                    ? compactDateSummary(dateDraft)
                    : state.date == ScanUiPresentation.FieldState.ATTENTION
                    ? "出现多个日期，请继续扫描后核对"
                    : state.date == ScanUiPresentation.FieldState.LOCATED
                    ? "已看到日期样文字，尚未稳定"
                    : "尚未找到清楚日期");
        }
        if (confirmationDateNote != null) {
            confirmationDateNote.setText(state.date == ScanUiPresentation.FieldState.ATTENTION
                    ? "日期证据有冲突，不会静默预选"
                    : snapshot.hasStableDateCandidate()
                    ? "来自关键帧和跨帧推理，请确认字段含义"
                    : "继续对准喷码、拓印或激光日期");
        }

        if (evidenceQualityLabel != null) {
            int keyframeCount = keyframeSnapshot().size();
            evidenceQualityLabel.setText(keyframeCount > 0
                    ? "已保留 " + Math.min(3, keyframeCount) + " 帧"
                    : "等待清晰画面");
        }
        updateKnownProductBanner(snapshot);

        if (fillButton != null) {
            fillButton.setText(dateOnlyMode ? "返回填写日期" : "继续填写");
            fillButton.setEnabled(true);
            fillButton.setAlpha(1f);
        }
        updateModeButtons();
    }

    private void updateScanField(ScanFieldView field, ScanUiPresentation.FieldState state) {
        if (field == null) {
            return;
        }
        int foreground;
        int background;
        if (state == ScanUiPresentation.FieldState.READY) {
            foreground = Color.rgb(23, 132, 93);
            background = Color.rgb(232, 247, 240);
        } else if (state == ScanUiPresentation.FieldState.ATTENTION) {
            foreground = Color.rgb(180, 120, 18);
            background = Color.rgb(255, 246, 224);
        } else if (state == ScanUiPresentation.FieldState.LOCATED) {
            foreground = Color.rgb(47, 102, 237);
            background = Color.rgb(235, 241, 255);
        } else {
            foreground = Color.rgb(100, 116, 139);
            background = Color.TRANSPARENT;
        }
        field.icon.setColorFilter(foreground);
        field.status.setText(ScanUiPresentation.fieldStatus(state));
        field.status.setTextColor(foreground);
        field.root.setBackground(background == Color.TRANSPARENT
                ? null
                : rounded(background, dp(6), 0));
    }

    private void updateModeButtons() {
        if (automaticModeButton == null || dateModeButton == null) {
            return;
        }
        if (barcodeLookupOnly) {
            automaticModeButton.setText("扫码查找商品");
            automaticModeButton.setVisibility(View.VISIBLE);
            dateModeButton.setVisibility(View.GONE);
            automaticModeButton.setTextColor(Color.rgb(23, 33, 43));
            automaticModeButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            automaticModeButton.setBackground(rounded(Color.WHITE, dp(7), 0));
            if (productProgressView != null) {
                productProgressView.root.setVisibility(View.GONE);
            }
            if (dateProgressView != null) {
                dateProgressView.root.setVisibility(View.GONE);
            }
            if (reviewResultButton != null) {
                reviewResultButton.setVisibility(View.GONE);
            }
            return;
        }
        if (dateOnlyEntryMode) {
            automaticModeButton.setVisibility(View.GONE);
            dateModeButton.setVisibility(View.GONE);
            if (barcodeProgressView != null) {
                barcodeProgressView.root.setVisibility(View.GONE);
            }
            if (productProgressView != null) {
                productProgressView.root.setVisibility(View.GONE);
            }
            if (dateProgressView != null) {
                dateProgressView.root.setVisibility(View.VISIBLE);
            }
            if (reviewResultButton != null) {
                reviewResultButton.setVisibility(View.GONE);
            }
            return;
        }
        automaticModeButton.setText("自动");
        dateModeButton.setVisibility(View.VISIBLE);
        if (productProgressView != null) {
            productProgressView.root.setVisibility(View.VISIBLE);
        }
        if (dateProgressView != null) {
            dateProgressView.root.setVisibility(View.VISIBLE);
        }
        if (reviewResultButton != null) {
            reviewResultButton.setVisibility(compactScannerLayout ? View.GONE : View.VISIBLE);
        }
        styleModeButton(automaticModeButton, !dateOnlyMode);
        styleModeButton(dateModeButton, dateOnlyMode);
    }

    private void styleModeButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.rgb(23, 33, 43) : Color.rgb(100, 116, 139));
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(selected
                ? rounded(Color.WHITE, dp(7), 0)
                : rounded(Color.TRANSPARENT, dp(7), 0));
    }

    private void showConfirmationScreen() {
        ScanUiPresentation.State state = currentUiPresentation();
        diagnosticEvent("confirmation.open", "partial=" + !state.ready);
        liveCameraResultFrozen = true;
        recognitionGeneration = recognitionWorkSession
                .showConfirmation(cameraBound || videoReplayActive)
                .generation;
        if (cameraBound) {
            stopCamera();
        }
        if (videoReplayActive) {
            stopVideoReplay();
        }
        confirmationVisible = true;
        if (scannerScreen != null) {
            scannerScreen.setVisibility(View.GONE);
        }
        if (confirmationScreen != null) {
            confirmationScreen.setVisibility(View.VISIBLE);
        }
        setLightSystemBars(true);
        renderKeyframes();
        updatePresentationUi(latestSnapshot);
    }

    private void showScannerScreen() {
        recognitionGeneration = recognitionWorkSession.resumeScanner().generation;
        confirmationVisible = false;
        if (confirmationScreen != null) {
            confirmationScreen.setVisibility(View.GONE);
        }
        if (scannerScreen != null) {
            scannerScreen.setVisibility(View.VISIBLE);
        }
        setLightSystemBars(false);
        List<SessionKeyframe> keyframes = keyframeSnapshot();
        if (!cameraBound && !videoReplayActive && !keyframes.isEmpty()) {
            showEvidenceFrame(keyframes.get(0), false);
        }
        updatePresentationUi(latestSnapshot);
    }

    private void setLightSystemBars(boolean light) {
        Window window = getWindow();
        window.setStatusBarColor(light ? Color.WHITE : Color.rgb(15, 23, 29));
        window.setNavigationBarColor(light ? Color.WHITE : Color.rgb(15, 23, 29));
        int flags = window.getDecorView().getSystemUiVisibility();
        if (light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void requestDateFocus() {
        statusBadge.setText("日期特写");
        statusText.setText("让喷码、拓印或激光日期尽量占满框");
        if (cameraFrameSource != null) {
            cameraFrameSource.focusAtCenter(true, false);
        }
    }

    private void toggleTorch() {
        if (cameraFrameSource == null || !cameraFrameSource.hasFlashUnit()) {
            Toast.makeText(this, "当前相机不支持手电筒", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean nextTorchState = !torchEnabled;
        if (!cameraFrameSource.setTorch(nextTorchState)) {
            Toast.makeText(this, "手电筒开启失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }
        torchEnabled = nextTorchState;
        torchButton.setContentDescription(torchEnabled ? "关闭手电筒" : "打开手电筒");
        torchButton.setBackground(rounded(
                torchEnabled ? Color.rgb(47, 102, 237) : Color.argb(56, 255, 255, 255),
                dp(8),
                0
        ));
    }

    private void showScanHelp() {
        new AlertDialog.Builder(this)
                .setTitle("怎样更快识别")
                .setMessage("先对准商品正面；找到名称或条码后，再切到日期特写。"
                        + "\n\n日期发糊时轻点文字并稍后退；反光时倾斜手机，不要让灯光正对包装。"
                        + "\n\n识别结果只会进入核对页，不会自动保存。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showConfirmationMenu() {
        final String[] actions = isDiagnosticsAvailable()
                ? new String[] {"查看全部文字", "识别诊断（测试版）"}
                : new String[] {"查看全部文字"};
        new AlertDialog.Builder(this)
                .setTitle("更多识别信息")
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showRawTextDialog();
                        } else {
                            showDiagnosticsDialog();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void beginDirectedRescan(RecognitionFieldGapPlan.Target target) {
        directedScanTarget = target == null
                ? RecognitionFieldGapPlan.Target.AUTOMATIC
                : target;
        showScannerScreen();
        prepareCameraSurfacePreservingRecognition();
        RecognitionFieldGapPlan plan = currentFieldGapPlan();
        statusBadge.setText(plan.label);
        statusText.setText(plan.guidance + "；已有识别结果会保留");
        ensureCameraPermission(true);
    }

    private void showProductReview() {
        showProductNameEditor(latestSnapshot);
    }

    private void showProductNameEditor(final UnifiedRecognitionStabilizer.Snapshot snapshot) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), 0);

        TextView helper = reviewNote("识别结果只是草稿，可直接修正漏字或错字。");
        helper.setTextSize(13);
        helper.setPadding(0, 0, 0, dp(10));
        content.addView(helper, matchWrap());

        final EditText input = new EditText(this);
        String currentName = recommendedProductName(snapshot);
        input.setText(currentName);
        input.setHint("输入完整商品名称");
        input.setTextColor(Color.rgb(23, 33, 43));
        input.setTextSize(17);
        input.setSingleLine(false);
        input.setMaxLines(2);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(rounded(Color.WHITE, dp(8), Color.rgb(180, 199, 246)));
        if (currentName.length() > 0) {
            input.setSelection(currentName.length());
        }
        content.addView(input, matchWrap());

        String suggestions = productNameSuggestions(snapshot);
        if (suggestions.length() > 0) {
            TextView suggestionView = reviewNote("识别建议：" + suggestions);
            suggestionView.setPadding(0, dp(10), 0, 0);
            suggestionView.setMaxLines(3);
            content.addView(suggestionView, matchWrap());
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("修改商品名称")
                .setView(content)
                .setPositiveButton("保存修改", null)
                .setNeutralButton("补扫商品名", null)
                .setNegativeButton("取消", null)
                .create();
        productNameEditorDialog = dialog;
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                String edited = FoodItem.cleanText(input.getText().toString());
                                if (edited.length() == 0) {
                                    input.setError("请输入商品名称，或取消后直接继续填写");
                                    return;
                                }
                                if (edited.length() > 60) {
                                    input.setError("商品名称最多 60 个字符");
                                    return;
                                }
                                String previous = recommendedProductName(snapshot);
                                selectedPackagingName = edited;
                                diagnosticEvent(
                                        "field.product.edit",
                                        "changed=" + !edited.equals(previous)
                                                + " value=" + edited
                                );
                                updateResultUi(latestSnapshot);
                                dialog.dismiss();
                            }
                        });
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                dialog.dismiss();
                                beginDirectedRescan(RecognitionFieldGapPlan.Target.PRODUCT_NAME);
                            }
                        });
            }
        });
        dialog.show();
        input.requestFocus();
    }

    private String productNameSuggestions(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot == null || snapshot.rankedPackagingCandidates.isEmpty()) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        int count = Math.min(3, snapshot.rankedPackagingCandidates.size());
        for (int index = 0; index < count; index++) {
            String candidate = FoodItem.cleanText(snapshot.rankedPackagingCandidates.get(index).text);
            if (candidate.length() == 0 || output.indexOf(candidate) >= 0) {
                continue;
            }
            if (output.length() > 0) {
                output.append(" / ");
            }
            output.append(candidate);
        }
        return output.toString();
    }

    private boolean showEvidenceForField(String field) {
        SessionKeyframe best = null;
        double bestConfidence = -1d;
        for (SessionKeyframe keyframe : keyframeSnapshot()) {
            for (RecognitionEvidence.Region region : keyframe.evidence.regions) {
                if (field.equals(region.field) && region.confidence > bestConfidence) {
                    best = keyframe;
                    bestConfidence = region.confidence;
                }
            }
        }
        if (best == null) {
            return false;
        }
        showEvidenceFrame(best);
        return true;
    }

    private void updateKnownProductBanner(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (knownProductBanner == null) {
            return;
        }
        String barcode = snapshot == null || !snapshot.hasStableBarcode()
                ? ""
                : FoodItem.cleanText(snapshot.stableBarcode);
        if (barcode.length() == 0) {
            knownProductBanner.setVisibility(View.GONE);
            return;
        }
        if (!barcode.equals(cachedLocalMatchBarcode)) {
            cachedLocalMatchBarcode = barcode;
            cachedLocalMatchCount = new FoodStore(this)
                    .loadProductProfileIndex()
                    .findByBarcode(barcode)
                    .size();
        }
        if (cachedLocalMatchCount <= 0) {
            knownProductBanner.setVisibility(View.GONE);
            return;
        }
        knownProductBanner.setText(cachedLocalMatchCount == 1
                ? "已找到 1 个本地商品档案 · 下一步可作为新批次补货"
                : "已找到 " + cachedLocalMatchCount + " 个本地商品档案 · 下一步选择补货对象");
        knownProductBanner.setVisibility(View.VISIBLE);
    }
    private void requestExitRecognition() {
        if (!currentUiPresentation().hasReviewableResult) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("退出识别？")
                .setMessage("当前已识别字段还没有填入录入表单。")
                .setNegativeButton("继续识别", null)
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                })
                .show();
    }
    private void showRecognitionSourceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("继续识别")
                .setItems(
                        new String[] {
                                "继续相机识别（保留当前结果）",
                                "清空当前结果后重来"
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    ensureCameraPermission(true);
                                } else {
                                    confirmRestartRecognition();
                                }
                            }
                        }
                )
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmRestartRecognition() {
        new AlertDialog.Builder(this)
                .setTitle("清空识别结果？")
                .setMessage("商品名、日期、条码和识别依据都会清空。")
                .setNegativeButton("保留结果", null)
                .setPositiveButton("清空重来", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ensureCameraPermission(false);
                    }
                })
                .show();
    }

    private void setRecognitionComplete(boolean complete) {
        updatePresentationUi(latestSnapshot);
        if (complete && !barcodeLookupOnly && currentUiPresentation().hasReviewableResult) {
            showConfirmationScreen();
        }
    }
    private void showInputChoiceState() {
        stopCamera();
        stopVideoReplay();
        resetRecognitionState();
        confirmationVisible = false;
        if (confirmationScreen != null) {
            confirmationScreen.setVisibility(View.GONE);
        }
        if (scannerScreen != null) {
            scannerScreen.setVisibility(View.VISIBLE);
        }
        setLightSystemBars(false);
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
                ? "让日期小字占满识别框；若画面发糊，请稍后退并轻点日期。"
                : "正在准备相机，请让包装文字和喷码尽量占满识别框。");
        updateResultUi(stabilizer.snapshot());
    }

    private void ensureCameraPermission(boolean preserveRecognition) {
        if (preserveRecognition
                && cameraBound
                && previewView != null
                && previewView.getVisibility() == View.VISIBLE) {
            statusBadge.setText("相机");
            statusText.setText("相机正在继续识别，已有商品名、日期和条码不会被清空。");
            if (cameraFrameSource != null) {
                cameraFrameSource.focusAtCenter(false, false);
            }
            return;
        }
        if (preserveRecognition) {
            prepareCameraSurfacePreservingRecognition();
        } else {
            stopVideoReplay();
            resetRecognitionState();
            showCameraSurface();
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            return;
        }
        requestPermissions(new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA_PERMISSION);
    }

    private void showCameraSurface() {
        if (torchButton != null) {
            torchButton.setVisibility(View.GONE);
        }
        if (previewView != null) {
            previewView.setVisibility(View.VISIBLE);
        }
        if (replayFrameView != null) {
            replayFrameView.setVisibility(View.GONE);
            replayFrameView.setImageBitmap(null);
        }
        clearLastReplayFrame();
        if (recognitionOverlay != null) {
            recognitionOverlay.showEvidence(null, 0, 0);
            recognitionOverlay.showLiveRegions(null, 0, 0);
        }
        renderCameraHud();
    }

    private void prepareCameraSurfacePreservingRecognition() {
        stopVideoReplay();
        recognitionGeneration = recognitionWorkSession
                .restartPreservingEvidence()
                .generation;
        resetLatestCameraFrames();
        showingEvidenceFrame = false;
        analyzedFrameSequence = 0;
        recognitionFirstFrameAtMs = 0L;
        firstProductFieldElapsedMs = -1L;
        firstBarcodeFieldElapsedMs = -1L;
        firstDateFieldElapsedMs = -1L;
        paddleCameraBandFramesUsed = 0;
        paddleDetectionCameraFramesUsed = 0;
        paddleDetectedLineCameraFramesUsed = 0;
        lowContrastCameraPassesUsed = 0;
        liveCameraResultFrozen = false;
        lastAnalyzeAt = 0L;
        cameraFramesCaptured = 0;
        cameraLastStatusAtMs = 0L;
        cameraFocusState = "自动对焦准备中";
        cameraFrameState = "已保留原结果，等待新画面";
        resetCameraFrameGate();
        focusRecoveryPolicy.reset();
        showCameraSurface();
        setRecognitionComplete(false);
        statusBadge.setText("继续识别");
        statusText.setText("已有候选和识别依据已保留；继续对准缺失或不清晰的包装信息。");
        updateResultUi(latestSnapshot);
    }

    private void resetCameraFrameGate() {
        bestCameraVisualScore = -1d;
        bestCameraAcceptedScore = -1d;
        lastCameraProbeAtMs = 0L;
        lastCameraAcceptedAtMs = 0L;
        lastCameraAcceptedSignature = 0L;
        lastValuableFramePromptAtMs = 0L;
        cameraProbeCount = 0;
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
        if (torchButton != null) {
            torchButton.setVisibility(View.GONE);
        }
        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
        if (cameraHud != null) {
            cameraHud.setVisibility(View.GONE);
        }
        if (replayFrameView != null) {
            replayFrameView.setVisibility(View.VISIBLE);
        }
        diagnostics.setSource(
                SystemClock.elapsedRealtime(),
                "相机模拟".equals(badge) ? "qa-video" : "video"
        );
        diagnosticEvent("source.ready", "badge=" + badge);
        statusBadge.setText(badge);
        statusText.setText("正在分析中英文包装，条码、商品名和日期会自动合并。");
    }

    private void replayVideoFrames(Uri uri, String path) {
        int analyzedFrames = 0;
        int scannedFrames = 0;
        Bitmap selectedFrame = null;
        long selectedFrameUs = 0L;
        double selectedFrameRatio = 0d;
        double selectedFrameScore = -1d;
        boolean selectedDetailFrame = false;
        final int replayGeneration = recognitionGeneration;
        try (VideoReplayFrameSource frameSource = VideoReplayFrameSource.open(
                this,
                uri,
                path,
                VIDEO_MAX_FRAME_SIDE
        )) {
            long durationUs = frameSource.durationUs();
            if (durationUs <= 0) {
                durationUs = 30000000L;
            }

            longVideoProfile = durationUs > 20000000L;
            Bitmap recordingProbe = frameSource.frameAt(0L);
            if (recordingProbe != null && isLikelyPhoneScreenRecording(recordingProbe, durationUs)) {
                cameraSimulationScreenRecording = cameraSimulationActive;
                appScreenRecordingDetected = !cameraSimulationActive;
                Log.i("ShiqiRecognition", "Phone screen recording detected from video geometry");
            }
            recycleBitmap(recordingProbe);
            List<Long> frameTimesUs = cameraSimulationScreenRecording
                    ? RecognitionFrameSelector.cameraScreenRecordingFrameTimes(durationUs)
                    : RecognitionFrameSelector.highRateVideoFrameTimes(
                            durationUs,
                            longVideoProfile
                    );
            int selectionWindow = cameraSimulationScreenRecording
                    ? RecognitionFrameSelector.cameraScreenRecordingSelectionWindow(frameTimesUs.size())
                    : RecognitionFrameSelector.highRateVideoSelectionWindow(
                    frameTimesUs.size(),
                    longVideoProfile
            );
            int expectedFrames = Math.max(
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
                Bitmap frame = frameSource.frameAt(frameUs);
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
                if (frameIndex == 0
                        && !cameraSimulationScreenRecording
                        && !appScreenRecordingDetected
                        && isLikelyPhoneScreenRecording(frame, durationUs)) {
                    cameraSimulationScreenRecording = cameraSimulationActive;
                    appScreenRecordingDetected = !cameraSimulationActive;
                    if (cameraSimulationScreenRecording) {
                        selectionWindow = RecognitionFrameSelector.cameraScreenRecordingSelectionWindow(
                                frameTimesUs.size()
                        );
                        expectedFrames = Math.max(
                                1,
                                (frameTimesUs.size() + selectionWindow - 1) / selectionWindow
                        );
                    }
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

                diagnostics.increment("frames_captured");
                diagnostics.increment("frames_probed");
                LowContrastTextPreprocessor.FrameMetrics metrics =
                        LowContrastTextPreprocessor.measureFrame(scaled);
                double candidateScore = RecognitionFrameSelector.highRateVideoFrameScore(
                        metrics.visualScore,
                        metrics.sharpness,
                        metrics.glareRatio
                );
                if (selectedFrame == null || candidateScore > selectedFrameScore) {
                    if (selectedFrame != null) {
                        diagnostics.increment("pending_replaced");
                    }
                    recycleBitmap(selectedFrame);
                    selectedFrame = scaled;
                    selectedFrameUs = frameUs;
                    selectedFrameRatio = frameRatio;
                    selectedFrameScore = candidateScore;
                    selectedDetailFrame = videoDetailFrame;
                } else {
                    diagnostics.increment("skip_not_better");
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
                diagnostics.increment("frames_accepted");
                diagnosticEvent("frame.accept", "source=video timestampUs=" + selectedFrameUs);
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
                boolean replayHasStrongTextIdentity = hasReplayStrongTextIdentity();
                boolean replayHasIdentity = hasReplayProductIdentity(
                        replayHasStrongTextIdentity
                );
                boolean replayHasFinishableIdentity = hasReplayFinishableIdentity(
                        replayHasStrongTextIdentity
                );
                boolean replayTranscriptComplete = shouldFinishReplayTranscript(
                        analyzedFrames,
                        replayHasIdentity
                );
                boolean replayIdentityOnlyComplete =
                        RecognitionReplayCompletion.shouldFinishIdentityOnly(
                                analyzedFrames,
                                frameRatio,
                                cameraSimulationScreenRecording,
                                paddleDetectionCameraFramesUsed,
                                replayHasFinishableIdentity,
                                replayTranscriptComplete,
                                cameraSimulationActive
                        );
                if (replayTranscriptComplete || replayIdentityOnlyComplete) {
                    diagnosticEvent(
                            "replay.early-stop",
                            "frames=" + analyzedFrames + " reason="
                                    + (replayTranscriptComplete
                                    ? "complete-transcript"
                                    : "identity-saturated")
                    );
                }
                boolean replayComplete = hasCompleteVideoLabelCandidate()
                        || replayTranscriptComplete
                        || replayIdentityOnlyComplete;
                if (cameraSimulationActive
                        && RecognitionFrameSelector.shouldFinishCameraSimulation(
                        analyzedFrames,
                        replayComplete
                )) {
                    break;
                }
                if (selectedFrameRatio >= 0.90d && replayComplete) {
                    break;
                }
            }

            refineIncompleteVideoDateFromKeyframe(frameSource, durationUs);
            refineIncompleteVideoProductNameFromEarlyFrames(frameSource, durationUs);
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
                boolean hasAnalyzedFrame = analyzedFrames > 0;
                statusBadge.setText(ScanUiPresentation.replayHeadline(hasAnalyzedFrame));
                statusText.setText(ScanUiPresentation.replayMessage(hasAnalyzedFrame));
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
        if (cameraHud != null) {
            cameraHud.setVisibility(View.GONE);
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
        if (torchButton != null) {
            torchButton.setVisibility(View.GONE);
        }
        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
        if (cameraHud != null) {
            cameraHud.setVisibility(View.GONE);
        }
        if (replayFrameView != null) {
            replayFrameView.setVisibility(View.VISIBLE);
        }
        diagnostics.setSource(SystemClock.elapsedRealtime(), "image");
        diagnosticEvent("source.ready", "image");
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
                    logImageRecognitionCompletion(snapshot);
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

    private void logImageRecognitionCompletion(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
        BarcodeProductInfo info = latestProductBarcode.equals(snapshot.stableBarcode)
                ? latestProductInfo
                : null;
        String packagingName = selectedPackagingName.length() > 0
                ? selectedPackagingName
                : snapshot.bestPackagingNameForConfirmation();
        String productName = productNameForResult(packagingName, info);
        long elapsedMs = recognitionElapsedMs();
        String shelfLifeValue = dateDraft.shelfLifeValue == null
                ? ""
                : Integer.toString(dateDraft.shelfLifeValue.intValue());

        diagnostics.value("completion_source", "image");
        diagnostics.value("completion_fillable", Boolean.toString(snapshot.hasFillableCandidate()));
        diagnostics.value("completion_barcode", snapshot.stableBarcode);
        diagnostics.value("completion_product_name", productName);
        diagnostics.value("completion_production", dateDraft.productionDate);
        diagnostics.value("completion_expiry", dateDraft.expiryDate);
        diagnostics.value("completion_shelf_life_value", shelfLifeValue);
        diagnostics.value("completion_shelf_life_unit", dateDraft.shelfLifeUnit);
        diagnosticEvent(
                "image.complete",
                "fillable=" + snapshot.hasFillableCandidate()
                        + " barcode=" + snapshot.stableBarcode
                        + " production=" + dateDraft.productionDate
                        + " expiry=" + dateDraft.expiryDate
                        + " elapsedMs=" + elapsedMs
        );
        Log.i(
                "ShiqiRecognition",
                "Image complete fillable=" + snapshot.hasFillableCandidate()
                        + " barcodeValue=" + snapshot.stableBarcode
                        + " productName=" + productName
                        + " production=" + dateDraft.productionDate
                        + " expiry=" + dateDraft.expiryDate
                        + " shelfLifeValue=" + shelfLifeValue
                        + " shelfLifeUnit=" + dateDraft.shelfLifeUnit
                        + " elapsedMs=" + elapsedMs
        );
    }
    private void analyzeBitmapFrame(Bitmap bitmap, boolean singleFrameConfirmation) throws Exception {
        analyzeBitmapFrame(bitmap, singleFrameConfirmation, false, recognitionGeneration, null);
    }

    private void analyzeBitmapFrame(
            Bitmap bitmap,
            boolean singleFrameConfirmation,
            boolean cameraLive,
            int expectedGeneration
    ) throws Exception {
        analyzeBitmapFrame(
                bitmap, singleFrameConfirmation, cameraLive, expectedGeneration, null
        );
    }

    private void analyzeBitmapFrame(
            Bitmap bitmap,
            boolean singleFrameConfirmation,
            boolean cameraLive,
            int expectedGeneration,
            CameraFrameProbe cameraProbe
    ) throws Exception {
        if (bitmap == null || textRecognizer == null || barcodeScanner == null) {
            return;
        }
        final long fullOcrStartedAt = SystemClock.elapsedRealtime();
        diagnosticEvent("ocr.start", "cameraLive=" + cameraLive + " frame=" + (analyzedFrameSequence + 1));
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
                cameraProbe == null
                        ? LowContrastTextPreprocessor.measureFrame(bitmap)
                        : cameraProbe.metrics;
        analyzedFrameSequence++;
        if (cameraLive) {
            String quality = CameraOcrFeedback.qualityLabel(
                    frameMetrics.sharpness,
                    frameMetrics.brightness,
                    frameMetrics.contrast,
                    frameMetrics.glareRatio
            );
            setCameraFrameState(
                    quality + " · 中文识别 " + analyzedFrameSequence + " 帧",
                    false
            );
        }
        RecognitionFieldGapPlan gapPlan = currentFieldGapPlan();
        diagnostics.value("field_target", gapPlan.label);
        diagnostics.value("field_missing_mask", Integer.toString(gapPlan.missingFields));
        boolean needsBarcode = barcodeLookupOnly || gapPlan.scanBarcode;
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
        boolean runLowContrastRecovery = cameraLive
                && gapPlan.scanDates
                && CameraOcrFeedback.shouldRunLowContrastRecovery(
                analyzedFrameSequence,
                lowContrastCameraPassesUsed,
                frameMetrics.sharpness,
                frameMetrics.brightness,
                frameMetrics.contrast,
                frameMetrics.glareRatio,
                hasCompleteCameraDateCandidate()
        );
        if (runLowContrastRecovery) {
            lowContrastCameraPassesUsed++;
        }
        List<PackagingTextAnalyzer.Observation> textObservations =
                new ArrayList<PackagingTextAnalyzer.Observation>();
        boolean dedicatedStillDateStrip = singleFrameConfirmation && isLikelyDateStrip(bitmap);
        boolean paddlePrimaryPass = runFullPaddleTextDetection
                && (singleFrameConfirmation || cameraLive || videoReplayActive);
        List<RecognitionFrameVariant> variants = buildFrameVariants(
                bitmap,
                cameraLive,
                singleFrameConfirmation,
                runFullPaddleTextDetection,
                runLowContrastRecovery,
                dedicatedStillDateStrip
        );
        int processedVariants = 0;
        try {
            for (RecognitionFrameVariant variant : variants) {
                processedVariants++;
                diagnostics.increment("variants_processed");
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
                    if (cameraLive && FoodItem.cleanText(recognizedText.getText()).length() > 0) {
                        setCameraFrameState("已读到中文文字 · 正在确认日期", false);
                    }
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
                if (dedicatedStillDateStrip) {
                    DateOcrParser.Result visibleVariantDates = DateOcrParser.parse(
                            rawText.toString()
                    );
                    if (RecognitionRoiBudget.shouldStopStillDateVariants(
                            true,
                            !visibleVariantDates.productionDates.isEmpty(),
                            !visibleVariantDates.expiryDates.isEmpty(),
                            !visibleVariantDates.calculatedExpiryDates.isEmpty()
                    )) {
                        int skippedVariants = Math.max(0, variants.size() - processedVariants);
                        diagnostics.add("variants_skipped_after_complete", skippedVariants);
                        diagnosticEvent(
                                "variant.stop",
                                "processed=" + processedVariants + " skipped=" + skippedVariants
                        );
                        break;
                    }
                }
            }
            boolean targetedCameraDatePass = cameraLive
                    && gapPlan.allowsDateBandPass(paddleDetectedLineCameraFramesUsed)
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
            if (singleFrameConfirmation || targetedCameraDatePass || (gapPlan.scanDates && videoReplayActive
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
            PaddleDateEvidence centeredLowContrastEvidence = PaddleDateEvidence.empty();
            if (singleFrameConfirmation || (cameraLive && runLowContrastRecovery)) {
                centeredLowContrastEvidence = recognizePaddleCenteredDateLines(
                        bitmap,
                        singleFrameConfirmation
                );
                if (centeredLowContrastEvidence.text.length() > 0) {
                    appendRecognizedText(rawText, centeredLowContrastEvidence.text);
                    appendRecognizedText(focusedDateText, centeredLowContrastEvidence.text);
                    appendRecognizedText(supplementaryDateText, centeredLowContrastEvidence.text);
                    preferredDateText = centeredLowContrastEvidence.originalText;
                    if (centeredLowContrastEvidence.confidence > independentPaddleDateConfidence) {
                        independentPaddleDateText = centeredLowContrastEvidence.originalText;
                        independentPaddleDateConfidence = centeredLowContrastEvidence.confidence;
                    }
                    textObservations.add(
                            centeredLowContrastEvidence.toObservation(frameMetrics.visualScore)
                    );
                }
            }
            DateOcrParser.Result centeredDates = DateOcrParser.parse(
                    centeredLowContrastEvidence.text
            );
            boolean centeredCompletedDate = singleFrameConfirmation
                    && hasCompleteDateEvidence(centeredDates);
            List<PaddleTextDetectionEngine.TextRegion> centeredCompletionProbeRegions = null;
            boolean skipFullPaddleAfterCenteredDate = false;
            if (centeredCompletedDate
                    && runFullPaddleTextDetection
                    && paddleTextDetectionEngine != null) {
                long completionProbeStartedAt = SystemClock.elapsedRealtime();
                centeredCompletionProbeRegions = paddleTextDetectionEngine.detect(bitmap, 12, 640);
                long completionProbeElapsedMs =
                        SystemClock.elapsedRealtime() - completionProbeStartedAt;
                diagnostics.timing("completion_probe_detector", completionProbeElapsedMs);
                skipFullPaddleAfterCenteredDate = centeredCompletionProbeRegions.isEmpty();
                diagnosticEvent(
                        "date.fast.probe",
                        "regions=" + centeredCompletionProbeRegions.size()
                                + " elapsedMs=" + completionProbeElapsedMs
                );
            }
            if (centeredCompletedDate) {
                diagnosticEvent(
                        skipFullPaddleAfterCenteredDate
                                ? "date.fast.stop"
                                : "date.fast.complete",
                        "source=centered-low-contrast"
                );
            }
            PaddleDateEvidence detectedRegionEvidence = PaddleDateEvidence.empty();
            if (runFullPaddleTextDetection && !skipFullPaddleAfterCenteredDate) {
                if (videoReplayActive) {
                    paddleDetectionVideoFramesUsed++;
                }
                detectedRegionEvidence = centeredCompletionProbeRegions == null
                        ? recognizePaddleTextRegionsForFrame(
                                bitmap,
                                cameraLive,
                                cameraProbe
                        )
                        : recognizePaddleTextRegions(
                                bitmap,
                                false,
                                0,
                                centeredCompletionProbeRegions
                        );
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
            if (!detectorFoundCompleteDate
                    && !centeredCompletedDate
                    && shouldRunPaddleFallback(
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
        barcode = RecognitionFieldPromoter.acceptedBarcodeEvidence(
                barcode,
                rawText.toString()
        );
        trustedBarcodeEvidence = trustedBarcodeEvidence
                && BarcodeUtils.isSupportedProductCode(barcode);
        if (trustedBarcodeEvidence
                && (barcodeLookupOnly
                || singleFrameConfirmation
                || barcode.length() == 13)
                && expectedGeneration == recognitionGeneration) {
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
        if (cameraLive) {
            UnifiedRecognitionStabilizer.Snapshot cameraSnapshot = latestSnapshot;
            String progress = cameraSnapshot != null && cameraSnapshot.hasStableDateCandidate()
                    ? "日期候选已稳定 · 请核对结果"
                    : rawText.length() > 0
                    ? "已读到包装文字 · 继续确认日期"
                    : CameraOcrFeedback.qualityLabel(
                    frameMetrics.sharpness,
                    frameMetrics.brightness,
                    frameMetrics.contrast,
                    frameMetrics.glareRatio
            );
            setCameraFrameState(progress + " · 已分析 " + analyzedFrameSequence + " 帧", false);
        }
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
        long fullOcrElapsedMs = SystemClock.elapsedRealtime() - fullOcrStartedAt;
        diagnostics.increment("ocr_completed");
        diagnostics.timing("full_ocr", fullOcrElapsedMs);
        diagnostics.value("latest_raw_chars", Integer.toString(rawText.length()));
        diagnostics.value("latest_sharpness", String.format(Locale.US, "%.1f", frameMetrics.sharpness));
        diagnosticEvent("ocr.done", "frame=" + analyzedFrameSequence
                + " elapsedMs=" + fullOcrElapsedMs
                + " rawChars=" + rawText.length()
                + " date=" + parsedFrame.hasAnyCandidate());
        maybeCompleteLiveCamera(cameraLive, expectedGeneration);
    }

    private boolean awaitRecognitionTask(Task<?> task, String label, long timeoutSeconds) {
        long startedAt = SystemClock.elapsedRealtime();
        try {
            Tasks.await(task, timeoutSeconds, TimeUnit.SECONDS);
            return task.isSuccessful();
        } catch (Exception error) {
            Log.w("ShiqiRecognition", label + " failed for one variant", error);
            return false;
        } finally {
            diagnostics.timing("mlkit_wait", SystemClock.elapsedRealtime() - startedAt);
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

    private List<RecognitionFrameVariant> buildFrameVariants(
            Bitmap bitmap,
            boolean cameraLive,
            boolean singleFrameConfirmation,
            boolean runningHeavyDatePass,
            boolean runningLowContrastRecovery,
            boolean dedicatedStillDateStrip
    ) {
        List<RecognitionFrameVariant> variants = new ArrayList<RecognitionFrameVariant>();
        if (singleFrameConfirmation) {
            variants.add(new RecognitionFrameVariant(bitmap, 0, true, true, true, false, 0.90d));
            variants.add(new RecognitionFrameVariant(bitmap, 90, false, true, true, false, 0.80d, true, true));
            variants.add(new RecognitionFrameVariant(bitmap, 270, false, true, true, false, 0.80d, true, true));
            if (dedicatedStillDateStrip) {
                Bitmap laser = LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap);
                if (laser != null) {
                    variants.add(new RecognitionFrameVariant(laser, 0, false, true, true, true, 0.96d, true, true));
                    variants.add(new RecognitionFrameVariant(laser, 90, false, true, true, false, 0.88d, true, true));
                    variants.add(new RecognitionFrameVariant(laser, 270, false, true, true, false, 0.88d, true, true));
                }
                Bitmap embossed = LowContrastTextPreprocessor.enhanceEmbossedText(bitmap);
                if (embossed != null) {
                    variants.add(new RecognitionFrameVariant(embossed, 0, false, true, true, true, 0.96d, true, true));
                }
                Bitmap binary = LowContrastTextPreprocessor.binarizeText(bitmap, false);
                if (binary != null) {
                    variants.add(new RecognitionFrameVariant(binary, 0, false, true, true, true, 0.94d, true, true));
                }
                Bitmap inverted = LowContrastTextPreprocessor.binarizeText(bitmap, true);
                if (inverted != null) {
                    variants.add(new RecognitionFrameVariant(inverted, 0, false, true, true, true, 0.94d, true, true));
                }
                Bitmap brightText = LowContrastTextPreprocessor.isolateBrightText(bitmap);
                if (brightText != null) {
                    variants.add(new RecognitionFrameVariant(brightText, 0, false, true, true, true, 0.97d, true, true));
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
                variants.add(new RecognitionFrameVariant(enhanced, 0, false, true, true, true, 0.92d, true, false));
                variants.add(new RecognitionFrameVariant(enhanced, 90, false, true, true, false, 0.86d, true, true));
                variants.add(new RecognitionFrameVariant(enhanced, 270, false, true, true, false, 0.86d, true, true));
            }
            return variants;
        }
        if (videoReplayActive && appScreenRecordingDetected) {
            variants.add(new RecognitionFrameVariant(bitmap, 0, true, false, false, false, 0.74d));
            addFrameCrop(
                    variants,
                    bitmap,
                    0.04f,
                    0.075f,
                    0.92f,
                    0.565f,
                    900,
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
            RecognitionFieldGapPlan gapPlan = currentFieldGapPlan();
            boolean scanPackagingText = !barcodeLookupOnly && gapPlan.scanProductName;
            boolean scanBarcode = barcodeLookupOnly || gapPlan.scanBarcode;
            boolean scanAnyText = !barcodeLookupOnly
                    && (gapPlan.scanProductName || gapPlan.scanDates);
            variants.add(new RecognitionFrameVariant(
                    bitmap,
                    0,
                    scanBarcode,
                    scanAnyText,
                    scanAnyText && analyzedFrameSequence == 1,
                    false,
                    0.84d
            ));
            boolean stillNeedsIdentity = gapPlan.scanProductName || gapPlan.scanBarcode;
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
                        gapPlan.scanBarcode,
                        scanPackagingText,
                        false,
                        false
                );
            }
            if (!barcodeLookupOnly
                    && gapPlan.scanDates
                    && (runningHeavyDatePass || runningLowContrastRecovery)) {
                Bitmap enhanced = runningLowContrastRecovery
                        ? LowContrastTextPreprocessor.enhanceBidirectionalDateStrokes(bitmap)
                        : paddleDetectionCameraFramesUsed % 2 == 0
                        ? LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap)
                        : LowContrastTextPreprocessor.enhanceEmbossedText(bitmap);
                if (enhanced != null) {
                    variants.add(new RecognitionFrameVariant(
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
            variants.add(new RecognitionFrameVariant(
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
            variants.add(new RecognitionFrameVariant(
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
        variants.add(new RecognitionFrameVariant(bitmap, 0, true, true, false, false, 0.68d));
        addFrameCrop(variants, bitmap, 0.04f, 0.13f, 0.92f, 0.50f, 1100, 0, false, true, false);
        addFrameCrop(variants, bitmap, 0.14f, 0.20f, 0.72f, 0.40f, 1800, 0, false, true, true, true);
        addFrameCrop(variants, bitmap, 0.38f, 0.48f, 0.60f, 0.44f, 1400, 0, false, true, false, true);
        addFrameCrop(variants, bitmap, 0.54f, 0.60f, 0.44f, 0.22f, 1500, 0, false, true, true, true);
        if (analyzedFrameSequence % 2 == 1) {
            Bitmap enhanced = LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap);
            if (enhanced != null) {
                variants.add(new RecognitionFrameVariant(enhanced, 0, false, true, false, true, 0.88d, false, true));
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

    private void recordFirstFieldMetrics(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        long elapsedMs = recognitionElapsedMs();
        String productName = snapshot.bestPackagingNameForConfirmation();
        if (firstProductFieldElapsedMs < 0L
                && PackagingNamePolicy.isStrong(PackagingNamePolicy.normalize(productName))) {
            firstProductFieldElapsedMs = elapsedMs;
            diagnostics.value("first_product_elapsed_ms", Long.toString(elapsedMs));
            Log.i(
                    "ShiqiMetric",
                    "first.field type=productName elapsedMs=" + elapsedMs
                            + " frame=" + analyzedFrameSequence
                            + " value=" + productName
            );
        }
        if (firstBarcodeFieldElapsedMs < 0L && snapshot.hasStableBarcode()) {
            firstBarcodeFieldElapsedMs = elapsedMs;
            diagnostics.value("first_barcode_elapsed_ms", Long.toString(elapsedMs));
            Log.i(
                    "ShiqiMetric",
                    "first.field type=barcode elapsedMs=" + elapsedMs
                            + " frame=" + analyzedFrameSequence
                            + " value=" + snapshot.stableBarcode
            );
        }
        if (firstDateFieldElapsedMs < 0L && snapshot.hasStableDateCandidate()) {
            firstDateFieldElapsedMs = elapsedMs;
            diagnostics.value("first_date_elapsed_ms", Long.toString(elapsedMs));
            FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
            Log.i(
                    "ShiqiMetric",
                    "first.field type=date elapsedMs=" + elapsedMs
                            + " frame=" + analyzedFrameSequence
                            + " production=" + dateDraft.productionDate
                            + " expiry=" + dateDraft.expiryDate
            );
        }
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

    private boolean hasReplayProductIdentity(boolean hasStrongTextIdentity) {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
        return hasStrongTextIdentity
                || (snapshot != null
                && (snapshot.hasStableBarcode() || snapshot.hasStablePackagingName()));
    }

    private boolean hasReplayFinishableIdentity(boolean hasStrongTextIdentity) {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
        return hasStrongTextIdentity
                || (snapshot != null && snapshot.hasStableBarcode());
    }
    private boolean hasReplayStrongTextIdentity() {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
        if (snapshot == null || !snapshot.hasStablePackagingName()) {
            return false;
        }
        String stableName = RecognitionTextCleaner.intelligentProductNameCandidate(
                snapshot.stablePackagingName
        );
        return RecognitionReplayCompletion.isRichTextIdentity(stableName)
                && PackagingNamePolicy.isStrong(PackagingNamePolicy.normalize(stableName));
    }
    private boolean shouldFinishReplayTranscript(
            int analyzedFrames,
            boolean hasIdentity
    ) {
        return RecognitionReplayCompletion.shouldFinish(
                analyzedFrames,
                dateOnlyMode,
                hasIdentity,
                videoTranscriptText.toString(),
                bestVideoDatePair
        );
    }
    private void addEnhancedFrameCrop(
            List<RecognitionFrameVariant> variants,
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
            variants.add(new RecognitionFrameVariant(
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
            List<RecognitionFrameVariant> variants,
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
                if (!videoReplayActive && lowContrastCameraPassesUsed > 0) {
                    Bitmap bidirectional =
                            LowContrastTextPreprocessor.enhanceBidirectionalDateStrokes(prepared);
                    if (bidirectional != null) {
                        try {
                            lineResults.add(engine.recognizeResult(bidirectional, 0.25f));
                        } finally {
                            bidirectional.recycle();
                        }
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

    private PaddleDateEvidence recognizePaddleCenteredDateLines(
            Bitmap bitmap,
            boolean singleFrameConfirmation
    ) {
        PaddleLineOcrEngine engine = paddleLineOcrEngine;
        if (engine == null || bitmap == null || bitmap.isRecycled()) {
            return PaddleDateEvidence.empty();
        }
        float[] rowStarts = new float[] {
                0.295f, 0.325f, 0.285f, 0.335f,
                0.305f, 0.315f, 0.345f
        };
        int firstRow = singleFrameConfirmation
                ? 0
                : Math.max(0, (lowContrastCameraPassesUsed - 1) % rowStarts.length);
        int rowCount = singleFrameConfirmation ? rowStarts.length : 1;
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int left = Math.round(imageWidth * 0.18f);
        int width = Math.min(imageWidth - left, Math.round(imageWidth * 0.54f));
        int height = Math.max(1, Math.round(imageHeight * 0.040f));
        StringBuilder recognized = new StringBuilder();
        StringBuilder original = new StringBuilder();
        RecognitionEvidence.NormalizedRect evidenceRect = null;
        double bestConfidence = 0d;
        List<String> candidateTexts = new ArrayList<String>();
        List<String> candidateSignatures = new ArrayList<String>();
        List<Double> candidateConfidences = new ArrayList<Double>();
        List<RecognitionEvidence.NormalizedRect> candidateRects =
                new ArrayList<RecognitionEvidence.NormalizedRect>();

        for (int rowOffset = 0; rowOffset < rowCount; rowOffset++) {
            int rowIndex = (firstRow + rowOffset) % rowStarts.length;
            int top = clamp(
                    Math.round(imageHeight * rowStarts[rowIndex]),
                    0,
                    imageHeight - height
            );
            Bitmap crop = Bitmap.createBitmap(bitmap, left, top, width, height);
            List<PaddleLineOcrEngine.LineResult> results =
                    new ArrayList<PaddleLineOcrEngine.LineResult>();
            try {
                results.add(engine.recognizeResult(crop, 0.20f));
                Bitmap laser = LowContrastTextPreprocessor.enhanceLaserPrintedText(crop);
                if (laser != null) {
                    try {
                        results.add(engine.recognizeResult(laser, 0.20f));
                    } finally {
                        laser.recycle();
                    }
                }
                Bitmap embossed = LowContrastTextPreprocessor.enhanceEmbossedText(crop);
                if (embossed != null) {
                    try {
                        results.add(engine.recognizeResult(embossed, 0.20f));
                    } finally {
                        embossed.recycle();
                    }
                }
                Bitmap bidirectional =
                        LowContrastTextPreprocessor.enhanceBidirectionalDateStrokes(crop);
                if (bidirectional != null) {
                    try {
                        results.add(engine.recognizeResult(bidirectional, 0.20f));
                    } finally {
                        bidirectional.recycle();
                    }
                }
                for (boolean invert : new boolean[] {false, true}) {
                    Bitmap faint = LowContrastTextPreprocessor.enhanceFaintDateText(crop, invert);
                    if (faint == null) {
                        continue;
                    }
                    try {
                        results.add(engine.recognizeResult(faint, 0.20f));
                    } finally {
                        faint.recycle();
                    }
                }
            } finally {
                crop.recycle();
            }

            for (PaddleLineOcrEngine.LineResult result : results) {
                if (isDebuggable() && result != null && result.text.length() > 0) {
                    Log.d(
                            "ShiqiRecognition",
                            "Paddle centered row="
                                    + String.format(Locale.US, "%.3f", rowStarts[rowIndex])
                                    + " text=" + result.text
                                    + " confidence="
                                    + String.format(Locale.US, "%.2f", result.confidence)
                    );
                }
                if (result == null || !CameraOcrFeedback.isStrongCenteredDateRead(result.text)) {
                    continue;
                }
                RecognitionEvidence.NormalizedRect rowRect =
                        new RecognitionEvidence.NormalizedRect(
                                left / (double) imageWidth,
                                top / (double) imageHeight,
                                (left + width) / (double) imageWidth,
                                (top + height) / (double) imageHeight
                        );
                candidateTexts.add(result.text);
                candidateSignatures.add(CameraOcrFeedback.dateSignature(result.text));
                candidateConfidences.add(Double.valueOf(result.confidence));
                candidateRects.add(rowRect);
            }
            if (hasCompleteDateEvidence(DateOcrParser.parse(stableCenteredDateText(
                    candidateTexts,
                    candidateSignatures,
                    candidateConfidences
            )))) {
                diagnosticEvent(
                        "centered.stop",
                        "rows=" + (rowOffset + 1) + "/" + rowCount
                );
                break;
            }
        }
        for (int index = 0; index < candidateTexts.size(); index++) {
            String signature = candidateSignatures.get(index);
            int votes = 0;
            for (String other : candidateSignatures) {
                if (signature.equals(other)) {
                    votes++;
                }
            }
            double confidence = candidateConfidences.get(index).doubleValue();
            if (signature.length() == 0 || (votes < 2 && confidence < 0.86d)) {
                continue;
            }
            appendRecognizedText(recognized, candidateTexts.get(index));
            appendRecognizedText(original, candidateTexts.get(index));
            bestConfidence = Math.max(bestConfidence, confidence);
            RecognitionEvidence.NormalizedRect rowRect = candidateRects.get(index);
            evidenceRect = evidenceRect == null ? rowRect : evidenceRect.union(rowRect);
        }
        return new PaddleDateEvidence(
                recognized.toString(),
                original.toString(),
                bestConfidence,
                evidenceRect
        );
    }

    private String stableCenteredDateText(
            List<String> candidateTexts,
            List<String> candidateSignatures,
            List<Double> candidateConfidences
    ) {
        StringBuilder stable = new StringBuilder();
        for (int index = 0; index < candidateTexts.size(); index++) {
            String signature = candidateSignatures.get(index);
            int votes = 0;
            for (String other : candidateSignatures) {
                if (signature.equals(other)) {
                    votes++;
                }
            }
            double confidence = candidateConfidences.get(index).doubleValue();
            if (signature.length() > 0 && (votes >= 2 || confidence >= 0.86d)) {
                appendRecognizedText(stable, candidateTexts.get(index));
            }
        }
        return stable.toString();
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
        RecognitionFieldGapPlan gapPlan = currentFieldGapPlan();
        if (cameraLive && (barcodeLookupOnly || !gapPlan.runHeavyDetector)) {
            return false;
        }
        if (cameraLive && !paddleModelsReady) {
            return false;
        }
        if (cameraSimulationScreenRecording) {
            return gapPlan.allowsHeavyPass(paddleDetectionCameraFramesUsed)
                    && currentVideoFrameRatio >= 0.40d
                    && currentVideoFrameRatio <= 0.88d
                    && !hasCompleteCameraDateCandidate();
        }
        if (cameraLive) {
            return gapPlan.allowsHeavyPass(paddleDetectionCameraFramesUsed)
                    && RecognitionFrameSelector.shouldRunHeavyCameraOcr(
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
        return recognizePaddleTextRegions(bitmap, cameraLive, 0, null);
    }

    private PaddleDateEvidence recognizePaddleTextRegions(
            Bitmap bitmap,
            boolean cameraLive,
            int requestedMaxRegions
    ) {
        return recognizePaddleTextRegions(
                bitmap,
                cameraLive,
                requestedMaxRegions,
                null
        );
    }

    private PaddleDateEvidence recognizePaddleTextRegions(
            Bitmap bitmap,
            boolean cameraLive,
            int requestedMaxRegions,
            List<PaddleTextDetectionEngine.TextRegion> preDetectedRegions
    ) {
        PaddleTextDetectionEngine detector = paddleTextDetectionEngine;
        PaddleLineOcrEngine recognizer = paddleLineOcrEngine;
        if (detector == null || recognizer == null || bitmap == null || bitmap.isRecycled()) {
            return PaddleDateEvidence.empty();
        }

        int cameraPass = paddleDetectionCameraFramesUsed;
        int cameraInputSide = cameraSimulationScreenRecording
                ? 1280
                : cameraPass == 0 ? 960 : 1280;
        Bitmap detectorInput = bitmap;
        boolean stillPhotoPass = !cameraLive && !videoReplayActive;
        boolean hasReusableRegions = RecognitionRoiBudget.shouldReuseDetectorProbe(
                cameraLive,
                cameraPass,
                preDetectedRegions == null ? 0 : preDetectedRegions.size()
        );
        if (!hasReusableRegions
                && ((cameraLive && lowContrastCameraPassesUsed > 0 && cameraPass < 3)
                || stillPhotoPass
                || (cameraLive && cameraPass >= 2))) {
            Bitmap enhancedDetectorInput = (stillPhotoPass || lowContrastCameraPassesUsed > cameraPass)
                    ? LowContrastTextPreprocessor.enhanceBidirectionalDateStrokes(bitmap)
                    : cameraPass % 2 == 1
                    ? LowContrastTextPreprocessor.enhanceLaserPrintedText(bitmap)
                    : LowContrastTextPreprocessor.enhanceEmbossedText(bitmap);
            if (enhancedDetectorInput != null) {
                detectorInput = enhancedDetectorInput;
            }
        }
        long detectorStartedAt = SystemClock.elapsedRealtime();
        List<PaddleTextDetectionEngine.TextRegion> detectedRegions =
                new ArrayList<PaddleTextDetectionEngine.TextRegion>();
        boolean reuseProbe = detectorInput == bitmap && hasReusableRegions;
        try {
            if (reuseProbe) {
                detectedRegions.addAll(preDetectedRegions);
                cameraInputSide = 640;
            } else {
                int maxRegions = requestedMaxRegions > 0
                        ? requestedMaxRegions
                        : cameraLive ? (dateOnlyMode ? 28 : 22) : videoReplayActive ? 10 : 24;
                detectedRegions.addAll(detector.detect(
                        detectorInput,
                        maxRegions,
                        cameraLive ? cameraInputSide : 1280
                ));
            }
        } finally {
            if (detectorInput != bitmap && !detectorInput.isRecycled()) {
                detectorInput.recycle();
            }
        }
        if (cameraLive) {
            publishLiveTextRegions(detectedRegions, bitmap.getWidth(), bitmap.getHeight());
        }
        RecognitionRoiBudget.Plan roiPlan = cameraLive
                ? RecognitionRoiBudget.forCameraPass(
                cameraPass,
                dateOnlyMode,
                lowContrastCameraPassesUsed > cameraPass,
                detectedRegions.size()
        )
                : RecognitionRoiBudget.forStillPass(detectedRegions.size());
        List<PaddleTextDetectionEngine.TextRegion> regions =
                new ArrayList<PaddleTextDetectionEngine.TextRegion>();
        List<RecognitionEvidence.NormalizedRect> cropRects =
                new ArrayList<RecognitionEvidence.NormalizedRect>();
        int dateRegionCount = roiPlan.baseRegionLimit;
        for (int index = 0; index < dateRegionCount; index++) {
            PaddleTextDetectionEngine.TextRegion region = detectedRegions.get(index);
            regions.add(region);
            cropRects.add(region.contextRect());
        }
        int extraTightAdded = 0;
        if (cameraLive && cameraPass > 0 && roiPlan.extraTightRegionLimit > 0) {
            for (PaddleTextDetectionEngine.TextRegion region : detectedRegions) {
                if (region.aspectRatio() < 4d || region.widthRatio() < 0.06d) {
                    continue;
                }
                regions.add(region);
                cropRects.add(region.tightTextRect());
                extraTightAdded++;
                if (extraTightAdded >= roiPlan.extraTightRegionLimit) {
                    break;
                }
            }
        }
        long detectorElapsedMs = SystemClock.elapsedRealtime() - detectorStartedAt;
        diagnostics.timing("text_detector", detectorElapsedMs);
        diagnostics.value("last_regions", Integer.toString(regions.size()));
        diagnosticEvent("detector.done", "pass=" + (cameraPass + 1)
                + " regions=" + regions.size() + " elapsedMs=" + detectorElapsedMs);
        Log.i(
                "ShiqiRecognition",
                "Text detector pass=" + (cameraPass + 1)
                        + " input=" + cameraInputSide
                        + " regions=" + regions.size()
                        + " elapsedMs=" + detectorElapsedMs
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
            int augmentationLimit = roiPlan.enhancementLimit;
            for (int cropIndex = 0; cropIndex < crops.size(); cropIndex++) {
                if (cropIndex >= augmentationLimit
                        || recognitionCrops.size() >= roiPlan.maxRecognitionCrops) {
                    break;
                }
                Bitmap enhanced = enhanceRecognitionCrop(
                        crops.get(cropIndex),
                        roiPlan.enhancement
                );
                if (enhanced == null) {
                    continue;
                }
                recognitionCrops.add(enhanced);
                generatedRecognitionCrops.add(enhanced);
                recognitionRegionIndexes.add(Integer.valueOf(cropIndex));
            }
        }
        diagnostics.value("last_crop_count", Integer.toString(recognitionCrops.size()));
        diagnostics.value("last_roi_plan", roiPlan.enhancement.name().toLowerCase(Locale.US));
        diagnostics.value("last_batch_size", Integer.toString(roiPlan.batchSize));
        diagnosticEvent(
                "roi.plan",
                "pass=" + (cameraPass + 1)
                        + " detected=" + detectedRegions.size()
                        + " regions=" + regions.size()
                        + " enhancement=" + roiPlan.enhancement.name().toLowerCase(Locale.US)
                        + " crops=" + recognitionCrops.size()
                        + "/" + roiPlan.maxRecognitionCrops
                        + " batches=" + RecognitionRoiBudget.batchCount(
                        recognitionCrops.size(),
                        roiPlan.batchSize
                )
        );
        long recognizerStartedAt = SystemClock.elapsedRealtime();
        try {
            List<PaddleLineOcrEngine.LineResult> results =
                    recognizePaddleCropsInSmallBatches(
                            recognizer,
                            recognitionCrops,
                            roiPlan.batchSize
                    );
            long recognizerElapsedMs = SystemClock.elapsedRealtime() - recognizerStartedAt;
            diagnostics.timing("line_recognizer", recognizerElapsedMs);
            diagnosticEvent("recognizer.done", "pass=" + (cameraPass + 1)
                    + " crops=" + recognitionCrops.size()
                    + " batch=" + roiPlan.batchSize
                    + " elapsedMs=" + recognizerElapsedMs);
            Log.i(
                    "ShiqiRecognition",
                    "Text recognizer pass=" + (cameraPass + 1)
                            + " crops=" + recognitionCrops.size()
                            + " elapsedMs=" + recognizerElapsedMs
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
                            recognizePaddleCropsInSmallBatches(
                                    recognizer,
                                    enhancedCrops,
                                    roiPlan.batchSize
                            );
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

    private PaddleDateEvidence recognizePaddleTextRegionsForFrame(
            Bitmap bitmap,
            boolean cameraLive,
            CameraFrameProbe cameraProbe
    ) {
        if (!appScreenRecordingDetected || bitmap == null || bitmap.isRecycled()) {
            return recognizePaddleTextRegions(
                    bitmap,
                    cameraLive,
                    0,
                    cameraProbe == null ? null : cameraProbe.regions
            );
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

    private PaddleDateEvidence recognizePaddleFocusedProductRegion(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return PaddleDateEvidence.empty();
        }
        double leftRatio = 0.08d;
        double topRatio = appScreenRecordingDetected ? 0.12d : 0.08d;
        double widthRatio = 0.84d;
        double heightRatio = appScreenRecordingDetected ? 0.46d : 0.84d;
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        int left = clamp((int) Math.floor(imageWidth * leftRatio), 0, imageWidth - 1);
        int top = clamp((int) Math.floor(imageHeight * topRatio), 0, imageHeight - 1);
        int width = clamp((int) Math.ceil(imageWidth * widthRatio), 1, imageWidth - left);
        int height = clamp((int) Math.ceil(imageHeight * heightRatio), 1, imageHeight - top);
        Bitmap crop = Bitmap.createBitmap(bitmap, left, top, width, height);
        Bitmap prepared = upscaleIfNeeded(crop, 1800);
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

    private Bitmap enhanceRecognitionCrop(
            Bitmap crop,
            RecognitionRoiBudget.Enhancement enhancement
    ) {
        if (enhancement == RecognitionRoiBudget.Enhancement.EMBOSSED) {
            return LowContrastTextPreprocessor.enhanceEmbossedText(crop);
        }
        if (enhancement == RecognitionRoiBudget.Enhancement.BIDIRECTIONAL) {
            return LowContrastTextPreprocessor.enhanceBidirectionalDateStrokes(crop);
        }
        if (enhancement == RecognitionRoiBudget.Enhancement.BINARY) {
            return LowContrastTextPreprocessor.binarizeText(crop, false);
        }
        if (enhancement == RecognitionRoiBudget.Enhancement.INVERTED) {
            return LowContrastTextPreprocessor.binarizeText(crop, true);
        }
        return LowContrastTextPreprocessor.enhanceLaserPrintedText(crop);
    }
    private List<PaddleLineOcrEngine.LineResult> recognizePaddleCropsInSmallBatches(
            PaddleLineOcrEngine recognizer,
            List<Bitmap> crops,
            int requestedBatchSize
    ) {
        int batchSize = Math.max(1, requestedBatchSize);
        List<PaddleLineOcrEngine.LineResult> results =
                new ArrayList<PaddleLineOcrEngine.LineResult>();
        for (int start = 0; start < crops.size(); start += batchSize) {
            int end = Math.min(crops.size(), start + batchSize);
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
                        if (singleFrameConfirmation
                                || (cameraLive && lowContrastCameraPassesUsed > 0)) {
                            Bitmap bidirectional =
                                    LowContrastTextPreprocessor.enhanceBidirectionalDateStrokes(preparedCrop);
                            if (bidirectional != null) {
                                try {
                                    PaddleLineOcrEngine.LineResult bidirectionalResult =
                                            engine.recognizeResult(bidirectional, 0.25f);
                                    appendRecognizedText(recognized, bidirectionalResult.text);
                                    if (bidirectionalResult.text.length() > 0) {
                                        bestConfidence = Math.max(
                                                bestConfidence,
                                                bidirectionalResult.confidence
                                        );
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
                                    bidirectional.recycle();
                                }
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
        RecognitionFieldGapPlan gapPlan = currentFieldGapPlan();
        if (!gapPlan.scanDates) {
            return false;
        }
        if (cameraLive) {
            if (cameraSimulationScreenRecording && currentVideoFrameRatio < 0.52d) {
                return false;
            }
            int attemptLimit = Math.min(
                    cameraSimulationScreenRecording ? 4 : 6,
                    gapPlan.maxDateBandPasses
            );
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
            List<RecognitionFrameVariant> variants,
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
        variants.add(new RecognitionFrameVariant(
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

    private void appendRecognizedText(
            StringBuilder builder,
            Text text,
            RecognitionFrameVariant variant
    ) {
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
        directedScanTarget = RecognitionFieldGapPlan.Target.AUTOMATIC;
        paddleVideoFramesUsed = 0;
        paddleDetectionVideoFramesUsed = 0;
        paddleFocusedLabelVideoFramesUsed = 0;
        paddleCameraBandFramesUsed = 0;
        paddleDetectionCameraFramesUsed = 0;
        paddleDetectedLineCameraFramesUsed = 0;
        lowContrastCameraPassesUsed = 0;
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

    private boolean isAllowedEvidenceRegion(RecognitionFrameVariant variant, Rect box) {
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
            RecognitionFrameVariant variant,
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

    private void recycleVariants(List<RecognitionFrameVariant> variants) {
        for (RecognitionFrameVariant variant : variants) {
            if (variant.recycleBitmap) {
                variant.bitmap.recycle();
            }
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void addEmbossedFrameCrop(
            List<RecognitionFrameVariant> variants,
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
            variants.add(new RecognitionFrameVariant(
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
                    recognitionOverlay.showLiveRegions(null, 0, 0);
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
        Log.i(
                "ShiqiRecognition",
                "Completion date policy production=" + finalTranscriptDates.productionDates.size()
                        + " expiry=" + finalTranscriptDates.expiryDates.size()
                        + " shelf=" + finalTranscriptDates.shelfLives.size()
                        + " calculated=" + finalTranscriptDates.calculatedExpiryDates.size()
                        + " evidenceProduction=" + evidenceDates.productionDates.size()
                        + " evidenceExpiry=" + evidenceDates.expiryDates.size()
                        + " evidenceCalculated=" + evidenceDates.calculatedExpiryDates.size()
        );
        boolean finalDatesComplete = !finalTranscriptDates.productionDates.isEmpty()
                && (!finalTranscriptDates.expiryDates.isEmpty()
                || !finalTranscriptDates.calculatedExpiryDates.isEmpty());
        boolean evidenceDatesComplete = !evidenceDates.productionDates.isEmpty()
                && (!evidenceDates.expiryDates.isEmpty()
                || !evidenceDates.calculatedExpiryDates.isEmpty());
        if (!finalDatesComplete && evidenceDatesComplete) {
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
        String completionProduction = finalTranscriptDates.productionDates.isEmpty()
                ? ""
                : finalTranscriptDates.productionDates.get(0).normalized;
        String completionExpiry = completionExpiry(finalTranscriptDates);
        long completedElapsedMs = recognitionElapsedMs();
        BarcodeProductInfo completionInfo = latestProductBarcode.equals(snapshot.stableBarcode)
                ? latestProductInfo
                : null;
        String completionPackagingName = selectedPackagingName.length() > 0
                ? selectedPackagingName
                : snapshot.bestPackagingNameForConfirmation();
        String completionProductName = productNameForResult(completionPackagingName, completionInfo);
        diagnostics.value("completion_source", "video");
        diagnostics.value("completion_barcode", snapshot.stableBarcode);
        diagnostics.value("completion_product_name", completionProductName);
        diagnostics.value("completion_fillable", Boolean.toString(snapshot.hasFillableCandidate()));
        diagnostics.value("completion_production", completionProduction);
        diagnostics.value("completion_expiry", completionExpiry);
        diagnostics.value("completion_stable_name", snapshot.stablePackagingName);
        diagnostics.value(
                "completion_best_name",
                snapshot.bestPackagingNameForConfirmation()
        );
        diagnostics.value("completion_selected_name", selectedPackagingName);
        diagnostics.value(
                "completion_lookup_name",
                latestProductInfo == null ? "" : latestProductInfo.displayName()
        );
        diagnosticEvent(
                "replay.complete",
                "frames=" + analyzedFrames
                        + " fillable=" + snapshot.hasFillableCandidate()
                        + " barcode=" + snapshot.stableBarcode
                        + " productName=" + completionProductName
                        + " production=" + completionProduction
                        + " expiry=" + completionExpiry
                        + " elapsedMs=" + completedElapsedMs
        );
        Log.i(
                "ShiqiRecognition",
                "Replay complete frames=" + analyzedFrames
                        + " fillable=" + snapshot.hasFillableCandidate()
                        + " barcode=" + snapshot.hasStableBarcode()
                        + " name=" + snapshot.hasStablePackagingName()
                        + " dates=" + (snapshot.stableDateVote != null)
                        + " completionProduction=" + completionProduction
                        + " completionExpiry=" + completionExpiry
                        + " resultName=" + completionProductName
                        + " barcodeValue=" + snapshot.stableBarcode
                        + " sessionName=" + sessionProductName
                        + " stableName=" + snapshot.stablePackagingName
                        + " bestName=" + snapshot.bestPackagingNameForConfirmation()
                        + " selectedName=" + selectedPackagingName
                        + " lookupName="
                        + (latestProductInfo == null ? "" : latestProductInfo.displayName())
                        + " elapsedMs=" + completedElapsedMs
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

    private void refineIncompleteVideoProductNameFromEarlyFrames(
            VideoReplayFrameSource frameSource,
            long durationUs
    ) {
        String existingProductName = bestProductNameFromSessionEvidence();
        boolean hasStrongProductName = existingProductName.length() > 0
                && !RecognitionTextCleaner.isGenericProductCategoryName(existingProductName);
        if (!RecognitionFrameSelector.needsVideoProductRefinement(
                hasStrongProductName,
                cameraSimulationActive
        ) || frameSource == null || durationUs <= 0L) {
            return;
        }

        double[] earlyRatios = new double[] {0.06d, 0.14d, 0.22d, 0.30d};
        for (double ratio : earlyRatios) {
            long frameTimeUs = Math.max(
                    0L,
                    Math.min(durationUs - 1L, Math.round(durationUs * ratio))
            );
            Log.i(
                    "ShiqiRecognition",
                    "Reviewing deterministic product frame timestampUs=" + frameTimeUs
            );
            Bitmap frame = frameSource.frameAt(frameTimeUs);
            if (frame == null) {
                continue;
            }
            Bitmap prepared = prepareStillImage(frame);
            if (prepared != frame && !frame.isRecycled()) {
                frame.recycle();
            }
            try {
                PaddleDateEvidence evidence = recognizePaddleFocusedProductRegion(prepared);
                StringBuilder reviewTextBuilder = new StringBuilder();
                appendRecognizedText(reviewTextBuilder, evidence.packagingText);
                appendRecognizedText(reviewTextBuilder, evidence.originalText);
                appendRecognizedText(reviewTextBuilder, evidence.text);
                String reviewText = FoodItem.cleanText(reviewTextBuilder.toString());
                if (reviewText.length() == 0) {
                    continue;
                }

                List<PackagingTextAnalyzer.Observation> observations =
                        new ArrayList<PackagingTextAnalyzer.Observation>(
                                evidence.toPackagingObservations(1d)
                        );
                List<PackagingTextAnalyzer.Candidate> candidates =
                        PackagingTextAnalyzer.analyze(observations, true);
                String productName = "";
                for (PackagingTextAnalyzer.Candidate candidate : candidates) {
                    if (PackagingNamePolicy.isStrong(candidate.text)) {
                        productName = candidate.text;
                        break;
                    }
                }
                if (productName.length() == 0) {
                    productName = RecognitionTextCleaner.intelligentProductNameCandidate(reviewText);
                }
                if (!PackagingNamePolicy.isStrong(productName)
                        || RecognitionTextCleaner.isGenericProductCategoryName(productName)) {
                    productName = RecognitionTextCleaner.extractProductNameFromOcr(reviewText);
                }
                productName = PackagingNamePolicy.normalize(productName);
                if (!PackagingNamePolicy.isStrong(productName)
                        || RecognitionTextCleaner.isGenericProductCategoryName(productName)) {
                    continue;
                }

                if (observations.isEmpty()) {
                    RecognitionEvidence.NormalizedRect rect = evidence.rect;
                    observations.add(new PackagingTextAnalyzer.Observation(
                            productName,
                            Math.max(0.02d, rect.bottom - rect.top),
                            Math.max(0.08d, rect.right - rect.left),
                            (rect.left + rect.right) / 2d,
                            (rect.top + rect.bottom) / 2d,
                            1d,
                            rect.left,
                            rect.top,
                            rect.right,
                            rect.bottom,
                            evidence.packagingConfidence,
                            "Paddle focused product"
                    ));
                }
                List<PackagingTextAnalyzer.Candidate> evidenceCandidates =
                        new ArrayList<PackagingTextAnalyzer.Candidate>();
                evidenceCandidates.add(new PackagingTextAnalyzer.Candidate(
                        productName,
                        Math.max(100d, RecognitionTextCleaner.productNameScore(productName)),
                        1,
                        Collections.singletonList(productName)
                ));
                evidenceCandidates.addAll(candidates);

                appendRecognizedText(videoTranscriptText, reviewText);
                UnifiedRecognitionStabilizer.Snapshot promoted =
                        stabilizer.promotePackagingCandidateForConfirmation(productName);
                latestSnapshot = UnifiedRecognitionStabilizer.retainStableEvidence(
                        latestSnapshot,
                        promoted
                );
                rememberSessionSnapshot(latestSnapshot);
                currentVideoFrameRatio = ratio;
                rememberEvidenceFrame(
                        prepared,
                        frameTimeUs / 1000L,
                        frameTimeUs,
                        LowContrastTextPreprocessor.measureFrame(prepared),
                        observations,
                        evidenceCandidates,
                        DateOcrParser.parse(reviewText),
                        ""
                );
                Log.i(
                        "ShiqiRecognition",
                        "Static product frame review timestampUs=" + frameTimeUs
                                + " productName=" + productName
                );
                return;
            } finally {
                if (!prepared.isRecycled()) {
                    prepared.recycle();
                }
            }
        }
    }
    private void refineIncompleteVideoDateFromKeyframe(
            VideoReplayFrameSource frameSource,
            long durationUs
    ) {
        String transcript = videoTranscriptText.length() > 0
                ? videoTranscriptText.toString()
                : latestRawText;
        DateOcrParser.Result current = DateEvidencePolicy.chooseVideoCompletionEvidence(
                transcript,
                bestVideoDatePair
        );
        Log.i(
                "ShiqiRecognition",
                "Refinement date policy production=" + current.productionDates.size()
                        + " expiry=" + current.expiryDates.size()
                        + " shelf=" + current.shelfLives.size()
                        + " calculated=" + current.calculatedExpiryDates.size()
                        + " stableVote="
                        + (latestSnapshot != null && latestSnapshot.stableDateVote != null)
        );
        if (latestSnapshot != null
                && (UnifiedRecognitionStabilizer.isReliableDirectDatePair(
                latestSnapshot.stableDateVote
        ) || DateEvidencePolicy.isReliableCalculatedDate(latestSnapshot.stableDateVote))) {
            return;
        }
        if (!RecognitionFrameSelector.needsVideoDateRefinement(
                !current.productionDates.isEmpty(),
                !current.expiryDates.isEmpty(),
                !current.calculatedExpiryDates.isEmpty(),
                cameraSimulationActive
        )) {
            return;
        }
        if (frameSource == null) {
            return;
        }

        SessionKeyframe keyframe = bestDateReviewKeyframe();
        if (keyframe == null) {
            refineDateFromReplayTailFrames(frameSource, durationUs, transcript);
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
            Bitmap frame = frameSource.rawFrameAt(frameTimeUs);
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
        if (cameraSimulationActive) {
            refineDateFromReplayTailFrames(frameSource, durationUs, transcript);
        }
    }

    private boolean refineDateFromReplayTailFrames(
            VideoReplayFrameSource frameSource,
            long durationUs,
            String transcript
    ) {
        if (!cameraSimulationActive || frameSource == null || durationUs <= 0L) {
            return false;
        }
        double[] tailRatios = new double[] {0.98d, 0.90d, 0.82d};
        for (double ratio : tailRatios) {
            long frameTimeUs = Math.max(
                    0L,
                    Math.min(durationUs - 1L, Math.round(durationUs * ratio))
            );
            Log.i(
                    "ShiqiRecognition",
                    "Reviewing deterministic tail date timestampUs=" + frameTimeUs
            );
            Bitmap frame = frameSource.frameAt(frameTimeUs);
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
                boolean allowFullRegionDetection = ratio <= 0.91d;
                if (refineDateFromPreparedKeyframe(
                        prepared,
                        transcript,
                        frameTimeUs,
                        null,
                        allowFullRegionDetection
                )) {
                    return true;
                }
            } finally {
                if (!prepared.isRecycled()) {
                    prepared.recycle();
                }
            }
        }
        return false;
    }

    private boolean refineDateFromPreparedKeyframe(
            Bitmap prepared,
            String transcript,
            long frameTimeUs,
            RecognitionEvidence.NormalizedRect dateTailRect
    ) {
        return refineDateFromPreparedKeyframe(
                prepared,
                transcript,
                frameTimeUs,
                dateTailRect,
                true
        );
    }

    private boolean refineDateFromPreparedKeyframe(
            Bitmap prepared,
            String transcript,
            long frameTimeUs,
            RecognitionEvidence.NormalizedRect dateTailRect,
            boolean allowFullRegionDetection
    ) {
        PaddleDateEvidence evidence = dateTailRect == null
                ? recognizeFocusedLaserExpiry(prepared, allowFullRegionDetection)
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
        DateOcrParser.Result confirmedDirectPair = DateEvidencePolicy.apply(
                DateOcrParser.parse(
                        "\u751f\u4ea7\u65e5\u671f " + vote.productionDate.value
                                + "\n\u6709\u6548\u671f\u81f3 " + vote.expiryDate.value
                ),
                "",
                false
        );
        appendRecognizedText(videoTranscriptText, evidence.text);
        bestVideoDatePair = confirmedDirectPair;
        bestVideoDatePairScore = Math.max(bestVideoDatePairScore, 3d);
        latestSnapshot = UnifiedRecognitionStabilizer.retainStableEvidence(
                latestSnapshot,
                stabilizer.promoteDirectDatePairForConfirmation(confirmedDirectPair)
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
        return recognizeFocusedLaserExpiry(bitmap, true);
    }

    private PaddleDateEvidence recognizeFocusedLaserExpiry(
            Bitmap bitmap,
            boolean allowFullRegionDetection
    ) {
        PaddleLineOcrEngine engine = paddleLineOcrEngine;
        if (engine == null || bitmap == null || bitmap.isRecycled()) {
            return PaddleDateEvidence.empty();
        }

        // Narrow strips are substantially cheaper and preserve faint dot-matrix strokes.
        // Only one representative tail frame is allowed to fall back to whole-frame detection.
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
        if (!allowFullRegionDetection) {
            return recognizePaddleDateBands(bitmap, true, false, true);
        }

        PaddleDateEvidence detectedRegions = recognizePaddleTextRegions(
                bitmap,
                false,
                24
        );
        PaddleDateEvidence brightRegions = PaddleDateEvidence.empty();
        Bitmap brightText = LowContrastTextPreprocessor.isolateBrightText(bitmap);
        if (brightText != null) {
            try {
                brightRegions = recognizePaddleTextRegions(brightText, false, 24);
            } finally {
                if (!brightText.isRecycled()) {
                    brightText.recycle();
                }
            }
        }
        StringBuilder detectedText = new StringBuilder();
        StringBuilder detectedOriginal = new StringBuilder();
        StringBuilder detectedPackaging = new StringBuilder();
        appendRecognizedText(detectedText, detectedRegions.text);
        appendRecognizedText(detectedText, brightRegions.text);
        appendRecognizedText(detectedOriginal, detectedRegions.originalText);
        appendRecognizedText(detectedOriginal, brightRegions.originalText);
        appendRecognizedText(detectedPackaging, detectedRegions.packagingText);
        appendRecognizedText(detectedPackaging, brightRegions.packagingText);
        if (detectedText.length() > 0) {
            RecognitionEvidence.NormalizedRect detectedRect =
                    detectedRegions.text.length() > 0
                    ? detectedRegions.rect
                    : brightRegions.rect;
            if (detectedRegions.text.length() > 0 && brightRegions.text.length() > 0) {
                detectedRect = detectedRegions.rect.union(brightRegions.rect);
            }
            return new PaddleDateEvidence(
                    detectedText.toString(),
                    detectedOriginal.toString(),
                    detectedPackaging.toString(),
                    Math.max(detectedRegions.confidence, brightRegions.confidence),
                    Math.max(
                            detectedRegions.packagingConfidence,
                            brightRegions.packagingConfidence
                    ),
                    detectedRect
            );
        }
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
        int height = clamp(Math.round(imageHeight * 0.425f), 1, imageHeight - top);
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
        RecognitionWorkSession.State sessionState = recognitionWorkSession.resetAll();
        Log.i(
                "ShiqiRecognition",
                "Reset recognition generation=" + sessionState.generation
                        + " videoActive=" + videoReplayActive
        );
        diagnostics.reset(SystemClock.elapsedRealtime(), "reset");
        recognitionGeneration = sessionState.generation;
        resetLatestCameraFrames();
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
        firstProductFieldElapsedMs = -1L;
        firstBarcodeFieldElapsedMs = -1L;
        firstDateFieldElapsedMs = -1L;
        paddleVideoFramesUsed = 0;
        paddleDetectionVideoFramesUsed = 0;
        paddleFocusedLabelVideoFramesUsed = 0;
        paddleCameraBandFramesUsed = 0;
        paddleDetectionCameraFramesUsed = 0;
        paddleDetectedLineCameraFramesUsed = 0;
        lowContrastCameraPassesUsed = 0;
        videoAnalyzedFrames = 0;
        videoExpectedFrames = 0;
        liveCameraResultFrozen = false;
        lastAnalyzeAt = 0L;
        cameraFramesCaptured = 0;
        resetCameraFrameGate();
        focusRecoveryPolicy.reset();
        cameraLastStatusAtMs = 0L;
        latestTextFocusXRatio = 0.5d;
        latestTextFocusYRatio = 0.48d;
        cameraFocusState = "自动对焦准备中";
        cameraFrameState = "等待相机画面";
        renderCameraHud();
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

        Bitmap bidirectional = LowContrastTextPreprocessor.enhanceBidirectionalDateStrokes(bitmap);
        if (bidirectional != null) {
            try {
                results.add(engine.recognizeResult(bidirectional, lowConfidenceDateThreshold));
            } finally {
                bidirectional.recycle();
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

    private void initializeCameraPipeline() {
        latestCameraFrames = new LatestFrameProcessor<Bitmap>(
                cameraExecutor,
                new LatestFrameProcessor.Processor<Bitmap>() {
                    @Override
                    public void process(LatestFrameProcessor.Frame<Bitmap> frame) throws Exception {
                        processLatestCameraFrame(frame);
                    }
                },
                new LatestFrameProcessor.Releaser<Bitmap>() {
                    @Override
                    public void release(Bitmap bitmap) {
                        recycleBitmap(bitmap);
                    }
                },
                new LatestFrameProcessor.ErrorListener() {
                    @Override
                    public void onError(Throwable error) {
                        Log.w("ShiqiRecognition", "Latest camera frame processing failed", error);
                        diagnostics.increment("camera_worker_error");
                        diagnosticEvent(
                                "camera.worker.failed",
                                error == null ? "unknown" : error.getClass().getSimpleName()
                        );
                    }
                }
        );
        cameraFrameSource = new CameraXFrameSource(
                this,
                previewView,
                cameraAnalyzerExecutor,
                mainExecutor,
                new CameraXFrameSource.Listener() {
                    @Override
                    public boolean shouldAcquireFrame(long nowMs) {
                        LatestFrameProcessor<Bitmap> frames = latestCameraFrames;
                        boolean busy = frames != null && frames.isBusy();
                        if (!RecognitionFrameSelector.shouldCaptureLatestCameraFrame(
                                cameraBound,
                                nowMs,
                                lastAnalyzeAt,
                                CAMERA_CAPTURE_INTERVAL_MS,
                                busy
                        )) {
                            return false;
                        }
                        lastAnalyzeAt = nowMs;
                        return true;
                    }

                    @Override
                    public void onFrame(Bitmap frame, long timestampUs) {
                        offerLatestCameraFrame(frame, timestampUs, recognitionGeneration);
                    }

                    @Override
                    public void onCameraReady(boolean flashAvailable) {
                        handleCameraReady(flashAvailable);
                    }

                    @Override
                    public void onCameraUnavailable(String userMessage, Throwable error) {
                        if (error != null) {
                            Log.w("ShiqiRecognition", "Camera start failed", error);
                        }
                        showCameraUnavailable(userMessage);
                    }

                    @Override
                    public void onFrameError(Throwable error) {
                        Log.w("ShiqiRecognition", "Camera frame capture failed", error);
                        diagnostics.increment("camera_capture_error");
                        diagnosticEvent(
                                "camera.capture.failed",
                                error == null ? "unknown" : error.getClass().getSimpleName()
                        );
                    }

                    @Override
                    public void onFocusEvent(CameraXFrameSource.FocusEvent event) {
                        handleCameraFocusEvent(event);
                    }
                }
        );
    }

    private void startCamera() {
        if (previewView == null
                || cameraFrameSource == null
                || latestCameraFrames == null) {
            return;
        }
        cameraBound = false;
        latestCameraFrames.start(recognitionGeneration);
        diagnostics.setSource(SystemClock.elapsedRealtime(), "camera");
        diagnosticEvent("camera.start", "request provider");
        cameraFrameSource.start(dateOnlyMode);
    }

    private void handleCameraReady(boolean flashAvailable) {
        cameraBound = true;
        if (torchButton != null) {
            torchButton.setVisibility(flashAvailable ? View.VISIBLE : View.GONE);
        }
        statusBadge.setText("相机");
        RecognitionFieldGapPlan plan = currentFieldGapPlan();
        setCameraFrameState("相机已连接 · " + plan.label + " · 等待第一帧", false);
        statusText.setText(barcodeLookupOnly
                ? "请对准商品条码，连续两帧一致后会自动查找。"
                : plan.guidance + "；已有识别结果会保留");
    }

    private void showCameraUnavailable(String message) {
        cameraBound = false;
        statusBadge.setText("无相机");
        statusText.setText(message);
        updateResultUi(stabilizer.snapshot());
        stopCamera();
    }

    private void handleCameraFocusEvent(CameraXFrameSource.FocusEvent event) {
        if (event == null) {
            return;
        }
        if (event.status == CameraXFrameSource.FocusStatus.STARTED) {
            focusRecoveryPolicy.markFocusRequested(SystemClock.elapsedRealtime());
            diagnostics.increment("focus_requested");
            if (event.automaticRecovery) {
                diagnostics.increment("focus_recovery");
            }
            diagnosticEvent(
                    "focus.start",
                    "id=" + event.requestId
                            + " recovery=" + event.automaticRecovery
                            + " x=" + Math.round(event.x)
                            + " y=" + Math.round(event.y)
            );
            setCameraFocusState(event.announced ? "点按对焦中" : "自动对焦中");
            recognitionOverlay.showFocusPoint(
                    event.x,
                    event.y,
                    RecognitionGuideOverlay.FOCUS_RUNNING
            );
            return;
        }
        if (event.status == CameraXFrameSource.FocusStatus.UNSUPPORTED) {
            setCameraFocusState("固定焦距设备");
            recognitionOverlay.showFocusPoint(
                    event.x,
                    event.y,
                    RecognitionGuideOverlay.FOCUS_UNSUPPORTED
            );
            diagnostics.increment("focus_failed");
            diagnosticEvent(
                    "focus.unsupported",
                    "x=" + Math.round(event.x) + " y=" + Math.round(event.y)
            );
            return;
        }
        if (event.status == CameraXFrameSource.FocusStatus.SUPERSEDED) {
            diagnosticEvent("focus.superseded", "id=" + event.requestId);
            return;
        }
        if (event.status == CameraXFrameSource.FocusStatus.CONTINUOUS) {
            setCameraFocusState("连续自动对焦 · 实时检查清晰度");
            return;
        }

        boolean successful = event.status == CameraXFrameSource.FocusStatus.SUCCESS;
        diagnostics.increment(successful ? "focus_success" : "focus_failed");
        diagnostics.timing("focus", event.elapsedMs);
        diagnosticEvent(
                successful ? "focus.done" : "focus.failed",
                "id=" + event.requestId
                        + " success=" + successful
                        + (event.error == null
                        ? ""
                        : " error=" + event.error.getClass().getSimpleName())
        );
        setCameraFocusState(successful
                ? "本次对焦已完成 · 正在验清晰度"
                : event.error == null
                ? "未锁定焦点 · 连续对焦已恢复"
                : "对焦失败 · 连续对焦已恢复");
        recognitionOverlay.showFocusPoint(
                event.x,
                event.y,
                successful
                        ? RecognitionGuideOverlay.FOCUS_SUCCESS
                        : RecognitionGuideOverlay.FOCUS_FAILED
        );
    }
    private void setCameraFocusState(String state) {
        cameraFocusState = FoodItem.cleanText(state);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderCameraHud();
            }
        });
    }

    private void setCameraFrameState(String state, boolean throttle) {
        long now = SystemClock.uptimeMillis();
        if (throttle && !CameraOcrFeedback.shouldPublishFrameStatus(now, cameraLastStatusAtMs)) {
            return;
        }
        cameraLastStatusAtMs = now;
        cameraFrameState = FoodItem.cleanText(state);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderCameraHud();
            }
        });
    }

    private void renderCameraHud() {
        if (cameraHud == null) {
            return;
        }
        cameraHud.setText(ScanUiPresentation.cameraMessage(
                cameraFocusState,
                cameraFrameState
        ));
        boolean previewVisible = previewView != null
                && previewView.getVisibility() == View.VISIBLE
                && !videoReplayActive
                && !confirmationVisible;
        cameraHud.setVisibility(previewVisible && !showingEvidenceFrame ? View.VISIBLE : View.GONE);
    }
    private void stopCamera() {
        cameraBound = false;
        if (cameraFrameSource != null) {
            cameraFrameSource.stop();
        }
        if (latestCameraFrames != null) {
            latestCameraFrames.stop();
        }
        torchEnabled = false;
        if (torchButton != null) {
            torchButton.setContentDescription("打开手电筒");
            torchButton.setBackground(rounded(
                    Color.argb(56, 255, 255, 255),
                    dp(8),
                    0
            ));
        }
    }

    private void offerLatestCameraFrame(Bitmap bitmap, long timestampUs, int generation) {
        LatestFrameProcessor<Bitmap> frames = latestCameraFrames;
        if (bitmap == null || bitmap.isRecycled() || frames == null) {
            recycleBitmap(bitmap);
            return;
        }
        if (!cameraBound || generation != recognitionGeneration) {
            recycleBitmap(bitmap);
            return;
        }

        boolean recognitionBusy = frames.isBusy();
        cameraFramesCaptured++;
        LatestFrameProcessor.OfferResult result = frames.offer(
                bitmap,
                timestampUs,
                generation
        );
        if (!result.accepted) {
            diagnostics.increment("camera_offer_rejected");
            return;
        }
        diagnostics.increment("frames_captured");
        if (result.replacedPending) {
            diagnostics.increment("pending_replaced");
        }
        setCameraFrameState(
                recognitionBusy
                        ? "已看到实时画面 · 正在识别，保留最新帧 " + cameraFramesCaptured
                        : "已看到实时画面 · 正在检查清晰度 " + cameraFramesCaptured,
                true
        );
    }

    private void processLatestCameraFrame(
            LatestFrameProcessor.Frame<Bitmap> queuedFrame
    ) throws Exception {
        if (queuedFrame == null
                || !cameraBound
                || queuedFrame.generation != recognitionGeneration) {
            return;
        }
        long queueLagMs = Math.max(
                0L,
                (SystemClock.elapsedRealtime() * 1000L - queuedFrame.timestampUs) / 1000L
        );
        diagnostics.timing("queue_lag", queueLagMs);
        currentFrameTimestampUs = queuedFrame.timestampUs;
        CameraFrameProbe probe = inspectCameraFrame(
                queuedFrame.value,
                queuedFrame.generation
        );
        if (probe.shouldRecognize) {
            analyzeBitmapFrame(
                    queuedFrame.value,
                    false,
                    true,
                    queuedFrame.generation,
                    probe
            );
        }
    }
    private CameraFrameProbe inspectCameraFrame(Bitmap frame, int expectedGeneration) {
        LowContrastTextPreprocessor.FrameMetrics metrics =
                LowContrastTextPreprocessor.measureFrame(frame);
        diagnostics.increment("frames_probed");
        long now = SystemClock.elapsedRealtime();
        RecognitionFrameSelector.CameraFramePreflight preflight =
                RecognitionFrameSelector.cameraFramePreflight(
                        cameraFramesCaptured,
                        now,
                        lastCameraProbeAtMs,
                        metrics.visualScore,
                        metrics.sharpness,
                        metrics.brightness,
                        metrics.contrast,
                        metrics.glareRatio,
                        bestCameraVisualScore,
                        dateOnlyMode
                );
        if (!preflight.shouldProbe) {
            if (preflight.reason == RecognitionFrameSelector.CAMERA_FRAME_BLUR) {
                diagnostics.increment("skip_blur");
            } else {
                diagnostics.increment("skip_preflight");
            }
            diagnostics.value("latest_sharpness", String.format(Locale.US, "%.1f", metrics.sharpness));
            diagnostics.value("latest_contrast", String.format(Locale.US, "%.1f", metrics.contrast));
            diagnosticEvent("frame.skip", "preflight=" + preflight.reason
                    + " sharpness=" + String.format(Locale.US, "%.1f", metrics.sharpness)
                    + " contrast=" + String.format(Locale.US, "%.1f", metrics.contrast));
            publishCameraPreflightGuidance(preflight.reason);
            return new CameraFrameProbe(
                    metrics,
                    Collections.<PaddleTextDetectionEngine.TextRegion>emptyList(),
                    false
            );
        }

        boolean detectorReady = paddleModelsReady
                && paddleTextDetectionEngine != null
                && !barcodeLookupOnly;
        List<PaddleTextDetectionEngine.TextRegion> regions =
                new ArrayList<PaddleTextDetectionEngine.TextRegion>();
        if (detectorReady && expectedGeneration == recognitionGeneration) {
            long probeStartedAt = SystemClock.elapsedRealtime();
            regions.addAll(paddleTextDetectionEngine.detect(
                    frame,
                    dateOnlyMode ? 16 : 12,
                    640
            ));
            long probeElapsedMs = SystemClock.elapsedRealtime() - probeStartedAt;
            diagnostics.timing("probe_detector", probeElapsedMs);
            diagnosticEvent("probe.done", "regions=" + regions.size()
                    + " elapsedMs=" + probeElapsedMs);
        }
        if (expectedGeneration != recognitionGeneration) {
            return new CameraFrameProbe(metrics, regions, false);
        }

        cameraProbeCount++;
        lastCameraProbeAtMs = now;
        bestCameraVisualScore = Math.max(bestCameraVisualScore, preflight.visualScore);
        double coverage = 0d;
        double confidence = 0d;
        for (PaddleTextDetectionEngine.TextRegion region : regions) {
            coverage += Math.max(0d, region.right - region.left)
                    * Math.max(0d, region.bottom - region.top);
            confidence += region.confidence;
        }
        double meanConfidence = regions.isEmpty() ? 0d : confidence / regions.size();
        double informationScore = RecognitionFrameSelector.cameraInformationScore(
                regions.size(),
                coverage,
                meanConfidence
        );
        double candidateScore = RecognitionFrameSelector.cameraFrameValueScore(
                preflight.visualScore,
                informationScore
        );
        boolean shouldRecognize = RecognitionFrameSelector.shouldRecognizeCameraFrame(
                cameraProbeCount,
                now,
                lastCameraAcceptedAtMs,
                barcodeLookupOnly,
                dateOnlyMode,
                regions.size(),
                candidateScore,
                bestCameraAcceptedScore,
                detectorReady
        );
        int signatureDistance = lastCameraAcceptedAtMs <= 0L
                ? 64
                : RecognitionFrameSelector.perceptualHashDistance(
                        metrics.signature,
                        lastCameraAcceptedSignature
                );
        if (shouldRecognize
                && signatureDistance <= 1
                && now - lastCameraAcceptedAtMs < 650L
                && candidateScore <= bestCameraAcceptedScore + 0.01d) {
            shouldRecognize = false;
        }

        updateLatestFocusTarget(regions);
        publishLiveTextRegions(regions, frame.getWidth(), frame.getHeight());
        if (!shouldRecognize) {
            if (regions.isEmpty()) {
                diagnostics.increment("skip_no_text");
            } else {
                diagnostics.increment("skip_not_better");
            }
            setCameraFrameState(
                    regions.isEmpty()
                            ? "暂未发现文字区 · 缓慢移动包装"
                            : "发现 " + regions.size() + " 个文字区 · 等待更清晰画面",
                    true
            );
            return new CameraFrameProbe(metrics, regions, false);
        }

        boolean meaningfulImprovement = bestCameraAcceptedScore < 0d
                || candidateScore >= bestCameraAcceptedScore + 0.025d;
        focusRecoveryPolicy.shouldRecover(false, false, SystemClock.elapsedRealtime());
        diagnostics.increment("frames_accepted");
        diagnosticEvent("frame.accept", "source=camera regions=" + regions.size());
        lastCameraAcceptedAtMs = now;
        lastCameraAcceptedSignature = metrics.signature;
        bestCameraAcceptedScore = Math.max(bestCameraAcceptedScore, candidateScore);
        if (!regions.isEmpty()) {
            announceValuableFrame(regions.size(), meaningfulImprovement, now);
        } else {
            setCameraFrameState("正在核验低对比度文字 · 请保持稳定", false);
        }
        return new CameraFrameProbe(metrics, regions, true);
    }

    private void publishCameraPreflightGuidance(int reason) {
        maybeRecoverCameraFocus(reason);
        String guidance;
        if (reason == RecognitionFrameSelector.CAMERA_FRAME_TOO_DARK) {
            guidance = "画面太暗 · 请增加环境光";
        } else if (reason == RecognitionFrameSelector.CAMERA_FRAME_TOO_BRIGHT) {
            guidance = "画面过亮 · 请避开直射光";
        } else if (reason == RecognitionFrameSelector.CAMERA_FRAME_GLARE) {
            guidance = "包装反光 · 请稍微倾斜手机";
        } else if (reason == RecognitionFrameSelector.CAMERA_FRAME_BLUR) {
            guidance = "文字发糊 · 请轻点文字并保持稳定";
        } else {
            guidance = "正在比较画面 · 等待更清晰一帧";
        }
        setCameraFrameState(guidance, true);
    }

    private void maybeRecoverCameraFocus(int reason) {
        boolean blurred = reason == RecognitionFrameSelector.CAMERA_FRAME_BLUR;
        CameraXFrameSource source = cameraFrameSource;
        long now = SystemClock.elapsedRealtime();
        if (source == null || !cameraBound) {
            focusRecoveryPolicy.shouldRecover(false, false, now);
            return;
        }
        if (!focusRecoveryPolicy.shouldRecover(
                blurred,
                source.isFocusInFlight(),
                now
        )) {
            return;
        }
        final double focusXRatio = latestTextFocusXRatio;
        final double focusYRatio = latestTextFocusYRatio;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CameraXFrameSource activeSource = cameraFrameSource;
                if (activeSource == null || !cameraBound) {
                    return;
                }
                setCameraFrameState("连续模糊 · 正在自动重对焦", false);
                activeSource.focusAtRatio(
                        focusXRatio,
                        focusYRatio,
                        false,
                        true
                );
            }
        });
    }
    private void updateLatestFocusTarget(List<PaddleTextDetectionEngine.TextRegion> regions) {
        if (regions == null || regions.isEmpty()) {
            return;
        }
        PaddleTextDetectionEngine.TextRegion best = null;
        double bestScore = -1d;
        for (PaddleTextDetectionEngine.TextRegion region : regions) {
            if (region == null) {
                continue;
            }
            double area = Math.max(0d, region.right - region.left)
                    * Math.max(0d, region.bottom - region.top);
            double score = region.confidence + Math.min(0.25d, area * 2d);
            if (score > bestScore) {
                best = region;
                bestScore = score;
            }
        }
        if (best == null) {
            return;
        }
        latestTextFocusXRatio = Math.max(
                0.08d,
                Math.min(0.92d, (best.left + best.right) / 2d)
        );
        latestTextFocusYRatio = Math.max(
                0.12d,
                Math.min(0.78d, (best.top + best.bottom) / 2d)
        );
    }

    private void publishLiveTextRegions(
            List<PaddleTextDetectionEngine.TextRegion> regions,
            int imageWidth,
            int imageHeight
    ) {
        final List<PaddleTextDetectionEngine.TextRegion> safeRegions =
                regions == null
                        ? Collections.<PaddleTextDetectionEngine.TextRegion>emptyList()
                        : new ArrayList<PaddleTextDetectionEngine.TextRegion>(regions);
        final int width = imageWidth;
        final int height = imageHeight;
        final int generation = recognitionGeneration;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (generation != recognitionGeneration || recognitionOverlay == null) {
                    return;
                }
                recognitionOverlay.showLiveRegions(safeRegions, width, height);
            }
        });
    }

    private void announceValuableFrame(
            final int regionCount,
            boolean meaningfulImprovement,
            long now
    ) {
        if (!meaningfulImprovement
                && lastValuableFramePromptAtMs > 0L
                && now - lastValuableFramePromptAtMs < 1800L) {
            setCameraFrameState("已发现文字区 · 正在稳定识别", false);
            return;
        }
        final boolean firstPrompt = lastValuableFramePromptAtMs == 0L;
        lastValuableFramePromptAtMs = now;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusBadge.setText("发现有效画面");
                setCameraFrameState(
                        "已保留更清晰画面 · 正在识别 " + regionCount + " 个文字区",
                        false
                );
                View hapticView = previewView == null ? recognitionOverlay : previewView;
                if (hapticView != null) {
                    hapticView.performHapticFeedback(
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                    ? HapticFeedbackConstants.CONFIRM
                                    : HapticFeedbackConstants.CLOCK_TICK
                    );
                }
                if (firstPrompt) {
                    Toast.makeText(
                            DateOcrScanActivity.this,
                            "已发现有效包装画面，正在选取最清晰文字",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        });
    }

    private void resetLatestCameraFrames() {
        if (latestCameraFrames != null) {
            latestCameraFrames.start(recognitionGeneration);
        }
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
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
                RecognitionFieldPromoter.candidatesFromTranscript(rawText),
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
        RecognitionFieldPromoter.Promotion promotion = RecognitionFieldPromoter.promote(
                stabilizer,
                latestSnapshot,
                barcode,
                parsed,
                rawText,
                packagingCandidates,
                singleFrameConfirmation
        );
        latestSnapshot = promotion.snapshot;
        recordFieldPromotionDiagnostics(promotion);

        if (promotion.cleanedText.length() > 0) {
            latestRawText = promotion.cleanedText;
            if (videoReplayActive && videoTranscriptText.length() < 32000) {
                appendRecognizedText(videoTranscriptText, promotion.cleanedText);
            }
        }
        String barcodeContext = videoReplayActive
                ? videoTranscriptText.toString()
                : promotion.cleanedText;
        if (latestSnapshot != null
                && latestSnapshot.hasStableBarcode()
                && RecognitionTextCleaner.isNonProductTraceabilityCode(
                barcodeContext,
                latestSnapshot.stableBarcode
        )) {
            latestSnapshot = UnifiedRecognitionStabilizer.withoutBarcode(latestSnapshot);
            bestSessionSnapshot = UnifiedRecognitionStabilizer.withoutBarcode(bestSessionSnapshot);
            diagnosticEvent("barcode.reject", "reason=traceability-context");
        }
        rememberSessionSnapshot(latestSnapshot);
        recordFirstFieldMetrics(latestSnapshot);
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
        if (regions.isEmpty()) {
            RecognitionEvidence.Region genericText = genericTextRegion(
                    observations,
                    frameQuality
            );
            if (genericText != null) {
                regions.add(genericText);
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

    private RecognitionEvidence.Region genericTextRegion(
            List<PackagingTextAnalyzer.Observation> observations,
            double frameQuality
    ) {
        if (observations == null || observations.isEmpty()) {
            return null;
        }
        PackagingTextAnalyzer.Observation best = null;
        String bestText = "";
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PackagingTextAnalyzer.Observation observation : observations) {
            String cleaned = RecognitionTextCleaner.cleanForPackagingOcr(observation.text);
            if (cleaned.length() < 2) {
                continue;
            }
            double area = Math.max(0d, observation.right - observation.left)
                    * Math.max(0d, observation.bottom - observation.top);
            double model = Double.isNaN(observation.modelConfidence)
                    ? 0.5d
                    : RecognitionEvidence.clamp01(observation.modelConfidence);
            double score = (Math.min(0.12d, area) * 4d)
                    + (model * 0.35d)
                    + (Math.min(20, cleaned.length()) / 100d);
            if (best == null || score > bestScore) {
                best = observation;
                bestText = cleaned;
                bestScore = score;
            }
        }
        if (best == null) {
            return null;
        }
        return new RecognitionEvidence.Region(
                RecognitionEvidence.FIELD_TEXT_REGION,
                snippet(bestText, 40),
                observationRect(best),
                "文字定位",
                best.modelConfidence,
                RecognitionEvidence.fusedConfidence(
                        best.modelConfidence,
                        frameQuality,
                        0.55d,
                        1,
                        1
                )
        );
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
        if (RecognitionEvidence.FIELD_BARCODE.equals(strongest.field)) {
            return RecognitionFrameSelector.EVIDENCE_BARCODE;
        }
        return RecognitionFrameSelector.EVIDENCE_NONE;
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
        if (RecognitionEvidence.FIELD_TEXT_REGION.equals(field)) {
            return 3;
        }
        return 4;
    }
    private void renderKeyframesAsync() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderKeyframes();
                renderProductCandidates(latestSnapshot);
                updatePresentationUi(latestSnapshot);
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

        boolean fillable = dateOnlyEntryMode
                ? snapshot.hasStableDateCandidate()
                : snapshot.hasFillableCandidate() || selectedPackagingName.length() > 0;
        fillButton.setEnabled(fillable);
        fillButton.setAlpha(fillable ? 1f : 0.45f);

        updateStatus(snapshot);
        updatePresentationUi(snapshot);
        updateResultSheetHeight(snapshot);
    }

    private void updateResultSheetHeight(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        // D1b keeps the camera sheet stable so recognition cannot resize the preview.
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
                RecognitionFieldPromoter.candidatesFromTranscript(cleaned);
        String barcode = RecognitionTextCleaner.extractProductCodeFromOcr(cleaned);
        RecognitionFieldPromoter.Promotion promotion = RecognitionFieldPromoter.promote(
                stabilizer,
                latestSnapshot,
                barcode,
                dateEvidence,
                cleaned,
                candidates,
                true
        );
        latestSnapshot = promotion.snapshot;
        recordFieldPromotionDiagnostics(promotion);
        rememberSessionSnapshot(latestSnapshot);
        recordFirstFieldMetrics(latestSnapshot);
    }

    private void recordFieldPromotionDiagnostics(
            RecognitionFieldPromoter.Promotion promotion
    ) {
        if (promotion == null) {
            return;
        }
        if (promotion.hasText()) {
            diagnostics.increment("field_text_seen");
        }
        diagnostics.add("field_candidates", promotion.candidateCount);
        diagnostics.value(
                "field_candidates_latest",
                Integer.toString(promotion.candidateCount)
        );
        if (promotion.barcodePromoted) {
            diagnostics.increment("field_barcode_promoted");
        }
        if (promotion.namePromoted) {
            diagnostics.increment("field_name_promoted");
        }
        if (promotion.datePromoted) {
            diagnostics.increment("field_date_promoted");
        }
        diagnosticEvent(
                "field.promote",
                "text=" + promotion.hasText()
                        + " candidates=" + promotion.candidateCount
                        + " barcode=" + promotion.barcodePromoted
                        + " name=" + promotion.namePromoted
                        + " date=" + promotion.datePromoted
        );
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
        if (snapshot != null
                && !UnifiedRecognitionStabilizer.isReliableDirectDatePair(snapshot.stableDateVote)
                && DateEvidencePolicy.isReliableCalculatedDate(snapshot.stableDateVote)) {
            DateOcrFrameVoter.VoteResult vote = snapshot.stableDateVote;
            String shelfUnit = "month".equals(vote.shelfLife.unit)
                    ? "个月"
                    : "year".equals(vote.shelfLife.unit) ? "年" : "天";
            return DateEvidencePolicy.apply(
                    DateOcrParser.parse(
                            "生产日期 " + vote.productionDate.value
                                    + " 保质期 " + vote.shelfLife.value + shelfUnit
                    ),
                    "",
                    false
            );
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
            if (bestSessionSnapshot.hasStablePackagingName()
                    && PackagingNamePolicy.isStrong(
                    bestSessionSnapshot.stablePackagingName
            )) {
                bestName = bestSessionSnapshot.stablePackagingName;
                bestScore = 200d + bestSessionSnapshot.packagingNameVotes;
            }
            for (PackagingTextAnalyzer.Candidate candidate
                    : bestSessionSnapshot.rankedPackagingCandidates) {
                if (!PackagingNamePolicy.isStrong(candidate.text)) {
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
                        || !PackagingNamePolicy.isStrong(region.value)) {
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
        return PackagingNamePolicy.isStrong(normalized) ? normalized : "";
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
            String name = productNameForResult(packagingName, latestProductInfo);
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
        boolean stableRecommendation = snapshot.hasStablePackagingName()
                && RecognitionTextCleaner.productNamesSimilar(
                snapshot.stablePackagingName,
                recommended.text
        )
                && PackagingNamePolicy.isStrong(recommended.text);
        if (stableRecommendation) {
            confidence.setText(snapshot.frameCount > 1
                    ? "商品名多帧合并 · 请核对"
                    : "商品名文字清晰 · 请核对");
        } else {
            double fusedConfidence = productCandidateConfidence(recommended);
            confidence.setText("商品名建议 "
                    + RecognitionEvidence.confidenceLabel(fusedConfidence)
                    + " · 请核对");
        }
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
                .setNeutralButton("手动修改", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showProductNameEditor(latestSnapshot);
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

    private void renderEmptyEvidence() {
        keyframeTitle.setVisibility(View.VISIBLE);
        keyframeScroll.setVisibility(View.VISIBLE);
        keyframeContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText("尚未保留清晰关键帧\n继续扫描后会在这里显示识别依据");
        empty.setTextColor(Color.rgb(100, 116, 139));
        empty.setTextSize(13);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(rounded(Color.rgb(240, 243, 247), dp(8), 0));
        keyframeContainer.addView(empty, fixed(280, 88));
        updateResultSheetHeight(latestSnapshot);
    }

    private int evidenceDialogHeight() {
        int available = getResources().getDisplayMetrics().heightPixels;
        return Math.min(dp(430), Math.max(dp(240), Math.round(available * 0.56f)));
    }
    private String evidenceQualityText(double confidence) {
        if (confidence >= 0.78d) {
            return "清楚";
        }
        if (confidence >= 0.62d) {
            return "可用";
        }
        return "请核对";
    }
    private void renderKeyframes() {
        if (keyframeContainer == null || keyframeTitle == null || keyframeScroll == null) {
            return;
        }
        keyframeContainer.removeAllViews();
        List<SessionKeyframe> keyframes = keyframeSnapshot();
        if (keyframes.isEmpty()) {
            renderEmptyEvidence();
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
            renderEmptyEvidence();
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
                    + evidenceQualityText(primary.confidence));
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

    private void showEvidenceFrame(final SessionKeyframe keyframe) {
        if (keyframe == null || keyframe.bitmap == null || keyframe.bitmap.isRecycled()) {
            return;
        }
        if (evidenceDialog != null) {
            evidenceDialog.dismiss();
        }

        RecognitionEvidence.Region primary = keyframe.primaryRegion();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(4), dp(8), 0);

        TextView summary = new TextView(this);
        summary.setText(fieldLabel(primary.field) + " · 画面"
                + evidenceQualityText(primary.confidence)
                + "\n关闭后会回到刚才的识别结果，当前字段不会清空。");
        summary.setTextColor(Color.rgb(54, 65, 58));
        summary.setTextSize(15);
        summary.setLineSpacing(0f, 1.08f);
        summary.setPadding(dp(4), 0, dp(4), dp(10));
        content.addView(summary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout evidenceFrame = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.BLACK);
        image.setImageBitmap(keyframe.bitmap);
        evidenceFrame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        RecognitionGuideOverlay overlay = new RecognitionGuideOverlay(this);
        overlay.showEvidence(
                keyframe.evidence.regions,
                keyframe.evidence.width,
                keyframe.evidence.height
        );
        evidenceFrame.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        content.addView(evidenceFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                evidenceDialogHeight()
        ));

        evidenceDialog = new AlertDialog.Builder(this)
                .setTitle("识别依据")
                .setView(content)
                .setPositiveButton("返回识别结果", null)
                .create();
        evidenceDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                evidenceDialog = null;
            }
        });
        evidenceDialog.show();
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
        renderCameraHud();
        if (updateStatusText) {
            RecognitionEvidence.Region primary = keyframe.primaryRegion();
            statusBadge.setText("证据帧");
            statusText.setText(fieldLabel(primary.field) + " · 画面 "
                    + evidenceQualityText(primary.confidence));
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
        if (RecognitionEvidence.FIELD_BARCODE.equals(field)) {
            return "条码";
        }
        return "包装文字";
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

    private void updateStatus(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (videoReplayActive && videoExpectedFrames > 0 && videoAnalyzedFrames < videoExpectedFrames) {
            statusBadge.setText("分析中");
            statusText.setText("正在挑选清晰画面并合并识别字段。");
            return;
        }
        if (dateOnlyEntryMode) {
            if (snapshot != null && snapshot.hasStableDateCandidate()) {
                statusBadge.setText("日期已识别");
                statusText.setText("结果已跨帧稳定，请核对日期含义后填入。");
            } else if (snapshot != null
                    && snapshot.latestDateVote != null
                    && snapshot.latestDateVote.hasConflict) {
                statusBadge.setText("需继续确认");
                statusText.setText("看到多个日期，请保持画面稳定或只对准其中一处。");
            } else if (latestRawText.length() > 0) {
                statusBadge.setText("正在确认日期");
                statusText.setText("已看到文字，继续保持稳定以确认日期。");
            } else {
                statusBadge.setText("识别日期");
                statusText.setText("把生产日期、保质期或有效期放入识别框。");
            }
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

    private void showDiagnosticsDialog() {
        if (!isDebuggable()) {
            return;
        }
        if (diagnosticsDialog != null) {
            diagnosticsDialog.dismiss();
        }
        ScrollView scroll = new ScrollView(this);
        diagnosticsSummaryView = new TextView(this);
        diagnosticsSummaryView.setTextColor(Color.rgb(35, 42, 38));
        diagnosticsSummaryView.setTextSize(13);
        diagnosticsSummaryView.setTypeface(Typeface.MONOSPACE);
        diagnosticsSummaryView.setTextIsSelectable(true);
        diagnosticsSummaryView.setPadding(dp(16), dp(10), dp(16), dp(12));
        scroll.addView(diagnosticsSummaryView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        diagnosticsDialog = new AlertDialog.Builder(this)
                .setTitle("识别诊断（测试版）")
                .setView(scroll)
                .setNeutralButton("选择录屏", null)
                .setPositiveButton("分享日志", null)
                .setNegativeButton("关闭", null)
                .create();
        diagnosticsDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                diagnosticsDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                shareDiagnosticsLog();
                            }
                        });
                diagnosticsDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                diagnosticsDialog.dismiss();
                                startVideoReplayPicker();
                            }
                        });
                refreshDiagnosticsDialog();
            }
        });
        diagnosticsDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                diagnosticsDialog = null;
                diagnosticsSummaryView = null;
            }
        });
        diagnosticsDialog.show();
    }

    private void refreshDiagnosticsDialog() {
        final TextView summaryView = diagnosticsSummaryView;
        if (summaryView == null || diagnosticsDialog == null || !diagnosticsDialog.isShowing()) {
            return;
        }
        summaryView.setText(diagnostics.summary(SystemClock.elapsedRealtime()));
        summaryView.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshDiagnosticsDialog();
            }
        }, 750L);
    }

    private void shareDiagnosticsLog() {
        diagnosticEvent("log.share", "requested");
        persistDiagnosticsSnapshot("log.share");
        try {
            String transientReport = RecognitionDebugStore.isEnabled(this)
                    ? ""
                    : currentDiagnosticsReport("transient.share");
            File file = RecognitionDebugStore.buildShareFile(this, transientReport);
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, contentUri);
            share.putExtra(Intent.EXTRA_SUBJECT, "食期管家识别调试日志");
            share.setClipData(ClipData.newRawUri("识别调试日志", contentUri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享到 QQ、微信或其他应用"));
        } catch (IOException | IllegalArgumentException | ActivityNotFoundException error) {
            Toast.makeText(
                    this,
                    "诊断日志分享失败：" + FoodItem.cleanText(error.getMessage()),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void persistDiagnosticsSnapshot(String reason) {
        if (!RecognitionDebugStore.isEnabled(this)) {
            return;
        }
        try {
            RecognitionDebugStore.saveSession(
                    this,
                    diagnostics.sessionId(),
                    reason,
                    currentDiagnosticsReport(reason)
            );
        } catch (IOException error) {
            Log.w("ShiqiDiag", "persist failed: " + FoodItem.cleanText(error.getMessage()));
        }
    }

    private String currentDiagnosticsReport(String reason) {
        captureDiagnosticsState(reason);
        return diagnostics.report(
                SystemClock.elapsedRealtime(),
                diagnosticsEnvironment()
        ) + "\nraw_ocr\n" + FoodItem.cleanText(latestRawText);
    }

    private void captureDiagnosticsState(String reason) {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot == null
                ? stabilizer.snapshot()
                : latestSnapshot;
        Runtime runtime = Runtime.getRuntime();
        diagnostics.value("persist_reason", reason);
        diagnostics.value(
                "debug_capture",
                Boolean.toString(RecognitionDebugStore.isEnabled(this))
        );
        diagnostics.value("paddle_models_ready", Boolean.toString(paddleModelsReady));
        diagnostics.value("camera_bound", Boolean.toString(cameraBound));
        diagnostics.value("video_replay_active", Boolean.toString(videoReplayActive));
        diagnostics.value("date_only_mode", Boolean.toString(dateOnlyMode));
        diagnostics.value("barcode_only_mode", Boolean.toString(barcodeLookupOnly));
        diagnostics.value("selected_product_name", selectedPackagingName);
        diagnostics.value("stable_barcode", snapshot.stableBarcode);
        diagnostics.value(
                "stable_product_name",
                snapshot.bestPackagingNameForConfirmation()
        );
        FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
        diagnostics.value("stable_production_date", dateDraft.productionDate);
        diagnostics.value("stable_expiry_date", dateDraft.expiryDate);
        diagnostics.value(
                "runtime_memory_mb",
                (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
                        + "/" + runtime.maxMemory() / (1024L * 1024L)
        );
        diagnostics.value(
                "private_storage_free_mb",
                Long.toString(getFilesDir().getUsableSpace() / (1024L * 1024L))
        );
    }

    private String diagnosticsEnvironment() {
        String version = "unknown";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // Package metadata is best-effort diagnostics only.
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        boolean cameraPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        return "device=" + Build.MANUFACTURER + "/" + Build.MODEL
                + "; sdk=" + Build.VERSION.SDK_INT
                + "; abi=" + abi
                + "; app=" + version
                + "; package=" + getPackageName()
                + "; screen=" + width + "x" + height
                + "; density=" + getResources().getDisplayMetrics().densityDpi
                + "; orientation=" + getResources().getConfiguration().orientation
                + "; camera_permission=" + cameraPermission;
    }

    private void diagnosticEvent(String stage, String details) {
        if (!isDiagnosticsAvailable()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        diagnostics.event(now, stage, details);
        Log.i("ShiqiDiag", stage + " " + FoodItem.cleanText(details));
        if (RecognitionDebugStore.isEnabled(this)
                && now - lastDiagnosticsCheckpointAtMs >= 8000L) {
            lastDiagnosticsCheckpointAtMs = now;
            persistDiagnosticsSnapshot("checkpoint." + stage);
        }
    }

    private void finishWithCandidate() {
        UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot == null
                ? stabilizer.snapshot()
                : latestSnapshot;
        diagnosticEvent(
                "confirmation.continue",
                "partial=" + !snapshot.hasFillableCandidate()
                        + " dateOnly=" + dateOnlyMode
        );
        FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
        BarcodeProductInfo info = latestProductBarcode.equals(snapshot.stableBarcode) ? latestProductInfo : null;
        String packagingName = selectedPackagingName.length() > 0
                ? selectedPackagingName
                : snapshot.bestPackagingNameForConfirmation();
        String productName = productNameForResult(packagingName, info);
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
        persistDiagnosticsSnapshot("confirmation.continue");
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

    private boolean isDiagnosticsAvailable() {
        return isDebuggable() || RecognitionDebugStore.isEnabled(this);
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

    private static final class CameraFrameProbe {
        final LowContrastTextPreprocessor.FrameMetrics metrics;
        final List<PaddleTextDetectionEngine.TextRegion> regions;
        final boolean shouldRecognize;

        CameraFrameProbe(
                LowContrastTextPreprocessor.FrameMetrics metrics,
                List<PaddleTextDetectionEngine.TextRegion> regions,
                boolean shouldRecognize
        ) {
            this.metrics = metrics;
            this.regions = regions == null
                    ? Collections.<PaddleTextDetectionEngine.TextRegion>emptyList()
                    : new ArrayList<PaddleTextDetectionEngine.TextRegion>(regions);
            this.shouldRecognize = shouldRecognize;
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

    private static final class ScanFieldView {
        final LinearLayout root;
        final ImageView icon;
        final TextView status;

        ScanFieldView(LinearLayout root, ImageView icon, TextView status) {
            this.root = root;
            this.icon = icon;
            this.status = status;
        }
    }
}
