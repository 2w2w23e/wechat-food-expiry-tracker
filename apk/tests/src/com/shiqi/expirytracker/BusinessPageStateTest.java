package com.shiqi.expirytracker;

import java.util.Arrays;
import java.util.Collections;

public final class BusinessPageStateTest {
    private int passed;

    public static void main(String[] args) throws Exception {
        new BusinessPageStateTest().run();
    }

    private void run() throws Exception {
        BarcodeResolutionState empty = BarcodeResolutionState.fromMatches(
                "4006381333931",
                Collections.<FoodItem>emptyList()
        );
        expect(0, empty.matchCount(), "zero matches");
        expect(false, empty.canReplenish(), "zero matches cannot replenish");

        FoodItem oneProfile = profile("batch-one", "profile-one", "原味酸奶");
        BarcodeResolutionState one = BarcodeResolutionState.fromMatches(
                "4006381333931",
                Arrays.asList(oneProfile)
        );
        expect(1, one.matchCount(), "one match");
        expect(true, one.canReplenish(), "one match is safely preselected");
        expect("profile-one", one.selectedProfile().productProfileId, "one selected profile");

        FoodItem secondProfile = profile("batch-two", "profile-two", "草莓酸奶");
        BarcodeResolutionState many = BarcodeResolutionState.fromMatches(
                "4006381333931",
                Arrays.asList(oneProfile, secondProfile)
        );
        expect(true, many.requiresExplicitChoice(), "many matches require explicit choice");
        expect(false, many.canReplenish(), "many matches have no implicit selection");
        expect(
                true,
                many.selectProfile(BarcodeResolutionState.profileKey(secondProfile)),
                "explicit second selection"
        );
        expect("profile-two", many.selectedProfile().productProfileId, "second selected profile");
        expect(false, many.selectProfile("profile:missing"), "unknown profile rejected");
        expect(false, many.canReplenish(), "invalid selection clears action eligibility");

        String payload = "{\"schemaVersion\":3,\"foods\":[{\"name\":\"牛奶\"}]}";
        String encoded = MigrationBackupEnvelope.create(
                payload,
                3,
                "2026-07-31T12:00:00+0800"
        );
        MigrationBackupEnvelope.Parsed parsed = MigrationBackupEnvelope.parse(encoded);
        expect(payload, parsed.payload, "backup payload exact");
        expect(3, parsed.sourceSchemaVersion, "backup schema");
        expect(
                MigrationBackupEnvelope.sha256(payload),
                parsed.payloadSha256,
                "backup hash"
        );

        boolean tamperRejected = false;
        try {
            MigrationBackupEnvelope.parse(encoded.replace("牛奶", "酸奶"));
        } catch (Exception expected) {
            tamperRejected = true;
        }
        expect(true, tamperRejected, "tampered payload rejected");

        System.out.println("BusinessPageStateTest: " + passed + " passed");
    }

    private FoodItem profile(String batchId, String profileId, String name) {
        FoodItem item = new FoodItem();
        item.id = batchId;
        item.productProfileId = profileId;
        item.barcode = "4006381333931";
        item.name = name;
        return item;
    }

    private void expect(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + " expected=<" + expected + "> actual=<" + actual + ">"
            );
        }
        passed++;
    }
}