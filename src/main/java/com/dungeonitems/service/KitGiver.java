package com.dungeonitems.service;

import com.dungeonitems.model.KitDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class KitGiver {
    public int give(Player player, KitDefinition kit) {
        PlayerInventory inventory = player.getInventory(); List<ItemStack> collisions = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = kit.slots().get("inventory." + slot);
            if (item == null) continue;
            if (empty(inventory.getItem(slot))) inventory.setItem(slot, item.clone()); else collisions.add(item.clone());
        }
        placeSpecial(inventory.getHelmet(), kit.slots().get("helmet"), inventory::setHelmet, collisions);
        placeSpecial(inventory.getChestplate(), kit.slots().get("chestplate"), inventory::setChestplate, collisions);
        placeSpecial(inventory.getLeggings(), kit.slots().get("leggings"), inventory::setLeggings, collisions);
        placeSpecial(inventory.getBoots(), kit.slots().get("boots"), inventory::setBoots, collisions);
        placeSpecial(inventory.getItemInOffHand(), kit.slots().get("offhand"), inventory::setItemInOffHand, collisions);
        int dropped = 0;
        for (ItemStack item : collisions) {
            Map<Integer, ItemStack> remaining = inventory.addItem(item);
            for (ItemStack overflow : remaining.values()) { player.getWorld().dropItemNaturally(player.getLocation(), overflow); dropped += overflow.getAmount(); }
        }
        return dropped;
    }

    private static void placeSpecial(ItemStack current, ItemStack authored, java.util.function.Consumer<ItemStack> setter, List<ItemStack> collisions) {
        if (authored == null) return;
        if (empty(current)) setter.accept(authored.clone()); else collisions.add(authored.clone());
    }
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir(); }
}
