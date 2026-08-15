package com.dungeonitems.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

public final class KitDefinition {
    private final String id;
    private final Map<String, ItemStack> slots = new LinkedHashMap<>();

    public KitDefinition(String id) {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid kit id " + id);
        this.id = id;
    }

    public String id() { return id; }
    public Map<String, ItemStack> slots() { return slots; }
    public KitDefinition copy() {
        return copyAs(id);
    }
    public KitDefinition copyAs(String newId) {
        KitDefinition copy = new KitDefinition(newId);
        slots.forEach((key, item) -> copy.slots.put(key, item.clone()));
        return copy;
    }
}
