package com.shiqi.expirytracker;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

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

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

final class CameraXFrameSource implements AutoCloseable {
    enum FocusStatus {
        STARTED,
        SUCCESS,
        FAILED,
        UNSUPPORTED,
        SUPERSEDED,
        CONTINUOUS
    }

    static final class FocusEvent {
        final FocusStatus status;
        final long requestId;
        final float x;
        final float y;
        final boolean announced;
        final boolean automaticRecovery;
        final long elapsedMs;
        final Throwable error;

        FocusEvent(
                FocusStatus status,
                long requestId,
                float x,
                float y,
                boolean announced,
                boolean automaticRecovery,
                long elapsedMs,
                Throwable error
        ) {
            this.status = status;
            this.requestId = requestId;
            this.x = x;
            this.y = y;
            this.announced = announced;
            this.automaticRecovery = automaticRecovery;
            this.elapsedMs = elapsedMs;
            this.error = error;
        }
    }

    interface Listener {
        boolean shouldAcquireFrame(long nowMs);

        void onFrame(Bitmap frame, long timestampUs);

        void onCameraReady(boolean flashAvailable);

        void onCameraUnavailable(String userMessage, Throwable error);

        void onFrameError(Throwable error);

        void onFocusEvent(FocusEvent event);
    }

    private final ComponentActivity owner;
    private final PreviewView previewView;
    private final Executor analyzerExecutor;
    private final Executor mainExecutor;
    private final Listener listener;

    private volatile int sessionId;
    private volatile boolean bound;
    private volatile boolean focusInFlight;
    private ProcessCameraProvider provider;
    private Camera camera;
    private long latestFocusRequestId;

    CameraXFrameSource(
            ComponentActivity owner,
            PreviewView previewView,
            Executor analyzerExecutor,
            Executor mainExecutor,
            Listener listener
    ) {
        if (owner == null
                || previewView == null
                || analyzerExecutor == null
                || mainExecutor == null
                || listener == null) {
            throw new IllegalArgumentException("camera source dependencies are required");
        }
        this.owner = owner;
        this.previewView = previewView;
        this.analyzerExecutor = analyzerExecutor;
        this.mainExecutor = mainExecutor;
        this.listener = listener;
    }

    void start(final boolean dateOnlyMode) {
        stop();
        final int requestedSession = ++sessionId;
        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(owner);
        future.addListener(new Runnable() {
            @Override
            public void run() {
                if (requestedSession != sessionId) {
                    return;
                }
                try {
                    ProcessCameraProvider resolvedProvider = future.get();
                    if (!hasBackCamera(resolvedProvider)) {
                        failStart(
                                requestedSession,
                                "未找到可用后置相机，请检查设备相机状态。",
                                null
                        );
                        return;
                    }
                    bind(resolvedProvider, requestedSession, dateOnlyMode);
                } catch (Exception error) {
                    failStart(
                            requestedSession,
                            "相机启动失败，请检查相机权限或重新打开应用。",
                            error
                    );
                }
            }
        }, mainExecutor);
    }

    boolean isBound() {
        return bound;
    }

    boolean isFocusInFlight() {
        return focusInFlight;
    }

    boolean hasFlashUnit() {
        Camera active = camera;
        return active != null && active.getCameraInfo().hasFlashUnit();
    }

    boolean setTorch(boolean enabled) {
        Camera active = camera;
        if (active == null || !active.getCameraInfo().hasFlashUnit()) {
            return false;
        }
        try {
            active.getCameraControl().enableTorch(enabled);
            return true;
        } catch (RuntimeException error) {
            listener.onFrameError(error);
            return false;
        }
    }

    boolean focusAtCenter(boolean announced, boolean automaticRecovery) {
        return focusAtRatio(0.5d, 0.48d, announced, automaticRecovery);
    }

    boolean focusAtRatio(
            double xRatio,
            double yRatio,
            boolean announced,
            boolean automaticRecovery
    ) {
        if (previewView.getWidth() <= 0 || previewView.getHeight() <= 0) {
            return false;
        }
        float x = (float) (clamp(xRatio, 0d, 1d) * previewView.getWidth());
        float y = (float) (clamp(yRatio, 0d, 1d) * previewView.getHeight());
        return requestFocus(x, y, announced, automaticRecovery);
    }

    @Override
    public void close() {
        stop();
    }

    void stop() {
        sessionId++;
        bound = false;
        focusInFlight = false;
        latestFocusRequestId++;
        previewView.setOnTouchListener(null);
        ProcessCameraProvider activeProvider = provider;
        provider = null;
        camera = null;
        if (activeProvider != null) {
            activeProvider.unbindAll();
        }
    }

    private void bind(
            ProcessCameraProvider resolvedProvider,
            final int requestedSession,
            boolean dateOnlyMode
    ) {
        if (requestedSession != sessionId) {
            return;
        }
        resolvedProvider.unbindAll();

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
        imageAnalysis.setAnalyzer(analyzerExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(ImageProxy imageProxy) {
                acquire(imageProxy, requestedSession);
            }
        });

        Camera activeCamera = resolvedProvider.bindToLifecycle(
                owner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
        );
        if (requestedSession != sessionId) {
            resolvedProvider.unbindAll();
            return;
        }

