package com.dungeonitems.api;

import com.dungeonitems.model.DungeonItemType;
import com.dungeonitems.model.Rarity;
import org.bukkit.Material;

public record DungeonItemInfo(String id, String name, Rarity rarity, DungeonItemType type, Material material) { }
