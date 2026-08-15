package com.dungeonitems.storage;

import com.bountysmp.configurableitems.model.TriggerType;
import com.dungeonitems.model.Ability;
import com.dungeonitems.model.DungeonItemTemplate;
import com.dungeonitems.model.DungeonItemType;
import com.dungeonitems.model.Rarity;
import com.dungeonitems.model.StatKey;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TemplateRepositoryTest {
    @TempDir Path directory;

    @Test void roundTripsMetadataAndAbility() throws Exception {
        TemplateRepository repository = new TemplateRepository(directory);
        DungeonItemTemplate template = new DungeonItemTemplate("flame_sword", "Flame Sword", Rarity.LEGENDARY, DungeonItemType.WEAPON, "Burns targets");
        template.statOverrides().put(StatKey.DAMAGE, 8.5);
        template.abilities().put(TriggerType.RIGHT_CLICK, new Ability(TriggerType.RIGHT_CLICK, "Fire Blast", "Launches fire", 300, "Wait {COOLDOWN}s",
            List.of(new CustomItemDefinition.TriggerCommandDef("DAMAGE 5"), new CustomItemDefinition.TriggerCommandDef("say fire", 20, "wait"))));
        repository.save(template);

        TemplateRepository reloaded = new TemplateRepository(directory); reloaded.reload();
        DungeonItemTemplate value = reloaded.get("flame_sword");
        assertEquals(Rarity.LEGENDARY, value.rarity());
        assertEquals(DungeonItemType.WEAPON, value.type());
        assertEquals(300, value.abilities().get(TriggerType.RIGHT_CLICK).cooldownTicks());
        assertEquals(8.5, value.statOverrides().get(StatKey.DAMAGE));
        assertEquals("DAMAGE 5", value.abilities().get(TriggerType.RIGHT_CLICK).actions().getFirst().command());
        assertEquals(20, value.abilities().get(TriggerType.RIGHT_CLICK).actions().get(1).cooldownTicks());
    }
}
