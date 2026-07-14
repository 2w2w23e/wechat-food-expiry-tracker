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
    private static final Pattern PACKED_PRODUCTION_CODE = Pattern.compile("(?<!\\d)(20\\d{6})\\d{1,8}(?!\\d)");
    private static final Pattern COMPACT_DATE_RANGE = Pattern.compile(
            "(?<!\\d)(20\\d{6})\\s*(?:(?:[/|~～]|至)\\s*)?(20\\d{6})(?!\\d)"
    );
    private static final Pattern OCR_DAMAGED_COMPACT_DATE_RANGE = Pattern.compile(
            "(?<!\\d)((?:0\\d{6})|(?:\\d{6}))\\s*(?:[/|~～]|至)\\s*(20\\d{6})(?!\\d)"
    );
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
            "(保\\s*质\\s*期|质\\s*期|保\\s*鲜\\s*期|保\\s*存\\s*期|冷藏|冷冻|常温|shelf\\s*life|valid(?:ity)?|storage\\s*time)\\s*[:：为是约\\-]*\\s*(\\d{1,4})\\s*(天|日|个\\s*月|月|年|days?|months?|years?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BARCODE_HINT = Pattern.compile(
            "(条码|商品码|barcode|gtin|ean|upc)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UNHINTED_SHELF_LIFE = Pattern.compile(
            "(?<!\\d)(\\d{1,3})\\s*(天|日|个\\s*月)(?!\\d)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OCR_DAMAGED_MONTH_SHELF_LIFE = Pattern.compile(
            "保[^\\s\\d]{2,3}\\s*(\\d{1,3})\\s*个(?:月|1)?(?!\\d)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AGE_CONTEXT = Pattern.compile(
            "(月龄|年龄|适用.*月|婴幼儿|宝宝|儿童|周岁|岁以上|岁以下)",
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
        collectPackedProductionCodes(text, productionDates);
        promoteCompactDateRange(text, productionDates, expiryDates);
        promoteOcrDamagedCompactDateRange(text, productionDates, expiryDates);
        collectShelfLives(text, shelfLives);
        collectOcrDamagedMonthShelfLives(text, shelfLives);
        if (shelfLives.isEmpty()) {
            collectUnhintedShelfLives(text, shelfLives);
        }

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

    static Result parseFocusedWithDateOnlySupplement(String focusedText, String supplementText) {
        Result focused = parse(focusedText);
        if (FoodItem.cleanText(supplementText).length() == 0) {
            return focused;
        }

        Result supplement = parse(supplementText);
        List<DateCandidate> productionDates = new ArrayList<DateCandidate>(focused.productionDates);
        productionDates.addAll(supplement.productionDates);
        List<DateCandidate> expiryDates = new ArrayList<DateCandidate>(focused.expiryDates);
        expiryDates.addAll(supplement.expiryDates);
        List<ShelfLifeCandidate> shelfLives = new ArrayList<ShelfLifeCandidate>(focused.shelfLives);

        dedupeDateCandidates(productionDates);
        dedupeDateCandidates(expiryDates);
        dedupeShelfLives(shelfLives);
        sortDateCandidates(productionDates);
        sortDateCandidates(expiryDates);
        sortShelfLives(shelfLives);

        List<DateCandidate> calculatedExpiryDates = calculateExpiryDates(productionDates, shelfLives);
        dedupeDateCandidates(calculatedExpiryDates);
        sortDateCandidates(calculatedExpiryDates);
        return new Result(
                focused.rawText + "\n" + supplement.rawText,
                focused.normalizedText + "\n" + supplement.normalizedText,
                productionDates,
                expiryDates,
                shelfLives,
                calculatedExpiryDates
        );
    }

    private static void collectPackedProductionCodes(String text, List<DateCandidate> productionDates) {
        Matcher matcher = PACKED_PRODUCTION_CODE.matcher(text);
        while (matcher.find()) {
            String rawDate = matcher.group(1);
            String normalized = normalizeDate(rawDate);
            if (!DateRules.isValidDateString(normalized)) {
                continue;
            }

            String hintContext = nearbyText(text, matcher.start(1), matcher.end(), 64);
            if (BARCODE_HINT.matcher(hintContext).find()) {
                continue;
            }
            boolean productionHint = PRODUCTION_HINT.matcher(hintContext).find();
            productionDates.add(new DateCandidate(
                    "productionDate",
                    rawDate,
                    normalized,
                    hintContext,
                    productionHint ? 0.90d : 0.52d,
                    !productionHint,
                    false
            ));
        }
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
            if (!productionHint && !expiryHint && compactDigitLength(raw) == 6) {
                continue;
            }

            if (productionHint) {
                productionDates.add(new DateCandidate("productionDate", raw, normalized, context, 0.88d, false, false));
            }
            if (expiryHint) {
                expiryDates.add(new DateCandidate("expiryDate", raw, normalized, context, 0.88d, false, false));
            }
            if (!productionHint && !expiryHint) {
                productionDates.add(new DateCandidate("productionDate", raw, normalized, context, 0.45d, true, false));
            }
        }
    }

    private static void promoteCompactDateRange(
            String text,
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates
    ) {
        if (hasStrongDateCandidate(productionDates) || hasStrongDateCandidate(expiryDates)) {
            return;
        }
        Matcher matcher = COMPACT_DATE_RANGE.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String production = normalizeDate(matcher.group(1));
        String expiry = normalizeDate(matcher.group(2));
        if (!DateRules.isValidDateString(production)
                || !DateRules.isValidDateString(expiry)
                || production.compareTo(expiry) >= 0) {
            return;
        }

        String context = nearbyText(text, matcher.start(), matcher.end(), 30);
        productionDates.clear();
        expiryDates.clear();
        productionDates.add(new DateCandidate(
                "productionDate",
                matcher.group(1),
                production,
                context,
                0.76d,
                false,
                false
        ));
        expiryDates.add(new DateCandidate(
                "expiryDate",
                matcher.group(2),
                expiry,
                context,
                0.76d,
                false,
                false
        ));
    }

    private static boolean hasStrongDateCandidate(List<DateCandidate> candidates) {
        for (DateCandidate candidate : candidates) {
            if (!candidate.weakHint) {
                return true;
            }
        }
        return false;
    }

    private static void promoteOcrDamagedCompactDateRange(
            String text,
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates
    ) {
        if (hasStrongDateCandidate(productionDates) || hasStrongDateCandidate(expiryDates)) {
            return;
        }
        Matcher matcher = OCR_DAMAGED_COMPACT_DATE_RANGE.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String productionRaw = matcher.group(1);
        String production = productionRaw.length() == 7
                ? normalizeDate("2" + productionRaw)
                : normalizeDate(productionRaw);
        String expiry = normalizeDate(matcher.group(2));
        if (!DateRules.isValidDateString(production)
                || !DateRules.isValidDateString(expiry)
                || production.compareTo(expiry) >= 0) {
            return;
        }

        String context = nearbyText(text, matcher.start(), matcher.end(), 30);
        productionDates.clear();
        expiryDates.clear();
        productionDates.add(new DateCandidate(
                "productionDate",
                productionRaw,
                production,
                context,
                0.74d,
                false,
                false
        ));
        expiryDates.add(new DateCandidate(
                "expiryDate",
                matcher.group(2),
                expiry,
                context,
                0.74d,
                false,
                false
        ));
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

    private static void collectUnhintedShelfLives(String text, List<ShelfLifeCandidate> shelfLives) {
        Matcher matcher = UNHINTED_SHELF_LIFE.matcher(text);
        while (matcher.find()) {
            int value = parsePositiveInt(matcher.group(1));
            String unit = normalizeShelfLifeUnit(matcher.group(2));
            String context = nearbyText(text, matcher.start(), matcher.end(), 18);
            if (!isPlausibleShelfLife(value, unit) || AGE_CONTEXT.matcher(context).find()) {
                continue;
            }
            shelfLives.add(new ShelfLifeCandidate(
                    matcher.group(0),
                    value,
                    unit,
                    context,
                    0.62d
            ));
        }
    }

    private static void collectOcrDamagedMonthShelfLives(
            String text,
            List<ShelfLifeCandidate> shelfLives
    ) {
        Matcher matcher = OCR_DAMAGED_MONTH_SHELF_LIFE.matcher(text);
        while (matcher.find()) {
            int value = parsePositiveInt(matcher.group(1));
            if (!isPlausibleShelfLife(value, "month")) {
                continue;
            }
            shelfLives.add(new ShelfLifeCandidate(
                    matcher.group(0),
                    value,
                    "month",
                    nearbyText(text, matcher.start(), matcher.end(), 18),
                    0.74d
            ));
        }
    }

    private static boolean isPlausibleShelfLife(int value, String unit) {
        if (value <= 0 || unit.length() == 0) {
            return false;
        }
        if ("day".equals(unit)) {
            return value <= 3650;
        }
        if ("month".equals(unit)) {
            return value <= 120;
        }
        return "year".equals(unit) && value <= 50;
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

    private static int compactDigitLength(String raw) {
        int count = 0;
        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (current >= '0' && current <= '9') {
                count++;
            }
        }
        return count;
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
        if ("月".equals(unit) || "个月".equals(unit.replace(" ", ""))
                || "month".equals(unit) || "months".equals(unit)) {
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

        int productionDateEvidenceCount(String normalizedDate) {
            if (!DateRules.isValidDateString(normalizedDate)) {
                return 0;
            }
            int count = countDateEvidence(normalizedText, DATE_WITH_SEPARATOR, normalizedDate);
            count += countDateEvidence(normalizedText, COMPACT_DATE, normalizedDate);
            count += countDateEvidence(normalizedText, PACKED_PRODUCTION_CODE, normalizedDate);
            return Math.min(3, count);
        }

        int strongDatePairEvidenceCount() {
            Matcher matcher = COMPACT_DATE_RANGE.matcher(normalizedText);
            int count = 0;
            while (matcher.find()) {
                String production = normalizeDate(matcher.group(1));
                String expiry = normalizeDate(matcher.group(2));
                if (DateRules.isValidDateString(production)
                        && DateRules.isValidDateString(expiry)
                        && production.compareTo(expiry) < 0) {
                    count++;
                }
            }
            matcher = OCR_DAMAGED_COMPACT_DATE_RANGE.matcher(normalizedText);
            while (matcher.find()) {
                String productionRaw = matcher.group(1);
                String production = productionRaw.length() == 7
                        ? normalizeDate("2" + productionRaw)
                        : normalizeDate(productionRaw);
                String expiry = normalizeDate(matcher.group(2));
                if (DateRules.isValidDateString(production)
                        && DateRules.isValidDateString(expiry)
                        && production.compareTo(expiry) < 0) {
                    count += 2;
                }
            }
            return count;
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

    private static int countDateEvidence(String text, Pattern pattern, String normalizedDate) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (normalizedDate.equals(normalizeDate(matcher.group(1)))) {
                count++;
            }
        }
        return count;
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
