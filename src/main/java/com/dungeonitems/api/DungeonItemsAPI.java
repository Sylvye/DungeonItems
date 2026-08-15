package com.dungeonitems.api;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface DungeonItemsAPI {
    Collection<DungeonItemInfo> items();
    Optional<ItemStack> createItem(String id, int amount);
}
