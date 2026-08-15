package com.dungeonitems.model;

import com.bountysmp.configurableitems.model.TriggerType;
import java.util.EnumMap;
import java.util.Map;

public final class DungeonItemTemplate {
    private final String id;
    private String name;
    private Rarity rarity;
    private DungeonItemType type;
    private String description;
    private final Map<TriggerType, Ability> abilities = new EnumMap<>(TriggerType.class);
    private final Map<StatKey, Double> statOverrides = new EnumMap<>(StatKey.class);

    public DungeonItemTemplate(String id, String name, Rarity rarity, DungeonItemType type, String description) {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid item id " + id);
        this.id = id; this.name = clean(name); this.rarity = rarity; this.type = type; this.description = clean(description);
    }

    public String id() { return id; }
    public String name() { return name; }
    public void name(String value) { name = clean(value); }
    public Rarity rarity() { return rarity; }
    public void rarity(Rarity value) { rarity = value; }
    public DungeonItemType type() { return type; }
    public void type(DungeonItemType value) { type = value; }
    public String description() { return description; }
    public void description(String value) { description = clean(value); }
    public Map<TriggerType, Ability> abilities() { return abilities; }
    public Map<StatKey, Double> statOverrides() { return statOverrides; }

    public DungeonItemTemplate copy() {
        DungeonItemTemplate copy = new DungeonItemTemplate(id, name, rarity, type, description);
        copy.abilities.putAll(abilities);
        copy.statOverrides.putAll(statOverrides);
        return copy;
    }

    private static String clean(String value) { return Rarity.stripLegacyFormatting(value).trim(); }
}
