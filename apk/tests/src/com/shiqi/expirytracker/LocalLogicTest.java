package com.shiqi.expirytracker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
                assertListContains(plan.offsets, Integer.valueOf(5));
                assertListContains(plan.offsets, Integer.valueOf(3));
                assertListContains(plan.offsets, Integer.valueOf(1));
                assertListContains(plan.offsets, Integer.valueOf(0));
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
                assertEquals(Arrays.asList(Integer.valueOf(30), Integer.valueOf(7), Integer.valueOf(0)), plan.offsets);
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
                FoodItem upcoming = food("Upcoming", "staple", "room_temp", DateRules.addDaysString(today, -7), DateRules.addDaysString(today, 3), 1);

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

        runFoodStoreMigrationTestsIfAvailable();
        runBarcodeHistoryTestsIfAvailable();
        runFoodExcelExporterTests();
        runFoodExcelImporterTests();
        runDateOcrParserTests();
        runDateOcrFrameVoterTests();

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

        test("DateOcrParser marks unhinted dates as weak conflicting candidates", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse("画面里看到 20260705 和 20260706");

                assertEquals(2, result.productionDates.size());
                assertEquals(2, result.expiryDates.size());
                assertTrue(result.productionDates.get(0).weakHint, "unhinted production date should be weak");
                assertTrue(result.expiryDates.get(0).weakHint, "unhinted expiry date should be weak");
                assertTrue(result.hasDateConflict(), "different dates should be a conflict");
            }
        });

        test("DateOcrParser ignores barcodes zero shelf life and invalid dates", new TestCase() {
            public void run() {
                DateOcrParser.Result result = DateOcrParser.parse("条码 6936832557442 保质期0天 日期 2026-02-30");

                assertFalse(result.hasAnyCandidate(), "barcode-like and invalid data should not create candidates");
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

        test("DateOcrFrameVoter blocks confirmation when repeated dates conflict", new TestCase() {
            public void run() {
                List<DateOcrParser.Result> frames = new ArrayList<DateOcrParser.Result>();
                frames.add(DateOcrParser.parse("画面里看到 20260705 和 20260706"));
                frames.add(DateOcrParser.parse("画面里看到 20260705 和 20260706"));

                DateOcrFrameVoter.VoteResult result = DateOcrFrameVoter.vote(frames);

                assertTrue(result.hasConflict, "repeated conflicting candidates should be flagged");
                assertFalse(result.readyForUserConfirmation(), "conflict must require manual confirmation");
                assertEquals(null, result.productionDate);
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

            if (index % 53 == 0) {
                item.isFinished = true;
                item.finishedAt = today;
            }

            foods.add(item);
        }
        return foods;
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

    private interface TestCase {
        void run();
    }
}
