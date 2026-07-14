package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class PackagingTextAnalyzer {
    private static final int MAX_CANDIDATES = 3;

    private PackagingTextAnalyzer() {}

    static List<Candidate> analyze(List<Observation> observations) {
        if (observations == null || observations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Aggregate> aggregates = new ArrayList<Aggregate>();
        for (Observation observation : observations) {
            if (observation == null) {
                continue;
            }
            String[] lines = FoodItem.cleanText(observation.text).split("\\r?\\n");
            for (String line : lines) {
                String candidateText = RecognitionTextCleaner.cleanProductNameLine(line);
                int lexicalScore = RecognitionTextCleaner.productNameScore(candidateText);
                if (lexicalScore <= 0) {
                    continue;
                }
                double score = scoreObservation(observation, lexicalScore);
                addEvidence(aggregates, candidateText, score, FoodItem.cleanText(line));
                for (String fragment : RecognitionTextCleaner.extractFoodNameFragments(candidateText)) {
                    int fragmentScore = RecognitionTextCleaner.productNameScore(fragment);
                    if (fragmentScore > 0) {
                        addEvidence(
                                aggregates,
                                fragment,
                                scoreObservation(observation, fragmentScore) + 70d,
                                FoodItem.cleanText(line)
                        );
                    }
                }
            }
        }

        List<Candidate> candidates = new ArrayList<Candidate>();
        for (Aggregate aggregate : aggregates) {
            candidates.add(aggregate.toCandidate());
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate left, Candidate right) {
                int scoreOrder = Double.compare(right.score, left.score);
                if (scoreOrder != 0) {
                    return scoreOrder;
                }
                int evidenceOrder = Integer.compare(right.votes, left.votes);
                if (evidenceOrder != 0) {
                    return evidenceOrder;
                }
                return left.text.compareTo(right.text);
            }
        });
        if (candidates.size() > MAX_CANDIDATES) {
            candidates = new ArrayList<Candidate>(candidates.subList(0, MAX_CANDIDATES));
        }
        return Collections.unmodifiableList(candidates);
    }

    private static double scoreObservation(Observation observation, int lexicalScore) {
        double height = clamp01(observation.normalizedHeight);
        double width = clamp01(observation.normalizedWidth);
        double centerDistance = Math.sqrt(
                square(clamp01(observation.centerX) - 0.5d)
                        + square(clamp01(observation.centerY) - 0.5d)
        ) / Math.sqrt(0.5d);
        double centrality = 1d - clamp01(centerDistance);
        double quality = clamp01(observation.sourceQuality);
        return lexicalScore
                + (height * 180d)
                + (Math.sqrt(width) * 24d)
                + (centrality * 12d)
                + (quality * 18d);
    }

    private static void addEvidence(
            List<Aggregate> aggregates,
            String text,
            double score,
            String evidence
    ) {
        Aggregate bestMatch = null;
        double bestSimilarity = 0d;
        for (Aggregate aggregate : aggregates) {
            double similarity = RecognitionTextCleaner.productNameSimilarity(aggregate.text, text);
            if (similarity >= 0.72d && similarity > bestSimilarity) {
                bestMatch = aggregate;
                bestSimilarity = similarity;
            }
        }
        if (bestMatch == null) {
            aggregates.add(new Aggregate(text, score, evidence));
        } else {
            bestMatch.add(text, score, evidence);
        }
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || value <= 0d) {
            return 0d;
        }
        return Math.min(1d, value);
    }

    static final class Observation {
        final String text;
        final double normalizedHeight;
        final double normalizedWidth;
        final double centerX;
        final double centerY;
        final double sourceQuality;

        Observation(
                String text,
                double normalizedHeight,
                double normalizedWidth,
                double centerX,
                double centerY,
                double sourceQuality
        ) {
            this.text = text == null ? "" : text;
            this.normalizedHeight = normalizedHeight;
            this.normalizedWidth = normalizedWidth;
            this.centerX = centerX;
            this.centerY = centerY;
            this.sourceQuality = sourceQuality;
        }
    }

    static final class Candidate {
        final String text;
        final double score;
        final int votes;
        final List<String> evidence;

        Candidate(String text, double score) {
            this(text, score, 1, Collections.<String>emptyList());
        }

        Candidate(String text, double score, int votes, List<String> evidence) {
            this.text = FoodItem.cleanText(text);
            this.score = Double.isNaN(score) || Double.isInfinite(score) ? 0d : Math.max(0d, score);
            this.votes = Math.max(0, votes);
            List<String> safeEvidence = evidence == null
                    ? Collections.<String>emptyList()
                    : new ArrayList<String>(evidence);
            this.evidence = Collections.unmodifiableList(safeEvidence);
        }
    }

    private static final class Aggregate {
        String text;
        double bestScore;
        final List<String> evidence = new ArrayList<String>();

        Aggregate(String text, double score, String firstEvidence) {
            this.text = text;
            this.bestScore = score;
            add(text, score, firstEvidence);
        }

        void add(String candidateText, double score, String sourceText) {
            if (score > bestScore || (score == bestScore && candidateText.length() > text.length())) {
                text = candidateText;
                bestScore = score;
            }
            if (sourceText.length() > 0 && !evidence.contains(sourceText)) {
                evidence.add(sourceText);
            }
        }

        Candidate toCandidate() {
            double evidenceBonus = Math.min(12d, Math.max(0, evidence.size() - 1) * 4d);
            return new Candidate(text, bestScore + evidenceBonus, Math.max(1, evidence.size()), evidence);
        }
    }
}
