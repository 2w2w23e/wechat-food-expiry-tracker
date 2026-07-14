package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class ReminderPolicy {
    private static final int INVALID_DAYS = Integer.MIN_VALUE;
    private static final String RISK_HIGH = "A";
    private static final String RISK_MEDIUM = "B";
    private static final String RISK_LOW = "C";
    private static ReminderSettings defaultSettings = ReminderSettings.defaults();

    private ReminderPolicy() {}

    static void useSettings(ReminderSettings settings) {
        defaultSettings = ReminderSettings.validOrDefault(settings);
    }

    static ReminderPlan planFor(FoodItem food) {
        return planFor(food, defaultSettings);
    }

    static ReminderPlan planFor(FoodItem food, ReminderSettings settings) {
        ReminderSettings effectiveSettings = ReminderSettings.validOrDefault(settings);
        ReminderPlan plan = new ReminderPlan();
        if (!effectiveSettings.enabled) {
            plan.disabledReason = "提醒已关闭：全局设置不提醒";
            plan.cardHint = plan.disabledReason;
            plan.detailAdvice = "提醒：已在全局设置关闭，不会删除食品数据";
            return plan;
        }

        if (food == null) {
            plan.disabledReason = "提醒：暂无食品信息";
            plan.cardHint = plan.disabledReason;
            return plan;
        }

        if (food.isFinished) {
            plan.disabledReason = "已用完：不再提醒";
            plan.cardHint = finishedHint(food);
            plan.detailAdvice = "提醒：不再提醒";
            return plan;
        }

        if (!DateRules.isValidDateString(food.expiryDate)) {
            plan.disabledReason = "提醒：暂无有效到期日";
            plan.cardHint = plan.disabledReason;
            plan.detailAdvice = "提醒：暂无提醒计划";
            return plan;
        }

        if (effectiveSettings.isSmartMode() && !hasCurrentSmartSchedule(food)) {
            plan.disabledReason = "智能提醒计划尚未保存";
            plan.cardHint = "提醒：计划未保存，请编辑食品后重试";
            return plan;
        }

        int expiryDaysLeft = DateRules.daysUntil(food.expiryDate);
        String afterOpenRecommendedDate = DateRules.addAfterOpenShelfLife(
                food.openedDate,
                food.afterOpenShelfLifeValue,
                food.afterOpenShelfLifeUnit
        );
        boolean hasAfterOpenRecommendedDate = DateRules.isValidDateString(afterOpenRecommendedDate);
        boolean usesAfterOpenDate = effectiveSettings.isSmartMode()
                && hasAfterOpenRecommendedDate
                && afterOpenRecommendedDate.compareTo(food.expiryDate) < 0;
        String effectiveReminderDate = usesAfterOpenDate ? afterOpenRecommendedDate : food.expiryDate;
        int daysLeft = DateRules.daysUntil(effectiveReminderDate);
        if (daysLeft == INVALID_DAYS) {
            plan.disabledReason = "提醒：暂无有效到期日";
            plan.cardHint = plan.disabledReason;
            plan.detailAdvice = "提醒：暂无提醒计划";
            return plan;
        }

        int totalShelfLifeDays = usesAfterOpenDate
                ? afterOpenShelfLifeDays(food, daysLeft)
                : totalShelfLifeDays(food, expiryDaysLeft);
        String riskLevel = riskLevel(food, totalShelfLifeDays);
        String storage = normalizedStorage(food.storageMethod);

        plan.enabled = true;
        plan.expiryDaysLeft = expiryDaysLeft;
        plan.daysLeft = daysLeft;
        plan.afterOpenRecommendedDate = hasAfterOpenRecommendedDate ? afterOpenRecommendedDate : "";
        plan.afterOpenDaysLeft = hasAfterOpenRecommendedDate ? DateRules.daysUntil(afterOpenRecommendedDate) : INVALID_DAYS;
        plan.effectiveReminderDate = effectiveReminderDate;
        plan.usesAfterOpenDate = usesAfterOpenDate;
        plan.totalShelfLifeDays = totalShelfLifeDays;
        plan.riskLevel = riskLevel;
        plan.riskLabel = riskLabel(riskLevel);
        plan.riskReason = FoodData.storageLabel(storage) + " / " + FoodData.categoryLabel(food.category);
        plan.reminderMode = effectiveSettings.mode;
        plan.priorityScore = priorityScore(daysLeft, riskLevel, storage, food.remainingQuantity);
        plan.priorityBand = priorityBand(plan.priorityScore);

        List<Integer> offsets = effectiveSettings.offsetsFor(
                food.smartReminderOffsets,
                totalShelfLifeDays
        );
        plan.offsets.addAll(offsets);
        plan.scheduleReason = effectiveSettings.isSmartMode()
                ? smartScheduleReason(food, plan)
                : "固定日期：到期前 " + effectiveSettings.advanceDaysText() + " 天";
        for (Integer offset : offsets) {
            String reminderDate = DateRules.addDaysString(plan.effectiveReminderDate, -offset.intValue());
            if (reminderDate.length() > 0) {
                plan.events.add(new ReminderEvent(
                        offset.intValue(),
                        reminderDate,
                        food.expiryDate,
                        false,
                        reminderEventLabel(offset.intValue(), plan.usesAfterOpenDate)
                ));
            }
        }

        if (effectiveSettings.isSmartMode() && shouldAddPostExpiryReminder(riskLevel, storage)) {
            String postExpiryDate = DateRules.addDaysString(food.expiryDate, 1);
            if (postExpiryDate.length() > 0) {
                plan.events.add(new ReminderEvent(-1, postExpiryDate, food.expiryDate, true, "过期后 1 天检查"));
            }
        }

        sortEventsByDate(plan.events);

        if (daysLeft == 0 && offsets.contains(Integer.valueOf(0))) {
            plan.dueDayHours.addAll(dueDayHours(riskLevel, effectiveSettings));
        }

        plan.nextReminderSummary = nextReminderSummary(food, plan);
        plan.detailAdvice = detailAdvice(food, plan);
        plan.cardHint = cardHint(food, plan);
        return plan;
    }

    static DailyBriefing dailyBriefing(List<FoodItem> foods) {
        return dailyBriefing(foods, defaultSettings);
    }

    static DailyBriefing dailyBriefing(List<FoodItem> foods, ReminderSettings settings) {
        ReminderSettings effectiveSettings = ReminderSettings.validOrDefault(settings);
        DailyBriefing briefing = new DailyBriefing();
        if (foods == null || !effectiveSettings.enabled) {
            return briefing;
        }

        for (FoodItem food : foods) {
            if (food == null || food.isFinished || !DateRules.isValidDateString(food.expiryDate)) {
                continue;
            }

            ReminderPlan plan = planFor(food, effectiveSettings);
            if (!plan.enabled) {
                continue;
            }

            if (effectiveSettings.isSmartMode() && DateRules.isYesterday(food.expiryDate)) {
                briefing.yesterdayExpired.add(new DailyBriefing.Entry(food, plan, food.name));
            } else if (DateRules.isToday(food.expiryDate) && !plan.dueDayHours.isEmpty()) {
                briefing.todayDue.add(new DailyBriefing.Entry(
                        food,
                        plan,
                        food.name + "（" + join(plan.dueDayHours, "、") + "）"
                ));
            } else if (plan.usesAfterOpenDate
                    && DateRules.isToday(plan.effectiveReminderDate)
                    && !plan.dueDayHours.isEmpty()) {
                briefing.todayDue.add(new DailyBriefing.Entry(
                        food,
                        plan,
                        food.name + "：开封后建议今天处理"
                ));
            } else if (plan.daysLeft > 0 && plan.hasReminderToday()) {
                briefing.upcoming.add(new DailyBriefing.Entry(food, plan, upcomingText(food, plan)));
            }
        }

        sortEntries(briefing.yesterdayExpired);
        sortEntries(briefing.todayDue);
        sortEntries(briefing.upcoming);
        return briefing;
    }

    static List<String> dueDayHours(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel)) {
            return new ArrayList<String>(Arrays.asList("08:30", "12:30", "18:30"));
        }
        if (RISK_LOW.equals(riskLevel)) {
            return new ArrayList<String>(Arrays.asList("09:00"));
        }
        return new ArrayList<String>(Arrays.asList("09:00", "18:00"));
    }

    static List<String> dueDayHours(String riskLevel, ReminderSettings settings) {
        ReminderSettings effectiveSettings = ReminderSettings.validOrDefault(settings);
        if (!effectiveSettings.enabled) {
            return new ArrayList<String>();
        }
        if (effectiveSettings.usesDefaultTodaySlots()) {
            return dueDayHours(riskLevel);
        }
        return effectiveSettings.todayReminderSlots();
    }

    private static String reminderEventLabel(int offset, boolean usesAfterOpenDate) {
        if (usesAfterOpenDate) {
            return offset == 0 ? "开封后建议处理日提醒" : "开封后提前 " + offset + " 天提醒";
        }
        return offset == 0 ? "到期日提醒" : "提前 " + offset + " 天提醒";
    }

    private static int totalShelfLifeDays(FoodItem food, int daysLeft) {
        int dateSpan = DateRules.daysBetween(food.productionDate, food.expiryDate);
        if (dateSpan != INVALID_DAYS && dateSpan >= 1) {
            return dateSpan;
        }

        if (food.shelfLifeValue != null && food.shelfLifeValue.intValue() > 0) {
            int value = food.shelfLifeValue.intValue();
            if ("day".equals(food.shelfLifeUnit)) {
                return value;
            }
            if ("month".equals(food.shelfLifeUnit)) {
                return value * 30;
            }
            if ("year".equals(food.shelfLifeUnit)) {
                return value * 365;
            }
        }

        return Math.max(daysLeft, 1);
    }

    private static int afterOpenShelfLifeDays(FoodItem food, int daysLeft) {
        String afterOpenRecommendedDate = DateRules.addAfterOpenShelfLife(
                food.openedDate,
                food.afterOpenShelfLifeValue,
                food.afterOpenShelfLifeUnit
        );
        int dateSpan = DateRules.daysBetween(food.openedDate, afterOpenRecommendedDate);
        if (dateSpan != INVALID_DAYS && dateSpan >= 1) {
            return dateSpan;
        }

        if (food.afterOpenShelfLifeValue != null && food.afterOpenShelfLifeValue.intValue() > 0) {
            int value = food.afterOpenShelfLifeValue.intValue();
            if ("day".equals(food.afterOpenShelfLifeUnit)) {
                return value;
            }
            if ("month".equals(food.afterOpenShelfLifeUnit)) {
                return value * 30;
            }
            if ("year".equals(food.afterOpenShelfLifeUnit)) {
                return value * 365;
            }
        }

        return Math.max(daysLeft, 1);
    }

    private static String riskLevel(FoodItem food, int totalShelfLifeDays) {
        String category = FoodData.normalizeCategoryValue(food.category);
        String storage = normalizedStorage(food.storageMethod);

        if ("meat_egg_seafood".equals(category)
                || "cooked".equals(category)
                || ("dairy".equals(category) && "refrigerated".equals(storage))
                || ("other".equals(category) && "refrigerated".equals(storage) && totalShelfLifeDays <= 7)) {
            return RISK_HIGH;
        }

        if ("frozen".equals(category) && "frozen".equals(storage)) {
            return RISK_LOW;
        }

        if ("produce".equals(category)
                || ("staple".equals(category) && totalShelfLifeDays <= 14)
                || ("beverage".equals(category) && "refrigerated".equals(storage))
                || ("snack".equals(category) && totalShelfLifeDays <= 30)
                || "refrigerated".equals(storage)) {
            return RISK_MEDIUM;
        }

        if ("condiment".equals(category)
                || ("snack".equals(category) && totalShelfLifeDays > 30)
                || ("staple".equals(category) && totalShelfLifeDays > 14)
                || "cool_dry".equals(storage)
                || "avoid_light".equals(storage)
                || ("room_temp".equals(storage) && totalShelfLifeDays > 90)) {
            return RISK_LOW;
        }

        return RISK_MEDIUM;
    }

    private static List<Integer> smartOffsets(
            int daysLeft,
            String riskLevel,
            String storage,
            boolean usesAfterOpenDate
    ) {
        int remainingDays = Math.max(daysLeft, 0);
        List<Integer> offsets;
        if (usesAfterOpenDate || RISK_HIGH.equals(riskLevel)) {
            offsets = highAttentionOffsets(remainingDays);
        } else if (RISK_LOW.equals(riskLevel)) {
            offsets = lowAttentionOffsets(remainingDays);
        } else {
            offsets = mediumAttentionOffsets(remainingDays);
        }

        if ("refrigerated".equals(storage) && !RISK_HIGH.equals(riskLevel)) {
            addOffsetIfValid(offsets, 2, remainingDays);
            addOffsetIfValid(offsets, 1, remainingDays);
        }
        if (usesAfterOpenDate) {
            addOffsetIfValid(offsets, 5, remainingDays);
            addOffsetIfValid(offsets, 2, remainingDays);
        }
        addOffsetIfValid(offsets, 0, remainingDays);
        return capOffsets(uniqueValidOffsets(offsets, remainingDays), 6);
    }

    static boolean ensureSmartSchedule(FoodItem food) {
        return ensureSmartScheduleAt(food, DateRules.todayString());
    }

    static boolean ensureSmartScheduleAt(FoodItem food, String planningDate) {
        if (food == null || !DateRules.isValidDateString(food.expiryDate)) {
            return clearSmartSchedule(food);
        }

        String fingerprint = smartScheduleFingerprint(food);
        if (hasCurrentSmartSchedule(food)) {
            return false;
        }

        String afterOpenRecommendedDate = DateRules.addAfterOpenShelfLife(
                food.openedDate,
                food.afterOpenShelfLifeValue,
                food.afterOpenShelfLifeUnit
        );
        boolean usesAfterOpenDate = DateRules.isValidDateString(afterOpenRecommendedDate)
                && afterOpenRecommendedDate.compareTo(food.expiryDate) < 0;
        String effectiveReminderDate = usesAfterOpenDate ? afterOpenRecommendedDate : food.expiryDate;
        String effectivePlanningDate = DateRules.isValidDateString(planningDate)
                ? planningDate
                : DateRules.todayString();
        int daysLeft = DateRules.daysBetween(effectivePlanningDate, effectiveReminderDate);
        int expiryDaysLeft = DateRules.daysBetween(effectivePlanningDate, food.expiryDate);
        int totalShelfLifeDays = usesAfterOpenDate
                ? afterOpenShelfLifeDays(food, daysLeft)
                : totalShelfLifeDays(food, expiryDaysLeft);
        String storage = normalizedStorage(food.storageMethod);
        String riskLevel = riskLevel(food, totalShelfLifeDays);

        food.smartReminderOffsets.clear();
        food.smartReminderOffsets.addAll(smartOffsets(daysLeft, riskLevel, storage, usesAfterOpenDate));
        food.smartReminderFingerprint = fingerprint;
        food.smartReminderPlannedDaysLeft = daysLeft;
        food.smartReminderPlannedOn = effectivePlanningDate;
        return true;
    }

    static boolean ensureSmartSchedules(List<FoodItem> foods) {
        boolean changed = false;
        if (foods == null) {
            return false;
        }
        for (FoodItem food : foods) {
            changed = ensureSmartSchedule(food) || changed;
        }
        return changed;
    }

    private static boolean clearSmartSchedule(FoodItem food) {
        if (food == null) {
            return false;
        }
        boolean changed = !food.smartReminderOffsets.isEmpty()
                || FoodItem.cleanText(food.smartReminderFingerprint).length() > 0
                || food.smartReminderPlannedDaysLeft != Integer.MIN_VALUE
                || FoodItem.cleanText(food.smartReminderPlannedOn).length() > 0;
        food.smartReminderOffsets.clear();
        food.smartReminderFingerprint = "";
        food.smartReminderPlannedDaysLeft = Integer.MIN_VALUE;
        food.smartReminderPlannedOn = "";
        return changed;
    }

    private static boolean hasCurrentSmartSchedule(FoodItem food) {
        return food != null
                && DateRules.isValidDateString(food.expiryDate)
                && smartScheduleFingerprint(food).equals(food.smartReminderFingerprint)
                && !food.smartReminderOffsets.isEmpty()
                && food.smartReminderPlannedDaysLeft != Integer.MIN_VALUE
                && DateRules.isValidDateString(food.smartReminderPlannedOn);
    }

    private static String smartScheduleFingerprint(FoodItem food) {
        return FoodData.normalizeCategoryValue(food.category)
                + "|" + normalizedStorage(food.storageMethod)
                + "|" + FoodItem.cleanText(food.productionDate)
                + "|" + nullableIntegerText(food.shelfLifeValue)
                + "|" + FoodItem.cleanText(food.shelfLifeUnit)
                + "|" + FoodItem.cleanText(food.expiryDate)
                + "|" + FoodItem.cleanText(food.openedDate)
                + "|" + nullableIntegerText(food.afterOpenShelfLifeValue)
                + "|" + FoodItem.cleanText(food.afterOpenShelfLifeUnit);
    }

    private static String nullableIntegerText(Integer value) {
        return value == null ? "" : String.valueOf(value.intValue());
    }

    private static List<Integer> highAttentionOffsets(int remainingDays) {
        if (remainingDays <= 3) {
            return offsets(2, 1, 0);
        }
        if (remainingDays <= 7) {
            return offsets(5, 3, 2, 1, 0);
        }
        if (remainingDays <= 14) {
            return offsets(7, 5, 3, 1, 0);
        }
        if (remainingDays <= 30) {
            return offsets(14, 7, 3, 1, 0);
        }
        if (remainingDays <= 90) {
            return offsets(30, 14, 7, 3, 1, 0);
        }
        if (remainingDays <= 180) {
            return offsets(60, 30, 14, 7, 1, 0);
        }
        return offsets(90, 60, 30, 14, 7, 0);
    }

    private static List<Integer> mediumAttentionOffsets(int remainingDays) {
        if (remainingDays <= 3) {
            return offsets(1, 0);
        }
        if (remainingDays <= 7) {
            return offsets(3, 1, 0);
        }
        if (remainingDays <= 14) {
            return offsets(7, 3, 1, 0);
        }
        if (remainingDays <= 30) {
            return offsets(14, 7, 3, 1, 0);
        }
        if (remainingDays <= 90) {
            return offsets(30, 14, 7, 3, 0);
        }
        if (remainingDays <= 180) {
            return offsets(60, 30, 14, 7, 0);
        }
        if (remainingDays <= 365) {
            return offsets(90, 60, 30, 14, 7, 0);
        }
        return offsets(180, 90, 30, 7, 0);
    }

    private static List<Integer> lowAttentionOffsets(int remainingDays) {
        if (remainingDays <= 7) {
            return offsets(0);
        }
        if (remainingDays <= 30) {
            return offsets(7, 0);
        }
        if (remainingDays <= 90) {
            return offsets(30, 7, 0);
        }
        if (remainingDays <= 365) {
            return offsets(90, 30, 0);
        }
        return offsets(180, 60, 0);
    }

    private static List<Integer> offsets(int... values) {
        List<Integer> result = new ArrayList<Integer>();
        for (int value : values) {
            if (!result.contains(Integer.valueOf(value))) {
                result.add(Integer.valueOf(value));
            }
        }
        return result;
    }

    private static List<Integer> uniqueValidOffsets(List<Integer> offsets, int totalShelfLifeDays) {
        List<Integer> result = new ArrayList<Integer>();
        for (Integer offset : offsets) {
            if (offset == null) {
                continue;
            }
            addOffsetIfValid(result, offset.intValue(), totalShelfLifeDays);
        }
        sortOffsetsDescending(result);
        return result;
    }

    private static List<Integer> capOffsets(List<Integer> offsets, int maxCount) {
        if (offsets.size() <= maxCount) {
            return offsets;
        }

        boolean hasDueDay = offsets.contains(Integer.valueOf(0));
        List<Integer> result = new ArrayList<Integer>();
        int earlyLimit = hasDueDay ? maxCount - 1 : maxCount;
        Integer nearestPositive = null;
        for (Integer offset : offsets) {
            if (offset.intValue() > 0) {
                nearestPositive = offset;
            }
        }
        for (Integer offset : offsets) {
            if (offset.intValue() == 0) {
                continue;
            }
            int reservedNearest = nearestPositive == null ? 0 : 1;
            if (result.size() < earlyLimit - reservedNearest) {
                result.add(offset);
            }
        }
        if (nearestPositive != null && !result.contains(nearestPositive) && result.size() < earlyLimit) {
            result.add(nearestPositive);
        }
        if (hasDueDay) {
            result.add(Integer.valueOf(0));
        }
        sortOffsetsDescending(result);
        return result;
    }

    private static void addOffsetIfValid(List<Integer> offsets, int offset, int totalShelfLifeDays) {
        if (offset < 0 || offset > totalShelfLifeDays || offsets.contains(Integer.valueOf(offset))) {
            return;
        }
        offsets.add(Integer.valueOf(offset));
    }

    private static int largestPositiveOffset(List<Integer> offsets) {
        int largest = 0;
        for (Integer offset : offsets) {
            if (offset != null && offset.intValue() > largest) {
                largest = offset.intValue();
            }
        }
        return largest;
    }

    private static boolean shouldAddPostExpiryReminder(String riskLevel, String storage) {
        return !("frozen".equals(storage) || RISK_LOW.equals(riskLevel));
    }

    private static double priorityScore(int daysLeft, String riskLevel, String storage, double remainingQuantity) {
        return urgencyScore(daysLeft) * 0.45
                + riskScore(riskLevel) * 0.30
                + storageScore(storage) * 0.15
                + quantityScore(remainingQuantity) * 0.10;
    }

    private static int urgencyScore(int daysLeft) {
        if (daysLeft < 0) {
            return 100;
        }
        if (daysLeft == 0) {
            return 95;
        }
        if (daysLeft <= 1) {
            return 90;
        }
        if (daysLeft <= 3) {
            return 75;
        }
        if (daysLeft <= 7) {
            return 55;
        }
        if (daysLeft <= 14) {
            return 35;
        }
        if (daysLeft <= 30) {
            return 20;
        }
        return 5;
    }

    private static int riskScore(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel)) {
            return 100;
        }
        if (RISK_LOW.equals(riskLevel)) {
            return 30;
        }
        return 60;
    }

    private static int storageScore(String storage) {
        if ("refrigerated".equals(storage)) {
            return 80;
        }
        if ("room_temp".equals(storage)) {
            return 50;
        }
        if ("frozen".equals(storage)) {
            return 30;
        }
        if ("cool_dry".equals(storage) || "avoid_light".equals(storage)) {
            return 35;
        }
        return 70;
    }

    private static int quantityScore(double remainingQuantity) {
        if (remainingQuantity >= 5) {
            return 80;
        }
        if (remainingQuantity >= 2) {
            return 50;
        }
        if (remainingQuantity > 0) {
            return 30;
        }
        return 10;
    }

    private static String priorityBand(double score) {
        if (score >= 80) {
            return "紧急";
        }
        if (score >= 60) {
            return "优先";
        }
        if (score >= 40) {
            return "注意";
        }
        return "普通";
    }

    private static String riskLabel(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel)) {
            return "高风险";
        }
        if (RISK_LOW.equals(riskLevel)) {
            return "低风险";
        }
        return "中风险";
    }

    private static String smartScheduleReason(FoodItem food, ReminderPlan plan) {
        String horizon = food.smartReminderPlannedDaysLeft < 0
                ? "当时已超过提醒日期"
                : "当时还剩 " + food.smartReminderPlannedDaysLeft + " 天";
        String plannedOn = DateRules.isValidDateString(food.smartReminderPlannedOn)
                ? food.smartReminderPlannedOn + " 生成，"
                : "";
        String basis = FoodData.storageLabel(food.storageMethod)
                + " · " + FoodData.categoryLabel(food.category)
                + " · " + smartFrequencyLabel(plan.riskLevel);
        if (plan.usesAfterOpenDate) {
            basis += " · 开封后期限优先";
        }
        return plannedOn + horizon + "；" + basis;
    }

    private static String smartFrequencyLabel(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel)) {
            return "提醒较频繁";
        }
        if (RISK_LOW.equals(riskLevel)) {
            return "提醒较少";
        }
        return "常规提醒";
    }

    private static String cardHint(FoodItem food, ReminderPlan plan) {
        if (plan.usesAfterOpenDate) {
            return afterOpenCardHint(food, plan);
        }

        String afterOpenSuffix = afterOpenSuffix(plan);
        if (plan.daysLeft < 0) {
            return expiredCopy(food) + afterOpenSuffix + zeroQuantitySuffix(food);
        }
        if (plan.daysLeft == 0) {
            if (!plan.hasReminderToday()) {
                return "提醒：暂无后续提醒" + afterOpenSuffix + zeroQuantitySuffix(food);
            }
            return "今日提醒：" + join(plan.dueDayHours, "、") + " · " + plan.priorityBand + afterOpenSuffix + zeroQuantitySuffix(food);
        }

        ReminderEvent next = plan.nextFutureEvent();
        if (next != null) {
            int daysToReminder = DateRules.daysBetween(DateRules.todayString(), next.reminderDate);
            if (daysToReminder == 0) {
                return "今日提醒：还有 " + plan.daysLeft + " 天到期 · " + plan.priorityBand + afterOpenSuffix + zeroQuantitySuffix(food);
            }
            return "提醒：" + daysToReminder + " 天后提醒 · " + plan.priorityBand + afterOpenSuffix + zeroQuantitySuffix(food);
        }

        if (ReminderSettings.MODE_FIXED.equals(plan.reminderMode)) {
            return "提醒：暂无后续提醒" + afterOpenSuffix + zeroQuantitySuffix(food);
        }
        return "提醒：还有 " + plan.daysLeft + " 天到期 · " + plan.priorityBand + afterOpenSuffix + zeroQuantitySuffix(food);
    }

    private static String nextReminderSummary(FoodItem food, ReminderPlan plan) {
        if (plan.usesAfterOpenDate) {
            return afterOpenNextReminderSummary(plan);
        }

        if (plan.daysLeft < 0) {
            return expiredCopy(food);
        }
        ReminderEvent next = plan.nextFutureEvent();
        if (next == null) {
            if (ReminderSettings.MODE_FIXED.equals(plan.reminderMode)) {
                return "暂无后续提醒";
            }
            return "还有 " + plan.daysLeft + " 天到期，可以安排食用";
        }

        if (plan.daysLeft == 0) {
            return "今天到期，请优先查看";
        }
        if (plan.daysLeft == 1) {
            return "明天到期，建议优先处理";
        }

        int daysToReminder = DateRules.daysBetween(DateRules.todayString(), next.reminderDate);
        if (daysToReminder == 0) {
            return "今天提醒：还有 " + plan.daysLeft + " 天到期，请优先查看";
        }
        return daysToReminder + " 天后提醒：" + reminderPosition(next, "到期");
    }

    private static String detailAdvice(FoodItem food, ReminderPlan plan) {
        String advice = plan.nextReminderSummary;
        if (plan.usesAfterOpenDate) {
            advice += "；最终可食用日期仍为 " + food.expiryDate;
        } else if (plan.afterOpenRecommendedDate.length() > 0) {
            advice += "；开封后建议处理日：" + plan.afterOpenRecommendedDate;
        }
        if (food.remainingQuantity <= 0) {
            advice += "；剩余数量为 0，可标记已用完";
        }
        return advice;
    }

    private static String afterOpenCardHint(FoodItem food, ReminderPlan plan) {
        if (plan.daysLeft < 0) {
            return "开封后建议处理日已过：" + plan.afterOpenRecommendedDate
                    + " · 最终可食用日期 " + food.expiryDate
                    + zeroQuantitySuffix(food);
        }
        if (plan.daysLeft == 0) {
            return "今日建议处理：开封后建议处理日 · "
                    + plan.priorityBand
                    + " · 最终可食用日期 " + food.expiryDate
                    + zeroQuantitySuffix(food);
        }

        ReminderEvent next = plan.nextFutureEvent();
        if (next != null) {
            int daysToReminder = DateRules.daysBetween(DateRules.todayString(), next.reminderDate);
            if (daysToReminder == 0) {
                return "今日提醒：还有 " + plan.daysLeft + " 天到开封后建议处理日 · "
                        + plan.priorityBand
                        + zeroQuantitySuffix(food);
            }
            return "提醒：" + daysToReminder + " 天后提醒 · "
                    + plan.priorityBand
                    + " · 开封后建议 " + plan.afterOpenRecommendedDate
                    + zeroQuantitySuffix(food);
        }

        return "提醒：还有 " + plan.daysLeft + " 天到开封后建议处理日 · "
                + plan.priorityBand
                + zeroQuantitySuffix(food);
    }

    private static String afterOpenNextReminderSummary(ReminderPlan plan) {
        if (plan.daysLeft < 0) {
            return "开封后建议处理日已过，请优先查看并按家庭习惯处理";
        }
        if (plan.daysLeft == 0) {
            return "今天是开封后建议处理日，请优先查看";
        }
        if (plan.daysLeft == 1) {
            return "明天到开封后建议处理日，建议优先安排";
        }

        ReminderEvent next = plan.nextFutureEvent();
        if (next == null) {
            return "还有 " + plan.daysLeft + " 天到开封后建议处理日，可以提前安排";
        }

        int daysToReminder = DateRules.daysBetween(DateRules.todayString(), next.reminderDate);
        if (daysToReminder == 0) {
            return "今天提醒：还有 " + plan.daysLeft + " 天到开封后建议处理日，请优先查看";
        }
        return daysToReminder + " 天后提醒：" + reminderPosition(next, "开封后建议处理日");
    }

    private static String reminderPosition(ReminderEvent event, String dateLabel) {
        if (event == null || event.offsetDays <= 0) {
            return dateLabel + "当天";
        }
        return dateLabel + "前 " + event.offsetDays + " 天";
    }

    private static String afterOpenSuffix(ReminderPlan plan) {
        if (plan.afterOpenRecommendedDate.length() == 0) {
            return "";
        }
        return " · 开封后建议 " + plan.afterOpenRecommendedDate;
    }

    private static String expiredCopy(FoodItem food) {
        if ("frozen".equals(normalizedStorage(food.storageMethod))) {
            return "已过期：建议检查品质并安排食用";
        }
        return "已过期：请检查并处理；不确定时建议不要食用";
    }

    private static String finishedHint(FoodItem food) {
        if (FoodItem.cleanText(food.finishedAt).length() == 0) {
            return "已用完：不再提醒";
        }
        return "已用完：不再提醒 · " + food.finishedAt;
    }

    private static String zeroQuantitySuffix(FoodItem food) {
        return food.remainingQuantity <= 0 ? " · 可标记已用完" : "";
    }

    private static String upcomingText(FoodItem food, ReminderPlan plan) {
        if (plan.usesAfterOpenDate) {
            if (plan.daysLeft == 1) {
                return food.name + " 明天到开封后建议处理日";
            }
            return food.name + " " + plan.daysLeft + " 天后到开封后建议处理日";
        }

        if (plan.daysLeft == 1) {
            return food.name + " 明天到期";
        }
        return food.name + " " + plan.daysLeft + " 天后到期";
    }

    private static String normalizedStorage(String value) {
        String storage = FoodItem.cleanText(value);
        return storage.length() == 0 ? "room_temp" : storage;
    }

    private static void sortEntries(List<DailyBriefing.Entry> entries) {
        for (int index = 1; index < entries.size(); index++) {
            DailyBriefing.Entry current = entries.get(index);
            int cursor = index - 1;
            while (cursor >= 0 && compareBriefingEntries(entries.get(cursor), current) > 0) {
                entries.set(cursor + 1, entries.get(cursor));
                cursor--;
            }
            entries.set(cursor + 1, current);
        }
    }

    private static int compareBriefingEntries(DailyBriefing.Entry left, DailyBriefing.Entry right) {
        if (left.plan.priorityScore < right.plan.priorityScore) {
            return 1;
        }
        if (left.plan.priorityScore > right.plan.priorityScore) {
            return -1;
        }
        return left.food.expiryDate.compareTo(right.food.expiryDate);
    }

    private static void sortEventsByDate(List<ReminderEvent> events) {
        for (int index = 1; index < events.size(); index++) {
            ReminderEvent current = events.get(index);
            int cursor = index - 1;
            while (cursor >= 0 && events.get(cursor).reminderDate.compareTo(current.reminderDate) > 0) {
                events.set(cursor + 1, events.get(cursor));
                cursor--;
            }
            events.set(cursor + 1, current);
        }
    }

    private static void sortOffsetsDescending(List<Integer> offsets) {
        for (int index = 1; index < offsets.size(); index++) {
            Integer current = offsets.get(index);
            int cursor = index - 1;
            while (cursor >= 0 && offsets.get(cursor).intValue() < current.intValue()) {
                offsets.set(cursor + 1, offsets.get(cursor));
                cursor--;
            }
            offsets.set(cursor + 1, current);
        }
    }

    private static String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(separator);
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    static String formattedScore(double score) {
        return String.format(Locale.US, "%.0f", score);
    }
}

