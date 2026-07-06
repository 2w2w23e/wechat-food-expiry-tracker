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

import java.io.InputStream;
import java.util.ArrayList;
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

    private static final int REQUEST_CAMERA_PERMISSION = 6201;
    private static final int REQUEST_VIDEO_REPLAY = 6202;
    private static final int REQUEST_IMAGE_RECOGNITION = 6203;
    private static final long ANALYZE_INTERVAL_MS = 420L;
    private static final long VIDEO_FRAME_INTERVAL_US = 420000L;
    private static final int VIDEO_MAX_FRAME_SIDE = 1280;

    private PreviewView previewView;
    private ImageView replayFrameView;
    private TextView statusBadge;
    private TextView statusText;
    private TextView barcodeValue;
    private TextView productValue;
    private TextView productionDateValue;
    private TextView shelfLifeValue;
    private TextView expiryDateValue;
    private TextView rawPreviewText;
    private Button fillButton;

    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private final UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer();
    private UnifiedRecognitionStabilizer.Snapshot latestSnapshot = stabilizer.snapshot();
    private BarcodeProductInfo latestProductInfo;
    private String latestProductBarcode = "";
    private String productLookupInFlightBarcode = "";
    private String productLookupError = "";
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
        window.setStatusBarColor(Color.rgb(17, 23, 19));
        window.setNavigationBarColor(Color.rgb(17, 23, 19));
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraExecutor = Executors.newSingleThreadExecutor();
        textRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
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
                .build());

        buildScreen();
        showInputChoiceState();

        Intent intent = getIntent();
        String qaVideoPath = intent == null ? "" : FoodItem.cleanText(intent.getStringExtra(EXTRA_QA_VIDEO_PATH));
        String qaImagePath = intent == null ? "" : FoodItem.cleanText(intent.getStringExtra(EXTRA_QA_IMAGE_PATH));
        if (isDebuggable() && qaVideoPath.length() > 0) {
            startVideoReplayPath(qaVideoPath);
        } else if (isDebuggable() && qaImagePath.length() > 0) {
            startImageRecognitionPath(qaImagePath);
        } else if (intent != null && intent.getBooleanExtra(EXTRA_START_VIDEO_REPLAY, false)) {
            startVideoReplayPicker();
        }
    }

    @Override
    protected void onDestroy() {
        stopVideoReplay();
        stopCamera();
        clearLastReplayFrame();
        if (textRecognizer != null) {
            textRecognizer.close();
            textRecognizer = null;
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
            barcodeScanner = null;
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

        root.addView(new RecognitionGuideOverlay(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(dp(16), dp(12), dp(16), dp(10));
        topPanel.setBackgroundColor(Color.argb(176, 14, 20, 17));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        topParams.gravity = Gravity.TOP;
        root.addView(topPanel, topParams);

        LinearLayout topActions = new LinearLayout(this);
        topActions.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.addView(topActions, matchWrap());

        Button closeButton = overlayButton("返回", Color.rgb(48, 60, 52));
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        topActions.addView(closeButton, fixed(72, 40));

        TextView title = new TextView(this);
        title.setText("智能识别");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        topActions.addView(title, weightWrap(1));

        statusBadge = new TextView(this);
        statusBadge.setText("待识别");
        statusBadge.setTextColor(Color.rgb(226, 246, 232));
        statusBadge.setTextSize(12);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusBadge.setBackground(rounded(Color.rgb(51, 92, 68), dp(8), Color.argb(80, 255, 255, 255)));
        topActions.addView(statusBadge, fixed(86, 34));

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(221, 234, 224));
        statusText.setTextSize(13);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(8), 0, 0);
        topPanel.addView(statusText, matchWrap());

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(dp(16), dp(12), dp(16), dp(16));
        bottomPanel.setBackgroundColor(Color.argb(226, 246, 249, 244));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bottomParams.gravity = Gravity.BOTTOM;
        root.addView(bottomPanel, bottomParams);

        LinearLayout resultHeader = new LinearLayout(this);
        resultHeader.setGravity(Gravity.CENTER_VERTICAL);
        bottomPanel.addView(resultHeader, matchWrap());

        TextView resultTitle = new TextView(this);
        resultTitle.setText("识别结果");
        resultTitle.setTextColor(Color.rgb(32, 42, 34));
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
        resultHeader.addView(rawButton, fixed(58, 36));

        bottomPanel.addView(resultRow("商品码", true), matchWrap());
        bottomPanel.addView(resultRow("商品名", false), matchWrap());
        bottomPanel.addView(resultRow("生产日期", false), matchWrap());
        bottomPanel.addView(resultRow("保质期", false), matchWrap());
        bottomPanel.addView(resultRow("最终日期", false), matchWrap());

        rawPreviewText = new TextView(this);
        rawPreviewText.setTextColor(Color.rgb(86, 99, 88));
        rawPreviewText.setTextSize(12);
        rawPreviewText.setMaxLines(2);
        rawPreviewText.setPadding(0, dp(6), 0, dp(8));
        bottomPanel.addView(rawPreviewText, matchWrap());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomPanel.addView(actionRow, fixedHeight(48));

        Button cameraButton = sourceButton("相机");
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ensureCameraPermission();
            }
        });
        actionRow.addView(cameraButton, withMargins(weightWrap(1), 0, 0, dp(6), 0));

        Button videoButton = sourceButton("视频");
        videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startVideoReplayPicker();
            }
        });
        actionRow.addView(videoButton, withMargins(weightWrap(1), 0, 0, dp(6), 0));

        Button imageButton = sourceButton("图片");
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startImagePicker();
            }
        });
        actionRow.addView(imageButton, withMargins(weightWrap(1), 0, 0, dp(6), 0));

        Button manualButton = sourceButton("手动");
        manualButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        actionRow.addView(manualButton, weightWrap(1));

        fillButton = overlayButton("填入新增表单", Color.rgb(63, 111, 83));
        fillButton.setEnabled(false);
        fillButton.setAlpha(0.45f);
        fillButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishWithCandidate();
            }
        });
        bottomPanel.addView(fillButton, withMargins(fixedHeight(48), 0, dp(10), 0, 0));

        setContentView(root);
    }

    private View resultRow(String label, boolean barcodeRow) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, 0);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(88, 101, 90));
        labelView.setTextSize(13);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(labelView, fixed(72, 28));

        TextView valueView = new TextView(this);
        valueView.setText("待识别");
        valueView.setTextColor(Color.rgb(30, 40, 32));
        valueView.setTextSize(14);
        valueView.setSingleLine(false);
        row.addView(valueView, weightWrap(1));

        if (barcodeRow) {
            barcodeValue = valueView;
        } else if (productValue == null) {
            productValue = valueView;
        } else if (productionDateValue == null) {
            productionDateValue = valueView;
        } else if (shelfLifeValue == null) {
            shelfLifeValue = valueView;
        } else {
            expiryDateValue = valueView;
        }
        return row;
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
        statusText.setText("选择相机、视频或图片。系统会同时寻找条码和包装文字。");
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
            statusText.setText("未获得相机权限，可使用视频、图片或返回后手动新增。");
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

    private void startImagePicker() {
        stopCamera();
        stopVideoReplay();
        resetRecognitionState();
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
        prepareReplaySurface("视频样本");
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
        statusText.setText("正在模拟实时识别，条码和日期候选都需要确认后才会进入表单。");
    }

    private void replayVideoFrames(Uri uri, String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int analyzedFrames = 0;
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
                analyzeBitmapFrame(scaled, false);
                analyzedFrames++;
                SystemClock.sleep(Math.max(110L, ANALYZE_INTERVAL_MS / 2L));
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
                    statusBadge.setText("读取失败");
                    statusText.setText("视频模拟识别失败，请换一个更清晰的本地视频或图片。");
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
                analyzeDecodedImage(BitmapFactory.decodeFile(path));
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
            InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(uri);
                analyzeDecodedImage(BitmapFactory.decodeStream(inputStream));
            } catch (final Exception error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusBadge.setText("读取失败");
                        statusText.setText("图片读取失败，请换一张更清晰的图片。");
                    }
                });
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void analyzeDecodedImage(Bitmap bitmap) {
        try {
            if (bitmap == null) {
                throw new IllegalArgumentException("bad image");
            }
            Bitmap scaled = scaleReplayFrame(bitmap);
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
        if (bitmap == null || textRecognizer == null || barcodeScanner == null) {
            return;
        }
        String barcode = "";
        StringBuilder rawText = new StringBuilder();
        List<FrameVariant> variants = buildFrameVariants(bitmap);
        try {
            for (FrameVariant variant : variants) {
                InputImage image = InputImage.fromBitmap(variant.bitmap, variant.rotationDegrees);
                if (variant.scanBarcode && barcode.length() == 0) {
                    Task<List<Barcode>> barcodeTask = barcodeScanner.process(image);
                    Tasks.await(barcodeTask, 8, TimeUnit.SECONDS);
                    if (barcodeTask.isSuccessful()) {
                        barcode = extractProductBarcode(barcodeTask.getResult());
                    }
                }
                if (variant.scanText) {
                    Task<Text> textTask = textRecognizer.process(image);
                    Tasks.await(textTask, 8, TimeUnit.SECONDS);
                    if (textTask.isSuccessful() && textTask.getResult() != null) {
                        appendRecognizedText(rawText, textTask.getResult().getText());
                    }
                }
            }
        } finally {
            recycleVariants(variants);
        }

        handleRecognitionResult(
                barcode,
                rawText.toString(),
                singleFrameConfirmation
        );
    }

    private List<FrameVariant> buildFrameVariants(Bitmap bitmap) {
        List<FrameVariant> variants = new ArrayList<FrameVariant>();
        variants.add(new FrameVariant(bitmap, 0, true, false, false));
        addFrameCrop(variants, bitmap, 0.04f, 0.13f, 0.92f, 0.50f, 1100, 0, true, true);
        addFrameCrop(variants, bitmap, 0.06f, 0.27f, 0.88f, 0.35f, 1100, 0, true, true);
        addFrameCrop(variants, bitmap, 0.16f, 0.31f, 0.72f, 0.25f, 1000, 0, true, true);
        addFrameCrop(variants, bitmap, 0.24f, 0.37f, 0.46f, 0.22f, 1000, 0, true, false);
        addFrameCrop(variants, bitmap, 0.24f, 0.37f, 0.46f, 0.22f, 1000, 90, true, false);
        addFrameCrop(variants, bitmap, 0.24f, 0.37f, 0.46f, 0.22f, 1000, 270, true, false);
        return variants;
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
            boolean scanText
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
        variants.add(new FrameVariant(prepared, rotationDegrees, scanBarcode, scanText, true));
    }

    private Bitmap upscaleIfNeeded(Bitmap bitmap, int minLargestSide) {
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest >= minLargestSide) {
            return bitmap;
        }
        float scale = Math.min(3.0f, minLargestSide / (float) largest);
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
        UnifiedRecognitionStabilizer.Snapshot snapshot = stabilizer.snapshot();
        if (snapshot.hasFillableCandidate()) {
            statusText.setText("视频模拟完成，候选已可用；确认前不会保存到食品列表。");
        } else if (analyzedFrames > 0) {
            statusText.setText("视频模拟完成，但候选还不稳定，可换更清晰视频或图片。");
        } else {
            statusText.setText("视频没有可用画面，请换一个更清晰的本地视频。");
        }
    }

    private void stopVideoReplay() {
        videoReplayActive = false;
    }

    private void resetRecognitionState() {
        stabilizer.reset();
        latestSnapshot = stabilizer.snapshot();
        latestRawText = "";
        analysisInFlight = false;
        lastAnalyzeAt = 0L;
        latestProductInfo = null;
        latestProductBarcode = "";
        productLookupInFlightBarcode = "";
        productLookupError = "";
        if (fillButton != null) {
            fillButton.setEnabled(false);
            fillButton.setAlpha(0.45f);
        }
        updateResultUi(latestSnapshot);
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
                        showCameraUnavailable("未找到可用后置相机，可使用视频、图片或手动新增。");
                        return;
                    }
                    bindCameraUseCases(cameraProvider);
                    cameraBound = true;
                    statusBadge.setText("相机");
                    statusText.setText("正在同时寻找条码和日期文字，请让包装信息进入识别框。");
                } catch (Exception error) {
                    showCameraUnavailable("相机启动失败，可使用视频、图片或手动新增。");
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
        if (mediaImage == null || textRecognizer == null || barcodeScanner == null) {
            analysisInFlight = false;
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );
        final Task<List<Barcode>> barcodeTask = barcodeScanner.process(inputImage);
        final Task<Text> textTask = textRecognizer.process(inputImage);
        Tasks.whenAllComplete(barcodeTask, textTask)
                .addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<List<Task<?>>>() {
                    @Override
                    public void onComplete(Task<List<Task<?>>> task) {
                        try {
                            List<Barcode> barcodes = barcodeTask.isSuccessful() ? barcodeTask.getResult() : null;
                            Text text = textTask.isSuccessful() ? textTask.getResult() : null;
                            handleRecognitionResult(
                                    extractProductBarcode(barcodes),
                                    text == null ? "" : text.getText(),
                                    false
                            );
                        } catch (Exception ignored) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusText.setText("本帧识别失败，继续对准包装信息。");
                                }
                            });
                        } finally {
                            analysisInFlight = false;
                            imageProxy.close();
                        }
                    }
                });
    }

    private String extractProductBarcode(List<Barcode> barcodes) {
        if (barcodes == null) {
            return "";
        }
        for (Barcode barcode : barcodes) {
            if (barcode == null) {
                continue;
            }
            String productCode = BarcodeUtils.extractProductCode(barcode.getRawValue());
            if (BarcodeUtils.isSupportedProductCode(productCode)) {
                return productCode;
            }
            productCode = BarcodeUtils.extractProductCode(barcode.getDisplayValue());
            if (BarcodeUtils.isSupportedProductCode(productCode)) {
                return productCode;
            }
        }
        return "";
    }

    private void handleRecognitionResult(String barcode, String rawText, boolean singleFrameConfirmation) {
        String cleanedText = FoodItem.cleanText(rawText);
        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
            barcode = RecognitionTextCleaner.extractProductCodeFromOcr(cleanedText);
        }
        DateOcrParser.Result parsed = DateOcrParser.parse(cleanedText);
        latestSnapshot = stabilizer.addFrame(barcode, parsed, cleanedText, singleFrameConfirmation);
        latestRawText = cleanedText;
        final UnifiedRecognitionStabilizer.Snapshot snapshot = latestSnapshot;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateResultUi(snapshot);
                startProductLookupIfNeeded(snapshot);
            }
        });
    }

    private void startProductLookupIfNeeded(final UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot == null || !snapshot.hasStableBarcode()) {
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
        updateResultUi(snapshot);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BarcodeProductInfo info = BarcodeLookupClient.query(barcode);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
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
        latestSnapshot = snapshot;

        if (barcodeValue != null) {
            barcodeValue.setText(snapshot.hasStableBarcode()
                    ? snapshot.stableBarcode + "（已锁定）"
                    : "未发现稳定条码");
        }

        if (productValue != null) {
            productValue.setText(productDisplayText(snapshot));
        }

        FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
        if (productionDateValue != null) {
            productionDateValue.setText(dateDraft.productionDate.length() > 0
                    ? dateDraft.productionDate + "（已稳定）"
                    : "待确认");
        }
        if (shelfLifeValue != null) {
            shelfLifeValue.setText(dateDraft.shelfLifeValue != null
                    ? dateDraft.shelfLifeValue + FoodData.shelfLifeUnitLabel(dateDraft.shelfLifeUnit)
                    : "待确认");
        }
        if (expiryDateValue != null) {
            expiryDateValue.setText(dateDraft.expiryDate.length() > 0
                    ? dateDraft.expiryDate
                    : "待确认");
        }

        String rawSnippet = snippet(latestRawText, 96);
        rawPreviewText.setText("原文片段：" + rawSnippet);

        boolean fillable = snapshot.hasFillableCandidate();
        fillButton.setEnabled(fillable);
        fillButton.setAlpha(fillable ? 1f : 0.45f);

        updateStatus(snapshot);
    }

    private String productDisplayText(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot != null && snapshot.hasStablePackagingName() && !snapshot.hasStableBarcode()) {
            return snapshot.stablePackagingName + "（包装文字候选）";
        }
        if (latestProductInfo != null && latestProductInfo.found) {
            String name = latestProductInfo.displayName();
            return name.length() > 0 ? name + "（条码查询）" : "已查询到商品";
        }
        if (snapshot.hasStableBarcode() && productLookupInFlightBarcode.length() > 0) {
            return "正在查询商品信息";
        }
        if (snapshot.hasStableBarcode() && productLookupError.length() > 0) {
            return "未完成查询，将使用条码候选名";
        }
        if (snapshot.hasStableBarcode() && latestProductBarcode.length() > 0) {
            return "未查到商品名，将使用条码候选名";
        }
        return "等待条码或包装文字";
    }

    private void updateStatus(UnifiedRecognitionStabilizer.Snapshot snapshot) {
        if (snapshot != null
                && snapshot.hasStablePackagingName()
                && !snapshot.hasStableBarcode()
                && !snapshot.hasStableDateCandidate()) {
            statusBadge.setText("文字已锁");
            statusText.setText("已锁定包装文字候选；可先填入表单，确认前不会保存。");
            return;
        }
        if (snapshot.hasStableBarcode() && snapshot.hasStableDateCandidate()) {
            statusBadge.setText("已稳定");
            statusText.setText("已锁定条码和日期候选，可确认后填入新增表单。");
        } else if (snapshot.hasStableBarcode()) {
            statusBadge.setText("条码已锁");
            statusText.setText("已锁定条码；可继续找日期，也可以先填入表单。");
        } else if (snapshot.hasStableDateCandidate()) {
            statusBadge.setText("日期已稳");
            statusText.setText("日期候选已稳定；未发现条码，可确认后填入表单。");
        } else if (snapshot.latestDateVote != null && snapshot.latestDateVote.hasConflict) {
            statusBadge.setText("需确认");
            statusText.setText("出现多个日期候选，继续保持清晰或改用手动修正。");
        } else if (latestRawText.length() > 0) {
            statusBadge.setText("识别中");
            statusText.setText("已看到包装文字，继续保持稳定直到候选锁定。");
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
        if (!snapshot.hasFillableCandidate()) {
            Toast.makeText(this, "还没有可填入字段", Toast.LENGTH_SHORT).show();
            return;
        }

        FoodItem dateDraft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);
        BarcodeProductInfo info = latestProductInfo;
        String productName = info != null && info.found ? info.displayName() : snapshot.stablePackagingName;
        String productCategory = info != null && info.found ? BarcodeCategoryClassifier.inferCategory(info) : "";
        String productNotes = info != null && info.found ? info.notes() : "";

        Intent result = new Intent();
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
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rounded(color, dp(8), Color.argb(90, 255, 255, 255)));
        return button;
    }

    private Button sourceButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(35, 49, 39));
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rounded(Color.rgb(231, 238, 228), dp(8), Color.rgb(197, 210, 196)));
        return button;
    }

    private Button plainButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(63, 111, 83));
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rounded(Color.rgb(239, 245, 236), dp(8), Color.rgb(205, 216, 203)));
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

    private static final class FrameVariant {
        final Bitmap bitmap;
        final int rotationDegrees;
        final boolean scanBarcode;
        final boolean scanText;
        final boolean recycleBitmap;

        FrameVariant(
                Bitmap bitmap,
                int rotationDegrees,
                boolean scanBarcode,
                boolean scanText,
                boolean recycleBitmap
        ) {
            this.bitmap = bitmap;
            this.rotationDegrees = rotationDegrees;
            this.scanBarcode = scanBarcode;
            this.scanText = scanText;
            this.recycleBitmap = recycleBitmap;
        }
    }

    private final class RecognitionGuideOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        RecognitionGuideOverlay(ComponentActivity context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            RectF frame = new RectF(
                    width * 0.07f,
                    height * 0.28f,
                    width * 0.93f,
                    height * 0.61f
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

        private void drawCorner(Canvas canvas, float x, float y, float length, boolean left, boolean top) {
            float horizontalEnd = left ? x + length : x - length;
            float verticalEnd = top ? y + length : y - length;
            canvas.drawLine(x, y, horizontalEnd, y, paint);
            canvas.drawLine(x, y, x, verticalEnd, paint);
        }
    }
}
