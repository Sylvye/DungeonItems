package com.dungeonitems.storage;

import com.dungeonitems.model.KitDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class KitRepositoryTest {
    @TempDir Path directory;

    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void roundTripsEveryAuthoredSlotUsingNestedYamlPaths() throws Exception {
        KitRepository repository = new KitRepository(directory);
        KitDefinition kit = new KitDefinition("starter");
        for (int slot = 0; slot < 36; slot++) kit.slots().put("inventory." + slot, new ItemStack(slot == 0 ? Material.CROSSBOW : Material.ARROW, slot == 0 ? 1 : Math.min(64, slot + 1)));
        kit.slots().put("helmet", new ItemStack(Material.DIAMOND_HELMET));
        kit.slots().put("chestplate", new ItemStack(Material.DIAMOND_CHESTPLATE));
        kit.slots().put("leggings", new ItemStack(Material.DIAMOND_LEGGINGS));
        kit.slots().put("boots", new ItemStack(Material.DIAMOND_BOOTS));
        kit.slots().put("offhand", new ItemStack(Material.SHIELD));
        repository.save(kit);

        String yaml = Files.readString(directory.resolve("starter.yml"));
        assertEquals(true, yaml.contains("inventory:"));
        assertEquals(true, yaml.contains("schema-version: 1"));
        KitRepository reloaded = new KitRepository(directory); reloaded.reload();
        KitDefinition loaded = reloaded.get("starter");
        assertEquals(41, loaded.slots().size());
        assertEquals(Material.CROSSBOW, loaded.slots().get("inventory.0").getType());
        assertEquals(36, loaded.slots().get("inventory.35").getAmount());
        assertEquals(Material.SHIELD, loaded.slots().get("offhand").getType());
    }

    @Test void loadsExistingLegacyNestedInventoryYamlWithoutRewritingIt() throws Exception {
        Path file = directory.resolve("legacy.yml");
        Files.writeString(file, "id: legacy\nslots:\n  inventory:\n    '0':\n      ==: org.bukkit.inventory.ItemStack\n      v: 3955\n      type: CROSSBOW\n      amount: 1\n");
        KitRepository repository = new KitRepository(directory); repository.reload();
        assertEquals(Material.CROSSBOW, repository.get("legacy").slots().get("inventory.0").getType());
        assertEquals(false, Files.readString(file).contains("schema-version"));
    }

    @Test void renameAndDuplicateCopyExactSnapshotsAndRejectCollisions() throws Exception {
        KitRepository repository = new KitRepository(directory);
        KitDefinition starter = new KitDefinition("starter"); starter.slots().put("inventory.0", new ItemStack(Material.MACE)); repository.save(starter);
        repository.duplicate(starter.copyAs("copy"));
        repository.rename("starter", starter.copyAs("renamed"));
        assertEquals(null, repository.get("starter"));
        assertEquals(Material.MACE, repository.get("copy").slots().get("inventory.0").getType());
        assertEquals(Material.MACE, repository.get("renamed").slots().get("inventory.0").getType());
        assertThrows(IllegalArgumentException.class, () -> repository.duplicate(starter.copyAs("copy")));
    }

    @Test void failedReloadDoesNotClearAlreadyLoadedKits() throws Exception {
        KitRepository repository = new KitRepository(directory);
        KitDefinition starter = new KitDefinition("starter"); starter.slots().put("inventory.0", new ItemStack(Material.STONE)); repository.save(starter);
        Files.writeString(directory.resolve("broken.yml"), "id: broken\nslots:\n  inventory:\n    '0': not-an-item\n");
        assertThrows(IllegalStateException.class, repository::reload);
        assertEquals(Material.STONE, repository.get("starter").slots().get("inventory.0").getType());
    }
}