final class ReminderSettings {
    static final String MODE_SMART = "smart";
    static final String MODE_FIXED = "fixed";
    static final String DEFAULT_MODE = MODE_SMART;
    static final boolean DEFAULT_ENABLED = true;
    static final String DEFAULT_ADVANCE_DAYS_TEXT = "7,3,1,0";
    static final String DEFAULT_TODAY_SLOTS_TEXT = "08:30,09:00,12:30,18:00,18:30";

    final boolean enabled;
    final String mode;
    final List<Integer> advanceDays;
    final List<String> todaySlots;

    ReminderSettings(boolean enabled, List<Integer> advanceDays, List<String> todaySlots) {
        this(enabled, inferredLegacyMode(advanceDays), advanceDays, todaySlots);
    }

    ReminderSettings(boolean enabled, String mode, List<Integer> advanceDays, List<String> todaySlots) {
        this.enabled = enabled;
        this.mode = normalizeMode(mode);
        this.advanceDays = advanceDays == null || advanceDays.isEmpty()
                ? parseAdvanceDaysOrNull(DEFAULT_ADVANCE_DAYS_TEXT)
                : normalizeAdvanceDays(advanceDays);
        this.todaySlots = todaySlots == null || todaySlots.isEmpty()
                ? parseTodaySlotsOrNull(DEFAULT_TODAY_SLOTS_TEXT)
                : normalizeTodaySlots(todaySlots);
    }

