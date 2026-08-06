package com.shiqi.expirytracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class RecognitionDebugStore {
    private static final String PREFS_NAME = "recognition_debug_v1";
    private static final String KEY_ENABLED = "enabled";
    private static final String DIRECTORY_NAME = "recognition-debug";
    private static final String META_FILE_NAME = "capture-meta.txt";
    private static final int MAX_SESSION_FILES = 8;
    private static final int MAX_REPORT_CHARS = 700_000;

    private RecognitionDebugStore() {
    }

    static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(KEY_ENABLED, false);
    }

    static void enable(Context context, String environment) throws IOException {
        File directory = directory(context);
        clearDirectory(directory);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建识别调试目录");
        }
        String timestamp = timestamp();
        writeText(
                new File(directory, META_FILE_NAME),
                "食期管家识别调试记录\n"
                        + "enabled_at=" + timestamp + "\n"
                        + "environment=" + clean(environment) + "\n"
        );
        preferences(context).edit().putBoolean(KEY_ENABLED, true).apply();
    }

    static void disable(Context context) {
        preferences(context).edit().putBoolean(KEY_ENABLED, false).apply();
    }

    static void saveSession(
            Context context,
            String sessionId,
            String reason,
            String report
    ) throws IOException {
        if (!isEnabled(context)) {
            return;
        }
        File directory = directory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建识别调试目录");
        }
        String safeSessionId = safeFilePart(sessionId);
        if (safeSessionId.length() == 0) {
            safeSessionId = Long.toHexString(System.currentTimeMillis());
        }
        String body = "saved_at=" + timestamp() + "\n"
                + "reason=" + clean(reason) + "\n"
                + truncate(report, MAX_REPORT_CHARS);
        writeText(new File(directory, "session-" + safeSessionId + ".txt"), body);
        pruneSessions(directory);
    }

    static boolean hasShareableLog(Context context) {
        File[] files = reportFiles(directory(context));
        return files.length > 0;
    }

    static File buildShareFile(Context context, String transientReport) throws IOException {
        File shareDirectory = new File(context.getCacheDir(), "shared-transfers");
        if (!shareDirectory.exists() && !shareDirectory.mkdirs()) {
            throw new IOException("无法创建分享目录");
        }
        File output = new File(
                shareDirectory,
                "shiqi-recognition-debug-" + fileTimestamp() + ".txt"
        );
        StringBuilder content = new StringBuilder();
        File[] files = reportFiles(directory(context));
        for (File file : files) {
            appendFile(content, file);
            content.append("\n\n");
        }
        String cleanTransient = transientReport == null ? "" : transientReport.trim();
        if (cleanTransient.length() > 0) {
            content.append("current_session\n").append(cleanTransient).append('\n');
        }
        if (content.length() == 0) {
            throw new IOException("暂无可分享的识别调试日志");
        }
        writeText(output, truncate(content.toString(), MAX_REPORT_CHARS));
        return output;
    }

    static String readForTests(Context context) throws IOException {
        StringBuilder content = new StringBuilder();
        for (File file : reportFiles(directory(context))) {
            appendFile(content, file);
            content.append('\n');
        }
        return content.toString();
    }

    static void resetForTests(Context context) {
        preferences(context).edit().clear().commit();
        clearDirectory(directory(context));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static File directory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY_NAME);
    }

    private static File[] reportFiles(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return new File[0];
        }
        List<File> reports = new ArrayList<File>();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".txt")) {
                reports.add(file);
            }
        }
        File[] result = reports.toArray(new File[0]);
        Arrays.sort(result, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (META_FILE_NAME.equals(left.getName())) {
                    return META_FILE_NAME.equals(right.getName()) ? 0 : -1;
                }
                if (META_FILE_NAME.equals(right.getName())) {
                    return 1;
                }
                return Long.compare(left.lastModified(), right.lastModified());
            }
        });
        return result;
    }

    private static void pruneSessions(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        List<File> sessions = new ArrayList<File>();
        for (File file : files) {
            if (file.isFile() && file.getName().startsWith("session-")) {
                sessions.add(file);
            }
        }
        sessions.sort(new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(right.lastModified(), left.lastModified());
            }
        });
        for (int index = MAX_SESSION_FILES; index < sessions.size(); index++) {
            sessions.get(index).delete();
        }
    }

    private static void appendFile(StringBuilder output, File file) throws IOException {
        char[] buffer = new char[4096];
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file),
                StandardCharsets.UTF_8
        )) {
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                output.append(buffer, 0, read);
            }
        }
    }

    private static void writeText(File file, String value) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file, false),
                StandardCharsets.UTF_8
        )) {
            writer.write(value == null ? "" : value);
        }
    }

    private static void clearDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date());
    }

    private static String fileTimestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    private static String safeFilePart(String value) {
        return clean(value).replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static String clean(String value) {
        return value == null
                ? ""
                : value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
    }

    private static String truncate(String value, int maxLength) {
        String text = value == null ? "" : value;
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(text.length() - maxLength);
    }
}
