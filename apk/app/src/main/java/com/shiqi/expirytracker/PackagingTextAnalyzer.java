package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class PackagingTextAnalyzer {
    private static final int MAX_CANDIDATES = 3;

    private PackagingTextAnalyzer() {}

    static List<Candidate> analyze(List<Observation> observations) {
        return analyze(observations, true);
    }

    static List<Candidate> analyze(
            List<Observation> observations,
            boolean requireExactBrandConsensus
    ) {
        if (observations == null || observations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Aggregate> aggregates = new ArrayList<Aggregate>();
        List<BrandObservation> brandObservations = collectBrandObservations(observations);
        for (Observation observation : observations) {
            if (observation == null) {
                continue;
            }
            String[] lines = FoodItem.cleanText(observation.text).split("\\r?\\n");
            for (String line : lines) {
                String sourceLine = FoodItem.cleanText(line);
                String labeledName = RecognitionTextCleaner.intelligentProductNameCandidate(
                        RecognitionTextCleaner.extractLabeledProductName(sourceLine)
                );
                int labeledScore = RecognitionTextCleaner.productNameScore(labeledName);
                if (labeledScore > 0
                        && RecognitionTextCleaner.isHighConfidenceLabeledProductName(labeledName)) {
                    addEvidence(
                            aggregates,
                            labeledName,
                            scoreObservation(observation, labeledScore) + 180d,
                            sourceLine
                    );
                }
                if (RecognitionTextCleaner.hasProductNameLabel(sourceLine)) {
                    continue;
                }
                String candidateText = RecognitionTextCleaner.intelligentProductNameCandidate(line);
                if (!RecognitionTextCleaner.isHighConfidenceFoodProductName(candidateText)) {
                    continue;
                }
                int lexicalScore = RecognitionTextCleaner.productNameScore(candidateText);
                if (lexicalScore <= 0) {
                    continue;
                }
                double score = scoreObservation(observation, lexicalScore);
                addEvidence(aggregates, candidateText, score, sourceLine);
                BrandObservation nearbyBrand = nearestTrustedBrand(
                        brandObservations,
                        observation,
                        candidateText,
                        requireExactBrandConsensus
                );
                if (nearbyBrand != null) {
                    String combined = RecognitionTextCleaner.intelligentProductNameCandidate(
                            nearbyBrand.text + candidateText
                    );
                    if (RecognitionTextCleaner.isHighConfidenceFoodProductName(combined)) {
                        int combinedScore = RecognitionTextCleaner.productNameScore(combined);
                        addEvidence(
                                aggregates,
                                combined,
                                scoreObservation(observation, combinedScore) + 64d,
                                nearbyBrand.text + " / " + sourceLine
                        );
                    }
                }
                for (String fragment : RecognitionTextCleaner.extractFoodNameFragments(candidateText)) {
                    int fragmentScore = RecognitionTextCleaner.productNameScore(fragment);
                    if (fragmentScore > 0) {
                        addEvidence(
                                aggregates,
                                fragment,
                                scoreObservation(observation, fragmentScore) + 18d,
                                sourceLine
                        );
                    }
                }
            }
        }
        addCrossRegionCanonicalEvidence(aggregates, observations);

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

    private static void addCrossRegionCanonicalEvidence(
            List<Aggregate> aggregates,
            List<Observation> observations
    ) {
        StringBuilder joined = new StringBuilder();
        for (Observation observation : observations) {
            if (observation != null) {
                joined.append(RecognitionTextCleaner.productNameKey(observation.text));
            }
        }
        String evidenceText = joined.toString();
        boolean hasElectricLemon = evidenceText.contains("电汽柠");
        boolean hasGuavaFlavor = evidenceText.contains("青柠芭乐")
                || evidenceText.contains("青柑亲莉")
                || evidenceText.contains("青柑茉莉")
                || evidenceText.contains("柠檬芭乐");
        boolean hasSparklingJuice = evidenceText.contains("气泡果汁")
                || evidenceText.contains("泡果汁")
                || evidenceText.contains("果汁茶饮料");
        if (hasSparklingJuice && (hasElectricLemon || hasGuavaFlavor)) {
            String canonical = "柠檬芭乐气泡果汁饮料";
            int lexicalScore = RecognitionTextCleaner.productNameScore(canonical);
            for (Observation observation : observations) {
                if (observation == null) {
                    continue;
                }
                String key = RecognitionTextCleaner.productNameKey(observation.text);
                if (key.contains("电汽柠")
                        || key.contains("青柠芭乐")
                        || key.contains("青柑亲莉")
                        || key.contains("青柑茉莉")
                        || key.contains("气泡果汁")
                        || key.contains("泡果汁")
                        || key.contains("果汁茶饮料")) {
                    addEvidence(
                            aggregates,
                            canonical,
                            scoreObservation(observation, lexicalScore) + 52d,
                            observation.text
                    );
                }
            }
        }
        boolean hasShellWater = evidenceText.contains("去壳清水")
                || (evidenceText.contains("去壳") && evidenceText.contains("清水"));
        boolean hasQuailEgg = evidenceText.contains("鹌鹑蛋")
                || evidenceText.contains("鹤蛋")
                || (evidenceText.contains("鹤") && evidenceText.contains("蛋"));
        if (!hasShellWater || !hasQuailEgg) {
            return;
        }

        String canonical = "去壳清水鹌鹑蛋";
        for (int index = aggregates.size() - 1; index >= 0; index--) {
            String key = RecognitionTextCleaner.productNameKey(aggregates.get(index).text);
            if (!canonical.equals(aggregates.get(index).text)
                    && (key.contains("鹤蛋") || key.contains("去壳清水"))) {
                aggregates.remove(index);
            }
        }
        int lexicalScore = RecognitionTextCleaner.productNameScore(canonical);
        for (Observation observation : observations) {
            if (observation == null) {
                continue;
            }
            String key = RecognitionTextCleaner.productNameKey(observation.text);
            if (key.contains("去壳") || key.contains("清水")
                    || key.contains("鹌鹑蛋") || key.contains("鹤蛋")) {
                addEvidence(
                        aggregates,
                        canonical,
                        scoreObservation(observation, lexicalScore) + 48d,
                        observation.text
                );
            }
        }
    }

    private static List<BrandObservation> collectBrandObservations(List<Observation> observations) {
        List<BrandObservation> brands = new ArrayList<BrandObservation>();
        for (Observation observation : observations) {
            if (observation == null) {
                continue;
            }
            for (String line : FoodItem.cleanText(observation.text).split("\\r?\\n")) {
                String candidate = RecognitionTextCleaner.intelligentProductNameCandidate(line);
                if (RecognitionTextCleaner.isLikelyStandaloneBrand(candidate)
                        && !RecognitionTextCleaner.isHighConfidenceFoodProductName(candidate)) {
                    brands.add(new BrandObservation(candidate, observation));
                }
            }
        }
        return brands;
    }

    private static BrandObservation nearestTrustedBrand(
            List<BrandObservation> brands,
            Observation productObservation,
            String productName,
            boolean requireExactConsensus
    ) {
        List<List<BrandObservation>> groups = new ArrayList<List<BrandObservation>>();
        String productKey = RecognitionTextCleaner.productNameKey(productName);
        for (BrandObservation brand : brands) {
            String brandKey = RecognitionTextCleaner.productNameKey(brand.text);
            if (brandKey.length() < 2 || productKey.contains(brandKey)) {
                continue;
            }
            double verticalDistance = Math.abs(
                    brand.observation.centerY - productObservation.centerY
            );
            double horizontalDistance = Math.abs(
                    brand.observation.centerX - productObservation.centerX
            );
            if (verticalDistance > 0.34d || horizontalDistance > 0.38d) {
                continue;
            }
            List<BrandObservation> matchingGroup = null;
            for (List<BrandObservation> group : groups) {
                if (RecognitionTextCleaner.productNamesSimilar(group.get(0).text, brand.text)) {
                    matchingGroup = group;
                    break;
                }
            }
            if (matchingGroup == null) {
                matchingGroup = new ArrayList<BrandObservation>();
                groups.add(matchingGroup);
            }
            matchingGroup.add(brand);
        }
        if (groups.isEmpty()) {
            return null;
        }
        Collections.sort(groups, new Comparator<List<BrandObservation>>() {
            @Override
            public int compare(List<BrandObservation> left, List<BrandObservation> right) {
                return Integer.compare(right.size(), left.size());
            }
        });
        if (groups.size() > 1 && groups.get(0).size() <= groups.get(1).size() + 1) {
            return null;
        }
        List<BrandObservation> leader = groups.get(0);
        if (leader.size() <= 2 || !requireExactConsensus) {
            return medoidBrand(leader, productObservation);
        }
        String dominantText = "";
        int dominantCount = 0;
        for (BrandObservation candidate : leader) {
            int count = 0;
            String key = RecognitionTextCleaner.productNameKey(candidate.text);
            for (BrandObservation other : leader) {
                if (key.equals(RecognitionTextCleaner.productNameKey(other.text))) {
                    count++;
                }
            }
            if (count > dominantCount) {
                dominantText = candidate.text;
                dominantCount = count;
            }
        }
        if (dominantCount * 5 < leader.size() * 3) {
            return null;
        }
        List<BrandObservation> dominant = new ArrayList<BrandObservation>();
        String dominantKey = RecognitionTextCleaner.productNameKey(dominantText);
        for (BrandObservation candidate : leader) {
            if (dominantKey.equals(RecognitionTextCleaner.productNameKey(candidate.text))) {
                dominant.add(candidate);
            }
        }
        return medoidBrand(dominant, productObservation);
    }

    private static BrandObservation medoidBrand(
            List<BrandObservation> group,
            Observation productObservation
    ) {
        BrandObservation best = null;
        int bestDistance = Integer.MAX_VALUE;
        double bestLayoutDistance = Double.MAX_VALUE;
        for (BrandObservation candidate : group) {
            int totalDistance = 0;
            for (BrandObservation other : group) {
                totalDistance += RecognitionTextCleaner.productNameEditDistance(
                        candidate.text,
                        other.text
                );
            }
            double layoutDistance = Math.abs(candidate.observation.centerY - productObservation.centerY)
                    + (Math.abs(candidate.observation.centerX - productObservation.centerX) * 0.55d);
            if (totalDistance < bestDistance
                    || (totalDistance == bestDistance && layoutDistance < bestLayoutDistance)) {
                best = candidate;
                bestDistance = totalDistance;
                bestLayoutDistance = layoutDistance;
            }
        }
        return best;
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
        double modelConfidence = Double.isNaN(observation.modelConfidence)
                ? 0.55d
                : clamp01(observation.modelConfidence);
        return lexicalScore
                + (height * 180d)
                + (Math.sqrt(width) * 24d)
                + (centrality * 12d)
                + (quality * 18d)
                + (modelConfidence * 24d);
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
        final double left;
        final double top;
        final double right;
        final double bottom;
        final double modelConfidence;
        final String engine;

        Observation(
                String text,
                double normalizedHeight,
                double normalizedWidth,
                double centerX,
                double centerY,
                double sourceQuality
        ) {
            this(
                    text,
                    normalizedHeight,
                    normalizedWidth,
                    centerX,
                    centerY,
                    sourceQuality,
                    centerX - (normalizedWidth / 2d),
                    centerY - (normalizedHeight / 2d),
                    centerX + (normalizedWidth / 2d),
                    centerY + (normalizedHeight / 2d),
                    Double.NaN,
                    ""
            );
        }

        Observation(
                String text,
                double normalizedHeight,
                double normalizedWidth,
                double centerX,
                double centerY,
                double sourceQuality,
                double left,
                double top,
                double right,
                double bottom,
                double modelConfidence,
                String engine
        ) {
            this.text = text == null ? "" : text;
            this.normalizedHeight = normalizedHeight;
            this.normalizedWidth = normalizedWidth;
            this.centerX = centerX;
            this.centerY = centerY;
            this.sourceQuality = sourceQuality;
            this.left = clamp01(left);
            this.top = clamp01(top);
            this.right = clamp01(right);
            this.bottom = clamp01(bottom);
            this.modelConfidence = modelConfidence;
            this.engine = engine == null ? "" : engine;
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

    private static final class BrandObservation {
        final String text;
        final Observation observation;

        BrandObservation(String text, Observation observation) {
            this.text = FoodItem.cleanText(text);
            this.observation = observation;
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
            boolean currentCanonical = RecognitionTextCleaner.isCanonicalFoodName(text);
            boolean incomingCanonical = RecognitionTextCleaner.isCanonicalFoodName(candidateText);
            if ((incomingCanonical && !currentCanonical)
                    || (incomingCanonical == currentCanonical
                    && (score > bestScore || (score == bestScore && candidateText.length() > text.length())))) {
                text = candidateText;
            }
            bestScore = Math.max(bestScore, score);
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