    static ReminderSettings defaults() {
        return fromStoredValues(DEFAULT_ENABLED, DEFAULT_MODE, DEFAULT_ADVANCE_DAYS_TEXT, DEFAULT_TODAY_SLOTS_TEXT);
    }

    static ReminderSettings validOrDefault(ReminderSettings settings) {
        return settings == null ? defaults() : settings;
    }

    static ReminderSettings fromStoredValues(boolean enabled, String advanceDaysText, String todaySlotsText) {
        List<Integer> advanceDays = parseAdvanceDaysOrNull(advanceDaysText);
        List<String> todaySlots = parseTodaySlotsOrNull(todaySlotsText);
        return new ReminderSettings(
                enabled,
                advanceDays == null ? parseAdvanceDaysOrNull(DEFAULT_ADVANCE_DAYS_TEXT) : advanceDays,
                todaySlots == null ? parseTodaySlotsOrNull(DEFAULT_TODAY_SLOTS_TEXT) : todaySlots
        );
    }

    static ReminderSettings fromStoredValues(
            boolean enabled,
            String mode,
            String advanceDaysText,
            String todaySlotsText
    ) {
        List<Integer> advanceDays = parseAdvanceDaysOrNull(advanceDaysText);
        List<String> todaySlots = parseTodaySlotsOrNull(todaySlotsText);
        return new ReminderSettings(
                enabled,
                mode,
                advanceDays == null ? parseAdvanceDaysOrNull(DEFAULT_ADVANCE_DAYS_TEXT) : advanceDays,
                todaySlots == null ? parseTodaySlotsOrNull(DEFAULT_TODAY_SLOTS_TEXT) : todaySlots
        );
    }

