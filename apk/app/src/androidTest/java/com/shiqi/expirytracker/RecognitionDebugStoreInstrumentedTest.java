package com.shiqi.expirytracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public final class RecognitionDebugStoreInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        RecognitionDebugStore.resetForTests(context);
    }

    @After
    public void tearDown() {
        RecognitionDebugStore.resetForTests(context);
    }

    @Test
    public void enabledCapturePersistsSessionAndRemainsShareableAfterDisable()
            throws Exception {
        RecognitionDebugStore.enable(context, "device=test; sdk=29");
        assertTrue(RecognitionDebugStore.isEnabled(context));
        assertTrue(RecognitionDebugStore.hasShareableLog(context));

        RecognitionDebugStore.saveSession(
                context,
                "session-a",
                "confirmation.continue",
                "frames_captured=12\nraw_ocr\n生产日期 2026-08-01"
        );
        String stored = RecognitionDebugStore.readForTests(context);
        assertTrue(stored.contains("device=test"));
        assertTrue(stored.contains("frames_captured=12"));
        assertTrue(stored.contains("生产日期 2026-08-01"));

        RecognitionDebugStore.disable(context);
        assertFalse(RecognitionDebugStore.isEnabled(context));
        assertTrue(RecognitionDebugStore.hasShareableLog(context));

        File shared = RecognitionDebugStore.buildShareFile(context, "");
        assertTrue(shared.isFile());
        assertTrue(shared.length() > 0L);
        assertTrue(shared.getParentFile().getName().equals("shared-transfers"));
    }
}
