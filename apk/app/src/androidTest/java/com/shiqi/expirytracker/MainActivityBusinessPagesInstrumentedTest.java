package com.shiqi.expirytracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
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
import java.lang.reflect.Method;
import java.util.Arrays;

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
    }

    @After
    public void tearDown() {
        RoomInventoryStore.resetForTests(context);
        preferences.edit().clear().commit();
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
                    assertNotNull(findText(activity.getWindow().getDecorView(), "总览"));
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
}