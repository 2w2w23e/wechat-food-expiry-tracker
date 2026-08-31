package com.shiqi.expirytracker;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class RoomMigrationBackup {
    private static final String DIRECTORY_NAME = "inventory-migration";

    private RoomMigrationBackup() {
    }

    static BackupDescriptor ensureBackup(
            Context context,
            String sourceRaw,
            int sourceSchemaVersion
    ) throws IOException, JSONException {
        String payload = sourceRaw == null ? "" : sourceRaw;
        String sha256 = MigrationBackupEnvelope.sha256(payload);
        File directory = backupDirectory(context);
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

        String envelope = MigrationBackupEnvelope.create(
                payload,
                sourceSchemaVersion,
                DateRules.nowIsoLike()
        );
        File temporary = new File(
                directory,
                target.getName() + ".tmp-" + UUID.randomUUID().toString()
        );
        byte[] bytes = envelope.getBytes(StandardCharsets.UTF_8);
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

    static BackupCatalog verifiedCatalog(Context context, int currentSchemaVersion) {
        File directory = backupDirectory(context);
        File[] files = directory.listFiles();
        List<File> ordered = new ArrayList<File>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()
                        && file.getName().endsWith(".json")
                        && file.getName().indexOf(".tmp-") < 0) {
                    ordered.add(file);
                }
            }
        }
        Collections.sort(ordered, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                int byModified = Long.compare(right.lastModified(), left.lastModified());
                return byModified != 0 ? byModified : right.getName().compareTo(left.getName());
            }
        });

        List<BackupCandidate> candidates = new ArrayList<BackupCandidate>();
        int rejected = 0;
        for (File file : ordered) {
            try {
                BackupCandidate candidate = readCandidate(file, currentSchemaVersion);
                if (candidate.payloadLength == 0 && candidate.batchCount == 0) {
                    continue;
                }
                candidates.add(candidate);
            } catch (Exception ignored) {
                rejected++;
            }
        }
        return new BackupCatalog(candidates, rejected);
    }

    static RestoreResult restore(
            Context context,
            FoodStore store,
            List<FoodItem> currentFoods,
            File candidateFile,
            boolean forceFailureForQa
    ) {
        if (store == null || candidateFile == null) {
            return RestoreResult.failed("未选择可恢复的备份");
        }
        try {
            BackupCandidate candidate = readCandidate(
                    candidateFile,
                    FoodStore.CURRENT_SCHEMA_VERSION
            );
            String currentRaw = FoodStoreMigration.serialize(
                    copies(currentFoods),
                    FoodStore.CURRENT_SCHEMA_VERSION
            );
            BackupDescriptor safetyBackup = ensureBackup(
                    context,
                    currentRaw,
                    FoodStore.CURRENT_SCHEMA_VERSION
            );
            BackupCandidate verifiedSafety = readCandidate(
                    safetyBackup.file,
                    FoodStore.CURRENT_SCHEMA_VERSION
            );
            if (!safetyBackup.payloadSha256.equals(verifiedSafety.payloadSha256)) {
                return RestoreResult.failed("当前库存的恢复前备份校验失败");
            }

            List<FoodItem> restoredFoods = candidate.foods();
            boolean saved = forceFailureForQa
                    ? store.saveFoodsForQaForcedFailure(restoredFoods)
                    : store.saveFoods(restoredFoods);
            if (!saved) {
                return RestoreResult.failed("恢复写入失败，当前库存未改动");
            }
            return RestoreResult.succeeded(restoredFoods, safetyBackup.file);
        } catch (Exception error) {
            String message = FoodItem.cleanText(error.getMessage());
            return RestoreResult.failed(
                    message.length() == 0
                            ? "备份校验失败，当前库存未改动"
                            : "备份不可恢复：" + message
            );
        }
    }

    static void deleteBackupsForTests(Context context) {
        File directory = backupDirectory(context);
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

    private static File backupDirectory(Context context) {
        return new File(
                context.getApplicationContext().getFilesDir(),
                DIRECTORY_NAME
        );
    }

    private static BackupCandidate readCandidate(
            File file,
            int currentSchemaVersion
    ) throws IOException, JSONException {
        MigrationBackupEnvelope.Parsed envelope =
                MigrationBackupEnvelope.parse(readUtf8(file));
        if (envelope.sourceSchemaVersion > currentSchemaVersion) {
            throw new IOException("Backup schema is newer than this app.");
        }
        FoodStoreMigration.MigrationResult migration = FoodStoreMigration.migrate(
                envelope.payload,
                currentSchemaVersion
        );
        if (migration.sourceSchemaVersion != envelope.sourceSchemaVersion) {
            throw new IOException("Backup schema metadata does not match its payload.");
        }
        List<FoodItem> migratedFoods = copies(migration.foods);
        return new BackupCandidate(
                file,
                envelope.createdAt,
                envelope.sourceSchemaVersion,
                envelope.payloadLength,
                envelope.payloadSha256,
                migratedFoods,
                InventoryPresentation.group(migratedFoods).size(),
                file.lastModified()
        );
    }

    private static void verifyExisting(
            File file,
            String expectedPayload,
            BackupDescriptor expected
    ) throws IOException, JSONException {
        MigrationBackupEnvelope.Parsed envelope =
                MigrationBackupEnvelope.parse(readUtf8(file));
        if (envelope.sourceSchemaVersion != expected.sourceSchemaVersion
                || envelope.payloadLength != expected.payloadLength
                || !expected.payloadSha256.equals(envelope.payloadSha256)
                || !expectedPayload.equals(envelope.payload)) {
            throw new IOException("Existing Room migration backup failed verification.");
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

    private static List<FoodItem> copies(List<FoodItem> source) {
        List<FoodItem> result = new ArrayList<FoodItem>();
        if (source != null) {
            for (FoodItem item : source) {
                if (item != null) {
                    result.add(item.copy());
                }
            }
        }
        return result;
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

    static final class BackupCandidate {
        final File file;
        final String createdAt;
        final int sourceSchemaVersion;
        final int payloadLength;
        final String payloadSha256;
        final int batchCount;
        final int productCount;
        final long lastModified;
        private final List<FoodItem> migratedFoods;

        BackupCandidate(
                File file,
                String createdAt,
                int sourceSchemaVersion,
                int payloadLength,
                String payloadSha256,
                List<FoodItem> migratedFoods,
                int productCount,
                long lastModified
        ) {
            this.file = file;
            this.createdAt = createdAt;
            this.sourceSchemaVersion = sourceSchemaVersion;
            this.payloadLength = payloadLength;
            this.payloadSha256 = payloadSha256;
            this.migratedFoods = copies(migratedFoods);
            this.batchCount = this.migratedFoods.size();
            this.productCount = productCount;
            this.lastModified = lastModified;
        }

        List<FoodItem> foods() {
            return copies(migratedFoods);
        }
    }

    static final class BackupCatalog {
        final List<BackupCandidate> candidates;
        final int rejectedFiles;

        BackupCatalog(List<BackupCandidate> candidates, int rejectedFiles) {
            this.candidates = new ArrayList<BackupCandidate>(candidates);
            this.rejectedFiles = Math.max(0, rejectedFiles);
        }
    }

    static final class RestoreResult {
        final boolean success;
        final List<FoodItem> restoredFoods;
        final File safetyBackup;
        final String message;

        private RestoreResult(
                boolean success,
                List<FoodItem> restoredFoods,
                File safetyBackup,
                String message
        ) {
            this.success = success;
            this.restoredFoods = copies(restoredFoods);
            this.safetyBackup = safetyBackup;
            this.message = FoodItem.cleanText(message);
        }

        static RestoreResult succeeded(List<FoodItem> restoredFoods, File safetyBackup) {
            return new RestoreResult(true, restoredFoods, safetyBackup, "");
        }

        static RestoreResult failed(String message) {
            return new RestoreResult(
                    false,
                    Collections.<FoodItem>emptyList(),
                    null,
                    message
            );
        }
    }
}