    static ReminderSettings fromInput(boolean enabled, String advanceDaysText, String todaySlotsText) {
        List<Integer> advanceDays = parseAdvanceDaysOrNull(advanceDaysText);
        String mode = sameIntegers(advanceDays, parseAdvanceDaysOrNull(DEFAULT_ADVANCE_DAYS_TEXT))
                ? MODE_SMART
                : MODE_FIXED;
        return fromInput(enabled, mode, advanceDaysText, todaySlotsText);
    }

    static ReminderSettings fromInput(
            boolean enabled,
            String mode,
            String advanceDaysText,
            String todaySlotsText
    ) {
        List<Integer> advanceDays = parseAdvanceDaysOrNull(advanceDaysText);
        List<String> todaySlots = parseTodaySlotsOrNull(todaySlotsText);
        if (advanceDays == null && MODE_SMART.equals(normalizeMode(mode))) {
            advanceDays = parseAdvanceDaysOrNull(DEFAULT_ADVANCE_DAYS_TEXT);
        }
        if (advanceDays == null || todaySlots == null) {
            return null;
        }
        return new ReminderSettings(enabled, mode, advanceDays, todaySlots);
    }

    List<Integer> offsetsFor(List<Integer> policyOffsets, int totalShelfLifeDays) {
        if (!enabled) {
            return new ArrayList<Integer>();
        }
        if (isSmartMode()) {
            return new ArrayList<Integer>(policyOffsets);
        }

        List<Integer> result = new ArrayList<Integer>();
        for (Integer offset : advanceDays) {
            if (offset != null
                    && offset.intValue() >= 0
                    && offset.intValue() <= totalShelfLifeDays
                    && !result.contains(offset)) {
                result.add(offset);
            }
        }
        sortOffsetsDescending(result);
        return result;
    }

