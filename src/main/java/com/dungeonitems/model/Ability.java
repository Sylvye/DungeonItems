package com.dungeonitems.model;

import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import java.util.List;

public record Ability(TriggerType trigger, String name, String description, int cooldownTicks, String cooldownMessage,
                      List<CustomItemDefinition.TriggerCommandDef> actions) {
    public Ability {
        if (trigger == null) throw new IllegalArgumentException("Ability trigger is required");
        name = Rarity.stripLegacyFormatting(name).trim();
        description = Rarity.stripLegacyFormatting(description).trim();
        cooldownTicks = Math.max(0, cooldownTicks);
        cooldownMessage = cooldownMessage == null ? "" : cooldownMessage;
        actions = actions == null ? List.of() : actions.stream().map(CustomItemDefinition.TriggerCommandDef::copy).toList();
    }

    public Ability(TriggerType trigger, String name, String description, int cooldownTicks, String cooldownMessage) {
        this(trigger, name, description, cooldownTicks, cooldownMessage, List.of());
    }

    public Ability withName(String value) { return new Ability(trigger, value, description, cooldownTicks, cooldownMessage, actions); }
    public Ability withDescription(String value) { return new Ability(trigger, name, value, cooldownTicks, cooldownMessage, actions); }
    public Ability withCooldown(int ticks) { return new Ability(trigger, name, description, ticks, cooldownMessage, actions); }
    public Ability withCooldownMessage(String value) { return new Ability(trigger, name, description, cooldownTicks, value, actions); }
    public Ability withActions(List<CustomItemDefinition.TriggerCommandDef> value) { return new Ability(trigger, name, description, cooldownTicks, cooldownMessage, value); }
    public Ability withTrigger(TriggerType value) { return new Ability(value, name, description, cooldownTicks, cooldownMessage, actions); }
}
