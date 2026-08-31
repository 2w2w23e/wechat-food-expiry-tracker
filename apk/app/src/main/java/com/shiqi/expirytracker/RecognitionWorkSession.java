package com.shiqi.expirytracker;

final class RecognitionWorkSession {
    enum Phase {
        SCANNING,
        CONFIRMING,
        CLOSED
    }

    static final class State {
        final int generation;
        final Phase phase;
        final boolean preserveEvidence;

        State(int generation, Phase phase, boolean preserveEvidence) {
            this.generation = generation;
            this.phase = phase;
            this.preserveEvidence = preserveEvidence;
        }
    }

    private int generation;
    private Phase phase = Phase.SCANNING;
    private boolean preserveEvidence;

    synchronized State resetAll() {
        generation++;
        phase = Phase.SCANNING;
        preserveEvidence = false;
        return state();
    }

    synchronized State restartPreservingEvidence() {
        generation++;
        phase = Phase.SCANNING;
        preserveEvidence = true;
        return state();
    }

    synchronized State showConfirmation(boolean hasActiveAsyncWork) {
        if (hasActiveAsyncWork) {
            generation++;
        }
        phase = Phase.CONFIRMING;
        preserveEvidence = true;
        return state();
    }

    synchronized State resumeScanner() {
        if (phase != Phase.CLOSED) {
            phase = Phase.SCANNING;
        }
        return state();
    }

    synchronized State close() {
        generation++;
        phase = Phase.CLOSED;
        preserveEvidence = false;
        return state();
    }

    synchronized State snapshot() {
        return state();
    }

    synchronized boolean accepts(int expectedGeneration) {
        return phase != Phase.CLOSED && expectedGeneration == generation;
    }

    private State state() {
        return new State(generation, phase, preserveEvidence);
    }
}
