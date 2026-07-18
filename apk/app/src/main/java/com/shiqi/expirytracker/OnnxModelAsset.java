package com.shiqi.expirytracker;

import android.content.Context;
import android.content.pm.PackageInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class OnnxModelAsset {
    private OnnxModelAsset() {}

    static File materialize(Context context, String assetName) throws Exception {
        Context applicationContext = context.getApplicationContext();
        File directory = new File(applicationContext.getCodeCacheDir(), "onnx-models");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create ONNX model cache");
        }

        String safeName = assetName.replaceAll("[^A-Za-z0-9._-]", "_");
        File target = new File(directory, safeName + "-" + packageUpdateStamp(applicationContext));
        if (target.isFile() && target.length() > 0L) {
            return target;
        }

        File temporary = new File(directory, target.getName() + ".tmp");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Could not replace temporary ONNX model");
        }
        InputStream input = applicationContext.getAssets().open(assetName);
        try {
            FileOutputStream output = new FileOutputStream(temporary);
            try {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace cached ONNX model");
        }
        if (!temporary.renameTo(target)) {
            throw new IOException("Could not finalize cached ONNX model");
        }
        removeOlderVersions(directory, safeName, target);
        return target;
    }

    private static long packageUpdateStamp(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.lastUpdateTime;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static void removeOlderVersions(File directory, String safeName, File current) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        String prefix = safeName + "-";
        for (File file : files) {
            if (!file.equals(current) && file.getName().startsWith(prefix)) {
                file.delete();
            }
        }
    }
}
