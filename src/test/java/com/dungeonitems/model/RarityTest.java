package com.dungeonitems.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class RarityTest {
    @Test void decoratesSpecialNameWithReadableCenterAndObfuscatedXs() {
        assertEquals("&d&kX&r &dStarfall &d&kX", Rarity.SPECIAL.decorateName("&cStarfall"));
    }

    @Test void usesRequestedRarityColors() {
        assertEquals("&f", Rarity.COMMON.legacyColor());
        assertEquals("&9", Rarity.RARE.legacyColor());
        assertEquals("&6", Rarity.LEGENDARY.legacyColor());
        assertEquals("&d", Rarity.SPECIAL.legacyColor());
    }
}
