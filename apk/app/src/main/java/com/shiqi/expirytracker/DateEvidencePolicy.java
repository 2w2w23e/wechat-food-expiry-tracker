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
        DateOcrFrameVoter.VoteResult direct = DateOcrFrameVoter.vote(
                Collections.singletonList(latest),
                1
        );
        if (UnifiedRecognitionStabilizer.isReliableDirectDatePair(direct)) {
            int spanDays = DateRules.daysBetween(
                    direct.productionDate.value,
                    direct.expiryDate.value
            );
            if (spanDays > 0 && spanDays <= (366 * 5)) {
                return latest;
            }
        }
        return earlierFallback == null ? latest : earlierFallback;
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
