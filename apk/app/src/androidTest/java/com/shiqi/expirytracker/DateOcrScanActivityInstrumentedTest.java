package com.shiqi.expirytracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public final class DateOcrScanActivityInstrumentedTest {
    @Test
    public void dateOnlyEntryShowsOnePurposeAndManualFallback() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, DateOcrScanActivity.class);
        intent.putExtra(DateOcrScanActivity.EXTRA_DATE_ONLY_MODE, true);
        intent.putExtra(
                DateOcrScanActivity.EXTRA_QA_IMAGE_PATH,
                new File(context.getCacheDir(), "missing-date-image.jpg").getAbsolutePath()
        );

        ActivityScenario<DateOcrScanActivity> scenario = ActivityScenario.launch(intent);
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<DateOcrScanActivity>() {
                @Override
                public void perform(DateOcrScanActivity activity) {
                    View root = activity.getWindow().getDecorView();
                    assertNotNull(findText(root, "日期"));
                    assertNotNull(findText(root, "返回手动填写"));
                    assertNotVisible(root, "自动");
                    assertNotVisible(root, "日期特写");
                    assertNotVisible(root, "条码");
                    assertNotVisible(root, "名称");
                    assertNotVisible(root, "优先识别日期");
                }
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void partialRecognitionNameIsEditableAndMissingDateCanContinue() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, DateOcrScanActivity.class);
        intent.putExtra(
                DateOcrScanActivity.EXTRA_QA_IMAGE_PATH,
                new File(context.getCacheDir(), "missing-recognition-image.jpg").getAbsolutePath()
        );

        ActivityScenario<DateOcrScanActivity> scenario = ActivityScenario.launch(intent);
        try {
            scenario.onActivity(new ActivityScenario.ActivityAction<DateOcrScanActivity>() {
                @Override
                public void perform(DateOcrScanActivity activity) {
                    setSelectedProductName(activity, "维C");
                    TextView skip = findText(
                            activity.getWindow().getDecorView(),
                            "跳过识别，手动填写"
                    );
                    assertNotNull(skip);
                    click(skip);

                    View root = activity.getWindow().getDecorView();
                    assertNotNull(findText(root, "核对结果"));
                    assertNotNull(findText(root, "尚未找到清楚日期"));
                    TextView continueButton = findText(root, "继续填写");
                    assertNotNull(continueButton);
                    assertTrue(continueButton.isEnabled());

                    TextView productName = findText(root, "维C");
                    assertNotNull(productName);
                    click(productName);
                }
            });

            scenario.onActivity(new ActivityScenario.ActivityAction<DateOcrScanActivity>() {
                @Override
                public void perform(DateOcrScanActivity activity) {
                    AlertDialog dialog = getProductNameDialog(activity);
                    assertNotNull(dialog);
                    assertTrue(dialog.isShowing());
                    View dialogRoot = dialog.getWindow().getDecorView();
                    assertNotNull(findText(dialogRoot, "修改商品名称"));

                    EditText editor = findEditText(dialogRoot);
                    assertNotNull(editor);
                    assertEquals("维C", editor.getText().toString());
                    editor.setText("维C水");
                    View save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    assertNotNull(save);
                    save.performClick();
                }
            });

            scenario.onActivity(new ActivityScenario.ActivityAction<DateOcrScanActivity>() {
                @Override
                public void perform(DateOcrScanActivity activity) {
                    View root = activity.getWindow().getDecorView();
                    assertEquals("维C水", getSelectedProductName(activity));
                    assertNotNull(findText(root, "维C水"));
                    assertTrue(findText(root, "继续填写").isEnabled());
                }
            });
        } finally {
            scenario.close();
        }
    }

    private static String getSelectedProductName(DateOcrScanActivity activity) {
        try {
            Field field = DateOcrScanActivity.class.getDeclaredField("selectedPackagingName");
            field.setAccessible(true);
            return (String) field.get(activity);
        } catch (Exception error) {
            throw new AssertionError("Could not inspect edited product name.", error);
        }
    }

    private static AlertDialog getProductNameDialog(DateOcrScanActivity activity) {
        try {
            Field field = DateOcrScanActivity.class.getDeclaredField("productNameEditorDialog");
            field.setAccessible(true);
            return (AlertDialog) field.get(activity);
        } catch (Exception error) {
            throw new AssertionError("Could not inspect product name dialog.", error);
        }
    }

    private static void setSelectedProductName(
            DateOcrScanActivity activity,
            String value
    ) {
        try {
            Field field = DateOcrScanActivity.class.getDeclaredField("selectedPackagingName");
            field.setAccessible(true);
            field.set(activity, value);
        } catch (Exception error) {
            throw new AssertionError("Could not seed partial product name.", error);
        }
    }

    private static void click(View view) {
        View clickable = view;
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

    private static void assertNotVisible(View root, String value) {
        TextView found = findText(root, value);
        assertTrue(found == null || !found.isShown());
    }

    private static EditText findEditText(View root) {
        if (root instanceof EditText) {
            return (EditText) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                EditText found = findEditText(group.getChildAt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
