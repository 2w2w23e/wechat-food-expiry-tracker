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
    private static final Pattern DAY_MONTH_YEAR_WITH_SEPARATOR = Pattern.compile(
            "(?<![\\d./-])(\\d{1,2}\\s*[./-]\\s*\\d{1,2}\\s*[./-]\\s*20\\d{2})(?!\\d)"
    );
    private static final Pattern YEAR_MONTH_WITH_SEPARATOR = Pattern.compile(
            "(?<![\\d./-])(20\\d{2}\\s*(?:年|[./-])\\s*\\d{1,2}\\s*(?:月)?)(?!\\s*(?:[./-]|月)\\s*\\d)(?!\\d)"
    );
    private static final Pattern COMPACT_YEAR_MONTH = Pattern.compile("(?<!\\d)(20\\d{4})(?!\\d)");
    private static final Pattern MONTH_YEAR_WITH_SEPARATOR = Pattern.compile(
            "(?<![\\d./-])(\\d{1,2}\\s*[./-]\\s*20\\d{2})(?!\\d)"
    );
    private static final String ENGLISH_MONTH =
            "JAN(?:UARY)?|FEB(?:RUARY)?|MAR(?:CH)?|APR(?:IL)?|MAY|JUN(?:E)?|"
                    + "JUL(?:Y)?|AUG(?:UST)?|SEP(?:T(?:EMBER)?)?|OCT(?:OBER)?|"
                    + "NOV(?:EMBER)?|DEC(?:EMBER)?";
    private static final Pattern MONTH_NAME_FIRST_DATE = Pattern.compile(
            "\\b(" + ENGLISH_MONTH + ")\\s+(\\d{1,2})(?:\\s*,)?\\s+(20\\d{2})\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DAY_FIRST_MONTH_NAME_DATE = Pattern.compile(
            "\\b(\\d{1,2})\\s+(" + ENGLISH_MONTH + ")(?:\\s*,)?\\s+(20\\d{2})\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMPACT_DATE = Pattern.compile("(?<!\\d)((?:20\\d{6})|(?:\\d{6}))(?!\\d)");
    private static final Pattern COMPACT_DAY_MONTH_YEAR = Pattern.compile("(?<!\\d)(\\d{4}20\\d{2})(?!\\d)");
    private static final Pattern PACKED_PRODUCTION_CODE = Pattern.compile("(?<!\\d)(20\\d{6})\\d{1,8}(?!\\d)");
    private static final Pattern NOISY_LASER_DIGIT_RUN = Pattern.compile("(?<!\\d)(\\d{10,18})(?!\\d)");
    private static final Pattern NOISY_LABELED_EXPIRY = Pattern.compile(
            "保\\s*质\\s*期[^\\n\\r\\d]{0,4}(\\d{8,12})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMPACT_DATE_RANGE = Pattern.compile(
            "(?<!\\d)(20\\d{6})\\s*(?:(?:[/|~～]|至)\\s*)?(20\\d{6})(?!\\d)"
    );
    private static final Pattern OCR_DAMAGED_COMPACT_DATE_RANGE = Pattern.compile(
            "(?<!\\d)((?:0\\d{6})|(?:\\d{6}))\\s*(?:[/|~～]|至)\\s*(20\\d{6})(?!\\d)"
    );
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern PRODUCTION_HINT = Pattern.compile(
            "(生\\s*[产產]\\s*(?:日\\s*期|时\\s*间|批\\s*号)|生\\s*产\\s*日|包\\s*装\\s*日\\s*期|加\\s*工\\s*日\\s*期|分\\s*装\\s*日\\s*期|[制製]\\s*造\\s*日\\s*期|灌\\s*装\\s*日\\s*期|出\\s*厂\\s*日\\s*期|烘\\s*焙\\s*日\\s*期|喷\\s*码|prod(?:uction)?\\.?\\s*date|prod\\.?|mfg\\.?\\s*date|\\bmfg\\b|\\bmfd\\b|date\\s*of\\s*manufacture|manufactured\\s*on|made\\s*on|packed\\s*on|pack(?:ing)?\\s*date|bottled\\s*on)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXPIRY_HINT = Pattern.compile(
            "(有\\s*效\\s*(?:期|日\\s*期)(?:\\s*限)?\\s*[:：]?\\s*(?:至|到)|保\\s*(?:质|鮮|鲜|存)\\s*期\\s*[:：]?\\s*(?:至|到)|失\\s*效\\s*(?:期|日\\s*期)|限\\s*用\\s*日\\s*期|到\\s*期\\s*(?:日|日\\s*期)|截\\s*止\\s*日\\s*期|食\\s*用\\s*期\\s*限|使\\s*用\\s*期\\s*限|最\\s*佳\\s*(?:食\\s*用|赏\\s*味)\\s*(?:期|日\\s*期|期\\s*限)(?:\\s*至)?|此\\s*日\\s*期\\s*前\\s*最\\s*佳|建\\s*议\\s*食\\s*用\\s*(?:期|日\\s*期)(?:\\s*至)?|\\bexp\\b|\\bexd\\b|exp(?:iry|iration)?\\.?\\s*date|best\\s*(?:before|by)|best\\s*if\\s*used\\s*by|use\\s*by|consume\\s*before|bb\\s*e?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MAX_HINT_DISTANCE = 36;
    private static final Pattern SHELF_LIFE = Pattern.compile(
            "(保\\s*质\\s*期|质\\s*期|保\\s*鲜\\s*期|保\\s*存\\s*期|有\\s*效\\s*期|货\\s*架\\s*期|冷藏|冷冻|常温|shelf\\s*life|valid(?:ity)?|storage\\s*time)\\s*[:：为是约\\-]*\\s*(\\d{1,4})\\s*(天|日|周|星期|个\\s*月|月|年|days?|weeks?|months?|years?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BARCODE_HINT = Pattern.compile(
            "(条码|商品码|barcode|gtin|ean|upc)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRODUCT_BATCH_HINT = Pattern.compile(
            "(产\\s*品\\s*批\\s*号|批\\s*次|\\blot\\b|\\bbatch\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UNHINTED_SHELF_LIFE = Pattern.compile(
            "(?<!\\d)(\\d{1,3})[ \\t]*(天|日|个[ \\t]*月)(?!\\d)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OCR_DAMAGED_MONTH_SHELF_LIFE = Pattern.compile(
            "(?:保[^\\s\\d]{1,3}|质\\s*期|(?<![用动验])期)\\s*(\\d{1,3})\\s*个"
                    + "([月日天门曰目冃节1])?(?![\\p{IsHan}\\d])",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OCR_MISSING_MONTH_CLASSIFIER = Pattern.compile(
            "(?<!\\d)((?:1[0-9]|2[0-4]|[1-9]))\\s*[1Il|]\\s*月(?!\\d)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AGE_CONTEXT = Pattern.compile(
            "(月龄|年龄|适用.*月|婴幼儿|宝宝|儿童|周岁|岁以上|岁以下)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NON_SHELF_PERIOD_CONTEXT = Pattern.compile(
            "(儿童|适用期|活动期|试用期|账期|疗程|治疗周期|生理周期)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DAILY_FREQUENCY_CONTEXT = Pattern.compile(
            "(?:一|1)\\s*日\\s*三\\s*餐|\\d+\\s*日\\s*(?:三餐|一次|两次|多次)"
    );

    private DateOcrParser() {}

    static Result parse(String rawText) {
        return parse(rawText, DateRules.getTodayString());
    }

    static Result parse(String rawText, String referenceDate) {
        String text = normalizeEnglishMonthDates(normalizeText(rawText));
        List<DateCandidate> productionDates = new ArrayList<DateCandidate>();
        List<DateCandidate> expiryDates = new ArrayList<DateCandidate>();
        List<DateCandidate> unhintedDates = new ArrayList<DateCandidate>();
        List<ShelfLifeCandidate> shelfLives = new ArrayList<ShelfLifeCandidate>();

        collectDates(text, DATE_WITH_SEPARATOR, productionDates, expiryDates, unhintedDates);
        collectDates(text, DAY_MONTH_YEAR_WITH_SEPARATOR, productionDates, expiryDates, unhintedDates);
        collectDates(text, COMPACT_DATE, productionDates, expiryDates, unhintedDates);
        collectDates(text, COMPACT_DAY_MONTH_YEAR, productionDates, expiryDates, unhintedDates);
        collectYearMonths(text, YEAR_MONTH_WITH_SEPARATOR, expiryDates, unhintedDates, false);
        collectYearMonths(text, COMPACT_YEAR_MONTH, expiryDates, unhintedDates, false);
        collectYearMonths(text, MONTH_YEAR_WITH_SEPARATOR, expiryDates, unhintedDates, true);
        collectPackedProductionCodes(text, productionDates);
        inferUnhintedDates(unhintedDates, productionDates, expiryDates, referenceDate);
        reconcileChronologicalDatePair(productionDates, expiryDates, text);
        promoteCompactDateRange(text, productionDates, expiryDates);
        promoteOcrDamagedCompactDateRange(text, productionDates, expiryDates);
        recoverNoisyLaserProduction(text, productionDates, referenceDate);
        collectShelfLives(text, shelfLives);
        collectOcrDamagedMonthShelfLives(
                text,
                shelfLives,
                productionDates,
                expiryDates
        );
        collectMissingMonthClassifierShelfLives(text, shelfLives, productionDates);
        if (shelfLives.isEmpty() && expiryDates.isEmpty()) {
            collectUnhintedShelfLives(text, shelfLives);
        }
        recoverNoisyLabeledExpiry(text, productionDates, shelfLives, expiryDates);

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
        // Shelf life is date evidence too. A small label often lands in a full-frame or
        // detector supplement while the focused crop only contains the printed date.
        shelfLives.addAll(supplement.shelfLives);

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
            List<DateCandidate> expiryDates,
            List<DateCandidate> unhintedDates
    ) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (pattern == COMPACT_DATE && hasLotCodePrefix(text, matcher.start(1))) {
                continue;
            }
            String normalized = normalizeDate(raw);
            if (!DateRules.isValidDateString(normalized)) {
                continue;
            }

            String context = nearbyText(text, matcher.start(1), matcher.end(1), 30);
            int productionDistance = nearestHintDistance(text, matcher.start(1), matcher.end(1), PRODUCTION_HINT);
            int expiryDistance = nearestHintDistance(text, matcher.start(1), matcher.end(1), EXPIRY_HINT);
            int batchDistance = nearestHintDistance(text, matcher.start(1), matcher.end(1), PRODUCT_BATCH_HINT);
            boolean explicitCalendarDate = hasExplicitDateSeparator(raw);
            if (!explicitCalendarDate
                    && batchDistance <= MAX_HINT_DISTANCE
                    && batchDistance < Math.min(productionDistance, expiryDistance)) {
                continue;
            }
            boolean productionHint = productionDistance <= MAX_HINT_DISTANCE
                    && productionDistance <= expiryDistance;
            boolean expiryHint = expiryDistance <= MAX_HINT_DISTANCE
                    && expiryDistance < productionDistance;
            if (!productionHint && !expiryHint
                    && nearestHintDistance(text, matcher.start(1), matcher.end(1), BARCODE_HINT) <= MAX_HINT_DISTANCE) {
                continue;
            }
            if (!productionHint && !expiryHint && !explicitCalendarDate
                    && batchDistance <= MAX_HINT_DISTANCE) {
                continue;
            }

            if (productionHint) {
                productionDates.add(new DateCandidate("productionDate", raw, normalized, context, 0.88d, false, false));
            }
            if (expiryHint) {
                expiryDates.add(new DateCandidate("expiryDate", raw, normalized, context, 0.88d, false, false));
            }
            if (!productionHint && !expiryHint) {
                unhintedDates.add(new DateCandidate("unhintedDate", raw, normalized, context, 0.48d, true, false));
            }
        }
    }

    private static boolean hasExplicitDateSeparator(String raw) {
        return raw != null && (raw.indexOf('.') >= 0
                || raw.indexOf('/') >= 0
                || raw.indexOf('-') >= 0
                || raw.indexOf('年') >= 0
                || raw.indexOf('月') >= 0
                || raw.indexOf('日') >= 0);
    }

    private static boolean hasLotCodePrefix(String text, int dateStart) {
        if (text == null || dateStart <= 0) {
            return false;
        }
        char prefix = Character.toUpperCase(text.charAt(dateStart - 1));
        return prefix == 'L';
    }

    private static void inferUnhintedDates(
            List<DateCandidate> unhintedDates,
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates,
            String referenceDate
    ) {
        dedupeDateCandidates(unhintedDates);
        List<DateCandidate> remaining = new ArrayList<DateCandidate>();
        for (DateCandidate candidate : unhintedDates) {
            if (!containsDate(productionDates, candidate.normalized)
                    && !containsDate(expiryDates, candidate.normalized)) {
                remaining.add(candidate);
            }
        }
        Collections.sort(remaining, new Comparator<DateCandidate>() {
            @Override
            public int compare(DateCandidate left, DateCandidate right) {
                return left.normalized.compareTo(right.normalized);
            }
        });

        if (remaining.size() == 2) {
            DateCandidate older = remaining.get(0);
            DateCandidate newer = remaining.get(1);
            if (!older.normalized.equals(newer.normalized)) {
                productionDates.add(asInferredDate(older, "productionDate", 0.68d));
                expiryDates.add(asInferredDate(newer, "expiryDate", 0.68d));
            }
            return;
        }
        if (remaining.size() != 1) {
            return;
        }

        DateCandidate only = remaining.get(0);
        if (productionDates.size() == 1 && expiryDates.isEmpty()
                && productionDates.get(0).normalized.compareTo(only.normalized) < 0) {
            expiryDates.add(asInferredDate(only, "expiryDate", 0.64d));
            return;
        }
        if (expiryDates.size() == 1 && productionDates.isEmpty()
                && only.normalized.compareTo(expiryDates.get(0).normalized) < 0) {
            productionDates.add(asInferredDate(only, "productionDate", 0.64d));
            return;
        }
        if (!productionDates.isEmpty() || !expiryDates.isEmpty()) {
            return;
        }

        String today = DateRules.isValidDateString(referenceDate)
                ? referenceDate
                : DateRules.getTodayString();
        if (only.normalized.compareTo(today) > 0) {
            expiryDates.add(asInferredDate(only, "expiryDate", 0.58d));
        } else {
            productionDates.add(asInferredDate(only, "productionDate", 0.58d));
        }
    }

    private static DateCandidate asInferredDate(DateCandidate source, String type, double confidence) {
        return new DateCandidate(
                type,
                source.raw,
                source.normalized,
                source.context,
                confidence,
                true,
                false
        );
    }

    private static void reconcileChronologicalDatePair(
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates,
            String sourceText
    ) {
        if (productionDates.isEmpty()) {
            reconcileOneSidedDatePair(expiryDates, productionDates, true, sourceText);
        } else if (expiryDates.isEmpty()) {
            reconcileOneSidedDatePair(productionDates, expiryDates, false, sourceText);
        }
    }

    private static void reconcileOneSidedDatePair(
            List<DateCandidate> source,
            List<DateCandidate> destination,
            boolean sourceIsExpiry,
            String sourceText
    ) {
        Map<String, DateCandidate> distinct = new LinkedHashMap<String, DateCandidate>();
        for (DateCandidate candidate : source) {
            if (!distinct.containsKey(candidate.normalized)) {
                distinct.put(candidate.normalized, candidate);
            }
        }
        if ((!sourceIsExpiry && allCandidatesHaveStrongHints(source))
                || (sourceIsExpiry
                && allCandidatesHaveStrongHints(source)
                && countMatches(EXPIRY_HINT, sourceText) >= distinct.size()
                && !PRODUCT_BATCH_HINT.matcher(sourceText == null ? "" : sourceText).find())) {
            return;
        }
        if (distinct.size() != 2) {
            return;
        }

        List<DateCandidate> ordered = new ArrayList<DateCandidate>(distinct.values());
        Collections.sort(ordered, new Comparator<DateCandidate>() {
            @Override
            public int compare(DateCandidate left, DateCandidate right) {
                return left.normalized.compareTo(right.normalized);
            }
        });
        DateCandidate moved = sourceIsExpiry ? ordered.get(0) : ordered.get(1);
        String movedType = sourceIsExpiry ? "productionDate" : "expiryDate";
        for (int index = source.size() - 1; index >= 0; index--) {
            if (source.get(index).normalized.equals(moved.normalized)) {
                source.remove(index);
            }
        }
        destination.add(asInferredDate(moved, movedType, 0.66d));
    }

    private static int countMatches(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static boolean allCandidatesHaveStrongHints(List<DateCandidate> candidates) {
        if (candidates.isEmpty()) {
            return false;
        }
        for (DateCandidate candidate : candidates) {
            if (candidate.weakHint) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsDate(List<DateCandidate> candidates, String normalized) {
        for (DateCandidate candidate : candidates) {
            if (candidate.normalized.equals(normalized)) {
                return true;
            }
        }
        return false;
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
            if ("week".equals(unit)) {
                value *= 7;
                unit = "day";
            }
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
            if (!isPlausibleShelfLife(value, unit)
                    || AGE_CONTEXT.matcher(context).find()
                    || NON_SHELF_PERIOD_CONTEXT.matcher(context).find()
                    || DAILY_FREQUENCY_CONTEXT.matcher(context).find()) {
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

    private static void collectYearMonths(
            String text,
            Pattern pattern,
            List<DateCandidate> expiryDates,
            List<DateCandidate> unhintedDates,
            boolean monthFirst
    ) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int expiryDistance = nearestHintDistance(text, matcher.start(1), matcher.end(1), EXPIRY_HINT);
            String raw = matcher.group(1);
            String normalized = normalizeExpiryMonth(raw, monthFirst);
            if (!DateRules.isValidDateString(normalized)) {
                continue;
            }
            DateCandidate candidate = new DateCandidate(
                    expiryDistance <= MAX_HINT_DISTANCE ? "expiryDate" : "unhintedDate",
                    raw,
                    normalized,
                    nearbyText(text, matcher.start(1), matcher.end(1), 30),
                    expiryDistance <= MAX_HINT_DISTANCE ? 0.90d : 0.50d,
                    expiryDistance > MAX_HINT_DISTANCE,
                    false
            );
            if (expiryDistance <= MAX_HINT_DISTANCE) {
                expiryDates.add(candidate);
            } else {
                unhintedDates.add(candidate);
            }
        }
    }

    private static void collectOcrDamagedMonthShelfLives(
            String text,
            List<ShelfLifeCandidate> shelfLives,
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates
    ) {
        Matcher matcher = OCR_DAMAGED_MONTH_SHELF_LIFE.matcher(text);
        while (matcher.find()) {
            int value = parsePositiveInt(matcher.group(1));
            String context = nearbyText(text, matcher.start(), matcher.end(), 18);
            if (AGE_CONTEXT.matcher(context).find()
                    || NON_SHELF_PERIOD_CONTEXT.matcher(context).find()) {
                continue;
            }
            String unit = resolveDamagedClassifierUnit(
                    value,
                    matcher.group(2),
                    productionDates,
                    expiryDates
            );
            if (!isPlausibleShelfLife(value, unit)) {
                continue;
            }
            shelfLives.add(new ShelfLifeCandidate(
                    matcher.group(0),
                    value,
                    unit,
                    context,
                    hasDateSpanSupport(value, unit, productionDates, expiryDates)
                            ? 0.88d
                            : 0.74d
            ));
        }
    }

    private static void collectMissingMonthClassifierShelfLives(
            String text,
            List<ShelfLifeCandidate> shelfLives,
            List<DateCandidate> productionDates
    ) {
        Matcher matcher = OCR_MISSING_MONTH_CLASSIFIER.matcher(text);
        while (matcher.find()) {
            int value = parsePositiveInt(matcher.group(1));
            String context = nearbyText(text, matcher.start(), matcher.end(), 48);
            String lowerContext = context.toLowerCase(Locale.US);
            boolean packagingDateContext = !productionDates.isEmpty()
                    || PRODUCTION_HINT.matcher(context).find()
                    || lowerContext.contains("y.m.d")
                    || lowerContext.contains("ymd");
            if (!packagingDateContext
                    || AGE_CONTEXT.matcher(context).find()
                    || NON_SHELF_PERIOD_CONTEXT.matcher(context).find()
                    || !isPlausibleShelfLife(value, "month")) {
                continue;
            }
            shelfLives.add(new ShelfLifeCandidate(
                    matcher.group(0),
                    value,
                    "month",
                    context,
                    0.70d
            ));
        }
    }

    private static void recoverNoisyLaserProduction(
            String text,
            List<DateCandidate> productionDates,
            String referenceDate
    ) {
        if (hasStrongDateCandidate(productionDates)
                || !DateRules.isValidDateString(referenceDate)) {
            return;
        }
        String earliest = DateRules.addDaysString(referenceDate, -(366 * 5));
        Matcher matcher = NOISY_LASER_DIGIT_RUN.matcher(text);
        String bestDate = "";
        String bestRaw = "";
        String bestContext = "";
        while (matcher.find()) {
            String digits = matcher.group(1);
            String context = nearbyText(text, matcher.start(1), matcher.end(1), 28);
            if (BARCODE_HINT.matcher(context).find()
                    || BarcodeUtils.isSupportedProductCode(digits)) {
                continue;
            }
            for (int index = 0; index + 6 <= digits.length(); index++) {
                String raw = digits.substring(index, index + 6);
                String normalized = normalizeDate(raw);
                if (!DateRules.isValidDateString(normalized)
                        || normalized.compareTo(earliest) < 0
                        || normalized.compareTo(referenceDate) > 0) {
                    continue;
                }
                if (bestDate.length() == 0 || normalized.compareTo(bestDate) > 0) {
                    bestDate = normalized;
                    bestRaw = raw;
                    bestContext = context;
                }
            }
        }
        if (bestDate.length() > 0) {
            productionDates.add(new DateCandidate(
                    "productionDate",
                    bestRaw,
                    bestDate,
                    bestContext + " noisy laser code",
                    0.68d,
                    false,
                    false
            ));
        }
    }

    private static void recoverNoisyLabeledExpiry(
            String text,
            List<DateCandidate> productionDates,
            List<ShelfLifeCandidate> shelfLives,
            List<DateCandidate> expiryDates
    ) {
        if (productionDates.isEmpty()
                || shelfLives.isEmpty()
                || hasPlausibleStrongExpiry(productionDates, expiryDates)) {
            return;
        }
        Matcher matcher = NOISY_LABELED_EXPIRY.matcher(text);
        int bestMismatch = Integer.MAX_VALUE;
        String bestDate = "";
        String bestRaw = "";
        String bestContext = "";
        while (matcher.find()) {
            String noisyDigits = matcher.group(1);
            for (DateCandidate production : productionDates) {
                for (ShelfLifeCandidate shelfLife : shelfLives) {
                    String calculated = DateRules.addShelfLife(
                            production.normalized,
                            Integer.valueOf(shelfLife.value),
                            shelfLife.unit
                    );
                    if (!DateRules.isValidDateString(calculated)) {
                        continue;
                    }
                    String[] constrained = new String[] {
                            calculated,
                            DateRules.addDaysString(calculated, -1)
                    };
                    for (String candidate : constrained) {
                        int mismatch = bestDigitWindowMismatch(
                                noisyDigits,
                                candidate.replace("-", "")
                        );
                        if (mismatch < bestMismatch) {
                            bestMismatch = mismatch;
                            bestDate = candidate;
                            bestRaw = noisyDigits;
                            bestContext = nearbyText(text, matcher.start(), matcher.end(), 30);
                        }
                    }
                }
            }
        }
        if (bestMismatch <= 3 && DateRules.isValidDateString(bestDate)) {
            expiryDates.add(new DateCandidate(
                    "expiryDate",
                    bestRaw,
                    bestDate,
                    bestContext + " shelf-life constrained OCR repair",
                    Math.max(0.66d, 0.78d - (bestMismatch * 0.04d)),
                    false,
                    false
            ));
        }
    }

    private static boolean hasPlausibleStrongExpiry(
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates
    ) {
        for (DateCandidate expiry : expiryDates) {
            if (expiry.weakHint) {
                continue;
            }
            for (DateCandidate production : productionDates) {
                if (production.weakHint) {
                    continue;
                }
                int spanDays = DateRules.daysBetween(
                        production.normalized,
                        expiry.normalized
                );
                if (spanDays >= 0 && spanDays <= (366 * 5)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int bestDigitWindowMismatch(String noisyDigits, String expectedDigits) {
        if (noisyDigits == null || expectedDigits == null
                || expectedDigits.length() == 0
                || noisyDigits.length() < expectedDigits.length()) {
            return Integer.MAX_VALUE;
        }
        int best = Integer.MAX_VALUE;
        for (int start = 0; start + expectedDigits.length() <= noisyDigits.length(); start++) {
            int mismatch = 0;
            for (int index = 0; index < expectedDigits.length(); index++) {
                if (noisyDigits.charAt(start + index) != expectedDigits.charAt(index)) {
                    mismatch++;
                }
            }
            best = Math.min(best, mismatch);
        }
        return best;
    }

    private static String resolveDamagedClassifierUnit(
            int value,
            String damagedUnitGlyph,
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates
    ) {
        if ("月".equals(damagedUnitGlyph)) {
            return "month";
        }
        if ("天".equals(damagedUnitGlyph)) {
            return "day";
        }
        boolean dayMatches = hasDateSpanSupport(
                value,
                "day",
                productionDates,
                expiryDates
        );
        boolean monthMatches = hasDateSpanSupport(
                value,
                "month",
                productionDates,
                expiryDates
        );
        if (dayMatches && !monthMatches) {
            return "day";
        }
        return "month";
    }

    private static boolean hasDateSpanSupport(
            int value,
            String unit,
            List<DateCandidate> productionDates,
            List<DateCandidate> expiryDates
    ) {
        for (DateCandidate productionDate : productionDates) {
            if (productionDate.weakHint) {
                continue;
            }
            String calculated = DateRules.addShelfLife(
                    productionDate.normalized,
                    Integer.valueOf(value),
                    unit
            );
            if (!DateRules.isValidDateString(calculated)) {
                continue;
            }
            for (DateCandidate expiryDate : expiryDates) {
                if (expiryDate.weakHint) {
                    continue;
                }
                int difference = Math.abs(DateRules.daysBetween(
                        calculated,
                        expiryDate.normalized
                ));
                if (difference <= 1) {
                    return true;
                }
            }
        }
        return false;
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
            } else if (ch == '\r' || ch == '\n') {
                builder.append('\n');
            } else if (Character.isWhitespace(ch)) {
                builder.append(' ');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String normalizeEnglishMonthDates(String text) {
        String monthFirst = replaceEnglishMonthDates(text, MONTH_NAME_FIRST_DATE, true);
        return replaceEnglishMonthDates(monthFirst, DAY_FIRST_MONTH_NAME_DATE, false);
    }

    private static String replaceEnglishMonthDates(
            String text,
            Pattern pattern,
            boolean monthFirst
    ) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer normalized = new StringBuffer();
        while (matcher.find()) {
            String monthName = matcher.group(monthFirst ? 1 : 2);
            String day = matcher.group(monthFirst ? 2 : 1);
            String year = matcher.group(3);
            String replacement = year + "-" + englishMonthNumber(monthName) + "-"
                    + String.format(Locale.US, "%02d", parsePositiveInt(day));
            matcher.appendReplacement(normalized, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }

    private static String englishMonthNumber(String monthName) {
        String prefix = FoodItem.cleanText(monthName).toUpperCase(Locale.US);
        if (prefix.length() > 3) {
            prefix = prefix.substring(0, 3);
        }
        if ("JAN".equals(prefix)) return "01";
        if ("FEB".equals(prefix)) return "02";
        if ("MAR".equals(prefix)) return "03";
        if ("APR".equals(prefix)) return "04";
        if ("MAY".equals(prefix)) return "05";
        if ("JUN".equals(prefix)) return "06";
        if ("JUL".equals(prefix)) return "07";
        if ("AUG".equals(prefix)) return "08";
        if ("SEP".equals(prefix)) return "09";
        if ("OCT".equals(prefix)) return "10";
        if ("NOV".equals(prefix)) return "11";
        if ("DEC".equals(prefix)) return "12";
        return "00";
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
                if (digits.substring(4, 6).equals("20")) {
                    return formatDate(digits.substring(4, 8), digits.substring(2, 4), digits.substring(0, 2));
                }
                return formatDate(digits.substring(0, 4), digits.substring(4, 6), digits.substring(6, 8));
            }
            if (digits.length() == 6) {
                return formatDate(twoDigitYear(digits.substring(0, 2)), digits.substring(2, 4), digits.substring(4, 6));
            }
        }

        if (numbers.size() >= 3) {
            String first = numbers.get(0);
            String second = numbers.get(1);
            String third = numbers.get(2);
            if (third.length() == 4 && first.length() <= 2 && second.length() <= 2) {
                return formatDate(third, second, first);
            }
            if (first.length() == 2 && second.length() <= 2 && third.length() == 2) {
                String yearFirst = formatDate(twoDigitYear(first), second, third);
                String dayFirst = formatDate(twoDigitYear(third), second, first);
                if (DateRules.isValidDateString(yearFirst)
                        && DateRules.isValidDateString(dayFirst)) {
                    int yearFirstDistance = Math.abs(DateRules.daysBetween(
                            yearFirst,
                            DateRules.getTodayString()
                    ));
                    int dayFirstDistance = Math.abs(DateRules.daysBetween(
                            dayFirst,
                            DateRules.getTodayString()
                    ));
                    return dayFirstDistance < yearFirstDistance ? dayFirst : yearFirst;
                }
                if (DateRules.isValidDateString(dayFirst)) {
                    return dayFirst;
                }
                return yearFirst;
            }
            String year = first.length() == 2 ? twoDigitYear(first) : first;
            return formatDate(year, second, third);
        }

        return "";
    }

    private static String normalizeExpiryMonth(String raw, boolean monthFirst) {
        List<String> numbers = new ArrayList<String>();
        Matcher matcher = NUMBER.matcher(raw);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }

        String yearText;
        String monthText;
        if (numbers.size() == 1 && numbers.get(0).length() == 6) {
            String digits = numbers.get(0);
            yearText = digits.substring(0, 4);
            monthText = digits.substring(4, 6);
        } else if (numbers.size() >= 2) {
            String first = numbers.get(0);
            String second = numbers.get(1);
            if (monthFirst) {
                yearText = second;
                monthText = first;
            } else {
                yearText = first.length() == 2 ? twoDigitYear(first) : first;
                monthText = second;
            }
        } else {
            return "";
        }

        return DateRules.lastDayOfMonthString(parsePositiveInt(yearText), parsePositiveInt(monthText));
    }

    static boolean isMonthOnlyExpiryRaw(String raw) {
        String normalized = normalizeText(raw);
        return YEAR_MONTH_WITH_SEPARATOR.matcher(normalized).find()
                || COMPACT_YEAR_MONTH.matcher(normalized).find()
                || MONTH_YEAR_WITH_SEPARATOR.matcher(normalized).find();
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
        if ("周".equals(unit) || "星期".equals(unit)
                || "week".equals(unit) || "weeks".equals(unit)) {
            return "week";
        }
        return "";
    }

    private static String nearbyText(String text, int start, int end, int radius) {
        int safeStart = Math.max(0, start - radius);
        int safeEnd = Math.min(text.length(), end + radius);
        return text.substring(safeStart, safeEnd).trim().replaceAll("\\s+", " ");
    }

    private static int nearestHintDistance(String text, int start, int end, Pattern hintPattern) {
        int safeStart = Math.max(0, start - MAX_HINT_DISTANCE);
        int safeEnd = Math.min(text.length(), end + MAX_HINT_DISTANCE);
        Matcher matcher = hintPattern.matcher(text.substring(safeStart, safeEnd));
        int best = Integer.MAX_VALUE;
        while (matcher.find()) {
            int absoluteStart = safeStart + matcher.start();
            int absoluteEnd = safeStart + matcher.end();
            int distance;
            if (absoluteEnd <= start) {
                distance = start - absoluteEnd;
            } else if (absoluteStart >= end) {
                distance = absoluteStart - end + 8;
            } else {
                distance = 0;
            }
            best = Math.min(best, distance);
        }
        return best;
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
            count += countDateEvidence(normalizedText, DAY_MONTH_YEAR_WITH_SEPARATOR, normalizedDate);
            count += countDateEvidence(normalizedText, COMPACT_DATE, normalizedDate);
            count += countDateEvidence(normalizedText, COMPACT_DAY_MONTH_YEAR, normalizedDate);
            count += countDateEvidence(normalizedText, PACKED_PRODUCTION_CODE, normalizedDate);
            return Math.min(3, count);
        }

        int strongDatePairEvidenceCount() {
            int count = 0;
            for (DateCandidate production : productionDates) {
                for (DateCandidate expiry : expiryDates) {
                    count += strongDatePairEvidenceCount(
                            production.normalized,
                            expiry.normalized
                    );
                }
            }
            return count;
        }

        int strongDatePairEvidenceCount(String productionDate, String expiryDate) {
            Matcher matcher = COMPACT_DATE_RANGE.matcher(normalizedText);
            int count = 0;
            while (matcher.find()) {
                String production = normalizeDate(matcher.group(1));
                String expiry = normalizeDate(matcher.group(2));
                if (productionDate.equals(production) && expiryDate.equals(expiry)) {
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
                if (productionDate.equals(production) && expiryDate.equals(expiry)) {
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
