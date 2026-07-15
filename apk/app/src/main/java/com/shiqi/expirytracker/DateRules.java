package com.shiqi.expirytracker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

final class DateRules {
    static final String STATUS_EXPIRED = "expired";
    static final String STATUS_TODAY = "today";
    static final String STATUS_SOON = "soon";
    static final String STATUS_NORMAL = "normal";
    static final String STATUS_UNKNOWN = "unknown";
    static final String STATUS_FINISHED = "finished";

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int DEFAULT_SOON_DAYS = 7;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private DateRules() {}

    static boolean isValidDateString(String value) {
        return parseDateParts(value) != null;
    }

    static boolean isShelfLifeUnit(String shelfLifeUnit) {
        return "day".equals(shelfLifeUnit)
                || "month".equals(shelfLifeUnit)
                || "year".equals(shelfLifeUnit);
    }

    static String addShelfLife(String productionDate, Integer shelfLifeValue, String shelfLifeUnit) {
        DateParts date = parseDateParts(productionDate);
        if (date == null || shelfLifeValue == null || shelfLifeValue.intValue() <= 0) {
            return "";
        }

        int value = shelfLifeValue.intValue();
        if ("day".equals(shelfLifeUnit)) {
            return format(addDays(date, value));
        }

        if ("month".equals(shelfLifeUnit)) {
            return format(addMonths(date, value));
        }

        if ("year".equals(shelfLifeUnit)) {
            return format(addYears(date, value));
        }

        return "";
    }

    static String addAfterOpenShelfLife(String openedDate, Integer afterOpenShelfLifeValue, String afterOpenShelfLifeUnit) {
        return addShelfLife(openedDate, afterOpenShelfLifeValue, afterOpenShelfLifeUnit);
    }

    static String getExpiryStatus(String expiryDate) {
        DateParts expiry = parseDateParts(expiryDate);
        DateParts today = parseDateParts(getTodayString());

        if (expiry == null || today == null) {
            return STATUS_UNKNOWN;
        }

        long diffDays = (toUtcMillis(expiry) - toUtcMillis(today)) / DAY_MS;

        if (diffDays < 0) {
            return STATUS_EXPIRED;
        }

        if (diffDays == 0) {
            return STATUS_TODAY;
        }

        if (diffDays <= DEFAULT_SOON_DAYS) {
            return STATUS_SOON;
        }

        return STATUS_NORMAL;
    }

    static String getTodayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
    }

    static String todayString() {
        return getTodayString();
    }

    static int daysBetween(String startDate, String endDate) {
        DateParts start = parseDateParts(startDate);
        DateParts end = parseDateParts(endDate);
        if (start == null || end == null) {
            return Integer.MIN_VALUE;
        }
        return (int) ((toUtcMillis(end) - toUtcMillis(start)) / DAY_MS);
    }

    static int daysUntil(String date) {
        return daysBetween(getTodayString(), date);
    }

    static String addDaysString(String date, int delta) {
        DateParts parts = parseDateParts(date);
        if (parts == null) {
            return "";
        }
        return format(addDays(parts, delta));
    }

    static String lastDayOfMonthString(int year, int month) {
        if (year < 2000 || year > 2099 || month < 1 || month > 12) {
            return "";
        }
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, getDaysInMonth(year, month));
    }

    static boolean isYesterday(String date) {
        return daysUntil(date) == -1;
    }

    static boolean isToday(String date) {
        return daysUntil(date) == 0;
    }

    static boolean isTomorrow(String date) {
        return daysUntil(date) == 1;
    }

    static String productionAgeLabel(String productionDate) {
        DateParts production = parseDateParts(productionDate);
        DateParts today = parseDateParts(getTodayString());

        if (production == null || today == null) {
            return "生产日期未填写";
        }

        long diffDays = (toUtcMillis(today) - toUtcMillis(production)) / DAY_MS;
        if (diffDays < 0) {
            return "尚未到生产日期";
        }

        int years = today.year - production.year;
        int months = today.month - production.month;
        int days = today.day - production.day;

        if (days < 0) {
            months--;
            int previousMonth = today.month - 1;
            int previousYear = today.year;
            if (previousMonth < 1) {
                previousMonth = 12;
                previousYear--;
            }
            days += getDaysInMonth(previousYear, previousMonth);
        }

        if (months < 0) {
            years--;
            months += 12;
        }

        StringBuilder builder = new StringBuilder();
        if (years > 0) {
            builder.append(years).append("年");
        }
        if (months > 0) {
            builder.append(months).append("月");
        }
        if (days > 0 || builder.length() == 0) {
            builder.append(days).append("日");
        }
        return builder.toString();
    }

    static String nowIsoLike() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new java.util.Date());
    }

    private static DateParts parseDateParts(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }

        int year = Integer.parseInt(value.substring(0, 4));
        int month = Integer.parseInt(value.substring(5, 7));
        int day = Integer.parseInt(value.substring(8, 10));

        if (month < 1 || month > 12) {
            return null;
        }

        if (day < 1 || day > getDaysInMonth(year, month)) {
            return null;
        }

        return new DateParts(year, month, day);
    }

    private static DateParts addDays(DateParts date, int amount) {
        GregorianCalendar calendar = new GregorianCalendar(UTC, Locale.US);
        calendar.clear();
        calendar.set(date.year, date.month - 1, date.day);
        calendar.add(Calendar.DAY_OF_MONTH, amount);
        return new DateParts(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
        );
    }

    private static DateParts addMonths(DateParts date, int amount) {
        int totalMonths = date.year * 12 + (date.month - 1) + amount;
        int year = totalMonths / 12;
        int month = totalMonths % 12 + 1;
        int day = Math.min(date.day, getDaysInMonth(year, month));
        return new DateParts(year, month, day);
    }

    private static DateParts addYears(DateParts date, int amount) {
        int year = date.year + amount;
        int day = Math.min(date.day, getDaysInMonth(year, date.month));
        return new DateParts(year, date.month, day);
    }

    private static int getDaysInMonth(int year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    private static boolean isLeapYear(int year) {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    private static long toUtcMillis(DateParts date) {
        GregorianCalendar calendar = new GregorianCalendar(UTC, Locale.US);
        calendar.clear();
        calendar.set(date.year, date.month - 1, date.day);
        return calendar.getTimeInMillis();
    }

    private static String format(DateParts date) {
        return String.format(Locale.US, "%04d-%02d-%02d", date.year, date.month, date.day);
    }

    private static final class DateParts {
        final int year;
        final int month;
        final int day;

        DateParts(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }
    }
}
