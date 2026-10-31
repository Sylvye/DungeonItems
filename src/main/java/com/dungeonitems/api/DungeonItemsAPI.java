package com.dungeonitems.api;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface DungeonItemsAPI {
    /** Empty defaults keep older API implementations compatible. */
    default Collection<String> kits() { return java.util.List.of(); }
    default Optional<com.dungeonitems.model.KitDefinition> kit(String id) { return Optional.empty(); }
    Collection<DungeonItemInfo> items();
    Optional<ItemStack> createItem(String id, int amount);
}
