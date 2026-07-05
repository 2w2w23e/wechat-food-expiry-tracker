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

        int expiryDaysLeft = DateRules.daysUntil(food.expiryDate);
        String afterOpenRecommendedDate = DateRules.addAfterOpenShelfLife(
                food.openedDate,
                food.afterOpenShelfLifeValue,
                food.afterOpenShelfLifeUnit
        );
        boolean hasAfterOpenRecommendedDate = DateRules.isValidDateString(afterOpenRecommendedDate);
        boolean usesAfterOpenDate = hasAfterOpenRecommendedDate
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
        plan.priorityScore = priorityScore(daysLeft, riskLevel, storage, food.remainingQuantity);
        plan.priorityBand = priorityBand(plan.priorityScore);

        List<Integer> offsets = effectiveSettings.offsetsFor(
                adjustedOffsets(food, totalShelfLifeDays, riskLevel, storage),
                totalShelfLifeDays
        );
        plan.offsets.addAll(offsets);
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

        if (shouldAddPostExpiryReminder(riskLevel, storage)) {
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

            if (DateRules.isYesterday(food.expiryDate)) {
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
            } else if (plan.daysLeft > 0 && plan.hasReminderInNextDays(7)) {
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

    private static List<Integer> adjustedOffsets(FoodItem food, int totalShelfLifeDays, String riskLevel, String storage) {
        List<Integer> offsets = baseOffsets(totalShelfLifeDays);

        if (RISK_HIGH.equals(riskLevel)) {
            addHighRiskEarlyOffset(offsets, totalShelfLifeDays);
            return capOffsets(uniqueValidOffsets(offsets, totalShelfLifeDays), 6);
        }

        if (RISK_LOW.equals(riskLevel)) {
            return capOffsets(lowRiskOffsets(offsets, totalShelfLifeDays), 4);
        }

        return capOffsets(uniqueValidOffsets(offsets, totalShelfLifeDays), 5);
    }

    private static List<Integer> baseOffsets(int totalShelfLifeDays) {
        if (totalShelfLifeDays <= 3) {
            return new ArrayList<Integer>(Arrays.asList(1, 0));
        }
        if (totalShelfLifeDays <= 7) {
            return new ArrayList<Integer>(Arrays.asList(3, 1, 0));
        }
        if (totalShelfLifeDays <= 14) {
            return new ArrayList<Integer>(Arrays.asList(5, 2, 0));
        }
        if (totalShelfLifeDays <= 30) {
            return new ArrayList<Integer>(Arrays.asList(14, 7, 3, 1, 0));
        }
        if (totalShelfLifeDays <= 90) {
            return new ArrayList<Integer>(Arrays.asList(30, 14, 7, 3, 0));
        }
        if (totalShelfLifeDays <= 180) {
            return new ArrayList<Integer>(Arrays.asList(30, 14, 7, 0));
        }
        if (totalShelfLifeDays <= 365) {
            return new ArrayList<Integer>(Arrays.asList(60, 30, 14, 7, 0));
        }
        return new ArrayList<Integer>(Arrays.asList(90, 30, 7, 0));
    }

    private static void addHighRiskEarlyOffset(List<Integer> offsets, int totalShelfLifeDays) {
        int candidate = 0;
        if (totalShelfLifeDays <= 7) {
            candidate = 5;
        } else if (totalShelfLifeDays <= 14) {
            candidate = 7;
        } else {
            int largest = largestPositiveOffset(offsets);
            int[] candidates = new int[] { 14, 30, 60 };
            for (int index = 0; index < candidates.length; index++) {
                if (candidates[index] > largest && candidates[index] <= totalShelfLifeDays) {
                    candidate = candidates[index];
                    break;
                }
            }
        }

        if (candidate > 0 && candidate <= totalShelfLifeDays && !offsets.contains(Integer.valueOf(candidate))) {
            offsets.add(Integer.valueOf(candidate));
        }
    }

    private static List<Integer> lowRiskOffsets(List<Integer> baseOffsets, int totalShelfLifeDays) {
        List<Integer> reduced = new ArrayList<Integer>();
        if (totalShelfLifeDays > 365) {
            addOffsetIfValid(reduced, 90, totalShelfLifeDays);
            addOffsetIfValid(reduced, 30, totalShelfLifeDays);
            addOffsetIfValid(reduced, 7, totalShelfLifeDays);
            addOffsetIfValid(reduced, 0, totalShelfLifeDays);
            return uniqueValidOffsets(reduced, totalShelfLifeDays);
        }

        if (totalShelfLifeDays > 90) {
            addOffsetIfValid(reduced, 30, totalShelfLifeDays);
            addOffsetIfValid(reduced, 7, totalShelfLifeDays);
            addOffsetIfValid(reduced, 0, totalShelfLifeDays);
            return uniqueValidOffsets(reduced, totalShelfLifeDays);
        }

        int largest = largestPositiveOffset(baseOffsets);
        addOffsetIfValid(reduced, largest, totalShelfLifeDays);
        if (baseOffsets.contains(Integer.valueOf(7))) {
            addOffsetIfValid(reduced, 7, totalShelfLifeDays);
        }
        addOffsetIfValid(reduced, 0, totalShelfLifeDays);
        return uniqueValidOffsets(reduced, totalShelfLifeDays);
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
        for (Integer offset : offsets) {
            if (offset.intValue() == 0) {
                continue;
            }
            if (result.size() < earlyLimit) {
                result.add(offset);
            }
        }
        if (hasDueDay) {
            result.add(Integer.valueOf(0));
        }
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

    private static String cardHint(FoodItem food, ReminderPlan plan) {
        if (plan.usesAfterOpenDate) {
            return afterOpenCardHint(food, plan);
        }

        String afterOpenSuffix = afterOpenSuffix(plan);
        if (plan.daysLeft < 0) {
            return expiredCopy(food) + afterOpenSuffix + zeroQuantitySuffix(food);
        }
        if (plan.daysLeft == 0) {
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

        return "提醒：还有 " + plan.daysLeft + " 天到期 · " + plan.priorityBand + afterOpenSuffix + zeroQuantitySuffix(food);
    }

    private static String nextReminderSummary(FoodItem food, ReminderPlan plan) {
        if (plan.usesAfterOpenDate) {
            return afterOpenNextReminderSummary(plan);
        }

        if (plan.daysLeft < 0) {
            return expiredCopy(food);
        }
        if (plan.daysLeft == 0) {
            return "今天到期，请优先查看";
        }
        if (plan.daysLeft == 1) {
            return "明天到期，建议优先处理";
        }

        ReminderEvent next = plan.nextFutureEvent();
        if (next == null) {
            return "还有 " + plan.daysLeft + " 天到期，可以安排食用";
        }

        int daysToReminder = DateRules.daysBetween(DateRules.todayString(), next.reminderDate);
        if (daysToReminder == 0) {
            return "今天提醒：还有 " + plan.daysLeft + " 天到期，请优先查看";
        }
        return daysToReminder + " 天后提醒：还有 " + plan.daysLeft + " 天到期，可以安排食用";
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
        return daysToReminder + " 天后提醒：还有 " + plan.daysLeft + " 天到开封后建议处理日，可以提前安排";
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
    static final boolean DEFAULT_ENABLED = true;
    static final String DEFAULT_ADVANCE_DAYS_TEXT = "7,3,1,0";
    static final String DEFAULT_TODAY_SLOTS_TEXT = "08:30,09:00,12:30,18:00,18:30";

    final boolean enabled;
    final List<Integer> advanceDays;
    final List<String> todaySlots;

    ReminderSettings(boolean enabled, List<Integer> advanceDays, List<String> todaySlots) {
        this.enabled = enabled;
        this.advanceDays = advanceDays == null || advanceDays.isEmpty()
                ? parseAdvanceDaysOrNull(DEFAULT_ADVANCE_DAYS_TEXT)
                : normalizeAdvanceDays(advanceDays);
        this.todaySlots = todaySlots == null || todaySlots.isEmpty()
                ? parseTodaySlotsOrNull(DEFAULT_TODAY_SLOTS_TEXT)
                : normalizeTodaySlots(todaySlots);
    }

    static ReminderSettings defaults() {
        return fromStoredValues(DEFAULT_ENABLED, DEFAULT_ADVANCE_DAYS_TEXT, DEFAULT_TODAY_SLOTS_TEXT);
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

    static ReminderSettings fromInput(boolean enabled, String advanceDaysText, String todaySlotsText) {
        List<Integer> advanceDays = parseAdvanceDaysOrNull(advanceDaysText);
        List<String> todaySlots = parseTodaySlotsOrNull(todaySlotsText);
        if (advanceDays == null || todaySlots == null) {
            return null;
        }
        return new ReminderSettings(enabled, advanceDays, todaySlots);
    }

    List<Integer> offsetsFor(List<Integer> policyOffsets, int totalShelfLifeDays) {
        if (!enabled) {
            return new ArrayList<Integer>();
        }
        if (usesDefaultAdvanceDays()) {
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
                + "；提前天数 " + advanceDaysText()
                + "；今日时段 " + todaySlotsText();
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
