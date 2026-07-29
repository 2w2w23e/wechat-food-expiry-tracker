package com.shiqi.expirytracker;

import android.graphics.Color;

/**
 * Shared semantic colors and stable component sizes for the APK's View-based UI.
 *
 * Keeping these values outside individual Activities makes the current Java UI
 * visually coherent while screen state is gradually extracted from the large hosts.
 */
final class AppUiTokens {
    static final int COLOR_BACKGROUND = Color.rgb(247, 248, 246);
    static final int COLOR_SURFACE = Color.WHITE;
    static final int COLOR_SURFACE_MUTED = Color.rgb(239, 242, 240);
    static final int COLOR_TEXT = Color.rgb(27, 33, 30);
    static final int COLOR_TEXT_MUTED = Color.rgb(91, 101, 96);
    static final int COLOR_PRIMARY = Color.rgb(35, 103, 73);
    static final int COLOR_DANGER = Color.rgb(159, 53, 46);
    static final int COLOR_DIVIDER = Color.rgb(222, 227, 224);

    static final int MIN_TOUCH_TARGET_DP = 48;

    private AppUiTokens() {
    }
}
