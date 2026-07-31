package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.List;

final class BarcodeResolutionState {
    private final String barcode;
    private final List<FoodItem> profiles;
    private String selectedProfileKey;

    private BarcodeResolutionState(String barcode, List<FoodItem> sourceProfiles) {
        this.barcode = BarcodeUtils.digitsOnly(barcode);
        this.profiles = copies(sourceProfiles);
        this.selectedProfileKey = profiles.size() == 1
                ? profileKey(profiles.get(0))
                : "";
    }

    static BarcodeResolutionState fromMatches(String barcode, List<FoodItem> profiles) {
        return new BarcodeResolutionState(barcode, profiles);
    }

    String barcode() {
        return barcode;
    }

    int matchCount() {
        return profiles.size();
    }

    boolean requiresExplicitChoice() {
        return profiles.size() > 1 && selectedProfileKey.length() == 0;
    }

    boolean canReplenish() {
        return selectedProfile() != null;
    }

    List<FoodItem> profiles() {
        return copies(profiles);
    }

    String selectedProfileKey() {
        return selectedProfileKey;
    }

    boolean selectProfile(String requestedKey) {
        String wanted = FoodItem.cleanText(requestedKey);
        for (FoodItem profile : profiles) {
            String candidate = profileKey(profile);
            if (candidate.equals(wanted)) {
                selectedProfileKey = candidate;
                return true;
            }
        }
        selectedProfileKey = "";
        return false;
    }

    FoodItem selectedProfile() {
        for (FoodItem profile : profiles) {
            if (profileKey(profile).equals(selectedProfileKey)) {
                return profile.copy();
            }
        }
        return null;
    }

    static String profileKey(FoodItem profile) {
        if (profile == null) {
            return "";
        }
        String profileId = FoodItem.cleanText(profile.productProfileId);
        if (profileId.length() > 0) {
            return "profile:" + profileId;
        }
        String batchId = FoodItem.cleanText(profile.id);
        if (batchId.length() > 0) {
            return "batch:" + batchId;
        }
        return "";
    }

    private static List<FoodItem> copies(List<FoodItem> source) {
        List<FoodItem> result = new ArrayList<FoodItem>();
        if (source == null) {
            return result;
        }
        for (FoodItem profile : source) {
            if (profile != null && profileKey(profile).length() > 0) {
                result.add(profile.copy());
            }
        }
        return result;
    }
}