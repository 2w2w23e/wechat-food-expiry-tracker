package com.shiqi.expirytracker;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class MigrationBackupEnvelope {
    static final String FORMAT = "shiqi-room-migration-backup";
    static final int FORMAT_VERSION = 1;

    private MigrationBackupEnvelope() {
    }

    static String create(
            String sourceRaw,
            int sourceSchemaVersion,
            String createdAt
    ) throws JSONException, IOException {
        String payload = sourceRaw == null ? "" : sourceRaw;
        JSONObject envelope = new JSONObject();
        envelope.put("format", FORMAT);
        envelope.put("formatVersion", FORMAT_VERSION);
        envelope.put("createdAt", FoodItem.cleanText(createdAt));
        envelope.put("sourceSchemaVersion", sourceSchemaVersion);
        envelope.put("payloadLength", utf8Length(payload));
        envelope.put("payloadSha256", sha256(payload));
        envelope.put("payload", payload);
        return envelope.toString();
    }

    static Parsed parse(String raw) throws JSONException, IOException {
        JSONTokener tokener = new JSONTokener(raw == null ? "" : raw);
        Object root = tokener.nextValue();
        if (!(root instanceof JSONObject) || tokener.nextClean() != 0) {
            throw new IOException("Inventory backup JSON is invalid.");
        }
        JSONObject envelope = (JSONObject) root;
        if (!FORMAT.equals(envelope.optString("format", ""))
                || envelope.optInt("formatVersion", -1) != FORMAT_VERSION) {
            throw new IOException("Unsupported inventory backup format.");
        }
        if (!envelope.has("payload")
                || !envelope.has("payloadLength")
                || !envelope.has("payloadSha256")
                || !envelope.has("sourceSchemaVersion")) {
            throw new IOException("Inventory backup metadata is incomplete.");
        }

        String payload = envelope.optString("payload", "");
        int sourceSchemaVersion = envelope.optInt("sourceSchemaVersion", Integer.MIN_VALUE);
        int payloadLength = envelope.optInt("payloadLength", -1);
        String payloadSha256 = envelope.optString("payloadSha256", "");
        if (sourceSchemaVersion < 0
                || payloadLength != utf8Length(payload)
                || !sha256(payload).equals(payloadSha256)) {
            throw new IOException("Inventory backup verification failed.");
        }

        return new Parsed(
                FoodItem.cleanText(envelope.optString("createdAt", "")),
                sourceSchemaVersion,
                payloadLength,
                payloadSha256,
                payload
        );
    }

    static String sha256(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                result.append(String.format("%02x", current & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable.", exception);
        }
    }

    private static int utf8Length(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8).length;
    }

    static final class Parsed {
        final String createdAt;
        final int sourceSchemaVersion;
        final int payloadLength;
        final String payloadSha256;
        final String payload;

        Parsed(
                String createdAt,
                int sourceSchemaVersion,
                int payloadLength,
                String payloadSha256,
                String payload
        ) {
            this.createdAt = createdAt;
            this.sourceSchemaVersion = sourceSchemaVersion;
            this.payloadLength = payloadLength;
            this.payloadSha256 = payloadSha256;
            this.payload = payload;
        }
    }
}