    boolean usesDefaultAdvanceDays() {
        return sameIntegers(advanceDays, parseAdvanceDaysOrNull(DEFAULT_ADVANCE_DAYS_TEXT));
    }

    boolean usesDefaultTodaySlots() {
        return sameStrings(todaySlots, parseTodaySlotsOrNull(DEFAULT_TODAY_SLOTS_TEXT));
    }

    List<String> todayReminderSlots() {
        return new ArrayList<String>(todaySlots);
    }

    String advanceDaysText() {
        return joinIntegers(advanceDays);
    }

    String todaySlotsText() {
        return joinStrings(todaySlots);
    }

    String summaryText() {
        return (enabled ? "已启用" : "已关闭")
                + "；" + modeLabel()
                + "；提前天数 " + advanceDaysText()
                + "；今日时段 " + todaySlotsText();
    }

    boolean isSmartMode() {
        return MODE_SMART.equals(mode);
    }

    String modeLabel() {
        return isSmartMode() ? "智能提醒" : "固定日期";
    }

    private static String normalizeMode(String value) {
        return MODE_FIXED.equals(FoodItem.cleanText(value)) ? MODE_FIXED : MODE_SMART;
    }

    private static String inferredLegacyMode(List<Integer> advanceDays) {
        if (advanceDays == null || advanceDays.isEmpty()) {
            return MODE_SMART;
        }
        return sameIntegers(normalizeAdvanceDays(advanceDays), parseAdvanceDaysOrNull(DEFAULT_ADVANCE_DAYS_TEXT))
                ? MODE_SMART
                : MODE_FIXED;
    }