        provider = resolvedProvider;
        camera = activeCamera;
        bound = true;
        installTouchFocus(requestedSession);
        try {
            activeCamera.getCameraControl().setLinearZoom(dateOnlyMode ? 0.12f : 0.06f);
        } catch (RuntimeException ignored) {
            // Fixed-focus and virtual cameras can expose no usable zoom range.
        }
        listener.onCameraReady(activeCamera.getCameraInfo().hasFlashUnit());
        previewView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (requestedSession == sessionId && bound) {
                    focusAtCenter(false, false);
                }
            }
        }, 450L);
    }

    private void installTouchFocus(final int requestedSession) {
        previewView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP
                        && requestedSession == sessionId
                        && bound) {
                    requestFocus(event.getX(), event.getY(), true, false);
                }
                return true;
            }
        });
    }

    private boolean requestFocus(
            final float x,
            final float y,
            final boolean announced,
            final boolean automaticRecovery
    ) {
        final Camera activeCamera = camera;
        final int requestedSession = sessionId;
        if (!bound
                || activeCamera == null
                || previewView.getWidth() <= 0
                || previewView.getHeight() <= 0) {
            return false;
        }
        if (focusInFlight && !announced) {
            return false;
        }

        MeteringPoint point = previewView.getMeteringPointFactory().createPoint(x, y, 0.18f);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF
                        | FocusMeteringAction.FLAG_AE
                        | FocusMeteringAction.FLAG_AWB
        ).setAutoCancelDuration(announced ? 2L : 1L, TimeUnit.SECONDS).build();
        if (!activeCamera.getCameraInfo().isFocusMeteringSupported(action)) {
            emitFocus(new FocusEvent(
                    FocusStatus.UNSUPPORTED,
                    latestFocusRequestId,
                    x,
                    y,
                    announced,
                    automaticRecovery,
                    0L,
                    null
            ));
            return false;
        }

        focusInFlight = true;
        final long requestId = ++latestFocusRequestId;
        final long startedAt = SystemClock.elapsedRealtime();
        emitFocus(new FocusEvent(
                FocusStatus.STARTED,
                requestId,
                x,
                y,
                announced,
                automaticRecovery,
                0L,
                null
        ));
        final ListenableFuture<FocusMeteringResult> future =
                activeCamera.getCameraControl().startFocusAndMetering(action);
        future.addListener(new Runnable() {
            @Override
            public void run() {
                if (requestedSession != sessionId || activeCamera != camera) {
                    return;
                }
                if (requestId != latestFocusRequestId) {
                    emitFocus(new FocusEvent(
                            FocusStatus.SUPERSEDED,
                            requestId,
                            x,
                            y,
                            announced,
                            automaticRecovery,
                            SystemClock.elapsedRealtime() - startedAt,
                            null
                    ));
                    return;
                }
                try {
                    boolean successful = future.get().isFocusSuccessful();
                    focusInFlight = false;
                    emitFocus(new FocusEvent(
                            successful ? FocusStatus.SUCCESS : FocusStatus.FAILED,
                            requestId,
                            x,
                            y,
                            announced,
                            automaticRecovery,
                            SystemClock.elapsedRealtime() - startedAt,
                            null
                    ));
                } catch (Exception error) {
                    focusInFlight = false;
                    emitFocus(new FocusEvent(
                            FocusStatus.FAILED,
                            requestId,
                            x,
                            y,
                            announced,
                            automaticRecovery,
                            SystemClock.elapsedRealtime() - startedAt,
                            error
                    ));
                }
                previewView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (requestedSession == sessionId
                                && requestId == latestFocusRequestId
                                && activeCamera == camera) {
                            emitFocus(new FocusEvent(
                                    FocusStatus.CONTINUOUS,
                                    requestId,
                                    x,
                                    y,
                                    announced,
                                    automaticRecovery,
                                    0L,
                                    null
                            ));
                        }
                    }
                }, announced ? 2200L : 1200L);
            }
        }, mainExecutor);
        return true;
    }

    private void acquire(ImageProxy imageProxy, int requestedSession) {
        if (imageProxy == null) {
            return;
        }
        long nowMs = SystemClock.uptimeMillis();
        try {
            if (requestedSession != sessionId
                    || !bound
                    || !listener.shouldAcquireFrame(nowMs)) {
                imageProxy.close();
                return;
            }
        } catch (RuntimeException error) {
            imageProxy.close();
            listener.onFrameError(error);
            return;
        }

        Bitmap upright = null;
        long timestampUs = SystemClock.elapsedRealtime() * 1000L;
        try {
            upright = copyUprightBitmap(imageProxy);
            listener.onFrame(upright, timestampUs);
            upright = null;
        } catch (Exception error) {
            if (upright != null && !upright.isRecycled()) {
                upright.recycle();
            }
            listener.onFrameError(error);
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

    private boolean hasBackCamera(ProcessCameraProvider resolvedProvider) {
        try {
            return resolvedProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
        } catch (Exception error) {
            return false;
        }
    }

    private void failStart(int requestedSession, String message, Throwable error) {
        if (requestedSession != sessionId) {
            return;
        }
        bound = false;
        listener.onCameraUnavailable(message, error);
    }

    private void emitFocus(FocusEvent event) {
        listener.onFocusEvent(event);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
