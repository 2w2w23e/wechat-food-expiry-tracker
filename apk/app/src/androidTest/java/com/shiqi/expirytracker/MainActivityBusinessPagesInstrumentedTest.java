package com.shiqi.expirytracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class MainActivityBusinessPagesInstrumentedTest {
    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        RoomInventoryStore.resetForTests(context);
        preferences = context.getSharedPreferences(
                FoodStore.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        assertTrue(preferences.edit().clear().commit());
        RecognitionDebugStore.resetForTests(context);
        RoomMigrationBackup.deleteBackupsForTests(context);
    }

    @After
    public void tearDown() {
        RoomInventoryStore.resetForTests(context);
        preferences.edit().clear().commit();
        RecognitionDebugStore.resetForTests(context);
        RoomMigrationBackup.deleteBackupsForTests(context);
    }

    @Test
    public void multiProductBarcodeRequiresExplicitSelectionBeforeReplenishment()
            throws Exception {
        FoodItem first = food("batch-a", "profile-a", "原味酸奶");
        FoodItem second = food("batch-b", "profile-b", "草莓酸奶");
        FoodStore store = new FoodStore(context);
        assertTrue(store.loadFoods().isEmpty());
        assertTrue(store.saveFoods(Arrays.asList(first, second)));

        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    invokeHandleBarcode(activity, "4006381333931");

                    View root = activity.getWindow().getDecorView();
                    assertNotNull(findText(root, "请选择要补货的商品"));
                    TextView replenish = findText(root, "补货新批次");
                    assertNotNull(replenish);
                    assertFalse(replenish.isEnabled());
                }
            });
            captureScreenshot("d1d-barcode-multi-unselected-1080x2400.png");

            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    View root = activity.getWindow().getDecorView();
                    TextView replenish = findText(root, "补货新批次");
                    RadioButton secondChoice = (RadioButton) findTextStartingWith(
                            root,
                            "草莓酸奶"
                    );
                    assertNotNull(replenish);
                    assertNotNull(secondChoice);
                    secondChoice.performClick();
                    assertTrue(replenish.isEnabled());
                }
            });
            captureScreenshot("d1d-barcode-multi-selected-1080x2400.png");

            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    activity.onBackPressed();
                    assertNotNull(findText(activity.getWindow().getDecorView(), "今天要处理"));
                }
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void verifiedOfflineBarcodeFillsEditorOnlyAfterUserConfirms()
            throws Exception {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    invokeHandleBarcode(activity, "6926265313430");
                }
            });
            assertTrue(waitForText(scenario, "查询到商品信息", 3000L));
            captureScreenshot("d1d-barcode-verified-online-1080x2400.png");

            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    clickText(activity, "填入新增表单");
                    View root = activity.getWindow().getDecorView();
                    assertNotNull(findText(root, "新增商品"));
                    assertNotNull(findText(root, "6926265313430"));
                }
            });
        } finally {
            scenario.close();
        }
    }
    @Test
    public void settingsBusinessPagesReturnToTheirParentWithoutLosingNavigation() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    clickText(activity, "设置");
                    assertNotNull(findText(activity.getWindow().getDecorView(), "本地数据"));

                    clickText(activity, "提醒方式");
                    assertNotNull(findText(activity.getWindow().getDecorView(), "提醒设置"));
                    activity.onBackPressed();
                    assertNotNull(findText(activity.getWindow().getDecorView(), "本地数据"));

                    clickText(activity, "JSON 快速导入导出");
                    assertNotNull(findText(activity.getWindow().getDecorView(), "同步操作"));
                    clickText(activity, "筛选导出与分享");
                    assertNotNull(findText(activity.getWindow().getDecorView(), "导出范围"));
                    activity.onBackPressed();
                    assertNotNull(findText(activity.getWindow().getDecorView(), "同步操作"));
                    activity.onBackPressed();
                    assertNotNull(findText(activity.getWindow().getDecorView(), "本地数据"));

                    clickText(activity, "Excel 批量管理");
                    assertNotNull(findText(activity.getWindow().getDecorView(), "批量处理"));
                    activity.onBackPressed();
                    assertNotNull(findText(activity.getWindow().getDecorView(), "本地数据"));
                }
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void incompleteRecognitionStillOpensManualEditor() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    try {
                        Method method = MainActivity.class.getDeclaredMethod(
                                "handleUnifiedRecognitionResult",
                                Intent.class
                        );
                        method.setAccessible(true);
                        method.invoke(activity, new Intent());
                    } catch (Exception error) {
                        throw new AssertionError("Could not invoke partial recognition flow.", error);
                    }
                    View root = activity.getWindow().getDecorView();
                    assertNotNull(findText(root, "新增商品"));
                    assertNotNull(findText(root, "商品"));
                    assertNotNull(findText(root, "日期"));
                    assertNotNull(findText(root, "暂时不知道完整日期，稍后补充"));
                }
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void manualEditorShowsAllBlockingErrorsTogether() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    clickText(activity, "新增");
                    clickText(activity, "确认新增");

                    View root = activity.getWindow().getDecorView();
                    EditText name = findEditTextWithHint(root, "商品名称");
                    EditText productionDate = findEditTextWithHint(root, "选择生产日期");
                    EditText shelfLife = findEditTextWithHint(root, "保质期数值");
                    assertNotNull(name);
                    assertNotNull(productionDate);
                    assertNotNull(shelfLife);
                    assertNotNull(name.getError());
                    assertNotNull(productionDate.getError());
                    assertNotNull(shelfLife.getError());
                }
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void manualEditorCanSaveUnknownDateWithoutMarkingNoExpiry() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    clickText(activity, "新增");
                    View root = activity.getWindow().getDecorView();
                    EditText name = findEditTextWithHint(root, "商品名称");
                    assertNotNull(name);
                    name.setText("日期待补充测试商品");
                    clickText(activity, "暂时不知道完整日期，稍后补充");
                    clickText(activity, "确认新增");
                }
            });

            List<FoodItem> saved = new FoodStore(context).loadFoods();
            assertEquals(1, saved.size());
            assertEquals("unknown", saved.get(0).dateSource);
            assertEquals("", saved.get(0).expiryDate);
            assertFalse("none".equals(saved.get(0).dateSource));
        } finally {
            scenario.close();
        }
    }

    @Test
    public void manualEditorDraftSurvivesActivityRecreation() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    clickText(activity, "新增");
                    View root = activity.getWindow().getDecorView();
                    EditText name = findEditTextWithHint(root, "商品名称");
                    EditText barcode = findEditTextWithHint(root, "商品条码（可选）");
                    assertNotNull(name);
                    assertNotNull(barcode);
                    name.setText("不会丢失的草莓酸奶");
                    barcode.setText("6901234567892");
                    clickText(activity, "暂时不知道完整日期，稍后补充");
                }
            });

            scenario.recreate();
            assertTrue(waitForStartup(scenario, 5000L));
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    View root = activity.getWindow().getDecorView();
                    assertNotNull(findText(root, "新增商品"));
                    EditText name = findEditTextWithHint(root, "商品名称");
                    EditText barcode = findEditTextWithHint(root, "商品条码（可选）");
                    assertNotNull(name);
                    assertNotNull(barcode);
                    assertEquals("不会丢失的草莓酸奶", name.getText().toString());
                    assertEquals("6901234567892", barcode.getText().toString());
                    TextView pending = findText(root, "暂时不知道完整日期，稍后补充");
                    assertNotNull(pending);
                    assertTrue(((android.widget.CheckBox) pending).isChecked());
                }
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void manualInventoryJourneyAddsEditsDecrementsAndDeletes() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    clickText(activity, "新增");
                    EditText name = findEditTextWithHint(
                            activity.getWindow().getDecorView(),
                            "商品名称"
                    );
                    assertNotNull(name);
                    name.setText("全流程测试商品");
                    clickText(activity, "暂时不知道完整日期，稍后补充");
                    clickText(activity, "确认新增");

                    clickText(activity, "全流程测试商品");
                    clickText(activity, "编辑");
                    EditText editedName = findEditTextWithHint(
                            activity.getWindow().getDecorView(),
                            "商品名称"
                    );
                    assertNotNull(editedName);
                    editedName.setText("全流程测试商品-已修改");
                    clickText(activity, "保存修改");
                    assertNotNull(findText(
                            activity.getWindow().getDecorView(),
                            "全流程测试商品-已修改"
                    ));

                    clickContentDescription(
                            activity,
                            "全流程测试商品-已修改当前批次减少 1"
                    );
                    List<FoodItem> afterDecrease = new FoodStore(context).loadFoods();
                    assertEquals(1, afterDecrease.size());
                    assertEquals(0d, afterDecrease.get(0).remainingQuantity, 0d);
                    assertTrue(afterDecrease.get(0).isFinished);

                    invokeDeleteFood(activity, afterDecrease.get(0));
                    assertTrue(new FoodStore(context).loadFoods().isEmpty());
                }
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void recognitionSettingsExposeUserControlledDebugCapture() throws Exception {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    clickText(activity, "设置");
                    clickText(activity, "识别状态");
                    View root = activity.getWindow().getDecorView();
                    assertNotNull(findText(root, "开启识别调试模式"));
                    assertNotNull(findText(root, "分享调试日志"));

                    clickText(activity, "开启识别调试模式");
                    assertTrue(RecognitionDebugStore.isEnabled(context));
                    assertNotNull(findText(activity.getWindow().getDecorView(), "记录中"));
                }
            });
            captureScreenshot("d2-recognition-debug-enabled-1080x2400.png");
        } finally {
            scenario.close();
        }
    }
    @Test
    public void backupPageShowsOnlyVerifiedCandidateAndKeepsRestoreExplicit()
            throws Exception {
        FoodItem item = food("backup-batch", "backup-profile", "备份商品");
        String raw = FoodStoreMigration.serialize(
                Arrays.asList(item),
                FoodStore.CURRENT_SCHEMA_VERSION
        );
        RoomMigrationBackup.ensureBackup(
                context,
                raw,
                FoodStore.CURRENT_SCHEMA_VERSION
        );

        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(scenario, 5000L));
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    clickText(activity, "设置");
                    clickText(activity, "备份与恢复");

                    View root = activity.getWindow().getDecorView();
                    assertNotNull(findText(root, "可恢复备份"));
                    TextView restore = findText(root, "恢复所选备份");
                    assertNotNull(restore);
                    assertTrue(restore.isEnabled());
                    assertNotNull(findTextStartingWith(root, "2026-"));

                    activity.onBackPressed();
                    assertNotNull(findText(activity.getWindow().getDecorView(), "本地数据"));
                }
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void importSaveCreatesBackupAndFailedOverwriteKeepsCurrentInventory()
            throws Exception {
        FoodItem original = food("original-batch", "profile-import", "\u539f\u5e93\u5b58\u5546\u54c1");
        assertTrue(new FoodStore(context).saveFoods(Arrays.asList(original)));

        FoodItem replacement = food("replacement-batch", "profile-import", "\u8986\u76d6\u540e\u5546\u54c1");
        ActivityScenario<MainActivity> firstScenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(firstScenario, 5000L));
        try {
            assertTrue(invokeSaveFoodsForImport(
                    firstScenario,
                    Arrays.asList(replacement),
                    false
            ));
        } finally {
            firstScenario.close();
        }

        List<FoodItem> afterSuccess = new FoodStore(context).loadFoods();
        assertEquals(1, afterSuccess.size());
        assertEquals("\u8986\u76d6\u540e\u5546\u54c1", afterSuccess.get(0).name);
        RoomMigrationBackup.BackupCatalog firstCatalog =
                RoomMigrationBackup.verifiedCatalog(
                        context,
                        FoodStore.CURRENT_SCHEMA_VERSION
                );
        assertTrue(containsFoodName(firstCatalog, "\u539f\u5e93\u5b58\u5546\u54c1"));

        FoodItem rejected = food("rejected-batch", "profile-import", "\u4e0d\u5e94\u5199\u5165\u5546\u54c1");
        ActivityScenario<MainActivity> secondScenario = ActivityScenario.launch(MainActivity.class);
        assertTrue(waitForStartup(secondScenario, 5000L));
        try {
            assertFalse(invokeSaveFoodsForImport(
                    secondScenario,
                    Arrays.asList(rejected),
                    true
            ));
        } finally {
            secondScenario.close();
        }

        List<FoodItem> afterFailure = new FoodStore(context).loadFoods();
        assertEquals(1, afterFailure.size());
        assertEquals("\u8986\u76d6\u540e\u5546\u54c1", afterFailure.get(0).name);
        RoomMigrationBackup.BackupCatalog finalCatalog =
                RoomMigrationBackup.verifiedCatalog(
                        context,
                        FoodStore.CURRENT_SCHEMA_VERSION
                );
        assertTrue(containsFoodName(finalCatalog, "\u8986\u76d6\u540e\u5546\u54c1"));
    }

    private boolean waitForStartup(
            final ActivityScenario<MainActivity> scenario,
            long timeoutMs
    ) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] complete = new boolean[] { false };
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    complete[0] = activity.startupCompleteForTests();
                }
            });
            if (complete[0]) {
                return true;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean invokeSaveFoodsForImport(
            final ActivityScenario<MainActivity> scenario,
            final List<FoodItem> foods,
            final boolean forceFailure
    ) {
        final boolean[] result = new boolean[] { false };
        scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
            @Override
            public void perform(MainActivity activity) {
                try {
                    if (forceFailure) {
                        Field field = MainActivity.class.getDeclaredField(
                                "qaForceNextImportSaveFailure"
                        );
                        field.setAccessible(true);
                        field.setBoolean(activity, true);
                    }
                    Method method = MainActivity.class.getDeclaredMethod(
                            "saveFoodsForImport",
                            List.class
                    );
                    method.setAccessible(true);
                    result[0] = ((Boolean) method.invoke(
                            activity,
                            new ArrayList<FoodItem>(foods)
                    )).booleanValue();
                } catch (Exception error) {
                    throw new AssertionError("Could not invoke import save flow.", error);
                }
            }
        });
        return result[0];
    }

    private boolean containsFoodName(
            RoomMigrationBackup.BackupCatalog catalog,
            String name
    ) {
        for (RoomMigrationBackup.BackupCandidate candidate : catalog.candidates) {
            for (FoodItem food : candidate.foods()) {
                if (name.equals(food.name)) {
                    return true;
                }
            }
        }
        return false;
    }
    private boolean waitForText(
            final ActivityScenario<MainActivity> scenario,
            final String value,
            long timeoutMs
    ) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] found = new boolean[] { false };
            scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
                @Override
                public void perform(MainActivity activity) {
                    found[0] = findText(activity.getWindow().getDecorView(), value) != null;
                }
            });
            if (found[0]) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }
    private void captureScreenshot(String fileName) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(500L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .takeScreenshot();
        assertNotNull(bitmap);

        context.getContentResolver().delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.DISPLAY_NAME + " = ? AND "
                        + MediaStore.Images.Media.RELATIVE_PATH + " = ?",
                new String[] { fileName, "Pictures/ShiqiQA/" }
        );

        ContentValues values = new ContentValues();
        String uniqueName = fileName.replace(".png", "")
                + "-" + System.currentTimeMillis() + ".png";
        values.put(MediaStore.Images.Media.DISPLAY_NAME, uniqueName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ShiqiQA");
        values.put(MediaStore.Images.Media.IS_PENDING, Integer.valueOf(1));
        Uri uri = context.getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        );
        assertNotNull(uri);
        OutputStream output = context.getContentResolver().openOutputStream(uri);
        assertNotNull(output);
        try {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            output.flush();
        } finally {
            output.close();
            bitmap.recycle();
        }
        ContentValues published = new ContentValues();
        published.put(MediaStore.Images.Media.IS_PENDING, Integer.valueOf(0));
        context.getContentResolver().update(uri, published, null, null);
    }
    private FoodItem food(String id, String profileId, String name) {
        FoodItem item = new FoodItem();
        item.id = id;
        item.productProfileId = profileId;
        item.barcode = "4006381333931";
        item.name = name;
        item.category = "dairy";
        item.productionDate = "2026-07-01";
        item.shelfLifeValue = Integer.valueOf(31);
        item.shelfLifeUnit = "day";
        item.expiryDate = "2026-08-01";
        item.dateSource = "manual";
        item.quantity = 1;
        item.remainingQuantity = 1;
        item.unit = "盒";
        item.storageMethod = "refrigerated";
        item.location = "fridge";
        item.createdAt = "2026-07-01T08:00:00+0800";
        item.updatedAt = "2026-07-01T08:00:00+0800";
        return item;
    }

    private static void invokeHandleBarcode(MainActivity activity, String barcode) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(
                    "handleBarcodeValue",
                    String.class
            );
            method.setAccessible(true);
            method.invoke(activity, barcode);
        } catch (Exception error) {
            throw new AssertionError("Could not invoke barcode result flow.", error);
        }
    }

    private static void invokeDeleteFood(MainActivity activity, FoodItem food) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(
                    "deleteFood",
                    FoodItem.class
            );
            method.setAccessible(true);
            method.invoke(activity, food);
        } catch (Exception error) {
            throw new AssertionError("Could not invoke confirmed delete flow.", error);
        }
    }

    private static void clickText(MainActivity activity, String value) {
        TextView text = findText(activity.getWindow().getDecorView(), value);
        assertNotNull(text);
        View clickable = text;
        while (clickable != null && !clickable.isClickable()) {
            Object parent = clickable.getParent();
            clickable = parent instanceof View ? (View) parent : null;
        }
        assertNotNull(clickable);
        clickable.performClick();
    }

    private static void clickContentDescription(MainActivity activity, String value) {
        View view = findContentDescription(activity.getWindow().getDecorView(), value);
        assertNotNull(view);
        view.performClick();
    }

    private static TextView findText(View root, String value) {
        if (root instanceof TextView
                && value.contentEquals(((TextView) root).getText())) {
            return (TextView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findText(group.getChildAt(index), value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static TextView findTextStartingWith(View root, String prefix) {
        if (root instanceof TextView
                && ((TextView) root).getText().toString().startsWith(prefix)) {
            return (TextView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findTextStartingWith(group.getChildAt(index), prefix);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static View findContentDescription(View root, String value) {
        if (root != null
                && root.getContentDescription() != null
                && value.contentEquals(root.getContentDescription())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findContentDescription(group.getChildAt(index), value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static EditText findEditTextWithHint(View root, String hint) {
        if (root instanceof EditText
                && hint.contentEquals(((EditText) root).getHint())) {
            return (EditText) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                EditText found = findEditTextWithHint(group.getChildAt(index), hint);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