    private static List<Integer> normalizeAdvanceDays(List<Integer> source) {
        List<Integer> result = new ArrayList<Integer>();
        for (Integer value : source) {
            if (value == null || value.intValue() < 0 || result.contains(value)) {
                continue;
            }
            result.add(value);
        }
        sortOffsetsDescending(result);
        return result;
    }

    private static List<String> normalizeTodaySlots(List<String> source) {
        List<String> result = new ArrayList<String>();
        for (String value : source) {
            String slot = normalizeSlot(value);
            if (slot.length() > 0 && !result.contains(slot)) {
                result.add(slot);
            }
        }
        return result;
    }

    private static List<Integer> parseAdvanceDaysOrNull(String raw) {
        String text = FoodItem.cleanText(raw);
        if (text.length() == 0) {
            return null;
        }

        String[] parts = text.split(",");
        List<Integer> result = new ArrayList<Integer>();
        for (int index = 0; index < parts.length; index++) {
            String part = FoodItem.cleanText(parts[index]);
            if (part.length() == 0) {
                return null;
            }
            try {
                Integer value = Integer.valueOf(Integer.parseInt(part));
                if (value.intValue() < 0) {
                    return null;
                }
                if (!result.contains(value)) {
                    result.add(value);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        if (result.isEmpty()) {
            return null;
        }
        sortOffsetsDescending(result);
        return result;
    }

    private static List<String> parseTodaySlotsOrNull(String raw) {
        String text = FoodItem.cleanText(raw);
        if (text.length() == 0) {
            return null;
        }

        String[] parts = text.split(",");
        List<String> result = new ArrayList<String>();
        for (int index = 0; index < parts.length; index++) {
            String slot = normalizeSlot(parts[index]);
            if (slot.length() == 0) {
                return null;
            }
            if (!result.contains(slot)) {
                result.add(slot);
            }
        }

        return result.isEmpty() ? null : result;
    }

    private static String normalizeSlot(String raw) {
        String text = FoodItem.cleanText(raw);
        int separator = text.indexOf(':');
        if (separator <= 0 || separator != text.lastIndexOf(':') || separator == text.length() - 1) {
            return "";
        }

        try {
            int hour = Integer.parseInt(text.substring(0, separator));
            int minute = Integer.parseInt(text.substring(separator + 1));
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return "";
            }
            return String.format(Locale.US, "%02d:%02d", hour, minute);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static boolean sameIntegers(List<Integer> left, List<Integer> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (!left.get(index).equals(right.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStrings(List<String> left, List<String> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (!left.get(index).equals(right.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static void sortOffsetsDescending(List<Integer> offsets) {
        for (int index = 1; index < offsets.size(); index++) {
            Integer current = offsets.get(index);
            int cursor = index - 1;
            while (cursor >= 0 && offsets.get(cursor).intValue() < current.intValue()) {
                offsets.set(cursor + 1, offsets.get(cursor));
                cursor--;
            }
            offsets.set(cursor + 1, current);
        }
    }

    private static String joinIntegers(List<Integer> values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private static String joinStrings(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }
}
