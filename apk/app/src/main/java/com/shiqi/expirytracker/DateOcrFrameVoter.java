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

            addDateCandidates(productionDates, frame.productionDates, frame, frameIndex, true);
            addDateCandidates(expiryDates, frame.expiryDates, frame, frameIndex, false);
            addDateCandidates(calculatedExpiryDates, frame.calculatedExpiryDates, frame, frameIndex, false);
            addShelfLifeCandidates(shelfLives, frame.shelfLives, frameIndex);
        }

        StableDatePair strongPair = stableStrongDatePair(safeFrames, Math.min(requiredVotes, 2));
        StableDate productionDate = strongPair == null
                ? stableDate(productionDates, requiredVotes)
                : strongPair.productionDate;
        StableDate expiryDate = strongPair == null
                ? stableDate(expiryDates, requiredVotes)
                : strongPair.expiryDate;
        int shelfLifeRequiredVotes = productionDate == null ? requiredVotes : 1;
        StableShelfLife shelfLife = stableShelfLife(shelfLives, shelfLifeRequiredVotes);
        StableDate calculatedExpiryDate = stableDate(calculatedExpiryDates, requiredVotes);
        if (calculatedExpiryDate == null && productionDate != null && shelfLife != null) {
            String calculated = DateRules.addShelfLife(
                    productionDate.value,
                    Integer.valueOf(shelfLife.value),
                    shelfLife.unit
            );
            if (DateRules.isValidDateString(calculated)) {
                calculatedExpiryDate = new StableDate(
                        "calculatedExpiryDate",
                        calculated,
                        productionDate.raw + " + " + shelfLife.raw,
                        productionDate.context + " | " + shelfLife.context,
                        Math.min(productionDate.votes, shelfLife.votes),
                        Math.min(productionDate.confidence, shelfLife.confidence),
                        productionDate.weakHint,
                        true
                );
            }
        }

        boolean conflict = hasDateConflict(productionDates, requiredVotes)
                || hasDateConflict(expiryDates, requiredVotes)
                || hasDateConflict(calculatedExpiryDates, requiredVotes)
                || hasShelfLifeConflict(shelfLives, shelfLifeRequiredVotes);

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
            DateOcrParser.Result frame,
            int frameIndex,
            boolean productionEvidence
    ) {
        for (DateOcrParser.DateCandidate candidate : candidates) {
            DateAccumulator accumulator = accumulators.get(candidate.normalized);
            if (accumulator == null) {
                accumulator = new DateAccumulator(candidate.type, candidate.normalized);
                accumulators.put(candidate.normalized, accumulator);
            }
            int evidenceVotes = productionEvidence
                    ? Math.max(1, frame.productionDateEvidenceCount(candidate.normalized))
                    : 1;
            accumulator.add(candidate, frameIndex, evidenceVotes);
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

    private static StableDatePair stableStrongDatePair(
            List<DateOcrParser.Result> frames,
            int minVotes
    ) {
        Map<String, DatePairAccumulator> pairs = new LinkedHashMap<String, DatePairAccumulator>();
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            DateOcrParser.Result frame = frames.get(frameIndex);
            if (frame == null) {
                continue;
            }
            DateOcrParser.DateCandidate production = firstStrongDate(frame.productionDates);
            DateOcrParser.DateCandidate expiry = firstStrongDate(frame.expiryDates);
            if (production == null || expiry == null
                    || production.normalized.compareTo(expiry.normalized) >= 0) {
                continue;
            }
            String key = production.normalized + "|" + expiry.normalized;
            DatePairAccumulator pair = pairs.get(key);
            if (pair == null) {
                pair = new DatePairAccumulator(production.normalized, expiry.normalized);
                pairs.put(key, pair);
            }
            pair.add(
                    production,
                    expiry,
                    frameIndex,
                    Math.max(1, Math.min(2, frame.strongDatePairEvidenceCount()))
            );
        }

        DatePairAccumulator best = null;
        boolean tied = false;
        for (DatePairAccumulator pair : pairs.values()) {
            if (best == null || pair.votes > best.votes
                    || (pair.votes == best.votes && pair.score > best.score)) {
                best = pair;
                tied = false;
            } else if (best != null && pair.votes == best.votes && pair.score == best.score) {
                tied = true;
            }
        }
        return best == null || tied || best.votes < Math.max(2, minVotes)
                ? null
                : best.toStableDatePair();
    }

    private static DateOcrParser.DateCandidate firstStrongDate(
            List<DateOcrParser.DateCandidate> candidates
    ) {
        for (DateOcrParser.DateCandidate candidate : candidates) {
            if (!candidate.weakHint) {
                return candidate;
            }
        }
        return null;
    }

    private static StableDate stableDate(Map<String, DateAccumulator> accumulators, int minVotes) {
        DateAccumulator best = bestDate(accumulators, minVotes);
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

    private static DateAccumulator bestDate(
            Map<String, DateAccumulator> accumulators,
            final int minVotes
    ) {
        List<DateAccumulator> values = new ArrayList<DateAccumulator>(accumulators.values());
        Collections.sort(values, new Comparator<DateAccumulator>() {
            @Override
            public int compare(DateAccumulator left, DateAccumulator right) {
                boolean leftStrong = left.strongVotes >= minVotes;
                boolean rightStrong = right.strongVotes >= minVotes;
                if (leftStrong != rightStrong) {
                    return leftStrong ? -1 : 1;
                }
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
        boolean hasStrongCandidate = false;
        for (DateAccumulator accumulator : accumulators.values()) {
            if (accumulator.strongVotes >= minVotes) {
                hasStrongCandidate = true;
                break;
            }
        }
        int contenders = 0;
        for (DateAccumulator accumulator : accumulators.values()) {
            if (hasStrongCandidate
                    ? accumulator.strongVotes >= minVotes
                    : accumulator.votes >= minVotes) {
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
        int strongVotes = 0;
        boolean calculated = false;
        String bestRaw = "";
        String bestContext = "";
        double bestConfidence = 0.0d;
        private int lastFrameIndex = -1;
        private int lastStrongFrameIndex = -1;

        DateAccumulator(String type, String value) {
            this.type = type;
            this.value = value;
        }

        void add(DateOcrParser.DateCandidate candidate, int frameIndex, int evidenceVotes) {
            if (frameIndex != lastFrameIndex) {
                votes += Math.max(1, evidenceVotes);
                lastFrameIndex = frameIndex;
            }
            score += candidate.confidence;
            if (!candidate.weakHint && frameIndex != lastStrongFrameIndex) {
                strongVotes += Math.max(1, evidenceVotes);
                lastStrongFrameIndex = frameIndex;
            }
            weakHint = strongVotes == 0;
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

    private static final class DatePairAccumulator {
        final String productionValue;
        final String expiryValue;
        int votes = 0;
        double score = 0.0d;
        String productionRaw = "";
        String expiryRaw = "";
        String context = "";
        private int lastFrameIndex = -1;

        DatePairAccumulator(String productionValue, String expiryValue) {
            this.productionValue = productionValue;
            this.expiryValue = expiryValue;
        }

        void add(
                DateOcrParser.DateCandidate production,
                DateOcrParser.DateCandidate expiry,
                int frameIndex,
                int evidenceVotes
        ) {
            if (frameIndex != lastFrameIndex) {
                votes += evidenceVotes;
                lastFrameIndex = frameIndex;
            }
            score += production.confidence + expiry.confidence;
            if (productionRaw.length() == 0) {
                productionRaw = production.raw;
                expiryRaw = expiry.raw;
                context = production.context;
            }
        }

        StableDatePair toStableDatePair() {
            double confidence = confidenceFromScore(score / 2.0d, votes);
            return new StableDatePair(
                    new StableDate("productionDate", productionValue, productionRaw, context,
                            votes, confidence, false, false),
                    new StableDate("expiryDate", expiryValue, expiryRaw, context,
                            votes, confidence, false, false)
            );
        }
    }

    private static final class StableDatePair {
        final StableDate productionDate;
        final StableDate expiryDate;

        StableDatePair(StableDate productionDate, StableDate expiryDate) {
            this.productionDate = productionDate;
            this.expiryDate = expiryDate;
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
