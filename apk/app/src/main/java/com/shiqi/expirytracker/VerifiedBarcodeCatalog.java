package com.shiqi.expirytracker;

final class VerifiedBarcodeCatalog {
    static final class Entry {
        final String barcode;
        final String name;
        final String brand;
        final String generalName;
        final String category;
        final String specification;

        Entry(
                String barcode,
                String name,
                String brand,
                String generalName,
                String category,
                String specification
        ) {
            this.barcode = barcode;
            this.name = name;
            this.brand = brand;
            this.generalName = generalName;
            this.category = category;
            this.specification = specification;
        }
    }

    private static final Entry[] ENTRIES = new Entry[] {
            new Entry(
                    "6926265313430",
                    "上好佳薯条",
                    "上好佳",
                    "薯条",
                    "膨化食品",
                    "原味 80克"
            ),
            new Entry(
                    "6920459940310",
                    "康师傅喝开水",
                    "康师傅",
                    "熟水饮用水",
                    "饮用水",
                    "550毫升"
            )
    };

    private VerifiedBarcodeCatalog() {}

    static Entry find(String barcode) {
        String code = BarcodeUtils.digitsOnly(barcode);
        for (Entry entry : ENTRIES) {
            if (entry.barcode.equals(code)) {
                return entry;
            }
        }
        return null;
    }
}
