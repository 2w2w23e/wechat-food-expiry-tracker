package com.shiqi.expirytracker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public final class CameraFramePipelineTest {
    private int passed;
    private int failed;

    public static void main(String[] args) {
        new CameraFramePipelineTest().run();
    }

    private void run() {
        test("LatestFrameProcessor keeps only the newest queued frame", new TestCase() {
            @Override
            public void run() {
                QueuedExecutor executor = new QueuedExecutor();
                final List<String> processed = new ArrayList<String>();
                LatestFrameProcessor<TrackedFrame> processor =
                        trackingProcessor(executor, processed, null);

                processor.start(7);
                TrackedFrame first = new TrackedFrame("first");
                TrackedFrame second = new TrackedFrame("second");
                TrackedFrame latest = new TrackedFrame("latest");
                LatestFrameProcessor.OfferResult firstOffer = processor.offer(first, 10L, 7);
                LatestFrameProcessor.OfferResult secondOffer = processor.offer(second, 20L, 7);
                LatestFrameProcessor.OfferResult latestOffer = processor.offer(latest, 30L, 7);

                assertTrue(firstOffer.accepted, "first frame should be accepted");
                assertTrue(firstOffer.scheduledWorker, "first frame should schedule the worker");
                assertTrue(secondOffer.replacedPending, "second frame should replace the first");
                assertTrue(latestOffer.replacedPending, "latest frame should replace the second");
                assertEquals(1, first.releaseCount);
                assertEquals(1, second.releaseCount);
                assertEquals(0, latest.releaseCount);
                assertTrue(processor.isBusy(), "queued latest frame should report busy");

                executor.runAll();

                assertEquals(Arrays.asList("latest"), processed);
                assertEquals(1, latest.releaseCount);
                assertFalse(processor.isBusy(), "drained processor should become idle");
                processor.stop();
            }
        });

        test("LatestFrameProcessor rejects stale generations and releases once", new TestCase() {
            @Override
            public void run() {
                QueuedExecutor executor = new QueuedExecutor();
                final List<String> processed = new ArrayList<String>();
                LatestFrameProcessor<TrackedFrame> processor =
                        trackingProcessor(executor, processed, null);

                processor.start(1);
                TrackedFrame oldPending = new TrackedFrame("old-pending");
                processor.offer(oldPending, 10L, 1);
                processor.start(2);

                TrackedFrame stale = new TrackedFrame("stale");
                LatestFrameProcessor.OfferResult staleOffer = processor.offer(stale, 20L, 1);
                TrackedFrame current = new TrackedFrame("current");
                LatestFrameProcessor.OfferResult currentOffer = processor.offer(current, 30L, 2);
                executor.runAll();

                assertFalse(staleOffer.accepted, "stale generation must be rejected");
                assertTrue(currentOffer.accepted, "current generation should be accepted");
                assertEquals(Arrays.asList("current"), processed);
                assertEquals(1, oldPending.releaseCount);
                assertEquals(1, stale.releaseCount);
                assertEquals(1, current.releaseCount);
                assertEquals(2, processor.activeGeneration());
                processor.stop();
            }
        });

        test("LatestFrameProcessor cleans up after executor rejection", new TestCase() {
            @Override
            public void run() {
                final List<Throwable> errors = new ArrayList<Throwable>();
                LatestFrameProcessor<TrackedFrame> processor =
                        trackingProcessor(
                                new Executor() {
                                    @Override
                                    public void execute(Runnable command) {
                                        throw new IllegalStateException("executor rejected frame");
                                    }
                                },
                                new ArrayList<String>(),
                                errors
                        );
                processor.start(3);
                TrackedFrame rejected = new TrackedFrame("rejected");
                LatestFrameProcessor.OfferResult offer = processor.offer(rejected, 1L, 3);

                assertFalse(offer.accepted, "rejected worker must not retain frame ownership");
                assertEquals(1, rejected.releaseCount);
                assertEquals(1, errors.size());
                assertFalse(processor.isBusy(), "executor rejection must clear busy state");
                processor.stop();
            }
        });

        test("LatestFrameProcessor cleans up after processor failure", new TestCase() {
            @Override
            public void run() {
                QueuedExecutor executor = new QueuedExecutor();
                final List<Throwable> errors = new ArrayList<Throwable>();
                LatestFrameProcessor<TrackedFrame> processor =
                        new LatestFrameProcessor<TrackedFrame>(
                                executor,
                                new LatestFrameProcessor.Processor<TrackedFrame>() {
                                    @Override
                                    public void process(LatestFrameProcessor.Frame<TrackedFrame> frame) {
                                        throw new IllegalArgumentException("bad frame");
                                    }
                                },
                                new LatestFrameProcessor.Releaser<TrackedFrame>() {
                                    @Override
                                    public void release(TrackedFrame value) {
                                        value.releaseCount++;
                                    }
                                },
                                new LatestFrameProcessor.ErrorListener() {
                                    @Override
                                    public void onError(Throwable error) {
                                        errors.add(error);
                                    }
                                }
                        );
                processor.start(4);
                TrackedFrame bad = new TrackedFrame("bad");
                processor.offer(bad, 2L, 4);
                executor.runAll();

                assertEquals(1, bad.releaseCount);
                assertEquals(1, errors.size());
                assertFalse(processor.isBusy(), "processor failure must not wedge the pipeline");
                processor.stop();
            }
        });

        test("CameraFocusRecoveryPolicy requires evidence and respects cooldown", new TestCase() {
            @Override
            public void run() {
                CameraFocusRecoveryPolicy policy = new CameraFocusRecoveryPolicy(2, 1800L);
                assertFalse(policy.shouldRecover(true, false, 1000L),
                        "one blurry frame is insufficient");
                assertEquals(1, policy.blurStreak());
                assertTrue(policy.shouldRecover(true, false, 1010L),
                        "second blurry frame should recover focus");
                assertEquals(0, policy.blurStreak());

                assertFalse(policy.shouldRecover(true, false, 1200L),
                        "cooldown should block first retry");
                assertFalse(policy.shouldRecover(true, false, 1300L),
                        "cooldown should block repeated retry");
                assertTrue(policy.shouldRecover(true, false, 2810L),
                        "retry should resume after cooldown");

                policy.reset();
                assertFalse(policy.shouldRecover(true, false, 4000L),
                        "new streak starts at one");
                assertFalse(policy.shouldRecover(true, true, 4010L),
                        "in-flight focus should block recovery");
                assertTrue(policy.shouldRecover(true, false, 4020L),
                        "stored blur evidence can recover after focus ends");

                policy.markFocusRequested(5000L);
                assertFalse(policy.shouldRecover(true, false, 4990L),
                        "backward clock must not bypass cooldown");
                assertFalse(policy.shouldRecover(true, false, 4991L),
                        "backward clock must remain in cooldown");
                policy.shouldRecover(false, false, 7000L);
                assertEquals(0, policy.blurStreak());
            }
        });

        test("RecognitionWorkSession invalidates stale callbacks on reset and rescan", new TestCase() {
            @Override
            public void run() {
                RecognitionWorkSession session = new RecognitionWorkSession();
                int initial = session.snapshot().generation;

                RecognitionWorkSession.State reset = session.resetAll();
                assertEquals(initial + 1, reset.generation);
                assertEquals(RecognitionWorkSession.Phase.SCANNING, reset.phase);
                assertFalse(reset.preserveEvidence, "full reset must clear evidence ownership");
                assertFalse(session.accepts(initial), "callbacks from the old generation must stop");

                RecognitionWorkSession.State rescan = session.restartPreservingEvidence();
                assertEquals(reset.generation + 1, rescan.generation);
                assertTrue(rescan.preserveEvidence, "directed rescan must retain accepted evidence");
                assertTrue(session.accepts(rescan.generation), "new generation should accept callbacks");
            }
        });

        test("RecognitionWorkSession freezes confirmation and closes permanently", new TestCase() {
            @Override
            public void run() {
                RecognitionWorkSession session = new RecognitionWorkSession();
                int active = session.resetAll().generation;

                RecognitionWorkSession.State confirming = session.showConfirmation(true);
                assertEquals(active + 1, confirming.generation);
                assertEquals(RecognitionWorkSession.Phase.CONFIRMING, confirming.phase);
                assertFalse(session.accepts(active), "confirmation must reject active OCR callbacks");

                RecognitionWorkSession.State resumed = session.resumeScanner();
                assertEquals(RecognitionWorkSession.Phase.SCANNING, resumed.phase);
                assertTrue(session.accepts(resumed.generation), "scanner can reuse preserved evidence");

                RecognitionWorkSession.State closed = session.close();
                assertEquals(RecognitionWorkSession.Phase.CLOSED, closed.phase);
                assertFalse(session.accepts(closed.generation), "closed Activity must accept no callback");
                assertEquals(RecognitionWorkSession.Phase.CLOSED, session.resumeScanner().phase);
            }
        });

        System.out.println("Camera frame pipeline tests: "
                + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            throw new AssertionError("Camera frame pipeline tests failed: " + failed);
        }
    }

    private static LatestFrameProcessor<TrackedFrame> trackingProcessor(
            Executor executor,
            final List<String> processed,
            final List<Throwable> errors
    ) {
        return new LatestFrameProcessor<TrackedFrame>(
                executor,
                new LatestFrameProcessor.Processor<TrackedFrame>() {
                    @Override
                    public void process(LatestFrameProcessor.Frame<TrackedFrame> frame) {
                        processed.add(frame.value.id);
                    }
                },
                new LatestFrameProcessor.Releaser<TrackedFrame>() {
                    @Override
                    public void release(TrackedFrame value) {
                        value.releaseCount++;
                    }
                },
                errors == null
                        ? null
                        : new LatestFrameProcessor.ErrorListener() {
                            @Override
                            public void onError(Throwable error) {
                                errors.add(error);
                            }
                        }
        );
    }

    private void test(String name, TestCase body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable error) {
            failed++;
            System.err.println("FAIL " + name + ": " + error.getMessage());
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but got <" + actual + ">");
        }
    }

    private interface TestCase {
        void run();
    }

    private static final class TrackedFrame {
        final String id;
        int releaseCount;

        TrackedFrame(String id) {
            this.id = id;
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<Runnable>();

        @Override
        public void execute(Runnable command) {
            queue.addLast(command);
        }

        void runAll() {
            while (!queue.isEmpty()) {
                queue.removeFirst().run();
            }
        }
    }
}
