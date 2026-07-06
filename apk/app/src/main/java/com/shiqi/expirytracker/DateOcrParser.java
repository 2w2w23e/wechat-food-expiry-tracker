package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DateOcrParser {
    private static final Pattern DATE_WITH_SEPARATOR = Pattern.compile(
            "(?<!\\d)((?:20\\d{2}|\\d{2})\\s*(?:年|[./-])\\s*\\d{1,2}\\s*(?:月|[./-])\\s*\\d{1,2}\\s*(?:日)?)(?!\\d)"
    );
    private static final Pattern COMPACT_DATE = Pattern.compile("(?<!\\d)((?:20\\d{6})|(?:\\d{6}))(?!\\d)");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern PRODUCTION_HINT = Pattern.compile(
            "(生产日期|生产时间|生产批号|包装日期|制造日期|灌装日期|出厂日期|喷码|prod(?:uction)?\\.?\\s*date|mfg\\.?\\s*date|made\\s*on)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXPIRY_HINT = Pattern.compile(
            "(有效期至|保质期至|到期日|截止日期|食用期限|使用期限|\\bexp\\b|exp(?:iry|iration)?\\.?\\s*date|best\\s*before|use\\s*by|bb\\s*e?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SHELF_LIFE = Pattern.compile(
            "(保质期|质期|保鲜期|保存期|冷藏|冷冻|常温|shelf\\s*life|valid(?:ity)?|storage\\s*time)\\s*[:：为是约\\-]*\\s*(\\d{1,4})\\s*(天|日|个月|月|年|days?|months?|years?)",
            Pattern.CASE_INSENSITIVE
    );

    private DateOcrParser() {}

    static Result parse(String rawText) {
        String text = normalizeText(rawText);
        List<DateCandidate> productionDates = new ArrayList<DateCandidate>();
        List<DateCandidate> expiryDates = new ArrayList<DateCandidate>();
        List<ShelfLifeCandidate> shelfLives = new ArrayList<ShelfLifeCandidate>();

        collectDates(text, DATE_WITH_SEPARATOR, productionDates, expiryDates);
        collectDates(text, COMPACT_DATE, productionDates, expiryDates);
        collectShelfLives(text, shelfLives);

        dedupeDateCandidates(productionDates);
        dedupeDateCandidates(expiryDates);
        dedupeShelfLives(shelfLives);
        sortDateCandidates(productionDates);
        sortDateCandidates(expiryDates);
        sortShelfLives(shelfLives);

        List<DateCandidate> calculatedExpiryDates = calculateExpiryDates(productionDates, shelfLives);
        dedupeDateCandidates(calculatedExpiryDates);
        sortDateCandidates(calculatedExpiryDates);

        return new Result(rawText == null ? "" : rawText, text, productionDates, expiryDates, shelfLives, calculatedExpiryDates);
    }

    private static void collectDates(
            String text,
            Pattern pattern,
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates
    ) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(1);
            String normalized = normalizeDate(raw);
            if (!DateRules.isValidDateString(normalized)) {
                continue;
            }

            String context = nearbyText(text, matcher.start(1), matcher.end(1), 30);
            String hintContext = nearbyHintText(text, matcher.start(1), matcher.end(1));
            boolean productionHint = PRODUCTION_HINT.matcher(hintContext).find();
            boolean expiryHint = EXPIRY_HINT.matcher(hintContext).find();

            if (productionHint) {
                productionDates.add(new DateCandidate("productionDate", raw, normalized, context, 0.88d, false, false));
            }
            if (expiryHint) {
                expiryDates.add(new DateCandidate("expiryDate", raw, normalized, context, 0.88d, false, false));
            }
            if (!productionHint && !expiryHint) {
                productionDates.add(new DateCandidate("productionDate", raw, normalized, context, 0.45d, true, false));
                expiryDates.add(new DateCandidate("expiryDate", raw, normalized, context, 0.45d, true, false));
            }
        }
    }

    private static void collectShelfLives(String text, List<ShelfLifeCandidate> shelfLives) {
        Matcher matcher = SHELF_LIFE.matcher(text);
        while (matcher.find()) {
            int value = parsePositiveInt(matcher.group(2));
            String unit = normalizeShelfLifeUnit(matcher.group(3));
            if (value <= 0 || unit.length() == 0) {
                continue;
            }
            shelfLives.add(new ShelfLifeCandidate(
                    matcher.group(0),
                    value,
                    unit,
                    nearbyText(text, matcher.start(), matcher.end(), 30),
                    0.84d
            ));
        }
    }

    private static List<DateCandidate> calculateExpiryDates(
            List<DateCandidate> productionDates,
            List<ShelfLifeCandidate> shelfLives
    ) {
        List<DateCandidate> result = new ArrayList<DateCandidate>();
        for (DateCandidate productionDate : productionDates) {
            for (ShelfLifeCandidate shelfLife : shelfLives) {
                String expiryDate = DateRules.addShelfLife(
                        productionDate.normalized,
                        Integer.valueOf(shelfLife.value),
                        shelfLife.unit
                );
                if (DateRules.isValidDateString(expiryDate)) {
                    double confidence = Math.min(0.92d, (productionDate.confidence + shelfLife.confidence) / 2.0d);
                    result.add(new DateCandidate(
                            "calculatedExpiryDate",
                            productionDate.raw + " + " + shelfLife.raw,
                            expiryDate,
                            productionDate.context + " | " + shelfLife.context,
                            confidence,
                            productionDate.weakHint,
                            true
                    ));
                }
            }
        }
        return result;
    }

    private static String normalizeText(String rawText) {
        if (rawText == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(rawText.length());
        for (int index = 0; index < rawText.length(); index++) {
            char ch = rawText.charAt(index);
            if (ch >= '０' && ch <= '９') {
                builder.append((char) ('0' + (ch - '０')));
            } else if (ch == '／') {
                builder.append('/');
            } else if (ch == '－' || ch == '—' || ch == '–') {
                builder.append('-');
            } else if (ch == '．') {
                builder.append('.');
            } else if (Character.isWhitespace(ch)) {
                builder.append(' ');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String normalizeDate(String raw) {
        List<String> numbers = new ArrayList<String>();
        Matcher matcher = NUMBER.matcher(raw);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }

        if (numbers.size() == 1) {
            String digits = numbers.get(0);
            if (digits.length() == 8) {
                return formatDate(digits.substring(0, 4), digits.substring(4, 6), digits.substring(6, 8));
            }
            if (digits.length() == 6) {
                return formatDate(twoDigitYear(digits.substring(0, 2)), digits.substring(2, 4), digits.substring(4, 6));
            }
        }

        if (numbers.size() >= 3) {
            String year = numbers.get(0).length() == 2 ? twoDigitYear(numbers.get(0)) : numbers.get(0);
            return formatDate(year, numbers.get(1), numbers.get(2));
        }

        return "";
    }

    private static String formatDate(String yearText, String monthText, String dayText) {
        int year = parsePositiveInt(yearText);
        int month = parsePositiveInt(monthText);
        int day = parsePositiveInt(dayText);
        if (year <= 0 || month <= 0 || day <= 0) {
            return "";
        }
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    private static String twoDigitYear(String value) {
        int year = parsePositiveInt(value);
        return String.valueOf(2000 + year);
    }

    private static int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static String normalizeShelfLifeUnit(String raw) {
        String unit = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if ("天".equals(unit) || "日".equals(unit) || "day".equals(unit) || "days".equals(unit)) {
            return "day";
        }
        if ("月".equals(unit) || "个月".equals(unit) || "month".equals(unit) || "months".equals(unit)) {
            return "month";
        }
        if ("年".equals(unit) || "year".equals(unit) || "years".equals(unit)) {
            return "year";
        }
        return "";
    }

    private static String nearbyText(String text, int start, int end, int radius) {
        int safeStart = Math.max(0, start - radius);
        int safeEnd = Math.min(text.length(), end + radius);
        return text.substring(safeStart, safeEnd).trim().replaceAll("\\s+", " ");
    }

    private static String nearbyHintText(String text, int start, int end) {
        int safeStart = Math.max(0, start - 24);
        int safeEnd = Math.min(text.length(), end + 10);
        return text.substring(safeStart, safeEnd).trim().replaceAll("\\s+", " ");
    }

    private static void dedupeDateCandidates(List<DateCandidate> candidates) {
        Map<String, DateCandidate> unique = new LinkedHashMap<String, DateCandidate>();
        for (DateCandidate candidate : candidates) {
            String key = candidate.type + "|" + candidate.normalized;
            DateCandidate previous = unique.get(key);
            if (previous == null || candidate.confidence > previous.confidence) {
                unique.put(key, candidate);
            }
        }
        candidates.clear();
        candidates.addAll(unique.values());
    }

    private static void dedupeShelfLives(List<ShelfLifeCandidate> candidates) {
        Map<String, ShelfLifeCandidate> unique = new LinkedHashMap<String, ShelfLifeCandidate>();
        for (ShelfLifeCandidate candidate : candidates) {
            String key = candidate.value + "|" + candidate.unit;
            ShelfLifeCandidate previous = unique.get(key);
            if (previous == null || candidate.confidence > previous.confidence) {
                unique.put(key, candidate);
            }
        }
        candidates.clear();
        candidates.addAll(unique.values());
    }

    private static void sortDateCandidates(List<DateCandidate> candidates) {
        Collections.sort(candidates, new Comparator<DateCandidate>() {
            @Override
            public int compare(DateCandidate left, DateCandidate right) {
                int confidence = Double.compare(right.confidence, left.confidence);
                return confidence != 0 ? confidence : left.normalized.compareTo(right.normalized);
            }
        });
    }

    private static void sortShelfLives(List<ShelfLifeCandidate> candidates) {
        Collections.sort(candidates, new Comparator<ShelfLifeCandidate>() {
            @Override
            public int compare(ShelfLifeCandidate left, ShelfLifeCandidate right) {
                int confidence = Double.compare(right.confidence, left.confidence);
                if (confidence != 0) {
                    return confidence;
                }
                return Integer.valueOf(left.value).compareTo(Integer.valueOf(right.value));
            }
        });
    }

    static final class Result {
        final String rawText;
        final String normalizedText;
        final List<DateCandidate> productionDates;
        final List<DateCandidate> expiryDates;
        final List<ShelfLifeCandidate> shelfLives;
        final List<DateCandidate> calculatedExpiryDates;
        final boolean candidateOnly = true;

        Result(
                String rawText,
                String normalizedText,
                List<DateCandidate> productionDates,
                List<DateCandidate> expiryDates,
                List<ShelfLifeCandidate> shelfLives,
                List<DateCandidate> calculatedExpiryDates
        ) {
            this.rawText = rawText;
            this.normalizedText = normalizedText;
            this.productionDates = Collections.unmodifiableList(new ArrayList<DateCandidate>(productionDates));
            this.expiryDates = Collections.unmodifiableList(new ArrayList<DateCandidate>(expiryDates));
            this.shelfLives = Collections.unmodifiableList(new ArrayList<ShelfLifeCandidate>(shelfLives));
            this.calculatedExpiryDates = Collections.unmodifiableList(new ArrayList<DateCandidate>(calculatedExpiryDates));
        }

        boolean hasAnyCandidate() {
            return !productionDates.isEmpty()
                    || !expiryDates.isEmpty()
                    || !shelfLives.isEmpty()
                    || !calculatedExpiryDates.isEmpty();
        }

        boolean hasDateConflict() {
            return hasDifferentDateValues(productionDates)
                    || hasDifferentDateValues(expiryDates)
                    || hasDifferentDateValues(calculatedExpiryDates);
        }

        private static boolean hasDifferentDateValues(List<DateCandidate> candidates) {
            String first = "";
            for (DateCandidate candidate : candidates) {
                if (first.length() == 0) {
                    first = candidate.normalized;
                } else if (!first.equals(candidate.normalized)) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class DateCandidate {
        final String type;
        final String raw;
        final String normalized;
        final String context;
        final double confidence;
        final boolean weakHint;
        final boolean calculated;
        final boolean candidateOnly = true;

        DateCandidate(
                String type,
                String raw,
                String normalized,
                String context,
                double confidence,
                boolean weakHint,
                boolean calculated
        ) {
            this.type = type;
            this.raw = raw;
            this.normalized = normalized;
            this.context = context;
            this.confidence = confidence;
            this.weakHint = weakHint;
            this.calculated = calculated;
        }
    }

    static final class ShelfLifeCandidate {
        final String raw;
        final int value;
        final String unit;
        final String context;
        final double confidence;
        final boolean candidateOnly = true;

        ShelfLifeCandidate(String raw, int value, String unit, String context, double confidence) {
            this.raw = raw;
            this.value = value;
            this.unit = unit;
            this.context = context;
            this.confidence = confidence;
        }
    }
}
