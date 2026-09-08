package com.shiqi.expirytracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class MainActivityRecoveryInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        resetStorage();
    }

    @After
    public void tearDown() {
        resetStorage();
    }

    private void resetStorage() {
        RoomInventoryStore.resetForTests(context);
        assertTrue(context.getSharedPreferences(FoodStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit());
        RoomMigrationBackup.deleteBackupsForTests(context);
    }

    @Test
    public void successfulDeleteInvalidatesUndoAndDetachedButtonCannotResurrect() {
        seedFood();
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                decrement(activity);
                View staleUndo = undoButton(activity);
                call(activity, "deleteFood", new Class<?>[] { FoodItem.class }, storedFood());
                assertNull(get(activity, "pendingUndoFood"));
                assertNull(get(activity, "pendingUndoExpected"));
                staleUndo.performClick();
                call(activity, "undoPendingDecrement", new Class<?>[0]);
                assertTrue(new FoodStore(context).loadFoods().isEmpty());
                assertTrue(currentFoods(activity).isEmpty());
            });
        }
    }

    @Test
    public void successfulEditInvalidatesUndoAndKeepsNewerFields() {
        seedFood();
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                decrement(activity);
                View staleUndo = undoButton(activity);
                FoodItem edited = storedFood();
                edited.notes = "newer saved notes";
                InventoryCommandService commands = (InventoryCommandService) call(
                        activity, "inventoryCommandService", new Class<?>[0]);
                assertTrue(commands.edit(edited.id, edited).isSuccess());
                assertNull(get(activity, "pendingUndoFood"));
                staleUndo.performClick();
                assertEquals("newer saved notes", storedFood().notes);
                assertEquals(2d, storedFood().remainingQuantity, 0d);
            });
        }
    }

    @Test
    public void snapshotGuardRejectsNewerDataWithoutRelyingOnInvalidation() {
        seedFood();
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                decrement(activity);
                FoodItem changed = storedFood();
                // Keep quantity and timestamp unchanged: the entire snapshot must match.
                changed.notes = "out of band edit";
                assertTrue(new FoodStore(context).saveFoods(Arrays.asList(changed)));
                set(activity, "foods", new ArrayList<FoodItem>(Arrays.asList(changed)));
                assertNotNull(get(activity, "pendingUndoFood"));
                undoButton(activity).performClick();
                assertEquals("out of band edit", storedFood().notes);
                assertEquals(2d, storedFood().remainingQuantity, 0d);
            });
        }
    }

    @Test
    public void snapshotGuardNeverAddsAMissingBatch() {
        seedFood();
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                decrement(activity);
                assertTrue(new FoodStore(context).saveFoods(new ArrayList<FoodItem>()));
                currentFoods(activity).clear();
                assertNotNull(get(activity, "pendingUndoFood"));
                undoButton(activity).performClick();
                assertTrue(currentFoods(activity).isEmpty());
                assertTrue(new FoodStore(context).loadFoods().isEmpty());
            });
        }
    }

    @Test
    public void failedLaterWriteKeepsUndoAndValidUndoRestoresQuantity() {
        seedFood();
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                decrement(activity);
                Object token = get(activity, "pendingUndoFood");
                set(activity, "qaForceNextSaveFailure", true);
                call(activity, "deleteFood", new Class<?>[] { FoodItem.class }, storedFood());
                assertSame(token, get(activity, "pendingUndoFood"));
                assertEquals(2d, storedFood().remainingQuantity, 0d);
                undoButton(activity).performClick();
                assertEquals(3d, storedFood().remainingQuantity, 0d);
                assertFalse(storedFood().isFinished);
            });
        }
    }

    @Test
    public void dateRequestSurvivesRecreationAndRebindsToRestoredDraft() throws Exception {
        final Object[] oldTarget = new Object[1];
        final String[] draftId = new String[1];
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                Object editor = startDraftRequest(activity);
                oldTarget[0] = get(activity, "pendingReplenishmentRecognitionTarget");
                draftId[0] = (String) get(editor, "draftId");
            });
            scenario.recreate();
            awaitEditor(scenario);
            scenario.onActivity(activity -> {
                Object editor = get(activity, "activeFoodEditor");
                assertEquals(draftId[0], get(editor, "draftId"));
                assertNotSame(oldTarget[0], get(activity, "pendingReplenishmentRecognitionTarget"));
                assertSame(get(editor, "recognitionTarget"),
                        get(activity, "pendingReplenishmentRecognitionTarget"));
                deliver(activity, Activity.RESULT_OK, recognizedDate());
                assertRecognizedDraftOnly(activity);
            });
        }
    }

    @Test
    public void earlyResultIsBufferedAndSurvivesAnotherRecreationBeforeEditorRestore()
            throws Exception {
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                Object editor = startDraftRequest(activity);
                deferEditorRestoration(activity, editor);
                deliver(activity, Activity.RESULT_OK, recognizedDate());
                assertNotNull(get(activity, "pendingDateRecognitionResult"));
                assertNull(get(activity, "activeFoodEditor"));
                assertTrue(new FoodStore(context).loadFoods().isEmpty());
                Bundle state = new Bundle();
                activity.onSaveInstanceState(state);
                assertNotNull(state.getBundle("main_food_editor_v2"));
                assertNotNull(state.getParcelable("main_date_result_v2"));
            });
            // Exercise actual savedInstanceState + onCreate, not only a manual rebind.
            scenario.recreate();
            awaitEditor(scenario);
            scenario.onActivity(this::assertRecognizedDraftOnly);
        }
    }

    @Test
    public void cancellationBeforeRestoreKeepsDraftAndDoesNotReplayOldResult() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                Object editor = startDraftRequest(activity);
                deferEditorRestoration(activity, editor);
                deliver(activity, Activity.RESULT_OK, recognizedDate());
                deliver(activity, Activity.RESULT_CANCELED, null);
                assertNull(get(activity, "pendingDateRecognitionResult"));
                assertEquals("", get(activity, "pendingDateRecognitionDraftId"));
            });
            scenario.recreate();
            awaitEditor(scenario);
            scenario.onActivity(activity -> {
                Object editor = get(activity, "activeFoodEditor");
                assertEquals("draft to preserve", input(editor, "nameInput").getText().toString());
                assertEquals("", input(editor, "expiryDateInput").getText().toString());
                assertTrue(new FoodStore(context).loadFoods().isEmpty());
            });
        }
    }

    @Test
    public void resultForOldDraftCannotPopulateAnotherDraft() {
        try (ActivityScenario<MainActivity> scenario = launchReady()) {
            scenario.onActivity(activity -> {
                Object oldEditor = startDraftRequest(activity);
                call(activity, "showFoodForm", new Class<?>[] { FoodItem.class }, (Object) null);
                Object newEditor = get(activity, "activeFoodEditor");
                assertFalse(get(oldEditor, "draftId").equals(get(newEditor, "draftId")));
                deliver(activity, Activity.RESULT_OK, recognizedDate());
                assertEquals("", input(newEditor, "expiryDateInput").getText().toString());
                assertEquals("", get(activity, "pendingDateRecognitionDraftId"));
                assertTrue(new FoodStore(context).loadFoods().isEmpty());
            });
        }
    }

    private Object startDraftRequest(MainActivity activity) {
        call(activity, "showFoodForm", new Class<?>[] { FoodItem.class }, (Object) null);
        Object editor = get(activity, "activeFoodEditor");
        input(editor, "nameInput").setText("draft to preserve");
        input(editor, "notesInput").setText("unsaved notes");
        input(editor, "quantityInput").setText("4");
        call(activity, "beginDateRecognition", new Class<?>[] { editor.getClass() }, editor);
        return editor;
    }

    private void deferEditorRestoration(MainActivity activity, Object editor) {
        Bundle state = (Bundle) call(activity, "captureFoodEditorState",
                new Class<?>[] { editor.getClass() }, editor);
        // Deterministically model result delivery while the async startup loader is pending.
        set(activity, "pendingFoodEditorRestore", state);
        set(activity, "activeFoodEditor", null);
        set(activity, "foodEditorVisible", false);
        set(activity, "pendingReplenishmentRecognitionTarget", null);
    }

    private void assertRecognizedDraftOnly(MainActivity activity) {
        Object editor = get(activity, "activeFoodEditor");
        assertNotNull(editor);
        assertEquals("draft to preserve", input(editor, "nameInput").getText().toString());
        assertEquals("unsaved notes", input(editor, "notesInput").getText().toString());
        assertEquals("4", input(editor, "quantityInput").getText().toString());
        assertEquals("2026-09-30", input(editor, "expiryDateInput").getText().toString());
        assertNull(get(activity, "pendingDateRecognitionResult"));
        assertEquals("", get(activity, "pendingDateRecognitionDraftId"));
        assertTrue(new FoodStore(context).loadFoods().isEmpty());
    }

    private static Intent recognizedDate() {
        return new Intent().putExtra(DateOcrResultPayload.EXTRA_EXPIRY_DATE, "2026-09-30")
                .putExtra(DateOcrResultPayload.EXTRA_EXPIRY_CALCULATED, false);
    }

    private static void deliver(MainActivity activity, int resultCode, Intent result) {
        int requestCode = (Integer) get(activity, "REQUEST_REPLENISHMENT_OCR");
        activity.onActivityResult(requestCode, resultCode, result);
    }

    private void seedFood() {
        FoodItem food = new FoodItem();
        food.id = "recovery-batch";
        food.productProfileId = "recovery-product";
        food.name = "Recovery food";
        food.quantity = 3d;
        food.remainingQuantity = 3d;
        food.dateSource = "unknown";
        food.createdAt = DateRules.nowIsoLike();
        food.updatedAt = food.createdAt;
        assertTrue(new FoodStore(context).saveFoods(Arrays.asList(food)));
    }

    private FoodItem storedFood() {
        List<FoodItem> items = new FoodStore(context).loadFoods();
        assertEquals(1, items.size());
        return items.get(0);
    }

    private void decrement(MainActivity activity) {
        call(activity, "decreaseBatchWithUndo", new Class<?>[] { FoodItem.class }, storedFood());
        assertEquals(2d, storedFood().remainingQuantity, 0d);
    }

    private static View undoButton(MainActivity activity) {
        View result = findText(activity.getWindow().getDecorView(), "\u64a4\u9500");
        assertNotNull(result);
        return result;
    }

    private ActivityScenario<MainActivity> launchReady() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] ready = new boolean[1];
            scenario.onActivity(activity -> ready[0] = activity.startupCompleteForTests());
            if (ready[0]) {
                return scenario;
            }
            android.os.SystemClock.sleep(50L);
        }
        scenario.close();
        throw new AssertionError("Startup timed out");
    }

    private static void awaitEditor(ActivityScenario<MainActivity> scenario) throws Exception {
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] ready = new boolean[1];
            scenario.onActivity(activity -> ready[0] = activity.startupCompleteForTests()
                    && get(activity, "activeFoodEditor") != null
                    && get(activity, "pendingFoodEditorRestore") == null);
            if (ready[0]) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Editor restoration timed out");
    }

    private static EditText input(Object editor, String field) {
        return (EditText) get(editor, field);
    }

    @SuppressWarnings("unchecked")
    private static List<FoodItem> currentFoods(MainActivity activity) {
        return (List<FoodItem>) get(activity, "foods");
    }

    private static Object get(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception error) {
            throw new AssertionError(name, error);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception error) {
            throw new AssertionError(name, error);
        }
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (Exception error) {
            throw new AssertionError(name, error);
        }
    }

    private static View findText(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findText(group.getChildAt(index), value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
