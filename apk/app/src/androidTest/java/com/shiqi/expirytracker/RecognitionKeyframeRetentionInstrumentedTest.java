package com.shiqi.expirytracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class RecognitionKeyframeRetentionInstrumentedTest {
    @Test
    public void strongerNearDuplicateEvictsOldKeyframeWithoutInvalidatingDisplayedDrawable() {
        withSession(session -> {
            TestFrame old = session.frame("old", 1000L, "same-label", 0.2d);
            TestFrame stronger = session.frame("stronger", 1100L, "same-label", 0.9d);
            session.offer(old);
            session.assertRetained(old);
            Drawable displayed = session.displayedDrawable(old);

            session.offer(stronger);

            // Membership and displayed-bitmap lifetime are separate ownership contracts.
            session.assertRetained(stronger);
            assertDrawableStillDraws(displayed);
            session.clear();
            session.assertRetained();
            assertDrawableStillDraws(displayed);
        });
    }

    @Test
    public void onlySelectedThreeAreRetainedAndRejectedBitmapsAreRecycled() {
        withSession(session -> {
            TestFrame weakest = session.frame("weakest", 1000L, "label-a", 0.2d);
            TestFrame middle = session.frame("middle", 2000L, "label-b", 0.5d);
            TestFrame strong = session.frame("strong", 3000L, "label-c", 0.7d);
            TestFrame strongest = session.frame("strongest", 4000L, "label-d", 0.95d);
            TestFrame rejected = session.frame("rejected", 5000L, "label-e", 0.1d);

            session.offer(weakest);
            session.assertRetained(weakest);
            session.offer(middle);
            session.assertRetained(weakest, middle);
            session.offer(strong);
            session.assertRetained(weakest, middle, strong);
            session.offer(strongest);
            session.assertRetained(middle, strong, strongest);

            session.offer(rejected);
            assertTrue("Rejected incoming bitmap must be recycled", rejected.bitmap.isRecycled());
            session.assertRetained(middle, strong, strongest);
        });
    }

    @Test
    public void clearDropsCacheReferencesWithoutInvalidatingDrawablesAndIsIdempotent() {
        withSession(session -> {
            TestFrame first = session.frame("first", 1000L, "label-a", 0.5d);
            TestFrame second = session.frame("second", 2000L, "label-b", 0.6d);
            TestFrame third = session.frame("third", 3000L, "label-c", 0.7d);
            session.offer(first);
            Drawable firstDrawable = session.displayedDrawable(first);
            session.offer(second);
            Drawable secondDrawable = session.displayedDrawable(second);
            session.offer(third);
            Drawable thirdDrawable = session.displayedDrawable(third);
            session.assertRetained(first, second, third);

            session.clear();
            session.assertRetained();
            assertDrawableStillDraws(firstDrawable);
            assertDrawableStillDraws(secondDrawable);
            assertDrawableStillDraws(thirdDrawable);

            session.clear();
            session.assertRetained();
            assertDrawableStillDraws(firstDrawable);
            assertDrawableStillDraws(secondDrawable);
            assertDrawableStillDraws(thirdDrawable);

            // Reusing the same candidate proves clear also reset selector ownership.
            TestFrame fresh = session.frame("first", 1000L, "label-a", 0.5d);
            session.offer(fresh);
            session.assertRetained(fresh);
            Drawable freshDrawable = session.displayedDrawable(fresh);
            session.clear();
            session.assertRetained();
            assertDrawableStillDraws(freshDrawable);
        });
    }

    private static void withSession(SessionCheck check) {
        Context context = ApplicationProvider.getApplicationContext();
        File missingImage = new File(context.getCacheDir(),
                "missing-keyframe-retention-" + UUID.randomUUID() + ".jpg");
        assertFalse("QA input must not exist or start real image recognition", missingImage.exists());
        Intent intent = new Intent(context, DateOcrScanActivity.class);
        intent.putExtra(DateOcrScanActivity.EXTRA_QA_IMAGE_PATH, missingImage.getAbsolutePath());
        ActivityScenario<DateOcrScanActivity> scenario = ActivityScenario.launch(intent);
        try {
            scenario.onActivity(activity -> {
                Session session = new Session(activity);
                try {
                    session.clear();
                    check.run(session);
                } finally {
                    session.close();
                }
            });
        } finally {
            scenario.close();
        }
    }

    private interface SessionCheck {
        void run(Session session);
    }

    private static void assertDrawableStillDraws(Drawable drawable) {
        Bitmap target = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, target.getWidth(), target.getHeight());
            drawable.draw(new Canvas(target));
            assertEquals("A retained thumbnail drawable must still render its original pixels",
                    0xff447788, target.getPixel(8, 8));
        } finally {
            target.recycle();
        }
    }

    private static Drawable findDrawable(View root, Bitmap bitmap) {
        if (root instanceof ImageView) {
            Drawable drawable = ((ImageView) root).getDrawable();
            if (drawable instanceof BitmapDrawable
                    && ((BitmapDrawable) drawable).getBitmap() == bitmap) {
                return drawable;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                Drawable drawable = findDrawable(group.getChildAt(index), bitmap);
                if (drawable != null) {
                    return drawable;
                }
            }
        }
        return null;
    }

    private static final class TestFrame {
        final Bitmap bitmap;
        final RecognitionFrameSelector.FrameCandidate candidate;
        final Object keyframe;

        TestFrame(Bitmap bitmap, RecognitionFrameSelector.FrameCandidate candidate, Object keyframe) {
            this.bitmap = bitmap;
            this.candidate = candidate;
            this.keyframe = keyframe;
        }
    }

    private static final class Session {
        private final DateOcrScanActivity activity;
        private long nextFrameId;
        private final Constructor<?> constructor;
        private final Method offer;
        private final Method clear;
        private final Field retained;
        private final Field selector;
        private final Field bitmap;
        private final Field candidate;

        Session(DateOcrScanActivity activity) {
            this.activity = activity;
            try {
                Class<?> type = Class.forName(DateOcrScanActivity.class.getName() + "$SessionKeyframe");
                constructor = type.getDeclaredConstructor(RecognitionEvidence.Frame.class,
                        Bitmap.class, RecognitionFrameSelector.FrameCandidate.class);
                constructor.setAccessible(true);
                offer = DateOcrScanActivity.class.getDeclaredMethod("offerSessionKeyframe", type);
                offer.setAccessible(true);
                clear = DateOcrScanActivity.class.getDeclaredMethod("clearSessionKeyframes");
                clear.setAccessible(true);
                retained = field(DateOcrScanActivity.class, "sessionKeyframes");
                selector = field(DateOcrScanActivity.class, "frameSelector");
                bitmap = field(type, "bitmap");
                candidate = field(type, "selectorCandidate");
            } catch (Exception error) {
                throw new AssertionError("Could not access session keyframe lifecycle", error);
            }
        }

        TestFrame frame(String id, long timestampMillis, String signature, double quality) {
            Bitmap image = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            image.eraseColor(0xff447788);
            RecognitionFrameSelector.FrameCandidate selection = new RecognitionFrameSelector.FrameCandidate(
                    id, timestampMillis, signature, quality, 0.5d, 0d, quality, quality, 0.5d);
            RecognitionEvidence.Region region = new RecognitionEvidence.Region(
                    RecognitionEvidence.FIELD_TEXT_REGION, id,
                    new RecognitionEvidence.NormalizedRect(0.1d, 0.1d, 0.9d, 0.9d),
                    "instrumentation", quality, quality);
            RecognitionEvidence.Frame evidence = new RecognitionEvidence.Frame(
                    ++nextFrameId, timestampMillis * 1000L, image.getWidth(), image.getHeight(),
                    selection.qualityScore, signature.hashCode(), Collections.singletonList(region));
            try {
                return new TestFrame(image, selection, constructor.newInstance(evidence, image, selection));
            } catch (Exception error) {
                image.recycle();
                throw new AssertionError("Could not construct real bitmap keyframe", error);
            }
        }

        void offer(TestFrame frame) {
            invoke(offer, frame.keyframe);
        }

        void clear() {
            invoke(clear);
        }

        Drawable displayedDrawable(TestFrame frame) {
            Drawable result = findDrawable(activity.getWindow().getDecorView(), frame.bitmap);
            assertTrue("The admitted bitmap must be referenced by a real thumbnail", result != null);
            return result;
        }

        void assertRetained(TestFrame... expected) {
            try {
                List<?> frames = (List<?>) retained.get(activity);
                assertTrue("Retained bitmap count must never exceed three", frames.size() <= 3);
                assertEquals("Retained bitmap count", expected.length, frames.size());
                Set<String> actualIds = new HashSet<String>();
                for (Object frame : frames) {
                    RecognitionFrameSelector.FrameCandidate value =
                            (RecognitionFrameSelector.FrameCandidate) candidate.get(frame);
                    assertTrue("No duplicate retained frame IDs", actualIds.add(value.frameId));
                    assertFalse("Selected bitmap must remain live", ((Bitmap) bitmap.get(frame)).isRecycled());
                }
                Set<String> expectedIds = new HashSet<String>();
                for (TestFrame frame : expected) {
                    expectedIds.add(frame.candidate.frameId);
                    assertTrue("Expected keyframe object must be retained", frames.contains(frame.keyframe));
                    assertSame(frame.bitmap, bitmap.get(frame.keyframe));
                    assertFalse(frame.bitmap.isRecycled());
                }
                assertEquals(expectedIds, actualIds);
                Set<String> selectedIds = new HashSet<String>();
                for (RecognitionFrameSelector.FrameCandidate value
                        : ((RecognitionFrameSelector) selector.get(activity)).selectedFrames()) {
                    selectedIds.add(value.frameId);
                }
                assertEquals("Bitmap ownership must exactly match selector membership", selectedIds, actualIds);
            } catch (Exception error) {
                throw new AssertionError("Could not inspect retained bitmap ownership", error);
            }
        }

        void close() {
            clear();
        }

        private void invoke(Method method, Object... args) {
            try {
                method.invoke(activity, args);
            } catch (Exception error) {
                throw new AssertionError("Could not invoke " + method.getName(), error);
            }
        }

        private static Field field(Class<?> type, String name) throws Exception {
            Field result = type.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        }
    }
}
