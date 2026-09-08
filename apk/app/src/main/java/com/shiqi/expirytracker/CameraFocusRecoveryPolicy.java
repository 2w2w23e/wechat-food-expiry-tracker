package com.shiqi.expirytracker;

final class CameraFocusRecoveryPolicy {
    private final int blurThreshold;
    private final long cooldownMs;

    private int blurStreak;
    private long lastFocusRequestAtMs = Long.MIN_VALUE;

    CameraFocusRecoveryPolicy(int blurThreshold, long cooldownMs) {
        if (blurThreshold <= 0 || cooldownMs < 0L) {
            throw new IllegalArgumentException("invalid focus recovery policy");
        }
        this.blurThreshold = blurThreshold;
        this.cooldownMs = cooldownMs;
    }

    boolean shouldRecover(boolean blurred, boolean focusInFlight, long nowMs) {
        if (!blurred) {
            blurStreak = 0;
            return false;
        }
        blurStreak++;
        if (blurStreak < blurThreshold || focusInFlight || isCoolingDown(nowMs)) {
            return false;
        }
        blurStreak = 0;
        lastFocusRequestAtMs = nowMs;
        return true;
    }

    void markFocusRequested(long nowMs) {
        blurStreak = 0;
        lastFocusRequestAtMs = nowMs;
    }

    void reset() {
        blurStreak = 0;
        lastFocusRequestAtMs = Long.MIN_VALUE;
    }

    int blurStreak() {
        return blurStreak;
    }

    private boolean isCoolingDown(long nowMs) {
        if (lastFocusRequestAtMs == Long.MIN_VALUE) {
            return false;
        }
        long elapsed = nowMs - lastFocusRequestAtMs;
        return elapsed < 0L || elapsed < cooldownMs;
    }
}
