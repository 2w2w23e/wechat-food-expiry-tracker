package com.shiqi.expirytracker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RecognitionDiagnostics {
    static final String BOTTLENECK_LINE_RECOGNIZER = "line_recognizer";
    static final String BOTTLENECK_OCR_BACKLOG = "ocr_backlog";
    static final String BOTTLENECK_FOCUS_BLUR = "focus_blur";
    static final String BOTTLENECK_TEXT_DETECTOR = "text_detector";
    static final String BOTTLENECK_NONE = "none";

    private static final class Timing {
        long count;
        long totalMs;
        long latestMs;
        long maxMs;

        void add(long elapsedMs) {
            long safe = Math.max(0L, elapsedMs);
            count++;
            totalMs += safe;
            latestMs = safe;
            maxMs = Math.max(maxMs, safe);
        }

        long averageMs() {
            return count <= 0L ? 0L : Math.round(totalMs / (double) count);
        }
    }

    private final int maxEvents;
    private final ArrayDeque<String> events = new ArrayDeque<String>();
    private final Map<String, Long> counters = new LinkedHashMap<String, Long>();
    private final Map<String, Timing> timings = new LinkedHashMap<String, Timing>();
    private final Map<String, String> values = new LinkedHashMap<String, String>();
    private long startedAtMs;
    private long eventSequence;
    private String sessionId = "";
    private String source = "launch";

    RecognitionDiagnostics(int maxEvents) {
        this.maxEvents = Math.max(40, maxEvents);
    }

    synchronized void reset(long nowMs, String nextSource) {
        events.clear();
        counters.clear();
        timings.clear();
        values.clear();
        startedAtMs = Math.max(0L, nowMs);
        eventSequence = 0L;
        sessionId = Long.toHexString(System.currentTimeMillis());
        source = clean(nextSource);
        if (source.length() == 0) {
            source = "launch";
        }
        event(nowMs, "session.start", "source=" + source);
    }

    synchronized void setSource(long nowMs, String nextSource) {
        String cleanSource = clean(nextSource);
        if (cleanSource.length() == 0 || cleanSource.equals(source)) {
            return;
        }
        source = cleanSource;
        event(nowMs, "session.source", "source=" + source);
    }

    synchronized void event(long nowMs, String stage, String details) {
        long elapsed = startedAtMs <= 0L ? 0L : Math.max(0L, nowMs - startedAtMs);
        eventSequence++;
        String line = String.format(
                Locale.US,
                "%04d +%06dms %-24s %s",
                eventSequence,
                elapsed,
                clean(stage),
                clean(details)
        ).trim();
        events.addLast(line);
        while (events.size() > maxEvents) {
            events.removeFirst();
        }
    }

    synchronized void increment(String key) {
        add(key, 1L);
    }

    synchronized void add(String key, long amount) {
        String safeKey = clean(key);
        Long current = counters.get(safeKey);
        counters.put(
                safeKey,
                Long.valueOf((current == null ? 0L : current.longValue()) + amount)
        );
    }

    synchronized long counter(String key) {
        Long value = counters.get(clean(key));
        return value == null ? 0L : value.longValue();
    }

    synchronized void timing(String key, long elapsedMs) {
        String safeKey = clean(key);
        Timing timing = timings.get(safeKey);
        if (timing == null) {
            timing = new Timing();
            timings.put(safeKey, timing);
        }
        timing.add(elapsedMs);
    }

    synchronized long latestTimingMs(String key) {
        Timing timing = timings.get(clean(key));
        return timing == null ? 0L : timing.latestMs;
    }

    synchronized long averageTimingMs(String key) {
        Timing timing = timings.get(clean(key));
        return timing == null ? 0L : timing.averageMs();
    }

    synchronized void value(String key, String value) {
        values.put(clean(key), clean(value));
    }

    synchronized String compactHud() {
        return "\u53d6\u5e27 " + counter("frames_captured")
                + "  \u5165\u9009 " + counter("frames_accepted")
                + "  OCR " + counter("ocr_completed")
                + "  \u4e22\u65e7 " + counter("pending_replaced")
                + "\n\u68c0\u6d4b " + latestTimingMs("text_detector") + "ms"
                + "  \u884c\u8bc6\u522b " + latestTimingMs("line_recognizer") + "ms"
                + "  \u6574\u5e27 " + latestTimingMs("full_ocr") + "ms";
    }

    synchronized String summary(long nowMs) {
        long elapsed = startedAtMs <= 0L ? 0L : Math.max(0L, nowMs - startedAtMs);
        StringBuilder output = new StringBuilder();
        output.append("\u4f1a\u8bdd ").append(sessionId)
                .append("  ").append(source)
                .append("  ").append(elapsed / 1000L).append(" \u79d2\n");
        output.append("\u53d6\u5e27 ").append(counter("frames_captured"))
                .append("  \u9884\u68c0 ").append(counter("frames_probed"))
                .append("  \u5165\u9009 ").append(counter("frames_accepted"))
                .append("  OCR ").append(counter("ocr_completed")).append('\n');
        output.append("\u6700\u65b0\u5e27\u66ff\u6362 ").append(counter("pending_replaced"))
                .append("  \u6a21\u7cca\u8df3\u8fc7 ").append(counter("skip_blur"))
                .append("  \u65e0\u6587\u5b57\u8df3\u8fc7 ").append(counter("skip_no_text"))
                .append("  \u975e\u66f4\u4f18\u8df3\u8fc7 ").append(counter("skip_not_better")).append('\n');
        output.append("\u5bf9\u7126\u8bf7\u6c42 ").append(counter("focus_requested"))
                .append("  \u6210\u529f ").append(counter("focus_success"))
                .append("  \u5931\u8d25 ").append(counter("focus_failed"))
                .append("  \u81ea\u52a8\u91cd\u5bf9\u7126 ").append(counter("focus_recovery")).append('\n');
        appendTiming(output, "\u5feb\u901f\u6587\u5b57\u68c0\u6d4b", "probe_detector");
        appendTiming(output, "\u5b8c\u6574\u6587\u5b57\u68c0\u6d4b", "text_detector");
        appendTiming(output, "\u884c\u6587\u5b57\u8bc6\u522b", "line_recognizer");
        appendTiming(output, "ML Kit \u7b49\u5f85", "mlkit_wait");
        appendTiming(output, "\u6574\u5e27 OCR", "full_ocr");
        appendTiming(output, "\u6392\u961f\u7b49\u5f85", "queue_lag");
        if (!values.isEmpty()) {
            output.append("\u72b6\u6001: ");
            boolean first = true;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (!first) {
                    output.append("  ");
                }
                output.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            output.append('\n');
        }
        output.append("\u5224\u65ad: ").append(bottleneckHint());
        return output.toString();
    }

    synchronized String report(long nowMs, String environment) {
        StringBuilder output = new StringBuilder();
        output.append("\u98df\u671f\u7ba1\u5bb6 OCR \u8bca\u65ad\u65e5\u5fd7\n");
        output.append("environment=").append(clean(environment)).append('\n');
        output.append(summary(nowMs)).append("\n\n");
        output.append("counters\n");
        for (Map.Entry<String, Long> entry : counters.entrySet()) {
            output.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        output.append("\ntimings_ms\n");
        for (Map.Entry<String, Timing> entry : timings.entrySet()) {
            Timing timing = entry.getValue();
            output.append(entry.getKey())
                    .append(" count=").append(timing.count)
                    .append(" latest=").append(timing.latestMs)
                    .append(" avg=").append(timing.averageMs())
                    .append(" max=").append(timing.maxMs)
                    .append('\n');
        }
        output.append("\nevents\n");
        for (String event : events) {
            output.append(event).append('\n');
        }
        return output.toString();
    }

    synchronized List<String> eventSnapshot() {
        return new ArrayList<String>(events);
    }

    synchronized String sessionId() {
        return sessionId;
    }

    synchronized String bottleneckCode() {
        long detector = averageTimingMs("text_detector");
        long recognizer = averageTimingMs("line_recognizer");
        long full = averageTimingMs("full_ocr");
        long replaced = counter("pending_replaced");
        long blur = counter("skip_blur");
        long probed = counter("frames_probed");
        if (recognizer >= 1200L && recognizer > Math.max(1L, detector) * 2L) {
            return BOTTLENECK_LINE_RECOGNIZER;
        }
        if (full >= 2500L && replaced >= 8L) {
            return BOTTLENECK_OCR_BACKLOG;
        }
        if (probed >= 4L && blur * 3L >= probed) {
            return BOTTLENECK_FOCUS_BLUR;
        }
        if (counter("skip_no_text") >= 4L) {
            return BOTTLENECK_TEXT_DETECTOR;
        }
        return BOTTLENECK_NONE;
    }

    private String bottleneckHint() {
        String code = bottleneckCode();
        if (BOTTLENECK_LINE_RECOGNIZER.equals(code)) {
            return "\u884c\u6587\u5b57\u8bc6\u522b\u8017\u65f6\u6700\u9ad8\uff0c\u8bf7\u68c0\u67e5\u88c1\u526a\u6570\u91cf\u548c ONNX \u6279\u5904\u7406\u3002";
        }
        if (BOTTLENECK_OCR_BACKLOG.equals(code)) {
            return "OCR \u6162\u4e8e\u53d6\u666f\u901f\u5ea6\uff0c\u5904\u7406\u671f\u95f4\u5927\u91cf\u6700\u65b0\u5e27\u8986\u76d6\u65e7\u5e27\u3002";
        }
        if (BOTTLENECK_FOCUS_BLUR.equals(code)) {
            return "\u6a21\u7cca\u5e27\u5360\u6bd4\u8f83\u9ad8\uff0c\u8bf7\u68c0\u67e5\u7126\u8ddd\u53d8\u5316\u548c\u81ea\u52a8\u91cd\u5bf9\u7126\u3002";
        }
        if (BOTTLENECK_TEXT_DETECTOR.equals(code)) {
            return "\u753b\u9762\u53ef\u7528\u4f46\u6587\u5b57\u68c0\u6d4b\u53ec\u56de\u504f\u4f4e\uff0c\u8bf7\u68c0\u67e5\u6587\u5b57\u533a\u57df\u548c\u4f4e\u5bf9\u6bd4\u5ea6\u589e\u5f3a\u3002";
        }
        return "\u5c1a\u672a\u51fa\u73b0\u5355\u4e00\u660e\u663e\u74f6\u9888\uff0c\u8bf7\u7ed3\u5408\u4e8b\u4ef6\u65f6\u95f4\u7ebf\u67e5\u770b\u3002";
    }

    private void appendTiming(StringBuilder output, String label, String key) {
        Timing timing = timings.get(key);
        if (timing == null || timing.count <= 0L) {
            return;
        }
        output.append(label)
                .append(": \u6700\u8fd1 ").append(timing.latestMs).append("ms")
                .append("  \u5e73\u5747 ").append(timing.averageMs()).append("ms")
                .append("  \u6700\u5927 ").append(timing.maxMs).append("ms")
                .append("  ").append(timing.count).append(" \u6b21\n");
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
    }
}
