package com.shiqi.expirytracker;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

final class RoomMigrationBackup {
    private static final String DIRECTORY_NAME = "inventory-migration";
    private static final String FORMAT = "shiqi-room-migration-backup";
    private static final int FORMAT_VERSION = 1;

    private RoomMigrationBackup() {
    }

    static BackupDescriptor ensureBackup(
            Context context,
            String sourceRaw,
            int sourceSchemaVersion
    ) throws IOException, JSONException {
        String payload = sourceRaw == null ? "" : sourceRaw;
        String sha256 = sha256(payload);
        File directory = new File(context.getApplicationContext().getFilesDir(), DIRECTORY_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create the Room migration backup directory.");
        }
        File target = new File(
                directory,
                "room-v1-legacy-" + sha256.substring(0, 16) + ".json"
        );
        BackupDescriptor descriptor = new BackupDescriptor(
                target,
                sha256,
                payload.getBytes(StandardCharsets.UTF_8).length,
                sourceSchemaVersion
        );
        if (target.exists()) {
            verifyExisting(target, payload, descriptor);
            return descriptor;
        }

        JSONObject envelope = new JSONObject();
        envelope.put("format", FORMAT);
        envelope.put("formatVersion", FORMAT_VERSION);
        envelope.put("createdAt", DateRules.nowIsoLike());
        envelope.put("sourceSchemaVersion", sourceSchemaVersion);
        envelope.put("payloadLength", descriptor.payloadLength);
        envelope.put("payloadSha256", sha256);
        envelope.put("payload", payload);

        File temporary = new File(
                directory,
                target.getName() + ".tmp-" + UUID.randomUUID().toString()
        );
        byte[] bytes = envelope.toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream output = null;
        boolean moved = false;
        try {
            output = new FileOutputStream(temporary);
            output.write(bytes);
            output.flush();
            output.getFD().sync();
            output.close();
            output = null;
            try {
                Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
                moved = true;
            } catch (ErrnoException exception) {
                throw new IOException(
                        "Could not atomically publish the Room migration backup.",
                        exception
                );
            }
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                    // The original write error remains authoritative.
                }
            }
            if (!moved && temporary.exists()) {
                temporary.delete();
            }
        }
        verifyExisting(target, payload, descriptor);
        return descriptor;
    }

    static void deleteBackupsForTests(Context context) {
        File directory = new File(context.getApplicationContext().getFilesDir(), DIRECTORY_NAME);
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    private static void verifyExisting(
            File file,
            String expectedPayload,
            BackupDescriptor expected
    ) throws IOException, JSONException {
        JSONObject envelope = new JSONObject(readUtf8(file));
        if (!FORMAT.equals(envelope.optString("format"))
                || envelope.optInt("formatVersion", -1) != FORMAT_VERSION
                || envelope.optInt("sourceSchemaVersion", Integer.MIN_VALUE)
                        != expected.sourceSchemaVersion
                || envelope.optInt("payloadLength", -1) != expected.payloadLength
                || !expected.payloadSha256.equals(envelope.optString("payloadSha256"))
                || !expectedPayload.equals(envelope.optString("payload"))) {
            throw new IOException("Existing Room migration backup failed verification.");
        }
        if (!expected.payloadSha256.equals(sha256(envelope.optString("payload")))) {
            throw new IOException("Existing Room migration backup hash does not match its payload.");
        }
    }

    private static String readUtf8(File file) throws IOException {
        FileInputStream input = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            input = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    private static String sha256(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                result.append(String.format("%02x", current & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable.", exception);
        }
    }

    static final class BackupDescriptor {
        final File file;
        final String payloadSha256;
        final int payloadLength;
        final int sourceSchemaVersion;

        BackupDescriptor(
                File file,
                String payloadSha256,
                int payloadLength,
                int sourceSchemaVersion
        ) {
            this.file = file;
            this.payloadSha256 = payloadSha256;
            this.payloadLength = payloadLength;
            this.sourceSchemaVersion = sourceSchemaVersion;
        }
    }
}
