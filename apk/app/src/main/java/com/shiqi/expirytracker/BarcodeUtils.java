package com.shiqi.expirytracker;

import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BarcodeUtils {
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{8,32}");
    private static final String[] QUERY_CODE_KEYS = new String[] {
            "01",
            "gtin",
            "gtin8",
            "gtin12",
            "gtin13",
            "gtin14",
            "ean",
            "ean8",
            "ean13",
            "upc",
            "barcode",
            "bar_code",
            "code",
            "productcode",
            "product_code",
            "spbm"
    };

    private BarcodeUtils() {}

    static String digitsOnly(String value) {
        String text = FoodItem.cleanText(value);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current >= '0' && current <= '9') {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    static String extractProductCode(String value) {
        String text = FoodItem.cleanText(value);
        if (text.length() == 0) {
            return "";
        }

        String direct = validProductCode(text);
        if (direct.length() > 0) {
            return direct;
        }

        String decoded = decodeUrlLikeText(text);
        String fromPath = extractGs1DigitalLinkPath(decoded);
        if (fromPath.length() > 0) {
            return fromPath;
        }

        String fromQuery = extractKnownQueryCode(decoded);
        if (fromQuery.length() > 0) {
            return fromQuery;
        }

        String fromAi01 = extractApplicationIdentifier01(decoded);
        if (fromAi01.length() > 0) {
            return fromAi01;
        }

        return extractBestDigitCandidate(decoded);
    }

    static boolean isSupportedProductCode(String value) {
        String code = digitsOnly(value);
        int length = code.length();
        return (length == 8 || length == 12 || length == 13 || length == 14) && hasValidCheckDigit(code);
    }

    static String toGtin14(String value) {
        String code = digitsOnly(value);
        if (!isSupportedProductCode(code) || code.length() > 14) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = code.length(); index < 14; index++) {
            builder.append('0');
        }
        builder.append(code);
        return builder.toString();
    }

    static String stripLeadingGtinZero(String value) {
        String code = digitsOnly(value);
        if (code.length() == 14 && code.charAt(0) == '0') {
            String ean13 = code.substring(1);
            if (isSupportedProductCode(ean13)) {
                return ean13;
            }
        }
        return code;
    }

    private static String decodeUrlLikeText(String value) {
        try {
            return URLDecoder.decode(value.replace("&amp;", "&"), "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String validProductCode(String value) {
        String code = digitsOnly(value);
        return isSupportedProductCode(code) ? code : "";
    }

    private static String extractGs1DigitalLinkPath(String value) {
        String[] parts = value.split("[/#?&]");
        for (int index = 0; index < parts.length - 1; index++) {
            String key = parts[index].trim().toLowerCase(Locale.US);
            if (!"01".equals(key) && !"gtin".equals(key)) {
                continue;
            }

            String code = leadingDigits(parts[index + 1]);
            String valid = validProductCode(code);
            if (valid.length() > 0) {
                return valid;
            }
        }
        return "";
    }

    private static String extractKnownQueryCode(String value) {
        String[] parts = value.split("[?&;]");
        for (String part : parts) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator >= part.length() - 1) {
                continue;
            }

            String key = part.substring(0, separator).trim().toLowerCase(Locale.US);
            if (!isKnownCodeKey(key)) {
                continue;
            }

            String valid = validProductCode(part.substring(separator + 1));
            if (valid.length() > 0) {
                return valid;
            }
        }
        return "";
    }

    private static boolean isKnownCodeKey(String key) {
        for (String candidate : QUERY_CODE_KEYS) {
            if (candidate.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String extractApplicationIdentifier01(String value) {
        String compact = value
                .replace("(", "")
                .replace(")", "")
                .replace(String.valueOf((char) 29), "");

        for (int index = 0; index <= compact.length() - 16; index++) {
            if (index > 0 && isDigit(compact.charAt(index - 1)) && !hasGs1SymbologyPrefix(compact, index)) {
                continue;
            }
            if (!compact.startsWith("01", index)) {
                continue;
            }

            String candidate = compact.substring(index + 2, index + 16);
            String valid = validProductCode(candidate);
            if (valid.length() > 0) {
                return valid;
            }
        }
        return "";
    }

    private static boolean hasGs1SymbologyPrefix(String value, int index) {
        if (index < 3) {
            return false;
        }

        char marker = value.charAt(index - 3);
        return marker == ']';
    }

    private static String extractBestDigitCandidate(String value) {
        Matcher matcher = DIGIT_RUN.matcher(value);
        while (matcher.find()) {
            String run = matcher.group();
            int[] lengths = new int[] { 14, 13, 12, 8 };
            for (int length : lengths) {
                for (int start = 0; start + length <= run.length(); start++) {
                    String candidate = run.substring(start, start + length);
                    if (isSupportedProductCode(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return "";
    }

    private static String leadingDigits(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < '0' || current > '9') {
                break;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean hasValidCheckDigit(String code) {
        int sum = 0;
        boolean triple = true;
        for (int index = code.length() - 2; index >= 0; index--) {
            int digit = code.charAt(index) - '0';
            sum += triple ? digit * 3 : digit;
            triple = !triple;
        }

        int expected = (10 - (sum % 10)) % 10;
        int actual = code.charAt(code.length() - 1) - '0';
        return expected == actual;
    }
}
