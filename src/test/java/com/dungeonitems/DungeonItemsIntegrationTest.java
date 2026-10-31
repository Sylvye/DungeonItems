package com.dungeonitems;

import com.bountysmp.configurableitems.ConfigurableItemsPlugin;
import com.bountysmp.configurableitems.api.ConfigurableItemsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class DungeonItemsIntegrationTest {
    private ServerMock server;

    @BeforeEach void setUp() { server = MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void loadsBothServicesAndOpensUnifiedCreateEditor() {
        MockBukkit.load(ConfigurableItemsPlugin.class);
        MockBukkit.load(DungeonItemsPlugin.class);
        PlayerMock player = server.addPlayer(); player.setOp(true);

        assertTrue(player.performCommand("di create smoke_sword"));
        assertNotNull(Bukkit.getServicesManager().load(ConfigurableItemsAPI.class));
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(4));
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(21));

        var before = player.getOpenInventory().getTopInventory().getItem(4).getItemMeta().displayName();
        player.simulateInventoryClick(11);
        var after = player.getOpenInventory().getTopInventory().getItem(4).getItemMeta().displayName();
        assertNotEquals(before, after, "rarity changes must refresh the preview in the same interaction");

        player.simulateInventoryClick(20); // Abilities
        player.simulateInventoryClick(10); // Create Ability
        player.simulateInventoryClick(10); // First available trigger
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(14), "ability editor must expose Actions");
        player.simulateInventoryClick(14);
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(10), "focused actions must expose Console Command");
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(12), "focused actions must expose the guided CI action catalog");
        player.simulateInventoryClick(49); // Ability editor
        player.simulateInventoryClick(49); // Ability list
        player.simulateInventoryClick(49); // Main editor

        player.simulateInventoryClick(53);
        assertTrue(player.performCommand("ci give smoke_sword"));
        assertTrue(Arrays.stream(player.getInventory().getStorageContents()).allMatch(item -> item == null || item.getType().isAir()),
            "CI must refuse to distribute DungeonItems-managed definitions");
    }

    @Test void kitCommandsAndMainMenuOpenTheKitBrowser() {
        MockBukkit.load(ConfigurableItemsPlugin.class);
        MockBukkit.load(DungeonItemsPlugin.class);
        PlayerMock player = server.addPlayer(); player.setOp(true);

        assertTrue(player.performCommand("di kit create browser_kit"));
        player.simulateInventoryClick(9); // Save the empty kit.
        player.simulateInventoryClick(10); // Back to the browser.
        assertEquals("DI: Kits", player.getOpenInventory().getTitle());
        assertEquals(Material.STRUCTURE_VOID, player.getOpenInventory().getTopInventory().getItem(10).getType());

        assertTrue(player.performCommand("di kits"));
        assertEquals("DI: Kits", player.getOpenInventory().getTitle());
        assertTrue(player.performCommand("di"));
        player.simulateInventoryClick(45);
        assertEquals("DI: Kits", player.getOpenInventory().getTitle());
    }

    @Test void publicKitApiListsKitsAndReturnsDetachedContents() {
        MockBukkit.load(ConfigurableItemsPlugin.class);
        MockBukkit.load(DungeonItemsPlugin.class);
        PlayerMock player = server.addPlayer(); player.setOp(true);
        assertTrue(player.performCommand("di kit create api_kit"));
        player.simulateInventoryClick(9);
        var api = Bukkit.getServicesManager().load(com.dungeonitems.api.DungeonItemsAPI.class);
        assertNotNull(api); assertTrue(api.kits().contains("api_kit"));
        var kit = api.kit("api_kit").orElseThrow();
        kit.slots().put("inventory.0", new org.bukkit.inventory.ItemStack(Material.DIAMOND));
        assertTrue(api.kit("api_kit").orElseThrow().slots().isEmpty());
        assertTrue(api.kit("missing").isEmpty());
    }
}
