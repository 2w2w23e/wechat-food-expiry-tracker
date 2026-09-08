package com.shiqi.expirytracker;

final class FoodEditorSession {
    static final int MODE_CREATE = 0;
    static final int MODE_EDIT = 1;
    static final int MODE_REPLENISH = 2;

    private FoodEditorSession() {
    }

    static int resolveMode(boolean isEdit, boolean isReplenishment) {
        if (isEdit) {
            return MODE_EDIT;
        }
        return isReplenishment ? MODE_REPLENISH : MODE_CREATE;
    }

    static String title(int mode) {
        if (mode == MODE_EDIT) {
            return "编辑批次";
        }
        if (mode == MODE_REPLENISH) {
            return "补货新批次";
        }
        return "新增商品";
    }

    static String primaryAction(int mode) {
        if (mode == MODE_EDIT) {
            return "保存修改";
        }
        if (mode == MODE_REPLENISH) {
            return "确认入库";
        }
        return "确认新增";
    }

    static boolean shouldConfirmDiscard(
            String initialFingerprint,
            String currentFingerprint,
            boolean protectInitialContent
    ) {
        if (protectInitialContent) {
            return true;
        }
        return !clean(initialFingerprint).equals(clean(currentFingerprint));
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
