package com.shiqi.expirytracker;

final class Option {
    final String value;
    final String label;
    final String pinyin;
    final String initials;

    Option(String value, String label) {
        this(value, label, "", "");
    }

    Option(String value, String label, String pinyin, String initials) {
        this.value = value;
        this.label = label;
        this.pinyin = pinyin;
        this.initials = initials;
    }
}
