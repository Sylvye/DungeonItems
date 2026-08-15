package com.dungeonitems.model;

public enum Rarity {
    COMMON("&f"), RARE("&9"), LEGENDARY("&6"), SPECIAL("&d");

    private final String legacyColor;

    Rarity(String legacyColor) { this.legacyColor = legacyColor; }

    public String legacyColor() { return legacyColor; }

    public String decorateName(String plainName) {
        String safe = stripLegacyFormatting(plainName);
        return this == SPECIAL ? "&d&kX&r &d" + safe + " &d&kX" : legacyColor + safe;
    }

    public static String stripLegacyFormatting(String input) {
        return (input == null ? "" : input).replaceAll("(?i)&[0-9A-FK-ORX]", "");
    }
}
