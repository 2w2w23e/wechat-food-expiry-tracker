package com.shiqi.expirytracker;

final class AppNavigation {
    static final int OVERVIEW = 0;
    static final int INVENTORY = 1;
    static final int SCAN = 2;
    static final int SETTINGS = 3;

    private AppNavigation() {
    }

    static boolean isPersistentDestination(int destination) {
        return destination == OVERVIEW
                || destination == INVENTORY
                || destination == SETTINGS;
    }

    static int restoreDestination(int savedDestination) {
        return isPersistentDestination(savedDestination) ? savedDestination : OVERVIEW;
    }

    static int persistentDestinationAfterSelection(int currentDestination, int selectedDestination) {
        if (selectedDestination == SCAN) {
            return restoreDestination(currentDestination);
        }
        return restoreDestination(selectedDestination);
    }

    static String label(int destination) {
        if (destination == INVENTORY) {
            return "库存";
        }
        if (destination == SCAN) {
            return "扫描";
        }
        if (destination == SETTINGS) {
            return "设置";
        }
        return "今日";
    }
}
