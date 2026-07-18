package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class DateEvidencePolicy {
    private DateEvidencePolicy() {}

    static DateOcrParser.Result chooseVideoCompletionEvidence(
            String latestFrameText,
            DateOcrParser.Result earlierFallback
    ) {
        DateOcrParser.Result latest = apply(
                DateOcrParser.parse(FoodItem.cleanText(latestFrameText)),
                "",
                false
        );
        latest = retainDominantProductionDate(latest);
        DateOcrFrameVoter.VoteResult direct = DateOcrFrameVoter.vote(
                Collections.singletonList(latest),
                1
        );
        if (UnifiedRecognitionStabilizer.isReliableDirectDatePair(direct)) {
            int spanDays = DateRules.daysBetween(
                    direct.productionDate.value,
                    direct.expiryDate.value
            );
            if (spanDays >= 0 && spanDays <= (366 * 5)) {
                return latest;
            }
        }
        if (isReliableCalculatedDate(direct)) {
            return latest;
        }
        return earlierFallback == null ? latest : earlierFallback;
    }

    private static DateOcrParser.Result retainDominantProductionDate(
            DateOcrParser.Result parsed
    ) {
        if (parsed == null || parsed.productionDates.size() <= 1) {
            return parsed;
        }
        DateOcrParser.DateCandidate best = null;
        int bestCount = 0;
        int runnerUpCount = 0;
        for (DateOcrParser.DateCandidate candidate : parsed.productionDates) {
            int count = parsed.productionDateEvidenceCount(candidate.normalized);
            if (best == null
                    || count > bestCount
                    || (count == bestCount && candidate.confidence > best.confidence)) {
                runnerUpCount = bestCount;
                best = candidate;
                bestCount = count;
            } else {
                runnerUpCount = Math.max(runnerUpCount, count);
            }
        }
        if (best == null || bestCount < 2 || bestCount <= runnerUpCount) {
            return parsed;
        }
        List<DateOcrParser.DateCandidate> productionDates =
                Collections.singletonList(best);
        List<DateOcrParser.DateCandidate> calculatedExpiryDates =
                validatedCalculatedExpiryDates(
                        parsed.calculatedExpiryDates,
                        productionDates,
                        parsed.shelfLives
                );
        return new DateOcrParser.Result(
                parsed.rawText,
                parsed.normalizedText,
                productionDates,
                parsed.expiryDates,
                parsed.shelfLives,
                calculatedExpiryDates
        );
    }

    private static boolean isReliableCalculatedDate(DateOcrFrameVoter.VoteResult vote) {
        return vote != null
                && !vote.hasConflict
                && vote.productionDate != null
                && vote.shelfLife != null
                && vote.calculatedExpiryDate != null;
    }

    static DateOcrParser.Result apply(
            DateOcrParser.Result parsed,
            String preferredOriginalText
    ) {
        return apply(parsed, preferredOriginalText, true);
    }

    static DateOcrParser.Result apply(
            DateOcrParser.Result parsed,
            String preferredOriginalText,
            boolean allowPreferredOverride
    ) {
        if (parsed == null) {
            return DateOcrParser.parse("");
        }

        String today = DateRules.getTodayString();
        List<DateOcrParser.DateCandidate> productionDates =
                new ArrayList<DateOcrParser.DateCandidate>();
        for (DateOcrParser.DateCandidate candidate : parsed.productionDates) {
            if (DateRules.isValidDateString(candidate.normalized)
                    && candidate.normalized.compareTo(today) <= 0) {
                productionDates.add(candidate);
            }
        }

        DateOcrParser.Result preferred = DateOcrParser.parse(
                allowPreferredOverride ? FoodItem.cleanText(preferredOriginalText) : ""
        );
        List<DateOcrParser.DateCandidate> expiryDates = preferredExpiryCandidates(
                parsed.expiryDates,
                preferred
        );
        removeWeakMonthOnlyDuplicates(productionDates, expiryDates);
        removeCrossFieldDuplicates(productionDates, expiryDates, preferred, today);
        removeWeakContradictions(productionDates, expiryDates);
        removeImplausibleFoodExpiryDates(productionDates, expiryDates);

        List<DateOcrParser.DateCandidate> calculatedExpiryDates =
                validatedCalculatedExpiryDates(
                        parsed.calculatedExpiryDates,
                        productionDates,
                        parsed.shelfLives
                );
        return new DateOcrParser.Result(
                parsed.rawText,
                parsed.normalizedText,
                productionDates,
                expiryDates,
                parsed.shelfLives,
                calculatedExpiryDates
        );
    }

    static DateOcrParser.Result reconcileIndependentExpiryEvidence(
            DateOcrParser.Result parsed,
            String independentOriginalText,
            double independentConfidence
    ) {
        if (parsed == null
                || independentConfidence < 0.75d
                || FoodItem.cleanText(independentOriginalText).length() == 0
                || parsed.productionDates.isEmpty()
                || parsed.expiryDates.isEmpty()) {
            return parsed;
        }

        DateOcrParser.Result independent = DateOcrParser.parse(independentOriginalText);
        DateOcrParser.DateCandidate independentExpiry = onlyDistinctFutureExpiry(
                independent.expiryDates,
                DateRules.getTodayString()
        );
        if (independentExpiry == null
                || !hasEarlierProduction(parsed.productionDates, independentExpiry.normalized)) {
            return parsed;
        }

        List<DateOcrParser.DateCandidate> expiryDates =
                new ArrayList<DateOcrParser.DateCandidate>();
        DateOcrParser.DateCandidate semanticTemplate = null;
        boolean foundNearMiss = false;
        for (DateOcrParser.DateCandidate candidate : parsed.expiryDates) {
            if (candidate.normalized.equals(independentExpiry.normalized)) {
                semanticTemplate = strongerCandidate(semanticTemplate, candidate);
                continue;
            }
            int difference = Math.abs(DateRules.daysBetween(
                    candidate.normalized,
                    independentExpiry.normalized
            ));
            if (difference > 0 && difference <= 2) {
                foundNearMiss = true;
                semanticTemplate = strongerCandidate(semanticTemplate, candidate);
                continue;
            }
            expiryDates.add(candidate);
        }
        if (!foundNearMiss) {
            return parsed;
        }

        double confidence = Math.min(
                0.94d,
                Math.max(independentConfidence,
                        semanticTemplate == null ? 0d : semanticTemplate.confidence)
        );
        expiryDates.add(new DateOcrParser.DateCandidate(
                "expiryDate",
                independentExpiry.raw,
                independentExpiry.normalized,
                semanticTemplate == null
                        ? independentExpiry.context
                        : semanticTemplate.context,
                confidence,
                semanticTemplate == null || semanticTemplate.weakHint,
                false
        ));

        return new DateOcrParser.Result(
                parsed.rawText,
                parsed.normalizedText,
                parsed.productionDates,
                expiryDates,
                parsed.shelfLives,
                parsed.calculatedExpiryDates
        );
    }

    private static DateOcrParser.DateCandidate onlyDistinctFutureExpiry(
            List<DateOcrParser.DateCandidate> candidates,
            String today
    ) {
        DateOcrParser.DateCandidate selected = null;
        for (DateOcrParser.DateCandidate candidate : candidates) {
            if (!DateRules.isValidDateString(candidate.normalized)
                    || candidate.normalized.compareTo(today) <= 0) {
                continue;
            }
            if (selected != null && !selected.normalized.equals(candidate.normalized)) {
                return null;
            }
            selected = strongerCandidate(selected, candidate);
        }
        return selected;
    }

    private static boolean hasEarlierProduction(
            List<DateOcrParser.DateCandidate> productionDates,
            String expiryDate
    ) {
        for (DateOcrParser.DateCandidate production : productionDates) {
            if (DateRules.isValidDateString(production.normalized)
                    && production.normalized.compareTo(expiryDate) < 0) {
                return true;
            }
        }
        return false;
    }

    private static DateOcrParser.DateCandidate strongerCandidate(
            DateOcrParser.DateCandidate left,
            DateOcrParser.DateCandidate right
    ) {
        if (left == null) {
            return right;
        }
        return right != null && right.confidence > left.confidence ? right : left;
    }

    private static List<DateOcrParser.DateCandidate> preferredExpiryCandidates(
            List<DateOcrParser.DateCandidate> parsedCandidates,
            DateOcrParser.Result preferred
    ) {
        List<DateOcrParser.DateCandidate> all =
                new ArrayList<DateOcrParser.DateCandidate>(parsedCandidates);
        if (preferred.expiryDates.isEmpty()) {
            return all;
        }

        Set<String> preferredValues = new LinkedHashSet<String>();
        for (DateOcrParser.DateCandidate candidate : preferred.expiryDates) {
            preferredValues.add(candidate.normalized);
        }
        List<DateOcrParser.DateCandidate> matched =
                new ArrayList<DateOcrParser.DateCandidate>();
        for (DateOcrParser.DateCandidate candidate : parsedCandidates) {
            if (preferredValues.contains(candidate.normalized)) {
                matched.add(candidate);
            }
        }
        return matched.isEmpty() ? all : matched;
    }

    private static void removeWeakContradictions(
            List<DateOcrParser.DateCandidate> productionDates,
            List<DateOcrParser.DateCandidate> expiryDates
    ) {
        for (int productionIndex = productionDates.size() - 1;
             productionIndex >= 0;
             productionIndex--) {
            DateOcrParser.DateCandidate production = productionDates.get(productionIndex);
            if (!production.weakHint) {
                continue;
            }
            for (DateOcrParser.DateCandidate expiry : expiryDates) {
                if (!expiry.weakHint
                        && expiry.normalized.compareTo(production.normalized) < 0) {
                    productionDates.remove(productionIndex);
                    break;
                }
            }
        }

        if (productionDates.isEmpty()) {
            return;
        }
        String earliestProduction = productionDates.get(0).normalized;
        for (DateOcrParser.DateCandidate production : productionDates) {
            if (production.normalized.compareTo(earliestProduction) < 0) {
                earliestProduction = production.normalized;
            }
        }
        for (int expiryIndex = expiryDates.size() - 1; expiryIndex >= 0; expiryIndex--) {
            DateOcrParser.DateCandidate expiry = expiryDates.get(expiryIndex);
            if (expiry.weakHint && expiry.normalized.compareTo(earliestProduction) < 0) {
                expiryDates.remove(expiryIndex);
            }
        }
    }

    private static void removeImplausibleFoodExpiryDates(
            List<DateOcrParser.DateCandidate> productionDates,
            List<DateOcrParser.DateCandidate> expiryDates
    ) {
        if (productionDates.isEmpty() || expiryDates.isEmpty()) {
            return;
        }
        for (int expiryIndex = expiryDates.size() - 1; expiryIndex >= 0; expiryIndex--) {
            String expiry = expiryDates.get(expiryIndex).normalized;
            boolean plausible = false;
            for (DateOcrParser.DateCandidate production : productionDates) {
                int spanDays = DateRules.daysBetween(production.normalized, expiry);
                if (spanDays >= 0 && spanDays <= (366 * 5)) {
                    plausible = true;
                    break;
                }
            }
            if (!plausible) {
                expiryDates.remove(expiryIndex);
            }
        }
    }

    private static void removeWeakMonthOnlyDuplicates(
            List<DateOcrParser.DateCandidate> productionDates,
            List<DateOcrParser.DateCandidate> expiryDates
    ) {
        for (int expiryIndex = expiryDates.size() - 1; expiryIndex >= 0; expiryIndex--) {
            DateOcrParser.DateCandidate expiry = expiryDates.get(expiryIndex);
            if (!expiry.weakHint || !DateOcrParser.isMonthOnlyExpiryRaw(expiry.raw)) {
                continue;
            }
            String expiryMonth = expiry.normalized.substring(0, 7);
            for (DateOcrParser.DateCandidate production : productionDates) {
                if (production.normalized.startsWith(expiryMonth)) {
                    expiryDates.remove(expiryIndex);
                    break;
                }
            }
        }
    }

    private static void removeCrossFieldDuplicates(
            List<DateOcrParser.DateCandidate> productionDates,
            List<DateOcrParser.DateCandidate> expiryDates,
            DateOcrParser.Result preferred,
            String today
    ) {
        for (int productionIndex = productionDates.size() - 1;
             productionIndex >= 0;
             productionIndex--) {
            DateOcrParser.DateCandidate production = productionDates.get(productionIndex);
            for (int expiryIndex = expiryDates.size() - 1;
                 expiryIndex >= 0;
                 expiryIndex--) {
                DateOcrParser.DateCandidate expiry = expiryDates.get(expiryIndex);
                if (!production.normalized.equals(expiry.normalized)) {
                    continue;
                }
                boolean preferredProduction = containsDate(
                        preferred.productionDates,
                        production.normalized
                );
                boolean preferredExpiry = containsDate(
                        preferred.expiryDates,
                        expiry.normalized
                );
                if (preferredExpiry && !preferredProduction) {
                    productionDates.remove(productionIndex);
                } else if (preferredProduction && !preferredExpiry) {
                    expiryDates.remove(expiryIndex);
                } else if (production.weakHint && !expiry.weakHint) {
                    productionDates.remove(productionIndex);
                } else if (!production.weakHint && expiry.weakHint) {
                    expiryDates.remove(expiryIndex);
                } else if (production.weakHint) {
                    if (production.normalized.compareTo(today) > 0) {
                        productionDates.remove(productionIndex);
                    } else {
                        expiryDates.remove(expiryIndex);
                    }
                } else {
                    productionDates.remove(productionIndex);
                    expiryDates.remove(expiryIndex);
                }
                break;
            }
        }
    }

    private static boolean containsDate(
            List<DateOcrParser.DateCandidate> candidates,
            String normalized
    ) {
        for (DateOcrParser.DateCandidate candidate : candidates) {
            if (candidate.normalized.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static List<DateOcrParser.DateCandidate> validatedCalculatedExpiryDates(
            List<DateOcrParser.DateCandidate> calculatedCandidates,
            List<DateOcrParser.DateCandidate> productionDates,
            List<DateOcrParser.ShelfLifeCandidate> shelfLives
    ) {
        List<DateOcrParser.DateCandidate> validated =
                new ArrayList<DateOcrParser.DateCandidate>();
        for (DateOcrParser.DateCandidate candidate : calculatedCandidates) {
            if (matchesValidCalculation(candidate.normalized, productionDates, shelfLives)) {
                validated.add(candidate);
            }
        }
        return validated;
    }

    private static boolean matchesValidCalculation(
            String expectedExpiry,
            List<DateOcrParser.DateCandidate> productionDates,
            List<DateOcrParser.ShelfLifeCandidate> shelfLives
    ) {
        for (DateOcrParser.DateCandidate production : productionDates) {
            for (DateOcrParser.ShelfLifeCandidate shelfLife : shelfLives) {
                String calculated = DateRules.addShelfLife(
                        production.normalized,
                        Integer.valueOf(shelfLife.value),
                        shelfLife.unit
                );
                if (expectedExpiry.equals(calculated)) {
                    return true;
                }
            }
        }
        return false;
    }
}
