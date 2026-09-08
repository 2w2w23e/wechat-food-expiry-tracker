package com.shiqi.expirytracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class RecognitionConfirmedNameInstrumentedTest {
    @Test
    public void confirmedNameWinsOverAuthoritativeBarcodeNameWithoutDates() {
        Context context = ApplicationProvider.getApplicationContext();
        File missingImage = new File(context.getCacheDir(),
                "missing-confirmed-name-" + UUID.randomUUID() + ".jpg");
        assertFalse(missingImage.exists());
        Intent intent = new Intent(context, DateOcrScanActivity.class);
        intent.putExtra(DateOcrScanActivity.EXTRA_QA_IMAGE_PATH, missingImage.getAbsolutePath());

        final String barcode = "6901234567892";
        final String editedName = "\u7ef4C\u6c34 QA  (\u624b\u52a8\u4fee\u8ba2)";
        ActivityScenario<DateOcrScanActivity> scenario = ActivityScenario.launchActivityForResult(intent);
        try {
            scenario.onActivity(activity -> {
                BarcodeProductInfo info = new BarcodeProductInfo();
                info.barcode = barcode;
                info.name = "\u519c\u592b\u5c71\u6cc9\u996e\u7528\u5929\u7136\u6c34";
                info.found = true;
                info.registered = true;
                info.source = "GS1";
                assertFalse("Authoritative lookup must conflict with the user edit",
                        editedName.equals(info.displayName()));

                UnifiedRecognitionStabilizer.Snapshot snapshot =
                        new UnifiedRecognitionStabilizer.Snapshot(
                                3, barcode, 3, "\u7ef4C\u6c34", 3, null, null, "");
                assertTrue(snapshot.hasStableBarcode());
                assertFalse("Missing dates must not block returning confirmed fields",
                        snapshot.hasStableDateCandidate());
                setField(activity, "latestSnapshot", snapshot);
                setField(activity, "latestProductInfo", info);
                setField(activity, "latestProductBarcode", barcode);
                // Surrounding whitespace may be trimmed; user spelling and internal spacing may not.
                setField(activity, "selectedPackagingName", "  " + editedName + "  ");
                finishWithCandidate(activity);
            });

            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_OK, result.getResultCode());
            Intent data = result.getResultData();
            assertNotNull(data);
            assertEquals("Return the user's confirmed name, not cleaned OCR or lookup text",
                    editedName, data.getStringExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_NAME));
            assertEquals(barcode, data.getStringExtra(UnifiedRecognitionPayload.EXTRA_BARCODE));
            assertEquals(barcode, data.getStringExtra("SCAN_RESULT"));
            assertTrue("The authoritative lookup must actually participate in the result",
                    data.getBooleanExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_FOUND, false));
            assertEquals("", data.getStringExtra(DateOcrResultPayload.EXTRA_PRODUCTION_DATE));
            assertEquals("", data.getStringExtra(DateOcrResultPayload.EXTRA_EXPIRY_DATE));
        } finally {
            scenario.close();
        }
    }

    private static void setField(DateOcrScanActivity activity, String name, Object value) {
        try {
            Field field = DateOcrScanActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(activity, value);
        } catch (Exception error) {
            throw new AssertionError("Could not seed recognition field " + name, error);
        }
    }

    private static void finishWithCandidate(DateOcrScanActivity activity) {
        try {
            Method method = DateOcrScanActivity.class.getDeclaredMethod("finishWithCandidate");
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Exception error) {
            throw new AssertionError("Could not return the confirmed recognition result", error);
        }
    }
}
