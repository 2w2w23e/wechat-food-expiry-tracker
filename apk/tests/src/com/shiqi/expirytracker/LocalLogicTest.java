package com.shiqi.expirytracker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class LocalLogicTest {
    private int passed = 0;
    private int failed = 0;

    public static void main(String[] args) {
        LocalLogicTest test = new LocalLogicTest();
        test.run();
    }

    private void run() {
        test("DateRules validates strict yyyy-MM-dd dates", new TestCase() {
            public void run() {
                assertTrue(DateRules.isValidDateString("2024-02-29"), "leap day should be valid");
                assertFalse(DateRules.isValidDateString("2023-02-29"), "non-leap day should be invalid");
                assertFalse(DateRules.isValidDateString("2026-13-01"), "month 13 should be invalid");
                assertFalse(DateRules.isValidDateString("2026-1-01"), "non-padded dates should be invalid");
                assertFalse(DateRules.isValidDateString(null), "null should be invalid");
            }
        });

        test("DateRules calculates shelf life dates", new TestCase() {
            public void run() {
                assertEquals("2026-01-08", DateRules.addShelfLife("2026-01-01", Integer.valueOf(7), "day"));
                assertEquals("2026-02-28", DateRules.addShelfLife("2026-01-31", Integer.valueOf(1), "month"));
                assertEquals("2024-02-29", DateRules.addShelfLife("2024-01-31", Integer.valueOf(1), "month"));
                assertEquals("2025-02-28", DateRules.addShelfLife("2024-02-29", Integer.valueOf(1), "year"));
                assertEquals("", DateRules.addShelfLife("2026-01-01", Integer.valueOf(0), "day"));
                assertEquals("", DateRules.addShelfLife("2026-01-01", Integer.valueOf(1), "week"));
                assertEquals("", DateRules.addShelfLife("bad-date", Integer.valueOf(1), "day"));
            }
        });

        test("DateRules handles day math and relative expiry status", new TestCase() {
            public void run() {
                assertEquals(2, DateRules.daysBetween("2024-02-28", "2024-03-01"));
                assertEquals("2024-02-29", DateRules.addDaysString("2024-03-01", -1));
                assertEquals("", DateRules.addDaysString("2024-02-30", 1));

                String today = DateRules.todayString();
                assertEquals(DateRules.STATUS_EXPIRED, DateRules.getExpiryStatus(DateRules.addDaysString(today, -1)));
                assertEquals(DateRules.STATUS_TODAY, DateRules.getExpiryStatus(today));
                assertEquals(DateRules.STATUS_SOON, DateRules.getExpiryStatus(DateRules.addDaysString(today, 7)));
                assertEquals(DateRules.STATUS_NORMAL, DateRules.getExpiryStatus(DateRules.addDaysString(today, 8)));
                assertEquals(DateRules.STATUS_UNKNOWN, DateRules.getExpiryStatus("not-a-date"));
            }
        });

        test("BarcodeUtils normalizes and validates product codes", new TestCase() {
            public void run() {
                assertEquals("4006381333931", BarcodeUtils.digitsOnly(" EAN 400-6381-333931 "));
                assertEquals("", BarcodeUtils.digitsOnly(null));
                assertTrue(BarcodeUtils.isSupportedProductCode("4006381333931"), "valid EAN-13 should be supported");
                assertTrue(BarcodeUtils.isSupportedProductCode("036000291452"), "valid UPC-A should be supported");
                assertTrue(BarcodeUtils.isSupportedProductCode("96385074"), "valid EAN-8 should be supported");
                assertFalse(BarcodeUtils.isSupportedProductCode("4006381333932"), "bad check digit should be rejected");
                assertFalse(BarcodeUtils.isSupportedProductCode("1234567"), "unsupported length should be rejected");
                assertEquals("04006381333931", BarcodeUtils.toGtin14("4006381333931"));
                assertEquals("4006381333931", BarcodeUtils.stripLeadingGtinZero("04006381333931"));
            }
        });

        test("BarcodeUtils extracts codes from common scan payloads", new TestCase() {
            public void run() {
                assertEquals("4006381333931", BarcodeUtils.extractProductCode(" 4006381333931 "));
                assertEquals("4006381333931", BarcodeUtils.extractProductCode("https://example.test/item?gtin=4006381333931"));
                assertEquals("4006381333931", BarcodeUtils.extractProductCode("https://example.test/item?barcode=4006381333931&amp;source=test"));
                assertEquals("04006381333931", BarcodeUtils.extractProductCode("https://id.gs1.org/01/04006381333931/10/LOT123"));
                assertEquals("04006381333931", BarcodeUtils.extractProductCode("(01)04006381333931(17)260101"));
                assertEquals("", BarcodeUtils.extractProductCode("no supported product code here 1234567"));
            }
        });

        test("ReminderPolicy disables reminders when required data is missing", new TestCase() {
            public void run() {
                ReminderPlan nullPlan = ReminderPolicy.planFor(null);
                assertFalse(nullPlan.enabled, "null food should be disabled");
                assertTrue(nullPlan.disabledReason.length() > 0, "null food should explain disabled state");

                FoodItem invalid = food("No date", "other", "room_temp", "", "", 1);
                ReminderPlan invalidPlan = ReminderPolicy.planFor(invalid);
                assertFalse(invalidPlan.enabled, "missing expiryDate should be disabled");

                FoodItem finished = food("Finished", "other", "room_temp", "", DateRules.todayString(), 1);
                finished.isFinished = true;
                finished.finishedAt = DateRules.todayString();
                ReminderPlan finishedPlan = ReminderPolicy.planFor(finished);
                assertFalse(finishedPlan.enabled, "finished food should be disabled");
                assertTrue(finishedPlan.cardHint.length() > 0, "finished food should have a card hint");
            }
        });

        test("ReminderPolicy plans high-risk refrigerated food", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 1);
                FoodItem milk = food("Milk", "dairy", "refrigerated", DateRules.addDaysString(expiry, -5), expiry, 2);
                ReminderPlan plan = ReminderPolicy.planFor(milk);

                assertTrue(plan.enabled, "valid high-risk food should be enabled");
                assertEquals(1, plan.daysLeft);
                assertEquals(5, plan.totalShelfLifeDays);
                assertEquals("A", plan.riskLevel);
                assertEquals(ReminderSettings.MODE_SMART, plan.reminderMode);
                assertListContains(plan.offsets, Integer.valueOf(1));
                assertListContains(plan.offsets, Integer.valueOf(0));
                assertAllOffsetsAtMost(plan.offsets, plan.daysLeft);
                assertTrue(plan.events.size() >= plan.offsets.size(), "events should include all offsets");
                assertEventsSorted(plan.events);
            }
        });

        test("ReminderPolicy reduces low-risk frozen reminders", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 30);
                FoodItem frozen = food("Frozen peas", "frozen", "frozen", DateRules.addDaysString(expiry, -120), expiry, 1);
                ReminderPlan plan = ReminderPolicy.planFor(frozen);

                assertTrue(plan.enabled, "valid frozen food should be enabled");
                assertEquals("C", plan.riskLevel);
                assertEquals(Arrays.asList(Integer.valueOf(7), Integer.valueOf(0)), plan.offsets);
                for (ReminderEvent event : plan.events) {
                    assertFalse(event.postExpiry, "low-risk frozen food should not add post-expiry reminders");
                }
            }
        });

        test("ReminderPolicy adds due-day hours and daily briefing buckets", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                FoodItem dueToday = food("Due today", "meat_egg_seafood", "refrigerated", DateRules.addDaysString(today, -3), today, 1);
                ReminderPlan duePlan = ReminderPolicy.planFor(dueToday);
                assertEquals(Arrays.asList("08:30", "12:30", "18:30"), duePlan.dueDayHours);

                FoodItem expiredYesterday = food("Expired yesterday", "produce", "room_temp", DateRules.addDaysString(today, -5), DateRules.addDaysString(today, -1), 1);
                FoodItem upcoming = food("Upcoming", "staple", "room_temp", DateRules.addDaysString(today, -7), DateRules.addDaysString(today, 1), 1);

                List<FoodItem> foods = new ArrayList<FoodItem>();
                foods.add(upcoming);
                foods.add(expiredYesterday);
                foods.add(dueToday);

                DailyBriefing briefing = ReminderPolicy.dailyBriefing(foods);
                assertEquals(1, briefing.yesterdayExpired.size());
                assertEquals(1, briefing.todayDue.size());
                assertEquals(1, briefing.upcoming.size());
            }
        });

        test("Large local inventory search status and reminders stay stable", new TestCase() {
            public void run() {
                long startNanos = System.nanoTime();
                List<FoodItem> foods = largeInventory(640);
                assertEquals(640, foods.size());

                int milkMatches = 0;
                int noResultMatches = 0;
                int expiredCount = 0;
                int todayCount = 0;
                int soonCount = 0;
                int normalCount = 0;
                int unknownCount = 0;
                int enabledPlans = 0;
                int disabledPlans = 0;
                int reminderEvents = 0;

                for (FoodItem item : foods) {
                    String status = DateRules.getExpiryStatus(item.expiryDate);
                    assertTrue(isKnownExpiryStatus(status), "unexpected status: " + status);
                    if (DateRules.STATUS_EXPIRED.equals(status)) {
                        expiredCount++;
                    } else if (DateRules.STATUS_TODAY.equals(status)) {
                        todayCount++;
                    } else if (DateRules.STATUS_SOON.equals(status)) {
                        soonCount++;
                    } else if (DateRules.STATUS_NORMAL.equals(status)) {
                        normalCount++;
                    } else if (DateRules.STATUS_UNKNOWN.equals(status)) {
                        unknownCount++;
                    }

                    if (FoodData.matchesFoodSearch(item, "milk")) {
                        milkMatches++;
                    }
                    if (FoodData.matchesFoodSearch(item, "not-present-query")) {
                        noResultMatches++;
                    }

                    ReminderPlan plan = ReminderPolicy.planFor(item);
                    if (plan.enabled) {
                        enabledPlans++;
                        reminderEvents += plan.events.size();
                        assertTrue(DateRules.isValidDateString(item.expiryDate), "enabled plan must have a valid expiryDate");
                    } else {
                        disabledPlans++;
                    }
                }

                assertTrue(milkMatches > 0, "search should find generated milk items");
                assertEquals(0, noResultMatches);
                assertTrue(expiredCount > 0, "inventory should include expired items");
                assertTrue(todayCount > 0, "inventory should include today-expiring items");
                assertTrue(soonCount > 0, "inventory should include soon-expiring items");
                assertTrue(normalCount > 0, "inventory should include normal items");
                assertTrue(unknownCount > 0, "inventory should include unknown-date items");
                assertTrue(enabledPlans > 0, "valid items should produce reminder plans");
                assertTrue(disabledPlans > 0, "missing-date or finished items should disable reminders");
                assertTrue(reminderEvents > 0, "enabled reminder plans should include events");

                long elapsedMs = (System.nanoTime() - startNanos) / 1000000L;
                System.out.println("PERF large inventory probe: items=" + foods.size()
                        + ", milkMatches=" + milkMatches
                        + ", enabledPlans=" + enabledPlans
                        + ", disabledPlans=" + disabledPlans
                        + ", reminderEvents=" + reminderEvents
                        + ", elapsedMs=" + elapsedMs);
            }
        });

        test("FoodItem accepts old JSON without after-open fields", new TestCase() {
            public void run() {
                FoodItem item = FoodItem.fromJson(new org.json.JSONObject());
                assertEquals("", item.openedDate);
                assertEquals(null, item.afterOpenShelfLifeValue);
                assertEquals("", item.afterOpenShelfLifeUnit);
                assertEquals(0, item.smartReminderOffsets.size());
                assertEquals("", item.smartReminderFingerprint);
                assertEquals(Integer.MIN_VALUE, item.smartReminderPlannedDaysLeft);
                assertEquals("", item.smartReminderPlannedOn);
            }
        });

        test("FoodItem defaults old JSON location", new TestCase() {
            public void run() {
                FoodItem item = FoodItem.fromJson(new org.json.JSONObject());
                assertEquals(FoodData.LOCATION_UNSPECIFIED, item.location);
                assertEquals("未指定", FoodData.locationLabel(item.location));
            }
        });

        test("FoodData labels standard locations", new TestCase() {
            public void run() {
                assertEquals("冰箱", FoodData.locationLabel("fridge"));
                assertEquals("freezer", FoodData.normalizeLocationValue("冷冻"));
                assertEquals("常温柜", FoodData.locationLabel("pantry"));
            }
        });

        test("FoodItem preserves custom location strings", new TestCase() {
            public void run() {
                org.json.JSONObject raw = new org.json.JSONObject();
                try {
                    raw.put("location", "餐边柜");
                } catch (org.json.JSONException error) {
                    throw new AssertionError("failed to build location JSON: " + error.getMessage());
                }

                FoodItem item = FoodItem.fromJson(raw);
                assertEquals("餐边柜", item.location);
                assertEquals("餐边柜", FoodData.locationLabel(item.location));
                assertEquals("餐边柜", item.copy().location);
                try {
                    assertEquals("餐边柜", item.toJson().optString("location", ""));
                } catch (org.json.JSONException error) {
                    throw new AssertionError("failed to serialize location JSON: " + error.getMessage());
                }
            }
        });

        test("FoodItem clamps quantity and remaining boundaries", new TestCase() {
            public void run() {
                assertEquals(Double.valueOf(0), Double.valueOf(FoodItem.normalizedQuantity(-1)));
                assertEquals(Double.valueOf(0), Double.valueOf(FoodItem.clampedRemainingQuantity(-2, 3)));
                assertEquals(Double.valueOf(3), Double.valueOf(FoodItem.clampedRemainingQuantity(5, 3)));

                org.json.JSONObject raw = new org.json.JSONObject();
                try {
                    raw.put("quantity", 2);
                    raw.put("remainingQuantity", 5);
                } catch (org.json.JSONException error) {
                    throw new AssertionError("failed to build quantity JSON: " + error.getMessage());
                }

                FoodItem item = FoodItem.fromJson(raw);
                assertEquals(Double.valueOf(2), Double.valueOf(item.quantity));
                assertEquals(Double.valueOf(2), Double.valueOf(item.remainingQuantity));
            }
        });

        test("FoodItem copies a food as a fresh unfinished record", new TestCase() {
            public void run() {
                FoodItem source = food("Copy source", "dairy", "refrigerated", "2026-07-01", "2026-07-08", 1);
                source.id = "old-id";
                source.quantity = 4;
                source.remainingQuantity = 1;
                source.unit = "box";
                source.location = "fridge";
                source.shelfLifeValue = Integer.valueOf(7);
                source.shelfLifeUnit = "day";
                source.dateSource = "calculated";
                source.openedDate = "2026-07-03";
                source.afterOpenShelfLifeValue = Integer.valueOf(2);
                source.afterOpenShelfLifeUnit = "day";
                source.createdAt = "old-created";
                source.updatedAt = "old-updated";
                source.isFinished = true;
                source.finishedAt = "2026-07-04T08:00:00+0800";
                source.smartReminderOffsets.add(Integer.valueOf(3));
                source.smartReminderOffsets.add(Integer.valueOf(0));
                source.smartReminderFingerprint = "stored-plan";
                source.smartReminderPlannedDaysLeft = 3;
                source.smartReminderPlannedOn = "2026-07-05";

                FoodItem copied = source.copyAsNewRecord("new-id", "2026-07-05T08:00:00+0800");

                assertEquals("new-id", copied.id);
                assertEquals("Copy source", copied.name);
                assertEquals("dairy", copied.category);
                assertEquals("refrigerated", copied.storageMethod);
                assertEquals("fridge", copied.location);
                assertEquals("box", copied.unit);
                assertEquals("2026-07-01", copied.productionDate);
                assertEquals(Integer.valueOf(7), copied.shelfLifeValue);
                assertEquals("day", copied.shelfLifeUnit);
                assertEquals("2026-07-08", copied.expiryDate);
                assertEquals("calculated", copied.dateSource);
                assertEquals("2026-07-03", copied.openedDate);
                assertEquals(Integer.valueOf(2), copied.afterOpenShelfLifeValue);
                assertEquals("day", copied.afterOpenShelfLifeUnit);
                assertEquals("2026-07-05T08:00:00+0800", copied.createdAt);
                assertEquals("2026-07-05T08:00:00+0800", copied.updatedAt);
                assertEquals(Double.valueOf(4), Double.valueOf(copied.quantity));
                assertEquals(Double.valueOf(4), Double.valueOf(copied.remainingQuantity));
                assertFalse(copied.isFinished, "copied food should not copy finished status");
                assertEquals("", copied.finishedAt);
                assertEquals(0, copied.smartReminderOffsets.size());
                assertEquals("", copied.smartReminderFingerprint);
                assertEquals(Integer.MIN_VALUE, copied.smartReminderPlannedDaysLeft);
                assertEquals("", copied.smartReminderPlannedOn);
            }
        });

        test("FoodItem persists a smart reminder snapshot", new TestCase() {
            public void run() {
                FoodItem source = new FoodItem();
                source.smartReminderOffsets.add(Integer.valueOf(7));
                source.smartReminderOffsets.add(Integer.valueOf(1));
                source.smartReminderOffsets.add(Integer.valueOf(0));
                source.smartReminderFingerprint = "category|storage|dates";
                source.smartReminderPlannedDaysLeft = 10;
                source.smartReminderPlannedOn = "2026-07-14";
                try {
                    FoodItem restored = FoodItem.fromJson(source.toJson());
                    assertEquals(source.smartReminderOffsets, restored.smartReminderOffsets);
                    assertEquals(source.smartReminderFingerprint, restored.smartReminderFingerprint);
                    assertEquals(10, restored.smartReminderPlannedDaysLeft);
                    assertEquals("2026-07-14", restored.smartReminderPlannedOn);
                } catch (org.json.JSONException error) {
                    throw new AssertionError("failed to round-trip reminder snapshot: " + error.getMessage());
                }
            }
        });

        test("DateRules calculates after-open dates for month-end and leap years", new TestCase() {
            public void run() {
                assertEquals("", DateRules.addAfterOpenShelfLife("", null, ""));
                assertEquals("", DateRules.addAfterOpenShelfLife("2026-02-30", Integer.valueOf(1), "day"));
                assertEquals("2026-02-28", DateRules.addAfterOpenShelfLife("2026-01-31", Integer.valueOf(1), "month"));
                assertEquals("2024-02-29", DateRules.addAfterOpenShelfLife("2024-01-31", Integer.valueOf(1), "month"));
                assertEquals("2025-02-28", DateRules.addAfterOpenShelfLife("2024-02-29", Integer.valueOf(1), "year"));
            }
        });

        test("ReminderPolicy leaves unopened food on expiryDate", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 8);
                FoodItem unopened = food("Unopened", "staple", "room_temp", DateRules.addDaysString(today, -2), expiry, 1);
                ReminderPlan plan = ReminderPolicy.planFor(unopened);

                assertTrue(plan.enabled, "unopened food should still have reminders");
                assertFalse(plan.usesAfterOpenDate, "unopened food should not use after-open date");
                assertEquals(expiry, plan.effectiveReminderDate);
                assertEquals("", plan.afterOpenRecommendedDate);
                assertEquals(8, plan.daysLeft);
                assertEquals(8, plan.expiryDaysLeft);
            }
        });

        test("ReminderPolicy prioritizes after-open date when earlier than expiryDate", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 20);
                String suggested = DateRules.addDaysString(today, 3);
                FoodItem opened = food("Opened early", "staple", "room_temp", DateRules.addDaysString(today, -4), expiry, 1);
                opened.openedDate = today;
                opened.afterOpenShelfLifeValue = Integer.valueOf(3);
                opened.afterOpenShelfLifeUnit = "day";
                ReminderPolicy.ensureSmartSchedule(opened);

                ReminderPlan plan = ReminderPolicy.planFor(opened);
                assertTrue(plan.enabled, "opened food should have reminders");
                assertTrue(plan.usesAfterOpenDate, "earlier after-open date should become effective reminder date");
                assertEquals(expiry, opened.expiryDate);
                assertEquals(suggested, plan.afterOpenRecommendedDate);
                assertEquals(suggested, plan.effectiveReminderDate);
                assertEquals(3, plan.daysLeft);
                assertEquals(20, plan.expiryDaysLeft);
                assertListContains(plan.offsets, Integer.valueOf(1));
                assertListContains(plan.offsets, Integer.valueOf(0));
            }
        });

        test("ReminderPolicy displays later after-open date without overriding expiryDate", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 5);
                String suggested = DateRules.addDaysString(today, 10);
                FoodItem opened = food("Opened later", "staple", "room_temp", DateRules.addDaysString(today, -4), expiry, 1);
                opened.openedDate = today;
                opened.afterOpenShelfLifeValue = Integer.valueOf(10);
                opened.afterOpenShelfLifeUnit = "day";
                ReminderPolicy.ensureSmartSchedule(opened);

                ReminderPlan plan = ReminderPolicy.planFor(opened);
                assertTrue(plan.enabled, "valid expiryDate should keep reminders enabled");
                assertFalse(plan.usesAfterOpenDate, "later after-open date should not override expiryDate priority");
                assertEquals(expiry, opened.expiryDate);
                assertEquals(suggested, plan.afterOpenRecommendedDate);
                assertEquals(expiry, plan.effectiveReminderDate);
                assertEquals(5, plan.daysLeft);
                assertEquals(5, plan.expiryDaysLeft);
            }
        });

        test("ReminderPolicy ignores illegal openedDate", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 6);
                FoodItem opened = food("Bad opened date", "staple", "room_temp", DateRules.addDaysString(today, -4), expiry, 1);
                opened.openedDate = "2026-02-30";
                opened.afterOpenShelfLifeValue = Integer.valueOf(1);
                opened.afterOpenShelfLifeUnit = "day";
                ReminderPolicy.ensureSmartSchedule(opened);

                ReminderPlan plan = ReminderPolicy.planFor(opened);
                assertTrue(plan.enabled, "bad openedDate should not disable expiryDate reminders");
                assertFalse(plan.usesAfterOpenDate, "bad openedDate should not affect priority");
                assertEquals("", plan.afterOpenRecommendedDate);
                assertEquals(expiry, plan.effectiveReminderDate);
                assertEquals(6, plan.daysLeft);
            }
        });

        test("ReminderSettings keeps default reminder behavior", new TestCase() {
            public void run() {
                ReminderSettings settings = ReminderSettings.defaults();
                assertTrue(settings.enabled, "default reminders should be enabled");
                assertEquals(ReminderSettings.MODE_SMART, settings.mode);
                assertEquals(Arrays.asList(Integer.valueOf(7), Integer.valueOf(3), Integer.valueOf(1), Integer.valueOf(0)), settings.advanceDays);
                assertEquals(Arrays.asList("08:30", "09:00", "12:30", "18:00", "18:30"), settings.todayReminderSlots());
                assertTrue(settings.usesDefaultAdvanceDays(), "default advance days should keep policy offsets");
                assertTrue(settings.usesDefaultTodaySlots(), "default today slots should keep risk-based due-day hours");

                String today = DateRules.todayString();
                FoodItem dueToday = food("Default due", "meat_egg_seafood", "refrigerated", DateRules.addDaysString(today, -3), today, 1);
                ReminderPlan plan = ReminderPolicy.planFor(dueToday, settings);
                assertEquals(Arrays.asList("08:30", "12:30", "18:30"), plan.dueDayHours);
            }
        });

        test("ReminderSettings disables reminder plans without deleting food", new TestCase() {
            public void run() {
                ReminderSettings settings = ReminderSettings.fromInput(false, "7,3,1,0", "08:30");
                String today = DateRules.todayString();
                FoodItem food = food("Disabled reminders", "dairy", "refrigerated", DateRules.addDaysString(today, -2), DateRules.addDaysString(today, 2), 1);
                ReminderPlan plan = ReminderPolicy.planFor(food, settings);

                assertFalse(plan.enabled, "disabled settings should disable plans");
                assertEquals(0, plan.events.size());
                assertTrue(plan.disabledReason.indexOf("关闭") >= 0, "disabled plan should explain reminders are closed");

                List<FoodItem> foods = new ArrayList<FoodItem>();
                foods.add(food);
                DailyBriefing briefing = ReminderPolicy.dailyBriefing(foods, settings);
                assertTrue(briefing.isEmpty(), "disabled settings should suppress briefing entries");
                assertEquals("Disabled reminders", food.name);
            }
        });

        test("ReminderSettings applies custom advance days", new TestCase() {
            public void run() {
                ReminderSettings settings = ReminderSettings.fromInput(true, "2,0", "09:00");
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 5);
                FoodItem food = food("Custom days", "staple", "room_temp", DateRules.addDaysString(today, -5), expiry, 1);
                ReminderPlan plan = ReminderPolicy.planFor(food, settings);

                assertTrue(plan.enabled, "custom reminder settings should keep valid plans enabled");
                assertEquals(Arrays.asList(Integer.valueOf(2), Integer.valueOf(0)), plan.offsets);
                assertEquals(2, nonPostExpiryEventCount(plan.events));
                assertTrue(settings.summaryText().indexOf("2,0") >= 0, "summary should include custom advance days");
            }
        });

        test("ReminderSettings applies custom today slots", new TestCase() {
            public void run() {
                ReminderSettings settings = ReminderSettings.fromInput(true, "7,3,1,0", "8:05,19:45");
                String today = DateRules.todayString();
                FoodItem dueToday = food("Custom today slots", "meat_egg_seafood", "refrigerated", DateRules.addDaysString(today, -3), today, 1);
                ReminderPlan plan = ReminderPolicy.planFor(dueToday, settings);

                assertEquals(Arrays.asList("08:05", "19:45"), settings.todayReminderSlots());
                assertEquals(Arrays.asList("08:05", "19:45"), ReminderPolicy.dueDayHours("A", settings));
                assertEquals(Arrays.asList("08:05", "19:45"), plan.dueDayHours);
            }
        });

        test("ReminderSettings loads legacy values as smart mode", new TestCase() {
            public void run() {
                ReminderSettings settings = ReminderSettings.fromStoredValues(
                        true,
                        "7,3,1,0",
                        "08:30,18:00"
                );
                assertEquals(ReminderSettings.MODE_SMART, settings.mode);
                assertTrue(settings.isSmartMode(), "legacy settings should preserve risk-based behavior");
            }
        });

        test("ReminderSettings keeps legacy custom days as fixed mode", new TestCase() {
            public void run() {
                ReminderSettings settings = ReminderSettings.fromStoredValues(
                        true,
                        "2,0",
                        "08:30,18:00"
                );
                assertEquals(ReminderSettings.MODE_FIXED, settings.mode);
                assertFalse(settings.isSmartMode(), "legacy custom offsets should remain fixed after upgrade");
                assertEquals(Arrays.asList(Integer.valueOf(2), Integer.valueOf(0)), settings.advanceDays);
            }
        });

        test("ReminderSettings smart mode ignores invalid hidden fixed input", new TestCase() {
            public void run() {
                ReminderSettings settings = ReminderSettings.fromInput(
                        true,
                        ReminderSettings.MODE_SMART,
                        "not-a-day-list",
                        "09:00"
                );
                assertTrue(settings != null, "hidden fixed input should not block smart settings");
                assertEquals(ReminderSettings.MODE_SMART, settings.mode);
                assertEquals(Arrays.asList(
                        Integer.valueOf(7),
                        Integer.valueOf(3),
                        Integer.valueOf(1),
                        Integer.valueOf(0)
                ), settings.advanceDays);
            }
        });

        test("ReminderSettings explicit fixed mode keeps configured dates", new TestCase() {
            public void run() {
                ReminderSettings settings = ReminderSettings.fromInput(
                        true,
                        ReminderSettings.MODE_FIXED,
                        "7,3,1,0",
                        "09:00"
                );
                String today = DateRules.todayString();
                FoodItem food = food(
                        "Fixed schedule",
                        "dairy",
                        "refrigerated",
                        DateRules.addDaysString(today, -5),
                        DateRules.addDaysString(today, 5),
                        1
                );
                ReminderPlan plan = ReminderPolicy.planFor(food, settings);

                assertEquals(ReminderSettings.MODE_FIXED, plan.reminderMode);
                assertEquals(Arrays.asList(
                        Integer.valueOf(7),
                        Integer.valueOf(3),
                        Integer.valueOf(1),
                        Integer.valueOf(0)
                ), plan.offsets);
                assertTrue(plan.scheduleReason.indexOf("固定日期") >= 0, "fixed plan should explain its basis");
            }
        });

        test("ReminderPolicy next reminder copy describes the future reminder node", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                ReminderSettings settings = ReminderSettings.fromInput(
                        true,
                        ReminderSettings.MODE_FIXED,
                        "3,0",
                        "09:00"
                );
                FoodItem food = food(
                        "Clear reminder copy",
                        "other",
                        "room_temp",
                        DateRules.addDaysString(today, -3),
                        DateRules.addDaysString(today, 7),
                        1
                );

                ReminderPlan plan = ReminderPolicy.planFor(food, settings);

                assertEquals("4 天后提醒：到期前 3 天", plan.nextReminderSummary);
            }
        });

        test("ReminderPolicy fixed mode does not promise missing future reminders", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                ReminderSettings settings = ReminderSettings.fromInput(
                        true,
                        ReminderSettings.MODE_FIXED,
                        "3",
                        "09:00"
                );
                FoodItem tomorrow = food(
                        "No future node",
                        "other",
                        "room_temp",
                        DateRules.addDaysString(today, -5),
                        DateRules.addDaysString(today, 1),
                        1
                );
                FoodItem dueToday = food(
                        "No due-day node",
                        "other",
                        "room_temp",
                        DateRules.addDaysString(today, -6),
                        today,
                        1
                );

                ReminderPlan tomorrowPlan = ReminderPolicy.planFor(tomorrow, settings);
                ReminderPlan todayPlan = ReminderPolicy.planFor(dueToday, settings);

                assertEquals("暂无后续提醒", tomorrowPlan.nextReminderSummary);
                assertTrue(tomorrowPlan.cardHint.indexOf("暂无后续提醒") >= 0, "card should not promise a missing reminder");
                assertEquals("暂无后续提醒", todayPlan.nextReminderSummary);
                assertTrue(todayPlan.cardHint.indexOf("暂无后续提醒") >= 0, "due-day card should stay quiet without offset zero");
            }
        });

        test("ReminderPolicy fixed mode stays on expiryDate after opening", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 20);
                FoodItem opened = food(
                        "Fixed opened food",
                        "meat_egg_seafood",
                        "refrigerated",
                        DateRules.addDaysString(today, -2),
                        expiry,
                        1
                );
                opened.openedDate = today;
                opened.afterOpenShelfLifeValue = Integer.valueOf(3);
                opened.afterOpenShelfLifeUnit = "day";
                ReminderSettings fixed = ReminderSettings.fromInput(
                        true,
                        ReminderSettings.MODE_FIXED,
                        "7,0",
                        "09:00"
                );

                ReminderPlan plan = ReminderPolicy.planFor(opened, fixed);
                assertFalse(plan.usesAfterOpenDate, "fixed dates should remain relative to expiryDate");
                assertEquals(expiry, plan.effectiveReminderDate);
                assertEquals(Arrays.asList(Integer.valueOf(7), Integer.valueOf(0)), plan.offsets);
                assertEquals(2, plan.events.size());
            }
        });

        test("ReminderPolicy fixed briefing triggers only on configured date", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                ReminderSettings fixed = ReminderSettings.fromInput(
                        true,
                        ReminderSettings.MODE_FIXED,
                        "3,0",
                        "09:00"
                );
                FoodItem oneDayBeforeNode = food(
                        "Not today",
                        "other",
                        "room_temp",
                        DateRules.addDaysString(today, -6),
                        DateRules.addDaysString(today, 4),
                        1
                );
                FoodItem onNode = food(
                        "Today node",
                        "other",
                        "room_temp",
                        DateRules.addDaysString(today, -7),
                        DateRules.addDaysString(today, 3),
                        1
                );

                List<FoodItem> foods = new ArrayList<FoodItem>();
                foods.add(oneDayBeforeNode);
                DailyBriefing before = ReminderPolicy.dailyBriefing(foods, fixed);
                assertEquals(0, before.upcoming.size());

                foods.add(onNode);
                DailyBriefing onDate = ReminderPolicy.dailyBriefing(foods, fixed);
                assertEquals(1, onDate.upcoming.size());
                assertEquals("Today node", onDate.upcoming.get(0).food.name);
            }
        });

        test("ReminderPolicy smart mode combines horizon category and storage", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 10);
                String production = DateRules.addDaysString(today, -20);

                ReminderPlan high = ReminderPolicy.planFor(food(
                        "Fresh meal", "cooked", "refrigerated", production, expiry, 1
                ), ReminderSettings.defaults());
                ReminderPlan medium = ReminderPolicy.planFor(food(
                        "Fruit", "produce", "room_temp", production, expiry, 1
                ), ReminderSettings.defaults());
                ReminderPlan low = ReminderPolicy.planFor(food(
                        "Frozen beans", "frozen", "frozen", production, expiry, 1
                ), ReminderSettings.defaults());

                assertTrue(high.offsets.size() > medium.offsets.size(), "higher-attention food should get denser dates");
                assertTrue(medium.offsets.size() > low.offsets.size(), "frozen low-attention food should get fewer dates");
                assertAllOffsetsAtMost(high.offsets, high.daysLeft);
                assertAllOffsetsAtMost(medium.offsets, medium.daysLeft);
                assertAllOffsetsAtMost(low.offsets, low.daysLeft);
                assertListContains(high.offsets, Integer.valueOf(0));
                assertListContains(medium.offsets, Integer.valueOf(0));
                assertListContains(low.offsets, Integer.valueOf(0));
            }
        });

        test("ReminderPolicy smart mode increases attention after opening", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                FoodItem opened = food(
                        "Opened sauce",
                        "condiment",
                        "cool_dry",
                        DateRules.addDaysString(today, -30),
                        DateRules.addDaysString(today, 60),
                        1
                );
                opened.openedDate = today;
                opened.afterOpenShelfLifeValue = Integer.valueOf(4);
                opened.afterOpenShelfLifeUnit = "day";
                ReminderPolicy.ensureSmartSchedule(opened);

                ReminderPlan plan = ReminderPolicy.planFor(opened, ReminderSettings.defaults());
                assertTrue(plan.usesAfterOpenDate, "opened schedule should use the earlier opened date");
                assertEquals(Arrays.asList(
                        Integer.valueOf(3),
                        Integer.valueOf(2),
                        Integer.valueOf(1),
                        Integer.valueOf(0)
                ), plan.offsets);
                assertTrue(plan.scheduleReason.indexOf("开封后期限优先") >= 0, "opened plan should explain the adjustment");
            }
        });

        test("ReminderPolicy smart mode does not create a moving today node", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                FoodItem fourDaysLeft = food(
                        "Stable high schedule",
                        "cooked",
                        "refrigerated",
                        DateRules.addDaysString(today, -2),
                        DateRules.addDaysString(today, 4),
                        1
                );
                ReminderPlan plan = ReminderPolicy.planFor(fourDaysLeft, ReminderSettings.defaults());

                assertEquals(Arrays.asList(
                        Integer.valueOf(3),
                        Integer.valueOf(2),
                        Integer.valueOf(1),
                        Integer.valueOf(0)
                ), plan.offsets);
                assertFalse(plan.hasReminderToday(), "remaining-day value must not become a rolling reminder node");
            }
        });

        test("ReminderPolicy keeps a smart schedule fixed as time passes", new TestCase() {
            public void run() {
                String planningDay = "2026-07-01";
                FoodItem item = food("Frozen schedule", "frozen", "frozen", "2026-01-01", "2026-07-31", 1);
                clearSmartScheduleForTest(item);
                assertTrue(ReminderPolicy.ensureSmartScheduleAt(item, planningDay), "first input should create a plan");
                List<Integer> plannedOffsets = new ArrayList<Integer>(item.smartReminderOffsets);
                String fingerprint = item.smartReminderFingerprint;

                assertFalse(ReminderPolicy.ensureSmartScheduleAt(item, "2026-07-24"),
                        "time passing alone must not recalculate the plan");
                assertEquals(plannedOffsets, item.smartReminderOffsets);
                assertEquals(fingerprint, item.smartReminderFingerprint);
                assertEquals(planningDay, item.smartReminderPlannedOn);
                assertEquals(30, item.smartReminderPlannedDaysLeft);
            }
        });

        test("ReminderPolicy never invents an unpersisted smart plan while rendering", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                FoodItem item = food(
                        "Unsaved plan",
                        "other",
                        "room_temp",
                        today,
                        DateRules.addDaysString(today, 10),
                        1
                );
                clearSmartScheduleForTest(item);

                ReminderPlan plan = ReminderPolicy.planFor(item, ReminderSettings.defaults());
                assertFalse(plan.enabled, "a missing snapshot must not be recalculated during rendering");
                assertEquals(0, item.smartReminderOffsets.size());
                assertEquals("", item.smartReminderPlannedOn);
                assertTrue(plan.disabledReason.indexOf("尚未保存") >= 0, "the failure should be explainable");
            }
        });

        test("ReminderPolicy recalculates only after reminder inputs change", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                FoodItem item = food(
                        "Drink",
                        "beverage",
                        "room_temp",
                        DateRules.addDaysString(today, -20),
                        DateRules.addDaysString(today, 10),
                        1
                );
                clearSmartScheduleForTest(item);
                assertTrue(ReminderPolicy.ensureSmartSchedule(item), "new food should get a smart plan");
                List<Integer> original = new ArrayList<Integer>(item.smartReminderOffsets);
                String originalFingerprint = item.smartReminderFingerprint;

                item.name = "Renamed drink";
                item.notes = "updated note";
                item.location = "fridge";
                item.remainingQuantity = 0;
                assertFalse(ReminderPolicy.ensureSmartSchedule(item),
                        "name notes location and quantity must not recalculate reminder dates");
                assertEquals(original, item.smartReminderOffsets);
                assertEquals(originalFingerprint, item.smartReminderFingerprint);

                item.storageMethod = "refrigerated";
                assertTrue(ReminderPolicy.ensureSmartSchedule(item), "storage changes should recalculate reminder dates");
                assertTrue(item.smartReminderOffsets.contains(Integer.valueOf(2)),
                        "refrigerated medium-attention food should add the 2-day node");

                item.openedDate = today;
                item.afterOpenShelfLifeValue = Integer.valueOf(4);
                item.afterOpenShelfLifeUnit = "day";
                assertTrue(ReminderPolicy.ensureSmartSchedule(item), "opening state should recalculate reminder dates");
                assertEquals(4, item.smartReminderPlannedDaysLeft);
                assertEquals(Arrays.asList(
                        Integer.valueOf(3),
                        Integer.valueOf(2),
                        Integer.valueOf(1),
                        Integer.valueOf(0)
                ), item.smartReminderOffsets);
            }
        });

        test("ReminderPolicy smart mode adjusts medium tier by storage", new TestCase() {
            public void run() {
                String today = DateRules.todayString();
                String expiry = DateRules.addDaysString(today, 10);
                String production = DateRules.addDaysString(today, -20);
                ReminderPlan chilled = ReminderPolicy.planFor(food(
                        "Chilled drink", "beverage", "refrigerated", production, expiry, 1
                ), ReminderSettings.defaults());
                ReminderPlan room = ReminderPolicy.planFor(food(
                        "Room drink", "beverage", "room_temp", production, expiry, 1
                ), ReminderSettings.defaults());

                assertEquals("B", chilled.riskLevel);
                assertEquals("B", room.riskLevel);
                assertListContains(chilled.offsets, Integer.valueOf(2));
                assertFalse(room.offsets.contains(Integer.valueOf(2)), "room-temperature medium tier should stay sparser");
                assertTrue(chilled.offsets.size() > room.offsets.size(), "refrigerated medium tier should add a reminder date");
            }
        });

        runFoodStoreMigrationTestsIfAvailable();
        runBarcodeHistoryTestsIfAvailable();
        runFoodExcelExporterTests();
        runFoodExcelImporterTests();
        runDateOcrParserTests();
        runRecognitionFrameSelectorTests();
        runDateOcrFrameVoterTests();
        runDateOcrResultPayloadTests();
        runUnifiedRecognitionStabilizerTests();
        runUnifiedRecognitionPayloadTests();

        System.out.println("Local logic tests: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            throw new AssertionError("Local logic tests failed: " + failed);
        }
    }

    private void runFoodStoreMigrationTestsIfAvailable() {
        final Class<?> migrationClass = optionalClass("com.shiqi.expirytracker.FoodStoreMigration");
        if (migrationClass == null) {
            System.out.println("SKIP FoodStoreMigration backup tests; source not compiled in this local test pass.");
            return;
        }

        test("FoodStoreMigration loads good primary before backups", new TestCase() {
            public void run() {
                Object result = loadFoodStoreWithBackups(
                        migrationClass,
                        foodStoreRaw(1, "primary", "Primary milk"),
                        Arrays.asList(foodStoreRaw(1, "backup", "Backup milk")),
                        1
                );

                assertTrue(booleanField(result, "loaded"), "good primary should load");
                assertFalse(booleanField(result, "restoredFromBackup"), "good primary should not use backup");
                assertFalse(booleanField(result, "needsPrimaryWriteBack"), "current schema primary should not write back");
                List<?> foods = foodStoreFoods(result);
                assertEquals(1, foods.size());
                assertEquals("primary", fieldText(foods.get(0), "id"));
            }
        });

        test("FoodStoreMigration recovers bad primary from newest good backup", new TestCase() {
            public void run() {
                Object result = loadFoodStoreWithBackups(
                        migrationClass,
                        "{bad json",
                        Arrays.asList("{bad backup", foodStoreRaw(1, "backup", "Backup milk")),
                        1
                );

                assertTrue(booleanField(result, "loaded"), "good backup should recover bad primary");
                assertTrue(booleanField(result, "restoredFromBackup"), "result should mark backup recovery");
                assertTrue(booleanField(result, "needsPrimaryWriteBack"), "recovered backup should write back to primary");
                List<?> foods = foodStoreFoods(result);
                assertEquals(1, foods.size());
                assertEquals("backup", fieldText(foods.get(0), "id"));
            }
        });

        test("FoodStoreMigration leaves bad primary failed when backups are bad", new TestCase() {
            public void run() {
                Object result = loadFoodStoreWithBackups(
                        migrationClass,
                        "{bad json",
                        Arrays.asList("{bad backup", "{\"schemaVersion\":1,\"foods\":\"not-array\"}"),
                        1
                );

                assertFalse(booleanField(result, "loaded"), "bad primary and bad backups should fail closed");
                assertFalse(booleanField(result, "restoredFromBackup"), "bad backups should not recover");
                assertFalse(booleanField(result, "needsPrimaryWriteBack"), "failed load should not write primary");
                assertEquals(0, foodStoreFoods(result).size());
            }
        });

        test("FoodStoreMigration does not restore backup over future primary schema", new TestCase() {
            public void run() {
                Object result = loadFoodStoreWithBackups(
                        migrationClass,
                        foodStoreRaw(2, "future", "Future milk"),
                        Arrays.asList(foodStoreRaw(1, "backup", "Backup milk")),
                        1
                );

                assertFalse(booleanField(result, "loaded"), "future schema should not be read by older app");
                assertTrue(booleanField(result, "primaryHasFutureSchema"), "future schema should be identified");
                assertFalse(booleanField(result, "restoredFromBackup"), "older backup must not replace future primary");
                assertFalse(booleanField(result, "needsPrimaryWriteBack"), "future primary must not be overwritten");
            }
        });

        test("FoodStoreMigration preserves legacy expiry when calculated inputs are incomplete", new TestCase() {
            public void run() {
                String raw = "{\"schemaVersion\":1,\"foods\":[{"
                        + "\"id\":\"legacy-calculated\",\"name\":\"Legacy milk\","
                        + "\"productionDate\":\"\",\"shelfLifeValue\":null,\"shelfLifeUnit\":\"\","
                        + "\"expiryDate\":\"2026-07-25\",\"dateSource\":\"calculated\"}]}";

                Object result = loadFoodStoreWithBackups(
                        migrationClass,
                        raw,
                        new ArrayList<String>(),
                        1
                );

                assertTrue(booleanField(result, "loaded"), "legacy calculated item should load");
                assertTrue(booleanField(result, "needsPrimaryWriteBack"), "repaired source should be written back");
                List<?> foods = foodStoreFoods(result);
                assertEquals(1, foods.size());
                assertEquals("2026-07-25", fieldText(foods.get(0), "expiryDate"));
                assertEquals("manual", fieldText(foods.get(0), "dateSource"));
            }
        });
    }

    private void runFoodExcelExporterTests() {
        test("FoodExcelExporter writes xlsx workbook and escapes fields", new TestCase() {
            public void run() {
                FoodItem item = food("QA Milk & <Fresh>", "dairy", "refrigerated", "2026-07-01", "2026-07-08", 2);
                item.id = "xlsx-1";
                item.shelfLifeValue = Integer.valueOf(7);
                item.shelfLifeUnit = "day";
                item.openedDate = "2026-07-02";
                item.afterOpenShelfLifeValue = Integer.valueOf(3);
                item.afterOpenShelfLifeUnit = "day";
                item.location = "fridge";
                item.unit = "box";
                item.notes = "line 1 & line 2";
                item.createdAt = "2026-07-05T08:30:00+0800";
                item.updatedAt = item.createdAt;

                List<FoodItem> foods = new ArrayList<FoodItem>();
                foods.add(item);

                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    FoodExcelExporter.writeWorkbook(output, foods);
                    byte[] bytes = output.toByteArray();
                    assertTrue(bytes.length > 1000, "xlsx should have meaningful content");

                    Map<String, String> entries = unzipUtf8Entries(bytes);
                    assertTrue(entries.containsKey("[Content_Types].xml"), "xlsx should include content types");
                    assertTrue(entries.containsKey("xl/workbook.xml"), "xlsx should include workbook");
                    assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"), "xlsx should include foods sheet");
                    assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"), "xlsx should include README sheet");

                    String workbook = entries.get("xl/workbook.xml");
                    assertTrue(workbook.indexOf("name=\"foods\"") >= 0, "workbook should include foods sheet");
                    assertTrue(workbook.indexOf("name=\"README\"") >= 0, "workbook should include README sheet");

                    String foodsSheet = entries.get("xl/worksheets/sheet1.xml");
                    assertTrue(foodsSheet.indexOf("<t>expiryDate</t>") >= 0, "foods sheet should include expiryDate header");
                    assertTrue(foodsSheet.indexOf("<t>2026-07-08</t>") >= 0, "foods sheet should include expiryDate value");
                    assertTrue(foodsSheet.indexOf("QA Milk &amp; &lt;Fresh&gt;") >= 0, "foods sheet should escape XML text");
                    assertTrue(foodsSheet.indexOf("<t>xlsx-1</t>") >= 0, "foods sheet should include item id");

                    String readmeSheet = entries.get("xl/worksheets/sheet2.xml");
                    assertTrue(readmeSheet.indexOf("OCR or AI results must never be auto-saved") >= 0,
                            "README sheet should preserve OCR confirmation rule");
                } catch (Exception error) {
                    throw new AssertionError("xlsx export failed: " + error.getMessage());
                }
            }
        });
    }

    private void runFoodExcelImporterTests() {
        test("FoodExcelImporter previews exported workbook without saving", new TestCase() {
            public void run() {
                FoodItem item = food("Import milk", "dairy", "refrigerated", "2026-07-01", "2026-07-08", 2);
                item.id = "import-1";
                item.shelfLifeValue = Integer.valueOf(7);
                item.shelfLifeUnit = "day";
                item.openedDate = "2026-07-02";
                item.afterOpenShelfLifeValue = Integer.valueOf(3);
                item.afterOpenShelfLifeUnit = "day";
                item.location = "fridge";
                item.unit = "box";
                item.notes = "keep cold";
                item.createdAt = "2026-07-05T08:30:00+0800";
                item.updatedAt = item.createdAt;

                FoodExcelImporter.ImportPreview preview = readWorkbookPreview(Arrays.asList(item));

                assertEquals(1, preview.totalRows);
                assertEquals(1, preview.importableRows);
                assertEquals(0, preview.errorRows);
                assertEquals(1, preview.importableFoods().size());

                FoodItem imported = preview.importableFoods().get(0);
                assertEquals("Import milk", imported.name);
                assertEquals("dairy", imported.category);
                assertEquals("refrigerated", imported.storageMethod);
                assertEquals("fridge", imported.location);
                assertEquals("2026-07-08", imported.expiryDate);
                assertEquals("manual", imported.dateSource);
                assertEquals(Integer.valueOf(7), imported.shelfLifeValue);
                assertEquals("day", imported.shelfLifeUnit);
                assertEquals("2026-07-02", imported.openedDate);
                assertEquals(Integer.valueOf(3), imported.afterOpenShelfLifeValue);
                assertEquals("day", imported.afterOpenShelfLifeUnit);
                assertEquals(Double.valueOf(2), Double.valueOf(imported.remainingQuantity));
                assertEquals("keep cold", imported.notes);
            }
        });

        test("FoodExcelImporter calculates missing expiryDate from shelf life", new TestCase() {
            public void run() {
                FoodItem item = food("Calculated import", "staple", "room_temp", "2026-01-31", "", 1);
                item.shelfLifeValue = Integer.valueOf(1);
                item.shelfLifeUnit = "month";
                item.dateSource = "unknown";

                FoodExcelImporter.ImportPreview preview = readWorkbookPreview(Arrays.asList(item));

                assertEquals(1, preview.totalRows);
                assertEquals(1, preview.importableRows);
                FoodItem imported = preview.importableFoods().get(0);
                assertEquals("2026-02-28", imported.expiryDate);
                assertEquals("calculated", imported.dateSource);
            }
        });

        test("FoodExcelImporter preserves calculated date source on export round-trip", new TestCase() {
            public void run() {
                FoodItem item = food("Calculated round-trip", "dairy", "refrigerated", "2026-07-14", "2026-07-21", 1);
                item.shelfLifeValue = Integer.valueOf(7);
                item.shelfLifeUnit = "day";
                item.dateSource = "calculated";

                FoodExcelImporter.ImportPreview preview = readWorkbookPreview(Arrays.asList(item));

                assertEquals(1, preview.importableRows);
                FoodItem imported = preview.importableFoods().get(0);
                assertEquals("2026-07-21", imported.expiryDate);
                assertEquals("calculated", imported.dateSource);
            }
        });

        test("FoodExcelImporter converts inconsistent calculated date source to manual", new TestCase() {
            public void run() {
                FoodItem item = food("Inconsistent calculated", "dairy", "refrigerated", "2026-07-14", "2026-07-25", 1);
                item.shelfLifeValue = Integer.valueOf(7);
                item.shelfLifeUnit = "day";
                item.dateSource = "calculated";

                FoodExcelImporter.ImportPreview preview = readWorkbookPreview(Arrays.asList(item));

                assertEquals(1, preview.importableRows);
                assertEquals(1, preview.warningRows);
                FoodItem imported = preview.importableFoods().get(0);
                assertEquals("2026-07-25", imported.expiryDate);
                assertEquals("manual", imported.dateSource);
            }
        });

        test("FoodExcelImporter converts incomplete calculated date source to manual", new TestCase() {
            public void run() {
                FoodItem item = food("Incomplete calculated", "dairy", "refrigerated", "", "2026-07-25", 1);
                item.dateSource = "calculated";

                FoodExcelImporter.ImportPreview preview = readWorkbookPreview(Arrays.asList(item));

                assertEquals(1, preview.importableRows);
                assertEquals(1, preview.warningRows);
                assertEquals("manual", preview.importableFoods().get(0).dateSource);
            }
        });

        test("FoodExcelImporter reads numeric Excel date cells", new TestCase() {
            public void run() {
                FoodItem item = food("Numeric dates", "dairy", "refrigerated", "2026-07-14", "2026-07-21", 1);
                item.shelfLifeValue = Integer.valueOf(7);
                item.shelfLifeUnit = "day";
                item.dateSource = "calculated";
                item.openedDate = "2026-07-15";
                item.afterOpenShelfLifeValue = Integer.valueOf(2);
                item.afterOpenShelfLifeUnit = "day";

                FoodExcelImporter.ImportPreview preview = readNumericDateWorkbookPreview(item);

                assertEquals(1, preview.importableRows);
                FoodItem imported = preview.importableFoods().get(0);
                assertEquals("2026-07-14", imported.productionDate);
                assertEquals("2026-07-15", imported.openedDate);
                assertEquals("2026-07-21", imported.expiryDate);
                assertEquals("calculated", imported.dateSource);
            }
        });

        test("FoodExcelImporter reads numeric dates from a 1904 date-system workbook", new TestCase() {
            public void run() {
                FoodItem item = food("Numeric dates 1904", "dairy", "refrigerated", "2026-07-14", "2026-07-21", 1);
                item.shelfLifeValue = Integer.valueOf(7);
                item.shelfLifeUnit = "day";
                item.dateSource = "calculated";
                item.openedDate = "2026-07-15";
                item.afterOpenShelfLifeValue = Integer.valueOf(2);
                item.afterOpenShelfLifeUnit = "day";

                FoodExcelImporter.ImportPreview preview = readNumericDateWorkbookPreview(item, true);

                assertEquals(1, preview.importableRows);
                FoodItem imported = preview.importableFoods().get(0);
                assertEquals("2026-07-14", imported.productionDate);
                assertEquals("2026-07-15", imported.openedDate);
                assertEquals("2026-07-21", imported.expiryDate);
                assertEquals("calculated", imported.dateSource);
            }
        });

        test("FoodIdGenerator reserves unique ids across one import batch", new TestCase() {
            public void run() {
                Set<String> reserved = new HashSet<String>();
                List<FoodItem> existing = new ArrayList<FoodItem>();
                FoodItem old = food("Existing", "other", "room_temp", "", "", 1);
                old.id = "food_existing";
                existing.add(old);

                for (int index = 0; index < 1000; index++) {
                    String id = FoodIdGenerator.nextId(existing, reserved);
                    assertTrue(id.startsWith("food_"), "generated id should use the food_ prefix");
                    assertFalse("food_existing".equals(id), "generated id must not reuse an existing id");
                }
                assertEquals(1000, reserved.size());
            }
        });

        test("FoodExcelImporter round-trips legacy no-expiry food without losing the row", new TestCase() {
            public void run() {
                FoodItem item = food("Legacy pantry item", "other", "room_temp", "", "", 1);
                item.dateSource = "none";

                FoodExcelImporter.ImportPreview preview = readWorkbookPreview(Arrays.asList(item));

                assertEquals(1, preview.totalRows);
                assertEquals(1, preview.importableRows);
                assertEquals(0, preview.errorRows);
                assertEquals(1, preview.warningRows);
                FoodItem imported = preview.importableFoods().get(0);
                assertEquals("none", imported.dateSource);
                assertEquals("", imported.productionDate);
                assertEquals("", imported.expiryDate);
            }
        });

        test("FoodExcelImporter reports invalid rows instead of importing them", new TestCase() {
            public void run() {
                FoodItem missingName = food("", "other", "room_temp", "2026-07-01", "2026-07-08", 1);
                FoodItem badDate = food("Bad date", "other", "room_temp", "2026-02-30", "", 1);
                badDate.shelfLifeValue = Integer.valueOf(1);
                badDate.shelfLifeUnit = "month";

                FoodExcelImporter.ImportPreview preview = readWorkbookPreview(Arrays.asList(missingName, badDate));

                assertEquals(2, preview.totalRows);
                assertEquals(0, preview.importableRows);
                assertEquals(2, preview.errorRows);
                assertEquals(0, preview.importableFoods().size());
                assertFalse(preview.rows.get(0).canImport(), "missing name row should not import");
                assertFalse(preview.rows.get(1).canImport(), "bad date row should not import");
                assertTrue(preview.rows.get(0).errors.size() > 0, "missing name should explain error");
                assertTrue(preview.rows.get(1).errors.size() > 0, "bad date should explain error");
            }
        });

        test("FoodExcelImporter rejects non-xlsx content", new TestCase() {
            public void run() {
                try {
                    FoodExcelImporter.readWorkbook(new ByteArrayInputStream("not a zip".getBytes(StandardCharsets.UTF_8)));
                    throw new AssertionError("bad xlsx should throw");
                } catch (Exception expected) {
                    assertTrue(expected.getMessage().length() > 0, "bad xlsx error should be visible");
                }
            }
        });
    }

    private void runDateOcrParserTests() {
        test("DateOcrParser extracts production date shelf life and calculated expiry", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "生产日期：2026年7月1日\n保质期 7 天\nEXP 2026-07-08"
                );

                assertTrue(result.candidateOnly, "result must stay candidate-only");
                assertEquals("2026-07-01", result.productionDates.get(0).normalized);
                assertEquals("2026-07-08", result.expiryDates.get(0).normalized);
                assertEquals(7, result.shelfLives.get(0).value);
                assertEquals("day", result.shelfLives.get(0).unit);
                assertEquals("2026-07-08", result.calculatedExpiryDates.get(0).normalized);
                assertTrue(result.productionDates.get(0).candidateOnly, "production candidate must not be saved directly");
                assertTrue(result.shelfLives.get(0).candidateOnly, "shelf life candidate must not be saved directly");
                assertTrue(result.calculatedExpiryDates.get(0).calculated, "calculated expiry should be marked");
            }
        });

        test("DateOcrParser normalizes compact and full-width date text", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "包装日期：２０２６／０７／０５ 喷码 260706 常温180天"
                );

                assertEquals("2026-07-05", result.productionDates.get(0).normalized);
                assertEquals("2026-07-06", result.productionDates.get(1).normalized);
                assertEquals(180, result.shelfLives.get(0).value);
                assertEquals("day", result.shelfLives.get(0).unit);
                assertTrue(result.calculatedExpiryDates.size() >= 2, "both production candidates should calculate expiry candidates");
                assertTrue(result.hasDateConflict(), "multiple production dates should require user confirmation");
            }
        });

        test("DateOcrParser orders two unhinted dates as production and expiry candidates", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "画面里看到 20260705 和 20260706",
                        "2026-07-15"
                );

                assertEquals(1, result.productionDates.size());
                assertEquals(1, result.expiryDates.size());
                assertEquals("2026-07-05", result.productionDates.get(0).normalized);
                assertEquals("2026-07-06", result.expiryDates.get(0).normalized);
                assertTrue(result.productionDates.get(0).weakHint, "unhinted production date should be weak");
                assertTrue(result.expiryDates.get(0).weakHint, "inferred expiry date should require confirmation");
            }
        });

        test("DateOcrParser ignores barcodes zero shelf life and invalid dates", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse("条码 6936832557442 保质期0天 日期 2026-02-30");

                assertFalse(result.hasAnyCandidate(), "barcode-like and invalid data should not create candidates");
            }
        });

        test("DateOcrParser ignores unhinted six digit screen noise but keeps hinted spray code", new TestCase() {
            public void run() {
                DateOcrParser.Result noise = DateOcrParser.parse("屏幕录制 154606 00:20 80");
                assertFalse(noise.hasAnyCandidate(), "screen timers should not become weak dates");

                DateOcrParser.Result hinted = DateOcrParser.parse("喷码 250506 保质期:540天");
                assertEquals("2025-05-06", hinted.productionDates.get(0).normalized);
                assertEquals(540, hinted.shelfLives.get(0).value);
                assertEquals("day", hinted.shelfLives.get(0).unit);
            }
        });

        test("DateOcrParser maps two-digit production date and month-only valid-until date", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "产品批号：L250321\n生产日期：25.03.13\n有效期：至 2029.02"
                );

                assertEquals(1, result.productionDates.size());
                assertEquals("2025-03-13", result.productionDates.get(0).normalized);
                assertEquals(1, result.expiryDates.size());
                assertEquals("2029-02-28", result.expiryDates.get(0).normalized);
                assertTrue(DateOcrParser.isMonthOnlyExpiryRaw(result.expiryDates.get(0).raw),
                        "month-only expiry should stay identifiable for confirmation copy");
                assertEquals(0, result.shelfLives.size());
                assertEquals(0, result.calculatedExpiryDates.size());
            }
        });

        test("DateOcrParser reads trailing four-digit years as day month year", new TestCase() {
            public void run() {
                DateOcrParser.Result slash = DateOcrParser.parse("生产日期 26/04/2022");
                assertEquals("2022-04-26", slash.productionDates.get(0).normalized);

                DateOcrParser.Result dot = DateOcrParser.parse("有效期至 25.06.2021");
                assertEquals("2021-06-25", dot.expiryDates.get(0).normalized);

                DateOcrParser.Result dash = DateOcrParser.parse("生产日期 14-12-2021");
                assertEquals("2021-12-14", dash.productionDates.get(0).normalized);

                DateOcrParser.Result secondDot = DateOcrParser.parse("有效期至 21.07.2021");
                assertEquals("2021-07-21", secondDot.expiryDates.get(0).normalized);

                DateOcrParser.Result compact = DateOcrParser.parse("生产日期 24092021");
                assertEquals("2021-09-24", compact.productionDates.get(0).normalized);

                DateOcrParser.Result monthName = DateOcrParser.parse("BEST IF USED BY JUN 28 2021");
                assertEquals("2021-06-28", monthName.expiryDates.get(0).normalized);

                DateOcrParser.Result dayMonthName = DateOcrParser.parse("EXP 28 September 2027");
                assertEquals("2027-09-28", dayMonthName.expiryDates.get(0).normalized);
            }
        });

        test("DateOcrParser preserves year-first and month-only formats", new TestCase() {
            public void run() {
                DateOcrParser.Result shortYear = DateOcrParser.parse("生产日期 25.03.13");
                assertEquals("2025-03-13", shortYear.productionDates.get(0).normalized);

                DateOcrParser.Result dayFirstShortYear = DateOcrParser.parse("生产日期 09/06/20");
                assertEquals("2020-06-09", dayFirstShortYear.productionDates.get(0).normalized);

                DateOcrParser.Result fullYear = DateOcrParser.parse("生产日期 2022-04-26");
                assertEquals("2022-04-26", fullYear.productionDates.get(0).normalized);

                DateOcrParser.Result monthFirst = DateOcrParser.parse("有效期至 02/2028");
                assertEquals("2028-02-29", monthFirst.expiryDates.get(0).normalized);

                DateOcrParser.Result yearFirst = DateOcrParser.parse("有效期至 2028.04");
                assertEquals("2028-04-30", yearFirst.expiryDates.get(0).normalized);
            }
        });

        test("DateOcrParser validates day month year and rejects ambiguous short pairs", new TestCase() {
            public void run() {
                DateOcrParser.Result leapYear = DateOcrParser.parse("生产日期 29/02/2024");
                assertEquals("2024-02-29", leapYear.productionDates.get(0).normalized);

                DateOcrParser.Result nonLeapYear = DateOcrParser.parse("生产日期 29/02/2023");
                assertFalse(nonLeapYear.hasAnyCandidate(), "non-leap February 29 must be rejected");

                DateOcrParser.Result invalidMonthDay = DateOcrParser.parse("生产日期 31/04/2022");
                assertFalse(invalidMonthDay.hasAnyCandidate(), "April 31 must be rejected");

                DateOcrParser.Result ambiguousHinted = DateOcrParser.parse("有效期至 10.01");
                assertFalse(ambiguousHinted.hasAnyCandidate(),
                        "two short segments must not be expanded to a month-end date");

                DateOcrParser.Result ambiguousBare = DateOcrParser.parse("包装上只有 10-01");
                assertFalse(ambiguousBare.hasAnyCandidate(),
                        "an unhinted two-part short date must remain unselected");
            }
        });

        test("DateOcrParser recovers chronological pair when OCR drops the production label", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "产品批号:\n有效期:至\nL25032 1\n25.03. 13\n2029. 02"
                );

                assertEquals(1, result.productionDates.size());
                assertEquals("2025-03-13", result.productionDates.get(0).normalized);
                assertEquals(1, result.expiryDates.size());
                assertEquals("2029-02-28", result.expiryDates.get(0).normalized);
                assertTrue(result.productionDates.get(0).weakHint,
                        "chronology-recovered production date must remain a confirmation candidate");
            }
        });

        test("DateOcrFrameVoter accepts repeated OCR evidence from the valid-until video frame", new TestCase() {
            public void run() {
                String raw = "产品批号:\n有效期: 至\nL25032 1\n25.03. 13\n2029. 02\n"
                        + "产品批号:\n有效期:至\nL250321\n25.03. 13\n2029. 02\n"
                        + "L250321\n25.03. 13\n2029. 02";
                DateOcrParser.Result parsed = DateOcrParser.parseFocusedWithDateOnlySupplement("", raw);
                DateOcrFrameVoter.VoteResult vote = DateOcrFrameVoter.vote(
                        java.util.Arrays.asList(parsed, parsed)
                );

                assertEquals(1, parsed.productionDates.size());
                assertEquals(1, parsed.expiryDates.size());
                assertEquals(0, parsed.shelfLives.size());
                assertEquals("2025-03-13", parsed.productionDates.get(0).normalized);
                assertEquals("2029-02-28", parsed.expiryDates.get(0).normalized);
                assertTrue(vote.readyForUserConfirmation(),
                        "independent OCR repetitions in one video frame should stabilize the date pair");
                assertEquals("2029-02-28", vote.expiryDate.value);
            }
        });

        test("DateOcrParser supports direct valid-until aliases and compact expiry month", new TestCase() {
            public void run() {
                DateOcrParser.Result english = DateOcrParser.parse("MFG 250313 EXP 202902");
                assertEquals(1, english.productionDates.size());
                assertEquals(1, english.expiryDates.size());
                assertEquals("2025-03-13", english.productionDates.get(0).normalized);
                assertEquals("2029-02-28", english.expiryDates.get(0).normalized);

                DateOcrParser.Result leapYear = DateOcrParser.parse("有效期限至 2028年2月");
                assertEquals(1, leapYear.expiryDates.size());
                assertEquals("2028-02-29", leapYear.expiryDates.get(0).normalized);

                DateOcrParser.Result fullDate = DateOcrParser.parse("限用日期 2027/11/06");
                assertEquals(1, fullDate.expiryDates.size());
                assertEquals("2027-11-06", fullDate.expiryDates.get(0).normalized);
            }
        });

        test("DateOcrParser assigns adjacent production and expiry hints to their nearest dates", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "生产日期 2025-03-13\n有效期至 2029-02-28"
                );

                assertEquals(1, result.productionDates.size());
                assertEquals("2025-03-13", result.productionDates.get(0).normalized);
                assertEquals(1, result.expiryDates.size());
                assertEquals("2029-02-28", result.expiryDates.get(0).normalized);

                DateOcrParser.Result unhintedMonth = DateOcrParser.parse("包装上看到 2029.02");
                assertEquals("2029-02-28", unhintedMonth.expiryDates.get(0).normalized);
            }
        });

        test("DateOcrParser keeps two explicitly labeled expiry dates as expiry candidates", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "有效期至 2027-01-01\n有效期至 2028-01-01"
                );

                assertEquals(0, result.productionDates.size());
                assertEquals(2, result.expiryDates.size());
                assertEquals("2027-01-01", result.expiryDates.get(0).normalized);
                assertEquals("2028-01-01", result.expiryDates.get(1).normalized);
            }
        });

        test("DateOcrParser keeps duplicated explicit expiry OCR evidence out of production", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "有效期至 2027-01-01\n有效期至 2028-01-01\n有效期至 2028/01/01"
                );

                assertEquals(0, result.productionDates.size());
                assertEquals(2, result.expiryDates.size());
                assertEquals("2027-01-01", result.expiryDates.get(0).normalized);
                assertEquals("2028-01-01", result.expiryDates.get(1).normalized);
            }
        });

        test("DateOcrParser applies current-date fallback to one unhinted date", new TestCase() {
            public void run() {
                DateOcrParser.Result past = DateOcrParser.parse("D250912", "2026-07-15");
                assertEquals("2025-09-12", past.productionDates.get(0).normalized);
                assertEquals(0, past.expiryDates.size());

                DateOcrParser.Result future = DateOcrParser.parse("20270911", "2026-07-15");
                assertEquals(0, future.productionDates.size());
                assertEquals("2027-09-11", future.expiryDates.get(0).normalized);

                DateOcrParser.Result embossedPair = DateOcrParser.parse(
                        "D250912\n20270911",
                        "2026-07-15"
                );
                assertEquals("2025-09-12", embossedPair.productionDates.get(0).normalized);
                assertEquals("2027-09-11", embossedPair.expiryDates.get(0).normalized);
            }
        });

        test("DateOcrParser covers common market aliases month-year and week shelf life", new TestCase() {
            public void run() {
                DateOcrParser.Result chinese = DateOcrParser.parse(
                        "分装日期 2025年3月13日 最佳食用期至 2027年3月12日"
                );
                assertEquals("2025-03-13", chinese.productionDates.get(0).normalized);
                assertEquals("2027-03-12", chinese.expiryDates.get(0).normalized);

                DateOcrParser.Result imported = DateOcrParser.parse("MFD 250313 USE BY 03/2029");
                assertEquals("2025-03-13", imported.productionDates.get(0).normalized);
                assertEquals("2029-03-31", imported.expiryDates.get(0).normalized);

                DateOcrParser.Result weeks = DateOcrParser.parse("生产日期 2025-03-13 保质期 6周");
                assertEquals(42, weeks.shelfLives.get(0).value);
                assertEquals("day", weeks.shelfLives.get(0).unit);
            }
        });

        test("DateOcrResultPayload keeps direct month-only expiry in manual confirmation mode", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("生产日期 25.03.13 有效期：至 2029.02"));
                frames.add(DateOcrParser.parse("生 产 日 期 25.03.13 有 效 期 : 至 2029.02"));

                DateOcrFrameVoter.VoteResult vote = DateOcrFrameVoter.vote(frames);
                FoodItem draft = DateOcrResultPayload.toDraft(vote);

                assertTrue(vote.readyForUserConfirmation(), "direct expiry should become a stable candidate");
                assertEquals("2025-03-13", draft.productionDate);
                assertEquals("2029-02-28", draft.expiryDate);
                assertEquals("manual", draft.dateSource);
                assertEquals(null, draft.shelfLifeValue);
            }
        });

        test("DateOcrParser extracts date prefix from laser production batch code", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "保 质 期 9个 月\n生产日期见喷码\n2026012057420"
                );

                assertEquals("2026-01-20", result.productionDates.get(0).normalized);
                assertEquals(9, result.shelfLives.get(0).value);
                assertEquals("month", result.shelfLives.get(0).unit);
                assertEquals("2026-10-20", result.calculatedExpiryDates.get(0).normalized);

                DateOcrParser.Result barcodeOnly = DateOcrParser.parse("条码 2026012057420");
                assertFalse(barcodeOnly.hasAnyCandidate(), "long numeric code needs production context");

                DateOcrParser.Result repeatedVariants = DateOcrParser.parse(
                        "20260120S7420\n2026012057420\n20260120S7420"
                );
                assertEquals(3, repeatedVariants.productionDateEvidenceCount("2026-01-20"));
            }
        });

        test("DateOcrParser recovers shelf life when Chinese hint is damaged by OCR", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "W劇9个月\n产1年月UY MD\n20260120S7420"
                );

                assertEquals(9, result.shelfLives.get(0).value);
                assertEquals("month", result.shelfLives.get(0).unit);
                assertEquals("2026-01-20", result.productionDates.get(0).normalized);
                assertEquals("2026-10-20", result.calculatedExpiryDates.get(0).normalized);

                DateOcrParser.Result missingMonthGlyph = DateOcrParser.parse(
                        "保廣明9个\n生产日期年月日YMD\n2026012057420"
                );
                assertEquals(9, missingMonthGlyph.shelfLives.get(0).value);
                assertEquals("month", missingMonthGlyph.shelfLives.get(0).unit);
                assertEquals("2026-10-20", missingMonthGlyph.calculatedExpiryDates.get(0).normalized);

                DateOcrParser.Result monthReadAsOne = DateOcrParser.parse("保唐呀9个1");
                assertEquals(9, monthReadAsOne.shelfLives.get(0).value);
                assertEquals("month", monthReadAsOne.shelfLives.get(0).unit);

                DateOcrParser.Result monthReadAsDay = DateOcrParser.parse("保期9个日");
                assertEquals(9, monthReadAsDay.shelfLives.get(0).value);
                assertEquals("month", monthReadAsDay.shelfLives.get(0).unit);

                DateOcrParser.Result supplemented = DateOcrParser.parseFocusedWithDateOnlySupplement(
                        "保廣明9个",
                        "2026012057420"
                );
                assertEquals("2026-01-20", supplemented.productionDates.get(0).normalized);
                assertEquals("2026-10-20", supplemented.calculatedExpiryDates.get(0).normalized);

                DateOcrParser.Result datePairOnly = DateOcrParser.parseFocusedWithDateOnlySupplement(
                        "",
                        "20260612 20270611 204日"
                );
                assertEquals("2026-06-12", datePairOnly.productionDates.get(0).normalized);
                assertEquals("2027-06-11", datePairOnly.expiryDates.get(0).normalized);
                assertEquals(0, datePairOnly.shelfLives.size());

                DateOcrParser.Result clippedDatePair = DateOcrParser.parse(
                        "0260612/20270611"
                );
                assertEquals("2026-06-12", clippedDatePair.productionDates.get(0).normalized);
                assertEquals("2027-06-11", clippedDatePair.expiryDates.get(0).normalized);
                List<DateOcrParser.Result> clippedFrames = new ArrayList<DateOcrParser.Result>();
                clippedFrames.add(clippedDatePair);
                DateOcrFrameVoter.VoteResult clippedVote = DateOcrFrameVoter.vote(clippedFrames, 3);
                assertEquals("2026-06-12", clippedVote.productionDate.value);
                assertEquals("2027-06-11", clippedVote.expiryDate.value);

                DateOcrParser.Result age = DateOcrParser.parse("适用年龄 9个月以上");
                assertFalse(age.hasAnyCandidate(), "baby age must not become shelf life");
            }
        });
    }

    private void runRecognitionFrameSelectorTests() {
        test("RecognitionFrameSelector normalizes quality score boundaries", new TestCase() {
            public void run() {
                double ideal = RecognitionFrameSelector.qualityScore(
                        1.0d, 0.5d, 0.0d, 1.0d, 1.0d, 0.5d
                );
                double worst = RecognitionFrameSelector.qualityScore(
                        0.0d, 0.0d, 1.0d, 0.0d, 0.0d, 0.0d
                );
                double clampedIdeal = RecognitionFrameSelector.qualityScore(
                        Double.POSITIVE_INFINITY,
                        0.5d,
                        Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY,
                        0.5d
                );
                double invalid = RecognitionFrameSelector.qualityScore(
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                );

                assertNear(1.0d, ideal);
                assertNear(0.0d, worst);
                assertNear(1.0d, clampedIdeal);
                assertNear(0.0d, invalid);
            }
        });

        test("RecognitionFrameSelector keeps the best three frames in score order", new TestCase() {
            public void run() {
                RecognitionFrameSelector selector = new RecognitionFrameSelector(500L);
                assertFalse(selector.offer(null), "null candidates must not change selection");
                assertTrue(selector.offer(recognitionFrame("low", 100L, "a", 0.10d)), "first frame should be kept");
                assertTrue(selector.offer(recognitionFrame("medium", 101L, "b", 0.40d)), "different signature should be kept");
                assertTrue(selector.offer(recognitionFrame("high", 102L, "c", 0.70d)), "third frame should be kept");
                assertFalse(selector.offer(recognitionFrame("weakest", 103L, "d", 0.05d)),
                        "a weaker fourth frame must not displace a retained frame");
                assertTrue(selector.offer(recognitionFrame("strongest", 104L, "e", 0.90d)),
                        "a stronger fourth frame should replace the weakest retained frame");

                List<RecognitionFrameSelector.FrameCandidate> selected = selector.selectedFrames();
                assertEquals(RecognitionFrameSelector.MAX_KEYFRAMES, selected.size());
                assertEquals("strongest", selected.get(0).frameId);
                assertEquals("high", selected.get(1).frameId);
                assertEquals("medium", selected.get(2).frameId);
                for (int index = 1; index < selected.size(); index++) {
                    assertTrue(selected.get(index - 1).qualityScore >= selected.get(index).qualityScore,
                            "selected frames must be sorted by descending quality");
                }

                selector.clear();
                assertEquals(0, selector.size());
            }
        });

        test("RecognitionFrameSelector replaces near duplicates and preserves diversity", new TestCase() {
            public void run() {
                RecognitionFrameSelector selector = new RecognitionFrameSelector(500L);
                assertTrue(selector.offer(recognitionFrame("same-low", 1000L, "same", 0.20d)),
                        "initial content frame should be kept");
                assertFalse(selector.offer(recognitionFrame("same-weaker", 1499L, "same", 0.10d)),
                        "same content inside the time gap should not replace a better frame");
                assertTrue(selector.offer(recognitionFrame("same-high", 1499L, "same", 0.90d)),
                        "same content inside the time gap should keep the higher score");
                assertEquals(1, selector.size());
                assertEquals("same-high", selector.selectedFrames().get(0).frameId);

                assertTrue(selector.offer(recognitionFrame("different", 1500L, "other", 0.30d)),
                        "a different signature should be eligible even inside the time gap");
                assertTrue(selector.offer(recognitionFrame("same-at-boundary", 1999L, "same", 0.50d)),
                        "the exact time-gap boundary should count as a distinct frame");

                List<RecognitionFrameSelector.FrameCandidate> selected = selector.selectedFrames();
                assertEquals(3, selected.size());
                assertEquals("same-high", selected.get(0).frameId);
                assertEquals("same-at-boundary", selected.get(1).frameId);
                assertEquals("different", selected.get(2).frameId);
            }
        });

        test("RecognitionFrameSelector preserves product and both date evidence types", new TestCase() {
            public void run() {
                RecognitionFrameSelector selector = new RecognitionFrameSelector(500L);
                assertTrue(selector.offer(recognitionFrameWithEvidence(
                        "production-high", 1000L, "production-a", 0.90d,
                        RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE
                )), "first production frame should be kept");
                assertTrue(selector.offer(recognitionFrameWithEvidence(
                        "production-medium", 2000L, "production-b", 0.70d,
                        RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE
                )), "second production frame is temporarily eligible");
                assertTrue(selector.offer(recognitionFrameWithEvidence(
                        "expiry", 3000L, "expiry", 0.65d,
                        RecognitionFrameSelector.EVIDENCE_EXPIRY_DATE
                )), "expiry evidence should be kept");
                assertTrue(selector.offer(recognitionFrameWithEvidence(
                        "product", 4000L, "product", 0.35d,
                        RecognitionFrameSelector.EVIDENCE_PRODUCT_NAME
                )), "product evidence must replace redundant production evidence");

                int covered = RecognitionFrameSelector.EVIDENCE_NONE;
                for (RecognitionFrameSelector.FrameCandidate frame : selector.selectedFrames()) {
                    covered |= frame.primaryEvidence;
                }
                assertTrue((covered & RecognitionFrameSelector.EVIDENCE_PRODUCT_NAME) != 0,
                        "selected frames must include product evidence");
                assertTrue((covered & RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE) != 0,
                        "selected frames must include production evidence");
                assertTrue((covered & RecognitionFrameSelector.EVIDENCE_EXPIRY_DATE) != 0,
                        "selected frames must include expiry evidence");
                assertFalse(containsFrame(selector.selectedFrames(), "production-medium"),
                        "the redundant lower-quality production frame should be removed");
            }
        });

        test("RecognitionFrameSelector treats missing signatures conservatively", new TestCase() {
            public void run() {
                RecognitionFrameSelector selector = new RecognitionFrameSelector(500L);
                RecognitionFrameSelector.FrameCandidate first = recognitionFrame(
                        "blank-low", -1L, "", 0.20d
                );
                assertEquals(Long.valueOf(0L), Long.valueOf(first.timestampMillis));
                assertTrue(selector.offer(first), "first unsigned frame should be kept");
                assertTrue(selector.offer(recognitionFrame("blank-high", 499L, null, 0.80d)),
                        "higher quality should replace an unsigned near duplicate");
                assertTrue(selector.offer(recognitionFrame("blank-boundary", 999L, "", 0.40d)),
                        "unsigned frame at the exact gap should remain eligible");
                assertEquals(2, selector.size());
                assertEquals("blank-high", selector.selectedFrames().get(0).frameId);
            }
        });

        test("RecognitionFrameSelector exposes field fusion evidence separately", new TestCase() {
            public void run() {
                RecognitionFrameSelector.FieldFusionConfidence full =
                        RecognitionFrameSelector.fuseFieldConfidence(1.0d, 3, 3, 1.0d);
                assertNear(1.0d, full.qualityEvidence);
                assertNear(1.0d, full.voteAgreement);
                assertNear(1.0d, full.multiFrameSupport);
                assertNear(1.0d, full.voteEvidence);
                assertNear(1.0d, full.ocrEvidence);
                assertNear(1.0d, full.combinedScore);
                assertTrue(full.hasMultiFrameSupport(), "three agreeing frames should expose multi-frame support");

                RecognitionFrameSelector.FieldFusionConfidence single =
                        RecognitionFrameSelector.fuseFieldConfidence(1.0d, 1, 1, 1.0d);
                assertNear(1.0d, single.voteAgreement);
                assertNear(1.0d / 3.0d, single.multiFrameSupport);
                assertNear(1.0d / 3.0d, single.voteEvidence);
                assertTrue(single.combinedScore < 1.0d,
                        "one frame must not look like full multi-frame confidence");
                assertFalse(single.hasMultiFrameSupport(), "one frame is not multi-frame support");

                RecognitionFrameSelector.FieldFusionConfidence partial =
                        RecognitionFrameSelector.fuseFieldConfidence(0.80d, 2, 3, 0.60d);
                assertEquals(2, partial.agreeingFrames);
                assertEquals(3, partial.observedFrames);
                assertNear(2.0d / 3.0d, partial.voteAgreement);
                assertNear(2.0d / 3.0d, partial.multiFrameSupport);
                assertNear(4.0d / 9.0d, partial.voteEvidence);
                assertTrue(partial.hasMultiFrameSupport(), "two agreeing frames should expose cross-frame support");

                RecognitionFrameSelector.FieldFusionConfidence clamped =
                        RecognitionFrameSelector.fuseFieldConfidence(
                                Double.POSITIVE_INFINITY,
                                5,
                                3,
                                Double.NEGATIVE_INFINITY
                        );
                assertEquals(3, clamped.agreeingFrames);
                assertEquals(3, clamped.observedFrames);
                assertNear(1.0d, clamped.qualityEvidence);
                assertNear(0.0d, clamped.ocrEvidence);
                assertNear(0.75d, clamped.combinedScore);

                RecognitionFrameSelector.FieldFusionConfidence empty =
                        RecognitionFrameSelector.fuseFieldConfidence(
                                Double.NaN,
                                -1,
                                -1,
                                Double.NaN
                        );
                assertEquals(0, empty.agreeingFrames);
                assertEquals(0, empty.observedFrames);
                assertNear(0.0d, empty.combinedScore);
            }
        });

        test("DateOcrParser reads the July 15 bottle-cap production and valid-until pair", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "20260608 0849033\n保质期至 20270307"
                );
                assertEquals("2026-06-08", result.productionDates.get(0).normalized);
                assertEquals("2027-03-07", result.expiryDates.get(0).normalized);
            }
        });
    }

    private void runDateOcrFrameVoterTests() {
        test("DateOcrFrameVoter promotes repeated candidates to confirmation", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("生产日期：2026年7月1日 保质期 7 天"));
                frames.add(DateOcrParser.parse("生产日期 2026/07/01 保质期7天"));
                frames.add(DateOcrParser.parse("生产日期 2026-07-01 其他说明"));

                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(frames);

                assertTrue(result.candidateOnly, "vote result must stay candidate-only");
                assertTrue(result.readyForUserConfirmation(), "repeated candidates should be ready for confirmation");
                assertEquals(3, result.frameCount);
                assertEquals(3, result.framesWithCandidates);
                assertEquals("2026-07-01", result.productionDate.value);
                assertTrue(result.productionDate.votes >= 2, "production date should have repeated votes");
                assertEquals(7, result.shelfLife.value);
                assertEquals("day", result.shelfLife.unit);
                assertEquals("2026-07-08", result.calculatedExpiryDate.value);
                assertTrue(result.calculatedExpiryDate.candidateOnly, "calculated expiry must not be saved directly");
            }
        });

        test("DateOcrFrameVoter waits for repeated shelf-life evidence in temporal mode", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("20260120S7420 W劇9个月"));
                frames.add(DateOcrParser.parse("20260120S7420"));
                frames.add(DateOcrParser.parse("20260120S7420"));

                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(frames, 3);
                assertEquals("2026-01-20", result.productionDate.value);
                assertEquals(null, result.shelfLife);
                assertEquals(null, result.calculatedExpiryDate);

                frames.set(1, frames.get(0));
                frames.set(2, frames.get(0));
                result = DateOcrFrameVoter.vote(frames, 3);
                assertEquals(9, result.shelfLife.value);
                assertEquals("month", result.shelfLife.unit);
                assertEquals("2026-10-20", result.calculatedExpiryDate.value);
                assertTrue(result.calculatedExpiryDate.calculated, "expiry should be derived from confirmed inputs");

                List<DateOcrParser.Result> noisy = new ArrayList<DateOcrParser.Result>();
                noisy.add(DateOcrParser.parse("8日"));
                noisy.add(DateOcrParser.parse("8日"));
                noisy.add(DateOcrParser.parse("8日"));
                noisy.add(DateOcrParser.parse("保期9个日"));
                noisy.add(DateOcrParser.parse("保期9个日"));
                DateOcrFrameVoter.VoteResult corrected = DateOcrFrameVoter.vote(noisy, 3);
                assertEquals(9, corrected.shelfLife.value);
                assertEquals("month", corrected.shelfLife.unit);
            }
        });

        test("DateOcrFrameVoter keeps single-frame candidates below confirmation threshold", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("生产日期：2026年7月1日 保质期 7 天"));

                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(frames);

                assertFalse(result.readyForUserConfirmation(), "single frame should not be considered stable");
                assertEquals(null, result.productionDate);
                assertEquals(null, result.shelfLife);
                assertEquals(null, result.calculatedExpiryDate);
            }
        });

        test("DateOcrFrameVoter accepts a repeated inferred date pair", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("D250912 20270911", "2026-07-15"));
                frames.add(DateOcrParser.parse("D250912 20270911", "2026-07-15"));

                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(frames);

                assertFalse(result.hasConflict, "chronological pair should not be treated as a conflict");
                assertTrue(result.readyForUserConfirmation(), "repeated inferred pair should be confirmable");
                assertEquals("2025-09-12", result.productionDate.value);
                assertEquals("2027-09-11", result.expiryDate.value);
            }
        });
    }

    private void runDateOcrResultPayloadTests() {
        test("DateOcrResultPayload builds calculated draft without saving raw OCR text", new TestCase() {
            public void run() {
                FoodItem draft = DateOcrResultPayload.toDraft(
                        "2026-07-01",
                        "2026-07-08",
                        true,
                        Integer.valueOf(7),
                        "day"
                );

                assertEquals("2026-07-01", draft.productionDate);
                assertEquals(Integer.valueOf(7), draft.shelfLifeValue);
                assertEquals("day", draft.shelfLifeUnit);
                assertEquals("2026-07-08", draft.expiryDate);
                assertEquals("calculated", draft.dateSource);
                assertEquals("", draft.notes);
                assertTrue(DateOcrResultPayload.hasUsableDraft(draft), "draft should be usable for confirmation form");
            }
        });

        test("DateOcrResultPayload preserves explicit expiry as manual draft candidate", new TestCase() {
            public void run() {
                FoodItem draft = DateOcrResultPayload.toDraft(
                        "",
                        "2026-12-31",
                        false,
                        null,
                        ""
                );

                assertEquals("", draft.productionDate);
                assertEquals(null, draft.shelfLifeValue);
                assertEquals("", draft.shelfLifeUnit);
                assertEquals("2026-12-31", draft.expiryDate);
                assertEquals("manual", draft.dateSource);
                assertEquals("", draft.notes);
                assertTrue(DateOcrResultPayload.hasUsableDraft(draft), "manual expiry draft should be usable");
            }
        });

        test("DateOcrResultPayload ignores invalid dates and shelf life units", new TestCase() {
            public void run() {
                FoodItem draft = DateOcrResultPayload.toDraft(
                        "2026-02-30",
                        "bad",
                        true,
                        Integer.valueOf(7),
                        "week"
                );

                assertEquals("", draft.productionDate);
                assertEquals("", draft.expiryDate);
                assertEquals(Integer.valueOf(7), draft.shelfLifeValue);
                assertEquals("", draft.shelfLifeUnit);
                assertEquals("unknown", draft.dateSource);
                assertTrue(DateOcrResultPayload.hasUsableDraft(draft), "shelf life alone still needs user confirmation");
            }
        });

        test("Date OCR never preselects a production date later than today", new TestCase() {
            public void run() {
                FoodItem draft = DateOcrResultPayload.toDraft(
                        "2999-01-01",
                        "2999-12-31",
                        false,
                        null,
                        ""
                );
                assertEquals("", draft.productionDate);
                assertEquals("2999-12-31", draft.expiryDate);

                DateOcrFrameVoter.VoteResult vote = DateOcrFrameVoter.vote(Arrays.asList(
                        DateOcrParser.parse("生产日期 2999-01-01"),
                        DateOcrParser.parse("生产日期：2999/01/01")
                ));
                assertEquals(null, vote.productionDate);
            }
        });

        test("Date OCR never keeps an expiry calculated from a future production date", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("生产日期 2999-01-01 保质期 7 天"));
                frames.add(DateOcrParser.parse("生产日期 2999/01/01 保质期 7 天"));
                DateOcrFrameVoter.VoteResult vote = DateOcrFrameVoter.vote(frames, 2);
                FoodItem draft = DateOcrResultPayload.toDraft(vote);

                assertEquals(null, vote.productionDate);
                assertEquals(null, vote.calculatedExpiryDate);
                assertEquals("", draft.productionDate);
                assertEquals("", draft.expiryDate);

                FoodItem defensive = DateOcrResultPayload.toDraft(
                        "2999-01-01", "2999-01-08", true,
                        Integer.valueOf(7), "day"
                );
                assertEquals("", defensive.productionDate);
                assertEquals("", defensive.expiryDate);
            }
        });

        test("Date evidence policy preserves every explicit original expiry candidate", new TestCase() {
            public void run() {
                DateOcrParser.Result merged = DateOcrParser.parse(
                        "有效期至 2027-03-07\n有效期至 2027-04-08\n有效期至 2027-03-02"
                );
                DateOcrParser.Result filtered = DateEvidencePolicy.apply(
                        merged,
                        "有效期至 2027-03-07\n有效期至 2027-04-08"
                );

                assertEquals(2, filtered.expiryDates.size());
                assertEquals("2027-03-07", filtered.expiryDates.get(0).normalized);
                assertEquals("2027-04-08", filtered.expiryDates.get(1).normalized);
            }
        });

        test("Video date evidence keeps competing expiry readings for temporal voting", new TestCase() {
            public void run() {
                List<DateOcrParser.DateCandidate> production = Arrays.asList(
                        new DateOcrParser.DateCandidate(
                                "productionDate", "20260612", "2026-06-12",
                                "20260612 20270611", 0.76d, false, false
                        )
                );
                List<DateOcrParser.DateCandidate> expiry = Arrays.asList(
                        new DateOcrParser.DateCandidate(
                                "expiryDate", "20270611", "2027-06-11",
                                "20260612 20270611", 0.76d, false, false
                        ),
                        new DateOcrParser.DateCandidate(
                                "expiryDate", "20270511", "2027-05-11",
                                "有效期至 20270511", 0.88d, false, false
                        )
                );
                DateOcrParser.Result merged = new DateOcrParser.Result(
                        "", "", production, expiry,
                        new ArrayList<DateOcrParser.ShelfLifeCandidate>(),
                        new ArrayList<DateOcrParser.DateCandidate>()
                );

                DateOcrParser.Result video = DateEvidencePolicy.apply(
                        merged,
                        "有效期至 20270511",
                        false
                );
                assertEquals(2, video.expiryDates.size());
                assertEquals("2027-06-11", video.expiryDates.get(0).normalized);
                assertEquals("2027-05-11", video.expiryDates.get(1).normalized);

                DateOcrParser.Result still = DateEvidencePolicy.apply(
                        merged,
                        "有效期至 20270511",
                        true
                );
                assertEquals(1, still.expiryDates.size());
                assertEquals("2027-05-11", still.expiryDates.get(0).normalized);

                List<DateOcrParser.DateCandidate> reversedExpiry = Arrays.asList(
                        expiry.get(1),
                        expiry.get(0)
                );
                DateOcrParser.Result paired = new DateOcrParser.Result(
                        "20260612/20270611\n20260612/20270611\n有效期至 20270511",
                        "20260612/20270611\n20260612/20270611\n有效期至 20270511",
                        production,
                        reversedExpiry,
                        new ArrayList<DateOcrParser.ShelfLifeCandidate>(),
                        new ArrayList<DateOcrParser.DateCandidate>()
                );
                DateOcrFrameVoter.VoteResult pairedVote = DateOcrFrameVoter.vote(
                        java.util.Collections.singletonList(paired),
                        1
                );
                assertEquals("2027-06-11", pairedVote.expiryDate.value);
                assertFalse(pairedVote.hasConflict,
                        "the repeated explicit pair must beat a single competing OCR reading");
            }
        });

        test("Video completion prefers the latest clear laser pair over an earlier wrong tie", new TestCase() {
            public void run() {
                DateOcrParser.Result earlierWrong = DateOcrParser.parse(
                        "20260612/20270511"
                );
                String latest = "20260612/2027061\n20260612/20270611\n"
                        + "2026061212027\n0260612/20270611";

                DateOcrParser.Result selected = DateEvidencePolicy.chooseVideoCompletionEvidence(
                        latest,
                        earlierWrong
                );
                DateOcrFrameVoter.VoteResult vote = DateOcrFrameVoter.vote(
                        java.util.Collections.singletonList(selected),
                        1
                );
                assertEquals("2026-06-12", vote.productionDate.value);
                assertEquals("2027-06-11", vote.expiryDate.value);

                DateOcrParser.Result shelfOnly = DateEvidencePolicy.chooseVideoCompletionEvidence(
                        "生产日期 2026-01-20 保质期 9个月",
                        null
                );
                assertEquals("2026-01-20", shelfOnly.productionDates.get(0).normalized);
                assertEquals(9, shelfOnly.shelfLives.get(0).value);
            }
        });

        test("Date evidence policy removes weak month fragments duplicated from a full date", new TestCase() {
            public void run() {
                DateOcrParser.Result noisy = DateOcrParser.parse(
                        "2023.12.06\n2023.12",
                        "2026-07-15"
                );
                DateOcrParser.Result filtered = DateEvidencePolicy.apply(noisy, "");
                assertEquals("2023-12-06", filtered.productionDates.get(0).normalized);
                assertEquals(0, filtered.expiryDates.size());

                DateOcrParser.Result explicit = DateEvidencePolicy.apply(
                        DateOcrParser.parse(
                                "生产日期 2023.12.06 有效期至 2023.12",
                                "2026-07-15"
                        ),
                        "有效期至 2023.12"
                );
                assertEquals(1, explicit.expiryDates.size());
                assertEquals("2023-12-31", explicit.expiryDates.get(0).normalized);
            }
        });

        test("Date evidence policy never preselects the same date as both production and expiry", new TestCase() {
            public void run() {
                List<DateOcrParser.DateCandidate> production = new ArrayList<DateOcrParser.DateCandidate>();
                production.add(new DateOcrParser.DateCandidate(
                        "productionDate", "2021-04-03", "2021-04-03", "", 0.58d, true, false
                ));
                List<DateOcrParser.DateCandidate> expiry = new ArrayList<DateOcrParser.DateCandidate>();
                expiry.add(new DateOcrParser.DateCandidate(
                        "expiryDate", "2021-04-03", "2021-04-03", "best before", 0.88d, false, false
                ));
                DateOcrParser.Result filtered = DateEvidencePolicy.apply(
                        new DateOcrParser.Result(
                                "", "", production, expiry,
                                new ArrayList<DateOcrParser.ShelfLifeCandidate>(),
                                new ArrayList<DateOcrParser.DateCandidate>()
                        ),
                        ""
                );
                assertEquals(0, filtered.productionDates.size());
                assertEquals(1, filtered.expiryDates.size());

                production = new ArrayList<DateOcrParser.DateCandidate>();
                production.add(new DateOcrParser.DateCandidate(
                        "productionDate", "2021-04-03", "2021-04-03", "", 0.58d, true, false
                ));
                expiry = new ArrayList<DateOcrParser.DateCandidate>();
                expiry.add(new DateOcrParser.DateCandidate(
                        "expiryDate", "2021-04-03", "2021-04-03", "", 0.58d, true, false
                ));
                DateOcrParser.Result unhinted = DateEvidencePolicy.apply(
                        new DateOcrParser.Result(
                                "", "", production, expiry,
                                new ArrayList<DateOcrParser.ShelfLifeCandidate>(),
                                new ArrayList<DateOcrParser.DateCandidate>()
                        ),
                        ""
                );
                assertEquals(1, unhinted.productionDates.size());
                assertEquals(0, unhinted.expiryDates.size());
            }
        });

        test("Date evidence policy keeps an explicitly labeled expiry when merged OCR also marks it as production", new TestCase() {
            public void run() {
                List<DateOcrParser.DateCandidate> production = new ArrayList<DateOcrParser.DateCandidate>();
                production.add(new DateOcrParser.DateCandidate(
                        "productionDate", "2021/04/03", "2021-04-03",
                        "Produced by ... Best Before: 2021/04/03", 0.88d, false, false
                ));
                List<DateOcrParser.DateCandidate> expiry = new ArrayList<DateOcrParser.DateCandidate>();
                expiry.add(new DateOcrParser.DateCandidate(
                        "expiryDate", "2021/04/03", "2021-04-03",
                        "Best Before: 2021/04/03", 0.88d, false, false
                ));

                DateOcrParser.Result filtered = DateEvidencePolicy.apply(
                        new DateOcrParser.Result(
                                "", "", production, expiry,
                                new ArrayList<DateOcrParser.ShelfLifeCandidate>(),
                                new ArrayList<DateOcrParser.DateCandidate>()
                        ),
                        "Best Before: 2021/04/03"
                );

                assertEquals(0, filtered.productionDates.size());
                assertEquals(1, filtered.expiryDates.size());
                assertEquals("2021-04-03", filtered.expiryDates.get(0).normalized);
            }
        });

        test("July 15 repeated laser production plus labeled expiry is reliable direct evidence", new TestCase() {
            public void run() {
                String raw = "202606080846.50\n202606080846153\n"
                        + "保质期20270307\n保质期20270302";
                DateOcrParser.Result parsed = DateEvidencePolicy.apply(
                        DateOcrParser.parse(raw),
                        "保质期20270307"
                );
                DateOcrFrameVoter.VoteResult direct = DateOcrFrameVoter.vote(
                        Arrays.asList(parsed),
                        1
                );

                assertTrue(UnifiedRecognitionStabilizer.isReliableDirectDatePair(direct),
                        "repeated laser production plus a labeled original expiry should be reliable");
                assertEquals("2026-06-08", direct.productionDate.value);
                assertEquals("2027-03-07", direct.expiryDate.value);
            }
        });

        test("Date payload allows same-day production and expiry", new TestCase() {
            public void run() {
                FoodItem draft = DateOcrResultPayload.toDraft(
                        "2026-07-15", "2026-07-15", false, null, ""
                );
                assertEquals("2026-07-15", draft.productionDate);
                assertEquals("2026-07-15", draft.expiryDate);
            }
        });

        test("DateOcrResultPayload maps stable frame vote to editable draft", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("prod date 2026-07-01 shelf life 7 days"));
                frames.add(DateOcrParser.parse("production date 2026/07/01 shelf life 7 days"));

                DateOcrFrameVoter.VoteResult vote = DateOcrFrameVoter.vote(frames);
                FoodItem draft = DateOcrResultPayload.toDraft(vote);

                assertTrue(vote.readyForUserConfirmation(), "vote should be stable before draft mapping");
                assertEquals("2026-07-01", draft.productionDate);
                assertEquals(Integer.valueOf(7), draft.shelfLifeValue);
                assertEquals("day", draft.shelfLifeUnit);
                assertEquals("2026-07-08", draft.expiryDate);
                assertEquals("calculated", draft.dateSource);
            }
        });
    }

    private void runUnifiedRecognitionStabilizerTests() {
        test("RecognitionTextCleaner removes screen-recorded app UI but keeps label candidates", new TestCase() {
            public void run() {
                String cleaned = RecognitionTextCleaner.cleanForPackagingOcr(
                        "识别结果\n"
                                + "商品码 未发现稳定条码\n"
                                + "条码 / 生产日期 / 保质期\n"
                                + "保质期:540天\n"
                                + "6920459940310\n"
                                + "填入新增表单"
                );

                assertFalse(cleaned.contains("识别结果"), "screen-recorded result panel should be removed");
                assertFalse(cleaned.contains("填入新增表单"), "screen-recorded button text should be removed");
                assertTrue(cleaned.contains("保质期:540天"), "real shelf life should stay");
                assertEquals("6920459940310", RecognitionTextCleaner.extractProductCodeFromOcr(cleaned));
                assertFalse(
                        RecognitionTextCleaner.isHighConfidenceFoodProductName(
                                "候选 可继续识别日期或直接慎入表单"
                        ),
                        "fuzzy OCR from the old result panel must not become a product name"
                );
            }
        });

        test("RecognitionTextCleaner extracts packaging product name from OCR text", new TestCase() {
            public void run() {
                String name = RecognitionTextCleaner.extractProductNameFromOcr(
                        "大董老北京\n"
                                + "炸酱面 约2人份\n"
                                + "配料：小麦粉、饮用水\n"
                                + "净含量 550g"
                );

                assertTrue(name.contains("大董老北京") || name.contains("炸酱面"),
                        "product name should come from visible packaging words");
                assertFalse(name.contains("配料"), "ingredients line should not become product name");
            }
        });

        test("RecognitionTextCleaner rejects numbered medical explanation without rejecting numeric food names", new TestCase() {
            public void run() {
                String medicalExplanation = "2.用于非增殖性糖尿病视网膜病变，可改善相关症状";

                assertEquals(0, RecognitionTextCleaner.productNameScore(medicalExplanation));
                assertEquals(0, RecognitionTextCleaner.productNameScore(
                        "用于非增殖性糖尿病视网膜病变的患者说明"
                ));
                assertEquals(0, RecognitionTextCleaner.productNameScore(
                        "病变气滞花食的视物昏花 面色晦暗 眼底点片状出血 舌质紫"
                ));
                assertEquals(0, RecognitionTextCleaner.productNameScore(
                        "状本品为橙色的薄衣片，除去包装后显黄棕色至棕色，气香"
                ));
                assertEquals(0, RecognitionTextCleaner.productNameScore(
                        "不良反应临床试验和监测中可见以下不良反应 胃肠系统"
                ));
                assertTrue(RecognitionTextCleaner.productNameScore("3+2苏打饼干") > 0,
                        "a normal food name containing digits should remain eligible");
                assertTrue(RecognitionTextCleaner.productNameScore("0糖可乐") > 0,
                        "a numeric product attribute should not be treated as a numbered sentence");

                String extracted = RecognitionTextCleaner.extractProductNameFromOcr(
                        medicalExplanation + "\n3+2苏打饼干"
                );
                assertTrue(extracted.contains("饼干"),
                        "the real food candidate should survive canonical phrase extraction");
                assertFalse(extracted.contains("糖尿病"),
                        "the medical explanation must never win product-name extraction");

                List<PackagingTextAnalyzer.Candidate> candidates = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation(
                                medicalExplanation,
                                0.20d,
                                0.90d,
                                0.5d,
                                0.5d,
                                1d
                        ),
                        new PackagingTextAnalyzer.Observation(
                                "3+2苏打饼干",
                                0.05d,
                                0.35d,
                                0.5d,
                                0.5d,
                                0.8d
                        )
                ));
                assertEquals(1, candidates.size());
                assertTrue(candidates.get(0).text.contains("饼干"),
                        "the analyzer should retain the food candidate");
                assertFalse(candidates.get(0).text.contains("糖尿病"),
                        "the analyzer must discard the medical explanation even when it is visually larger");
            }
        });

        test("DateOcrFrameVoter prefers a repeated laser date pair over more weak single-date frames", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                for (int index = 0; index < 7; index++) {
                    frames.add(DateOcrParser.parse("20260612"));
                }
                for (int index = 0; index < 4; index++) {
                    frames.add(DateOcrParser.parse("20260612/20270611"));
                }

                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(frames, 3);
                assertEquals("2026-06-12", result.productionDate.value);
                assertEquals("2027-06-11", result.expiryDate.value);
                assertFalse(result.hasConflict, "strong chronological-pair evidence should resolve weak ambiguity");
            }
        });

        test("DateOcrFrameVoter accepts two matching laser pairs over incomplete date reads", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("20260612"));
                frames.add(DateOcrParser.parse("20260612/2027061"));
                frames.add(DateOcrParser.parse("20260612"));
                frames.add(DateOcrParser.parse("20260612/20270611"));
                frames.add(DateOcrParser.parse("20260612/20270611"));

                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(frames, 3);
                assertEquals("2026-06-12", result.productionDate.value);
                assertEquals("2027-06-11", result.expiryDate.value);
                assertFalse(result.hasConflict, "matching explicit pairs should beat incomplete weak reads");
            }
        });

        test("DateOcrFrameVoter accepts a laser pair repeated by independent OCR variants", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse(
                        "20260612/20270611\n酸酸爽爽\n20260612/20270611"
                ));

                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(frames, 3);
                assertEquals("2026-06-12", result.productionDate.value);
                assertEquals("2027-06-11", result.expiryDate.value);
                assertFalse(result.hasConflict, "matching OCR variants should corroborate the date pair");
            }
        });

        test("DateOcrFrameVoter accepts the full noisy laser-video OCR transcript", new TestCase() {
            public void run() {
                String raw = "20260612/20270611\n酸酸爽\n配啥者\n"
                        + "2026061212027\n酸酸\n配\n2026061212027\n"
                        + "0260612/20270611\n酸酸爽爽\n啥都好吃!\n爽爽\nF吃!\n奖爽\nF吃";
                DateOcrParser.Result parsed = DateOcrParser.parseFocusedWithDateOnlySupplement("", raw);
                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(
                        java.util.Collections.singletonList(parsed),
                        3
                );

                assertEquals("2026-06-12", result.productionDate.value);
                assertEquals("2027-06-11", result.expiryDate.value);
                assertFalse(result.hasConflict, "damaged duplicates must not defeat the clear repeated date pair");
            }
        });

        test("DateOcrParser maps a laser-printed compact date range chronologically", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse("20260612/20270611");

                assertEquals(1, result.productionDates.size());
                assertEquals(1, result.expiryDates.size());
                assertEquals("2026-06-12", result.productionDates.get(0).normalized);
                assertEquals("2027-06-11", result.expiryDates.get(0).normalized);
                assertFalse(result.hasDateConflict(), "a chronological laser-code pair should not conflict");

                DateOcrParser.Result missingSlash = DateOcrParser.parse("2026061220270611");
                assertEquals("2026-06-12", missingSlash.productionDates.get(0).normalized);
                assertEquals("2027-06-11", missingSlash.expiryDates.get(0).normalized);
            }
        });

        test("RecognitionTextCleaner rejects Chinese packaging metadata and matches OCR variants", new TestCase() {
            public void run() {
                assertEquals(0, RecognitionTextCleaner.productNameScore("营养表"));
                assertEquals(0, RecognitionTextCleaner.productNameScore("水化合物 三就食"));
                assertEquals(0, RecognitionTextCleaner.productNameScore("配料表 小麦粉 饮用水"));
                assertEquals(0, RecognitionTextCleaner.productNameScore("厂家地址：北京市朝阳区"));
                assertEquals(0, RecognitionTextCleaner.productNameScore("如您有宝贵建议请拨打公司服务电话"));
                assertEquals(0, RecognitionTextCleaner.productNameScore("受委托生产 产地黑龙江哈尔滨市"));
                assertTrue(RecognitionTextCleaner.productNameScore("BLUE") > 0,
                        "Latin packaging brands should remain available as candidates");
                assertTrue(
                        RecognitionTextCleaner.productNameScore("酸菜")
                                > RecognitionTextCleaner.productNameScore("配啥都好吃 酸酸爽爽"),
                        "food product words should outrank repeated marketing slogans"
                );
                assertTrue(
                        RecognitionTextCleaner.isLikelyMarketingSlogan("蛋白爽滑 蛋黄绵密"),
                        "packaging slogans should not become an automatic lock"
                );
                assertEquals(
                        "壳清水鹌鹑蛋",
                        RecognitionTextCleaner.cleanProductNameLine("壳清水鹤鹑蛋 O产品名称:清水鹌鹑蛋")
                );
                assertEquals(
                        "BLUE 果汁饮料",
                        RecognitionTextCleaner.cleanProductNameLine("第blue 票汁系料")
                );
                assertEquals("喝开水", RecognitionTextCleaner.cleanProductNameLine("個开水"));
                assertTrue(
                        RecognitionTextCleaner.extractFoodNameFragments("清净减盐工艺 個酸菜").contains("酸菜"),
                        "a short food name should survive a noisy packaging line"
                );
                assertTrue(RecognitionTextCleaner.isCanonicalFoodName("大董老北京炸酱面"),
                        "a brand-prefixed food name should remain canonical");
                assertFalse(RecognitionTextCleaner.isCanonicalFoodName("清净减盐工艺 個酸菜"),
                        "a noisy sentence containing a food word is not itself a product name");
                assertTrue(
                        RecognitionTextCleaner.productNamesSimilar("大董北京炸酱面", "大董老北京炸酱面"),
                        "Chinese LCS similarity should tolerate one inserted OCR character"
                );
            }
        });

        test("RecognitionTextCleaner normalizes common Chinese drink-name OCR errors", new TestCase() {
            public void run() {
                assertEquals("喝开水", RecognitionTextCleaner.extractFoodNameFragments("遇开水").get(0));
                assertEquals("喝开水", RecognitionTextCleaner.extractFoodNameFragments("倜开水").get(0));
                assertEquals("纯牛奶", RecognitionTextCleaner.intelligentProductNameCandidate("純牛如"));
                assertEquals("去壳清水鹌鹑蛋", RecognitionTextCleaner.intelligentProductNameCandidate("去売清水鹌鹑蛋"));
                assertEquals("天然水", RecognitionTextCleaner.intelligentProductNameCandidate("文用天然水 1.31魚天繁水"));
                assertTrue(RecognitionTextCleaner.extractFoodNameFragments("饮用纯净水").contains("饮用纯净水"),
                        "Chinese water categories should remain available without inventing a brand");
                assertFalse(RecognitionTextCleaner.productNamesSimilar("天然水", "饮用天然矿泉水"),
                        "different water subcategories must not share one candidate cluster");
                assertTrue(RecognitionTextCleaner.productNamesSimilar("姓哈哈", "娃哈哈"),
                        "one-character OCR substitutions in a three-character brand should cluster");
                assertFalse(RecognitionTextCleaner.productNamesSimilar("必宝", "怡宝"),
                        "conflicting two-character brands need independent evidence instead of guessing");
                assertFalse(RecognitionTextCleaner.isLikelyFoodProductName("望 水"),
                        "a random short OCR phrase must not become a water product");
                assertTrue(RecognitionTextCleaner.cleanForPackagingOcr("商品名称：奥利奥").contains("奥利奥"),
                        "explicit product-name labels must survive app UI filtering");
            }
        });

        test("PackagingTextAnalyzer uses consensus brands and falls back on category when short brands conflict", new TestCase() {
            public void run() {
                List<PackagingTextAnalyzer.Candidate> water = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation("姓哈哈", 0.08d, 0.30d, 0.50d, 0.32d, 0.92d),
                        new PackagingTextAnalyzer.Observation("哇哈哈", 0.08d, 0.30d, 0.50d, 0.33d, 0.92d),
                        new PackagingTextAnalyzer.Observation("娃哈哈", 0.08d, 0.30d, 0.50d, 0.34d, 0.92d),
                        new PackagingTextAnalyzer.Observation("娃哈味", 0.08d, 0.30d, 0.50d, 0.35d, 0.92d),
                        new PackagingTextAnalyzer.Observation("饮用净水", 0.12d, 0.42d, 0.50d, 0.45d, 0.95d)
                ));
                assertEquals("饮用净水", water.get(0).text);

                List<PackagingTextAnalyzer.Candidate> conflict = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation("必宝", 0.08d, 0.22d, 0.45d, 0.34d, 0.92d),
                        new PackagingTextAnalyzer.Observation("怡寶", 0.08d, 0.22d, 0.55d, 0.35d, 0.92d),
                        new PackagingTextAnalyzer.Observation("饮用纯净水", 0.12d, 0.44d, 0.50d, 0.46d, 0.95d)
                ));
                assertEquals("饮用纯净水", conflict.get(0).text);

                List<PackagingTextAnalyzer.Candidate> milk = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation("浓纯营界 航天品质", 0.08d, 0.40d, 0.50d, 0.34d, 0.92d),
                        new PackagingTextAnalyzer.Observation("純牛如", 0.16d, 0.34d, 0.50d, 0.46d, 0.95d)
                ));
                assertEquals("纯牛奶", milk.get(0).text);
            }
        });

        test("PackagingTextAnalyzer uses temporal brand medoids for video without weakening still-image review", new TestCase() {
            public void run() {
                List<PackagingTextAnalyzer.Observation> observations = Arrays.asList(
                        new PackagingTextAnalyzer.Observation("外星人", 0.08d, 0.28d, 0.50d, 0.34d, 0.96d),
                        new PackagingTextAnalyzer.Observation("外量人", 0.08d, 0.28d, 0.50d, 0.30d, 0.90d),
                        new PackagingTextAnalyzer.Observation("外显人", 0.08d, 0.28d, 0.50d, 0.27d, 0.88d),
                        new PackagingTextAnalyzer.Observation("外星入", 0.08d, 0.28d, 0.50d, 0.25d, 0.86d),
                        new PackagingTextAnalyzer.Observation("维C水", 0.13d, 0.38d, 0.50d, 0.43d, 0.95d)
                );

                assertEquals("维C水", PackagingTextAnalyzer.analyze(observations).get(0).text);
                assertEquals(
                        "外星人维C水",
                        PackagingTextAnalyzer.analyze(observations, false).get(0).text
                );
            }
        });

        test("DateOcrParser does not join package volume to the next-line Chinese day character", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse(
                        "饮用天然水 净含量 550mL\n天然水净含量50\n天然"
                );
                assertEquals(0, result.shelfLives.size());
            }
        });

        test("Packaging name fusion prefers the July 15 visible Chinese product name", new TestCase() {
            public void run() {
                assertFalse(RecognitionTextCleaner.isHighConfidenceFoodProductName("VI 低糖"),
                        "an OCR fragment plus a nutrition attribute is not a product name");
                assertFalse(RecognitionTextCleaner.isLikelyStandaloneBrand("ECI"),
                        "a three-letter OCR fragment should not be fused as a brand");
                assertEquals(
                        "外星人维C水",
                        RecognitionTextCleaner.intelligentProductNameCandidate("名外星人维C水")
                );
                assertEquals(
                        "外星人维C水",
                        RecognitionTextCleaner.intelligentProductNameCandidate(
                                RecognitionTextCleaner.extractLabeledProductName(
                                        "品名：外星人维C水车厘子蔓越莓口味维生素饮料"
                                )
                        )
                );
                List<PackagingTextAnalyzer.Candidate> candidates = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation(
                                "外星人", 0.08d, 0.30d, 0.50d, 0.34d, 0.92d
                        ),
                        new PackagingTextAnalyzer.Observation(
                                "维C水", 0.13d, 0.38d, 0.50d, 0.43d, 0.95d
                        ),
                        new PackagingTextAnalyzer.Observation(
                                "VI 低糖", 0.16d, 0.42d, 0.50d, 0.52d, 0.95d
                        )
                ));
                assertEquals("外星人维C水", candidates.get(0).text);

                List<PackagingTextAnalyzer.Candidate> brandBelowProduct =
                        PackagingTextAnalyzer.analyze(Arrays.asList(
                                new PackagingTextAnalyzer.Observation(
                                        "维C水", 0.13d, 0.38d, 0.50d, 0.31d, 0.95d
                                ),
                                new PackagingTextAnalyzer.Observation(
                                        "外星人", 0.05d, 0.22d, 0.50d, 0.52d, 0.92d
                                )
                        ));
                assertEquals("外星人维C水", brandBelowProduct.get(0).text);
            }
        });

        test("RecognitionTextCleaner does not turn production dates into EAN-8 barcodes", new TestCase() {
            public void run() {
                assertEquals("", RecognitionTextCleaner.extractProductCodeFromOcr("生产日期 2026-07-05"));
                assertEquals("", RecognitionTextCleaner.extractProductCodeFromOcr("有效期至 20260705"));
                assertEquals("", RecognitionTextCleaner.extractProductCodeFromOcr("20260612/20270611"));
                assertEquals("", RecognitionTextCleaner.extractProductCodeFromOcr("L (0211 59898584"));
                assertEquals("96385074", RecognitionTextCleaner.extractProductCodeFromOcr("商品码：96385074"));
                assertEquals("4006381333931", RecognitionTextCleaner.extractProductCodeFromOcr("4006381333931"));
            }
        });

        test("PackagingTextAnalyzer rejects metadata and prioritizes a food name over a large slogan", new TestCase() {
            public void run() {
                List<PackagingTextAnalyzer.Observation> metadata = Arrays.asList(
                        new PackagingTextAnalyzer.Observation("营养成分表", 0.08d, 0.40d, 0.5d, 0.5d, 1d),
                        new PackagingTextAnalyzer.Observation("配料表：小麦粉、饮用水", 0.06d, 0.70d, 0.5d, 0.5d, 1d),
                        new PackagingTextAnalyzer.Observation("生产日期 2026-07-01", 0.06d, 0.50d, 0.5d, 0.5d, 1d),
                        new PackagingTextAnalyzer.Observation("保质期 180天", 0.06d, 0.40d, 0.5d, 0.5d, 1d),
                        new PackagingTextAnalyzer.Observation("净含量 500g", 0.06d, 0.30d, 0.5d, 0.5d, 1d),
                        new PackagingTextAnalyzer.Observation("执行标准 GB/T 20977", 0.05d, 0.50d, 0.5d, 0.5d, 1d),
                        new PackagingTextAnalyzer.Observation("厂家地址 北京市朝阳区", 0.05d, 0.70d, 0.5d, 0.5d, 1d),
                        new PackagingTextAnalyzer.Observation("食用方法 煮制五分钟", 0.05d, 0.60d, 0.5d, 0.5d, 1d),
                        new PackagingTextAnalyzer.Observation("扫码关注公众号", 0.05d, 0.40d, 0.5d, 0.5d, 1d)
                );
                assertEquals(0, PackagingTextAnalyzer.analyze(metadata).size());

                List<PackagingTextAnalyzer.Candidate> candidates = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation("芝麻饼", 0.035d, 0.30d, 0.5d, 0.5d, 0.95d),
                        new PackagingTextAnalyzer.Observation("经典原味", 0.16d, 0.62d, 0.5d, 0.5d, 0.80d)
                ));
                assertEquals("芝麻饼", candidates.get(0).text);
            }
        });

        test("PackagingTextAnalyzer keeps only plausible food names and score ranks them", new TestCase() {
            public void run() {
                List<PackagingTextAnalyzer.Candidate> candidates = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation("大董炸酱面", 0.18d, 0.72d, 0.5d, 0.45d, 0.95d),
                        new PackagingTextAnalyzer.Observation("老北京风味", 0.12d, 0.55d, 0.5d, 0.55d, 0.90d),
                        new PackagingTextAnalyzer.Observation("手工宽面", 0.08d, 0.45d, 0.5d, 0.60d, 0.85d),
                        new PackagingTextAnalyzer.Observation("传统酱香", 0.05d, 0.38d, 0.5d, 0.65d, 0.80d)
                ));
                assertEquals(2, candidates.size());
                assertTrue(candidates.get(0).score >= candidates.get(1).score, "candidates should be score sorted");
                assertTrue(RecognitionTextCleaner.isLikelyFoodProductName(candidates.get(0).text),
                        "every visible candidate must look like a real food name");
                assertTrue(RecognitionTextCleaner.isLikelyFoodProductName(candidates.get(1).text),
                        "low-confidence slogan text must not remain in the candidate list");
            }
        });

        test("PackagingTextAnalyzer prioritizes labeled names and rejects medical screen text", new TestCase() {
            public void run() {
                List<PackagingTextAnalyzer.Candidate> labeled = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation(
                                "产品名称：清水鹌鹑蛋 净含量 100克",
                                0.08d, 0.60d, 0.5d, 0.5d, 0.95d
                        ),
                        new PackagingTextAnalyzer.Observation(
                                "功能主治活血化瘀用于气滞血瘀所致的胸闷",
                                0.16d, 0.80d, 0.5d, 0.5d, 0.95d
                        )
                ));
                assertEquals(1, labeled.size());
                assertEquals("清水鹌鹑蛋", labeled.get(0).text);
                assertEquals("", RecognitionTextCleaner.extractLabeledProductName(
                        "企业名称：天士力医药集团股份有限公司"
                ));
                assertFalse(RecognitionTextCleaner.isLikelyFoodProductName(
                        "天士力医药集团股份有限公司"
                ), "company names must not be shown as product-name candidates");

                List<PackagingTextAnalyzer.Candidate> medical = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation(
                                "不良反应临床试验和监测中可见以下不良反应 胃肠系统",
                                0.18d, 0.90d, 0.5d, 0.5d, 1d
                        )
                ));
                assertEquals(0, medical.size());
            }
        });

        test("PackagingTextAnalyzer accepts a clean labeled brand without a food suffix", new TestCase() {
            public void run() {
                List<PackagingTextAnalyzer.Candidate> candidates = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation(
                                "产品名称：奥利奥",
                                0.08d, 0.60d, 0.5d, 0.5d, 0.95d
                        )
                ));

                assertEquals(1, candidates.size());
                assertEquals("奥利奥", candidates.get(0).text);
                assertTrue(RecognitionTextCleaner.isHighConfidenceLabeledProductName("奥利奥"),
                        "an explicit product-name label is strong evidence for a clean brand name");
            }
        });

        test("RecognitionTextCleaner rejects garbled labels and reduces duplicate food names", new TestCase() {
            public void run() {
                assertEquals("炸酱面", RecognitionTextCleaner.intelligentProductNameCandidate(
                        "AJANGuIAS 炸酱面"
                ));
                assertEquals("", RecognitionTextCleaner.intelligentProductNameCandidate(
                        "存去元承水鸭蛋OF品类型:再"
                ));
                assertEquals("酸菜", RecognitionTextCleaner.intelligentProductNameCandidate(
                        "发醇酸菜 區酸菜"
                ));
                assertEquals("去壳清水鹌鹑蛋", RecognitionTextCleaner.intelligentProductNameCandidate(
                        "去壳清水鹌鹑蛋"
                ));
            }
        });

        test("PackagingTextAnalyzer lets a clean food line beat a garbled product label", new TestCase() {
            public void run() {
                List<PackagingTextAnalyzer.Candidate> candidates = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation(
                                "去壳清水鹌鹑蛋\nOP品名存去元承水鸭蛋OF品类型:再",
                                0.12d, 0.75d, 0.5d, 0.5d, 0.9d
                        )
                ));
                assertTrue(candidates.size() > 0, "clean package text should remain usable");
                assertEquals("去壳清水鹌鹑蛋", candidates.get(0).text);
            }
        });

        test("PackagingTextAnalyzer extracts a canonical food phrase from noisy Chinese OCR", new TestCase() {
            public void run() {
                List<PackagingTextAnalyzer.Candidate> candidates = PackagingTextAnalyzer.analyze(Arrays.asList(
                        new PackagingTextAnalyzer.Observation(
                                "墨酸菜 0活净发减盆 工艺 0家名",
                                0.12d,
                                0.62d,
                                0.5d,
                                0.5d,
                                0.9d
                        )
                ));
                assertEquals("酸菜", candidates.get(0).text);
            }
        });

        test("UnifiedRecognitionStabilizer locks barcode after repeated frames", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(6, 3);

                UnifiedRecognitionStabilizer.Snapshot first = stabilizer.addFrame(
                        "4006381333931",
                        DateOcrParser.parse(""),
                        "",
                        false
                );
                assertFalse(first.hasStableBarcode(), "single live frame should not lock barcode");
                assertFalse(first.hasFillableCandidate(), "single live barcode should not be fillable yet");

                UnifiedRecognitionStabilizer.Snapshot second = stabilizer.addFrame(
                        "4006381333931",
                        DateOcrParser.parse("noise"),
                        "noise",
                        false
                );
                assertEquals("4006381333931", second.stableBarcode);
                assertTrue(second.hasStableBarcode(), "same barcode should lock after two frames");
                assertTrue(second.hasFillableCandidate(), "locked barcode alone should allow confirmation/editing");

                UnifiedRecognitionStabilizer.Snapshot afterNoise = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "",
                        false
                );
                assertEquals("4006381333931", afterNoise.stableBarcode);
                assertTrue(afterNoise.hasFillableCandidate(), "empty frames must not clear locked barcode");
            }
        });

        test("UnifiedRecognitionStabilizer old addFrame waits for repeated packaging text and keeps its lock", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(6, 3);

                UnifiedRecognitionStabilizer.Snapshot first = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("大董老北京\n炸酱面"),
                        "大董老北京\n炸酱面",
                        false
                );
                assertFalse(first.hasStablePackagingName(), "first live packaging candidate must not lock");
                assertFalse(first.rankedPackagingCandidates.isEmpty(), "old addFrame should still extract candidates");

                UnifiedRecognitionStabilizer.Snapshot second = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("大董老北京\n炸酱面"),
                        "大董老北京\n炸酱面",
                        false
                );
                assertFalse(second.hasStablePackagingName(), "two live frames should remain selectable but unlocked");
                UnifiedRecognitionStabilizer.Snapshot third = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("大董老北京\n炸酱面"),
                        "大董老北京\n炸酱面",
                        false
                );
                assertTrue(third.hasStablePackagingName(), "three repeated packaging frames should lock");
                String locked = third.stablePackagingName;

                UnifiedRecognitionStabilizer.Snapshot afterNoise = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("随机噪声"),
                        "完全不同的随机噪声",
                        Arrays.asList(new PackagingTextAnalyzer.Candidate("错误候选", 200d)),
                        false
                );
                assertEquals(locked, afterNoise.stablePackagingName);
            }
        });

        test("UnifiedRecognitionStabilizer replaces a weak first candidate with a stronger repeated candidate", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(8, 3);

                UnifiedRecognitionStabilizer.Snapshot first = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "家常面",
                        Arrays.asList(new PackagingTextAnalyzer.Candidate("家常面", 32d)),
                        false
                );
                assertFalse(first.hasStablePackagingName(), "weak first-frame candidate must remain unlocked");

                UnifiedRecognitionStabilizer.Snapshot second = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "大董老北京炸酱面",
                        Arrays.asList(new PackagingTextAnalyzer.Candidate("大董老北京炸酱面", 120d)),
                        false
                );
                assertFalse(second.hasStablePackagingName(), "one frame of the stronger candidate is not enough");
                assertEquals("大董老北京炸酱面", second.rankedPackagingCandidates.get(0).text);

                UnifiedRecognitionStabilizer.Snapshot third = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "大董老北京炸酱面",
                        Arrays.asList(
                                new PackagingTextAnalyzer.Candidate("大董老北京炸酱面", 118d),
                                new PackagingTextAnalyzer.Candidate("老北京风味", 62d),
                                new PackagingTextAnalyzer.Candidate("手工宽面", 50d)
                        ),
                        false
                );
                assertFalse(third.hasStablePackagingName(), "two stronger frames should still wait for one more vote");
                UnifiedRecognitionStabilizer.Snapshot fourth = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "大董老北京炸酱面",
                        Arrays.asList(new PackagingTextAnalyzer.Candidate("大董老北京炸酱面", 121d)),
                        false
                );
                assertEquals("大董老北京炸酱面", fourth.stablePackagingName);
                assertEquals(3, fourth.packagingNameVotes);
                assertTrue(fourth.rankedPackagingCandidates.size() <= 3,
                        "the recommended name may retain up to two hidden alternatives for user choice");
                assertEquals("大董老北京炸酱面", fourth.rankedPackagingCandidates.get(0).text);
                assertEquals(3, fourth.rankedPackagingCandidates.get(0).votes);
            }
        });

        test("UnifiedRecognitionStabilizer lets a selected image lock one packaging candidate", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(6, 3);
                UnifiedRecognitionStabilizer.Snapshot snapshot = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "冷萃乌龙茶",
                        Arrays.asList(new PackagingTextAnalyzer.Candidate("冷萃乌龙茶", 96d)),
                        true
                );

                assertEquals("冷萃乌龙茶", snapshot.stablePackagingName);
                assertTrue(snapshot.hasFillableCandidate(), "single selected image candidate should be fillable");
            }
        });

        test("UnifiedRecognitionStabilizer hides repeated low-confidence non-food names", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(8, 3);
                UnifiedRecognitionStabilizer.Snapshot snapshot = null;
                for (int index = 0; index < 6; index++) {
                    snapshot = stabilizer.addFrame(
                            "",
                            DateOcrParser.parse(""),
                            "功能主治活血化瘀用于气滞血瘀所致的胸闷",
                            Arrays.asList(new PackagingTextAnalyzer.Candidate(
                                    "功能主治活血化瘀用于气滞血瘀所致的胸闷",
                                    240d
                            )),
                            false
                    );
                }
                assertEquals(0, snapshot.rankedPackagingCandidates.size());
                assertFalse(snapshot.hasStablePackagingName(),
                        "repetition alone must never promote non-food explanatory text");
            }
        });

        test("UnifiedRecognitionStabilizer exposes a clean explicitly labeled brand", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(6, 3);
                UnifiedRecognitionStabilizer.Snapshot snapshot = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "产品名称：奥利奥",
                        true
                );

                assertEquals("奥利奥", snapshot.bestPackagingNameForConfirmation());
                assertTrue(snapshot.hasFillableCandidate(),
                        "a clean explicit label should reach the editable confirmation form");
            }
        });

        test("UnifiedRecognitionStabilizer does not downgrade a rejected label into a generic name", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(6, 3);
                UnifiedRecognitionStabilizer.Snapshot snapshot = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "产品名称：好喝果汁",
                        true
                );

                assertEquals(0, snapshot.rankedPackagingCandidates.size());
                assertFalse(snapshot.hasFillableCandidate(),
                        "a label rejected as marketing copy must not re-enter through generic OCR scoring");
            }
        });

        test("UnifiedRecognitionStabilizer exposes one gated name to the confirmation form", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(8, 3);
                UnifiedRecognitionStabilizer.Snapshot snapshot = null;
                for (int index = 0; index < 2; index++) {
                    snapshot = stabilizer.addFrame(
                            "",
                            DateOcrParser.parse(""),
                            "酸菜",
                            Arrays.asList(new PackagingTextAnalyzer.Candidate("酸菜", 160d)),
                            false
                    );
                }

                assertFalse(snapshot.hasStablePackagingName(),
                        "two frames should remain below the automatic lock threshold");
                assertEquals("酸菜", snapshot.bestPackagingNameForConfirmation());
                assertTrue(snapshot.hasFillableCandidate(),
                        "the only name that passed strict display gating should fill the review form");
            }
        });

        test("UnifiedRecognitionStabilizer caps repeated-slogan duration bias", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(20, 3);
                for (int index = 0; index < 10; index++) {
                    stabilizer.addFrame(
                            "",
                            DateOcrParser.parse(""),
                            "配啥都好吃 酸酸爽爽",
                            Arrays.asList(new PackagingTextAnalyzer.Candidate("配啥都好吃 酸酸爽爽", 68d)),
                            false
                    );
                }
                UnifiedRecognitionStabilizer.Snapshot snapshot = null;
                for (int index = 0; index < 3; index++) {
                    snapshot = stabilizer.addFrame(
                            "",
                            DateOcrParser.parse(""),
                            "酸菜",
                            Arrays.asList(new PackagingTextAnalyzer.Candidate("酸菜", 145d)),
                            false
                    );
                }

                assertEquals("酸菜", snapshot.rankedPackagingCandidates.get(0).text);
                assertEquals("酸菜", snapshot.stablePackagingName);
            }
        });

        test("UnifiedRecognitionStabilizer keeps the longer name inside a similar OCR cluster", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(6, 3);
                UnifiedRecognitionStabilizer.Snapshot snapshot = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse(""),
                        "BLUE\nNFC蓝莓汁饮料",
                        Arrays.asList(new PackagingTextAnalyzer.Candidate("BLUE", 160d)),
                        true
                );

                assertEquals("BLUE NFC蓝莓汁饮料", snapshot.stablePackagingName);
            }
        });

        test("UnifiedRecognitionStabilizer locks gallery barcode from single image", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(6, 3);
                UnifiedRecognitionStabilizer.Snapshot snapshot = stabilizer.addFrame(
                        "036000291452",
                        DateOcrParser.parse(""),
                        "",
                        true
                );

                assertEquals("036000291452", snapshot.stableBarcode);
                assertTrue(snapshot.hasFillableCandidate(), "single selected image barcode should be fillable");
            }
        });

        test("UnifiedRecognitionStabilizer keeps single-image Chinese date candidates for form review", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(6, 3);
                UnifiedRecognitionStabilizer.Snapshot snapshot = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("生产日期 2026-07-01 保质期 30天"),
                        "生产日期 2026-07-01 保质期 30天",
                        true
                );
                FoodItem draft = DateOcrResultPayload.toDraft(snapshot.stableDateVote);

                assertTrue(snapshot.hasStableDateCandidate(), "single selected image date should be reviewable");
                assertTrue(snapshot.hasFillableCandidate(), "single selected image date should reach the form");
                assertEquals("2026-07-01", draft.productionDate);
                assertEquals(Integer.valueOf(30), draft.shelfLifeValue);
                assertEquals("2026-07-31", draft.expiryDate);
            }
        });

        test("UnifiedRecognitionStabilizer upgrades an early weak date lock to a strong laser pair", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(20, 3);
                for (int index = 0; index < 7; index++) {
                    stabilizer.addFrame("", DateOcrParser.parse("20260612"), "20260612", false);
                }
                UnifiedRecognitionStabilizer.Snapshot snapshot = null;
                for (int index = 0; index < 4; index++) {
                    snapshot = stabilizer.addFrame(
                            "",
                            DateOcrParser.parse("20260612/20270611"),
                            "20260612/20270611",
                            false
                    );
                }

                assertEquals("2026-06-12", snapshot.stableDateVote.productionDate.value);
                assertEquals("2027-06-11", snapshot.stableDateVote.expiryDate.value);
            }
        });

        test("UnifiedRecognitionStabilizer promotes a clear final video transcript for confirmation", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(12, 3);
                stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("20260612"),
                        "20260612",
                        false
                );
                DateOcrParser.Result directResult =
                        DateOcrParser.parse("20260612/20270611\n0260612/20270611");
                UnifiedRecognitionStabilizer.Snapshot snapshot =
                        stabilizer.promoteDirectDatePairForConfirmation(directResult);

                assertEquals("2026-06-12", snapshot.stableDateVote.productionDate.value);
                assertEquals("2027-06-11", snapshot.stableDateVote.expiryDate.value);
            }
        });

        test("UnifiedRecognitionStabilizer never lets one final frame replace a stable date pair", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(12, 3);
                for (int index = 0; index < 3; index++) {
                    stabilizer.addFrame(
                            "",
                            DateOcrParser.parse("20260612/20270611"),
                            "20260612/20270611",
                            false
                    );
                }
                UnifiedRecognitionStabilizer.Snapshot snapshot =
                        stabilizer.promoteDirectDatePairForConfirmation(
                                DateOcrParser.parse("20260612/20270511")
                        );

                assertEquals("2026-06-12", snapshot.stableDateVote.productionDate.value);
                assertEquals("2027-06-11", snapshot.stableDateVote.expiryDate.value);
            }
        });

        test("UnifiedRecognitionStabilizer requires stable date votes and keeps them through noise", new TestCase() {
            public void run() {
                UnifiedRecognitionStabilizer stabilizer = new UnifiedRecognitionStabilizer(8, 3);

                stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("production date 2026-07-01 shelf life 7 days"),
                        "production date 2026-07-01 shelf life 7 days",
                        false
                );
                UnifiedRecognitionStabilizer.Snapshot second = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("prod date 2026/07/01 shelf life 7 days"),
                        "prod date 2026/07/01 shelf life 7 days",
                        false
                );
                assertFalse(second.hasStableDateCandidate(), "two frames are below unified stability threshold");

                UnifiedRecognitionStabilizer.Snapshot third = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("MFG 20260701 shelf life 7 days"),
                        "MFG 20260701 shelf life 7 days",
                        false
                );
                assertTrue(third.hasStableDateCandidate(), "three matching date frames should stabilize");
                FoodItem draft = DateOcrResultPayload.toDraft(third.stableDateVote);
                assertEquals("2026-07-01", draft.productionDate);
                assertEquals(Integer.valueOf(7), draft.shelfLifeValue);
                assertEquals("2026-07-08", draft.expiryDate);

                UnifiedRecognitionStabilizer.Snapshot afterNoise = stabilizer.addFrame(
                        "",
                        DateOcrParser.parse("unrelated label text"),
                        "unrelated label text",
                        false
                );
                assertTrue(afterNoise.hasStableDateCandidate(), "stable date candidate should survive later noise");
                assertEquals("2026-07-01", DateOcrResultPayload.toDraft(afterNoise.stableDateVote).productionDate);
            }
        });
    }

    private void runUnifiedRecognitionPayloadTests() {
        test("UnifiedRecognitionPayload merges barcode product and OCR fields into editable draft", new TestCase() {
            public void run() {
                FoodItem draft = UnifiedRecognitionPayload.toDraft(
                        "4006381333931",
                        "测试牛奶",
                        "dairy",
                        "品牌：测试\n规格：250ml",
                        "2026-07-01",
                        "2026-07-08",
                        true,
                        Integer.valueOf(7),
                        "day"
                );

                assertEquals("测试牛奶", draft.name);
                assertEquals("dairy", draft.category);
                assertEquals("件", draft.unit);
                assertEquals("2026-07-01", draft.productionDate);
                assertEquals("2026-07-08", draft.expiryDate);
                assertEquals("calculated", draft.dateSource);
                assertTrue(draft.notes.contains("4006381333931"), "draft notes should include barcode");
                assertTrue(UnifiedRecognitionPayload.hasUsableDraft(draft), "merged draft should be usable");
            }
        });

        test("UnifiedRecognitionPayload keeps barcode-only date unknown instead of no-expiry", new TestCase() {
            public void run() {
                FoodItem draft = UnifiedRecognitionPayload.toDraft(
                        "036000291452",
                        "",
                        "",
                        "",
                        "",
                        "",
                        false,
                        null,
                        ""
                );

                assertEquals("条码商品 036000291452", draft.name);
                assertEquals("unknown", draft.dateSource);
                assertTrue(draft.notes.contains("036000291452"), "barcode-only result should be preserved in notes");
                assertTrue(UnifiedRecognitionPayload.hasUsableDraft(draft), "barcode-only draft should be editable");
            }
        });

        test("UnifiedRecognitionPayload keeps packaging-text-only date unknown instead of no-expiry", new TestCase() {
            public void run() {
                FoodItem draft = UnifiedRecognitionPayload.toDraft(
                        "",
                        "大董老北京 炸酱面",
                        "",
                        "",
                        "",
                        "",
                        false,
                        null,
                        ""
                );

                assertEquals("大董老北京 炸酱面", draft.name);
                assertEquals("unknown", draft.dateSource);
                assertTrue(UnifiedRecognitionPayload.hasUsableDraft(draft), "text-only draft should be editable");
            }
        });
    }

    private void runBarcodeHistoryTestsIfAvailable() {
        final Class<?> itemClass = optionalClass("com.shiqi.expirytracker.BarcodeHistoryItem");
        if (itemClass == null) {
            System.out.println("SKIP BarcodeHistoryItem helper tests; source not compiled in this local test pass.");
            return;
        }

        test("BarcodeHistoryItem serializes confirmed draft templates", new TestCase() {
            public void run() {
                Object item = newBarcodeHistoryItem(
                        itemClass,
                        " EAN 400-6381-333931 ",
                        "  Milk  ",
                        "dairy",
                        "box",
                        "keep \"cold\"\nsecond line",
                        "2026-07-05T08:00:00+0800"
                );

                List<Object> items = new ArrayList<Object>();
                items.add(item);
                String raw = serializeBarcodeHistory(itemClass, items);

                assertTrue(raw.contains("\"schemaVersion\":1"), "history JSON should include schemaVersion");
                assertTrue(raw.contains("\\\"cold\\\""), "history JSON should escape quotes");

                List<?> parsed = parseBarcodeHistory(itemClass, raw);
                assertEquals(1, parsed.size());
                Object parsedItem = parsed.get(0);
                assertEquals("4006381333931", fieldText(parsedItem, "barcode"));
                assertEquals("Milk", fieldText(parsedItem, "name"));
                assertEquals("dairy", fieldText(parsedItem, "category"));
                assertEquals("box", fieldText(parsedItem, "unit"));
                assertEquals("keep \"cold\"\nsecond line", fieldText(parsedItem, "notes"));
                assertEquals("2026-07-05T08:00:00+0800", fieldText(parsedItem, "updatedAt"));
            }
        });

        test("BarcodeHistoryItem upserts edited confirmed drafts", new TestCase() {
            public void run() {
                Object oldItem = newBarcodeHistoryItem(
                        itemClass,
                        "4006381333931",
                        "Old milk",
                        "dairy",
                        "box",
                        "old note",
                        "2026-07-01T08:00:00+0800"
                );
                Object otherItem = newBarcodeHistoryItem(
                        itemClass,
                        "96385074",
                        "Other item",
                        "snack",
                        "bag",
                        "",
                        "2026-07-02T08:00:00+0800"
                );
                Object editedItem = newBarcodeHistoryItem(
                        itemClass,
                        "4006381333931",
                        "Edited milk",
                        "dairy",
                        "bottle",
                        "confirmed by user",
                        ""
                );

                List<Object> current = new ArrayList<Object>();
                current.add(oldItem);
                current.add(otherItem);

                List<?> result = upsertBarcodeHistory(
                        itemClass,
                        current,
                        editedItem,
                        "2026-07-05T08:00:00+0800",
                        10
                );

                assertEquals(2, result.size());
                assertEquals("Edited milk", fieldText(result.get(0), "name"));
                assertEquals("bottle", fieldText(result.get(0), "unit"));
                assertEquals("2026-07-05T08:00:00+0800", fieldText(result.get(0), "updatedAt"));
                assertEquals("96385074", fieldText(result.get(1), "barcode"));
            }
        });

        test("BarcodeHistoryItem treats bad history JSON as empty", new TestCase() {
            public void run() {
                assertEquals(0, parseBarcodeHistory(itemClass, "{bad json").size());
                assertEquals(0, parseBarcodeHistory(itemClass, "{\"items\":\"not-array\"}").size());
            }
        });
    }

    private static RecognitionFrameSelector.FrameCandidate recognitionFrame(
            String frameId,
            long timestampMillis,
            String contentSignature,
            double signalQuality
    ) {
        return new RecognitionFrameSelector.FrameCandidate(
                frameId,
                timestampMillis,
                contentSignature,
                signalQuality,
                0.5d,
                1.0d - signalQuality,
                signalQuality,
                signalQuality,
                0.5d
        );
    }

    private static RecognitionFrameSelector.FrameCandidate recognitionFrameWithEvidence(
            String frameId,
            long timestampMillis,
            String contentSignature,
            double signalQuality,
            int evidence
    ) {
        return new RecognitionFrameSelector.FrameCandidate(
                frameId,
                timestampMillis,
                contentSignature,
                signalQuality,
                0.5d,
                1.0d - signalQuality,
                signalQuality,
                signalQuality,
                0.5d,
                evidence,
                evidence
        );
    }

    private static boolean containsFrame(
            List<RecognitionFrameSelector.FrameCandidate> frames,
            String frameId
    ) {
        for (RecognitionFrameSelector.FrameCandidate frame : frames) {
            if (frame.frameId.equals(frameId)) {
                return true;
            }
        }
        return false;
    }

    private void test(String name, TestCase body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable error) {
            failed++;
            System.err.println("FAIL " + name + ": " + error.getMessage());
        }
    }

    private static FoodItem food(String name, String category, String storage, String productionDate, String expiryDate, double remainingQuantity) {
        FoodItem item = new FoodItem();
        item.name = name;
        item.category = category;
        item.storageMethod = storage;
        item.productionDate = productionDate;
        item.expiryDate = expiryDate;
        item.dateSource = expiryDate.length() > 0 ? "manual" : "unknown";
        item.quantity = Math.max(remainingQuantity, 1);
        item.remainingQuantity = remainingQuantity;
        ReminderPolicy.ensureSmartSchedule(item);
        return item;
    }

    private static List<FoodItem> largeInventory(int count) {
        List<FoodItem> foods = new ArrayList<FoodItem>();
        String today = DateRules.todayString();
        String[] names = new String[] {
                "milk", "rice", "noodles", "frozen peas", "apple", "sauce", "yogurt", "shrimp"
        };
        String[] categories = new String[] {
                "dairy", "staple", "staple", "frozen", "produce", "condiment", "dairy", "meat_egg_seafood"
        };
        String[] storageMethods = new String[] {
                "refrigerated", "room_temp", "cool_dry", "frozen", "room_temp", "avoid_light", "refrigerated", "frozen"
        };

        for (int index = 0; index < count; index++) {
            int family = index % names.length;
            int dateCase = index % 20;
            int shelfLifeDays = 3 + (index % 180);
            String expiryDate;
            if (dateCase == 0) {
                expiryDate = "";
            } else if (dateCase <= 3) {
                expiryDate = DateRules.addDaysString(today, -dateCase);
            } else if (dateCase <= 5) {
                expiryDate = today;
            } else if (dateCase <= 12) {
                expiryDate = DateRules.addDaysString(today, dateCase - 5);
            } else {
                expiryDate = DateRules.addDaysString(today, dateCase + 10);
            }

            String productionDate = expiryDate.length() > 0
                    ? DateRules.addDaysString(expiryDate, -shelfLifeDays)
                    : "";
            FoodItem item = food(
                    names[family] + " item " + String.format(Locale.US, "%03d", index),
                    categories[family],
                    storageMethods[family],
                    productionDate,
                    expiryDate,
                    1 + (index % 7)
            );
            item.id = "perf-" + index;
            item.shelfLifeValue = Integer.valueOf(shelfLifeDays);
            item.shelfLifeUnit = "day";
            item.notes = "batch " + (index % 10);
            item.unit = "item";
            item.createdAt = "2026-07-05T00:00:00+0800";
            item.updatedAt = item.createdAt;
            ReminderPolicy.ensureSmartSchedule(item);

            if (index % 53 == 0) {
                item.isFinished = true;
                item.finishedAt = today;
            }

            foods.add(item);
        }
        return foods;
    }

    private static void clearSmartScheduleForTest(FoodItem item) {
        item.smartReminderOffsets.clear();
        item.smartReminderFingerprint = "";
        item.smartReminderPlannedDaysLeft = Integer.MIN_VALUE;
        item.smartReminderPlannedOn = "";
    }

    private static String foodStoreRaw(int schemaVersion, String id, String name) {
        return "{\"schemaVersion\":" + schemaVersion
                + ",\"foods\":[{\"id\":\"" + id
                + "\",\"name\":\"" + name
                + "\",\"expiryDate\":\"2026-07-10\",\"dateSource\":\"manual\"}]}";
    }

    private static Object loadFoodStoreWithBackups(
            Class<?> migrationClass,
            String primaryRaw,
            List<String> backupRaws,
            int currentSchemaVersion
    ) {
        try {
            java.lang.reflect.Method method = migrationClass.getDeclaredMethod(
                    "loadWithBackups",
                    String.class,
                    List.class,
                    Integer.TYPE
            );
            method.setAccessible(true);
            return method.invoke(null, primaryRaw, backupRaws, Integer.valueOf(currentSchemaVersion));
        } catch (Exception error) {
            throw new AssertionError("failed to load food store with backups: " + error.getMessage());
        }
    }

    private static List<?> foodStoreFoods(Object result) {
        try {
            java.lang.reflect.Field field = result.getClass().getDeclaredField("foods");
            field.setAccessible(true);
            return (List<?>) field.get(result);
        } catch (Exception error) {
            throw new AssertionError("failed to read food store result foods: " + error.getMessage());
        }
    }

    private static boolean isKnownExpiryStatus(String status) {
        return DateRules.STATUS_EXPIRED.equals(status)
                || DateRules.STATUS_TODAY.equals(status)
                || DateRules.STATUS_SOON.equals(status)
                || DateRules.STATUS_NORMAL.equals(status)
                || DateRules.STATUS_UNKNOWN.equals(status);
    }

    private static void assertEventsSorted(List<ReminderEvent> events) {
        for (int index = 1; index < events.size(); index++) {
            String previous = events.get(index - 1).reminderDate;
            String current = events.get(index).reminderDate;
            assertTrue(previous.compareTo(current) <= 0, "events should be sorted by reminderDate");
        }
    }

    private static int nonPostExpiryEventCount(List<ReminderEvent> events) {
        int count = 0;
        for (ReminderEvent event : events) {
            if (!event.postExpiry) {
                count++;
            }
        }
        return count;
    }

    private static void assertListContains(List<Integer> values, Integer expected) {
        assertTrue(values.contains(expected), "expected list to contain " + expected + ", got " + values);
    }

    private static void assertAllOffsetsAtMost(List<Integer> values, int maximum) {
        for (Integer value : values) {
            assertTrue(value != null && value.intValue() >= 0 && value.intValue() <= maximum,
                    "expected offsets between 0 and " + maximum + ", got " + values);
        }
    }

    private static Class<?> optionalClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Map<String, String> unzipUtf8Entries(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<String, String>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                entries.put(entry.getName(), new String(output.toByteArray(), StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return entries;
    }

    private static FoodExcelImporter.ImportPreview readWorkbookPreview(List<FoodItem> foods) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            FoodExcelExporter.writeWorkbook(output, foods);
            return FoodExcelImporter.readWorkbook(new ByteArrayInputStream(output.toByteArray()));
        } catch (Exception error) {
            throw new AssertionError("failed to read workbook preview: " + error.getMessage());
        }
    }

    private static FoodExcelImporter.ImportPreview readNumericDateWorkbookPreview(FoodItem food) {
        return readNumericDateWorkbookPreview(food, false);
    }

    private static FoodExcelImporter.ImportPreview readNumericDateWorkbookPreview(FoodItem food, boolean uses1904DateSystem) {
        try {
            ByteArrayOutputStream exported = new ByteArrayOutputStream();
            FoodExcelExporter.writeWorkbook(exported, Arrays.asList(food));
            Map<String, byte[]> entries = unzipByteEntries(exported.toByteArray());

            String sheet = new String(entries.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
            sheet = replaceInlineCellWithNumber(sheet, "E2", "2026-07-14", uses1904DateSystem ? "44755" : "46217");
            sheet = replaceInlineCellWithNumber(sheet, "I2", "2026-07-15", uses1904DateSystem ? "44756" : "46218");
            sheet = replaceInlineCellWithNumber(sheet, "L2", "2026-07-21", uses1904DateSystem ? "44762" : "46224");
            entries.put("xl/worksheets/sheet1.xml", sheet.getBytes(StandardCharsets.UTF_8));

            if (uses1904DateSystem) {
                String workbook = new String(entries.get("xl/workbook.xml"), StandardCharsets.UTF_8);
                workbook = workbook.replace("<sheets>", "<workbookPr date1904=\"1\"/><sheets>");
                entries.put("xl/workbook.xml", workbook.getBytes(StandardCharsets.UTF_8));
            }

            String styles = new String(entries.get("xl/styles.xml"), StandardCharsets.UTF_8);
            styles = styles.replace(
                    "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>",
                    "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                            + "<xf numFmtId=\"14\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>"
            );
            entries.put("xl/styles.xml", styles.getBytes(StandardCharsets.UTF_8));

            ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
            ZipOutputStream zip = new ZipOutputStream(rewritten);
            try {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            } finally {
                zip.close();
            }
            return FoodExcelImporter.readWorkbook(new ByteArrayInputStream(rewritten.toByteArray()));
        } catch (Exception error) {
            throw new AssertionError("failed to read numeric date workbook: " + error.getMessage());
        }
    }

    private static Map<String, byte[]> unzipByteEntries(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<String, byte[]>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                entries.put(entry.getName(), output.toByteArray());
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return entries;
    }

    private static String replaceInlineCellWithNumber(String sheet, String reference, String text, String serial) {
        return sheet.replace(
                "<c r=\"" + reference + "\" t=\"inlineStr\"><is><t>" + text + "</t></is></c>",
                "<c r=\"" + reference + "\" s=\"1\"><v>" + serial + "</v></c>"
        );
    }

    private static Object newBarcodeHistoryItem(
            Class<?> itemClass,
            String barcode,
            String name,
            String category,
            String unit,
            String notes,
            String updatedAt
    ) {
        try {
            java.lang.reflect.Constructor<?> constructor = itemClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object item = constructor.newInstance();
            setFieldText(item, "barcode", barcode);
            setFieldText(item, "name", name);
            setFieldText(item, "category", category);
            setFieldText(item, "unit", unit);
            setFieldText(item, "notes", notes);
            setFieldText(item, "updatedAt", updatedAt);
            return item;
        } catch (Exception error) {
            throw new AssertionError("failed to create BarcodeHistoryItem: " + error.getMessage());
        }
    }

    private static String serializeBarcodeHistory(Class<?> itemClass, List<Object> items) {
        try {
            java.lang.reflect.Method method = itemClass.getDeclaredMethod("serializeList", List.class);
            method.setAccessible(true);
            return (String) method.invoke(null, items);
        } catch (Exception error) {
            throw new AssertionError("failed to serialize barcode history: " + error.getMessage());
        }
    }

    private static List<?> parseBarcodeHistory(Class<?> itemClass, String raw) {
        try {
            java.lang.reflect.Method method = itemClass.getDeclaredMethod("parseListOrEmpty", String.class);
            method.setAccessible(true);
            return (List<?>) method.invoke(null, raw);
        } catch (Exception error) {
            throw new AssertionError("failed to parse barcode history: " + error.getMessage());
        }
    }

    private static List<?> upsertBarcodeHistory(
            Class<?> itemClass,
            List<Object> current,
            Object draft,
            String updatedAt,
            int maxItems
    ) {
        try {
            java.lang.reflect.Method method = itemClass.getDeclaredMethod(
                    "upsertConfirmedTemplate",
                    List.class,
                    itemClass,
                    String.class,
                    int.class
            );
            method.setAccessible(true);
            return (List<?>) method.invoke(null, current, draft, updatedAt, Integer.valueOf(maxItems));
        } catch (Exception error) {
            throw new AssertionError("failed to upsert barcode history: " + error.getMessage());
        }
    }

    private static void setFieldText(Object target, String fieldName, String value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception error) {
            throw new AssertionError("failed to set field " + fieldName + ": " + error.getMessage());
        }
    }

    private static boolean booleanField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(target);
        } catch (Exception error) {
            throw new AssertionError("failed to read boolean field " + fieldName + ": " + error.getMessage());
        }
    }

    private static String fieldText(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception error) {
            throw new AssertionError("failed to read field " + fieldName + ": " + error.getMessage());
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but got <" + actual + ">");
        }
    }

    private static void assertNear(double expected, double actual) {
        if (Double.isNaN(actual) || Math.abs(expected - actual) > 0.000000001d) {
            throw new AssertionError("expected approximately <" + expected + "> but got <" + actual + ">");
        }
    }

    private interface TestCase {
        void run();
    }
}
