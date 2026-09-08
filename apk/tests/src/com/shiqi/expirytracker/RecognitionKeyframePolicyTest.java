package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class RecognitionKeyframePolicyTest {
    private int passed;
    private int failed;

    public static void main(String[] args) {
        new RecognitionKeyframePolicyTest().run();
    }

    private void run() {
        test("Empty regions have no primary evidence and retain quality-only scoring", new TestCase() {
            @Override
            public void run() {
                RecognitionEvidence.Frame empty = frame(0.8d);
                assertSame(null, RecognitionKeyframePolicy.primaryRegion(empty));
                assertEquals(0, RecognitionKeyframePolicy.evidenceMask(empty.regions));
                assertEquals(0, RecognitionKeyframePolicy.primaryEvidence(empty.regions));
                assertEquals(4, RecognitionKeyframePolicy.keyframeFieldOrder(null));
                assertClose(0.44d, RecognitionKeyframePolicy.score(empty));
                assertEquals(0, RecognitionKeyframePolicy.compare(empty, frame(0.8d)));

                RecognitionEvidence.Frame nullRegions = new RecognitionEvidence.Frame(
                        1L, 0L, 100, 100, 0d, 0L, null);
                assertSame(null, RecognitionKeyframePolicy.primaryRegion(nullRegions));
                assertEquals(0, RecognitionKeyframePolicy.evidenceMask(null));
                assertEquals(0, RecognitionKeyframePolicy.primaryEvidence(null));
                assertClose(0d, RecognitionKeyframePolicy.score(nullRegions));
            }
        });

        test("First product name wins even against stronger names and dates", new TestCase() {
            @Override
            public void run() {
                RecognitionEvidence.Region firstName = region("product_name", 0.1d);
                RecognitionEvidence.Frame mixed = frame(0.8d,
                        region("expiry_date", 0.99d), firstName,
                        region("product_name", 0.9d), region("barcode", 1d));
                assertSame(firstName, RecognitionKeyframePolicy.primaryRegion(mixed));
                assertSame(firstName, RecognitionKeyframePolicy.primaryRegion(mixed.regions));
                assertEquals(RecognitionFrameSelector.EVIDENCE_PRODUCT_NAME,
                        RecognitionKeyframePolicy.primaryEvidence(mixed.regions));
                assertClose(0.895d, RecognitionKeyframePolicy.score(mixed));
            }
        });

        test("Without a name confidence wins rather than display field priority", new TestCase() {
            @Override
            public void run() {
                RecognitionEvidence.Region barcode = region("barcode", 0.9d);
                RecognitionEvidence.Frame mixed = frame(0.5d,
                        region("production_date", 0.8d), region("expiry_date", 0.7d), barcode);
                assertSame(barcode, RecognitionKeyframePolicy.primaryRegion(mixed));
                assertEquals(RecognitionFrameSelector.EVIDENCE_BARCODE,
                        RecognitionKeyframePolicy.primaryEvidence(mixed.regions));
            }
        });

        test("Equal confidence keeps the first region including zero confidence", new TestCase() {
            @Override
            public void run() {
                RecognitionEvidence.Region expiry = region("expiry_date", 0.7d);
                RecognitionEvidence.Region production = region("production_date", 0.7d);
                RecognitionEvidence.Frame first = frame(0.5d, expiry, production);
                RecognitionEvidence.Frame reversed = frame(0.5d, production, expiry);
                assertSame(expiry, RecognitionKeyframePolicy.primaryRegion(first));
                assertSame(production, RecognitionKeyframePolicy.primaryRegion(reversed));
                assertEquals(RecognitionFrameSelector.EVIDENCE_EXPIRY_DATE,
                        RecognitionKeyframePolicy.primaryEvidence(first.regions));
                assertEquals(RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE,
                        RecognitionKeyframePolicy.primaryEvidence(reversed.regions));
                RecognitionEvidence.Region zero = region("text_region", 0d);
                assertSame(zero, RecognitionKeyframePolicy.primaryRegion(
                        frame(0d, zero, region("barcode", 0d))));
            }
        });

        test("Evidence mask unions supported fields without duplicate or text bits", new TestCase() {
            @Override
            public void run() {
                String[] fields = {"product_name", "production_date", "expiry_date", "barcode"};
                int[] bits = {RecognitionFrameSelector.EVIDENCE_PRODUCT_NAME,
                        RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE,
                        RecognitionFrameSelector.EVIDENCE_EXPIRY_DATE,
                        RecognitionFrameSelector.EVIDENCE_BARCODE};
                List<RecognitionEvidence.Region> regions = new ArrayList<RecognitionEvidence.Region>();
                int expectedMask = 0;
                for (int index = 0; index < fields.length; index++) {
                    RecognitionEvidence.Region value = region(fields[index], 0.5d);
                    assertEquals(bits[index], RecognitionKeyframePolicy.evidenceMask(
                            Arrays.asList(value, value)));
                    assertEquals(bits[index], RecognitionKeyframePolicy.primaryEvidence(
                            Collections.singletonList(value)));
                    regions.add(value);
                    expectedMask |= bits[index];
                }
                regions.add(region("text_region", 1d));
                regions.add(region("unknown", 1d));
                assertEquals(expectedMask, RecognitionKeyframePolicy.evidenceMask(regions));
            }
        });

        test("Strongest text or unknown field has NONE primary evidence without fallback", new TestCase() {
            @Override
            public void run() {
                for (String field : Arrays.asList("text_region", "unknown", "")) {
                    RecognitionEvidence.Region strongest = region(field, 0.9d);
                    RecognitionEvidence.Frame mixed = frame(0.5d,
                            region("production_date", 0.4d), strongest);
                    assertSame(strongest, RecognitionKeyframePolicy.primaryRegion(mixed));
                    assertEquals(RecognitionFrameSelector.EVIDENCE_NONE,
                            RecognitionKeyframePolicy.primaryEvidence(mixed.regions));
                    assertEquals(RecognitionFrameSelector.EVIDENCE_PRODUCTION_DATE,
                            RecognitionKeyframePolicy.evidenceMask(mixed.regions));
                }
            }
        });

        test("Score preserves weights and caps only the region count at three", new TestCase() {
            @Override
            public void run() {
                RecognitionEvidence.Region evidence = region("text_region", 0.5d);
                assertClose(0.655d, RecognitionKeyframePolicy.score(frame(0.8d, evidence)));
                assertClose(0.705d, RecognitionKeyframePolicy.score(
                        frame(0.8d, evidence, evidence, evidence)));
                assertClose(0.705d, RecognitionKeyframePolicy.score(
                        frame(0.8d, evidence, evidence, evidence, evidence)));
                RecognitionEvidence.Region perfect = region("text_region", 1d);
                assertClose(1.005d, RecognitionKeyframePolicy.score(
                        frame(1d, perfect, perfect, perfect)));
            }
        });

        test("Frame ordering preserves all display priorities and empty fallback", new TestCase() {
            @Override
            public void run() {
                String[] fields = {"product_name", "production_date", "expiry_date",
                        "text_region", "barcode", "unknown"};
                for (int index = 0; index < fields.length; index++) {
                    assertEquals(Math.min(index, 4),
                            RecognitionKeyframePolicy.keyframeFieldOrder(fields[index]));
                    if (index < 4) {
                        assertBefore(frame(0d, region(fields[index], 0d)),
                                frame(1d, region(fields[index + 1], 1d)));
                        assertBefore(frame(0d, region(fields[index], 0d)), frame(1d));
                    }
                }
                assertBefore(frame(1d), frame(0d, region("barcode", 0d)));
                assertBefore(frame(0.9d), frame(0.1d));
            }
        });

        test("Same-priority frames use descending score and stable ties", new TestCase() {
            @Override
            public void run() {
                RecognitionEvidence.Frame lower = frame(0.1d, region("expiry_date", 0.4d));
                RecognitionEvidence.Frame higher = frame(0.9d, region("expiry_date", 0.8d));
                RecognitionEvidence.Frame tied = new RecognitionEvidence.Frame(
                        99L, 999L, 400, 300, 0.9d, 123L, higher.regions);
                assertBefore(higher, lower);
                assertEquals(0, RecognitionKeyframePolicy.compare(higher, tied));
                assertEquals(0, RecognitionKeyframePolicy.compare(tied, higher));
                List<RecognitionEvidence.Frame> frames = new ArrayList<RecognitionEvidence.Frame>(
                        Arrays.asList(lower, tied, higher));
                Collections.sort(frames, new Comparator<RecognitionEvidence.Frame>() {
                    @Override
                    public int compare(RecognitionEvidence.Frame left, RecognitionEvidence.Frame right) {
                        return RecognitionKeyframePolicy.compare(left, right);
                    }
                });
                assertSame(tied, frames.get(0));
                assertSame(higher, frames.get(1));
                assertSame(lower, frames.get(2));
            }
        });

        System.out.println("Recognition keyframe policy tests: "
                + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            throw new AssertionError("Recognition keyframe policy tests failed: " + failed);
        }
    }

    private static RecognitionEvidence.Region region(String field, double confidence) {
        return new RecognitionEvidence.Region(field, "value", null, "test", Double.NaN, confidence);
    }

    private static RecognitionEvidence.Frame frame(double quality, RecognitionEvidence.Region... regions) {
        return new RecognitionEvidence.Frame(1L, 0L, 100, 100, quality, 0L, Arrays.asList(regions));
    }

    private static void assertBefore(RecognitionEvidence.Frame first, RecognitionEvidence.Frame second) {
        if (RecognitionKeyframePolicy.compare(first, second) >= 0
                || RecognitionKeyframePolicy.compare(second, first) <= 0) {
            throw new AssertionError("expected strict frame order in both directions");
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError("expected same region/frame reference");
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected <" + expected + "> but got <" + actual + ">");
        }
    }

    private static void assertClose(double expected, double actual) {
        if (!(Math.abs(expected - actual) < 1e-12d)) {
            throw new AssertionError("expected <" + expected + "> but got <" + actual + ">");
        }
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

    private interface TestCase {
        void run();
    }
}
