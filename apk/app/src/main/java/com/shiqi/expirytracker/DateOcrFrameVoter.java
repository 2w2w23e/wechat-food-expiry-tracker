package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DateOcrFrameVoter {
    static final int DEFAULT_MIN_VOTES = 2;

    private DateOcrFrameVoter() {}

    static VoteResult vote(List<DateOcrParser.Result> frames) {
        return vote(frames, DEFAULT_MIN_VOTES);
    }

    static VoteResult vote(List<DateOcrParser.Result> frames, int minVotes) {
        int requiredVotes = Math.max(1, minVotes);
        List<DateOcrParser.Result> safeFrames = frames == null
                ? Collections.<DateOcrParser.Result>emptyList()
                : frames;

        Map<String, DateAccumulator> productionDates = new LinkedHashMap<String, DateAccumulator>();
        Map<String, DateAccumulator> expiryDates = new LinkedHashMap<String, DateAccumulator>();
        Map<String, DateAccumulator> calculatedExpiryDates = new LinkedHashMap<String, DateAccumulator>();
        Map<String, ShelfLifeAccumulator> shelfLives = new LinkedHashMap<String, ShelfLifeAccumulator>();
        int framesWithCandidates = 0;

        for (int frameIndex = 0; frameIndex < safeFrames.size(); frameIndex++) {
            DateOcrParser.Result frame = safeFrames.get(frameIndex);
            if (frame == null) {
                continue;
            }
            if (frame.hasAnyCandidate()) {
                framesWithCandidates++;
            }

            addDateCandidates(productionDates, frame.productionDates, frameIndex);
            addDateCandidates(expiryDates, frame.expiryDates, frameIndex);
            addDateCandidates(calculatedExpiryDates, frame.calculatedExpiryDates, frameIndex);
            addShelfLifeCandidates(shelfLives, frame.shelfLives, frameIndex);
        }

        StableDate productionDate = stableDate(productionDates, requiredVotes);
        StableDate expiryDate = stableDate(expiryDates, requiredVotes);
        StableDate calculatedExpiryDate = stableDate(calculatedExpiryDates, requiredVotes);
        StableShelfLife shelfLife = stableShelfLife(shelfLives, requiredVotes);

        boolean conflict = hasDateConflict(productionDates, requiredVotes)
                || hasDateConflict(expiryDates, requiredVotes)
                || hasDateConflict(calculatedExpiryDates, requiredVotes)
                || hasShelfLifeConflict(shelfLives, requiredVotes);

        return new VoteResult(
                safeFrames.size(),
                framesWithCandidates,
                requiredVotes,
                productionDate,
                expiryDate,
                calculatedExpiryDate,
                shelfLife,
                conflict
        );
    }

    private static void addDateCandidates(
            Map<String, DateAccumulator> accumulators,
            List<DateOcrParser.DateCandidate> candidates,
            int frameIndex
    ) {
        for (DateOcrParser.DateCandidate candidate : candidates) {
            DateAccumulator accumulator = accumulators.get(candidate.normalized);
            if (accumulator == null) {
                accumulator = new DateAccumulator(candidate.type, candidate.normalized);
                accumulators.put(candidate.normalized, accumulator);
            }
            accumulator.add(candidate, frameIndex);
        }
    }

    private static void addShelfLifeCandidates(
            Map<String, ShelfLifeAccumulator> accumulators,
            List<DateOcrParser.ShelfLifeCandidate> candidates,
            int frameIndex
    ) {
        for (DateOcrParser.ShelfLifeCandidate candidate : candidates) {
            String key = candidate.value + "|" + candidate.unit;
            ShelfLifeAccumulator accumulator = accumulators.get(key);
            if (accumulator == null) {
                accumulator = new ShelfLifeAccumulator(candidate.value, candidate.unit);
                accumulators.put(key, accumulator);
            }
            accumulator.add(candidate, frameIndex);
        }
    }

    private static StableDate stableDate(Map<String, DateAccumulator> accumulators, int minVotes) {
        DateAccumulator best = bestDate(accumulators);
        if (best == null || best.votes < minVotes || hasTopVoteTie(accumulators, best.votes)) {
            return null;
        }
        return best.toStableDate();
    }

    private static StableShelfLife stableShelfLife(Map<String, ShelfLifeAccumulator> accumulators, int minVotes) {
        ShelfLifeAccumulator best = bestShelfLife(accumulators);
        if (best == null || best.votes < minVotes || hasTopShelfLifeTie(accumulators, best.votes)) {
            return null;
        }
        return best.toStableShelfLife();
    }

    private static DateAccumulator bestDate(Map<String, DateAccumulator> accumulators) {
        List<DateAccumulator> values = new ArrayList<DateAccumulator>(accumulators.values());
        Collections.sort(values, new Comparator<DateAccumulator>() {
            @Override
            public int compare(DateAccumulator left, DateAccumulator right) {
                int votes = Integer.valueOf(right.votes).compareTo(Integer.valueOf(left.votes));
                if (votes != 0) {
                    return votes;
                }
                int score = Double.compare(right.score, left.score);
                return score != 0 ? score : left.value.compareTo(right.value);
            }
        });
        return values.isEmpty() ? null : values.get(0);
    }

    private static ShelfLifeAccumulator bestShelfLife(Map<String, ShelfLifeAccumulator> accumulators) {
        List<ShelfLifeAccumulator> values = new ArrayList<ShelfLifeAccumulator>(accumulators.values());
        Collections.sort(values, new Comparator<ShelfLifeAccumulator>() {
            @Override
            public int compare(ShelfLifeAccumulator left, ShelfLifeAccumulator right) {
                int votes = Integer.valueOf(right.votes).compareTo(Integer.valueOf(left.votes));
                if (votes != 0) {
                    return votes;
                }
                int score = Double.compare(right.score, left.score);
                if (score != 0) {
                    return score;
                }
                return left.label().compareTo(right.label());
            }
        });
        return values.isEmpty() ? null : values.get(0);
    }

    private static boolean hasDateConflict(Map<String, DateAccumulator> accumulators, int minVotes) {
        int contenders = 0;
        for (DateAccumulator accumulator : accumulators.values()) {
            if (accumulator.votes >= minVotes) {
                contenders++;
            }
        }
        return contenders > 1;
    }

    private static boolean hasShelfLifeConflict(Map<String, ShelfLifeAccumulator> accumulators, int minVotes) {
        int contenders = 0;
        for (ShelfLifeAccumulator accumulator : accumulators.values()) {
            if (accumulator.votes >= minVotes) {
                contenders++;
            }
        }
        return contenders > 1;
    }

    private static boolean hasTopVoteTie(Map<String, DateAccumulator> accumulators, int topVotes) {
        int tied = 0;
        for (DateAccumulator accumulator : accumulators.values()) {
            if (accumulator.votes == topVotes) {
                tied++;
            }
        }
        return tied > 1;
    }

    private static boolean hasTopShelfLifeTie(Map<String, ShelfLifeAccumulator> accumulators, int topVotes) {
        int tied = 0;
        for (ShelfLifeAccumulator accumulator : accumulators.values()) {
            if (accumulator.votes == topVotes) {
                tied++;
            }
        }
        return tied > 1;
    }

    private static double confidenceFromScore(double score, int votes) {
        if (votes <= 0) {
            return 0.0d;
        }
        return Math.min(0.99d, score / votes);
    }

    private static final class DateAccumulator {
        final String type;
        final String value;
        int votes = 0;
        double score = 0.0d;
        boolean weakHint = false;
        boolean calculated = false;
        String bestRaw = "";
        String bestContext = "";
        double bestConfidence = 0.0d;
        private int lastFrameIndex = -1;

        DateAccumulator(String type, String value) {
            this.type = type;
            this.value = value;
        }

        void add(DateOcrParser.DateCandidate candidate, int frameIndex) {
            if (frameIndex != lastFrameIndex) {
                votes++;
                lastFrameIndex = frameIndex;
            }
            score += candidate.confidence;
            weakHint = weakHint || candidate.weakHint;
            calculated = calculated || candidate.calculated;
            if (bestContext.length() == 0 || candidate.confidence > bestConfidence) {
                bestRaw = candidate.raw;
                bestContext = candidate.context;
                bestConfidence = candidate.confidence;
            }
        }

        StableDate toStableDate() {
            return new StableDate(
                    type,
                    value,
                    bestRaw,
                    bestContext,
                    votes,
                    confidenceFromScore(score, votes),
                    weakHint,
                    calculated
            );
        }
    }

    private static final class ShelfLifeAccumulator {
        final int value;
        final String unit;
        int votes = 0;
        double score = 0.0d;
        String bestRaw = "";
        String bestContext = "";
        double bestConfidence = 0.0d;
        private int lastFrameIndex = -1;

        ShelfLifeAccumulator(int value, String unit) {
            this.value = value;
            this.unit = unit;
        }

        void add(DateOcrParser.ShelfLifeCandidate candidate, int frameIndex) {
            if (frameIndex != lastFrameIndex) {
                votes++;
                lastFrameIndex = frameIndex;
            }
            score += candidate.confidence;
            if (bestContext.length() == 0 || candidate.confidence > bestConfidence) {
                bestRaw = candidate.raw;
                bestContext = candidate.context;
                bestConfidence = candidate.confidence;
            }
        }

        String label() {
            return String.format(Locale.US, "%d|%s", value, unit);
        }

        StableShelfLife toStableShelfLife() {
            return new StableShelfLife(
                    value,
                    unit,
                    bestRaw,
                    bestContext,
                    votes,
                    confidenceFromScore(score, votes)
            );
        }
    }

    static final class VoteResult {
        final int frameCount;
        final int framesWithCandidates;
        final int minVotes;
        final StableDate productionDate;
        final StableDate expiryDate;
        final StableDate calculatedExpiryDate;
        final StableShelfLife shelfLife;
        final boolean hasConflict;
        final boolean candidateOnly = true;

        VoteResult(
                int frameCount,
                int framesWithCandidates,
                int minVotes,
                StableDate productionDate,
                StableDate expiryDate,
                StableDate calculatedExpiryDate,
                StableShelfLife shelfLife,
                boolean hasConflict
        ) {
            this.frameCount = frameCount;
            this.framesWithCandidates = framesWithCandidates;
            this.minVotes = minVotes;
            this.productionDate = productionDate;
            this.expiryDate = expiryDate;
            this.calculatedExpiryDate = calculatedExpiryDate;
            this.shelfLife = shelfLife;
            this.hasConflict = hasConflict;
        }

        boolean hasStableCandidate() {
            return productionDate != null
                    || expiryDate != null
                    || calculatedExpiryDate != null
                    || shelfLife != null;
        }

        boolean readyForUserConfirmation() {
            return hasStableCandidate() && !hasConflict;
        }
    }

    static final class StableDate {
        final String type;
        final String value;
        final String raw;
        final String context;
        final int votes;
        final double confidence;
        final boolean weakHint;
        final boolean calculated;
        final boolean candidateOnly = true;

        StableDate(
                String type,
                String value,
                String raw,
                String context,
                int votes,
                double confidence,
                boolean weakHint,
                boolean calculated
        ) {
            this.type = type;
            this.value = value;
            this.raw = raw;
            this.context = context;
            this.votes = votes;
            this.confidence = confidence;
            this.weakHint = weakHint;
            this.calculated = calculated;
        }
    }

    static final class StableShelfLife {
        final int value;
        final String unit;
        final String raw;
        final String context;
        final int votes;
        final double confidence;
        final boolean candidateOnly = true;

        StableShelfLife(String value, String unit, String raw, String context, int votes, double confidence) {
            this(Integer.parseInt(value), unit, raw, context, votes, confidence);
        }

        StableShelfLife(int value, String unit, String raw, String context, int votes, double confidence) {
            this.value = value;
            this.unit = unit;
            this.raw = raw;
            this.context = context;
            this.votes = votes;
            this.confidence = confidence;
        }
    }
}
