package com.shiqi.expirytracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class BarcodeScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final int REQUEST_CAMERA_PERMISSION = 5301;
    private static final int REQUEST_GALLERY_PERMISSION = 5302;
    private static final int REQUEST_GALLERY_IMAGE = 5303;
    private static final long DECODE_INTERVAL_MS = 90L;

    private SurfaceView surfaceView;
    private TextView statusText;
    private TextView zoomText;
    private Button torchButton;
    private Button zoomInButton;
    private Button zoomOutButton;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private int cameraId = -1;
    private int previewWidth;
    private int previewHeight;
    private int previewBufferSize;
    private int maxZoom;
    private int currentZoom;
    private int pinchStartZoom;
    private long lastDecodeAt;
    private float pinchStartDistance;
    private List<Integer> zoomRatios;
    private boolean surfaceReady;
    private boolean torchOn;
    private boolean autoFocusMode;
    private volatile boolean decodeInFlight;
    private volatile boolean decodeFinished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildScreen();
        ensureCameraPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraIfReady();
    }

    @Override
    protected void onPause() {
        releaseCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        super.onDestroy();
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleZoomTouch(event);
            }
        });

        surfaceView = new SurfaceView(this);
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleZoomTouch(event);
            }
        });
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(new ScannerOverlayView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(dp(18), dp(14), dp(18), dp(14));
        topPanel.setBackgroundColor(Color.argb(168, 0, 0, 0));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        topParams.gravity = Gravity.TOP;
        root.addView(topPanel, topParams);

        LinearLayout topActions = new LinearLayout(this);
        topActions.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.addView(topActions, matchWrap());

        Button closeButton = overlayButton("返回", 74);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        topActions.addView(closeButton, wrapFixed(74, 42));

        TextView title = new TextView(this);
        title.setText("扫码识别商品");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        topActions.addView(title, weightWrap(1));

        torchButton = overlayButton("补光", 74);
        torchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleTorch();
            }
        });
        topActions.addView(torchButton, wrapFixed(74, 42));

        statusText = new TextView(this);
        statusText.setText("将商品条码或中国物品编码二维码放入框内");
        statusText.setTextColor(Color.rgb(220, 238, 224));
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(10), 0, 0);
        topPanel.addView(statusText, matchWrap());

        LinearLayout zoomPanel = new LinearLayout(this);
        zoomPanel.setOrientation(LinearLayout.VERTICAL);
        zoomPanel.setGravity(Gravity.CENTER);
        zoomPanel.setPadding(dp(6), dp(6), dp(6), dp(6));
        zoomPanel.setBackground(rounded(Color.argb(146, 0, 0, 0), dp(22), Color.argb(80, 255, 255, 255)));
        FrameLayout.LayoutParams zoomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        zoomParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        zoomParams.setMargins(0, 0, dp(14), 0);
        root.addView(zoomPanel, zoomParams);

        zoomInButton = overlayButton("+", 44);
        zoomInButton.setTextSize(22);
        zoomInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adjustZoom(1);
            }
        });
        zoomPanel.addView(zoomInButton, wrapFixed(44, 42));

        zoomText = new TextView(this);
        zoomText.setText("1.0x");
        zoomText.setTextColor(Color.WHITE);
        zoomText.setTextSize(12);
        zoomText.setGravity(Gravity.CENTER);
        zoomText.setPadding(0, dp(5), 0, dp(5));
        zoomPanel.addView(zoomText, wrapFixed(44, 34));

        zoomOutButton = overlayButton("-", 44);
        zoomOutButton.setTextSize(22);
        zoomOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adjustZoom(-1);
            }
        });
        zoomPanel.addView(zoomOutButton, wrapFixed(44, 42));

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(dp(18), dp(12), dp(18), dp(22));
        bottomPanel.setBackgroundColor(Color.argb(160, 0, 0, 0));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bottomParams.gravity = Gravity.BOTTOM;
        root.addView(bottomPanel, bottomParams);

        TextView tip = new TextView(this);
        tip.setText("双指缩放或点 + 放大，让条码尽量铺满取景框");
        tip.setTextColor(Color.rgb(231, 238, 231));
        tip.setTextSize(13);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, 0, 0, dp(10));
        bottomPanel.addView(tip, matchWrap());

        LinearLayout bottomActions = new LinearLayout(this);
        bottomActions.setGravity(Gravity.CENTER_VERTICAL);
        bottomPanel.addView(bottomActions, fixedHeight(52));

        Button manualButton = overlayButton("输入条码", 112);
        manualButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showManualBarcodeDialog();
            }
        });
        bottomActions.addView(manualButton, wrapFixed(112, 48));

        SpaceView spacer = new SpaceView(this);
        bottomActions.addView(spacer, weightedSpacer());

        Button galleryButton = overlayButton("图库", 92);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGalleryWithPermission();
            }
        });
        bottomActions.addView(galleryButton, wrapFixed(92, 48));

        setContentView(root);
    }

    private void ensureCameraPermission() {
        if (hasCameraPermission()) {
            startCameraIfReady();
            return;
        }
        requestPermissions(new String[] { "android.permission.CAMERA" }, REQUEST_CAMERA_PERMISSION);
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission("android.permission.CAMERA") == PackageManager.PERMISSION_GRANTED;
    }

    private void startCameraIfReady() {
        if (!hasCameraPermission() || surfaceHolder == null || !surfaceReady || camera != null) {
            return;
        }

        cameraId = findBackCameraId();
        if (cameraId < 0) {
            statusText.setText("未找到后置相机，可从图库选择图片或手动输入");
            return;
        }

        try {
            camera = Camera.open(cameraId);
            configureCamera(camera);
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallbackWithBuffer(this);
            addPreviewBuffers(camera);
            camera.startPreview();
            if (autoFocusMode) {
                scheduleAutoFocus();
            }
            statusText.setText("将商品条码或中国物品编码二维码放入框内");
        } catch (Exception exception) {
            releaseCamera();
            statusText.setText("相机启动失败，可从图库选择图片或手动输入");
        }
    }

    private int findBackCameraId() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int count = Camera.getNumberOfCameras();
        for (int index = 0; index < count; index++) {
            Camera.getCameraInfo(index, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return index;
            }
        }
        return count > 0 ? 0 : -1;
    }

    private void configureCamera(Camera targetCamera) {
        Camera.Parameters parameters = targetCamera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21);

        Camera.Size size = choosePreviewSize(parameters.getSupportedPreviewSizes());
        if (size != null) {
            parameters.setPreviewSize(size.width, size.height);
            previewWidth = size.width;
            previewHeight = size.height;
        }

        autoFocusMode = false;
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null) {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                autoFocusMode = true;
            }
        }

        if (parameters.isZoomSupported()) {
            maxZoom = parameters.getMaxZoom();
            zoomRatios = parameters.getZoomRatios();
            if (maxZoom > 0) {
                int preferredZoom = currentZoom > 0
                        ? Math.min(currentZoom, maxZoom)
                        : Math.min(maxZoom, Math.max(1, maxZoom / 5));
                parameters.setZoom(preferredZoom);
                currentZoom = preferredZoom;
            }
        } else {
            maxZoom = 0;
            currentZoom = 0;
            zoomRatios = null;
        }

        applyTorchMode(parameters);
        targetCamera.setParameters(parameters);
        targetCamera.setDisplayOrientation(cameraDisplayOrientation(cameraId));
        updateTorchButton();
        updateZoomControls();
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }

        Camera.Size best = null;
        int bestArea = 0;
        for (Camera.Size size : sizes) {
            int area = size.width * size.height;
            if (size.width <= 1920 && size.height <= 1080 && area > bestArea) {
                best = size;
                bestArea = area;
            }
        }
        return best == null ? sizes.get(0) : best;
    }

    private void addPreviewBuffers(Camera targetCamera) {
        if (previewWidth <= 0 || previewHeight <= 0) {
            return;
        }

        int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
        previewBufferSize = previewWidth * previewHeight * bitsPerPixel / 8;
        targetCamera.addCallbackBuffer(new byte[previewBufferSize]);
        targetCamera.addCallbackBuffer(new byte[previewBufferSize]);
    }

    private int cameraDisplayOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        if (rotation == android.view.Surface.ROTATION_90) {
            degrees = 90;
        } else if (rotation == android.view.Surface.ROTATION_180) {
            degrees = 180;
        } else if (rotation == android.view.Surface.ROTATION_270) {
            degrees = 270;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private void scheduleAutoFocus() {
        if (camera == null || !autoFocusMode || decodeFinished) {
            return;
        }

        try {
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera sourceCamera) {
                    surfaceView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scheduleAutoFocus();
                        }
                    }, 1400L);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void toggleTorch() {
        if (camera == null) {
            toast("相机还未准备好");
            return;
        }

        try {
            torchOn = !torchOn;
            Camera.Parameters parameters = camera.getParameters();
            applyTorchMode(parameters);
            camera.setParameters(parameters);
            updateTorchButton();
        } catch (Exception exception) {
            torchOn = false;
            updateTorchButton();
            toast("当前设备不支持补光");
        }
    }

    private void applyTorchMode(Camera.Parameters parameters) {
        List<String> modes = parameters.getSupportedFlashModes();
        if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            torchOn = false;
            return;
        }
        parameters.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
    }

    private void updateTorchButton() {
        if (torchButton == null) {
            return;
        }
        torchButton.setText(torchOn ? "关灯" : "补光");
    }

    private boolean handleZoomTouch(MotionEvent event) {
        if (event == null || maxZoom <= 0) {
            return false;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            pinchStartDistance = pointerDistance(event);
            pinchStartZoom = currentZoom;
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2 && pinchStartDistance > dp(12)) {
            float distance = pointerDistance(event);
            float scale = distance / pinchStartDistance;
            int targetZoom = pinchStartZoom + Math.round((scale - 1f) * maxZoom * 0.75f);
            applyZoom(targetZoom);
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
            pinchStartDistance = 0f;
        }
        return false;
    }

    private float pointerDistance(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void adjustZoom(int direction) {
        if (maxZoom <= 0) {
            toast("当前设备不支持缩放");
            return;
        }
        int step = Math.max(1, Math.round(maxZoom / 12f));
        applyZoom(currentZoom + direction * step);
    }

    private void applyZoom(int targetZoom) {
        if (camera == null || maxZoom <= 0) {
            updateZoomControls();
            return;
        }

        int zoom = Math.max(0, Math.min(maxZoom, targetZoom));
        if (zoom == currentZoom) {
            updateZoomControls();
            return;
        }

        try {
            Camera.Parameters parameters = camera.getParameters();
            if (!parameters.isZoomSupported()) {
                maxZoom = 0;
                currentZoom = 0;
                zoomRatios = null;
                updateZoomControls();
                return;
            }
            parameters.setZoom(zoom);
            camera.setParameters(parameters);
            currentZoom = zoom;
        } catch (Exception ignored) {
        }
        updateZoomControls();
    }

    private void updateZoomControls() {
        boolean supported = maxZoom > 0;
        if (zoomInButton != null) {
            zoomInButton.setEnabled(supported && currentZoom < maxZoom);
            zoomInButton.setAlpha(supported && currentZoom < maxZoom ? 1f : 0.45f);
        }
        if (zoomOutButton != null) {
            zoomOutButton.setEnabled(supported && currentZoom > 0);
            zoomOutButton.setAlpha(supported && currentZoom > 0 ? 1f : 0.45f);
        }
        if (zoomText != null) {
            zoomText.setText(supported ? zoomLabel() : "1.0x");
            zoomText.setAlpha(supported ? 1f : 0.45f);
        }
    }

    private String zoomLabel() {
        if (zoomRatios != null && currentZoom >= 0 && currentZoom < zoomRatios.size()) {
            return String.format(Locale.US, "%.1fx", zoomRatios.get(currentZoom) / 100f);
        }
        float fallback = 1f + (maxZoom <= 0 ? 0f : (2.5f * currentZoom / maxZoom));
        return String.format(Locale.US, "%.1fx", fallback);
    }

    private void releaseCamera() {
        if (camera == null) {
            return;
        }

        try {
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
        } catch (Exception ignored) {
        }

        camera.release();
        camera = null;
        decodeInFlight = false;
        updateZoomControls();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        surfaceReady = true;
        startCameraIfReady();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceHolder = holder;
        surfaceReady = true;
        releaseCamera();
        startCameraIfReady();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseCamera();
    }

    @Override
    public void onPreviewFrame(final byte[] data, Camera sourceCamera) {
        if (decodeFinished || data == null || previewWidth <= 0 || previewHeight <= 0) {
            requeuePreviewBuffer(data);
            return;
        }

        long now = System.currentTimeMillis();
        if (decodeInFlight || now - lastDecodeAt < DECODE_INTERVAL_MS) {
            requeuePreviewBuffer(data);
            return;
        }

        lastDecodeAt = now;
        decodeInFlight = true;
        final int width = previewWidth;
        final int height = previewHeight;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String barcode = BarcodeImageDecoder.decodePreviewFrame(data, width, height);
                    if (BarcodeUtils.isSupportedProductCode(barcode)) {
                        finishWithBarcode(barcode);
                    }
                } finally {
                    decodeInFlight = false;
                    if (!decodeFinished) {
                        requeuePreviewBuffer(data);
                    }
                }
            }
        }).start();
    }

    private void requeuePreviewBuffer(byte[] data) {
        if (data == null || camera == null || decodeFinished) {
            return;
        }
        try {
            camera.addCallbackBuffer(data);
        } catch (Exception ignored) {
        }
    }

    private void openGalleryWithPermission() {
        if (hasGalleryPermission()) {
            openGalleryPicker();
            return;
        }
        requestPermissions(galleryPermissions(), REQUEST_GALLERY_PERMISSION);
    }

    private boolean hasGalleryPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission("android.permission.READ_MEDIA_IMAGES") == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission("android.permission.READ_MEDIA_IMAGES") == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
    }

    private String[] galleryPermissions() {
        if (Build.VERSION.SDK_INT >= 34) {
            return new String[] {
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
            };
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[] { "android.permission.READ_MEDIA_IMAGES" };
        }
        return new String[] { "android.permission.READ_EXTERNAL_STORAGE" };
    }

    private void openGalleryPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_GALLERY_IMAGE);
        } catch (ActivityNotFoundException exception) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            try {
                startActivityForResult(Intent.createChooser(fallback, "选择条码图片"), REQUEST_GALLERY_IMAGE);
            } catch (ActivityNotFoundException ignored) {
                toast("没有找到图库应用");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (isAnyPermissionGranted(grantResults)) {
                startCameraIfReady();
            } else {
                statusText.setText("需要相机权限才能实时扫码，可从图库选择图片或手动输入");
            }
            return;
        }

        if (requestCode == REQUEST_GALLERY_PERMISSION) {
            if (isAnyPermissionGranted(grantResults)) {
                openGalleryPicker();
            } else {
                toast("未获得图库权限");
            }
        }
    }

    private boolean isAnyPermissionGranted(int[] grantResults) {
        if (grantResults == null) {
            return false;
        }
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_GALLERY_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Bitmap bitmap = null;
        try {
            bitmap = loadScaledBitmap(data.getData(), 2400);
            String barcode = BarcodeImageDecoder.decodeProductBarcode(bitmap);
            if (BarcodeUtils.isSupportedProductCode(barcode)) {
                finishWithBarcode(barcode);
            } else {
                toast("没有识别到商品条码，请换一张更清晰的图片");
            }
        } catch (Exception exception) {
            toast("图片读取失败");
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private Bitmap loadScaledBitmap(Uri uri, int maxSize) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream boundsStream = getContentResolver().openInputStream(uri);
        if (boundsStream != null) {
            try {
                BitmapFactory.decodeStream(boundsStream, null, bounds);
            } finally {
                boundsStream.close();
            }
        }

        int sampleSize = 1;
        int largestSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (largestSide / sampleSize > maxSize) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        InputStream imageStream = getContentResolver().openInputStream(uri);
        if (imageStream == null) {
            throw new IOException("image stream is empty");
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, decodeOptions);
            if (bitmap == null) {
                throw new IOException("image decode failed");
            }
            return bitmap;
        } finally {
            imageStream.close();
        }
    }

    private void showManualBarcodeDialog() {
        final EditText input = new EditText(this);
        input.setHint("输入包装上的 8/12/13/14 位商品条码");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("输入商品条码")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("查询", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String barcode = BarcodeUtils.extractProductCode(input.getText().toString());
                        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
                            toast("条码格式不正确，请检查数字和校验位");
                            return;
                        }
                        dialog.dismiss();
                        finishWithBarcode(barcode);
                    }
                });
            }
        });
        dialog.show();
    }

    private void finishWithBarcode(final String barcode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (decodeFinished) {
                    return;
                }
                decodeFinished = true;
                releaseCamera();
                Intent result = new Intent();
                result.putExtra("SCAN_RESULT", barcode);
                setResult(RESULT_OK, result);
                finish();
            }
        });
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
        button.setBackground(rounded(Color.argb(214, 63, 111, 83), dp(8), Color.argb(120, 255, 255, 255)));
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

    private LinearLayout.LayoutParams weightedSpacer() {
        return new LinearLayout.LayoutParams(0, dp(1), 1);
    }

    private LinearLayout.LayoutParams wrapFixed(int widthDp, int heightDp) {
        return new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp));
    }

    private LinearLayout.LayoutParams fixedHeight(int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class ScannerOverlayView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long startTime = System.currentTimeMillis();

        ScannerOverlayView(Activity context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            RectF frame = scanFrame(width, height);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(108, 0, 0, 0));
            canvas.drawRect(0, 0, width, frame.top, paint);
            canvas.drawRect(0, frame.bottom, width, height, paint);
            canvas.drawRect(0, frame.top, frame.left, frame.bottom, paint);
            canvas.drawRect(frame.right, frame.top, width, frame.bottom, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(160, 255, 255, 255));
            canvas.drawRoundRect(frame, dp(12), dp(12), paint);

            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.rgb(126, 231, 164));
            float corner = dp(34);
            drawCorner(canvas, frame.left, frame.top, corner, true, true);
            drawCorner(canvas, frame.right, frame.top, corner, false, true);
            drawCorner(canvas, frame.left, frame.bottom, corner, true, false);
            drawCorner(canvas, frame.right, frame.bottom, corner, false, false);

            float progress = ((System.currentTimeMillis() - startTime) % 1500L) / 1500f;
            float scanY = frame.top + frame.height() * progress;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(136, 224, 169));
            canvas.drawLine(frame.left + dp(20), scanY, frame.right - dp(20), scanY, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(46, 136, 224, 169));
            canvas.drawRect(frame.left + dp(18), scanY - dp(10), frame.right - dp(18), scanY + dp(10), paint);

            postInvalidateDelayed(16L);
        }

        private RectF scanFrame(int width, int height) {
            return new RectF(
                    width * 0.08f,
                    height * 0.30f,
                    width * 0.92f,
                    height * 0.68f
            );
        }

        private void drawCorner(Canvas canvas, float x, float y, float length, boolean left, boolean top) {
            float horizontalEnd = left ? x + length : x - length;
            float verticalEnd = top ? y + length : y - length;
            canvas.drawLine(x, y, horizontalEnd, y, paint);
            canvas.drawLine(x, y, x, verticalEnd, paint);
        }
    }

    private static final class SpaceView extends View {
        SpaceView(Activity context) {
            super(context);
        }
    }
}
