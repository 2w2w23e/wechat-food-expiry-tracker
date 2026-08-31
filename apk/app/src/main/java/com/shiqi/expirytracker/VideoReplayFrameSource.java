package com.shiqi.expirytracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

final class VideoReplayFrameSource implements AutoCloseable {
    private static final String TAG = "ShiqiRecognition";

    private final MediaMetadataRetriever retriever;
    private final int maxFrameSide;
    private final int sourceWidth;
    private final int sourceHeight;
    private final long durationUs;
    private boolean closed;

    static VideoReplayFrameSource open(
            Context context,
            Uri uri,
            String path,
            int maxFrameSide
    ) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        boolean opened = false;
        try {
            String cleanPath = path == null ? "" : path.trim();
            if (cleanPath.length() > 0) {
                retriever.setDataSource(cleanPath);
            } else {
                if (context == null || uri == null) {
                    throw new IllegalArgumentException("video uri or path is required");
                }
                retriever.setDataSource(context, uri);
            }
            VideoReplayFrameSource source = new VideoReplayFrameSource(
                    retriever,
                    maxFrameSide
            );
            opened = true;
            return source;
        } finally {
            if (!opened) {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                    // Opening failure must not retain native decoder resources.
                }
            }
        }
    }

    private VideoReplayFrameSource(MediaMetadataRetriever retriever, int maxFrameSide) {
        this.retriever = retriever;
        this.maxFrameSide = Math.max(1, maxFrameSide);
        sourceWidth = metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        sourceHeight = metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        durationUs = metadataLong(MediaMetadataRetriever.METADATA_KEY_DURATION) * 1000L;
    }

    long durationUs() {
        return durationUs;
    }

    int sourceWidth() {
        return sourceWidth;
    }

    int sourceHeight() {
        return sourceHeight;
    }

    Bitmap frameAt(long timestampUs) {
        ensureOpen();
        long safeTimestampUs = Math.max(0L, timestampUs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            int largestSide = Math.max(sourceWidth, sourceHeight);
            if (sourceWidth > 0 && sourceHeight > 0 && largestSide > 0) {
                double scale = Math.min(1d, maxFrameSide / (double) largestSide);
                int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
                int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
                try {
                    return retriever.getScaledFrameAtTime(
                            safeTimestampUs,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                            targetWidth,
                            targetHeight
                    );
                } catch (RuntimeException error) {
                    Log.d(TAG, "Scaled video frame unavailable; using full frame", error);
                }
            }
        }
        return rawFrameAt(safeTimestampUs);
    }

    Bitmap rawFrameAt(long timestampUs) {
        ensureOpen();
        return retriever.getFrameAtTime(
                Math.max(0L, timestampUs),
                MediaMetadataRetriever.OPTION_CLOSEST
        );
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            retriever.release();
        } catch (Exception ignored) {
            // Release is best effort on vendor codecs.
        }
    }

    private int metadataInt(int key) {
        long value = metadataLong(key);
        return value <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, value);
    }

    private long metadataLong(int key) {
        try {
            String value = retriever.extractMetadata(key);
            return value == null ? 0L : Math.max(0L, Long.parseLong(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("video frame source is closed");
        }
    }
}
