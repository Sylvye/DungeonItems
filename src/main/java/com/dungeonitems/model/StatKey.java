package com.dungeonitems.model;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

/** Common RPG stats edited as final effective values rather than raw modifiers. */
public enum StatKey {
    DAMAGE("attack_damage", "Damage", DungeonItemType.WEAPON),
    ATTACK_SPEED("attack_speed", "Attack Speed", DungeonItemType.WEAPON),
    ENTITY_REACH("entity_interaction_range", "Entity Reach", DungeonItemType.WEAPON),
    ATTACK_KNOCKBACK("attack_knockback", "Attack Knockback", DungeonItemType.WEAPON),
    ARMOR("armor", "Armor", DungeonItemType.ARMOR),
    ARMOR_TOUGHNESS("armor_toughness", "Armor Toughness", DungeonItemType.ARMOR),
    KNOCKBACK_RESISTANCE("knockback_resistance", "Knockback Resistance", DungeonItemType.ARMOR);

    private final String attributeKey;
    private final String label;
    private final DungeonItemType type;

    StatKey(String attributeKey, String label, DungeonItemType type) {
        this.attributeKey = attributeKey; this.label = label; this.type = type;
    }

    public Attribute attribute() { return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attributeKey)); }
    public String attributeKey() { return "minecraft:" + attributeKey; }
    public String label() { return label; }
    public DungeonItemType type() { return type; }
}
