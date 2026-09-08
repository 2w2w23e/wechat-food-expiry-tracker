package com.shiqi.expirytracker;

import java.util.concurrent.Executor;

final class LatestFrameProcessor<T> implements AutoCloseable {
    interface Processor<T> {
        void process(Frame<T> frame) throws Exception;
    }

    interface Releaser<T> {
        void release(T value);
    }

    interface ErrorListener {
        void onError(Throwable error);
    }

    static final class Frame<T> {
        final T value;
        final long timestampUs;
        final int generation;
        final long sequence;

        Frame(T value, long timestampUs, int generation, long sequence) {
            this.value = value;
            this.timestampUs = timestampUs;
            this.generation = generation;
            this.sequence = sequence;
        }
    }

    static final class OfferResult {
        final boolean accepted;
        final boolean replacedPending;
        final boolean scheduledWorker;

        OfferResult(boolean accepted, boolean replacedPending, boolean scheduledWorker) {
            this.accepted = accepted;
            this.replacedPending = replacedPending;
            this.scheduledWorker = scheduledWorker;
        }
    }

    private final Executor executor;
    private final Processor<T> processor;
    private final Releaser<T> releaser;
    private final ErrorListener errorListener;

    private Frame<T> pending;
    private boolean active;
    private boolean workerScheduled;
    private boolean processing;
    private int activeGeneration;
    private long workerEpoch;
    private long nextSequence;

    LatestFrameProcessor(
            Executor executor,
            Processor<T> processor,
            Releaser<T> releaser,
            ErrorListener errorListener
    ) {
        if (executor == null || processor == null || releaser == null) {
            throw new IllegalArgumentException("executor, processor and releaser are required");
        }
        this.executor = executor;
        this.processor = processor;
        this.releaser = releaser;
        this.errorListener = errorListener;
    }

    void start(int generation) {
        Frame<T> discarded;
        synchronized (this) {
            discarded = pending;
            pending = null;
            active = true;
            activeGeneration = generation;
            workerScheduled = false;
            processing = false;
            workerEpoch++;
        }
        release(discarded);
    }

    OfferResult offer(T value, long timestampUs, int generation) {
        if (value == null) {
            return new OfferResult(false, false, false);
        }

        Frame<T> replaced = null;
        boolean scheduleWorker = false;
        long epoch = 0L;
        synchronized (this) {
            if (!active || generation != activeGeneration) {
                releaseValue(value);
                return new OfferResult(false, false, false);
            }
            replaced = pending;
            pending = new Frame<T>(value, timestampUs, generation, ++nextSequence);
            if (!workerScheduled) {
                workerScheduled = true;
                scheduleWorker = true;
                epoch = workerEpoch;
            }
        }
        release(replaced);

        if (!scheduleWorker) {
            return new OfferResult(true, replaced != null, false);
        }

        final long scheduledEpoch = epoch;
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    drain(scheduledEpoch);
                }
            });
            return new OfferResult(true, replaced != null, true);
        } catch (RuntimeException error) {
            Frame<T> discarded = null;
            synchronized (this) {
                if (scheduledEpoch == workerEpoch) {
                    workerScheduled = false;
                    processing = false;
                    discarded = pending;
                    pending = null;
                }
            }
            release(discarded);
            report(error);
            return new OfferResult(false, replaced != null, false);
        }
    }

    synchronized boolean isBusy() {
        return processing || pending != null;
    }

    synchronized int activeGeneration() {
        return activeGeneration;
    }

    void stop() {
        Frame<T> discarded;
        synchronized (this) {
            discarded = pending;
            pending = null;
            active = false;
            workerScheduled = false;
            processing = false;
            workerEpoch++;
        }
        release(discarded);
    }

    @Override
    public void close() {
        stop();
    }

    private void drain(long epoch) {
        while (true) {
            Frame<T> frame;
            synchronized (this) {
                if (epoch != workerEpoch || !active) {
                    return;
                }
                frame = pending;
                pending = null;
                if (frame == null) {
                    workerScheduled = false;
                    processing = false;
                    return;
                }
                processing = true;
            }

            try {
                processor.process(frame);
            } catch (Exception error) {
                report(error);
            } finally {
                release(frame);
                synchronized (this) {
                    if (epoch == workerEpoch) {
                        processing = false;
                    }
                }
            }
        }
    }

    private void release(Frame<T> frame) {
        if (frame != null) {
            releaseValue(frame.value);
        }
    }

    private void releaseValue(T value) {
        try {
            releaser.release(value);
        } catch (RuntimeException error) {
            report(error);
        }
    }

    private void report(Throwable error) {
        if (errorListener == null || error == null) {
            return;
        }
        try {
            errorListener.onError(error);
        } catch (RuntimeException ignored) {
            // Diagnostics must not break frame cleanup.
        }
    }
}
